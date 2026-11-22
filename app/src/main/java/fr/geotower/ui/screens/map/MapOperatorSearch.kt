package fr.geotower.ui.screens.map

import fr.geotower.utils.OperatorColorSpec
import fr.geotower.utils.OperatorColors
import java.text.Normalizer
import java.util.Locale

private val operatorSearchSplitRegex = Regex("\\s*(?:[,;/\\u2022]|\\bet\\b|\\+|&|\\|)\\s*", RegexOption.IGNORE_CASE)
private val operatorSearchCombiningMarksRegex = Regex("\\p{Mn}+")
private val operatorSearchNonWordRegex = Regex("[^A-Z0-9]+")
private val operatorSearchRepeatedSpacesRegex = Regex("\\s+")
private val operatorSearchPrefixes = setOf("OP", "OPERATEUR", "OPERATOR", "O")
private val operatorSearchAllTokens = setOf("TOUS", "ALL", "TOUT")

internal fun normalizeOperatorSearchToken(value: String): String {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(operatorSearchCombiningMarksRegex, "")
        .uppercase(Locale.ROOT)
        .replace(operatorSearchNonWordRegex, " ")
        .trim()
        .replace(operatorSearchRepeatedSpacesRegex, " ")
}

/**
 * Alias d'opérateurs normalisés une seule fois (indépendants de la requête de recherche) :
 * évite de tout re-normaliser à chaque frappe dans la recherche « op: … ».
 */
private val operatorSearchAliasCandidates: List<Pair<String, String>> by lazy {
    OperatorColors.all
        .flatMap { spec ->
            (listOf(spec.key, spec.label) + spec.aliases).map { rawAlias ->
                spec.key to normalizeOperatorSearchToken(rawAlias)
            }
        }
        .filter { (_, alias) -> alias.isNotBlank() }
        .distinct()
        .sortedByDescending { (_, alias) -> alias.length }
}

private val operatorSearchTermsByKey: Map<String, List<String>> by lazy {
    OperatorColors.all.associate { spec ->
        spec.key to (listOf(spec.key, spec.label) + spec.aliases)
            .map(::normalizeOperatorSearchToken)
            .filter { it.isNotBlank() }
            .distinct()
    }
}

internal fun extractExplicitOperatorQuery(query: String): String? {
    val trimmed = query.trim()
    val splitIndex = trimmed.indexOf(':')
    if (splitIndex <= 0) return null

    val prefix = normalizeOperatorSearchToken(trimmed.substring(0, splitIndex)).replace(" ", "")
    if (prefix !in operatorSearchPrefixes) return null

    return trimmed.substring(splitIndex + 1).trim()
}

private fun matchSingleOperatorTokenKeys(token: String): List<String> {
    val normalizedToken = normalizeOperatorSearchToken(token)
    if (normalizedToken.isBlank()) return emptyList()

    val exact = OperatorColors.all
        .filter { spec ->
            operatorSearchTermsByKey[spec.key].orEmpty().any { it == normalizedToken }
        }
        .map { it.key }
    if (exact.isNotEmpty()) return exact

    val prefix = OperatorColors.all
        .filter { spec ->
            operatorSearchTermsByKey[spec.key].orEmpty().any { it.startsWith(normalizedToken) }
        }
        .map { it.key }
    if (prefix.isNotEmpty()) return prefix

    if (normalizedToken.length < 2) return emptyList()

    return OperatorColors.all
        .filter { spec ->
            operatorSearchTermsByKey[spec.key].orEmpty().any { it.contains(normalizedToken) }
        }
        .map { it.key }
}

