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

    /** Python `update_masks_from_generation`. */
    fun updateMasksFromGeneration(masks: StationMasks, generation: String?) {
        val g = AnfrParsing.cleanText(generation).uppercase()
        if (g.contains("2G")) masks.techMask = masks.techMask or RadioFilterMasks.TECH_2G
        if (g.contains("3G")) masks.techMask = masks.techMask or RadioFilterMasks.TECH_3G
        if (g.contains("4G")) masks.techMask = masks.techMask or RadioFilterMasks.TECH_4G
        if (g.contains("5G")) masks.techMask = masks.techMask or RadioFilterMasks.TECH_5G
    }

    /** Python `update_masks_from_system_and_band`. */
    fun updateMasksFromSystemAndBand(
        masks: StationMasks,
        system: String?,
        fStartMhz: Double?,
        fEndMhz: Double?
    ) {
        val s = AnfrParsing.cleanText(system).uppercase()

        if (s.contains("FH")) {
            masks.techMask = masks.techMask or RadioFilterMasks.TECH_FH
            masks.bandMask = masks.bandMask or RadioFilterMasks.BAND_FH
            return
        }

        val is2g = s.contains("GSM") || s.contains("2G")
        val is3g = s.contains("UMTS") || s.contains("3G")
        val is4g = s.contains("LTE") || s.contains("4G")
        val is5g = s.contains("NR") || s.contains("5G")

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

    private fun addBand(masks: StationMasks, bandBit: Int) {
        masks.bandMask = masks.bandMask or bandBit
    }

    private fun overlaps(start: Double?, end: Double?, low: Double, high: Double): Boolean =
        AnfrParsing.rangeOverlaps(start, end, low, high)
}
