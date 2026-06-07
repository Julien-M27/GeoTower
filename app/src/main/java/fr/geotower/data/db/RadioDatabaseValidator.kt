package fr.geotower.data.db

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.Locale

object RadioDatabaseValidator {
    const val DB_NAME = "geotower_fr_radio.db"
    const val EXPECTED_COUNTRY_CODE = "FR"
    const val EXPECTED_SCHEMA_VERSION = 1
    const val EXPECTED_SOURCE = "ANFR_RADIO"

    data class ValidationResult(
        val isValid: Boolean,
        val reason: String? = null
    )

    private enum class SQLiteAffinity {
        INTEGER,
        TEXT,
        REAL,
        NUMERIC,
        BLOB
    }

    private data class TableColumn(
        val name: String,
        val type: String,
        val notNull: Boolean,
        val primaryKeyPosition: Int
    )

    private val requiredColumns = mapOf(
        "non_mobile_site" to setOf(
            "sta_nm_anfr",
            "sup_id",
            "adm_id",
            "lat_e6",
            "lon_e6",
            "nat_id",
            "tpo_id",
            "height_dm",
            "code_insee",
            "service_mask",
            "system_mask",
            "emitter_count",
            "antenna_count",
            "freq_range_count",
            "min_freq_khz",
            "max_freq_khz"
        ),
        "non_mobile_detail" to setOf("sta_nm_anfr", "sup_id", "detail_z"),
        "ref_actor" to setOf("adm_id", "label"),
        "ref_nature" to setOf("nat_id", "label"),
        "ref_owner" to setOf("tpo_id", "label"),
        "ref_type_antenne" to setOf("tae_id", "label"),
        "metadata" to setOf(
            "version",
            "schema_version",
            "country_code",
            "country_name",
            "source",
            "date_maj_anfr",
            "zip_version",
            "row_count"
        )
    )

    private val expectedAffinities = mapOf(
        "non_mobile_site" to mapOf(
            "sta_nm_anfr" to SQLiteAffinity.TEXT,
            "sup_id" to SQLiteAffinity.TEXT,
            "adm_id" to SQLiteAffinity.INTEGER,
            "lat_e6" to SQLiteAffinity.INTEGER,
            "lon_e6" to SQLiteAffinity.INTEGER,
            "nat_id" to SQLiteAffinity.INTEGER,
            "tpo_id" to SQLiteAffinity.INTEGER,
            "height_dm" to SQLiteAffinity.INTEGER,
            "service_mask" to SQLiteAffinity.INTEGER,
            "system_mask" to SQLiteAffinity.INTEGER,
            "emitter_count" to SQLiteAffinity.INTEGER,
            "antenna_count" to SQLiteAffinity.INTEGER,
            "freq_range_count" to SQLiteAffinity.INTEGER,
            "min_freq_khz" to SQLiteAffinity.INTEGER,
            "max_freq_khz" to SQLiteAffinity.INTEGER
        ),
        "non_mobile_detail" to mapOf(
            "sta_nm_anfr" to SQLiteAffinity.TEXT,
            "sup_id" to SQLiteAffinity.TEXT,
            "detail_z" to SQLiteAffinity.TEXT
        ),
        "metadata" to mapOf(
            "version" to SQLiteAffinity.TEXT,
            "schema_version" to SQLiteAffinity.INTEGER,
            "country_code" to SQLiteAffinity.TEXT,
            "source" to SQLiteAffinity.TEXT,
            "row_count" to SQLiteAffinity.INTEGER
        )
    )

    private val requiredPrimaryKeys = mapOf(
        "non_mobile_site" to listOf("sta_nm_anfr", "sup_id"),
        "non_mobile_detail" to listOf("sta_nm_anfr", "sup_id"),
        "ref_actor" to listOf("adm_id"),
        "ref_nature" to listOf("nat_id"),
        "ref_owner" to listOf("tpo_id"),
        "ref_type_antenne" to listOf("tae_id"),
        "metadata" to listOf("version")
    )

    private val requiredNotNullColumns = mapOf(
        "non_mobile_site" to listOf(
            "sta_nm_anfr",
            "sup_id",
            "lat_e6",
            "lon_e6",
            "service_mask",
            "system_mask",
            "emitter_count",
            "antenna_count",
            "freq_range_count"
        ),
        "non_mobile_detail" to listOf("sta_nm_anfr", "sup_id", "detail_z"),
        "ref_actor" to listOf("adm_id", "label"),
        "ref_nature" to listOf("nat_id", "label"),
        "ref_owner" to listOf("tpo_id", "label"),
        "ref_type_antenne" to listOf("tae_id", "label"),
        "metadata" to listOf("version", "schema_version", "country_code", "source", "row_count")
    )

    private val criticalNonEmptyTables = listOf(
        "non_mobile_site",
        "non_mobile_detail",
        "metadata",
        "ref_actor"
    )

