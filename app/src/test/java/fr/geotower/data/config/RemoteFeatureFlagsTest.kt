package fr.geotower.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteFeatureFlagsTest {
    @Test
    fun parseConfig_mergesRemoteOverridesWithDefaults() {
        val config = RemoteFeatureFlags.parseConfig(
            """
            {
              "schemaVersion": 1,
              "cacheTtlSeconds": 120,
              "screens": {
                "map": false
              },
              "menus": {
                "externalLinksSettings": false
              },
              "features": {
                "signalQuest.upload": false,
                "cellularFr.photos": true
              },
              "actions": {
                "share.site": false
              },
              "providers": {
                "search.nominatim": false
              },
              "workers": {
                "databaseDownload": false
              },
              "platform": {
                "widgets": false
              },
              "limits": {
                "nearbyMaxRadiusKm": 25
              },
              "homeAnnouncement": {
                "enabled": true,
                "id": "maintenance-2026-05-25",
                "title": "Maintenance",
                "message": "Intervention en cours",
                "severity": "warning",
                "actionLabel": "Details",
                "actionUrl": "https://api.cajejuma.fr/status",
                "dismissible": false,
                "minAppVersionInclusive": "1.9.9.0",
                "maxAppVersionExclusive": "2.0.0",
                "translations": {
                  "fr": {
                    "title": "Maintenance FR",
                    "message": "Intervention en cours FR",
                    "actionLabel": "Voir"
                  },
                  "en": {
                    "title": "Maintenance EN",
                    "message": "Maintenance in progress",
                    "actionLabel": "Open"
                  }
                }
              }
            }
            """.trimIndent()
        )

        requireNotNull(config)
        assertEquals(120L, config.cacheTtlSeconds)
        assertFalse(config.isScreenEnabled(RemoteFeatureFlags.Screens.MAP))
        assertTrue(config.isScreenEnabled(RemoteFeatureFlags.Screens.NEARBY))
        assertFalse(config.isMenuEnabled(RemoteFeatureFlags.Menus.EXTERNAL_LINKS_SETTINGS))
        assertFalse(config.isFeatureEnabled(RemoteFeatureFlags.Features.SIGNALQUEST_UPLOAD))
        assertTrue(config.isFeatureEnabled(RemoteFeatureFlags.Features.CELLULARFR_PHOTOS))
        assertFalse(config.isActionEnabled(RemoteFeatureFlags.Actions.SHARE_SITE))
        assertFalse(config.isProviderEnabled(RemoteFeatureFlags.Providers.SEARCH_NOMINATIM))
        assertFalse(config.isWorkerEnabled(RemoteFeatureFlags.Workers.DATABASE_DOWNLOAD))
        assertFalse(config.isPlatformEnabled(RemoteFeatureFlags.Platform.WIDGETS))
        assertEquals(25, config.limitOrDefault(RemoteFeatureFlags.Limits.NEARBY_MAX_RADIUS_KM, 50))
        assertTrue(config.homeAnnouncement.enabled)
        assertEquals("maintenance-2026-05-25", config.homeAnnouncement.id)
        assertEquals("Maintenance", config.homeAnnouncement.title)
        assertEquals("Intervention en cours", config.homeAnnouncement.message)
        assertEquals("warning", config.homeAnnouncement.severity)
        assertEquals("Details", config.homeAnnouncement.actionLabel)
        assertEquals("https://api.cajejuma.fr/status", config.homeAnnouncement.actionUrl)
        assertFalse(config.homeAnnouncement.dismissible)
        assertEquals("1.9.9.0", config.homeAnnouncement.minAppVersionInclusive)
        assertEquals("2.0.0", config.homeAnnouncement.maxAppVersionExclusive)
        assertEquals("Maintenance FR", config.homeAnnouncement.localizedText("fr-FR").title)
        assertEquals("Intervention en cours FR", config.homeAnnouncement.localizedText("fr-FR").message)
        assertEquals("Voir", config.homeAnnouncement.localizedText("fr-FR").actionLabel)
        assertEquals("Maintenance EN", config.homeAnnouncement.localizedText("en-US").title)
        assertEquals("Maintenance FR", config.homeAnnouncement.localizedText("de-DE").title)
    }

    @Test
    fun parseConfig_keepsDefaultsWhenFieldsAreMissing() {
        val config = RemoteFeatureFlags.parseConfig("{}")

        requireNotNull(config)
        assertTrue(config.isFeatureEnabled(RemoteFeatureFlags.Features.SIGNALQUEST_PHOTOS))
        assertFalse(config.isFeatureEnabled(RemoteFeatureFlags.Features.CELLULARFR_PHOTOS))
        assertTrue(config.isActionEnabled(RemoteFeatureFlags.Actions.SHARE_MAP))
        assertTrue(config.isProviderEnabled(RemoteFeatureFlags.Providers.MAP_IGN))
        assertTrue(config.isWorkerEnabled(RemoteFeatureFlags.Workers.DATABASE_UPDATE_CHECK))
        assertTrue(config.isPlatformEnabled(RemoteFeatureFlags.Platform.NOTIFICATIONS))
        assertEquals(50, config.limitOrDefault(RemoteFeatureFlags.Limits.NEARBY_MAX_RADIUS_KM, 99))
        assertFalse(config.homeAnnouncement.enabled)
    }

    @Test
    fun parseConfig_ignoresUnsafeAnnouncementUrlAndEmptyEnabledMessage() {
        val config = RemoteFeatureFlags.parseConfig(
            """
            {
              "homeAnnouncement": {
                "enabled": true,
                "severity": "oops",
                "actionUrl": "javascript:alert(1)"
              }
            }
            """.trimIndent()
        )

        requireNotNull(config)
        assertFalse(config.homeAnnouncement.enabled)
        assertEquals("info", config.homeAnnouncement.severity)
        assertEquals("", config.homeAnnouncement.actionUrl)
    }

    @Test
    fun parseConfig_rejectsHttpAnnouncementUrl() {
        val config = RemoteFeatureFlags.parseConfig(
            """
            {
              "homeAnnouncement": {
                "enabled": true,
                "title": "Update",
                "message": "Details available",
                "actionUrl": "http://api.cajejuma.fr/status"
              }
            }
            """.trimIndent()
        )

        requireNotNull(config)
        assertEquals("", config.homeAnnouncement.actionUrl)
        assertNull(config.homeAnnouncement.httpActionUrlOrNull())
    }

    @Test
    fun parseConfig_rejectsNonOfficialAnnouncementDomain() {
        val config = RemoteFeatureFlags.parseConfig(
            """
            {
              "homeAnnouncement": {
                "enabled": true,
                "title": "Update",
                "message": "Details available",
                "actionUrl": "https://example.com/status"
              }
            }
            """.trimIndent()
        )

        requireNotNull(config)
        assertEquals("", config.homeAnnouncement.actionUrl)
        assertNull(config.homeAnnouncement.httpActionUrlOrNull())
    }

    @Test
    fun homeAnnouncement_isVisibleOnlyInsideConfiguredAppVersionRange() {
        val config = RemoteFeatureFlags.parseConfig(
            """
            {
              "homeAnnouncement": {
                "enabled": true,
                "title": "Update",
                "message": "Please update",
                "minAppVersionInclusive": "1.9.9.0",
                "maxAppVersionExclusive": "1.9.9.4.2"
              }
            }
            """.trimIndent()
        )

        requireNotNull(config)
        assertFalse(config.homeAnnouncement.isVisibleForAppVersion("1.9.8.9"))
        assertTrue(config.homeAnnouncement.isVisibleForAppVersion("1.9.9.0"))
        assertTrue(config.homeAnnouncement.isVisibleForAppVersion("1.9.9.4.1"))
        assertFalse(config.homeAnnouncement.isVisibleForAppVersion("1.9.9.4.2"))
        assertFalse(config.homeAnnouncement.isVisibleForAppVersion("1.9.9.4.3"))
    }
}
