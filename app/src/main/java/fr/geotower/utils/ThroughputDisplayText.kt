package fr.geotower.utils

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.geotower.R

object ThroughputTextKey {
    const val THROUGHPUT_WARNING_NETWORK_UNKNOWN = "La charge du réseau, le backhaul et les capacités exactes du téléphone ne sont pas connus."
    const val THROUGHPUT_WARNING_PROFILE_PREFIX = "Le MIMO et la modulation ne sont pas publiés au niveau du site : le profil "
    const val THROUGHPUT_WARNING_PROFILE_SUFFIX = " est donc appliqué."
    const val THROUGHPUT_WARNING_ALLOCATION_PREFIX = "Bande "
    const val THROUGHPUT_WARNING_ALLOCATION_SUFFIX = " exclue : allocation opérateur introuvable."
    const val THROUGHPUT_WARNING_DSS_PREFIX = "Bande "
    const val THROUGHPUT_WARNING_DSS_SUFFIX = " potentiellement partagée entre la 4G et la 5G : le débit n'est pas additionné intégralement."
    const val THROUGHPUT_WARNING_UPLINK_AGGREGATION = "Le débit montant est limité aux deux meilleures fréquences agrégées, une hypothèse plus réaliste pour les réseaux mobiles en France."
    const val THROUGHPUT_WARNING_LOW_BAND_AGGREGATION = "Agrégation 4G entre bandes basses 700/800/900 MHz limitée : beaucoup de téléphones ne cumulent pas ces porteuses."
    const val THROUGHPUT_WARNING_LTE_AGGREGATION_LIMIT = "Limite d'agrégation 4G choisie : seules les meilleures porteuses sont comptées."
    const val THROUGHPUT_WARNING_NR_AGGREGATION_LIMIT = "Limite d'agrégation 5G du profil : seules les meilleures porteuses sont comptées."
    const val THROUGHPUT_REASON_NO_METROPOLITAN_ARCEP_ALLOCATION = "Aucune allocation Arcep France métropolitaine compatible avec cette technologie et cette bande."
    const val THROUGHPUT_REASON_DSS_SHARED = "Bande potentiellement partagée entre la 4G et la 5G : elle n'est pas additionnée deux fois."
    const val THROUGHPUT_REASON_5G_DISABLED = "5G désactivée"
    const val THROUGHPUT_REASON_4G_DISABLED = "4G désactivée"
    const val THROUGHPUT_REASON_BAND_EXCLUDED = "Bande exclue"
    const val THROUGHPUT_REASON_OPERATOR_NOT_RECOGNIZED = "Opérateur non reconnu"
    const val THROUGHPUT_REASON_OPERATOR_NOT_RECOGNIZED_ARCEP = "Opérateur non reconnu pour les allocations Arcep"
    const val THROUGHPUT_REASON_ARCEP_ALLOCATION_NOT_FOUND = "Allocation Arcep introuvable"
    const val THROUGHPUT_REASON_ALLOCATION_NOT_FOUND = "Allocation introuvable"
    const val THROUGHPUT_REASON_PLANNED_BAND = "Bande en projet"
    const val THROUGHPUT_SOURCE_SUMMARY_ENGINE = "ANFR/data.gouv pour les fréquences déclarées, Arcep pour les allocations opérateur, ETSI/3GPP TS 38.306 et TS 36.306/36.213 pour le modèle radio."
    const val THROUGHPUT_SOURCE_SUMMARY_DEFAULT = "ANFR/data.gouv pour les fréquences déclarées, Arcep pour les allocations opérateur, 3GPP pour le modèle radio."
    const val THROUGHPUT_PROFILE_PRUDENT_DESC = "Profil prudent : 4G 64-QAM en descendant, 16-QAM en montant, 5G NR 64-QAM, agrégation limitée et DSS non compté deux fois."
    const val THROUGHPUT_PROFILE_STANDARD_DESC = "Profil standard : 4G 256-QAM en descendant avec MIMO 2x2, montant 64-QAM côté téléphone, 5G n78 256-QAM en descendant avec MIMO 4x4, montant 64-QAM sur 2 couches, DSS non compté deux fois."
    const val THROUGHPUT_PROFILE_IDEAL_LABEL = "Profil idéal"
    const val THROUGHPUT_PROFILE_IDEAL_DESC = "Profil idéal : très bonnes conditions radio plausibles, 4G en descendant avec MIMO 4x4, 5G NR 256-QAM, agrégation plus ouverte et sans double comptage DSS."
    const val THROUGHPUT_PROFILE_CUSTOM_LABEL = "Personnalisé"
    const val THROUGHPUT_PROFILE_CUSTOM_DESC = "Profil personnalisé : modulations descendantes et montantes choisies dans l'interface, débit montant traité comme celui d'un téléphone."
    const val THROUGHPUT_PROFILE_CUSTOM_SHORT_DESC = "Profil personnalisé : modulations DL/UL choisies dans l'interface, UL traité comme un téléphone."
}

