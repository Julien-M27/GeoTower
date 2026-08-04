@file:OptIn(ExperimentalMaterial3Api::class)
package fr.geotower.ui.screens.about

import android.content.Context
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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import fr.geotower.data.outages.OutageLocalConfig
import fr.geotower.data.outages.OutageServerInfo
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AboutPagePrefs
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppIconManager
import fr.geotower.utils.AppLogoDrawingResources
import fr.geotower.utils.AppLogger
import fr.geotower.utils.LocalizedDateLabels
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.NewReleases as FilledNewReleases
import android.database.sqlite.SQLiteDatabase
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import fr.geotower.ui.components.PageScrollEdgeButtons
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.components.pageScrollbar
import fr.geotower.ui.components.rememberLocalDbBuildStatus
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.utils.PageScrollPrefs
import fr.geotower.utils.PreferenceStores
import fr.geotower.ui.navigation.ROOT_FALLBACK_ROUTE
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import fr.geotower.R

private const val TAG_ABOUT = "GeoTower"

// Les parties de la page sont désignées par leur indice dans AboutPagePrefs.sections : c'est aussi
// celui de leur ancre de défilement et de leur entrée de menu. Deux d'entre elles sont nommées, le
// suivi du défilement les traitant à part (voir AboutScreen).
private const val VERSIONS_SECTION = 4
private const val DEVELOPMENT_SECTION = 5

