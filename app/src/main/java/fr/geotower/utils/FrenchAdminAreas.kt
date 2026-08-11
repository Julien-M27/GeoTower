package fr.geotower.utils

import java.text.Normalizer
import java.util.Locale

/**
 * Référentiel des départements et régions, pour la recherche de la carte (« 35 », « Ille-et-Vilaine »,
 * « Bretagne », « dept:29 », « region:Occitanie »).
 *
 * Table **figée dans l'app** et non lue en base : la recherche doit répondre instantanément, hors
 * réseau, et même quand `dept_stat_current` est vide (cas d'une base générée sur l'appareil, qui
 * crée la table sans la remplir). Les codes suivent le même découpage que [DepartmentCodes], donc
 * que `dept_stat_current` : toute divergence ferait pointer la carte sur un département inexistant
 * côté statistiques.
 *
 * Les collectivités d'outre-mer (975, 977, 978, 984, 986 à 988) sont rangées avec les départements :
 * ce sont des zones de recherche comme les autres, et l'ANFR les code de la même façon.
 */
object FrenchAdminAreas {

    enum class Kind { DEPARTMENT, REGION }

    /**
     * Une zone recherchable. [departmentCodes] tient le découpage réel :
     * un seul code pour un département, la liste de ses départements pour une région.
     */
    data class Area(
        val kind: Kind,
        val code: String,
        val name: String,
        val departmentCodes: List<String>
    )

    private data class DeptSpec(
        val code: String,
        val name: String,
        val regionCode: String?,
        val aliases: List<String> = emptyList()
    )

    private data class RegionSpec(
        val code: String,
        val name: String,
        val aliases: List<String> = emptyList()
    )

