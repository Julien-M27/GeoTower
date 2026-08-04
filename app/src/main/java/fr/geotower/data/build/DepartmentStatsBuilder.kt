package fr.geotower.data.build

import fr.geotower.data.models.FrequencyDetailsCodec
import fr.geotower.data.models.RadioFilterMasks
import fr.geotower.utils.DepartmentCodes

/** Superficie / population / nom d'un departement, tels que servis par geo.api.gouv.fr. */
data class DepartmentReferenceRow(
    val code: String,
    val name: String? = null,
    val regionCode: String? = null,
    val areaKm2: Double? = null,
    val population: Int? = null,
    val populationYear: String? = null,
)

/**
 * Peuple `dept_stat_current` et `dept_stat_operator_tech` a partir de la base deja construite.
 * Equivalent de `populate_department_stats` (docs/server/fr_dept_stats.py), aux memes definitions :
 * une antenne est comptee par station, les faisceaux hertziens sont a part, et `tech = 'ALL'`
 * compte les antennes distinctes plutot que la somme des colonnes.
 *
 * MEMOIRE : le serveur garde en RAM un ensemble d'identifiants de support par
 * (departement x operateur x technologie) — plusieurs centaines de Mo sur la France entiere, ce
 * qu'un tas Android ne supporterait pas. Ici, une table de staging recoit une ligne par
 * (station, technologie) et SQLite fait les agregats sur disque, comme pour le reste du build.
 * Seules les 101 lignes departementales finales repassent en memoire, pour les ratios.
 */
object DepartmentStatsBuilder {

    const val STAGING_TABLE = "stg_dept_station"

    /**
     * Millesime des populations legales servies par geo.api.gouv.fr, comme
     * `POPULATION_YEAR_DEFAULT` cote serveur. L'API ne le renvoie pas : il est fige ici pour
     * l'affichage, et a mettre a jour en meme temps que celui du serveur.
     */
    const val POPULATION_YEAR = "2022"

    private const val ITEM_ALL = "ALL"

    private val TECH_BITS = linkedMapOf(
        "2G" to RadioFilterMasks.TECH_2G,
        "3G" to RadioFilterMasks.TECH_3G,
        "4G" to RadioFilterMasks.TECH_4G,
        "5G" to RadioFilterMasks.TECH_5G,
    )

    private val AER_ID_REGEX = Regex("""\[AER_ID:\s*([^\]]+)\]""")

    private val STATIONS_QUERY = """
        SELECT
            l.id_anfr,
            l.code_insee,
            COALESCE(l.tech_mask, 0) AS tech_mask,
            UPPER(TRIM(COALESCE(o.libelle, ''))) AS operator_name,
            COALESCE(t.has_active, 0) AS has_active,
            COALESCE(st.libelle, '') AS statut,
            t.details_frequences AS details_frequences
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        LEFT JOIN technique t ON t.id_anfr = l.id_anfr
        LEFT JOIN ref_statut st ON t.statut_id = st.id
    """.trimIndent()

    private val CREATE_STAGING = """
        CREATE TABLE IF NOT EXISTS $STAGING_TABLE (
            id_anfr TEXT NOT NULL,
            dept TEXT NOT NULL,
            operator TEXT NOT NULL,
            tech TEXT NOT NULL,
            is_active INTEGER NOT NULL,
            antennas INTEGER NOT NULL,
            antennas_active INTEGER NOT NULL,
            antennas_fh INTEGER NOT NULL,
            PRIMARY KEY (id_anfr, tech)
        )
    """.trimIndent()

