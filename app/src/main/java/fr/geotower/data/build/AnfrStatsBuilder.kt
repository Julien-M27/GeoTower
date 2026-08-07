package fr.geotower.data.build

import fr.geotower.data.models.FrequencyDetailsCodec
import fr.geotower.data.models.RadioFilterMasks
import java.text.Normalizer

/**
 * Peuple `radio_stat_current` a partir de la base deja construite. Port fidele de
 * `populate_current_stats` (docs/server/fr_anfr_stats.py). Cette table est **obligatoire** :
 * [fr.geotower.data.db.GeoTowerDatabaseValidator] la refuse si elle est vide.
 *
 * `radio_stat_weekly` (series temporelles hebdomadaires) n'est pas peuplee ici : elle
 * necessite plusieurs CSV hebdomadaires historiques, non telecharges sur l'appareil, et
 * n'est pas exigee par le validateur.
 */
object AnfrStatsBuilder {

    private const val CATEGORY_SUPPORT = "support"
    private const val CATEGORY_TECH = "tech"
    private const val CATEGORY_BAND = "band"
    private const val ITEM_ALL = "ALL"
    private const val OVERSEAS_SUFFIX = "_OVERSEAS"

    private val OVERSEAS_MARKERS = listOf(
        "FREE CARAIBE", "FREE CARAIBES", "OUTREMER TELECOM", "OUTREMER", "SFR CARAIBE",
        "UTS CARAIBE", "DAUPHIN TELECOM", "DIGICEL", "SRR", "TELCO OI", "TELCO", "ONLY",
        "ZEOP", "MAORE", "SPM TELECOM", "GLOBALTEL", "OPT NOUVELLE", "OPT NC", "ONATI",
        "VINI", "PMT", "VODAFONE", "VITI", "SPT",
    )

    private val TECH_BITS = linkedMapOf(
        "2G" to RadioFilterMasks.TECH_2G,
        "3G" to RadioFilterMasks.TECH_3G,
        "4G" to RadioFilterMasks.TECH_4G,
        "5G" to RadioFilterMasks.TECH_5G,
        "FH" to RadioFilterMasks.TECH_FH,
    )

    private val BAND_BITS = linkedMapOf(
        "2G|900" to RadioFilterMasks.BAND_2G_900,
        "2G|1800" to RadioFilterMasks.BAND_2G_1800,
        "3G|900" to RadioFilterMasks.BAND_3G_900,
        "3G|2100" to RadioFilterMasks.BAND_3G_2100,
        "4G|700" to RadioFilterMasks.BAND_4G_700,
        "4G|800" to RadioFilterMasks.BAND_4G_800,
        "4G|900" to RadioFilterMasks.BAND_4G_900,
        "4G|1800" to RadioFilterMasks.BAND_4G_1800,
        "4G|2100" to RadioFilterMasks.BAND_4G_2100,
        "4G|2600" to RadioFilterMasks.BAND_4G_2600,
        "5G|700" to RadioFilterMasks.BAND_5G_700,
        "5G|2100" to RadioFilterMasks.BAND_5G_2100,
        "5G|3500" to RadioFilterMasks.BAND_5G_3500,
        "5G|26000" to RadioFilterMasks.BAND_5G_26000,
        "FH" to RadioFilterMasks.BAND_FH,
        "5G|1400" to RadioFilterMasks.BAND_5G_1400,
        "5G|4200" to RadioFilterMasks.BAND_5G_4200,
    )

    private val BAND_LABELS = mapOf(
        "2G|900" to "2G 900 MHz",
        "2G|1800" to "2G 1800 MHz",
        "3G|900" to "3G 900 MHz",
        "3G|2100" to "3G 2100 MHz",
        "4G|700" to "4G 700 MHz",
        "4G|800" to "4G 800 MHz",
        "4G|900" to "4G 900 MHz",
        "4G|1800" to "4G 1800 MHz",
        "4G|2100" to "4G 2100 MHz",
        "4G|2600" to "4G 2600 MHz",
        "5G|700" to "5G 700 MHz",
        "5G|1400" to "5G 1400 MHz (exp)",
        "5G|2100" to "5G 2100 MHz",
        "5G|3500" to "5G 3500 MHz",
        "5G|4200" to "5G 4200 MHz (exp)",
        "5G|26000" to "5G 26 GHz (exp)",
        "FH" to "FH",
    )

