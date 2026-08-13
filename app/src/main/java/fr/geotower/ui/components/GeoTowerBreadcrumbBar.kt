package fr.geotower.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import fr.geotower.R

data class GeoTowerBreadcrumbItem(
    val label: String,
    val icon: ImageVector? = null,
    val onClick: (() -> Unit)? = null,
    val key: String? = null
)

@Composable
fun GeoTowerBreadcrumbBar(
    items: List<GeoTowerBreadcrumbItem>,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainer
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    if (items.isEmpty()) return

    val scrollState = rememberScrollState()
    val currentItemKey = items.last().resolvedKey

    LaunchedEffect(items.size, currentItemKey, scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // Coins concentriques : la barre ET ses pastilles sont des stades (rayon = moitie de la
    // hauteur), et la marge qui les separe est la meme sur les quatre cotes. Le rayon exterieur
    // vaut alors toujours exactement « rayon interieur + marge », a n'importe quelle taille
    // d'interface. Avec l'ancien MaterialTheme.shapes.extraLarge (28.dp figes) la barre gardait un
    // bord droit des que sa hauteur depassait 56.dp, alors que la pastille restait un demi-cercle :
    // les deux coins ne s'emboitaient plus.
    val pillShape = RoundedCornerShape(percent = 50)
    val pillMargin = sizing.spacing(7.dp)
    // Un segment cliquable est une Surface(onClick), donc Material 3 lui impose la cible tactile
    // minimale de 48.dp : son EMPLACEMENT passe a 48.dp mais la pastille restait peinte a sa
    // hauteur naturelle (~39.dp), centree, laissant ~4,4.dp de vide en haut et en bas. La marge
    // visible valait donc 11.dp en haut/bas contre 7.dp sur les cotes. On peint la pastille a la
    // taille de sa cible tactile : plus de vide, marge identique sur les quatre cotes (et hauteur
    // de barre inchangee, puisque c'est deja cette cible qui la dictait). Valeur non mise a
    // l'echelle, comme la cible tactile de Material qu'elle vient combler.
    val pillMinHeight = 48.dp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = sizing.spacing(12.dp), vertical = sizing.spacing(6.dp)),
        shape = pillShape,
        color = backgroundColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(pillMargin),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sizing.spacing(4.dp))
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(sizing.component(16.dp))
                    )
                }

                val isCurrent = index == items.lastIndex || item.onClick == null
                val segmentColor = if (isCurrent) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
                }
                val segmentContentColor = if (isCurrent) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                if (item.onClick != null) {
                    Surface(
                        onClick = item.onClick,
                        modifier = Modifier.heightIn(min = pillMinHeight),
                        color = segmentColor,
                        contentColor = segmentContentColor,
                        shape = pillShape,
                        tonalElevation = if (isCurrent) 0.dp else 1.dp
                    ) {
                        BreadcrumbContent(
                            item = item,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = sizing.spacing(11.dp), vertical = sizing.spacing(8.dp))
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier.heightIn(min = pillMinHeight),
                        color = segmentColor,
                        contentColor = segmentContentColor,
                        shape = pillShape,
                        tonalElevation = if (isCurrent) 0.dp else 1.dp
                    ) {
                        BreadcrumbContent(
                            item = item,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = sizing.spacing(11.dp), vertical = sizing.spacing(8.dp))
                        )
                    }
                }
            }
        }
    }
}

@Suppress("RestrictedApi")
@Composable
fun GeoTowerNavigationBreadcrumbBar(
    navController: NavController,
    currentItem: GeoTowerBreadcrumbItem,
    currentRouteKeys: Set<String>,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    impliedParentItems: List<GeoTowerBreadcrumbItem> = emptyList(),
    onBackStackItemClick: () -> Unit = {}
) {
    val backStack by navController.currentBackStack.collectAsState()
    val labels = rememberGeoTowerBreadcrumbLabels()
    val stackItems = backStack
        .mapNotNull { entry ->
            entry.toGeoTowerBreadcrumbItem(labels) {
                onBackStackItemClick()
                navController.popBackStack(entry.destination.id, false)
            }
        }
        .toMutableList()

    while (stackItems.lastOrNull()?.resolvedKey in currentRouteKeys) {
        stackItems.removeAt(stackItems.lastIndex)
    }

    val current = currentItem.copy(onClick = null)
    val breadcrumbItems = if (impliedParentItems.isNotEmpty()) {
        impliedParentItems + current
    } else {
        if (stackItems.lastOrNull()?.resolvedKey != current.resolvedKey) {
            stackItems.add(current)
        }
        stackItems
    }

    GeoTowerBreadcrumbBar(
        items = breadcrumbItems,
        modifier = modifier,
        backgroundColor = backgroundColor
    )
}

@Composable
private fun BreadcrumbContent(
    item: GeoTowerBreadcrumbItem,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.SemiBold
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        item.icon?.let { icon ->
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(sizing.component(16.dp))
            )
            Spacer(modifier = Modifier.width(sizing.spacing(5.dp)))
        }
        Text(
            text = item.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = sizing.text(13.sp),
            fontWeight = fontWeight
        )
    }
}