@Composable
fun AboutScreen(navController: NavController) {

    val safeClick = rememberSafeClick()
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = ROOT_FALLBACK_ROUTE)

    BackHandler(enabled = !safeBackNavigation.isLocked) {
        safeBackNavigation.navigateBack()
    }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val sectionBringIntoViewRequesters = remember { List(AboutPagePrefs.sections.size) { BringIntoViewRequester() } }
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
    val sizing = LocalGeoTowerUiStyle.current.sizing

    val txtVersion = stringResource(R.string.appstrings_version)
    val txtUnknown = stringResource(R.string.appstrings_unknown)

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
    val isWideScreen = minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 600

    // Parties retirées depuis « Personnalisation des pages » : elles quittent la page ET son menu
    // latéral, sinon on proposerait d'aller à une section qui n'existe plus.
    val prefs = remember(context) { context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE) }
    val visibleSections = remember(prefs) { AboutPagePrefs.visibleSections(prefs) }

    var activeSectionIndex by remember { mutableIntStateOf(visibleSections.firstOrNull() ?: 0) }

    val menuItems = listOf(
        Triple(stringResource(R.string.appstrings_about_presentation), Icons.Outlined.Info, 0),
        Triple(stringResource(R.string.appstrings_about_new), Icons.Outlined.NewReleases, 1),
        Triple(stringResource(R.string.appstrings_privacy_category), Icons.Outlined.Lock, 2),
        Triple(stringResource(R.string.appstrings_about_sources), Icons.Outlined.Folder, 3),
        Triple(stringResource(R.string.appstrings_about_versions_title), Icons.Outlined.Storage, 4),
        Triple(stringResource(R.string.appstrings_about_dev), Icons.Default.EditNote, 5)
    ).filter { it.third in visibleSections }

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

    // La section active doit rester atteignable : masquer celle qui l'était renvoie sur la première
    // partie encore affichée.
    LaunchedEffect(visibleSections) {
        if (activeSectionIndex !in visibleSections) {
            activeSectionIndex = visibleSections.firstOrNull() ?: 0
        }
    }

    // --- LOGIQUE DE SYNCHRONISATION FLUIDE ---
    if (navMode == 0) {
        LaunchedEffect(scrollState.value, sectionContentSnapshot, sectionHeightSnapshot, scrollViewportHeight, visibleSections) {
            if (visibleSections.isEmpty()) return@LaunchedEffect
            val sortedSections = sectionContentSnapshot
                .filterKeys { it in visibleSections }
                .toList()
                .sortedBy { it.second }

            if (sortedSections.size >= menuItems.size && sectionHeightSnapshot.size >= menuItems.size) {
                val viewportHeight = scrollViewportHeight.coerceAtLeast(1f)
                val viewportStart = scrollState.value.toFloat()
                val viewportEnd = viewportStart + viewportHeight
                val focusStart = viewportStart + viewportHeight * 0.18f
                val focusEnd = viewportStart + viewportHeight * 0.82f
                // -1 quand la partie est masquée : aucune position ne porte cet indice, les deux
                // cas particuliers ci-dessous s'éteignent d'eux-mêmes.
                val versionsIndex = if (VERSIONS_SECTION in visibleSections) VERSIONS_SECTION else -1
                val developmentIndex = visibleSections.last()

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
                    .filterKeys { it in visibleSections }
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
        // Même raison que dans les réglages : la route enveloppe déjà l'écran dans le padding du
        // Scaffold racine, donc sans barre supérieure (grand écran) l'inset de la barre d'état
        // était appliqué deux fois.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                // Même garde que dans les Réglages : la colonne défile, sinon les dernières entrées
                // sont rognées dès que le contenu dépasse (échelle 120 %, traductions longues).
                val sidebarScrollState = rememberScrollState()
                Row(modifier = Modifier.width(width).fillMaxHeight().background(mainBgColor)) {
                    Column(
                        // 🚨 CORRECTION 2 : Ajout du navigationBarsPadding
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .navigationBarsPadding()
                            .verticalScroll(sidebarScrollState)
                            // Même resserrage que l'en-tête du volet de contenu, pour que la ligne
                            // retour/menu et le titre restent sur le même axe.
                            .padding(top = sizing.spacing(4.dp), bottom = sizing.spacing(24.dp))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { safeBackNavigation.navigateBack() },
                                enabled = !safeBackNavigation.isLocked,
                                modifier = Modifier.padding(start = sizing.spacing(8.dp))
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.appstrings_back), tint = MaterialTheme.colorScheme.onSurface)
                            }
                            // ✅ RETOUR DU BOUTON MENU
                            IconButton(onClick = onCloseSidebar, modifier = Modifier.padding(end = sizing.spacing(8.dp))) {
                                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.appstrings_close_menu), tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))

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
                                    } else {
                                        // Mode « pages » : la nouvelle section remplace l'ancienne,
                                        // on repart de son début (sinon on hérite du défilement
                                        // précédent, désormais possible dans ce mode aussi).
                                        scope.launch { scrollState.scrollTo(0) }
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp)),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        AboutNavigationMenuItem(
                            title = stringResource(R.string.appstrings_settings_title),
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
                            style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = sizing.spacing(16.dp)),
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
                        // Marges serrées, comme dans les réglages : la bande est déjà repoussée
                        // sous la barre d'état et sa hauteur est imposée par les boutons.
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = sizing.spacing(4.dp), bottom = sizing.spacing(8.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(
                                visible = !isSidebarVisible,
                                enter = fadeIn(animationSpec = tween(300)) + expandHorizontally(),
                                exit = fadeOut(animationSpec = tween(200)) + shrinkHorizontally()
                            ) {
                                IconButton(
                                    onClick = onToggleSidebar,
                                    modifier = Modifier.padding(start = sizing.spacing(8.dp))
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.appstrings_open_menu), tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }

                            Text(
                                text = stringResource(R.string.appstrings_about),
                                style = sizing.textStyle(MaterialTheme.typography.headlineSmall),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )

                            AnimatedVisibility(
                                visible = !isSidebarVisible,
                                enter = fadeIn(animationSpec = tween(300)) + expandHorizontally(),
                                exit = fadeOut(animationSpec = tween(200)) + shrinkHorizontally()
                            ) {
                                Spacer(modifier = Modifier.width(sizing.spacing(56.dp)))
                            }
                        }
                    }

                    // --- CONTENU DE LA PAGE ---
                    Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coordinates ->
                                scrollViewportTop = coordinates.positionInRoot().y
                                scrollViewportHeight = coordinates.size.height.toFloat()
                            }
                            // Le défilement vaut pour TOUS les modes : en « pages » sur grand écran
                            // une section (Nouveautés, Sources…) peut dépasser la hauteur d'écran,
                            // et sans défilement son bas était simplement inatteignable.
                            .geoTowerFadingEdge(scrollState)
                            .pageScrollbar(PageScrollPrefs.ABOUT, scrollState)
                            .verticalScroll(scrollState)
                            .padding(horizontal = sizing.spacing(if (isExpanded) 48.dp else 24.dp))
                            // 🚨 CORRECTION 3 : Ajout de la marge de sécurité
                            .navigationBarsPadding()
                    ) {
                        if (navMode == 0 || !isExpanded) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AllAboutContent(
                                    appTitle = appTitle,
                                    appVersion = appVersion,
                                    logoResId = displayLogoResId,
                                    cardShape = cardShape,
                                    bubbleColor = bubbleBaseColor,
                                    visibleSections = visibleSections,
                                    sectionModifiers = sectionAnchorModifiers,
                                    onOpenHistories = {
                                        navController.navigate("histories")
                                    },
                                    onOpenDiagnostic = {
                                        navController.navigate("diagnostic")
                                    },
                                    onOpenTerms = {
                                        navController.navigate("terms")
                                    }
                                )
                                Spacer(modifier = Modifier.height(sizing.spacing(60.dp)))
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (activeSectionIndex in visibleSections) {
                                    AboutSectionContent(
                                        section = activeSectionIndex,
                                        appTitle = appTitle,
                                        appVersion = appVersion,
                                        logoResId = displayLogoResId,
                                        cardShape = cardShape,
                                        bubbleColor = bubbleBaseColor,
                                        onOpenHistories = { navController.navigate("histories") },
                                        onOpenDiagnostic = { navController.navigate("diagnostic") },
                                        onOpenTerms = { navController.navigate("terms") }
                                    )
                                }
                            }
                        }
                    }
                    // Le contenu défile dans tous les modes : les aides au défilement suivent.
                    PageScrollEdgeButtons(PageScrollPrefs.ABOUT, scrollState)
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
    val sizing = LocalGeoTowerUiStyle.current.sizing

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = sizing.spacing(12.dp), vertical = sizing.spacing(4.dp)),
        shape = shape,
        color = bgColor
    ) {
        Row(modifier = Modifier.padding(sizing.spacing(16.dp)), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(sizing.component(24.dp)))
            Spacer(modifier = Modifier.width(sizing.spacing(16.dp)))
            Text(title, style = sizing.textStyle(MaterialTheme.typography.bodyLarge), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = contentColor)
        }
    }
}

