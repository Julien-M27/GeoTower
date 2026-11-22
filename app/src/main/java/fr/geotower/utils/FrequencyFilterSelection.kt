package fr.geotower.utils

import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.RadioFilterMasks

data class FrequencyFilterSelection(
    val show2G: Boolean,
    val show3G: Boolean,
    val show4G: Boolean,
    val show5G: Boolean,
    val showFh: Boolean,
    val f2G900: Boolean,
    val f2G1800: Boolean,
    val f3G900: Boolean,
    val f3G2100: Boolean,
    val f4G700: Boolean,
    val f4G800: Boolean,
    val f4G900: Boolean,
    val f4G1800: Boolean,
    val f4G2100: Boolean,
    val f4G2600: Boolean,
    val f5G700: Boolean,
    val f5G1400: Boolean,
    val f5G2100: Boolean,
    val f5G3500: Boolean,
    val f5G4200: Boolean,
    val f5G26000: Boolean,
    val mobileTechnologyOnly: MobileTechnologyOnly = MobileTechnologyOnly.NONE,
    val f3G1900: Boolean = true,
    val f4G600: Boolean = true,
    val f4G2300: Boolean = true,
    val f4G3500: Boolean = true,
    val f5G600: Boolean = true,
    val f5G850: Boolean = true,
    val f5G1900: Boolean = true,
    val f5G2000: Boolean = true,
    val f5G2600: Boolean = true,
    val f5G3800: Boolean = true
) {
    val isFullyEnabled: Boolean
        get() = mobileTechnologyOnly == MobileTechnologyOnly.NONE &&
            show2G && show3G && show4G && show5G && showFh &&
            allMobileBandsEnabled

    private val allMobileBandsEnabled: Boolean
        get() =
            f2G900 && f2G1800 &&
            f3G900 && f3G1900 && f3G2100 &&
            f4G600 && f4G700 && f4G800 && f4G900 && f4G1800 && f4G2100 && f4G2300 && f4G2600 && f4G3500 &&
            f5G600 && f5G700 && f5G850 && f5G1400 && f5G1900 && f5G2000 && f5G2100 && f5G2600 && f5G3500 && f5G3800 && f5G4200 && f5G26000

    fun matchesAntenna(antenna: LocalisationEntity): Boolean {
        if (!mobileTechnologyOnly.matches(antenna)) return false

        val bandMask = antenna.bandMask
        val hasFh = bandMask has RadioFilterMasks.BAND_FH || !antenna.azimutsFh.isNullOrBlank()
        val hasKnownRadio = bandMask != 0 || hasFh

        if (!hasKnownRadio) {
            return isFullyEnabled
        }

        return (show2G && (
            (f2G900 && bandMask has RadioFilterMasks.BAND_2G_900) ||
                (f2G1800 && bandMask has RadioFilterMasks.BAND_2G_1800)
            )) ||
            (show3G && (
                (f3G900 && bandMask has RadioFilterMasks.BAND_3G_900) ||
                    (f3G1900 && bandMask has RadioFilterMasks.BAND_3G_1900) ||
                    (f3G2100 && bandMask has RadioFilterMasks.BAND_3G_2100)
                )) ||
            (show4G && (
                    (f4G600 && bandMask has RadioFilterMasks.BAND_4G_600) ||
                    (f4G700 && bandMask has RadioFilterMasks.BAND_4G_700) ||
                    (f4G800 && bandMask has RadioFilterMasks.BAND_4G_800) ||
                    (f4G900 && bandMask has RadioFilterMasks.BAND_4G_900) ||
                    (f4G1800 && bandMask has RadioFilterMasks.BAND_4G_1800) ||
                    (f4G2100 && bandMask has RadioFilterMasks.BAND_4G_2100) ||
                    (f4G2300 && bandMask has RadioFilterMasks.BAND_4G_2300) ||
                    (f4G2600 && bandMask has RadioFilterMasks.BAND_4G_2600) ||
                    (f4G3500 && bandMask has RadioFilterMasks.BAND_4G_3500)
                )) ||
            (show5G && (
                (f5G600 && bandMask has RadioFilterMasks.BAND_5G_600) ||
                    (f5G700 && bandMask has RadioFilterMasks.BAND_5G_700) ||
                    (f5G850 && bandMask has RadioFilterMasks.BAND_5G_850) ||
                    (f5G1400 && bandMask has RadioFilterMasks.BAND_5G_1400) ||
                    (f5G1900 && bandMask has RadioFilterMasks.BAND_5G_1900) ||
                    (f5G2000 && bandMask has RadioFilterMasks.BAND_5G_2000) ||
                    (f5G2100 && bandMask has RadioFilterMasks.BAND_5G_2100) ||
                    (f5G2600 && bandMask has RadioFilterMasks.BAND_5G_2600) ||
                    (f5G3500 && bandMask has RadioFilterMasks.BAND_5G_3500) ||
                    (f5G3800 && bandMask has RadioFilterMasks.BAND_5G_3800) ||
                    (f5G4200 && bandMask has RadioFilterMasks.BAND_5G_4200) ||
                    (f5G26000 && bandMask has RadioFilterMasks.BAND_5G_26000)
                )) ||
            (showFh && allMobileBandsEnabled && hasFh)
    }

    fun matchesBand(gen: Int, value: Int): Boolean {
        return when (gen) {
            5 -> show5G && when (value) {
                600 -> f5G600
                700 -> f5G700
                850 -> f5G850
                1400 -> f5G1400
                1900 -> f5G1900
                2000, 2010 -> f5G2000
                2100 -> f5G2100
                2600 -> f5G2600
                3500 -> f5G3500
                3800 -> f5G3800
                4200 -> f5G4200
                26000 -> f5G26000
                else -> f5G600 || f5G700 || f5G850 || f5G1400 || f5G1900 || f5G2000 || f5G2100 || f5G2600 || f5G3500 || f5G3800 || f5G4200 || f5G26000
            }
            4 -> show4G && when (value) {
                600 -> f4G600
                700 -> f4G700
                800, 850 -> f4G800
                900 -> f4G900
                1800, 1900 -> f4G1800
                2100 -> f4G2100
                2300 -> f4G2300
                2600 -> f4G2600
                3500 -> f4G3500
                else -> f4G600 || f4G700 || f4G800 || f4G900 || f4G1800 || f4G2100 || f4G2300 || f4G2600 || f4G3500
            }
            3 -> show3G && when (value) {
                850, 900 -> f3G900
                1900 -> f3G1900
                2100 -> f3G2100
                else -> f3G900 || f3G1900 || f3G2100
            }
            2 -> show2G && when (value) {
                850, 900 -> f2G900
                1800, 1900 -> f2G1800
                else -> f2G900 || f2G1800
            }
            else -> showFh && allMobileBandsEnabled
        }
    }

    fun detailBackedBandMaskForEnrichment(): Int {
        if (isFullyEnabled || !show5G) return 0

        var mask = 0
        if (f5G1400) mask = mask or RadioFilterMasks.BAND_5G_1400
        if (f5G4200) mask = mask or RadioFilterMasks.BAND_5G_4200
        if (f5G26000) mask = mask or RadioFilterMasks.BAND_5G_26000
        return mask
    }

    fun selectedMobileBandMask(): Int {
        var mask = 0
        if (show2G) {
            if (f2G900) mask = mask or RadioFilterMasks.BAND_2G_900
            if (f2G1800) mask = mask or RadioFilterMasks.BAND_2G_1800
        }
        if (show3G) {
            if (f3G900) mask = mask or RadioFilterMasks.BAND_3G_900
            if (f3G1900) mask = mask or RadioFilterMasks.BAND_3G_1900
            if (f3G2100) mask = mask or RadioFilterMasks.BAND_3G_2100
        }
        if (show4G) {
            if (f4G600) mask = mask or RadioFilterMasks.BAND_4G_600
            if (f4G700) mask = mask or RadioFilterMasks.BAND_4G_700
            if (f4G800) mask = mask or RadioFilterMasks.BAND_4G_800
            if (f4G900) mask = mask or RadioFilterMasks.BAND_4G_900
            if (f4G1800) mask = mask or RadioFilterMasks.BAND_4G_1800
            if (f4G2100) mask = mask or RadioFilterMasks.BAND_4G_2100
            if (f4G2300) mask = mask or RadioFilterMasks.BAND_4G_2300
            if (f4G2600) mask = mask or RadioFilterMasks.BAND_4G_2600
            if (f4G3500) mask = mask or RadioFilterMasks.BAND_4G_3500
        }
        if (show5G) {
            if (f5G600) mask = mask or RadioFilterMasks.BAND_5G_600
            if (f5G700) mask = mask or RadioFilterMasks.BAND_5G_700
            if (f5G850) mask = mask or RadioFilterMasks.BAND_5G_850
            if (f5G1400) mask = mask or RadioFilterMasks.BAND_5G_1400
            if (f5G1900) mask = mask or RadioFilterMasks.BAND_5G_1900
            if (f5G2000) mask = mask or RadioFilterMasks.BAND_5G_2000
            if (f5G2100) mask = mask or RadioFilterMasks.BAND_5G_2100
            if (f5G2600) mask = mask or RadioFilterMasks.BAND_5G_2600
            if (f5G3500) mask = mask or RadioFilterMasks.BAND_5G_3500
            if (f5G3800) mask = mask or RadioFilterMasks.BAND_5G_3800
            if (f5G4200) mask = mask or RadioFilterMasks.BAND_5G_4200
            if (f5G26000) mask = mask or RadioFilterMasks.BAND_5G_26000
        }
        return mask
    }

    fun includesFh(): Boolean = showFh && allMobileBandsEnabled

    private infix fun Int.has(bit: Int): Boolean = (this and bit) != 0

    companion object {
        fun fromMapConfig(): FrequencyFilterSelection {
            return FrequencyFilterSelection(
                show2G = AppConfig.showTechno2G.value,
                show3G = AppConfig.showTechno3G.value,
                show4G = AppConfig.showTechno4G.value,
                show5G = AppConfig.showTechno5G.value,
                showFh = AppConfig.showTechnoFH.value,
                f2G900 = AppConfig.f2G_900.value,
                f2G1800 = AppConfig.f2G_1800.value,
                f3G900 = AppConfig.f3G_900.value,
                f3G1900 = AppConfig.f3G_1900.value,
                f3G2100 = AppConfig.f3G_2100.value,
                f4G600 = AppConfig.f4G_600.value,
                f4G700 = AppConfig.f4G_700.value,
                f4G800 = AppConfig.f4G_800.value,
                f4G900 = AppConfig.f4G_900.value,
                f4G1800 = AppConfig.f4G_1800.value,
                f4G2100 = AppConfig.f4G_2100.value,
                f4G2300 = AppConfig.f4G_2300.value,
                f4G2600 = AppConfig.f4G_2600.value,
                f4G3500 = AppConfig.f4G_3500.value,
                f5G600 = AppConfig.f5G_600.value,
                f5G700 = AppConfig.f5G_700.value,
                f5G850 = AppConfig.f5G_850.value,
                f5G1400 = AppConfig.f5G_1400.value,
                f5G1900 = AppConfig.f5G_1900.value,
                f5G2000 = AppConfig.f5G_2000.value,
                f5G2100 = AppConfig.f5G_2100.value,
                f5G2600 = AppConfig.f5G_2600.value,
                f5G3500 = AppConfig.f5G_3500.value,
                f5G3800 = AppConfig.f5G_3800.value,
                f5G4200 = AppConfig.f5G_4200.value,
                f5G26000 = AppConfig.f5G_26000.value,
                mobileTechnologyOnly = AppConfig.mobileTechnologyOnly.value
            )
        }
    }
}
