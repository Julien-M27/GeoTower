package fr.geotower.data.db

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.Locale

/**
 * Validation structurelle de `geotower_fr_enb.db` (identifiants reseau eNB/gNB, source partenaire
 * eNB-Analytics), sur le meme principe que [RadioDatabaseValidator] : la base n'est **pas** geree
 * par Room, elle est ouverte en lecture seule a la requete. On refuse donc d'installer un fichier
 * dont le schema, les types ou les metadonnees ne correspondent pas a ce que l'app sait lire.
 *
 * Schema attendu (produit par `docs/server/build_fr_enb_db.py`) :
 *  - `enb_cell(mnc, techno, enb, id_support, lat_e6, lon_e6, address)`, PK `(mnc, techno, enb)` ;
 *    `id_support` est NULL pour les eNB non rattaches a un pylone ANFR (indoor/DAS), auquel cas
 *    `address` porte l'adresse brute du partenaire.
 *  - `enb_source(plmn, mnc, mnc_list, operator, source_date, row_count, fetched_at, from_cache)` :
 *    une ligne par fichier source. `mnc_list` enumere les codes reseau reellement presents pour
 *    l'operateur (ex. « 15,16 » pour Free) : c'est lui qui resout operateur -> lignes `enb_cell`,
 *    pas `mnc` seul.
 *  - `metadata(version, schema_version, country_code, country_name, source, source_date, ...)`.
 */
object EnbDatabaseValidator {
    const val DB_NAME = "geotower_fr_enb.db"
    const val EXPECTED_COUNTRY_CODE = "FR"
    const val EXPECTED_SCHEMA_VERSION = 1
    const val EXPECTED_SOURCE = "ENB_ANALYTICS"

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
        "enb_cell" to setOf("mnc", "techno", "enb", "id_support", "lat_e6", "lon_e6", "address"),
        "enb_source" to setOf("plmn", "mnc", "mnc_list", "operator", "source_date", "row_count"),
        "metadata" to setOf(
            "version",
            "schema_version",
            "country_code",
            "country_name",
            "source",
            "source_date",
            "row_count"
        )
    )

    private val expectedAffinities = mapOf(
        "enb_cell" to mapOf(
            "mnc" to SQLiteAffinity.INTEGER,
            "techno" to SQLiteAffinity.INTEGER,
            "enb" to SQLiteAffinity.INTEGER,
            "id_support" to SQLiteAffinity.INTEGER,
            "lat_e6" to SQLiteAffinity.INTEGER,
            "lon_e6" to SQLiteAffinity.INTEGER,
            "address" to SQLiteAffinity.TEXT
        ),
        "enb_source" to mapOf(
            "plmn" to SQLiteAffinity.TEXT,
            "mnc" to SQLiteAffinity.INTEGER,
            "mnc_list" to SQLiteAffinity.TEXT,
            "operator" to SQLiteAffinity.TEXT,
            "row_count" to SQLiteAffinity.INTEGER
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
        "enb_cell" to listOf("mnc", "techno", "enb"),
        "enb_source" to listOf("plmn"),
        "metadata" to listOf("version")
    )

    private val requiredNotNullColumns = mapOf(
        "enb_cell" to listOf("mnc", "techno", "enb", "lat_e6", "lon_e6"),
        "enb_source" to listOf("plmn", "mnc", "mnc_list", "operator", "row_count"),
        "metadata" to listOf("version", "schema_version", "country_code", "source", "row_count")
    )

    private val criticalNonEmptyTables = listOf("enb_cell", "enb_source", "metadata")

    fun validateDatabaseFile(file: File): ValidationResult {
        if (!file.isFile || file.length() <= 0L) {
            return ValidationResult(false, "Fichier de base eNB absent ou vide")
        }

        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            validateOpenDatabase(db)
        } catch (e: Exception) {
            ValidationResult(false, e.message ?: "Base eNB SQLite illisible")
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
                return ValidationResult(false, "Table eNB manquante: $tableName")
            }

            val tableColumns = readTableInfo(db, tableName)
            val missingColumns = columns.filterNot { tableColumns.containsKey(it) }
            if (missingColumns.isNotEmpty()) {
                return ValidationResult(
                    false,
                    "Colonnes eNB manquantes dans $tableName: ${missingColumns.joinToString()}"
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
                return ValidationResult(false, "Type SQLite eNB incompatible: $invalidType")
            }

            val invalidPrimaryKeys = requiredPrimaryKeys[tableName]
                ?.filter { columnName -> (tableColumns[columnName]?.primaryKeyPosition ?: 0) <= 0 }
                .orEmpty()
            if (invalidPrimaryKeys.isNotEmpty()) {
                return ValidationResult(
                    false,
                    "Cle primaire eNB manquante dans $tableName: ${invalidPrimaryKeys.joinToString()}"
                )
            }

            val nullableRequiredColumns = requiredNotNullColumns[tableName]
                ?.filter { columnName -> tableColumns[columnName]?.notNull != true }
                .orEmpty()
            if (nullableRequiredColumns.isNotEmpty()) {
                return ValidationResult(
                    false,
                    "Colonnes eNB NOT NULL manquantes dans $tableName: ${nullableRequiredColumns.joinToString()}"
                )
            }
        }

        criticalNonEmptyTables.forEach { tableName ->
            if (tableRowCount(db, tableName) <= 0L) {
                return ValidationResult(false, "Table eNB vide: $tableName")
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
                ValidationResult(false, "Metadata eNB absente")
            } else {
                val schemaVersion = it.getInt(0)
                val countryCode = it.getString(1)?.uppercase(Locale.US)
                val source = it.getString(2)?.uppercase(Locale.US)
                val rowCount = it.getLong(3)
                when {
                    schemaVersion != EXPECTED_SCHEMA_VERSION -> {
                        ValidationResult(false, "Schema DB eNB incompatible: $schemaVersion")
                    }
                    countryCode != EXPECTED_COUNTRY_CODE -> {
                        ValidationResult(false, "Pays DB eNB incompatible: $countryCode")
                    }
                    source != EXPECTED_SOURCE -> {
                        ValidationResult(false, "Source DB eNB incompatible: $source")
                    }
                    rowCount <= 0L -> {
                        ValidationResult(false, "Metadata eNB sans lignes")
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
