package fr.geotower.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Forme relevée sur le service le 2026-08-11 : une portion par segment, sans géométrie propre, et
 * des instructions en codes anglais. Le jeu d'essai reprend des rues et des manœuvres réelles.
 */
class RoutePortionsParserTest {
    private val response = """
        {
          "distance": 6154.4,
          "duration": 1016.3,
          "geometry": { "type": "LineString", "coordinates": [[2.349176, 48.853606]] },
          "portions": [
            {
              "start": "2.3488,48.8534",
              "end": "2.3400,48.8600",
              "distance": 561.5,
              "duration": "99.9",
              "steps": [
                {
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [[2.349176, 48.853606], [2.350561, 48.853152], [2.352197, 48.852658]]
                  },
                  "attributes": { "name": { "nom_1_gauche": "R DU CLOITRE NOTRE-DAME", "cpx_numero": "" } },
                  "distance": 245.1,
                  "duration": 45.8,
                  "instruction": { "type": "depart", "modifier": "right" }
                },
                {
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [[2.352197, 48.852658], [2.350282, 48.855069]]
                  },
                  "attributes": { "name": { "nom_1_gauche": "" } },
                  "distance": 316.4,
                  "duration": 54.1,
                  "instruction": { "type": "end of road", "modifier": "left" }
                }
              ]
            },
            {
              "start": "2.3400,48.8600",
              "end": "2.2945,48.8584",
              "duration": 87.0,
              "steps": [
                {
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [[2.350282, 48.855069], [2.346476, 48.856190]]
                  },
                  "attributes": { "name": { "nom_1_gauche": "QU DE L'HORLOGE" } },
                  "distance": 305.6,
                  "duration": 87.0,
                  "instruction": { "type": "arrive" }
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun readsOnePortionPerRequestedSegment() {
        assertEquals(2, parseRoutePortions(response).size)
    }

    @Test
    fun rebuildsPortionGeometryFromItsSteps() {
        val portion = parseRoutePortions(response).first()

        // Deux steps de 3 et 2 points partageant leur jonction : 4 points, pas 5. Le doublon à
        // chaque manœuvre gonflerait le tracé pour rien.
        assertEquals(4, portion.points.size)
        assertEquals(48.853606, portion.points.first()[0], 1e-6)
        assertEquals(2.349176, portion.points.first()[1], 1e-6)
        assertEquals(48.855069, portion.points.last()[0], 1e-6)
    }

    @Test
    fun readsDistanceAndDurationIncludingNumbersSentAsStrings() {
        val portion = parseRoutePortions(response).first()

        assertEquals(561.5, portion.distanceMeters, 0.01)
        assertEquals(99.9, portion.durationSeconds, 0.01)
    }

    @Test
    fun fallsBackToTheGeometryLengthWhenDistanceIsMissing() {
        val portion = parseRoutePortions(response)[1]

        assertTrue("distance=${portion.distanceMeters}", portion.distanceMeters > 250.0)
    }

    @Test
    fun keepsManeuverCodesRawAndDropsUnnamedRoads() {
        val maneuvers = parseRoutePortions(response).first().maneuvers

        assertEquals(2, maneuvers.size)
        assertEquals("depart", maneuvers[0].type)
        assertEquals("right", maneuvers[0].modifier)
        assertEquals("R DU CLOITRE NOTRE-DAME", maneuvers[0].roadName)
        assertEquals("end of road", maneuvers[1].type)
        // Voie sans nom : le service rend "", qui ne doit pas ressortir comme un nom vide.
        assertNull(maneuvers[1].roadName)
    }

    @Test
    fun toleratesAManeuverWithoutModifier() {
        val arrival = parseRoutePortions(response)[1].maneuvers.single()

        assertEquals("arrive", arrival.type)
        assertNull(arrival.modifier)
        assertEquals("QU DE L'HORLOGE", arrival.roadName)
    }
}
