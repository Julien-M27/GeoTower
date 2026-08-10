package fr.geotower.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkingParsersTest {
    @Test
    fun dataCoverage_flagsKnownUnsupportedCountriesOnly() {
        assertFalse(GeoTowerDataCoverage.isKnownUnsupportedCountryCode("FR"))
        assertFalse(GeoTowerDataCoverage.isKnownUnsupportedCountryCode("gp"))
        assertFalse(GeoTowerDataCoverage.isKnownUnsupportedCountryCode(null))
        assertTrue(GeoTowerDataCoverage.isKnownUnsupportedCountryCode("BE"))
        assertTrue(GeoTowerDataCoverage.nominatimCountryCodes.split(",").contains("fr"))
    }

    @Test
    fun parseNominatimArea_validResult() {
        val json = """
            [
              {
                "boundingbox": ["48.80", "48.90", "2.20", "2.40"],
                "geojson": {
                  "type": "Polygon",
                  "coordinates": [
                    [[2.20, 48.80], [2.40, 48.80], [2.40, 48.90], [2.20, 48.90], [2.20, 48.80]]
                  ]
                }
              }
            ]
        """.trimIndent()

        val area = parseNominatimArea(json)

        assertNotNull(area)
        assertEquals(48.90, area!!.latNorth, 0.0)
        assertEquals(2.40, area.lonEast, 0.0)
        assertEquals(48.80, area.latSouth, 0.0)
        assertEquals(2.20, area.lonWest, 0.0)
        assertTrue(area.geoJsonFeature!!.contains("\"type\":\"Feature\""))
        assertEquals(1, area.polygons.size)
        assertEquals(5, area.polygons.first().size)
    }

    @Test
    fun parseNominatimArea_emptyResultReturnsNull() {
        assertNull(parseNominatimArea("[]"))
    }

    @Test
    fun parseNominatimArea_prefersResultWithPolygon() {
        val json = """
            [
              {
                "boundingbox": ["33.30", "33.90", "-118.10", "-117.40"],
                "geojson": {
                  "type": "Point",
                  "coordinates": [-117.85, 33.78]
                }
              },
              {
                "boundingbox": ["44.10", "44.20", "4.70", "4.90"],
                "geojson": {
                  "type": "Polygon",
                  "coordinates": [
                    [[4.70, 44.10], [4.90, 44.10], [4.90, 44.20], [4.70, 44.20], [4.70, 44.10]]
                  ]
                }
              }
            ]
        """.trimIndent()

        val area = parseNominatimArea(json)

        assertNotNull(area)
        assertEquals(44.20, area!!.latNorth, 0.0)
        assertEquals(4.90, area.lonEast, 0.0)
        assertEquals(1, area.polygons.size)
    }

    @Test
    fun parseCellularFrPhotos_validPhotos() {
        val json = """
            {
              "photos": [
                {
                  "url": "/uploads/site/photo.jpg",
                  "nickname": "Alice",
                  "uploadDate": "2026-05-01"
                }
              ]
            }
        """.trimIndent()

        val photos = parseCellularFrPhotos(json)

        assertEquals(1, photos.size)
        assertEquals("https://cellularfr.fr/uploads/site/photo.jpg", photos.first().url)
        assertEquals("Alice", photos.first().author)
        assertEquals("2026-05-01", photos.first().uploadedAt)
    }

    @Test
    fun parseCellularFrPhotos_missingPhotosReturnsEmptyList() {
        assertTrue(parseCellularFrPhotos("""{"ok":true}""").isEmpty())
    }

    @Test
    fun parseCellularFrPhotos_rejectsMalformedRelativeUrls() {
        val json = """
            {
              "photos": [
                {"url": "uploads/site/photo.jpg"},
                {"url": "//evil.example/photo.jpg"},
                {"url": "https://evil.example/photo.jpg"},
                {"url": "/uploads/site/valid.jpg"}
              ]
            }
        """.trimIndent()

        val photos = parseCellularFrPhotos(json)

        assertEquals(1, photos.size)
        assertEquals("https://cellularfr.fr/uploads/site/valid.jpg", photos.first().url)
        assertNull(CellularFrApi.resolvePhotoUrl("//evil.example/photo.jpg"))
    }

    @Test
    fun parseElevationProfile_validProfile() {
        val json = """
            {
              "elevations": [
                {"lat": 48.0, "lon": 2.0, "z": 35.0},
                {"lat": 48.001, "lon": 2.0, "z": 40.0}
              ]
            }
        """.trimIndent()

        val profile = parseElevationProfile(json, fallbackDistanceMeters = 120f)

        assertEquals(2, profile.points.size)
        assertEquals(35.0, profile.points.first().elevation, 0.0)
        assertTrue(profile.distanceMeters > 0f)
    }

    @Test
    fun parseRoutePath_readsGeometryAndAnnouncedDistance() {
        val json = """
            {
              "distance": 1234.5,
              "duration": 300,
              "geometry": {
                "type": "LineString",
                "coordinates": [[2.0, 48.0], [2.001, 48.0], [2.001, 48.001]]
              }
            }
        """.trimIndent()

        val route = parseRoutePath(json)

        assertEquals(3, route.points.size)
        // Les coordonnées GeoJSON arrivent en [lon, lat] et repartent en [lat, lon].
        assertEquals(48.0, route.points.first()[0], 0.0)
        assertEquals(2.0, route.points.first()[1], 0.0)
        assertEquals(1234.5, route.distanceMeters, 0.0)
    }

    @Test
    fun parseRoutePath_fallsBackToGeometryLengthAndRejectsErrors() {
        val withoutDistance = """
            {
              "geometry": {
                "type": "LineString",
                "coordinates": [[2.0, 48.0], [2.0, 48.01]]
              }
            }
        """.trimIndent()

        // Sans champ distance, la longueur est recalculée depuis la géométrie (~1,1 km).
        assertEquals(1112.0, parseRoutePath(withoutDistance).distanceMeters, 5.0)

        // Réponse d'erreur du service : aucun itinéraire exploitable.
        assertThrows(IllegalStateException::class.java) {
            parseRoutePath("""{"message": "no route found"}""")
        }
    }

    @Test
    fun routeApi_rejectsRoutesSnappedOutsideTheRequestedArea() {
        val paris = RoutePathResult(
            points = listOf(doubleArrayOf(48.8600, 2.3370), doubleArrayOf(48.8560, 2.3520)),
            distanceMeters = 1170.0
        )
        assertTrue(
            RouteApi.isRouteAnchoredOnRequest(paris, 48.8601, 2.3371, 48.8559, 2.3519)
        )

        // Hors couverture BD TOPO, le service rabat les points sur le réseau le plus proche : le
        // tracé renvoyé n'a alors plus rien à voir avec les points demandés (Berlin -> Alsace).
        val snapped = RoutePathResult(
            points = listOf(doubleArrayOf(48.9764, 8.2216), doubleArrayOf(48.9764, 8.2216)),
            distanceMeters = 0.0
        )
        assertFalse(
            RouteApi.isRouteAnchoredOnRequest(snapped, 52.5200, 13.4000, 52.5000, 13.4200)
        )
    }
}
