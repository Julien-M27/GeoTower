package fr.geotower.data.build

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.Types

/**
 * Implementation JDBC (sqlite-jdbc) de [SqlDatabase] pour les tests JVM. Sur l'appareil,
 * une implementation `android.database.sqlite` fournira le meme contrat (Slice 3). Le SQL
 * emis par [GeoTowerDbBuilder] etant brut, les deux backends sont interchangeables.
 */
class JdbcSqlDatabase(path: String, stagingPath: String? = null) : SqlDatabase {
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:$path")

    /** Non vide quand une base de staging est attachee : la DDL des `stg_*` la vise alors. */
    override val stagingPrefix: String = if (stagingPath != null) "$STAGING_SCHEMA." else ""

    init {
        if (stagingPath != null) execSql("ATTACH DATABASE '$stagingPath' AS $STAGING_SCHEMA")
    }

    override fun execSql(sql: String) {
        connection.createStatement().use { it.execute(sql) }
    }

    override fun insertBatch(sql: String, rows: Iterable<List<Any?>>): Int {
        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false
        var count = 0
        connection.prepareStatement(sql).use { statement ->
            for (row in rows) {
                row.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.addBatch()
                count++
                if (count % 5000 == 0) statement.executeBatch()
            }
            statement.executeBatch()
        }
        connection.commit()
        connection.autoCommit = previousAutoCommit
        return count
    }

    override fun insertInTransaction(sql: String, block: (SqlInsertStatement) -> Unit) {
        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            connection.prepareStatement(sql).use { statement ->
                block(JdbcInsertStatement(statement))
            }
            connection.commit()
        } catch (error: Throwable) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = previousAutoCommit
        }
    }

    override fun query(sql: String, onRow: (SqlRow) -> Unit) {
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                val row = object : SqlRow {
                    override fun getString(column: String): String? = rs.getString(column)
                    override fun getInt(column: String): Int = rs.getInt(column)
                    override fun getIntOrNull(column: String): Int? {
                        val value = rs.getInt(column)
                        return if (rs.wasNull()) null else value
                    }
                    override fun getDouble(column: String): Double? {
                        val value = rs.getDouble(column)
                        return if (rs.wasNull()) null else value
                    }
                }
                while (rs.next()) onRow(row)
            }
        }
    }

    override fun close() {
        connection.close()
    }

    private companion object {
        /** Meme alias que sur l'appareil (`AndroidSqlDatabase.STAGING_SCHEMA`). */
        const val STAGING_SCHEMA = "stg"
    }

    private class JdbcInsertStatement(
        private val statement: PreparedStatement,
    ) : SqlInsertStatement {
        override fun clearBindings() = statement.clearParameters()

        override fun bindNull(index: Int) = statement.setNull(index, Types.NULL)

        override fun bindString(index: Int, value: String) = statement.setString(index, value)

        override fun bindLong(index: Int, value: Long) = statement.setLong(index, value)

        override fun bindDouble(index: Int, value: Double) = statement.setDouble(index, value)

        override fun executeInsert() {
            statement.executeUpdate()
        }
    }
}
