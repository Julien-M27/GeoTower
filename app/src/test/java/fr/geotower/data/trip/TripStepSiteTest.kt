package fr.geotower.data.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripStepSiteTest {
    private val stepLat = 48.8000
    private val stepLon = 2.3000

    /** ~11 m au nord par 0,0001° de latitude : de quoi placer des stations au mètre près. */
    private fun antenna(
        idAnfr: String,
        supportId: String,
        northMeters: Double = 0.0
    ) = TripStepAntennaRef(
        idAnfr = idAnfr,
        supportId = supportId,
        latitude = stepLat + northMeters / 111_320.0,
        longitude = stepLon
    )

    @Test
    fun groupsEveryStationOfTheSamePylon() {
        val supports = groupTripStepSupports(
            listOf(
                antenna("A1", "SUP1", northMeters = 10.0),
                antenna("A2", "SUP1", northMeters = 20.0),
                antenna("A3", "SUP1", northMeters = 30.0)
            ),
            stepLat, stepLon
        )

        assertEquals(1, supports.size)
        assertEquals(listOf("A1", "A2", "A3"), supports.first().idAnfrs)
    }

    @Test
    fun rankesSupportsByTheirClosestStation() {
        val supports = groupTripStepSupports(
            listOf(
                antenna("FAR", "SUP_FAR", northMeters = 120.0),
                antenna("NEAR", "SUP_NEAR", northMeters = 25.0),
                // Station lointaine du support proche : elle ne doit pas le reléguer.
                antenna("NEAR_2", "SUP_NEAR", northMeters = 140.0)
            ),
            stepLat, stepLon
        )

        assertEquals(listOf("SUP_NEAR", "SUP_FAR"), supports.map { it.supportId })
        assertEquals(25.0, supports.first().distanceMeters, 2.0)
        // Le support est représenté par SA station la plus proche : c'est elle qu'on a devant soi.
        assertEquals("NEAR", supports.first().idAnfrs.first())
    }

    @Test
    fun leavesOutWhatIsBeyondTheRadius() {
        val supports = groupTripStepSupports(
            listOf(
                antenna("IN", "SUP_IN", northMeters = 100.0),
                antenna("OUT", "SUP_OUT", northMeters = 400.0)
            ),
            stepLat, stepLon
        )

        assertEquals(listOf("SUP_IN"), supports.map { it.supportId })
    }

    @Test
    fun keepsAStationThatHasNoKnownSupport() {
        // Support inconnu : la station se représente elle-même, sinon la photo n'aurait aucune
        // cible alors qu'on est devant le pylône.
        val supports = groupTripStepSupports(
            listOf(antenna("ORPHAN", supportId = "  ", northMeters = 15.0)),
            stepLat, stepLon
        )

        assertEquals(listOf("ORPHAN"), supports.map { it.supportId })
    }

    @Test
    fun findsNothingWorthProposing() {
        assertTrue(groupTripStepSupports(emptyList(), stepLat, stepLon).isEmpty())
        assertTrue(
            groupTripStepSupports(
                listOf(antenna("A", "SUP", northMeters = 10.0)),
                stepLat, stepLon, radiusMeters = 0.0
            ).isEmpty()
        )
        // Coordonnées inexploitables : on ne peut ni mesurer ni classer.
        assertTrue(
            groupTripStepSupports(
                listOf(TripStepAntennaRef("A", "SUP", Double.NaN, stepLon)),
                stepLat, stepLon
            ).isEmpty()
        )
    }

    // --- Rectangle de lecture en base ---------------------------------------------------------

    @Test
    fun coversTheWholeRadius() {
        val box = tripStepSearchBox(stepLat, stepLon, radiusMeters = 150.0)

        // Les quatre bords sont bien à 150 m du point.
        assertEquals(150.0, haversineMeters(stepLat, stepLon, box[0], stepLon), 1.0)
        assertEquals(150.0, haversineMeters(stepLat, stepLon, box[2], stepLon), 1.0)
        assertEquals(150.0, haversineMeters(stepLat, stepLon, stepLat, box[1]), 1.0)
        assertEquals(150.0, haversineMeters(stepLat, stepLon, stepLat, box[3]), 1.0)
    }

    @Test
    fun widensInLongitudeAsLatitudeRises() {
        // Un degré de longitude rétrécit vers les pôles : à Dunkerque, 150 m couvrent plus de degrés
        // qu'à Cayenne. Une conversion en degrés constante manquerait des sites d'un côté ou de
        // l'autre — l'application couvre les deux.
        val north = tripStepSearchBox(51.0, 2.3)
        val south = tripStepSearchBox(4.9, -52.3)

        assertTrue((north[1] - north[3]) > (south[1] - south[3]))
    }
}
