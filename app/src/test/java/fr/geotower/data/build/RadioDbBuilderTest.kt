package fr.geotower.data.build

import fr.geotower.data.models.RadioServiceMasks
import fr.geotower.data.models.RadioSystemMasks
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.Base64
import java.util.zip.InflaterInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build end-to-end de la base radio non-mobile via [JdbcSqlDatabase] (JVM, sans device). Verifie la
 * regle d'exclusion du mobile public, l'agregation des masques, la resolution de support (AER + repli
 * "support unique"), le blob `detail_z` et les metadonnees attendues par `RadioDatabaseValidator`.
 */
class RadioDbBuilderTest {

    private fun row(vararg pairs: Pair<String, String?>): AnfrCsvRow = AnfrCsvRow.of(mapOf(*pairs))

    private fun sources(): AnfrSources = AnfrSources(
        weekly = emptyList(),
        stations = listOf(
            row("sta_nm_anfr" to "1", "adm_id" to "5"), // ORANGE (mobile public)
            row("sta_nm_anfr" to "2", "adm_id" to "6"), // TDF
            row("sta_nm_anfr" to "3", "adm_id" to "7"), // SNCF
            row("sta_nm_anfr" to "4", "adm_id" to "8"), // Defense
            row("sta_nm_anfr" to "6", "adm_id" to "5"), // ORANGE (mais emetteur FM non-mobile)
            row("sta_nm_anfr" to "7", "adm_id" to "6"), // TDF, emetteur sans antenne -> repli support unique
        ),
        bandes = listOf(
            row("emr_id" to "E2", "ban_nb_f_deb" to "174", "ban_nb_f_fin" to "230", "ban_fg_unite" to "M"),
            row("emr_id" to "E3", "ban_nb_f_deb" to "921", "ban_nb_f_fin" to "925", "ban_fg_unite" to "M"),
            row("emr_id" to "E4", "ban_nb_f_deb" to "2700", "ban_nb_f_fin" to "2900", "ban_fg_unite" to "M"),
            row("emr_id" to "E6", "ban_nb_f_deb" to "87.5", "ban_nb_f_fin" to "108", "ban_fg_unite" to "M"),
            row("emr_id" to "E7", "ban_nb_f_deb" to "440", "ban_nb_f_fin" to "450", "ban_fg_unite" to "M"),
        ),
        emetteurs = listOf(
            row("sta_nm_anfr" to "1", "emr_id" to "E1", "aer_id" to "AE1", "emr_lb_systeme" to "LTE 800"), // exclu
            row("sta_nm_anfr" to "2", "emr_id" to "E2", "aer_id" to "AE2", "emr_lb_systeme" to "RDF T-DAB"),
            row("sta_nm_anfr" to "3", "emr_id" to "E3", "aer_id" to "AE3", "emr_lb_systeme" to "GSM R"),
            row("sta_nm_anfr" to "4", "emr_id" to "E4", "aer_id" to "AE4", "emr_lb_systeme" to "RDR"),
            row("sta_nm_anfr" to "6", "emr_id" to "E6", "aer_id" to "AE6", "emr_lb_systeme" to "FM"), // ORANGE mais garde
            row("sta_nm_anfr" to "7", "emr_id" to "E7", "aer_id" to "", "emr_lb_systeme" to "PMR"), // repli support unique
        ),
        antennes = listOf(
            row("sta_nm_anfr" to "1", "aer_id" to "AE1", "sup_id" to "S1", "tae_id" to "10", "aer_nb_azimut" to "90", "aer_nb_alt_bas" to "10"),
            row("sta_nm_anfr" to "2", "aer_id" to "AE2", "sup_id" to "S2", "tae_id" to "10", "aer_nb_azimut" to "45", "aer_nb_alt_bas" to "120"),
            row("sta_nm_anfr" to "3", "aer_id" to "AE3", "sup_id" to "S3", "tae_id" to "10", "aer_nb_azimut" to "0", "aer_nb_alt_bas" to "50"),
            row("sta_nm_anfr" to "4", "aer_id" to "AE4", "sup_id" to "S4", "tae_id" to "10", "aer_nb_azimut" to "180", "aer_nb_alt_bas" to "60"),
            row("sta_nm_anfr" to "6", "aer_id" to "AE6", "sup_id" to "S6", "tae_id" to "10", "aer_nb_azimut" to "270", "aer_nb_alt_bas" to "80"),
        ),
        supports = listOf(
            row(
                "sta_nm_anfr" to "1", "sup_id" to "S1", "nat_id" to "23", "tpo_id" to "1", "sup_nm_haut" to "50",
                "cor_nb_dg_lat" to "48", "cor_nb_mn_lat" to "51", "cor_nb_sc_lat" to "30", "cor_cd_ns_lat" to "N",
                "cor_nb_dg_lon" to "2", "cor_nb_mn_lon" to "21", "cor_nb_sc_lon" to "0", "cor_cd_ew_lon" to "E",
            ),
            row(
                "sta_nm_anfr" to "2", "sup_id" to "S2", "nat_id" to "23", "tpo_id" to "1", "sup_nm_haut" to "200",
                "adr_lb_lieu" to "Emetteur Sud", "adr_nm_cp" to "31000",
                "cor_nb_dg_lat" to "43", "cor_nb_mn_lat" to "36", "cor_nb_sc_lat" to "0", "cor_cd_ns_lat" to "N",
                "cor_nb_dg_lon" to "1", "cor_nb_mn_lon" to "26", "cor_nb_sc_lon" to "0", "cor_cd_ew_lon" to "E",
            ),
            row(
                "sta_nm_anfr" to "3", "sup_id" to "S3", "nat_id" to "23", "tpo_id" to "1", "sup_nm_haut" to "30",
                "cor_nb_dg_lat" to "45", "cor_nb_mn_lat" to "45", "cor_nb_sc_lat" to "0", "cor_cd_ns_lat" to "N",
                "cor_nb_dg_lon" to "4", "cor_nb_mn_lon" to "50", "cor_nb_sc_lon" to "0", "cor_cd_ew_lon" to "E",
            ),
            row(
                "sta_nm_anfr" to "4", "sup_id" to "S4", "nat_id" to "23", "tpo_id" to "1", "sup_nm_haut" to "40",
                "cor_nb_dg_lat" to "48", "cor_nb_mn_lat" to "0", "cor_nb_sc_lat" to "0", "cor_cd_ns_lat" to "N",
                "cor_nb_dg_lon" to "7", "cor_nb_mn_lon" to "0", "cor_nb_sc_lon" to "0", "cor_cd_ew_lon" to "E",
            ),
            row(
                "sta_nm_anfr" to "6", "sup_id" to "S6", "nat_id" to "23", "tpo_id" to "1", "sup_nm_haut" to "10",
                "cor_nb_dg_lat" to "48", "cor_nb_mn_lat" to "52", "cor_nb_sc_lat" to "0", "cor_cd_ns_lat" to "N",
                "cor_nb_dg_lon" to "2", "cor_nb_mn_lon" to "20", "cor_nb_sc_lon" to "0", "cor_cd_ew_lon" to "E",
            ),
            row(
                "sta_nm_anfr" to "7", "sup_id" to "S7", "nat_id" to "23", "tpo_id" to "1", "sup_nm_haut" to "15",
                "cor_nb_dg_lat" to "47", "cor_nb_mn_lat" to "0", "cor_nb_sc_lat" to "0", "cor_cd_ns_lat" to "N",
                "cor_nb_dg_lon" to "1", "cor_nb_mn_lon" to "0", "cor_nb_sc_lon" to "0", "cor_cd_ew_lon" to "E",
            ),
        ),
    )

