package fr.geotower.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
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

enum class BottomNavTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    NEARBY("emitters", R.string.bottom_nav_nearby, Icons.Default.MyLocation),
    MAP("map", R.string.bottom_nav_map, Icons.Default.Map),
    TRIPS("trips", R.string.bottom_nav_trips, Icons.Default.Route),
    STATS("stats", R.string.bottom_nav_stats, Icons.Default.BarChart),
    MORE("more", R.string.bottom_nav_more, Icons.Default.MoreHoriz)
}

@Composable
fun GeoTowerBottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiStyle = LocalGeoTowerUiStyle.current
    val activeTab = BottomNavTab.entries.find { tab ->
        if (tab.route == "map") {
            currentRoute?.startsWith("map") == true
        } else {
            currentRoute == tab.route
        }
    }

    if (uiStyle.useOneUi) {
        OneUiFloatingBottomNavBar(
            activeTab = activeTab,
            onNavigate = onNavigate,
            modifier = modifier
        )
    } else {
        StandardBottomNavBar(
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

    val floatingBarBgColor = when {
        isDark && isOled -> Color(0xFF201F23)
        isDark -> Color(0xFF28272C)
        else -> Color(0xFFF3F4F7)
    }

    val floatingBarBorderColor = if (isDark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.Black.copy(alpha = 0.07f)
    }

    // Géométrie concentrique exacte :
    // Hauteur totale H = 58.dp => Rayon extérieur R_outer = 29.dp.
    // Marge uniforme M = 4.dp sur les 4 côtés.
    // Hauteur intérieure = 50.dp => Rayon intérieur R_inner = 25.dp.
    // Vérification : R_inner = R_outer - M (25 = 29 - 4). Les deux arcs sont rigoureusement concentriques.
    val barHeight = sizing.component(58.dp)
    val uniformMargin = sizing.spacing(4.dp)

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
                .widthIn(max = 500.dp)
                .fillMaxWidth()
                .height(barHeight),
            shape = CircleShape,
            color = floatingBarBgColor,
            border = BorderStroke(1.dp, floatingBarBorderColor),
            shadowElevation = 8.dp,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(uniformMargin),
                horizontalArrangement = Arrangement.spacedBy(sizing.spacing(2.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavTab.entries.forEach { tab ->
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

/**
 * Bouton individuel One UI avec surbrillance ovale concentrique pour l'onglet actif.
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

    val activeItemBgColor = if (isDark) {
        Color(0xFF424046)
    } else {
        Color(0xFFDFE1E8)
    }

    val animatedBgColor by animateColorAsState(
        targetValue = if (isSelected) activeItemBgColor else Color.Transparent,
        label = "oneui_tab_bg"
    )

    val selectedContentColor = if (isDark) Color.White else Color(0xFF1B1B1E)
    val unselectedContentColor = if (isDark) Color(0xFFC4C3C8) else Color(0xFF6B6C72)

    val animatedContentColor by animateColorAsState(
        targetValue = if (isSelected) selectedContentColor else unselectedContentColor,
        label = "oneui_tab_content"
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(animatedBgColor)
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

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(customInsets)
                .height(sizing.component(64.dp))
                .padding(horizontal = sizing.spacing(8.dp)),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavTab.entries.forEach { tab ->
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
                .width(sizing.component(44.dp))
                .clip(CircleShape)
                .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
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