    /**
     * Agregats par (departement, operateur, technologie). Les supports se comptent a part :
     * une station peut etre declaree sur plusieurs supports, et joindre `support` a la volee
     * multiplierait les lignes — donc les compteurs d'antennes.
     */
    private val INSERT_OPERATOR_TECH = """
        INSERT INTO dept_stat_operator_tech (
            dept_code, operator_name, tech,
            supports, supports_active, stations, stations_active, antennas, antennas_active
        )
        SELECT
            c.dept,
            c.operator,
            c.tech,
            COALESCE(s.supports, 0),
            COALESCE(s.supports_active, 0),
            c.stations,
            c.stations_active,
            c.antennas,
            c.antennas_active
        FROM (
            SELECT
                dept,
                operator,
                tech,
                COUNT(*) AS stations,
                SUM(is_active) AS stations_active,
                SUM(antennas) AS antennas,
                SUM(antennas_active) AS antennas_active
            FROM $STAGING_TABLE
            GROUP BY dept, operator, tech
        ) c
        LEFT JOIN (
            SELECT
                d.dept AS dept,
                d.operator AS operator,
                d.tech AS tech,
                COUNT(DISTINCT sup.id_support) AS supports,
                COUNT(DISTINCT CASE WHEN d.is_active = 1 THEN sup.id_support END) AS supports_active
            FROM $STAGING_TABLE d
            INNER JOIN support sup ON sup.id_anfr = d.id_anfr
            GROUP BY d.dept, d.operator, d.tech
        ) s ON s.dept = c.dept AND s.operator = c.operator AND s.tech = c.tech
    """.trimIndent()

    /** Memes agregats, tous operateurs confondus (`operator_name = 'ALL'`). */
    private val INSERT_ALL_OPERATORS = """
        INSERT INTO dept_stat_operator_tech (
            dept_code, operator_name, tech,
            supports, supports_active, stations, stations_active, antennas, antennas_active
        )
        SELECT
            c.dept,
            '$ITEM_ALL',
            c.tech,
            COALESCE(s.supports, 0),
            COALESCE(s.supports_active, 0),
            c.stations,
            c.stations_active,
            c.antennas,
            c.antennas_active
        FROM (
            SELECT
                dept,
                tech,
                COUNT(*) AS stations,
                SUM(is_active) AS stations_active,
                SUM(antennas) AS antennas,
                SUM(antennas_active) AS antennas_active
            FROM $STAGING_TABLE
            GROUP BY dept, tech
        ) c
        LEFT JOIN (
            SELECT
                d.dept AS dept,
                d.tech AS tech,
                COUNT(DISTINCT sup.id_support) AS supports,
                COUNT(DISTINCT CASE WHEN d.is_active = 1 THEN sup.id_support END) AS supports_active
            FROM $STAGING_TABLE d
            INNER JOIN support sup ON sup.id_anfr = d.id_anfr
            GROUP BY d.dept, d.tech
        ) s ON s.dept = c.dept AND s.tech = c.tech
    """.trimIndent()

    /** Totaux du departement : la ligne (ALL, ALL) deja calculee, plus les paraboles FH. */
    private val DEPARTMENT_TOTALS_QUERY = """
        SELECT
            m.dept_code AS dept_code,
            m.supports AS supports,
            m.supports_active AS supports_active,
            m.stations AS stations,
            m.stations_active AS stations_active,
            m.antennas AS antennas,
            m.antennas_active AS antennas_active,
            COALESCE(fh.antennas_fh, 0) AS antennas_fh
        FROM dept_stat_operator_tech m
        LEFT JOIN (
            SELECT dept, SUM(antennas_fh) AS antennas_fh
            FROM $STAGING_TABLE
            WHERE tech = '$ITEM_ALL'
            GROUP BY dept
        ) fh ON fh.dept = m.dept_code
        WHERE m.operator_name = '$ITEM_ALL' AND m.tech = '$ITEM_ALL'
        ORDER BY m.dept_code
    """.trimIndent()

