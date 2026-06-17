package fr.geotower.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fr.geotower.R
import fr.geotower.utils.AppConfig
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.ui.components.oneUiActionButtonShape
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.components.rememberReorderableDragState
import fr.geotower.ui.components.settingsPopupFadingEdge
import fr.geotower.ui.components.MiniMapViewMode
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import kotlin.math.roundToInt
import fr.geotower.utils.PreferenceStores
import fr.geotower.utils.StatsDisplayMode
import fr.geotower.utils.StatsPreferences
import fr.geotower.utils.SitePagePrefs
import fr.geotower.utils.SupportPagePrefs
import fr.geotower.utils.ThroughputPrefs
import fr.geotower.utils.ThroughputDisplayText
import fr.geotower.utils.radioFrequencyLabel

object SiteSpeedtestsPagePreferences {
    const val FILTER_MAJOR_ENB = "page_speedtests_filter_major_enb"
    const val INCLUDE_MISSING_ENB = "page_speedtests_include_missing_enb"
    const val SHOW_COUNT = "page_speedtests_show_count"
    const val SHOW_RADIO = "page_speedtests_show_radio"
    const val SHOW_NETWORK = "page_speedtests_show_network"
    const val SHOW_COORDINATES = "page_speedtests_show_coordinates"
    const val BEST_METRIC = "page_speedtests_best_metric"
    const val SORT_METRIC = "page_speedtests_sort_metric"
    const val SORT_DESCENDING = "page_speedtests_sort_descending"

    const val SORT_AVERAGE = "average"
    const val SORT_MAX = "max"
    const val SORT_DOWNLOAD = "download"

    const val DEFAULT_FILTER_MAJOR_ENB = true
    const val DEFAULT_INCLUDE_MISSING_ENB = true
    const val DEFAULT_SHOW_COUNT = true
    const val DEFAULT_SHOW_RADIO = true
    const val DEFAULT_SHOW_NETWORK = true
    const val DEFAULT_SHOW_COORDINATES = true
    const val DEFAULT_BEST_METRIC = SORT_AVERAGE
    const val DEFAULT_SORT_METRIC = SORT_AVERAGE
    const val DEFAULT_SORT_DESCENDING = true

    fun putDefaults(editor: SharedPreferences.Editor): SharedPreferences.Editor {
        return editor
            .putBoolean(FILTER_MAJOR_ENB, DEFAULT_FILTER_MAJOR_ENB)
            .putBoolean(INCLUDE_MISSING_ENB, DEFAULT_INCLUDE_MISSING_ENB)
            .putBoolean(SHOW_COUNT, DEFAULT_SHOW_COUNT)
            .putBoolean(SHOW_RADIO, DEFAULT_SHOW_RADIO)
            .putBoolean(SHOW_NETWORK, DEFAULT_SHOW_NETWORK)
            .putBoolean(SHOW_COORDINATES, DEFAULT_SHOW_COORDINATES)
            .putString(BEST_METRIC, DEFAULT_BEST_METRIC)
            .putString(SORT_METRIC, DEFAULT_SORT_METRIC)
            .putBoolean(SORT_DESCENDING, DEFAULT_SORT_DESCENDING)
    }

    fun reset(prefs: SharedPreferences) {
        putDefaults(prefs.edit()).apply()
    }

    fun normalizeSortMetric(metric: String?): String {
        return when (metric) {
            SORT_MAX -> SORT_MAX
            SORT_DOWNLOAD -> SORT_DOWNLOAD
            else -> SORT_AVERAGE
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagesCustomizationSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState,
    useOneUi: Boolean,
    onStartupPageClick: () -> Unit,
    onHomeClick: () -> Unit,
    onNearbyClick: () -> Unit,
    onMapClick: () -> Unit,
    onCompassClick: () -> Unit,
    onStatsClick: () -> Unit,
    // --- NOUVEAUX PARAMÈTRES ---
    onSupportClick: () -> Unit,
    onSiteClick: () -> Unit,
    onSpeedtestsClick: () -> Unit,
    onThroughputCalculatorClick: () -> Unit,
    onOpenFrequencies: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val scrollState = rememberScrollState()
    val featureFlags by RemoteFeatureFlags.config

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .settingsPopupFadingEdge(scrollState)
                .verticalScroll(scrollState)
                .padding(bottom = sizing.spacing(48.dp), start = sizing.spacing(16.dp), end = sizing.spacing(16.dp))
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(
                    text = stringResource(R.string.settings_pages_customization_title),
                    style = sizing.textStyle(MaterialTheme.typography.titleLarge),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(sizing.spacing(48.dp)))
            }

            Text(
                text = stringResource(R.string.settings_pages_customization_desc),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(24.dp)),
                textAlign = TextAlign.Center
            )

            // 1. La page de démarrage
            NavigationMenuItem(title = stringResource(R.string.appstrings_startup_page_settings), icon = Icons.AutoMirrored.Filled.Launch, isSelected = false, isDark = isDark) { onStartupPageClick() }
            Spacer(Modifier.height(sizing.spacing(12.dp)))

            // 2. Les menus individuels des pages
            NavigationMenuItem(title = stringResource(R.string.appstrings_page_home_settings), icon = Icons.Default.Home, isSelected = false, isDark = isDark) { onHomeClick() }
            if (featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.NEARBY)) {
                NavigationMenuItem(title = stringResource(R.string.appstrings_page_nearby_settings), icon = Icons.Default.NearMe, isSelected = false, isDark = isDark) { onNearbyClick() }
            }
            if (featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.MAP)) {
                NavigationMenuItem(title = stringResource(R.string.appstrings_page_map_settings), icon = Icons.Default.Map, isSelected = false, isDark = isDark) { onMapClick() }
            }
            if (AppConfig.hasCompass.value && featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.COMPASS)) {
                NavigationMenuItem(title = stringResource(R.string.appstrings_page_compass_settings), icon = Icons.Default.Explore, isSelected = false, isDark = isDark) { onCompassClick() }
            }
            if (featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.STATS)) {
                NavigationMenuItem(title = stringResource(R.string.appstrings_stats_title), icon = Icons.Default.BarChart, isSelected = false, isDark = isDark) { onStatsClick() }
            }

            // --- NOUVELLES SECTIONS ---
            if (featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.SUPPORT_DETAIL)) {
                NavigationMenuItem(title = stringResource(R.string.appstrings_page_support_settings), icon = Icons.Default.VerticalAlignTop, isSelected = false, isDark = isDark) { onSupportClick() }
            }
            if (featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.SITE_DETAIL)) {
                NavigationMenuItem(title = stringResource(R.string.appstrings_page_site_settings), icon = Icons.Default.WifiTethering, isSelected = false, isDark = isDark) { onSiteClick() }
                NavigationMenuItem(title = stringResource(R.string.appstrings_page_speedtests_settings), icon = Icons.Default.Speed, isSelected = false, isDark = isDark) { onSpeedtestsClick() }
            }
            if (featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.THROUGHPUT_CALCULATOR)) {
                NavigationMenuItem(title = stringResource(R.string.appstrings_throughput_calculator_title), icon = Icons.Default.Speed, isSelected = false, isDark = isDark) { onThroughputCalculatorClick() }
            }
        }
    }
}