object ThroughputDisplayText {
    @Composable
    fun headerSite(siteId: String, supportHeightLabel: String?): String {
        return if (supportHeightLabel.isNullOrBlank()) {
            stringResource(R.string.throughput_header_site, siteId)
        } else {
            stringResource(R.string.throughput_header_site_support, siteId, supportHeightLabel)
        }
    }

    @Composable fun includedBandsCount(included: Int, total: Int) = stringResource(R.string.throughput_included_bands_count, included, total)
    @Composable fun mainZoneEstimated(near: String, far: String) = stringResource(R.string.throughput_main_zone_estimated, near, far)
    @Composable fun estimatedCone(center: String, near: String, far: String) = stringResource(R.string.throughput_estimated_cone, center, near, far)
    @Composable fun calculationAssumptions(assumptions: String) = stringResource(R.string.throughput_calculation_assumptions, assumptions)
    @Composable fun sources(summary: String) = stringResource(R.string.throughput_sources, summary)
    @Composable fun customSelectedPosition(label: String) = stringResource(R.string.throughput_custom_selected_position, label)
    @Composable fun positionDistance(distance: String) = stringResource(R.string.throughput_position_distance, distance)
    @Composable fun positionAzimuthInside(azimuth: String, delta: Int) = stringResource(R.string.throughput_position_azimuth_inside, azimuth, delta)
    @Composable fun positionAzimuthOutside(azimuth: String, delta: Int) = stringResource(R.string.throughput_position_azimuth_outside, azimuth, delta)
    @Composable fun positionCone(center: String, near: String, far: String) = stringResource(R.string.throughput_position_cone, center, near, far)
    @Composable fun customImpact(ltePercent: Int, nrPercent: Int) = stringResource(R.string.throughput_custom_impact, ltePercent, nrPercent)
    @Composable fun customExplanationSignalDesc(ltePercent: Int, nrPercent: Int) = stringResource(R.string.throughput_custom_explanation_signal_desc_dynamic, ltePercent, nrPercent)
    @Composable fun customExplanationAggregationDesc(maxLteCarriers: Int) = stringResource(R.string.throughput_custom_explanation_aggregation_desc_dynamic, maxLteCarriers)
    @Composable fun shareOptimalDistance(distance: String) = stringResource(R.string.share_throughput_optimal_distance, distance)
    @Composable fun shareZone(near: String, far: String) = stringResource(R.string.share_throughput_zone, near, far)
    @Composable fun shareBandsSummary(presetLabel: String, included: Int, total: Int) = stringResource(R.string.share_throughput_bands_summary, presetLabel, included, total)

    @Composable fun presetLabel(presetId: String): String = stringResource(presetLabelRes(presetId))
    @Composable fun presetDescription(presetId: String): String = stringResource(presetDescriptionRes(presetId))

    @Composable
    fun environmentLabel(id: String): String = stringResourceOrRaw(id, mapOf(
        "outdoor" to R.string.throughput_environment_outdoor,
        "vehicle" to R.string.throughput_environment_vehicle,
        "indoor" to R.string.throughput_environment_indoor,
        "deep_indoor" to R.string.throughput_environment_deep_indoor
    ))

    @Composable
    fun positionScenarioLabel(id: String): String = stringResourceOrRaw(id, mapOf(
        "unknown" to R.string.throughput_position_scenario_unknown,
        "in_cone" to R.string.throughput_position_scenario_in_cone,
        "too_close" to R.string.throughput_position_scenario_too_close,
        "too_far" to R.string.throughput_position_scenario_too_far,
        "outside_beam" to R.string.throughput_position_scenario_outside_beam
    ))

    @Composable
    fun networkLoadLabel(id: String): String = stringResourceOrRaw(id, mapOf(
        "unknown" to R.string.throughput_network_load_unknown,
        "light" to R.string.throughput_network_load_light,
        "medium" to R.string.throughput_network_load_medium,
        "heavy" to R.string.throughput_network_load_heavy,
        "saturated" to R.string.throughput_network_load_saturated
    ))

