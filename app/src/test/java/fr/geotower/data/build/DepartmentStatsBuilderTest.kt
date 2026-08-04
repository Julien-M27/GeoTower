package fr.geotower.data.build

import java.io.File
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Les attendus sont ceux de `test_fr_dept_stats.py` (meme scenario : pylone mutualise, antenne
 * portant 4G et 5G, parabole FH, station en projet dans un second departement). Les deux
 * implementations — SQL de staging ici, ensembles en memoire cote serveur — doivent produire
 * exactement les memes chiffres.
 */
class DepartmentStatsBuilderTest {

    private fun row(vararg pairs: Pair<String, String?>): AnfrCsvRow = AnfrCsvRow.of(mapOf(*pairs))

    /**
     * Sarthe (72) : un pylone S1 portant une station Orange et une station SFR, qui declarent la
     * MEME antenne AE1 — le cas que la table `antenne` (cle primaire `aer_id`) ne sait pas
     * representer. Ille-et-Vilaine (35) : une station Orange entierement en projet.
     */
    private fun sources(): AnfrSources = AnfrSources(
        weekly = listOf(
            row(
                "sta_nm_anfr" to "1", "coordonnees" to "47.99 0.19", "adm_lb_nom" to "Orange",
                "statut" to "En service", "generation" to "4G", "emr_lb_systeme" to "LTE 800",
                "date_maj" to "2026-06-01",
            ),
            row(
                "sta_nm_anfr" to "1", "coordonnees" to "47.99 0.19", "adm_lb_nom" to "Orange",
                "statut" to "En service", "generation" to "5G", "emr_lb_systeme" to "5G NR 3500",
            ),
            row(
                "sta_nm_anfr" to "1", "coordonnees" to "47.99 0.19", "adm_lb_nom" to "Orange",
                "statut" to "Projet approuve", "generation" to "4G", "emr_lb_systeme" to "LTE 1800",
            ),
            row(
                "sta_nm_anfr" to "1", "coordonnees" to "47.99 0.19", "adm_lb_nom" to "Orange",
                "statut" to "En service", "emr_lb_systeme" to "FH 4",
            ),
            row(
                "sta_nm_anfr" to "2", "coordonnees" to "47.99 0.19", "adm_lb_nom" to "SFR",
                "statut" to "En service", "generation" to "4G", "emr_lb_systeme" to "LTE 2600",
            ),
            row(
                "sta_nm_anfr" to "3", "coordonnees" to "48.11 -1.68", "adm_lb_nom" to "Orange",
                "statut" to "Projet approuve", "generation" to "2G", "emr_lb_systeme" to "GSM 900",
            ),
        ),
        stations = listOf(
            row("sta_nm_anfr" to "1", "adm_id" to "5"),
            row("sta_nm_anfr" to "2", "adm_id" to "6"),
            row("sta_nm_anfr" to "3", "adm_id" to "5"),
        ),
        bandes = listOf(
            row("emr_id" to "E1", "ban_nb_f_deb" to "791", "ban_nb_f_fin" to "801", "ban_fg_unite" to "M"),
            row("emr_id" to "E2", "ban_nb_f_deb" to "3400", "ban_nb_f_fin" to "3800", "ban_fg_unite" to "M"),
            row("emr_id" to "E3", "ban_nb_f_deb" to "1805", "ban_nb_f_fin" to "1830", "ban_fg_unite" to "M"),
            row("emr_id" to "E4", "ban_nb_f_deb" to "22", "ban_nb_f_fin" to "23", "ban_fg_unite" to "G"),
            row("emr_id" to "E5", "ban_nb_f_deb" to "2500", "ban_nb_f_fin" to "2570", "ban_fg_unite" to "M"),
            row("emr_id" to "E6", "ban_nb_f_deb" to "890", "ban_nb_f_fin" to "915", "ban_fg_unite" to "M"),
        ),
        emetteurs = listOf(
            // AE1 porte la 4G ET la 5G : elle ne doit compter qu'une fois dans le total.
            row("sta_nm_anfr" to "1", "emr_id" to "E1", "aer_id" to "AE1", "emr_lb_systeme" to "LTE 800"),
            row("sta_nm_anfr" to "1", "emr_id" to "E2", "aer_id" to "AE1", "emr_lb_systeme" to "5G NR 3500"),
            row("sta_nm_anfr" to "1", "emr_id" to "E3", "aer_id" to "AE2", "emr_lb_systeme" to "LTE 1800"),
            row("sta_nm_anfr" to "1", "emr_id" to "E4", "aer_id" to "AEF", "emr_lb_systeme" to "FH 4"),
            row("sta_nm_anfr" to "2", "emr_id" to "E5", "aer_id" to "AE1", "emr_lb_systeme" to "LTE 2600"),
            row("sta_nm_anfr" to "3", "emr_id" to "E6", "aer_id" to "AE3", "emr_lb_systeme" to "GSM 900"),
        ),
        antennes = listOf(
            row("sta_nm_anfr" to "1", "aer_id" to "AE1", "sup_id" to "S1", "tae_id" to "16", "aer_nb_azimut" to "90", "aer_nb_alt_bas" to "25"),
            row("sta_nm_anfr" to "1", "aer_id" to "AE2", "sup_id" to "S1", "tae_id" to "16", "aer_nb_azimut" to "210", "aer_nb_alt_bas" to "25"),
            row("sta_nm_anfr" to "1", "aer_id" to "AEF", "sup_id" to "S1", "tae_id" to "26", "aer_nb_azimut" to "12", "aer_nb_alt_bas" to "30"),
            row("sta_nm_anfr" to "2", "aer_id" to "AE1", "sup_id" to "S1", "tae_id" to "16", "aer_nb_azimut" to "90", "aer_nb_alt_bas" to "25"),
            row("sta_nm_anfr" to "3", "aer_id" to "AE3", "sup_id" to "S3", "tae_id" to "16", "aer_nb_azimut" to "0", "aer_nb_alt_bas" to "18"),
        ),
        supports = listOf(
            row("sta_nm_anfr" to "1", "sup_id" to "S1", "nat_id" to "23", "com_cd_insee" to "72181", "sup_nm_haut" to "30"),
            row("sta_nm_anfr" to "2", "sup_id" to "S1", "nat_id" to "23", "com_cd_insee" to "72181", "sup_nm_haut" to "30"),
            row("sta_nm_anfr" to "3", "sup_id" to "S3", "nat_id" to "23", "com_cd_insee" to "35238", "sup_nm_haut" to "25"),
        ),
    )

