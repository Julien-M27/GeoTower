package fr.geotower.data.trip

import android.content.Context
import com.google.gson.Gson
import fr.geotower.data.api.RouteApi
import java.io.File
import java.util.UUID

/**
 * Les trajets enregistrés : un fichier JSON dans `filesDir`, sur le patron de `ShareHistoryStore`.
 * Rien n'est envoyé au serveur, et rien ne passe par Room — la base de l'app est préconstruite, son
 * hash de schéma est figé et n'accepte aucune table de plus.
 *
 * **Pas de plafond** de trajets, contrairement à l'historique des partages : une tournée est une
 * donnée saisie à la main, jamais générée en masse, et en perdre une silencieusement serait grave.
 * L'écriture est atomique pour la même raison : une coupure en pleine sauvegarde ne doit pas laisser
 * un fichier tronqué à la place de toutes les tournées.
 */
/** Compte rendu de [TripPlanStore.mergePlans] : trajets ajoutés, et trajets remis à jour. */
data class TripPlanMergeResult(val added: Int, val refreshed: Int)

object TripPlanStore {
    /** Version du format écrit par cette version de l'app. Voir [TripPlan.schemaVersion]. */
    const val SCHEMA_VERSION = 1

    private const val FILE_NAME = "trip_plans.json"
    private const val TEMP_FILE_NAME = "trip_plans.json.tmp"

    private val gson = Gson()

    @Synchronized
    fun read(context: Context): List<TripPlan> = readInternal(context.applicationContext)

    @Synchronized
    fun readOne(context: Context, id: String): TripPlan? =
        readInternal(context.applicationContext).firstOrNull { it.id == id }

    /** Crée le trajet s'il est inconnu, le remplace sinon. Met [TripPlan.updatedAtMillis] à jour. */
    @Synchronized
    fun save(context: Context, plan: TripPlan, updatedAtMillis: Long = System.currentTimeMillis()) {
        val sanitized = plan.copy(updatedAtMillis = updatedAtMillis).sanitized() ?: return
        val plans = readInternal(context.applicationContext)
        val index = plans.indexOfFirst { it.id == sanitized.id }
        val next = if (index >= 0) plans.toMutableList().apply { set(index, sanitized) } else plans + sanitized
        saveInternal(context.applicationContext, next)
    }

    /**
     * Relit les trajets en écartant les brouillons restés **sans aucune étape** : un « nouveau
     * trajet » ouvert puis quitté sans rien poser ne doit pas encombrer la liste.
     *
     * Le ménage se fait ici, au chargement de la liste, et non en quittant la carte : là-bas, une
     * simple rotation d'écran recompose l'écran et supprimerait le trajet sous les doigts de
     * quelqu'un qui n'a pas encore posé son premier point.
     *
     * Rend la liste conservée, pour épargner une seconde lecture à l'appelant.
     */
    @Synchronized
    fun purgeEmptyDrafts(context: Context): List<TripPlan> {
        val plans = readInternal(context.applicationContext)
        val kept = plans.filterNot { it.isEmptyDraft() }
        if (kept.size != plans.size) saveInternal(context.applicationContext, kept)
        return kept
    }

    /**
     * Crédite une étape des photos qui viennent de partir depuis elle.
     *
     * Lecture-modification-écriture sous verrou plutôt qu'un `save` construit par l'appelant : au
     * moment où l'envoi démarre, l'écran de la carte a pu écrire le trajet de son côté (une note
     * saisie, une étape cochée), et repartir d'une copie en mémoire écraserait ce travail.
     *
     * Sans effet si le trajet ou l'étape n'existent plus : une tournée peut être modifiée pendant
     * qu'un envoi est en vol, et un envoi ne doit jamais faire échouer autre chose.
     */
    @Synchronized
    fun addPhotosSent(context: Context, tripId: String, stepIndex: Int, count: Int) {
        if (count <= 0) return
        val plans = readInternal(context.applicationContext)
        val planIndex = plans.indexOfFirst { it.id == tripId }
        if (planIndex < 0) return
        val plan = plans[planIndex]
        val step = plan.steps.getOrNull(stepIndex) ?: return

        val steps = plan.steps.toMutableList().apply {
            set(stepIndex, step.copy(photosSentCount = step.photosSentCount + count))
        }
        val updated = plan
            .copy(steps = steps, updatedAtMillis = System.currentTimeMillis())
            .sanitized()
            ?: return
        saveInternal(
            context.applicationContext,
            plans.toMutableList().apply { set(planIndex, updated) }
        )
    }

    @Synchronized
    fun delete(context: Context, id: String) {
        val plans = readInternal(context.applicationContext)
        if (plans.none { it.id == id }) return
        saveInternal(context.applicationContext, plans.filterNot { it.id == id })
    }

