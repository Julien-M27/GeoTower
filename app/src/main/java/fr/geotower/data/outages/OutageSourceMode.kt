package fr.geotower.data.outages

/**
 * Mode de source pour les sites hors service (pannes réseau) :
 * - [OPERATORS] : Fichiers CSV publiés directement par les 4 opérateurs mobiles (Orange, SFR, Bouygues, Free).
 *   Données en quasi temps réel, réparties sur 4 flux.
 * - [DAILY] : Fichier consolidé quotidien officiel publié par l'Arcep sur data.gouv.fr.
 *   Fichier unique et unifié, mais pouvant présenter 24h à 48h de retard.
 */
enum class OutageSourceMode(val key: String) {
    OPERATORS("operators"),
    DAILY("daily");

    companion object {
        fun fromKey(key: String?): OutageSourceMode =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: OPERATORS
    }
}