@Composable
fun AboutTopBar(onBack: () -> Unit) {
    GeoTowerBackTopBar(title = stringResource(R.string.appstrings_about), onBack = onBack)
}

// ============================================================
// CONTENU DES SECTIONS À PROPOS
// ============================================================

/**
 * Toutes les parties de la page à la suite, dans l'ordre de lecture.
 *
 * [visibleSections] porte les indices retenus : une partie masquée n'émet rien, et l'espacement qui
 * la précédait disparaît avec elle — sinon la page garderait des trous là où il n'y a plus rien.
 */
@Composable
fun AllAboutContent(
    appTitle: String,
    appVersion: String,
    logoResId: Int,
    cardShape: Shape,
    bubbleColor: Color,
    visibleSections: List<Int> = AboutPagePrefs.sections.indices.toList(),
    sectionModifiers: List<Modifier> = List(AboutPagePrefs.sections.size) { Modifier },
    onOpenHistories: () -> Unit = {},
    onOpenDiagnostic: () -> Unit = {},
    onOpenTerms: () -> Unit = {}
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    visibleSections.forEachIndexed { position, section ->
        if (position > 0) {
            // Le développement reste collé aux versions : c'est une signature, pas une section de
            // plus. Toutes les autres gardent leur respiration de 48.dp.
            Spacer(modifier = Modifier.height(sizing.spacing(if (section == DEVELOPMENT_SECTION) 16.dp else 48.dp)))
        }
        Column(
            modifier = sectionModifiers[section].fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AboutSectionContent(
                section = section,
                appTitle = appTitle,
                appVersion = appVersion,
                logoResId = logoResId,
                cardShape = cardShape,
                bubbleColor = bubbleColor,
                onOpenHistories = onOpenHistories,
                onOpenDiagnostic = onOpenDiagnostic,
                onOpenTerms = onOpenTerms
            )
        }
    }
}

/** Une partie de la page « À propos », désignée par son indice dans [AboutPagePrefs.sections]. */
@Composable
private fun AboutSectionContent(
    section: Int,
    appTitle: String,
    appVersion: String,
    logoResId: Int,
    cardShape: Shape,
    bubbleColor: Color,
    onOpenHistories: () -> Unit,
    onOpenDiagnostic: () -> Unit,
    onOpenTerms: () -> Unit
) {
    when (section) {
        0 -> SectionPresentation(appTitle, appVersion, logoResId)
        1 -> SectionNouveautes(appVersion, cardShape, bubbleColor)
        2 -> SectionConfidentialite(cardShape, bubbleColor, onOpenHistories, onOpenTerms)
        3 -> SectionSources(cardShape, bubbleColor)
        VERSIONS_SECTION -> SectionVersions(cardShape, bubbleColor, onOpenDiagnostic)
        DEVELOPMENT_SECTION -> SectionDeveloppement()
    }
}

