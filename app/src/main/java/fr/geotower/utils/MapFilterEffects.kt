package fr.geotower.utils

import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.SiteHsEntity

private const val HS_OPERATOR_WILDCARD = "*"

fun combineOperatorKeyFilters(vararg filters: Set<String>?): Set<String>? {
    return filters
        .filterNotNull()
        .reduceOrNull { activeKeys, filterKeys -> activeKeys intersect filterKeys }
}

fun activeOperatorKeysForSiteStatusFilter(
    antennas: List<LocalisationEntity>,
    sitesHs: Collection<SiteHsEntity>,
    showSitesInService: Boolean,
    showSitesOutOfService: Boolean
): Set<String>? {
    if (showSitesInService && showSitesOutOfService) return null

    val hsOperatorMap = buildHsOperatorMap(sitesHs)
    return antennas
        .flatMap { antenna ->
            OperatorColors.keysFor(antenna.operateur).filter { operatorKey ->
                val isHs = isOperatorDeclaredHs(antenna, operatorKey, hsOperatorMap)
                if (isHs) showSitesOutOfService else showSitesInService
            }
        }
        .toSet()
}

private fun buildHsOperatorMap(sitesHs: Collection<SiteHsEntity>): Map<String, Set<String>> {
    val result = mutableMapOf<String, MutableSet<String>>()

    sitesHs.forEach { hs ->
        val id = normalizedAnfrId(hs.idAnfr)
        if (id.isBlank()) return@forEach

        val parsedOperators = OperatorColors.keysFor(hs.operateur)
        val operators = if (parsedOperators.isEmpty()) {
            listOf(HS_OPERATOR_WILDCARD)
        } else {
            parsedOperators
        }
        result.getOrPut(id) { mutableSetOf() }.addAll(operators)
    }

    return result
}

private fun isOperatorDeclaredHs(
    antenna: LocalisationEntity,
    operatorKey: String,
    hsOperatorMap: Map<String, Set<String>>
): Boolean {
    val hsOperators = hsOperatorMap[normalizedAnfrId(antenna.idAnfr)] ?: return false
    return HS_OPERATOR_WILDCARD in hsOperators || operatorKey in hsOperators
}

private fun normalizedAnfrId(value: String): String {
    val trimmed = value.trim()
    return trimmed.toLongOrNull()?.toString() ?: trimmed
}
