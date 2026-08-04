package fr.geotower.ui.screens.stats

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.db.DepartmentOperatorTechRow
import fr.geotower.data.db.DepartmentStatRow
import fr.geotower.ui.components.CustomizableBlock
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.GeoTowerPullToRefreshBox
import fr.geotower.ui.components.PageCustomizationFooter
import fr.geotower.ui.components.PageCustomizationHint
import fr.geotower.ui.components.PageScrollEdgeButtons
import fr.geotower.ui.components.SecureScreenEffect
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.components.geoTowerLazyListFadingEdge
import fr.geotower.ui.components.pageScrollbar
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.navigation.ROOT_FALLBACK_ROUTE
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.screens.settings.DepartmentStatsSettingsSheet
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.DepartmentStatsPreferences
import fr.geotower.utils.LocationHelper
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.PageScrollPrefs
import fr.geotower.utils.PreferenceStores
import fr.geotower.utils.hasLocationPermission
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.withTimeoutOrNull

const val DEPARTMENT_STATS_ROUTE = "stats/departments"
const val DEPARTMENT_STAT_DETAIL_ROUTE = "stats/departments/{deptCode}"

fun departmentStatDetailRoute(deptCode: String): String = "stats/departments/$deptCode"

private val TECH_COLUMNS = listOf("2G", "3G", "4G", "5G")

/** Totalisateur du serveur, aussi bien côté opérateur que côté technologie. */
private const val ITEM_ALL = "ALL"

/**
 * Les deux tables départementales sont figées tant que la base ne change pas : on garde le
 * résultat au niveau process pour que l'aller-retour liste → fiche → liste soit instantané.
 */
private object DepartmentStatsCache {
    var departments: List<DepartmentStatRow>? = null
    val matrixByDept = mutableMapOf<String, List<DepartmentOperatorTechRow>>()
}

private fun searchNormalized(value: String): String =
    Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

private fun formatCount(value: Int): String = "%,d".format(value)

private fun formatRatio(value: Double?, decimals: Int): String =
    if (value == null) "—" else "%,.${decimals}f".format(value)

private fun DepartmentStatRow.displayName(): String = deptName?.takeIf { it.isNotBlank() } ?: deptCode

private fun DepartmentStatRow.displayTitle(): String {
    val name = deptName?.takeIf { it.isNotBlank() } ?: return deptCode
    return "$deptCode — $name"
}

/** Libellé court de l'opérateur (`BOUYGUES TELECOM` → `Bouygues Telecom`) et sa couleur. */
private fun operatorLabel(rawName: String): String {
    val key = OperatorColors.keyFor(rawName) ?: return rawName
    return OperatorColors.specForKey(key)?.label ?: rawName
}

private fun operatorColor(rawName: String): Color {
    val key = OperatorColors.keyFor(rawName)
    val spec = key?.let { OperatorColors.specForKey(it) }
    return Color(spec?.colorArgb?.toInt() ?: OperatorColors.UNKNOWN_ARGB.toInt())
}

/**
 * Point servant à désigner « votre département ».
 *
 * La position de l'appareil passe avant tout : `last_map_lat` / `last_map_lon` sont le **centre de
 * la carte**, réécrit à chaque déplacement et à chaque ouverture de fiche site, donc le dernier
 * endroit regardé — pas l'endroit où l'on est. Ils ne servent plus que de repli, quand la
 * permission de localisation est refusée ou qu'aucun point n'est disponible.
 *
 * @param allowFreshFix demande un point GPS frais (2,5 s au plus). Réservé au geste de
 *   rafraîchissement : à l'ouverture, le dernier point connu suffit et n'allume rien.
 */