// === LE SOUS-MENU POUR CHOISIR LA PAGE DE DÉMARRAGE ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartupPageSelectionSheet(
    currentStartupPage: String,
    onPageSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit, // <-- NOUVEAU
    sheetState: SheetState,
    useOneUi: Boolean,
    bubbleColor: Color
) {
    val featureFlags by RemoteFeatureFlags.config
    val safeStartupPage = remember(currentStartupPage, featureFlags) {
        when {
            currentStartupPage == "nearby" && featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.NEARBY) -> currentStartupPage
            currentStartupPage == "map" && featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.MAP) -> currentStartupPage
            currentStartupPage == "compass" && featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.COMPASS) -> currentStartupPage
            currentStartupPage == "stats" && featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.STATS) -> currentStartupPage
            currentStartupPage == "home" -> currentStartupPage
            else -> "home"
        }
    }
    var tempPage by remember(safeStartupPage) { mutableStateOf(safeStartupPage) }
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val scrollState = rememberScrollState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        BackHandler(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .settingsPopupFadingEdge(scrollState)
                .verticalScroll(scrollState)
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(stringResource(R.string.appstrings_startup_page_settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
            Spacer(Modifier.height(24.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsRadioItem(stringResource(R.string.appstrings_page_home_settings), tempPage == "home", useOneUi, bubbleColor) { tempPage = "home" }
                if (featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.NEARBY)) {
                    SettingsRadioItem(stringResource(R.string.appstrings_page_nearby_settings), tempPage == "nearby", useOneUi, bubbleColor) { tempPage = "nearby" }
                }
                if (featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.MAP)) {
                    SettingsRadioItem(stringResource(R.string.appstrings_page_map_settings), tempPage == "map", useOneUi, bubbleColor) { tempPage = "map" }
                }
                if (AppConfig.hasCompass.value && featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.COMPASS)) {
                    SettingsRadioItem(stringResource(R.string.appstrings_page_compass_settings), tempPage == "compass", useOneUi, bubbleColor) { tempPage = "compass" }
                }

                if (featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.STATS)) {
                    SettingsRadioItem(stringResource(R.string.appstrings_stats_title), tempPage == "stats", useOneUi, bubbleColor) { tempPage = "stats" }
                }

                // --- NOUVEAU BOUTON RÉINITIALISER ---
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = {
                        // On valide directement le choix par défaut et on ferme la fenêtre
                        onPageSelected("home")
                        onDismiss()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.appstrings_reset_to_default), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())

                // Bouton Valider classique
                Button(
                    onClick = { onPageSelected(tempPage); onDismiss() },
                    modifier = Modifier.fillMaxWidth().height(50.dp).padding(top = 8.dp),
                    shape = oneUiActionButtonShape(useOneUi, RoundedCornerShape(25.dp))
                ) {
                    Text(stringResource(R.string.appstrings_validate), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Petit composant esthétique pour les sélections
@Composable
fun SettingsRadioItem(name: String, isSelected: Boolean, useOneUi: Boolean, bubbleColor: Color, onClick: () -> Unit) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val accentColor = MaterialTheme.colorScheme.primary
    val activeBg = accentColor.copy(alpha = 0.1f)
    val inactiveBg = if (useOneUi) bubbleColor else Color.Transparent
    val bgColor = if (isSelected) activeBg else inactiveBg
    val border = if (useOneUi) { if (isSelected) BorderStroke(sizing.component(2.dp), accentColor) else null } else { BorderStroke(sizing.component(if (isSelected) 2.dp else 1.dp), if (isSelected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) }
    val shape = if (useOneUi) RoundedCornerShape(sizing.component(22.dp)) else RoundedCornerShape(sizing.component(12.dp))

    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().height(sizing.component(60.dp)), color = bgColor, border = border, shape = shape) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = sizing.spacing(16.dp)), verticalAlignment = Alignment.CenterVertically) {
            Text(text = name, style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
            if (useOneUi) {
                Box(modifier = Modifier.size(sizing.component(24.dp)).border(sizing.component(2.dp), if (isSelected) accentColor else MaterialTheme.colorScheme.outline, CircleShape).padding(sizing.spacing(5.dp)), contentAlignment = Alignment.Center) {
                    if (isSelected) Box(modifier = Modifier.fillMaxSize().background(accentColor, CircleShape))
                }
            } else {
                RadioButton(selected = isSelected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = accentColor))
            }
        }
    }
}
// === LE SOUS-MENU POUR LA PAGE D'ACCUEIL ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSettingsSheet(
    pagesOrder: List<String>, onOrderChange: (List<String>) -> Unit,
    showNearby: Boolean, onNearbyChange: (Boolean) -> Unit,
    showMap: Boolean, onMapChange: (Boolean) -> Unit,
    showCompass: Boolean, onCompassChange: (Boolean) -> Unit,
    showStats: Boolean, onStatsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit, // <-- NOUVEAU
    sheetState: SheetState, useOneUi: Boolean, bubbleColor: Color
) {
    // ---> 1. LECTURE DE LA PRÉFÉRENCE DU LOGO <---
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
    var showLogo by remember { mutableStateOf(prefs.getBoolean("show_home_logo", true)) }
    var showHelpButton by remember { mutableStateOf(prefs.getBoolean("show_home_help", true)) }
    var helpButtonPosition by remember { mutableStateOf(prefs.getString("home_help_position", "bottom_end") ?: "bottom_end") }
    var showLogoSettings by remember { mutableStateOf(false) }
    var logoSelectorResetKey by remember { mutableStateOf(0) }
    var showHelpPositionSettings by remember { mutableStateOf(false) }
    val safeClick = rememberSafeClick()
    val featureFlags by RemoteFeatureFlags.config
    val sizing = LocalGeoTowerUiStyle.current.sizing

    // ---> 2. SÉCURITÉ ET RÉACTIVITÉ : Assure que le logo est dans la liste <---
    val safeOrder = remember(pagesOrder, featureFlags) {
        val withLogo = if (!pagesOrder.contains("logo")) pagesOrder + listOf("logo") else pagesOrder
        withLogo.filter { pageId ->
            when (pageId) {
                "nearby" -> featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.NEARBY)
                "map" -> featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.MAP)
                "compass" -> featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.COMPASS)
                "stats" -> featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.STATS)
                else -> true
            }
        }
    }
    // On mémorise la liste sécurisée en temps réel pour le glisser-déposer
    val currentOrder by rememberUpdatedState(safeOrder)

    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val scrollState = rememberScrollState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        BackHandler {
            when {
                showLogoSettings -> showLogoSettings = false
                showHelpPositionSettings -> showHelpPositionSettings = false
                else -> onBack()
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .settingsPopupFadingEdge(scrollState)
                .verticalScroll(scrollState)
                .padding(bottom = sizing.spacing(48.dp), start = sizing.spacing(24.dp), end = sizing.spacing(24.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showLogoSettings) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(24.dp)), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showLogoSettings = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                    Text(stringResource(R.string.appstrings_page_home_logo_settings), style = sizing.textStyle(MaterialTheme.typography.titleLarge), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Spacer(Modifier.width(sizing.spacing(48.dp)))
                }

                key(logoSelectorResetKey) {
                    fr.geotower.ui.components.HomeLogoSelectorBlock(
                        safeClick = safeClick
                    )
                }

                Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))
                TextButton(
                    onClick = {
                        prefs.edit().putString("home_logo_choice", "app").apply()
                        logoSelectorResetKey++
                    }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(sizing.spacing(8.dp)))
                    Text(stringResource(R.string.appstrings_reset_to_default), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(sizing.spacing(32.dp)).navigationBarsPadding())
            } else if (showHelpPositionSettings) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(24.dp)), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showHelpPositionSettings = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                    Text(stringResource(R.string.appstrings_home_help_position_settings), style = sizing.textStyle(MaterialTheme.typography.titleLarge), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Spacer(Modifier.width(sizing.spacing(48.dp)))
                }

                Column(verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))) {
                    val savePosition = { position: String ->
                        helpButtonPosition = position
                        prefs.edit().putString("home_help_position", position).apply()
                    }

                    SettingsRadioItem(stringResource(R.string.appstrings_position_top_left), helpButtonPosition == "top_start", useOneUi, bubbleColor) { savePosition("top_start") }
                    SettingsRadioItem(stringResource(R.string.appstrings_position_top_right), helpButtonPosition == "top_end", useOneUi, bubbleColor) { savePosition("top_end") }
                    SettingsRadioItem(stringResource(R.string.appstrings_position_bottom_left), helpButtonPosition == "bottom_start", useOneUi, bubbleColor) { savePosition("bottom_start") }
                    SettingsRadioItem(stringResource(R.string.appstrings_position_bottom_right), helpButtonPosition == "bottom_end", useOneUi, bubbleColor) { savePosition("bottom_end") }
                }

                Spacer(modifier = Modifier.height(sizing.spacing(32.dp)).navigationBarsPadding())
            } else {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                    Text(stringResource(R.string.appstrings_page_home_settings), style = sizing.textStyle(MaterialTheme.typography.titleLarge), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Spacer(Modifier.width(sizing.spacing(48.dp)))
                }
                Text(stringResource(R.string.appstrings_drag_to_reorder_hint), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = sizing.spacing(24.dp)), textAlign = TextAlign.Center)

                val cardHeight = sizing.component(64.dp)
                val spacing = sizing.spacing(12.dp)
                val reorderState = rememberReorderableDragState(currentOrder, cardHeight, spacing, onOrderChange)

                val shape = oneUiActionButtonShape(useOneUi)
                val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null

                Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                // --- MODIFICATION : On utilise 'currentOrder' au lieu de 'pagesOrder' partout ici ---
                currentOrder.forEach { pageId ->
                    key(pageId) {
                        val isDragged = reorderState.isDragged(pageId)
                        val dragModifier = reorderState.dragModifier(pageId)
                        val dragOffset = reorderState.offsetFor(pageId)

                        when (pageId) {
                            // ---> 3. AJOUT DU BLOC LOGO <---
                            "logo" -> DraggableSwitchCard(
                                stringResource(R.string.appstrings_page_home_logo_settings), showLogo,
                                { showLogo = it; prefs.edit().putBoolean("show_home_logo", it).apply() },
                                shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight,
                                onSettingsClick = { showLogoSettings = true }
                            )
                            "nearby" -> DraggableSwitchCard(stringResource(R.string.appstrings_page_nearby_settings), showNearby, onNearbyChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "map" -> DraggableSwitchCard(stringResource(R.string.appstrings_page_map_settings), showMap, onMapChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "compass" -> {
                                if (AppConfig.hasCompass.value) {
                                    DraggableSwitchCard(
                                        stringResource(R.string.appstrings_page_compass_settings), showCompass, onCompassChange,
                                        shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight
                                    )
                                }
                            }
                            "stats" -> DraggableSwitchCard(stringResource(R.string.appstrings_stats_group_title), showStats, onStatsChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)

                            // --- NOUVEAU : AJOUT DE LA CARTE PARAMÈTRES ---
                            "settings" -> DraggableSwitchCard(stringResource(R.string.appstrings_settings_title), true, {}, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight, hideSwitch = true)
                        }
                    }
                }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ConfigurableSwitchCard(
                        title = stringResource(R.string.appstrings_home_help_settings),
                        checked = showHelpButton,
                        onCheckedChange = {
                            showHelpButton = it
                            prefs.edit().putBoolean("show_home_help", it).apply()
                        },
                        onSettingsClick = { showHelpPositionSettings = true },
                        shape = shape,
                        border = border,
                        bubbleColor = bubbleColor,
                        useOneUi = useOneUi
                    )
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = {
                val defaultHomeOrder = if (AppConfig.hasCompass.value) {
                    listOf("nearby", "map", "compass", "stats", "settings", "logo") // Logo à la fin
                } else {
                    listOf("nearby", "map", "stats", "settings", "logo") // Logo à la fin
                }

                onOrderChange(defaultHomeOrder)
                onNearbyChange(true)
                onMapChange(true)
                if (AppConfig.hasCompass.value) onCompassChange(true)
                onStatsChange(true)

                // Réinitialise le logo
                showLogo = true
                showHelpButton = true
                helpButtonPosition = "bottom_end"
                prefs.edit()
                    .putBoolean("show_home_logo", true)
                    .putBoolean("show_home_help", true)
                    .putString("home_help_position", "bottom_end")
                    .putString("home_logo_choice", "app")
                    .apply()
            }) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(sizing.spacing(8.dp)))
                Text(stringResource(R.string.appstrings_reset_to_default), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(sizing.spacing(32.dp)).navigationBarsPadding())
        }
    }
}
}

// === COMPOSANT UTILITAIRE POUR TOUS LES GLISSER-DÉPOSER ===
@Composable
fun DraggableSwitchCard(
    title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    shape: androidx.compose.ui.graphics.Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean,
    dragModifier: Modifier, isDragged: Boolean, dragOffset: Float, height: Dp,
    hideSwitch: Boolean = false,
    onSettingsClick: (() -> Unit)? = null
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val themeMode by AppConfig.themeMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val switchColor = MaterialTheme.colorScheme.primary
    val paleBgColor = if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent

    Surface(
        shape = shape,
        border = border,
        color = if (isDragged) MaterialTheme.colorScheme.surfaceVariant else cardBg,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = height)
            .zIndex(if (isDragged) 10f else 0f)
            .graphicsLayer {
                translationY = if (isDragged) dragOffset else 0f
                scaleX = if (isDragged) 1.05f else 1f
                scaleY = if (isDragged) 1.05f else 1f
                shadowElevation = if (isDragged) sizing.component(8.dp).toPx() else 0f
                this.shape = shape
                clip = true
            }
            .then(dragModifier)
    ) {
        Row(modifier = Modifier.padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp)), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DragHandle, contentDescription = stringResource(R.string.appstrings_move), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(sizing.component(24.dp)))
            Spacer(modifier = Modifier.width(sizing.spacing(16.dp)))
            Text(
                text = title,
                style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            // ✅ AJOUT DE L'ENGRENAGE (S'affiche si on passe une action)
            if (onSettingsClick != null) {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.appstrings_settings_title),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(sizing.component(20.dp))
                    )
                }
            }

            // --- NOUVEAU : GESTION DU CADENAS VS SWITCH ---
            if (hideSwitch) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(sizing.component(20.dp))
                )
            } else {
                fr.geotower.ui.components.GeoTowerSwitch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    useOneUi = useOneUi,
                    checkedColor = switchColor
                )
            }
        }
    }
}

