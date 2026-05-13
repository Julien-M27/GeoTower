package fr.geotower.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.Locale

object GeoTowerDatabaseValidator {
    const val DB_NAME = "geotower_fr.db"
    const val EXPECTED_COUNTRY_CODE = "FR"
    const val EXPECTED_SCHEMA_VERSION = 4

    private const val LEGACY_DB_NAME = "geotower.db"
    private const val PREFS_NAME = "GeoTowerPrefs"
    private const val PREF_INVALID_REASON = "geotower_db_invalid_reason"
    private val obsoleteDatabaseNames = listOf(LEGACY_DB_NAME)

    enum class LocalDatabaseState {
        VALID,
        MISSING,
        INVALID
    }

    data class ValidationResult(
        val isValid: Boolean,
        val reason: String? = null
    )

    data class LocalDatabaseStatus(
        val state: LocalDatabaseState,
        val reason: String? = null
    )

    fun getInstalledDatabaseFileStatus(context: Context): LocalDatabaseStatus {
        deleteObsoleteDatabases(context)
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.isFile || dbFile.length() <= 0L) {
            clearInstalledDatabaseInvalid(context)
            return LocalDatabaseStatus(LocalDatabaseState.MISSING)
        }

        return LocalDatabaseStatus(LocalDatabaseState.VALID)
    }

    fun getInstalledDatabaseVersion(context: Context): String? {
        deleteObsoleteDatabases(context)
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.isFile || dbFile.length() <= 0L) return null
        return readDatabaseVersion(dbFile)
    }

    private fun readDatabaseVersion(file: File): String? {
        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val cursor = db.rawQuery("SELECT version FROM metadata LIMIT 1", null)
            cursor.use {
                if (it.moveToFirst()) {
                    DatabaseVersionPolicy.normalizedVersion(it.getString(0))
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        } finally {
            db?.close()
        }
    }

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
        "localisation" to setOf(
            "id_anfr",
            "operateur_id",
            "latitude",
            "longitude",
            "azimuts",
            "code_insee",
            "azimuts_fh",
            "tech_mask",
            "band_mask"
        ),
        "technique" to setOf(
            "id_anfr",
            "statut_id",
            "date_implantation",
            "date_service",
            "date_modif",
            "details_frequences",
            "adresse",
            "has_active"
        ),
        "support" to setOf(
            "id_anfr",
            "id_support",
            "nat_id",
            "tpo_id",
            "hauteur"
        ),
        "antenne" to setOf(
            "aer_id",
            "id_anfr",
            "id_support",
            "tae_id",
            "azimut",
            "hauteur_bas",
            "is_fh"
        ),
        "ref_operateur" to setOf("id", "libelle"),
        "ref_nature" to setOf("nat_id", "libelle"),
        "ref_proprietaire" to setOf("tpo_id", "libelle"),
        "ref_type_antenne" to setOf("tae_id", "libelle"),
        "ref_systeme" to setOf("id", "libelle"),
        "ref_statut" to setOf("id", "libelle"),
        "ref_commune" to setOf("code_insee", "nom"),
        "metadata" to setOf(
            "version",
            "schema_version",
            "country_code",
            "country_name",
            "source",
            "date_maj_anfr",
            "zip_version"
        )
    )

    private val criticalNonEmptyTables = listOf(
        "localisation",
        "technique",
        "support",
        "metadata",
        "ref_operateur",
        "ref_systeme",
        "ref_statut"
    )

    private val expectedAffinities = mapOf(
        "localisation" to mapOf(
            "id_anfr" to SQLiteAffinity.TEXT,
            "operateur_id" to SQLiteAffinity.INTEGER,
            "latitude" to SQLiteAffinity.REAL,
            "longitude" to SQLiteAffinity.REAL,
            "tech_mask" to SQLiteAffinity.INTEGER,
            "band_mask" to SQLiteAffinity.INTEGER
        ),
        "technique" to mapOf(
            "id_anfr" to SQLiteAffinity.TEXT,
            "statut_id" to SQLiteAffinity.INTEGER,
            "has_active" to SQLiteAffinity.INTEGER
        ),
        "support" to mapOf(
            "id_anfr" to SQLiteAffinity.TEXT,
            "id_support" to SQLiteAffinity.TEXT,
            "nat_id" to SQLiteAffinity.INTEGER,
            "tpo_id" to SQLiteAffinity.INTEGER,
            "hauteur" to SQLiteAffinity.REAL
        ),
        "antenne" to mapOf(
            "aer_id" to SQLiteAffinity.TEXT,
            "id_anfr" to SQLiteAffinity.TEXT,
            "id_support" to SQLiteAffinity.TEXT,
            "tae_id" to SQLiteAffinity.INTEGER,
            "azimut" to SQLiteAffinity.INTEGER,
            "hauteur_bas" to SQLiteAffinity.REAL,
            "is_fh" to SQLiteAffinity.INTEGER
        ),
        "metadata" to mapOf(
            "version" to SQLiteAffinity.TEXT,
            "schema_version" to SQLiteAffinity.INTEGER,
            "country_code" to SQLiteAffinity.TEXT,
            "source" to SQLiteAffinity.TEXT
        )
    )

    private val requiredPrimaryKeys = mapOf(
        "localisation" to listOf("id_anfr"),
        "technique" to listOf("id_anfr"),
        "support" to listOf("id_anfr", "id_support"),
        "antenne" to listOf("aer_id"),
        "ref_operateur" to listOf("id"),
        "ref_nature" to listOf("nat_id"),
        "ref_proprietaire" to listOf("tpo_id"),
        "ref_type_antenne" to listOf("tae_id"),
        "ref_systeme" to listOf("id"),
        "ref_statut" to listOf("id"),
        "ref_commune" to listOf("code_insee"),
        "metadata" to listOf("version")
    )

    private val requiredNotNullColumns = mapOf(
        "localisation" to listOf("id_anfr", "latitude", "longitude", "tech_mask", "band_mask"),
        "technique" to listOf("id_anfr", "has_active"),
        "support" to listOf("id_anfr", "id_support"),
        "antenne" to listOf("aer_id", "id_anfr", "is_fh"),
        "ref_operateur" to listOf("id", "libelle"),
        "ref_nature" to listOf("nat_id", "libelle"),
        "ref_proprietaire" to listOf("tpo_id", "libelle"),
        "ref_type_antenne" to listOf("tae_id", "libelle"),
        "ref_systeme" to listOf("id", "libelle"),
        "ref_statut" to listOf("id", "libelle"),
        "ref_commune" to listOf("code_insee", "nom"),
        "metadata" to listOf("version", "schema_version", "country_code", "source")
    )

    fun getInstalledDatabaseStatus(context: Context): LocalDatabaseStatus {
        deleteObsoleteDatabases(context)
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.isFile || dbFile.length() <= 0L) {
            clearInstalledDatabaseInvalid(context)
            return LocalDatabaseStatus(LocalDatabaseState.MISSING)
        }

        val validation = validateDatabaseFile(dbFile)
        return if (validation.isValid) {
            clearInstalledDatabaseInvalid(context)
            LocalDatabaseStatus(LocalDatabaseState.VALID)
        } else {
            val reason = validation.reason ?: "Schema local incompatible"
            markInstalledDatabaseInvalid(context, reason)
            LocalDatabaseStatus(LocalDatabaseState.INVALID, reason)
        }
    }

    fun deleteObsoleteDatabases(context: Context) {
        obsoleteDatabaseNames
            .filterNot { it == DB_NAME }
            .forEach { dbName -> deleteDatabaseArtifacts(context, dbName) }
    }

    fun validateDatabaseFile(file: File): ValidationResult {
        if (!file.isFile || file.length() <= 0L) {
            return ValidationResult(false, "Fichier de base absent ou vide")
        }

        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            validateOpenDatabase(db)
        } catch (e: Exception) {
            ValidationResult(false, e.message ?: "Base SQLite illisible")
        } finally {
            db?.close()
        }
    }

    fun markInstalledDatabaseInvalid(context: Context, reason: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_INVALID_REASON, reason)
            .apply()
    }

    fun clearInstalledDatabaseInvalid(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_INVALID_REASON)
            .apply()
    }

    private fun validateOpenDatabase(db: SQLiteDatabase): ValidationResult {
        if (!runIntegrityCheck(db)) {
            return ValidationResult(false, "PRAGMA integrity_check a echoue")
        }

        requiredColumns.forEach { (tableName, columns) ->
            if (!tableExists(db, tableName)) {
                return ValidationResult(false, "Table manquante: $tableName")
            }

            val tableColumns = readTableInfo(db, tableName)
            val missingColumns = columns.filterNot { tableColumns.containsKey(it) }
            if (missingColumns.isNotEmpty()) {
                return ValidationResult(
                    false,
                    "Colonnes manquantes dans $tableName: ${missingColumns.joinToString()}"
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
                return ValidationResult(false, "Type SQLite incompatible: $invalidType")
            }

            val invalidPrimaryKeys = requiredPrimaryKeys[tableName]
                ?.filter { columnName -> (tableColumns[columnName]?.primaryKeyPosition ?: 0) <= 0 }
                .orEmpty()
            if (invalidPrimaryKeys.isNotEmpty()) {
                return ValidationResult(
                    false,
                    "Cle primaire manquante dans $tableName: ${invalidPrimaryKeys.joinToString()}"
                )
            }

            val nullableRequiredColumns = requiredNotNullColumns[tableName]
                ?.filter { columnName -> tableColumns[columnName]?.notNull != true }
                .orEmpty()
            if (nullableRequiredColumns.isNotEmpty()) {
                return ValidationResult(
                    false,
                    "Colonnes NOT NULL manquantes dans $tableName: ${nullableRequiredColumns.joinToString()}"
                )
            }
        }

        criticalNonEmptyTables.forEach { tableName ->
            if (tableRowCount(db, tableName) <= 0L) {
                return ValidationResult(false, "Table vide: $tableName")
            }
        }

        validateMetadata(db)?.let { return it }

        return ValidationResult(true)
    }

    private fun validateMetadata(db: SQLiteDatabase): ValidationResult? {
        val cursor = db.rawQuery(
            "SELECT schema_version, country_code FROM metadata LIMIT 1",
            null
        )
        return cursor.use {
            if (!it.moveToFirst()) {
                ValidationResult(false, "Metadata absente")
            } else {
                val schemaVersion = it.getInt(0)
                val countryCode = it.getString(1)?.uppercase(Locale.US)
                when {
                    schemaVersion != EXPECTED_SCHEMA_VERSION -> {
                        ValidationResult(false, "Schema DB incompatible: $schemaVersion")
                    }
                    countryCode != EXPECTED_COUNTRY_CODE -> {
                        ValidationResult(false, "Pays DB incompatible: $countryCode")
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

    private fun deleteDatabaseArtifacts(context: Context, dbName: String) {
        val relatedNames = listOf(dbName, "$dbName.download", "$dbName.backup")
        relatedNames.forEach { name ->
            val dbFile = context.getDatabasePath(name)
            if (dbFile.exists()) {
                context.deleteDatabase(name)
                dbFile.delete()
            }

            listOf("-wal", "-shm", "-journal").forEach { suffix ->
                File(dbFile.path + suffix).delete()
            }
        }
    }
}
