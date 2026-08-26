package fr.geotower.data.build

import java.io.Closeable

/**
 * Abstraction minimale d'ecriture SQLite utilisee par [GeoTowerDbBuilder].
 *
 * Deux implementations sont prevues : `android.database.sqlite` (sur l'appareil, Slice 3)
 * et JDBC `sqlite` (tests JVM). Le builder n'emet que du SQL brut, donc les deux backends
 * executent des instructions strictement identiques (pas de divergence ORM possible).
 */
interface SqlDatabase : Closeable {

    /**
     * Prefixe de schema des tables de staging : `""` quand elles vivent dans le fichier final,
     * `"stg."` quand une base annexe est ATTACHee (cf. [staging]).
     *
     * Le staging dans un fichier separe evite que les pages qu'il a occupees restent a jamais dans
     * la base installee (SQLite ne rend pas les pages liberees sans VACUUM) et permet de le rendre
     * au systeme en supprimant un fichier.
     *
     * Seule la **DDL** du staging (CREATE TABLE / CREATE INDEX / DROP TABLE) est qualifiee. Les
     * lectures et ecritures restent volontairement non qualifiees : SQLite resout un nom non
     * qualifie dans `temp`, puis `main`, puis les bases attachees dans leur ordre d'attachement, et
     * `main` ne contient jamais de table `stg_*`. Cela evite d'interpoler un prefixe dans chaque
     * requete du builder — donc d'y introduire des fautes de frappe silencieuses.
     */
    val stagingPrefix: String get() = ""

    /** Nom qualifie d'une table de staging, pour la DDL uniquement. */
    fun staging(table: String): String = stagingPrefix + table

    /** Execute une instruction unique (DDL, PRAGMA, INSERT fixe). */
    fun execSql(sql: String)

    /**
     * Prepare `sql` (par ex. `"INSERT INTO t VALUES (?, ?)"`) et l'execute pour chaque
     * ligne, en transaction et par lots. Retourne le nombre de lignes inserees.
     */
    fun insertBatch(sql: String, rows: Iterable<List<Any?>>): Int

    /**
     * Execute many inserts through one prepared statement and one transaction. This is intended
     * for very large source streams where allocating a `List<Any?>` for every row would dominate
     * the build cost.
     */
    fun insertInTransaction(sql: String, block: (SqlInsertStatement) -> Unit)

    /**
     * Execute une requete `SELECT` et invoque `onRow` pour chaque ligne, en flux (pas de
     * materialisation complete). Utilise par [AnfrStatsBuilder] pour recalculer les stats
     * a partir de la base construite.
     */
    fun query(sql: String, onRow: (SqlRow) -> Unit)

    override fun close()
}

/** Minimal portable prepared-statement API shared by Android SQLite and JDBC tests. */
interface SqlInsertStatement {
    fun clearBindings()
    fun bindNull(index: Int)
    fun bindString(index: Int, value: String)
    fun bindLong(index: Int, value: Long)
    fun bindDouble(index: Int, value: Double)
    fun executeInsert()
}

/** Acces en lecture a une ligne de resultat, par nom de colonne. */
interface SqlRow {
    fun getString(column: String): String?

    /** Valeur entiere (0 si NULL, comme les colonnes `COALESCE(..., 0)` des requetes stats). */
    fun getInt(column: String): Int

    /** Valeur entiere ou null si la colonne est NULL. */
    fun getIntOrNull(column: String): Int?

    /** Valeur reelle ou null si la colonne est NULL. */
    fun getDouble(column: String): Double?
}
