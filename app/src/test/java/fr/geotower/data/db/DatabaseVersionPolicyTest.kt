package fr.geotower.data.db

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseVersionPolicyTest {
    @Test
    fun sameInstalledVersionIsCurrentAndDoesNotNotify() {
        val version = "20260509_2000"

        assertFalse(DatabaseVersionPolicy.isRemoteNewer(version, version))
        assertTrue(DatabaseVersionPolicy.isLocalCurrentOrNewer(version, version))
        assertFalse(DatabaseVersionPolicy.shouldNotify(version, version, ""))
    }

    @Test
    fun newerRemoteVersionIsAvailableWhenLocalIsOlder() {
        assertTrue(DatabaseVersionPolicy.isRemoteNewer("20260509_2000", "20260508_2000"))
        assertFalse(DatabaseVersionPolicy.isLocalCurrentOrNewer("20260509_2000", "20260508_2000"))
    }

    @Test
    fun olderRemoteVersionDoesNotDowngradeCurrentLocalDatabase() {
        assertFalse(DatabaseVersionPolicy.isRemoteNewer("20260508_2000", "20260509_2000"))
        assertTrue(DatabaseVersionPolicy.isLocalCurrentOrNewer("20260508_2000", "20260509_2000"))
    }

    @Test
    fun missingLocalVersionMeansRemoteCanBeInstalled() {
        assertTrue(DatabaseVersionPolicy.isRemoteNewer("20260509_2000", null))
        assertFalse(DatabaseVersionPolicy.isLocalCurrentOrNewer("20260509_2000", null))
    }

    @Test
    fun lastNotifiedVersionSuppressesDuplicateNotificationOnlyWhenUpdateStillNeeded() {
        assertFalse(DatabaseVersionPolicy.shouldNotify("20260509_2000", "20260508_2000", "20260509_2000"))
        assertTrue(DatabaseVersionPolicy.shouldNotify("20260509_2000", "20260508_2000", "20260508_2000"))
    }

    @Test
    fun isoRemoteVersionWithSecondsMatchesInstalledMinuteVersion() {
        assertFalse(DatabaseVersionPolicy.isRemoteNewer("2026-05-09T20:00:00", "20260509_2000"))
        assertTrue(DatabaseVersionPolicy.isLocalCurrentOrNewer("2026-05-09T20:00:00", "20260509_2000"))
        assertFalse(DatabaseVersionPolicy.shouldNotify("2026-05-09T20:00:00", "20260509_2000", ""))
    }

    @Test
    fun newerIsoMinuteStillRequiresUpdate() {
        assertTrue(DatabaseVersionPolicy.isRemoteNewer("2026-05-09T20:01:00", "20260509_2000"))
        assertFalse(DatabaseVersionPolicy.isLocalCurrentOrNewer("2026-05-09T20:01:00", "20260509_2000"))
    }

    @Test
    fun equivalentLastNotifiedFormatSuppressesDuplicateNotification() {
        assertFalse(DatabaseVersionPolicy.shouldNotify("2026-05-09T20:00:00", "20260508_2000", "20260509_2000"))
    }
}