    @Composable
    fun backhaulLabel(id: String): String = stringResourceOrRaw(id, mapOf(
        "unknown" to R.string.throughput_backhaul_unknown,
        "fiber" to R.string.throughput_backhaul_fiber,
        "radio" to R.string.throughput_backhaul_radio,
        "limited" to R.string.throughput_backhaul_limited
    ))

    @Composable
    fun lteAggregationLabel(id: String): String = stringResourceOrRaw(id, mapOf(
        "single" to R.string.throughput_lte_aggregation_single,
        "realistic" to R.string.throughput_lte_aggregation_realistic,
        "wide" to R.string.throughput_lte_aggregation_wide
    ))

    @Composable
    fun blockTitle(blockId: String): String = when (blockId) {
        "header" -> stringResource(R.string.throughput_block_header)
        "summary" -> stringResource(R.string.throughput_block_summary)
        "cone" -> stringResource(R.string.throughput_block_cone)
        "controls" -> stringResource(R.string.throughput_block_controls)
        "bands" -> stringResource(R.string.appstrings_throughput_frequencies_and_modulation_title)
        "assumptions" -> stringResource(R.string.throughput_block_assumptions)
        else -> blockId
    }

    @Composable
    fun translateWarning(warning: String): String {
        return when {
            warning == ThroughputTextKey.THROUGHPUT_WARNING_NETWORK_UNKNOWN -> stringResource(R.string.throughput_warning_network_unknown)
            warning.startsWith(ThroughputTextKey.THROUGHPUT_WARNING_PROFILE_PREFIX) && warning.endsWith(ThroughputTextKey.THROUGHPUT_WARNING_PROFILE_SUFFIX) -> {
                val rawLabel = warning.removePrefix(ThroughputTextKey.THROUGHPUT_WARNING_PROFILE_PREFIX).removeSuffix(ThroughputTextKey.THROUGHPUT_WARNING_PROFILE_SUFFIX)
                stringResource(R.string.throughput_warning_profile_applied, translateProfileLabel(rawLabel))
            }
            warning.startsWith(ThroughputTextKey.THROUGHPUT_WARNING_ALLOCATION_PREFIX) && warning.endsWith(ThroughputTextKey.THROUGHPUT_WARNING_ALLOCATION_SUFFIX) -> stringResource(R.string.throughput_warning_allocation_missing, warning.removePrefix(ThroughputTextKey.THROUGHPUT_WARNING_ALLOCATION_PREFIX).removeSuffix(ThroughputTextKey.THROUGHPUT_WARNING_ALLOCATION_SUFFIX))
            warning.startsWith(ThroughputTextKey.THROUGHPUT_WARNING_DSS_PREFIX) && warning.endsWith(ThroughputTextKey.THROUGHPUT_WARNING_DSS_SUFFIX) -> stringResource(R.string.throughput_warning_dss_shared, warning.removePrefix(ThroughputTextKey.THROUGHPUT_WARNING_DSS_PREFIX).removeSuffix(ThroughputTextKey.THROUGHPUT_WARNING_DSS_SUFFIX))
            warning == ThroughputTextKey.THROUGHPUT_WARNING_UPLINK_AGGREGATION -> stringResource(R.string.throughput_warning_uplink_aggregation)
            warning == ThroughputTextKey.THROUGHPUT_WARNING_LOW_BAND_AGGREGATION -> stringResource(R.string.throughput_warning_low_band_aggregation)
            warning == ThroughputTextKey.THROUGHPUT_WARNING_LTE_AGGREGATION_LIMIT -> stringResource(R.string.throughput_warning_lte_aggregation_limit)
            warning == ThroughputTextKey.THROUGHPUT_WARNING_NR_AGGREGATION_LIMIT -> stringResource(R.string.throughput_warning_nr_aggregation_limit)
            else -> warning
        }
    }

    @Composable
    fun translateAssumption(assumption: String): String = when (assumption) {
        ThroughputTextKey.THROUGHPUT_PROFILE_PRUDENT_DESC -> stringResource(R.string.throughput_profile_prudent_engine_desc)
        ThroughputTextKey.THROUGHPUT_PROFILE_STANDARD_DESC -> stringResource(R.string.throughput_profile_standard_engine_desc)
        ThroughputTextKey.THROUGHPUT_PROFILE_IDEAL_DESC -> stringResource(R.string.throughput_profile_ideal_engine_desc)
        ThroughputTextKey.THROUGHPUT_PROFILE_CUSTOM_DESC,
        ThroughputTextKey.THROUGHPUT_PROFILE_CUSTOM_SHORT_DESC -> stringResource(R.string.throughput_profile_custom_engine_desc)
        else -> assumption
    }

