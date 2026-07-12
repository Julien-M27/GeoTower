package fr.geotower.data.build

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement

/**
 * Implementation on-device de [SqlDatabase] via le SQLite du framework Android. Contrat
 * identique a `JdbcSqlDatabase` (tests JVM) : [GeoTowerDbBuilder] n'emet que du SQL brut, donc
 * la logique de construction est deja prouvee par les tests et cette classe n'est qu'un
 * adaptateur mince.
 *
 * A utiliser sur une base ouverte hors Room (le fichier temporaire du build local), puis
 * remise a `installValidatedDatabase` comme un telechargement.
 */
class AndroidSqlDatabase(private val db: SQLiteDatabase) : SqlDatabase {

    override fun execSql(sql: String) {
        val trimmed = sql.trimStart()
        if (trimmed.startsWith("PRAGMA", ignoreCase = true)) {
            // `PRAGMA user_version = N` : via db.version (setVersion) = la voie fiable Android ;
            // Room refuse la base si user_version reste a 0. Les autres PRAGMA (journal_mode...)
            // renvoient une valeur -> rawQuery (execSQL les rejette).
            val userVersion = USER_VERSION_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (userVersion != null) {
                db.version = userVersion
            } else {
                db.rawQuery(sql, null).use { it.moveToFirst() }
            }
        } else {
            db.execSQL(sql)
        }
    }

    override fun insertBatch(sql: String, rows: Iterable<List<Any?>>): Int {
        var count = 0
        val statement = db.compileStatement(sql)
        db.beginTransaction()
        try {
            for (row in rows) {
                statement.clearBindings()
                bindRow(statement, row)
                statement.executeInsert()
                count++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            statement.close()
        }
        return count
    }

    override fun query(sql: String, onRow: (SqlRow) -> Unit) {
        db.rawQuery(sql, null).use { cursor ->
            enableForwardOnlyWindow(cursor)
            val row = CursorRow(cursor)
            while (cursor.moveToNext()) onRow(row)
        }
    }

    /**
     * Force le curseur en **forward-only**. Sans cela, le curseur Android RE-EXECUTE la requete (et
     * re-trie tout `ORDER BY`) a chaque remplissage de fenetre : un scan de N lignes devient O(N²).
     * Sur les scans ANFR de plusieurs millions de lignes (masques, details, classification radio),
     * c'est la difference entre quelques minutes et des dizaines de minutes. La methode peut etre
     * masquee selon l'API -> appel reflexif tolerant (no-op si absente).
     */
    private fun enableForwardOnlyWindow(cursor: Cursor) {
        runCatching {
            cursor.javaClass
                .getMethod("setFillWindowForwardOnly", Boolean::class.javaPrimitiveType)
                .invoke(cursor, true)
        }
    }

    override fun close() {
        db.close()
    }

    private fun bindRow(statement: SQLiteStatement, row: List<Any?>) {
        row.forEachIndexed { index, value ->
            val position = index + 1
            when (value) {
                null -> statement.bindNull(position)
                is Int -> statement.bindLong(position, value.toLong())
                is Long -> statement.bindLong(position, value)
                is Double -> statement.bindDouble(position, value)
                is Float -> statement.bindDouble(position, value.toDouble())
                is ByteArray -> statement.bindBlob(position, value)
                else -> statement.bindString(position, value.toString())
            }
        }
    }

    private companion object {
        val USER_VERSION_REGEX = Regex("""PRAGMA\s+user_version\s*=\s*(\d+)""", RegexOption.IGNORE_CASE)
    }

    private class CursorRow(private val cursor: Cursor) : SqlRow {
        // Cache nom -> index de colonne. La meme instance CursorRow est reutilisee pour toutes les
        // lignes d'un scan ; sans cache, `getColumnIndex` (comparaison de chaines lineaire sur les
        // noms de colonnes) etait rappele a chaque acces, soit des dizaines de millions de fois sur
        // les scans ANFR. On le resout une seule fois par colonne.
        private val indexCache = HashMap<String, Int>()

        private fun indexOf(column: String): Int = indexCache.getOrPut(column) { cursor.getColumnIndex(column) }

        override fun getString(column: String): String? {
            val index = indexOf(column)
            return if (index < 0 || cursor.isNull(index)) null else cursor.getString(index)
        }

        override fun getInt(column: String): Int {
            val index = indexOf(column)
            return if (index < 0 || cursor.isNull(index)) 0 else cursor.getInt(index)
        }

        override fun getIntOrNull(column: String): Int? {
            val index = indexOf(column)
            return if (index < 0 || cursor.isNull(index)) null else cursor.getInt(index)
        }

        override fun getDouble(column: String): Double? {
            val index = indexOf(column)
            return if (index < 0 || cursor.isNull(index)) null else cursor.getDouble(index)
        }
    }
}