@Composable
fun SectionPresentation(appTitle: String, appVersion: String, logoResId: Int) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    AboutDrawableImage(resId = logoResId, modifier = Modifier.size(sizing.component(150.dp)).padding(top = sizing.spacing(16.dp)))
    Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))
    Text(appTitle, style = sizing.textStyle(MaterialTheme.typography.headlineLarge), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Text(appVersion, style = sizing.textStyle(MaterialTheme.typography.bodyMedium), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(sizing.spacing(32.dp)))
    Text(
        text = stringResource(R.string.appstrings_about_intro),
        textAlign = TextAlign.Center,
        style = sizing.textStyle(MaterialTheme.typography.bodyLarge)
    )
}

@Composable
fun SectionNouveautes(appVersion: String, cardShape: Shape, bubbleColor: Color) {
    val releaseNotes = currentReleaseNotes()
    val sizing = LocalGeoTowerUiStyle.current.sizing

    SectionTitle(stringResource(R.string.about_new_for_version, appVersion))
    Card(
        colors = CardDefaults.cardColors(containerColor = if (bubbleColor == Color.Transparent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else bubbleColor),
        shape = cardShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(sizing.spacing(16.dp))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FilledNewReleases, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                Text(stringResource(R.string.appstrings_latest_changes), style = sizing.textStyle(MaterialTheme.typography.titleSmall), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

            releaseNotes.sections.forEach { section ->
                Text(section.title, style = sizing.textStyle(MaterialTheme.typography.labelLarge), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = sizing.spacing(8.dp), bottom = sizing.spacing(4.dp)))
                section.entries.forEach { entry ->
                    when (entry) {
                        is ReleaseNoteGroup -> {
                            Text(entry.title, style = sizing.textStyle(MaterialTheme.typography.bodyMedium), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = sizing.spacing(8.dp), top = sizing.spacing(4.dp), bottom = sizing.spacing(2.dp)))
                            entry.items.forEach { item ->
                                Text("• $item", style = sizing.textStyle(MaterialTheme.typography.bodySmall), modifier = Modifier.padding(start = sizing.spacing(20.dp), top = sizing.spacing(1.dp), bottom = sizing.spacing(1.dp)))
                            }
                        }
                        is ReleaseNoteItem -> Text("• ${entry.text}", style = sizing.textStyle(MaterialTheme.typography.bodyMedium), modifier = Modifier.padding(start = sizing.spacing(8.dp), top = sizing.spacing(2.dp), bottom = sizing.spacing(2.dp)))
                    }
                }
                Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))

            }
        }
    }
}

@Composable
fun SectionSources(cardShape: Shape, bubbleColor: Color) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    SectionTitle(stringResource(R.string.appstrings_about_sources))
    val cardColor = if (bubbleColor == Color.Transparent) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else bubbleColor

    // ✅ On initialise le gestionnaire de liens
    val uriHandler = LocalUriHandler.current

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = cardShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(sizing.spacing(16.dp))) {
            // 1. ANFR (Données)
            CreditItem(stringResource(R.string.appstrings_src_antennas), stringResource(R.string.appstrings_src_antennas_desc))
            Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
            SourceDataLink(
                label = stringResource(R.string.appstrings_version_weekly_label).replace('\n', ' '),
                host = "data.anfr.fr",
                onClick = { uriHandler.openUri(ANFR_WEEKLY_DATA_URL) }
            )
            SourceDataLink(
                label = stringResource(R.string.appstrings_version_monthly_label).replace('\n', ' '),
                host = "data.gouv.fr",
                onClick = { uriHandler.openUri(ANFR_MONTHLY_DATA_URL) }
            )
            SourceDataLink(
                label = stringResource(R.string.appstrings_version_quarterly_label).replace('\n', ' '),
                host = "data.arcep.fr",
                onClick = { uriHandler.openUri(ARCEP_QUARTERLY_DATA_URL) }
            )
            SourceDataLink(
                label = stringResource(R.string.appstrings_version_hs_label).replace('\n', ' '),
                host = "data.gouv.fr",
                onClick = { uriHandler.openUri(ARCEP_HS_DATA_URL) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = sizing.spacing(12.dp)), color = MaterialTheme.colorScheme.outlineVariant)

            // 2. IGN (Cliquable)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri("https://geoservices.ign.fr/") }
            ) {
                CreditItem(stringResource(R.string.appstrings_src_ign), stringResource(R.string.appstrings_src_ign_desc))
                Text("geoservices.ign.fr", style = sizing.textStyle(MaterialTheme.typography.labelSmall), color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = sizing.spacing(12.dp)), color = MaterialTheme.colorScheme.outlineVariant)

            // 3. OpenStreetMap (Cliquable)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri("https://www.openstreetmap.org/copyright") }
            ) {
                CreditItem(stringResource(R.string.appstrings_src_osm), stringResource(R.string.appstrings_src_osm_desc))
                Text("www.openstreetmap.org", style = sizing.textStyle(MaterialTheme.typography.labelSmall), color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = sizing.spacing(12.dp)), color = MaterialTheme.colorScheme.outlineVariant)

            // 4. MapsForge (Cliquable)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri("https://mapsforge.org") }
            ) {
                CreditItem(stringResource(R.string.appstrings_maps_forges_title), stringResource(R.string.appstrings_maps_forges_desc))
                Text("www.mapsforge.org", style = sizing.textStyle(MaterialTheme.typography.labelSmall), color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = sizing.spacing(12.dp)), color = MaterialTheme.colorScheme.outlineVariant)

            // 5. Inspiration
            CreditItem(stringResource(R.string.appstrings_src_inspo), stringResource(R.string.appstrings_src_inspo_desc))
        }
    }
}

