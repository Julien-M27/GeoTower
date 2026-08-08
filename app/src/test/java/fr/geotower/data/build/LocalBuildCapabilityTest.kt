package fr.geotower.data.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBuildCapabilityTest {

    private val mib = 1024L * 1024L
    private val gib = 1024L * mib

    private fun evaluate(
        heapLimitMib: Long,
        freeStorageMib: Long,
        lowRam: Boolean = false,
        mobile: Boolean = true,
        radio: Boolean = false,
    ) = LocalBuildCapability.evaluate(
        totalRamBytes = 4L * gib,
        freeStorageBytes = freeStorageMib * mib,
        heapLimitBytes = heapLimitMib * mib,
        lowRamDevice = lowRam,
        required = LocalBuildCapability.budgetFor(mobile, radio),
    )

    /**
     * Le cas qui a motive toute la reecriture : un 4 Go dont le tas plafonne a 192 Mo etait exclu
     * par l'ancien seuil « RAM >= 6 Go » alors que la generation y tient (pic mesure : 110 Mo).
     */
    @Test
    fun acceptsAFourGigabyteDeviceWithATypicalHeapCeiling() {
        val result = evaluate(heapLimitMib = 192, freeStorageMib = 2048)

        assertTrue(result.reason ?: "", result.eligible)
        assertNull(result.reason)
    }

    @Test
    fun rejectsADeviceWhoseHeapCeilingIsTooLow() {
        val result = evaluate(heapLimitMib = 128, freeStorageMib = 2048)

        assertFalse(result.eligible)
        assertTrue(result.reason ?: "", result.reason!!.contains("mémoire d'application"))
    }

    /**
     * L'ancien plancher de stockage (1 Go) laissait passer un build complet qui reclame ~1,2 Go :
     * il echouait alors en fin de parcours, apres la data et la batterie deja consommees.
     */
    @Test
    fun requiresMoreStorageForTheFullBuildThanForMobileAlone() {
        val mobileOnly = LocalBuildCapability.budgetFor(mobile = true, radio = false)
        val everything = LocalBuildCapability.budgetFor(mobile = true, radio = true)

        assertTrue(everything.storageBytes > mobileOnly.storageBytes)
        assertTrue(everything.storageBytes > 1L * gib)
        // Le budget memoire, lui, ne depend pas des packs : le poste lourd est commun.
        assertEquals(mobileOnly.heapBytes, everything.heapBytes)
    }

    @Test
    fun rejectsWhenFreeStorageIsBelowTheBudgetOfTheChosenPacks() {
        // 1,1 Go libre : suffisant pour le mobile seul, insuffisant pour tout generer.
        assertTrue(evaluate(heapLimitMib = 256, freeStorageMib = 1126).eligible)

        val full = evaluate(heapLimitMib = 256, freeStorageMib = 1126, mobile = true, radio = true)
        assertFalse(full.eligible)
        assertTrue(full.reason ?: "", full.reason!!.contains("stockage libre"))
    }

    @Test
    fun rejectsDevicesTheSystemFlagsAsLowRam() {
        val result = evaluate(heapLimitMib = 256, freeStorageMib = 4096, lowRam = true)

        assertFalse(result.eligible)
        assertNotNull(result.reason)
    }

    /** Les deux causes se cumulent dans le motif, pour que le diagnostic soit complet. */
    @Test
    fun reportsEveryBlockingReasonAtOnce() {
        val result = evaluate(heapLimitMib = 96, freeStorageMib = 100)

        assertFalse(result.eligible)
        assertTrue(result.reason ?: "", result.reason!!.contains("mémoire d'application"))
        assertTrue(result.reason ?: "", result.reason!!.contains("stockage libre"))
    }

    /** Les marges doivent rester au-dessus des pics reellement mesures, sans les doubler. */
    @Test
    fun keepsMarginsAboveMeasuredPeaksWithoutInflatingThem() {
        val budget = LocalBuildCapability.budgetFor(mobile = true, radio = true)

        assertTrue(budget.heapBytes > 112L * mib)
        assertTrue(budget.heapBytes < 2L * 112L * mib)
        assertTrue(budget.storageBytes > 1157L * mib)
        assertTrue(budget.storageBytes < 2L * 1157L * mib)
    }
}
