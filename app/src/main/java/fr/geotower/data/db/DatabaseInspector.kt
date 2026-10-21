package fr.geotower.data.db

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.google.gson.Gson
import fr.geotower.data.models.SiteHsEntity
import fr.geotower.data.outages.CachedOutages
import fr.geotower.data.outages.CachedServerOutages
import fr.geotower.data.outages.OutageLocalCache
import fr.geotower.data.outages.OutageSourceMode
import fr.geotower.data.outages.ServerOutageCache
import fr.geotower.data.outages.SitesHsGeoJsonParser
import fr.geotower.data.outages.cachedOutageSitesAreUsable
import fr.geotower.utils.AppConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class InspectableDatabase(
    val fileName: String,
) {
    MOBILE(GeoTowerDatabaseValidator.DB_NAME),
    RADIO(RadioDatabaseValidator.DB_NAME),
    ENB(EnbDatabaseValidator.DB_NAME),
    OUTAGES_OPERATORS(ServerOutageCache.fileFor(OutageSourceMode.OPERATORS)),
    OUTAGES_DAILY(ServerOutageCache.fileFor(OutageSourceMode.DAILY));

    val isOutages: Boolean
        get() = this == OUTAGES_OPERATORS || this == OUTAGES_DAILY
}

data class DatabaseTableInfo(
    val name: String,
    val rowCount: Long,
)

data class DatabaseColumnInfo(
    val name: String,
    val type: String,
    val notNull: Boolean,
    val primaryKeyPosition: Int,
)

data class DatabaseTablePage(
    val tableName: String,
    val columns: List<DatabaseColumnInfo>,
    val rows: List<List<String?>>,
    val page: Int,
    val pageSize: Int,
    val totalRows: Long,
    val searchQuery: String = "",
)

data class DatabaseSearchSuggestion(
    val columnName: String,
    val value: String,
)

object DatabaseInspectorRules {
    const val DEFAULT_PAGE_SIZE = 50
    const val MAX_SEARCH_SUGGESTIONS = 6

    fun tableNamesQuery(): String =
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name"

    fun tableRowsQuery(tableName: String): String =
        "SELECT * FROM ${quoteIdentifier(tableName)} LIMIT ? OFFSET ?"

    fun filteredRowsQuery(tableName: String, columns: List<String>): String =
        "SELECT * FROM ${quoteIdentifier(tableName)} WHERE ${searchPredicate(columns)} LIMIT ? OFFSET ?"

    fun filteredCountQuery(tableName: String, columns: List<String>): String =
        "SELECT COUNT(*) FROM ${quoteIdentifier(tableName)} WHERE ${searchPredicate(columns)}"

    fun searchArguments(columns: List<String>, query: String): Array<String> =
        Array(columns.size) { "%${escapeLikeQuery(query)}%" }

    fun searchSuggestions(
        page: DatabaseTablePage?,
        query: String,
        limit: Int = MAX_SEARCH_SUGGESTIONS,
    ): List<DatabaseSearchSuggestion> {
        val normalizedQuery = query.trim()
        if (
            normalizedQuery.codePointCount(0, normalizedQuery.length) < 2 ||
            limit <= 0 ||
            page == null ||
            page.page != 0 ||
            page.searchQuery != normalizedQuery
        ) {
            return emptyList()
        }

        data class Candidate(
            val suggestion: DatabaseSearchSuggestion,
            val startsWithQuery: Boolean,
            var occurrences: Int = 1,
        )

        val candidates = linkedMapOf<String, Candidate>()
        page.rows.forEach { row ->
            page.columns.forEachIndexed { columnIndex, column ->
                val value = row.getOrNull(columnIndex) ?: return@forEachIndexed
                if (
                    value.isBlank() ||
                    (value.startsWith("<BLOB ") && value.endsWith(" octets>")) ||
                    !value.contains(normalizedQuery, ignoreCase = true) ||
                    value.equals(normalizedQuery, ignoreCase = true)
                ) {
                    return@forEachIndexed
                }

                val valueKey = value.lowercase(Locale.ROOT)
                val startsWithQuery = value.startsWith(normalizedQuery, ignoreCase = true)
                val existing = candidates[valueKey]
                if (existing == null) {
                    candidates[valueKey] = Candidate(
                        suggestion = DatabaseSearchSuggestion(column.name, value),
                        startsWithQuery = startsWithQuery,
                    )
                } else {
                    existing.occurrences += 1
                }
            }
        }

        return candidates.values
            .sortedWith(
                compareByDescending<Candidate> { it.startsWithQuery }
                    .thenByDescending { it.occurrences }
                    .thenBy { it.suggestion.value.lowercase(Locale.ROOT) }
                    .thenBy { it.suggestion.columnName.lowercase(Locale.ROOT) },
            )
            .take(limit.coerceAtMost(MAX_SEARCH_SUGGESTIONS))
            .map { it.suggestion }
    }

