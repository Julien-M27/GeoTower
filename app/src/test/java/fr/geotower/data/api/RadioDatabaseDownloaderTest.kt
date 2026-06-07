package fr.geotower.data.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioDatabaseDownloaderTest {
    @Test
    fun isValidRemoteRadioDatabaseInfo_rejectsInvalidMetadata() {
        assertFalse(validInfo(filename = "geotower_fr.db"))
        assertFalse(validInfo(sizeBytes = 0L))
        assertFalse(validInfo(sizeBytes = 2L * 1024L * 1024L * 1024L))
        assertFalse(validInfo(sha256 = "abc"))
        assertFalse(validInfo(schemaVersion = 2))
        assertFalse(validInfo(countryCode = "CA"))
    }

    @Test
    fun isValidRemoteRadioDatabaseInfo_acceptsExpectedMetadata() {
        assertTrue(validInfo())
        assertTrue(validInfo(schemaVersion = null, countryCode = null))
    }

    @Test
    fun isOfficialRadioDatabaseDownloadUrl_rejectsNonOfficialUrls() {
        assertTrue(RadioDatabaseDownloader.isOfficialRadioDatabaseDownloadUrl("https://api.cajejuma.fr/api/v2/download/radio_db"))
        assertFalse(RadioDatabaseDownloader.isOfficialRadioDatabaseDownloadUrl("http://api.cajejuma.fr/api/v2/download/radio_db"))
        assertFalse(RadioDatabaseDownloader.isOfficialRadioDatabaseDownloadUrl("https://evil.example/api/v2/download/radio_db"))
        assertFalse(RadioDatabaseDownloader.isOfficialRadioDatabaseDownloadUrl("https://api.cajejuma.fr/api/v2/download/db"))
        assertFalse(RadioDatabaseDownloader.isOfficialRadioDatabaseDownloadUrl("https://user:pass@api.cajejuma.fr/api/v2/download/radio_db"))
    }

    private fun validInfo(
        filename: String = "geotower_fr_radio.db",
        sizeBytes: Long = 42L * 1024L * 1024L,
        sha256: String = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        schemaVersion: Int? = 1,
        countryCode: String? = "FR"
    ): Boolean {
        return RadioDatabaseDownloader.isValidRemoteRadioDatabaseInfo(
            filename = filename,
            sizeBytes = sizeBytes,
            sha256 = sha256,
            schemaVersion = schemaVersion,
            countryCode = countryCode
        )
    }
}
