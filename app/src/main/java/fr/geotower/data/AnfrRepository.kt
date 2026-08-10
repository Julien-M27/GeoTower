package fr.geotower.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import fr.geotower.data.api.AnfrService
import fr.geotower.data.api.LiveDatabaseStatus
import fr.geotower.data.api.LiveSitesClient
import fr.geotower.data.api.SitesHsRebuildDto
import androidx.sqlite.db.SimpleSQLiteQuery
import fr.geotower.data.db.AppDatabase
import fr.geotower.data.db.DepartmentOperatorTechRow
import fr.geotower.data.db.DepartmentStatRow
import fr.geotower.data.db.GeoTowerDatabaseValidator
import fr.geotower.data.db.GeoTowerDao
import fr.geotower.data.db.InseeRangeBoundsRow
import fr.geotower.data.db.InvalidGeoTowerDatabaseException
import fr.geotower.data.db.RadioStatRow
import fr.geotower.data.db.SupportRadioStatsRow
import fr.geotower.data.db.WeeklyRadioStatRow
import fr.geotower.data.models.AntenneDbEntity
import fr.geotower.data.models.CoverageSiteLocationEntity
import fr.geotower.data.models.DbCluster
import fr.geotower.data.models.FaisceauxEntity
import fr.geotower.data.models.FavoriteScopeSiteRow
import fr.geotower.data.models.FrequencyDetailsCodec
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.LiveSiteResponseDto
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.RadioFilterMasks
import fr.geotower.data.models.RefTypeAntenneDbEntity
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.data.models.isDeclaredActive
import fr.geotower.data.api.SignalQuestClient
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.models.SiteHsEntity
import fr.geotower.data.outages.LocalOutageGenerator
import fr.geotower.data.outages.LocalOutageProvider
import fr.geotower.data.outages.NoOutageProgress
import fr.geotower.data.outages.OutageLocalCache
import fr.geotower.data.outages.OutageLocalConfig
import fr.geotower.data.outages.CachedServerOutages
import fr.geotower.data.outages.OutageProgressCallback
import fr.geotower.data.outages.OutageServerInfo
import fr.geotower.data.outages.HTTP_TOO_MANY_REQUESTS
import fr.geotower.data.outages.ServerOutageCache
import fr.geotower.data.outages.ServerOutageRebuildStatus
import fr.geotower.data.outages.parseServerDetail
import fr.geotower.data.outages.parseSitesHsRebuildBody
import fr.geotower.data.outages.toRebuildStatus
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import fr.geotower.utils.DepartmentCodes
import fr.geotower.utils.FrenchAdminAreas
import fr.geotower.utils.FrequencyFilterSelection
import fr.geotower.data.models.toDepartmentOperatorTechRow
import fr.geotower.data.models.toDepartmentStatRow
import fr.geotower.data.models.toLocalisationEntity
import fr.geotower.data.models.toPhysiqueEntity
import fr.geotower.data.models.toRadioStatRow
import fr.geotower.data.models.toTechniqueEntity
import fr.geotower.data.models.toWeeklyRadioStatRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.cos

data class ActiveSupportRadioCounts(
    val techCounts: Map<String, Int>,
    val bandCounts: Map<String, Int>
)

/**
 * Emprise d'une zone administrative (département ou région), déduite des stations qui y sont
 * déclarées : la base ne porte aucun contour administratif, l'enveloppe des sites est donc le
 * meilleur cadrage disponible. [siteCount] sert à décider si la zone tient dans un affichage
 * détaillé ou s'il faut se contenter d'y déplacer la carte.
 */
data class AdminAreaExtent(
    val latNorth: Double,
    val lonEast: Double,
    val latSouth: Double,
    val lonWest: Double,
    val siteCount: Int
)

private data class ActiveRadioKeys(
    val techKeys: Set<String>,
    val bandKeys: Set<String>
)

private val activeStatsFrequencyRangeRegex =
    Regex("""(\d+(?:[.,]\d+)?)\s*-\s*(\d+(?:[.,]\d+)?)\s*(kHz|MHz|GHz|Hz)?""", RegexOption.IGNORE_CASE)

private const val DETAIL_BACKED_5G_BANDS =
    RadioFilterMasks.BAND_5G_1400 or RadioFilterMasks.BAND_5G_4200 or RadioFilterMasks.BAND_5G_26000

private fun buildActiveSupportRadioCounts(rows: List<SupportRadioStatsRow>): ActiveSupportRadioCounts {
    val techSupportIds = mutableMapOf<String, MutableSet<String>>()
    val bandSupportIds = mutableMapOf<String, MutableSet<String>>()

    rows.forEach { row ->
        val decodedDetails = FrequencyDetailsCodec.decode(row.encodedDetailsFrequences)
        val detailedKeys = activeRadioKeysFromDetails(decodedDetails)
        val hasDetailedActiveKeys = detailedKeys.techKeys.isNotEmpty() || detailedKeys.bandKeys.isNotEmpty()
        val techKeys = if (hasDetailedActiveKeys) {
            detailedKeys.techKeys
        } else if (row.isActiveGlobally()) {
            techKeysFromMask(row.techMask)
        } else {
            emptySet()
        }
        val bandKeys = if (hasDetailedActiveKeys) {
            detailedKeys.bandKeys
        } else if (row.isActiveGlobally()) {
            bandKeysFromMask(row.bandMask)
        } else {
            emptySet()
        }

        techKeys.forEach { techKey ->
            techSupportIds.getOrPut(techKey) { mutableSetOf() }.add(row.idSupport)
        }
        bandKeys.forEach { bandKey ->
            bandSupportIds.getOrPut(bandKey) { mutableSetOf() }.add(row.idSupport)
        }
    }

    return ActiveSupportRadioCounts(
        techCounts = techSupportIds.mapValues { it.value.size },
        bandCounts = bandSupportIds.mapValues { it.value.size }
    )
}

private fun SupportRadioStatsRow.isActiveGlobally(): Boolean {
    if (hasActive == 1) return true

    val normalizedStatus = statut.orEmpty().lowercase(Locale.ROOT)
    return normalizedStatus.contains("en service") || normalizedStatus.contains("techniquement")
}

private fun activeRadioKeysFromDetails(details: String?): ActiveRadioKeys {
    if (details.isNullOrBlank()) return ActiveRadioKeys(emptySet(), emptySet())

    val techKeys = mutableSetOf<String>()
    val bandKeys = mutableSetOf<String>()
    details.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { line ->
            val parts = line.split("|").map { it.trim() }
            val rawFrequency = parts.getOrNull(0).orEmpty()
            val status = parts.getOrNull(1).orEmpty()
            if (isActiveFrequencyStatus(status)) {
                rawFrequencyToTechKey(rawFrequency)?.let { techKeys.add(it) }
                bandKeys.addAll(frequencyKeysFromRawDetails(rawFrequency))
            }
        }

    return ActiveRadioKeys(techKeys, bandKeys)
}

internal fun radioBandMaskFromFrequencyDetails(details: String?): Int {
    if (details.isNullOrBlank()) return 0

    var mask = 0
    details.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { line ->
            val rawFrequency = line.split("|", limit = 2).firstOrNull().orEmpty().trim()
            frequencyKeysFromRawDetails(rawFrequency).forEach { key ->
                mask = mask or radioBandMaskForKey(key)
            }
        }

    return mask
}

private fun isActiveFrequencyStatus(status: String): Boolean {
    val normalized = status.lowercase(Locale.ROOT)
    return normalized.contains("en service") || normalized.contains("techniquement")
}

private fun rawFrequencyToTechKey(rawFrequency: String): String? {
    val systemName = rawFrequency.substringBefore(":").trim().uppercase(Locale.ROOT)
    val gen = when {
        systemName.contains("5G") || systemName.contains("NR") -> 5
        systemName.contains("4G") || systemName.contains("LTE") -> 4
        systemName.contains("3G") || systemName.contains("UMTS") -> 3
        systemName.contains("2G") || systemName.contains("GSM") -> 2
        else -> 0
    }
    return gen.takeIf { it in 2..5 }?.let { "${it}G" }
}

private fun frequencyKeysFromRawDetails(rawFrequency: String): Set<String> {
    val techKey = rawFrequencyToTechKey(rawFrequency) ?: return emptySet()
    val gen = techKey.removeSuffix("G").toIntOrNull() ?: return emptySet()
    val systemName = rawFrequency.substringBefore(":").trim().uppercase(Locale.ROOT)
    val knownValues = mobileFrequencyValuesForGeneration(gen)
    val systemNumbers = Regex("\\d+").findAll(systemName)
        .mapNotNull { it.value.toIntOrNull() }
        .toList()
    val systemValue = systemNumbers
        .filter { it in knownValues }
        .maxOrNull()
        ?: if (gen == 5 && systemNumbers.contains(26)) 26000 else null

    if (systemValue != null) return setOf("$techKey|$systemValue")

    val preciseFrequencies = rawFrequency.substringAfter(":", "")
    return mobileFrequencyValuesFromRanges(gen, preciseFrequencies).map { "$techKey|$it" }.toSet()
}

private fun mobileFrequencyValuesForGeneration(gen: Int): Set<Int> = when (gen) {
    5 -> setOf(700, 1400, 2100, 3500, 4200, 26000)
    4 -> setOf(700, 800, 900, 1800, 2100, 2600)
    3 -> setOf(900, 2100)
    2 -> setOf(900, 1800)
    else -> emptySet()
}

private fun mobileFrequencyValuesFromRanges(gen: Int, rawRanges: String): Set<Int> {
    return activeStatsFrequencyRangeRegex.findAll(rawRanges)
        .flatMap { match ->
            val start = normalizeFrequencyToMhz(match.groupValues[1], match.groupValues[3])
            val end = normalizeFrequencyToMhz(match.groupValues[2], match.groupValues[3])
            frequencyValuesForRange(gen, start, end).asSequence()
        }
        .toSet()
}

private fun normalizeFrequencyToMhz(value: String, unit: String): Double? {
    val number = value.replace(',', '.').toDoubleOrNull() ?: return null
    val normalizedUnit = unit.lowercase(Locale.ROOT)
    return when {
        normalizedUnit.contains("ghz") -> number * 1000.0
        normalizedUnit.contains("khz") -> number / 1000.0
        normalizedUnit.contains("hz") && !normalizedUnit.contains("mhz") -> number / 1_000_000.0
        else -> number
    }
}

