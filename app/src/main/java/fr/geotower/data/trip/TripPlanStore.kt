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
