package fr.geotower.data.outages

import androidx.annotation.StringRes
import fr.geotower.R

/**
 * Étapes de la génération locale des pannes, pour l'affichage « en direct » (notification live +
 * page de réglages). Mappées vers un libellé localisé via [labelRes]. Calqué sur `BuildPhase`.
 */
enum class OutageGenerationStep {
    DOWNLOAD,   // Téléchargement des fichiers opérateurs (détail = opérateur en cours)
    GEOCODE,    // Géocodage / calcul des pannes (détail = compteur de lignes)
    FINALIZE,   // Écriture du cache
    DONE,
}

@StringRes
fun OutageGenerationStep.labelRes(): Int = when (this) {
    OutageGenerationStep.DOWNLOAD -> R.string.outage_gen_step_download
    OutageGenerationStep.GEOCODE -> R.string.outage_gen_step_geocode
    OutageGenerationStep.FINALIZE -> R.string.outage_gen_step_finalize
    OutageGenerationStep.DONE -> R.string.outage_gen_step_done
}

/** Callback de progression : étape, pourcentage global (0-100), détail optionnel (opérateur, compteur…). */
typealias OutageProgressCallback = (step: OutageGenerationStep, percent: Int, detail: String?) -> Unit

/** No-op partagé pour les appels qui n'ont pas besoin de progression (ex. régénération paresseuse). */
val NoOutageProgress: OutageProgressCallback = { _, _, _ -> }

// Répartition du pourcentage global entre les étapes.
internal const val DOWNLOAD_PERCENT_END = 40
internal const val GEOCODE_PERCENT_END = 90