private fun frequencyValuesForRange(gen: Int, start: Double?, end: Double?): Set<Int> {
    if (start == null || end == null) return emptySet()
    return when (gen) {
        5 -> buildSet {
            if (frequencyRangeOverlaps(start, end, 700.0, 790.0)) add(700)
            if (frequencyRangeOverlaps(start, end, 1427.0, 1518.0)) add(1400)
            if (frequencyRangeOverlaps(start, end, 1920.0, 2170.0)) add(2100)
            if (frequencyRangeOverlaps(start, end, 3300.0, 3800.0)) add(3500)
            if (frequencyRangeOverlaps(start, end, 3800.1, 4200.0)) add(4200)
            if (frequencyRangeOverlaps(start, end, 24000.0, 27500.0)) add(26000)
        }
        4 -> buildSet {
            if (frequencyRangeOverlaps(start, end, 700.0, 790.0)) add(700)
            if (frequencyRangeOverlaps(start, end, 791.0, 862.0)) add(800)
            if (frequencyRangeOverlaps(start, end, 880.0, 960.0)) add(900)
            if (frequencyRangeOverlaps(start, end, 1710.0, 1880.0)) add(1800)
            if (frequencyRangeOverlaps(start, end, 1920.0, 2170.0)) add(2100)
            if (frequencyRangeOverlaps(start, end, 2500.0, 2690.0)) add(2600)
        }
        3 -> buildSet {
            if (frequencyRangeOverlaps(start, end, 880.0, 960.0)) add(900)
            if (frequencyRangeOverlaps(start, end, 1920.0, 2170.0)) add(2100)
        }
        2 -> buildSet {
            if (frequencyRangeOverlaps(start, end, 880.0, 960.0)) add(900)
            if (frequencyRangeOverlaps(start, end, 1710.0, 1880.0)) add(1800)
        }
        else -> emptySet()
    }
}

private fun frequencyRangeOverlaps(start: Double, end: Double, low: Double, high: Double): Boolean {
    val min = minOf(start, end)
    val max = maxOf(start, end)
    return min <= high && max >= low
}

private fun techKeysFromMask(mask: Int): Set<String> = buildSet {
    if ((mask and RadioFilterMasks.TECH_2G) != 0) add("2G")
    if ((mask and RadioFilterMasks.TECH_3G) != 0) add("3G")
    if ((mask and RadioFilterMasks.TECH_4G) != 0) add("4G")
    if ((mask and RadioFilterMasks.TECH_5G) != 0) add("5G")
}

private fun bandKeysFromMask(mask: Int): Set<String> = buildSet {
    if ((mask and RadioFilterMasks.BAND_2G_900) != 0) add("2G|900")
    if ((mask and RadioFilterMasks.BAND_2G_1800) != 0) add("2G|1800")
    if ((mask and RadioFilterMasks.BAND_3G_900) != 0) add("3G|900")
    if ((mask and RadioFilterMasks.BAND_3G_2100) != 0) add("3G|2100")
    if ((mask and RadioFilterMasks.BAND_4G_700) != 0) add("4G|700")
    if ((mask and RadioFilterMasks.BAND_4G_800) != 0) add("4G|800")
    if ((mask and RadioFilterMasks.BAND_4G_900) != 0) add("4G|900")
    if ((mask and RadioFilterMasks.BAND_4G_1800) != 0) add("4G|1800")
    if ((mask and RadioFilterMasks.BAND_4G_2100) != 0) add("4G|2100")
    if ((mask and RadioFilterMasks.BAND_4G_2600) != 0) add("4G|2600")
    if ((mask and RadioFilterMasks.BAND_5G_700) != 0) add("5G|700")
    if ((mask and RadioFilterMasks.BAND_5G_1400) != 0) add("5G|1400")
    if ((mask and RadioFilterMasks.BAND_5G_2100) != 0) add("5G|2100")
    if ((mask and RadioFilterMasks.BAND_5G_3500) != 0) add("5G|3500")
    if ((mask and RadioFilterMasks.BAND_5G_4200) != 0) add("5G|4200")
    if ((mask and RadioFilterMasks.BAND_5G_26000) != 0) add("5G|26000")
}

private fun radioBandMaskForKey(key: String): Int {
    return when (key) {
        "2G|900" -> RadioFilterMasks.BAND_2G_900
        "2G|1800" -> RadioFilterMasks.BAND_2G_1800
        "3G|900" -> RadioFilterMasks.BAND_3G_900
        "3G|2100" -> RadioFilterMasks.BAND_3G_2100
        "4G|700" -> RadioFilterMasks.BAND_4G_700
        "4G|800" -> RadioFilterMasks.BAND_4G_800
        "4G|900" -> RadioFilterMasks.BAND_4G_900
        "4G|1800" -> RadioFilterMasks.BAND_4G_1800
        "4G|2100" -> RadioFilterMasks.BAND_4G_2100
        "4G|2600" -> RadioFilterMasks.BAND_4G_2600
        "5G|700" -> RadioFilterMasks.BAND_5G_700
        "5G|1400" -> RadioFilterMasks.BAND_5G_1400
        "5G|2100" -> RadioFilterMasks.BAND_5G_2100
        "5G|3500" -> RadioFilterMasks.BAND_5G_3500
        "5G|4200" -> RadioFilterMasks.BAND_5G_4200
        "5G|26000" -> RadioFilterMasks.BAND_5G_26000
        else -> 0
    }
}

/**
 * Rayons de repli de la recherche « à proximité » en base en ligne, en km.
 *
 * Le dernier palier vaut le plafond du serveur (`liveApiNearbyHardMaxRadiusKm`), soit un peu plus
 * que la demi-circonférence terrestre (20 037 km) : aucun point du globe ne peut lui échapper.
 */
private val LIVE_NEARBY_FALLBACK_RADII_KM = listOf(200.0, 1_000.0, 20_100.0)

/** Tolérance de comparaison des rayons renvoyés par le serveur, en km. */
private const val LIVE_NEARBY_RADIUS_EPSILON_KM = 0.001

/**
 * Le serveur renvoie le rayon qu'il a réellement appliqué (`radius_km`) et le borne de son côté.
 * S'il l'a rogné, élargir encore ne changerait rien à la réponse : autant s'arrêter là plutôt que
 * de rejouer la même requête plafonnée.
 */
internal fun liveNearbyRadiusWasClampedByServer(requestedKm: Double, effectiveKm: Double?): Boolean {
    if (effectiveKm == null) return false
    return effectiveKm < requestedKm - LIVE_NEARBY_RADIUS_EPSILON_KM
}

/**
 * Rayons successifs essayés par la recherche « à proximité » quand la base est en ligne.
 *
 * Le chemin base locale n'a aucun rayon (cf. [AnfrRepository.getNearest] : rayons croissants puis
 * repli sur le plus proche quelle que soit la distance). Sans cet élargissement, une position hors
 * de la zone ANFR — émulateur à l'étranger, plein océan — rend une liste vide alors que la base
 * téléchargée, elle, sortirait bien les sites les plus proches.
 *
 * Le premier rayon reste celui de la limite distante : le cas nominal ne coûte qu'une requête.
 */
internal fun liveNearbyRadiusEscalationKm(baseRadiusKm: Double): List<Double> {
    val base = baseRadiusKm.coerceAtLeast(1.0)
    return listOf(base) + LIVE_NEARBY_FALLBACK_RADII_KM.filter { it > base }
}