    private val departments: List<DeptSpec> = listOf(
        DeptSpec("01", "Ain", "84"),
        DeptSpec("02", "Aisne", "32"),
        DeptSpec("03", "Allier", "84"),
        DeptSpec("04", "Alpes-de-Haute-Provence", "93"),
        DeptSpec("05", "Hautes-Alpes", "93"),
        DeptSpec("06", "Alpes-Maritimes", "93"),
        DeptSpec("07", "Ardèche", "84"),
        DeptSpec("08", "Ardennes", "44"),
        DeptSpec("09", "Ariège", "76"),
        DeptSpec("10", "Aube", "44"),
        DeptSpec("11", "Aude", "76"),
        DeptSpec("12", "Aveyron", "76"),
        DeptSpec("13", "Bouches-du-Rhône", "93"),
        DeptSpec("14", "Calvados", "28"),
        DeptSpec("15", "Cantal", "84"),
        DeptSpec("16", "Charente", "75"),
        DeptSpec("17", "Charente-Maritime", "75"),
        DeptSpec("18", "Cher", "24"),
        DeptSpec("19", "Corrèze", "75"),
        DeptSpec("2A", "Corse-du-Sud", "94"),
        DeptSpec("2B", "Haute-Corse", "94"),
        DeptSpec("21", "Côte-d'Or", "27"),
        DeptSpec("22", "Côtes-d'Armor", "53"),
        DeptSpec("23", "Creuse", "75"),
        DeptSpec("24", "Dordogne", "75"),
        DeptSpec("25", "Doubs", "27"),
        DeptSpec("26", "Drôme", "84"),
        DeptSpec("27", "Eure", "28"),
        DeptSpec("28", "Eure-et-Loir", "24"),
        DeptSpec("29", "Finistère", "53"),
        DeptSpec("30", "Gard", "76"),
        DeptSpec("31", "Haute-Garonne", "76"),
        DeptSpec("32", "Gers", "76"),
        DeptSpec("33", "Gironde", "75"),
        DeptSpec("34", "Hérault", "76"),
        DeptSpec("35", "Ille-et-Vilaine", "53"),
        DeptSpec("36", "Indre", "24"),
        DeptSpec("37", "Indre-et-Loire", "24"),
        DeptSpec("38", "Isère", "84"),
        DeptSpec("39", "Jura", "27"),
        DeptSpec("40", "Landes", "75"),
        DeptSpec("41", "Loir-et-Cher", "24"),
        DeptSpec("42", "Loire", "84"),
        DeptSpec("43", "Haute-Loire", "84"),
        DeptSpec("44", "Loire-Atlantique", "52"),
        DeptSpec("45", "Loiret", "24"),
        DeptSpec("46", "Lot", "76"),
        DeptSpec("47", "Lot-et-Garonne", "75"),
        DeptSpec("48", "Lozère", "76"),
        DeptSpec("49", "Maine-et-Loire", "52"),
        DeptSpec("50", "Manche", "28"),
        DeptSpec("51", "Marne", "44"),
        DeptSpec("52", "Haute-Marne", "44"),
        DeptSpec("53", "Mayenne", "52"),
        DeptSpec("54", "Meurthe-et-Moselle", "44"),
        DeptSpec("55", "Meuse", "44"),
        DeptSpec("56", "Morbihan", "53"),
        DeptSpec("57", "Moselle", "44"),
        DeptSpec("58", "Nièvre", "27"),
        DeptSpec("59", "Nord", "32"),
        DeptSpec("60", "Oise", "32"),
        DeptSpec("61", "Orne", "28"),
        DeptSpec("62", "Pas-de-Calais", "32"),
        DeptSpec("63", "Puy-de-Dôme", "84"),
        DeptSpec("64", "Pyrénées-Atlantiques", "75"),
        DeptSpec("65", "Hautes-Pyrénées", "76"),
        DeptSpec("66", "Pyrénées-Orientales", "76"),
        DeptSpec("67", "Bas-Rhin", "44"),
        DeptSpec("68", "Haut-Rhin", "44"),
        DeptSpec("69", "Rhône", "84"),
        DeptSpec("70", "Haute-Saône", "27"),
        DeptSpec("71", "Saône-et-Loire", "27"),
        DeptSpec("72", "Sarthe", "52"),
        DeptSpec("73", "Savoie", "84"),
        DeptSpec("74", "Haute-Savoie", "84"),
        DeptSpec("75", "Paris", "11"),
        DeptSpec("76", "Seine-Maritime", "28"),
        DeptSpec("77", "Seine-et-Marne", "11"),
        DeptSpec("78", "Yvelines", "11"),
        DeptSpec("79", "Deux-Sèvres", "75"),
        DeptSpec("80", "Somme", "32"),
        DeptSpec("81", "Tarn", "76"),
        DeptSpec("82", "Tarn-et-Garonne", "76"),
        DeptSpec("83", "Var", "93"),
        DeptSpec("84", "Vaucluse", "93"),
        DeptSpec("85", "Vendée", "52"),
        DeptSpec("86", "Vienne", "75"),
        DeptSpec("87", "Haute-Vienne", "75"),
        DeptSpec("88", "Vosges", "44"),
        DeptSpec("89", "Yonne", "27"),
        DeptSpec("90", "Territoire de Belfort", "27"),
        DeptSpec("91", "Essonne", "11"),
        DeptSpec("92", "Hauts-de-Seine", "11"),
        DeptSpec("93", "Seine-Saint-Denis", "11"),
        DeptSpec("94", "Val-de-Marne", "11"),
        DeptSpec("95", "Val-d'Oise", "11"),
        DeptSpec("971", "Guadeloupe", "01"),
        DeptSpec("972", "Martinique", "02"),
        DeptSpec("973", "Guyane", "03", listOf("Guyane française")),
        DeptSpec("974", "La Réunion", "04", listOf("Réunion")),
        DeptSpec("975", "Saint-Pierre-et-Miquelon", null),
        DeptSpec("976", "Mayotte", "06"),
        DeptSpec("977", "Saint-Barthélemy", null),
        DeptSpec("978", "Saint-Martin", null),
        DeptSpec("984", "Terres australes et antarctiques françaises", null, listOf("TAAF")),
        DeptSpec("986", "Wallis-et-Futuna", null),
        DeptSpec("987", "Polynésie française", null),
        DeptSpec("988", "Nouvelle-Calédonie", null)
    )

