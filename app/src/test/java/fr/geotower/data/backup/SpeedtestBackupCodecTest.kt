package fr.geotower.data.backup

import fr.geotower.data.speedtest.SpeedtestEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SpeedtestBackupCodecTest {

    @Test
    fun speedtestRoundTripPreservesAllFields() {
        val original = SpeedtestEntryEntity(
            id = "st-1",
            imageFileName = "st_1.jpg",
            testedAtMillis = 1700000000000L,
            createdAtMillis = 1700000001000L,
            downloadMbps = 312.4,
            uploadMbps = 45.8,
            pingMs = 19.0,
            jitterMs = 2.5,
            operatorName = "Bouygues Telecom",
            networkTech = "5G",
            band = "n78",
            cellIdentifier = "123456",
            locationLabel = "Lyon Part-Dieu",
            latitude = 45.7604,
            longitude = 4.8594,
            linkedSiteId = "SITE-42",
            note = "Test extérieur",
            ocrRawText = "312.4 Mbps"
        )

        val json = AppBackupCodec.speedtestToJson(original, imageBase64 = "AQID")
        val decoded = AppBackupCodec.speedtestFromJson(json)
        assertNotNull(decoded)
        assertEquals(original, decoded)
        assertEquals("AQID", AppBackupCodec.speedtestImageBase64(json))
    }
}
