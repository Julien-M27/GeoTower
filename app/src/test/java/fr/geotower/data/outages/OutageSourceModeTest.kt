package fr.geotower.data.outages

import fr.geotower.data.build.OfficialSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OutageSourceModeTest {

    @Test
    fun outageSourceModeFromKeyParsesCorrectly() {
        assertEquals(OutageSourceMode.OPERATORS, OutageSourceMode.fromKey("operators"))
        assertEquals(OutageSourceMode.DAILY, OutageSourceMode.fromKey("daily"))
        assertEquals(OutageSourceMode.OPERATORS, OutageSourceMode.fromKey(null))
        assertEquals(OutageSourceMode.OPERATORS, OutageSourceMode.fromKey("unknown"))
    }

    @Test
    fun selectLatestDailyOutageResourceExtractsValidGeoJson() {
        val datasetJson = """
            {
                "resources": [
                    {
                        "title": "Fichier CSV ancien",
                        "format": "csv",
                        "url": "https://arcep.s3.rbx.io.cloud.ovh.net/sites-indisponibles/all/2026-10-01/raw.csv"
                    },
                    {
                        "title": "Fichier GeoJSON 2026-10-05",
                        "format": "geojson",
                        "url": "https://arcep.s3.rbx.io.cloud.ovh.net/sites-indisponibles/all/2026-10-05/raw2026-10-05.geojson"
                    }
                ]
            }
        """.trimIndent()

        val resource = OfficialSources.selectLatestDailyOutageResource(datasetJson)
        assertNotNull(resource)
        assertEquals("https://arcep.s3.rbx.io.cloud.ovh.net/sites-indisponibles/all/2026-10-05/raw2026-10-05.geojson", resource!!.url)
        assertEquals("2026-10-05", resource.date)
    }

    @Test
    fun selectLatestDailyOutageResourceRejectsDisallowedHosts() {
        val datasetJson = """
            {
                "resources": [
                    {
                        "title": "Fichier pirate",
                        "format": "geojson",
                        "url": "https://evil.com/fake.geojson"
                    }
                ]
            }
        """.trimIndent()

        val resource = OfficialSources.selectLatestDailyOutageResource(datasetJson)
        assertNull(resource)
    }
}
