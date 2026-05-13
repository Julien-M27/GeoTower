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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings
import android.content.Context
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
import androidx.compose.runtime.MutableState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.components.rememberReorderableDragState
import kotlin.math.roundToInt


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
    // --- NOUVEAUX PARAMÈTRES ---
    onSupportClick: () -> Unit,
    onSiteClick: () -> Unit,
    onThroughputCalculatorClick: () -> Unit,
    onOpenFrequencies: () -> Unit
) {
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        Column(modifier = Modifier.padding(bottom = 48.dp, start = 16.dp, end = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(
                    text = AppStrings.pagesCustomizationTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(48.dp))
            }

            Text(
                text = AppStrings.pagesCustomizationDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                textAlign = TextAlign.Center
            )

            // 1. La page de démarrage
            NavigationMenuItem(title = AppStrings.startupPageSettings, icon = Icons.AutoMirrored.Filled.Launch, isSelected = false, isDark = isDark) { onStartupPageClick() }
            Spacer(Modifier.height(12.dp))

            // 2. Les menus individuels des pages
            NavigationMenuItem(title = AppStrings.pageHomeSettings, icon = Icons.Default.Home, isSelected = false, isDark = isDark) { onHomeClick() }
            NavigationMenuItem(title = AppStrings.pageNearbySettings, icon = Icons.Default.NearMe, isSelected = false, isDark = isDark) { onNearbyClick() }
            NavigationMenuItem(title = AppStrings.pageMapSettings, icon = Icons.Default.Map, isSelected = false, isDark = isDark) { onMapClick() }
            if (AppConfig.hasCompass.value) {
                NavigationMenuItem(title = AppStrings.pageCompassSettings, icon = Icons.Default.Explore, isSelected = false, isDark = isDark) { onCompassClick() }
            }

            // --- NOUVELLES SECTIONS ---
            NavigationMenuItem(title = AppStrings.pageSupportSettings, icon = Icons.Default.VerticalAlignTop, isSelected = false, isDark = isDark) { onSupportClick() }
            NavigationMenuItem(title = AppStrings.pageSiteSettings, icon = Icons.Default.WifiTethering, isSelected = false, isDark = isDark) { onSiteClick() }
            NavigationMenuItem(title = AppStrings.throughputCalculatorTitle, icon = Icons.Default.Speed, isSelected = false, isDark = isDark) { onThroughputCalculatorClick() }
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
    var tempPage by remember { mutableStateOf(currentStartupPage) }
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        BackHandler(onBack = onBack)
        Column(modifier = Modifier.padding(bottom = 48.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(AppStrings.startupPageSettings, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
            Spacer(Modifier.height(24.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsRadioItem(AppStrings.pageHomeSettings, tempPage == "home", useOneUi, bubbleColor) { tempPage = "home" }
                SettingsRadioItem(AppStrings.pageNearbySettings, tempPage == "nearby", useOneUi, bubbleColor) { tempPage = "nearby" }
                SettingsRadioItem(AppStrings.pageMapSettings, tempPage == "map", useOneUi, bubbleColor) { tempPage = "map" }
                if (AppConfig.hasCompass.value) {
                    SettingsRadioItem(AppStrings.pageCompassSettings, tempPage == "compass", useOneUi, bubbleColor) { tempPage = "compass" }
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
                    Text(AppStrings.resetToDefault, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())

                // Bouton Valider classique
                Button(
                    onClick = { onPageSelected(tempPage); onDismiss() },
                    modifier = Modifier.fillMaxWidth().height(50.dp).padding(top = 8.dp),
                    shape = RoundedCornerShape(25.dp)
                ) {
                    Text(AppStrings.validate, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Petit composant esthétique pour les sélections
@Composable
fun SettingsRadioItem(name: String, isSelected: Boolean, useOneUi: Boolean, bubbleColor: Color, onClick: () -> Unit) {
    val accentColor = MaterialTheme.colorScheme.primary
    val activeBg = accentColor.copy(alpha = 0.1f)
    val inactiveBg = if (useOneUi) bubbleColor else Color.Transparent
    val bgColor = if (isSelected) activeBg else inactiveBg
    val border = if (useOneUi) { if (isSelected) BorderStroke(2.dp, accentColor) else null } else { BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) }
    val shape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)

    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().height(60.dp), color = bgColor, border = border, shape = shape) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = name, style = MaterialTheme.typography.titleMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
            if (useOneUi) {
                Box(modifier = Modifier.size(24.dp).border(2.dp, if (isSelected) accentColor else MaterialTheme.colorScheme.outline, CircleShape).padding(5.dp), contentAlignment = Alignment.Center) {
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
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    var showLogo by remember { mutableStateOf(prefs.getBoolean("show_home_logo", true)) }
    var showHelpButton by remember { mutableStateOf(prefs.getBoolean("show_home_help", true)) }
    var helpButtonPosition by remember { mutableStateOf(prefs.getString("home_help_position", "bottom_end") ?: "bottom_end") }
    var showLogoSettings by remember { mutableStateOf(false) }
    var logoSelectorResetKey by remember { mutableStateOf(0) }
    var showHelpPositionSettings by remember { mutableStateOf(false) }
    val safeClick = rememberSafeClick()

    // ---> 2. SÉCURITÉ ET RÉACTIVITÉ : Assure que le logo est dans la liste <---
    val safeOrder = remember(pagesOrder) {
        if (!pagesOrder.contains("logo")) pagesOrder + listOf("logo") else pagesOrder
    }
    // On mémorise la liste sécurisée en temps réel pour le glisser-déposer
    val currentOrder by rememberUpdatedState(safeOrder)

    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        BackHandler {
            when {
                showLogoSettings -> showLogoSettings = false
                showHelpPositionSettings -> showHelpPositionSettings = false
                else -> onBack()
            }
        }
        Column(modifier = Modifier.padding(bottom = 48.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showLogoSettings) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showLogoSettings = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                    Text(AppStrings.pageHomeLogoSettings, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Spacer(Modifier.width(48.dp))
                }

                key(logoSelectorResetKey) {
                    fr.geotower.ui.components.HomeLogoSelectorBlock(
                        safeClick = safeClick
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                TextButton(
                    onClick = {
                        prefs.edit().putString("home_logo_choice", "app").apply()
                        logoSelectorResetKey++
                    }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(AppStrings.resetToDefault, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
            } else if (showHelpPositionSettings) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showHelpPositionSettings = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                    Text(AppStrings.homeHelpPositionSettings, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Spacer(Modifier.width(48.dp))
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val savePosition = { position: String ->
                        helpButtonPosition = position
                        prefs.edit().putString("home_help_position", position).apply()
                    }

                    SettingsRadioItem(AppStrings.positionTopLeft, helpButtonPosition == "top_start", useOneUi, bubbleColor) { savePosition("top_start") }
                    SettingsRadioItem(AppStrings.positionTopRight, helpButtonPosition == "top_end", useOneUi, bubbleColor) { savePosition("top_end") }
                    SettingsRadioItem(AppStrings.positionBottomLeft, helpButtonPosition == "bottom_start", useOneUi, bubbleColor) { savePosition("bottom_start") }
                    SettingsRadioItem(AppStrings.positionBottomRight, helpButtonPosition == "bottom_end", useOneUi, bubbleColor) { savePosition("bottom_end") }
                }

                Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
            } else {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                    Text(AppStrings.pageHomeSettings, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Spacer(Modifier.width(48.dp))
                }
                Text(AppStrings.dragToReorderHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp), textAlign = TextAlign.Center)

                val cardHeight = 64.dp
                val spacing = 12.dp
                val reorderState = rememberReorderableDragState(currentOrder, cardHeight, spacing, onOrderChange)

                val shape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)
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
                                AppStrings.pageHomeLogoSettings, showLogo,
                                { showLogo = it; prefs.edit().putBoolean("show_home_logo", it).apply() },
                                shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight,
                                onSettingsClick = { showLogoSettings = true }
                            )
                            "nearby" -> DraggableSwitchCard(AppStrings.pageNearbySettings, showNearby, onNearbyChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "map" -> DraggableSwitchCard(AppStrings.pageMapSettings, showMap, onMapChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "compass" -> {
                                if (AppConfig.hasCompass.value) {
                                    DraggableSwitchCard(
                                        AppStrings.pageCompassSettings, showCompass, onCompassChange,
                                        shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight
                                    )
                                }
                            }
                            "stats" -> DraggableSwitchCard(AppStrings.statsGroupTitle, showStats, onStatsChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)

                            // --- NOUVEAU : AJOUT DE LA CARTE PARAMÈTRES ---
                            "settings" -> DraggableSwitchCard(AppStrings.settingsTitle, true, {}, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight, hideSwitch = true)
                        }
                    }
                }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ConfigurableSwitchCard(
                        title = AppStrings.homeHelpSettings,
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
                Spacer(Modifier.width(8.dp))
                Text(AppStrings.resetToDefault, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
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
                shadowElevation = if (isDragged) 8.dp.toPx() else 0f
            }
            .then(dragModifier)
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DragHandle, contentDescription = AppStrings.move, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
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
                        contentDescription = AppStrings.settingsTitle,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // --- NOUVEAU : GESTION DU CADENAS VS SWITCH ---
            if (hideSwitch) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            } else {
                if (useOneUi) {
                    fr.geotower.ui.components.OneUiSwitch(checked, onCheckedChange)
                } else {
                    // ✅ MODIFICATION : On utilise switchColor
                    Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedTrackColor = switchColor))
                }
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
    val switchColor = MaterialTheme.colorScheme.primary
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent

    Surface(shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = AppStrings.settingsTitle,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            if (useOneUi) {
                fr.geotower.ui.components.OneUiSwitch(checked, onCheckedChange)
            } else {
                Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedTrackColor = switchColor))
            }
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
    val currentOrder by rememberUpdatedState(nearbyOrder)
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        BackHandler(onBack = onBack)
        Column(modifier = Modifier.padding(bottom = 48.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(AppStrings.pageNearbySettings, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
            Text(AppStrings.dragToReorderHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp), textAlign = TextAlign.Center)

            val cardHeight = 64.dp
            val spacing = 12.dp
            val reorderState = rememberReorderableDragState(currentOrder, cardHeight, spacing, onOrderChange)

            val shape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)
            val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null

            // --- 1. LES BLOCS GLISSER/DÉPOSER ---
            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                currentOrder.forEach { blockId ->
                    key(blockId) {
                        val isDragged = reorderState.isDragged(blockId)
                        val dragModifier = reorderState.dragModifier(blockId)
                        val dragOffset = reorderState.offsetFor(blockId)

                        when (blockId) {
                            "search" -> DraggableSwitchCard(AppStrings.nearbySearchOption, showSearch, onSearchChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "sites" -> DraggableSwitchCard(AppStrings.nearbySitesOption, showSites, onSitesChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))
            SimpleSwitchCard(
                title = AppStrings.nearbySearchSuggestionsOption,
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
                    text = AppStrings.resetToDefault,
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
                    Text(AppStrings.searchRadiusTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
    val currentOrder by rememberUpdatedState(compassOrder)
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        BackHandler(onBack = onBack)
        Column(modifier = Modifier.padding(bottom = 48.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(AppStrings.pageCompassSettings, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
            Text(AppStrings.dragToReorderHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp), textAlign = TextAlign.Center)

            val cardHeight = 64.dp
            val spacing = 12.dp
            val reorderState = rememberReorderableDragState(currentOrder, cardHeight, spacing, onOrderChange)

            val shape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)
            val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null

            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                currentOrder.forEach { blockId ->
                    key(blockId) {
                        val isDragged = reorderState.isDragged(blockId)
                        val dragModifier = reorderState.dragModifier(blockId)
                        val dragOffset = reorderState.offsetFor(blockId)

                        when (blockId) {
                            "location" -> DraggableSwitchCard(AppStrings.compassLocationOption, showLocation, onLocationChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "gps" -> DraggableSwitchCard(AppStrings.compassGpsOption, showGps, onGpsChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "accuracy" -> DraggableSwitchCard(AppStrings.compassAccuracyOption, showAccuracy, onAccuracyChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = {
                onOrderChange(listOf("location", "gps", "accuracy"))
                onLocationChange(true)
                onGpsChange(true)
                onAccuracyChange(true)
            }) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(AppStrings.resetToDefault, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        BackHandler(onBack = onBack)
        Column(modifier = Modifier.padding(bottom = 48.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(AppStrings.pageMapSettings, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }

            val shape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)
            val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SimpleSwitchCard(AppStrings.mapLocationOption, showMapLocation = showLocation, onLocationChange = onLocationChange, shape = shape, border = border, bubbleColor = bubbleColor, useOneUi = useOneUi)
                SimpleSwitchCard(AppStrings.mapZoomOption, showMapLocation = showZoom, onLocationChange = onZoomChange, shape = shape, border = border, bubbleColor = bubbleColor, useOneUi = useOneUi)
                SimpleSwitchCard(AppStrings.mapToolboxOption, showMapLocation = showToolbox, onLocationChange = onToolboxChange, shape = shape, border = border, bubbleColor = bubbleColor, useOneUi = useOneUi)
                if (AppConfig.hasCompass.value) {
                    SimpleSwitchCard(AppStrings.mapCompassOption, showMapLocation = showCompass, onLocationChange = onCompassChange, shape = shape, border = border, bubbleColor = bubbleColor, useOneUi = useOneUi)
                }
                SimpleSwitchCard(AppStrings.showSpeedometer, showMapLocation = showSpeedometer, onLocationChange = onSpeedometerChange, shape = shape, border = border, bubbleColor = bubbleColor, useOneUi = useOneUi)
                SimpleSwitchCard(AppStrings.mapScaleOption, showMapLocation = showScale, onLocationChange = onScaleChange, shape = shape, border = border, bubbleColor = bubbleColor, useOneUi = useOneUi)
                SimpleSwitchCard(AppStrings.mapAttributionOption, showMapLocation = showAttribution, onLocationChange = onAttributionChange, shape = shape, border = border, bubbleColor = bubbleColor, useOneUi = useOneUi)
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = {
                onLocationChange(true)
                onZoomChange(true)
                onToolboxChange(true)
                onCompassChange(true)
                onSpeedometerChange(true)
                onScaleChange(true)
                onAttributionChange(true)
            }) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(AppStrings.resetToDefault, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
        }
    }
}

@Composable
fun SimpleSwitchCard(title: String, showMapLocation: Boolean, onLocationChange: (Boolean) -> Unit, shape: androidx.compose.ui.graphics.Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean) {
    val switchColor = MaterialTheme.colorScheme.primary
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent

    Surface(shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if (useOneUi) {
                fr.geotower.ui.components.OneUiSwitch(showMapLocation, onLocationChange)
            } else {
                Switch(checked = showMapLocation, onCheckedChange = onLocationChange, colors = SwitchDefaults.colors(checkedTrackColor = switchColor))
            }
        }
    }
}

private val defaultThroughputBlockOrder = listOf("header", "summary", "cone", "controls", "bands", "assumptions")

private fun normalizeThroughputBlockOrder(order: List<String>): List<String> {
    val knownBlocks = defaultThroughputBlockOrder.toSet()
    val normalized = order.map { it.trim() }.filter { it in knownBlocks }.distinct().toMutableList()
    defaultThroughputBlockOrder.forEach { block ->
        if (!normalized.contains(block)) normalized.add(block)
    }
    return normalized
}

@Composable
private fun throughputBlockTitle(blockId: String): String {
    return AppStrings.throughputBlockTitle(blockId)
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
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val shape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)
    val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null
    val currentOrder by rememberUpdatedState(normalizeThroughputBlockOrder(throughputOrder))

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        BackHandler(onBack = onBack)
        val cardHeight = 64.dp
        val spacing = 12.dp
        val reorderState = rememberReorderableDragState(currentOrder, cardHeight, spacing, onThroughputOrderChange)

        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(AppStrings.throughputCalculatorTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }

            SimpleSwitchCard(
                title = AppStrings.siteThroughputCalculatorOption,
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
            Text(AppStrings.dragToReorderHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp), textAlign = TextAlign.Center)

            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                currentOrder.forEach { blockId ->
                    key(blockId) {
                        val isDragged = reorderState.isDragged(blockId)
                        val dragModifier = reorderState.dragModifier(blockId)
                        val dragOffset = reorderState.offsetFor(blockId)
                        val checked = when (blockId) {
                            "header" -> showHeader
                            "summary" -> showSummary
                            "cone" -> showCone
                            "controls" -> showControls
                            "bands" -> showBands
                            "assumptions" -> showAssumptions
                            else -> true
                        }
                        val onCheckedChange: (Boolean) -> Unit = when (blockId) {
                            "header" -> onHeaderChange
                            "summary" -> onSummaryChange
                            "cone" -> onConeChange
                            "controls" -> onControlsChange
                            "bands" -> onBandsChange
                            "assumptions" -> onAssumptionsChange
                            else -> { _: Boolean -> }
                        }
                        DraggableSwitchCard(
                            throughputBlockTitle(blockId),
                            checked,
                            onCheckedChange,
                            shape,
                            border,
                            bubbleColor,
                            useOneUi,
                            dragModifier,
                            isDragged,
                            dragOffset,
                            cardHeight,
                            onSettingsClick = if (blockId == "controls") {
                                {
                                    onDismiss()
                                    onOpenCalculationDefaults()
                                }
                            } else {
                                null
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = {
                onThroughputCalculatorChange(true)
                onThroughputOrderChange(defaultThroughputBlockOrder)
                onHeaderChange(true)
                onSummaryChange(true)
                onConeChange(true)
                onControlsChange(true)
                onBandsChange(true)
                onAssumptionsChange(true)
            }) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(AppStrings.resetToDefault, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
        }
    }
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
    val prefs = remember(context) { context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE) }
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val shape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)
    val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null

    var preset by remember { mutableStateOf(prefs.getString("throughput_default_preset", "conservative") ?: "conservative") }
    var lteDownIndex by remember { mutableStateOf(prefs.getInt("throughput_custom_lte_down", 3).coerceIn(0, throughputModulationLabels.lastIndex)) }
    var lteUpIndex by remember { mutableStateOf(prefs.getInt("throughput_custom_lte_up", 2).coerceIn(0, throughputModulationLabels.lastIndex)) }
    var nrDownIndex by remember { mutableStateOf(prefs.getInt("throughput_custom_nr_down", 3).coerceIn(0, throughputModulationLabels.lastIndex)) }
    var nrUpIndex by remember { mutableStateOf(prefs.getInt("throughput_custom_nr_up", 2).coerceIn(0, throughputModulationLabels.lastIndex)) }
    var include4G by remember { mutableStateOf(prefs.getBoolean("throughput_include_4g", true)) }
    var include5G by remember { mutableStateOf(prefs.getBoolean("throughput_include_5g", true)) }
    var includePlanned by remember { mutableStateOf(prefs.getBoolean("throughput_include_planned", false)) }
    var bandSelection by remember {
        mutableStateOf(
            throughputBandDefaults
                .flatMap { it.bands }
                .associate { band -> band.prefSuffix to prefs.getBoolean("throughput_band_${band.prefSuffix}", true) }
        )
    }

    fun savePreset(value: String) {
        preset = value
        prefs.edit().putString("throughput_default_preset", value).apply()
    }

    fun saveInt(key: String, value: Int, update: (Int) -> Unit) {
        val coerced = value.coerceIn(0, throughputModulationLabels.lastIndex)
        update(coerced)
        prefs.edit().putInt(key, coerced).apply()
    }

    fun saveBool(key: String, value: Boolean, update: (Boolean) -> Unit) {
        update(value)
        prefs.edit().putBoolean(key, value).apply()
    }

    fun saveBand(prefSuffix: String, value: Boolean) {
        bandSelection = bandSelection + (prefSuffix to value)
        prefs.edit().putBoolean("throughput_band_$prefSuffix", value).apply()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        BackHandler(onBack = onBack)
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(
                    AppStrings.throughputCalculationSettingsTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(48.dp))
            }

            Text(
                AppStrings.throughputDefaultModeTitle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                throughputPresetDefaults.forEach { option ->
                    FilterChip(
                        selected = preset == option.id,
                        onClick = { savePreset(option.id) },
                        label = { Text(option.label()) }
                    )
                }
            }

            AnimatedVisibility(visible = preset == "custom") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        AppStrings.throughputCustomModulationTitle,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    ThroughputDefaultModulationSlider(AppStrings.throughput4gDownloadLabel, lteDownIndex, useOneUi) {
                        saveInt("throughput_custom_lte_down", it) { value -> lteDownIndex = value }
                    }
                    ThroughputDefaultModulationSlider(AppStrings.throughput4gUploadLabel, lteUpIndex, useOneUi) {
                        saveInt("throughput_custom_lte_up", it) { value -> lteUpIndex = value }
                    }
                    ThroughputDefaultModulationSlider(AppStrings.throughput5gDownloadLabel, nrDownIndex, useOneUi) {
                        saveInt("throughput_custom_nr_down", it) { value -> nrDownIndex = value }
                    }
                    ThroughputDefaultModulationSlider(AppStrings.throughput5gUploadLabel, nrUpIndex, useOneUi) {
                        saveInt("throughput_custom_nr_up", it) { value -> nrUpIndex = value }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(Modifier.height(12.dp))

            SimpleSwitchCard(AppStrings.throughputInclude4g, include4G, { saveBool("throughput_include_4g", it) { value -> include4G = value } }, shape, border, bubbleColor, useOneUi)
            Spacer(Modifier.height(8.dp))
            SimpleSwitchCard(AppStrings.throughputInclude5g, include5G, { saveBool("throughput_include_5g", it) { value -> include5G = value } }, shape, border, bubbleColor, useOneUi)
            Spacer(Modifier.height(8.dp))
            SimpleSwitchCard(AppStrings.throughputIncludePlanned, includePlanned, { saveBool("throughput_include_planned", it) { value -> includePlanned = value } }, shape, border, bubbleColor, useOneUi)

            Spacer(Modifier.height(20.dp))
            Text(
                AppStrings.throughputDefaultFrequencyBandsTitle,
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
                preset = "conservative"
                lteDownIndex = 3
                lteUpIndex = 2
                nrDownIndex = 3
                nrUpIndex = 2
                include4G = true
                include5G = true
                includePlanned = false
                bandSelection = throughputBandDefaults.flatMap { it.bands }.associate { it.prefSuffix to true }
                val editor = prefs.edit()
                    .putString("throughput_default_preset", "conservative")
                    .putInt("throughput_custom_lte_down", 3)
                    .putInt("throughput_custom_lte_up", 2)
                    .putInt("throughput_custom_nr_down", 3)
                    .putInt("throughput_custom_nr_up", 2)
                    .putBoolean("throughput_include_4g", true)
                    .putBoolean("throughput_include_5g", true)
                    .putBoolean("throughput_include_planned", false)
                throughputBandDefaults.flatMap { it.bands }.forEach { band ->
                    editor.putBoolean("throughput_band_${band.prefSuffix}", true)
                }
                editor.apply()
            }) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(AppStrings.resetToDefault, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun ThroughputDefaultModulationSlider(
    label: String,
    selectedIndex: Int,
    useOneUi: Boolean,
    onSelectedIndexChange: (Int) -> Unit
) {
    val coercedIndex = selectedIndex.coerceIn(0, throughputModulationLabels.lastIndex)
    val onSliderChange: (Float) -> Unit = { value ->
        onSelectedIndexChange(value.roundToInt().coerceIn(0, throughputModulationLabels.lastIndex))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "$label : ${throughputModulationLabels[coercedIndex]}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (useOneUi) {
            Slider(
                value = coercedIndex.toFloat(),
                onValueChange = onSliderChange,
                valueRange = 0f..throughputModulationLabels.lastIndex.toFloat(),
                steps = (throughputModulationLabels.size - 2).coerceAtLeast(0),
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    )
                },
                track = { _ ->
                    Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
                        val centerY = size.height / 2
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.3f),
                            start = Offset(0f, centerY),
                            end = Offset(size.width, centerY),
                            strokeWidth = 14.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        val stepWidth = size.width / throughputModulationLabels.lastIndex.coerceAtLeast(1)
                        throughputModulationLabels.indices.forEach { index ->
                            drawCircle(
                                color = Color.Gray.copy(alpha = 0.6f),
                                radius = 4.dp.toPx(),
                                center = Offset(index * stepWidth, centerY)
                            )
                        }
                    }
                }
            )
        } else {
            Slider(
                value = coercedIndex.toFloat(),
                onValueChange = onSliderChange,
                valueRange = 0f..throughputModulationLabels.lastIndex.toFloat(),
                steps = (throughputModulationLabels.size - 2).coerceAtLeast(0)
            )
        }
    }
}

private data class ThroughputPresetDefault(
    val id: String,
    val label: @Composable () -> String
)

private data class ThroughputBandDefaultGroup(
    val title: String,
    val bands: List<ThroughputBandDefault>
)

private data class ThroughputBandDefault(
    val prefSuffix: String,
    val label: String
)

private val throughputModulationLabels = listOf("QPSK", "16-QAM", "64-QAM", "256-QAM")

private val throughputPresetDefaults = listOf(
    ThroughputPresetDefault("conservative") { AppStrings.throughputPresetLabel("conservative") },
    ThroughputPresetDefault("standard") { AppStrings.throughputPresetLabel("standard") },
    ThroughputPresetDefault("ideal") { AppStrings.throughputPresetLabel("ideal") },
    ThroughputPresetDefault("custom") { AppStrings.throughputPresetLabel("custom") }
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
            ThroughputBandDefault("5g_3500", "3500 MHz (N78)"),
            ThroughputBandDefault("5g_2100", "2100 MHz (N1)"),
            ThroughputBandDefault("5g_700", "700 MHz (N28)")
        )
    )
)

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
    onDismiss: () -> Unit, onBack: () -> Unit,
    sheetState: SheetState, useOneUi: Boolean, bubbleColor: Color
) {
    val currentOrder by rememberUpdatedState(supportOrder)
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

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
        Column(modifier = Modifier.padding(bottom = 48.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(AppStrings.pageSupportSettings, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
            Text(AppStrings.dragToReorderHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp), textAlign = TextAlign.Center)

            val cardHeight = 64.dp
            val spacing = 12.dp
            val reorderState = rememberReorderableDragState(currentOrder, cardHeight, spacing, onOrderChange)

            val shape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)
            val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null

            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                currentOrder.forEach { blockId ->
                    key(blockId) {
                        val isDragged = reorderState.isDragged(blockId)
                        val dragModifier = reorderState.dragModifier(blockId)
                        val dragOffset = reorderState.offsetFor(blockId)

                        when (blockId) {
                            "map" -> DraggableSwitchCard(AppStrings.supportMapOption, showMap, onMapChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "details" -> DraggableSwitchCard(AppStrings.supportDetailsOption, showDetails, onDetailsChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "photos" -> DraggableSwitchCard(AppStrings.supportPhotosOption, showPhotos, onPhotosChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "open_map" -> DraggableSwitchCard(AppStrings.supportOpenMapOption, showOpenMap, onOpenMapChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "nav" -> DraggableSwitchCard(AppStrings.supportNavOption, showNav, onNavChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "share" -> DraggableSwitchCard(AppStrings.supportShareOption, showShare, onShareChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "operators" -> DraggableSwitchCard(AppStrings.supportOperatorsOption, showOperators, onOperatorsChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = {
                onOrderChange(listOf("map", "details", "photos", "open_map", "nav", "share", "operators"))
                onMapChange(true)
                onDetailsChange(true)
                onPhotosChange(true)
                onOpenMapChange(true)
                onNavChange(true)
                onShareChange(true)
                onOperatorsChange(true)
            }) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(AppStrings.resetToDefault, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
        }
    }
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
    onOpenFrequencies: () -> Unit,
    onOpenPhotosSettings: () -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    sheetState: SheetState,
    useOneUi: Boolean,
    bubbleColor: Color
) {
    val currentOrder by rememberUpdatedState(siteOrder)
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

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
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(AppStrings.pageSiteSettings, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
            Text(AppStrings.dragToReorderHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp), textAlign = TextAlign.Center)

            val cardHeight = 64.dp
            val spacing = 12.dp
            val reorderState = rememberReorderableDragState(currentOrder, cardHeight, spacing, onOrderChange)

            val shape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)
            val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null

            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                currentOrder.forEach { blockId ->
                    key(blockId) {
                        val isDragged = reorderState.isDragged(blockId)
                        val dragModifier = reorderState.dragModifier(blockId)
                        val dragOffset = reorderState.offsetFor(blockId)

                        when (blockId) {
                            "operator" -> DraggableSwitchCard(AppStrings.siteOperatorOption, showOperator, onOperatorChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "bearing_height" -> if (AppConfig.hasCompass.value) DraggableSwitchCard(AppStrings.siteBearingHeightOption, showBearingHeight, onBearingHeightChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "map" -> DraggableSwitchCard(AppStrings.siteMapOption, showMap, onMapChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "support_details" -> DraggableSwitchCard(AppStrings.siteSupportDetailsOption, showSupportDetails, onSupportDetailsChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "photos" -> DraggableSwitchCard(AppStrings.sitePhotosAndSchemesOption, showPhotos, onPhotosChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight, onSettingsClick = {
                                onDismiss()
                                onOpenPhotosSettings()
                            })
                            "ids" -> DraggableSwitchCard(AppStrings.siteIdsOption, showIds, onIdsChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "open_map" -> DraggableSwitchCard(AppStrings.siteOpenMapOption, showOpenMap, onOpenMapChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "elevation_profile" -> DraggableSwitchCard(AppStrings.siteElevationProfileOption, showElevationProfile, onElevationProfileChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "throughput_calculator" -> DraggableSwitchCard(AppStrings.siteThroughputCalculatorOption, showThroughputCalculator, onThroughputCalculatorChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "nav" -> DraggableSwitchCard(AppStrings.siteNavOption, showNav, onNavChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "share" -> DraggableSwitchCard(AppStrings.siteShareOption, showShare, onShareChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "dates" -> DraggableSwitchCard(AppStrings.siteDatesOption, showDates, onDatesChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "address" -> DraggableSwitchCard(AppStrings.siteAddressOption, showAddress, onAddressChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "status" -> DraggableSwitchCard(AppStrings.showStatusOption, showStatus, onStatusChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "speedtest" -> DraggableSwitchCard(AppStrings.showSpeedtestLabel, showSpeedtest, onSpeedtestChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "freqs" -> DraggableSwitchCard(AppStrings.siteFreqsOption, showFreqs, onFreqsChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight, onSettingsClick = {
                                onDismiss()
                                onOpenFrequencies()
                            })
                            "links" -> DraggableSwitchCard(AppStrings.siteLinksOption, showLinks, onLinksChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = {
                onOrderChange(listOf("operator", "bearing_height", "map", "support_details", "elevation_profile", "throughput_calculator", "open_map", "photos", "speedtest", "nav", "share", "panel_heights", "ids", "dates", "address", "status", "freqs", "links"))
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
            }) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(AppStrings.resetToDefault, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteFreqFiltersSheet(
    onDismiss: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
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

    val txtMinOneTechno = AppStrings.minOneTechnoWarning
    val txtMinOneFreq = AppStrings.minOneFreqWarning

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
            AppConfig.siteF5G_3500.value, AppConfig.siteF5G_2100.value, AppConfig.siteF5G_700.value,
            AppConfig.siteF4G_2600.value, AppConfig.siteF4G_2100.value, AppConfig.siteF4G_1800.value,
            AppConfig.siteF4G_900.value, AppConfig.siteF4G_800.value, AppConfig.siteF4G_700.value,
            AppConfig.siteF3G_2100.value, AppConfig.siteF3G_900.value,
            AppConfig.siteF2G_1800.value, AppConfig.siteF2G_900.value
        ).count { it }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        BackHandler(onBack = onBack)
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Text(
                    text = AppStrings.siteFreqFiltersTitle,
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
                        text = AppStrings.freqGridDisplayOption,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        fontSize = 16.sp
                    )

                    val onGridChange = { newValue: Boolean ->
                        saveBool("site_freq_grid_display", AppConfig.siteFreqGridDisplay, newValue)
                    }

                    if (useOneUi) {
                        fr.geotower.ui.components.OneUiSwitch(AppConfig.siteFreqGridDisplay.value, onGridChange)
                    } else {
                        Switch(
                            checked = AppConfig.siteFreqGridDisplay.value,
                            onCheckedChange = onGridChange,
                            colors = SwitchDefaults.colors(checkedTrackColor = switchColor)
                        )
                    }
                }
            }

            AppConfig.siteTechnoOrder.value.forEach { technoId ->
                key(technoId) {
                    val isTechnoDragged = technoDragState.isDragged(technoId)
                    val technoDragOffset = technoDragState.offsetFor(technoId)
                    val technoData = when(technoId) {
                        "5G" -> Triple("5G (NR)", AppConfig.siteShowTechno5G, "site_show_techno_5g") to (listOf("3500" to AppConfig.siteF5G_3500, "2100" to AppConfig.siteF5G_2100, "700" to AppConfig.siteF5G_700) to AppConfig.siteFreqOrder5G)
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
                                if (useOneUi) fr.geotower.ui.components.OneUiSwitch(technoState.value, onTechnoChange)
                                else Switch(checked = technoState.value, onCheckedChange = onTechnoChange, colors = SwitchDefaults.colors(checkedTrackColor = switchColor))
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
                                                    Text("$freqLabel MHz", modifier = Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = if(isFreqDragged) FontWeight.Bold else FontWeight.Normal)
                                                    val onFreqChange = { newValue: Boolean ->
                                                        if (!newValue && getTotalActiveFrequenciesCount() <= 1) {
                                                            android.widget.Toast.makeText(context, txtMinOneFreq, android.widget.Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            saveBool(freqPrefKey, freqState, newValue)
                                                            if (!newValue && freqList.count { it.second.value } == 0) saveBool(technoKey, technoState, false)
                                                            else if (newValue && !technoState.value) saveBool(technoKey, technoState, true)
                                                        }
                                                    }
                                                    if (useOneUi) Box(modifier = Modifier.scale(0.85f)) { fr.geotower.ui.components.OneUiSwitch(freqState.value, onFreqChange) }
                                                    else Switch(checked = freqState.value, onCheckedChange = onFreqChange, modifier = Modifier.scale(0.8f), colors = SwitchDefaults.colors(checkedTrackColor = switchColor))
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
                        Text(AppStrings.spectrumTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        val onSpectrumChange = { newValue: Boolean ->
                            saveBool("site_show_spectrum", AppConfig.siteShowSpectrum, newValue)
                            saveBool("site_show_spectrum_band", AppConfig.siteShowSpectrumBand, newValue)
                            saveBool("site_show_spectrum_total", AppConfig.siteShowSpectrumTotal, newValue)
                        }
                        if (useOneUi) fr.geotower.ui.components.OneUiSwitch(AppConfig.siteShowSpectrum.value, onSpectrumChange)
                        else Switch(checked = AppConfig.siteShowSpectrum.value, onCheckedChange = onSpectrumChange, colors = SwitchDefaults.colors(checkedTrackColor = switchColor))
                    }
                    AnimatedVisibility(visible = AppConfig.siteShowSpectrum.value) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(AppStrings.spectrumByBand, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                val onBandChange = { newValue: Boolean ->
                                    saveBool("site_show_spectrum_band", AppConfig.siteShowSpectrumBand, newValue)
                                    if (!newValue && !AppConfig.siteShowSpectrumTotal.value) saveBool("site_show_spectrum", AppConfig.siteShowSpectrum, false)
                                    else if (newValue && !AppConfig.siteShowSpectrum.value) saveBool("site_show_spectrum", AppConfig.siteShowSpectrum, true)
                                }
                                if (useOneUi) Box(modifier = Modifier.scale(0.85f)) { fr.geotower.ui.components.OneUiSwitch(AppConfig.siteShowSpectrumBand.value, onBandChange) }
                                else Switch(checked = AppConfig.siteShowSpectrumBand.value, onCheckedChange = onBandChange, modifier = Modifier.scale(0.8f), colors = SwitchDefaults.colors(checkedTrackColor = switchColor))
                            }
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(AppStrings.totalspectrum, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                val onTotalChange = { newValue: Boolean ->
                                    saveBool("site_show_spectrum_total", AppConfig.siteShowSpectrumTotal, newValue)
                                    if (!newValue && !AppConfig.siteShowSpectrumBand.value) saveBool("site_show_spectrum", AppConfig.siteShowSpectrum, false)
                                    else if (newValue && !AppConfig.siteShowSpectrum.value) saveBool("site_show_spectrum", AppConfig.siteShowSpectrum, true)
                                }
                                if (useOneUi) Box(modifier = Modifier.scale(0.85f)) { fr.geotower.ui.components.OneUiSwitch(AppConfig.siteShowSpectrumTotal.value, onTotalChange) }
                                else Switch(checked = AppConfig.siteShowSpectrumTotal.value, onCheckedChange = onTotalChange, modifier = Modifier.scale(0.8f), colors = SwitchDefaults.colors(checkedTrackColor = switchColor))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(
                onClick = {
                    AppConfig.siteTechnoOrder.value = listOf("5G", "4G", "3G", "2G", "FH")
                    AppConfig.siteFreqOrder5G.value = listOf("3500", "2100", "700")
                    AppConfig.siteFreqOrder4G.value = listOf("2600", "2100", "1800", "900", "800", "700")
                    AppConfig.siteFreqOrder3G.value = listOf("2100", "900")
                    AppConfig.siteFreqOrder2G.value = listOf("1800", "900")
                    prefs.edit()
                        .putString("site_techno_order", "5G,4G,3G,2G,FH")
                        .putString("site_freq_5g_order", "3500,2100,700")
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
                    saveBool("site_f5g_2100", AppConfig.siteF5G_2100, true)
                    saveBool("site_f5g_700", AppConfig.siteF5G_700, true)
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
                Text(AppStrings.resetToDefault, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitePhotosSettingsSheet(
    onDismiss: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val switchColor = MaterialTheme.colorScheme.primary
    val useOneUi = AppConfig.useOneUiDesign

    fun saveBool(key: String, state: androidx.compose.runtime.MutableState<Boolean>, value: Boolean) {
        state.value = value
        prefs.edit().putBoolean(key, value).apply()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        BackHandler(onBack = onBack)
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Text(
                    text = AppStrings.sitePhotosSettingsTitle,
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
                        Text(AppStrings.sitePhotosAndSchemesOption, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), fontSize = 16.sp)
                        val onMasterChange = { newValue: Boolean ->
                            saveBool("page_site_photos", AppConfig.siteShowPhotos, newValue)
                        }
                        if (useOneUi) fr.geotower.ui.components.OneUiSwitch(AppConfig.siteShowPhotos.value, onMasterChange)
                        else Switch(checked = AppConfig.siteShowPhotos.value, onCheckedChange = onMasterChange, colors = SwitchDefaults.colors(checkedTrackColor = switchColor))
                    }
                    
                    androidx.compose.animation.AnimatedVisibility(visible = AppConfig.siteShowPhotos.value) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            
                            // CellularFR
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(AppStrings.showCellularFrPhotosLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                if (useOneUi) Box(modifier = Modifier.scale(0.85f)) { fr.geotower.ui.components.OneUiSwitch(AppConfig.siteShowCellularFrPhotos.value) { saveBool("site_show_cellularfr_photos", AppConfig.siteShowCellularFrPhotos, it) } }
                                else Switch(checked = AppConfig.siteShowCellularFrPhotos.value, onCheckedChange = { saveBool("site_show_cellularfr_photos", AppConfig.siteShowCellularFrPhotos, it) }, modifier = Modifier.scale(0.8f), colors = SwitchDefaults.colors(checkedTrackColor = switchColor))
                            }
                            
                            // SignalQuest
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(AppStrings.showSignalQuestPhotosLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                if (useOneUi) Box(modifier = Modifier.scale(0.85f)) { fr.geotower.ui.components.OneUiSwitch(AppConfig.siteShowSignalQuestPhotos.value) { saveBool("site_show_signalquest_photos", AppConfig.siteShowSignalQuestPhotos, it) } }
                                else Switch(checked = AppConfig.siteShowSignalQuestPhotos.value, onCheckedChange = { saveBool("site_show_signalquest_photos", AppConfig.siteShowSignalQuestPhotos, it) }, modifier = Modifier.scale(0.8f), colors = SwitchDefaults.colors(checkedTrackColor = switchColor))
                            }
                            
                            // Schematics
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(AppStrings.showSchemesLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                if (useOneUi) Box(modifier = Modifier.scale(0.85f)) { fr.geotower.ui.components.OneUiSwitch(AppConfig.siteShowSchemes.value) { saveBool("site_show_schemes", AppConfig.siteShowSchemes, it) } }
                                else Switch(checked = AppConfig.siteShowSchemes.value, onCheckedChange = { saveBool("site_show_schemes", AppConfig.siteShowSchemes, it) }, modifier = Modifier.scale(0.8f), colors = SwitchDefaults.colors(checkedTrackColor = switchColor))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(
                onClick = {
                    saveBool("page_site_photos", AppConfig.siteShowPhotos, true)
                    saveBool("site_show_cellularfr_photos", AppConfig.siteShowCellularFrPhotos, true)
                    saveBool("site_show_signalquest_photos", AppConfig.siteShowSignalQuestPhotos, true)
                    saveBool("site_show_schemes", AppConfig.siteShowSchemes, true)
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(AppStrings.resetToDefault, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
        }
    }
}
