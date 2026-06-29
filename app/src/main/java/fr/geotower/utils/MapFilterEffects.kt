package fr.geotower.utils

import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.SiteHsEntity
import fr.geotower.data.models.physicalSiteKey

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

/**
 * Propagation « zone blanche » pour UN site partagé (toutes les antennes reçues sont
 * considérées comme appartenant au même support).
 *
 * Sur un site qui comporte au moins une antenne en zone blanche ([LocalisationEntity.isZb] == 1)
 * ET au moins un opérateur déclaré HS (présent dans [declaredHs]), on renvoie des entrées HS
 * synthétiques ([SiteHsEntity.isPotential] == true) pour les autres opérateurs ZB du site qui
 * n'ont pas leur propre déclaration. Les opérateurs déjà déclarés HS sont laissés tels quels.
 */
fun zbPotentialOutagesForSite(
    siteAntennas: List<LocalisationEntity>,
    declaredHs: Collection<SiteHsEntity>
): List<SiteHsEntity> {
    val declaredIds = declaredHs.asSequence()
        .map { normalizedAnfrId(it.idAnfr) }
        .filter { it.isNotBlank() }
        .toSet()
    if (declaredIds.isEmpty()) return emptyList()

    val zbAntennas = siteAntennas.filter { it.isZb == 1 }
    if (zbAntennas.isEmpty()) return emptyList() // site pas en zone blanche → aucune propagation

    val hasDeclaredOutageOnSite = siteAntennas.any { normalizedAnfrId(it.idAnfr) in declaredIds }
    if (!hasDeclaredOutageOnSite) return emptyList()

    val seen = mutableSetOf<String>()
    return zbAntennas.mapNotNull { antenna ->
        val id = normalizedAnfrId(antenna.idAnfr)
        when {
            id.isBlank() -> null
            id in declaredIds -> null // garde sa panne réellement déclarée
            !seen.add(id) -> null     // déduplication
            else -> SiteHsEntity(
                idAnfr = antenna.idAnfr,
                operateur = antenna.operateur.orEmpty(),
                latitude = antenna.latitude,
                longitude = antenna.longitude,
                isPotential = true
            )
        }
    }
}

/**
 * Variante multi-sites : regroupe d'abord les antennes par site physique
 * ([LocalisationEntity.physicalSiteKey]) puis applique [zbPotentialOutagesForSite] à chaque
 * groupe. Pratique pour la carte, qui reçoit toutes les antennes visibles d'un coup.
 */
fun zbPotentialOutages(
    antennas: List<LocalisationEntity>,
    declaredHs: Collection<SiteHsEntity>
): List<SiteHsEntity> {
    if (antennas.isEmpty() || declaredHs.isEmpty()) return emptyList()
    return antennas
        .groupBy { it.physicalSiteKey() }
        .flatMap { (_, group) -> zbPotentialOutagesForSite(group, declaredHs) }
}
