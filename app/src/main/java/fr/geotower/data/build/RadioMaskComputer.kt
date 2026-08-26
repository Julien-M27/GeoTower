package fr.geotower.data.build

import fr.geotower.data.models.RadioFilterMasks

/** Accumulateur mutable des masques technologie / bande d'une station. */
class StationMasks(var techMask: Int = 0, var bandMask: Int = 0)

/**
 * Calcul des masques `tech_mask` / `band_mask`, porte a l'identique depuis
 * `update_masks_from_generation` et `update_masks_from_system_and_band`
 * (docs/server/build_fr_anfr_db.py). Les bits utilises sont ceux de
 * [RadioFilterMasks] (partages avec le reste de l'app).
 */
object RadioMaskComputer {

    // Familles deduites d'un libelle de systeme ou de generation. Bits internes au cache
    // ci-dessous, sans rapport avec ceux de RadioFilterMasks.
    private const val FAMILY_FH = 1 shl 0
    private const val FAMILY_2G = 1 shl 1
    private const val FAMILY_3G = 1 shl 2
    private const val FAMILY_4G = 1 shl 3
    private const val FAMILY_5G = 1 shl 4

    /**
     * Plafond des caches de libelles : les sources ANFR n'exposent qu'une poignee de valeurs
     * distinctes, ce garde-fou evite toute croissance non bornee sur une source inattendue.
     */
    private const val MAX_CACHE_ENTRIES = 8192

    private val systemFamilies = HashMap<String, Int>()
    private val generationFamilies = HashMap<String, Int>()

    /**
     * Memoise la reconnaissance de famille d'un libelle. Le scan des masques traverse plusieurs
     * millions de lignes (emetteur x bande) mais ne rencontre qu'une poignee de libelles distincts
     * ("GSM 900", "LTE 1800", "FH"...) : sans cache, chaque ligne payait un `uppercase()` — donc
     * une allocation — suivi de six `contains`. Le calcul est inchange, il n'est plus fait qu'une
     * fois par libelle. Non thread-safe, comme le reste du builder (un build = un thread).
     */
    private inline fun families(cache: HashMap<String, Int>, label: String?, compute: (String) -> Int): Int {
        val key = label ?: ""
        cache[key]?.let { return it }
        val value = compute(AnfrParsing.cleanText(label).uppercase())
        if (cache.size >= MAX_CACHE_ENTRIES) cache.clear()
        cache[key] = value
        return value
    }

    /** Python `update_masks_from_generation`. */
    fun updateMasksFromGeneration(masks: StationMasks, generation: String?) {
        val families = families(generationFamilies, generation) { g ->
            var flags = 0
            if (g.contains("2G")) flags = flags or FAMILY_2G
            if (g.contains("3G")) flags = flags or FAMILY_3G
            if (g.contains("4G")) flags = flags or FAMILY_4G
            if (g.contains("5G")) flags = flags or FAMILY_5G
            flags
        }
        if (families and FAMILY_2G != 0) masks.techMask = masks.techMask or RadioFilterMasks.TECH_2G
        if (families and FAMILY_3G != 0) masks.techMask = masks.techMask or RadioFilterMasks.TECH_3G
        if (families and FAMILY_4G != 0) masks.techMask = masks.techMask or RadioFilterMasks.TECH_4G
        if (families and FAMILY_5G != 0) masks.techMask = masks.techMask or RadioFilterMasks.TECH_5G
    }

    /**
     * Variante sans objet temporaire pour les builds nationaux : les deux masques sont empaquetes
     * dans un Long (tech en bas, bandes en haut). Elle evite de creer un [StationMasks] par ligne
     * de l'observatoire ou par jointure emetteur x bande.
     */
    fun updateMasksFromGeneration(packedMasks: Long, generation: String?): Long {
        val families = families(generationFamilies, generation) { g ->
            var flags = 0
            if (g.contains("2G")) flags = flags or FAMILY_2G
            if (g.contains("3G")) flags = flags or FAMILY_3G
            if (g.contains("4G")) flags = flags or FAMILY_4G
            if (g.contains("5G")) flags = flags or FAMILY_5G
            flags
        }
        var techMask = packedMasks.toInt()
        if (families and FAMILY_2G != 0) techMask = techMask or RadioFilterMasks.TECH_2G
        if (families and FAMILY_3G != 0) techMask = techMask or RadioFilterMasks.TECH_3G
        if (families and FAMILY_4G != 0) techMask = techMask or RadioFilterMasks.TECH_4G
        if (families and FAMILY_5G != 0) techMask = techMask or RadioFilterMasks.TECH_5G
        return packMasks(techMask, (packedMasks ushr 32).toInt())
    }

