package fr.geotower.data.workers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationPauseStoreTest {
    @Test
    fun updatePausedOperationsAddsAndRemovesOnlyTheRequestedOperation() {
        val current = setOf(OperationPauseStore.LOCAL_DB_BUILD)

        val paused = OperationPauseStore.updatePausedOperations(
            current,
            OperationPauseStore.RADIO_DB_DOWNLOAD,
            paused = true,
        )
        val resumed = OperationPauseStore.updatePausedOperations(
            paused,
            OperationPauseStore.LOCAL_DB_BUILD,
            paused = false,
        )

        assertTrue(paused.contains(OperationPauseStore.LOCAL_DB_BUILD))
        assertTrue(paused.contains(OperationPauseStore.RADIO_DB_DOWNLOAD))
        assertFalse(resumed.contains(OperationPauseStore.LOCAL_DB_BUILD))
        assertEquals(setOf(OperationPauseStore.RADIO_DB_DOWNLOAD), resumed)
    }

    @Test
    fun mapDownloadKeyIsScopedToTheMapFilename() {
        assertEquals(
            "${OperationPauseStore.OFFLINE_MAP_DOWNLOAD_PREFIX}alsace.map",
            OperationPauseStore.mapDownloadKey("alsace.map"),
        )
    }
}
