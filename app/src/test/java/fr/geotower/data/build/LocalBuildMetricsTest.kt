package fr.geotower.data.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBuildMetricsTest {

    private var now = 0L
    private val metrics = LocalBuildMetrics { now }

    private val device = BuildDeviceProfile(
        totalRamBytes = 4L * 1024 * 1024 * 1024,
        heapLimitBytes = 192L * 1024 * 1024,
        largeHeapLimitBytes = 384L * 1024 * 1024,
        lowRamDevice = false,
        processors = 8,
    )

    /**
     * READING_SUPPORTS est traversee trois fois (emetteurs, antennes, supports) et son compteur de
     * lignes repart de zero a chaque sous-boucle : duree et lignes doivent se **cumuler**, sinon le
     * rapport sous-estime la phase la plus lourde du build.
     */
    @Test
    fun sumsDurationAndRowsOfAPhaseTraversedSeveralTimes() {
        metrics.onProgress(BuildPhase.READING_SUPPORTS, 0)
        now += 1_000
        metrics.onProgress(BuildPhase.READING_SUPPORTS, 50_000)
        now += 1_000
        metrics.onProgress(BuildPhase.READING_SUPPORTS, 0)
        now += 1_000
        metrics.onProgress(BuildPhase.READING_SUPPORTS, 30_000)
        now += 1_000
        metrics.onProgress(BuildPhase.INSERTING, 0)
        now += 2_000

        val report = metrics.report(device, "mobile", success = true, reason = null)

        assertTrue(
            report.toString(),
            report.any { it == "Phase READING_SUPPORTS : 4 s — 80000 lignes — 20000 lignes/s" },
        )
        assertTrue(report.toString(), report.any { it == "Phase INSERTING : 2 s" })
        assertEquals("Duree totale : 6 s", report.first { it.startsWith("Duree totale") })
    }

    /**
     * Le pic doit etre rattache a **la phase qui l'a provoque** : c'est lui qui dit ou porter
     * l'effort, et si un build partiel (un seul pack) tiendrait dans bien moins de memoire.
     */
    @Test
    fun keepsPeaksAndAttributesThemToTheGuiltyPhase() {
        metrics.onProgress(BuildPhase.BUILDING_DETAILS, 0)
        metrics.sampleMemory(heapBytes = 40L * 1024 * 1024, nativeBytes = 10L * 1024 * 1024, pssBytes = 0L)
        metrics.sampleDisk(workDirBytes = 200L * 1024 * 1024, outputBytes = 100L * 1024 * 1024)
        now += 1_000
        metrics.onProgress(BuildPhase.RADIO_BUILDING, 0)
        metrics.sampleMemory(heapBytes = 96L * 1024 * 1024, nativeBytes = 5L * 1024 * 1024, pssBytes = 300L * 1024 * 1024)
        metrics.sampleMemory(heapBytes = 12L * 1024 * 1024, nativeBytes = 64L * 1024 * 1024, pssBytes = 0L)
        metrics.sampleDisk(workDirBytes = 50L * 1024 * 1024, outputBytes = 400L * 1024 * 1024)

        val report = metrics.report(device, "mobile+radio/TV", success = true, reason = null)

        assertEquals(96L * 1024 * 1024, metrics.peakHeapBytes)
        assertEquals(64L * 1024 * 1024, metrics.peakNativeBytes)
        assertEquals(BuildPhase.RADIO_BUILDING, metrics.peakHeapPhase)
        // Le pic de stockage est celui d'un instant donne, pas la somme des maximums de chaque poste.
        assertEquals(450L * 1024 * 1024, metrics.peakTotalDiskBytes)
        assertTrue(
            report.toString(),
            report.any { it == "Pic tas Java : 96 Mo (50 % du plafond) — pendant RADIO_BUILDING" },
        )
        assertTrue(report.toString(), report.any { it == "Pic PSS process : 300 Mo" })
        // Les trois pics sont pris a des instants differents : le rapport ne doit pas les presenter
        // comme une somme (450 n'est pas 200 + 400).
        assertTrue(
            report.toString(),
            report.any { it == "Pic stockage simultane : 450 Mo — pendant RADIO_BUILDING" },
        )
        assertTrue(
            report.toString(),
            report.any { it == "Pics separes : travail 200 Mo | bases produites 400 Mo" },
        )
        // Chaque phase porte son propre pic, pour reperer celle qui coute cher isolement.
        assertTrue(report.toString(), report.any { it == "Phase BUILDING_DETAILS : 1 s — pic tas 40 Mo" })
    }

    @Test
    fun reportsFailureCauseAndMeasuredFiles() {
        metrics.noteFile("observatoire.csv", 182L * 1024 * 1024)
        metrics.noteFile("vide", 0L)

        val report = metrics.report(device, "mobile", success = false, reason = "Observatoire vide")

        assertEquals("Resultat : echec — Observatoire vide", report.first())
        assertTrue(report.toString(), report.any { it == "Fichier observatoire.csv : 182 Mo" })
        // Une taille nulle n'apporte rien : elle n'est pas listee.
        assertTrue(report.toString(), report.none { it.startsWith("Fichier vide") })
    }
}
