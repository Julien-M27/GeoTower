package fr.geotower.data.trip

import fr.geotower.data.api.RouteApi

/**
 * Un trajet planifié : une tournée de relevés, ses étapes dans l'ordre, et l'itinéraire calculé
 * entre elles.
 *
 * Stocké tel quel en JSON par [TripPlanStore]. Deux règles à respecter en modifiant cette classe :
 *
 * 1. Gson n'applique **pas** les valeurs par défaut de Kotlin. Un champ ajouté après coup vaut
 *    `null` à la relecture d'un fichier écrit par une version antérieure, même déclaré non nul :
 *    ajouter les nouveaux champs **en fin de classe**, et leur donner une valeur de repli dans
 *    [sanitized].
 * 2. Cette classe porte des champs collection (`steps`, `legs`), donc une règle `-keep` est
 *    obligatoire dans `proguard-rules.pro` — sans elle, R8 en mode complet efface le type
 *    générique et la première relecture plante en release seulement. La contrepartie est que les
 *    **noms de champs sont figés** : renommer `steps` rendrait illisibles les trajets déjà
 *    enregistrés sur les téléphones.
 */
data class TripPlan(
    val id: String,
    /** Version du format, pour une éventuelle migration. Voir [TripPlanStore.SCHEMA_VERSION]. */
    val schemaVersion: Int,
    val name: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    /** [RouteApi.PROFILE_CAR] ou [RouteApi.PROFILE_PEDESTRIAN], pour tout le trajet. */
    val profile: String,
    /** Referme le trajet sur la première étape : le dernier segment ramène au point de départ. */
    val returnToStart: Boolean,
    val steps: List<TripStep>,
    /** Segments calculés. Peut être incomplet : un segment manquant se dessine en trait direct. */
    val legs: List<TripLeg>,
    /** Date et heure prévues, ou `null` pour un trajet sans date. */
    val plannedAtMillis: Long?,
    /** Délais de rappel en minutes avant [plannedAtMillis], par exemple `[1440, 180]`. */
    val reminderOffsetsMinutes: List<Int>,
    /** Temps d'arrêt estimé sur chaque étape, pour l'heure de fin de tournée. */
    val stopDurationMinutes: Int,
    /** [STATUS_DRAFT], [STATUS_PLANNED], [STATUS_DONE] ou [STATUS_ARCHIVED]. */
    val status: String,
    /**
     * Vrai tant que le nom est celui proposé par l'app : il suit alors la date prévue. Dès que
     * l'utilisateur renomme, ce drapeau tombe et son titre n'est plus jamais réécrit.
     *
     * Ajouté après coup, donc **en fin de classe**. C'est un booléen primitif : Gson le relit à
     * `false` sur un trajet écrit par une version antérieure, ce qui est le bon repli — un titre
     * déjà en place est traité comme choisi, jamais réécrit dans le dos de l'utilisateur.
     */
    val autoNamed: Boolean = false
) {
    /**
     * Les couples d'indices d'étapes que l'itinéraire doit relier, dans l'ordre. Avec
     * [returnToStart], le dernier couple ramène de la dernière étape vers la première.
     */
    fun legPairs(): List<Pair<Int, Int>> {
        if (steps.size < 2) return emptyList()
        val pairs = ArrayList<Pair<Int, Int>>(steps.size)
        for (index in 0 until steps.lastIndex) {
            pairs += index to (index + 1)
        }
        if (returnToStart) pairs += steps.lastIndex to 0
        return pairs
    }

    fun legBetween(fromIndex: Int, toIndex: Int): TripLeg? =
        legs.firstOrNull { it.fromIndex == fromIndex && it.toIndex == toIndex }

    /** Vrai quand tous les segments attendus sont calculés — donc quand distance et durée sont fiables. */
    fun isRouteComplete(): Boolean = legPairs().all { legBetween(it.first, it.second) != null }

    /** Somme des segments **calculés**. Un trajet incomplet renvoie donc une distance minorée. */
    fun totalDistanceMeters(): Double = legPairs().sumOf { legBetween(it.first, it.second)?.distanceMeters ?: 0.0 }

    fun totalDurationSeconds(): Double = legPairs().sumOf { legBetween(it.first, it.second)?.durationSeconds ?: 0.0 }

    /** Durée totale de la tournée, temps d'arrêt sur place compris. */
    fun totalDurationWithStopsSeconds(): Double {
        // Le point de départ ne compte pas comme un arrêt, et l'arrivée d'une boucle non plus :
        // c'est la même étape que le départ.
        val stops = (steps.size - 1).coerceAtLeast(0)
        return totalDurationSeconds() + stops * stopDurationMinutes * 60.0
    }

    fun visitedCount(): Int = steps.count { it.visitedAtMillis != null }

    /**
     * Brouillon sans la moindre étape : rien n'y a été posé, il n'a pas à être conservé. Le statut
     * compte autant que les étapes — une tournée datée, terminée ou archivée reste un choix de
     * l'utilisateur, même vide, et on ne la lui retire pas.
     */
    fun isEmptyDraft(): Boolean = steps.isEmpty() && status == STATUS_DRAFT

    /**
     * Même tournée, au sens de ce que l'édition sur la carte peut changer : les étapes, le profil
     * et la fermeture de la boucle.
     *
     * Volontairement **pas** l'égalité de la classe : `updatedAtMillis` change à chaque
     * enregistrement, et les segments ne font que découler des trois champs comparés ici. Sert à
     * savoir s'il y a quelque chose à proposer d'enregistrer en quittant l'édition.
     */
    fun hasSameContentAs(other: TripPlan): Boolean =
        steps == other.steps && profile == other.profile && returnToStart == other.returnToStart

    /**
     * Le statut que prend le trajet quand on lui fixe ou lui retire une date.
     *
     * C'est le statut qui alimente le filtre « à venir » : une tournée datée restée « brouillon »
     * n'y apparaîtrait jamais. Une tournée déjà terminée ou archivée garde le sien — la dater à
     * nouveau ne la ramène pas dans les tournées à faire.
     */
    fun statusAfterScheduling(plannedAtMillis: Long?): String = when {
        status == STATUS_ARCHIVED || status == STATUS_DONE -> status
        plannedAtMillis != null -> STATUS_PLANNED
        else -> STATUS_DRAFT
    }

    /** Étapes réellement à relever : en boucle, le retour au départ n'en est pas une de plus. */
    fun relevantStepCount(): Int = steps.size

    /**
     * Remplace tout champ que Gson a pu laisser à `null` en relisant un fichier écrit par une
     * version antérieure. Voir la règle 1 de la documentation de classe.
     */
    // Le compilateur signale ces gardes comme inutiles : pour lui, ces champs ne peuvent pas être
    // nuls. C'est justement le piège — Gson instancie sans passer par le constructeur Kotlin et ne
    // vérifie donc aucun type. Voir la règle 1 de la documentation de classe.
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS", "UNNECESSARY_SAFE_CALL")
    fun sanitized(): TripPlan? {
        if (id == null || id.isBlank()) return null
        val safeSteps = (steps ?: emptyList()).mapNotNull { it?.sanitized() }
        return copy(
            schemaVersion = if (schemaVersion <= 0) TripPlanStore.SCHEMA_VERSION else schemaVersion,
            name = name ?: "",
            profile = profile.takeIf { it == RouteApi.PROFILE_PEDESTRIAN } ?: RouteApi.PROFILE_CAR,
            steps = safeSteps,
            legs = (legs ?: emptyList())
                .filterNotNull()
                .filter { it.isWithin(safeSteps.size) },
            reminderOffsetsMinutes = (reminderOffsetsMinutes ?: emptyList())
                .filterNotNull()
                .filter { it > 0 }
                .distinct()
                .sortedDescending(),
            stopDurationMinutes = stopDurationMinutes.coerceIn(0, MAX_STOP_DURATION_MINUTES),
            status = status.takeIf { it in ALL_STATUSES } ?: STATUS_DRAFT
        )
    }

    companion object {
        const val STATUS_DRAFT = "draft"
        const val STATUS_PLANNED = "planned"
        const val STATUS_DONE = "done"
        const val STATUS_ARCHIVED = "archived"

        val ALL_STATUSES = setOf(STATUS_DRAFT, STATUS_PLANNED, STATUS_DONE, STATUS_ARCHIVED)

        /** Une journée d'arrêt sur une étape n'a pas de sens : c'est un garde-fou de relecture. */
        const val MAX_STOP_DURATION_MINUTES = 12 * 60

        /** Rappels proposés par défaut : la veille, puis trois heures avant. */
        val DEFAULT_REMINDER_OFFSETS_MINUTES = listOf(24 * 60, 3 * 60)
    }
}

