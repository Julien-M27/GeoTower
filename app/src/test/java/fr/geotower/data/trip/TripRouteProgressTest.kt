package fr.geotower.data.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRouteProgressTest {
    /** ~1,1 km plein nord, en dix points. */
    private val northbound = (0..10).map { doubleArrayOf(48.8000 + it * 0.001, 2.3000) }

    /** ~0,7 km plein est, dans la continuité du précédent. */
    private val eastbound = (0..10).map { doubleArrayOf(48.8100, 2.3000 + it * 0.001) }

    @Test
    fun findsHowFarAlongTheRouteWeAre() {
        // À hauteur du quatrième point, soit ~445 m depuis le départ.
        val progress = tripRouteProgress(listOf(northbound), 48.8040, 2.3000)!!

        assertEquals(0, progress.legOrdinal)
        assertEquals(445.0, progress.distanceIntoLegMeters, 15.0)
        assertEquals(445.0, progress.distanceFromStartMeters, 15.0)
        assertEquals(0.0, progress.bearingDegrees, 1.0)
    }

    @Test
    fun countsTheLegsBeforeTheCurrentOne() {
        val progress = tripRouteProgress(listOf(northbound, eastbound), 48.8100, 2.3030)!!

        assertEquals(1, progress.legOrdinal)
        // Le kilométrage depuis le départ inclut le premier segment ; celui « dans le segment » non.
        assertTrue(progress.distanceFromStartMeters > progress.distanceIntoLegMeters + 1_000.0)
        assertEquals(90.0, progress.bearingDegrees, 2.0)
    }

    @Test
    fun goesBackwardsWhenWeDo() {
        val forward = tripRouteProgress(listOf(northbound), 48.8080, 2.3000)!!
        val backward = tripRouteProgress(
            listOf(northbound), 48.8030, 2.3000,
            previousDistanceFromStartMeters = forward.distanceFromStartMeters
        )!!

        // Rien n'est consommé définitivement : faire demi-tour fait reculer la progression, et le
        // trait effacé réapparaît.
        assertTrue(backward.distanceFromStartMeters < forward.distanceFromStartMeters)
    }

    @Test
    fun staysOnTheOutboundLegOfARouteThatDoublesBack() {
        // Aller au nord puis retour par le même chemin : les deux passages sont à distance nulle du
        // point interrogé. Seule la continuité peut les départager.
        val back = northbound.reversed()
        val legs = listOf(northbound, back)

        val outbound = tripRouteProgress(legs, 48.8050, 2.3000, previousDistanceFromStartMeters = 400.0)!!
        val returning = tripRouteProgress(legs, 48.8050, 2.3000, previousDistanceFromStartMeters = 1_700.0)!!

        assertEquals(0, outbound.legOrdinal)
        assertEquals(1, returning.legOrdinal)
    }

    @Test
    fun knowsNothingWhenTooFarFromTheRoute() {
        // ~2 km à l'ouest : hors de portée, le tracé doit rester affiché entier.
        assertNull(tripRouteProgress(listOf(northbound), 48.8050, 2.2700))
        assertNotNull(tripRouteProgress(listOf(northbound), 48.8050, 2.3000))
    }

    // --- Découpe du trait ---------------------------------------------------------------------

    @Test
    fun cutsTheLineExactlyWhereWeStand() {
        val remaining = remainingLegPoints(northbound, fromDistanceMeters = 445.0)

        // Le premier point rendu est l'endroit atteint, pas le sommet suivant : sans quoi le trait
        // se raccourcirait par à-coups.
        assertEquals(48.8040, remaining.first()[0], 0.0005)
        assertEquals(northbound.last()[0], remaining.last()[0], 1e-9)
    }

    @Test
    fun keepsTheWholeLineBeforeWeStart() {
        assertEquals(northbound.size, remainingLegPoints(northbound, 0.0).size)
        assertEquals(northbound.size, remainingLegPoints(northbound, -50.0).size)
    }

    @Test
    fun leavesNothingOfALegAlreadyDone() {
        assertTrue(remainingLegPoints(northbound, 5_000.0).isEmpty())
    }

    // --- Cap aligné sur le tracé --------------------------------------------------------------

    @Test
    fun followsTheLineWhenHeadingTheSameWay() {
        // Cap GPS qui frétille de quelques degrés autour d'une route droite : c'est la route qui
        // doit commander, sinon la flèche tremble sur une ligne parfaitement droite.
        assertEquals(90.0, headingAlignedToRoute(measuredDegrees = 96.0, routeDegrees = 90.0)!!, 0.001)
        assertEquals(90.0, headingAlignedToRoute(measuredDegrees = 84.0, routeDegrees = 90.0)!!, 0.001)
    }

    @Test
    fun keepsTheMeasuredHeadingOnAUTurn() {
        // Demi-tour : aligner la flèche sur le tracé ferait mentir l'affichage sur ce qu'on fait.
        assertEquals(270.0, headingAlignedToRoute(measuredDegrees = 270.0, routeDegrees = 90.0)!!, 0.001)
    }

    @Test
    fun takesTheShortWayAroundNorthToCompare() {
        // 350° et 10° sont à 20° l'un de l'autre, pas à 340 : l'alignement doit s'appliquer.
        assertEquals(10.0, headingAlignedToRoute(measuredDegrees = 350.0, routeDegrees = 10.0)!!, 0.001)
    }

    @Test
    fun fallsBackOnWhicheverIsKnown() {
        assertEquals(90.0, headingAlignedToRoute(measuredDegrees = null, routeDegrees = 90.0)!!, 0.001)
        assertEquals(42.0, headingAlignedToRoute(measuredDegrees = 42.0, routeDegrees = null)!!, 0.001)
        assertNull(headingAlignedToRoute(measuredDegrees = null, routeDegrees = null))
    }
}