@Composable
fun ConfigurableSwitchCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val switchColor = MaterialTheme.colorScheme.primary
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent

    Surface(shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(sizing.component(56.dp))
                .padding(start = sizing.spacing(16.dp), end = sizing.spacing(16.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(sizing.component(40.dp)),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.appstrings_settings_title),
                    modifier = Modifier.size(sizing.component(22.dp))
                )
            }
            Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
            fr.geotower.ui.components.GeoTowerSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                useOneUi = useOneUi,
                checkedColor = switchColor
            )
        }
    }
}
// === LE SOUS-MENU POUR LA PAGE ANTENNES À PROXIMITÉ ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbySettingsSheet(
    nearbyOrder: List<String>, onOrderChange: (List<String>) -> Unit,
    showSearch: Boolean, onSearchChange: (Boolean) -> Unit,
    showSuggestions: Boolean, onSuggestionsChange: (Boolean) -> Unit,
    showSites: Boolean, onSitesChange: (Boolean) -> Unit,
    searchRadius: Int, onRadiusChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit, // <-- NOUVEAU
    sheetState: SheetState, useOneUi: Boolean, bubbleColor: Color
) {
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val scrollState = rememberScrollState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        BackHandler(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .settingsPopupFadingEdge(scrollState)
                .verticalScroll(scrollState)
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(stringResource(R.string.appstrings_page_nearby_settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
            Text(stringResource(R.string.appstrings_drag_to_reorder_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp), textAlign = TextAlign.Center)

            val shape = oneUiActionButtonShape(useOneUi)
            val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null

            // --- 1. LES BLOCS GLISSER/DÉPOSER ---
            ReorderableBlockList(
                order = nearbyOrder,
                blocks = listOf(
                    ConfigurableBlock("search", { stringResource(R.string.appstrings_nearby_search_option) }, showSearch, onSearchChange),
                    ConfigurableBlock("sites", { stringResource(R.string.appstrings_nearby_sites_option) }, showSites, onSitesChange)
                ),
                onOrderChange = onOrderChange,
                shape = shape,
                border = border,
                bubbleColor = bubbleColor,
                useOneUi = useOneUi
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))
            SimpleSwitchCard(
                title = stringResource(R.string.appstrings_nearby_search_suggestions_option),
                showMapLocation = showSuggestions,
                onLocationChange = onSuggestionsChange,
                shape = shape,
                border = border,
                bubbleColor = bubbleColor,
                useOneUi = useOneUi
            )
            Spacer(modifier = Modifier.height(24.dp))
            // --- NOUVEAU : BOUTON RÉINITIALISER L'ORDRE ---
            TextButton(
                onClick = {
                    onOrderChange(listOf("search", "sites"))
                    onSearchChange(true)
                    onSuggestionsChange(true)
                    onSitesChange(true)
                    onRadiusChange(5) // On remet le rayon par défaut (5 km)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.appstrings_reset_to_default),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())

            // --- 2. LE CURSEUR DU RAYON ---
            SearchRadiusCard(
                currentRadius = searchRadius,
                onValueChange = onRadiusChange,
                shape = shape,
                border = border,
                bubbleColor = bubbleColor,
                useOneUi = useOneUi
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchRadiusCard(
    currentRadius: Int,
    onValueChange: (Int) -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean
) {
    // 🚨 ON GARDE TOUTE LA LOGIQUE CI-DESSOUS INTACTE
    val steps = listOf(1, 2, 5, 10, 20, 30, 50)
    val labels = listOf("1 km", "2 km", "5 km", "10 km", "20 km", "30 km", "50 km")
    var currentIndex by remember { mutableFloatStateOf(steps.indexOf(currentRadius).coerceAtLeast(0).toFloat()) }
    val accentColor = MaterialTheme.colorScheme.primary
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent

    // 🚨 ON ENVELOPPE LE DESSIN DANS UN "if (false)"
    if (false) {
        Surface(shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.appstrings_search_radius_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(labels[currentIndex.toInt()], style = MaterialTheme.typography.titleMedium, color = accentColor, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (useOneUi) {
                    Slider(
                        value = currentIndex,
                        onValueChange = { currentIndex = it },
                        onValueChangeFinished = { onValueChange(steps[currentIndex.toInt()]) },
                        valueRange = 0f..(steps.size - 1).toFloat(),
                        steps = steps.size - 2,
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        },
                        track = { _ ->
                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
                                val trackColor = Color.Gray.copy(alpha = 0.3f)
                                val dotColor = Color.Gray.copy(alpha = 0.6f)
                                drawLine(color = trackColor, start = androidx.compose.ui.geometry.Offset(0f, size.height / 2), end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2), strokeWidth = 14.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                val dotCount = steps.size
                                val stepWidth = size.width / (dotCount - 1)
                                for (i in 0 until dotCount) {
                                    drawCircle(color = dotColor, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(i * stepWidth, size.height / 2))
                                }
                            }
                        }
                    )
                } else {
                    Slider(
                        value = currentIndex,
                        onValueChange = { currentIndex = it },
                        onValueChangeFinished = { onValueChange(steps[currentIndex.toInt()]) },
                        valueRange = 0f..(steps.size - 1).toFloat(),
                        steps = steps.size - 2
                    )
                }
            }
        }
    }
}
// === LE SOUS-MENU POUR LA PAGE BOUSSOLE ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassSettingsSheet(
    compassOrder: List<String>, onOrderChange: (List<String>) -> Unit,
    showLocation: Boolean, onLocationChange: (Boolean) -> Unit,
    showGps: Boolean, onGpsChange: (Boolean) -> Unit,
    showAccuracy: Boolean, onAccuracyChange: (Boolean) -> Unit,
    onDismiss: () -> Unit, onBack: () -> Unit,
    sheetState: SheetState, useOneUi: Boolean, bubbleColor: Color
) {
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val scrollState = rememberScrollState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        BackHandler(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .settingsPopupFadingEdge(scrollState)
                .verticalScroll(scrollState)
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(stringResource(R.string.appstrings_page_compass_settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
            Text(stringResource(R.string.appstrings_drag_to_reorder_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp), textAlign = TextAlign.Center)

            val shape = oneUiActionButtonShape(useOneUi)
            val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null

            ReorderableBlockList(
                order = compassOrder,
                blocks = listOf(
                    ConfigurableBlock("location", { stringResource(R.string.appstrings_compass_location_option) }, showLocation, onLocationChange),
                    ConfigurableBlock("gps", { stringResource(R.string.appstrings_compass_gps_option) }, showGps, onGpsChange),
                    ConfigurableBlock("accuracy", { stringResource(R.string.appstrings_compass_accuracy_option) }, showAccuracy, onAccuracyChange)
                ),
                onOrderChange = onOrderChange,
                shape = shape,
                border = border,
                bubbleColor = bubbleColor,
                useOneUi = useOneUi
            )

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = {
                onOrderChange(listOf("location", "gps", "accuracy"))
                onLocationChange(true)
                onGpsChange(true)
                onAccuracyChange(true)
            }) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.appstrings_reset_to_default), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
        }
    }
}
// === LE SOUS-MENU POUR LA CARTE DES ANTENNES ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSettingsSheet(
    showLocation: Boolean, onLocationChange: (Boolean) -> Unit,
    showLocationMarker: Boolean, onLocationMarkerChange: (Boolean) -> Unit,
    showAzimuths: Boolean, onAzimuthsChange: (Boolean) -> Unit,
    showAzimuthsCone: Boolean, onAzimuthsConeChange: (Boolean) -> Unit,
    showZoom: Boolean, onZoomChange: (Boolean) -> Unit,
    showToolbox: Boolean, onToolboxChange: (Boolean) -> Unit,
    showCompass: Boolean, onCompassChange: (Boolean) -> Unit,
    showScale: Boolean, onScaleChange: (Boolean) -> Unit,
    showAttribution: Boolean, onAttributionChange: (Boolean) -> Unit,
    showSpeedometer: Boolean, onSpeedometerChange: (Boolean) -> Unit,
    onDismiss: () -> Unit, onBack: () -> Unit,
    sheetState: SheetState, useOneUi: Boolean, bubbleColor: Color
) {
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val showAnyAzimuths = showAzimuths || showAzimuthsCone
    var showAzimuthSettings by remember { mutableStateOf(false) }

    fun setAzimuthsVisible(visible: Boolean) {
        if (visible) {
            if (!showAzimuths && !showAzimuthsCone) onAzimuthsChange(true)
        } else {
            onAzimuthsChange(false)
            onAzimuthsConeChange(false)
        }
    }
    val scrollState = rememberScrollState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        BackHandler {
            if (showAzimuthSettings) showAzimuthSettings = false else onBack()
        }
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .settingsPopupFadingEdge(scrollState)
                .verticalScroll(scrollState)
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (showAzimuthSettings) showAzimuthSettings = false else onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
                Text(
                    if (showAzimuthSettings) stringResource(R.string.appstrings_map_azimuths_option) else stringResource(R.string.appstrings_page_map_settings),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(48.dp))
            }

            val shape = oneUiActionButtonShape(useOneUi)
            val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null

            if (showAzimuthSettings) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SimpleSwitchCard(stringResource(R.string.appstrings_map_azimuth_lines_option), showMapLocation = showAzimuths, onLocationChange = onAzimuthsChange, shape = shape, border = border, bubbleColor = bubbleColor, useOneUi = useOneUi)
                    SimpleSwitchCard(stringResource(R.string.appstrings_map_azimuth_cones_option), showMapLocation = showAzimuthsCone, onLocationChange = onAzimuthsConeChange, shape = shape, border = border, bubbleColor = bubbleColor, useOneUi = useOneUi)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SimpleSwitchCard(stringResource(R.string.appstrings_map_location_option), showMapLocation = showLocation, onLocationChange = onLocationChange, shape = shape, border = border, bubbleColor = bubbleColor, useOneUi = useOneUi)
                    SimpleSwitchCard(stringResource(R.string.appstrings_map_location_marker_option), showMapLocation = showLocationMarker, onLocationChange = onLocationMarkerChange, shape = shape, border = border, bubbleColor = bubbleColor, useOneUi = useOneUi)
                    ConfigurableSwitchCard(stringResource(R.string.appstrings_map_azimuths_option), showAnyAzimuths, ::setAzimuthsVisible, { showAzimuthSettings = true }, shape, border, bubbleColor, useOneUi)
                    SimpleSwitchCard(stringResource(R.string.appstrings_map_zoom_option), showMapLocation = showZoom, onLocationChange = onZoomChange, shape = shape, border = border, bubbleColor = bubbleColor, useOneUi = useOneUi)
                    SimpleSwitchCard(stringResource(R.string.appstrings_map_toolbox_option), showMapLocation = showToolbox, onLocationChange = onToolboxChange, shape = shape, border = border, bubbleColor = bubbleColor, useOneUi = useOneUi)
                    if (AppConfig.hasCompass.value) {
                        SimpleSwitchCard(stringResource(R.string.appstrings_map_compass_option), showMapLocation = showCompass, onLocationChange = onCompassChange, shape = shape, border = border, bubbleColor = bubbleColor, useOneUi = useOneUi)
                    }
                    SimpleSwitchCard(stringResource(R.string.appstrings_show_speedometer), showMapLocation = showSpeedometer, onLocationChange = onSpeedometerChange, shape = shape, border = border, bubbleColor = bubbleColor, useOneUi = useOneUi)
                    SimpleSwitchCard(stringResource(R.string.appstrings_map_scale_option), showMapLocation = showScale, onLocationChange = onScaleChange, shape = shape, border = border, bubbleColor = bubbleColor, useOneUi = useOneUi)
                    SimpleSwitchCard(stringResource(R.string.appstrings_map_attribution_option), showMapLocation = showAttribution, onLocationChange = onAttributionChange, shape = shape, border = border, bubbleColor = bubbleColor, useOneUi = useOneUi)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = {
                if (showAzimuthSettings) {
                    onAzimuthsChange(AppConfig.DEFAULT_SHOW_AZIMUTH_LINES)
                    onAzimuthsConeChange(AppConfig.DEFAULT_SHOW_AZIMUTH_CONES)
                } else {
                    onLocationChange(true)
                    onLocationMarkerChange(true)
                    onAzimuthsChange(AppConfig.DEFAULT_SHOW_AZIMUTH_LINES)
                    onAzimuthsConeChange(AppConfig.DEFAULT_SHOW_AZIMUTH_CONES)
                    onZoomChange(true)
                    onToolboxChange(true)
                    onCompassChange(true)
                    onSpeedometerChange(true)
                    onScaleChange(true)
                    onAttributionChange(true)
                }
            }) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.appstrings_reset_to_default), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
        }
    }
}