    /**
     * @param reference superficie / population par code departement. Vide si l'appareil n'a pas pu
     *   joindre geo.api.gouv.fr : les compteurs sont alors ecrits quand meme, seuls les ratios de
     *   densite et de population restent NULL (l'ecran sait l'afficher).
     * @return le nombre de departements ecrits.
     */
    fun populateDepartmentStats(
        db: SqlDatabase,
        reference: Map<String, DepartmentReferenceRow> = emptyMap(),
        populationYear: String? = null,
    ): Int {
        db.execSql("DROP TABLE IF EXISTS $STAGING_TABLE")
        db.execSql(CREATE_STAGING)
        db.execSql("DELETE FROM dept_stat_current")
        db.execSql("DELETE FROM dept_stat_operator_tech")

        fillStaging(db)

        db.execSql(INSERT_OPERATOR_TECH)
        db.execSql(INSERT_ALL_OPERATORS)

        val rows = departmentRows(db, reference, populationYear)
        db.insertBatch(
            "INSERT INTO dept_stat_current (" +
                "dept_code, dept_name, region_code, area_km2, population, population_year, " +
                "supports, supports_active, stations, stations_active, " +
                "antennas, antennas_active, antennas_fh, " +
                "stations_per_support, antennas_per_station, " +
                "supports_per_km2, stations_per_km2, antennas_per_km2, " +
                "supports_per_1k_hab, stations_per_1k_hab, antennas_per_1k_hab, " +
                "hab_per_support, hab_per_station, hab_per_antenna" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            rows,
        )

        db.execSql("DROP TABLE IF EXISTS $STAGING_TABLE")
        return rows.size
    }

    /** Ecriture par lots, comme les autres builders (leurs BatchInserter sont prives). */
    private class BatchInserter(
        private val db: SqlDatabase,
        private val sql: String,
        private val batchSize: Int = 5000,
    ) {
        private val buffer = ArrayList<List<Any?>>(batchSize)

        fun add(row: List<Any?>) {
            buffer.add(row)
            if (buffer.size >= batchSize) flush()
        }

        fun flush() {
            if (buffer.isNotEmpty()) {
                db.insertBatch(sql, buffer)
                buffer.clear()
            }
        }
    }

    /** Une ligne par (station, technologie), plus une ligne `ALL` portant les totaux de la station. */
    private fun fillStaging(db: SqlDatabase) {
        val inserter = BatchInserter(
            db,
            "INSERT OR REPLACE INTO $STAGING_TABLE (" +
                "id_anfr, dept, operator, tech, is_active, antennas, antennas_active, antennas_fh" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        )

        db.query(STATIONS_QUERY) { row ->
            val idAnfr = row.getString("id_anfr").orEmpty().trim()
            val dept = DepartmentCodes.fromInsee(row.getString("code_insee"))
            if (idAnfr.isEmpty() || dept == null) return@query

            val operator = row.getString("operator_name").orEmpty().trim().ifEmpty { "INCONNU" }
            val techMask = row.getInt("tech_mask")
            val hasActive = row.getInt("has_active")
            val statut = row.getString("statut").orEmpty()
            val antennas = antennasFromDetails(row.getString("details_frequences"))

            val declaredTechs = techKeysFromMask(techMask) + antennas.techs
            val stationActive = antennas.activeTechs.isNotEmpty() || hasActive == 1 || isActiveStatus(statut)
            val activeTechs = if (antennas.activeTechs.isNotEmpty()) {
                antennas.activeTechs
            } else if (stationActive) {
                techKeysFromMask(techMask)
            } else {
                emptySet()
            }

            inserter.add(
                listOf(
                    idAnfr,
                    dept,
                    operator,
                    ITEM_ALL,
                    if (stationActive) 1 else 0,
                    antennas.distinctCount(),
                    antennas.distinctActiveCount(),
                    antennas.fh.size,
                ),
            )
            declaredTechs.forEach { tech ->
                inserter.add(
                    listOf(
                        idAnfr,
                        dept,
                        operator,
                        tech,
                        if (tech in activeTechs) 1 else 0,
                        antennas.byTech[tech]?.size ?: 0,
                        antennas.activeByTech[tech]?.size ?: 0,
                        0,
                    ),
                )
            }
        }
        inserter.flush()
    }

