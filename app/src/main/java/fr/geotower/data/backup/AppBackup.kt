package fr.geotower.data.backup

/**
 * Les rubriques d'une sauvegarde. L'identifiant est écrit tel quel dans le fichier : le renommer
 * rendrait muette la rubrique correspondante dans les sauvegardes déjà produites, qui serait alors
 * silencieusement ignorée à l'import. On en ajoute, on n'en renomme pas.
 */
object BackupSection {
    const val SHARE_HISTORY = "share_history"
    const val NOTIFICATION_HISTORY = "notification_history"
    const val PHOTO_UPLOADS = "photo_uploads"
    const val PHOTO_REPORTS = "photo_reports"
    const val TRIPS = "trips"
    const val PHOTO_FAVORITES = "photo_favorites"
    const val COUNTERS = "counters"
    const val SETTINGS_PROFILES = "settings_profiles"
    const val HIDDEN_SITES = "hidden_sites"

    /** Ordre d'affichage dans l'écran de sauvegarde, et ordre d'application à l'import. */
    val ALL = listOf(
        SHARE_HISTORY,
        NOTIFICATION_HISTORY,
        PHOTO_UPLOADS,
        PHOTO_REPORTS,
        TRIPS,
        PHOTO_FAVORITES,
        COUNTERS,
        SETTINGS_PROFILES,
        HIDDEN_SITES
    )
}

/**
 * Ce que cet appareil a à sauvegarder pour une rubrique donnée : le nombre d'éléments, affiché
 * avant l'export pour que l'utilisateur sache ce qu'il emporte.
 */
data class BackupSectionSize(
    val section: String,
    val itemCount: Int
)

/**
 * Ce qu'un fichier de sauvegarde apporterait à cet appareil-ci, rubrique par rubrique. Calculé sans
 * rien écrire : c'est l'aperçu montré avant de confirmer l'import.
 *
 * [newCount] + [alreadyPresentCount] = [incomingCount], sauf pour les trajets, où un trajet déjà
 * connu mais plus récent dans la sauvegarde est compté dans [refreshableCount] — il est déjà là,
 * mais l'import le remettra à jour.
 */
data class BackupSectionPreview(
    val section: String,
    val incomingCount: Int,
    val newCount: Int,
    val refreshableCount: Int = 0
) {
    val alreadyPresentCount: Int get() = incomingCount - newCount

    /** Vrai si l'import de cette rubrique changerait quelque chose ici. */
    val hasChanges: Boolean get() = newCount > 0 || refreshableCount > 0
}

/**
 * L'aperçu complet d'un fichier de sauvegarde. [sections] ne contient que les rubriques réellement
 * présentes dans le fichier : une sauvegarde produite par une version plus ancienne en porte moins,
 * et une produite par une version plus récente peut en porter que cette version-ci ne sait pas lire
 * — elles sont alors comptées dans [unknownSections] et laissées de côté, jamais refusées.
 */
data class BackupImportPreview(
    val schemaVersion: Int,
    val exportedAtMillis: Long,
    val appVersionName: String,
    val deviceLabel: String,
    val sections: List<BackupSectionPreview>,
    val unknownSections: List<String>,
    /** Le document d'origine, conservé pour l'import qui suit l'aperçu sans le relire du disque. */
    val rawJson: String
) {
    val hasChanges: Boolean get() = sections.any { it.hasChanges }

    fun section(section: String): BackupSectionPreview? = sections.firstOrNull { it.section == section }
}

/** Ce qu'une rubrique a réellement changé à l'import. */
data class BackupSectionOutcome(
    val section: String,
    val added: Int,
    val refreshed: Int = 0
) {
    val touched: Int get() = added + refreshed
}

data class BackupImportResult(
    val outcomes: List<BackupSectionOutcome>
) {
    val addedTotal: Int get() = outcomes.sumOf { it.added }
    val refreshedTotal: Int get() = outcomes.sumOf { it.refreshed }
    val changedAnything: Boolean get() = outcomes.any { it.touched > 0 }
}

/** Un fichier qui n'est pas une sauvegarde GeoTower, ou dont l'enveloppe est illisible. */
class BackupFormatException(message: String) : IllegalArgumentException(message)
