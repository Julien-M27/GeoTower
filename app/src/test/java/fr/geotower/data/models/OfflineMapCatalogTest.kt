package fr.geotower.data.models

import fr.geotower.data.workers.OfflineMapDownloadValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMapCatalogTest {

    @Test
    fun everyEmbeddedEntryPassesCatalogValidation() {
        assertTrue(OfflineMapCatalog.entries.isNotEmpty())
        OfflineMapCatalog.entries.forEach { entry ->
            assertTrue(
                "Entrée de catalogue invalide: ${entry.id}",
                OfflineMapDownloadValidator.isValidCatalogEntry(entry)
            )
        }
    }

    @Test
    fun entryIdsAreUniqueAndUrlsDerivedFromId() {
        val ids = OfflineMapCatalog.entries.map { it.id }
        assertEquals("Des id de région sont dupliqués", ids.size, ids.toSet().size)

        OfflineMapCatalog.entries.forEach { entry ->
            assertEquals("${entry.id}.map", entry.mapFilename)
            assertTrue(entry.mapUrl.endsWith("/${entry.id}.map"))
            assertTrue(entry.mapUrl.startsWith("https://download.mapsforge.org/"))
            // Le catalogue embarqué ne fige volontairement aucun hash.
            assertTrue(entry.sha256 == null)
        }
    }
}
