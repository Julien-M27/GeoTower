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

    /** Execute une instruction unique (DDL, PRAGMA, INSERT fixe). */
    fun execSql(sql: String)

    /**
     * Prepare `sql` (par ex. `"INSERT INTO t VALUES (?, ?)"`) et l'execute pour chaque
     * ligne, en transaction et par lots. Retourne le nombre de lignes inserees.
     */
    fun insertBatch(sql: String, rows: Iterable<List<Any?>>): Int

    /**
     * Execute une requete `SELECT` et invoque `onRow` pour chaque ligne, en flux (pas de
     * materialisation complete). Utilise par [AnfrStatsBuilder] pour recalculer les stats
     * a partir de la base construite.
     */
    fun query(sql: String, onRow: (SqlRow) -> Unit)

    override fun close()
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