    private val NUMBER_REGEX = Regex("""\d{2,5}""")

    /**
     * PERF : pas de `DISTINCT`. `support` a pour cle primaire `(id_anfr, id_support)` et les quatre
     * jointures qui suivent sont toutes 1:1 sur une cle primaire (`localisation.id_anfr`,
     * `ref_operateur.id`, `technique.id_anfr`, `ref_statut.id`) : chaque ligne de `support` produit
     * donc exactement une ligne de resultat et le `DISTINCT` ne pouvait rien eliminer. Il forcait
     * en revanche SQLite a trier toutes les lignes dans un B-tree temporaire — sur disque, puisque
     * le build tourne en `temp_store = FILE` — en y transportant le blob `details_frequences`.
     */
    private val CURRENT_STATS_QUERY = """
        SELECT
            s.id_anfr,
            s.id_support,
            UPPER(TRIM(o.libelle)) AS operator_name,
            l.code_insee,
            COALESCE(l.tech_mask, 0) AS tech_mask,
            COALESCE(l.band_mask, 0) AS band_mask,
            COALESCE(st.libelle, '') AS statut,
            COALESCE(t.has_active, 0) AS has_active,
            t.details_frequences AS details_frequences
        FROM support s
        INNER JOIN localisation l ON s.id_anfr = l.id_anfr
        INNER JOIN ref_operateur o ON l.operateur_id = o.id
        LEFT JOIN technique t ON l.id_anfr = t.id_anfr
        LEFT JOIN ref_statut st ON t.statut_id = st.id
    """.trimIndent()

    /**
     * Caches des deux helpers bases sur [normalizedText] : la decomposition NFKD est couteuse et
     * ces fonctions sont appelees une fois par support pour [statsOperatorName], et une fois par
     * ligne de detail — soit plusieurs millions de fois — pour [isActiveStatus], alors que les
     * valeurs distinctes se comptent sur les doigts. Vides a chaque build.
     */
    private val activeStatusCache = HashMap<String, Boolean>()
    private val overseasOperatorCache = HashMap<String, String>()

    /**
     * Table de staging des couples (operateur x categorie x item x support). Le comptage distinct
     * est delegue a SQLite au lieu d'etre fait en RAM.
     *
     * MEMOIRE (mesure sur Galaxy A52s, 2026-08-06) : la version precedente gardait deux
     * `HashMap<Triple, MutableSet<String>>` ou chaque support etait insere dans 5 a 20 ensembles,
     * soit 1 a 3 millions d'entrees vivantes **en meme temps**. C'etait le pic memoire de toute la
     * generation : 199 Mo de tas sur un plafond de 256, atteints pendant COMPUTING_STATS, une phase
     * qui ne represente pourtant que 10 % de la duree. Meme methode que
     * [DepartmentStatsBuilder] : une ligne par couple sur disque, agregats en SQL.
     */
    const val STAGING_TABLE = "stg_stat_pair"

    private fun createStaging(prefix: String) = "CREATE TABLE IF NOT EXISTS $prefix$STAGING_TABLE (" +
        "operator_name TEXT NOT NULL, category TEXT NOT NULL, item_key TEXT NOT NULL, " +
        "support_key TEXT NOT NULL, in_total INTEGER NOT NULL, in_active INTEGER NOT NULL)"