    private fun references(): AnfrReferences = AnfrReferences(
        nature = mapOf("23" to "Pylone"),
        proprietaire = mapOf("1" to "TDF Proprio"),
        exploitant = mapOf("5" to "ORANGE", "6" to "TDF", "7" to "SNCF RESEAU", "8" to "MIN DEFENSE"),
        typeAntenne = mapOf("10" to "Antenne broadcast"),
    )

    private fun buildRadio(): File {
        val file = File.createTempFile("geotower_radio_test", ".db").apply { deleteOnExit() }
        JdbcSqlDatabase(file.absolutePath).use { db ->
            RadioDbBuilder.build(
                db, sources(), references(),
                RadioBuildConfig(version = "20260701_1200", zipVersion = "sup_2026_07.zip", dateMajAnfr = "2026-07-01"),
            )
        }
        return file
    }

    @Test
    fun excludesPublicMobileButKeepsNonMobileSites() {
        DriverManager.getConnection("jdbc:sqlite:${buildRadio().absolutePath}").use { conn ->
            // 5 sites gardes (2,3,4,6,7). La station 1 (ORANGE + LTE mobile-like) est exclue.
            assertEquals(5L, conn.count("non_mobile_site"))
            assertEquals(5L, conn.count("non_mobile_detail"))
            assertNull(conn.one("SELECT * FROM non_mobile_site WHERE sta_nm_anfr = '0000000001'"))
        }
    }