/**
 * Une étape du trajet. **Les coordonnées font foi** : [supportId] n'est qu'un pointeur de confort
 * vers la fiche du site, qui peut très bien ne plus exister après une régénération de la base sans
 * que la tournée en soit invalidée pour autant.
 */
data class TripStep(
    val latitude: Double,
    val longitude: Double,
    /** Nom du site, adresse saisie, ou libellé de repli construit à l'affichage si vide. */
    val label: String,
    /** [KIND_SITE], [KIND_MANUAL], [KIND_ADDRESS] ou [KIND_CURRENT_POSITION]. */
    val kind: String,
    val supportId: String?,
    val visitedAtMillis: Long?,
    /** Réservé : note de terrain, sans interface pour l'instant. */
    val note: String?,
    /** Réservé : profil du segment vers l'étape suivante. `null` = celui du trajet. */
    val profileToNext: String?
) {
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS", "UNNECESSARY_SAFE_CALL")
    fun sanitized(): TripStep? {
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return copy(
            label = label ?: "",
            kind = kind.takeIf { it in ALL_KINDS } ?: KIND_MANUAL
        )
    }

    companion object {
        /**
         * Étape posée en cliquant un marqueur d'antenne. [TripStep.supportId] n'est renseigné que
         * lorsqu'il est connu sans requête : les marqueurs de la carte portent l'identifiant de
         * station, pas celui du support.
         */
        const val KIND_SITE = "site"

        /** Point libre posé sur la carte. */
        const val KIND_MANUAL = "manual"

        /** Résultat de la recherche d'adresse ou de commune. */
        const val KIND_ADDRESS = "address"

        /** Position de l'appareil au moment où l'étape a été posée — figée ensuite. */
        const val KIND_CURRENT_POSITION = "current_position"

        val ALL_KINDS = setOf(KIND_SITE, KIND_MANUAL, KIND_ADDRESS, KIND_CURRENT_POSITION)
    }
}

