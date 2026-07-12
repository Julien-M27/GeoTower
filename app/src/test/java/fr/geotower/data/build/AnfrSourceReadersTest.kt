package fr.geotower.data.build

import fr.geotower.data.models.FrequencyDetailsCodec
import fr.geotower.data.models.RadioFilterMasks
import java.io.File
import java.io.FileOutputStream
import java.sql.Connection
import java.sql.DriverManager
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnfrSourceReadersTest {

    @Test
    fun splitLineHandlesDelimiterEmptyAndQuotedFields() {
        assertEquals(listOf("a", "b", "c"), AnfrCsvParser.splitLine("a;b;c"))
        assertEquals(listOf("a", "", "c"), AnfrCsvParser.splitLine("a;;c"))
        assertEquals(listOf("a", "b", ""), AnfrCsvParser.splitLine("a;b;"))
        assertEquals(listOf("a;b", "c"), AnfrCsvParser.splitLine("\"a;b\";c"))
        assertEquals(listOf("a\"b", "c"), AnfrCsvParser.splitLine("\"a\"\"b\";c"))
    }

    @Test
    fun readsBothUtf8AndLatin1CsvIdentically() {
        // Le meme texte accentue, encode en UTF-8 PUIS en Windows-1252 (vieux exports ANFR), doit etre lu
        // a l'identique : le lecteur detecte l'encodage (data.gouv heberge les deux). C'est le fix du bug
        // des adresses en "�" (l'app telechargeait un vieil export Latin-1 lu comme de l'UTF-8).
        val text = "id;lieu\n1;Château d'eau à Mazières\n"
        for (charset in listOf(Charsets.UTF_8, java.nio.charset.Charset.forName("windows-1252"))) {
            val rows = AnfrCsvParser.iterator(java.io.ByteArrayInputStream(text.toByteArray(charset)))
                .asSequence().toList()
            assertEquals("Château d'eau à Mazières", rows[0].get("lieu"))
        }
    }

    @Test
    fun buildsDatabaseFromRawZipAndWeeklyCsv() {
        val weekly = tempFile(
            "anfr_weekly", ".csv",
            """
            sta_nm_anfr;coordonnees;adm_lb_nom;statut;generation;emr_lb_systeme;emr_dt;date_maj
            1;48.85 2.35;Orange;En service;4G;LTE 800;2026-01-01;2026-06-01
            2;43.60 1.44;SFR;En projet;5G;5G NR 3500;;2026-06-01
            """.trimIndent(),
        )

        val zip = zipOf(
            "SUP_STATION.txt" to """
                STA_NM_ANFR;ADM_ID;DTE_IMPLANTATION;DTE_MODIF;DTE_EN_SERVICE
                1;5;2020-01-01;2026-05-01;2020-06-01
                2;6;;;
            """.trimIndent(),
            "SUP_BANDE.txt" to """
                EMR_ID;BAN_NB_F_DEB;BAN_NB_F_FIN;BAN_FG_UNITE
                E1;791;801;M
                E2;3400;3800;M
            """.trimIndent(),
            "SUP_EMETTEUR.txt" to """
                STA_NM_ANFR;EMR_ID;AER_ID;EMR_LB_SYSTEME
                1;E1;AE1;LTE 800
                2;E2;AE2;5G NR 3500
            """.trimIndent(),
            "SUP_ANTENNE.txt" to """
                STA_NM_ANFR;AER_ID;SUP_ID;TAE_ID;AER_NB_AZIMUT;AER_NB_ALT_BAS
                1;AE1;S1;16;120;28
                2;AE2;S2;32;240;30
            """.trimIndent(),
            "SUP_SUPPORT.txt" to """
                STA_NM_ANFR;SUP_ID;NAT_ID;TPO_ID;SUP_NM_HAUT;COM_CD_INSEE;ADR_LB_LIEU;ADR_LB_ADD1;ADR_LB_ADD2;ADR_LB_ADD3;ADR_NM_CP
                1;S1;23;1;30;75056;Rue X;;;;75001
                2;S2;17;2;25;31555;;;;;
            """.trimIndent(),
            "SUP_NATURE.txt" to "NAT_ID;NAT_LB_NOM\n23;Pylone\n17;Chateau d'eau",
            "SUP_EXPLOITANT.txt" to "ADM_ID;ADM_LB_NOM\n5;Orange\n6;SFR",
            "SUP_TYPE_ANTENNE.txt" to "TAE_ID;TAE_LB\n16;Panneau\n32;Panneau 5G",
        )

        val dbFile = tempFile("anfr_reader_db", ".db", "")

        AnfrMonthlyZip(zip).use { monthly ->
            val sources = anfrSourcesFrom(weekly, monthly)
            val references = anfrReferencesFrom(monthly, mapOf("75056" to "PARIS", "31555" to "TOULOUSE"))
            JdbcSqlDatabase(dbFile.absolutePath).use { db ->
                GeoTowerDbBuilder.build(
                    db, sources, references,
                    mapOf(("0000000001" to "ORANGE") to ArcepSiteMeta("NIDT1", 0)),
                    BuildConfig(version = "20260601_1200", zipVersion = "sup.zip"),
                )
            }
        }

        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            assertEquals(7, conn.int("PRAGMA user_version"))
            assertEquals(2L, conn.count("localisation"))
            assertEquals(2L, conn.count("support"))
            assertEquals(2L, conn.count("antenne"))

            val locA = conn.one("SELECT * FROM localisation WHERE id_anfr = '0000000001'")!!
            assertEquals(RadioFilterMasks.TECH_4G, (locA["tech_mask"] as Number).toInt())
            assertEquals(RadioFilterMasks.BAND_4G_800, (locA["band_mask"] as Number).toInt())
            assertEquals("120", locA["azimuts"])
            assertEquals("NIDT1", locA["arcep_nidt"])

            // Adresse composee via le referentiel communes injecte.
            val techA = conn.one("SELECT * FROM technique WHERE id_anfr = '0000000001'")!!
            assertEquals("Rue X, 75001 PARIS", techA["adresse"])
            // Le type d'antenne provient du referentiel SUP_TYPE_ANTENNE lu dans le ZIP.
            assertEquals(
                "LTE 800 : 791-801 MHz | En service | 2026-01-01 | Panneau : 120° (28m) [AER_ID: AE1]",
                FrequencyDetailsCodec.decode(techA["details_frequences"] as String?),
            )

            // Referentiel exploitant lu dans le ZIP.
            assertEquals("Orange", conn.one("SELECT libelle FROM ref_exploitant WHERE adm_id = 5")!!["libelle"])

            // Stats obligatoires peuplees.
            assertTrue(conn.count("radio_stat_current") >= 6L)
        }
    }

    @Test
    fun detectsDelimiterFromHeader() {
        assertEquals(';', AnfrCsvParser.detectDelimiter("a;b;c"))
        assertEquals(',', AnfrCsvParser.detectDelimiter("a,b,c"))
        assertEquals(';', AnfrCsvParser.detectDelimiter(null))
    }

    @Test
    fun csvRowsAutoDetectsCommaSeparatedStream() {
        val csv = "sta_nm_anfr,coordonnees,statut\n1,48.85 2.35,En service"
        val rows = csvRows { csv.byteInputStream() }.toList()
        assertEquals(1, rows.size)
        assertEquals("1", rows[0].get("sta_nm_anfr"))
        assertEquals("En service", rows[0].get("statut"))
    }

    private fun tempFile(prefix: String, suffix: String, content: String): File =
        File.createTempFile(prefix, suffix).apply {
            deleteOnExit()
            if (content.isNotEmpty()) writeText(content, Charsets.UTF_8)
        }

    private fun zipOf(vararg entries: Pair<String, String>): File {
        val file = File.createTempFile("anfr_zip", ".zip").apply { deleteOnExit() }
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return file
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

    private fun Connection.int(query: String): Int =
        createStatement().use { st -> st.executeQuery(query).use { it.next(); it.getInt(1) } }
}