    fun pageOffset(page: Int, pageSize: Int): Int =
        page.coerceAtLeast(0) * pageSize.coerceAtLeast(1)

    fun quoteIdentifier(identifier: String): String =
        "\"${identifier.replace("\"", "\"\"")}\""

    private fun searchPredicate(columns: List<String>): String {
        require(columns.isNotEmpty()) { "Une table doit avoir au moins une colonne pour être recherchée." }
        return columns.joinToString(separator = " OR ", prefix = "(", postfix = ")") { column ->
            "CAST(${quoteIdentifier(column)} AS TEXT) LIKE ? ESCAPE '!'"
        }
    }

    private fun escapeLikeQuery(query: String): String =
        query.replace("!", "!!").replace("%", "!%").replace("_", "!_")
}

class DatabaseNotAvailableException(message: String) : IllegalStateException(message)

class DatabaseInspector(
    private val context: Context? = null,
    private val customFilesDir: File? = null,
) {
    private val filesDir: File
        get() = customFilesDir ?: context?.filesDir ?: File(".")

    data class LoadedOutageData(
        val file: File,
        val sites: List<SiteHsEntity>,
        val metadata: Map<String, String>,
    )

    private var cachedOutageData: Pair<InspectableDatabase, LoadedOutageData>? = null

    fun resolveOutageFile(database: InspectableDatabase): File? {
        if (!database.isOutages) return null
        val localFirst = try { AppConfig.outagesLocal() } catch (_: Exception) { false }
        val candidates = when (database) {
            InspectableDatabase.OUTAGES_OPERATORS -> {
                val serverList = listOf(
                    File(filesDir, ServerOutageCache.fileFor(OutageSourceMode.OPERATORS)),
                    File(filesDir, ServerOutageCache.FILE_NAME),
                    File(filesDir, "sites_hs.geojson"),
                    File(filesDir, "sites_hs_operators.geojson"),
                )
                val localList = listOf(
                    File(filesDir, OutageLocalCache.fileFor(OutageSourceMode.OPERATORS)),
                    File(filesDir, OutageLocalCache.FILE_NAME),
                )
                if (localFirst) localList + serverList else serverList + localList
            }
            InspectableDatabase.OUTAGES_DAILY -> {
                val serverList = listOf(
                    File(filesDir, ServerOutageCache.fileFor(OutageSourceMode.DAILY)),
                    File(filesDir, "sites_hs_daily.geojson"),
                )
                val localList = listOf(
                    File(filesDir, OutageLocalCache.fileFor(OutageSourceMode.DAILY)),
                )
                if (localFirst) localList + serverList else serverList + localList
            }
            else -> emptyList()
        }
        return candidates.firstOrNull { it.isFile && it.length() > 0L }
    }

    fun isAvailable(database: InspectableDatabase): Boolean {
        return if (database.isOutages) {
            resolveOutageFile(database) != null
        } else {
            val file = context?.getDatabasePath(database.fileName) ?: return false
            file.isFile && file.length() > 0L
        }
    }

    fun loadOutageData(database: InspectableDatabase): LoadedOutageData {
        val file = resolveOutageFile(database)
            ?: throw DatabaseNotAvailableException(database.fileName)

        val cached = cachedOutageData
        if (cached != null && cached.first == database && cached.second.file.absolutePath == file.absolutePath && cached.second.file.lastModified() == file.lastModified()) {
            return cached.second
        }

        val text = file.readText()
        var parsedSites: List<SiteHsEntity>? = null
        var sourceLastUpdate: String? = null
        var downloadedAtIso: String? = null
        var generatedAtIso: String? = null

        // 1. Essai CachedServerOutages
        try {
            val cachedServer = Gson().fromJson(text, CachedServerOutages::class.java)
            if (cachedServer?.sites != null && cachedOutageSitesAreUsable(cachedServer.sites)) {
                parsedSites = cachedServer.sites
                sourceLastUpdate = cachedServer.sourceLastUpdate
                if (cachedServer.downloadedAtMillis > 0L) {
                    downloadedAtIso = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                        .format(Date(cachedServer.downloadedAtMillis))
                }
                if (cachedServer.serverGeneratedAtMillis > 0L) {
                    generatedAtIso = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                        .format(Date(cachedServer.serverGeneratedAtMillis))
                }
            }
        } catch (_: Exception) {}

        // 2. Essai CachedOutages (génération locale)
        if (parsedSites == null) {
            try {
                val cachedLocal = Gson().fromJson(text, CachedOutages::class.java)
                if (cachedLocal?.sites != null && cachedOutageSitesAreUsable(cachedLocal.sites)) {
                    parsedSites = cachedLocal.sites
                    sourceLastUpdate = cachedLocal.sourceLastUpdate
                    if (cachedLocal.generatedAtMillis > 0L) {
                        generatedAtIso = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                            .format(Date(cachedLocal.generatedAtMillis))
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Essai GeoJSON brut
        if (parsedSites == null) {
            try {
                val geoResult = SitesHsGeoJsonParser.parse(text)
                if (geoResult.sites.isNotEmpty()) {
                    parsedSites = geoResult.sites
                    generatedAtIso = geoResult.generatedAtIso
                }
            } catch (_: Exception) {}
        }

        val finalSites = parsedSites ?: emptyList()
        val metadata = linkedMapOf<String, String>()
        metadata["fichier"] = file.name
        metadata["taille"] = "${(file.length() + 1023) / 1024} Ko"
        metadata["nombre_sites"] = finalSites.size.toString()
        metadata["source"] = if (database == InspectableDatabase.OUTAGES_OPERATORS) "Opérateurs (4 flux)" else "Arcep journalier"
        metadata["mode_origine"] = if (file.name.contains("local")) "Traitement local" else "Serveur GeoTower"
        sourceLastUpdate?.let { metadata["date_source"] = it }
        generatedAtIso?.let { metadata["date_generation"] = it }
        downloadedAtIso?.let { metadata["date_telechargement"] = it }
        metadata["chemin"] = file.absolutePath

        val result = LoadedOutageData(file, finalSites, metadata)
        cachedOutageData = database to result
        return result
    }

    fun listTables(database: InspectableDatabase): List<DatabaseTableInfo> {
        if (database.isOutages) {
            val data = loadOutageData(database)
            return listOf(
                DatabaseTableInfo(name = "sites_hs", rowCount = data.sites.size.toLong()),
                DatabaseTableInfo(name = "metadata", rowCount = data.metadata.size.toLong()),
            )
        }
        return withReadOnlyDatabase(database) { db ->
            readTableNames(db).map { name ->
                DatabaseTableInfo(name = name, rowCount = countRows(db, name))
            }
        }
    }

    fun listTableNames(database: InspectableDatabase): List<String> =
        if (database.isOutages) {
            listOf("sites_hs", "metadata")
        } else {
            withReadOnlyDatabase(database) { db -> readTableNames(db) }
        }

    fun readTableColumns(database: InspectableDatabase, tableName: String): List<DatabaseColumnInfo> =
        if (database.isOutages) {
            if (tableName == "metadata") METADATA_COLUMNS else OUTAGE_COLUMNS
        } else {
            withReadOnlyDatabase(database) { db -> readColumns(db, tableName) }
        }

    fun readPage(
        database: InspectableDatabase,
        tableName: String,
        page: Int,
        pageSize: Int = DatabaseInspectorRules.DEFAULT_PAGE_SIZE,
        searchQuery: String = "",
    ): DatabaseTablePage {
        val safePage = page.coerceAtLeast(0)
        val safePageSize = pageSize.coerceIn(1, 200)
        val normalizedQuery = searchQuery.trim()

        if (database.isOutages) {
            val data = loadOutageData(database)
            val columns = if (tableName == "metadata") METADATA_COLUMNS else OUTAGE_COLUMNS
            val allRows = if (tableName == "metadata") {
                data.metadata.entries.map { listOf(it.key, it.value) }
            } else {
                data.sites.map(::siteToRow)
            }
            val filteredRows = if (normalizedQuery.isEmpty()) {
                allRows
            } else {
                allRows.filter { row ->
                    row.any { cell -> cell != null && cell.contains(normalizedQuery, ignoreCase = true) }
                }
            }
            val offset = DatabaseInspectorRules.pageOffset(safePage, safePageSize)
            val pagedRows = filteredRows.drop(offset).take(safePageSize)

            return DatabaseTablePage(
                tableName = tableName,
                columns = columns,
                rows = pagedRows,
                page = safePage,
                pageSize = safePageSize,
                totalRows = filteredRows.size.toLong(),
                searchQuery = normalizedQuery,
            )
        }

        return withReadOnlyDatabase(database) { db ->
            val columns = readColumns(db, tableName)
            val columnNames = columns.map(DatabaseColumnInfo::name)
            val totalRows = if (normalizedQuery.isEmpty()) {
                countRows(db, tableName)
            } else {
                db.rawQuery(
                    DatabaseInspectorRules.filteredCountQuery(tableName, columnNames),
                    DatabaseInspectorRules.searchArguments(columnNames, normalizedQuery),
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
            }
            val rowsQuery = if (normalizedQuery.isEmpty()) {
                DatabaseInspectorRules.tableRowsQuery(tableName)
            } else {
                DatabaseInspectorRules.filteredRowsQuery(tableName, columnNames)
            }
            val rowsArguments = if (normalizedQuery.isEmpty()) {
                arrayOf(safePageSize.toString(), DatabaseInspectorRules.pageOffset(safePage, safePageSize).toString())
            } else {
                DatabaseInspectorRules.searchArguments(columnNames, normalizedQuery) +
                    arrayOf(safePageSize.toString(), DatabaseInspectorRules.pageOffset(safePage, safePageSize).toString())
            }
            val rows = db.rawQuery(rowsQuery, rowsArguments).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.readRow())
                    }
                }
            }
            DatabaseTablePage(
                tableName = tableName,
                columns = columns,
                rows = rows,
                page = safePage,
                pageSize = safePageSize,
                totalRows = totalRows,
                searchQuery = normalizedQuery,
            )
        }
    }

    companion object {
        val METADATA_COLUMNS = listOf(
            DatabaseColumnInfo("cle", "TEXT", notNull = true, primaryKeyPosition = 1),
            DatabaseColumnInfo("valeur", "TEXT", notNull = false, primaryKeyPosition = 0),
        )

        val OUTAGE_COLUMNS = listOf(
            DatabaseColumnInfo("id_anfr", "TEXT", notNull = true, primaryKeyPosition = 1),
            DatabaseColumnInfo("operateur", "TEXT", notNull = true, primaryKeyPosition = 0),
            DatabaseColumnInfo("commune", "TEXT", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("departement", "TEXT", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("code_insee", "TEXT", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("code_postal", "TEXT", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("latitude", "REAL", notNull = true, primaryKeyPosition = 0),
            DatabaseColumnInfo("longitude", "REAL", notNull = true, primaryKeyPosition = 0),
            DatabaseColumnInfo("voix_2g", "TEXT", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("voix_3g", "TEXT", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("voix_4g", "TEXT", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("voix_5g", "TEXT", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("data_2g", "TEXT", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("data_3g", "TEXT", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("data_4g", "TEXT", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("data_5g", "TEXT", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("voix_global", "TEXT", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("data_global", "TEXT", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("raison", "TEXT", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("detail", "TEXT", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("date_debut", "TEXT", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("date_fin", "TEXT", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("debut_voix", "TEXT", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("fin_voix", "TEXT", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("debut_data", "TEXT", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("fin_data", "TEXT", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("is_potential", "INTEGER", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("source_last_update", "TEXT", notNull = false, primaryKeyPosition = 0),
            DatabaseColumnInfo("geojson", "TEXT", notNull = false, primaryKeyPosition = 0),
        )
    }

    private fun readColumns(db: SQLiteDatabase, tableName: String): List<DatabaseColumnInfo> {
        return db.rawQuery(
            "PRAGMA table_info(${DatabaseInspectorRules.quoteIdentifier(tableName)})",
            null
        ).use { cursor ->
            buildList {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val typeIndex = cursor.getColumnIndexOrThrow("type")
                val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
                val primaryKeyIndex = cursor.getColumnIndexOrThrow("pk")
                while (cursor.moveToNext()) {
                    add(
                        DatabaseColumnInfo(
                            name = cursor.getString(nameIndex),
                            type = cursor.getString(typeIndex).orEmpty(),
                            notNull = cursor.getInt(notNullIndex) != 0,
                            primaryKeyPosition = cursor.getInt(primaryKeyIndex),
                        )
                    )
                }
            }
        }
    }

    private fun readTableNames(db: SQLiteDatabase): List<String> =
        db.rawQuery(DatabaseInspectorRules.tableNamesQuery(), null).use { cursor ->
            buildList {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }

    private fun countRows(db: SQLiteDatabase, tableName: String): Long {
        return db.rawQuery(
            "SELECT COUNT(*) FROM ${DatabaseInspectorRules.quoteIdentifier(tableName)}",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    private fun <T> withReadOnlyDatabase(database: InspectableDatabase, block: (SQLiteDatabase) -> T): T {
        val file = context?.getDatabasePath(database.fileName)
            ?: throw DatabaseNotAvailableException(database.fileName)
        if (!file.isFile || file.length() == 0L) {
            throw DatabaseNotAvailableException(database.fileName)
        }
        return SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        ).use(block)
    }
}

private fun Cursor.readRow(): List<String?> = buildList {
    for (index in 0 until columnCount) {
        add(
            when (getType(index)) {
                Cursor.FIELD_TYPE_NULL -> null
                Cursor.FIELD_TYPE_BLOB -> "<BLOB ${getBlob(index).size} octets>"
                else -> getString(index)
            }
        )
    }
}

private fun siteToRow(site: SiteHsEntity): List<String?> = listOf(
    site.idAnfr.ifEmpty { null },
    site.operateur.ifEmpty { null },
    site.commune,
    site.departement,
    site.codeInsee,
    site.codePostal,
    site.latitude.toString(),
    site.longitude.toString(),
    site.voix2g,
    site.voix3g,
    site.voix4g,
    site.voix5g,
    site.data2g,
    site.data3g,
    site.data4g,
    site.data5g,
    site.voixGlobal,
    site.dataGlobal,
    site.raison,
    site.detail,
    site.dateDebut,
    site.dateFin,
    site.debutVoix,
    site.finVoix,
    site.debutData,
    site.finData,
    if (site.isPotential) "1" else "0",
    site.sourceLastUpdate,
    site.toGeoJsonFeature(),
)

internal fun SiteHsEntity.toGeoJsonFeature(): String {
    val props = JSONObject()
    props.put("station_anfr", idAnfr)
    props.put("operateur", operateur)
    commune?.let { props.put("commune", it) }
    departement?.let { props.put("departement", it) }
    codeInsee?.let { props.put("code_insee", it) }
    codePostal?.let { props.put("code_postal", it) }
    voix2g?.let { props.put("voix_2g", it) }
    voix3g?.let { props.put("voix_3g", it) }
    voix4g?.let { props.put("voix_4g", it) }
    voix5g?.let { props.put("voix_5g", it) }
    data2g?.let { props.put("data_2g", it) }
    data3g?.let { props.put("data_3g", it) }
    data4g?.let { props.put("data_4g", it) }
    data5g?.let { props.put("data_5g", it) }
    voixGlobal?.let { props.put("voix", it) }
    dataGlobal?.let { props.put("data", it) }
    raison?.let { props.put("raison", it) }
    detail?.let { props.put("detail", it) }
    dateDebut?.let { props.put("debut", it) }
    dateFin?.let { props.put("fin", it) }
    debutVoix?.let { props.put("debut_voix", it) }
    finVoix?.let { props.put("fin_voix", it) }
    debutData?.let { props.put("debut_data", it) }
    finData?.let { props.put("fin_data", it) }
    propre?.let { props.put("propre", it) }
    if (isPotential) props.put("is_potential", true)
    sourceLastUpdate?.let { props.put("source_last_update", it) }

    val geom = JSONObject()
    geom.put("type", geometryType ?: "Point")
    val coords = JSONArray()
    coords.put(longitude)
    coords.put(latitude)
    geom.put("coordinates", coords)

    val feature = JSONObject()
    feature.put("type", "Feature")
    feature.put("geometry", geom)
    feature.put("properties", props)
    return feature.toString(2)
}
