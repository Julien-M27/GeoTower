package fr.geotower.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
        Column(modifier = Modifier.padding(bottom = 48.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(AppStrings.pageHomeSettings, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
            Text(AppStrings.dragToReorderHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp), textAlign = TextAlign.Center)

            val density = LocalDensity.current
            val cardHeight = 64.dp
            val spacing = 12.dp
            val stepPx = with(density) { (cardHeight + spacing).toPx() }

            var draggedItem by remember { mutableStateOf<String?>(null) }
            var dragOffset by remember { mutableFloatStateOf(0f) }

            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                val shape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)
                val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null

                // --- MODIFICATION : On utilise 'currentOrder' au lieu de 'pagesOrder' partout ici ---
                currentOrder.forEach { pageId ->
                    key(pageId) {
                        val isDragged = draggedItem == pageId
                        val dragModifier = Modifier.pointerInput(pageId) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggedItem = pageId; dragOffset = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.y

                                    val currentIndex = currentOrder.indexOf(pageId) // Utilise currentOrder
                                    if (currentIndex < 0) return@detectDragGesturesAfterLongPress

                                    var newIndex = currentIndex
                                    while (dragOffset > stepPx * 0.5f && newIndex < currentOrder.size - 1) { // Utilise currentOrder
                                        dragOffset -= stepPx
                                        newIndex++
                                    }
                                    while (dragOffset < -stepPx * 0.5f && newIndex > 0) {
                                        dragOffset += stepPx
                                        newIndex--
                                    }

                                    if (newIndex != currentIndex) {
                                        val newList = currentOrder.toMutableList() // Utilise currentOrder
                                        val item = newList.removeAt(currentIndex)
                                        newList.add(newIndex, item)
                                        onOrderChange(newList)
                                    }
                                },
                                onDragEnd = { draggedItem = null; dragOffset = 0f },
                                onDragCancel = { draggedItem = null; dragOffset = 0f }
                            )
                        }

                        when (pageId) {
                            // ---> 3. AJOUT DU BLOC LOGO <---
                            "logo" -> DraggableSwitchCard(
                                AppStrings.pageHomeLogoSettings, showLogo,
                                { showLogo = it; prefs.edit().putBoolean("show_home_logo", it).apply() },
                                shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight
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
                prefs.edit().putBoolean("show_home_logo", true).apply()
            }) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(AppStrings.resetToDefault, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
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
                        contentDescription = "Paramètres",
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
// === LE SOUS-MENU POUR LA PAGE ANTENNES À PROXIMITÉ ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbySettingsSheet(
    nearbyOrder: List<String>, onOrderChange: (List<String>) -> Unit,
    showSearch: Boolean, onSearchChange: (Boolean) -> Unit,
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
        Column(modifier = Modifier.padding(bottom = 48.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(AppStrings.pageNearbySettings, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
            Text(AppStrings.dragToReorderHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp), textAlign = TextAlign.Center)

            val density = LocalDensity.current
            val cardHeight = 64.dp
            val spacing = 12.dp
            val stepPx = with(density) { (cardHeight + spacing).toPx() }

            var draggedItem by remember { mutableStateOf<String?>(null) }
            var dragOffset by remember { mutableFloatStateOf(0f) }

            val shape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)
            val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null

            // --- 1. LES BLOCS GLISSER/DÉPOSER ---
            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                currentOrder.forEach { blockId ->
                    key(blockId) {
                        val isDragged = draggedItem == blockId
                        val dragModifier = Modifier.pointerInput(blockId) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggedItem = blockId; dragOffset = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.y
                                    val currentIndex = currentOrder.indexOf(blockId)
                                    if (currentIndex < 0) return@detectDragGesturesAfterLongPress
                                    var newIndex = currentIndex
                                    while (dragOffset > stepPx * 0.5f && newIndex < currentOrder.size - 1) { dragOffset -= stepPx; newIndex++ }
                                    while (dragOffset < -stepPx * 0.5f && newIndex > 0) { dragOffset += stepPx; newIndex-- }
                                    if (newIndex != currentIndex) {
                                        val newList = currentOrder.toMutableList()
                                        val item = newList.removeAt(currentIndex)
                                        newList.add(newIndex, item)
                                        onOrderChange(newList)
                                    }
                                },
                                onDragEnd = { draggedItem = null; dragOffset = 0f },
                                onDragCancel = { draggedItem = null; dragOffset = 0f }
                            )
                        }

                        when (blockId) {
                            "search" -> DraggableSwitchCard(AppStrings.nearbySearchOption, showSearch, onSearchChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "sites" -> DraggableSwitchCard(AppStrings.nearbySitesOption, showSites, onSitesChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            // --- NOUVEAU : BOUTON RÉINITIALISER L'ORDRE ---
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(
                onClick = {
                    onOrderChange(listOf("search", "sites"))
                    onSearchChange(true)
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

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
        Column(modifier = Modifier.padding(bottom = 48.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(AppStrings.pageCompassSettings, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
            Text(AppStrings.dragToReorderHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp), textAlign = TextAlign.Center)

            val density = LocalDensity.current
            val cardHeight = 64.dp
            val spacing = 12.dp
            val stepPx = with(density) { (cardHeight + spacing).toPx() }

            var draggedItem by remember { mutableStateOf<String?>(null) }
            var dragOffset by remember { mutableFloatStateOf(0f) }

            val shape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)
            val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null

            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                currentOrder.forEach { blockId ->
                    key(blockId) {
                        val isDragged = draggedItem == blockId
                        val dragModifier = Modifier.pointerInput(blockId) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggedItem = blockId; dragOffset = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.y
                                    val currentIndex = currentOrder.indexOf(blockId)
                                    if (currentIndex < 0) return@detectDragGesturesAfterLongPress
                                    var newIndex = currentIndex
                                    while (dragOffset > stepPx * 0.5f && newIndex < currentOrder.size - 1) { dragOffset -= stepPx; newIndex++ }
                                    while (dragOffset < -stepPx * 0.5f && newIndex > 0) { dragOffset += stepPx; newIndex-- }
                                    if (newIndex != currentIndex) {
                                        val newList = currentOrder.toMutableList()
                                        val item = newList.removeAt(currentIndex)
                                        newList.add(newIndex, item)
                                        onOrderChange(newList)
                                    }
                                },
                                onDragEnd = { draggedItem = null; dragOffset = 0f },
                                onDragCancel = { draggedItem = null; dragOffset = 0f }
                            )
                        }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportSettingsSheet(
    supportOrder: List<String>, onOrderChange: (List<String>) -> Unit,
    showMap: Boolean, onMapChange: (Boolean) -> Unit,
    showDetails: Boolean, onDetailsChange: (Boolean) -> Unit,
    showPhotos: Boolean, onPhotosChange: (Boolean) -> Unit,
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
        Column(modifier = Modifier.padding(bottom = 48.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(AppStrings.pageSupportSettings, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
            Text(AppStrings.dragToReorderHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp), textAlign = TextAlign.Center)

            val density = LocalDensity.current
            val cardHeight = 64.dp
            val spacing = 12.dp
            val stepPx = with(density) { (cardHeight + spacing).toPx() }

            var draggedItem by remember { mutableStateOf<String?>(null) }
            var dragOffset by remember { mutableFloatStateOf(0f) }

            val shape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)
            val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null

            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                currentOrder.forEach { blockId ->
                    key(blockId) {
                        val isDragged = draggedItem == blockId
                        val dragModifier = Modifier.pointerInput(blockId) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggedItem = blockId; dragOffset = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.y
                                    val currentIndex = currentOrder.indexOf(blockId)
                                    if (currentIndex < 0) return@detectDragGesturesAfterLongPress
                                    var newIndex = currentIndex
                                    while (dragOffset > stepPx * 0.5f && newIndex < currentOrder.size - 1) { dragOffset -= stepPx; newIndex++ }
                                    while (dragOffset < -stepPx * 0.5f && newIndex > 0) { dragOffset += stepPx; newIndex-- }
                                    if (newIndex != currentIndex) {
                                        val newList = currentOrder.toMutableList()
                                        val item = newList.removeAt(currentIndex)
                                        newList.add(newIndex, item)
                                        onOrderChange(newList)
                                    }
                                },
                                onDragEnd = { draggedItem = null; dragOffset = 0f },
                                onDragCancel = { draggedItem = null; dragOffset = 0f }
                            )
                        }

                        when (blockId) {
                            "map" -> DraggableSwitchCard(AppStrings.supportMapOption, showMap, onMapChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "details" -> DraggableSwitchCard(AppStrings.supportDetailsOption, showDetails, onDetailsChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "photos" -> DraggableSwitchCard(AppStrings.supportPhotosOption, showPhotos, onPhotosChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "nav" -> DraggableSwitchCard(AppStrings.supportNavOption, showNav, onNavChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "share" -> DraggableSwitchCard(AppStrings.supportShareOption, showShare, onShareChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "operators" -> DraggableSwitchCard(AppStrings.supportOperatorsOption, showOperators, onOperatorsChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = {
                onOrderChange(listOf("map", "details", "photos", "nav", "share", "operators"))
                onMapChange(true)
                onDetailsChange(true)
                onPhotosChange(true)
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

            val density = LocalDensity.current
            val cardHeight = 64.dp
            val spacing = 12.dp
            val stepPx = with(density) { (cardHeight + spacing).toPx() }

            var draggedItem by remember { mutableStateOf<String?>(null) }
            var dragOffset by remember { mutableFloatStateOf(0f) }

            val shape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)
            val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null

            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                currentOrder.forEach { blockId ->
                    key(blockId) {
                        val isDragged = draggedItem == blockId
                        val dragModifier = Modifier.pointerInput(blockId) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggedItem = blockId; dragOffset = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.y
                                    val currentIndex = currentOrder.indexOf(blockId)
                                    if (currentIndex < 0) return@detectDragGesturesAfterLongPress
                                    var newIndex = currentIndex
                                    while (dragOffset > stepPx * 0.5f && newIndex < currentOrder.size - 1) { dragOffset -= stepPx; newIndex++ }
                                    while (dragOffset < -stepPx * 0.5f && newIndex > 0) { dragOffset += stepPx; newIndex-- }
                                    if (newIndex != currentIndex) {
                                        val newList = currentOrder.toMutableList()
                                        val item = newList.removeAt(currentIndex)
                                        newList.add(newIndex, item)
                                        onOrderChange(newList)
                                    }
                                },
                                onDragEnd = { draggedItem = null; dragOffset = 0f },
                                onDragCancel = { draggedItem = null; dragOffset = 0f }
                            )
                        }

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
                onOrderChange(listOf("operator", "bearing_height", "map", "support_details", "photos", "speedtest", "ids", "nav", "share", "dates", "address", "status", "freqs", "links"))
                onOperatorChange(true)
                onBearingHeightChange(true)
                onMapChange(true)
                onSupportDetailsChange(true)
                onPhotosChange(true)
                onSpeedtestChange(true)
                onIdsChange(true)
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
    val density = LocalDensity.current
    val useOneUi = AppConfig.useOneUiDesign

    var draggedTechno by remember { mutableStateOf<String?>(null) }
    var technoDragOffset by remember { mutableFloatStateOf(0f) }
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
                    val isTechnoDragged = draggedTechno == technoId
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
                            .pointerInput(technoId) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggedTechno = technoId; technoDragOffset = 0f },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        technoDragOffset += dragAmount.y
                                        val currentList = AppConfig.siteTechnoOrder.value
                                        val currentIndex = currentList.indexOf(technoId)
                                        val stepPx = with(density) { 80.dp.toPx() }
                                        var newIndex = currentIndex
                                        while (technoDragOffset > stepPx * 0.5f && newIndex < currentList.size - 1) { technoDragOffset -= stepPx; newIndex++ }
                                        while (technoDragOffset < -stepPx * 0.5f && newIndex > 0) { technoDragOffset += stepPx; newIndex-- }
                                        if (newIndex != currentIndex) {
                                            val newList = currentList.toMutableList()
                                            val item = newList.removeAt(currentIndex)
                                            newList.add(newIndex, item)
                                            AppConfig.siteTechnoOrder.value = newList
                                            prefs.edit().putString("site_techno_order", newList.joinToString(",")).apply()
                                        }
                                    },
                                    onDragEnd = { draggedTechno = null; technoDragOffset = 0f },
                                    onDragCancel = { draggedTechno = null; technoDragOffset = 0f }
                                )
                            },
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
                                    var draggedFreq by remember { mutableStateOf<String?>(null) }
                                    var freqDragOffset by remember { mutableFloatStateOf(0f) }
                                    freqOrder.value.forEach { freqLabel ->
                                        key(freqLabel) {
                                            val freqStatePair = freqList.find { it.first == freqLabel }
                                            if (freqStatePair != null) {
                                                val freqState = freqStatePair.second
                                                val isFreqDragged = draggedFreq == freqLabel
                                                val freqPrefKey = "site_f${technoId.lowercase()}_${freqLabel}"
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth().zIndex(if (isFreqDragged) 1f else 0f)
                                                        .graphicsLayer { translationY = if (isFreqDragged) freqDragOffset else 0f; scaleX = if (isFreqDragged) 1.02f else 1f; scaleY = if (isFreqDragged) 1.02f else 1f }
                                                        .background(if (isFreqDragged) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(8.dp))
                                                        .pointerInput(freqLabel) {
                                                            detectDragGesturesAfterLongPress(
                                                                onDragStart = { draggedFreq = freqLabel; freqDragOffset = 0f },
                                                                onDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    freqDragOffset += dragAmount.y
                                                                    val currentList = freqOrder.value
                                                                    val currentIndex = currentList.indexOf(freqLabel)
                                                                    val stepPx = with(density) { 48.dp.toPx() }
                                                                    var newIndex = currentIndex
                                                                    while (freqDragOffset > stepPx * 0.5f && newIndex < currentList.size - 1) { freqDragOffset -= stepPx; newIndex++ }
                                                                    while (freqDragOffset < -stepPx * 0.5f && newIndex > 0) { freqDragOffset += stepPx; newIndex-- }
                                                                    if (newIndex != currentIndex) {
                                                                        val newList = currentList.toMutableList()
                                                                        val item = newList.removeAt(currentIndex)
                                                                        newList.add(newIndex, item)
                                                                        freqOrder.value = newList
                                                                        prefs.edit().putString("site_freq_${technoId.lowercase()}_order", newList.joinToString(",")).apply()
                                                                    }
                                                                },
                                                                onDragEnd = { draggedFreq = null; freqDragOffset = 0f },
                                                                onDragCancel = { draggedFreq = null; freqDragOffset = 0f }
                                                            )
                                                        }.padding(vertical = 4.dp, horizontal = 4.dp)
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
