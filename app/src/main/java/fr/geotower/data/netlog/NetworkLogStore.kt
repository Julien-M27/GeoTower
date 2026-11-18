package fr.geotower.data.netlog

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.text.format.Formatter
import fr.geotower.utils.PreferenceStores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

enum class NetworkLogStatusFilter {
    ALL,
    SUCCESS_2XX,
    ERRORS
}

enum class NetworkLogExportFormat(val extension: String, val mimeType: String) {
    TEXT("txt", "text/plain"),
    JSON("json", "application/json"),
    CSV("csv", "text/csv")
}

data class NetworkLogStats(
    val totalCount: Long = 0L,
    val errorCount: Long = 0L,
    val totalSizeBytes: Long = 0L,
    val avgDurationMs: Long = 0L
)

object NetworkLogStore {
    const val PREF_RETENTION_DAYS = "network_log_retention_days"
    const val DEFAULT_RETENTION_DAYS = 30

    val RETENTION_OPTIONS_DAYS = listOf(1, 7, 15, 30, 90, 180, 365, 0)

    private const val DB_NAME = "geotower_network_log.db"
    private const val DB_VERSION = 2
    private const val TABLE_NAME = "network_requests"

    private const val COL_ID = "id"
    private const val COL_TIMESTAMP = "timestamp"
    private const val COL_METHOD = "method"
    private const val COL_URL = "url"
    private const val COL_HOST = "host"
    private const val COL_PATH = "path"
    private const val COL_STATUS_CODE = "status_code"
    private const val COL_DURATION_MS = "duration_ms"
    private const val COL_RESPONSE_SIZE = "response_size"
    private const val COL_ERROR_MESSAGE = "error_message"
    private const val COL_FEATURE = "feature"

    private const val MAX_ENTRIES_SAFETY_CAP = 10_000
    private const val PURGE_INTERVAL_INSERTS = 50