    /** Remplace tout le contenu — réservé à un import, et à la purge depuis les réglages. */
    @Synchronized
    fun replaceAll(context: Context, plans: List<TripPlan>) {
        saveInternal(context.applicationContext, plans.mapNotNull { it.sanitized() })
    }

    /**
     * Fusionne les trajets d'une sauvegarde avec ceux d'ici. Contrairement aux historiques, une
     * tournée n'est pas un événement révolu mais un document qu'on rouvre et qu'on modifie : elle se
     * fusionne donc sur [TripPlan.updatedAtMillis], et non par simple ajout.
     *
     * - trajet inconnu → ajouté ;
     * - trajet connu, version importée **strictement** plus récente → remplacée (les étapes cochées
     *   sur l'autre appareil reviennent ici) ;
     * - trajet connu, version importée aussi ancienne ou plus → laissée intacte.
     *
     * Rien n'est jamais supprimé, et la comparaison stricte rend l'opération sans effet à la
     * deuxième application de la même sauvegarde.
     */
    @Synchronized
    fun mergePlans(context: Context, plans: List<TripPlan>): TripPlanMergeResult {
        val sanitized = plans.mapNotNull { it.sanitized() }.filterNot { it.isEmptyDraft() }
        if (sanitized.isEmpty()) return TripPlanMergeResult(0, 0)

        val existing = readInternal(context.applicationContext)
        val byId = existing.associateByTo(LinkedHashMap()) { it.id }
        var added = 0
        var refreshed = 0
        sanitized.forEach { incoming ->
            val local = byId[incoming.id]
            when {
                local == null -> {
                    byId[incoming.id] = incoming
                    added++
                }
                incoming.updatedAtMillis > local.updatedAtMillis -> {
                    byId[incoming.id] = incoming
                    refreshed++
                }
            }
        }

        if (added == 0 && refreshed == 0) return TripPlanMergeResult(0, 0)
        saveInternal(context.applicationContext, byId.values.toList())
        return TripPlanMergeResult(added, refreshed)
    }

    @Synchronized
    fun clear(context: Context) {
        file(context.applicationContext).delete()
    }

    /**
     * Copie un trajet : nouvel identifiant, étapes décochées, date et rappels effacés. Dupliquer
     * sert à refaire une tournée, pas à en garder deux fois le compte rendu.
     */
    fun duplicated(plan: TripPlan, name: String, createdAtMillis: Long = System.currentTimeMillis()): TripPlan =
        plan.copy(
            id = UUID.randomUUID().toString(),
            name = name,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = createdAtMillis,
            steps = plan.steps.map { it.copy(visitedAtMillis = null) },
            plannedAtMillis = null,
            reminderOffsetsMinutes = emptyList(),
            status = TripPlan.STATUS_DRAFT,
            // « … (copie) » est un nom porté par la copie : on ne le réécrira pas sur une date.
            autoNamed = false
        )

    /** Inverse le sens de la tournée : les segments calculés ne valent plus rien (sens uniques). */
    fun reversed(plan: TripPlan): TripPlan =
        plan.copy(steps = plan.steps.reversed(), legs = emptyList())

    fun newPlan(
        name: String,
        profile: String = RouteApi.PROFILE_CAR,
        createdAtMillis: Long = System.currentTimeMillis()
    ): TripPlan = TripPlan(
        id = UUID.randomUUID().toString(),
        schemaVersion = SCHEMA_VERSION,
        name = name,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = createdAtMillis,
        profile = profile,
        returnToStart = false,
        steps = emptyList(),
        legs = emptyList(),
        plannedAtMillis = null,
        reminderOffsetsMinutes = emptyList(),
        stopDurationMinutes = 0,
        status = TripPlan.STATUS_DRAFT,
        autoNamed = true
    )

    private fun readInternal(context: Context): List<TripPlan> {
        val file = file(context)
        if (!file.isFile) return emptyList()
        // Un trajet illisible est écarté, jamais propagé en exception : la liste doit s'ouvrir même
        // si un enregistrement a mal tourné, sinon l'utilisateur perd l'accès à toutes les autres.
        return runCatching {
            gson.fromJson(file.readText(), Array<TripPlan>::class.java)
                ?.filterNotNull()
                ?.mapNotNull { it.sanitized() }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun saveInternal(context: Context, plans: List<TripPlan>) {
        runCatching {
            val target = file(context)
            val temporary = File(context.filesDir, TEMP_FILE_NAME)
            temporary.writeText(gson.toJson(plans))
            if (!temporary.renameTo(target)) {
                // Repli si le renommage échoue : on écrit directement, en acceptant la fenêtre de
                // risque, plutôt que de perdre la modification.
                target.writeText(temporary.readText())
                temporary.delete()
            }
        }
    }

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)
}
