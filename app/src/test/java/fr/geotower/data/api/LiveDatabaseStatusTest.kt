package fr.geotower.data.api

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class LiveDatabaseStatusTest {
    @Test
    fun liveDatabaseMetadataCanBeQueriedWhenTheLocalDatabaseIsUsed() {
        assertTrue(
            canQueryLiveDatabaseMetadata(
                liveApiEnabled = true,
                communityAndUpdatesBlocked = false
            )
        )
    }

    @Test
    fun liveDatabaseMetadataCannotBeQueriedWhenTheLiveApiIsDisabled() {
        assertFalse(
            canQueryLiveDatabaseMetadata(
                liveApiEnabled = false,
                communityAndUpdatesBlocked = false
            )
        )
    }

    @Test
    fun liveDatabaseMetadataCannotBeQueriedWhenCommunityAndUpdatesAreBlocked() {
        assertFalse(
            canQueryLiveDatabaseMetadata(
                liveApiEnabled = true,
                communityAndUpdatesBlocked = true
            )
        )
    }
}
