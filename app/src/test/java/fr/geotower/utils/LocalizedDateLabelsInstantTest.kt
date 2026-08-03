package fr.geotower.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Heure de génération publiée par le serveur : les deux builders ne l'écrivent pas pareil
 * (« Z » pour les sites HS, « +00:00 » pour la base eNB) et les deux doivent tomber sur le même
 * instant, quel que soit le fuseau du téléphone.
 */
class LocalizedDateLabelsInstantTest {

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, hour, minute, second)
        }.timeInMillis

    @Test
    fun readsBothServerTimestampSpellings() {
        val expected = utcMillis(2026, 8, 2, 18, 0, 2)

        // sites_hs.geojson (build_sites_hs.py)
        assertEquals(expected, LocalizedDateLabels.isoInstantMillis("2026-08-02T18:00:02Z"))
        // metadata.generated_at de geotower_fr_enb.db (build_fr_enb_db.py)
        assertEquals(expected, LocalizedDateLabels.isoInstantMillis("2026-08-02T18:00:02+00:00"))
    }

    @Test
    fun honoursTheDeclaredOffset() {
        assertEquals(
            utcMillis(2026, 8, 2, 16, 0, 2),
            LocalizedDateLabels.isoInstantMillis("2026-08-02T18:00:02+02:00")
        )
    }

    @Test
    fun isNotFooledByTheDeviceTimeZone() {
        val previous = TimeZone.getDefault()
        try {
            val expected = utcMillis(2026, 8, 2, 18, 0, 2)
            listOf("UTC", "Europe/Paris", "Pacific/Auckland").forEach { zone ->
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                assertEquals(zone, expected, LocalizedDateLabels.isoInstantMillis("2026-08-02T18:00:02Z"))
            }
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun refusesWhatIsNotAnInstant() {
        assertEquals(0L, LocalizedDateLabels.isoInstantMillis(null))
        assertEquals(0L, LocalizedDateLabels.isoInstantMillis(""))
        assertEquals(0L, LocalizedDateLabels.isoInstantMillis("-"))
        // Une date seule n'est pas un instant : la convertir donnerait un faux « à 00:00 ».
        assertEquals(0L, LocalizedDateLabels.isoInstantMillis("2026-08-02"))
        assertEquals(0L, LocalizedDateLabels.isoInstantMillis("2026-08-02T18:00:02Z trailing"))
    }
}