    private val regions: List<RegionSpec> = listOf(
        RegionSpec("84", "Auvergne-Rhône-Alpes", listOf("ARA")),
        RegionSpec("27", "Bourgogne-Franche-Comté"),
        RegionSpec("53", "Bretagne"),
        RegionSpec("24", "Centre-Val de Loire"),
        RegionSpec("94", "Corse"),
        RegionSpec("44", "Grand Est"),
        RegionSpec("32", "Hauts-de-France"),
        RegionSpec("11", "Île-de-France", listOf("IDF")),
        RegionSpec("28", "Normandie"),
        RegionSpec("75", "Nouvelle-Aquitaine"),
        RegionSpec("76", "Occitanie"),
        RegionSpec("52", "Pays de la Loire"),
        RegionSpec("93", "Provence-Alpes-Côte d'Azur", listOf("PACA")),
        RegionSpec("01", "Guadeloupe"),
        RegionSpec("02", "Martinique"),
        RegionSpec("03", "Guyane"),
        RegionSpec("04", "La Réunion", listOf("Réunion")),
        RegionSpec("06", "Mayotte")
    )

    private val departmentByCode: Map<String, DeptSpec> by lazy {
        departments.associateBy { it.code }
    }

    private val departmentAreaByNormalizedName: Map<String, Area> by lazy {
        buildMap {
            departments.forEach { spec ->
                val area = departmentArea(spec)
                (listOf(spec.name) + spec.aliases).forEach { label ->
                    val key = normalize(label)
                    if (key !in this) put(key, area)
                }
            }
        }
    }

    private val regionAreaByNormalizedName: Map<String, Area> by lazy {
        buildMap {
            regions.forEach { spec ->
                val area = regionArea(spec)
                (listOf(spec.name) + spec.aliases).forEach { label ->
                    val key = normalize(label)
                    if (key !in this) put(key, area)
                }
            }
        }
    }

    private val regionAreaByCode: Map<String, Area> by lazy {
        regions.associate { spec -> spec.code to regionArea(spec) }
    }

    private fun departmentArea(spec: DeptSpec): Area =
        Area(Kind.DEPARTMENT, spec.code, spec.name, listOf(spec.code))

    private fun regionArea(spec: RegionSpec): Area = Area(
        kind = Kind.REGION,
        code = spec.code,
        name = spec.name,
        departmentCodes = departments.filter { it.regionCode == spec.code }.map { it.code }
    )

    private val departmentQueryPrefixes = setOf("D", "DEP", "DEPT", "DPT", "DEPARTEMENT", "DEPARTMENT")
    private val regionQueryPrefixes = setOf("R", "REG", "REGION")

    private val combiningMarksRegex = Regex("\\p{Mn}+")
    private val nonWordRegex = Regex("[^A-Z0-9]+")
    private val repeatedSpacesRegex = Regex("\\s+")

    /**
     * Accents, tirets et apostrophes gommés : « Côtes-d'Armor », « cotes d armor » et
     * « COTES D'ARMOR » doivent tomber sur la même clé.
     */
    private fun normalize(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(combiningMarksRegex, "")
            .uppercase(Locale.ROOT)
            .replace(nonWordRegex, " ")
            .trim()
            .replace(repeatedSpacesRegex, " ")
    }

    /**
     * Résout une saisie de la barre de recherche en zone administrative, ou null si ce n'en est pas une.
     *
     * Formes acceptées :
     *  - préfixée : `dept:35`, `département:Finistère`, `region:Occitanie`, `r:76` ;
     *  - code département nu : `35`, `2A`, `974` (jamais un code région, qui entrerait en collision
     *    avec les départements 01 à 95 — « 84 » doit rester le Vaucluse, pas Auvergne-Rhône-Alpes) ;
     *  - nom complet nu : `Ille-et-Vilaine`, `Bretagne`, `paca`.
     */
    fun match(query: String): Area? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null

        val splitIndex = trimmed.indexOf(':')
        if (splitIndex > 0) {
            val prefix = normalize(trimmed.substring(0, splitIndex)).replace(" ", "")
            val value = trimmed.substring(splitIndex + 1).trim()
            if (value.isEmpty()) return null

            return when (prefix) {
                in departmentQueryPrefixes -> matchDepartment(value)
                in regionQueryPrefixes -> matchRegion(value, allowCode = true)
                else -> null
            }
        }

