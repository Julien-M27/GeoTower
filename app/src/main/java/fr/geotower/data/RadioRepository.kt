package fr.geotower.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import fr.geotower.data.db.RadioDatabaseValidator
import fr.geotower.data.models.RadioMapCategoryMasks
import fr.geotower.data.models.RadioMapMarker
import fr.geotower.data.models.RadioServiceMasks
import fr.geotower.data.models.RadioSystemMasks
import fr.geotower.utils.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.InflaterInputStream
import kotlin.math.roundToInt

class RadioRepository(private val context: Context) {

    private data class LongitudeRange(val minE6: Int, val maxE6: Int)

    private data class QueryBounds(
        val minLatE6: Int,
        val maxLatE6: Int,
        val longitudeRanges: List<LongitudeRange>
    )

    private data class SqlFilter(
        val clause: String,
        val args: List<String>
    )

    suspend fun getVisibleMarkers(
        zoom: Double,
        latNorth: Double,
        lonEast: Double,
        latSouth: Double,
        lonWest: Double,
        serviceMask: Int = RadioServiceMasks.ALL,
        categoryMask: Int = RadioMapCategoryMasks.ALL
    ): List<RadioMapMarker> = withContext(Dispatchers.IO) {
        if (serviceMask == 0 || categoryMask == 0 || !databaseFile().isFile) return@withContext emptyList()

        try {
            val bounds = queryBounds(latNorth, lonEast, latSouth, lonWest)
            openDatabase().use { db ->
                if (zoom < RAW_MARKER_MIN_ZOOM) {
                    bounds.longitudeRanges
                        .flatMap { range -> queryClusters(db, zoom, bounds, range, serviceMask, categoryMask) }
                        .sortedByDescending { it.clusterCount }
                        .take(MAX_CLUSTER_MARKERS)
                } else {
                    bounds.longitudeRanges
                        .flatMap { range -> querySites(db, bounds, range, serviceMask, categoryMask) }
                        .distinctBy { it.id }
                        .take(MAX_RAW_MARKERS)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "Radio database map query failed", e)
            emptyList()
        }
    }

    fun isInstalled(): Boolean {
        return databaseFile().isFile
    }

    suspend fun getMarkersForSupport(supportId: String, limit: Int = MAX_SUPPORT_MARKERS): List<RadioMapMarker> = withContext(Dispatchers.IO) {
        val candidates = supportIdCandidates(supportId)
        if (candidates.isEmpty() || !databaseFile().isFile) return@withContext emptyList()

        try {
            openDatabase().use { db ->
                val placeholders = candidates.joinToString(",") { "?" }
                val cursor = db.rawQuery(
                    """
                    SELECT
                        s.sta_nm_anfr,
                        s.sup_id,
                        s.lat_e6,
                        s.lon_e6,
                        s.service_mask,
                        s.system_mask,
                        a.label,
                        s.emitter_count,
                        s.antenna_count,
                        s.min_freq_khz,
                        s.max_freq_khz,
                        d.detail_z
                    FROM non_mobile_site s
                    LEFT JOIN ref_actor a ON a.adm_id = s.adm_id
                    LEFT JOIN non_mobile_detail d ON d.sta_nm_anfr = s.sta_nm_anfr AND d.sup_id = s.sup_id
                    WHERE s.sup_id IN ($placeholders)
                      ${excludePublicMobileFhSql()}
                    ORDER BY s.service_mask, a.label, s.emitter_count DESC
                    LIMIT ?
                    """.trimIndent(),
                    (
                        candidates +
                            excludePublicMobileFhArgs() +
                            limit.coerceAtLeast(1).toString()
                    ).toTypedArray()
                )

                cursor.use { it.toRadioMarkers(typeLabels = loadAntennaTypeLabels(db)) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "Radio support query failed for supportId=$supportId", e)
            emptyList()
        }
    }

    suspend fun getMarkerForSite(stationId: String, supportId: String): RadioMapMarker? = withContext(Dispatchers.IO) {
        val stationCandidates = supportIdCandidates(stationId)
        val supportCandidates = supportIdCandidates(supportId)
        if (stationCandidates.isEmpty() || supportCandidates.isEmpty() || !databaseFile().isFile) {
            return@withContext null
        }

        try {
            openDatabase().use { db ->
                val stationPlaceholders = stationCandidates.joinToString(",") { "?" }
                val supportPlaceholders = supportCandidates.joinToString(",") { "?" }
                val cursor = db.rawQuery(
                    """
                    SELECT
                        s.sta_nm_anfr,
                        s.sup_id,
                        s.lat_e6,
                        s.lon_e6,
                        s.service_mask,
                        s.system_mask,
                        a.label,
                        s.emitter_count,
                        s.antenna_count,
                        s.min_freq_khz,
                        s.max_freq_khz,
                        d.detail_z
                    FROM non_mobile_site s
                    LEFT JOIN ref_actor a ON a.adm_id = s.adm_id
                    LEFT JOIN non_mobile_detail d ON d.sta_nm_anfr = s.sta_nm_anfr AND d.sup_id = s.sup_id
                    WHERE s.sta_nm_anfr IN ($stationPlaceholders)
                      AND s.sup_id IN ($supportPlaceholders)
                      ${excludePublicMobileFhSql()}
                    LIMIT 1
                    """.trimIndent(),
                    (
                        stationCandidates +
                            supportCandidates +
                            excludePublicMobileFhArgs()
                    ).toTypedArray()
                )

                cursor.use { it.toRadioMarkers(typeLabels = loadAntennaTypeLabels(db)).firstOrNull() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "Radio site query failed for stationId=$stationId supportId=$supportId", e)
            null
        }
    }

    private fun querySites(
        db: SQLiteDatabase,
        bounds: QueryBounds,
        longitudeRange: LongitudeRange,
        serviceMask: Int,
        categoryMask: Int
    ): List<RadioMapMarker> {
        val categoryFilter = radioCategoryFilter(categoryMask)
        val cursor = db.rawQuery(
            """
            SELECT
                s.sta_nm_anfr,
                s.sup_id,
                s.lat_e6,
                s.lon_e6,
                s.service_mask,
                s.system_mask,
                a.label,
                s.emitter_count,
                s.antenna_count,
                s.min_freq_khz,
                s.max_freq_khz,
                d.detail_z
            FROM non_mobile_site s
            LEFT JOIN ref_actor a ON a.adm_id = s.adm_id
            LEFT JOIN non_mobile_detail d ON d.sta_nm_anfr = s.sta_nm_anfr AND d.sup_id = s.sup_id
            WHERE s.lat_e6 BETWEEN ? AND ?
              AND s.lon_e6 BETWEEN ? AND ?
              AND (s.service_mask & ?) != 0
              ${categoryFilter.clause}
              ${excludePublicMobileFhSql()}
            ORDER BY s.emitter_count DESC, s.antenna_count DESC
            LIMIT ?
            """.trimIndent(),
            (listOf(
                bounds.minLatE6.toString(),
                bounds.maxLatE6.toString(),
                longitudeRange.minE6.toString(),
                longitudeRange.maxE6.toString(),
                serviceMask.toString()
            ) + categoryFilter.args + excludePublicMobileFhArgs() + listOf(
                MAX_RAW_MARKERS.toString()
            )).toTypedArray()
        )

        return cursor.use { it.toRadioMarkers() }
    }

    private fun queryClusters(
        db: SQLiteDatabase,
        zoom: Double,
        bounds: QueryBounds,
        longitudeRange: LongitudeRange,
        serviceMask: Int,
        categoryMask: Int
    ): List<RadioMapMarker> {
        val bucket = clusterBucketE6(zoom)
        val categoryFilter = radioCategoryFilter(categoryMask)
        val clusterServiceMaskSql = bitwiseOrSql(
            column = "s.service_mask",
            bits = RADIO_SERVICE_MASK_BITS
        )
        val clusterSystemMaskSql = bitwiseOrSql(
            column = "s.system_mask",
            bits = RADIO_SYSTEM_MASK_BITS
        )
        val cursor = db.rawQuery(
            """
            SELECT
                CAST(s.lat_e6 / ? AS INTEGER) AS lat_bucket,
                CAST(s.lon_e6 / ? AS INTEGER) AS lon_bucket,
                AVG(s.lat_e6) AS center_lat_e6,
                AVG(s.lon_e6) AS center_lon_e6,
                COUNT(*) AS site_count,
                $clusterServiceMaskSql AS service_mask,
                $clusterSystemMaskSql AS system_mask,
                CASE WHEN COUNT(DISTINCT s.adm_id) = 1 THEN MIN(a.label) ELSE NULL END AS actor_label,
                SUM(s.emitter_count) AS emitter_count,
                SUM(s.antenna_count) AS antenna_count,
                MIN(s.min_freq_khz) AS min_freq_khz,
                MAX(s.max_freq_khz) AS max_freq_khz
            FROM non_mobile_site s
            LEFT JOIN ref_actor a ON a.adm_id = s.adm_id
            WHERE s.lat_e6 BETWEEN ? AND ?
              AND s.lon_e6 BETWEEN ? AND ?
              AND (s.service_mask & ?) != 0
              ${categoryFilter.clause}
              ${excludePublicMobileFhSql()}
            GROUP BY lat_bucket, lon_bucket
            ORDER BY site_count DESC
            LIMIT ?
            """.trimIndent(),
            (listOf(
                bucket.toString(),
                bucket.toString(),
                bounds.minLatE6.toString(),
                bounds.maxLatE6.toString(),
                longitudeRange.minE6.toString(),
                longitudeRange.maxE6.toString(),
                serviceMask.toString()
            ) + categoryFilter.args + excludePublicMobileFhArgs() + listOf(
                MAX_CLUSTER_MARKERS.toString()
            )).toTypedArray()
        )

        return cursor.use {
            val latBucketIndex = it.getColumnIndexOrThrow("lat_bucket")
            val lonBucketIndex = it.getColumnIndexOrThrow("lon_bucket")
            val latIndex = it.getColumnIndexOrThrow("center_lat_e6")
            val lonIndex = it.getColumnIndexOrThrow("center_lon_e6")
            val countIndex = it.getColumnIndexOrThrow("site_count")
            val serviceIndex = it.getColumnIndexOrThrow("service_mask")
            val systemIndex = it.getColumnIndexOrThrow("system_mask")
            val actorIndex = it.getColumnIndexOrThrow("actor_label")
            val emitterIndex = it.getColumnIndexOrThrow("emitter_count")
            val antennaIndex = it.getColumnIndexOrThrow("antenna_count")
            val minFreqIndex = it.getColumnIndexOrThrow("min_freq_khz")
            val maxFreqIndex = it.getColumnIndexOrThrow("max_freq_khz")

            buildList {
                while (it.moveToNext()) {
                    val count = it.getInt(countIndex)
                    add(
                        RadioMapMarker(
                            id = "${RadioMapMarker.RADIO_CLUSTER_ID_PREFIX}${it.getInt(latBucketIndex)}_${it.getInt(lonBucketIndex)}",
                            latitude = it.getDouble(latIndex) / 1_000_000.0,
                            longitude = it.getDouble(lonIndex) / 1_000_000.0,
                            serviceMask = it.getInt(serviceIndex),
                            systemMask = it.getInt(systemIndex),
                            actorLabel = it.getNullableString(actorIndex),
                            emitterCount = it.getLong(emitterIndex).coerceToInt(),
                            antennaCount = it.getLong(antennaIndex).coerceToInt(),
                            minFreqKhz = it.getNullableInt(minFreqIndex),
                            maxFreqKhz = it.getNullableInt(maxFreqIndex),
                            clusterCount = count
                        )
                    )
                }
            }
        }
    }

    private fun clusterBucketE6(zoom: Double): Int {
        return when {
            zoom < 6.5 -> 2_000_000
            zoom < 8.0 -> 900_000
            zoom < 9.5 -> 350_000
            zoom < 10.5 -> 140_000
            zoom < 11.5 -> 60_000
            zoom < 12.5 -> 25_000
            else -> 12_000
        }
    }

    private fun queryBounds(
        latNorth: Double,
        lonEast: Double,
        latSouth: Double,
        lonWest: Double
    ): QueryBounds {
        val south = sanitizeLatitude(latSouth, -90.0)
        val north = sanitizeLatitude(latNorth, 90.0)
        val minLatE6 = minOf(south, north).toE6()
        val maxLatE6 = maxOf(south, north).toE6()

        val rawWest = sanitizeLongitude(lonWest, -180.0)
        val rawEast = sanitizeLongitude(lonEast, 180.0)
        val rawSpan = rawEast - rawWest

        val ranges = if (rawSpan >= 360.0 || rawSpan <= -360.0 || (rawWest <= -180.0 && rawEast >= 180.0)) {
            listOf(LongitudeRange((-180.0).toE6(), 180.0.toE6()))
        } else {
            val west = normalizeLongitude(rawWest)
            val east = normalizeLongitude(rawEast)
            if (rawSpan < 0.0 || west > east) {
                listOf(
                    LongitudeRange(west.toE6(), 180.0.toE6()),
                    LongitudeRange((-180.0).toE6(), east.toE6())
                )
            } else {
                listOf(LongitudeRange(west.toE6(), east.toE6()))
            }
        }

        return QueryBounds(minLatE6 = minLatE6, maxLatE6 = maxLatE6, longitudeRanges = ranges)
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

    private fun Double.toE6(): Int {
        return (this * 1_000_000.0).roundToInt()
    }

    private fun supportIdCandidates(value: String): List<String> {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return emptyList()
        if (!trimmed.all { it.isDigit() }) return listOf(trimmed)

        val compact = trimmed.trimStart('0').ifEmpty { "0" }
        return listOf(trimmed, compact, trimmed.padStart(10, '0')).distinct()
    }

    private fun excludePublicMobileFhSql(): String {
        val actorPredicates = PUBLIC_MOBILE_OPERATOR_MARKERS.joinToString(" OR ") {
            "UPPER(COALESCE(a.label, '')) LIKE ?"
        }
        return "AND NOT ((s.service_mask & ?) != 0 AND ($actorPredicates))"
    }

    private fun excludePublicMobileFhArgs(): List<String> {
        return listOf(RadioServiceMasks.FH.toString()) +
            PUBLIC_MOBILE_OPERATOR_MARKERS.map { "%$it%" }
    }

    private fun radioCategoryFilter(categoryMask: Int): SqlFilter {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()

        fun addSystem(mask: Int) {
            clauses += "(s.system_mask & ?) != 0"
            args += mask.toString()
        }

        fun addService(mask: Int) {
            clauses += "(s.service_mask & ?) != 0"
            args += mask.toString()
        }

        if ((categoryMask and RadioMapCategoryMasks.TV) != 0) {
            addSystem(RadioSystemMasks.TV)
        }
        if ((categoryMask and RadioMapCategoryMasks.RADIO) != 0) {
            addSystem(RadioSystemMasks.RADIO)
        }
        if ((categoryMask and RadioMapCategoryMasks.PRIVATE_MOBILE) != 0) {
            addService(RadioServiceMasks.PRIVATE or RadioServiceMasks.RAIL or RadioServiceMasks.TRANSPORT)
        }
        if ((categoryMask and RadioMapCategoryMasks.FH) != 0) {
            addService(RadioServiceMasks.FH)
        }
        if ((categoryMask and RadioMapCategoryMasks.OTHER) != 0) {
            addService(RadioServiceMasks.SATELLITE or RadioServiceMasks.RADAR or RadioServiceMasks.OTHER)
        }

        return if (clauses.isEmpty()) {
            SqlFilter("AND 0", emptyList())
        } else {
            SqlFilter("AND (${clauses.joinToString(" OR ")})", args)
        }
    }

    private fun bitwiseOrSql(column: String, bits: List<Int>): String {
        return bits.joinToString(" + ") { bit ->
            "MAX(CASE WHEN ($column & $bit) != 0 THEN $bit ELSE 0 END)"
        }
    }

    private fun Cursor.toRadioMarkers(typeLabels: Map<String, String> = emptyMap()): List<RadioMapMarker> {
        val staIndex = getColumnIndexOrThrow("sta_nm_anfr")
        val supIndex = getColumnIndexOrThrow("sup_id")
        val latIndex = getColumnIndexOrThrow("lat_e6")
        val lonIndex = getColumnIndexOrThrow("lon_e6")
        val serviceIndex = getColumnIndexOrThrow("service_mask")
        val systemIndex = getColumnIndexOrThrow("system_mask")
        val actorIndex = getColumnIndexOrThrow("label")
        val emitterIndex = getColumnIndexOrThrow("emitter_count")
        val antennaIndex = getColumnIndexOrThrow("antenna_count")
        val minFreqIndex = getColumnIndexOrThrow("min_freq_khz")
        val maxFreqIndex = getColumnIndexOrThrow("max_freq_khz")
        val detailIndex = getColumnIndex("detail_z")

        return buildList {
            while (moveToNext()) {
                val sta = getString(staIndex).orEmpty()
                val sup = getString(supIndex).orEmpty()
                add(
                    RadioMapMarker(
                        id = "RADIO_${sta}_$sup",
                        latitude = getInt(latIndex) / 1_000_000.0,
                        longitude = getInt(lonIndex) / 1_000_000.0,
                        serviceMask = getInt(serviceIndex),
                        systemMask = getInt(systemIndex),
                        actorLabel = getNullableString(actorIndex),
                        emitterCount = getInt(emitterIndex),
                        antennaCount = getInt(antennaIndex),
                        minFreqKhz = getNullableInt(minFreqIndex),
                        maxFreqKhz = getNullableInt(maxFreqIndex),
                        detailText = if (detailIndex >= 0) decodeDetail(getNullableString(detailIndex), typeLabels) else null,
                        stationId = sta,
                        supportId = sup
                    )
                )
            }
        }
    }

    private fun decodeDetail(raw: String?, typeLabels: Map<String, String>): String? {
        val value = raw?.takeIf { it.isNotBlank() } ?: return null
        if (!value.startsWith("Z1:")) return normalizeAntennaTypeLabels(value, typeLabels)

        return try {
            val compressed = Base64.decode(value.removePrefix("Z1:"), Base64.DEFAULT)
            InflaterInputStream(ByteArrayInputStream(compressed)).use { input ->
                normalizeAntennaTypeLabels(input.readBytes().toString(Charsets.UTF_8), typeLabels)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Radio detail decompression failed", e)
            null
        }
    }

    private fun loadAntennaTypeLabels(db: SQLiteDatabase): Map<String, String> {
        return try {
            db.rawQuery("SELECT tae_id, label FROM ref_type_antenne", emptyArray()).use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow("tae_id")
                val labelIndex = cursor.getColumnIndexOrThrow("label")
                buildMap {
                    while (cursor.moveToNext()) {
                        val label = cursor.getNullableString(labelIndex)?.takeIf { it.isNotBlank() }
                        if (label != null) {
                            put(cursor.getInt(idIndex).toString(), label)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun normalizeAntennaTypeLabels(value: String, typeLabels: Map<String, String>): String {
        if (typeLabels.isEmpty() || "TAE_ID" !in value) return value
        return value
            .replace(Regex("""TAE_ID\s+(\d+):""", RegexOption.IGNORE_CASE)) { match ->
                "${typeLabels[match.groupValues[1]] ?: match.value.removeSuffix(":")}:"
            }
            .replace(Regex("""\s*\[TAE_ID\s+\d+\]""", RegexOption.IGNORE_CASE), "")
    }

    private fun Cursor.getNullableString(index: Int): String? {
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.getNullableInt(index: Int): Int? {
        return if (isNull(index)) null else getInt(index)
    }

    private fun Long.coerceToInt(): Int {
        return coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }

    private fun openDatabase(): SQLiteDatabase {
        return SQLiteDatabase.openDatabase(databaseFile().absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    }

    private fun databaseFile(): File {
        return context.getDatabasePath(RadioDatabaseValidator.DB_NAME)
    }

    private companion object {
        const val TAG = "GeoTowerRadioDb"
        const val RAW_MARKER_MIN_ZOOM = 13.0
        const val MAX_RAW_MARKERS = 4500
        const val MAX_CLUSTER_MARKERS = 1800
        const val MAX_SUPPORT_MARKERS = 40

        private val RADIO_SERVICE_MASK_BITS = listOf(
            RadioServiceMasks.BROADCAST,
            RadioServiceMasks.PRIVATE,
            RadioServiceMasks.RAIL,
            RadioServiceMasks.TRANSPORT,
            RadioServiceMasks.FH,
            RadioServiceMasks.SATELLITE,
            RadioServiceMasks.RADAR,
            RadioServiceMasks.OTHER
        )

        private val RADIO_SYSTEM_MASK_BITS = listOf(
            RadioSystemMasks.FM,
            RadioSystemMasks.DVB_T,
            RadioSystemMasks.DAB,
            RadioSystemMasks.AM,
            RadioSystemMasks.FH,
            RadioSystemMasks.GSM_R,
            RadioSystemMasks.PMR,
            RadioSystemMasks.TETRA,
            RadioSystemMasks.POCSAG,
            RadioSystemMasks.COM_TER,
            RadioSystemMasks.COM_MAR,
            RadioSystemMasks.AIS,
            RadioSystemMasks.SAT,
            RadioSystemMasks.RADAR,
            RadioSystemMasks.BLR,
            RadioSystemMasks.LTE_PRIVATE,
            RadioSystemMasks.BROADCAST_5G,
            RadioSystemMasks.METEO_RS,
            RadioSystemMasks.TELEMETRY,
            RadioSystemMasks.OTHER
        )

        private val PUBLIC_MOBILE_OPERATOR_MARKERS = listOf(
            "BOUYGUES",
            "ORANGE",
            "SFR",
            "SOCIETE FRANCAISE DU RADIOTELEPHONE",
            "FREE MOBILE",
            "FREE CARAIB",
            "DIGICEL",
            "OUTREMER",
            "SRR",
            "ZEOP",
            "MAORE",
            "DAUPHIN",
            "TELCO OI",
            "VITI",
            "PMT",
            "VODAFONE",
            "SPM TELECOM",
            "ONATI",
            "GLOBALTEL",
            "UTS CARAIBE",
            "OPT"
        )
    }
}
