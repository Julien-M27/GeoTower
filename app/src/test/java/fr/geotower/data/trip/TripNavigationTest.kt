package fr.geotower.data.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripNavigationTest {
    @Test
    fun holdsTheLastHeadingWhileStandingStill() {
        val smoother = TripHeadingSmoother()
        smoother.update(bearingDegrees = 90.0, speedMetersPerSecond = 5.0)

        // À l'arrêt, le cap GPS tourne au gré du bruit : la carte ne doit pas le suivre.
        smoother.update(bearingDegrees = 270.0, speedMetersPerSecond = 0.1)
        smoother.update(bearingDegrees = 12.0, speedMetersPerSecond = 0.0)

        assertEquals(90.0, smoother.headingDegrees!!, 0.001)
    }

    @Test
    fun adoptsTheFirstReliableHeadingImmediately() {
        val smoother = TripHeadingSmoother()

        // Sans cela, la carte mettrait plusieurs secondes à s'orienter au démarrage.
        assertEquals(135.0, smoother.update(135.0, 5.0)!!, 0.001)
    }

    // --- Cap de repli, lu sur le tracé ------------------------------------------------------

    @Test
    fun readsTheHeadingOffTheRouteWhenStandingStill() {
        // Un tracé plein est : à l'arrêt, la carte doit s'orienter comme si on le parcourait.
        val eastbound = listOf(doubleArrayOf(48.80, 2.30), doubleArrayOf(48.80, 2.40))

        assertEquals(90.0, routeHeadingAhead(eastbound)!!, 1.0)
    }

    @Test
    fun ignoresAShortWiggleAtTheVeryStart() {
        // Sortie de parking de quelques mètres plein ouest, puis la route part au nord : c'est le
        // nord qui doit commander, sinon la carte se mettrait de travers dès le départ.
        val wiggle = listOf(
            doubleArrayOf(48.8000, 2.3000),
            doubleArrayOf(48.8000, 2.2999),
            doubleArrayOf(48.8050, 2.2999)
        )

        val heading = routeHeadingAhead(wiggle)!!

        assertTrue("cap=$heading", heading < 5.0 || heading > 355.0)
    }

    @Test
    fun stopsLookingAheadAtTheGivenDistance() {
        // Cent mètres au nord puis un virage plein est : en ne regardant que 100 m devant, le
        // virage ne doit pas encore compter.
        val corner = listOf(
            doubleArrayOf(48.8000, 2.3000),
            doubleArrayOf(48.8009, 2.3000),
            doubleArrayOf(48.8009, 2.4000)
        )

        val heading = routeHeadingAhead(corner, aheadMeters = 100.0)!!

        assertEquals(0.0, heading, 1.0)
    }

    @Test
    fun takesWhatItHasOnARouteShorterThanTheLookahead() {
        // 30 m plein nord : plus court que la portée, mais le cap reste lisible.
        val short = listOf(doubleArrayOf(48.80000, 2.30000), doubleArrayOf(48.80027, 2.30000))

        assertEquals(0.0, routeHeadingAhead(short)!!, 1.0)
    }

    @Test
    fun hasNoFallbackHeadingWithoutARoute() {
        assertNull(routeHeadingAhead(emptyList()))
        assertNull(routeHeadingAhead(listOf(doubleArrayOf(48.80, 2.30))))
        // Points confondus : aucune direction à en tirer.
        assertNull(
            routeHeadingAhead(listOf(doubleArrayOf(48.80, 2.30), doubleArrayOf(48.80, 2.30)))
        )
    }

    @Test
    fun staysSilentUntilAReliableHeadingArrives() {
        val smoother = TripHeadingSmoother()

        assertNull(smoother.update(bearingDegrees = 90.0, speedMetersPerSecond = 0.2))
        assertNull(smoother.update(bearingDegrees = null, speedMetersPerSecond = 8.0))
        assertNull(smoother.headingDegrees)
    }

    @Test
    fun turnsProgressivelyRatherThanJumping() {
        val smoother = TripHeadingSmoother()
        smoother.update(0.0, 5.0)

        val afterOne = smoother.update(90.0, 5.0)!!

        // Un quart du chemin par mesure : la carte tourne, elle ne saute pas.
        assertEquals(22.5, afterOne, 0.001)
        repeat(30) { smoother.update(90.0, 5.0) }
        assertEquals(90.0, smoother.headingDegrees!!, 0.5)
    }

    @Test
    fun takesTheShortWayAroundNorth() {
        val smoother = TripHeadingSmoother()
        smoother.update(350.0, 5.0)

        val next = smoother.update(10.0, 5.0)!!

        // De 350 à 10 on passe par 0, pas par 180 : le résultat doit rester près du nord.
        assertTrue("cap=$next", next > 350.0 || next < 10.0)
    }

    @Test
    fun ignoresAnUnusableBearing() {
        val smoother = TripHeadingSmoother()
        smoother.update(45.0, 5.0)

        smoother.update(Double.NaN, 5.0)

        assertEquals(45.0, smoother.headingDegrees!!, 0.001)
    }

    @Test
    fun resolvesTheShortestDeltaAcrossTheWrap() {
        assertEquals(20.0, shortestDeltaDegrees(350.0, 10.0), 0.001)
        assertEquals(-20.0, shortestDeltaDegrees(10.0, 350.0), 0.001)
        assertEquals(180.0, shortestDeltaDegrees(0.0, 180.0), 0.001)
    }

    @Test
    fun scalesTheGroundResolutionWithZoom() {
        // Repère connu : à l'équateur, zoom 0, une tuile de 256 px couvre le tour du globe.
        assertEquals(156_543.0, metersPerPixel(0.0, 0.0), 1.0)
        // Un cran de zoom divise l'échelle par deux.
        assertEquals(metersPerPixel(48.85, 16.0) / 2.0, metersPerPixel(48.85, 17.0), 0.001)
        // Et la longitude se resserre avec la latitude.
        assertTrue(metersPerPixel(60.0, 15.0) < metersPerPixel(0.0, 15.0))
    }

    @Test
    fun walksTheRightDistanceInTheRightDirection() {
        val north = destinationPoint(48.80, 2.30, bearingDegrees = 0.0, distanceMeters = 1_000.0)

        assertTrue(north[0] > 48.80)
        assertEquals(2.30, north[1], 1e-6)
        assertEquals(1_000.0, haversineMeters(48.80, 2.30, north[0], north[1]), 1.0)

        val east = destinationPoint(48.80, 2.30, bearingDegrees = 90.0, distanceMeters = 1_000.0)
        assertTrue(east[1] > 2.30)
        assertEquals(48.80, east[0], 1e-3)
    }

    @Test
    fun aimsAheadSoTheUserSitsLowOnTheScreen() {
        val target = navigationCameraTarget(
            latitude = 48.80,
            longitude = 2.30,
            headingDegrees = 0.0,
            zoom = NAV_FOLLOW_ZOOM,
            screenHeightPixels = 2_000
        )

        // Cap au nord : la caméra vise plus au nord que l'utilisateur, qui se retrouve donc en bas.
        assertTrue(target[0] > 48.80)
        // Et pas n'importe où : un quart d'écran devant, soit une distance modeste à ce zoom.
        val ahead = haversineMeters(48.80, 2.30, target[0], target[1])
        assertEquals(2_000 * 0.25 * metersPerPixel(48.80, NAV_FOLLOW_ZOOM), ahead, 5.0)
    }

    @Test
    fun staysPutWhenTheScreenSizeIsUnknown() {
        val target = navigationCameraTarget(48.80, 2.30, 0.0, screenHeightPixels = 0)

        assertEquals(48.80, target[0], 1e-9)
        assertEquals(2.30, target[1], 1e-9)
    }
}
