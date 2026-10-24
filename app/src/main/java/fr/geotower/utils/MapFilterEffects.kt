package fr.geotower.utils

import android.content.Context
import fr.geotower.data.hidden.HiddenSitesStore
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.SiteHsEntity
import fr.geotower.data.models.isDeclaredActive
import fr.geotower.data.models.physicalSiteKey

private const val HS_OPERATOR_WILDCARD = "*"

/**
 * Parse une date ANFR en entier yyyymmdd comparable, ou null si absente/invalide.
 * L'ANFR fournit "JJ/MM/AAAA" (ex: "01/06/2012") ; on accepte aussi l'ISO "AAAA-MM-JJ" par sécurité.
 */
fun parseServiceDateInt(raw: String?): Int? {
    val s = raw?.trim() ?: return null
    if (s.length < 10) return null
    return when {
        (s[2] == '/' || s[2] == '-') && (s[5] == '/' || s[5] == '-') -> {
            val d = s.substring(0, 2).toIntOrNull() ?: return null
            val m = s.substring(3, 5).toIntOrNull() ?: return null
            val y = s.substring(6, 10).toIntOrNull() ?: return null
            if (m in 1..12 && d in 1..31) y * 10000 + m * 100 + d else null
        }
        s[4] == '-' && s[7] == '-' -> {
            val y = s.substring(0, 4).toIntOrNull() ?: return null
            val m = s.substring(5, 7).toIntOrNull() ?: return null
            val d = s.substring(8, 10).toIntOrNull() ?: return null
            if (m in 1..12 && d in 1..31) y * 10000 + m * 100 + d else null
        }
        else -> null
    }
}

fun combineOperatorKeyFilters(vararg filters: Set<String>?): Set<String>? {
    return filters
        .filterNotNull()
        .reduceOrNull { activeKeys, filterKeys -> activeKeys intersect filterKeys }
}

fun isAnyMapFilterActive(
    context: Context? = null,
    antennas: List<LocalisationEntity> = emptyList(),
    selectedOperatorKeys: Set<String> = AppConfig.selectedOperatorKeys.value,
    frequencyFilter: FrequencyFilterSelection = FrequencyFilterSelection.fromMapConfig(),
    showSitesInService: Boolean = AppConfig.showSitesInService.value,
    showSitesOutOfService: Boolean = AppConfig.showSitesOutOfService.value,
    showProjectSites: Boolean = AppConfig.showProjectSites.value,
    hideUndergroundSites: Boolean = AppConfig.hideUndergroundSites.value,
    showOnlyZbSites: Boolean = AppConfig.showOnlyZbSites.value,
    timeSliderActive: Boolean = AppConfig.timeSliderActive.value,
    timeSliderThreshold: Int? = AppConfig.timeSliderThreshold.value
): Boolean {
    val normalizedSelectedKeys = selectedOperatorKeys.map { it.uppercase() }.toSet()
    val operatorFilterActive = !normalizedSelectedKeys.containsAll(OperatorColors.defaultVisibleKeys)
    val frequencyFilterActive = !frequencyFilter.isFullyEnabled
    val statusFilterActive = !(showSitesInService && showSitesOutOfService && showProjectSites)
    val timeSliderFilterActive = timeSliderActive && timeSliderThreshold != null
    val hiddenFilterActive = context != null && (
        if (antennas.isNotEmpty()) {
            antennas.any { antenna ->
                val opKeys = OperatorColors.keysFor(antenna.operateur)
                opKeys.isNotEmpty() && opKeys.any { HiddenSitesStore.isHidden(context, antenna.physicalSiteKey(), it) }
            }
        } else {
            HiddenSitesStore.hasAny(context)
        }
    )

    return operatorFilterActive ||
        frequencyFilterActive ||
        statusFilterActive ||
        hideUndergroundSites ||
        showOnlyZbSites ||
        hiddenFilterActive ||
        timeSliderFilterActive
}

