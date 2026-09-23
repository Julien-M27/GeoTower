package fr.geotower.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

class DatabaseInspectorRulesTest {
    @Test
    fun tableNamesQueryOnlyReturnsUserTables() {
        assertEquals(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
            DatabaseInspectorRules.tableNamesQuery()
        )
    }

    @Test
    fun tableRowsQueryQuotesTheSelectedTableAndUsesPagination() {
        assertEquals(
            "SELECT * FROM \"table\"\"with\"\"quotes\" LIMIT ? OFFSET ?",
            DatabaseInspectorRules.tableRowsQuery("table\"with\"quotes")
        )
    }

    @Test
    fun pageOffsetNeverGoesBelowZero() {
        assertEquals(0, DatabaseInspectorRules.pageOffset(page = -2, pageSize = 50))
        assertEquals(100, DatabaseInspectorRules.pageOffset(page = 2, pageSize = 50))
    }
}
