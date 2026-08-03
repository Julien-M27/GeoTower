package fr.geotower.data.outages

import fr.geotower.data.models.SiteHsEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException

class LocalOutageProviderTest {

    private val oneHourMs = 3_600_000L

    private fun site(id: String) = SiteHsEntity(idAnfr = id, operateur = "SFR", latitude = 48.85, longitude = 2.35)
    private fun result(vararg ids: String) = LocalOutageGenerator.GenerationResult(
        sites = ids.map { site(it) },
        stats = OutageBuildStats(),
        downloadErrors = emptyMap(),
    )

    private fun tempCache(): Pair<OutageLocalCache, File> {
        val file = File.createTempFile("outage_provider", ".json").also { it.delete() }
        return OutageLocalCache(file) to file
    }

    @Test
    fun servesFreshCacheWithoutGenerating() = runBlocking {
        val (cache, file) = tempCache()
        try {
            val now = 1_000_000L
            cache.save(CachedOutages(now, "d", listOf(site("A"))))
            var generateCalls = 0
            val provider = LocalOutageProvider(
                cache, { oneHourMs }, { _, _ -> }, { _, _ -> generateCalls++; result("B") }, { now },
            )
            assertEquals(listOf("A"), provider.getSites().map { it.idAnfr })
            assertEquals(0, generateCalls)
        } finally {
            file.delete()
        }
    }

    @Test
    fun regeneratesWhenCacheStale() = runBlocking {
        val (cache, file) = tempCache()
        try {
            val now = 10_000_000L
            cache.save(CachedOutages(now - 2 * oneHourMs, "old", listOf(site("A")))) // 2 h > TTL 1 h
            var marked = 0L
            var markedSites = emptyList<String>()
            var generateCalls = 0
            val provider = LocalOutageProvider(
                cache,
                { oneHourMs },
                { at, sites -> marked = at; markedSites = sites.map { it.idAnfr } },
                { _, _ -> generateCalls++; result("B", "C") },
                { now },
            )
            assertEquals(listOf("B", "C"), provider.getSites().map { it.idAnfr })
            assertEquals(1, generateCalls)
            assertEquals(now, marked)
            // Le résumé persisté doit porter sur les sites fraîchement générés (pas l'ancien cache).
            assertEquals(listOf("B", "C"), markedSites)
            assertEquals(listOf("B", "C"), cache.load()!!.sites.map { it.idAnfr }) // cache réécrit
        } finally {
            file.delete()
        }
    }

    @Test
    fun forceRegeneratesEvenWhenFresh() = runBlocking {
        val (cache, file) = tempCache()
        try {
            val now = 5_000_000L
            cache.save(CachedOutages(now, "d", listOf(site("A"))))
            var generateCalls = 0
            val provider = LocalOutageProvider(
                cache, { oneHourMs }, { _, _ -> }, { _, _ -> generateCalls++; result("B") }, { now },
            )
            assertEquals(listOf("B"), provider.regenerate(force = true).map { it.idAnfr })
            assertEquals(1, generateCalls)
        } finally {
            file.delete()
        }
    }

    @Test
    fun fallsBackToOldCacheWhenGenerationFails() = runBlocking {
        val (cache, file) = tempCache()
        try {
            val now = 20_000_000L
            cache.save(CachedOutages(now - 2 * oneHourMs, "old", listOf(site("A"))))
            val provider = LocalOutageProvider(
                cache, { oneHourMs }, { _, _ -> }, { _, _ -> throw IOException("down") }, { now },
            )
            // Génération en échec → on conserve l'ancien cache plutôt que rien.
            assertEquals(listOf("A"), provider.getSites().map { it.idAnfr })
        } finally {
            file.delete()
        }
    }

    /**
     * Déclenchement EXPLICITE en échec : l'exception remonte, sinon le bouton « mettre à jour
     * maintenant » est indiscernable d'un bouton qui ne fait rien.
     */
    @Test
    fun forcedRegenerationPropagatesFailure() = runBlocking {
        val (cache, file) = tempCache()
        try {
            val now = 30_000_000L
            cache.save(CachedOutages(now, "d", listOf(site("A")))) // cache frais : force doit passer outre
            val provider = LocalOutageProvider(
                cache, { oneHourMs }, { _, _ -> }, { _, _ -> throw IOException("aucun opérateur") }, { now },
            )
            try {
                provider.regenerate(force = true)
                fail("une régénération forcée en échec doit lever")
            } catch (e: IOException) {
                assertEquals("aucun opérateur", e.message)
            }
            // Le cache reste intact : l'échec ne détruit pas les dernières pannes connues.
            assertEquals(listOf("A"), provider.getSites().map { it.idAnfr })
        } finally {
            file.delete()
        }
    }
}
