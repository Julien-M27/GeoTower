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
        dateService: String? = null,
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
        statut = statut,
        dateService = dateService
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

    @Test
    fun passesSiteDisplayFiltersRespectsOperatorSelection() {
        val orangeAntenna = antenna("1001", "Orange")
        val freeAntenna = antenna("1002", "Free mobile")
        val hsMap = emptyMap<String, Set<String>>()

        val selected = setOf(OperatorColors.ORANGE_KEY)

        assertEquals(
            true,
            passesSiteDisplayFilters(
                antenna = orangeAntenna,
                hsOperatorMap = hsMap,
                selectedOperatorKeys = selected,
                showSitesInService = true,
                showSitesOutOfService = true,
                showProjectSites = true,
                hideUndergroundSites = false,
                showOnlyZbSites = false,
                frequencyFilter = fullyEnabledFilter()
            )
        )

        assertEquals(
            false,
            passesSiteDisplayFilters(
                antenna = freeAntenna,
                hsOperatorMap = hsMap,
                selectedOperatorKeys = selected,
                showSitesInService = true,
                showSitesOutOfService = true,
                showProjectSites = true,
                hideUndergroundSites = false,
                showOnlyZbSites = false,
                frequencyFilter = fullyEnabledFilter()
            )
        )
    }

    @Test
    fun passesSiteDisplayFiltersRespectsProjectStatus() {
        val projectAntenna = antenna("1001", "Orange", hasActive = 0, statut = "En projet")
        val activeAntenna = antenna("1002", "Orange", hasActive = 1, statut = "En service")
        val hsMap = emptyMap<String, Set<String>>()

        assertEquals(
            false,
            passesSiteDisplayFilters(
                antenna = projectAntenna,
                hsOperatorMap = hsMap,
                selectedOperatorKeys = OperatorColors.defaultVisibleKeys,
                showSitesInService = true,
                showSitesOutOfService = true,
                showProjectSites = false,
                hideUndergroundSites = false,
                showOnlyZbSites = false,
                frequencyFilter = fullyEnabledFilter()
            )
        )

        assertEquals(
            true,
            passesSiteDisplayFilters(
                antenna = activeAntenna,
                hsOperatorMap = hsMap,
                selectedOperatorKeys = OperatorColors.defaultVisibleKeys,
                showSitesInService = true,
                showSitesOutOfService = true,
                showProjectSites = false,
                hideUndergroundSites = false,
                showOnlyZbSites = false,
                frequencyFilter = fullyEnabledFilter()
            )
        )
    }

    @Test
    fun passesSiteDisplayFiltersRespectsOutOfServiceStatus() {
        val hsSite = antenna("1001", "Orange")
        val normalSite = antenna("1002", "Orange")
        val hsMap = buildHsOperatorMap(listOf(declaredHs("1001", "Orange")))

        assertEquals(
            false,
            passesSiteDisplayFilters(
                antenna = hsSite,
                hsOperatorMap = hsMap,
                selectedOperatorKeys = OperatorColors.defaultVisibleKeys,
                showSitesInService = true,
                showSitesOutOfService = false,
                showProjectSites = true,
                hideUndergroundSites = false,
                showOnlyZbSites = false,
                frequencyFilter = fullyEnabledFilter()
            )
        )

        assertEquals(
            true,
            passesSiteDisplayFilters(
                antenna = normalSite,
                hsOperatorMap = hsMap,
                selectedOperatorKeys = OperatorColors.defaultVisibleKeys,
                showSitesInService = true,
                showSitesOutOfService = false,
                showProjectSites = true,
                hideUndergroundSites = false,
                showOnlyZbSites = false,
                frequencyFilter = fullyEnabledFilter()
            )
        )
    }

    @Test
    fun parseServiceDateIntParsesCorrectly() {
        assertEquals(20120601, parseServiceDateInt("01/06/2012"))
        assertEquals(20120601, parseServiceDateInt("2012-06-01"))
        assertEquals(20120601, parseServiceDateInt("01-06-2012"))
        assertEquals(20120601, parseServiceDateInt("2012-06-01 14:30:00"))
        assertNull(parseServiceDateInt(null))
        assertNull(parseServiceDateInt(""))
        assertNull(parseServiceDateInt("invalid"))
        assertNull(parseServiceDateInt("01/99/2012"))
    }

    @Test
    fun resolveActiveOperatorKeysWithTimeSlider() {
        val antennas = listOf(
            antenna("1001", "Orange", dateService = "01/01/2012"),
            antenna("1002", "Free mobile", dateService = "01/01/2020"),
            antenna("1003", "SFR", dateService = null)
        )

        // Curseurs avec seuil en 2015 : seul Orange (2012) est actif, Free (2020) et SFR (sans date) sont grisés
        val result2015 = resolveActiveOperatorKeys(
            context = null,
            antennas = antennas,
            sitesHs = emptyList(),
            selectedOperatorKeys = OperatorColors.defaultVisibleKeys,
            frequencyFilter = fullyEnabledFilter(),
            showSitesInService = true,
            showSitesOutOfService = true,
            showProjectSites = true,
            hideUndergroundSites = false,
            showOnlyZbSites = false,
            timeSliderActive = true,
            timeSliderThreshold = 20150101
        )

        assertEquals(
            setOf(OperatorColors.ORANGE_KEY),
            result2015
        )

        // Curseur avec seuil en 2021 : Orange (2012) et Free (2020) sont actifs
        val result2021 = resolveActiveOperatorKeys(
            context = null,
            antennas = antennas,
            sitesHs = emptyList(),
            selectedOperatorKeys = OperatorColors.defaultVisibleKeys,
            frequencyFilter = fullyEnabledFilter(),
            showSitesInService = true,
            showSitesOutOfService = true,
            showProjectSites = true,
            hideUndergroundSites = false,
            showOnlyZbSites = false,
            timeSliderActive = true,
            timeSliderThreshold = 20210101
        )

        assertEquals(
            setOf(OperatorColors.ORANGE_KEY, OperatorColors.FREE_KEY),
            result2021
        )

        // Curseur inactif : aucun filtre actif donc retourne null (rien de grisé)
        val resultInactive = resolveActiveOperatorKeys(
            context = null,
            antennas = antennas,
            sitesHs = emptyList(),
            selectedOperatorKeys = OperatorColors.defaultVisibleKeys,
            frequencyFilter = fullyEnabledFilter(),
            showSitesInService = true,
            showSitesOutOfService = true,
            showProjectSites = true,
            hideUndergroundSites = false,
            showOnlyZbSites = false,
            timeSliderActive = false,
            timeSliderThreshold = null
        )

        assertNull(resultInactive)
    }
}
