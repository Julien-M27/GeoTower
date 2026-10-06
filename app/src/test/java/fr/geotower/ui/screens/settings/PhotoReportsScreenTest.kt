package fr.geotower.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoReportsScreenTest {

    @Test
    fun formatSiteAddress_bothPresentAndDistinct_combinesWithComma() {
        val result = formatSiteAddress("12 RUE DES FLEURS", "PARIS")
        assertEquals("12 RUE DES FLEURS, PARIS", result)
    }

    @Test
    fun formatSiteAddress_addressAlreadyContainsCommune_doesNotDuplicate() {
        val result = formatSiteAddress("12 RUE DES FLEURS, 75001 PARIS", "PARIS")
        assertEquals("12 RUE DES FLEURS, 75001 PARIS", result)
    }

    @Test
    fun formatSiteAddress_onlyAddressPresent_returnsAddress() {
        val result = formatSiteAddress("LIEU-DIT LE MOULIN", null)
        assertEquals("LIEU-DIT LE MOULIN", result)
    }

    @Test
    fun formatSiteAddress_onlyCommunePresent_returnsCommune() {
        val result = formatSiteAddress("", "LYON")
        assertEquals("LYON", result)
    }

    @Test
    fun formatSiteAddress_bothNullOrBlank_returnsNull() {
        assertNull(formatSiteAddress(null, null))
        assertNull(formatSiteAddress("   ", "  "))
    }

    @Test
    fun resolvedPhotoReportSite_dataHolder_keepsProperties() {
        val site = ResolvedPhotoReportSite(
            siteId = "123456",
            isSupportId = true,
            isAnfrId = false,
            targetAnfrId = "987654",
            targetSupportId = "123456",
            address = "RUE DU STADE",
            commune = "MARSEILLE",
            latitude = 43.2965,
            longitude = 5.3698
        )
        assertEquals("123456", site.siteId)
        assertEquals(true, site.isSupportId)
        assertEquals(false, site.isAnfrId)
        assertEquals("987654", site.targetAnfrId)
        assertEquals("123456", site.targetSupportId)
        assertEquals("RUE DU STADE", site.address)
        assertEquals("MARSEILLE", site.commune)
        assertEquals(43.2965, site.latitude!!, 0.0001)
        assertEquals(5.3698, site.longitude!!, 0.0001)
    }
}
