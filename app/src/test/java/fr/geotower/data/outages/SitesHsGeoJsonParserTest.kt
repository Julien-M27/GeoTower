package fr.geotower.data.outages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SitesHsGeoJsonParserTest {

    @Test
    fun parsesValidGeoJsonWithFeaturesAndMetadata() {
        val geoJson = """
            {
                "type": "FeatureCollection",
                "metadata": {
                    "generated_at": "2026-10-05T12:00:00Z"
                },
                "features": [
                    {
                        "type": "Feature",
                        "geometry": {
                            "type": "Point",
                            "coordinates": [2.3522, 48.8566]
                        },
                        "properties": {
                            "station_anfr": "0751234567",
                            "operateur": "Orange",
                            "departement": "75",
                            "commune": "PARIS",
                            "voix2g": "1",
                            "data4g": "1",
                            "data5g": "0",
                            "raison": "Maintenance",
                            "propre": 1,
                            "debut": "2026-10-05"
                        }
                    },
                    {
                        "type": "Feature",
                        "geometry": {
                            "type": "Point",
                            "coordinates": [4.8357, 45.7640]
                        },
                        "properties": {
                            "station_anfr": "0691234567",
                            "operateur": "Free",
                            "commune": "LYON",
                            "data4g": "1"
                        }
                    }
                ]
            }
        """.trimIndent()

        val result = SitesHsGeoJsonParser.parse(geoJson, sourceLastUpdate = "05/10/2026")

        assertEquals("2026-10-05T12:00:00Z", result.generatedAtIso)
        assertEquals(2, result.sites.size)

        val first = result.sites[0]
        assertEquals("0751234567", first.idAnfr)
        assertEquals("Orange", first.operateur)
        assertEquals(48.8566, first.latitude, 0.0001)
        assertEquals(2.3522, first.longitude, 0.0001)
        assertEquals("PARIS", first.commune)
        assertEquals("75", first.departement)
        assertEquals("1", first.voix2g)
        assertEquals("1", first.data4g)
        assertEquals("0", first.data5g)
        assertEquals("Maintenance", first.raison)
        assertEquals(1, first.propre)
        assertEquals("2026-10-05", first.dateDebut)
        assertEquals("05/10/2026", first.sourceLastUpdate)

        val second = result.sites[1]
        assertEquals("0691234567", second.idAnfr)
        assertEquals("Free", second.operateur)
        assertEquals(45.7640, second.latitude, 0.0001)
        assertEquals(4.8357, second.longitude, 0.0001)
        assertEquals("LYON", second.commune)
        assertNull(second.raison)
    }

    @Test
    fun ignoresFeaturesWithoutOperator() {
        val geoJson = """
            {
                "type": "FeatureCollection",
                "features": [
                    {
                        "type": "Feature",
                        "geometry": {
                            "type": "Point",
                            "coordinates": [2.3522, 48.8566]
                        },
                        "properties": {
                            "station_anfr": "0751234567",
                            "operateur": ""
                        }
                    }
                ]
            }
        """.trimIndent()

        val result = SitesHsGeoJsonParser.parse(geoJson)
        assertEquals(0, result.sites.size)
    }

    @Test
    fun parsesEmptyFeatureCollection() {
        val geoJson = """
            {
                "type": "FeatureCollection",
                "features": []
            }
        """.trimIndent()

        val result = SitesHsGeoJsonParser.parse(geoJson)
        assertEquals(0, result.sites.size)
        assertNull(result.generatedAtIso)
    }
}
