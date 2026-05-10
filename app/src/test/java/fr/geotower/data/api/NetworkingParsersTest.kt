package fr.geotower.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkingParsersTest {
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
}
