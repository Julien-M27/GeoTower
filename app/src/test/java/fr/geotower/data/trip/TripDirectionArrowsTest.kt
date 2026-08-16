package fr.geotower.data.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripDirectionArrowsTest {
    /** Une ligne droite du sud vers le nord, d'environ 11 km. */
    private val northbound = listOf(doubleArrayOf(48.70, 2.30), doubleArrayOf(48.80, 2.30))

    /** Une ligne droite d'ouest en est, d'environ 7 km à cette latitude. */
    private val eastbound = listOf(doubleArrayOf(48.80, 2.30), doubleArrayOf(48.80, 2.40))

    @Test
    fun pointsNorthOnANorthboundLeg() {
        val arrows = tripDirectionArrows(northbound)

        assertTrue(arrows.isNotEmpty())
        arrows.forEach { assertEquals(0.0, it.bearingDegrees, 1.0) }
    }

    @Test
    fun pointsEastOnAnEastboundLeg() {
        val arrows = tripDirectionArrows(eastbound)

        assertTrue(arrows.isNotEmpty())
        arrows.forEach { assertEquals(90.0, it.bearingDegrees, 1.0) }
    }

    @Test
    fun reversesWhenTheLegIsReversed() {
        val arrows = tripDirectionArrows(northbound.reversed())

        arrows.forEach { assertEquals(180.0, it.bearingDegrees, 1.0) }
    }

    @Test
    fun neverExceedsTheArrowBudget() {
        // 11 km : sans plafond, un espacement de 150 m en produirait plus de 70.
        assertTrue(tripDirectionArrows(northbound).size <= TRIP_ARROWS_PER_LEG)
    }

    @Test
    fun spreadsThemAlongTheWholeLeg() {
        val arrows = tripDirectionArrows(northbound)

        // Réparties, pas entassées : la première dans la première moitié, la dernière dans la seconde.
        assertTrue(arrows.first().latitude < 48.75)
        assertTrue(arrows.last().latitude > 48.75)
        // Et toutes sur le segment.
        arrows.forEach { assertTrue(it.latitude in 48.70..48.80) }
    }

    @Test
    fun keepsThemApartOnAShortLeg() {
        // ~330 m : l'espacement minimal l'emporte sur la répartition, donc une ou deux flèches.
        val short = listOf(doubleArrayOf(48.8000, 2.3000), doubleArrayOf(48.8030, 2.3000))

        val arrows = tripDirectionArrows(short)

        assertTrue("${arrows.size} flèches", arrows.size in 1..2)
    }

    @Test
    fun drawsNothingOnATinyOrDegenerateLeg() {
        assertEquals(emptyList<TripArrow>(), tripDirectionArrows(listOf(doubleArrayOf(48.80, 2.30))))
        assertEquals(
            emptyList<TripArrow>(),
            tripDirectionArrows(listOf(doubleArrayOf(48.80, 2.30), doubleArrayOf(48.80, 2.30)))
        )
        // 30 m : plus court que la demi-période, rien à afficher.
        val tiny = listOf(doubleArrayOf(48.80000, 2.30000), doubleArrayOf(48.80027, 2.30000))
        assertEquals(emptyList<TripArrow>(), tripDirectionArrows(tiny))
    }

    // --- Rail de défilement ----------------------------------------------------------------

    @Test
    fun samplesTheWholeTourAtAConstantStep() {
        val tour = plan(listOf(step(48.70, 2.30), step(48.80, 2.30), step(48.90, 2.30)))

        val track = tripFlowTrack(tour, sampleCount = 100)

        assertEquals(100, track.size)
        // Pas constant : c'est ce qui referme la boucle sans saut, chaque flèche prenant la place
        // de la suivante après un pas complet.
        val first = haversineMeters(
            track[0].latitude, track[0].longitude, track[1].latitude, track[1].longitude
        )
        val middle = haversineMeters(
            track[50].latitude, track[50].longitude, track[51].latitude, track[51].longitude
        )
        assertEquals(first, middle, 1.0)
    }

    @Test
    fun runsFromTheStartToTheEndInOrder() {
        val tour = plan(listOf(step(48.70, 2.30), step(48.90, 2.30)))

        val track = tripFlowTrack(tour, sampleCount = 50)

        assertEquals(48.70, track.first().latitude, 1e-3)
        assertTrue(track.last().latitude > 48.88)
        // Cap au nord tout du long.
        track.forEach { assertEquals(0.0, it.bearingDegrees, 1.0) }
    }

    @Test
    fun keepsFollowingTheRouteThroughATurn() {
        val corner = listOf(
            doubleArrayOf(48.70, 2.30),
            doubleArrayOf(48.80, 2.30),
            doubleArrayOf(48.80, 2.40)
        )
        val tour = plan(
            listOf(step(48.70, 2.30), step(48.80, 2.40)),
            legs = listOf(leg(0, 1).copy(encodedGeometry = TripGeometryCodec.encode(corner)))
        )

        val track = tripFlowTrack(tour, sampleCount = 100)

        assertTrue(track.any { it.bearingDegrees < 5.0 || it.bearingDegrees > 355.0 })
        assertTrue(track.any { kotlin.math.abs(it.bearingDegrees - 90.0) < 5.0 })
    }

    @Test
    fun hasNoRailWithoutATour() {
        assertEquals(emptyList<TripArrow>(), tripFlowTrack(plan(listOf(step(48.80, 2.30)))))
        assertEquals(emptyList<TripArrow>(), tripFlowTrack(plan(ladder(2)), sampleCount = 1))
        // Deux étapes confondues : longueur nulle, donc aucun rail à parcourir.
        val degenerate = plan(listOf(step(48.80, 2.30), step(48.80, 2.30)))
        assertEquals(emptyList<TripArrow>(), tripFlowTrack(degenerate))
    }

    @Test
    fun followsEachTurnOfASinuousLeg() {
        // Un L : nord puis est. Les flèches du premier tronçon pointent au nord, celles du second
        // à l'est -- une flèche ne doit pas hériter du cap d'un autre tronçon.
        val corner = listOf(
            doubleArrayOf(48.70, 2.30),
            doubleArrayOf(48.80, 2.30),
            doubleArrayOf(48.80, 2.40)
        )

        val arrows = tripDirectionArrows(corner, maxArrows = 6)

        assertTrue(arrows.any { it.bearingDegrees < 5.0 || it.bearingDegrees > 355.0 })
        assertTrue(arrows.any { kotlin.math.abs(it.bearingDegrees - 90.0) < 5.0 })
    }
}
