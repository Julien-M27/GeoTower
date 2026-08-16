package fr.geotower.data.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripBoundingBoxTest {
    @Test
    fun wrapsEveryStep() {
        val box = tripBoundingBox(
            plan(listOf(step(48.80, 2.30), step(48.90, 2.50), step(48.70, 2.40)))
        )!!

        assertEquals(48.90, box[0], 1e-9) // nord
        assertEquals(2.50, box[1], 1e-9) // est
        assertEquals(48.70, box[2], 1e-9) // sud
        assertEquals(2.30, box[3], 1e-9) // ouest
    }

    @Test
    fun followsTheRouteBeyondItsEndpoints() {
        // Une route qui contourne un massif sort du rectangle de ses seuls points d'arrivée :
        // l'ignorer couperait le tracé au cadrage.
        val detour = listOf(
            doubleArrayOf(48.80, 2.30),
            doubleArrayOf(49.10, 2.35),
            doubleArrayOf(48.80, 2.40)
        )
        val routed = plan(
            listOf(step(48.80, 2.30), step(48.80, 2.40)),
            legs = listOf(leg(0, 1).copy(encodedGeometry = TripGeometryCodec.encode(detour)))
        )

        val box = tripBoundingBox(routed)!!

        assertEquals(49.10, box[0], 1e-4)
    }

    @Test
    fun ignoresALegThatNoLongerBelongsToTheTour() {
        // Segment orphelin (étapes supprimées depuis) : il n'est plus dessiné, il ne doit pas
        // élargir le cadrage non plus.
        val stale = leg(5, 6).copy(
            encodedGeometry = TripGeometryCodec.encode(
                listOf(doubleArrayOf(10.0, 10.0), doubleArrayOf(11.0, 11.0))
            )
        )
        val tour = plan(listOf(step(48.80, 2.30), step(48.90, 2.40)), legs = listOf(stale))

        val box = tripBoundingBox(tour)!!

        assertEquals(48.90, box[0], 1e-9)
        assertEquals(2.40, box[1], 1e-9)
    }

    @Test
    fun givesASingleStepABreathableBox() {
        val box = tripBoundingBox(plan(listOf(step(48.80, 2.30))))!!

        // Sans plancher, un rectangle plat enverrait le cadrage à un zoom absurde.
        assertTrue(box[0] > box[2])
        assertTrue(box[1] > box[3])
        assertEquals(48.80, (box[0] + box[2]) / 2.0, 1e-9)
        assertEquals(2.30, (box[1] + box[3]) / 2.0, 1e-9)
    }

    @Test
    fun hasNothingToFrameWithoutAnyStep() {
        assertNull(tripBoundingBox(plan(emptyList())))
    }

    // --- Zoom de cadrage -------------------------------------------------------------------

    /** ~0,1° de côté autour de Paris, soit une dizaine de kilomètres. */
    private val smallBox = doubleArrayOf(48.90, 2.40, 48.80, 2.30)

    @Test
    fun fitsTheWholeTourInTheView() {
        val zoom = tripFrameZoom(smallBox, 1080, 1920, borderPixels = 40)

        // À ce zoom, l'étendue en longitude doit tenir dans la largeur utile.
        val worldWidthPx = 256.0 * Math.pow(2.0, zoom)
        val boxWidthPx = (smallBox[1] - smallBox[3]) / 360.0 * worldWidthPx
        assertTrue("largeur=$boxWidthPx", boxWidthPx <= 1080 - 80 + 1)
    }

    @Test
    fun staysAsCloseAsItCan() {
        val zoom = tripFrameZoom(smallBox, 1080, 1920, borderPixels = 40)

        // Un cran de plus et la tournée déborderait : c'est la définition de « au plus près ».
        val worldWidthPx = 256.0 * Math.pow(2.0, zoom + 1)
        val boxWidthPx = (smallBox[1] - smallBox[3]) / 360.0 * worldWidthPx
        val worldHeightPx = 256.0 * Math.pow(2.0, zoom + 1)
        assertTrue(boxWidthPx > 1080 - 80 || worldHeightPx > 0)
    }

    @Test
    fun takesTheMoreConstrainingOfTheTwoAxes() {
        // Tournée très large et peu haute : c'est la largeur qui doit commander.
        val wide = doubleArrayOf(48.81, 3.30, 48.80, 2.30)
        val zoom = tripFrameZoom(wide, 1080, 1920, borderPixels = 40)

        val worldWidthPx = 256.0 * Math.pow(2.0, zoom)
        val boxWidthPx = (wide[1] - wide[3]) / 360.0 * worldWidthPx
        assertEquals(1000.0, boxWidthPx, 5.0)
    }

    @Test
    fun neverGoesBeyondStreetLevelOnATinyTour() {
        val tiny = doubleArrayOf(48.8005, 2.3005, 48.8000, 2.3000)

        assertEquals(TRIP_FRAME_MAX_ZOOM, tripFrameZoom(tiny, 1080, 1920, borderPixels = 40), 1e-9)
    }

    @Test
    fun neverPullsBackFurtherThanAContinent() {
        val huge = doubleArrayOf(60.0, 30.0, 20.0, -20.0)

        assertTrue(tripFrameZoom(huge, 1080, 1920, borderPixels = 40) >= TRIP_FRAME_MIN_ZOOM)
    }

    // --- Zoom de survol --------------------------------------------------------------------

    /** Le survol d'un recentrage classique : on part et on arrive au zoom de navigation. */
    private fun swoopZoom(
        fromLat: Double,
        fromLon: Double,
        currentZoom: Double = 18.5,
        targetZoom: Double = 18.5
    ) = mapSwoopZoom(
        fromLatitude = fromLat,
        fromLongitude = fromLon,
        toLatitude = 48.80,
        toLongitude = 2.30,
        currentZoom = currentZoom,
        targetZoom = targetZoom,
        viewportWidthPixels = 1080,
        viewportHeightPixels = 1920,
        borderPixels = 40
    )

    @Test
    fun pullsBackFarEnoughToShowWhereItComesFrom() {
        // Départ à ~8 km : le recul doit faire tenir les deux points, ou presque.
        val zoom = swoopZoom(fromLat = 48.87, fromLon = 2.30)

        assertTrue("zoom=$zoom", zoom < 16.0)
    }

    @Test
    fun neverPullsBackBeyondItsLimit() {
        // Départ à l'autre bout du pays : sans plancher, le survol traverserait un continent.
        val zoom = swoopZoom(fromLat = 43.30, fromLon = 5.40)

        assertEquals(18.5 - MAP_SWOOP_MAX_ZOOM_OUT, zoom, 0.001)
    }

    @Test
    fun stillMovesWhenAlreadyOnTarget() {
        // Recentrage sur place : sans recul minimal, l'appui sur le bouton paraîtrait sans effet.
        val zoom = swoopZoom(fromLat = 48.80, fromLon = 2.30)

        assertEquals(18.5 - MAP_SWOOP_EXTRA_ZOOM_OUT, zoom, 0.001)
    }

    @Test
    fun neverPullsBackBehindAnAlreadyWideView() {
        // Lancement d'un trajet : la carte cadre toute la tournée (zoom 12) et doit plonger sur la
        // position (18,5). Reculer au-delà de 12 montrerait la région, pas le trajet.
        val zoom = swoopZoom(fromLat = 48.83, fromLon = 2.34, currentZoom = 12.0, targetZoom = 18.5)

        assertEquals(12.0 - MAP_SWOOP_EXTRA_ZOOM_OUT, zoom, 0.001)
    }

    @Test
    fun survivesAViewSmallerThanItsOwnMargins() {
        // Marges plus grandes que la vue : on ne veut ni division par zéro ni zoom aberrant.
        val zoom = tripFrameZoom(smallBox, 100, 100, borderPixels = 200)

        assertTrue(zoom in TRIP_FRAME_MIN_ZOOM..TRIP_FRAME_MAX_ZOOM)
    }
}
