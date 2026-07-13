package fr.geotower.utils

import android.content.SharedPreferences
import androidx.compose.runtime.MutableState

/**
 * Snapshot des filtres carte « par défaut » choisis par l'utilisateur.
 *
 * Le bandeau « Filtres actifs » de la carte ne s'affiche que lorsque l'état courant
 * s'écarte de ce snapshot. Tant que l'utilisateur n'a rien configuré, chaque valeur
 * retombe sur le défaut d'usine → le bandeau se comporte exactement comme avant.
 *
 * Seuls les filtres qui pèsent sur le bandeau sont couverts (les azimuts et le
 * marqueur de localisation, qui n'y figurent jamais, sont volontairement exclus).
 *
 * Les valeurs sont persistées sous des clés préfixées [PREFIX] dans le même store que
 * les filtres courants ([PreferenceStores.APP]). Ces clés ne sont PAS reprises par le
 * système de profils de préférences (préfixe non « visible ») : le défaut de filtres
 * est une préférence locale à l'appareil.
 */
object MapFilterDefaults {

    const val PREFIX = "filter_default_"

    // Clés « nues » des filtres booléens (technos + bandes) sans constante AppConfig dédiée.
    private const val KEY_TECHNO_2G = "show_techno_2g"
    private const val KEY_TECHNO_3G = "show_techno_3g"
    private const val KEY_TECHNO_4G = "show_techno_4g"
    private const val KEY_TECHNO_5G = "show_techno_5g"
    private const val KEY_TECHNO_FH = "show_techno_fh"
    private const val KEY_SITES_IN_SERVICE = "show_sites_in_service"
    private const val KEY_SITES_OUT_OF_SERVICE = "show_sites_out_of_service"

    private val KEY_OPERATORS = PREFIX + AppConfig.PREF_SELECTED_OPERATORS
    private val KEY_SQ_COVERAGE_OPERATORS = PREFIX + AppConfig.PREF_SIGNALQUEST_COVERAGE_OPERATOR_KEYS

    /**
     * Table de vérité (clé de préférence, état AppConfig courant, défaut d'usine) pour tous les
     * filtres booléens qui comptent pour le bandeau. Sert de source unique à la capture, au reset
     * et à la lecture de la référence.
     */
    private val booleanFilters: List<BooleanFilter> = listOf(
        BooleanFilter(KEY_TECHNO_2G, AppConfig.showTechno2G, factory = true),
        BooleanFilter(KEY_TECHNO_3G, AppConfig.showTechno3G, factory = true),
        BooleanFilter(KEY_TECHNO_4G, AppConfig.showTechno4G, factory = true),
        BooleanFilter(KEY_TECHNO_5G, AppConfig.showTechno5G, factory = true),
        BooleanFilter(KEY_TECHNO_FH, AppConfig.showTechnoFH, factory = true),
        BooleanFilter("f2g_900", AppConfig.f2G_900, factory = true),
        BooleanFilter("f2g_1800", AppConfig.f2G_1800, factory = true),
        BooleanFilter("f3g_900", AppConfig.f3G_900, factory = true),
        BooleanFilter("f3g_2100", AppConfig.f3G_2100, factory = true),
        BooleanFilter("f4g_700", AppConfig.f4G_700, factory = true),
        BooleanFilter("f4g_800", AppConfig.f4G_800, factory = true),
        BooleanFilter("f4g_900", AppConfig.f4G_900, factory = true),
        BooleanFilter("f4g_1800", AppConfig.f4G_1800, factory = true),
        BooleanFilter("f4g_2100", AppConfig.f4G_2100, factory = true),
        BooleanFilter("f4g_2600", AppConfig.f4G_2600, factory = true),
        BooleanFilter("f5g_700", AppConfig.f5G_700, factory = true),
        BooleanFilter("f5g_1400", AppConfig.f5G_1400, factory = true),
        BooleanFilter("f5g_2100", AppConfig.f5G_2100, factory = true),
        BooleanFilter("f5g_3500", AppConfig.f5G_3500, factory = true),
        BooleanFilter("f5g_4200", AppConfig.f5G_4200, factory = true),
        BooleanFilter("f5g_26000", AppConfig.f5G_26000, factory = true),
        BooleanFilter(KEY_SITES_IN_SERVICE, AppConfig.showSitesInService, factory = true),
        BooleanFilter(KEY_SITES_OUT_OF_SERVICE, AppConfig.showSitesOutOfService, factory = true),
        BooleanFilter(AppConfig.PREF_HIDE_UNDERGROUND_SITES, AppConfig.hideUndergroundSites, factory = false),
        BooleanFilter(AppConfig.PREF_SHOW_ONLY_ZB_SITES, AppConfig.showOnlyZbSites, factory = false),
        BooleanFilter(AppConfig.PREF_SHOW_RADIO_TV, AppConfig.showRadioTv, factory = false),
        BooleanFilter(AppConfig.PREF_SHOW_RADIO_BROADCAST, AppConfig.showRadioBroadcast, factory = false),
        BooleanFilter(AppConfig.PREF_SHOW_RADIO_PRIVATE_MOBILE, AppConfig.showRadioPrivateMobile, factory = false),
        BooleanFilter(AppConfig.PREF_SHOW_RADIO_FH, AppConfig.showRadioFh, factory = false),
        BooleanFilter(AppConfig.PREF_SHOW_RADIO_OTHER, AppConfig.showRadioOther, factory = false),
        BooleanFilter(AppConfig.PREF_SHOW_SIGNALQUEST_COVERAGE_POINTS, AppConfig.showSignalQuestCoveragePoints, factory = false)
    )