    /**
     * Scan **ordonne** des couples, dedoublonnes en flux cote Kotlin (cf. [countGroups]).
     *
     * PERF (regression mesuree puis corrigee, 2026-08-06) : la premiere version demandait
     * `COUNT(DISTINCT ...) GROUP BY ...` a SQLite. Sur un build mobile seul, ce n'etait payable
     * (+26 s) ; sur un build complet, ou quatre bases sont ouvertes en meme temps (mobile + son
     * staging, radio + le sien) et se disputent le cache de pages, les tables temporaires que
     * SQLite alloue pour chaque `DISTINCT` faisaient passer la phase de **2 min 23 s a 12 min 45 s**.
     *
     * L'ordre du scan vient de l'index couvrant cree apres le chargement : aucun tri, aucune table
     * temporaire, memoire constante. Les lignes d'un meme couple etant adjacentes, le dedoublonnage
     * se fait en comparant a la precedente — exactement ce que faisaient les ensembles d'origine.
     */
    private val AGGREGATE_QUERY = """
        SELECT operator_name, category, item_key, support_key, in_total, in_active
        FROM $STAGING_TABLE
        ORDER BY operator_name, category, item_key, support_key
    """.trimIndent()

    /** Insertion par lots (borne la RAM a un lot), meme patron que les autres builders. */
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

    fun populateCurrentStats(db: SqlDatabase): Int {
        activeStatusCache.clear()
        overseasOperatorCache.clear()
        db.execSql("DROP TABLE IF EXISTS ${db.staging(STAGING_TABLE)}")
        db.execSql(createStaging(db.stagingPrefix))
        val pairs = BatchInserter(db, "INSERT INTO $STAGING_TABLE VALUES (?, ?, ?, ?, ?, ?)")

        db.query(CURRENT_STATS_QUERY) { row ->
            val idAnfr = row.getString("id_anfr").orEmpty()
            val idSupport = row.getString("id_support")
            val rawOperator = row.getString("operator_name").orEmpty()
            val codeInsee = row.getString("code_insee")
            val techMask = row.getInt("tech_mask")
            val bandMask = row.getInt("band_mask")
            val statut = row.getString("statut").orEmpty()
            val hasActive = row.getInt("has_active")
            val details = row.getString("details_frequences")

            val operatorName = statsOperatorName(rawOperator, codeInsee)
            val supportKey = (idSupport?.takeIf { it.isNotEmpty() } ?: idAnfr).trim()
            val techKeys = techKeysFromMask(techMask)
            val bandKeys = bandKeysFromMask(bandMask)
            val (detailedTech, detailedBand) = activeRadioKeysFromDetails(details)
            val hasDetailedActive = detailedTech.isNotEmpty() || detailedBand.isNotEmpty()
            val isActiveSupport = hasDetailedActive || hasActive == 1 || isActiveStatus(statut)
            val activeTech = when {
                hasDetailedActive -> detailedTech
                isActiveSupport -> techKeys
                else -> emptySet()
            }
            val activeBand = when {
                hasDetailedActive -> detailedBand
                isActiveSupport -> bandKeys
                else -> emptySet()
            }

            addSupport(pairs, operatorName, supportKey, techKeys, bandKeys, activeTech, activeBand, isActiveSupport)
        }
        pairs.flush()
        // Index cree APRES le chargement en masse, et **couvrant** : le scan ordonne se fait
        // entierement dans l'index (aucun acces au rowid pour lire les deux drapeaux), donc sans
        // tri ni table temporaire.
        db.execSql(
            "CREATE INDEX IF NOT EXISTS ${db.stagingPrefix}ix_$STAGING_TABLE " +
                "ON $STAGING_TABLE(operator_name, category, item_key, support_key, in_total, in_active)",
        )

        // Les agregats (quelques dizaines de lignes) sont collectes avant de liberer le staging.
        val rows = countGroups(db)
        db.execSql("DROP TABLE IF EXISTS ${db.staging(STAGING_TABLE)}")

        db.execSql("DELETE FROM radio_stat_current")
        db.insertBatch(
            "INSERT INTO radio_stat_current (operator_name, category, item_key, label, total_count, active_count) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
            rows,
        )
        return rows.size
    }