class AnfrRepository(
    private val api: AnfrService,
    private val context: Context // ✅ NOUVEAU : On passe le context
) {

    // ✅ NOUVEAU : On récupère toujours le DAO depuis l'instance active (qui se recréera si elle a été fermée)
    private data class LongitudeRange(val min: Double, val max: Double)

    private data class MapQueryBounds(
        val minLat: Double,
        val maxLat: Double,
        val longitudeRanges: List<LongitudeRange>
    )

    private data class ClusterBucket(val latDegrees: Double, val lonDegrees: Double)

    private data class LiveCoverageBounds(
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double
    )

    private data class LiveBboxTile(
        val south: Double,
        val north: Double,
        val west: Double,
        val east: Double
    )

    private data class LiveBboxFetchResult(
        val sites: List<LocalisationEntity>,
        val mayBeTruncated: Boolean
    )

    private data class LiveNearestFetchResult(
        val sites: List<LocalisationEntity>,
        /** Rayon réellement appliqué par le serveur, qui peut être plus petit que celui demandé. */
        val effectiveRadiusKm: Double?
    )

    private class LiveBboxRequestBudget(remaining: Int) {
        private val remainingRequests = AtomicInteger(remaining)

        fun tryConsume(): Boolean {
            while (true) {
                val current = remainingRequests.get()
                if (current <= 0) return false
                if (remainingRequests.compareAndSet(current, current - 1)) return true
            }
        }
    }

    private val dao: GeoTowerDao
        get() = AppDatabase.getDatabase(context).geoTowerDao()

    // Génération LOCALE des pannes (opt-in) : config + cache/TTL, adossés à la base ANFR locale.
    private val outageLocalConfig by lazy { OutageLocalConfig(context) }

    // Copie sur l'appareil du fichier de pannes SERVEUR (l'autre source).
    private val serverOutageCache by lazy { ServerOutageCache(context) }
    private val outagePrefs by lazy {
        context.getSharedPreferences(OutageLocalConfig.PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val localOutageProvider by lazy {
        LocalOutageProvider(
            cache = OutageLocalCache(context),
            frequencyMillis = { outageLocalConfig.frequencyMillis },
            markGenerated = { at, sites -> outageLocalConfig.recordGeneration(at, sites) },
            generate = { lastUpdate, progress -> LocalOutageGenerator.create(dao).generate(lastUpdate, progress) },
        )
    }

    private suspend fun <T> queryLocalDatabase(defaultValue: T, block: suspend GeoTowerDao.() -> T): T {
        return try {
            dao.block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: InvalidGeoTowerDatabaseException) {
            AppLogger.w(TAG_DB, "Local database is unavailable", e)
            publishLocalDatabaseStatus(
                GeoTowerDatabaseValidator.getInstalledDatabaseStatus(context).state
            )
            defaultValue
        } catch (e: Exception) {
            AppLogger.w(TAG_DB, "Local database query failed", e)
            refreshLocalDatabaseStatusAfterFailure()
            defaultValue
        }
    }

    /**
     * Le repli sur l'API live tient à deux choses : l'endpoint est autorisé, et aucune base valide
     * n'est installée. Ce second point est la définition partagée de [LiveDatabaseStatus.isInUse] —
     * la page « À propos » s'en sert pour annoncer les données servies par la base en ligne, et ne
     * doit jamais s'en écarter.
     */
    private fun shouldUseLiveApiFallback(featureId: String): Boolean {
        if (!RemoteFeatureFlags.isFeatureEnabled(featureId)) return false
        return LiveDatabaseStatus.isInUse(context)
    }

    private fun liveNearbyRadiusKm(): Double {
        return RemoteFeatureFlags
            .limitOrDefault(RemoteFeatureFlags.Limits.LIVE_API_NEARBY_MAX_RADIUS_KM, 50)
            .coerceAtLeast(1)
            .toDouble()
    }

    private fun liveBboxMaxDegrees(): Double {
        return RemoteFeatureFlags
            .limitOrDefault(RemoteFeatureFlags.Limits.LIVE_API_BBOX_MAX_DEGREES, 3)
            .coerceAtLeast(1)
            .toDouble()
    }

    private fun buildLiveBboxTiles(
        latNorth: Double,
        lonEast: Double,
        latSouth: Double,
        lonWest: Double
    ): List<LiveBboxTile> {
        val bounds = mapQueryBounds(latNorth, lonEast, latSouth, lonWest)
        val maxDegrees = liveBboxMaxDegrees()
        val tiles = mutableListOf<LiveBboxTile>()

        bounds.longitudeRanges.forEach { lonRange ->
            liveCoverageBounds.forEach { coverage ->
                val south = maxOf(bounds.minLat, coverage.minLat)
                val north = minOf(bounds.maxLat, coverage.maxLat)
                val west = maxOf(lonRange.min, coverage.minLon)
                val east = minOf(lonRange.max, coverage.maxLon)
                if (south >= north || west >= east) return@forEach

                var tileSouth = south
                while (tileSouth < north) {
                    val tileNorth = minOf(tileSouth + maxDegrees, north)
                    var tileWest = west
                    while (tileWest < east) {
                        val tileEast = minOf(tileWest + maxDegrees, east)
                        tiles += LiveBboxTile(
                            south = tileSouth,
                            north = tileNorth,
                            west = tileWest,
                            east = tileEast
                        )
                        tileWest = tileEast
                    }
                    tileSouth = tileNorth
                }
            }
        }

        return tiles.take(LIVE_BBOX_TILE_REQUEST_LIMIT)
    }

    private fun canSplitLiveBboxTile(tile: LiveBboxTile): Boolean {
        return (tile.north - tile.south) > LIVE_BBOX_MIN_TILE_DEGREES ||
            (tile.east - tile.west) > LIVE_BBOX_MIN_TILE_DEGREES
    }

    private fun splitLiveBboxTile(tile: LiveBboxTile): List<LiveBboxTile> {
        val midLat = (tile.south + tile.north) / 2.0
        val midLon = (tile.west + tile.east) / 2.0
        return listOf(
            LiveBboxTile(south = tile.south, north = midLat, west = tile.west, east = midLon),
            LiveBboxTile(south = tile.south, north = midLat, west = midLon, east = tile.east),
            LiveBboxTile(south = midLat, north = tile.north, west = tile.west, east = midLon),
            LiveBboxTile(south = midLat, north = tile.north, west = midLon, east = tile.east)
        ).filter { child ->
            child.south < child.north && child.west < child.east
        }
    }

    private suspend fun getLiveSitesInTileRecursively(
        tile: LiveBboxTile,
        limit: Int,
        budget: LiveBboxRequestBudget,
        semaphore: Semaphore,
        depth: Int = 0
    ): List<LocalisationEntity> {
        if (!budget.tryConsume()) return emptyList()

        val result = semaphore.withPermit {
            getLiveSitesInTile(tile, limit)
        }
        if (
            result.mayBeTruncated &&
            depth < LIVE_BBOX_MAX_SUBDIVISION_DEPTH &&
            canSplitLiveBboxTile(tile)
        ) {
            val childSites = coroutineScope {
                splitLiveBboxTile(tile)
                    .map { child ->
                        async {
                            getLiveSitesInTileRecursively(
                                tile = child,
                                limit = limit,
                                budget = budget,
                                semaphore = semaphore,
                                depth = depth + 1
                            )
                        }
                    }
                    .awaitAll()
                    .flatten()
            }
            if (childSites.isNotEmpty()) return childSites
        }

        return result.sites
    }

    private suspend fun getLiveNearest(
        lat: Double,
        lon: Double,
        limit: Int,
        zbOnly: Boolean
    ): List<LocalisationEntity> {
        val radii = liveNearbyRadiusEscalationKm(liveNearbyRadiusKm())
        radii.forEachIndexed { index, radiusKm ->
            // null = requête en échec (réseau, ou rayon refusé par le serveur) : inutile
            // d'élargir, on ne ferait que rejouer l'erreur.
            val result = getLiveNearestWithinRadius(
                lat = lat,
                lon = lon,
                limit = limit,
                zbOnly = zbOnly,
                radiusKm = radiusKm
            ) ?: return emptyList()

            if (result.sites.isNotEmpty()) {
                if (index > 0) {
                    AppLogger.d(TAG_DB, "Live sites nearby: rayon élargi à $radiusKm km")
                }
                return result.sites
            }

            if (liveNearbyRadiusWasClampedByServer(radiusKm, result.effectiveRadiusKm)) {
                return emptyList()
            }
        }
        return emptyList()
    }

    private suspend fun getLiveNearestWithinRadius(
        lat: Double,
        lon: Double,
        limit: Int,
        zbOnly: Boolean,
        radiusKm: Double
    ): LiveNearestFetchResult? {
        return try {
            val response = LiveSitesClient.api.getNearbySites(
                lat = lat,
                lon = lon,
                limit = limit.coerceAtLeast(1),
                radiusKm = radiusKm,
                zbOnly = zbOnly.takeIf { it },
                activeOnly = null
            )
            if (!response.isSuccessful) return null
            val body = response.body()
            LiveNearestFetchResult(
                sites = body?.sites?.mapNotNull { it.toLocalisationEntity() }.orEmpty(),
                effectiveRadiusKm = body?.radiusKm
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG_DB, "Live sites nearby request failed", e)
            null
        }
    }

    private suspend fun getLiveSitesInBox(
        latNorth: Double,
        lonEast: Double,
        latSouth: Double,
        lonWest: Double
    ): List<LocalisationEntity> {
        val safeLimit = RemoteFeatureFlags
            .limitOrDefault(RemoteFeatureFlags.Limits.LIVE_API_BBOX_MAX_LIMIT, 1000)
            .coerceAtLeast(1)
        val tiles = buildLiveBboxTiles(latNorth, lonEast, latSouth, lonWest)
        if (tiles.isEmpty()) return emptyList()

        val budget = LiveBboxRequestBudget(LIVE_BBOX_TILE_REQUEST_LIMIT)
        val semaphore = Semaphore(LIVE_BBOX_MAX_PARALLEL_REQUESTS)
        val sites = coroutineScope {
            tiles
                .map { tile ->
                    async {
                        getLiveSitesInTileRecursively(
                            tile = tile,
                            limit = safeLimit,
                            budget = budget,
                            semaphore = semaphore
                        )
                    }
                }
                .awaitAll()
                .flatten()
        }
        return sites.distinctBy { it.idAnfr }
    }

    private suspend fun getLiveSitesInTile(tile: LiveBboxTile, limit: Int): LiveBboxFetchResult {
        return try {
            val response = LiveSitesClient.api.getSitesInBox(
                north = tile.north,
                south = tile.south,
                east = tile.east,
                west = tile.west,
                limit = limit
            )
            if (!response.isSuccessful) return LiveBboxFetchResult(emptyList(), mayBeTruncated = false)
            val sites = response.body()
                ?.sites
                ?.mapNotNull { it.toLocalisationEntity() }
                .orEmpty()
            LiveBboxFetchResult(
                sites = sites,
                mayBeTruncated = sites.size >= limit
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG_DB, "Live sites bbox request failed", e)
            LiveBboxFetchResult(emptyList(), mayBeTruncated = false)
        }
    }

    private suspend fun getLiveSite(siteId: String): LiveSiteResponseDto? {
        if (siteId.isBlank()) return null
        return try {
            val response = LiveSitesClient.api.getSite(siteId)
            if (!response.isSuccessful) return null
            response.body()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG_DB, "Live site detail request failed", e)
            null
        }
    }

    private suspend fun getLiveSites(siteIds: List<String>): List<LiveSiteResponseDto> {
        return siteIds
            .filter { it.isNotBlank() }
            .distinct()
            .take(LIVE_DETAIL_LOOKUP_LIMIT)
            .mapNotNull { id -> getLiveSite(id) }
    }

    private suspend fun getLiveCurrentRadioStatsByOperator(operatorNames: List<String>): List<RadioStatRow> {
        return try {
            val response = LiveSitesClient.api.getCurrentRadioStats(operatorNames)
            if (!response.isSuccessful) return emptyList()
            response.body()
                ?.rows
                ?.mapNotNull { it.toRadioStatRow() }
                .orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG_DB, "Live current stats request failed", e)
            emptyList()
        }
    }

    private suspend fun getLiveWeeklyRadioStatsByOperator(operatorNames: List<String>): List<WeeklyRadioStatRow> {
        return try {
            val response = LiveSitesClient.api.getWeeklyRadioStats(operatorNames)
            if (!response.isSuccessful) return emptyList()
            response.body()
                ?.rows
                ?.mapNotNull { it.toWeeklyRadioStatRow() }
                .orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG_DB, "Live weekly stats request failed", e)
            emptyList()
        }
    }

    private fun refreshLocalDatabaseStatusAfterFailure() {
        try {
            val status = GeoTowerDatabaseValidator.getInstalledDatabaseStatus(context)
            if (status.state != GeoTowerDatabaseValidator.LocalDatabaseState.VALID) {
                publishLocalDatabaseStatus(status.state)
            }
        } catch (statusError: Exception) {
            AppLogger.w(TAG_DB, "Local database status refresh failed", statusError)
        }
    }

    private fun publishLocalDatabaseStatus(state: GeoTowerDatabaseValidator.LocalDatabaseState) {
        Handler(Looper.getMainLooper()).post {
            AppConfig.localDatabaseState.value = state
        }
    }

    private fun sanitizeLatitude(value: Double, fallback: Double): Double {
        return if (value.isNaN() || value.isInfinite()) fallback else value.coerceIn(-90.0, 90.0)
    }

    private fun sanitizeLongitude(value: Double, fallback: Double): Double {
        return if (value.isNaN() || value.isInfinite()) fallback else value
    }

    private fun normalizeLongitude(value: Double): Double {
        val normalized = ((value + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        return if (normalized == -180.0 && value > 0.0) 180.0 else normalized
    }

    private fun mapQueryBounds(latNorth: Double, lonEast: Double, latSouth: Double, lonWest: Double): MapQueryBounds {
        val south = sanitizeLatitude(latSouth, -90.0)
        val north = sanitizeLatitude(latNorth, 90.0)
        val minLat = minOf(south, north)
        val maxLat = maxOf(south, north)

        val rawWest = sanitizeLongitude(lonWest, -180.0)
        val rawEast = sanitizeLongitude(lonEast, 180.0)
        val rawSpan = rawEast - rawWest

        val longitudeRanges = if (rawSpan >= 360.0 || rawSpan <= -360.0 || (rawWest <= -180.0 && rawEast >= 180.0)) {
            listOf(LongitudeRange(-180.0, 180.0))
        } else {
            val west = normalizeLongitude(rawWest)
            val east = normalizeLongitude(rawEast)
            if (rawSpan < 0.0 || west > east) {
                listOf(LongitudeRange(west, 180.0), LongitudeRange(-180.0, east))
            } else {
                listOf(LongitudeRange(west, east))
            }
        }

        return MapQueryBounds(minLat = minLat, maxLat = maxLat, longitudeRanges = longitudeRanges)
    }

    private fun databaseAnfrIdCandidates(value: String): List<String> {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return emptyList()
        if (!trimmed.all { it.isDigit() }) return listOf(trimmed)

        val compact = trimmed.trimStart('0').ifEmpty { "0" }
        return listOf(trimmed, compact, trimmed.padStart(10, '0')).distinct()
    }

    private suspend fun GeoTowerDao.getClustersForZoom(
        zoom: Double,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        hideUndergroundSites: Boolean,
        showOnlyZbSites: Boolean
    ): List<DbCluster> {
        return when {
            zoom < 6.5 -> getL1Clusters(minLat = minLat, maxLat = maxLat, minLon = minLon, maxLon = maxLon, hideUndergroundSites = hideUndergroundSites, showOnlyZbSites = showOnlyZbSites)
            zoom < 8.0 -> getL2Clusters(minLat = minLat, maxLat = maxLat, minLon = minLon, maxLon = maxLon, hideUndergroundSites = hideUndergroundSites, showOnlyZbSites = showOnlyZbSites)
            zoom < 9.5 -> getL3Clusters(minLat = minLat, maxLat = maxLat, minLon = minLon, maxLon = maxLon, hideUndergroundSites = hideUndergroundSites, showOnlyZbSites = showOnlyZbSites)
            zoom < 10.5 -> getL4Clusters(minLat = minLat, maxLat = maxLat, minLon = minLon, maxLon = maxLon, hideUndergroundSites = hideUndergroundSites, showOnlyZbSites = showOnlyZbSites)
            zoom < 11.5 -> getL5Clusters(minLat = minLat, maxLat = maxLat, minLon = minLon, maxLon = maxLon, hideUndergroundSites = hideUndergroundSites, showOnlyZbSites = showOnlyZbSites)
            zoom < 12.5 -> getL6Clusters(minLat = minLat, maxLat = maxLat, minLon = minLon, maxLon = maxLon, hideUndergroundSites = hideUndergroundSites, showOnlyZbSites = showOnlyZbSites)
            else -> getL7Clusters(minLat = minLat, maxLat = maxLat, minLon = minLon, maxLon = maxLon, hideUndergroundSites = hideUndergroundSites, showOnlyZbSites = showOnlyZbSites)
        }
    }

    private fun clusterBucketForZoom(zoom: Double): ClusterBucket {
        return when {
            zoom < 6.5 -> ClusterBucket(latDegrees = 2.5, lonDegrees = 3.0)
            zoom < 8.0 -> ClusterBucket(latDegrees = 1.0, lonDegrees = 1.2)
            zoom < 9.5 -> ClusterBucket(latDegrees = 0.4, lonDegrees = 0.5)
            zoom < 10.5 -> ClusterBucket(latDegrees = 0.15, lonDegrees = 0.2)
            zoom < 11.5 -> ClusterBucket(latDegrees = 0.1, lonDegrees = 0.1)
            zoom < 12.5 -> ClusterBucket(latDegrees = 0.05, lonDegrees = 0.06)
            else -> ClusterBucket(latDegrees = 0.02, lonDegrees = 0.025)
        }
    }

    private fun buildLiveClusters(sites: List<LocalisationEntity>, zoom: Double): List<DbCluster> {
        if (sites.isEmpty()) return emptyList()

        val bucket = clusterBucketForZoom(zoom)
        val clusters = sites
            .groupBy { site ->
                Math.round(site.latitude / bucket.latDegrees) to
                    Math.round(site.longitude / bucket.lonDegrees)
            }
            .map { (_, group) ->
                val distinctIds = group
                    .map { it.idAnfr }
                    .filter { it.isNotBlank() }
                    .distinct()
                val count = distinctIds.size.takeIf { it > 0 } ?: group.size
                val operators = group
                    .mapNotNull { it.operateur?.trim()?.takeIf(String::isNotBlank) }
                    .distinct()
                    .joinToString(", ")
                    .takeIf { it.isNotBlank() }

                DbCluster(
                    centerLat = group.sumOf { it.latitude } / group.size,
                    centerLon = group.sumOf { it.longitude } / group.size,
                    count = count,
                    operators = operators,
                    singleIdAnfr = distinctIds.singleOrNull()?.takeIf { count == 1 }
                )
            }

        return MacroClusterGrouper.mergeTargetedTerritories(clusters, zoom)
    }

    // =================================================================
    // 1. POUR LA CARTE (Affichage ultra-rapide des points)
    // =================================================================

    /** Date de mise en service la plus ancienne (repli implantation), pour borner le slider temporel de la carte. */
    suspend fun getOldestServiceDate(): String? {
        return queryLocalDatabase<String?>(null) { getOldestServiceDate() }
    }

    suspend fun getAntennasInBox(
        latNorth: Double,
        lonEast: Double,
        latSouth: Double,
        lonWest: Double,
        detailBackedBandMask: Int = DETAIL_BACKED_5G_BANDS
    ): List<LocalisationEntity> {
        if (shouldUseLiveApiFallback(RemoteFeatureFlags.Features.LIVE_API_FR_BBOX)) {
            return getLiveSitesInBox(latNorth, lonEast, latSouth, lonWest)
        }

        val bounds = mapQueryBounds(latNorth, lonEast, latSouth, lonWest)
        val localisations = queryLocalDatabase(emptyList()) {
            bounds.longitudeRanges
                .flatMap { range ->
                    getLocalisationsInBox(
                        minLat = bounds.minLat,
                        maxLat = bounds.maxLat,
                        minLon = range.min,
                        maxLon = range.max
                    )
                }
                .distinctBy { it.idAnfr }
        }
        return enrichRadioBandMasksFromDetails(localisations, detailBackedBandMask)
    }

    /**
     * Emprise d'un département ou d'une région, pour la recherche de la carte.
     *
     * Renvoie null quand la zone ne contient aucune station, ce qui couvre aussi le cas « pas de
     * base locale installée » (repli API live) : l'appelant retombe alors sur le géocodeur.
     */
    suspend fun getAdminAreaExtent(departmentCodes: List<String>): AdminAreaExtent? {
        if (departmentCodes.isEmpty()) return null

        val rows = queryLocalDatabase(emptyList<InseeRangeBoundsRow>()) {
            departmentCodes.mapNotNull { code ->
                val (start, end) = FrenchAdminAreas.inseeRange(code)
                getInseeRangeBounds(start, end)
            }
        }.filter { it.count > 0 }

        if (rows.isEmpty()) return null

        return AdminAreaExtent(
            latNorth = rows.mapNotNull { it.maxLat }.maxOrNull() ?: return null,
            lonEast = rows.mapNotNull { it.maxLon }.maxOrNull() ?: return null,
            latSouth = rows.mapNotNull { it.minLat }.minOrNull() ?: return null,
            lonWest = rows.mapNotNull { it.minLon }.minOrNull() ?: return null,
            siteCount = rows.sumOf { it.count }
        )
    }

    /**
     * Stations d'un département ou d'une région. Le filtre porte sur le code INSEE, donc la carte
     * n'affiche que la zone demandée — sans avoir besoin d'un contour, contrairement à la recherche
     * de commune qui découpe une bbox au polygone.
     */
    suspend fun getAntennasInAdminArea(
        departmentCodes: List<String>,
        detailBackedBandMask: Int = DETAIL_BACKED_5G_BANDS
    ): List<LocalisationEntity> {
        if (departmentCodes.isEmpty()) return emptyList()

        val localisations = queryLocalDatabase(emptyList<LocalisationEntity>()) {
            departmentCodes
                .flatMap { code ->
                    val (start, end) = FrenchAdminAreas.inseeRange(code)
                    getLocalisationsInInseeRange(start, end)
                }
                .distinctBy { it.idAnfr }
        }
        return enrichRadioBandMasksFromDetails(localisations, detailBackedBandMask)
    }

    /**
     * Variante de [getAntennasInBox] restreinte aux sites « totalement en projet » (aucune
     * émission en service). Le filtre est poussé en SQL : le filtre carte désactive le
     * clustering, on ne veut donc pas charger toute la zone pour n'en garder que quelques
     * pourcents côté client.
     */
    suspend fun getProjectAntennasInBox(
        latNorth: Double,
        lonEast: Double,
        latSouth: Double,
        lonWest: Double,
        detailBackedBandMask: Int = DETAIL_BACKED_5G_BANDS
    ): List<LocalisationEntity> {
        if (shouldUseLiveApiFallback(RemoteFeatureFlags.Features.LIVE_API_FR_BBOX)) {
            return getLiveSitesInBox(latNorth, lonEast, latSouth, lonWest)
                .filterNot { it.isDeclaredActive() }
        }

        val bounds = mapQueryBounds(latNorth, lonEast, latSouth, lonWest)
        val localisations = queryLocalDatabase(emptyList()) {
            bounds.longitudeRanges
                .flatMap { range ->
                    getProjectLocalisationsInBox(
                        minLat = bounds.minLat,
                        maxLat = bounds.maxLat,
                        minLon = range.min,
                        maxLon = range.max
                    )
                }
                .distinctBy { it.idAnfr }
        }
        return enrichRadioBandMasksFromDetails(localisations, detailBackedBandMask)
    }

    suspend fun getAntennasInBoxForFrequencyFilter(
        latNorth: Double,
        lonEast: Double,
        latSouth: Double,
        lonWest: Double,
        frequencyFilter: FrequencyFilterSelection
    ): List<LocalisationEntity> {
        val detailBackedBandMask = frequencyFilter.detailBackedBandMaskForEnrichment()
        if (frequencyFilter.isFullyEnabled) {
            return getAntennasInBox(
                latNorth = latNorth,
                lonEast = lonEast,
                latSouth = latSouth,
                lonWest = lonWest,
                detailBackedBandMask = detailBackedBandMask
            )
        }

        if (shouldUseLiveApiFallback(RemoteFeatureFlags.Features.LIVE_API_FR_BBOX)) {
            val liveSites = getLiveSitesInBox(latNorth, lonEast, latSouth, lonWest)
            return if (detailBackedBandMask != 0 && liveSites.size <= LIVE_DETAIL_LOOKUP_LIMIT) {
                enrichRadioBandMasksFromDetails(liveSites, detailBackedBandMask)
            } else {
                liveSites
            }
        }

        val selectedBandMask = frequencyFilter.selectedMobileBandMask()
        val includeFh = frequencyFilter.includesFh()
        if (selectedBandMask == 0 && detailBackedBandMask == 0 && !includeFh) {
            return emptyList()
        }

        val bounds = mapQueryBounds(latNorth, lonEast, latSouth, lonWest)
        val localisations = queryLocalDatabase(emptyList()) {
            bounds.longitudeRanges
                .flatMap { range ->
                    getLocalisationsInBoxForRadioFilter(
                        minLat = bounds.minLat,
                        maxLat = bounds.maxLat,
                        minLon = range.min,
                        maxLon = range.max,
                        selectedBandMask = selectedBandMask,
                        detailBackedBandMask = detailBackedBandMask,
                        includeFh = includeFh
                    )
                }
                .distinctBy { it.idAnfr }
        }
        return enrichRadioBandMasksFromDetails(localisations, detailBackedBandMask)
    }

    suspend fun getAntennasByIdsInBox(
        idAnfrs: List<String>,
        latNorth: Double,
        lonEast: Double,
        latSouth: Double,
        lonWest: Double,
        detailBackedBandMask: Int = DETAIL_BACKED_5G_BANDS
    ): List<LocalisationEntity> {
        val distinctIds = idAnfrs
            .flatMap(::databaseAnfrIdCandidates)
            .distinct()
        if (distinctIds.isEmpty()) return emptyList()

        val bounds = mapQueryBounds(latNorth, lonEast, latSouth, lonWest)
        val localisations = distinctIds.chunked(SQLITE_IN_CLAUSE_BATCH_SIZE)
            .flatMap { chunk ->
                queryLocalDatabase(emptyList<LocalisationEntity>()) {
                    bounds.longitudeRanges
                        .flatMap { range ->
                            getLocalisationsByIdsInBox(
                                idAnfrs = chunk,
                                minLat = bounds.minLat,
                                maxLat = bounds.maxLat,
                                minLon = range.min,
                                maxLon = range.max
                            )
                        }
                }
            }
            .distinctBy { it.idAnfr }

        return enrichRadioBandMasksFromDetails(localisations, detailBackedBandMask)
    }

    suspend fun getNearest100(lat: Double, lon: Double): List<LocalisationEntity> {
        return getNearest(lat, lon, 100)
    }

    suspend fun getNearestZb(
        lat: Double,
        lon: Double,
        limit: Int,
        detailBackedBandMask: Int = 0
    ): List<LocalisationEntity> {
        if (shouldUseLiveApiFallback(RemoteFeatureFlags.Features.LIVE_API_FR_NEARBY)) {
            return getLiveNearest(lat, lon, limit, zbOnly = true)
        }

        val localisations = queryLocalDatabase(emptyList()) {
            this.getNearestZb(lat, lon, limit.coerceAtLeast(1))
        }
        return enrichRadioBandMasksFromDetails(localisations, detailBackedBandMask)
    }

    suspend fun getNearest(
        lat: Double,
        lon: Double,
        limit: Int,
        detailBackedBandMask: Int = 0
    ): List<LocalisationEntity> {
        if (shouldUseLiveApiFallback(RemoteFeatureFlags.Features.LIVE_API_FR_NEARBY)) {
            return getLiveNearest(lat, lon, limit, zbOnly = false)
        }

        val localisations = queryLocalDatabase(emptyList()) {
            val safeLimit = limit.coerceAtLeast(1)
            val radii = listOf(0.03, 0.08, 0.18, 0.45, 1.0, 2.5, 5.0)
            var bestResult = emptyList<LocalisationEntity>()

            for (radius in radii) {
                val nearest = getNearestWithinRadius(
                    lat = lat,
                    lon = lon,
                    minLat = lat - radius,
                    maxLat = lat + radius,
                    minLon = lon - radius,
                    maxLon = lon + radius,
                    maxDistanceSquared = radius * radius,
                    limit = safeLimit
                )

                if (nearest.size > bestResult.size) bestResult = nearest
                if (nearest.size >= safeLimit) return@queryLocalDatabase nearest
            }

            this.getNearest(lat, lon, safeLimit).ifEmpty { bestResult }
        }
        return enrichRadioBandMasksFromDetails(localisations, detailBackedBandMask)
    }

    // =================================================================
    // 1.5 POUR LA CARTE (Mode Macro : Clustering progressif à 5 niveaux)
    // =================================================================
    suspend fun getClusteredAntennas(zoom: Double, latNorth: Double, lonEast: Double, latSouth: Double, lonWest: Double): List<DbCluster> {
        if (shouldUseLiveApiFallback(RemoteFeatureFlags.Features.LIVE_API_FR_BBOX)) {
            val liveSites = getLiveSitesInBox(latNorth, lonEast, latSouth, lonWest)
                .filter { site ->
                    (!AppConfig.showOnlyZbSites.value || site.isZb == 1) &&
                        (!AppConfig.hideUndergroundSites.value || site.hasUndergroundSupport != 1)
                }
            return buildLiveClusters(liveSites, zoom)
        }

        val bounds = mapQueryBounds(latNorth, lonEast, latSouth, lonWest)
        return queryLocalDatabase(emptyList()) {
            val clusters = bounds.longitudeRanges.flatMap { range ->
                getClustersForZoom(
                    zoom = zoom,
                    minLat = bounds.minLat,
                    maxLat = bounds.maxLat,
                    minLon = range.min,
                    maxLon = range.max,
                    hideUndergroundSites = AppConfig.hideUndergroundSites.value,
                    showOnlyZbSites = AppConfig.showOnlyZbSites.value
                )
            }
            MacroClusterGrouper.mergeTargetedTerritories(clusters, zoom)
        }
    }

    // =================================================================
    // 2. POUR LES DÉTAILS (Quand on clique sur une antenne)
    // =================================================================
    suspend fun getTechniqueDetails(idAnfr: String): TechniqueEntity? {
        if (shouldUseLiveApiFallback(RemoteFeatureFlags.Features.LIVE_API_FR_SITE_DETAIL)) {
            return getLiveSite(idAnfr)?.detail?.toTechniqueEntity()
        }

        return queryLocalDatabase(null) {
            getTechniqueDetails(idAnfr)
        }
    }

    suspend fun getPhysiqueDetails(idAnfr: String): List<PhysiqueEntity> {
        if (shouldUseLiveApiFallback(RemoteFeatureFlags.Features.LIVE_API_FR_SITE_DETAIL)) {
            return getLiveSite(idAnfr)?.supports?.mapNotNull { it.toPhysiqueEntity() }.orEmpty()
        }

        return queryLocalDatabase(emptyList()) {
            getPhysiqueDetails(idAnfr)
        }
    }

    /** Antennes brutes d'un site (couverture théorique). FH inclus : le moteur filtre `is_fh`. */
    suspend fun getAntennesForCoverage(idAnfr: String): List<AntenneDbEntity> {
        return queryLocalDatabase(emptyList()) {
            getAntennesByIdAnfr(idAnfr)
        }
    }

    /** Libellés de type d'antenne (tae_id → libelle), pour classer omni / sectorielle. */
    suspend fun getAntennaTypes(): List<RefTypeAntenneDbEntity> {
        return queryLocalDatabase(emptyList()) {
            getAntennaTypes()
        }
    }

    /** Position + opérateur d'un site (couverture théorique). */
    suspend fun getCoverageSiteLocation(idAnfr: String): CoverageSiteLocationEntity? {
        return queryLocalDatabase(null) {
            getCoverageSiteLocation(idAnfr)
        }
    }

    suspend fun getTechniqueDetailsByIds(idAnfrs: List<String>): Map<String, TechniqueEntity> {
        val distinctIds = idAnfrs.filter { it.isNotBlank() }.distinct()
        if (distinctIds.isEmpty()) return emptyMap()

        if (shouldUseLiveApiFallback(RemoteFeatureFlags.Features.LIVE_API_FR_SITE_DETAIL)) {
            return getLiveSites(distinctIds)
                .mapNotNull { site -> site.detail?.toTechniqueEntity() }
                .associateBy { it.idAnfr }
        }

        return distinctIds.chunked(SQLITE_IN_CLAUSE_BATCH_SIZE)
            .flatMap { chunk ->
                queryLocalDatabase(emptyList<TechniqueEntity>()) {
                    getTechniqueDetailsByIds(chunk)
                }
            }
            .associateBy { it.idAnfr }
    }

    suspend fun getTechniqueSummariesByIds(idAnfrs: List<String>): Map<String, TechniqueEntity> {
        val distinctIds = idAnfrs.filter { it.isNotBlank() }.distinct()
        if (distinctIds.isEmpty()) return emptyMap()

        if (shouldUseLiveApiFallback(RemoteFeatureFlags.Features.LIVE_API_FR_SITE_DETAIL)) {
            return getLiveSites(distinctIds)
                .mapNotNull { site -> site.detail?.toTechniqueEntity() }
                .associateBy { it.idAnfr }
        }

        return distinctIds.chunked(SQLITE_IN_CLAUSE_BATCH_SIZE)
            .flatMap { chunk ->
                queryLocalDatabase(emptyList<TechniqueEntity>()) {
                    getTechniqueSummariesByIds(chunk)
                }
            }
            .associateBy { it.idAnfr }
    }

    suspend fun getPhysiqueDetailsByIds(idAnfrs: List<String>): Map<String, List<PhysiqueEntity>> {
        val distinctIds = idAnfrs.filter { it.isNotBlank() }.distinct()
        if (distinctIds.isEmpty()) return emptyMap()

        if (shouldUseLiveApiFallback(RemoteFeatureFlags.Features.LIVE_API_FR_SITE_DETAIL)) {
            return getLiveSites(distinctIds)
                .flatMap { site -> site.supports.mapNotNull { it.toPhysiqueEntity() } }
                .groupBy { it.idAnfr }
        }

        return distinctIds.chunked(SQLITE_IN_CLAUSE_BATCH_SIZE)
            .flatMap { chunk ->
                queryLocalDatabase(emptyList<PhysiqueEntity>()) {
                    getPhysiqueDetailsByIds(chunk)
                }
            }
            .groupBy { it.idAnfr }
    }

    suspend fun getPhysiqueSummariesByIds(idAnfrs: List<String>): Map<String, List<PhysiqueEntity>> {
        val distinctIds = idAnfrs.filter { it.isNotBlank() }.distinct()
        if (distinctIds.isEmpty()) return emptyMap()

        if (shouldUseLiveApiFallback(RemoteFeatureFlags.Features.LIVE_API_FR_SITE_DETAIL)) {
            return getLiveSites(distinctIds)
                .flatMap { site -> site.supports.mapNotNull { it.toPhysiqueEntity() } }
                .groupBy { it.idAnfr }
        }

        return distinctIds.chunked(SQLITE_IN_CLAUSE_BATCH_SIZE)
            .flatMap { chunk ->
                queryLocalDatabase(emptyList<PhysiqueEntity>()) {
                    getPhysiqueSummariesByIds(chunk)
                }
            }
            .groupBy { it.idAnfr }
    }

    suspend fun getFaisceauxDetails(idAnfr: String): List<FaisceauxEntity> {
        return queryLocalDatabase(emptyList()) {
            getFaisceauxDetails(idAnfr)
        }
    }

    suspend fun getPhysiqueByAnfr(idAnfr: String): List<PhysiqueEntity> {
        if (shouldUseLiveApiFallback(RemoteFeatureFlags.Features.LIVE_API_FR_SITE_DETAIL)) {
            return getLiveSite(idAnfr)?.supports?.mapNotNull { it.toPhysiqueEntity() }.orEmpty()
        }

        return queryLocalDatabase(emptyList()) {
            getPhysiqueByAnfr(idAnfr)
        }
    }

    suspend fun getTechniqueByAnfr(idAnfr: String): List<TechniqueEntity> {
        if (shouldUseLiveApiFallback(RemoteFeatureFlags.Features.LIVE_API_FR_SITE_DETAIL)) {
            return listOfNotNull(getLiveSite(idAnfr)?.detail?.toTechniqueEntity())
        }

        return queryLocalDatabase(emptyList()) {
            getTechniqueByAnfr(idAnfr)
        }
    }

    /**
     * Résout un identifiant de « scope favori » (id_support, ou id_anfr en repli) vers les
     * antennes correspondantes, afin d'afficher adresse/commune/opérateur sur la page des
     * photos favorites. Lecture seule ; renvoie une liste vide si la base locale est indisponible.
     */
    suspend fun getFavoriteScopeSiteRows(scopeId: String): List<FavoriteScopeSiteRow> {
        val normalized = scopeId.trim()
        if (normalized.isBlank()) return emptyList()
        return queryLocalDatabase(emptyList()) {
            getFavoriteScopeSiteRows(normalized)
        }
    }

    suspend fun searchAntennasById(query: String): List<LocalisationEntity> {
        if (shouldUseLiveApiFallback(RemoteFeatureFlags.Features.LIVE_API_FR_SITE_DETAIL)) {
            return getAntennasByExactId(query)
        }

        val results = queryLocalDatabase(emptyList()) {
            searchAntennasById(query)
        }
        return enrichRadioBandMasksFromDetails(results)
    }

    suspend fun searchAntennasByText(query: String, limit: Int = 200): List<LocalisationEntity> {
        val results = queryLocalDatabase(emptyList()) {
            searchAntennasByText(query, limit)
        }
        return enrichRadioBandMasksFromDetails(results)
    }

    suspend fun searchAntennasByAddress(query: String, limit: Int = 5000): List<LocalisationEntity> {
        val results = queryLocalDatabase(emptyList()) {
            searchAntennasByAddress(query, limit)
        }
        return enrichRadioBandMasksFromDetails(results)
    }

    suspend fun getAntennasByExactId(exactId: String): List<LocalisationEntity> {
        if (shouldUseLiveApiFallback(RemoteFeatureFlags.Features.LIVE_API_FR_SITE_DETAIL)) {
            return getLiveSite(exactId)
                ?.matches
                ?.mapNotNull { it.toLocalisationEntity() }
                .orEmpty()
        }

        val results = queryLocalDatabase(emptyList()) {
            getAntennasByExactId(exactId)
        }
        return enrichRadioBandMasksFromDetails(results)
    }

    suspend fun enrichRadioBandMasksFromDetails(
        antennas: List<LocalisationEntity>,
        detailBackedBandMask: Int = DETAIL_BACKED_5G_BANDS
    ): List<LocalisationEntity> {
        val requestedDetailBackedBands = detailBackedBandMask and DETAIL_BACKED_5G_BANDS
        if (requestedDetailBackedBands == 0) return antennas

        val idsNeedingFallback = antennas
            .filter { antenna ->
                !antenna.idAnfr.startsWith("CLUSTER_") &&
                    ((antenna.techMask and RadioFilterMasks.TECH_5G) != 0 ||
                        (antenna.bandMask and requestedDetailBackedBands) != 0) &&
                    (antenna.bandMask and requestedDetailBackedBands) != requestedDetailBackedBands
            }
            .map { it.idAnfr }
            .distinct()

        if (idsNeedingFallback.isEmpty()) return antennas

        val techniquesById = getTechniqueDetailsByIds(idsNeedingFallback)
        if (techniquesById.isEmpty()) return antennas

        return antennas.map { antenna ->
            val detailsMask = radioBandMaskFromFrequencyDetails(
                techniquesById[antenna.idAnfr]?.detailsFrequences ?: antenna.frequences
            )
            val enrichedBandMask = antenna.bandMask or detailsMask
            if (enrichedBandMask == antenna.bandMask) {
                antenna
            } else {
                antenna.copy(bandMask = enrichedBandMask).also { copy ->
                    copy.frequences = antenna.frequences
                }
            }
        }
    }

    private fun normalizeOperatorNames(operatorNames: List<String>): List<String> {
        return operatorNames
            .map { it.trim().uppercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    suspend fun getUniqueSupportCountByOperator(operatorNames: List<String>): Int {
        val normalizedNames = normalizeOperatorNames(operatorNames)
        if (normalizedNames.isEmpty()) return 0

        return queryLocalDatabase(0) {
            getUniqueSupportCountByOperator(normalizedNames)
        }
    }

    suspend fun getActiveUniqueSupportCountByOperator(operatorNames: List<String>): Int {
        val normalizedNames = normalizeOperatorNames(operatorNames)
        if (normalizedNames.isEmpty()) return 0

        return queryLocalDatabase(0) {
            getActiveUniqueSupportCountByOperator(normalizedNames)
        }
    }

    suspend fun get2GSupportCountByOperator(operatorNames: List<String>): Int {
        val normalizedNames = normalizeOperatorNames(operatorNames)
        if (normalizedNames.isEmpty()) return 0

        return queryLocalDatabase(0) {
            get2GSupportCountByOperator(normalizedNames)
        }
    }

    suspend fun get3GSupportCountByOperator(operatorNames: List<String>): Int {
        val normalizedNames = normalizeOperatorNames(operatorNames)
        if (normalizedNames.isEmpty()) return 0

        return queryLocalDatabase(0) {
            get3GSupportCountByOperator(normalizedNames)
        }
    }

    suspend fun get4GSupportCountByOperator(operatorNames: List<String>): Int {
        val normalizedNames = normalizeOperatorNames(operatorNames)
        if (normalizedNames.isEmpty()) return 0

        return queryLocalDatabase(0) {
            get4GSupportCountByOperator(normalizedNames)
        }
    }

    suspend fun get5GSupportCountByOperator(operatorNames: List<String>): Int {
        val normalizedNames = normalizeOperatorNames(operatorNames)
        if (normalizedNames.isEmpty()) return 0

        return queryLocalDatabase(0) {
            get5GSupportCountByOperator(normalizedNames)
        }
    }

    suspend fun getSupportCountByOperatorAndBand(operatorNames: List<String>, bandMask: Int): Int {
        val normalizedNames = normalizeOperatorNames(operatorNames)
        if (normalizedNames.isEmpty() || bandMask == 0) return 0

        return queryLocalDatabase(0) {
            getSupportCountByOperatorAndBand(normalizedNames, bandMask)
        }
    }

    suspend fun getActiveSupportRadioCountsByOperator(operatorNames: List<String>): ActiveSupportRadioCounts {
        val normalizedNames = normalizeOperatorNames(operatorNames)
        if (normalizedNames.isEmpty()) return ActiveSupportRadioCounts(emptyMap(), emptyMap())

        val rows = queryLocalDatabase<List<SupportRadioStatsRow>>(emptyList()) {
            getSupportRadioStatsRowsByOperator(normalizedNames)
        }
        return buildActiveSupportRadioCounts(rows)
    }

    suspend fun getCurrentRadioStatsByOperator(operatorNames: List<String>): List<RadioStatRow> {
        val normalizedNames = normalizeOperatorNames(operatorNames)
        if (normalizedNames.isEmpty()) return emptyList()

        if (shouldUseLiveApiFallback(RemoteFeatureFlags.Features.LIVE_API_FR_STATS)) {
            return getLiveCurrentRadioStatsByOperator(normalizedNames)
        }

        return queryLocalDatabase(emptyList()) {
            getCurrentRadioStatsByOperator(normalizedNames)
        }
    }

    suspend fun getWeeklyRadioStatsByOperator(operatorNames: List<String>): List<WeeklyRadioStatRow> {
        val normalizedNames = normalizeOperatorNames(operatorNames)
        if (normalizedNames.isEmpty()) return emptyList()

        if (shouldUseLiveApiFallback(RemoteFeatureFlags.Features.LIVE_API_FR_STATS)) {
            return getLiveWeeklyRadioStatsByOperator(normalizedNames)
        }

        return queryLocalDatabase(emptyList()) {
            getWeeklyRadioStatsByOperator(normalizedNames)
        }
    }

    private suspend fun getLiveDepartmentStats(): List<DepartmentStatRow> {
        return try {
            val response = LiveSitesClient.api.getDepartmentStats()
            if (!response.isSuccessful) {
                AppLogger.w(TAG_DB, "Live department stats failed: ${response.code()}")
                emptyList()
            } else {
                response.body()?.rows?.mapNotNull { it.toDepartmentStatRow() }.orEmpty()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG_DB, "Live department stats unavailable", e)
            emptyList()
        }
    }

    private suspend fun getLiveDepartmentOperatorTechStats(deptCode: String): List<DepartmentOperatorTechRow> {
        return try {
            val response = LiveSitesClient.api.getDepartmentOperatorTechStats(deptCode)
            if (!response.isSuccessful) {
                AppLogger.w(TAG_DB, "Live department operator stats failed: ${response.code()}")
                emptyList()
            } else {
                response.body()?.rows?.mapNotNull { it.toDepartmentOperatorTechRow() }.orEmpty()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG_DB, "Live department operator stats unavailable", e)
            emptyList()
        }
    }

    /**
     * Statistiques par département, pré-calculées par le serveur.
     *
     * Renvoie une liste vide — et non une erreur — quand les tables sont absentes : c'est le cas
     * d'une base générée sur l'appareil, qui crée les tables mais ne les remplit pas, comme d'une
     * base téléchargée avant cette fonctionnalité. L'écran affiche alors « indisponible ».
     */
    suspend fun getDepartmentStats(): List<DepartmentStatRow> {
        if (shouldUseLiveApiFallback(RemoteFeatureFlags.Features.LIVE_API_FR_STATS)) {
            return getLiveDepartmentStats()
        }

        return queryLocalDatabase(emptyList()) {
            getDepartmentStatsRaw(
                SimpleSQLiteQuery(
                    """
                    SELECT dept_code, dept_name, area_km2, population, population_year,
                        supports, supports_active, stations, stations_active,
                        antennas, antennas_active, antennas_fh,
                        stations_per_support, antennas_per_station,
                        supports_per_km2, stations_per_km2, antennas_per_km2,
                        supports_per_1k_hab, stations_per_1k_hab, antennas_per_1k_hab,
                        hab_per_support, hab_per_station, hab_per_antenna
                    FROM dept_stat_current
                    ORDER BY dept_code
                    """.trimIndent()
                )
            )
        }
    }

    suspend fun getDepartmentOperatorTechStats(deptCode: String): List<DepartmentOperatorTechRow> {
        val normalizedCode = deptCode.trim().uppercase(Locale.ROOT)
        if (normalizedCode.isEmpty()) return emptyList()

        if (shouldUseLiveApiFallback(RemoteFeatureFlags.Features.LIVE_API_FR_STATS)) {
            return getLiveDepartmentOperatorTechStats(normalizedCode)
        }

        return queryLocalDatabase(emptyList()) {
            getDepartmentOperatorTechStatsRaw(
                SimpleSQLiteQuery(
                    """
                    SELECT dept_code, operator_name, tech,
                        supports, supports_active, stations, stations_active, antennas, antennas_active
                    FROM dept_stat_operator_tech
                    WHERE dept_code = ?
                    ORDER BY operator_name, tech
                    """.trimIndent(),
                    arrayOf<Any>(normalizedCode)
                )
            )
        }
    }

    /**
     * Département d'un point, deviné par les stations les plus proches — la base ne porte aucun
     * contour administratif, seules les antennes situent le terrain.
     *
     * Boîte de ±0,25° (~25 km) : au-delà, mieux vaut ne rien proposer que de désigner un
     * département voisin au hasard. Le résultat est le département **majoritaire** parmi les
     * stations les plus proches : à quelques kilomètres d'une limite, une station isolée de
     * l'autre côté ne doit pas emporter la décision. À égalité, le département de la station la
     * plus proche gagne.
     */
    suspend fun getDepartmentCodeNear(latitude: Double, longitude: Double): String? {
        val longitudeScale = cos(Math.toRadians(latitude)).coerceAtLeast(MIN_LONGITUDE_SCALE)
        val codes = queryLocalDatabase(emptyList<String>()) {
            getNearestCodesInsee(
                lat = latitude,
                lon = longitude,
                lonScale = longitudeScale,
                minLat = latitude - NEAREST_DEPARTMENT_DEGREES,
                maxLat = latitude + NEAREST_DEPARTMENT_DEGREES,
                minLon = longitude - NEAREST_DEPARTMENT_DEGREES,
                maxLon = longitude + NEAREST_DEPARTMENT_DEGREES,
                limit = NEAREST_DEPARTMENT_SAMPLE
            )
        }.mapNotNull { DepartmentCodes.fromInsee(it) }

        if (codes.isEmpty()) return null
        val counts = codes.groupingBy { it }.eachCount()
        return codes.distinct().maxByOrNull { code -> counts[code] ?: 0 }
    }

    // =================================================================
    // 3. PANNES RÉSEAU (Nouveau système GeoJSON GeoTower)
    // =================================================================
    /**
     * Pannes réseau, servies depuis un **cache mémoire partagé par tout le process**.
     *
     * Le fichier de pannes est national : le retélécharger (ou re-parser le cache disque local) à
     * chaque ouverture de site coûtait un aller-retour réseau complet + un parse JSON intégral, en
     * plein chemin bloquant des fiches support/site. On mémorise donc le résultat pendant
     * [SITES_HS_CACHE_TTL_MS], et les appels concurrents se partagent le même chargement (mutex).
     *
     * @param forceRefresh ignore le cache (pull-to-refresh explicite de l'utilisateur).
     */
    suspend fun getSitesHs(forceRefresh: Boolean = false): List<SiteHsEntity> {
        if (!RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.OUTAGES_DATA)) {
            return emptyList()
        }

        val sourceKey = currentSitesHsSourceKey()
        if (!forceRefresh) readSitesHsCache(sourceKey)?.let { return it }

        return sitesHsCacheMutex.withLock {
            // Un appel concurrent a pu remplir le cache pendant l'attente du verrou.
            if (!forceRefresh) readSitesHsCache(sourceKey)?.let { return@withLock it }

            val sites = loadSitesHs(forceRefresh)
            // Une liste vide vient presque toujours d'un échec (réseau coupé, provider off) :
            // on ne la mémorise pas, sinon l'app resterait « sans pannes » pendant tout le TTL.
            if (sites.isNotEmpty()) storeSitesHsCache(sourceKey, sites)
            sites
        }
    }

    /** Distingue les deux sources possibles : un changement de réglage doit invalider le cache. */
    private fun currentSitesHsSourceKey(): String {
        return if (AppConfig.outagesLocal()) SOURCE_KEY_LOCAL else SOURCE_KEY_REMOTE
    }

    private fun readSitesHsCache(sourceKey: String): List<SiteHsEntity>? {
        val cached = sitesHsCache ?: return null
        if (cached.sourceKey != sourceKey) return null
        if (System.currentTimeMillis() - cached.loadedAtMillis >= SITES_HS_CACHE_TTL_MS) return null
        return cached.sites
    }

    private fun storeSitesHsCache(sourceKey: String, sites: List<SiteHsEntity>) {
        sitesHsCache = CachedSitesHs(sourceKey, System.currentTimeMillis(), sites)
    }

    private suspend fun loadSitesHs(forceRefresh: Boolean): List<SiteHsEntity> {
        // Source « traitée localement » : télécharge les CSV opérateurs + géocode via la base ANFR.
        // Activée par le mode « traitement local » (crans « pannes en local »), seul réglage qui en décide.
        if (AppConfig.outagesLocal()) {
            return localOutageProvider.getSites()
        }
        return loadServerSitesHs(forceRefresh)
    }

    /**
     * Pannes servies par le serveur GeoTower, adossées à une copie conservée sur l'appareil.
     *
     * La copie sert trois choses : afficher des pannes dès le lancement sans attendre le réseau,
     * en garder quand le serveur est injoignable (avant, l'app n'avait tout simplement rien hors
     * ligne), et alimenter la carte « Sites en panne » des réglages. Sa fenêtre de fraîcheur est
     * volontairement la même que celle du cache mémoire : le rythme de téléchargement automatique
     * ne change pas, seul le redémarrage du process cesse de coûter un fichier national.
     */
    private suspend fun loadServerSitesHs(forceRefresh: Boolean): List<SiteHsEntity> {
        if (!RemoteFeatureFlags.isProviderEnabled(RemoteFeatureFlags.Providers.OUTAGES_GEOTOWER)) {
            return emptyList()
        }

        // Lecture + parsing d'un fichier national : jamais sur le dispatcher de l'appelant, qui
        // peut être le fil principal (fiche site, carte).
        val stored = withContext(Dispatchers.IO) { serverOutageCache.load() }
        if (!forceRefresh && stored != null &&
            System.currentTimeMillis() - stored.downloadedAtMillis < SITES_HS_CACHE_TTL_MS
        ) {
            return stored.sites
        }

        return try {
            downloadServerSitesHsFile()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG_MAP, "Outage data request failed", e)
            // Serveur injoignable : la copie conservée, même vieille, vaut mieux que « aucune panne ».
            stored?.sites ?: emptyList()
        }
    }

    /**
     * Télécharge le fichier de pannes du serveur, le range sur l'appareil et renvoie son contenu.
     *
     * Volontairement SANS filet : le bouton « Télécharger les pannes » des réglages a besoin de
     * l'échec pour le dire à l'utilisateur. C'est [loadServerSitesHs] qui décide du repli.
     */
    private suspend fun downloadServerSitesHsFile(): List<SiteHsEntity> {
        val sourceLastUpdate = runCatching {
            api.getSitesHsInfo().lastUpdate
                ?.trim()
                ?.takeIf { it.isNotBlank() && !it.equals("Inconnue", ignoreCase = true) }
        }.getOrNull()

        // 1. On télécharge le fichier brut depuis ton serveur
        val response = api.getSitesHsGeoJson()
        val jsonString = response.string()

        // 2. On lit la structure GeoJSON
        val jsonObject = org.json.JSONObject(jsonString)
        val features = jsonObject.getJSONArray("features")

        val hsList = mutableListOf<SiteHsEntity>()

        // 3. On extrait chaque point (Version blindée anti-crash)
        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val properties = feature.optJSONObject("properties") ?: org.json.JSONObject()

            // 🚨 CORRECTION : L'ARCEP publie parfois des pannes SANS coordonnées GPS !
            // optJSONObject évite que l'application ne crashe si la géométrie est absente.
            val geometry = feature.optJSONObject("geometry")
            val coordinates = geometry?.optJSONArray("coordinates")
            val geometryType = geometry?.optNullableString("type")

            // GeoJSON range toujours [Longitude, Latitude]
            val lon = coordinates?.optDouble(0, 0.0) ?: 0.0
            val lat = coordinates?.optDouble(1, 0.0) ?: 0.0

            // 1. Extraction de toutes les propriétés du JSON
            val stationAnfr = properties.optString("station_anfr", "")
            val operateurStr = properties.optString("operateur", "")

            // Détail technique des pannes par technologie
            val v2g = properties.optNullableString("voix2g")
            val v3g = properties.optNullableString("voix3g")
            val v4g = properties.optNullableString("voix4g")
            val v5g = properties.optNullableString("voix5g")

            val d2g = properties.optNullableString("data2g")
            val d3g = properties.optNullableString("data3g")
            val d4g = properties.optNullableString("data4g")
            val d5g = properties.optNullableString("data5g")

            // Infos de localisation
            val dept = properties.optNullableString("departement")
            val cp = properties.optNullableString("code_postal")
            val insee = properties.optNullableString("code_insee")
            val com = properties.optNullableString("commune")

            // 2. Création de l'objet complet
            val site = SiteHsEntity(
                idAnfr = stationAnfr,
                operateur = operateurStr,
                latitude = lat,
                longitude = lon,
                geometryType = geometryType,

                // Localisation
                departement = dept,
                codePostal = cp,
                codeInsee = insee,
                commune = com,

                // Voix
                voix2g = v2g,
                voix3g = v3g,
                voix4g = v4g,
                voix5g = v5g,

                // Data
                data2g = d2g,
                data3g = d3g,
                data4g = d4g,
                data5g = d5g,

                // Global et Détails
                voixGlobal = properties.optNullableString("voix"),
                dataGlobal = properties.optNullableString("data"),
                raison = properties.optNullableString("raison"),
                detail = properties.optNullableString("detail"),
                propre = properties.optInt("propre", 0),

                // Dates
                debutVoix = properties.optNullableString("debut_voix"),
                finVoix = properties.optNullableString("fin_voix"),
                debutData = properties.optNullableString("debut_data"),
                finData = properties.optNullableString("fin_data"),
                dateDebut = properties.optNullableString("debut"),
                dateFin = properties.optNullableString("fin"),
                sourceLastUpdate = sourceLastUpdate
            )
            hsList.add(site)
        }

        // 🚨 AJOUT : Sauvegarde de la date du jour (Dernière vérification réussie), plus
        // l'heure de génération que le fichier porte dans ses métadonnées : c'est le seul
        // endroit où le serveur la publie, et « À propos » l'affiche à la minute, comme pour
        // une génération locale. Le résumé (nombre, répartition) sert la carte des réglages.
        val downloadedAt = System.currentTimeMillis()
        val currentDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(downloadedAt))
        val generatedAt = OutageServerInfo.recordDownload(
            prefs = outagePrefs,
            lastUpdate = sourceLastUpdate ?: currentDate,
            generatedAtIso = jsonObject.optJSONObject("metadata")?.optNullableString("generated_at"),
            sites = hsList,
            downloadedAtMillis = downloadedAt,
        )

        // Un fichier vide est enregistré comme tel : il dit « plus aucune panne », et garder
        // l'ancienne copie afficherait des pannes déjà résolues.
        withContext(Dispatchers.IO) {
            serverOutageCache.save(
                CachedServerOutages(
                    downloadedAtMillis = downloadedAt,
                    sourceLastUpdate = sourceLastUpdate,
                    serverGeneratedAtMillis = generatedAt,
                    sites = hsList,
                )
            )
        }

        return hsList
    }

    /**
     * Force le téléchargement du fichier de pannes du SERVEUR (bouton « Télécharger les pannes »
     * des réglages), en ignorant la copie conservée. Propage l'échec pour que la carte puisse le dire.
     */
    suspend fun downloadServerSitesHs(): List<SiteHsEntity> {
        val sites = downloadServerSitesHsFile()
        // Le cache mémoire ne porte les pannes serveur que si c'est bien la source active : au
        // cran « pannes en local » du traitement local, il contient les pannes produites sur l'appareil.
        if (!AppConfig.outagesLocal()) {
            sitesHsCache = null
            if (sites.isNotEmpty()) storeSitesHsCache(SOURCE_KEY_REMOTE, sites)
        }
        return sites
    }

    /**
     * Demande au SERVEUR de refabriquer le fichier national des pannes (bouton « Demander une
     * régénération » des réglages), quand son relevé est plus vieux que la panne constatée.
     *
     * Le serveur n'en accepte que deux par heure : ce plafond est une réponse normale (429), pas
     * une panne, donc il revient dans le statut avec le délai avant le prochain créneau. Seules les
     * vraies erreurs (réseau, fonction coupée, générateur absent) lèvent une exception, pour que la
     * carte des réglages puisse les afficher comme telles.
     */
    suspend fun requestServerSitesHsRebuild(): ServerOutageRebuildStatus {
        val response = api.requestSitesHsRebuild()
        val code = response.code()
        if (code == HTTP_TOO_MANY_REQUESTS) {
            val refused = parseSitesHsRebuildBody(response.errorBody()?.string())
            return (refused ?: SitesHsRebuildDto()).toRebuildStatus(code)
        }
        if (!response.isSuccessful) {
            // 503 (fonctionnalité coupée), 500 (générateur introuvable)… : le serveur explique
            // pourquoi dans `detail`, et ce message vaut mieux qu'un code HTTP nu.
            throw IOException(parseServerDetail(response.errorBody()?.string()) ?: "HTTP $code")
        }
        return (response.body() ?: SitesHsRebuildDto()).toRebuildStatus(code)
    }

    /** Où en est la génération demandée au serveur, et ce qu'il reste de quota. Sans effet de bord. */
    suspend fun getServerSitesHsRebuildStatus(): ServerOutageRebuildStatus =
        api.getSitesHsRebuildStatus().toRebuildStatus(200)

    /** Supprime la copie du fichier serveur et tout ce que l'app en retenait. */
    fun clearServerSitesHs() {
        serverOutageCache.clear()
        OutageServerInfo.clear(outagePrefs)
        if (!AppConfig.outagesLocal()) sitesHsCache = null
    }

    /**
     * Force une génération LOCALE des pannes (bouton « Générer maintenant » et planification en fond),
     * en ignorant le TTL du cache. Renvoie la liste produite (ou l'ancien cache si la génération échoue).
     */
    suspend fun regenerateLocalSitesHs(
        onProgress: OutageProgressCallback = NoOutageProgress,
    ): List<SiteHsEntity> {
        val sites = localOutageProvider.regenerate(force = true, onProgress = onProgress)
        // Le cache mémoire deviendrait obsolète juste après une génération manuelle.
        sitesHsCache = null
        if (sites.isNotEmpty()) storeSitesHsCache(currentSitesHsSourceKey(), sites)
        return sites
    }

    private fun org.json.JSONObject.optNullableString(name: String): String? {
        return if (isNull(name)) null else optString(name)
    }

    /** Entrée du cache mémoire des pannes : la source + l'instant de chargement bornent sa validité. */
    private data class CachedSitesHs(
        val sourceKey: String,
        val loadedAtMillis: Long,
        val sites: List<SiteHsEntity>,
    )

    private companion object {
        const val TAG_DB = "GeoTowerDb"
        const val TAG_MAP = "GeoTowerMap"

        /**
         * Durée de vie du cache mémoire des pannes. Volontairement court : les pannes bougent, mais
         * plusieurs écrans ouverts à la suite (carte → support → site) doivent partager un seul
         * chargement. Le cache est au niveau process (plusieurs `AnfrRepository` coexistent :
         * application, activité, service live, worker).
         */
        const val SITES_HS_CACHE_TTL_MS = 5 * 60 * 1000L

        /** Les deux sources possibles des pannes, clés du cache mémoire. */
        const val SOURCE_KEY_LOCAL = "local"
        const val SOURCE_KEY_REMOTE = "remote"

        val sitesHsCacheMutex = Mutex()

        @Volatile
        var sitesHsCache: CachedSitesHs? = null

        /** Demi-côté de la boîte de recherche du département courant, en degrés (~25 km). */
        const val NEAREST_DEPARTMENT_DEGREES = 0.25

        /** Stations examinées pour le vote majoritaire du département. */
        const val NEAREST_DEPARTMENT_SAMPLE = 15

        /** Garde-fou du facteur cos(latitude), pour ne jamais annuler l'écart en longitude. */
        const val MIN_LONGITUDE_SCALE = 0.1

        const val SQLITE_IN_CLAUSE_BATCH_SIZE = 900
        const val LIVE_DETAIL_LOOKUP_LIMIT = 120
        const val LIVE_BBOX_TILE_REQUEST_LIMIT = 96
        const val LIVE_BBOX_MAX_PARALLEL_REQUESTS = 8
        const val LIVE_BBOX_MAX_SUBDIVISION_DEPTH = 4
        const val LIVE_BBOX_MIN_TILE_DEGREES = 0.2

        private val liveCoverageBounds = listOf(
            LiveCoverageBounds(minLat = 41.0, maxLat = 51.6, minLon = -5.8, maxLon = 10.1),
            LiveCoverageBounds(minLat = 14.2, maxLat = 18.3, minLon = -63.4, maxLon = -60.6),
            LiveCoverageBounds(minLat = 1.8, maxLat = 6.2, minLon = -54.9, maxLon = -51.2),
            LiveCoverageBounds(minLat = -21.5, maxLat = -20.8, minLon = 55.1, maxLon = 55.9),
            LiveCoverageBounds(minLat = -13.1, maxLat = -12.6, minLon = 44.9, maxLon = 45.4),
            LiveCoverageBounds(minLat = 46.6, maxLat = 47.2, minLon = -56.6, maxLon = -55.8)
        )
    }
}
