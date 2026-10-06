package fr.geotower.utils

import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.RadioFilterMasks
import fr.geotower.data.models.RadioMapCategoryMasks
import fr.geotower.data.models.SiteHsEntity
import fr.geotower.ui.components.RadioUsageKind
import fr.geotower.ui.components.radioCategoryMaskForKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteSupportFilterEffectsTest {

    private fun fullyEnabledFilter() = FrequencyFilterSelection(
        show2G = true,
        show3G = true,
        show4G = true,
        show5G = true,
        showFh = true,
        f2G900 = true,
        f2G1800 = true,
        f3G900 = true,
        f3G2100 = true,
        f4G700 = true,
        f4G800 = true,
        f4G900 = true,
        f4G1800 = true,
        f4G2100 = true,
        f4G2600 = true,
        f5G700 = true,
        f5G1400 = true,
        f5G2100 = true,
        f5G3500 = true,
        f5G4200 = true,
        f5G26000 = true
    )

    private fun antenna(
        idAnfr: String,
        operateur: String,
        bandMask: Int = RadioFilterMasks.BAND_4G_800,
        isZb: Int = 0,
        hasUndergroundSupport: Int = 0,
        hasActive: Int = 1,
        statut: String? = "En service",
        lat: Double = 48.85,
        lon: Double = 2.35
    ) = LocalisationEntity(
        idAnfr = idAnfr,
        operateur = operateur,
        latitude = lat,
        longitude = lon,
        azimuts = "0,120,240",
        codeInsee = null,
        azimutsFh = null,
        bandMask = bandMask,
        isZb = isZb,
        hasUndergroundSupport = hasUndergroundSupport,
        hasActive = hasActive,
        statut = statut
    )

    private fun declaredHs(idAnfr: String, operateur: String) = SiteHsEntity(
        idAnfr = idAnfr,
        operateur = operateur,
        latitude = 48.85,
        longitude = 2.35
    )

    @Test
    fun noFilterActiveReturnsNull() {
        val antennas = listOf(
            antenna("1001", "Orange"),
            antenna("1002", "Free"),
            antenna("1003", "SFR")
        )

        val result = resolveActiveOperatorKeys(
            context = null,
            antennas = antennas,
            sitesHs = emptyList(),
            selectedOperatorKeys = OperatorColors.defaultVisibleKeys,
            frequencyFilter = fullyEnabledFilter(),
            showSitesInService = true,
            showSitesOutOfService = true,
            showProjectSites = true,
            hideUndergroundSites = false,
            showOnlyZbSites = false
        )

        assertNull("Quand aucun filtre n'est actif, la fonction doit renvoyer null pour ne rien griser", result)
    }

    @Test
    fun operatorFilterActiveFiltersAnnexOperators() {
        val antennas = listOf(
            antenna("1001", "Orange"),
            antenna("1002", "Free mobile"),
            antenna("1003", "SFR")
        )

        val result = resolveActiveOperatorKeys(
            context = null,
            antennas = antennas,
            sitesHs = emptyList(),
            selectedOperatorKeys = setOf(OperatorColors.FREE_KEY),
            frequencyFilter = fullyEnabledFilter(),
            showSitesInService = true,
            showSitesOutOfService = true,
            showProjectSites = true,
            hideUndergroundSites = false,
            showOnlyZbSites = false
        )

        assertEquals(setOf(OperatorColors.FREE_KEY), result)
    }

    @Test
    fun frequencyFilterActiveFiltersAnnexOperators() {
        val antennas = listOf(
            antenna("1001", "Orange", bandMask = RadioFilterMasks.BAND_4G_800),
            antenna("1002", "Free mobile", bandMask = RadioFilterMasks.BAND_5G_3500)
        )

        val filter5gOnly = fullyEnabledFilter().copy(
            show2G = false,
            show3G = false,
            show4G = false,
            show5G = true
        )

        val result = resolveActiveOperatorKeys(
            context = null,
            antennas = antennas,
            sitesHs = emptyList(),
            selectedOperatorKeys = OperatorColors.defaultVisibleKeys,
            frequencyFilter = filter5gOnly,
            showSitesInService = true,
            showSitesOutOfService = true,
            showProjectSites = true,
            hideUndergroundSites = false,
            showOnlyZbSites = false
        )

        assertEquals(setOf(OperatorColors.FREE_KEY), result)
    }

    @Test
    fun siteStatusFilterActiveFiltersAnnexOperators() {
        val antennas = listOf(
            antenna("1001", "Orange", hasActive = 1, statut = "En service"),
            antenna("1002", "Free mobile", hasActive = 1, statut = "En service")
        )
        val outages = listOf(declaredHs("1001", "Orange"))

        // Filtre : uniquement les sites hors service
        val result = resolveActiveOperatorKeys(
            context = null,
            antennas = antennas,
            sitesHs = outages,
            selectedOperatorKeys = OperatorColors.defaultVisibleKeys,
            frequencyFilter = fullyEnabledFilter(),
            showSitesInService = false,
            showSitesOutOfService = true,
            showProjectSites = false,
            hideUndergroundSites = false,
            showOnlyZbSites = false
        )

        assertEquals(setOf(OperatorColors.ORANGE_KEY), result)
    }

    @Test
    fun undergroundFilterExcludesUndergroundAntennas() {
        val antennas = listOf(
            antenna("1001", "Orange", hasUndergroundSupport = 1),
            antenna("1002", "Free mobile", hasUndergroundSupport = 0)
        )

        val result = resolveActiveOperatorKeys(
            context = null,
            antennas = antennas,
            sitesHs = emptyList(),
            selectedOperatorKeys = OperatorColors.defaultVisibleKeys,
            frequencyFilter = fullyEnabledFilter(),
            showSitesInService = true,
            showSitesOutOfService = true,
            showProjectSites = true,
            hideUndergroundSites = true,
            showOnlyZbSites = false
        )

        assertEquals(setOf(OperatorColors.FREE_KEY), result)
    }

    @Test
    fun whiteZoneFilterExcludesNonZbAntennas() {
        val antennas = listOf(
            antenna("1001", "Orange", isZb = 0),
            antenna("1002", "Bouygues Telecom", isZb = 1)
        )

        val result = resolveActiveOperatorKeys(
            context = null,
            antennas = antennas,
            sitesHs = emptyList(),
            selectedOperatorKeys = OperatorColors.defaultVisibleKeys,
            frequencyFilter = fullyEnabledFilter(),
            showSitesInService = true,
            showSitesOutOfService = true,
            showProjectSites = true,
            hideUndergroundSites = false,
            showOnlyZbSites = true
        )

        assertEquals(setOf(OperatorColors.BOUYGUES_KEY), result)
    }

    @Test
    fun allOperatorsFilteredReturnsEmptySet() {
        val antennas = listOf(
            antenna("1001", "Orange"),
            antenna("1002", "SFR")
        )

        // Filtre : Free uniquement
        val result = resolveActiveOperatorKeys(
            context = null,
            antennas = antennas,
            sitesHs = emptyList(),
            selectedOperatorKeys = setOf(OperatorColors.FREE_KEY),
            frequencyFilter = fullyEnabledFilter(),
            showSitesInService = true,
            showSitesOutOfService = true,
            showProjectSites = true,
            hideUndergroundSites = false,
            showOnlyZbSites = false
        )

        assertEquals(emptySet<String>(), result)
    }

    @Test
    fun applyFiltersFalseReturnsNull() {
        val antennas = listOf(
            antenna("1001", "Orange"),
            antenna("1002", "SFR")
        )

        val result = resolveActiveOperatorKeys(
            context = null,
            antennas = antennas,
            sitesHs = emptyList(),
            selectedOperatorKeys = setOf(OperatorColors.FREE_KEY),
            applyFilters = false
        )

        assertNull(result)
    }

    @Test
    fun radioCategoryMaskForKindMapsCorrectly() {
        assertEquals(RadioMapCategoryMasks.TV, radioCategoryMaskForKind(RadioUsageKind.Tv))
        assertEquals(RadioMapCategoryMasks.RADIO, radioCategoryMaskForKind(RadioUsageKind.Radio))
        assertEquals(RadioMapCategoryMasks.PRIVATE_MOBILE, radioCategoryMaskForKind(RadioUsageKind.PrivateMobile))
        assertEquals(RadioMapCategoryMasks.FH, radioCategoryMaskForKind(RadioUsageKind.Fh))
        assertEquals(RadioMapCategoryMasks.OTHER, radioCategoryMaskForKind(RadioUsageKind.Other))
    }
}