    /**
     * Compte, en un seul scan ordonne et a memoire constante, les supports **distincts** de chaque
     * couple (operateur, categorie, item).
     *
     * Les lignes arrivant triees, celles d'un meme support sont adjacentes : leurs drapeaux sont
     * fusionnes par OU (un support compte comme actif des qu'une de ses stations l'est, comme le
     * faisaient les ensembles d'origine), puis le support n'est compte qu'une fois.
     */
    private fun countGroups(db: SqlDatabase): List<List<Any?>> {
        val rows = ArrayList<List<Any?>>()
        var operator: String? = null
        var category = ""
        var itemKey = ""
        var supportKey: String? = null
        var inTotal = false
        var inActive = false
        var total = 0
        var active = 0

        fun closeSupport() {
            if (supportKey != null) {
                if (inTotal) total++
                if (inActive) active++
            }
            supportKey = null
            inTotal = false
            inActive = false
        }

        fun closeGroup() {
            closeSupport()
            operator?.let {
                rows.add(listOf(it, category, itemKey, labelFor(category, itemKey), total, minOf(active, total)))
            }
            total = 0
            active = 0
        }

        db.query(AGGREGATE_QUERY) { row ->
            val rowOperator = row.getString("operator_name").orEmpty()
            val rowCategory = row.getString("category").orEmpty()
            val rowItem = row.getString("item_key").orEmpty()
            val rowSupport = row.getString("support_key").orEmpty()
            if (rowOperator != operator || rowCategory != category || rowItem != itemKey) {
                closeGroup()
                operator = rowOperator
                category = rowCategory
                itemKey = rowItem
            } else if (rowSupport != supportKey) {
                closeSupport()
            }
            supportKey = rowSupport
            inTotal = inTotal || row.getInt("in_total") == 1
            inActive = inActive || row.getInt("in_active") == 1
        }
        closeGroup()
        return rows
    }

    /**
     * Emet les couples du support courant. Une cle declaree ET active donne UNE ligne portant les
     * deux drapeaux ; une cle active absente des masques (venue du blob de details) donne une ligne
     * `in_total = 0`, ce qui reproduit exactement les deux ensembles independants de la version RAM.
     */
    private fun addSupport(
        pairs: BatchInserter,
        operatorName: String,
        supportKey: String,
        techKeys: Set<String>,
        bandKeys: Set<String>,
        activeTechKeys: Set<String>,
        activeBandKeys: Set<String>,
        isActiveSupport: Boolean,
    ) {
        if (operatorName.isEmpty() || supportKey.isEmpty()) return
        add(pairs, operatorName, CATEGORY_SUPPORT, ITEM_ALL, supportKey, inTotal = true, inActive = isActiveSupport)
        techKeys.forEach {
            add(pairs, operatorName, CATEGORY_TECH, it, supportKey, inTotal = true, inActive = it in activeTechKeys)
        }
        bandKeys.forEach {
            add(pairs, operatorName, CATEGORY_BAND, it, supportKey, inTotal = true, inActive = it in activeBandKeys)
        }
        activeTechKeys.forEach {
            if (it !in techKeys) {
                add(pairs, operatorName, CATEGORY_TECH, it, supportKey, inTotal = false, inActive = true)
            }
        }
        activeBandKeys.forEach {
            if (it !in bandKeys) {
                add(pairs, operatorName, CATEGORY_BAND, it, supportKey, inTotal = false, inActive = true)
            }
        }
    }

    private fun add(
        pairs: BatchInserter,
        operatorName: String,
        category: String,
        itemKey: String,
        supportKey: String,
        inTotal: Boolean,
        inActive: Boolean,
    ) {
        pairs.add(
            listOf(operatorName, category, itemKey, supportKey, if (inTotal) 1 else 0, if (inActive) 1 else 0),
        )
    }

    private fun labelFor(category: String, itemKey: String): String = when (category) {
        CATEGORY_SUPPORT -> "Supports"
        CATEGORY_TECH -> itemKey
        CATEGORY_BAND -> BAND_LABELS[itemKey] ?: itemKey
        else -> itemKey
    }

    private fun techKeysFromMask(mask: Int): Set<String> =
        TECH_BITS.filter { (key, bit) -> mask and bit != 0 && key != "FH" }.keys

    private fun bandKeysFromMask(mask: Int): Set<String> =
        BAND_BITS.filter { (key, bit) -> mask and bit != 0 && key != "FH" }.keys