    /** Python `update_masks_from_system_and_band`. */
    fun updateMasksFromSystemAndBand(
        masks: StationMasks,
        system: String?,
        fStartMhz: Double?,
        fEndMhz: Double?
    ) {
        val families = families(systemFamilies, system) { s ->
            if (s.contains("FH")) {
                FAMILY_FH
            } else {
                var flags = 0
                if (s.contains("GSM") || s.contains("2G")) flags = flags or FAMILY_2G
                if (s.contains("UMTS") || s.contains("3G")) flags = flags or FAMILY_3G
                if (s.contains("LTE") || s.contains("4G")) flags = flags or FAMILY_4G
                if (s.contains("NR") || s.contains("5G")) flags = flags or FAMILY_5G
                flags
            }
        }

        if (families and FAMILY_FH != 0) {
            masks.techMask = masks.techMask or RadioFilterMasks.TECH_FH
            masks.bandMask = masks.bandMask or RadioFilterMasks.BAND_FH
            return
        }

        val is2g = families and FAMILY_2G != 0
        val is3g = families and FAMILY_3G != 0
        val is4g = families and FAMILY_4G != 0
        val is5g = families and FAMILY_5G != 0

        if (is2g) {
            masks.techMask = masks.techMask or RadioFilterMasks.TECH_2G
            if (overlaps(fStartMhz, fEndMhz, 880.0, 960.0)) addBand(masks, RadioFilterMasks.BAND_2G_900)
            if (overlaps(fStartMhz, fEndMhz, 1710.0, 1880.0)) addBand(masks, RadioFilterMasks.BAND_2G_1800)
        }

        if (is3g) {
            masks.techMask = masks.techMask or RadioFilterMasks.TECH_3G
            if (overlaps(fStartMhz, fEndMhz, 880.0, 960.0)) addBand(masks, RadioFilterMasks.BAND_3G_900)
            if (overlaps(fStartMhz, fEndMhz, 1920.0, 2170.0)) addBand(masks, RadioFilterMasks.BAND_3G_2100)
        }

        if (is4g) {
            masks.techMask = masks.techMask or RadioFilterMasks.TECH_4G
            if (overlaps(fStartMhz, fEndMhz, 700.0, 790.0)) addBand(masks, RadioFilterMasks.BAND_4G_700)
            if (overlaps(fStartMhz, fEndMhz, 791.0, 862.0)) addBand(masks, RadioFilterMasks.BAND_4G_800)
            if (overlaps(fStartMhz, fEndMhz, 880.0, 960.0)) addBand(masks, RadioFilterMasks.BAND_4G_900)
            if (overlaps(fStartMhz, fEndMhz, 1710.0, 1880.0)) addBand(masks, RadioFilterMasks.BAND_4G_1800)
            if (overlaps(fStartMhz, fEndMhz, 1920.0, 2170.0)) addBand(masks, RadioFilterMasks.BAND_4G_2100)
            if (overlaps(fStartMhz, fEndMhz, 2500.0, 2690.0)) addBand(masks, RadioFilterMasks.BAND_4G_2600)
        }

        if (is5g) {
            masks.techMask = masks.techMask or RadioFilterMasks.TECH_5G
            if (overlaps(fStartMhz, fEndMhz, 700.0, 790.0)) addBand(masks, RadioFilterMasks.BAND_5G_700)
            if (overlaps(fStartMhz, fEndMhz, 1427.0, 1518.0)) addBand(masks, RadioFilterMasks.BAND_5G_1400)
            if (overlaps(fStartMhz, fEndMhz, 1920.0, 2170.0)) addBand(masks, RadioFilterMasks.BAND_5G_2100)
            if (overlaps(fStartMhz, fEndMhz, 3300.0, 3800.0)) addBand(masks, RadioFilterMasks.BAND_5G_3500)
            if (overlaps(fStartMhz, fEndMhz, 3800.1, 4200.0)) addBand(masks, RadioFilterMasks.BAND_5G_4200)
            if (overlaps(fStartMhz, fEndMhz, 24000.0, 27500.0)) addBand(masks, RadioFilterMasks.BAND_5G_26000)
        }
    }

