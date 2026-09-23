package fr.geotower.data.db

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.Locale

enum class InspectableDatabase(
    val fileName: String,
) {
    MOBILE(GeoTowerDatabaseValidator.DB_NAME),
    RADIO(RadioDatabaseValidator.DB_NAME),
    ENB(EnbDatabaseValidator.DB_NAME),
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

class DatabaseInspector(private val context: Context) {
    fun listTables(database: InspectableDatabase): List<DatabaseTableInfo> {
        return withReadOnlyDatabase(database) { db ->
            readTableNames(db).map { name ->
                DatabaseTableInfo(name = name, rowCount = countRows(db, name))
            }
        }
    }

    fun listTableNames(database: InspectableDatabase): List<String> =
        withReadOnlyDatabase(database) { db -> readTableNames(db) }

    fun readTableColumns(database: InspectableDatabase, tableName: String): List<DatabaseColumnInfo> =
        withReadOnlyDatabase(database) { db -> readColumns(db, tableName) }

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
        val file = context.getDatabasePath(database.fileName)
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
