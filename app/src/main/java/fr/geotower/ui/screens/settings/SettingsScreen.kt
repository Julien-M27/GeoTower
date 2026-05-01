@file:OptIn(ExperimentalMaterial3Api::class)
package fr.geotower.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppIconManager
import fr.geotower.utils.AppStrings
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Place
import androidx.compose.foundation.layout.navigationBarsPadding

@Composable
fun SettingsScreen(
    navController: NavController,
    repository: AnfrRepository
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var forceOneUi by AppConfig.forceOneUiTheme
    var themeMode by AppConfig.themeMode
    var isOledMode by AppConfig.isOledMode
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val showHomeLogo = remember { prefs.getBoolean("show_home_logo", true) }
    var showUnitSheet by remember { mutableStateOf(false) }


    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)

    val useOneUi = forceOneUi
    val isOled = isOledMode

    val cardShape = if (useOneUi) RoundedCornerShape(28.dp) else RoundedCornerShape(12.dp)
    val cardBorder = if (useOneUi) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

    val bubbleBaseColor = if (useOneUi) {
        if (isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    } else {
        Color.Transparent
    }

    val mainBgColor = if (isDark) {
        if (isOled) Color.Black else MaterialTheme.colorScheme.background
    } else {
        MaterialTheme.colorScheme.background
    }

    val packageInfo = remember { try { context.packageManager.getPackageInfo(context.packageName, 0) } catch (e: Exception) { null } }
    val versionName = packageInfo?.versionName ?: "1.0.0"
    val isWideScreen = configuration.screenWidthDp >= 600

    var lastClickTime by remember { mutableLongStateOf(0L) }
    fun safeClick(action: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > 700L) {
            lastClickTime = currentTime
            action()
        }
    }

    var isBlurEnabled by AppConfig.isBlurEnabled
    var mapProvider by AppConfig.mapProvider
    var ignStyle by AppConfig.ignStyle
    var defaultOperator by AppConfig.defaultOperator
    val navMode = AppConfig.navMode.intValue
    var activeSectionIndex by remember { mutableIntStateOf(0) }

    var appLanguage by remember { mutableStateOf(prefs.getString("app_language", "Français") ?: "Français") }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showOperatorSheet by remember { mutableStateOf(false) }
    var showIconSheet by remember { mutableStateOf(false) }


    // --- VARIABLES POUR LE PARTAGE ---
    var showShareSelectorSheet by remember { mutableStateOf(false) } // LE NOUVEAU SOUS-MENU
    var showSharePrefsSheet by remember { mutableStateOf(false) } // Fenêtre Antenne
    var showSupportSharePrefsSheet by remember { mutableStateOf(false) } // Fenêtre Pylône
    var showMapSharePrefsSheet by remember { mutableStateOf(false) } // ✅ AJOUT : Fenêtre Carte
    var showGlobalResetDialog by remember { mutableStateOf(false) }

    // ✅ AJOUT : Variables de la Carte
    var shareMapAzimuths by remember { mutableStateOf(prefs.getBoolean("share_map_azimuths", true)) }
    var shareMapSpeedometer by remember { mutableStateOf(prefs.getBoolean("share_map_speedometer", true)) }
    var shareMapScale by remember { mutableStateOf(prefs.getBoolean("share_map_scale", true)) }
    var shareMapAttribution by remember { mutableStateOf(prefs.getBoolean("share_map_attribution", true)) }
    var shareMapConfidential by remember { mutableStateOf(prefs.getBoolean("share_map_confidential", false)) }

    // 1. Variables de l'Antenne (Site)
    var shareMapEnabled by remember { mutableStateOf(prefs.getBoolean("share_map_enabled", true)) }
    var shareSupportEnabled by remember { mutableStateOf(prefs.getBoolean("share_support_enabled", true)) }
    var shareHeightsEnabled by remember { mutableStateOf(prefs.getBoolean("share_heights_enabled", true)) }
    var shareIdsEnabled by remember { mutableStateOf(prefs.getBoolean("share_ids_enabled", true)) }
    var shareDatesEnabled by remember { mutableStateOf(prefs.getBoolean("share_dates_enabled", true)) }
    var shareAddressEnabled by remember { mutableStateOf(prefs.getBoolean("share_address_enabled", true)) }
    var shareSpeedtestEnabled by remember { mutableStateOf(prefs.getBoolean("share_speedtest_enabled", true)) } // 🚨 NEW
    var shareFreqEnabled by remember { mutableStateOf(prefs.getBoolean("share_freq_enabled", true)) }
    var shareConfidentialEnabled by remember { mutableStateOf(prefs.getBoolean("share_confidential_enabled", false)) }
    var shareSiteQrEnabled by remember { mutableStateOf(prefs.getBoolean("share_site_qr_enabled", true)) }
    var shareSupQrEnabled by remember { mutableStateOf(prefs.getBoolean("share_sup_qr_enabled", true)) }
    var shareOrder by remember {
        mutableStateOf(
            (prefs.getString("share_order", "map,support,ids,dates,address,speedtest,status,freq") ?: "map,support,ids,dates,address,speedtest,status,freq")
                .split(",")
                .toMutableList()
                .apply { if (!contains("speedtest")) { val idx = indexOf("address"); if (idx >= 0) add(idx + 1, "speedtest") else add("speedtest") } }
                .toList()
        )
    }

    // 2. Variables du Pylône (Support) - SEULEMENT 3 BLOCS !
    var shareSupMapEnabled by remember { mutableStateOf(prefs.getBoolean("share_sup_map_enabled", true)) }
    var shareSupSupportEnabled by remember { mutableStateOf(prefs.getBoolean("share_sup_support_enabled", true)) }
    var shareSupOperatorsEnabled by remember { mutableStateOf(prefs.getBoolean("share_sup_operators_enabled", true)) }
    var shareSupConfidentialEnabled by remember { mutableStateOf(prefs.getBoolean("share_sup_confidential_enabled", false)) }
    var shareSupOrder by remember { mutableStateOf(prefs.getString("share_sup_order", "map,support,operators")!!.split(",")) }

    // --- VARIABLES POUR LA VISIBILITÉ DES PAGES ---
    var showNearbyPage by AppConfig.showNearbyPage
    var showMapPage by AppConfig.showMapPage
    var showCompassPage by AppConfig.showCompassPage
    var showStatsPage by AppConfig.showStatsPage

    var showMapScale by remember { mutableStateOf(prefs.getBoolean("show_map_scale", true)) }
    var showMapAttribution by remember { mutableStateOf(prefs.getBoolean("show_map_attribution", true)) }
    var showMapSpeedometer by remember { mutableStateOf(prefs.getBoolean("show_speedometer", true)) }
    var showMapSettingsSheet by remember { mutableStateOf(false) }
    var showMapLocation by remember { mutableStateOf(prefs.getBoolean("show_map_location", true)) }
    var showMapZoom by remember { mutableStateOf(prefs.getBoolean("show_map_zoom", true)) }
    var showMapToolbox by remember { mutableStateOf(prefs.getBoolean("show_map_toolbox", true)) }
    var showMapCompass by remember { mutableStateOf(prefs.getBoolean("show_map_compass", true)) }

    var showCompassSettingsSheet by remember { mutableStateOf(false) }
    var compassOrder by remember { mutableStateOf(prefs.getString("compass_order", "location,gps,accuracy")!!.split(",")) }
    var showCompassLocation by remember { mutableStateOf(prefs.getBoolean("show_compass_location", true)) }
    var showCompassGps by remember { mutableStateOf(prefs.getBoolean("show_compass_gps", true)) }
    var showCompassAccuracy by remember { mutableStateOf(prefs.getBoolean("show_compass_accuracy", true)) }

    // --- Variables d'état pour le Pylône et l'Antenne ---
    var showSupportSettingsSheet by remember { mutableStateOf(false) }
    var showSiteSettingsSheet by remember { mutableStateOf(false) }
    var showPhotosSettingsSheet by remember { mutableStateOf(false) }
    var pageSupportOrder by remember { mutableStateOf(prefs.getString("page_support_order", "map,details,photos,nav,share,operators")!!.split(",")) }
    var pageSupportMap by remember { mutableStateOf(prefs.getBoolean("page_support_map", true)) }
    var pageSupportDetails by remember { mutableStateOf(prefs.getBoolean("page_support_details", true)) }
    var pageSupportPhotos by remember { mutableStateOf(prefs.getBoolean("page_support_photos", true)) }
    var pageSupportNav by remember { mutableStateOf(prefs.getBoolean("page_support_nav", true)) }
    var pageSupportShare by remember { mutableStateOf(prefs.getBoolean("page_support_share", true)) }
    var pageSupportOperators by remember { mutableStateOf(prefs.getBoolean("page_support_operators", true)) }

    // --- Variables d'état pour l'Antenne (Site) ---
    var pageSiteOrder by remember {
        mutableStateOf(
            (prefs.getString("page_site_order", "operator,bearing_height,map,support_details,photos,speedtest,nav,share,panel_heights,ids,dates,address,status,freqs,links") ?: "operator,bearing_height,map,support_details,photos,speedtest,nav,share,panel_heights,ids,dates,address,status,freqs,links")
                .split(",")
                .toMutableList()
                .apply { if (!contains("speedtest")) { val idx = indexOf("photos"); if (idx >= 0) add(idx + 1, "speedtest") else add("speedtest") } }
                .toList()
        )
    };    var pageSiteOperator by remember { mutableStateOf(prefs.getBoolean("page_site_operator", true)) }
    var pageSiteBearingHeight by remember { mutableStateOf(prefs.getBoolean("page_site_bearing_height", true)) }
    var pageSiteMap by remember { mutableStateOf(prefs.getBoolean("page_site_map", true)) }
    var pageSiteSupportDetails by remember { mutableStateOf(prefs.getBoolean("page_site_support_details", true)) }
    var pageSitePanelHeights by remember { mutableStateOf(prefs.getBoolean("page_site_panel_heights", true)) }
    var pageSiteIds by remember { mutableStateOf(prefs.getBoolean("page_site_ids", true)) }
    var pageSiteNav by remember { mutableStateOf(prefs.getBoolean("page_site_nav", true)) }
    var pageSiteShare by remember { mutableStateOf(prefs.getBoolean("page_site_share", true)) }
    var pageSiteDates by remember { mutableStateOf(prefs.getBoolean("page_site_dates", true)) }
    var pageSiteAddress by remember { mutableStateOf(prefs.getBoolean("page_site_address", true)) }
    var pageSiteFreqs by remember { mutableStateOf(prefs.getBoolean("page_site_freqs", true)) }
    var pageSiteLinks by remember { mutableStateOf(prefs.getBoolean("page_site_links", true)) }

    var showPagesCustomizationSheet by remember { mutableStateOf(false) }
    var showFrequenciesSheet by remember { mutableStateOf(false) }
    var showExternalLinksSheet by remember { mutableStateOf(false) }
    var showStartupPageSheet by remember { mutableStateOf(false) }
    // On préparera les autres (showHomeSettingsSheet, etc.) dans la prochaine étape

    // La sauvegarde de la page de démarrage
    var startupPage by remember { mutableStateOf(prefs.getString("startup_page", "home") ?: "home") }
    var showHomeSettingsSheet by remember { mutableStateOf(false) }
    var pagesOrder by remember {
        mutableStateOf(
            (prefs.getString("pages_order", "nearby,map,compass,stats,settings") ?: "nearby,map,compass,stats,settings")
                .let { if (!it.contains("settings")) "$it,settings" else it }
                .split(",")
        )
    }
    var showNearbySettingsSheet by remember { mutableStateOf(false) }
    var nearbyOrder by remember { mutableStateOf(prefs.getString("nearby_order", "search,sites")!!.split(",")) }
    var showSearchBar by remember { mutableStateOf(prefs.getBoolean("show_search_bar", true)) }
    var showNearbySites by remember { mutableStateOf(prefs.getBoolean("show_nearby_sites", true)) }
    var nearbySearchRadius by remember { mutableIntStateOf(prefs.getInt("nearby_search_radius", 5)) } // Par défaut 5 km

    LaunchedEffect(Unit) {
        AppConfig.menuSize.value = prefs.getString("menuSize", "normal") ?: "normal"
        showNearbyPage = prefs.getBoolean("show_nearby_page", true)
        showMapPage = prefs.getBoolean("show_map_page", true)
        showCompassPage = prefs.getBoolean("show_compass_page", true)
        showStatsPage = prefs.getBoolean("show_stats_page", true)
    }

    val logoResId by AppIconManager.currentIconRes
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val menuItems = listOf(
        Triple(AppStrings.appearance, Icons.Outlined.Palette, 0),
        Triple(AppStrings.mapping, Icons.Outlined.Map, 1),
        Triple(AppStrings.preferences, Icons.Outlined.Tune, 2),
        Triple(AppStrings.system, Icons.Outlined.Settings, 3),
        Triple(AppStrings.database, Icons.Outlined.Storage, 4)
    )

    if (isWideScreen && navMode == 0) {
        LaunchedEffect(scrollState.value, scrollState.maxValue) {
            val max = scrollState.maxValue.toFloat()
            if (max > 0) {
                val ratio = scrollState.value / max
                activeSectionIndex = when { ratio >= 0.98f -> 4; ratio < 0.20f -> 0; ratio < 0.40f -> 1; ratio < 0.70f -> 2; ratio < 0.95f -> 3; else -> 4 }
            }
        }
    }

    Scaffold(
        containerColor = mainBgColor,
        topBar = {
            if (!isWideScreen) {
                // On remet la vraie barre supérieure pour les téléphones !
                SettingsTopBar(onBack = { safeClick { navController.popBackStack() } })
            }
        }
    ) { innerPadding ->
        // 🚀 NOUVEL AFFICHAGE QUI UTILISE LE COMPOSANT COMMUN
        fr.geotower.ui.components.ResponsiveDualPaneLayout(
            // 🚨 CORRECTION 1 : On utilise uniquement le padding du haut
            modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
            // ✅ AJOUT : onCloseSidebar
            sidebar = { width, onCloseSidebar ->
                Row(modifier = Modifier.width(width).fillMaxHeight().background(mainBgColor)) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            // 🚨 CORRECTION 2 : Marge pour les boutons de navigation
                            .navigationBarsPadding()
                            .padding(top = 16.dp, bottom = 16.dp)
                    ) {
                        // ✅ RETOUR DU ROW AVEC LES DEUX BOUTONS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { safeClick { navController.popBackStack() } },
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(
                                onClick = onCloseSidebar,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        menuItems.forEach { (title, icon, index) ->
                            NavigationMenuItem(title, icon, activeSectionIndex == index, isDark) {
                                activeSectionIndex = index
                                if (navMode == 0) {
                                    val target = when (index) { 0 -> 0; 1 -> 300; 2 -> 600; 3 -> 1000; else -> 1650 }
                                    scope.launch { scrollState.animateScrollTo(target) }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        NavigationMenuItem(AppStrings.about, Icons.Outlined.Info, false, isDark) {
                            safeClick { navController.navigate("about") { launchSingleTop = true } }
                        }
                        Spacer(Modifier.height(8.dp))
                        NavigationMenuItem(title = AppStrings.resetSettings, icon = Icons.Default.Refresh, isSelected = false, isDark = isDark) {
                            safeClick { showGlobalResetDialog = true }
                        }
                        Spacer(Modifier.weight(1f))
                        Text("${AppStrings.version} $versionName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), textAlign = TextAlign.Center)
                    }
                    VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            },
            content = { isExpanded, isSidebarVisible, onToggleSidebar ->
                Column(modifier = Modifier.fillMaxSize().background(mainBgColor)) {

                    // --- EN-TÊTE TABLETTE (Apparaît quand le menu latéral est replié) ---
                    if (isExpanded) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(visible = !isSidebarVisible, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                                IconButton(onClick = onToggleSidebar, modifier = Modifier.padding(start = 8.dp)) {
                                    Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Text(AppStrings.settingsTitle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            AnimatedVisibility(visible = !isSidebarVisible) { Spacer(Modifier.width(56.dp)) }
                        }
                    }

                    // --- CONTENU DÉFILANT DES PARAMÈTRES ---
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .then(if (navMode == 0 || !isExpanded) Modifier.settingsFadingEdge(scrollState) else Modifier)
                            .then(if (navMode == 0 || !isExpanded) Modifier.verticalScroll(scrollState) else Modifier)
                            .padding(horizontal = if (isExpanded) 48.dp else 24.dp)
                            // 🚨 CORRECTION 3 : Marge pour pouvoir scroller jusqu'au bout
                            .navigationBarsPadding()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            if (navMode == 0 || !isExpanded) {
                                AllSettingsContent(isExpanded, navMode, { AppConfig.navMode.intValue = it; prefs.edit().putInt("nav_mode", it).apply() }, themeMode, { themeMode = it; prefs.edit().putInt("theme_mode", it).apply() }, isOledMode, { isOledMode = it; prefs.edit().putBoolean("is_oled_mode", it).apply() }, forceOneUi, { forceOneUi = it; prefs.edit().putBoolean("force_one_ui", it).apply() }, isBlurEnabled, { isBlurEnabled = it; prefs.edit().putBoolean("is_blur_enabled", it).apply() }, logoResId, { showIconSheet = true }, defaultOperator, { showOperatorSheet = true }, appLanguage, { showLanguageSheet = true }, { showUnitSheet = true }, { showPagesCustomizationSheet = true }, { showExternalLinksSheet = true }, { showShareSelectorSheet = true }, mapProvider, { mapProvider = it; prefs.edit().putInt("map_provider", it).apply() }, ignStyle, { ignStyle = it; prefs.edit().putInt("ign_style", it).apply() }, context, cardShape, cardBorder, bubbleBaseColor, useOneUi, { safeClick(it) }, repository, scope)
                            } else {
                                when (activeSectionIndex) {
                                    0 -> SectionApparence(themeMode, { themeMode = it; prefs.edit().putInt("theme_mode", it).apply() }, isOledMode, { isOledMode = it; prefs.edit().putBoolean("is_oled_mode", it).apply() }, forceOneUi, { forceOneUi = it; prefs.edit().putBoolean("force_one_ui", it).apply() }, isBlurEnabled, { isBlurEnabled = it; prefs.edit().putBoolean("is_blur_enabled", it).apply() }, logoResId, { showIconSheet = true }, cardShape, cardBorder, bubbleBaseColor, useOneUi, { safeClick(it) })
                                    1 -> SectionCartographie(mapProvider, { mapProvider = it; prefs.edit().putInt("map_provider", it).apply() }, ignStyle, { ignStyle = it; prefs.edit().putInt("ign_style", it).apply() }, cardShape, cardBorder, bubbleBaseColor, useOneUi, { safeClick(it) })
                                    2 -> SectionPreferences(isExpanded, navMode, { AppConfig.navMode.intValue = it; prefs.edit().putInt("nav_mode", it).apply() }, defaultOperator, { showOperatorSheet = true }, appLanguage, { showLanguageSheet = true }, { showUnitSheet = true }, { showPagesCustomizationSheet = true }, { showExternalLinksSheet = true }, { showShareSelectorSheet = true }, cardShape, cardBorder, bubbleBaseColor, useOneUi, { safeClick(it) })
                                    3 -> SectionSysteme(context, cardShape, border = cardBorder, bubbleColor = bubbleBaseColor, useOneUi = useOneUi, safeClick = { safeClick(it) })
                                    4 -> SectionDatabase(isExpanded, cardShape, bubbleBaseColor, useOneUi, repository, scope, context)
                                }
                            }
                            Spacer(Modifier.height(48.dp))
                        }
                    }
                }
            }
        )

        if (showIconSheet) {
            IconSheet(
                onDismiss = { showIconSheet = false },
                currentIconRes = logoResId,
                onToggle = { choix -> AppIconManager.setIcon(context, choix) },
                context = context,
                sheetState = sheetState,
                useOneUi = useOneUi,
                safeClick = { safeClick(it) }
            )
        };
        if (showOperatorSheet) {
            fr.geotower.ui.components.OperatorSheet(
                defaultOperator,
                { defaultOperator = it; prefs.edit().putString("default_operator", it).apply() },
                { showOperatorSheet = false },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }

        if (showLanguageSheet) {
            fr.geotower.ui.components.LanguageSheet(
                current = appLanguage,
                onSelect = { nouvelleLangue ->
                    appLanguage = nouvelleLangue
                    AppConfig.appLanguage.value = nouvelleLangue
                    prefs.edit().putString("app_language", nouvelleLangue).apply()
                },
                onDismiss = { showLanguageSheet = false },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }
        // --- NOUVEAU MENU DE PERSONNALISATION DES PAGES ---
        if (showPagesCustomizationSheet) {
            PagesCustomizationSheet(
                onDismiss = { showPagesCustomizationSheet = false },
                sheetState = sheetState,
                useOneUi = useOneUi,
                onStartupPageClick = { safeClick { showPagesCustomizationSheet = false; showStartupPageSheet = true } },
                onHomeClick = { safeClick { showPagesCustomizationSheet = false; showHomeSettingsSheet = true } },
                onNearbyClick = { safeClick { showPagesCustomizationSheet = false; showNearbySettingsSheet = true } },
                onMapClick = { safeClick { showPagesCustomizationSheet = false; showMapSettingsSheet = true } },
                onCompassClick = { safeClick { showPagesCustomizationSheet = false; showCompassSettingsSheet = true } },
                onSupportClick = { safeClick { showPagesCustomizationSheet = false; showSupportSettingsSheet = true } },
                onSiteClick = { safeClick { showPagesCustomizationSheet = false; showSiteSettingsSheet = true } },
                onOpenFrequencies = {
                    // ✅ L'échange se fait ici : on ferme l'un et on ouvre l'autre
                    showPagesCustomizationSheet = false
                    showFrequenciesSheet = true
                }
            )
        }

        // ✅ AJOUT : Fenêtre des Unités
        if (showUnitSheet) {
            fr.geotower.ui.components.UnitSettingsSheet(
                onDismiss = { showUnitSheet = false },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }

        // ✅ AJOUT : Fenêtre des Fréquences
        if (showFrequenciesSheet) {
            fr.geotower.ui.screens.settings.SiteFreqFiltersSheet(
                onDismiss = { showFrequenciesSheet = false },
                onBack = {
                    // ✅ 3. LOGIQUE DE RETOUR
                    showFrequenciesSheet = false
                    showSiteSettingsSheet = true
                }
            )
        }

        // ✅ AJOUT : Fenêtre des Photos & Schémas
        if (showPhotosSettingsSheet) {
            fr.geotower.ui.screens.settings.SitePhotosSettingsSheet(
                onDismiss = { showPhotosSettingsSheet = false },
                onBack = {
                    showPhotosSettingsSheet = false
                    showSiteSettingsSheet = true
                }
            )
        }

        if (showStartupPageSheet) {
            StartupPageSelectionSheet(
                currentStartupPage = startupPage,
                onPageSelected = { newPage ->
                    startupPage = newPage
                    prefs.edit().putString("startup_page", newPage).apply()
                },
                onDismiss = { showStartupPageSheet = false },
                onBack = {
                    safeClick {
                        showStartupPageSheet = false; showPagesCustomizationSheet = true
                    }
                }, // <-- AJOUT ICI
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }

        // --- NOUVEAU MENU DES PAGES (AJOUTÉ ICI) ---
        // --- SOUS-MENU : PAGE D'ACCUEIL ---
        if (showHomeSettingsSheet) {
            HomeSettingsSheet(
                pagesOrder = pagesOrder,
                onOrderChange = { newOrder ->
                    pagesOrder = newOrder
                    prefs.edit().putString("pages_order", newOrder.joinToString(",")).apply()
                },
                showNearby = showNearbyPage,
                onNearbyChange = {
                    showNearbyPage = it; prefs.edit().putBoolean("show_nearby_page", it).apply()
                },
                showMap = showMapPage,
                onMapChange = {
                    showMapPage = it; prefs.edit().putBoolean("show_map_page", it).apply()
                },
                showCompass = showCompassPage,
                onCompassChange = {
                    showCompassPage = it; prefs.edit().putBoolean("show_compass_page", it).apply()
                },
                showStats = showStatsPage,
                onStatsChange = {
                    showStatsPage = it; prefs.edit().putBoolean("show_stats_page", it).apply()
                },
                onDismiss = { showHomeSettingsSheet = false },
                onBack = {
                    safeClick {
                        showHomeSettingsSheet = false; showPagesCustomizationSheet = true
                    }
                }, // <-- AJOUT ICI
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }
        // --- SOUS-MENU : ANTENNES À PROXIMITÉ ---
        if (showNearbySettingsSheet) {
            NearbySettingsSheet(
                nearbyOrder = nearbyOrder,
                onOrderChange = { newOrder ->
                    nearbyOrder = newOrder
                    prefs.edit().putString("nearby_order", newOrder.joinToString(",")).apply()
                },
                showSearch = showSearchBar,
                onSearchChange = {
                    showSearchBar = it; prefs.edit().putBoolean("show_search_bar", it).apply()
                },
                showSites = showNearbySites,
                onSitesChange = {
                    showNearbySites = it; prefs.edit().putBoolean("show_nearby_sites", it).apply()
                },
                searchRadius = nearbySearchRadius,
                onRadiusChange = {
                    nearbySearchRadius = it; prefs.edit().putInt("nearby_search_radius", it).apply()
                },
                onDismiss = { showNearbySettingsSheet = false },
                onBack = {
                    safeClick {
                        showNearbySettingsSheet = false; showPagesCustomizationSheet = true
                    }
                }, // <-- AJOUT ICI
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }
        // --- SOUS-MENU : CARTE DES ANTENNES ---
        if (showMapSettingsSheet) {
            MapSettingsSheet(
                showLocation = showMapLocation,
                onLocationChange = {
                    showMapLocation = it; prefs.edit().putBoolean("show_map_location", it).apply()
                },
                showZoom = showMapZoom,
                onZoomChange = {
                    showMapZoom = it; prefs.edit().putBoolean("show_map_zoom", it).apply()
                },
                showToolbox = showMapToolbox,
                onToolboxChange = {
                    showMapToolbox = it; prefs.edit().putBoolean("show_map_toolbox", it).apply()
                },
                showCompass = showMapCompass,
                onCompassChange = {
                    showMapCompass = it; prefs.edit().putBoolean("show_map_compass", it).apply()
                },
                // --- NOUVELLES OPTIONS ---
                showScale = showMapScale,
                onScaleChange = {
                    showMapScale = it; prefs.edit().putBoolean("show_map_scale", it).apply()
                },
                showAttribution = showMapAttribution,
                onAttributionChange = {
                    showMapAttribution = it; prefs.edit().putBoolean("show_map_attribution", it)
                    .apply()
                },

                showSpeedometer = showMapSpeedometer,
                onSpeedometerChange = {
                    showMapSpeedometer = it
                    AppConfig.showSpeedometer.value = it
                    prefs.edit().putBoolean("show_speedometer", it).apply()
                },

                onDismiss = { showMapSettingsSheet = false },
                onBack = {
                    safeClick {
                        showMapSettingsSheet = false; showPagesCustomizationSheet = true
                    }
                },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }
        // --- SOUS-MENU : BOUSSOLE ---
        if (showCompassSettingsSheet) {
            CompassSettingsSheet(
                compassOrder = compassOrder,
                onOrderChange = { newOrder ->
                    compassOrder = newOrder
                    prefs.edit().putString("compass_order", newOrder.joinToString(",")).apply()
                },
                showLocation = showCompassLocation,
                onLocationChange = {
                    showCompassLocation = it; prefs.edit().putBoolean("show_compass_location", it)
                    .apply()
                },
                showGps = showCompassGps,
                onGpsChange = {
                    showCompassGps = it; prefs.edit().putBoolean("show_compass_gps", it).apply()
                },
                showAccuracy = showCompassAccuracy,
                onAccuracyChange = {
                    showCompassAccuracy = it; prefs.edit().putBoolean("show_compass_accuracy", it)
                    .apply()
                },
                onDismiss = { showCompassSettingsSheet = false },
                onBack = {
                    safeClick {
                        showCompassSettingsSheet = false; showPagesCustomizationSheet = true
                    }
                },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }
        // --- NOUVELLES FENÊTRES ---
        if (showSupportSettingsSheet) {
            SupportSettingsSheet(
                supportOrder = pageSupportOrder, onOrderChange = { pageSupportOrder = it; prefs.edit().putString("page_support_order", it.joinToString(",")).apply() },
                showMap = pageSupportMap, onMapChange = { pageSupportMap = it; prefs.edit().putBoolean("page_support_map", it).apply() },
                showDetails = pageSupportDetails, onDetailsChange = { pageSupportDetails = it; prefs.edit().putBoolean("page_support_details", it).apply() },
                showPhotos = pageSupportPhotos, onPhotosChange = { pageSupportPhotos = it; prefs.edit().putBoolean("page_support_photos", it).apply() },
                showNav = pageSupportNav, onNavChange = { pageSupportNav = it; prefs.edit().putBoolean("page_support_nav", it).apply() },
                showShare = pageSupportShare, onShareChange = { pageSupportShare = it; prefs.edit().putBoolean("page_support_share", it).apply() },
                showOperators = pageSupportOperators, onOperatorsChange = { pageSupportOperators = it; prefs.edit().putBoolean("page_support_operators", it).apply() },
                onDismiss = { showSupportSettingsSheet = false },
                onBack = { safeClick { showSupportSettingsSheet = false; showPagesCustomizationSheet = true } },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }

        if (showSiteSettingsSheet) {
            SiteSettingsSheet(
                siteOrder = pageSiteOrder, onOrderChange = { pageSiteOrder = it; prefs.edit().putString("page_site_order", it.joinToString(",")).apply() },
                showOperator = pageSiteOperator, onOperatorChange = { pageSiteOperator = it; prefs.edit().putBoolean("page_site_operator", it).apply() },
                showBearingHeight = pageSiteBearingHeight, onBearingHeightChange = { pageSiteBearingHeight = it; prefs.edit().putBoolean("page_site_bearing_height", it).apply() },
                showMap = pageSiteMap, onMapChange = { pageSiteMap = it; prefs.edit().putBoolean("page_site_map", it).apply() },
                showSupportDetails = pageSiteSupportDetails, onSupportDetailsChange = { pageSiteSupportDetails = it; prefs.edit().putBoolean("page_site_support_details", it).apply() },
                showPhotos = AppConfig.siteShowPhotos.value, onPhotosChange = { AppConfig.siteShowPhotos.value = it; prefs.edit().putBoolean("page_site_photos", it).apply() },
                showPanelHeights = pageSitePanelHeights, onPanelHeightsChange = { pageSitePanelHeights = it; prefs.edit().putBoolean("page_site_panel_heights", it).apply() },
                showIds = pageSiteIds, onIdsChange = { pageSiteIds = it; prefs.edit().putBoolean("page_site_ids", it).apply() },
                showNav = pageSiteNav, onNavChange = { pageSiteNav = it; prefs.edit().putBoolean("page_site_nav", it).apply() },
                showShare = pageSiteShare, onShareChange = { pageSiteShare = it; prefs.edit().putBoolean("page_site_share", it).apply() },
                showDates = pageSiteDates, onDatesChange = { pageSiteDates = it; prefs.edit().putBoolean("page_site_dates", it).apply() },
                showAddress = pageSiteAddress, onAddressChange = { pageSiteAddress = it; prefs.edit().putBoolean("page_site_address", it).apply() },
                showStatus = AppConfig.siteShowStatus.value, onStatusChange = { AppConfig.siteShowStatus.value = it; prefs.edit().putBoolean("site_show_status", it).apply() }, // 🚨 AJOUT DU STATUT
                showSpeedtest = AppConfig.siteShowSpeedtest.value, onSpeedtestChange = { AppConfig.siteShowSpeedtest.value = it; prefs.edit().putBoolean("site_show_speedtest", it).apply() }, // 🚨 NEW
                showFreqs = pageSiteFreqs, onFreqsChange = { pageSiteFreqs = it; prefs.edit().putBoolean("page_site_freqs", it).apply() },
                showLinks = pageSiteLinks, onLinksChange = { pageSiteLinks = it; prefs.edit().putBoolean("page_site_links", it).apply() },
                onOpenFrequencies = {
                    showSiteSettingsSheet = false
                    showFrequenciesSheet = true
                },
                onOpenPhotosSettings = {
                    showSiteSettingsSheet = false
                    showPhotosSettingsSheet = true
                },
                onDismiss = { showSiteSettingsSheet = false },
                onBack = { safeClick { showSiteSettingsSheet = false; showPagesCustomizationSheet = true } },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }
        // --- MENU DES PRÉFÉRENCES DE PARTAGE ---
        // --- SOUS-MENU DE SÉLECTION DU PARTAGE ---
        if (showShareSelectorSheet) {
            val sheetBgColor2 =
                if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
            ModalBottomSheet(
                onDismissRequest = { showShareSelectorSheet = false },
                sheetState = sheetState,
                containerColor = sheetBgColor2
            ) {
                Column(modifier = Modifier.padding(bottom = 48.dp, start = 16.dp, end = 16.dp)) {
                    Text(
                        AppStrings.defaultShareContentTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                            .padding(bottom = 24.dp)
                    )

                    // ✅ AJOUT DU BOUTON CARTE
                    NavigationMenuItem(
                        title = AppStrings.shareMapDetailsTitle, // "Carte"
                        icon = Icons.Outlined.Map,
                        isSelected = false,
                        isDark = isDark
                    ) {
                        safeClick {
                            showShareSelectorSheet = false
                            showMapSharePrefsSheet = true
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    NavigationMenuItem(
                        title = AppStrings.shareSupportDetailsTitle,
                        icon = Icons.Default.VerticalAlignTop,
                        isSelected = false,
                        isDark = isDark
                    ) {
                        safeClick {
                            showShareSelectorSheet = false
                            showSupportSharePrefsSheet = true
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    NavigationMenuItem(
                        title = AppStrings.shareSiteDetailsTitle,
                        icon = Icons.Default.WifiTethering,
                        isSelected = false,
                        isDark = isDark
                    ) {
                        safeClick {
                            showShareSelectorSheet = false
                            showSharePrefsSheet = true
                        }
                    }
                }
            }
        }

        // --- MENU PRÉFÉRENCES PARTAGE PYLÔNE (SUPPORT) ---
        if (showSupportSharePrefsSheet) {
            SupportSharePreferencesSheet(
                shareOrder = shareSupOrder,
                onOrderChange = { newOrder ->
                    shareSupOrder = newOrder; prefs.edit()
                    .putString("share_sup_order", newOrder.joinToString(",")).apply()
                },
                mapEnabled = shareSupMapEnabled,
                onMapChange = {
                    shareSupMapEnabled = it; prefs.edit().putBoolean("share_sup_map_enabled", it)
                    .apply()
                },
                supportEnabled = shareSupSupportEnabled,
                onSupportChange = {
                    shareSupSupportEnabled = it; prefs.edit()
                    .putBoolean("share_sup_support_enabled", it).apply()
                },
                operatorsEnabled = shareSupOperatorsEnabled,
                onOperatorsChange = {
                    shareSupOperatorsEnabled = it; prefs.edit()
                    .putBoolean("share_sup_operators_enabled", it).apply()
                },
                qrEnabled = shareSupQrEnabled,
                onQrChange = { shareSupQrEnabled = it; prefs.edit().putBoolean("share_sup_qr_enabled", it).apply() },
                confidentialEnabled = shareSupConfidentialEnabled,
                onConfidentialChange = {
                    shareSupConfidentialEnabled = it; prefs.edit()
                    .putBoolean("share_sup_confidential_enabled", it).apply()
                },
                onDismiss = { showSupportSharePrefsSheet = false },
                onBack = {
                    safeClick {
                        showSupportSharePrefsSheet = false; showShareSelectorSheet = true
                    }
                },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }
        if (showExternalLinksSheet) {
            ExternalLinksSettingsSheet(
                onDismiss = { showExternalLinksSheet = false },
                sheetState = sheetState,
                useOneUi = useOneUi
            )
        }
        if (showSharePrefsSheet) {
            SharePreferencesSheet(
                shareOrder = shareOrder,
                onOrderChange = { newOrder ->
                    shareOrder = newOrder
                    prefs.edit().putString("share_order", newOrder.joinToString(",")).apply()
                },
                mapEnabled = shareMapEnabled,
                onMapChange = {
                    shareMapEnabled = it; prefs.edit().putBoolean("share_map_enabled", it).apply()
                },
                supportEnabled = shareSupportEnabled,
                onSupportChange = {
                    shareSupportEnabled = it; prefs.edit().putBoolean("share_support_enabled", it)
                    .apply()
                },
                idsEnabled = shareIdsEnabled,
                onIdsChange = {
                    shareIdsEnabled = it; prefs.edit().putBoolean("share_ids_enabled", it).apply()
                },
                datesEnabled = shareDatesEnabled,
                onDatesChange = {
                    shareDatesEnabled = it; prefs.edit().putBoolean("share_dates_enabled", it)
                    .apply()
                },
                addressEnabled = shareAddressEnabled,
                onAddressChange = {
                    shareAddressEnabled = it; prefs.edit().putBoolean("share_address_enabled", it).apply()
                },
                statusEnabled = AppConfig.shareSiteStatus.value,
                onStatusChange = {
                    AppConfig.shareSiteStatus.value = it; prefs.edit().putBoolean("share_site_status", it).apply()
                },
                speedtestEnabled = shareSpeedtestEnabled, // 🚨 NEW
                onSpeedtestChange = {
                    shareSpeedtestEnabled = it; prefs.edit().putBoolean("share_speedtest_enabled", it).apply()
                },
                freqEnabled = shareFreqEnabled,
                onFreqChange = {
                    shareFreqEnabled = it; prefs.edit().putBoolean("share_freq_enabled", it).apply()
                },
                qrEnabled = shareSiteQrEnabled,
                onQrChange = { shareSiteQrEnabled = it; prefs.edit().putBoolean("share_site_qr_enabled", it).apply() },
                confidentialEnabled = shareConfidentialEnabled,
                onConfidentialChange = {
                    shareConfidentialEnabled = it; prefs.edit()
                    .putBoolean("share_confidential_enabled", it).apply()
                },
                onDismiss = { showSharePrefsSheet = false },
                onBack = {
                    safeClick {
                        showSharePrefsSheet = false; showShareSelectorSheet = true
                    }
                },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }
    }
    // ✅ AJOUT : FENÊTRE DES PRÉFÉRENCES DE PARTAGE DE LA CARTE
    if (showMapSharePrefsSheet) {
        MapSharePreferencesSheet(
            // ✅ On change 'compass' par 'azimuths'
            azimuthsEnabled = shareMapAzimuths,
            onAzimuthsChange = {
                shareMapAzimuths = it; prefs.edit().putBoolean("share_map_azimuths", it).apply()
                AppConfig.shareMapAzimuths.value = it // Met à jour l'état global
            },
            speedometerEnabled = shareMapSpeedometer,
            onSpeedometerChange = {
                shareMapSpeedometer = it; prefs.edit().putBoolean("share_map_speedometer", it).apply()
                AppConfig.shareMapSpeedometer.value = it
            },
            scaleEnabled = shareMapScale,
            onScaleChange = {
                shareMapScale = it; prefs.edit().putBoolean("share_map_scale", it).apply()
                AppConfig.shareMapScale.value = it
            },
            attributionEnabled = shareMapAttribution,
            onAttributionChange = {
                shareMapAttribution = it; prefs.edit().putBoolean("share_map_attribution", it).apply()
                AppConfig.shareMapAttribution.value = it
            },
            statusEnabled = AppConfig.shareSiteStatus.value, // 🚨 C'EST ICI QU'IL MANQUAIT LES VARIABLES !
            onStatusChange = {
                AppConfig.shareSiteStatus.value = it; prefs.edit().putBoolean("share_site_status", it).apply()
            },
            confidentialEnabled = shareMapConfidential,
            onConfidentialChange = {
                shareMapConfidential = it; prefs.edit().putBoolean("share_map_confidential", it).apply()
                AppConfig.shareMapConfidential.value = it
            },
            onDismiss = { showMapSharePrefsSheet = false },
            onBack = {
                safeClick {
                    showMapSharePrefsSheet = false; showShareSelectorSheet = true
                }
            },
            sheetState = sheetState,
            useOneUi = useOneUi,
            bubbleColor = bubbleBaseColor
        )
    }
    // --- POP-UP DE RÉINITIALISATION GLOBALE (DEPUIS LE MENU LATÉRAL) ---
    if (showGlobalResetDialog) {
        AlertDialog(
            onDismissRequest = { showGlobalResetDialog = false },
            title = { Text(text = AppStrings.resetWarningTitle, fontWeight = FontWeight.Bold) },
            text = { Text(AppStrings.resetWarningDesc) },
            shape = cardShape,
            containerColor = MaterialTheme.colorScheme.surface,
            dismissButton = {
                TextButton(
                    onClick = {
                        showGlobalResetDialog = false

                        // 1. On efface toutes les préférences
                        prefs.edit().clear().apply()
                        // 2. On garde le fait que le tuto est passé
                        prefs.edit().putBoolean("isFirstRun", false).apply()

                        // 3. On redémarre l'application
                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        Runtime.getRuntime().exit(0)
                    }
                ) {
                    Text(AppStrings.yes, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showGlobalResetDialog = false },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50), // Vert
                        contentColor = Color.White
                    )
                ) {
                    Text(AppStrings.no, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

// ============================================================
// SECTIONS & HELPERS UI
// ============================================================

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        // On utilise primary tout court, Android gérera le mode clair/sombre tout seul !
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    )
}

@Composable
fun AllSettingsContent(
    isWide: Boolean, nav: Int, onNav: (Int) -> Unit, theme: Int, onTheme: (Int) -> Unit, oled: Boolean, onOled: (Boolean) -> Unit, oneUi: Boolean, onOneUi: (Boolean) -> Unit, blur: Boolean, onBlur: (Boolean) -> Unit, logo: Int, onIcon: () -> Unit, op: String, onOp: () -> Unit, lang: String, onLang: () -> Unit,
    onUnitSettings: () -> Unit,
    onPages: () -> Unit,
    onExternalLinks: () -> Unit,
    onSharePrefs: () -> Unit,
    map: Int, onMap: (Int) -> Unit, ign: Int, onIgn: (Int) -> Unit, ctx: Context, shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean, safeClick: (() -> Unit) -> Unit, repository: AnfrRepository, scope: kotlinx.coroutines.CoroutineScope
) {
    SectionApparence(theme, onTheme, oled, onOled, oneUi, onOneUi, blur, onBlur, logo, onIcon, shape, border, bubbleColor, useOneUi, safeClick); Spacer(Modifier.height(32.dp))
    SectionCartographie(map, onMap, ign, onIgn, shape, border, bubbleColor, useOneUi, safeClick); Spacer(Modifier.height(32.dp))
    SectionPreferences(isWide, nav, onNav, op, onOp, lang, onLang, onUnitSettings, onPages, onExternalLinks, onSharePrefs, shape, border, bubbleColor, useOneUi, safeClick); Spacer(Modifier.height(32.dp))
    SectionSysteme(ctx, shape, border, bubbleColor, useOneUi, safeClick); Spacer(Modifier.height(32.dp))
    SectionDatabase(isWide, shape, bubbleColor, useOneUi, repository, scope, ctx)
}

@Composable
fun SectionApparence(
    theme: Int, onTheme: (Int) -> Unit, oled: Boolean, onOled: (Boolean) -> Unit,
    oneUi: Boolean, onOneUi: (Boolean) -> Unit, blur: Boolean, onBlur: (Boolean) -> Unit,
    logo: Int, onIcon: () -> Unit,
    shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean, safeClick: (() -> Unit) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", android.content.Context.MODE_PRIVATE)
    val menuSize by AppConfig.menuSize
    val showHomeLogo = remember { prefs.getBoolean("show_home_logo", true) } // <-- NOUVEAU

    SectionTitle(AppStrings.appearance)

    fr.geotower.ui.components.AppearanceOptionsBlock(
        themeMode = theme, onThemeChange = onTheme,
        isOled = oled, onOledChange = onOled,
        useOneUi = oneUi, onOneUiChange = onOneUi,
        isBlur = blur, onBlurChange = onBlur,
        menuSize = menuSize,
        onMenuSizeChange = { newSize ->
            AppConfig.menuSize.value = newSize
            prefs.edit().putString("menuSize", newSize).apply()
        },
        appIconRes = logo,
        onAppIconClick = onIcon,
        shape = shape, border = border, bubbleColor = bubbleColor, safeClick = safeClick
    )

    // <-- NOUVEAU : AFFICHAGE DU SÉLECTEUR DE LOGO D'ACCUEIL
    androidx.compose.animation.AnimatedVisibility(
        visible = false, // ✅ Remplacé 'showHomeLogo' par 'false' pour masquer sans supprimer
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
    ) {
        Column {
            Spacer(modifier = Modifier.height(24.dp))
            fr.geotower.ui.components.HomeLogoSelectorBlock(safeClick = { action -> safeClick(action) })
        }
    }
}

@Composable
fun SectionCartographie(map: Int, onMap: (Int) -> Unit, ign: Int, onIgn: (Int) -> Unit, shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean, safeClick: (() -> Unit) -> Unit) {
    SectionTitle(AppStrings.mapping)

    fr.geotower.ui.components.MappingOptionsBlock(
        mapProvider = map,
        onMapProviderChange = onMap,
        ignStyle = ign,
        onIgnStyleChange = onIgn,
        shape = shape,
        border = border,
        bubbleColor = bubbleColor,
        useOneUi = useOneUi,
        safeClick = safeClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionPreferences(
    isWide: Boolean, nav: Int, onNav: (Int) -> Unit,
    op: String, onOp: () -> Unit, lang: String, onLang: () -> Unit,
    onUnitSettings: () -> Unit,
    onPages: () -> Unit,
    onExternalLinks: () -> Unit,
    onSharePrefs: () -> Unit,
    shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean, safeClick: (() -> Unit) -> Unit
) {
    // NOUVEAU : On récupère le contexte et les préférences ici pour le curseur
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

    // ✅ NOUVEAU : Le lanceur magique qui déclenche le menu Android spécifique !
    val bgLocationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { /* L'interface de Jetpack Compose se mettra à jour toute seule */ }
    )

    var widgetFrequency by remember {
        mutableIntStateOf(prefs.getInt("widget_sync_freq", 30).let { if (it < 30) 30 else it })
    }

    // ✅ AJOUT POUR LE STYLE D'AFFICHAGE
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    var displayStyle by remember { mutableIntStateOf(prefs.getInt("display_style", 0)) }
    var showDisplayStylesSheet by remember { mutableStateOf(false) }

    SectionTitle(AppStrings.preferences)
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode // <-- AJOUT
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val paleBgColor = if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow // <-- AJOUT

    if (isWide) {
        var showModeSheet by remember { mutableStateOf(false) }
        val cardBg = if (useOneUi) bubbleColor else Color.Transparent
        Surface(onClick = { showModeSheet = true }, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = shape, border = border, color = cardBg) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(AppStrings.navMode, fontWeight = FontWeight.Bold)
                    Text(if (nav == 0) AppStrings.navScroll else AppStrings.navPages, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.UnfoldMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showModeSheet) ModalBottomSheet(onDismissRequest = { showModeSheet = false }, containerColor = sheetBgColor) { Column(modifier = Modifier.padding(bottom = 48.dp, start = 24.dp, end = 24.dp)) { Text(AppStrings.navStyleTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(16.dp)); NavigationModeOption(AppStrings.navScrollTitle, AppStrings.navScrollDesc, nav == 0, useOneUi) { onNav(0); showModeSheet = false }; NavigationModeOption(AppStrings.navPagesTitle, AppStrings.navPagesDesc, nav == 1, useOneUi) { onNav(1); showModeSheet = false } } }
    }

    // ✅ NOUVEAU : Option Style d'affichage (Uniquement si écran >= 800px)
    if (configuration.screenWidthDp >= 600) {
        val cardBg = if (useOneUi) bubbleColor else Color.Transparent
        Surface(onClick = { safeClick { showDisplayStylesSheet = true } }, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = shape, border = border, color = cardBg) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(AppStrings.displayStyleTitle, fontWeight = FontWeight.Bold)
                    Text(if (displayStyle == 0) AppStrings.displayStyleFullScreen else AppStrings.displayStyleSplit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.UnfoldMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (showDisplayStylesSheet) {
            ModalBottomSheet(
                onDismissRequest = { showDisplayStylesSheet = false },
                containerColor = sheetBgColor
            ) {
                Column(modifier = Modifier.padding(bottom = 48.dp, start = 24.dp, end = 24.dp)) {
                    Text(
                        text = AppStrings.displayStyleTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    NavigationModeOption(
                        title = AppStrings.displayStyleFullScreen,
                        desc = AppStrings.displayStyleFullScreenDesc,
                        isSelected = displayStyle == 0,
                        useOneUi = useOneUi,
                        onClick = {
                            displayStyle = 0
                            prefs.edit().putInt("display_style", 0).apply()
                            AppConfig.displayStyle.intValue = 0
                            showDisplayStylesSheet = false
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    NavigationModeOption(
                        title = AppStrings.displayStyleSplit,
                        desc = AppStrings.displayStyleSplitDesc,
                        isSelected = displayStyle == 1,
                        useOneUi = useOneUi,
                        onClick = {
                            displayStyle = 1
                            prefs.edit().putInt("display_style", 1).apply()
                            AppConfig.displayStyle.intValue = 1
                            showDisplayStylesSheet = false
                        }
                    )
                }
            }
        }
    }

    // ========================================================
    // ✅ NOUVEAU : NOTIFICATIONS DE MISE À JOUR DE LA BASE
    // ========================================================
    val updateNotifsEnabled by fr.geotower.utils.AppConfig.enableUpdateNotifications

    PreferenceSwitchCard(
        title = fr.geotower.utils.AppStrings.updateNotifSettingTitle,
        desc = fr.geotower.utils.AppStrings.updateNotifSettingDesc,
        checked = updateNotifsEnabled,
        onCheckedChange = { isChecked ->
            fr.geotower.utils.AppConfig.enableUpdateNotifications.value = isChecked
            prefs.edit().putBoolean("enable_update_notifications", isChecked).apply()

            // Demande la permission sur Android 13+ si on active
            if (isChecked && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    (context as? android.app.Activity)?.requestPermissions(
                        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1003
                    )
                }
            }
        },
        shape = shape,
        border = border,
        bubbleColor = bubbleColor,
        useOneUi = useOneUi
    )
    Spacer(Modifier.height(12.dp))

    // --- NOUVEAU : NOTIFICATION LIVE ---
    val liveNotifsEnabled by AppConfig.enableLiveNotifications
    val isOperatorSelected = op != "Aucun"

    // Si on change d'opérateur pour "Aucun", on désactive de force la notification live
    LaunchedEffect(isOperatorSelected) {
        if (!isOperatorSelected && liveNotifsEnabled) {
            AppConfig.enableLiveNotifications.value = false
            prefs.edit().putBoolean("enable_live_notifications", false).apply()
        }
    }

    fr.geotower.ui.components.LiveNotificationCard(
        title = AppStrings.liveNotificationTitle,
        desc = if (isOperatorSelected) AppStrings.liveNotificationDesc else AppStrings.liveNotificationRequiresOp,
        checked = liveNotifsEnabled && isOperatorSelected,
        onCheckedChange = { isChecked ->
            AppConfig.enableLiveNotifications.value = isChecked
            prefs.edit().putBoolean("enable_live_notifications", isChecked).apply()
        },
        enabled = isOperatorSelected,
        shape = shape,
        border = border,
        bubbleColor = bubbleColor,
        useOneUi = useOneUi
    )
    Spacer(Modifier.height(12.dp))

    PreferenceOperatorCard(AppStrings.defaultOperator, op, onOp, shape, border, bubbleColor, useOneUi, safeClick)
    Spacer(Modifier.height(12.dp))

    PreferenceLanguageCard(AppStrings.appLanguageLabel, lang, onLang, shape, border, bubbleColor, useOneUi, safeClick)
    Spacer(Modifier.height(12.dp))
    PreferenceActionCard(
        title = AppStrings.unitSettingsTitle,
        desc = AppStrings.unitSettingsDesc,
        onClick = onUnitSettings,
        shape = shape,
        border = border,
        bubbleColor = bubbleColor,
        useOneUi = useOneUi,
        safeClick = safeClick,
        icon = Icons.Default.Straighten
    )
    Spacer(Modifier.height(12.dp))

    PreferenceActionCard(
        title = AppStrings.pagesCustomizationTitle,
        desc = AppStrings.pagesCustomizationDesc,
        onClick = onPages,
        shape = shape,
        border = border,
        bubbleColor = bubbleColor,
        useOneUi = useOneUi,
        safeClick = safeClick,
        icon = Icons.Default.Edit
    )
    Spacer(Modifier.height(12.dp))
    PreferenceActionCard(
        title = AppStrings.externalLinksSettingsTitle,
        desc = AppStrings.externalLinksSettingsDesc,
        onClick = onExternalLinks,
        shape = shape,
        border = border,
        bubbleColor = bubbleColor,
        useOneUi = useOneUi,
        safeClick = safeClick,
        icon = Icons.Default.Language
    )
    Spacer(Modifier.height(12.dp))

    PreferenceActionCard(
        title = AppStrings.defaultShareContentTitle,
        desc = AppStrings.defaultShareContentDesc,
        onClick = onSharePrefs,
        shape = shape,
        border = border,
        bubbleColor = bubbleColor,
        useOneUi = useOneUi,
        safeClick = safeClick,
        icon = Icons.Default.Share
    )
    Spacer(Modifier.height(12.dp))

    // --- NOUVEAU : BOUTON D'AUTORISATION ARRIÈRE-PLAN ---
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

        // ✅ 1. L'état qui permet à l'interface de se mettre à jour toute seule
        var isBgLocationGranted by remember {
            mutableStateOf(
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            )
        }

        // ✅ 2. On écoute le retour sur l'application pour revérifier la permission instantanément
        val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    isBgLocationGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // ✅ 3. L'animation de disparition fluide (le bloc disparaît si isBgLocationGranted devient true)
        androidx.compose.animation.AnimatedVisibility(
            visible = !isBgLocationGranted,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
        ) {
            Column {
                PreferenceActionCard(
                    title = AppStrings.bgLocationPermTitle,
                    desc = AppStrings.bgLocationPermDesc,
                    onClick = {
                        // ✅ 4. Trouver la VRAIE activité (On déballe le contexte de Compose pour réparer le bug de redirection)
                        var currentContext = context
                        while (currentContext is android.content.ContextWrapper && currentContext !is android.app.Activity) {
                            currentContext = currentContext.baseContext
                        }
                        val activity = currentContext as? android.app.Activity

                        // ✅ 5. Analyser l'état de la permission
                        val shouldShowRationale = activity?.shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) ?: false
                        val alreadyAsked = prefs.getBoolean("bg_loc_asked", false)

                        // Si on a déjà demandé, que l'OS refuse d'afficher l'alerte, ET qu'on a bien trouvé l'activité
                        if (alreadyAsked && !shouldShowRationale && activity != null) {
                            // Plan B : Le blocage est total (bouton "Ne plus demander" coché), on ouvre les paramètres globaux
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } else {
                            // Plan A : La magie d'Android ! Ouvre le sous-menu "Position"
                            prefs.edit().putBoolean("bg_loc_asked", true).apply()
                            bgLocationLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }

                        // ❌ Le Toast a été supprimé !
                    },
                    shape = shape, border = border, bubbleColor = MaterialTheme.colorScheme.errorContainer, useOneUi = useOneUi, safeClick = safeClick,
                    icon = Icons.Default.Place
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    // --- CURSEUR PARTAGÉ (Nettoyé des < 30 min) ---
    fr.geotower.ui.components.CustomSliderCard(
        title = AppStrings.widgetRefreshTitle,
        currentValue = widgetFrequency,
        steps = listOf(30, 45, 60, 120, 240, 480, 720, 1440),
        labels = listOf("30 min", "45 min", "1 h", "2 h", "4 h", "8 h", "12 h", "24 h"),
        onValueChange = { newFreq ->
            widgetFrequency = newFreq
            prefs.edit().putInt("widget_sync_freq", newFreq).apply()

            // Mettre à jour le WorkManager instantanément avec la nouvelle fréquence
            val periodicWork = androidx.work.PeriodicWorkRequestBuilder<fr.geotower.widget.AntennaWidgetWorker>(
                newFreq.toLong(), java.util.concurrent.TimeUnit.MINUTES
            ).build()

            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "WidgetPeriodicUpdate",
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE, // IMPORTANT : Update au lieu de Keep
                periodicWork
            )
        },
        shape = shape,
        border = border,
        bubbleColor = bubbleColor,
        useOneUi = useOneUi,
        footerText = AppStrings.widgetRefreshWarning
    )
}

@Composable
fun SectionSysteme(ctx: Context, shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean, safeClick: (() -> Unit) -> Unit) {
    SectionTitle(AppStrings.system);
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent
    Surface(onClick = { safeClick { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", ctx.packageName, null) }) } }, shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface); Spacer(Modifier.width(16.dp)); Column { Text(AppStrings.managePermissions, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(AppStrings.permissionsDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun SectionDatabase(
    isWideScreen: Boolean,
    shape: Shape,
    bubbleColor: Color,
    useOneUi: Boolean,
    repository: AnfrRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context
) {
    SectionTitle(AppStrings.database)
    var showResetDialog by remember { mutableStateOf(false) }
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

    // On génère la bordure si on n'est pas en OneUI
    val border = if (useOneUi) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

    // 🚀 NOUVEAU : LA CARTE DES CARTES HORS-LIGNE
    fr.geotower.ui.components.MapDownloadCard(
        useOneUi = useOneUi,
        shape = shape,
        border = border,
        bubbleColor = bubbleColor
    )

    Spacer(modifier = Modifier.height(16.dp)) // Espace entre les deux cartes

    // 🚀 LA CARTE DE LA BASE DE DONNÉES (Existante)
    fr.geotower.ui.components.DatabaseDownloadCard(
        useOneUi = useOneUi,
        shape = shape,
        border = border,
        bubbleColor = bubbleColor,
        title = AppStrings.database
    )

    if (!isWideScreen) {
        Spacer(modifier = Modifier.height(32.dp))
        TextButton(onClick = { showResetDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text(text = AppStrings.resetSettings, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(text = AppStrings.resetWarningTitle, fontWeight = FontWeight.Bold) },
            text = { Text(AppStrings.resetWarningDesc) },
            shape = shape,
            containerColor = MaterialTheme.colorScheme.surface,
            dismissButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    prefs.edit().clear().apply()
                    prefs.edit().putBoolean("isFirstRun", false).apply()
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Runtime.getRuntime().exit(0)
                }) { Text(AppStrings.yes, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            confirmButton = {
                Button(onClick = { showResetDialog = false }, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White)) {
                    Text(AppStrings.no, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun SettingsOptionCard(label: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier, shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean) {
    val themeMode by AppConfig.themeMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val paleBgColor = if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    val paleTextColor = if (isDark) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer

    val finalColor = if (isSelected) paleBgColor else (if (useOneUi) bubbleColor else Color.Transparent)
    val contentColor = if (isSelected) paleTextColor else MaterialTheme.colorScheme.onSurface

    Surface(onClick = onClick, modifier = modifier.height(80.dp), shape = shape, border = if (isSelected) null else border, color = finalColor) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = contentColor); Spacer(Modifier.height(8.dp)); Text(label, style = MaterialTheme.typography.labelMedium, color = contentColor)
        }
    }
}

@Composable
fun PreferenceSwitchCard(title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean) {
    // Toujours primary !
    val accentColor = MaterialTheme.colorScheme.primary

    val cardBg = if (useOneUi) bubbleColor else Color.Transparent
    Surface(shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (useOneUi) {
                fr.geotower.ui.components.OneUiSwitch(checked, onCheckedChange)
            } else {
                Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedTrackColor = accentColor))
            }
        }
    }
}

@Composable
fun PreferenceActionCard(
    title: String,
    desc: String,
    onClick: () -> Unit,
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean,
    safeClick: (() -> Unit) -> Unit,
    icon: ImageVector? = null // ✅ Garde le paramètre d'icône (si pas déjà fait)
) {
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent
    Surface(onClick = { safeClick { onClick() } }, shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {

            // ✅ LE TEXTE RESTE À GAUCHE (Prend toute la place dispo grâce au weight)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (desc.isNotEmpty()) {
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ✅ AJOUT DE L'ICÔNE ICI (À droite du texte, avant la flèche)
            if (icon != null) {
                Spacer(modifier = Modifier.width(12.dp)) // Espace avec le texte
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp) // Légèrement plus petite pour l'équilibre
                )
            }

            // ✅ LA FLÈCHE RESTE TOUT À DROITE
            Spacer(modifier = Modifier.width(8.dp)) // Espace avec l'icône
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PreferenceOperatorCard(title: String, operator: String, onClick: () -> Unit, shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean, safeClick: (() -> Unit) -> Unit) {
    val logoRes = when (operator) { "Orange" -> R.drawable.logo_orange; "Bouygues Telecom", "Bouygues" -> R.drawable.logo_bouygues; "SFR" -> R.drawable.logo_sfr; "Free" -> R.drawable.logo_free; else -> null }
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent
    Surface(onClick = { safeClick { onClick() } }, shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (operator == "Aucun") "Sélectionner" else "Actuel : $operator", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (logoRes != null) {
                    Image(painterResource(logoRes), contentDescription = null, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)))
                    Spacer(Modifier.width(12.dp))
                } else {
                    // ✅ AJOUT DE L'ICÔNE PAR DÉFAUT ICI (Si aucun opérateur n'est choisi)
                    Icon(
                        imageVector = Icons.Default.SimCard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PreferenceLanguageCard(title: String, language: String, onClick: () -> Unit, shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean, safeClick: (() -> Unit) -> Unit) {
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent

    // Détermination de l'emoji en fonction de la langue
    val flag = when (language) {
        "Français" -> "🇫🇷"
        "English" -> "🇬🇧"
        "Português" -> "🇵🇹"
        "Système" -> "📱"
        else -> "🌐"
    }

    Surface(onClick = { safeClick { onClick() } }, shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(AppStrings.current(language), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // L'EMOJI REMPLACE L'ICÔNE PLANÈTE
                Box(
                    modifier = Modifier.size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = flag, fontSize = 24.sp)
                }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PreferenceIconCard(title: String, logoRes: Int, onClick: () -> Unit, shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean, safeClick: (() -> Unit) -> Unit) {
    val cardBg = if (useOneUi) bubbleColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    Surface(onClick = { safeClick { onClick() } }, shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF1C1C1E), modifier = Modifier.size(32.dp)) { DrawableImage(logoRes, Modifier.fillMaxSize()) }
                Spacer(Modifier.width(12.dp)); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun NavigationMenuItem(title: String, icon: ImageVector, isSelected: Boolean, isDark: Boolean, onClick: () -> Unit) {
    // 1. On utilise le beau bleu dynamique pour l'élément sélectionné
    val activeColor = MaterialTheme.colorScheme.primary
    // 2. On utilise le gris par défaut d'Android pour les éléments inactifs
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    // 3. Fond légèrement bleuté (15% d'opacité) si sélectionné, sinon transparent
    val bgColor = if (isSelected) activeColor.copy(alpha = 0.15f) else Color.Transparent

    // 4. Couleur du texte et de l'icône
    val contentColor = if (isSelected) activeColor else inactiveColor

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = bgColor
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun NavigationModeOption(
    title: String,
    desc: String,
    isSelected: Boolean,
    useOneUi: Boolean,
    // ✅ NOUVEAU PARAMÈTRE : trailingIcon
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val border = if (useOneUi && isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = border
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(desc, style = MaterialTheme.typography.bodySmall, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ✅ NOUVELLE LOGIQUE : Affichage de l'icône descriptive
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    // Légèrement transparent si non sélectionné, coloré si sélectionné
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    // Espacement avant la coche si sélectionné, sinon aligné à droite
                    modifier = Modifier.padding(end = if(isSelected) 8.dp else 0.dp).size(20.dp)
                )
            }

            if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconSheet(
    onDismiss: () -> Unit,
    currentIconRes: Int,
    onToggle: (Int) -> Unit,
    context: Context,
    sheetState: SheetState,
    useOneUi: Boolean,
    safeClick: (() -> Unit) -> Unit
) {
    // 1. On détermine l'index initial (0, 1 ou 2) en fonction de l'image actuellement active
    val initialIndex = when (currentIconRes) {
        R.mipmap.ic_launcher_georadio -> 1
        R.mipmap.ic_launcher_funny -> 2
        else -> 0
    }

    // 2. On crée une variable temporaire pour stocker le clic avant la validation
    var tempIconIndex by remember { mutableIntStateOf(initialIndex) }

    // --- AJOUTS POUR OLED ---
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        Column(modifier = Modifier.padding(bottom = 48.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(AppStrings.appIcon, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 32.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {

                // --- LOGO 1 (Classique) : Index 0 ---
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Clic sur l'image : on change juste la variable temporaire (pas de onDismiss/onToggle)
                    Surface(onClick = { safeClick { tempIconIndex = 0 } }, shape = RoundedCornerShape(22.dp), color = Color(0xFF1C1C1E), modifier = Modifier.size(70.dp)) { DrawableImage(R.mipmap.ic_launcher_geotower, Modifier.fillMaxSize()) }
                    Spacer(Modifier.height(12.dp))
                    val isSelected = tempIconIndex == 0
                    // Clic sur le cercle radio
                    if(useOneUi) fr.geotower.ui.components.OneUiRadioButton(isSelected) { tempIconIndex = 0 } else RadioButton(selected = isSelected, onClick = { tempIconIndex = 0 })
                }

                // --- LOGO 2 (Radio) : Index 1 ---
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(onClick = { safeClick { tempIconIndex = 1 } }, shape = RoundedCornerShape(22.dp), color = Color(0xFF1C1C1E), modifier = Modifier.size(70.dp)) { DrawableImage(R.mipmap.ic_launcher_georadio, Modifier.fillMaxSize()) }
                    Spacer(Modifier.height(12.dp))
                    val isSelected = tempIconIndex == 1
                    if(useOneUi) fr.geotower.ui.components.OneUiRadioButton(isSelected) { tempIconIndex = 1 } else RadioButton(selected = isSelected, onClick = { tempIconIndex = 1 })
                }

                // --- LOGO 3 (Funny) : Index 2 ---
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(onClick = { safeClick { tempIconIndex = 2 } }, shape = RoundedCornerShape(22.dp), color = Color(0xFF1C1C1E), modifier = Modifier.size(70.dp)) { DrawableImage(R.mipmap.ic_launcher_funny, Modifier.fillMaxSize()) }
                    Spacer(Modifier.height(12.dp))
                    val isSelected = tempIconIndex == 2
                    if(useOneUi) fr.geotower.ui.components.OneUiRadioButton(isSelected) { tempIconIndex = 2 } else RadioButton(selected = isSelected, onClick = { tempIconIndex = 2 })
                }
            }

            Text(AppStrings.restartToApply, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 24.dp, bottom = 16.dp))

            // --- NOUVEAU : BOUTON VALIDER ---
            Button(
                onClick = {
                    safeClick {
                        onToggle(tempIconIndex) // On applique le changement
                        onDismiss() // On ferme la fenêtre
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text(AppStrings.validate, fontWeight = FontWeight.Bold)
            }
        }
    }
}


@Composable
fun SettingsTopBar(onBack: () -> Unit) { Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }; Text(AppStrings.settingsTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center); Spacer(Modifier.width(48.dp)) } }

fun Modifier.settingsFadingEdge(scrollState: ScrollState): Modifier { if (!AppConfig.isBlurEnabled.value) return this; val fadeHeight = 80.dp; return this.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent { drawContent(); val heightPx = fadeHeight.toPx(); val topAlpha = (scrollState.value / heightPx).coerceIn(0f, 1f); drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 1f - topAlpha), Color.Black), 0f, heightPx), blendMode = BlendMode.DstIn); val remainingScroll = scrollState.maxValue - scrollState.value; val bottomAlpha = (remainingScroll / heightPx).coerceIn(0f, 1f); drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 1f - bottomAlpha)), size.height - heightPx, size.height), blendMode = BlendMode.DstIn) } }

@Composable
fun DrawableImage(resId: Int, modifier: Modifier = Modifier) { AndroidView({ ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } }, modifier, { view -> view.setImageResource(resId) }) }