    private fun references(departments: Map<String, DepartmentReferenceRow> = emptyMap()) = AnfrReferences(
        nature = mapOf("23" to "Pylone"),
        exploitant = mapOf("5" to "Orange", "6" to "SFR"),
        typeAntenne = mapOf("16" to "Panneau", "26" to "Parabole FH"),
        communes = mapOf("72181" to "LE MANS", "35238" to "RENNES"),
        departments = departments,
    )

    private val sarthe = DepartmentReferenceRow(
        code = "72",
        name = "Sarthe",
        regionCode = "52",
        areaKm2 = 6206.0,
        population = 566506,
        populationYear = "2020",
    )

    private fun buildDatabase(departments: Map<String, DepartmentReferenceRow> = emptyMap()): File {
        val file = File.createTempFile("geotower_dept_stats", ".db").apply { deleteOnExit() }
        JdbcSqlDatabase(file.absolutePath).use { db ->
            GeoTowerDbBuilder.build(
                db,
                sources(),
                references(departments),
                emptyMap(),
                BuildConfig(version = "20260804_1200"),
            )
        }
        return file
    }

    private fun <T> withDb(file: File, block: (java.sql.Connection) -> T): T =
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use(block)

    private fun java.sql.Connection.department(code: String): Map<String, Any?>? =
        createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM dept_stat_current WHERE dept_code = '$code'").use { rs ->
                if (!rs.next()) return null
                (1..rs.metaData.columnCount).associate { rs.metaData.getColumnName(it) to rs.getObject(it) }
            }
        }

    private fun java.sql.Connection.matrix(): Map<Triple<String, String, String>, Triple<Int, Int, Int>> =
        createStatement().use { statement ->
            statement.executeQuery(
                "SELECT dept_code, operator_name, tech, supports, stations, antennas FROM dept_stat_operator_tech",
            ).use { rs ->
                val result = HashMap<Triple<String, String, String>, Triple<Int, Int, Int>>()
                while (rs.next()) {
                    result[Triple(rs.getString(1), rs.getString(2), rs.getString(3))] =
                        Triple(rs.getInt(4), rs.getInt(5), rs.getInt(6))
                }
                result
            }
        }

    @Test
    fun countsSupportsStationsAndAntennasPerDepartment() {
        val file = buildDatabase()

        withDb(file) { conn ->
            val sarthe = conn.department("72")!!
            assertEquals(1, (sarthe["supports"] as Number).toInt())
            assertEquals(2, (sarthe["stations"] as Number).toInt())
            // AE1 + AE2 pour Orange, AE1 pour SFR : l'antenne mutualisee compte par station.
            assertEquals(3, (sarthe["antennas"] as Number).toInt())
            assertEquals(1, (sarthe["antennas_fh"] as Number).toInt())

            val illeEtVilaine = conn.department("35")!!
            assertEquals(1, (illeEtVilaine["supports"] as Number).toInt())
            assertEquals(1, (illeEtVilaine["stations"] as Number).toInt())
            assertEquals(1, (illeEtVilaine["antennas"] as Number).toInt())
            assertEquals(0, (illeEtVilaine["antennas_fh"] as Number).toInt())
        }
    }

    @Test
    fun separatesAuthorisedAndActiveCounts() {
        val file = buildDatabase()

        withDb(file) { conn ->
            val sarthe = conn.department("72")!!
            assertEquals(2, (sarthe["stations_active"] as Number).toInt())
            assertEquals(2, (sarthe["antennas_active"] as Number).toInt())

            val illeEtVilaine = conn.department("35")!!
            assertEquals(0, (illeEtVilaine["stations_active"] as Number).toInt())
            assertEquals(0, (illeEtVilaine["antennas_active"] as Number).toInt())
        }
    }

    @Test
    fun breaksDownByOperatorAndTechnology() {
        val file = buildDatabase()

        withDb(file) { conn ->
            val matrix = conn.matrix()
            assertEquals(Triple(1, 1, 2), matrix[Triple("72", "ORANGE", "4G")])
            assertEquals(Triple(1, 1, 1), matrix[Triple("72", "ORANGE", "5G")])
            assertEquals(Triple(1, 1, 1), matrix[Triple("72", "SFR", "4G")])
            // 'ALL' cote technologie : antennes distinctes, pas la somme des colonnes.
            assertEquals(Triple(1, 1, 2), matrix[Triple("72", "ORANGE", "ALL")])
            assertEquals(Triple(1, 2, 3), matrix[Triple("72", "ALL", "4G")])
            assertEquals(Triple(1, 2, 3), matrix[Triple("72", "ALL", "ALL")])
            assertEquals(Triple(1, 1, 1), matrix[Triple("35", "ORANGE", "2G")])
            assertNull(matrix[Triple("72", "ORANGE", "2G")])
            assertNull(matrix[Triple("72", "ALL", "FH")])
        }
    }

    @Test
    fun matrixTotalsMatchTheDepartmentRow() {
        val file = buildDatabase()

        withDb(file) { conn ->
            val sarthe = conn.department("72")!!
            val total = conn.matrix()[Triple("72", "ALL", "ALL")]!!
            assertEquals(
                Triple(
                    (sarthe["supports"] as Number).toInt(),
                    (sarthe["stations"] as Number).toInt(),
                    (sarthe["antennas"] as Number).toInt(),
                ),
                total,
            )
        }
    }

    @Test
    fun computesRatiosFromTheReference() {
        val file = buildDatabase(mapOf("72" to sarthe))

        withDb(file) { conn ->
            val row = conn.department("72")!!
            assertEquals("Sarthe", row["dept_name"])
            assertEquals(566506, (row["population"] as Number).toInt())
            assertEquals("2020", row["population_year"])
            assertEquals(2.0, row["stations_per_support"] as Double, 1e-9)
            assertEquals(1.5, row["antennas_per_station"] as Double, 1e-9)
            assertEquals(round6(1.0 / 6206.0), row["supports_per_km2"] as Double, 1e-9)
            assertEquals(round6(3.0 / 6206.0), row["antennas_per_km2"] as Double, 1e-9)
            assertEquals(round6(2.0 / 566.506), row["stations_per_1k_hab"] as Double, 1e-9)
            assertEquals(round6(566506.0 / 3.0), row["hab_per_antenna"] as Double, 1e-9)
        }
    }

    @Test
    fun keepsCountersWhenTheReferenceIsMissing() {
        val file = buildDatabase()

        withDb(file) { conn ->
            val row = conn.department("72")!!
            assertNull(row["area_km2"])
            assertNull(row["population"])
            assertNull(row["supports_per_km2"])
            assertNull(row["hab_per_station"])
            // Les ratios purement ANFR restent calcules.
            assertEquals(2.0, row["stations_per_support"] as Double, 1e-9)
        }
    }

    @Test
    fun dropsTheStagingTableAfterTheBuild() {
        val file = buildDatabase()

        withDb(file) { conn ->
            conn.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='${DepartmentStatsBuilder.STAGING_TABLE}'",
                ).use { rs ->
                    rs.next()
                    assertEquals(0, rs.getInt(1))
                }
            }
        }
    }

    @Test
    fun aggregatesTheDepartmentReferenceFromGeoApiPayloads() {
        val departements = """
            [{"nom":"Sarthe","code":"72","codeRegion":"52"},{"nom":"Paris","code":"75","codeRegion":"11"}]
        """.trimIndent()
        val communes = """
            [
              {"nom":"Le Mans","code":"72181","codeDepartement":"72","population":143240,"surface":5424.0},
              {"nom":"Allonnes","code":"72003","codeDepartement":"72","population":10739,"surface":1820.38},
              {"nom":"Paris","code":"75056","codeDepartement":"75","population":2133111,"surface":10540.0}
            ]
        """.trimIndent()

        val reference = RawSourceDownloader.parseDepartmentReference(departements, communes, "2022")

        assertEquals("Sarthe", reference["72"]?.name)
        assertEquals("52", reference["72"]?.regionCode)
        // Hectares -> km2, comme le serveur.
        assertEquals(72.444, reference["72"]?.areaKm2!!, 1e-3)
        assertEquals(153979, reference["72"]?.population)
        assertEquals("2022", reference["72"]?.populationYear)
        assertEquals(105.4, reference["75"]?.areaKm2!!, 1e-3)
    }

    @Test
    fun readsAnOverseasCollectivityFromItsOwnEndpoints() {
        // `/departements/987` renvoie un objet, pas un tableau : c'est tout l'interet du parseur dedie.
        val departement = """{"nom":"Polynésie française","code":"987","codeRegion":"987"}"""
        val communes = """
            [
              {"code":"98735","population":26926,"surface":1755.0},
              {"code":"98718","population":25769,"surface":1730.0}
            ]
        """.trimIndent()

        val reference = RawSourceDownloader.parseSingleDepartmentReference(departement, communes, "987", "2022")!!

        assertEquals("987", reference.code)
        assertEquals("Polynésie française", reference.name)
        assertEquals(34.85, reference.areaKm2!!, 1e-3)
        assertEquals(52695, reference.population)
        assertEquals("2022", reference.populationYear)
    }

    @Test
    fun overseasCollectivityKeepsItsNameWithoutCommunes() {
        val reference = RawSourceDownloader.parseSingleDepartmentReference(
            """{"nom":"Saint-Barthélemy","code":"977"}""",
            "",
            "977",
        )!!

        assertEquals("Saint-Barthélemy", reference.name)
        assertNull(reference.areaKm2)
        assertNull(reference.population)
    }

    @Test
    fun overseasCollectivityIsSkippedWhenNothingIsUsable() {
        assertNull(RawSourceDownloader.parseSingleDepartmentReference("", "", "984"))
        assertNull(RawSourceDownloader.parseSingleDepartmentReference("{}", "[]", "984"))
    }

    @Test
    fun territoriesWithoutInhabitantsKeepANullPopulation() {
        // Les communes des TAAF n'ont pas de champ `population` : mieux vaut vide que « 0 habitant ».
        val reference = RawSourceDownloader.parseSingleDepartmentReference(
            """{"nom":"Terres australes et antarctiques françaises","code":"984"}""",
            """[{"code":"98411","surface":6052.39},{"code":"98412","surface":724889.59}]""",
            "984",
        )!!

        assertNull(reference.population)
        assertEquals(7309.42, reference.areaKm2!!, 1e-2)
    }

    @Test
    fun departmentReferenceSurvivesUnusablePayloads() {
        assertEquals(emptyMap<String, DepartmentReferenceRow>(), RawSourceDownloader.parseDepartmentReference("", ""))
        val reference = RawSourceDownloader.parseDepartmentReference(
            """[{"nom":"Sarthe","code":"72"}]""",
            "pas du json",
        )
        assertEquals("Sarthe", reference["72"]?.name)
        assertNull(reference["72"]?.areaKm2)
        assertNull(reference["72"]?.population)
    }

    private fun round6(value: Double): Double = Math.round(value * 1_000_000.0) / 1_000_000.0
}
