package fr.geotower.ui.screens.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.R
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.db.RadioDatabaseValidator
import fr.geotower.utils.AppConfig
import fr.geotower.utils.OperatorColorSpec
import fr.geotower.utils.OperatorColors
import android.content.SharedPreferences
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.MutableState
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val OperatorFilterButtonHeight = 76.dp
private const val PREF_OPERATOR_FILTER_METRO_EXPANDED = "map_operator_filter_metro_expanded"
private const val PREF_OPERATOR_FILTER_OVERSEAS_EXPANDED = "map_operator_filter_overseas_expanded"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MapSettingsSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val sizing = LocalGeoTowerUiStyle.current.sizing
    // ✅ AJOUT : Pour pouvoir sauvegarder le paramètre
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", android.content.Context.MODE_PRIVATE)
    val featureFlags by RemoteFeatureFlags.config
    var hasRadioDatabase by remember(context) { mutableStateOf(false) }

    LaunchedEffect(context) {
        hasRadioDatabase = withContext(Dispatchers.IO) {
            val dbPath = context.getDatabasePath(RadioDatabaseValidator.DB_NAME)
            RadioDatabaseValidator.validateDatabaseFile(dbPath).isValid
        }
    }

    // Variables Opérateurs
    var selectedOperatorKeys by AppConfig.selectedOperatorKeys

    // Variables Azimuts
    var showAzimuths by AppConfig.showAzimuths
    var showMapLocationMarker by AppConfig.showMapLocationMarker
    var showRadioTv by AppConfig.showRadioTv
    var showRadioBroadcast by AppConfig.showRadioBroadcast
    var showRadioPrivateMobile by AppConfig.showRadioPrivateMobile
    var showRadioFh by AppConfig.showRadioFh
    var showRadioOther by AppConfig.showRadioOther
    var showSignalQuestCoveragePoints by AppConfig.showSignalQuestCoveragePoints
    var selectedSignalQuestCoverageOperatorKeys by AppConfig.selectedSignalQuestCoverageOperatorKeys

    // Variables Affichage des sites
    var showSitesInService by AppConfig.showSitesInService
    var showSitesOutOfService by AppConfig.showSitesOutOfService
    var hideUndergroundSites by AppConfig.hideUndergroundSites
    var showOnlyZbSites by AppConfig.showOnlyZbSites

    // Variables Technos
    var show2G by AppConfig.showTechno2G
    var show3G by AppConfig.showTechno3G
    var show4G by AppConfig.showTechno4G
    var show5G by AppConfig.showTechno5G
    var showFH by AppConfig.showTechnoFH

    // Variables Fréquences
    // 2G
    var f2G_900 by AppConfig.f2G_900
    var f2G_1800 by AppConfig.f2G_1800
    // 3G
    var f3G_900 by AppConfig.f3G_900
    var f3G_2100 by AppConfig.f3G_2100
    // 4G
    var f4G_700 by AppConfig.f4G_700
    var f4G_800 by AppConfig.f4G_800
    var f4G_900 by AppConfig.f4G_900
    var f4G_1800 by AppConfig.f4G_1800
    var f4G_2100 by AppConfig.f4G_2100
    var f4G_2600 by AppConfig.f4G_2600
    // 5G
    var f5G_700 by AppConfig.f5G_700
    var f5G_1400 by AppConfig.f5G_1400
    var f5G_2100 by AppConfig.f5G_2100
    var f5G_3500 by AppConfig.f5G_3500
    var f5G_4200 by AppConfig.f5G_4200
    var f5G_26000 by AppConfig.f5G_26000

    fun saveRadioCategory(prefKey: String, value: Boolean) {
        AppConfig.updateShowRadioSitesFromCategoryFilters()
        prefs.edit()
            .putBoolean(prefKey, value)
            .putBoolean(AppConfig.PREF_SHOW_RADIO_SITES, AppConfig.showRadioSites.value)
            .apply()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Transparent,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sizing.spacing(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(bottom = sizing.spacing(64.dp))
        ) {

            // 1. OPÉRATEURS
            SectionTitle(stringResource(R.string.appstrings_operators_title))
            fun saveOperatorSelection(next: Set<String>) {
                AppConfig.saveSelectedOperatorKeys(prefs, next)
                selectedOperatorKeys = AppConfig.selectedOperatorKeys.value
            }
            val metroOperators = listOfNotNull(
                OperatorColors.specForKey(OperatorColors.ORANGE_KEY),
                OperatorColors.specForKey(OperatorColors.SFR_KEY),
                OperatorColors.specForKey(OperatorColors.BOUYGUES_KEY)?.copy(label = "Bouygues"),
                OperatorColors.specForKey(OperatorColors.FREE_KEY)?.copy(label = "Free")
            )
            val defaultOperatorKey = OperatorColors.keyFor(AppConfig.defaultOperator.value)
            val defaultOperatorIsMetro = metroOperators.any { it.key == defaultOperatorKey }
            val defaultOperatorIsOverseas = OperatorColors.overseas.any { it.key == defaultOperatorKey }
            var isMetroExpanded by remember(defaultOperatorKey) {
                mutableStateOf(prefs.getBoolean(PREF_OPERATOR_FILTER_METRO_EXPANDED, defaultOperatorIsMetro))
            }
            var isOverseasExpanded by remember(defaultOperatorKey) {
                mutableStateOf(prefs.getBoolean(PREF_OPERATOR_FILTER_OVERSEAS_EXPANDED, defaultOperatorIsOverseas))
            }
            Column(verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))) {
                OperatorFilterGroup(
                    title = stringResource(R.string.operator_region_metro),
                    operators = metroOperators,
                    selectedKeys = selectedOperatorKeys,
                    isExpanded = isMetroExpanded,
                    onExpandedChange = {
                        isMetroExpanded = it
                        prefs.edit().putBoolean(PREF_OPERATOR_FILTER_METRO_EXPANDED, it).apply()
                    },
                    onSelectionChange = ::saveOperatorSelection
                )

                OperatorFilterGroup(
                    title = stringResource(R.string.operator_region_overseas),
                    operators = OperatorColors.overseas,
                    selectedKeys = selectedOperatorKeys,
                    isExpanded = isOverseasExpanded,
                    onExpandedChange = {
                        isOverseasExpanded = it
                        prefs.edit().putBoolean(PREF_OPERATOR_FILTER_OVERSEAS_EXPANDED, it).apply()
                    },
                    onSelectionChange = ::saveOperatorSelection
                )
            }

            Spacer(modifier = Modifier.height(sizing.spacing(32.dp)))

            // ==========================================
            // 2. AZIMUTS
            // ==========================================
            SectionTitle(stringResource(R.string.appstrings_azimuths_title))

            // On récupère la nouvelle variable
            var showAzimuthsCone by AppConfig.showAzimuthsCone

            Row(horizontalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))) {
                SelectableButton(
                    label = stringResource(R.string.appstrings_show_azimuths_label).replace(" (", "\n("),
                    isSelected = showAzimuths,
                    modifier = Modifier.weight(1f),
                    minHeight = 116.dp,
                    maxLines = 4
                ) {
                    showAzimuths = it
                    prefs.edit().putBoolean(AppConfig.PREF_SHOW_AZIMUTH_LINES, it).apply()
                }

                SelectableButton(
                    label = stringResource(R.string.appstrings_show_azimuths_cone_label).replace(" (", "\n("),
                    isSelected = showAzimuthsCone,
                    modifier = Modifier.weight(1f),
                    minHeight = 116.dp,
                    maxLines = 4
                ) {
                    showAzimuthsCone = it
                    prefs.edit().putBoolean(AppConfig.PREF_SHOW_AZIMUTH_CONES, it).apply()
                }
            }

            Spacer(modifier = Modifier.height(sizing.spacing(32.dp)))

            // 2. TECHNOLOGIES
            SectionTitle(stringResource(R.string.appstrings_technologies_title))
            Row(horizontalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp))) {
                SelectableButton("5G", show5G, Modifier.weight(1f)) {
                    show5G = it; prefs.edit().putBoolean("show_techno_5g", it).apply()
                }
                SelectableButton("4G", show4G, Modifier.weight(1f)) {
                    show4G = it; prefs.edit().putBoolean("show_techno_4g", it).apply()
                }
                SelectableButton("3G", show3G, Modifier.weight(1f)) {
                    show3G = it; prefs.edit().putBoolean("show_techno_3g", it).apply()
                }
                SelectableButton("2G", show2G, Modifier.weight(1f)) {
                    show2G = it; prefs.edit().putBoolean("show_techno_2g", it).apply()
                }
                SelectableButton("FH", showFH, Modifier.weight(1f)) {
                    showFH = it; prefs.edit().putBoolean("show_techno_fh", it).apply()
                }
            }

            Spacer(modifier = Modifier.height(sizing.spacing(32.dp)))

            // 3. FRÉQUENCES
            SectionTitle(stringResource(R.string.appstrings_frequencies_title))

            AnimatedVisibility(visible = show5G) {
                Column {
                    FreqRow("5G") {
                        FilterToggleButton("700 MHz", "f5g_700", AppConfig.f5G_700, prefs)
                        FilterToggleButton("1400 MHz (exp)", "f5g_1400", AppConfig.f5G_1400, prefs)
                        FilterToggleButton("2100 MHz", "f5g_2100", AppConfig.f5G_2100, prefs)
                        FilterToggleButton("3500 MHz", "f5g_3500", AppConfig.f5G_3500, prefs)
                        FilterToggleButton("4200 MHz (exp)", "f5g_4200", AppConfig.f5G_4200, prefs)
                        FilterToggleButton("26 GHz (exp)", "f5g_26000", AppConfig.f5G_26000, prefs)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            AnimatedVisibility(visible = show4G) {
                Column {
                    FreqRow("4G") {
                        FilterToggleButton("700 MHz", "f4g_700", AppConfig.f4G_700, prefs)
                        FilterToggleButton("800 MHz", "f4g_800", AppConfig.f4G_800, prefs)
                        FilterToggleButton("900 MHz", "f4g_900", AppConfig.f4G_900, prefs)
                        FilterToggleButton("1800 MHz", "f4g_1800", AppConfig.f4G_1800, prefs)
                        FilterToggleButton("2100 MHz", "f4g_2100", AppConfig.f4G_2100, prefs)
                        FilterToggleButton("2600 MHz", "f4g_2600", AppConfig.f4G_2600, prefs)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            AnimatedVisibility(visible = show3G) {
                Column {
                    FreqRow("3G") {
                        FilterToggleButton("900 MHz", "f3g_900", AppConfig.f3G_900, prefs)
                        FilterToggleButton("2100 MHz", "f3g_2100", AppConfig.f3G_2100, prefs)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            AnimatedVisibility(visible = show2G) {
                Column {
                    FreqRow("2G") {
                        FilterToggleButton("900 MHz", "f2g_900", AppConfig.f2G_900, prefs)
                        FilterToggleButton("1800 MHz", "f2g_1800", AppConfig.f2G_1800, prefs)
                    }
                }
            }

            Spacer(modifier = Modifier.height(sizing.spacing(32.dp)))

            SectionTitle(stringResource(R.string.appstrings_map_location_section_title))
            SelectableButton(
                label = stringResource(R.string.appstrings_map_location_marker_option),
                isSelected = showMapLocationMarker,
                modifier = Modifier.fillMaxWidth()
            ) {
                showMapLocationMarker = it
                prefs.edit().putBoolean(AppConfig.PREF_SHOW_MAP_LOCATION_MARKER, it).apply()
            }

            if (featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SIGNALQUEST_COVERAGE)) {
                Spacer(modifier = Modifier.height(sizing.spacing(32.dp)))

                SectionTitle(stringResource(R.string.appstrings_signalquest_coverage_title))
                SelectableButton(
                    label = stringResource(R.string.appstrings_signalquest_coverage_points_label),
                    isSelected = showSignalQuestCoveragePoints,
                    modifier = Modifier.fillMaxWidth(),
                    selectedColor = MaterialTheme.colorScheme.tertiary
                ) { enabled ->
                    showSignalQuestCoveragePoints = enabled
                    prefs.edit()
                        .putBoolean(AppConfig.PREF_SHOW_SIGNALQUEST_COVERAGE_POINTS, enabled)
                        .apply()
                }

                AnimatedVisibility(visible = showSignalQuestCoveragePoints) {
                    val coverageOperators = listOfNotNull(
                        OperatorColors.specForKey(OperatorColors.ORANGE_KEY),
                        OperatorColors.specForKey(OperatorColors.SFR_KEY),
                        OperatorColors.specForKey(OperatorColors.BOUYGUES_KEY)?.copy(label = "Bouygues"),
                        OperatorColors.specForKey(OperatorColors.FREE_KEY)?.copy(label = "Free")
                    )
                    var isCoverageOperatorsExpanded by remember { mutableStateOf(true) }

                    Column(modifier = Modifier.padding(top = sizing.spacing(12.dp))) {
                        OperatorFilterGroup(
                            title = stringResource(R.string.appstrings_operators_title),
                            operators = coverageOperators,
                            selectedKeys = selectedSignalQuestCoverageOperatorKeys,
                            isExpanded = isCoverageOperatorsExpanded,
                            onExpandedChange = { isCoverageOperatorsExpanded = it },
                            onSelectionChange = { next ->
                                AppConfig.saveSelectedSignalQuestCoverageOperatorKeys(prefs, next)
                                selectedSignalQuestCoverageOperatorKeys =
                                    AppConfig.selectedSignalQuestCoverageOperatorKeys.value
                            },
                            singleSelection = true
                        )
                    }
                }
            }

            if (hasRadioDatabase) {
                Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

                SectionTitle(stringResource(R.string.appstrings_radio_filters_title))
                Row(horizontalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))) {
                    SelectableButton(
                        label = stringResource(R.string.appstrings_radio_category_tv),
                        isSelected = showRadioTv,
                        modifier = Modifier.weight(1f),
                        selectedColor = Color(0xFF8BC34A)
                    ) {
                        showRadioTv = it
                        saveRadioCategory(AppConfig.PREF_SHOW_RADIO_TV, it)
                    }
                    SelectableButton(
                        label = stringResource(R.string.appstrings_radio_category_radio),
                        isSelected = showRadioBroadcast,
                        modifier = Modifier.weight(1f),
                        selectedColor = Color(0xFFFDD835)
                    ) {
                        showRadioBroadcast = it
                        saveRadioCategory(AppConfig.PREF_SHOW_RADIO_BROADCAST, it)
                    }
                }

                Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

                Row(horizontalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))) {
                    SelectableButton(
                        label = stringResource(R.string.appstrings_radio_category_private_mobile),
                        isSelected = showRadioPrivateMobile,
                        modifier = Modifier.weight(1f),
                        selectedColor = Color(0xFF006D77),
                        minHeight = 68.dp,
                        maxLines = 3
                    ) {
                        showRadioPrivateMobile = it
                        saveRadioCategory(AppConfig.PREF_SHOW_RADIO_PRIVATE_MOBILE, it)
                    }
                    SelectableButton(
                        label = stringResource(R.string.appstrings_radio_category_fh),
                        isSelected = showRadioFh,
                        modifier = Modifier.weight(1f),
                        selectedColor = Color(0xFF0D47A1),
                        minHeight = 68.dp,
                        maxLines = 3
                    ) {
                        showRadioFh = it
                        saveRadioCategory(AppConfig.PREF_SHOW_RADIO_FH, it)
                    }
                }

                Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

                SelectableButton(
                    label = stringResource(R.string.appstrings_radio_category_other),
                    isSelected = showRadioOther,
                    modifier = Modifier.fillMaxWidth(),
                    selectedColor = Color.Black
                ) {
                    showRadioOther = it
                    saveRadioCategory(AppConfig.PREF_SHOW_RADIO_OTHER, it)
                }
            }

            Spacer(modifier = Modifier.height(sizing.spacing(32.dp)))

            SectionTitle(stringResource(R.string.appstrings_site_display_title))
            Row(horizontalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))) {
                SelectableButton(
                    label = stringResource(R.string.appstrings_sites_in_service_label),
                    isSelected = showSitesInService,
                    modifier = Modifier.weight(1f)
                ) {
                    showSitesInService = it
                    prefs.edit().putBoolean("show_sites_in_service", it).apply()
                }

                SelectableButton(
                    label = stringResource(R.string.appstrings_sites_out_of_service_label),
                    isSelected = showSitesOutOfService,
                    modifier = Modifier.weight(1f),
                    selectedColor = MaterialTheme.colorScheme.error
                ) {
                    showSitesOutOfService = it
                    prefs.edit().putBoolean("show_sites_out_of_service", it).apply()
                }
            }

            Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

            SelectableButton(
                label = stringResource(R.string.appstrings_show_only_zb_sites_label),
                isSelected = showOnlyZbSites,
                modifier = Modifier.fillMaxWidth(),
                selectedColor = MaterialTheme.colorScheme.tertiary
            ) {
                showOnlyZbSites = it
                prefs.edit().putBoolean(AppConfig.PREF_SHOW_ONLY_ZB_SITES, it).apply()
            }

            Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

            SelectableButton(
                label = stringResource(R.string.appstrings_hide_underground_sites_label),
                isSelected = hideUndergroundSites,
                modifier = Modifier.fillMaxWidth()
            ) {
                hideUndergroundSites = it
                prefs.edit().putBoolean(AppConfig.PREF_HIDE_UNDERGROUND_SITES, it).apply()
            }
        }
    }
}