    private val factoryByKey: Map<String, Boolean> = booleanFilters.associate { it.key to it.factory }

    /** Les valeurs de référence auxquelles le bandeau compare l'état courant de la carte. */
    data class Reference(
        val operatorKeys: Set<String>,
        val frequency: FrequencyFilterSelection,
        val showSitesInService: Boolean,
        val showSitesOutOfService: Boolean,
        val hideUndergroundSites: Boolean,
        val showOnlyZbSites: Boolean,
        val showRadioTv: Boolean,
        val showRadioBroadcast: Boolean,
        val showRadioPrivateMobile: Boolean,
        val showRadioFh: Boolean,
        val showRadioOther: Boolean,
        val showSignalQuestCoveragePoints: Boolean,
        val signalQuestCoverageOperatorKeys: Set<String>
    )

    fun reference(prefs: SharedPreferences): Reference {
        fun ref(key: String): Boolean = prefs.getBoolean(PREFIX + key, factoryByKey.getValue(key))
        return Reference(
            operatorKeys = readOperatorKeys(prefs),
            frequency = FrequencyFilterSelection(
                show2G = ref(KEY_TECHNO_2G),
                show3G = ref(KEY_TECHNO_3G),
                show4G = ref(KEY_TECHNO_4G),
                show5G = ref(KEY_TECHNO_5G),
                showFh = ref(KEY_TECHNO_FH),
                f2G900 = ref("f2g_900"),
                f2G1800 = ref("f2g_1800"),
                f3G900 = ref("f3g_900"),
                f3G2100 = ref("f3g_2100"),
                f4G700 = ref("f4g_700"),
                f4G800 = ref("f4g_800"),
                f4G900 = ref("f4g_900"),
                f4G1800 = ref("f4g_1800"),
                f4G2100 = ref("f4g_2100"),
                f4G2600 = ref("f4g_2600"),
                f5G700 = ref("f5g_700"),
                f5G1400 = ref("f5g_1400"),
                f5G2100 = ref("f5g_2100"),
                f5G3500 = ref("f5g_3500"),
                f5G4200 = ref("f5g_4200"),
                f5G26000 = ref("f5g_26000")
            ),
            showSitesInService = ref(KEY_SITES_IN_SERVICE),
            showSitesOutOfService = ref(KEY_SITES_OUT_OF_SERVICE),
            hideUndergroundSites = ref(AppConfig.PREF_HIDE_UNDERGROUND_SITES),
            showOnlyZbSites = ref(AppConfig.PREF_SHOW_ONLY_ZB_SITES),
            showRadioTv = ref(AppConfig.PREF_SHOW_RADIO_TV),
            showRadioBroadcast = ref(AppConfig.PREF_SHOW_RADIO_BROADCAST),
            showRadioPrivateMobile = ref(AppConfig.PREF_SHOW_RADIO_PRIVATE_MOBILE),
            showRadioFh = ref(AppConfig.PREF_SHOW_RADIO_FH),
            showRadioOther = ref(AppConfig.PREF_SHOW_RADIO_OTHER),
            showSignalQuestCoveragePoints = ref(AppConfig.PREF_SHOW_SIGNALQUEST_COVERAGE_POINTS),
            signalQuestCoverageOperatorKeys = readSignalQuestCoverageKeys(prefs)
        )
    }

