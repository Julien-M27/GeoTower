package fr.geotower.data.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTowerDbSchemaTest {

    /**
     * Colonnes exigees par [fr.geotower.data.db.GeoTowerDatabaseValidator]. Le schema local
     * doit toutes les couvrir sinon la base construite localement sera rejetee a l'installation.
     */
    private val requiredColumnsByTable = mapOf(
        "localisation" to listOf(
            "id_anfr", "operateur_id", "latitude", "longitude", "azimuts",
            "code_insee", "azimuts_fh", "tech_mask", "band_mask", "arcep_nidt", "is_zb"
        ),
        "technique" to listOf(
            "id_anfr", "adm_id", "statut_id", "date_implantation", "date_service",
            "date_modif", "details_frequences", "adresse", "has_active"
        ),
        "support" to listOf("id_anfr", "id_support", "nat_id", "tpo_id", "hauteur"),
        "antenne" to listOf("aer_id", "id_anfr", "id_support", "tae_id", "azimut", "hauteur_bas", "is_fh"),
        "ref_operateur" to listOf("id", "libelle"),
        "ref_nature" to listOf("nat_id", "libelle"),
        "ref_proprietaire" to listOf("tpo_id", "libelle"),
        "ref_exploitant" to listOf("adm_id", "libelle"),
        "ref_type_antenne" to listOf("tae_id", "libelle"),
        "ref_systeme" to listOf("id", "libelle"),
        "ref_statut" to listOf("id", "libelle"),
        "ref_commune" to listOf("code_insee", "nom"),
        "metadata" to listOf(
            "version", "schema_version", "country_code", "country_name",
            "source", "date_maj_anfr", "zip_version"
        ),
        "radio_stat_current" to listOf(
            "operator_name", "category", "item_key", "label", "total_count", "active_count"
        ),
        "radio_stat_weekly" to listOf(
            "week_key", "week_start", "source_date", "operator_name", "category",
            "item_key", "label", "total_count", "active_count"
        )
    )

    private fun statementFor(table: String): String {
        return GeoTowerDbSchema.CREATE_TABLE_STATEMENTS.firstOrNull {
            it.contains("`$table`") || it.contains("$table (")
        } ?: error("Aucune instruction CREATE TABLE pour '$table'")
    }

    @Test
    fun everyRequiredTableAndColumnIsPresent() {
        requiredColumnsByTable.forEach { (table, columns) ->
            val statement = statementFor(table)
            columns.forEach { column ->
                assertTrue(
                    "Colonne manquante $table.$column dans la DDL",
                    statement.contains(column)
                )
            }
        }
    }

    @Test
    fun roomStampsAreConsistent() {
        assertEquals(7, GeoTowerDbSchema.SCHEMA_VERSION)
        assertEquals("FR", GeoTowerDbSchema.COUNTRY_CODE)
        assertEquals("ANFR", GeoTowerDbSchema.SOURCE)
        assertEquals(32, GeoTowerDbSchema.ROOM_IDENTITY_HASH.length)
        assertEquals("f92129b45cc37b357c5ecb8e0ba597f0", GeoTowerDbSchema.ROOM_IDENTITY_HASH)
        assertTrue(GeoTowerDbSchema.INSERT_ROOM_IDENTITY.contains(GeoTowerDbSchema.ROOM_IDENTITY_HASH))
        assertEquals("PRAGMA user_version = 7", GeoTowerDbSchema.SET_USER_VERSION)
    }

    @Test
    fun schemaContainsExpectedStatementCount() {
        // 14 tables metier/reference + source_versions + 2 tables de stats.
        assertEquals(16, GeoTowerDbSchema.CREATE_TABLE_STATEMENTS.size)
        assertTrue(
            GeoTowerDbSchema.CREATE_TABLE_STATEMENTS.any { it.contains("source_versions") }
        )
    }
}