fun resolveActiveOperatorKeys(
    context: Context?,
    antennas: List<LocalisationEntity>,
    sitesHs: Collection<SiteHsEntity>,
    selectedOperatorKeys: Set<String> = AppConfig.selectedOperatorKeys.value,
    frequencyFilter: FrequencyFilterSelection = FrequencyFilterSelection.fromMapConfig(),
    showSitesInService: Boolean = AppConfig.showSitesInService.value,
    showSitesOutOfService: Boolean = AppConfig.showSitesOutOfService.value,
    showProjectSites: Boolean = AppConfig.showProjectSites.value,
    hideUndergroundSites: Boolean = AppConfig.hideUndergroundSites.value,
    showOnlyZbSites: Boolean = AppConfig.showOnlyZbSites.value,
    timeSliderActive: Boolean = AppConfig.timeSliderActive.value,
    timeSliderThreshold: Int? = AppConfig.timeSliderThreshold.value,
    applyFilters: Boolean = true
): Set<String>? {
    if (!applyFilters) return null

    val isFilterActive = isAnyMapFilterActive(
        context = context,
        antennas = antennas,
        selectedOperatorKeys = selectedOperatorKeys,
        frequencyFilter = frequencyFilter,
        showSitesInService = showSitesInService,
        showSitesOutOfService = showSitesOutOfService,
        showProjectSites = showProjectSites,
        hideUndergroundSites = hideUndergroundSites,
        showOnlyZbSites = showOnlyZbSites,
        timeSliderActive = timeSliderActive,
        timeSliderThreshold = timeSliderThreshold
    )

    if (!isFilterActive) return null

    val normalizedSelectedKeys = selectedOperatorKeys.map { it.uppercase() }.toSet()
    val hsOperatorMap = buildHsOperatorMap(sitesHs)
    val activeKeys = mutableSetOf<String>()

    antennas.forEach { antenna ->
        if (hideUndergroundSites && antenna.hasUndergroundSupport == 1) return@forEach
        if (showOnlyZbSites && antenna.isZb != 1) return@forEach
        if (!frequencyFilter.matchesAntenna(antenna)) return@forEach

        if (timeSliderActive && timeSliderThreshold != null) {
            val serviceInt = parseServiceDateInt(antenna.dateService)
            if (serviceInt == null || serviceInt > timeSliderThreshold) {
                return@forEach
            }
        }

        val isProjectSite = !antenna.isDeclaredActive()
        val opKeys = OperatorColors.keysFor(antenna.operateur)

        opKeys.forEach { operatorKey ->
            if (context != null && HiddenSitesStore.isHidden(context, antenna.physicalSiteKey(), operatorKey)) {
                return@forEach
            }
            if (operatorKey !in normalizedSelectedKeys) {
                return@forEach
            }
            val matchesStatus = when {
                isProjectSite -> showProjectSites
                isOperatorDeclaredHs(antenna, operatorKey, hsOperatorMap) -> showSitesOutOfService
                else -> showSitesInService
            }
            if (matchesStatus) {
                activeKeys.add(operatorKey)
            }
        }
    }

    return activeKeys
}

fun activeOperatorKeysForSiteStatusFilter(
    antennas: List<LocalisationEntity>,
    sitesHs: Collection<SiteHsEntity>,
    showSitesInService: Boolean,
    showSitesOutOfService: Boolean,
    showProjectSites: Boolean
): Set<String>? {
    if (showSitesInService && showSitesOutOfService && showProjectSites) return null

    val hsOperatorMap = buildHsOperatorMap(sitesHs)
    return antennas
        .flatMap { antenna ->
            // Statuts exclusifs : un site sans aucune émission en service est « en projet ».
            val isProjectSite = !antenna.isDeclaredActive()
            OperatorColors.keysFor(antenna.operateur).filter { operatorKey ->
                when {
                    isProjectSite -> showProjectSites
                    isOperatorDeclaredHs(antenna, operatorKey, hsOperatorMap) -> showSitesOutOfService
                    else -> showSitesInService
                }
            }
        }
        .toSet()
}

fun buildHsOperatorMap(sitesHs: Collection<SiteHsEntity>): Map<String, Set<String>> {
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

fun isOperatorDeclaredHs(
    antenna: LocalisationEntity,
    operatorKey: String,
    hsOperatorMap: Map<String, Set<String>>
): Boolean {
    val hsOperators = hsOperatorMap[normalizedAnfrId(antenna.idAnfr)] ?: return false
    return HS_OPERATOR_WILDCARD in hsOperators || operatorKey in hsOperators
}

fun visibleOperatorKeysForAntenna(
    antenna: LocalisationEntity,
    hsOperatorMap: Map<String, Set<String>>,
    showSitesInService: Boolean,
    showSitesOutOfService: Boolean,
    showProjectSites: Boolean,
    selectedOperatorKeys: Set<String>
): List<String> {
    val isProjectSite = !antenna.isDeclaredActive()
    return OperatorColors.keysFor(antenna.operateur).filter { operatorKey ->
        if (operatorKey !in selectedOperatorKeys) {
            false
        } else if (isProjectSite) {
            showProjectSites
        } else if (isOperatorDeclaredHs(antenna, operatorKey, hsOperatorMap)) {
            showSitesOutOfService
        } else {
            showSitesInService
        }
    }
}

fun passesSiteDisplayFilters(
    antenna: LocalisationEntity,
    hsOperatorMap: Map<String, Set<String>>,
    selectedOperatorKeys: Set<String>,
    showSitesInService: Boolean,
    showSitesOutOfService: Boolean,
    showProjectSites: Boolean,
    hideUndergroundSites: Boolean,
    showOnlyZbSites: Boolean,
    frequencyFilter: FrequencyFilterSelection
): Boolean {
    val visibleOperators = visibleOperatorKeysForAntenna(
        antenna = antenna,
        hsOperatorMap = hsOperatorMap,
        showSitesInService = showSitesInService,
        showSitesOutOfService = showSitesOutOfService,
        showProjectSites = showProjectSites,
        selectedOperatorKeys = selectedOperatorKeys
    )
    if (visibleOperators.isEmpty()) return false
    if (hideUndergroundSites && antenna.hasUndergroundSupport == 1) return false
    if (showOnlyZbSites && antenna.isZb != 1) return false
    return frequencyFilter.matchesAntenna(antenna)
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
