package fr.geotower.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateCheckerTest {
    @Test
    fun parseRelease_acceptsValidManifest() {
        val release = AppUpdateChecker.parseRelease(
            """
            {
              "schemaVersion": 1,
              "releaseId": "geotower-1.9.9.2.4-20260513-1608",
              "versionName": "1.9.9.2.4",
              "publishedAt": "2026-05-13T16:08:00+02:00",
              "downloadUrl": "https://kdrive.infomaniak.com/app/share/2149816/6d30423f-ac8b-4509-9857-86684d3a2e03",
              "fileName": "GeoTower_v1.9.9.2.4.apk",
              "notes": "Corrections et ameliorations.",
              "notesTranslations": {
                "fr": "Corrections et ameliorations FR.",
                "en": "Fixes and improvements.",
                "pt-BR": "Correcoes e melhorias."
              }
            }
            """.trimIndent()
        )

        assertNotNull(release)
        assertEquals("geotower-1.9.9.2.4-20260513-1608", release?.releaseId)
        assertEquals("1.9.9.2.4", release?.versionName)
        assertEquals("GeoTower_v1.9.9.2.4.apk", release?.fileName)
        assertEquals("Corrections et ameliorations FR.", release?.localizedNotes("fr-FR"))
        assertEquals("Fixes and improvements.", release?.localizedNotes("en-US"))
        assertEquals("Correcoes e melhorias.", release?.localizedNotes("pt-BR"))
        assertEquals("Corrections et ameliorations FR.", release?.localizedNotes("de-DE"))
    }

    @Test
    fun parseRelease_rejectsMissingWebDownloadUrl() {
        val release = AppUpdateChecker.parseRelease(
            """
            {
              "releaseId": "geotower-1.9.9.2.4-20260513-1608",
              "versionName": "1.9.9.2.4",
              "downloadUrl": "GeoTower_v1.9.9.2.4.apk"
            }
            """.trimIndent()
        )

        assertNull(release)
    }

    @Test
    fun parseRelease_rejectsHttpDownloadUrl() {
        val release = AppUpdateChecker.parseRelease(
            """
            {
              "releaseId": "geotower-1.9.9.2.4-20260513-1608",
              "versionName": "1.9.9.2.4",
              "downloadUrl": "http://kdrive.infomaniak.com/app/share/2149816/6d30423f-ac8b-4509-9857-86684d3a2e03"
            }
            """.trimIndent()
        )

        assertNull(release)
    }

    @Test
    fun parseRelease_rejectsNonOfficialDownloadDomain() {
        val release = AppUpdateChecker.parseRelease(
            """
            {
              "releaseId": "geotower-1.9.9.2.4-20260513-1608",
              "versionName": "1.9.9.2.4",
              "downloadUrl": "https://example.com/GeoTower.apk"
            }
            """.trimIndent()
        )

        assertNull(release)
    }

    @Test
    fun isRemoteVersionNewer_acceptsOnlyStrictlyNewerVersions() {
        assertEquals(true, AppUpdateChecker.isRemoteVersionNewer("1.9.9.2.5", "1.9.9.2.4"))
        assertEquals(false, AppUpdateChecker.isRemoteVersionNewer("1.9.9.2.4", "1.9.9.2.4"))
        assertEquals(false, AppUpdateChecker.isRemoteVersionNewer("1.9.9.2.3", "1.9.9.2.4"))
    }
}