@Composable
private fun rememberGeoTowerBreadcrumbLabels(): GeoTowerBreadcrumbLabels {
    return GeoTowerBreadcrumbLabels(
        home = stringResource(R.string.help_topic_title_home),
        map = stringResource(R.string.nav_map),
        emitters = stringResource(R.string.nav_near_antennas),
        compass = stringResource(R.string.nav_compass),
        stats = stringResource(R.string.nav_statistics),
        settings = stringResource(R.string.nav_settings),
        about = stringResource(R.string.nav_about),
        diagnostic = stringResource(R.string.appstrings_diagnostic_title),
        support = stringResource(R.string.appstrings_support_detail_title),
        site = stringResource(R.string.appstrings_site_detail_title),
        elevationProfile = stringResource(R.string.appstrings_elevation_profile_title),
        throughputCalculator = stringResource(R.string.appstrings_throughput_calculator_title),
        speedtests = stringResource(R.string.appstrings_speedtests_all_title),
        uploadHistory = stringResource(R.string.appstrings_upload_history_title),
        shareHistory = stringResource(R.string.share_history_title),
        notificationHistory = stringResource(R.string.notification_history_title),
        histories = stringResource(R.string.histories_title),
        departmentStats = stringResource(R.string.department_stats_title),
        radio = stringResource(R.string.appstrings_radio_share_radio_title)
    )
}

private data class GeoTowerBreadcrumbLabels(
    val home: String,
    val map: String,
    val emitters: String,
    val compass: String,
    val stats: String,
    val settings: String,
    val about: String,
    val diagnostic: String,
    val support: String,
    val site: String,
    val elevationProfile: String,
    val throughputCalculator: String,
    val speedtests: String,
    val uploadHistory: String,
    val shareHistory: String,
    val notificationHistory: String,
    val histories: String,
    val departmentStats: String,
    val radio: String
)

private val GeoTowerBreadcrumbItem.resolvedKey: String
    get() = key ?: label

private fun NavBackStackEntry.toGeoTowerBreadcrumbItem(
    labels: GeoTowerBreadcrumbLabels,
    onClick: () -> Unit
): GeoTowerBreadcrumbItem? {
    return when (destination.route) {
        "home" -> GeoTowerBreadcrumbItem(labels.home, Icons.Default.Home, onClick, "home")
        "map?photoDraftId={photoDraftId}" -> GeoTowerBreadcrumbItem(labels.map, Icons.Default.Map, onClick, "map")
        "emitters" -> GeoTowerBreadcrumbItem(labels.emitters, Icons.Default.MyLocation, onClick, "emitters")
        "compass" -> GeoTowerBreadcrumbItem(labels.compass, Icons.Default.Explore, onClick, "compass")
        "stats" -> GeoTowerBreadcrumbItem(labels.stats, Icons.Default.BarChart, onClick, "stats")
        "stats/departments" -> GeoTowerBreadcrumbItem(labels.departmentStats, Icons.Default.BarChart, onClick, "stats/departments")
        "stats/departments/{deptCode}" -> GeoTowerBreadcrumbItem(
            arguments?.getString("deptCode")?.takeIf { it.isNotBlank() } ?: labels.departmentStats,
            Icons.Default.BarChart,
            onClick,
            "stats/departments/detail"
        )
        "settings?section={section}&target_map={targetMapFilename}" -> GeoTowerBreadcrumbItem(labels.settings, Icons.Default.Settings, onClick, "settings")
        "about" -> GeoTowerBreadcrumbItem(labels.about, Icons.Default.Home, onClick, "about")
        "diagnostic" -> GeoTowerBreadcrumbItem(labels.diagnostic, Icons.Default.Info, onClick, "diagnostic")
        "photo_upload_history" -> GeoTowerBreadcrumbItem(labels.uploadHistory, Icons.Default.History, onClick, "photo_upload_history")
        "share_history" -> GeoTowerBreadcrumbItem(labels.shareHistory, Icons.Default.History, onClick, "share_history")
        "notification_history" -> GeoTowerBreadcrumbItem(labels.notificationHistory, Icons.Default.History, onClick, "notification_history")
        "histories" -> GeoTowerBreadcrumbItem(labels.histories, Icons.Default.History, onClick, "histories")
        "support_detail/{id}?operator={operator}&fromMap={fromMap}&photoDraftId={photoDraftId}" -> GeoTowerBreadcrumbItem(labels.support, Icons.Default.VerticalAlignTop, onClick, "support_detail")
        "site_detail/{id}" -> GeoTowerBreadcrumbItem(labels.site, Icons.Default.Tag, onClick, "site_detail")
        "site_detail_from_map/{id}" -> GeoTowerBreadcrumbItem(labels.site, Icons.Default.Tag, onClick, "site_detail_from_map")
        "elevation_profile/{id}" -> GeoTowerBreadcrumbItem(labels.elevationProfile, Icons.Default.Terrain, onClick, "elevation_profile")
        "throughput_calculator/{id}" -> GeoTowerBreadcrumbItem(labels.throughputCalculator, Icons.Default.Speed, onClick, "throughput_calculator")
        "site_speedtests?siteId={siteId}&anfrCode={anfrCode}&operator={operator}&market={market}&mcc={mcc}&mnc={mnc}" -> GeoTowerBreadcrumbItem(labels.speedtests, Icons.Default.Timer, onClick, "site_speedtests")
        "radio_site_detail/{stationId}/{supportId}" -> GeoTowerBreadcrumbItem(labels.radio, Icons.Default.VerticalAlignTop, onClick, "radio_site_detail")
        else -> null
    }
}
