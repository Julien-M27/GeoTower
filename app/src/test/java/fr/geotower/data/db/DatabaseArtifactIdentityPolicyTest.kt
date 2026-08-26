package fr.geotower.data.db

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseArtifactIdentityPolicyTest {
    @Test
    fun sameHashIsSameDatabaseRegardlessOfServerVersion() {
        assertTrue(
            DatabaseArtifactIdentityPolicy.matches(
                proposed = DatabaseArtifactIdentity("mirror-build-20260824", "a".repeat(64)),
                installed = DatabaseArtifactIdentity("primary-build-20260824", "A".repeat(64))
            )
        )
    }

    @Test
    fun sameDataVersionIsSameDatabaseWhenHostsBuildDifferentSqliteFiles() {
        assertTrue(
            DatabaseArtifactIdentityPolicy.matches(
                proposed = DatabaseArtifactIdentity("2026-08-24T12:00:00", "a".repeat(64)),
                installed = DatabaseArtifactIdentity("20260824_1200", "b".repeat(64))
            )
        )
    }

    @Test
    fun differentHashAndVersionMeansDifferentDatabase() {
        assertFalse(
            DatabaseArtifactIdentityPolicy.matches(
                proposed = DatabaseArtifactIdentity("20260824_1201", "a".repeat(64)),
                installed = DatabaseArtifactIdentity("20260824_1200", "b".repeat(64))
            )
        )
    }
}
