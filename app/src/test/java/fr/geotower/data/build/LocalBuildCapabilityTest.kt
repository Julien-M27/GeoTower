package fr.geotower.data.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBuildCapabilityTest {

    private val gib = 1024L * 1024L * 1024L

    @Test
    fun eligibleWhenEnoughRamAndStorage() {
        // Appareil 8 Go typique (totalMem ~7,4 GiB) + 2 Go libres.
        val result = LocalBuildCapability.evaluate(
            totalRamBytes = (7.4 * gib).toLong(),
            freeStorageBytes = 2 * gib,
            lowRamDevice = false,
        )
        assertTrue(result.eligible)
        assertNull(result.reason)
    }

    @Test
    fun sixGbDeviceReportingUnderSixGibStillPasses() {
        // Un vrai 6 Go rapporte ~5,6 GiB : l'arrondi au GiB superieur doit l'accepter.
        val result = LocalBuildCapability.evaluate(
            totalRamBytes = (5.6 * gib).toLong(),
            freeStorageBytes = 1 * gib,
            lowRamDevice = false,
        )
        assertTrue(result.eligible)
    }

    @Test
    fun rejectsFourGbDevice() {
        // Un 4 Go rapporte ~3,7 GiB -> arrondi 4 -> refuse.
        val result = LocalBuildCapability.evaluate(
            totalRamBytes = (3.7 * gib).toLong(),
            freeStorageBytes = 4 * gib,
            lowRamDevice = false,
        )
        assertFalse(result.eligible)
        assertEquals("RAM insuffisante (< 6 Go)", result.reason)
    }

    @Test
    fun rejectsInsufficientStorage() {
        val result = LocalBuildCapability.evaluate(
            totalRamBytes = 8 * gib,
            freeStorageBytes = 500L * 1024 * 1024, // 500 Mo
            lowRamDevice = false,
        )
        assertFalse(result.eligible)
        assertEquals("stockage libre insuffisant (< 1 Go)", result.reason)
    }

    @Test
    fun rejectsLowRamDevice() {
        val result = LocalBuildCapability.evaluate(
            totalRamBytes = 8 * gib,
            freeStorageBytes = 4 * gib,
            lowRamDevice = true,
        )
        assertFalse(result.eligible)
        assertTrue(result.reason!!.contains("faible en RAM"))
    }

    @Test
    fun exactlySixGibAndOneGibAreAccepted() {
        val result = LocalBuildCapability.evaluate(
            totalRamBytes = 6 * gib,
            freeStorageBytes = 1 * gib,
            lowRamDevice = false,
        )
        assertTrue(result.eligible)
    }
}