internal fun parseOperatorSearchKeys(query: String): List<String> {
    val cleanQuery = extractExplicitOperatorQuery(query) ?: return emptyList()
    if (cleanQuery.isBlank()) return emptyList()

    val normalizedQuery = normalizeOperatorSearchToken(cleanQuery)
    if (cleanQuery == "*" || normalizedQuery in operatorSearchAllTokens) {
        return OperatorColors.orderedKeys
    }
    if (normalizedQuery.isBlank()) return emptyList()

    val separatedTokens = cleanQuery
        .split(operatorSearchSplitRegex)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (separatedTokens.size > 1) {
        val separatedKeys = separatedTokens.map { token ->
            OperatorColors.keyFor(token)?.let(::listOf) ?: matchSingleOperatorTokenKeys(token)
        }
        if (separatedKeys.all { it.isNotEmpty() }) {
            return separatedKeys.flatten().distinct()
        }
    }

    var reducedQuery = " $normalizedQuery "
    val matches = mutableListOf<Pair<Int, String>>()

    operatorSearchAliasCandidates.forEach { (key, alias) ->
        val pattern = Regex("(?<![A-Z0-9])${Regex.escape(alias)}(?![A-Z0-9])")
        pattern.find(reducedQuery)?.let { match ->
            matches += match.range.first to key
            reducedQuery = reducedQuery.replaceRange(match.range, " ".repeat(match.value.length))
        }
    }

    val leftover = reducedQuery.replace(operatorSearchNonWordRegex, "")
    if (matches.isNotEmpty() && leftover.isEmpty()) {
        return matches
            .sortedBy { it.first }
            .map { it.second }
            .distinct()
    }

    return matchSingleOperatorTokenKeys(cleanQuery)
}

private fun activeCountryOperatorSpecs(): List<OperatorColorSpec> {
    return if (fr.geotower.data.db.DatabaseCountryStore.activeCountryState.value == fr.geotower.data.db.DatabaseCountry.CA) {
        OperatorColors.canada
    } else {
        OperatorColors.metro + OperatorColors.overseas
    }
}

internal fun suggestOperatorsForExplicitQuery(query: String): List<OperatorColorSpec> {
    val countrySpecs = activeCountryOperatorSpecs()
    val cleanQuery = extractExplicitOperatorQuery(query) ?: return emptyList()
    if (cleanQuery.isBlank() || cleanQuery == "*") return OperatorColors.all

    val normalizedQuery = normalizeOperatorSearchToken(cleanQuery)
    if (normalizedQuery.isBlank() || normalizedQuery in operatorSearchAllTokens) {
        return OperatorColors.all
    }

    if (cleanQuery.contains(operatorSearchSplitRegex)) {
        val parsedKeys = parseOperatorSearchKeys(query)
        if (parsedKeys.isNotEmpty()) {
            return parsedKeys.mapNotNull(OperatorColors::specForKey)
        }
        val lastToken = cleanQuery.split(operatorSearchSplitRegex).lastOrNull()?.trim().orEmpty()
        if (lastToken.isBlank()) return OperatorColors.all
        return suggestOperatorsForSingleToken(lastToken, countrySpecs)
    }

    return suggestOperatorsForSingleToken(cleanQuery, countrySpecs)
}

private fun suggestOperatorsForSingleToken(
    token: String,
    countrySpecs: List<OperatorColorSpec> = activeCountryOperatorSpecs()
): List<OperatorColorSpec> {
    val normalizedToken = normalizeOperatorSearchToken(token)
    if (normalizedToken.isBlank()) return countrySpecs

    val exact = mutableListOf<OperatorColorSpec>()
    val prefix = mutableListOf<OperatorColorSpec>()
    val wordPrefix = mutableListOf<OperatorColorSpec>()
    val contains = mutableListOf<OperatorColorSpec>()

    countrySpecs.forEach { spec ->
        val terms = operatorSearchTermsByKey[spec.key].orEmpty()
        when {
            terms.any { it == normalizedToken } -> exact += spec
            terms.any { it.startsWith(normalizedToken) } -> prefix += spec
            terms.any { term -> term.split(' ').any { word -> word.startsWith(normalizedToken) } } -> wordPrefix += spec
            normalizedToken.length >= 2 && terms.any { it.contains(normalizedToken) } -> contains += spec
        }
    }

    return (exact + prefix + wordPrefix + contains).distinctBy { it.key }
}

/**
 * Opérateurs dont un alias commence par la saisie, sans exiger le préfixe `op:`.
 *
 * [exactOnly] ne garde que la correspondance pleine : c'est ce qui décide si l'opérateur passe
 * devant les communes. Taper « orange » vise sans doute l'opérateur ; « ora » peut encore être le
 * début d'une commune, l'opérateur attend alors son tour.
 */
internal fun matchOperatorSuggestions(query: String, exactOnly: Boolean): List<OperatorColorSpec> {
    val normalizedQuery = normalizeOperatorSearchToken(query)
    if (normalizedQuery.length < 2) return emptyList()

    return activeCountryOperatorSpecs().filter { spec ->
        operatorSearchTermsByKey[spec.key].orEmpty().any { alias ->
            if (exactOnly) alias == normalizedQuery else alias.startsWith(normalizedQuery)
        }
    }
}
