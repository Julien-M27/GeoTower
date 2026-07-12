package fr.geotower.data.build

/**
 * Recepteur de lignes SUP_* brutes, appele UNE fois par ligne pendant le parse du ZIP mensuel.
 *
 * Mutualisation du parsing : le builder mobile ([GeoTowerDbBuilder]) parse le ZIP et « tee » chaque
 * ligne SUP vers ce sink **avant ses propres filtres** (mobile) ; la base radio est ainsi stagee dans
 * le MEME passage, ce qui evite de re-parser les cinq gros fichiers SUP (bande/emetteur/antenne/
 * support/station) une seconde fois. Le builder mobile ne connait rien de la radio : juste ce contrat.
 */
interface SupRowSink {
    fun station(row: AnfrCsvRow)
    fun support(row: AnfrCsvRow)
    fun antenne(row: AnfrCsvRow)
    fun emetteur(row: AnfrCsvRow)
    fun bande(row: AnfrCsvRow)

    /** Appele apres la derniere ligne SUP : flush des lots + finalisation du staging (vues/index). */
    fun finish()

    /** Sink no-op (build mobile standalone, sans generation radio). */
    object None : SupRowSink {
        override fun station(row: AnfrCsvRow) {}
        override fun support(row: AnfrCsvRow) {}
        override fun antenne(row: AnfrCsvRow) {}
        override fun emetteur(row: AnfrCsvRow) {}
        override fun bande(row: AnfrCsvRow) {}
        override fun finish() {}
    }
}