private suspend fun currentPoints(
    context: Context,
    prefs: SharedPreferences,
    allowFreshFix: Boolean
): List<Pair<Double, Double>> {
    val points = ArrayList<Pair<Double, Double>>(2)

    if (hasLocationPermission(context)) {
        val helper = LocationHelper(context)
        val cached = helper.getLastLocation()
        val fresh = if (allowFreshFix) withTimeoutOrNull(2_500L) { helper.getCurrentLocation() } else null
        (fresh ?: cached)?.let { points.add(it.latitude to it.longitude) }
    }

    val lat = prefs.getFloat("last_map_lat", Float.NaN)
    val lon = prefs.getFloat("last_map_lon", Float.NaN)
    if (!lat.isNaN() && !lon.isNaN()) points.add(lat.toDouble() to lon.toDouble())
    return points
}

/**
 * Même ordre que l'écran Statistiques : l'opérateur par défaut de l'utilisateur d'abord, puis
 * l'ordre du référentiel, et les opérateurs inconnus à la fin.
 */
private fun operatorSortIndex(rawName: String, defaultOperatorKey: String?): Int {
    val key = OperatorColors.keyFor(rawName) ?: return Int.MAX_VALUE
    if (key == defaultOperatorKey) return -1
    val index = OperatorColors.orderedKeys.indexOf(key)
    return if (index >= 0) index else Int.MAX_VALUE - 1
}

/** Accès aux stats départementales depuis l'écran Statistiques. */
@Composable
fun DepartmentStatsEntryCard(bgColor: Color, onClick: () -> Unit) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val safeClick = rememberSafeClick()
    val shape = if (AppConfig.useOneUiDesign) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)

    Surface(
        onClick = { safeClick("stats_departments_entry") { onClick() } },
        color = bgColor,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sizing.spacing(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(sizing.spacing(16.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Map,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(sizing.component(24.dp))
            )
            Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.department_stats_entry_title),
                    style = sizing.textStyle(MaterialTheme.typography.titleSmall),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.department_stats_entry_description),
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(sizing.component(20.dp))
            )
        }
    }
}

