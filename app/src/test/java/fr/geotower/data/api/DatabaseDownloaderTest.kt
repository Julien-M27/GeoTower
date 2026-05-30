package fr.geotower.data.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseDownloaderTest {
    @Test
    fun remoteDatabaseInfoRequiresSizeHashAndExpectedIdentity() {
        assertTrue(validInfo())

        assertFalse(validInfo(sha256 = ""))
        assertFalse(validInfo(sha256 = "abc"))
        assertFalse(validInfo(sizeBytes = 0L))
        assertFalse(validInfo(filename = "other.db"))
        assertFalse(validInfo(schemaVersion = 999))
        assertFalse(validInfo(countryCode = "US"))
    }

    @Test
    fun officialDatabaseDownloadUrlRequiresHttpsOfficialHostAndPath() {
        assertTrue(DatabaseDownloader.isOfficialDatabaseDownloadUrl("https://api.cajejuma.fr/api/v2/download/db"))
        assertFalse(DatabaseDownloader.isOfficialDatabaseDownloadUrl("http://api.cajejuma.fr/api/v2/download/db"))
        assertFalse(DatabaseDownloader.isOfficialDatabaseDownloadUrl("https://example.com/api/v2/download/db"))
        assertFalse(DatabaseDownloader.isOfficialDatabaseDownloadUrl("https://user:pass@api.cajejuma.fr/api/v2/download/db"))
        assertFalse(DatabaseDownloader.isOfficialDatabaseDownloadUrl("https://api.cajejuma.fr/api/v2/download/other"))
    }

    private fun validInfo(
        filename: String = "geotower_fr.db",
        sizeBytes: Long = 1024L,
        sha256: String = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        schemaVersion: Int? = 7,
        countryCode: String? = "FR"
    ): Boolean {
        return DatabaseDownloader.isValidRemoteDatabaseInfo(
            filename = filename,
            sizeBytes = sizeBytes,
            sha256 = sha256,
            schemaVersion = schemaVersion,
            countryCode = countryCode
        )
    }
}