@Composable
fun SectionDeveloppement() {
    SectionTitle(stringResource(R.string.appstrings_about_dev)) // <-- MODIFIÉ
    ListItem(
        headlineContent = { Text(stringResource(R.string.appstrings_dev_credit)) }, // <-- MODIFIÉ
        leadingContent = { Icon(imageVector = Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

// ============================================================
// UTILITAIRES
// ============================================================

@Composable
private fun SectionTitle(title: String) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Text(title, style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(12.dp)))
}

@Composable
private fun CreditItem(title: String, description: String) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Column {
        Text(title, style = sizing.textStyle(MaterialTheme.typography.titleSmall), fontWeight = FontWeight.Bold)
        Text(description, style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SourceDataLink(label: String, host: String, onClick: () -> Unit) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = sizing.spacing(4.dp))
    ) {
        Text(
            text = label,
            style = sizing.textStyle(MaterialTheme.typography.bodySmall),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = host,
            style = sizing.textStyle(MaterialTheme.typography.labelSmall),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private const val ANFR_WEEKLY_DATA_URL = "https://data.anfr.fr/explore/dataset/observatoire_2g_3g_4g/"
private const val ANFR_MONTHLY_DATA_URL = "https://www.data.gouv.fr/datasets/donnees-sur-les-installations-radioelectriques-de-plus-de-5-watts-1/"
private const val ARCEP_QUARTERLY_DATA_URL = "https://data.arcep.fr/mobile/sites/"
private const val ARCEP_HS_DATA_URL = "https://www.data.gouv.fr/datasets/sites-indisponibles"

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
fun SectionVersions(
    cardShape: Shape,
    bubbleColor: Color,
    onOpenDiagnostic: () -> Unit = {}
) {
    val context = LocalContext.current
    val sizing = LocalGeoTowerUiStyle.current.sizing
    var appVersion by remember { mutableStateOf("-") }
    var dbVersion by remember { mutableStateOf("-") }
    var radioDbVersion by remember { mutableStateOf("-") }
    var enbDbVersion by remember { mutableStateOf("-") }
    var anfrDate by remember { mutableStateOf("-") }
    var quarterlyVersion by remember { mutableStateOf("-") }
    var rawMonthlyVersion by remember { mutableStateOf("-") } // ✅ Changé en "rawMonthlyVersion"
    var hsDate by remember { mutableStateOf("-") }
    // Heure de génération des sites HS : l'appareil la connaît pour une génération locale, le
    // serveur la publie dans les métadonnées du fichier téléchargé. Une seule des deux est > 0 à la
    // fois, celle de la source réellement utilisée.
    var hsLocalGeneratedAt by remember { mutableLongStateOf(0L) }
    var hsServerGeneratedAt by remember { mutableLongStateOf(0L) }
    var hsRefreshTick by remember { mutableIntStateOf(0) }
    val txtDownloadNewBase = stringResource(R.string.appstrings_about_download_new_database)
    val txtInvalidLocalDatabase = stringResource(R.string.appstrings_invalid_local_database)
    val txtNotInstalled = stringResource(R.string.appstrings_about_database_not_installed)
    val txtVersionTimeAt = stringResource(R.string.appstrings_version_time_at)

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
                        dbVersion = formatAboutDatabaseVersion(cursor.getString(0), txtVersionTimeAt)

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
                    quarterlyVersion = readQuarterlyDataVersion(db)
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
                    quarterlyVersion = "-"
                }
            } catch (e: Exception) {
                quarterlyVersion = "-"
                AppLogger.w(TAG_ABOUT, "Database version info could not be read", e)
            }

            // 3. Données des sites HS (depuis les préférences)
            try {
                val radioDbPath = context.getDatabasePath(fr.geotower.data.db.RadioDatabaseValidator.DB_NAME)
                radioDbVersion = if (radioDbPath.exists()) {
                    val validation = fr.geotower.data.db.RadioDatabaseValidator.validateDatabaseFile(radioDbPath)
                    if (validation.isValid) {
                        SQLiteDatabase.openDatabase(radioDbPath.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { radioDb ->
                            radioDb.rawQuery("SELECT version FROM metadata LIMIT 1", null).use { radioCursor ->
                                if (radioCursor.moveToFirst()) {
                                    formatAboutDatabaseVersion(radioCursor.getString(0), txtVersionTimeAt)
                                } else {
                                    "-"
                                }
                            }
                        }
                    } else {
                        txtInvalidLocalDatabase
                    }
                } else {
                    txtNotInstalled
                }
            } catch (e: Exception) {
                radioDbVersion = "-"
                AppLogger.w(TAG_ABOUT, "Radio database version info could not be read", e)
            }

            // 4. Base des identifiants eNB/gNB (optionnelle, hors Room)
            enbDbVersion = readEnbDatabaseVersion(
                context = context,
                notInstalledLabel = txtNotInstalled,
                invalidLabel = txtInvalidLocalDatabase,
                timeAtLabel = txtVersionTimeAt
            )
        }
    }

    // Les sites HS, eux, bougent pendant que la page est ouverte : une génération locale peut se
    // terminer (bouton « Générer maintenant », planification en fond, cache expiré côté carte). On
    // relit donc leurs prefs à chaque retour au premier plan, sans refaire les lectures SQLite.
    LifecycleResumeEffect(Unit) {
        hsRefreshTick++
        onPauseOrDispose { }
    }
    LaunchedEffect(hsRefreshTick) {
        withContext(Dispatchers.IO) {
            val state = readSitesHsState(context)
            hsDate = state.serverDate
            hsLocalGeneratedAt = state.localGeneratedAtMillis
            hsServerGeneratedAt = state.serverGeneratedAtMillis
        }
    }

    SectionTitle(stringResource(R.string.appstrings_about_versions_title))
    val cardColor = if (bubbleColor == Color.Transparent) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else bubbleColor
    val uriHandler = LocalUriHandler.current

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = cardShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(10.dp))) {
            // Sites HS produits sur l'appareil : le libellé l'annonce. Les deux sources portent
            // désormais leur heure de génération (à la minute) ; il ne reste que la date seule
            // quand le fichier serveur ne l'annonce pas.
            val hsGeneratedLocally = hsLocalGeneratedAt > 0L
            val hsLabel = stringResource(
                if (hsGeneratedLocally) R.string.appstrings_version_hs_local_label
                else R.string.appstrings_version_hs_label
            )
            val hsValue = when {
                hsGeneratedLocally ->
                    LocalizedDateLabels.formatVersionDateTime(context, hsLocalGeneratedAt, txtVersionTimeAt)
                hsServerGeneratedAt > 0L ->
                    LocalizedDateLabels.formatVersionDateTime(context, hsServerGeneratedAt, txtVersionTimeAt)
                else -> LocalizedDateLabels.formatVersionDate(context, hsDate)
            }

            // Génération locale en cours : la base n'est installée qu'à la fin du build. On dit
            // « génération en cours » au lieu de « non installée » (la lecture SQLite ci-dessus ne
            // peut rien trouver tant que le fichier final n'est pas posé).
            val localBuild = rememberLocalDbBuildStatus()
            val txtGenerating = stringResource(R.string.appstrings_db_generation_in_progress)
            val dbVersionShown =
                if (localBuild.mobileRunning && dbVersion == txtNotInstalled) txtGenerating else dbVersion
            val radioDbVersionShown =
                if (localBuild.radioRunning && radioDbVersion == txtNotInstalled) txtGenerating else radioDbVersion

            val versionRows = listOf(
                stringResource(R.string.appstrings_version_app_label) to appVersion,
                stringResource(R.string.appstrings_version_db_label) to dbVersionShown,
                stringResource(R.string.appstrings_version_radio_db_label) to radioDbVersionShown,
                stringResource(R.string.appstrings_version_enb_db_label) to enbDbVersion,
                stringResource(R.string.appstrings_version_weekly_label) to LocalizedDateLabels.formatWeeklyVersionWithWeekNumber(context, anfrDate),
                stringResource(R.string.appstrings_version_monthly_label) to LocalizedDateLabels.formatMonthlyVersion(context, rawMonthlyVersion),
                stringResource(R.string.appstrings_version_quarterly_label) to LocalizedDateLabels.formatQuarterlyVersion(context, quarterlyVersion),
                hsLabel to hsValue
            )

            versionRows.forEachIndexed { index, row ->
                VersionLine(row.first, row.second)
                if (index < versionRows.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = sizing.spacing(2.dp)),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

    Surface(
        onClick = { uriHandler.openUri(GEOTOWER_APP_DOWNLOAD_URL) },
        shape = cardShape,
        color = cardColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(sizing.spacing(16.dp)), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
            Column {
                Text(stringResource(R.string.appstrings_about_download_app_title), style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.appstrings_about_download_app_desc), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

    Surface(
        onClick = onOpenDiagnostic,
        shape = cardShape,
        color = cardColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(sizing.spacing(16.dp)), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
            Column {
                Text(stringResource(R.string.appstrings_diagnostic_title), style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.appstrings_diagnostic_desc), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private const val GEOTOWER_APP_DOWNLOAD_URL =
    "https://kdrive.infomaniak.com/app/share/2149816/6d30423f-ac8b-4509-9857-86684d3a2e03"

private fun readQuarterlyDataVersion(db: SQLiteDatabase): String {
    return try {
        db.rawQuery(
            """
            SELECT source_value
            FROM source_versions
            WHERE source_key = 'quarterly_version'
            LIMIT 1
            """.trimIndent(),
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                normalizeQuarterlyVersion(cursor.getString(0))
            } else {
                "-"
            }
        }
    } catch (e: Exception) {
        "-"
    }
}

/**
 * Ligne « sites HS » : date de la donnée serveur, plus l'horodatage de génération de chaque source
 * (0 pour celle qui n'est pas utilisée).
 */
private data class SitesHsState(
    val serverDate: String,
    val serverGeneratedAtMillis: Long,
    val localGeneratedAtMillis: Long
)

/**
 * Lit l'état des sites HS (prefs uniquement, donc rejouable à volonté). Les horodatages sont rendus
 * exclusifs par la source réellement active (traitement local, crans « pannes en local ») : afficher l'heure
 * de la génération locale sur une donnée serveur - ou l'inverse - annoncerait une fraîcheur fausse.
 */
private fun readSitesHsState(context: Context): SitesHsState {
    val prefs = context.getSharedPreferences(OutageLocalConfig.PREFS_NAME, Context.MODE_PRIVATE)
    val config = OutageLocalConfig(prefs)
    val generatedLocally = AppConfig.outagesLocal()
    return SitesHsState(
        serverDate = OutageServerInfo.lastUpdate(prefs),
        serverGeneratedAtMillis = if (generatedLocally) 0L else OutageServerInfo.generatedAtMillis(prefs),
        localGeneratedAtMillis = if (generatedLocally) config.lastGeneratedAtMillis else 0L
    )
}

/**
 * Ligne « base eNB/gNB » : heure de production du fichier par le serveur (`metadata.generated_at`,
 * en UTC), avec repli sur la date de la version quand la base est trop ancienne pour la porter.
 *
 * Lecture volontairement légère - pas de [fr.geotower.data.db.EnbDatabaseValidator.validateDatabaseFile]
 * ici : son `PRAGMA integrity_check` balaye tout le fichier, ce qui n'a pas sa place à l'ouverture
 * d'une page. Une base illisible se signale d'elle-même par l'exception.
 */
private fun readEnbDatabaseVersion(
    context: Context,
    notInstalledLabel: String,
    invalidLabel: String,
    timeAtLabel: String
): String {
    val dbPath = context.getDatabasePath(fr.geotower.data.db.EnbDatabaseValidator.DB_NAME)
    if (!dbPath.exists()) return notInstalledLabel

    return try {
        SQLiteDatabase.openDatabase(dbPath.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            // `generated_at` n'est pas exigé par le validateur : requête en cascade, comme pour la
            // base mobile, pour rester lisible face à un fichier plus ancien.
            val cursor = try {
                db.rawQuery("SELECT version, generated_at FROM metadata LIMIT 1", null)
            } catch (e: Exception) {
                db.rawQuery("SELECT version FROM metadata LIMIT 1", null)
            }
            cursor.use { c ->
                if (!c.moveToFirst()) {
                    invalidLabel
                } else {
                    val generatedAt = if (c.columnCount > 1 && !c.isNull(1)) {
                        LocalizedDateLabels.isoInstantMillis(c.getString(1))
                    } else {
                        0L
                    }
                    if (generatedAt > 0L) {
                        LocalizedDateLabels.formatVersionDateTime(context, generatedAt, timeAtLabel)
                    } else {
                        // La version eNB est « <date des données>-<digest> » : seule la date en sort.
                        formatAboutDatabaseVersion(c.getString(0), timeAtLabel)
                    }
                }
            }
        }
    } catch (e: Exception) {
        AppLogger.w(TAG_ABOUT, "eNB database version info could not be read", e)
        invalidLabel
    }
}

private fun normalizeQuarterlyVersion(rawValue: String?): String {
    val raw = rawValue?.trim().orEmpty()
    if (raw.isBlank() || raw == "-") return "-"

    val normalized = raw.uppercase()
    Regex("""(20\d{2})[\s_-]*(?:T|Q|TRIM(?:ESTRE)?)[\s_-]*([1-4])""")
        .find(normalized)
        ?.let { return "${it.groupValues[1]}-T${it.groupValues[2]}" }
    Regex("""(?:T|Q|TRIM(?:ESTRE)?)[\s_-]*([1-4])[\s_-]*(20\d{2})""")
        .find(normalized)
        ?.let { return "${it.groupValues[2]}-T${it.groupValues[1]}" }
    Regex("""([1-4])(?:ER|E)?[\s_-]*TRIM(?:ESTRE)?[\s_-]*(20\d{2})""")
        .find(normalized)
        ?.let { return "${it.groupValues[2]}-T${it.groupValues[1]}" }

    return raw
}

private fun formatAboutDatabaseVersion(rawValue: String?, timeAtLabel: String): String {
    val raw = rawValue?.trim().orEmpty()
    if (raw.isBlank()) return "-"

    val match = Regex("""(20\d{2})\D?(\d{2})\D?(\d{2})(?:\D?(\d{2})\D?(\d{2}))?""")
        .find(raw)
        ?: return raw
    val date = "${match.groupValues[3]}/${match.groupValues[2]}/${match.groupValues[1]}"
    val hour = match.groupValues.getOrNull(4).orEmpty()
    val minute = match.groupValues.getOrNull(5).orEmpty()
    return if (hour.isNotBlank() && minute.isNotBlank()) {
        "$date\n$timeAtLabel $hour:$minute"
    } else {
        date
    }
}

@Composable
private fun VersionLine(label: String, value: String) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = sizing.spacing(7.dp)),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(0.52f)
                .padding(end = sizing.spacing(12.dp))
        )
        Text(
            text = value,
            style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.48f)
        )
    }
}

@Composable
fun SectionConfidentialite(
    cardShape: Shape,
    bubbleColor: Color,
    onOpenHistories: () -> Unit = {},
    onOpenTerms: () -> Unit = {}
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    SectionTitle(stringResource(R.string.appstrings_privacy_category))
    val cardColor = if (bubbleColor == Color.Transparent) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else bubbleColor

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = cardShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(sizing.spacing(16.dp))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                Text(stringResource(R.string.appstrings_your_data_title), style = sizing.textStyle(MaterialTheme.typography.titleSmall), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
            Text(stringResource(R.string.appstrings_your_data_desc), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))
    Surface(
        onClick = onOpenHistories,
        shape = cardShape,
        color = cardColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(sizing.spacing(16.dp)), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
            Column {
                Text(stringResource(R.string.histories_title), style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.histories_desc), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))
    Surface(
        onClick = onOpenTerms,
        shape = cardShape,
        color = cardColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(sizing.spacing(16.dp)), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
            Column {
                Text(stringResource(R.string.appstrings_terms_title), style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.appstrings_terms_button_desc), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