// =====================================================================================
// Liste des départements
// =====================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepartmentStatsScreen(navController: NavController, repository: AnfrRepository) {
    SecureScreenEffect(RemoteFeatureFlags.SecureScreens.STATS)
    val context = LocalContext.current
    val appContext = context.applicationContext
    val safeClick = rememberSafeClick()
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val listState = rememberLazyListState()
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = ROOT_FALLBACK_ROUTE)
    val prefs = remember(appContext) { appContext.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE) }

    var showSettingsSheet by remember { mutableStateOf(false) }
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Relu à chaque recomposition : la fermeture de la feuille de réglages en déclenche une.
    val showSearch = DepartmentStatsPreferences.isOptionEnabled(prefs, DepartmentStatsPreferences.PREF_SHOW_SEARCH)
    val showCurrent = DepartmentStatsPreferences.isOptionEnabled(prefs, DepartmentStatsPreferences.PREF_SHOW_CURRENT)
    val showSummary = DepartmentStatsPreferences.isOptionEnabled(prefs, DepartmentStatsPreferences.PREF_SHOW_SUMMARY)

    val themeMode by AppConfig.themeMode
    val isOled by AppConfig.isOledMode
    val isDark = themeMode == 2 || (themeMode == 0 && isSystemInDarkTheme())
    val pageColor = if (isDark && isOled) Color.Black else MaterialTheme.colorScheme.background
    val cardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val cardShape = if (AppConfig.useOneUiDesign) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)

    var departments by remember { mutableStateOf(DepartmentStatsCache.departments ?: emptyList()) }
    var isLoading by remember { mutableStateOf(DepartmentStatsCache.departments == null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var currentDeptCode by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = !safeBackNavigation.isLocked) { safeBackNavigation.navigateBack() }

    LaunchedEffect(appContext, refreshTrigger) {
        val loaded = repository.getDepartmentStats()
        DepartmentStatsCache.departments = loaded
        departments = loaded
        isLoading = false

        // Position de l'appareil d'abord ; un point frais n'est demande que sur un geste de
        // rafraichissement explicite, pour ne pas reveiller le GPS a chaque ouverture. Le centre
        // de la carte reste en repli : hors de France ou avec une fausse position figee dans le
        // fournisseur, mieux vaut le dernier endroit regarde que plus rien du tout.
        currentDeptCode = currentPoints(appContext, prefs, allowFreshFix = refreshTrigger > 0)
            .firstNotNullOfOrNull { (lat, lon) -> repository.getDepartmentCodeNear(lat, lon) }
        isRefreshing = false
    }

    val normalizedQuery = searchNormalized(query)
    val filtered = remember(departments, normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            departments
        } else {
            departments.filter { row ->
                row.deptCode.lowercase(Locale.ROOT).startsWith(normalizedQuery) ||
                    searchNormalized(row.deptName.orEmpty()).contains(normalizedQuery)
            }
        }
    }
    val currentDepartment = departments.firstOrNull { it.deptCode == currentDeptCode }

    Scaffold(
        containerColor = pageColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.department_stats_title),
                onBack = { safeBackNavigation.navigateBack() },
                backgroundColor = pageColor,
                backEnabled = !safeBackNavigation.isLocked,
                actions = {
                    PageCustomizationHint(
                        page = PageScrollPrefs.DEPARTMENT_STATS,
                        onOpenSettings = { showSettingsSheet = true }
                    ) {
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.appstrings_settings_title)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize()
        ) {
            GeoTowerPullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    if (!isRefreshing) {
                        isRefreshing = true
                        // La matrice memorisee par departement peut dater d'avant une mise a jour
                        // de la base : on la laisse se recharger a la prochaine ouverture de fiche.
                        DepartmentStatsCache.matrixByDept.clear()
                        refreshTrigger++
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .geoTowerLazyListFadingEdge(listState)
                        .pageScrollbar(PageScrollPrefs.DEPARTMENT_STATS, listState),
                    contentPadding = PaddingValues(
                        start = sizing.spacing(16.dp),
                        top = sizing.spacing(8.dp),
                        end = sizing.spacing(16.dp),
                        bottom = sizing.spacing(16.dp) +
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(sizing.spacing(10.dp))
                ) {
                    if (departments.isNotEmpty() && showSearch) {
                        item {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.department_stats_search_hint)) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (query.isNotEmpty()) {
                                        IconButton(onClick = { query = "" }) {
                                            Icon(
                                                Icons.Default.Clear,
                                                contentDescription = stringResource(R.string.appstrings_cancel)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }

                    if (isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(sizing.spacing(32.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (departments.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = cardColor),
                                shape = cardShape,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.department_stats_unavailable),
                                    style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(sizing.spacing(16.dp))
                                )
                            }
                        }
                    } else {
                        if (currentDepartment != null && showCurrent && normalizedQuery.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.department_stats_current),
                                    style = sizing.textStyle(MaterialTheme.typography.labelLarge),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = sizing.spacing(4.dp))
                                )
                            }
                            item {
                                DepartmentRow(
                                    row = currentDepartment,
                                    cardColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                    cardShape = cardShape,
                                    showSummary = showSummary,
                                    leadingIcon = Icons.Default.MyLocation,
                                    onClick = {
                                        safeClick("dept_stats_current") {
                                            navController.navigate(departmentStatDetailRoute(currentDepartment.deptCode))
                                        }
                                    }
                                )
                            }
                        }

                        if (filtered.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.department_stats_no_result),
                                    style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(sizing.spacing(16.dp))
                                )
                            }
                        } else {
                            items(filtered, key = { it.deptCode }) { row ->
                                DepartmentRow(
                                    row = row,
                                    cardColor = cardColor,
                                    cardShape = cardShape,
                                    showSummary = showSummary,
                                    onClick = {
                                        safeClick("dept_stats_${row.deptCode}") {
                                            navController.navigate(departmentStatDetailRoute(row.deptCode))
                                        }
                                    }
                                )
                            }
                        }

                        item {
                            PageCustomizationFooter(
                                onClick = { showSettingsSheet = true },
                                modifier = Modifier.padding(top = sizing.spacing(8.dp))
                            )
                        }
                    }
                }
            }

            PageScrollEdgeButtons(PageScrollPrefs.DEPARTMENT_STATS, listState)
        }
    }

    if (showSettingsSheet) {
        DepartmentStatsSettingsSheet(
            onDismiss = { showSettingsSheet = false },
            onBack = { showSettingsSheet = false },
            sheetState = settingsSheetState,
            useOneUi = uiStyle.useOneUi,
            bubbleColor = uiStyle.bubbleColor
        )
    }
}