@Composable
fun SimpleSwitchCard(title: String, showMapLocation: Boolean, onLocationChange: (Boolean) -> Unit, shape: androidx.compose.ui.graphics.Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val switchColor = MaterialTheme.colorScheme.primary
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent

    Surface(shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(12.dp)), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            fr.geotower.ui.components.GeoTowerSwitch(
                checked = showMapLocation,
                onCheckedChange = onLocationChange,
                useOneUi = useOneUi,
                checkedColor = switchColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteSpeedtestsSettingsSheet(
    filterMajorEnb: Boolean,
    onFilterMajorEnbChange: (Boolean) -> Unit,
    includeMissingEnb: Boolean,
    onIncludeMissingEnbChange: (Boolean) -> Unit,
    showSpeedtestsCount: Boolean,
    onShowSpeedtestsCountChange: (Boolean) -> Unit,
    showRadioDetails: Boolean,
    onShowRadioDetailsChange: (Boolean) -> Unit,
    showNetworkDetails: Boolean,
    onShowNetworkDetailsChange: (Boolean) -> Unit,
    showCoordinates: Boolean,
    onShowCoordinatesChange: (Boolean) -> Unit,
    bestMetric: String,
    onBestMetricChange: (String) -> Unit,
    sortMetric: String,
    onSortMetricChange: (String) -> Unit,
    sortDescending: Boolean,
    onSortDescendingChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    sheetState: SheetState,
    useOneUi: Boolean,
    bubbleColor: Color
) {
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val scrollState = rememberScrollState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        BackHandler(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .settingsPopupFadingEdge(scrollState)
                .verticalScroll(scrollState)
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(
                    text = stringResource(R.string.appstrings_speedtests_settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(48.dp))
            }

            val shape = oneUiActionButtonShape(useOneUi)
            val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.appstrings_speedtests_best_metric_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                SettingsRadioItem(
                    stringResource(R.string.appstrings_speedtests_best_average),
                    bestMetric == SiteSpeedtestsPagePreferences.SORT_AVERAGE,
                    useOneUi,
                    bubbleColor
                ) { onBestMetricChange(SiteSpeedtestsPagePreferences.SORT_AVERAGE) }
                SettingsRadioItem(
                    stringResource(R.string.appstrings_speedtests_best_max),
                    bestMetric == SiteSpeedtestsPagePreferences.SORT_MAX,
                    useOneUi,
                    bubbleColor
                ) { onBestMetricChange(SiteSpeedtestsPagePreferences.SORT_MAX) }
                SettingsRadioItem(
                    stringResource(R.string.appstrings_speedtests_best_download),
                    bestMetric == SiteSpeedtestsPagePreferences.SORT_DOWNLOAD,
                    useOneUi,
                    bubbleColor
                ) { onBestMetricChange(SiteSpeedtestsPagePreferences.SORT_DOWNLOAD) }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.appstrings_speedtests_sort_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                SettingsRadioItem(
                    stringResource(R.string.appstrings_speedtests_sort_average),
                    sortMetric == SiteSpeedtestsPagePreferences.SORT_AVERAGE,
                    useOneUi,
                    bubbleColor
                ) { onSortMetricChange(SiteSpeedtestsPagePreferences.SORT_AVERAGE) }
                SettingsRadioItem(
                    stringResource(R.string.appstrings_speedtests_sort_max),
                    sortMetric == SiteSpeedtestsPagePreferences.SORT_MAX,
                    useOneUi,
                    bubbleColor
                ) { onSortMetricChange(SiteSpeedtestsPagePreferences.SORT_MAX) }
                SettingsRadioItem(
                    stringResource(R.string.appstrings_speedtests_sort_download),
                    sortMetric == SiteSpeedtestsPagePreferences.SORT_DOWNLOAD,
                    useOneUi,
                    bubbleColor
                ) { onSortMetricChange(SiteSpeedtestsPagePreferences.SORT_DOWNLOAD) }
                SimpleSwitchCard(stringResource(R.string.appstrings_speedtests_sort_descending), sortDescending, onSortDescendingChange, shape, border, bubbleColor, useOneUi)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.appstrings_speedtests_display_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                SimpleSwitchCard(stringResource(R.string.appstrings_speedtests_filter_major_enb), filterMajorEnb, onFilterMajorEnbChange, shape, border, bubbleColor, useOneUi)
                SimpleSwitchCard(stringResource(R.string.appstrings_speedtests_include_missing_enb), includeMissingEnb, onIncludeMissingEnbChange, shape, border, bubbleColor, useOneUi)
                SimpleSwitchCard(stringResource(R.string.appstrings_speedtests_show_count), showSpeedtestsCount, onShowSpeedtestsCountChange, shape, border, bubbleColor, useOneUi)
                SimpleSwitchCard(stringResource(R.string.appstrings_speedtests_show_radio_details), showRadioDetails, onShowRadioDetailsChange, shape, border, bubbleColor, useOneUi)
                SimpleSwitchCard(stringResource(R.string.appstrings_speedtests_show_network_details), showNetworkDetails, onShowNetworkDetailsChange, shape, border, bubbleColor, useOneUi)
                SimpleSwitchCard(stringResource(R.string.appstrings_speedtests_show_coordinates), showCoordinates, onShowCoordinatesChange, shape, border, bubbleColor, useOneUi)
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = onReset) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.appstrings_reset_to_default), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun throughputBlockTitle(blockId: String): String {
    return ThroughputDisplayText.blockTitle(blockId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThroughputCalculatorSettingsSheet(
    showThroughputCalculator: Boolean,
    onThroughputCalculatorChange: (Boolean) -> Unit,
    throughputOrder: List<String>,
    onThroughputOrderChange: (List<String>) -> Unit,
    showHeader: Boolean,
    onHeaderChange: (Boolean) -> Unit,
    showSummary: Boolean,
    onSummaryChange: (Boolean) -> Unit,
    showCone: Boolean,
    onConeChange: (Boolean) -> Unit,
    showControls: Boolean,
    onControlsChange: (Boolean) -> Unit,
    showBands: Boolean,
    onBandsChange: (Boolean) -> Unit,
    showAssumptions: Boolean,
    onAssumptionsChange: (Boolean) -> Unit,
    onOpenCalculationDefaults: () -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    sheetState: SheetState,
    useOneUi: Boolean,
    bubbleColor: Color
) {
    ReorderableBlockSettingsSheet(
        title = stringResource(R.string.appstrings_throughput_calculator_title),
        order = ThroughputPrefs.normalizeBlockOrder(throughputOrder),
        blocks = listOf(
            ConfigurableBlock(ThroughputPrefs.BLOCK_HEADER, { throughputBlockTitle(ThroughputPrefs.BLOCK_HEADER) }, showHeader, onHeaderChange),
            ConfigurableBlock(ThroughputPrefs.BLOCK_SUMMARY, { throughputBlockTitle(ThroughputPrefs.BLOCK_SUMMARY) }, showSummary, onSummaryChange),
            ConfigurableBlock(ThroughputPrefs.BLOCK_CONE, { throughputBlockTitle(ThroughputPrefs.BLOCK_CONE) }, showCone, onConeChange),
            ConfigurableBlock(ThroughputPrefs.BLOCK_CONTROLS, { throughputBlockTitle(ThroughputPrefs.BLOCK_CONTROLS) }, showControls, onControlsChange, onSettingsClick = {
                onDismiss()
                onOpenCalculationDefaults()
            }),
            ConfigurableBlock(ThroughputPrefs.BLOCK_BANDS, { throughputBlockTitle(ThroughputPrefs.BLOCK_BANDS) }, showBands, onBandsChange),
            ConfigurableBlock(ThroughputPrefs.BLOCK_ASSUMPTIONS, { throughputBlockTitle(ThroughputPrefs.BLOCK_ASSUMPTIONS) }, showAssumptions, onAssumptionsChange)
        ),
        onOrderChange = onThroughputOrderChange,
        onReset = {
            onThroughputCalculatorChange(true)
            onThroughputOrderChange(ThroughputPrefs.defaultBlockOrder)
            onHeaderChange(true)
            onSummaryChange(true)
            onConeChange(true)
            onControlsChange(true)
            onBandsChange(true)
            onAssumptionsChange(true)
        },
        onDismiss = onDismiss,
        onBack = onBack,
        sheetState = sheetState,
        useOneUi = useOneUi,
        bubbleColor = bubbleColor,
        contentBeforeList = { shape, border ->
            SimpleSwitchCard(
                title = stringResource(R.string.appstrings_site_throughput_calculator_option),
                showMapLocation = showThroughputCalculator,
                onLocationChange = onThroughputCalculatorChange,
                shape = shape,
                border = border,
                bubbleColor = bubbleColor,
                useOneUi = useOneUi
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThroughputCalculationDefaultsSheet(
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    sheetState: SheetState,
    useOneUi: Boolean,
    bubbleColor: Color
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE) }
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val shape = oneUiActionButtonShape(useOneUi)
    val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null
    val scrollState = rememberScrollState()

    var include4G by remember { mutableStateOf(ThroughputPrefs.include4G.read(prefs)) }
    var include5G by remember { mutableStateOf(ThroughputPrefs.include5G.read(prefs)) }
    var includePlanned by remember { mutableStateOf(ThroughputPrefs.includePlanned.read(prefs)) }
    var bandSelection by remember {
        mutableStateOf(
            throughputBandDefaults
                .flatMap { it.bands }
                .associate { band -> band.prefSuffix to prefs.getBoolean(ThroughputPrefs.bandVisiblePrefKey(band.prefSuffix), true) }
        )
    }

    fun saveBool(key: String, value: Boolean, update: (Boolean) -> Unit) {
        update(value)
        prefs.edit().putBoolean(key, value).apply()
    }

    fun saveBand(prefSuffix: String, value: Boolean) {
        bandSelection = bandSelection + (prefSuffix to value)
        prefs.edit().putBoolean(ThroughputPrefs.bandVisiblePrefKey(prefSuffix), value).apply()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        BackHandler(onBack = onBack)
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .settingsPopupFadingEdge(scrollState)
                .verticalScroll(scrollState)
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(
                    stringResource(R.string.appstrings_throughput_calculation_settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(48.dp))
            }

            SimpleSwitchCard(stringResource(R.string.appstrings_throughput_include4g), include4G, { saveBool(ThroughputPrefs.include4G.key, it) { value -> include4G = value } }, shape, border, bubbleColor, useOneUi)
            Spacer(Modifier.height(8.dp))
            SimpleSwitchCard(stringResource(R.string.appstrings_throughput_include5g), include5G, { saveBool(ThroughputPrefs.include5G.key, it) { value -> include5G = value } }, shape, border, bubbleColor, useOneUi)
            Spacer(Modifier.height(8.dp))
            SimpleSwitchCard(stringResource(R.string.appstrings_throughput_include_planned), includePlanned, { saveBool(ThroughputPrefs.includePlanned.key, it) { value -> includePlanned = value } }, shape, border, bubbleColor, useOneUi)

            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.appstrings_throughput_default_frequency_bands_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            throughputBandDefaults.forEach { group ->
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)
                )
                group.bands.forEach { band ->
                    SimpleSwitchCard(
                        title = band.label,
                        showMapLocation = bandSelection[band.prefSuffix] != false,
                        onLocationChange = { saveBand(band.prefSuffix, it) },
                        shape = shape,
                        border = border,
                        bubbleColor = bubbleColor,
                        useOneUi = useOneUi
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = {
                include4G = true
                include5G = true
                includePlanned = false
                bandSelection = throughputBandDefaults.flatMap { it.bands }.associate { it.prefSuffix to true }
                val editor = prefs.edit()
                    .putString(ThroughputPrefs.DEFAULT_PRESET, ThroughputPrefs.DEFAULT_PRESET_VALUE)
                    .putInt(ThroughputPrefs.CUSTOM_LTE_DOWN, 3)
                    .putInt(ThroughputPrefs.CUSTOM_LTE_UP, 2)
                    .putInt(ThroughputPrefs.CUSTOM_NR_DOWN, 3)
                    .putInt(ThroughputPrefs.CUSTOM_NR_UP, 2)
                    .putBoolean(ThroughputPrefs.include4G.key, true)
                    .putBoolean(ThroughputPrefs.include5G.key, true)
                    .putBoolean(ThroughputPrefs.includePlanned.key, false)
                throughputBandDefaults.flatMap { it.bands }.forEach { band ->
                    editor.putBoolean(ThroughputPrefs.bandVisiblePrefKey(band.prefSuffix), true)
                }
                editor.apply()
            }) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.appstrings_reset_to_default), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
        }
    }
}

private data class ThroughputBandDefaultGroup(
    val title: String,
    val bands: List<ThroughputBandDefault>
)

private data class ThroughputBandDefault(
    val prefSuffix: String,
    val label: String
)

private val throughputBandDefaults = listOf(
    ThroughputBandDefaultGroup(
        title = "4G",
        bands = listOf(
            ThroughputBandDefault("4g_2600", "2600 MHz (B7)"),
            ThroughputBandDefault("4g_2100", "2100 MHz (B1)"),
            ThroughputBandDefault("4g_1800", "1800 MHz (B3)"),
            ThroughputBandDefault("4g_900", "900 MHz (B8)"),
            ThroughputBandDefault("4g_800", "800 MHz (B20)"),
            ThroughputBandDefault("4g_700", "700 MHz (B28)")
        )
    ),
    ThroughputBandDefaultGroup(
        title = "5G",
        bands = listOf(
            ThroughputBandDefault("5g_26000", "26 GHz (exp) (N258)"),
            ThroughputBandDefault("5g_4200", "4200 MHz (exp) (N77)"),
            ThroughputBandDefault("5g_3500", "3500 MHz (N78)"),
            ThroughputBandDefault("5g_2100", "2100 MHz (N1)"),
            ThroughputBandDefault("5g_1400", "1400 MHz (exp) (N75)"),
            ThroughputBandDefault("5g_700", "700 MHz (N28)")
        )
    )
)

data class ConfigurableBlock(
    val id: String,
    val title: @Composable () -> String,
    val visible: Boolean,
    val onVisibilityChange: (Boolean) -> Unit,
    val isAvailable: Boolean = true,
    val hideSwitch: Boolean = false,
    val onSettingsClick: (() -> Unit)? = null
)

@Composable
private fun ReorderableBlockList(
    order: List<String>,
    blocks: List<ConfigurableBlock>,
    onOrderChange: (List<String>) -> Unit,
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean,
    cardHeight: Dp = 64.dp,
    spacing: Dp = 12.dp
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val scaledCardHeight = sizing.component(cardHeight)
    val scaledSpacing = sizing.spacing(spacing)
    val currentOrder by rememberUpdatedState(order)
    val reorderState = rememberReorderableDragState(currentOrder, scaledCardHeight, scaledSpacing, onOrderChange)
    val blocksById = blocks.associateBy { it.id }

    Column(verticalArrangement = Arrangement.spacedBy(scaledSpacing)) {
        currentOrder.forEach { blockId ->
            val block = blocksById[blockId]
            if (block != null && block.isAvailable) {
                key(blockId) {
                    DraggableSwitchCard(
                        block.title(),
                        block.visible,
                        block.onVisibilityChange,
                        shape,
                        border,
                        bubbleColor,
                        useOneUi,
                        reorderState.dragModifier(blockId),
                        reorderState.isDragged(blockId),
                        reorderState.offsetFor(blockId),
                        scaledCardHeight,
                        hideSwitch = block.hideSwitch,
                        onSettingsClick = block.onSettingsClick
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderableBlockSettingsSheet(
    title: String,
    order: List<String>,
    blocks: List<ConfigurableBlock>,
    onOrderChange: (List<String>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    sheetState: SheetState,
    useOneUi: Boolean,
    bubbleColor: Color,
    contentBeforeList: @Composable ColumnScope.(Shape, BorderStroke?) -> Unit = { _, _ -> },
    contentAfterReset: @Composable ColumnScope.(Shape, BorderStroke?) -> Unit = { _, _ -> }
) {
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val scrollState = rememberScrollState()
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val shape = oneUiActionButtonShape(useOneUi)
    val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBgColor,
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BottomSheetDefaults.DragHandle(modifier = Modifier.padding(top = sizing.spacing(8.dp), bottom = sizing.spacing(4.dp)))
            }
        }
    ) {
        BackHandler(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .settingsPopupFadingEdge(scrollState)
                .verticalScroll(scrollState)
                .padding(bottom = sizing.spacing(48.dp), start = sizing.spacing(24.dp), end = sizing.spacing(24.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(title, style = sizing.textStyle(MaterialTheme.typography.titleLarge), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(sizing.spacing(48.dp)))
            }
            contentBeforeList(shape, border)
            Text(stringResource(R.string.appstrings_drag_to_reorder_hint), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = sizing.spacing(24.dp)), textAlign = TextAlign.Center)

            ReorderableBlockList(
                order = order,
                blocks = blocks,
                onOrderChange = onOrderChange,
                shape = shape,
                border = border,
                bubbleColor = bubbleColor,
                useOneUi = useOneUi
            )

            Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))
            TextButton(onClick = onReset) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(sizing.spacing(8.dp)))
                Text(stringResource(R.string.appstrings_reset_to_default), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            contentAfterReset(shape, border)
            Spacer(modifier = Modifier.height(sizing.spacing(32.dp)).navigationBarsPadding())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportSettingsSheet(
    supportOrder: List<String>, onOrderChange: (List<String>) -> Unit,
    showMap: Boolean, onMapChange: (Boolean) -> Unit,
    showDetails: Boolean, onDetailsChange: (Boolean) -> Unit,
    showPhotos: Boolean, onPhotosChange: (Boolean) -> Unit,
    showOpenMap: Boolean, onOpenMapChange: (Boolean) -> Unit,
    showNav: Boolean, onNavChange: (Boolean) -> Unit,
    showShare: Boolean, onShareChange: (Boolean) -> Unit,
    showOperators: Boolean, onOperatorsChange: (Boolean) -> Unit,
    onOpenMiniMapSettings: () -> Unit,
    onOpenPhotosSettings: () -> Unit,
    onDismiss: () -> Unit, onBack: () -> Unit,
    sheetState: SheetState, useOneUi: Boolean, bubbleColor: Color
) {
    ReorderableBlockSettingsSheet(
        title = stringResource(R.string.appstrings_page_support_settings),
        order = supportOrder,
        blocks = listOf(
            ConfigurableBlock("map", { stringResource(R.string.appstrings_support_map_option) }, showMap, onMapChange, onSettingsClick = onOpenMiniMapSettings),
            ConfigurableBlock("details", { stringResource(R.string.appstrings_support_details_option) }, showDetails, onDetailsChange),
            ConfigurableBlock("photos", { stringResource(R.string.appstrings_support_photos_option) }, showPhotos, onPhotosChange, onSettingsClick = {
                onDismiss()
                onOpenPhotosSettings()
            }),
            ConfigurableBlock("open_map", { stringResource(R.string.appstrings_support_open_map_option) }, showOpenMap, onOpenMapChange),
            ConfigurableBlock("nav", { stringResource(R.string.appstrings_support_nav_option) }, showNav, onNavChange),
            ConfigurableBlock("share", { stringResource(R.string.appstrings_support_share_option) }, showShare, onShareChange),
            ConfigurableBlock("operators", { stringResource(R.string.appstrings_support_operators_option) }, showOperators, onOperatorsChange)
        ),
        onOrderChange = onOrderChange,
        onReset = {
            onOrderChange(SupportPagePrefs.defaultOrder)
            onMapChange(true)
            onDetailsChange(true)
            onPhotosChange(true)
            onOpenMapChange(true)
            onNavChange(true)
            onShareChange(true)
            onOperatorsChange(true)
        },
        onDismiss = onDismiss,
        onBack = onBack,
        sheetState = sheetState,
        useOneUi = useOneUi,
        bubbleColor = bubbleColor
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteSettingsSheet(
    siteOrder: List<String>, onOrderChange: (List<String>) -> Unit,
    showOperator: Boolean, onOperatorChange: (Boolean) -> Unit,
    showBearingHeight: Boolean, onBearingHeightChange: (Boolean) -> Unit,
    showMap: Boolean, onMapChange: (Boolean) -> Unit,
    showSupportDetails: Boolean, onSupportDetailsChange: (Boolean) -> Unit,
    showPhotos: Boolean, onPhotosChange: (Boolean) -> Unit,
    showPanelHeights: Boolean, onPanelHeightsChange: (Boolean) -> Unit,
    showIds: Boolean, onIdsChange: (Boolean) -> Unit,
    showOpenMap: Boolean, onOpenMapChange: (Boolean) -> Unit,
    showElevationProfile: Boolean, onElevationProfileChange: (Boolean) -> Unit,
    showThroughputCalculator: Boolean, onThroughputCalculatorChange: (Boolean) -> Unit,
    showNav: Boolean, onNavChange: (Boolean) -> Unit,
    showShare: Boolean, onShareChange: (Boolean) -> Unit,
    showDates: Boolean, onDatesChange: (Boolean) -> Unit,
    showAddress: Boolean, onAddressChange: (Boolean) -> Unit,
    showStatus: Boolean, onStatusChange: (Boolean) -> Unit,
    showSpeedtest: Boolean, onSpeedtestChange: (Boolean) -> Unit,
    showFreqs: Boolean, onFreqsChange: (Boolean) -> Unit,
    showLinks: Boolean, onLinksChange: (Boolean) -> Unit,
    onOpenMiniMapSettings: () -> Unit,
    onOpenFrequencies: () -> Unit,
    onOpenPhotosSettings: () -> Unit,
    onOpenSpeedtestSettings: () -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    sheetState: SheetState,
    useOneUi: Boolean,
    bubbleColor: Color
) {
    ReorderableBlockSettingsSheet(
        title = stringResource(R.string.appstrings_page_site_settings),
        order = siteOrder,
        blocks = listOf(
            ConfigurableBlock("operator", { stringResource(R.string.appstrings_site_operator_option) }, showOperator, onOperatorChange),
            ConfigurableBlock("bearing_height", { stringResource(R.string.appstrings_site_bearing_height_option) }, showBearingHeight, onBearingHeightChange, isAvailable = AppConfig.hasCompass.value),
            ConfigurableBlock("map", { stringResource(R.string.appstrings_site_map_option) }, showMap, onMapChange, onSettingsClick = onOpenMiniMapSettings),
            ConfigurableBlock("support_details", { stringResource(R.string.appstrings_site_support_details_option) }, showSupportDetails, onSupportDetailsChange),
            ConfigurableBlock("photos", { stringResource(R.string.appstrings_site_photos_and_schemes_option) }, showPhotos, onPhotosChange, onSettingsClick = {
                onDismiss()
                onOpenPhotosSettings()
            }),
            ConfigurableBlock("ids", { stringResource(R.string.appstrings_site_ids_option) }, showIds, onIdsChange),
            ConfigurableBlock("open_map", { stringResource(R.string.appstrings_site_open_map_option) }, showOpenMap, onOpenMapChange),
            ConfigurableBlock("elevation_profile", { stringResource(R.string.appstrings_site_elevation_profile_option) }, showElevationProfile, onElevationProfileChange),
            ConfigurableBlock("throughput_calculator", { stringResource(R.string.appstrings_site_throughput_calculator_option) }, showThroughputCalculator, onThroughputCalculatorChange),
            ConfigurableBlock("nav", { stringResource(R.string.appstrings_site_nav_option) }, showNav, onNavChange),
            ConfigurableBlock("share", { stringResource(R.string.appstrings_site_share_option) }, showShare, onShareChange),
            ConfigurableBlock("dates", { stringResource(R.string.appstrings_site_dates_option) }, showDates, onDatesChange),
            ConfigurableBlock("address", { stringResource(R.string.appstrings_site_address_option) }, showAddress, onAddressChange),
            ConfigurableBlock("status", { stringResource(R.string.appstrings_show_status_option) }, showStatus, onStatusChange),
            ConfigurableBlock("speedtest", { stringResource(R.string.appstrings_show_speedtest_label) }, showSpeedtest, onSpeedtestChange, onSettingsClick = {
                onDismiss()
                onOpenSpeedtestSettings()
            }),
            ConfigurableBlock("freqs", { stringResource(R.string.appstrings_site_freqs_option) }, showFreqs, onFreqsChange, onSettingsClick = {
                onDismiss()
                onOpenFrequencies()
            }),
            ConfigurableBlock("links", { stringResource(R.string.appstrings_site_links_option) }, showLinks, onLinksChange)
        ),
        onOrderChange = onOrderChange,
        onReset = {
            onOrderChange(SitePagePrefs.defaultOrder)
            onOperatorChange(true)
            onBearingHeightChange(true)
            onMapChange(true)
            onSupportDetailsChange(true)
            onPhotosChange(true)
            onSpeedtestChange(true)
            onPanelHeightsChange(true)
            onIdsChange(true)
            onOpenMapChange(true)
            onElevationProfileChange(true)
            onThroughputCalculatorChange(true)
            onNavChange(true)
            onShareChange(true)
            onDatesChange(true)
            onAddressChange(true)
            onStatusChange(true)
            onFreqsChange(true)
            onLinksChange(true)
        },
        onDismiss = onDismiss,
        onBack = onBack,
        sheetState = sheetState,
        useOneUi = useOneUi,
        bubbleColor = bubbleColor
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniMapSettingsSheet(
    selectedMode: MiniMapViewMode,
    onModeChange: (MiniMapViewMode) -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    sheetState: SheetState,
    useOneUi: Boolean,
    bubbleColor: Color
) {
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val shape = oneUiActionButtonShape(useOneUi)
    val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBgColor,
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BottomSheetDefaults.DragHandle(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            }
        }
    ) {
        BackHandler(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(
                    stringResource(R.string.appstrings_mini_map_settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(48.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(LocalGeoTowerUiStyle.current.sizing.spacing(12.dp)), modifier = Modifier.fillMaxWidth()) {
                MiniMapModeOption(
                    title = stringResource(R.string.appstrings_mini_map_antenna_centered),
                    selected = selectedMode == MiniMapViewMode.AntennaCentered,
                    shape = shape,
                    border = border,
                    cardBg = cardBg
                ) {
                    onModeChange(MiniMapViewMode.AntennaCentered)
                }
                MiniMapModeOption(
                    title = stringResource(R.string.appstrings_mini_map_user_to_antenna),
                    selected = selectedMode == MiniMapViewMode.UserToAntenna,
                    shape = shape,
                    border = border,
                    cardBg = cardBg
                ) {
                    onModeChange(MiniMapViewMode.UserToAntenna)
                }
            }
        }
    }
}

@Composable
private fun MiniMapModeOption(
    title: String,
    selected: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    border: BorderStroke?,
    cardBg: Color,
    onClick: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Surface(
        onClick = onClick,
        shape = shape,
        border = border,
        color = cardBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(14.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun statsSettingsBlockTitle(blockId: String): String {
    return when (blockId) {
        "supports" -> stringResource(R.string.appstrings_stats_supports_title)
        "5G" -> stringResource(R.string.appstrings_stats5_g_title)
        "4G" -> stringResource(R.string.appstrings_stats4_g_title)
        "3G" -> stringResource(R.string.appstrings_stats3_g_title)
        "2G" -> stringResource(R.string.appstrings_stats2_g_title)
        else -> blockId
    }
}

@Composable
private fun statsDisplayModeTitle(mode: StatsDisplayMode): String {
    return when (mode) {
        StatsDisplayMode.Sites -> stringResource(R.string.appstrings_sites_label)
        StatsDisplayMode.Active -> stringResource(R.string.appstrings_active_sites_label)
        StatsDisplayMode.Both -> stringResource(R.string.appstrings_active_declared_sites_label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsSettingsSheet(
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    sheetState: SheetState,
    useOneUi: Boolean,
    bubbleColor: Color
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val shape = oneUiActionButtonShape(useOneUi)
    val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null
    val scrollState = rememberScrollState()

    var selectedFrequencyTech by remember { mutableStateOf<String?>(null) }
    var displayMode by remember { mutableStateOf(StatsPreferences.displayMode(prefs)) }
    var statsOrder by remember { mutableStateOf(StatsPreferences.statsBlockOrder(prefs)) }
    var statsFreqOrder5G by remember { mutableStateOf(StatsPreferences.statsFrequencyOrder(prefs, "5G")) }
    var statsFreqOrder4G by remember { mutableStateOf(StatsPreferences.statsFrequencyOrder(prefs, "4G")) }
    var statsFreqOrder3G by remember { mutableStateOf(StatsPreferences.statsFrequencyOrder(prefs, "3G")) }
    var statsFreqOrder2G by remember { mutableStateOf(StatsPreferences.statsFrequencyOrder(prefs, "2G")) }

    val blockVisibility = remember {
        mutableStateMapOf<String, Boolean>().apply {
            StatsPreferences.defaultStatsBlockOrder.forEach { blockId ->
                put(blockId, StatsPreferences.isStatsBlockVisible(prefs, blockId))
            }
        }
    }
    val frequencyVisibility = remember {
        mutableStateMapOf<String, Boolean>().apply {
            StatsPreferences.defaultTechOrder.forEach { tech ->
                StatsPreferences.defaultFrequencyOrder(tech).forEach { frequencyId ->
                    put("$tech|$frequencyId", StatsPreferences.isStatsFrequencyVisible(prefs, tech, frequencyId))
                }
            }
        }
    }

    fun saveDisplayMode(newMode: StatsDisplayMode) {
        displayMode = newMode
        AppConfig.statsDisplayMode.value = newMode
        prefs.edit().putString(StatsPreferences.PREF_DISPLAY_MODE, newMode.storageKey).apply()
    }

    fun saveStatsOrder(newOrder: List<String>) {
        val normalized = StatsPreferences.normalizeStatsBlockOrder(newOrder)
        statsOrder = normalized
        prefs.edit().putString(StatsPreferences.PREF_STATS_ORDER, normalized.joinToString(",")).apply()
    }

    fun saveStatsBlockVisibility(blockId: String, visible: Boolean) {
        blockVisibility[blockId] = visible
        prefs.edit().putBoolean(StatsPreferences.statsBlockVisiblePrefKey(blockId), visible).apply()
    }

    fun frequencyOrderFor(tech: String): List<String> {
        return when (tech) {
            "5G" -> statsFreqOrder5G
            "4G" -> statsFreqOrder4G
            "3G" -> statsFreqOrder3G
            "2G" -> statsFreqOrder2G
            else -> emptyList()
        }
    }

    fun setFrequencyOrder(tech: String, newOrder: List<String>) {
        when (tech) {
            "5G" -> statsFreqOrder5G = newOrder
            "4G" -> statsFreqOrder4G = newOrder
            "3G" -> statsFreqOrder3G = newOrder
            "2G" -> statsFreqOrder2G = newOrder
        }
    }

    fun saveFrequencyOrder(tech: String, newOrder: List<String>) {
        val normalized = StatsPreferences.normalizeFrequencyOrder(tech, newOrder)
        setFrequencyOrder(tech, normalized)
        prefs.edit().putString(StatsPreferences.statsFrequencyOrderPrefKey(tech), normalized.joinToString(",")).apply()
    }

    fun saveFrequencyVisibility(tech: String, frequencyId: String, visible: Boolean) {
        frequencyVisibility["$tech|$frequencyId"] = visible
        prefs.edit().putBoolean(StatsPreferences.statsFrequencyVisiblePrefKey(tech, frequencyId), visible).apply()
    }

    fun resetFrequencySettings(tech: String) {
        val normalizedTech = StatsPreferences.normalizeTech(tech)
        val defaultOrder = StatsPreferences.defaultFrequencyOrder(normalizedTech)
        setFrequencyOrder(normalizedTech, defaultOrder)
        val editor = prefs.edit()
            .putString(StatsPreferences.statsFrequencyOrderPrefKey(normalizedTech), defaultOrder.joinToString(","))
        defaultOrder.forEach { frequencyId ->
            frequencyVisibility["$normalizedTech|$frequencyId"] = true
            editor.putBoolean(StatsPreferences.statsFrequencyVisiblePrefKey(normalizedTech, frequencyId), true)
        }
        editor.apply()
    }

    fun resetStatsSettings() {
        val editor = prefs.edit()
            .putString(StatsPreferences.PREF_DISPLAY_MODE, StatsDisplayMode.Both.storageKey)
            .putString(StatsPreferences.PREF_STATS_ORDER, StatsPreferences.defaultStatsBlockOrder.joinToString(","))

        displayMode = StatsDisplayMode.Both
        AppConfig.statsDisplayMode.value = StatsDisplayMode.Both
        statsOrder = StatsPreferences.defaultStatsBlockOrder
        StatsPreferences.defaultStatsBlockOrder.forEach { blockId ->
            blockVisibility[blockId] = true
            editor.putBoolean(StatsPreferences.statsBlockVisiblePrefKey(blockId), true)
        }

        StatsPreferences.defaultTechOrder.forEach { tech ->
            val defaultOrder = StatsPreferences.defaultFrequencyOrder(tech)
            setFrequencyOrder(tech, defaultOrder)
            editor.putString(StatsPreferences.statsFrequencyOrderPrefKey(tech), defaultOrder.joinToString(","))
            defaultOrder.forEach { frequencyId ->
                frequencyVisibility["$tech|$frequencyId"] = true
                editor.putBoolean(StatsPreferences.statsFrequencyVisiblePrefKey(tech, frequencyId), true)
            }
        }
        editor.apply()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        BackHandler {
            if (selectedFrequencyTech != null) {
                selectedFrequencyTech = null
            } else {
                onBack()
            }
        }
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .settingsPopupFadingEdge(scrollState)
                .verticalScroll(scrollState)
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        if (selectedFrequencyTech != null) {
                            selectedFrequencyTech = null
                        } else {
                            onBack()
                        }
                    }
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(
                    text = selectedFrequencyTech?.let { "${stringResource(R.string.appstrings_frequencies_title)} $it" }
                        ?: stringResource(R.string.appstrings_stats_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(48.dp))
            }

            val frequencyTech = selectedFrequencyTech
            if (frequencyTech == null) {
                Text(
                    text = stringResource(R.string.appstrings_stats_display_mode_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatsDisplayMode.values().forEach { mode ->
                        SettingsRadioItem(statsDisplayModeTitle(mode), displayMode == mode, useOneUi, bubbleColor) {
                            saveDisplayMode(mode)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.appstrings_drag_to_reorder_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp), textAlign = TextAlign.Center)

                ReorderableBlockList(
                    order = statsOrder,
                    blocks = StatsPreferences.defaultStatsBlockOrder.map { blockId ->
                        ConfigurableBlock(
                            id = blockId,
                            title = { statsSettingsBlockTitle(blockId) },
                            visible = blockVisibility[blockId] ?: true,
                            onVisibilityChange = { saveStatsBlockVisibility(blockId, it) },
                            onSettingsClick = if (blockId in StatsPreferences.defaultTechOrder) {
                                { selectedFrequencyTech = blockId }
                            } else {
                                null
                            }
                        )
                    },
                    onOrderChange = ::saveStatsOrder,
                    shape = shape,
                    border = border,
                    bubbleColor = bubbleColor,
                    useOneUi = useOneUi
                )

                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = ::resetStatsSettings) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.appstrings_reset_to_default), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            } else {
                StatsFrequencySettingsContent(
                    tech = frequencyTech,
                    frequencyOrder = frequencyOrderFor(frequencyTech),
                    frequencyVisibility = frequencyVisibility,
                    useOneUi = useOneUi,
                    onOrderChange = { saveFrequencyOrder(frequencyTech, it) },
                    onVisibilityChange = { frequencyId, visible -> saveFrequencyVisibility(frequencyTech, frequencyId, visible) },
                    onReset = { resetFrequencySettings(frequencyTech) }
                )
            }
            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun StatsFrequencySettingsContent(
    tech: String,
    frequencyOrder: List<String>,
    frequencyVisibility: Map<String, Boolean>,
    useOneUi: Boolean,
    onOrderChange: (List<String>) -> Unit,
    onVisibilityChange: (String, Boolean) -> Unit,
    onReset: () -> Unit
) {
    Text(
        text = stringResource(R.string.appstrings_drag_to_reorder_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 16.dp),
        textAlign = TextAlign.Center
    )

    val freqDragState = rememberReorderableDragState(
        items = frequencyOrder,
        itemHeight = 48.dp,
        onOrderChange = onOrderChange
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            frequencyOrder.forEach { frequencyId ->
                key("$tech-$frequencyId") {
                    val isDragged = freqDragState.isDragged(frequencyId)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragged) freqDragState.offsetFor(frequencyId) else 0f
                                scaleX = if (isDragged) 1.02f else 1f
                                scaleY = if (isDragged) 1.02f else 1f
                            }
                            .background(
                                if (isDragged) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .then(freqDragState.dragModifier(frequencyId))
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = StatsPreferences.frequencyLabel(frequencyId),
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isDragged) FontWeight.Bold else FontWeight.Normal
                        )
                        fr.geotower.ui.components.GeoTowerSwitch(
                            checked = frequencyVisibility["$tech|$frequencyId"] ?: true,
                            onCheckedChange = { onVisibilityChange(frequencyId, it) },
                            modifier = Modifier.scale(if (useOneUi) 0.85f else 0.8f),
                            useOneUi = useOneUi,
                            checkedColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    TextButton(onClick = onReset) {
        Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.appstrings_reset_to_default), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteFreqFiltersSheet(
    onDismiss: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
    val switchColor = MaterialTheme.colorScheme.primary
    val useOneUi = AppConfig.useOneUiDesign
    val technoDragState = rememberReorderableDragState(
        items = AppConfig.siteTechnoOrder.value,
        itemHeight = 80.dp,
        onOrderChange = { newOrder ->
            AppConfig.siteTechnoOrder.value = newOrder
            prefs.edit().putString("site_techno_order", newOrder.joinToString(",")).apply()
        }
    )
    val emptyFreqOrderState = remember { mutableStateOf(emptyList<String>()) }
    val scrollState = rememberScrollState()

    val txtMinOneTechno = stringResource(R.string.appstrings_min_one_techno_warning)
    val txtMinOneFreq = stringResource(R.string.appstrings_min_one_freq_warning)

    fun saveBool(key: String, state: MutableState<Boolean>, value: Boolean) {
        state.value = value
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getActiveMobileTechnosCount(): Int {
        return listOf(
            AppConfig.siteShowTechno5G.value,
            AppConfig.siteShowTechno4G.value,
            AppConfig.siteShowTechno3G.value,
            AppConfig.siteShowTechno2G.value
        ).count { it }
    }
    fun getTotalActiveFrequenciesCount(): Int {
        return listOf(
            AppConfig.siteF5G_4200.value, AppConfig.siteF5G_3500.value, AppConfig.siteF5G_2100.value, AppConfig.siteF5G_1400.value, AppConfig.siteF5G_700.value,
            AppConfig.siteF4G_2600.value, AppConfig.siteF4G_2100.value, AppConfig.siteF4G_1800.value,
            AppConfig.siteF4G_900.value, AppConfig.siteF4G_800.value, AppConfig.siteF4G_700.value,
            AppConfig.siteF3G_2100.value, AppConfig.siteF3G_900.value,
            AppConfig.siteF2G_1800.value, AppConfig.siteF2G_900.value
        ).count { it }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        BackHandler(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .settingsPopupFadingEdge(scrollState)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Text(
                    text = stringResource(R.string.appstrings_site_freq_filters_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(48.dp))
            }

            // ✅ NOUVEAU : BOUTON AFFICHAGE EN GRILLE
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.GridView, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.appstrings_freq_grid_display_option),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        fontSize = 16.sp
                    )

                    val onGridChange = { newValue: Boolean ->
                        saveBool("site_freq_grid_display", AppConfig.siteFreqGridDisplay, newValue)
                    }

                    fr.geotower.ui.components.GeoTowerSwitch(
                        checked = AppConfig.siteFreqGridDisplay.value,
                        onCheckedChange = onGridChange,
                        useOneUi = useOneUi,
                        checkedColor = switchColor
                    )
                }
            }

            AppConfig.siteTechnoOrder.value.forEach { technoId ->
                key(technoId) {
                    val isTechnoDragged = technoDragState.isDragged(technoId)
                    val technoDragOffset = technoDragState.offsetFor(technoId)
                    val technoData = when(technoId) {
                        "5G" -> Triple("5G (NR)", AppConfig.siteShowTechno5G, "site_show_techno_5g") to (listOf("26000" to AppConfig.siteF5G_26000, "4200" to AppConfig.siteF5G_4200, "3500" to AppConfig.siteF5G_3500, "2100" to AppConfig.siteF5G_2100, "1400" to AppConfig.siteF5G_1400, "700" to AppConfig.siteF5G_700) to AppConfig.siteFreqOrder5G)
                        "4G" -> Triple("4G (LTE)", AppConfig.siteShowTechno4G, "site_show_techno_4g") to (listOf("2600" to AppConfig.siteF4G_2600, "2100" to AppConfig.siteF4G_2100, "1800" to AppConfig.siteF4G_1800, "900" to AppConfig.siteF4G_900, "800" to AppConfig.siteF4G_800, "700" to AppConfig.siteF4G_700) to AppConfig.siteFreqOrder4G)
                        "3G" -> Triple("3G (UMTS)", AppConfig.siteShowTechno3G, "site_show_techno_3g") to (listOf("2100" to AppConfig.siteF3G_2100, "900" to AppConfig.siteF3G_900) to AppConfig.siteFreqOrder3G)
                        "2G" -> Triple("2G (GSM)", AppConfig.siteShowTechno2G, "site_show_techno_2g") to (listOf("1800" to AppConfig.siteF2G_1800, "900" to AppConfig.siteF2G_900) to AppConfig.siteFreqOrder2G)
                        else -> Triple("FH", AppConfig.siteShowTechnoFH, "site_show_techno_fh") to (emptyList<Pair<String, MutableState<Boolean>>>() to emptyFreqOrderState)
                    }

                    @Suppress("UNCHECKED_CAST")
                    val title = technoData.first.first
                    @Suppress("UNCHECKED_CAST")
                    val technoState = technoData.first.second
                    @Suppress("UNCHECKED_CAST")
                    val technoKey = technoData.first.third
                    @Suppress("UNCHECKED_CAST")
                    val freqList = technoData.second.first
                    @Suppress("UNCHECKED_CAST")
                    val freqOrder = technoData.second.second

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            .zIndex(if (isTechnoDragged) 1f else 0f)
                            .graphicsLayer { translationY = if (isTechnoDragged) technoDragOffset else 0f }
                            .then(technoDragState.dragModifier(technoId)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DragHandle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), fontSize = 16.sp)
                                val onTechnoChange = { newValue: Boolean ->
                                    if (!newValue) {
                                        val remainingFreqs = getTotalActiveFrequenciesCount() - freqList.count { it.second.value }
                                        if (technoId != "FH" && getActiveMobileTechnosCount() <= 1) {
                                            android.widget.Toast.makeText(context, txtMinOneTechno, android.widget.Toast.LENGTH_SHORT).show()
                                        } else if (technoId != "FH" && remainingFreqs < 1) {
                                            android.widget.Toast.makeText(context, txtMinOneFreq, android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            saveBool(technoKey, technoState, false)
                                            freqList.forEach { saveBool("site_f${technoId.lowercase()}_${it.first}", it.second, false) }
                                        }
                                    } else {
                                        saveBool(technoKey, technoState, true)
                                        freqList.forEach { saveBool("site_f${technoId.lowercase()}_${it.first}", it.second, true) }
                                    }
                                }
                                fr.geotower.ui.components.GeoTowerSwitch(
                                    checked = technoState.value,
                                    onCheckedChange = onTechnoChange,
                                    useOneUi = useOneUi,
                                    checkedColor = switchColor
                                )
                            }
                            AnimatedVisibility(visible = technoState.value && freqList.isNotEmpty()) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    val freqDragState = rememberReorderableDragState(
                                        items = freqOrder.value,
                                        itemHeight = 48.dp,
                                        onOrderChange = { newOrder ->
                                            freqOrder.value = newOrder
                                            prefs.edit().putString("site_freq_${technoId.lowercase()}_order", newOrder.joinToString(",")).apply()
                                        }
                                    )
                                    freqOrder.value.forEach { freqLabel ->
                                        key(freqLabel) {
                                            val freqStatePair = freqList.find { it.first == freqLabel }
                                            if (freqStatePair != null) {
                                                val freqState = freqStatePair.second
                                                val isFreqDragged = freqDragState.isDragged(freqLabel)
                                                val freqDragOffset = freqDragState.offsetFor(freqLabel)
                                                val freqPrefKey = "site_f${technoId.lowercase()}_${freqLabel}"
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth().zIndex(if (isFreqDragged) 1f else 0f)
                                                        .graphicsLayer { translationY = if (isFreqDragged) freqDragOffset else 0f; scaleX = if (isFreqDragged) 1.02f else 1f; scaleY = if (isFreqDragged) 1.02f else 1f }
                                                        .background(if (isFreqDragged) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(8.dp))
                                                        .then(freqDragState.dragModifier(freqLabel))
                                                        .padding(vertical = 4.dp, horizontal = 4.dp)
                                                ) {
                                                    Icon(Icons.Default.DragHandle, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
                                                    val displayFreqLabel = freqLabel.toIntOrNull()?.let(::radioFrequencyLabel) ?: "$freqLabel MHz"
                                                    Text(displayFreqLabel, modifier = Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = if(isFreqDragged) FontWeight.Bold else FontWeight.Normal)
                                                    val onFreqChange = { newValue: Boolean ->
                                                        if (!newValue && getTotalActiveFrequenciesCount() <= 1) {
                                                            android.widget.Toast.makeText(context, txtMinOneFreq, android.widget.Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            saveBool(freqPrefKey, freqState, newValue)
                                                            if (!newValue && freqList.count { it.second.value } == 0) saveBool(technoKey, technoState, false)
                                                            else if (newValue && !technoState.value) saveBool(technoKey, technoState, true)
                                                        }
                                                    }
                                                    fr.geotower.ui.components.GeoTowerSwitch(
                                                        checked = freqState.value,
                                                        onCheckedChange = onFreqChange,
                                                        modifier = Modifier.scale(if (useOneUi) 0.85f else 0.8f),
                                                        useOneUi = useOneUi,
                                                        checkedColor = switchColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.appstrings_spectrum_title), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        val onSpectrumChange = { newValue: Boolean ->
                            saveBool("site_show_spectrum", AppConfig.siteShowSpectrum, newValue)
                            saveBool("site_show_spectrum_band", AppConfig.siteShowSpectrumBand, newValue)
                            saveBool("site_show_spectrum_total", AppConfig.siteShowSpectrumTotal, newValue)
                        }
                        fr.geotower.ui.components.GeoTowerSwitch(
                            checked = AppConfig.siteShowSpectrum.value,
                            onCheckedChange = onSpectrumChange,
                            useOneUi = useOneUi,
                            checkedColor = switchColor
                        )
                    }
                    AnimatedVisibility(visible = AppConfig.siteShowSpectrum.value) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.appstrings_spectrum_by_band), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                val onBandChange = { newValue: Boolean ->
                                    saveBool("site_show_spectrum_band", AppConfig.siteShowSpectrumBand, newValue)
                                    if (!newValue && !AppConfig.siteShowSpectrumTotal.value) saveBool("site_show_spectrum", AppConfig.siteShowSpectrum, false)
                                    else if (newValue && !AppConfig.siteShowSpectrum.value) saveBool("site_show_spectrum", AppConfig.siteShowSpectrum, true)
                                }
                                fr.geotower.ui.components.GeoTowerSwitch(
                                    checked = AppConfig.siteShowSpectrumBand.value,
                                    onCheckedChange = onBandChange,
                                    modifier = Modifier.scale(if (useOneUi) 0.85f else 0.8f),
                                    useOneUi = useOneUi,
                                    checkedColor = switchColor
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.appstrings_totalspectrum), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                val onTotalChange = { newValue: Boolean ->
                                    saveBool("site_show_spectrum_total", AppConfig.siteShowSpectrumTotal, newValue)
                                    if (!newValue && !AppConfig.siteShowSpectrumBand.value) saveBool("site_show_spectrum", AppConfig.siteShowSpectrum, false)
                                    else if (newValue && !AppConfig.siteShowSpectrum.value) saveBool("site_show_spectrum", AppConfig.siteShowSpectrum, true)
                                }
                                fr.geotower.ui.components.GeoTowerSwitch(
                                    checked = AppConfig.siteShowSpectrumTotal.value,
                                    onCheckedChange = onTotalChange,
                                    modifier = Modifier.scale(if (useOneUi) 0.85f else 0.8f),
                                    useOneUi = useOneUi,
                                    checkedColor = switchColor
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(
                onClick = {
                    AppConfig.siteTechnoOrder.value = listOf("5G", "4G", "3G", "2G", "FH")
                    AppConfig.siteFreqOrder5G.value = listOf("26000", "4200", "3500", "2100", "1400", "700")
                    AppConfig.siteFreqOrder4G.value = listOf("2600", "2100", "1800", "900", "800", "700")
                    AppConfig.siteFreqOrder3G.value = listOf("2100", "900")
                    AppConfig.siteFreqOrder2G.value = listOf("1800", "900")
                    prefs.edit()
                        .putString("site_techno_order", "5G,4G,3G,2G,FH")
                        .putString("site_freq_5g_order", "26000,4200,3500,2100,1400,700")
                        .putString("site_freq_4g_order", "2600,2100,1800,900,800,700")
                        .putString("site_freq_3g_order", "2100,900")
                        .putString("site_freq_2g_order", "1800,900")
                        .apply()
                    saveBool("site_show_techno_5g", AppConfig.siteShowTechno5G, true)
                    saveBool("site_show_techno_4g", AppConfig.siteShowTechno4G, true)
                    saveBool("site_show_techno_3g", AppConfig.siteShowTechno3G, true)
                    saveBool("site_show_techno_2g", AppConfig.siteShowTechno2G, true)
                    saveBool("site_show_techno_fh", AppConfig.siteShowTechnoFH, true)
                    saveBool("site_f5g_3500", AppConfig.siteF5G_3500, true)
                    saveBool("site_f5g_4200", AppConfig.siteF5G_4200, true)
                    saveBool("site_f5g_2100", AppConfig.siteF5G_2100, true)
                    saveBool("site_f5g_1400", AppConfig.siteF5G_1400, true)
                    saveBool("site_f5g_700", AppConfig.siteF5G_700, true)
                    saveBool("site_f5g_26000", AppConfig.siteF5G_26000, true)
                    saveBool("site_f4g_2600", AppConfig.siteF4G_2600, true)
                    saveBool("site_f4g_2100", AppConfig.siteF4G_2100, true)
                    saveBool("site_f4g_1800", AppConfig.siteF4G_1800, true)
                    saveBool("site_f4g_900", AppConfig.siteF4G_900, true)
                    saveBool("site_f4g_800", AppConfig.siteF4G_800, true)
                    saveBool("site_f4g_700", AppConfig.siteF4G_700, true)
                    saveBool("site_f3g_2100", AppConfig.siteF3G_2100, true)
                    saveBool("site_f3g_900", AppConfig.siteF3G_900, true)
                    saveBool("site_f2g_1800", AppConfig.siteF2G_1800, true)
                    saveBool("site_f2g_900", AppConfig.siteF2G_900, true)
                    saveBool("site_show_spectrum", AppConfig.siteShowSpectrum, true)
                    saveBool("site_show_spectrum_band", AppConfig.siteShowSpectrumBand, true)
                    saveBool("site_show_spectrum_total", AppConfig.siteShowSpectrumTotal, true)
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.appstrings_reset_to_default), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitePhotosSettingsSheet(
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    photosVisible: Boolean,
    onPhotosVisibilityChange: (Boolean) -> Unit,
    onOpenCommunityDataSettings: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
    val switchColor = MaterialTheme.colorScheme.primary
    val useOneUi = AppConfig.useOneUiDesign
    val scrollState = rememberScrollState()
    val featureFlags by RemoteFeatureFlags.config

    fun saveBool(key: String, state: androidx.compose.runtime.MutableState<Boolean>, value: Boolean) {
        state.value = value
        prefs.edit().putBoolean(key, value).apply()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        BackHandler(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .settingsPopupFadingEdge(scrollState)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Text(
                    text = stringResource(R.string.appstrings_site_photos_settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(48.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.appstrings_site_photos_and_schemes_option), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), fontSize = 16.sp)
                        val onMasterChange = { newValue: Boolean ->
                            onPhotosVisibilityChange(newValue)
                        }
                        fr.geotower.ui.components.GeoTowerSwitch(
                            checked = photosVisible,
                            onCheckedChange = onMasterChange,
                            useOneUi = useOneUi,
                            checkedColor = switchColor
                        )
                    }
                    
                    androidx.compose.animation.AnimatedVisibility(visible = photosVisible) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            
                            // Schematics
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.appstrings_show_schemes_label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                fr.geotower.ui.components.GeoTowerSwitch(
                                    checked = AppConfig.siteShowSchemes.value,
                                    onCheckedChange = { saveBool("site_show_schemes", AppConfig.siteShowSchemes, it) },
                                    modifier = Modifier.scale(if (useOneUi) 0.85f else 0.8f),
                                    useOneUi = useOneUi,
                                    checkedColor = switchColor
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.appstrings_show_exif_label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                fr.geotower.ui.components.GeoTowerSwitch(
                                    checked = AppConfig.siteShowPhotoExif.value,
                                    onCheckedChange = { saveBool("site_show_photo_exif", AppConfig.siteShowPhotoExif, it) },
                                    modifier = Modifier.scale(if (useOneUi) 0.85f else 0.8f),
                                    useOneUi = useOneUi,
                                    checkedColor = switchColor
                                )
                            }
                        }
                    }
                }
            }

            if (featureFlags.isMenuEnabled(RemoteFeatureFlags.Menus.COMMUNITY_DATA_SETTINGS)) {
                Card(
                    onClick = onOpenCommunityDataSettings,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.appstrings_photo_sources_settings_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = stringResource(R.string.appstrings_photo_sources_settings_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp).size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(
                onClick = {
                    onPhotosVisibilityChange(true)
                    saveBool("site_show_schemes", AppConfig.siteShowSchemes, true)
                    saveBool("site_show_photo_exif", AppConfig.siteShowPhotoExif, true)
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.appstrings_reset_to_default), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
        }
    }
}