    private fun generationFromSystem(system: String?, generation: String? = null): String? {
        val gen = (generation ?: "").trim().uppercase()
        if (gen in setOf("2G", "3G", "4G", "5G")) return gen
        val raw = (system ?: "").uppercase()
        return when {
            raw.contains("5G") || raw.contains("NR") -> "5G"
            raw.contains("4G") || raw.contains("LTE") -> "4G"
            raw.contains("3G") || raw.contains("UMTS") -> "3G"
            raw.contains("2G") || raw.contains("GSM") -> "2G"
            else -> null
        }
    }

    private fun bandKeysFromSystem(system: String?, generation: String?): Set<String> {
        val gen = generationFromSystem(system, generation) ?: return emptySet()
        val raw = (system ?: "").uppercase().replace(",", ".")
        val candidates = HashSet<String>()
        for (match in NUMBER_REGEX.findAll(raw)) {
            val number = match.value.toInt()
            if (number == 26 && gen == "5G") candidates.add("5G|26000") else candidates.add("$gen|$number")
        }
        return candidates.filter { BAND_BITS.containsKey(it) }.toSet()
    }

    private fun activeRadioKeysFromDetails(detailsValue: String?): Pair<Set<String>, Set<String>> {
        val details = FrequencyDetailsCodec.decode(detailsValue).orEmpty()
        val techKeys = HashSet<String>()
        val bandKeys = HashSet<String>()
        for (rawLine in details.split("\n")) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val parts = line.split("|").map { it.trim() }
            val rawFrequency = parts.getOrElse(0) { "" }
            val status = parts.getOrElse(1) { "" }
            if (isActiveStatus(status)) {
                val gen = generationFromSystem(rawFrequency)
                if (gen != null) {
                    techKeys.add(gen)
                    bandKeys.addAll(bandKeysFromSystem(rawFrequency, gen))
                }
            }
        }
        return techKeys to bandKeys
    }

    private fun statsOperatorName(operatorName: String, codeInsee: String?): String {
        if (!isOverseasCodeInsee(codeInsee)) return operatorName
        return overseasOperatorCache.getOrPut(operatorName) { overseasOperatorName(operatorName) }
    }

    private fun overseasOperatorName(operatorName: String): String {
        val normalized = normalizedText(operatorName).uppercase()
        if (OVERSEAS_MARKERS.any { normalized.contains(it) }) return operatorName
        return when {
            normalized.contains("ORANGE") -> "ORANGE$OVERSEAS_SUFFIX"
            normalized in setOf("SFR", "SFR MAYOTTE", "SOCIETE FRANCAISE DU RADIOTELEPHONE") -> "SFR$OVERSEAS_SUFFIX"
            normalized.contains("BOUYGUES") || normalized.contains("BYTEL") -> "BOUYGUES$OVERSEAS_SUFFIX"
            normalized in setOf("FREE", "FREE MOBILE") -> "FREE$OVERSEAS_SUFFIX"
            else -> operatorName
        }
    }

    private fun isOverseasCodeInsee(value: String?): Boolean {
        val code = (value ?: "").trim()
        return code.startsWith("97") || code.startsWith("98")
    }

    /** Python `is_active_status` (version stats, sur texte normalise sans accents). */
    private fun isActiveStatus(status: String?): Boolean = activeStatusCache.getOrPut(status ?: "") {
        val normalized = normalizedText(status)
        normalized.contains("en service") || normalized.contains("techniquement operationnel")
    }

    /** Python `normalized_text` : trim + minuscules + suppression des accents (NFKD). */
    private fun normalizedText(value: String?): String {
        val text = (value ?: "").trim().lowercase()
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD)
        return buildString {
            for (ch in decomposed) {
                val category = ch.category
                if (category != CharCategory.NON_SPACING_MARK &&
                    category != CharCategory.COMBINING_SPACING_MARK &&
                    category != CharCategory.ENCLOSING_MARK
                ) {
                    append(ch)
                }
            }
        }
    }
}
