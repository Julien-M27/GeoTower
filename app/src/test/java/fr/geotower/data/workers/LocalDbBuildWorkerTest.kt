package fr.geotower.data.workers

import fr.geotower.data.build.LocalDbBuildPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalDbBuildWorkerTest {

    private val mobileLabel = "Base mobile"
    private val radioLabel = "Base radio"
    private val fallbackLabel = "Génération de la base"

    @Test
    fun formatsDatabasesLabel_mobileOnly() {
        val packs = LocalDbBuildPipeline.Packs(mobile = true, radioBroadcast = false, nonMobileTech = false)
        val label = LocalDbBuildWorker.formatDatabasesLabel(packs, mobileLabel, radioLabel, fallbackLabel)
        assertEquals("Base mobile", label)
    }

    @Test
    fun formatsDatabasesLabel_radioBroadcastOnly() {
        val packs = LocalDbBuildPipeline.Packs(mobile = false, radioBroadcast = true, nonMobileTech = false)
        val label = LocalDbBuildWorker.formatDatabasesLabel(packs, mobileLabel, radioLabel, fallbackLabel)
        assertEquals("Base radio", label)
    }

    @Test
    fun formatsDatabasesLabel_nonMobileTechOnly() {
        val packs = LocalDbBuildPipeline.Packs(mobile = false, radioBroadcast = false, nonMobileTech = true)
        val label = LocalDbBuildWorker.formatDatabasesLabel(packs, mobileLabel, radioLabel, fallbackLabel)
        assertEquals("Base radio", label)
    }

    @Test
    fun formatsDatabasesLabel_mobileAndRadio() {
        val packs = LocalDbBuildPipeline.Packs(mobile = true, radioBroadcast = true, nonMobileTech = true)
        val label = LocalDbBuildWorker.formatDatabasesLabel(packs, mobileLabel, radioLabel, fallbackLabel)
        assertEquals("Base mobile, Base radio", label)
    }

    @Test
    fun formatsDatabasesLabel_empty() {
        val packs = LocalDbBuildPipeline.Packs(mobile = false, radioBroadcast = false, nonMobileTech = false)
        val label = LocalDbBuildWorker.formatDatabasesLabel(packs, mobileLabel, radioLabel, fallbackLabel)
        assertEquals("Génération de la base", label)
    }

    @Test
    fun buildRequestWithCombinedRadio_setsBothNonMobileFlags() {
        val request = LocalDbBuildWorker.buildRequest(mobile = true, radio = true, force = true)
        assertTrue(request.tags.contains(LocalDbBuildWorker.TAG_PACK_MOBILE))
        assertTrue(request.tags.contains(LocalDbBuildWorker.TAG_PACK_RADIO))
        assertEquals(true, request.workSpec.input.getBoolean(LocalDbBuildWorker.KEY_PACK_MOBILE, false))
        assertEquals(true, request.workSpec.input.getBoolean(LocalDbBuildWorker.KEY_PACK_RADIO_BROADCAST, false))
        assertEquals(true, request.workSpec.input.getBoolean(LocalDbBuildWorker.KEY_PACK_NONMOBILE, false))
        assertEquals(true, request.workSpec.input.getBoolean(LocalDbBuildWorker.KEY_FORCE, false))
    }

    @Test
    fun buildRequestWithMobileOnly_omitsRadioTag() {
        val request = LocalDbBuildWorker.buildRequest(mobile = true, radio = false, force = false)
        assertTrue(request.tags.contains(LocalDbBuildWorker.TAG_PACK_MOBILE))
        assertFalse(request.tags.contains(LocalDbBuildWorker.TAG_PACK_RADIO))
        assertEquals(true, request.workSpec.input.getBoolean(LocalDbBuildWorker.KEY_PACK_MOBILE, false))
        assertEquals(false, request.workSpec.input.getBoolean(LocalDbBuildWorker.KEY_PACK_RADIO_BROADCAST, true))
        assertEquals(false, request.workSpec.input.getBoolean(LocalDbBuildWorker.KEY_PACK_NONMOBILE, true))
    }
}