        return matchDepartment(trimmed) ?: matchRegion(trimmed, allowCode = false)
    }

    private fun matchDepartment(value: String): Area? {
        departmentCode(value)?.let { code ->
            departmentByCode[code]?.let { return departmentArea(it) }
        }
        return departmentAreaByNormalizedName[normalize(value)]
    }

    private fun matchRegion(value: String, allowCode: Boolean): Area? {
        if (allowCode) {
            val code = value.trim().padStart(2, '0')
            regionAreaByCode[code]?.let { return it }
        }
        return regionAreaByNormalizedName[normalize(value)]
    }

    /**
     * Code département d'une saisie brute : `35`, `2a`, `974`, et `5` complété en `05`.
     * Renvoie null dès que la forme n'est pas celle d'un code (une saisie de 4 chiffres est un
     * identifiant de site, pas un département).
     */
    private fun departmentCode(value: String): String? {
        val raw = value.trim().uppercase(Locale.ROOT)
        if (raw.isEmpty() || raw.length > 3) return null

        val candidate = if (raw.length == 1 && raw[0].isDigit()) "0$raw" else raw
        return candidate.takeIf { departmentByCode.containsKey(it) }
    }

    /** Nom du département, pour l'affichage. */
    fun departmentName(code: String): String? = departmentByCode[code]?.name

    /**
     * Département d'un code INSEE communal : « 35238 » → « 35 », « 2A004 » → « 2A », « 97411 » →
     * « 974 ». L'outre-mer se code sur trois chiffres, on essaie donc cette longueur d'abord — sans
     * quoi « 974 » serait lu « 97 », qui n'est pas un département.
     */
    fun departmentCodeForInsee(codeInsee: String): String? {
        val raw = codeInsee.trim().uppercase(Locale.ROOT)
        if (raw.length < 2) return null
        return raw.take(3).takeIf(departmentByCode::containsKey)
            ?: raw.take(2).takeIf(departmentByCode::containsKey)
    }

    /**
     * Zones proposées sous la barre de recherche pendant la frappe.
     *
     * Contrairement à [match], qui exige une saisie complète, on répond ici dès les premières
     * lettres : d'abord ce que [match] reconnaîtrait tel quel (« 35 » → Ille-et-Vilaine), puis les
     * noms qui commencent par la saisie, puis ceux qui la contiennent (« vilaine » doit sortir
     * Ille-et-Vilaine). Départements avant régions à pertinence égale : c'est la maille que l'on
     * cherche le plus souvent.
     */
    fun suggest(query: String, limit: Int): List<Area> {
        if (limit <= 0) return emptyList()
        val normalized = normalize(query)
        if (normalized.isBlank()) return emptyList()

        val results = LinkedHashMap<String, Area>()
        fun keep(area: Area) {
            val key = "${area.kind}:${area.code}"
            if (key !in results) results[key] = area
        }

        match(query)?.let(::keep)

        suggestionLabels.filter { (_, labels) -> labels.any { it.startsWith(normalized) } }
            .forEach { (area, _) -> keep(area) }
        // Codes : départements seulement. Les codes de région recouvrent ceux des départements
        // (« 11 » est l'Aude autant qu'Île-de-France), c'est déjà pourquoi [match] les ignore nus.
        departments.filter { it.code.startsWith(normalized) }
            .forEach { keep(departmentArea(it)) }
        suggestionLabels.filter { (_, labels) -> labels.any { it.contains(normalized) } }
            .forEach { (area, _) -> keep(area) }

        return results.values.take(limit)
    }

    /**
     * Libellés normalisés de chaque zone, préparés une fois pour toutes : [suggest] tourne à chaque
     * frappe et ne peut pas se permettre de renormaliser cent quarante intitulés à chaque fois.
     * Départements avant régions — l'ordre de cette liste est celui des suggestions.
     */
    private val suggestionLabels: List<Pair<Area, List<String>>> by lazy {
        departments.map { spec ->
            departmentArea(spec) to (listOf(spec.name) + spec.aliases).map(::normalize)
        } + regions.map { spec ->
            regionArea(spec) to (listOf(spec.name) + spec.aliases).map(::normalize)
        }
    }

    /**
     * Bornes `[début, fin[` des codes INSEE communaux du département, à comparer telles quelles en SQL.
     *
     * C'est volontairement une **plage** et non un `LIKE '35%'` : SQLite n'utilise un index avec LIKE
     * que si la colonne est en collation NOCASE, ce qui n'est pas le cas de `localisation.code_insee`
     * — la comparaison de plage, elle, exploite `idx_localisation_insee`.
     */
    fun inseeRange(departmentCode: String): Pair<String, String> {
        val end = departmentCode.dropLast(1) + (departmentCode.last() + 1)
        return departmentCode to end
    }
}
