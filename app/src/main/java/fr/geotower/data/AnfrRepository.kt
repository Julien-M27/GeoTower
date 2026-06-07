package fr.geotower.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import fr.geotower.data.api.AnfrService
import fr.geotower.data.db.AppDatabase
import fr.geotower.data.db.GeoTowerDatabaseValidator
import fr.geotower.data.db.GeoTowerDao
import fr.geotower.data.db.InvalidGeoTowerDatabaseException
import fr.geotower.data.db.RadioStatRow
import fr.geotower.data.db.SupportRadioStatsRow
import fr.geotower.data.db.WeeklyRadioStatRow
import fr.geotower.data.models.DbCluster
import fr.geotower.data.models.FaisceauxEntity
import fr.geotower.data.models.FrequencyDetailsCodec
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.RadioFilterMasks
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.data.api.SignalQuestClient
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.models.SiteHsEntity
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ActiveSupportRadioCounts(
    val techCounts: Map<String, Int>,
    val bandCounts: Map<String, Int>
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

    private val dao: GeoTowerDao
        get() = AppDatabase.getDatabase(context).geoTowerDao()

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

    // =================================================================
    // 1. POUR LA CARTE (Affichage ultra-rapide des points)
    // =================================================================
    suspend fun getAntennasInBox(
        latNorth: Double,
        lonEast: Double,
        latSouth: Double,
        lonWest: Double,
        detailBackedBandMask: Int = DETAIL_BACKED_5G_BANDS
    ): List<LocalisationEntity> {
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
        return queryLocalDatabase(null) {
            getTechniqueDetails(idAnfr)
        }
    }

    suspend fun getPhysiqueDetails(idAnfr: String): List<PhysiqueEntity> {
        return queryLocalDatabase(emptyList()) {
            getPhysiqueDetails(idAnfr)
        }
    }

    suspend fun getTechniqueDetailsByIds(idAnfrs: List<String>): Map<String, TechniqueEntity> {
        val distinctIds = idAnfrs.filter { it.isNotBlank() }.distinct()
        if (distinctIds.isEmpty()) return emptyMap()

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
        return queryLocalDatabase(emptyList()) {
            getPhysiqueByAnfr(idAnfr)
        }
    }

    suspend fun getTechniqueByAnfr(idAnfr: String): List<TechniqueEntity> {
        return queryLocalDatabase(emptyList()) {
            getTechniqueByAnfr(idAnfr)
        }
    }

    suspend fun searchAntennasById(query: String): List<LocalisationEntity> {
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

        return queryLocalDatabase(emptyList()) {
            getCurrentRadioStatsByOperator(normalizedNames)
        }
    }

    suspend fun getWeeklyRadioStatsByOperator(operatorNames: List<String>): List<WeeklyRadioStatRow> {
        val normalizedNames = normalizeOperatorNames(operatorNames)
        if (normalizedNames.isEmpty()) return emptyList()

        return queryLocalDatabase(emptyList()) {
            getWeeklyRadioStatsByOperator(normalizedNames)
        }
    }

    // =================================================================
    // 3. PANNES RÉSEAU (Nouveau système GeoJSON GeoTower)
    // =================================================================
    suspend fun getSitesHs(): List<SiteHsEntity> {
        if (
            !RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.OUTAGES_DATA) ||
            !RemoteFeatureFlags.isProviderEnabled(RemoteFeatureFlags.Providers.OUTAGES_GEOTOWER)
        ) {
            return emptyList()
        }
        return try {
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

            // 🚨 AJOUT : Sauvegarde de la date du jour (Dernière vérification réussie)
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val currentDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            prefs.edit().putString("last_hs_update", sourceLastUpdate ?: currentDate).apply()

            hsList
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG_MAP, "Outage data request failed", e)
            emptyList()
        }
    }

    private fun org.json.JSONObject.optNullableString(name: String): String? {
        return if (isNull(name)) null else optString(name)
    }

    private companion object {
        const val TAG_DB = "GeoTowerDb"
        const val TAG_MAP = "GeoTowerMap"
        const val SQLITE_IN_CLAUSE_BATCH_SIZE = 900
    }
}
