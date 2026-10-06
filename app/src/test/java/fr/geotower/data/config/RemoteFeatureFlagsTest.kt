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
                "actionUrl": "https://api.geotower.fr/status",
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
        assertEquals("https://api.geotower.fr/status", config.homeAnnouncement.actionUrl)
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
        assertFalse(config.isFeatureEnabled(RemoteFeatureFlags.Features.LIVE_API_FR_STATS))
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
                "actionUrl": "http://api.geotower.fr/status"
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

    @Test
    fun homeAnnouncement_maxAppVersionInclusive_takesPrecedenceAndIsInclusive() {
        val config = RemoteFeatureFlags.parseConfig(
            """
            {
              "homeAnnouncement": {
                "enabled": true,
                "title": "Update",
                "message": "Please update",
                "minAppVersionInclusive": "2.0.60",
                "maxAppVersionInclusive": "2.0.64",
                "maxAppVersionExclusive": "2.0.65"
              }
            }
            """.trimIndent()
        )

        requireNotNull(config)
        assertFalse(config.homeAnnouncement.isVisibleForAppVersion("2.0.59"))
        assertTrue(config.homeAnnouncement.isVisibleForAppVersion("2.0.60"))
        assertTrue(config.homeAnnouncement.isVisibleForAppVersion("2.0.64"))
        assertFalse(config.homeAnnouncement.isVisibleForAppVersion("2.0.65"))
    }

    @Test
    fun databasePolicy_enforcesPerTargetDownloadAndMaxAllowedVersion() {
        val config = RemoteFeatureFlags.parseConfig(
            """
            {
              "schemaVersion": 2,
              "databasePolicy": {
                "mobile": {
                  "download": true,
                  "updateCheck": true,
                  "maxAllowedVersion": "20261001_1200",
                  "minAppVersion": "2.0.60"
                },
                "radio": {
                  "download": false,
                  "updateCheck": true
                },
                "enb": {
                  "download": true,
                  "updateCheck": false
                },
                "localBuild": {
                  "enabled": false
                }
              }
            }
            """.trimIndent()
        )

        requireNotNull(config)

        // Mobile checks with maxAllowedVersion
        assertTrue(config.isDatabaseDownloadAllowed(RemoteFeatureFlags.DatabaseTarget.MOBILE, "20260901_1200", "2.0.64"))
        assertTrue(config.isDatabaseDownloadAllowed(RemoteFeatureFlags.DatabaseTarget.MOBILE, "20261001_1200", "2.0.64"))
        assertFalse(config.isDatabaseDownloadAllowed(RemoteFeatureFlags.DatabaseTarget.MOBILE, "20261002_0000", "2.0.64"))

        // Mobile minAppVersion checks
        assertFalse(config.isDatabaseDownloadAllowed(RemoteFeatureFlags.DatabaseTarget.MOBILE, "20260901_1200", "2.0.59"))
        assertTrue(config.isDatabaseDownloadAllowed(RemoteFeatureFlags.DatabaseTarget.MOBILE, "20260901_1200", "2.0.60"))

        // Radio checks (download blocked by policy)
        assertFalse(config.isDatabaseDownloadAllowed(RemoteFeatureFlags.DatabaseTarget.RADIO))
        assertTrue(config.isDatabaseUpdateCheckAllowed(RemoteFeatureFlags.DatabaseTarget.RADIO))

        // Enb checks (updateCheck blocked by policy)
        assertTrue(config.isDatabaseDownloadAllowed(RemoteFeatureFlags.DatabaseTarget.ENB))
        assertFalse(config.isDatabaseUpdateCheckAllowed(RemoteFeatureFlags.DatabaseTarget.ENB))

        // Local build disabled by policy
        assertFalse(config.isLocalDbBuildAllowed())
    }

    @Test
    fun databasePolicy_granularFeatureSwitchesOverrideDefaults() {
        val config = RemoteFeatureFlags.parseConfig(
            """
            {
              "features": {
                "database.mobile.download": false,
                "database.radio.download": true
              }
            }
            """.trimIndent()
        )

        requireNotNull(config)
        assertFalse(config.isDatabaseDownloadAllowed(RemoteFeatureFlags.DatabaseTarget.MOBILE))
        assertTrue(config.isDatabaseDownloadAllowed(RemoteFeatureFlags.DatabaseTarget.RADIO))
    }

    @Test
    fun databasePolicy_globalMasterSwitchDisablesAllDatabases() {
        val config = RemoteFeatureFlags.parseConfig(
            """
            {
              "features": {
                "database.download": false
              }
            }
            """.trimIndent()
        )

        requireNotNull(config)
        assertFalse(config.isDatabaseDownloadAllowed(RemoteFeatureFlags.DatabaseTarget.MOBILE))
        assertFalse(config.isDatabaseDownloadAllowed(RemoteFeatureFlags.DatabaseTarget.RADIO))
        assertFalse(config.isDatabaseDownloadAllowed(RemoteFeatureFlags.DatabaseTarget.ENB))
        // Outages should remain unaffected by SQLite database.download master switch
        assertTrue(config.isDatabaseDownloadAllowed(RemoteFeatureFlags.DatabaseTarget.OUTAGES))
    }

    @Test
    fun databasePolicy_backwardCompatibilityWithEmptyJson() {
        val config = RemoteFeatureFlags.parseConfig("{}")

        requireNotNull(config)
        assertTrue(config.isDatabaseDownloadAllowed(RemoteFeatureFlags.DatabaseTarget.MOBILE))
        assertTrue(config.isDatabaseDownloadAllowed(RemoteFeatureFlags.DatabaseTarget.RADIO))
        assertTrue(config.isDatabaseDownloadAllowed(RemoteFeatureFlags.DatabaseTarget.ENB))
        assertTrue(config.isDatabaseDownloadAllowed(RemoteFeatureFlags.DatabaseTarget.OUTAGES))
        assertTrue(config.isDatabaseUpdateCheckAllowed(RemoteFeatureFlags.DatabaseTarget.MOBILE))
        assertTrue(config.isDatabaseUpdateCheckAllowed(RemoteFeatureFlags.DatabaseTarget.RADIO))
        assertTrue(config.isDatabaseUpdateCheckAllowed(RemoteFeatureFlags.DatabaseTarget.ENB))
        assertTrue(config.isLocalDbBuildAllowed())
    }
}