    /**
     * Signature de l'état COURANT des filtres du bandeau. Sa valeur change dès qu'un filtre
     * est modifié : un `snapshotFlow { currentSignature() }` observe ainsi tout changement
     * (utilisé par l'éditeur « Filtres par défaut » pour recapturer le défaut au vol).
     */
    fun currentSignature(): List<Any?> = listOf(
        AppConfig.selectedOperatorKeys.value,
        FrequencyFilterSelection.fromMapConfig(),
        AppConfig.showSitesInService.value,
        AppConfig.showSitesOutOfService.value,
        AppConfig.hideUndergroundSites.value,
        AppConfig.showOnlyZbSites.value,
        AppConfig.showRadioTv.value,
        AppConfig.showRadioBroadcast.value,
        AppConfig.showRadioPrivateMobile.value,
        AppConfig.showRadioFh.value,
        AppConfig.showRadioOther.value,
        AppConfig.showSignalQuestCoveragePoints.value,
        AppConfig.selectedSignalQuestCoverageOperatorKeys.value
    )

    /** Fige l'état courant des filtres (AppConfig) comme nouveau défaut de référence. */
    fun captureFromCurrent(prefs: SharedPreferences) {
        val editor = prefs.edit()
        booleanFilters.forEach { filter ->
            editor.putBoolean(PREFIX + filter.key, filter.state.value)
        }
        editor.putStringSet(KEY_OPERATORS, AppConfig.selectedOperatorKeys.value)
        editor.putStringSet(KEY_SQ_COVERAGE_OPERATORS, AppConfig.selectedSignalQuestCoverageOperatorKeys.value)
        editor.apply()
    }

    /**
     * Remet les filtres courants (carte) ET le défaut de référence aux valeurs d'usine.
     * Après appel : la carte affiche tout, et le bandeau ne s'affiche plus.
     */
    fun resetToFactory(prefs: SharedPreferences) {
        // 1. Filtres booléens : état AppConfig + clé de préférence normale.
        val editor = prefs.edit()
        booleanFilters.forEach { filter ->
            filter.state.value = filter.factory
            editor.putBoolean(filter.key, filter.factory)
        }
        // Agrégat radio dérivé (toutes les catégories reviennent à false → aucun site radio).
        AppConfig.updateShowRadioSitesFromCategoryFilters()
        editor.putBoolean(AppConfig.PREF_SHOW_RADIO_SITES, AppConfig.showRadioSites.value)
        // 2. Sélections d'opérateurs (state + clés miroir gérés par AppConfig).
        editor.apply()
        AppConfig.saveSelectedOperatorKeys(prefs, OperatorColors.defaultVisibleKeys)
        AppConfig.saveSelectedSignalQuestCoverageOperatorKeys(prefs, AppConfig.defaultSignalQuestCoverageOperatorKeys)
        // 3. Efface le snapshot de défaut : la référence redevient l'usine.
        clearSnapshot(prefs)
    }

    private fun clearSnapshot(prefs: SharedPreferences) {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(PREFIX) }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    private fun readOperatorKeys(prefs: SharedPreferences): Set<String> {
        if (!prefs.contains(KEY_OPERATORS)) return OperatorColors.defaultVisibleKeys
        val stored = prefs.getStringSet(KEY_OPERATORS, null)
            ?.mapNotNull { OperatorColors.specForKey(it)?.key }
            ?.toSet()
        return stored ?: OperatorColors.defaultVisibleKeys
    }

    private fun readSignalQuestCoverageKeys(prefs: SharedPreferences): Set<String> {
        if (!prefs.contains(KEY_SQ_COVERAGE_OPERATORS)) return AppConfig.defaultSignalQuestCoverageOperatorKeys
        val stored = prefs.getStringSet(KEY_SQ_COVERAGE_OPERATORS, null)
            ?.mapNotNull { OperatorColors.specForKey(it)?.key }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
        return stored ?: AppConfig.defaultSignalQuestCoverageOperatorKeys
    }

    private class BooleanFilter(
        val key: String,
        val state: MutableState<Boolean>,
        val factory: Boolean
    )
}