// --- COMPOSANTS UI ---

@Composable
fun SectionTitle(text: String) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Text(
        text = text,
        style = sizing.textStyle(MaterialTheme.typography.titleMedium),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = sizing.spacing(16.dp))
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OperatorFilterGroup(
    title: String,
    operators: List<OperatorColorSpec>,
    selectedKeys: Set<String>,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectionChange: (Set<String>) -> Unit,
    singleSelection: Boolean = false
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val selectedCount = operators.count { it.key in selectedKeys }

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = { onExpandedChange(!isExpanded) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(sizing.component(12.dp)),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
            border = BorderStroke(sizing.component(1.dp), MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sizing.spacing(14.dp), vertical = sizing.spacing(12.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = sizing.textStyle(MaterialTheme.typography.labelLarge),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$selectedCount/${operators.size}",
                        style = sizing.textStyle(MaterialTheme.typography.labelMedium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = sizing.spacing(12.dp))
            ) {
                operators.chunked(2).forEach { rowOperators ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowOperators.forEach { operator ->
                            SelectableButton(
                                label = operator.label,
                                isSelected = operator.key in selectedKeys,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(sizing.component(OperatorFilterButtonHeight)),
                                selectedColor = Color(operator.colorArgb)
                            ) { selected ->
                                val next = if (singleSelection) {
                                    if (selected) setOf(operator.key) else emptySet()
                                } else {
                                    if (selected) selectedKeys + operator.key else selectedKeys - operator.key
                                }
                                onSelectionChange(next)
                            }
                        }
                        if (rowOperators.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SelectableButton(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    selectedColor: Color? = null,
    minHeight: Dp = 56.dp,
    maxLines: Int = 3,
    onClick: (Boolean) -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val activeColor = selectedColor ?: MaterialTheme.colorScheme.primary

    // Le fond reste légèrement teinté, mais le texte ne change plus de couleur
    val containerColor = if (isSelected) activeColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor = MaterialTheme.colorScheme.onSurface // Couleur fixe pour l'écriture
    val border = if (isSelected) BorderStroke(sizing.component(1.dp), activeColor) else null // Bordure plus fine

    Surface(
        onClick = { onClick(!isSelected) },
        // ✅ CORRECTION : heightIn(min = 56.dp) permet au bouton de s'agrandir s'il y a 2 lignes
        modifier = modifier.heightIn(min = sizing.component(minHeight)),
        shape = RoundedCornerShape(sizing.component(12.dp)),
        color = containerColor,
        contentColor = contentColor,
        border = border
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sizing.spacing(8.dp), vertical = sizing.spacing(8.dp))
        ) {
            val compactText = label.length >= 18
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
                fontSize = sizing.text(if (compactText) 15.sp else 16.sp),
                maxLines = maxLines,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center, // ✅ CORRECTION : Centre le texte
                lineHeight = sizing.text(20.sp) // Rapproche joliment les deux lignes
            )
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FreqRow(label: String, content: @Composable () -> Unit) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = sizing.spacing(12.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = sizing.text(18.sp),
            modifier = Modifier.width(sizing.component(50.dp))
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp)),
            verticalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp)),
            modifier = Modifier.weight(1f)
        ) { content() }
    }
}

@Composable
fun FreqButton(
    label: String,
    isSelected: Boolean,
    onClick: (Boolean) -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = { onClick(!isSelected) },
        shape = RoundedCornerShape(sizing.component(8.dp)),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.size(width = sizing.component(if (label.length >= 12) 112.dp else 86.dp), height = sizing.component(44.dp))
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = sizing.spacing(4.dp))
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.Medium,
                fontSize = sizing.text(if (label.length >= 12) 12.sp else 13.sp),
                textAlign = TextAlign.Center,
                lineHeight = sizing.text(14.sp),
                maxLines = 2
            )
        }
    }
}

@Composable
fun FilterToggleButton(
    label: String,
    prefKey: String,
    state: MutableState<Boolean>,
    prefs: SharedPreferences
) {
    // On utilise ton design de bouton existant
    FreqButton(
        label = label,
        isSelected = state.value,
        onClick = { newState ->
            state.value = newState
            prefs.edit().putBoolean(prefKey, newState).apply()
        }
    )
}
