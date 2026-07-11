package fr.geotower.data.build

import androidx.annotation.StringRes
import fr.geotower.R

/**
 * Phases de la generation locale, pour l'affichage « en direct » (notification live + carte).
 * L'ordre reflete le deroule reel ; chaque phase est mappee vers un libelle localise via
 * [labelRes] (partage entre le worker et la carte).
 */
enum class BuildPhase {
    RESOLVING,             // Recherche des sources officielles (data.gouv)
    DOWNLOADING,           // Telechargement des tables ANFR (ZIP mensuel)
    READING_STATIONS,      // Lecture de l'observatoire (liste maitresse des stations)
    READING_SUPPORTS,      // Lecture des tables SUP (supports/antennes/emetteurs/bandes)
    COMPUTING_FREQUENCIES, // Masques technologie/bande depuis emetteurs x bandes
    COMPUTING_ANTENNAS,    // Azimuts + FH
    BUILDING_DETAILS,      // Assemblage de details_frequences
    INSERTING,             // Emission des tables finales
    COMPUTING_STATS,       // radio_stat_current
    FINALIZING,            // Nettoyage staging + estampilles Room
    INSTALLING,            // Installation atomique de la base
    DONE,
}

@StringRes
fun BuildPhase.labelRes(): Int = when (this) {
    BuildPhase.RESOLVING -> R.string.appstrings_local_build_phase_resolving
    BuildPhase.DOWNLOADING -> R.string.appstrings_local_build_phase_downloading
    BuildPhase.READING_STATIONS -> R.string.appstrings_local_build_phase_reading
    BuildPhase.READING_SUPPORTS -> R.string.appstrings_local_build_phase_reading_supports
    BuildPhase.COMPUTING_FREQUENCIES -> R.string.appstrings_local_build_phase_processing
    BuildPhase.COMPUTING_ANTENNAS -> R.string.appstrings_local_build_phase_antennas
    BuildPhase.BUILDING_DETAILS -> R.string.appstrings_local_build_phase_details
    BuildPhase.INSERTING -> R.string.appstrings_local_build_phase_inserting
    BuildPhase.COMPUTING_STATS -> R.string.appstrings_local_build_phase_stats
    BuildPhase.FINALIZING -> R.string.appstrings_local_build_phase_finalizing
    BuildPhase.INSTALLING, BuildPhase.DONE -> R.string.appstrings_local_build_phase_installing
}