    /** Variante empaquetee sans allocation, pour [GeoTowerDbBuilder]. */
    fun updateMasksFromSystemAndBand(
        packedMasks: Long,
        system: String?,
        fStartMhz: Double?,
        fEndMhz: Double?,
    ): Long {
        val families = families(systemFamilies, system) { s ->
            if (s.contains("FH")) {
                FAMILY_FH
            } else {
                var flags = 0
                if (s.contains("GSM") || s.contains("2G")) flags = flags or FAMILY_2G
                if (s.contains("UMTS") || s.contains("3G")) flags = flags or FAMILY_3G
                if (s.contains("LTE") || s.contains("4G")) flags = flags or FAMILY_4G
                if (s.contains("NR") || s.contains("5G")) flags = flags or FAMILY_5G
                flags
            }
        }

        var techMask = packedMasks.toInt()
        var bandMask = (packedMasks ushr 32).toInt()
        if (families and FAMILY_FH != 0) {
            techMask = techMask or RadioFilterMasks.TECH_FH
            bandMask = bandMask or RadioFilterMasks.BAND_FH
            return packMasks(techMask, bandMask)
        }

        val is2g = families and FAMILY_2G != 0
        val is3g = families and FAMILY_3G != 0
        val is4g = families and FAMILY_4G != 0
        val is5g = families and FAMILY_5G != 0

        if (is2g) {
            techMask = techMask or RadioFilterMasks.TECH_2G
            if (overlaps(fStartMhz, fEndMhz, 880.0, 960.0)) bandMask = bandMask or RadioFilterMasks.BAND_2G_900
            if (overlaps(fStartMhz, fEndMhz, 1710.0, 1880.0)) bandMask = bandMask or RadioFilterMasks.BAND_2G_1800
        }
        if (is3g) {
            techMask = techMask or RadioFilterMasks.TECH_3G
            if (overlaps(fStartMhz, fEndMhz, 880.0, 960.0)) bandMask = bandMask or RadioFilterMasks.BAND_3G_900
            if (overlaps(fStartMhz, fEndMhz, 1920.0, 2170.0)) bandMask = bandMask or RadioFilterMasks.BAND_3G_2100
        }
        if (is4g) {
            techMask = techMask or RadioFilterMasks.TECH_4G
            if (overlaps(fStartMhz, fEndMhz, 700.0, 790.0)) bandMask = bandMask or RadioFilterMasks.BAND_4G_700
            if (overlaps(fStartMhz, fEndMhz, 791.0, 862.0)) bandMask = bandMask or RadioFilterMasks.BAND_4G_800
            if (overlaps(fStartMhz, fEndMhz, 880.0, 960.0)) bandMask = bandMask or RadioFilterMasks.BAND_4G_900
            if (overlaps(fStartMhz, fEndMhz, 1710.0, 1880.0)) bandMask = bandMask or RadioFilterMasks.BAND_4G_1800
            if (overlaps(fStartMhz, fEndMhz, 1920.0, 2170.0)) bandMask = bandMask or RadioFilterMasks.BAND_4G_2100
            if (overlaps(fStartMhz, fEndMhz, 2500.0, 2690.0)) bandMask = bandMask or RadioFilterMasks.BAND_4G_2600
        }
        if (is5g) {
            techMask = techMask or RadioFilterMasks.TECH_5G
            if (overlaps(fStartMhz, fEndMhz, 700.0, 790.0)) bandMask = bandMask or RadioFilterMasks.BAND_5G_700
            if (overlaps(fStartMhz, fEndMhz, 1427.0, 1518.0)) bandMask = bandMask or RadioFilterMasks.BAND_5G_1400
            if (overlaps(fStartMhz, fEndMhz, 1920.0, 2170.0)) bandMask = bandMask or RadioFilterMasks.BAND_5G_2100
            if (overlaps(fStartMhz, fEndMhz, 3300.0, 3800.0)) bandMask = bandMask or RadioFilterMasks.BAND_5G_3500
            if (overlaps(fStartMhz, fEndMhz, 3800.1, 4200.0)) bandMask = bandMask or RadioFilterMasks.BAND_5G_4200
            if (overlaps(fStartMhz, fEndMhz, 24000.0, 27500.0)) bandMask = bandMask or RadioFilterMasks.BAND_5G_26000
        }
        return packMasks(techMask, bandMask)
    }

    private fun addBand(masks: StationMasks, bandBit: Int) {
        masks.bandMask = masks.bandMask or bandBit
    }

    private fun overlaps(start: Double?, end: Double?, low: Double, high: Double): Boolean =
        AnfrParsing.rangeOverlaps(start, end, low, high)

    private fun packMasks(techMask: Int, bandMask: Int): Long =
        (bandMask.toLong() shl 32) or (techMask.toLong() and 0xffffffffL)
}