    fun validateDatabaseFile(file: File): ValidationResult {
        if (!file.isFile || file.length() <= 0L) {
            return ValidationResult(false, "Fichier de base radio absent ou vide")
        }

        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            validateOpenDatabase(db)
        } catch (e: Exception) {
            ValidationResult(false, e.message ?: "Base radio SQLite illisible")
        } finally {
            db?.close()
        }
    }

    private fun validateOpenDatabase(db: SQLiteDatabase): ValidationResult {
        if (!runIntegrityCheck(db)) {
            return ValidationResult(false, "PRAGMA integrity_check a echoue")
        }

        requiredColumns.forEach { (tableName, columns) ->
            if (!tableExists(db, tableName)) {
                return ValidationResult(false, "Table radio manquante: $tableName")
            }

            val tableColumns = readTableInfo(db, tableName)
            val missingColumns = columns.filterNot { tableColumns.containsKey(it) }
            if (missingColumns.isNotEmpty()) {
                return ValidationResult(
                    false,
                    "Colonnes radio manquantes dans $tableName: ${missingColumns.joinToString()}"
                )
            }

            val invalidType = expectedAffinities[tableName]
                ?.firstNotNullOfOrNull { (columnName, expectedAffinity) ->
                    val column = tableColumns[columnName] ?: return@firstNotNullOfOrNull null
                    val actualAffinity = sqliteAffinity(column.type)
                    if (actualAffinity != expectedAffinity) {
                        "$tableName.$columnName (${column.type.ifBlank { "sans type" }})"
                    } else {
                        null
                    }
                }
            if (invalidType != null) {
                return ValidationResult(false, "Type SQLite radio incompatible: $invalidType")
            }

            val invalidPrimaryKeys = requiredPrimaryKeys[tableName]
                ?.filter { columnName -> (tableColumns[columnName]?.primaryKeyPosition ?: 0) <= 0 }
                .orEmpty()
            if (invalidPrimaryKeys.isNotEmpty()) {
                return ValidationResult(
                    false,
                    "Cle primaire radio manquante dans $tableName: ${invalidPrimaryKeys.joinToString()}"
                )
            }

            val nullableRequiredColumns = requiredNotNullColumns[tableName]
                ?.filter { columnName -> tableColumns[columnName]?.notNull != true }
                .orEmpty()
            if (nullableRequiredColumns.isNotEmpty()) {
                return ValidationResult(
                    false,
                    "Colonnes radio NOT NULL manquantes dans $tableName: ${nullableRequiredColumns.joinToString()}"
                )
            }
        }

        criticalNonEmptyTables.forEach { tableName ->
            if (tableRowCount(db, tableName) <= 0L) {
                return ValidationResult(false, "Table radio vide: $tableName")
            }
        }

        validateMetadata(db)?.let { return it }
        return ValidationResult(true)
    }

    private fun validateMetadata(db: SQLiteDatabase): ValidationResult? {
        val cursor = db.rawQuery(
            "SELECT schema_version, country_code, source, row_count FROM metadata LIMIT 1",
            null
        )
        return cursor.use {
            if (!it.moveToFirst()) {
                ValidationResult(false, "Metadata radio absente")
            } else {
                val schemaVersion = it.getInt(0)
                val countryCode = it.getString(1)?.uppercase(Locale.US)
                val source = it.getString(2)?.uppercase(Locale.US)
                val rowCount = it.getLong(3)
                when {
                    schemaVersion != EXPECTED_SCHEMA_VERSION -> {
                        ValidationResult(false, "Schema DB radio incompatible: $schemaVersion")
                    }
                    countryCode != EXPECTED_COUNTRY_CODE -> {
                        ValidationResult(false, "Pays DB radio incompatible: $countryCode")
                    }
                    source != EXPECTED_SOURCE -> {
                        ValidationResult(false, "Source DB radio incompatible: $source")
                    }
                    rowCount <= 0L -> {
                        ValidationResult(false, "Metadata radio sans lignes")
                    }
                    else -> null
                }
            }
        }
    }

    private fun runIntegrityCheck(db: SQLiteDatabase): Boolean {
        val cursor = db.rawQuery("PRAGMA integrity_check", null)
        return cursor.use {
            it.moveToFirst() && it.getString(0).equals("ok", ignoreCase = true)
        }
    }

    private fun tableExists(db: SQLiteDatabase, tableName: String): Boolean {
        val cursor = db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
            arrayOf(tableName)
        )
        return cursor.use { it.moveToFirst() }
    }

    private fun readTableInfo(db: SQLiteDatabase, tableName: String): Map<String, TableColumn> {
        val cursor = db.rawQuery("PRAGMA table_info(${quoteIdentifier(tableName)})", null)
        return cursor.use {
            val nameIndex = it.getColumnIndexOrThrow("name")
            val typeIndex = it.getColumnIndexOrThrow("type")
            val notNullIndex = it.getColumnIndexOrThrow("notnull")
            val primaryKeyIndex = it.getColumnIndexOrThrow("pk")
            buildMap {
                while (it.moveToNext()) {
                    val column = TableColumn(
                        name = it.getString(nameIndex),
                        type = it.getString(typeIndex).orEmpty(),
                        notNull = it.getInt(notNullIndex) != 0,
                        primaryKeyPosition = it.getInt(primaryKeyIndex)
                    )
                    put(column.name, column)
                }
            }
        }
    }

    private fun tableRowCount(db: SQLiteDatabase, tableName: String): Long {
        val cursor = db.rawQuery("SELECT COUNT(*) FROM ${quoteIdentifier(tableName)}", null)
        return cursor.use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }

    private fun sqliteAffinity(rawType: String): SQLiteAffinity {
        val type = rawType.uppercase(Locale.US)
        return when {
            type.contains("INT") -> SQLiteAffinity.INTEGER
            type.contains("CHAR") || type.contains("CLOB") || type.contains("TEXT") -> SQLiteAffinity.TEXT
            type.contains("BLOB") || type.isBlank() -> SQLiteAffinity.BLOB
            type.contains("REAL") || type.contains("FLOA") || type.contains("DOUB") -> SQLiteAffinity.REAL
            else -> SQLiteAffinity.NUMERIC
        }
    }

    private fun quoteIdentifier(identifier: String): String {
        return "\"" + identifier.replace("\"", "\"\"") + "\""
    }
}