@Composable
private fun DepartmentRow(
    row: DepartmentStatRow,
    cardColor: Color,
    cardShape: Shape,
    onClick: () -> Unit,
    showSummary: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Surface(
        onClick = onClick,
        color = cardColor,
        shape = cardShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(sizing.spacing(14.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(sizing.component(20.dp))
                )
                Spacer(modifier = Modifier.width(sizing.spacing(10.dp)))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.displayTitle(),
                    style = sizing.textStyle(MaterialTheme.typography.titleSmall),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showSummary) {
                    Text(
                        text = stringResource(
                            R.string.department_stats_summary,
                            formatCount(row.supports),
                            formatCount(row.stations),
                            formatCount(row.antennas)
                        ),
                        style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// =====================================================================================
// Fiche d'un département
// =====================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepartmentStatDetailScreen(
    navController: NavController,
    repository: AnfrRepository,
    deptCode: String
) {
    SecureScreenEffect(RemoteFeatureFlags.SecureScreens.STATS)
    val context = LocalContext.current
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val scrollState = rememberScrollState()
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = ROOT_FALLBACK_ROUTE)
    val prefs = remember(context) {
        context.applicationContext.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
    }

    var showSettingsSheet by remember { mutableStateOf(false) }
    // Bloc visé par un appui long : la feuille s'ouvre sur sa ligne, comme sur l'écran Statistiques.
    var settingsHighlightBlock by remember { mutableStateOf<String?>(null) }
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val onCustomizeBlock: (String) -> Unit = { blockId ->
        settingsHighlightBlock = blockId
        showSettingsSheet = true
    }
    val blockOrder = DepartmentStatsPreferences.blockOrder(prefs)

    val themeMode by AppConfig.themeMode
    val isOled by AppConfig.isOledMode
    val isDark = themeMode == 2 || (themeMode == 0 && isSystemInDarkTheme())
    val pageColor = if (isDark && isOled) Color.Black else MaterialTheme.colorScheme.background
    val cardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val cardShape = if (AppConfig.useOneUiDesign) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)

    var department by remember { mutableStateOf(DepartmentStatsCache.departments?.firstOrNull { it.deptCode == deptCode }) }
    var matrix by remember { mutableStateOf(DepartmentStatsCache.matrixByDept[deptCode] ?: emptyList()) }
    var isLoading by remember { mutableStateOf(department == null || matrix.isEmpty()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var showActiveOnly by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = !safeBackNavigation.isLocked) { safeBackNavigation.navigateBack() }

    LaunchedEffect(deptCode, refreshTrigger) {
        // Au rafraichissement, on relit tout : la base a pu changer depuis l'ouverture de la fiche.
        val forced = refreshTrigger > 0
        if (department == null || forced) {
            val loaded = if (forced) {
                repository.getDepartmentStats().also { DepartmentStatsCache.departments = it }
            } else {
                DepartmentStatsCache.departments ?: repository.getDepartmentStats().also {
                    DepartmentStatsCache.departments = it
                }
            }
            department = loaded.firstOrNull { it.deptCode == deptCode }
        }
        val rows = repository.getDepartmentOperatorTechStats(deptCode)
        if (rows.isNotEmpty()) {
            DepartmentStatsCache.matrixByDept[deptCode] = rows
            matrix = rows
        }
        isLoading = false
        isRefreshing = false
    }

    Scaffold(
        containerColor = pageColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GeoTowerBackTopBar(
                title = department?.displayTitle() ?: deptCode,
                onBack = { safeBackNavigation.navigateBack() },
                backgroundColor = pageColor,
                backEnabled = !safeBackNavigation.isLocked,
                actions = {
                    PageCustomizationHint(
                        page = PageScrollPrefs.DEPARTMENT_STATS,
                        onOpenSettings = { settingsHighlightBlock = null; showSettingsSheet = true }
                    ) {
                        IconButton(onClick = { settingsHighlightBlock = null; showSettingsSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.appstrings_settings_title)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize()
        ) {
            GeoTowerPullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    if (!isRefreshing) {
                        isRefreshing = true
                        refreshTrigger++
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .geoTowerFadingEdge(scrollState)
                        .pageScrollbar(PageScrollPrefs.DEPARTMENT_STATS, scrollState)
                        .verticalScroll(scrollState)
                        .padding(
                            start = sizing.spacing(16.dp),
                            end = sizing.spacing(16.dp),
                            top = sizing.spacing(8.dp),
                            bottom = sizing.spacing(16.dp) +
                                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        ),
                    // Arrangement.Top : l'espacement des blocs est porté par CustomizableBlock, qui le
                    // fait disparaître avec le bloc quand il est masqué (voir sa documentation).
                    verticalArrangement = Arrangement.Top
                ) {
                    val row = department
                    if (isLoading && row == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(sizing.spacing(32.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (row == null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardColor),
                            shape = cardShape,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.department_stats_unavailable),
                                style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(sizing.spacing(16.dp))
                            )
                        }
                    } else {
                        DepartmentModeSelector(
                            showActiveOnly = showActiveOnly,
                            onChange = { showActiveOnly = it }
                        )
                        Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

                        blockOrder.forEach { blockId ->
                            if (!DepartmentStatsPreferences.isBlockVisible(prefs, blockId)) return@forEach
                            CustomizableBlock(blockId, onCustomizeBlock, spacing = 12.dp) {
                                when (blockId) {
                                    DepartmentStatsPreferences.BLOCK_AUTHORISATIONS -> DepartmentAuthorisationsCard(
                                        row = row,
                                        showActiveOnly = showActiveOnly,
                                        cardColor = cardColor,
                                        cardShape = cardShape
                                    )

                                    DepartmentStatsPreferences.BLOCK_OPERATORS -> DepartmentOperatorTechCard(
                                        rows = matrix,
                                        showActiveOnly = showActiveOnly,
                                        cardColor = cardColor,
                                        cardShape = cardShape
                                    )
                                }
                            }
                        }

                        PageCustomizationFooter(
                            onClick = { settingsHighlightBlock = null; showSettingsSheet = true }
                        )
                    }
                }
            }

            PageScrollEdgeButtons(PageScrollPrefs.DEPARTMENT_STATS, scrollState)
        }
    }

    if (showSettingsSheet) {
        DepartmentStatsSettingsSheet(
            onDismiss = { showSettingsSheet = false; settingsHighlightBlock = null },
            onBack = { showSettingsSheet = false; settingsHighlightBlock = null },
            sheetState = settingsSheetState,
            useOneUi = uiStyle.useOneUi,
            bubbleColor = uiStyle.bubbleColor,
            highlightBlockId = settingsHighlightBlock
        )
    }
}

@Composable
private fun DepartmentModeSelector(showActiveOnly: Boolean, onChange: (Boolean) -> Unit) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Row(horizontalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp))) {
        FilterChip(
            selected = !showActiveOnly,
            onClick = { onChange(false) },
            label = { Text(stringResource(R.string.department_stats_mode_all)) },
            colors = FilterChipDefaults.filterChipColors()
        )
        FilterChip(
            selected = showActiveOnly,
            onClick = { onChange(true) },
            label = { Text(stringResource(R.string.department_stats_mode_active)) },
            colors = FilterChipDefaults.filterChipColors()
        )
    }
}

