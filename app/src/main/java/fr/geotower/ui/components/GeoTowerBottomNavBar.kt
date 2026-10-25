package fr.geotower.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    val systemNavBars = WindowInsets.navigationBars.asPaddingValues()
    val rawBottomPadding = systemNavBars.calculateBottomPadding()

    val bottomInset = if (rawBottomPadding > 24.dp) {
        (rawBottomPadding * 0.4f).coerceAtMost(16.dp)
    } else {
        (rawBottomPadding * 0.15f).coerceAtMost(4.dp)
    }

    val leftInset = systemNavBars.calculateLeftPadding(layoutDirection)
    val rightInset = systemNavBars.calculateRightPadding(layoutDirection)

    val opacity = (AppConfig.bottomNavOpacity.intValue / 100f).coerceIn(0f, 1f)

    val floatingBarBgColor = when {
        isDark && isOled -> Color(0xFF201F23)
        isDark -> Color(0xFF28272C)
        else -> Color(0xFFF3F4F7)
    }.copy(alpha = opacity)

    val floatingBarBorderColor = if (isDark) {
        Color.White.copy(alpha = 0.10f * (opacity * 0.7f + 0.3f))
    } else {
        Color.Black.copy(alpha = 0.07f * (opacity * 0.7f + 0.3f))
    }

    // Géométrie concentrique exacte :
    // Hauteur totale H = 58.dp => Rayon extérieur R_outer = 29.dp.
    // Marge uniforme M = 4.dp sur les 4 côtés.
    // Hauteur intérieure = 50.dp => Rayon intérieur R_inner = 25.dp.
    val barHeight = sizing.component(58.dp)
    val uniformMargin = sizing.spacing(4.dp)

    // En mode One UI, la capsule adapte sa largeur au nombre d'onglets au lieu de s'étirer
    val itemWidth = sizing.component(72.dp)
    val targetBarWidth = itemWidth * tabs.size + uniformMargin * 2
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
            shadowElevation = if (opacity <= 0.05f) 0.dp else (8.dp * opacity),
            tonalElevation = if (opacity < 1f) 0.dp else 4.dp
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(uniformMargin)
            ) {
                val tabCount = tabs.size
                val tabWidth = maxWidth / tabCount
                val activeIndex = tabs.indexOf(activeTab)

                val activeItemBgColor = if (isDark) {
                    Color(0xFF424046).copy(alpha = (opacity * 0.6f + 0.4f).coerceAtMost(1f))
                } else {
                    Color(0xFFDFE1E8).copy(alpha = (opacity * 0.6f + 0.4f).coerceAtMost(1f))
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

                    Box(
                        modifier = Modifier
                            .offset(x = animatedOffset)
                            .width(tabWidth)
                            .fillMaxHeight()
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
                        val isSelected = activeTab == tab
                        OneUiBottomNavItem(
                            tab = tab,
                            isSelected = isSelected,
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
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val isDark = uiStyle.isDark

    val selectedContentColor = if (isDark) Color.White else Color(0xFF1B1B1E)
    val unselectedContentColor = if (isDark) Color(0xFFC4C3C8) else Color(0xFF6B6C72)

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
            .clickable { onNavigate(tab.route) },
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

    val opacity = (AppConfig.bottomNavOpacity.intValue / 100f).coerceIn(0f, 1f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = opacity),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = if (opacity < 1f) 0.dp else 3.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(customInsets)
                .height(sizing.component(64.dp))
                .padding(horizontal = sizing.spacing(8.dp))
        ) {
            val tabCount = tabs.size
            val tabWidth = maxWidth / tabCount
            val activeIndex = tabs.indexOf(activeTab)
            val indicatorWidth = sizing.component(44.dp)
            val indicatorHeight = sizing.component(26.dp)

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
                        .offset(x = animatedOffset, y = sizing.spacing(6.dp))
                        .width(indicatorWidth)
                        .height(indicatorHeight)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = (opacity * 0.6f + 0.4f).coerceAtMost(1f)))
                )
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val isSelected = activeTab == tab
                    StandardBottomNavItem(
                        tab = tab,
                        isSelected = isSelected,
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
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onNavigate(tab.route) }
            .padding(horizontal = sizing.spacing(1.dp), vertical = sizing.spacing(4.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .height(sizing.component(26.dp))
                .width(sizing.component(44.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = stringResource(tab.labelRes),
                tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(sizing.component(19.dp))
            )
        }
        Spacer(modifier = Modifier.height(sizing.spacing(2.dp)))
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
