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
    val f5G2100: Boolean,
    val f5G3500: Boolean,
    val f5G26000: Boolean
) {
    val isFullyEnabled: Boolean
        get() = show2G && show3G && show4G && show5G && showFh &&
            allMobileBandsEnabled

    private val allMobileBandsEnabled: Boolean
        get() =
            f2G900 && f2G1800 &&
            f3G900 && f3G2100 &&
            f4G700 && f4G800 && f4G900 && f4G1800 && f4G2100 && f4G2600 &&
            f5G700 && f5G2100 && f5G3500 && f5G26000

    fun matchesAntenna(antenna: LocalisationEntity): Boolean {
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
                    (f3G2100 && bandMask has RadioFilterMasks.BAND_3G_2100)
                )) ||
            (show4G && (
                (f4G700 && bandMask has RadioFilterMasks.BAND_4G_700) ||
                    (f4G800 && bandMask has RadioFilterMasks.BAND_4G_800) ||
                    (f4G900 && bandMask has RadioFilterMasks.BAND_4G_900) ||
                    (f4G1800 && bandMask has RadioFilterMasks.BAND_4G_1800) ||
                    (f4G2100 && bandMask has RadioFilterMasks.BAND_4G_2100) ||
                    (f4G2600 && bandMask has RadioFilterMasks.BAND_4G_2600)
                )) ||
            (show5G && (
                (f5G700 && bandMask has RadioFilterMasks.BAND_5G_700) ||
                    (f5G2100 && bandMask has RadioFilterMasks.BAND_5G_2100) ||
                    (f5G3500 && bandMask has RadioFilterMasks.BAND_5G_3500) ||
                    (f5G26000 && bandMask has RadioFilterMasks.BAND_5G_26000)
                )) ||
            (showFh && allMobileBandsEnabled && hasFh)
    }

    fun matchesBand(gen: Int, value: Int): Boolean {
        return when (gen) {
            5 -> show5G && when (value) {
                700 -> f5G700
                2100 -> f5G2100
                3500 -> f5G3500
                26000 -> f5G26000
                else -> f5G700 || f5G2100 || f5G3500 || f5G26000
            }
            4 -> show4G && when (value) {
                700 -> f4G700
                800 -> f4G800
                900 -> f4G900
                1800 -> f4G1800
                2100 -> f4G2100
                2600 -> f4G2600
                else -> f4G700 || f4G800 || f4G900 || f4G1800 || f4G2100 || f4G2600
            }
            3 -> show3G && when (value) {
                900 -> f3G900
                2100 -> f3G2100
                else -> f3G900 || f3G2100
            }
            2 -> show2G && when (value) {
                900 -> f2G900
                1800 -> f2G1800
                else -> f2G900 || f2G1800
            }
            else -> showFh && allMobileBandsEnabled
        }
    }

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
                f3G2100 = AppConfig.f3G_2100.value,
                f4G700 = AppConfig.f4G_700.value,
                f4G800 = AppConfig.f4G_800.value,
                f4G900 = AppConfig.f4G_900.value,
                f4G1800 = AppConfig.f4G_1800.value,
                f4G2100 = AppConfig.f4G_2100.value,
                f4G2600 = AppConfig.f4G_2600.value,
                f5G700 = AppConfig.f5G_700.value,
                f5G2100 = AppConfig.f5G_2100.value,
                f5G3500 = AppConfig.f5G_3500.value,
                f5G26000 = AppConfig.f5G_26000.value
            )
        }
    }
}
