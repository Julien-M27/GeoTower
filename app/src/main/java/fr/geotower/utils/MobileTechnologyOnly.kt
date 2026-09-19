package fr.geotower.utils

import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.RadioFilterMasks

/** Restriction to sites carrying exactly one mobile technology. */
enum class MobileTechnologyOnly(
    val preferenceValue: String,
    private val technologyBit: Int?
) {
    NONE("none", null),
    TWO_G("2g", RadioFilterMasks.TECH_2G),
    THREE_G("3g", RadioFilterMasks.TECH_3G),
    FOUR_G("4g", RadioFilterMasks.TECH_4G),
    FIVE_G("5g", RadioFilterMasks.TECH_5G);

    fun matches(site: LocalisationEntity): Boolean {
        val bit = technologyBit ?: return true
        val mobileMask = site.techMask and MOBILE_TECHNOLOGY_MASK
        return mobileMask == bit
    }

    companion object {
        private val MOBILE_TECHNOLOGY_MASK =
            RadioFilterMasks.TECH_2G or
                RadioFilterMasks.TECH_3G or
                RadioFilterMasks.TECH_4G or
                RadioFilterMasks.TECH_5G

        fun fromPreferenceValue(value: String?): MobileTechnologyOnly =
            entries.firstOrNull { it.preferenceValue == value?.lowercase() } ?: NONE
    }
}