    @Test
    fun aggregatesServiceAndSystemMasksPerFamily() {
        DriverManager.getConnection("jdbc:sqlite:${buildRadio().absolutePath}").use { conn ->
            val broadcast = conn.one("SELECT * FROM non_mobile_site WHERE sta_nm_anfr = '0000000002'")!!
            assertMask(RadioServiceMasks.BROADCAST, broadcast["service_mask"])
            assertMask(RadioSystemMasks.DAB, broadcast["system_mask"])
            assertEquals(43_600_000L, (broadcast["lat_e6"] as Number).toLong()) // 43°36'00"N
            assertEquals(174_000L, (broadcast["min_freq_khz"] as Number).toLong())
            assertEquals(230_000L, (broadcast["max_freq_khz"] as Number).toLong())
            assertEquals(1, (broadcast["antenna_count"] as Number).toInt())

            val rail = conn.one("SELECT * FROM non_mobile_site WHERE sta_nm_anfr = '0000000003'")!!
            assertMask(RadioServiceMasks.RAIL, rail["service_mask"])
            assertMask(RadioSystemMasks.GSM_R, rail["system_mask"])

            val radar = conn.one("SELECT * FROM non_mobile_site WHERE sta_nm_anfr = '0000000004'")!!
            assertMask(RadioServiceMasks.RADAR, radar["service_mask"])
            assertMask(RadioSystemMasks.RADAR, radar["system_mask"])

            // Operateur mobile public MAIS emetteur non-mobile (FM) -> conserve.
            val orangeFm = conn.one("SELECT * FROM non_mobile_site WHERE sta_nm_anfr = '0000000006'")!!
            assertMask(RadioServiceMasks.BROADCAST, orangeFm["service_mask"])
            assertMask(RadioSystemMasks.FM, orangeFm["system_mask"])
        }
    }

    @Test
    fun resolvesSupportViaSingleSupportFallbackWhenAerMissing() {
        DriverManager.getConnection("jdbc:sqlite:${buildRadio().absolutePath}").use { conn ->
            // Station 7 : emetteur sans AER exploitable -> repli sur l'unique support S7, 0 antenne.
            val site = conn.one("SELECT * FROM non_mobile_site WHERE sta_nm_anfr = '0000000007'")!!
            assertEquals("S7", site["sup_id"])
            assertEquals(0, (site["antenna_count"] as Number).toInt())
            assertMask(RadioServiceMasks.PRIVATE, site["service_mask"])
        }
    }

    @Test
    fun writesValidatorCompatibleMetadataAndRefs() {
        DriverManager.getConnection("jdbc:sqlite:${buildRadio().absolutePath}").use { conn ->
            val meta = conn.one("SELECT * FROM metadata")!!
            assertEquals("ANFR_RADIO", meta["source"])
            assertEquals(1, (meta["schema_version"] as Number).toInt())
            assertEquals("FR", meta["country_code"])
            assertEquals("France", meta["country_name"])
            assertEquals("20260701_1200", meta["version"])
            assertEquals(5L, (meta["row_count"] as Number).toLong())

            // ref_actor : uniquement les exploitants references par des sites gardes (5 via sta6, 6, 7, 8).
            assertEquals("TDF", conn.one("SELECT label FROM ref_actor WHERE adm_id = 6")!!["label"])
            assertEquals("SNCF RESEAU", conn.one("SELECT label FROM ref_actor WHERE adm_id = 7")!!["label"])
            assertEquals("Antenne broadcast", conn.one("SELECT label FROM ref_type_antenne WHERE tae_id = 10")!!["label"])
        }
    }

    @Test
    fun detailBlobDecodesToReadableLines() {
        DriverManager.getConnection("jdbc:sqlite:${buildRadio().absolutePath}").use { conn ->
            val raw = conn.one("SELECT detail_z FROM non_mobile_detail WHERE sta_nm_anfr = '0000000002'")!!["detail_z"] as String
            val detail = decodeDetail(raw)
            assertTrue(detail, detail.contains("Familles: Radio/TV"))
            assertTrue(detail, detail.contains("Systemes: RDF T-DAB x1"))
            assertTrue(detail, detail.contains("Support: Pylone; proprietaire TDF Proprio; hauteur_dm=2000"))
            assertTrue(detail, detail.contains("Frequences: 174-230 MHz"))
            assertTrue(detail, detail.contains("Antennes: Antenne broadcast: 45 deg (120m)"))
            assertTrue(detail, detail.contains("Adresse: Emetteur Sud, 31000"))
            // La ligne Programmes (ARCOM) est absente cote build local -> jamais presente.
            assertTrue(detail, !detail.contains("Programmes:"))
        }
    }

    /** Miroir de `RadioRepository.decodeDetail` : prefixe `Z1:` -> base64 standard + zlib, sinon texte brut. */
    private fun decodeDetail(value: String): String {
        if (!value.startsWith("Z1:")) return value
        val compressed = Base64.getDecoder().decode(value.substring(3))
        return InflaterInputStream(compressed.inputStream()).readBytes().toString(Charsets.UTF_8)
    }

    private fun assertMask(expectedBit: Int, actual: Any?) {
        val mask = (actual as Number).toInt()
        assertTrue("bit $expectedBit absent du masque $mask", (mask and expectedBit) != 0)
    }

    private fun Connection.one(sql: String): Map<String, Any?>? {
        createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                if (!rs.next()) return null
                val meta = rs.metaData
                return (1..meta.columnCount).associate { meta.getColumnLabel(it) to rs.getObject(it) }
            }
        }
    }

    private fun Connection.count(table: String): Long =
        createStatement().use { st -> st.executeQuery("SELECT COUNT(*) FROM $table").use { it.next(); it.getLong(1) } }
}
