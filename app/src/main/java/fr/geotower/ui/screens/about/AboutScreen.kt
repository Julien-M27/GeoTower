@file:OptIn(ExperimentalMaterial3Api::class)
package fr.geotower.ui.screens.about

import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppIconManager
import fr.geotower.utils.AppLogoDrawingResources
import fr.geotower.utils.AppLogger
import fr.geotower.utils.AppStrings
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.NewReleases as FilledNewReleases
import android.database.sqlite.SQLiteDatabase
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.navigationBarsPadding
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import kotlin.math.roundToInt

private const val TAG_ABOUT = "GeoTower"

@Composable
fun AboutScreen(navController: NavController) {

    val safeClick = rememberSafeClick()
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "home")

    BackHandler(enabled = !safeBackNavigation.isLocked) {
        safeBackNavigation.navigateBack()
    }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val sectionBringIntoViewRequesters = remember { List(6) { BringIntoViewRequester() } }
    val sectionContentPositions = remember { mutableStateMapOf<Int, Float>() }
    val sectionContentHeights = remember { mutableStateMapOf<Int, Float>() }
    var scrollViewportTop by remember { mutableFloatStateOf(0f) }
    var scrollViewportHeight by remember { mutableFloatStateOf(0f) }

    // --- 1. LECTURE RÉACTIVE DU THÈME (Comme sur l'accueil) ---
    val themeMode by AppConfig.themeMode
    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)

    // --- 2. CONFIGURATION ONE UI DYNAMIQUE ---
    val useOneUi = AppConfig.useOneUiDesign
    val isOled by AppConfig.isOledMode // "by" permet de réagir au changement instantanément

    val cardShape = if (useOneUi) RoundedCornerShape(28.dp) else RoundedCornerShape(12.dp)

    // Couleur de fond dynamique pour l'écran (gère le mode OLED)
    val mainBgColor = if (isDark && isOled) Color.Black else MaterialTheme.colorScheme.background

    // Couleur des bulles/cartes selon le mode (One UI utilise un gris anthracite en sombre)
    val bubbleBaseColor = if (useOneUi) {
        if (isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) // Style classique
    }

    // --- LECTURE DU MODE DE NAVIGATION DEPUIS AppConfig ---
    val navMode = AppConfig.navMode.intValue

    val txtVersion = AppStrings.version
    val txtUnknown = AppStrings.unknown

    // --- RÉCUPÉRATION DE VERSION ---
    val appVersion = remember(txtVersion, txtUnknown) {
        try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
            "$txtVersion ${packageInfo.versionName}"
        } catch (e: Exception) {
            "$txtVersion $txtUnknown"
        }
    }

    val appTitle = "GeoTower"
    val logoResId by AppIconManager.currentIconRes
    val appLogoDrawingChoice by AppConfig.appLogoDrawingChoice
    val displayLogoResId = AppLogoDrawingResources.resolve(appLogoDrawingChoice, logoResId, isDark)
    val isWideScreen = configuration.screenWidthDp >= 600

    var activeSectionIndex by remember { mutableIntStateOf(0) }

    val menuItems = listOf(
        Triple(AppStrings.aboutPresentation, Icons.Outlined.Info, 0),
        Triple(AppStrings.aboutNew, Icons.Outlined.NewReleases, 1),
        Triple(AppStrings.privacyCategory, Icons.Outlined.Lock, 2),
        Triple(AppStrings.aboutSources, Icons.Outlined.Folder, 3),
        Triple(AppStrings.aboutVersionsTitle, Icons.Outlined.Storage, 4),
        Triple(AppStrings.aboutDev, Icons.Default.EditNote, 5)
    )

    suspend fun alignAnchorToViewportTop(anchorContentY: Float?) {
        if (anchorContentY == null || anchorContentY.isNaN() || scrollState.maxValue <= 0) return
        val target = anchorContentY.roundToInt().coerceIn(0, scrollState.maxValue)
        scrollState.animateScrollTo(target)
    }

    val sectionContentSnapshot = sectionContentPositions.toMap()
    val sectionHeightSnapshot = sectionContentHeights.toMap()
    val sectionAnchorModifiers = sectionBringIntoViewRequesters.mapIndexed { index, requester ->
        Modifier
            .bringIntoViewRequester(requester)
            .onGloballyPositioned { coordinates ->
                sectionContentPositions[index] =
                    coordinates.positionInRoot().y - scrollViewportTop + scrollState.value
                sectionContentHeights[index] = coordinates.size.height.toFloat()
            }
    }

    // --- LOGIQUE DE SYNCHRONISATION FLUIDE ---
    if (navMode == 0) {
        LaunchedEffect(scrollState.value, sectionContentSnapshot, sectionHeightSnapshot, scrollViewportHeight) {
            val sortedSections = sectionContentSnapshot
                .filterKeys { it in menuItems.indices }
                .toList()
                .sortedBy { it.second }

            if (sortedSections.size >= menuItems.size && sectionHeightSnapshot.size >= menuItems.size) {
                val viewportHeight = scrollViewportHeight.coerceAtLeast(1f)
                val viewportStart = scrollState.value.toFloat()
                val viewportEnd = viewportStart + viewportHeight
                val focusStart = viewportStart + viewportHeight * 0.18f
                val focusEnd = viewportStart + viewportHeight * 0.82f
                val versionsIndex = menuItems[4].third
                val developmentIndex = menuItems.last().third

                fun isSectionFullyVisible(index: Int): Boolean {
                    val sectionStart = sectionContentSnapshot[index]
                    val sectionEnd = sectionStart?.plus(sectionHeightSnapshot[index] ?: 0f)
                    return sectionStart != null &&
                        sectionEnd != null &&
                        sectionStart >= viewportStart - 1f &&
                        sectionEnd <= viewportEnd + 1f
                }

                val isVersionsFullyVisible = isSectionFullyVisible(versionsIndex)
                val isDevelopmentFullyVisible = isSectionFullyVisible(developmentIndex)

                val nextSection = if (scrollState.value <= 2) {
                    sortedSections.first().first
                } else if (isVersionsFullyVisible && (activeSectionIndex < versionsIndex || !isDevelopmentFullyVisible)) {
                    versionsIndex
                } else if (isDevelopmentFullyVisible) {
                    developmentIndex
                } else {
                    sortedSections
                        .mapIndexed { sectionIndex, section ->
                            val sectionStart = section.second
                            val measuredEnd = sectionStart + (sectionHeightSnapshot[section.first] ?: 0f)
                            val nextStart = sortedSections.getOrNull(sectionIndex + 1)?.second
                            val sectionEnd = maxOf(measuredEnd, nextStart ?: measuredEnd)
                            val focusOverlap = (minOf(sectionEnd, focusEnd) - maxOf(sectionStart, focusStart))
                                .coerceAtLeast(0f)
                            val visibleOverlap = (minOf(sectionEnd, viewportEnd) - maxOf(sectionStart, viewportStart))
                                .coerceAtLeast(0f)
                            val titleBonus = if (sectionStart in viewportStart..viewportEnd) viewportHeight * 0.18f else 0f
                            val score = focusOverlap * 2f + visibleOverlap * 0.5f + titleBonus
                            section.first to score
                        }
                        .maxByOrNull { it.second }
                        ?.first
                        ?: activeSectionIndex
                }

                if (activeSectionIndex != nextSection) {
                    activeSectionIndex = nextSection
                }
            } else {
                sectionContentSnapshot
                    .filterKeys { it in menuItems.indices }
                    .minByOrNull { it.value }
                    ?.key
                    ?.let { activeSectionIndex = it }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (logoResId == 0) AppIconManager.getLogoResId(context)
    }

    Scaffold(
        containerColor = mainBgColor, // Utilisation de la couleur de fond dynamique
        topBar = {
            if (!isWideScreen) {
                AboutTopBar { safeBackNavigation.navigateBack() }
            }
        }
    ) { innerPadding ->
        // 🚀 NOUVEL AFFICHAGE QUI UTILISE LE COMPOSANT COMMUN
        fr.geotower.ui.components.ResponsiveDualPaneLayout(
            // 🚨 CORRECTION 1 : Seulement le topPadding pour passer sous la barre
            modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
            // ✅ AJOUT : onCloseSidebar
            sidebar = { width, onCloseSidebar ->
                Row(modifier = Modifier.width(width).fillMaxHeight().background(mainBgColor)) {
                    Column(
                        // 🚨 CORRECTION 2 : Ajout du navigationBarsPadding
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .navigationBarsPadding()
                            .padding(top = 16.dp, bottom = 24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { safeBackNavigation.navigateBack() },
                                enabled = !safeBackNavigation.isLocked,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.back, tint = MaterialTheme.colorScheme.onSurface)
                            }
                            // ✅ RETOUR DU BOUTON MENU
                            IconButton(onClick = onCloseSidebar, modifier = Modifier.padding(end = 8.dp)) {
                                Icon(Icons.Default.Menu, contentDescription = AppStrings.closeMenu, tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        menuItems.forEach { (title, icon, index) ->
                            AboutNavigationMenuItem(
                                title = title,
                                icon = icon,
                                isSelected = activeSectionIndex == index,
                                isDark = isDark,
                                useOneUi = useOneUi,
                                onClick = {
                                    activeSectionIndex = index
                                    if (navMode == 0) {
                                        scope.launch {
                                            sectionBringIntoViewRequesters[index].bringIntoView()
                                            kotlinx.coroutines.delay(80)
                                            alignAnchorToViewportTop(sectionContentPositions[index])
                                        }
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        AboutNavigationMenuItem(
                            title = AppStrings.settingsTitle,
                            icon = Icons.Outlined.Settings,
                            isSelected = false,
                            isDark = isDark,
                            useOneUi = useOneUi,
                            onClick = {
                                safeClick {
                                    try {
                                        val currentDestinationId = navController.currentDestination?.id
                                        navController.navigate("settings") {
                                            launchSingleTop = true
                                            if (currentDestinationId != null) {
                                                popUpTo(currentDestinationId) {
                                                    inclusive = true
                                                }
                                            }
                                        }
                                    } catch (e: IllegalArgumentException) {
                                        AppLogger.w(TAG_ABOUT, "Settings navigation failed", e)
                                        safeBackNavigation.navigateBack()
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        Text(
                            text = appVersion,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            },
            content = { isExpanded, isSidebarVisible, onToggleSidebar ->
                Column(modifier = Modifier.fillMaxSize().background(mainBgColor)) {

                    // --- EN-TÊTE TABLETTE ---
                    if (isExpanded) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(
                                visible = !isSidebarVisible,
                                enter = fadeIn(animationSpec = tween(300)) + expandHorizontally(),
                                exit = fadeOut(animationSpec = tween(200)) + shrinkHorizontally()
                            ) {
                                IconButton(
                                    onClick = onToggleSidebar,
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = AppStrings.openMenu, tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }

                            Text(
                                text = AppStrings.about,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )

                            AnimatedVisibility(
                                visible = !isSidebarVisible,
                                enter = fadeIn(animationSpec = tween(300)) + expandHorizontally(),
                                exit = fadeOut(animationSpec = tween(200)) + shrinkHorizontally()
                            ) {
                                Spacer(modifier = Modifier.width(56.dp))
                            }
                        }
                    }

                    // --- CONTENU DE LA PAGE ---
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coordinates ->
                                scrollViewportTop = coordinates.positionInRoot().y
                                scrollViewportHeight = coordinates.size.height.toFloat()
                            }
                            .then(if (navMode == 0 || !isExpanded) Modifier.geoTowerFadingEdge(scrollState) else Modifier)
                            .then(if (navMode == 0 || !isExpanded) Modifier.verticalScroll(scrollState) else Modifier)
                            .padding(horizontal = if (isExpanded) 48.dp else 24.dp)
                            // 🚨 CORRECTION 3 : Ajout de la marge de sécurité
                            .navigationBarsPadding()
                    ) {
                        if (navMode == 0 || !isExpanded) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AllAboutContent(
                                    appTitle,
                                    appVersion,
                                    displayLogoResId,
                                    cardShape,
                                    bubbleBaseColor,
                                    sectionAnchorModifiers[0],
                                    sectionAnchorModifiers[1],
                                    sectionAnchorModifiers[2],
                                    sectionAnchorModifiers[3],
                                    sectionAnchorModifiers[4],
                                    sectionAnchorModifiers[5]
                                )
                                Spacer(modifier = Modifier.height(60.dp))
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                when(activeSectionIndex) {
                                    0 -> SectionPresentation(appTitle, appVersion, displayLogoResId)
                                    1 -> SectionNouveautes(appVersion, cardShape, bubbleBaseColor)
                                    2 -> SectionConfidentialite(cardShape, bubbleBaseColor)
                                    3 -> SectionSources(cardShape, bubbleBaseColor)
                                    4 -> SectionVersions(cardShape, bubbleBaseColor)
                                    5 -> SectionDeveloppement()
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

// ============================================================
// COMPOSANT MENU DE NAVIGATION (FOLD)
// ============================================================

@Composable
fun AboutNavigationMenuItem(title: String, icon: ImageVector, isSelected: Boolean, isDark: Boolean, useOneUi: Boolean, onClick: () -> Unit) {
    // Si One UI est activé, on utilise la couleur primaire en fond comme pour les switchs
    val bgColor = if (isSelected) {
        if (useOneUi) MaterialTheme.colorScheme.primaryContainer else {
            if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)
        }
    } else Color.Transparent

    val contentColor = if (isSelected) {
        if (useOneUi) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
    } else {
        if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f)
    }

    val shape = if (useOneUi) RoundedCornerShape(16.dp) else RoundedCornerShape(12.dp)

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = shape,
        color = bgColor
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = contentColor)
        }
    }
}

@Composable
fun AboutTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.Transparent).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, AppStrings.back) }
        Text(AppStrings.about, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Spacer(Modifier.width(48.dp))
    }
}

// ============================================================
// CONTENU DES SECTIONS À PROPOS
// ============================================================

@Composable
fun AllAboutContent(
    appTitle: String,
    appVersion: String,
    logoResId: Int,
    cardShape: Shape,
    bubbleColor: Color,
    presentationModifier: Modifier = Modifier,
    newsModifier: Modifier = Modifier,
    privacyModifier: Modifier = Modifier,
    sourcesModifier: Modifier = Modifier,
    versionsModifier: Modifier = Modifier,
    developmentModifier: Modifier = Modifier
) {
    Column(
        modifier = presentationModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SectionPresentation(appTitle, appVersion, logoResId)
    }
    Spacer(modifier = Modifier.height(48.dp))
    Column(
        modifier = newsModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SectionNouveautes(appVersion, cardShape, bubbleColor)
    }
    Spacer(modifier = Modifier.height(48.dp))

    // --- NOUVEAU ---
    Column(
        modifier = privacyModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SectionConfidentialite(cardShape, bubbleColor)
    }
    Spacer(modifier = Modifier.height(48.dp))
    // ---------------

    Column(
        modifier = sourcesModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SectionSources(cardShape, bubbleColor)
    }
    Spacer(modifier = Modifier.height(48.dp))
    Column(
        modifier = versionsModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SectionVersions(cardShape, bubbleColor)
    }
    Spacer(modifier = Modifier.height(16.dp))
    Column(
        modifier = developmentModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SectionDeveloppement()
    }
}

@Composable
fun SectionPresentation(appTitle: String, appVersion: String, logoResId: Int) {
    AboutDrawableImage(resId = logoResId, modifier = Modifier.size(150.dp).padding(top = 16.dp))
    Spacer(modifier = Modifier.height(16.dp))
    Text(appTitle, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Text(appVersion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(32.dp))
    Text(
        text = AppStrings.aboutIntro,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
fun SectionNouveautes(appVersion: String, cardShape: Shape, bubbleColor: Color) {
    val releaseNotes = currentReleaseNotes()

    SectionTitle(AppStrings.aboutNewForVersion(appVersion))
    Card(
        colors = CardDefaults.cardColors(containerColor = if (bubbleColor == Color.Transparent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else bubbleColor),
        shape = cardShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FilledNewReleases, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(AppStrings.latestChanges, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))

            releaseNotes.sections.forEach { section ->
                Text(section.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                section.entries.forEach { entry ->
                    when (entry) {
                        is ReleaseNoteGroup -> {
                            Text(entry.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 2.dp))
                            entry.items.forEach { item ->
                                Text("• $item", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 20.dp, top = 1.dp, bottom = 1.dp))
                            }
                        }
                        is ReleaseNoteItem -> Text("• ${entry.text}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

            }
        }
    }
}

@Composable
fun SectionSources(cardShape: Shape, bubbleColor: Color) {
    SectionTitle(AppStrings.aboutSources)
    val cardColor = if (bubbleColor == Color.Transparent) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else bubbleColor

    // ✅ On initialise le gestionnaire de liens
    val uriHandler = LocalUriHandler.current

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = cardShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 1. ANFR (Données)
            CreditItem(AppStrings.srcAntennas, AppStrings.srcAntennasDesc)

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // 2. IGN (Cliquable)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri("https://geoservices.ign.fr/") }
            ) {
                CreditItem(AppStrings.srcIgn, AppStrings.srcIgnDesc)
                Text("geoservices.ign.fr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // 3. OpenStreetMap (Cliquable)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri("https://www.openstreetmap.org/copyright") }
            ) {
                CreditItem(AppStrings.srcOsm, AppStrings.srcOsmDesc)
                Text("www.openstreetmap.org", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // 4. MapsForge (Cliquable)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri("https://mapsforge.org") }
            ) {
                CreditItem(AppStrings.mapsForgesTitle, AppStrings.mapsForgesDesc)
                Text("www.mapsforge.org", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // 5. Inspiration
            CreditItem(AppStrings.srcInspo, AppStrings.srcInspoDesc)
        }
    }
}

@Composable
fun SectionDeveloppement() {
    SectionTitle(AppStrings.aboutDev) // <-- MODIFIÉ
    ListItem(
        headlineContent = { Text(AppStrings.devCredit) }, // <-- MODIFIÉ
        leadingContent = { Icon(imageVector = Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

// ============================================================
// UTILITAIRES
// ============================================================

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
}

@Composable
private fun CreditItem(title: String, description: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AboutDrawableImage(resId: Int, modifier: Modifier = Modifier, contentDescription: String? = null) {
    AndroidView<ImageView>(
        modifier = modifier,
        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { imageView ->
            if (resId != 0) imageView.setImageResource(resId)
            imageView.contentDescription = contentDescription
        }
    )
}

@Composable
fun SectionVersions(cardShape: Shape, bubbleColor: Color) {
    val context = LocalContext.current
    var appVersion by remember { mutableStateOf("-") }
    var dbVersion by remember { mutableStateOf("-") }
    var anfrDate by remember { mutableStateOf("-") }
    var rawMonthlyVersion by remember { mutableStateOf("-") } // ✅ Changé en "rawMonthlyVersion"
    var hsDate by remember { mutableStateOf("-") }
    val txtDownloadNewBase = AppStrings.aboutDownloadNewDatabase
    val txtInvalidLocalDatabase = AppStrings.invalidLocalDatabase
    val txtNotInstalled = AppStrings.aboutDatabaseNotInstalled
    val txtVersionTimeAt = AppStrings.versionTimeAt

    LaunchedEffect(txtDownloadNewBase, txtInvalidLocalDatabase, txtNotInstalled, txtVersionTimeAt) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // 1. Version de l'application
            try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                appVersion = pInfo.versionName ?: "-"
            } catch (e: Exception) {}

            // 2. Base de données locale (Extraction Directe)
            try {
                val dbPath = context.getDatabasePath(fr.geotower.data.db.GeoTowerDatabaseValidator.DB_NAME)
                val localStatus = fr.geotower.data.db.GeoTowerDatabaseValidator.getInstalledDatabaseFileStatus(context)
                if (localStatus.state == fr.geotower.data.db.GeoTowerDatabaseValidator.LocalDatabaseState.VALID) {
                    val db = android.database.sqlite.SQLiteDatabase.openDatabase(dbPath.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)

                    // 🚨 Requêtes en "cascade" pour ne pas planter avec l'ancienne BDD
                    val cursor = try {
                        db.rawQuery("SELECT version, date_maj_anfr, zip_version FROM metadata LIMIT 1", null)
                    } catch (e: Exception) {
                        try {
                            db.rawQuery("SELECT version, date_maj_anfr FROM metadata LIMIT 1", null)
                        } catch (e2: Exception) {
                            db.rawQuery("SELECT version FROM metadata LIMIT 1", null)
                        }
                    }

                    if (cursor.moveToFirst()) {
                        // A. Version interne
                        val rawVersion = cursor.getString(0)
                        if (rawVersion != null && rawVersion.length == 13) {
                            val dbDate = "${rawVersion.substring(6, 8)}/${rawVersion.substring(4, 6)}/${rawVersion.substring(0, 4)}"
                            val dbTime = "${rawVersion.substring(9, 11)}:${rawVersion.substring(11, 13)}"
                            dbVersion = "$dbDate\n$txtVersionTimeAt $dbTime"
                        } else {
                            dbVersion = rawVersion ?: "-"
                        }

                        // B. Date Hebdo (ANFR)
                        if (cursor.columnCount > 1 && !cursor.isNull(1)) {
                            val rawAnfr = cursor.getString(1)
                            anfrDate = try {
                                if (rawAnfr.contains("T")) {
                                    val datePart = rawAnfr.substringBefore("T")
                                    val dParts = datePart.split("-")
                                    if (dParts.size >= 3) "${dParts[2]}/${dParts[1]}/${dParts[0]}" else rawAnfr
                                } else {
                                    when (rawAnfr.length) {
                                        13, 8 -> "${rawAnfr.substring(6, 8)}/${rawAnfr.substring(4, 6)}/${rawAnfr.substring(0, 4)}"
                                        else -> rawAnfr
                                    }
                                }
                            } catch (e: Exception) { rawAnfr }
                        }

                        // C. ✅ Version Mensuelle Brute
                        if (cursor.columnCount > 2 && !cursor.isNull(2)) {
                            rawMonthlyVersion = cursor.getString(2) ?: "-"
                        } else {
                            rawMonthlyVersion = txtDownloadNewBase
                        }
                    }
                    cursor.close()
                    db.close()
                } else {
                    dbVersion = if (localStatus.state == fr.geotower.data.db.GeoTowerDatabaseValidator.LocalDatabaseState.INVALID) {
                        txtInvalidLocalDatabase
                    } else {
                        txtNotInstalled
                    }
                    anfrDate = "-"
                    rawMonthlyVersion = "-"
                }
            } catch (e: Exception) {
                AppLogger.w(TAG_ABOUT, "Database version info could not be read", e)
            }

            // 3. Données des sites HS (depuis les préférences)
            val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            hsDate = prefs.getString("last_hs_update", "-") ?: "-"
        }
    }

    SectionTitle(AppStrings.aboutVersionsTitle)
    val cardColor = if (bubbleColor == Color.Transparent) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else bubbleColor

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = cardShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            val versionRows = listOf(
                AppStrings.versionAppLabel to appVersion,
                AppStrings.versionDbLabel to dbVersion,
                AppStrings.versionWeeklyLabel to AppStrings.formatWeeklyVersionWithWeekNumber(anfrDate),
                AppStrings.versionMonthlyLabel to AppStrings.formatMonthlyVersion(rawMonthlyVersion),
                AppStrings.versionHsLabel to hsDate
            )

            versionRows.forEachIndexed { index, row ->
                VersionLine(row.first, row.second)
                if (index < versionRows.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(0.52f)
                .padding(end = 12.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.48f)
        )
    }
}

@Composable
fun SectionConfidentialite(cardShape: Shape, bubbleColor: Color) {
    SectionTitle(AppStrings.privacyCategory)
    val cardColor = if (bubbleColor == Color.Transparent) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else bubbleColor

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = cardShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(AppStrings.yourDataTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(AppStrings.yourDataDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
