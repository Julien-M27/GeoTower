package fr.geotower.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.R
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.PowerProfile

enum class BottomNavTab(
    val id: String,
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    NEARBY("nearby", "emitters", R.string.bottom_nav_nearby, Icons.Default.MyLocation),
    MAP("map", "map", R.string.bottom_nav_map, Icons.Default.Map),
    TRIPS("trips", "trips", R.string.bottom_nav_trips, Icons.Default.Route),
    COMPASS("compass", "compass", R.string.nav_compass, Icons.Default.Explore),
    STATS("stats", "stats", R.string.bottom_nav_stats, Icons.Default.BarChart),
    SETTINGS("settings", "settings", R.string.nav_settings, Icons.Default.Settings),
    ABOUT("about", "about", R.string.nav_about, Icons.Default.Info),
    HELP("help", "help", R.string.appstrings_help_title, Icons.AutoMirrored.Filled.Help),
    MORE("more", "more", R.string.bottom_nav_more, Icons.Default.MoreHoriz);

    companion object {
        fun fromId(id: String): BottomNavTab? = entries.firstOrNull { it.id == id }
    }
}

@Composable
fun GeoTowerBottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiStyle = LocalGeoTowerUiStyle.current
    val configuredTabs = AppConfig.bottomNavTabs.value.mapNotNull { BottomNavTab.fromId(it) }
    val tabs = if (configuredTabs.isEmpty()) {
        listOf(BottomNavTab.NEARBY, BottomNavTab.MAP, BottomNavTab.TRIPS, BottomNavTab.STATS, BottomNavTab.MORE)
    } else {
        configuredTabs + BottomNavTab.MORE
    }

    val activeTab = tabs.find { tab ->
        if (tab.route == "map") {
            currentRoute?.startsWith("map") == true
        } else {
            currentRoute == tab.route
        }
    }

    if (uiStyle.useOneUi) {
        OneUiFloatingBottomNavBar(
            tabs = tabs,
            activeTab = activeTab,
            onNavigate = onNavigate,
            modifier = modifier
        )
    } else {
        StandardBottomNavBar(
            tabs = tabs,
            activeTab = activeTab,
            onNavigate = onNavigate,
            modifier = modifier
        )
    }
}

/**
 * Barre de navigation flottante de type capsule / dock pour le mode One UI (One UI 7).
 * Ratios d'arcs de cercles concentriques strictement respectés (R_inner = R_outer - Marge).
 */