    private fun departmentRows(
        db: SqlDatabase,
        reference: Map<String, DepartmentReferenceRow>,
        populationYear: String?,
    ): List<List<Any?>> {
        val rows = ArrayList<List<Any?>>()
        db.query(DEPARTMENT_TOTALS_QUERY) { row ->
            val deptCode = row.getString("dept_code").orEmpty()
            if (deptCode.isEmpty()) return@query

            val supports = row.getInt("supports")
            val stations = row.getInt("stations")
            val antennas = row.getInt("antennas")
            val info = reference[deptCode]
            val areaKm2 = info?.areaKm2
            val population = info?.population
            val population1k = population?.takeIf { it > 0 }?.let { it / 1000.0 }

            rows.add(
                listOf(
                    deptCode,
                    info?.name,
                    info?.regionCode,
                    areaKm2,
                    population,
                    info?.populationYear ?: populationYear,
                    supports,
                    row.getInt("supports_active"),
                    stations,
                    row.getInt("stations_active"),
                    antennas,
                    row.getInt("antennas_active"),
                    row.getInt("antennas_fh"),
                    ratio(stations, supports),
                    ratio(antennas, stations),
                    ratio(supports, areaKm2),
                    ratio(stations, areaKm2),
                    ratio(antennas, areaKm2),
                    ratio(supports, population1k),
                    ratio(stations, population1k),
                    ratio(antennas, population1k),
                    ratio(population, supports),
                    ratio(population, stations),
                    ratio(population, antennas),
                ),
            )
        }
        return rows
    }

    /** Python `_ratio` : arrondi a 1e-6, null si le denominateur manque ou vaut zero. */
    private fun ratio(numerator: Number?, denominator: Number?): Double? {
        val top = numerator?.toDouble() ?: return null
        val bottom = denominator?.toDouble() ?: return null
        if (bottom == 0.0) return null
        return Math.round(top / bottom * 1_000_000.0) / 1_000_000.0
    }

    private fun techKeysFromMask(mask: Int): Set<String> =
        TECH_BITS.filter { (_, bit) -> mask and bit != 0 }.keys

    /** Antennes d'une station, lues dans `details_frequences` ; port de `antennas_from_details`. */
    internal fun antennasFromDetails(detailsValue: String?): StationAntennas {
        val result = StationAntennas()
        val details = FrequencyDetailsCodec.decode(detailsValue).orEmpty()
        if (details.isEmpty()) return result

        for (rawLine in details.split("\n")) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val parts = line.split("|").map { it.trim() }
            val system = parts.getOrElse(0) { "" }
            val status = parts.getOrElse(1) { "" }
            val active = isActiveStatus(status)
            val aerId = AER_ID_REGEX.find(line)?.groupValues?.getOrNull(1)?.trim().orEmpty()

            if (system.uppercase().contains("FH")) {
                if (aerId.isNotEmpty()) result.fh.add(aerId)
                continue
            }

            val generation = generationFromSystem(system) ?: continue
            result.techs.add(generation)
            if (active) result.activeTechs.add(generation)
            if (aerId.isEmpty()) continue

            result.byTech.getOrPut(generation) { HashSet() }.add(aerId)
            if (active) result.activeByTech.getOrPut(generation) { HashSet() }.add(aerId)
        }
        return result
    }

    private fun generationFromSystem(system: String?): String? {
        val raw = (system ?: "").uppercase()
        return when {
            raw.contains("5G") || raw.contains("NR") -> "5G"
            raw.contains("4G") || raw.contains("LTE") -> "4G"
            raw.contains("3G") || raw.contains("UMTS") -> "3G"
            raw.contains("2G") || raw.contains("GSM") -> "2G"
            else -> null
        }
    }

    private fun isActiveStatus(status: String?): Boolean {
        val normalized = (status ?: "").uppercase()
        return normalized.contains("EN SERVICE") || normalized.contains("TECHNIQUEMENT OP")
    }

    /** Ce que les lignes de detail d'une station apprennent sur ses antennes. */
    internal class StationAntennas {
        val byTech = HashMap<String, MutableSet<String>>()
        val activeByTech = HashMap<String, MutableSet<String>>()
        val techs = HashSet<String>()
        val activeTechs = HashSet<String>()
        val fh = HashSet<String>()

        /** Antennes distinctes hors FH : une antenne 4G+5G ne compte qu'une fois. */
        fun distinctCount(): Int = byTech.values.flatten().toHashSet().size

        fun distinctActiveCount(): Int = activeByTech.values.flatten().toHashSet().size
    }
}