    @Composable
    fun translateExcludedReason(reason: String): String = when (reason) {
        ThroughputTextKey.THROUGHPUT_REASON_5G_DISABLED -> stringResource(R.string.throughput_reason_5g_disabled)
        ThroughputTextKey.THROUGHPUT_REASON_4G_DISABLED -> stringResource(R.string.throughput_reason_4g_disabled)
        ThroughputTextKey.THROUGHPUT_REASON_BAND_EXCLUDED -> stringResource(R.string.throughput_reason_band_excluded)
        ThroughputTextKey.THROUGHPUT_REASON_OPERATOR_NOT_RECOGNIZED -> stringResource(R.string.throughput_reason_operator_not_recognized)
        ThroughputTextKey.THROUGHPUT_REASON_OPERATOR_NOT_RECOGNIZED_ARCEP -> stringResource(R.string.throughput_reason_operator_not_recognized_arcep)
        ThroughputTextKey.THROUGHPUT_REASON_ARCEP_ALLOCATION_NOT_FOUND -> stringResource(R.string.throughput_reason_arcep_allocation_not_found)
        ThroughputTextKey.THROUGHPUT_REASON_ALLOCATION_NOT_FOUND -> stringResource(R.string.throughput_reason_allocation_not_found)
        ThroughputTextKey.THROUGHPUT_REASON_PLANNED_BAND -> stringResource(R.string.throughput_reason_planned_band)
        ThroughputTextKey.THROUGHPUT_REASON_NO_METROPOLITAN_ARCEP_ALLOCATION -> stringResource(R.string.throughput_reason_no_metropolitan_arcep_allocation)
        ThroughputTextKey.THROUGHPUT_REASON_DSS_SHARED -> stringResource(R.string.throughput_reason_dss_shared)
        ThroughputTextKey.THROUGHPUT_WARNING_LOW_BAND_AGGREGATION -> stringResource(R.string.throughput_warning_low_band_aggregation)
        ThroughputTextKey.THROUGHPUT_WARNING_LTE_AGGREGATION_LIMIT -> stringResource(R.string.throughput_warning_lte_aggregation_limit)
        ThroughputTextKey.THROUGHPUT_WARNING_NR_AGGREGATION_LIMIT -> stringResource(R.string.throughput_warning_nr_aggregation_limit)
        else -> reason
    }

    @Composable
    fun translateSourceSummary(summary: String): String = when (summary) {
        ThroughputTextKey.THROUGHPUT_SOURCE_SUMMARY_ENGINE -> stringResource(R.string.throughput_source_summary_engine)
        ThroughputTextKey.THROUGHPUT_SOURCE_SUMMARY_DEFAULT -> stringResource(R.string.throughput_source_summary_default)
        else -> summary
    }

    @Composable
    private fun translateProfileLabel(label: String): String = when (label) {
        "Prudent" -> stringResource(R.string.throughput_profile_label_prudent_lower)
        "Standard" -> stringResource(R.string.throughput_profile_label_standard_lower)
        ThroughputTextKey.THROUGHPUT_PROFILE_IDEAL_LABEL -> stringResource(R.string.throughput_profile_label_ideal_lower)
        ThroughputTextKey.THROUGHPUT_PROFILE_CUSTOM_LABEL -> stringResource(R.string.throughput_profile_label_custom_lower)
        else -> label
    }

    @Composable
    private fun stringResourceOrRaw(rawValue: String, resources: Map<String, Int>): String {
        val resId = resources[rawValue] ?: return rawValue
        return stringResource(resId)
    }

    @StringRes
    private fun presetLabelRes(presetId: String): Int = when (presetId.lowercase()) {
        "conservative", "prudent" -> R.string.throughput_preset_label_conservative
        "ideal", "maximum" -> R.string.throughput_preset_label_ideal
        "custom" -> R.string.throughput_preset_label_custom
        else -> R.string.throughput_preset_label_standard
    }

    @StringRes
    private fun presetDescriptionRes(presetId: String): Int = when (presetId.lowercase()) {
        "conservative", "prudent" -> R.string.throughput_preset_description_conservative
        "ideal", "maximum" -> R.string.throughput_preset_description_ideal
        "custom" -> R.string.throughput_preset_description_custom
        else -> R.string.throughput_preset_description_standard
    }
}