/**
 * L'itinéraire calculé entre deux étapes. La géométrie est encodée ([TripGeometryCodec]) et non
 * stockée en tableau de doubles : voir la documentation du codec.
 */
data class TripLeg(
    val fromIndex: Int,
    val toIndex: Int,
    val profile: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val encodedGeometry: String,
    /** Manœuvres du guidage tour par tour. `null` tant que le lot 4 n'est pas livré. */
    val maneuvers: List<TripManeuver>?
) {
    /** Écarte un segment dont les indices ne pointent plus sur des étapes existantes. */
    fun isWithin(stepCount: Int): Boolean =
        fromIndex in 0 until stepCount && toIndex in 0 until stepCount && fromIndex != toIndex

    fun points(): List<DoubleArray> = TripGeometryCodec.decode(encodedGeometry)
}

/**
 * Une manœuvre du guidage. Les codes viennent tels quels du service (`instruction.type` et
 * `modifier`, en anglais façon OSRM) : la phrase affichée est construite par l'app depuis
 * `strings.xml`, jamais reprise du serveur, sinon elle ne se traduirait pas.
 */
data class TripManeuver(
    /** `depart`, `turn`, `new name`, `end of road`, `roundabout`, `fork`, `merge`, `arrive`… */
    val type: String,
    /** `left`, `right`, `slight left`, `sharp right`, `straight`, `uturn`… */
    val modifier: String?,
    /** Nom de voie brut, abrégé et en majuscules façon BD TOPO (`QU DE L'HORLOGE`). */
    val roadName: String?,
    val distanceMeters: Double,
    val durationSeconds: Double
)