@Composable
private fun DepartmentAuthorisationsCard(
    row: DepartmentStatRow,
    showActiveOnly: Boolean,
    cardColor: Color,
    cardShape: Shape
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val supports = if (showActiveOnly) row.supportsActive else row.supports
    val stations = if (showActiveOnly) row.stationsActive else row.stations
    val antennas = if (showActiveOnly) row.antennasActive else row.antennas

    // Les ratios servis par le serveur portent sur les autorisations : en mode « en service »,
    // on les recalcule sur place plutôt que d'afficher des chiffres qui ne correspondent pas.
    val stationsPerSupport = if (showActiveOnly) ratioOf(stations, supports) else row.stationsPerSupport
    val antennasPerStation = if (showActiveOnly) ratioOf(antennas, stations) else row.antennasPerStation
    val supportsPerKm2 = if (showActiveOnly) ratioOf(supports, row.areaKm2) else row.supportsPerKm2
    val stationsPerKm2 = if (showActiveOnly) ratioOf(stations, row.areaKm2) else row.stationsPerKm2
    val antennasPerKm2 = if (showActiveOnly) ratioOf(antennas, row.areaKm2) else row.antennasPerKm2
    val population1k = row.population?.let { it / 1000.0 }
    val supportsPer1k = if (showActiveOnly) ratioOf(supports, population1k) else row.supportsPer1kHab
    val stationsPer1k = if (showActiveOnly) ratioOf(stations, population1k) else row.stationsPer1kHab
    val antennasPer1k = if (showActiveOnly) ratioOf(antennas, population1k) else row.antennasPer1kHab
    val habPerSupport = if (showActiveOnly) ratioOf(row.population, supports) else row.habPerSupport
    val habPerStation = if (showActiveOnly) ratioOf(row.population, stations) else row.habPerStation
    val habPerAntenna = if (showActiveOnly) ratioOf(row.population, antennas) else row.habPerAntenna

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = cardShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(sizing.spacing(16.dp))) {
            Text(
                text = stringResource(R.string.department_stats_authorisations_title),
                style = sizing.textStyle(MaterialTheme.typography.titleSmall),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(sizing.spacing(10.dp)))

            StatLine(stringResource(R.string.department_stats_supports), formatCount(supports))
            StatLine(stringResource(R.string.department_stats_stations), formatCount(stations))
            StatLine(stringResource(R.string.department_stats_antennas), formatCount(antennas))
            HorizontalDivider(modifier = Modifier.padding(vertical = sizing.spacing(8.dp)))
            StatLine(stringResource(R.string.department_stats_stations_per_support), formatRatio(stationsPerSupport, 2))
            StatLine(stringResource(R.string.department_stats_antennas_per_station), formatRatio(antennasPerStation, 3))
            StatLine(stringResource(R.string.department_stats_supports_per_km2), formatRatio(supportsPerKm2, 3))
            StatLine(stringResource(R.string.department_stats_stations_per_km2), formatRatio(stationsPerKm2, 3))
            StatLine(stringResource(R.string.department_stats_antennas_per_km2), formatRatio(antennasPerKm2, 3))
            HorizontalDivider(modifier = Modifier.padding(vertical = sizing.spacing(8.dp)))
            StatLine(stringResource(R.string.department_stats_supports_per_1k), formatRatio(supportsPer1k, 3))
            StatLine(stringResource(R.string.department_stats_stations_per_1k), formatRatio(stationsPer1k, 3))
            StatLine(stringResource(R.string.department_stats_antennas_per_1k), formatRatio(antennasPer1k, 3))
            StatLine(stringResource(R.string.department_stats_hab_per_support), formatRatio(habPerSupport, 0))
            StatLine(stringResource(R.string.department_stats_hab_per_station), formatRatio(habPerStation, 0))
            StatLine(stringResource(R.string.department_stats_hab_per_antenna), formatRatio(habPerAntenna, 0))

            Spacer(modifier = Modifier.height(sizing.spacing(10.dp)))
            val area = row.areaKm2
            val population = row.population
            val reference = if (area != null && population != null) {
                val year = row.populationYear
                if (year.isNullOrBlank()) {
                    stringResource(
                        R.string.department_stats_reference_no_year,
                        row.displayName(),
                        "%,.0f".format(area),
                        formatCount(population)
                    )
                } else {
                    stringResource(
                        R.string.department_stats_reference,
                        row.displayName(),
                        "%,.0f".format(area),
                        formatCount(population),
                        year
                    )
                }
            } else {
                stringResource(R.string.department_stats_missing_reference)
            }
            Text(
                text = reference,
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (row.antennasFh > 0) {
                Text(
                    text = stringResource(R.string.department_stats_fh, formatCount(row.antennasFh)),
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun ratioOf(numerator: Number?, denominator: Number?): Double? {
    val top = numerator?.toDouble() ?: return null
    val bottom = denominator?.toDouble() ?: return null
    if (bottom == 0.0) return null
    return top / bottom
}

@Composable
private fun StatLine(label: String, value: String) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = sizing.spacing(3.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DepartmentOperatorTechCard(
    rows: List<DepartmentOperatorTechRow>,
    showActiveOnly: Boolean,
    cardColor: Color,
    cardShape: Shape
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val tableScrollState = rememberScrollState()
    val defaultOperatorKey = OperatorColors.keyFor(AppConfig.defaultOperator.value)
    val operators = rows.map { it.operatorName }
        .distinct()
        .filter { it != ITEM_ALL }
        .sortedWith(compareBy({ operatorSortIndex(it, defaultOperatorKey) }, { it }))
    if (operators.isEmpty()) return

    fun cell(operator: String, tech: String): DepartmentOperatorTechRow? =
        rows.firstOrNull { it.operatorName == operator && it.tech == tech }

    fun stationsOf(operator: String, tech: String): Int {
        val cellRow = cell(operator, tech) ?: return 0
        return if (showActiveOnly) cellRow.stationsActive else cellRow.stations
    }

    fun panelsOf(operator: String): Int {
        val cellRow = cell(operator, ITEM_ALL) ?: return 0
        return if (showActiveOnly) cellRow.antennasActive else cellRow.antennas
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = cardShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(sizing.spacing(16.dp))) {
            Text(
                text = stringResource(R.string.department_stats_matrix_title),
                style = sizing.textStyle(MaterialTheme.typography.titleSmall),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(sizing.spacing(10.dp)))

            Column(modifier = Modifier.horizontalScroll(tableScrollState)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TableHeaderCell(
                        text = stringResource(R.string.department_stats_matrix_operator),
                        width = sizing.component(132.dp),
                        alignEnd = false
                    )
                    TECH_COLUMNS.forEach { tech ->
                        TableHeaderCell(text = tech, width = sizing.component(56.dp))
                    }
                    TableHeaderCell(
                        text = stringResource(R.string.department_stats_matrix_total),
                        width = sizing.component(64.dp)
                    )
                    TableHeaderCell(
                        text = stringResource(R.string.department_stats_matrix_panels),
                        width = sizing.component(76.dp)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = sizing.spacing(4.dp)))

                operators.forEach { operator ->
                    val perTech = TECH_COLUMNS.map { stationsOf(operator, it) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = sizing.spacing(3.dp))
                    ) {
                        Row(
                            modifier = Modifier.width(sizing.component(132.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(sizing.component(10.dp))
                                    .clip(CircleShape)
                                    .background(operatorColor(operator))
                            )
                            Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                            Text(
                                text = operatorLabel(operator),
                                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        perTech.forEach { value ->
                            TableValueCell(
                                text = if (value > 0) formatCount(value) else "—",
                                width = sizing.component(56.dp)
                            )
                        }
                        TableValueCell(
                            text = formatCount(perTech.sum()),
                            width = sizing.component(64.dp),
                            bold = true
                        )
                        TableValueCell(
                            text = formatCount(panelsOf(operator)),
                            width = sizing.component(76.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(sizing.spacing(10.dp)))
            Text(
                text = stringResource(R.string.department_stats_matrix_legend),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TableHeaderCell(text: String, width: Dp, alignEnd: Boolean = true) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Text(
        text = text,
        style = sizing.textStyle(MaterialTheme.typography.labelMedium),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width)
    )
}

@Composable
private fun TableValueCell(text: String, width: Dp, bold: Boolean = false) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Text(
        text = text,
        style = sizing.textStyle(MaterialTheme.typography.bodySmall),
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.width(width)
    )
}