@Composable
private fun OneUiFloatingBottomNavBar(
    tabs: List<BottomNavTab>,
    activeTab: BottomNavTab?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val isDark = uiStyle.isDark
    val isOled = uiStyle.isOled
    val layoutDirection = LocalLayoutDirection.current
    val haptic = LocalHapticFeedback.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val systemNavBars = WindowInsets.navigationBars.asPaddingValues()
    val rawBottomPadding = systemNavBars.calculateBottomPadding()

    val bottomInset = if (rawBottomPadding > 24.dp) {
        (rawBottomPadding * 0.4f).coerceAtMost(16.dp)
    } else {
        (rawBottomPadding * 0.15f).coerceAtMost(4.dp)
    }

    val leftInset = systemNavBars.calculateLeftPadding(layoutDirection)
    val rightInset = systemNavBars.calculateRightPadding(layoutDirection)

    val opacity = AppConfig.floatingUiOpacityProgress
    val effectiveBarAlpha = AppConfig.floatingUiAlpha

    val floatingBarBgColor = when {
        isDark && isOled -> Color(0xFF201F23)
        isDark -> Color(0xFF28272C)
        else -> Color(0xFFF3F4F7)
    }.copy(alpha = effectiveBarAlpha)

    val floatingBarBorderColor = AppConfig.floatingUiBorderColor()

    // Géométrie concentrique exacte :
    // Hauteur totale H = 58.dp => Rayon extérieur R_outer = 29.dp.
    // Marge uniforme M = 4.dp sur les 4 côtés.
    // Hauteur intérieure = 50.dp => Rayon intérieur R_inner = 25.dp.
    val barHeight = sizing.component(58.dp)
    val uniformMargin = sizing.spacing(4.dp)

    // En mode One UI, la capsule adapte sa largeur au nombre d'onglets au lieu de s'étirer
    val itemWidth = sizing.component(72.dp)
    val targetBarWidth = itemWidth * tabs.size + uniformMargin * 2
    var hoveredTab by remember { mutableStateOf<BottomNavTab?>(null) }
    val displayTab = hoveredTab ?: activeTab
    val animatedBarWidth by animateDpAsState(
        targetValue = targetBarWidth,
        animationSpec = if (PowerProfile.isEcoPlus) snap() else spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "oneui_bar_width"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = sizing.spacing(16.dp) + leftInset,
                end = sizing.spacing(16.dp) + rightInset,
                bottom = bottomInset + sizing.spacing(16.dp),
                top = sizing.spacing(6.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = animatedBarWidth)
                .fillMaxWidth()
                .height(barHeight),
            shape = CircleShape,
            color = floatingBarBgColor,
            border = BorderStroke(1.dp, floatingBarBorderColor),
            shadowElevation = sizing.component(4.dp * opacity),
            tonalElevation = if (opacity < 1f) 0.dp else 4.dp
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(uniformMargin)
                    .bottomNavDraggable(
                        tabs = tabs,
                        activeTab = activeTab,
                        onNavigate = onNavigate,
                        haptic = haptic,
                        isRtl = isRtl,
                        onTabHovered = { hoveredTab = it }
                    )
            ) {
                val tabCount = tabs.size
                val tabWidth = maxWidth / tabCount
                val activeIndex = tabs.indexOf(displayTab)

                val activeItemBgColor = if (isDark) {
                    Color(0xFF48464E).copy(alpha = (0.55f + 0.45f * opacity).coerceAtMost(1f))
                } else {
                    Color(0xFFD6D8E2).copy(alpha = (0.60f + 0.40f * opacity).coerceAtMost(1f))
                }

                // Animation de glissement entre chaque bouton (désactivée en mode éco +)
                if (activeIndex >= 0) {
                    val targetOffset = tabWidth * activeIndex
                    val animatedOffset by animateDpAsState(
                        targetValue = targetOffset,
                        animationSpec = if (PowerProfile.isEcoPlus) snap() else spring(
                            dampingRatio = 0.85f,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "oneui_tab_slide"
                    )

                    // Animation de dézoom puis zoom lors du déplacement de la zone active
                    val pillScale = remember { Animatable(1f) }
                    var previousIndex by remember { mutableIntStateOf(activeIndex) }
                    var isInitialized by remember { mutableStateOf(false) }

                    LaunchedEffect(activeIndex) {
                        if (!isInitialized) {
                            isInitialized = true
                            previousIndex = activeIndex
                            return@LaunchedEffect
                        }
                        if (PowerProfile.isEcoPlus) {
                            pillScale.snapTo(1f)
                            previousIndex = activeIndex
                            return@LaunchedEffect
                        }
                        if (activeIndex != previousIndex) {
                            previousIndex = activeIndex
                            // Phase 1 : Dézoom rapide pendant le départ et la traversée
                            pillScale.animateTo(
                                targetValue = 0.80f,
                                animationSpec = tween(
                                    durationMillis = 110,
                                    easing = FastOutSlowInEasing
                                )
                            )
                            // Phase 2 : Zoom avec léger rebond élastique à l'arrivée
                            pillScale.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = 0.68f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .offset(x = animatedOffset)
                            .width(tabWidth)
                            .fillMaxHeight()
                            .graphicsLayer {
                                scaleX = pillScale.value
                                scaleY = pillScale.value
                            }
                            .clip(CircleShape)
                            .background(activeItemBgColor)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { tab ->
                        val isSelected = displayTab == tab
                        OneUiBottomNavItem(
                            tab = tab,
                            isSelected = isSelected,
                            opacity = opacity,
                            onNavigate = onNavigate,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bouton individuel One UI. L'arrière-plan actif est assuré par la pastille de glissement.
 */
@Composable
private fun OneUiBottomNavItem(
    tab: BottomNavTab,
    isSelected: Boolean,
    opacity: Float,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val isDark = uiStyle.isDark

    val selectedContentColor = if (isDark) Color.White else Color(0xFF141416)
    val unselectedContentColor = if (isDark) {
        Color(0xFFE2E2E8)
    } else {
        Color(0xFF28292E)
    }

    val animatedContentColor by animateColorAsState(
        targetValue = if (isSelected) selectedContentColor else unselectedContentColor,
        animationSpec = if (PowerProfile.isEcoPlus) snap() else spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "oneui_tab_content"
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .semantics {
                role = Role.Tab
                selected = isSelected
                onClick {
                    onNavigate(tab.route)
                    true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sizing.spacing(2.dp))
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = stringResource(tab.labelRes),
                tint = animatedContentColor,
                modifier = Modifier.size(sizing.component(20.dp))
            )
            Spacer(modifier = Modifier.height(sizing.spacing(2.dp)))
            Text(
                text = stringResource(tab.labelRes),
                fontSize = sizing.text(9.sp),
                lineHeight = sizing.text(12.sp),
                letterSpacing = (-0.3).sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = animatedContentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Barre de navigation classique Material 3 ancrée en bas pour le mode standard.
 * Marges de sécurité latérales et taille de texte ajustée pour éliminer toute coupure aux bords.
 */
@Composable
private fun StandardBottomNavBar(
    tabs: List<BottomNavTab>,
    activeTab: BottomNavTab?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val layoutDirection = LocalLayoutDirection.current
    val haptic = LocalHapticFeedback.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val systemNavBars = WindowInsets.navigationBars.asPaddingValues()
    val rawBottomPadding = systemNavBars.calculateBottomPadding()

    val bottomInset = if (rawBottomPadding > 24.dp) {
        (rawBottomPadding * 0.4f).coerceAtMost(16.dp)
    } else {
        (rawBottomPadding * 0.15f).coerceAtMost(3.dp)
    }

    val customInsets = WindowInsets(
        left = systemNavBars.calculateLeftPadding(layoutDirection),
        top = 0.dp,
        right = systemNavBars.calculateRightPadding(layoutDirection),
        bottom = bottomInset
    )

    var hoveredTab by remember { mutableStateOf<BottomNavTab?>(null) }
    val displayTab = hoveredTab ?: activeTab

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AppConfig.floatingUiAlpha),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(customInsets)
                .height(sizing.component(64.dp))
                .padding(horizontal = sizing.spacing(8.dp))
                .bottomNavDraggable(
                    tabs = tabs,
                    activeTab = activeTab,
                    onNavigate = onNavigate,
                    haptic = haptic,
                    isRtl = isRtl,
                    onTabHovered = { hoveredTab = it }
                )
        ) {
            val tabCount = tabs.size
            val tabWidth = maxWidth / tabCount
            val activeIndex = tabs.indexOf(displayTab)
            val indicatorWidth = sizing.component(48.dp)
            val indicatorHeight = sizing.component(28.dp)
            val indicatorTop = sizing.spacing(8.dp)

            // Animation de glissement entre chaque bouton (désactivée en mode éco +)
            if (activeIndex >= 0) {
                val targetOffset = tabWidth * activeIndex + (tabWidth - indicatorWidth) / 2
                val animatedOffset by animateDpAsState(
                    targetValue = targetOffset,
                    animationSpec = if (PowerProfile.isEcoPlus) snap() else spring(
                        dampingRatio = 0.85f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "standard_tab_slide"
                )

                Box(
                    modifier = Modifier
                        .offset(x = animatedOffset, y = indicatorTop)
                        .width(indicatorWidth)
                        .height(indicatorHeight)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                )
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val isSelected = displayTab == tab
                    StandardBottomNavItem(
                        tab = tab,
                        isSelected = isSelected,
                        indicatorWidth = indicatorWidth,
                        indicatorHeight = indicatorHeight,
                        indicatorTop = indicatorTop,
                        onNavigate = onNavigate,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Élément individuel en mode standard : indicateur compact et texte protégé anti-coupure.
 */
@Composable
private fun StandardBottomNavItem(
    tab: BottomNavTab,
    isSelected: Boolean,
    indicatorWidth: Dp,
    indicatorHeight: Dp,
    indicatorTop: Dp,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .semantics {
                role = Role.Tab
                selected = isSelected
                onClick {
                    onNavigate(tab.route)
                    true
                }
            }
            .padding(top = indicatorTop, bottom = sizing.spacing(4.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Box(
            modifier = Modifier
                .height(indicatorHeight)
                .width(indicatorWidth),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = stringResource(tab.labelRes),
                tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(sizing.component(19.dp))
            )
        }
        Spacer(modifier = Modifier.height(sizing.spacing(3.dp)))
        Text(
            text = stringResource(tab.labelRes),
            fontSize = sizing.text(9.sp),
            lineHeight = sizing.text(12.sp),
            letterSpacing = (-0.3).sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Gestionnaire de geste fluide pour la barre de navigation :
 * - Permet de faire glisser son doigt horizontalement sur la barre pour changer de page en continu et à la volée
 * - Déclenche un retour haptique à chaque transition d'onglet franchie
 * - Gère le balayage rapide (swipe / flick)
 * - Maintient le clic / tap direct
 * - Ne s'interrompt jamais pendant le glissement grâce à rememberUpdatedState
 */
@Composable
private fun Modifier.bottomNavDraggable(
    tabs: List<BottomNavTab>,
    activeTab: BottomNavTab?,
    onNavigate: (String) -> Unit,
    haptic: HapticFeedback,
    isRtl: Boolean,
    onTabHovered: (BottomNavTab?) -> Unit = {}
): Modifier {
    val currentTabs by rememberUpdatedState(tabs)
    val currentActiveTab by rememberUpdatedState(activeTab)
    val currentOnNavigate by rememberUpdatedState(onNavigate)
    val currentHaptic by rememberUpdatedState(haptic)
    val currentIsRtl by rememberUpdatedState(isRtl)
    val currentOnTabHovered by rememberUpdatedState(onTabHovered)

    return this.pointerInput(Unit) {
        val tabCountProvider = { currentTabs.size }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val tabCount = tabCountProvider()
            if (tabCount <= 1) return@awaitEachGesture

            val tabWidthPx = size.width.toFloat() / tabCount
            val startX = down.position.x
            val rawStartIndex = kotlin.math.floor(startX / tabWidthPx).toInt().coerceIn(0, tabCount - 1)
            val startIndex = if (currentIsRtl) (tabCount - 1 - rawStartIndex) else rawStartIndex
            val startTab = currentTabs.getOrNull(startIndex)
            var lastDispatchedRoute = currentActiveTab?.route
            var hasDragged = false

            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break

                    if (!pointer.pressed) {
                        val currentX = pointer.position.x
                        val diffX = currentX - startX
                        val swipeThreshold = viewConfiguration.touchSlop * 1.5f

                        if (!hasDragged) {
                            // Tap simple sur un onglet
                            if (startTab != null) {
                                currentHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnNavigate(startTab.route)
                            }
                        } else if (lastDispatchedRoute == currentActiveTab?.route) {
                            // Balayage rapide (swipe / flick) n'ayant pas atteint le seuil d'un onglet complet
                            val forward = if (currentIsRtl) diffX < -swipeThreshold else diffX > swipeThreshold
                            val backward = if (currentIsRtl) diffX > swipeThreshold else diffX < -swipeThreshold
                            val currentIdx = currentTabs.indexOf(currentActiveTab).coerceAtLeast(0)
                            if (forward && currentIdx < tabCount - 1) {
                                currentHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnNavigate(currentTabs[currentIdx + 1].route)
                            } else if (backward && currentIdx > 0) {
                                currentHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnNavigate(currentTabs[currentIdx - 1].route)
                            }
                        }
                        break
                    }

                    val currentX = pointer.position.x
                    val diffX = currentX - startX
                    val diffY = pointer.position.y - down.position.y

                    if (!hasDragged) {
                        val absX = kotlin.math.abs(diffX)
                        val absY = kotlin.math.abs(diffY)
                        if (absX > viewConfiguration.touchSlop && absX > absY) {
                            hasDragged = true
                        }
                    }

                    if (hasDragged) {
                        pointer.consume()
                        val currentTabCount = tabCountProvider()
                        val currentWidthPx = size.width.toFloat() / currentTabCount
                        val rawTargetIndex = kotlin.math.floor(currentX / currentWidthPx).toInt().coerceIn(0, currentTabCount - 1)
                        val targetIndex = if (currentIsRtl) (currentTabCount - 1 - rawTargetIndex) else rawTargetIndex
                        val targetTab = currentTabs.getOrNull(targetIndex)
                        if (targetTab != null) {
                            currentOnTabHovered(targetTab)
                            if (targetTab.route != lastDispatchedRoute) {
                                lastDispatchedRoute = targetTab.route
                                currentHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnNavigate(targetTab.route)
                            }
                        }
                    }
                }
            } finally {
                currentOnTabHovered(null)
            }
        }
    }
}
