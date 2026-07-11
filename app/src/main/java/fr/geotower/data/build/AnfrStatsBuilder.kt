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

    private val CURRENT_STATS_QUERY = """
        SELECT DISTINCT
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

    fun populateCurrentStats(db: SqlDatabase): Int {
        val totals = HashMap<Triple<String, String, String>, MutableSet<String>>()
        val actives = HashMap<Triple<String, String, String>, MutableSet<String>>()

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

            addSupport(totals, actives, operatorName, supportKey, techKeys, bandKeys, activeTech, activeBand, isActiveSupport)
        }

        val rows = statsRows(totals, actives)
        db.execSql("DELETE FROM radio_stat_current")
        db.insertBatch(
            "INSERT INTO radio_stat_current (operator_name, category, item_key, label, total_count, active_count) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
            rows,
        )
        return rows.size
    }

    private fun addSupport(
        totals: HashMap<Triple<String, String, String>, MutableSet<String>>,
        actives: HashMap<Triple<String, String, String>, MutableSet<String>>,
        operatorName: String,
        supportKey: String,
        techKeys: Set<String>,
        bandKeys: Set<String>,
        activeTechKeys: Set<String>,
        activeBandKeys: Set<String>,
        isActiveSupport: Boolean,
    ) {
        addKey(totals, operatorName, CATEGORY_SUPPORT, ITEM_ALL, supportKey)
        if (isActiveSupport) addKey(actives, operatorName, CATEGORY_SUPPORT, ITEM_ALL, supportKey)
        techKeys.forEach { addKey(totals, operatorName, CATEGORY_TECH, it, supportKey) }
        bandKeys.forEach { addKey(totals, operatorName, CATEGORY_BAND, it, supportKey) }
        activeTechKeys.forEach { addKey(actives, operatorName, CATEGORY_TECH, it, supportKey) }
        activeBandKeys.forEach { addKey(actives, operatorName, CATEGORY_BAND, it, supportKey) }
    }

    private fun addKey(
        target: HashMap<Triple<String, String, String>, MutableSet<String>>,
        operatorName: String,
        category: String,
        itemKey: String,
        supportKey: String,
    ) {
        if (operatorName.isNotEmpty() && supportKey.isNotEmpty()) {
            target.getOrPut(Triple(operatorName, category, itemKey)) { HashSet() }.add(supportKey)
        }
    }

    private fun statsRows(
        totals: Map<Triple<String, String, String>, Set<String>>,
        actives: Map<Triple<String, String, String>, Set<String>>,
    ): List<List<Any?>> {
        val keys = (totals.keys + actives.keys).toSortedSet(
            compareBy({ it.first }, { it.second }, { it.third }),
        )
        return keys.map { key ->
            val (operatorName, category, itemKey) = key
            val totalCount = totals[key]?.size ?: 0
            val activeCount = minOf(actives[key]?.size ?: 0, totalCount)
            listOf(operatorName, category, itemKey, labelFor(category, itemKey), totalCount, activeCount)
        }
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
    private fun isActiveStatus(status: String?): Boolean {
        val normalized = normalizedText(status)
        return normalized.contains("en service") || normalized.contains("techniquement operationnel")
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
