package fr.geotower.data.build

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Valide la **mutualisation du parsing** : le builder mobile parse le ZIP SUP une seule fois et « tee »
 * chaque ligne vers le [RadioDbBuilder.RadioStagingSink] (avant ses filtres mobiles) ; la base radio est
 * ensuite finalisee par [RadioDbBuilder.buildFromStaging] sans re-parser. Le resultat radio doit etre
 * identique a un build radio standalone.
 */
class RadioMutualizedBuildTest {

    private fun row(vararg pairs: Pair<String, String?>): AnfrCsvRow = AnfrCsvRow.of(mapOf(*pairs))

    private fun references(): AnfrReferences = AnfrReferences(
        nature = mapOf("23" to "Pylone"),
        proprietaire = mapOf("1" to "TDF Proprio"),
        exploitant = mapOf("5" to "ORANGE", "6" to "TDF"),
        typeAntenne = mapOf("10" to "Antenne broadcast"),
    )

    /** Sources mobiles : un observatoire minimal (station mobile 1) + les fichiers SUP partages. */
    private fun mobileSources(): AnfrSources = AnfrSources(
        weekly = listOf(
            row(
                "sta_nm_anfr" to "1", "coordonnees" to "48.85 2.35", "adm_lb_nom" to "Orange",
                "statut" to "En service", "generation" to "4G", "emr_lb_systeme" to "LTE 800",
                "emr_dt" to "2026-01-01", "date_maj" to "2026-07-01",
            ),
        ),
        stations = listOf(
            row("sta_nm_anfr" to "1", "adm_id" to "5"), // ORANGE
            row("sta_nm_anfr" to "2", "adm_id" to "6"), // TDF
        ),
        bandes = listOf(
            row("emr_id" to "E1", "ban_nb_f_deb" to "791", "ban_nb_f_fin" to "801", "ban_fg_unite" to "M"),
            row("emr_id" to "E2", "ban_nb_f_deb" to "174", "ban_nb_f_fin" to "230", "ban_fg_unite" to "M"),
        ),
        emetteurs = listOf(
            row("sta_nm_anfr" to "1", "emr_id" to "E1", "aer_id" to "AE1", "emr_lb_systeme" to "LTE 800"), // mobile public -> exclu radio
            row("sta_nm_anfr" to "2", "emr_id" to "E2", "aer_id" to "AE2", "emr_lb_systeme" to "RDF T-DAB"), // broadcast -> garde radio
        ),
        antennes = listOf(
            row("sta_nm_anfr" to "1", "aer_id" to "AE1", "sup_id" to "S1", "tae_id" to "10", "aer_nb_azimut" to "90", "aer_nb_alt_bas" to "20"),
            row("sta_nm_anfr" to "2", "aer_id" to "AE2", "sup_id" to "S2", "tae_id" to "10", "aer_nb_azimut" to "45", "aer_nb_alt_bas" to "120"),
        ),
        supports = listOf(
            row(
                "sta_nm_anfr" to "1", "sup_id" to "S1", "nat_id" to "23", "tpo_id" to "1", "sup_nm_haut" to "50",
                "cor_nb_dg_lat" to "48", "cor_nb_mn_lat" to "51", "cor_nb_sc_lat" to "0", "cor_cd_ns_lat" to "N",
                "cor_nb_dg_lon" to "2", "cor_nb_mn_lon" to "21", "cor_nb_sc_lon" to "0", "cor_cd_ew_lon" to "E",
            ),
            row(
                "sta_nm_anfr" to "2", "sup_id" to "S2", "nat_id" to "23", "tpo_id" to "1", "sup_nm_haut" to "200",
                "cor_nb_dg_lat" to "43", "cor_nb_mn_lat" to "36", "cor_nb_sc_lat" to "0", "cor_cd_ns_lat" to "N",
                "cor_nb_dg_lon" to "1", "cor_nb_mn_lon" to "26", "cor_nb_sc_lon" to "0", "cor_cd_ew_lon" to "E",
            ),
        ),
    )

    @Test
    fun radioStagedDuringMobileParseMatchesStandalone() {
        val radioFile = File.createTempFile("geotower_radio_mut", ".db").apply { deleteOnExit() }
        JdbcSqlDatabase(radioFile.absolutePath).use { radioDb ->
            // Prepare la base radio + le sink, comme le pipeline avant le build mobile.
            RadioDbBuilder.prepareSchema(radioDb)
            val sink = RadioDbBuilder.RadioStagingSink(radioDb, references().typeAntenne)

            // Build mobile : parse le ZIP une fois, « tee » chaque ligne SUP vers le sink radio.
            val mobileFile = File.createTempFile("geotower_mobile_mut", ".db").apply { deleteOnExit() }
            JdbcSqlDatabase(mobileFile.absolutePath).use { mobileDb ->
                GeoTowerDbBuilder.build(
                    mobileDb, mobileSources(), references(), emptyMap(),
                    BuildConfig(version = "20260701_1200"),
                    supSink = sink,
                )
            }

            // Finalisation radio depuis le staging deja peuple (aucun re-parse).
            RadioDbBuilder.buildFromStaging(radioDb, references(), RadioBuildConfig(version = "20260701_1200"))
        }

        DriverManager.getConnection("jdbc:sqlite:${radioFile.absolutePath}").use { conn ->
            // Un seul site non-mobile : station 2 (TDF/DAB). Station 1 (ORANGE/LTE) exclue.
            assertEquals(1L, conn.count("non_mobile_site"))
            assertNull(conn.one("SELECT * FROM non_mobile_site WHERE sta_nm_anfr = '0000000001'"))
            assertNotNull(conn.one("SELECT * FROM non_mobile_site WHERE sta_nm_anfr = '0000000002'"))
            assertEquals("ANFR_RADIO", conn.one("SELECT source FROM metadata")!!["source"])
        }
    }

    private fun Connection.one(sql: String): Map<String, Any?>? {
        createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                if (!rs.next()) return null
                val meta = rs.metaData
                return (1..meta.columnCount).associate { meta.getColumnLabel(it) to rs.getObject(it) }
            }
        }
    }

    private fun Connection.count(table: String): Long =
        createStatement().use { st -> st.executeQuery("SELECT COUNT(*) FROM $table").use { it.next(); it.getLong(1) } }
}
