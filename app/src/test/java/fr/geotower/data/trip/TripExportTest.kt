package fr.geotower.data.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripExportTest {
    private fun computedLeg(fromIndex: Int, toIndex: Int, points: List<DoubleArray>) = leg(fromIndex, toIndex)
        .copy(encodedGeometry = TripGeometryCodec.encode(points))

    @Test
    fun writesOneTrackSegmentPerLegAndOneWaypointPerStep() {
        val steps = ladder(3)
        val gpx = TripExport.buildGpx(
            listOf(
                plan(
                    steps,
                    legs = listOf(
                        computedLeg(0, 1, listOf(doubleArrayOf(48.80, 2.30), doubleArrayOf(48.81, 2.31))),
                        computedLeg(1, 2, listOf(doubleArrayOf(48.81, 2.31), doubleArrayOf(48.82, 2.32)))
                    )
                )
            ),
            nowMillis = 0L
        )

        assertEquals(3, Regex("<wpt ").findAll(gpx).count())
        assertEquals(2, Regex("<trkseg>").findAll(gpx).count())
        assertTrue(gpx.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(gpx.trimEnd().endsWith("</gpx>"))
    }

    @Test
    fun addsTheReturnLegWithoutRepeatingTheDepartureWaypoint() {
        val gpx = TripExport.buildGpx(listOf(plan(ladder(3), returnToStart = true)), nowMillis = 0L)

        // Trois étapes, trois points de passage : le retour n'en est pas une quatrième.
        assertEquals(3, Regex("<wpt ").findAll(gpx).count())
        // Trois segments en revanche : 0→1, 1→2 et le retour 2→0.
        assertEquals(3, Regex("<trkseg>").findAll(gpx).count())
    }

    @Test
    fun fallsBackToAStraightSegmentWhenALegWasNeverComputed() {
        val gpx = TripExport.buildGpx(listOf(plan(ladder(2))), nowMillis = 0L)

        assertEquals(1, Regex("<trkseg>").findAll(gpx).count())
        assertEquals(2, Regex("<trkpt ").findAll(gpx).count())
    }

    @Test
    fun writesCoordinatesWithADecimalPoint() {
        val gpx = TripExport.buildGpx(listOf(plan(listOf(step(48.853606, 2.349176)))), nowMillis = 0L)

        // Une virgule décimale rendrait le fichier illisible pour tout autre logiciel.
        assertTrue(gpx, gpx.contains("lat=\"48.853606\" lon=\"2.349176\""))
        assertFalse(gpx.contains("lat=\"48,"))
    }

    @Test
    fun escapesNamesThatWouldBreakTheXml() {
        val named = plan(listOf(step(48.85, 2.35, label = "Pylône <A> & \"B\"")))
            .copy(name = "Tournée d'Ille-et-Vilaine")

        val gpx = TripExport.buildGpx(listOf(named), nowMillis = 0L)

        assertTrue(gpx.contains("Pylône &lt;A&gt; &amp; &quot;B&quot;"))
        assertTrue(gpx.contains("Tournée d&apos;Ille-et-Vilaine"))
        assertFalse(gpx.contains("<A>"))
    }

    @Test
    fun namesUnlabelledStepsThroughTheCallerSoTheyStayTranslated() {
        val gpx = TripExport.buildGpx(
            listOf(plan(listOf(step(48.85, 2.35, label = ""), step(48.86, 2.36, label = "")))),
            nowMillis = 0L,
            stepFallbackLabel = { "Étape ${it + 1}" }
        )

        assertTrue(gpx.contains("<name>Étape 1</name>"))
        assertTrue(gpx.contains("<name>Étape 2</name>"))
    }

    @Test
    fun writesTheExportTimeInUtc() {
        val gpx = TripExport.buildGpx(listOf(plan(ladder(2))), nowMillis = 0L)

        // Fuseau figé à UTC : sans cela le fichier daterait de l'heure locale sans le dire.
        assertTrue(gpx, gpx.contains("<time>1970-01-01T00:00:00Z</time>"))
    }

    @Test
    fun exportsSeveralTripsInOneFile() {
        val gpx = TripExport.buildGpx(
            listOf(plan(ladder(2)).copy(name = "Lundi"), plan(ladder(3)).copy(name = "Mardi")),
            nowMillis = 0L
        )

        assertEquals(2, Regex("<trk>").findAll(gpx).count())
        assertEquals(5, Regex("<wpt ").findAll(gpx).count())
        assertTrue(gpx.contains("<name>Lundi</name>"))
        assertTrue(gpx.contains("<name>Mardi</name>"))
    }

    @Test
    fun keepsEverythingTheGpxCannotCarryInTheJson() {
        val scheduled = plan(listOf(step(48.85, 2.35, visitedAtMillis = 99L)))
            .copy(plannedAtMillis = 1_000L, reminderOffsetsMinutes = listOf(1440), stopDurationMinutes = 10)

        val json = TripExport.buildJson(listOf(scheduled))

        assertTrue(json.contains("plannedAtMillis"))
        assertTrue(json.contains("reminderOffsetsMinutes"))
        assertTrue(json.contains("visitedAtMillis"))
        assertTrue(json.contains("stopDurationMinutes"))
    }

    @Test
    fun readsBackJsonExportsForImport() {
        val original = plan(listOf(step(48.85, 2.35, visitedAtMillis = 99L)))
            .copy(plannedAtMillis = 1_000L, reminderOffsetsMinutes = listOf(1440), stopDurationMinutes = 10)

        assertEquals(listOf(original), TripExport.parseJson(TripExport.buildJson(listOf(original))))
    }

    @Test
    fun buildsFileNamesWithoutForbiddenCharacters() {
        // Les accents sont des lettres et restent ; seuls les caractères interdits deviennent des
        // tirets, et les tirets consécutifs fusionnent.
        assertEquals("Tournee-35-août", TripExport.fileStem(listOf(plan(ladder(2)).copy(name = "Tournee 35 : août"))))
        assertEquals("trajets-geotower", TripExport.fileStem(listOf(plan(ladder(2)), plan(ladder(2)))))
        assertEquals("trajets-geotower", TripExport.fileStem(listOf(plan(ladder(2)).copy(name = "///"))))
    }
}
