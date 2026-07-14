package fr.geotower.data.outages

import fr.geotower.data.models.SiteHsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class OutageLocalCacheTest {

    @Test
    fun savesAndLoadsRoundTrip() {
        val file = File.createTempFile("sites_hs_local", ".json")
        try {
            val cache = OutageLocalCache(file)
            val site = SiteHsEntity(
                idAnfr = "0751234567", operateur = "SFR", latitude = 48.85, longitude = 2.35,
                voix2g = "HS", dateDebut = "2026-01-01",
            )
            cache.save(CachedOutages(123L, "2026-07-13", listOf(site)))

            val loaded = cache.load()
            assertNotNull(loaded)
            assertEquals(123L, loaded!!.generatedAtMillis)
            assertEquals("2026-07-13", loaded.sourceLastUpdate)
            assertEquals(1, loaded.sites.size)
            assertEquals("0751234567", loaded.sites[0].idAnfr)
            assertEquals("HS", loaded.sites[0].voix2g)
        } finally {
            file.delete()
        }
    }

    @Test
    fun loadReturnsNullWhenMissing() {
        val file = File.createTempFile("outage_missing", ".json").also { it.delete() }
        assertNull(OutageLocalCache(file).load())
    }

    @Test
    fun loadReturnsNullWhenCorrupted() {
        val file = File.createTempFile("outage_corrupt", ".json")
        try {
            file.writeText("{ this is not valid json")
            assertNull(OutageLocalCache(file).load())
        } finally {
            file.delete()
        }
    }

    @Test
    fun clearDeletesCache() {
        val file = File.createTempFile("outage_clear", ".json")
        try {
            val cache = OutageLocalCache(file)
            cache.save(CachedOutages(1L, null, emptyList()))
            cache.clear()
            assertNull(cache.load())
        } finally {
            file.delete()
        }
    }
}