    private val insertCounter = AtomicInteger(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    @Volatile
    private var openHelper: DbHelper? = null

    private fun getHelper(context: Context): DbHelper {
        return openHelper ?: synchronized(lock) {
            openHelper ?: DbHelper(context.applicationContext).also { openHelper = it }
        }
    }

    fun init(context: Context) {
        scope.launch {
            runCatching {
                applyRetention(context)
            }
        }
    }

    fun getRetentionDays(context: Context): Int {
        val prefs = context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
        return prefs.getInt(PREF_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)
    }

    fun setRetentionDays(context: Context, days: Int) {
        val prefs = context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
        prefs.edit().putInt(PREF_RETENTION_DAYS, days).apply()
        scope.launch {
            applyRetention(context, days)
        }
    }

    fun record(context: Context, entry: NetworkRequestEntry) {
        scope.launch {
            runCatching {
                val db = getHelper(context).writableDatabase
                val values = ContentValues().apply {
                    put(COL_TIMESTAMP, entry.timestamp)
                    put(COL_METHOD, entry.method)
                    put(COL_URL, entry.url)
                    put(COL_HOST, entry.host)
                    put(COL_PATH, entry.path)
                    put(COL_STATUS_CODE, entry.statusCode)
                    put(COL_DURATION_MS, entry.durationMs)
                    put(COL_RESPONSE_SIZE, entry.responseSizeBytes)
                    put(COL_ERROR_MESSAGE, entry.errorMessage)
                    put(COL_FEATURE, entry.feature ?: entry.displayFeature)
                }
                db.insert(TABLE_NAME, null, values)

                if (insertCounter.incrementAndGet() % PURGE_INTERVAL_INSERTS == 0) {
                    applyRetention(context)
                }
            }
        }
    }

    fun applyRetention(context: Context, retentionDays: Int = getRetentionDays(context)) {
        synchronized(lock) {
            runCatching {
                val db = getHelper(context).writableDatabase
                if (retentionDays > 0) {
                    val cutoff = System.currentTimeMillis() - (retentionDays.toLong() * 24L * 3600L * 1000L)
                    db.delete(TABLE_NAME, "$COL_TIMESTAMP < ?", arrayOf(cutoff.toString()))
                }
                // Cap de sécurité : si plus de 10 000 lignes, on supprime les plus anciennes
                db.execSQL(
                    """
                    DELETE FROM $TABLE_NAME WHERE $COL_ID NOT IN (
                        SELECT $COL_ID FROM $TABLE_NAME ORDER BY $COL_TIMESTAMP DESC LIMIT $MAX_ENTRIES_SAFETY_CAP
                    )
                    """.trimIndent()
                )
            }
        }
    }

    fun getRequests(
        context: Context,
        limit: Int = 300,
        offset: Int = 0,
        searchQuery: String? = null,
        statusFilter: NetworkLogStatusFilter = NetworkLogStatusFilter.ALL
    ): List<NetworkRequestEntry> {
        val results = mutableListOf<NetworkRequestEntry>()
        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        if (!searchQuery.isNullOrBlank()) {
            val q = "%${searchQuery.trim()}%"
            selectionParts.add("($COL_URL LIKE ? OR $COL_PATH LIKE ? OR $COL_HOST LIKE ? OR $COL_METHOD LIKE ? OR $COL_FEATURE LIKE ?)")
            selectionArgs.add(q)
            selectionArgs.add(q)
            selectionArgs.add(q)
            selectionArgs.add(q)
            selectionArgs.add(q)
        }

        when (statusFilter) {
            NetworkLogStatusFilter.ALL -> Unit
            NetworkLogStatusFilter.SUCCESS_2XX -> {
                selectionParts.add("($COL_STATUS_CODE >= 200 AND $COL_STATUS_CODE < 300)")
            }
            NetworkLogStatusFilter.ERRORS -> {
                selectionParts.add("($COL_STATUS_CODE < 200 OR $COL_STATUS_CODE >= 400 OR $COL_ERROR_MESSAGE IS NOT NULL)")
            }
        }

        val selection = if (selectionParts.isEmpty()) null else selectionParts.joinToString(" AND ")
        val args = if (selectionArgs.isEmpty()) null else selectionArgs.toTypedArray()

        synchronized(lock) {
            runCatching {
                val db = getHelper(context).readableDatabase
                val cursor = db.query(
                    TABLE_NAME,
                    null,
                    selection,
                    args,
                    null,
                    null,
                    "$COL_TIMESTAMP DESC",
                    "$offset, $limit"
                )
                cursor.use { c ->
                    val idCol = c.getColumnIndexOrThrow(COL_ID)
                    val timeCol = c.getColumnIndexOrThrow(COL_TIMESTAMP)
                    val methodCol = c.getColumnIndexOrThrow(COL_METHOD)
                    val urlCol = c.getColumnIndexOrThrow(COL_URL)
                    val hostCol = c.getColumnIndexOrThrow(COL_HOST)
                    val pathCol = c.getColumnIndexOrThrow(COL_PATH)
                    val statusCol = c.getColumnIndexOrThrow(COL_STATUS_CODE)
                    val durCol = c.getColumnIndexOrThrow(COL_DURATION_MS)
                    val sizeCol = c.getColumnIndexOrThrow(COL_RESPONSE_SIZE)
                    val errCol = c.getColumnIndexOrThrow(COL_ERROR_MESSAGE)
                    val featCol = c.getColumnIndex(COL_FEATURE)

                    while (c.moveToNext()) {
                        val feature = if (featCol >= 0 && !c.isNull(featCol)) c.getString(featCol) else null
                        results.add(
                            NetworkRequestEntry(
                                id = c.getLong(idCol),
                                timestamp = c.getLong(timeCol),
                                method = c.getString(methodCol),
                                url = c.getString(urlCol),
                                host = c.getString(hostCol),
                                path = c.getString(pathCol),
                                statusCode = c.getInt(statusCol),
                                durationMs = c.getLong(durCol),
                                responseSizeBytes = c.getLong(sizeCol),
                                errorMessage = c.getString(errCol),
                                feature = feature
                            )
                        )
                    }
                }
            }
        }
        return results
    }

    fun getStats(context: Context): NetworkLogStats {
        var total = 0L
        var errors = 0L
        var avgDuration = 0L

        synchronized(lock) {
            runCatching {
                val db = getHelper(context).readableDatabase
                db.rawQuery(
                    "SELECT COUNT(*), AVG($COL_DURATION_MS) FROM $TABLE_NAME",
                    null
                ).use { cursor ->
                    if (cursor.moveToFirst()) {
                        total = cursor.getLong(0)
                        avgDuration = cursor.getDouble(1).toLong()
                    }
                }

                db.rawQuery(
                    "SELECT COUNT(*) FROM $TABLE_NAME WHERE $COL_STATUS_CODE < 200 OR $COL_STATUS_CODE >= 400 OR $COL_ERROR_MESSAGE IS NOT NULL",
                    null
                ).use { cursor ->
                    if (cursor.moveToFirst()) {
                        errors = cursor.getLong(0)
                    }
                }
            }
        }

        val dbFile = context.getDatabasePath(DB_NAME)
        val sizeBytes = if (dbFile.exists()) dbFile.length() else 0L

        return NetworkLogStats(
            totalCount = total,
            errorCount = errors,
            totalSizeBytes = sizeBytes,
            avgDurationMs = avgDuration
        )
    }

    fun clear(context: Context) {
        synchronized(lock) {
            runCatching {
                val db = getHelper(context).writableDatabase
                db.delete(TABLE_NAME, null, null)
                db.execSQL("VACUUM")
            }
        }
    }

    fun exportAsText(context: Context, requests: List<NetworkRequestEntry>? = null): String {
        val list = requests ?: getRequests(context, limit = MAX_ENTRIES_SAFETY_CAP)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val sb = StringBuilder()
        sb.append("=== GeoTower Network Request Log ===\n")
        sb.append("Generated: ${dateFormat.format(Date())}\n")
        sb.append("Total entries: ${list.size}\n\n")

        for (item in list) {
            val dateStr = dateFormat.format(Date(item.timestamp))
            val statusStr = if (item.statusCode > 0) "${item.statusCode}" else "ERR"
            val sizeStr = if (item.responseSizeBytes >= 0) "${item.responseSizeBytes} B" else "-"
            sb.append("[$dateStr] ${item.method} $statusStr (${item.durationMs}ms, $sizeStr) · ${item.displayFeature}\n")
            sb.append("  URL: ${item.url}\n")
            if (!item.errorMessage.isNullOrBlank()) {
                sb.append("  Error: ${item.errorMessage}\n")
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    fun exportAsJson(context: Context, requests: List<NetworkRequestEntry>? = null): String {
        val list = requests ?: getRequests(context, limit = MAX_ENTRIES_SAFETY_CAP)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
        val arr = org.json.JSONArray()
        for (item in list) {
            arr.put(
                org.json.JSONObject().apply {
                    put("id", item.id)
                    put("timestamp", item.timestamp)
                    put("date", dateFormat.format(Date(item.timestamp)))
                    put("method", item.method)
                    put("url", item.url)
                    put("host", item.host)
                    put("path", item.path)
                    put("statusCode", item.statusCode)
                    put("durationMs", item.durationMs)
                    put("responseSizeBytes", item.responseSizeBytes)
                    put("feature", item.displayFeature)
                    put("errorMessage", item.errorMessage)
                }
            )
        }
        return arr.toString(2)
    }

    fun exportAsCsv(context: Context, requests: List<NetworkRequestEntry>? = null): String {
        val list = requests ?: getRequests(context, limit = MAX_ENTRIES_SAFETY_CAP)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        fun csvCell(value: String?): String {
            val v = value.orEmpty()
            return if (v.contains(',') || v.contains('"') || v.contains('\n') || v.contains('\r')) {
                "\"" + v.replace("\"", "\"\"") + "\""
            } else {
                v
            }
        }
        val sb = StringBuilder()
        sb.append("id,timestamp,date,method,statusCode,durationMs,responseSizeBytes,feature,host,path,url,errorMessage\n")
        for (item in list) {
            sb.append(item.id).append(',')
            sb.append(item.timestamp).append(',')
            sb.append(csvCell(dateFormat.format(Date(item.timestamp)))).append(',')
            sb.append(csvCell(item.method)).append(',')
            sb.append(item.statusCode).append(',')
            sb.append(item.durationMs).append(',')
            sb.append(item.responseSizeBytes).append(',')
            sb.append(csvCell(item.displayFeature)).append(',')
            sb.append(csvCell(item.host)).append(',')
            sb.append(csvCell(item.path)).append(',')
            sb.append(csvCell(item.url)).append(',')
            sb.append(csvCell(item.errorMessage)).append('\n')
        }
        return sb.toString()
    }

    fun shareLog(
        context: Context,
        chooserTitle: String,
        format: NetworkLogExportFormat = NetworkLogExportFormat.TEXT,
        requests: List<NetworkRequestEntry>? = null
    ): Boolean {
        return runCatching {
            val content = when (format) {
                NetworkLogExportFormat.TEXT -> exportAsText(context, requests)
                NetworkLogExportFormat.JSON -> exportAsJson(context, requests)
                NetworkLogExportFormat.CSV -> exportAsCsv(context, requests)
            }
            if (content.isBlank()) return false

            val exportDir = File(context.cacheDir, "netlog").apply { mkdirs() }
            val file = File(exportDir, "geotower_network_log.${format.extension}")
            file.writeText(content, Charsets.UTF_8)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = format.mimeType
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                putExtra(Intent.EXTRA_STREAM, uri)
                if (format == NetworkLogExportFormat.TEXT && content.length <= 64_000) {
                    putExtra(Intent.EXTRA_TEXT, content)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    private class DbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_TIMESTAMP INTEGER NOT NULL,
                    $COL_METHOD TEXT NOT NULL,
                    $COL_URL TEXT NOT NULL,
                    $COL_HOST TEXT NOT NULL,
                    $COL_PATH TEXT NOT NULL,
                    $COL_STATUS_CODE INTEGER NOT NULL,
                    $COL_DURATION_MS INTEGER NOT NULL,
                    $COL_RESPONSE_SIZE INTEGER NOT NULL,
                    $COL_ERROR_MESSAGE TEXT,
                    $COL_FEATURE TEXT
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_requests_timestamp ON $TABLE_NAME ($COL_TIMESTAMP DESC)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_requests_status ON $TABLE_NAME ($COL_STATUS_CODE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_requests_host ON $TABLE_NAME ($COL_HOST)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                runCatching {
                    db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_FEATURE TEXT")
                }
            }
        }
    }
}
