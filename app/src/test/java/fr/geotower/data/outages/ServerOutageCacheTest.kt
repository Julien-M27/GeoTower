package fr.geotower.data.outages

import fr.geotower.data.models.SiteHsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ServerOutageCacheTest {

    @Test
    fun savesAndLoadsRoundTrip() {
        val file = File.createTempFile("sites_hs_server", ".json")
        try {
            val cache = ServerOutageCache(file)
            val site = SiteHsEntity(
                idAnfr = "0751234567", operateur = "Orange", latitude = 48.85, longitude = 2.35,
                data4g = "HS", dateDebut = "2026-01-01",
            )
            cache.save(CachedServerOutages(456L, "2026-08-07", 789L, listOf(site)))

            val loaded = cache.load()
            assertNotNull(loaded)
            assertEquals(456L, loaded!!.downloadedAtMillis)
            assertEquals("2026-08-07", loaded.sourceLastUpdate)
            assertEquals(789L, loaded.serverGeneratedAtMillis)
            assertEquals(1, loaded.sites.size)
            assertEquals("0751234567", loaded.sites[0].idAnfr)
            assertEquals("HS", loaded.sites[0].data4g)
        } finally {
            file.delete()
        }
    }

    @Test
    fun loadReturnsNullWhenMissing() {
        val file = File.createTempFile("server_outage_missing", ".json").also { it.delete() }
        assertNull(ServerOutageCache(file).load())
    }

    @Test
    fun loadReturnsNullWhenCorrupted() {
        val file = File.createTempFile("server_outage_corrupt", ".json")
        try {
            file.writeText("{ this is not valid json")
            assertNull(ServerOutageCache(file).load())
        } finally {
            file.delete()
        }
    }

    /**
     * Régression 2.0.11 : la copie posée par une version antérieure porte d'autres noms de champs
     * (R8 les renommait), `sites` revient donc `null`. C'est ce fichier-là qui fermait l'application
     * au démarrage suivant — mieux vaut le rejeter et retélécharger.
     */
    @Test
    fun loadIgnoresCacheWrittenWithOtherFieldNames() {
        val file = File.createTempFile("server_outage_legacy", ".json")
        try {
            file.writeText("""{"a":456,"b":"2026-08-07","c":789,"d":[{"a":"0751234567"}]}""")
            assertNull(ServerOutageCache(file).load())
        } finally {
            file.delete()
        }
    }

    @Test
    fun sizeBytesReportsStoredCopy() {
        val file = File.createTempFile("server_outage_size", ".json")
        try {
            ServerOutageCache(file).save(CachedServerOutages(1L, null, 0L, emptyList()))
            assertEquals(file.length(), ServerOutageCache(file).sizeBytes())
        } finally {
            file.delete()
        }
    }
}
