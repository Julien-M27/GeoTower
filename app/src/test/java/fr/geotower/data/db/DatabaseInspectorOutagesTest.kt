package fr.geotower.data.db

import com.google.gson.Gson
import fr.geotower.data.models.SiteHsEntity
import fr.geotower.data.outages.CachedServerOutages
import fr.geotower.data.outages.OutageSourceMode
import fr.geotower.data.outages.ServerOutageCache
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DatabaseInspectorOutagesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun outageDatabaseNotAvailableWhenFilesMissing() {
        val emptyDir = tempFolder.newFolder("empty_files")
        val inspector = DatabaseInspector(customFilesDir = emptyDir)

        assertFalse(inspector.isAvailable(InspectableDatabase.OUTAGES_OPERATORS))
        assertFalse(inspector.isAvailable(InspectableDatabase.OUTAGES_DAILY))

        assertThrows(DatabaseNotAvailableException::class.java) {
            inspector.listTables(InspectableDatabase.OUTAGES_OPERATORS)
        }
        assertThrows(DatabaseNotAvailableException::class.java) {
            inspector.readPage(InspectableDatabase.OUTAGES_OPERATORS, "sites_hs", page = 0)
        }
    }

    @Test
    fun outageDatabaseLoadsCachedServerOutagesAndProducesGeoJson() {
        val filesDir = tempFolder.newFolder("files_with_outages")
        val operatorsFile = File(filesDir, ServerOutageCache.fileFor(OutageSourceMode.OPERATORS))

        val site1 = SiteHsEntity(
            idAnfr = "075001",
            operateur = "Free Mobile",
            latitude = 48.8566,
            longitude = 2.3522,
            commune = "PARIS",
            departement = "75",
            voix2g = "HS",
            voix4g = "HS",
            raison = "Vandalisme",
            detail = "Câble sectionné",
            sourceLastUpdate = "21/10/2026",
        )
        val site2 = SiteHsEntity(
            idAnfr = "013002",
            operateur = "Orange",
            latitude = 43.2965,
            longitude = 5.3698,
            commune = "MARSEILLE",
            departement = "13",
            data4g = "HS",
            data5g = "HS",
            raison = "Coupure secteur",
            sourceLastUpdate = "21/10/2026",
        )

        val cached = CachedServerOutages(
            downloadedAtMillis = 1729540000000L,
            sourceLastUpdate = "21/10/2026",
            serverGeneratedAtMillis = 1729530000000L,
            sites = listOf(site1, site2),
        )
        operatorsFile.writeText(Gson().toJson(cached))

        val inspector = DatabaseInspector(customFilesDir = filesDir)

        assertTrue(inspector.isAvailable(InspectableDatabase.OUTAGES_OPERATORS))
        assertFalse(inspector.isAvailable(InspectableDatabase.OUTAGES_DAILY))

        val tables = inspector.listTables(InspectableDatabase.OUTAGES_OPERATORS)
        assertEquals(2, tables.size)
        assertEquals("sites_hs", tables[0].name)
        assertEquals(2L, tables[0].rowCount)
        assertEquals("metadata", tables[1].name)

        val columns = inspector.readTableColumns(InspectableDatabase.OUTAGES_OPERATORS, "sites_hs")
        assertTrue(columns.any { it.name == "id_anfr" })
        assertTrue(columns.any { it.name == "operateur" })
        assertTrue(columns.any { it.name == "commune" })
        assertTrue(columns.any { it.name == "geojson" })

        val page = inspector.readPage(
            InspectableDatabase.OUTAGES_OPERATORS,
            tableName = "sites_hs",
            page = 0,
            pageSize = 10,
        )
        assertEquals(2L, page.totalRows)
        assertEquals(2, page.rows.size)

        // Vérification de la première ligne
        val row1 = page.rows[0]
        val idAnfrIndex = columns.indexOfFirst { it.name == "id_anfr" }
        val opIndex = columns.indexOfFirst { it.name == "operateur" }
        val communeIndex = columns.indexOfFirst { it.name == "commune" }
        val geoJsonIndex = columns.indexOfFirst { it.name == "geojson" }

        assertEquals("075001", row1[idAnfrIndex])
        assertEquals("Free Mobile", row1[opIndex])
        assertEquals("PARIS", row1[communeIndex])

        // Le GeoJSON doit être un Feature valide
        val geoJsonStr = row1[geoJsonIndex]
        assertNotNull(geoJsonStr)
        val json = JSONObject(geoJsonStr!!)
        assertEquals("Feature", json.getString("type"))
        val props = json.getJSONObject("properties")
        assertEquals("075001", props.getString("station_anfr"))
        assertEquals("Free Mobile", props.getString("operateur"))
        assertEquals("PARIS", props.getString("commune"))
        val geometry = json.getJSONObject("geometry")
        assertEquals("Point", geometry.getString("type"))
        val coords = geometry.getJSONArray("coordinates")
        assertEquals(2.3522, coords.getDouble(0), 0.0001)
        assertEquals(48.8566, coords.getDouble(1), 0.0001)

        // Test recherche
        val filteredOrange = inspector.readPage(
            InspectableDatabase.OUTAGES_OPERATORS,
            tableName = "sites_hs",
            page = 0,
            searchQuery = "Orange",
        )
        assertEquals(1L, filteredOrange.totalRows)
        assertEquals("Orange", filteredOrange.rows[0][opIndex])

        val filteredParis = inspector.readPage(
            InspectableDatabase.OUTAGES_OPERATORS,
            tableName = "sites_hs",
            page = 0,
            searchQuery = "PARIS",
        )
        assertEquals(1L, filteredParis.totalRows)
        assertEquals("Free Mobile", filteredParis.rows[0][opIndex])

        // Test table metadata
        val metadataPage = inspector.readPage(
            InspectableDatabase.OUTAGES_OPERATORS,
            tableName = "metadata",
            page = 0,
        )
        assertTrue(metadataPage.totalRows > 0)
        val metaRows = metadataPage.rows.associate { it[0] to it[1] }
        assertEquals("sites_hs_server_operators.json", metaRows["fichier"])
        assertEquals("2", metaRows["nombre_sites"])
        assertEquals("21/10/2026", metaRows["date_source"])
    }

    @Test
    fun outageDatabaseSupportsRawGeoJsonForDaily() {
        val filesDir = tempFolder.newFolder("files_raw_daily")
        val dailyFile = File(filesDir, ServerOutageCache.fileFor(OutageSourceMode.DAILY))

        val rawGeoJson = """
            {
              "type": "FeatureCollection",
              "metadata": {
                "generated_at": "2026-10-21T10:00:00Z"
              },
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [4.8357, 45.7640]
                  },
                  "properties": {
                    "station_anfr": "069003",
                    "operateur": "Free",
                    "commune": "LYON",
                    "departement": "69",
                    "voix": "HS"
                  }
                }
              ]
            }
        """.trimIndent()
        dailyFile.writeText(rawGeoJson)

        val inspector = DatabaseInspector(customFilesDir = filesDir)
        assertTrue(inspector.isAvailable(InspectableDatabase.OUTAGES_DAILY))

        val page = inspector.readPage(
            InspectableDatabase.OUTAGES_DAILY,
            tableName = "sites_hs",
            page = 0,
        )
        assertEquals(1L, page.totalRows)
        val columns = inspector.readTableColumns(InspectableDatabase.OUTAGES_DAILY, "sites_hs")
        val opIndex = columns.indexOfFirst { it.name == "operateur" }
        // Vérifie la normalisation Free -> Free Mobile
        assertEquals("Free Mobile", page.rows[0][opIndex])
    }
}
