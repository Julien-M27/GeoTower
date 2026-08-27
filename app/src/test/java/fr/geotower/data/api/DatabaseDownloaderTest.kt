package fr.geotower.data.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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
        assertTrue(DatabaseDownloader.isOfficialDatabaseDownloadUrl("https://api.geotower.fr/api/v2/download/db"))
        assertFalse(DatabaseDownloader.isOfficialDatabaseDownloadUrl("http://api.geotower.fr/api/v2/download/db"))
        assertFalse(DatabaseDownloader.isOfficialDatabaseDownloadUrl("https://example.com/api/v2/download/db"))
        assertFalse(DatabaseDownloader.isOfficialDatabaseDownloadUrl("https://user:pass@api.geotower.fr/api/v2/download/db"))
        assertFalse(DatabaseDownloader.isOfficialDatabaseDownloadUrl("https://api.geotower.fr/api/v2/download/other"))
    }

    @Test
    fun newerWeeklyVersionSelectsTheMirror() {
        val primary = served("api.geotower.fr", "20260827_1200")
        val mirror = served("api.cajejuma.fr", "20260828_0800")

        assertSame(mirror, DatabaseDownloader.selectPreferredDatabase(primary, mirror))
    }

    @Test
    fun equalWeeklyVersionKeepsThePrimaryEvenWhenMirrorPublicationDiffers() {
        val primary = served("api.geotower.fr", "20260828_0800", sha256 = "a".repeat(64))
        val mirror = served("api.cajejuma.fr", "20260828_0800", sha256 = "b".repeat(64))

        assertSame(primary, DatabaseDownloader.selectPreferredDatabase(primary, mirror))
    }

    @Test
    fun olderMirrorVersionKeepsThePrimary() {
        val primary = served("api.geotower.fr", "20260828_0800")
        val mirror = served("api.cajejuma.fr", "20260827_1200")

        assertSame(primary, DatabaseDownloader.selectPreferredDatabase(primary, mirror))
    }

    @Test
    fun unavailablePrimaryFallsBackToMirror() {
        val mirror = served("api.cajejuma.fr", "20260828_0800")

        assertSame(mirror, DatabaseDownloader.selectPreferredDatabase(null, mirror))
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

    private fun served(host: String, version: String, sha256: String = "a".repeat(64)):
        ServedFrom<DownloadManifestDatabase> = ServedFrom(
            value = DownloadManifestDatabase(
                filename = "geotower_fr.db",
                url = "https://$host/api/v2/download/db",
                sizeBytes = 1024L,
                sha256 = sha256,
                schemaVersion = 7,
                countryCode = "FR",
                version = version
            ),
            host = host
        )
}
