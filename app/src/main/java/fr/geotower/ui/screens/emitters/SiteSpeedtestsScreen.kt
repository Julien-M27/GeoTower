package fr.geotower.ui.screens.emitters

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import fr.geotower.R
import fr.geotower.data.api.SignalQuestClient
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.ui.components.SecureScreenEffect
import fr.geotower.data.api.SignalQuestPlmnFilter
import fr.geotower.data.api.SignalQuestSpeedtestSortMetric
import fr.geotower.data.api.SqSpeedtestData
import fr.geotower.data.api.filterBySignalQuestPlmn
import fr.geotower.data.api.sortedBySignalQuestMetric
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.GeoTowerPullToRefreshBox
import fr.geotower.ui.components.geoTowerLazyListFadingEdge
import fr.geotower.ui.components.oneUiActionButtonShape
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.screens.settings.SiteSpeedtestsPagePreferences
import fr.geotower.ui.screens.settings.SiteSpeedtestsSettingsSheet
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import fr.geotower.utils.LocalizedDateLabels
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.OperatorLogos
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG_SITE_SPEEDTESTS = "GeoTowerUpload"
private const val SPEEDTESTS_PAGE_SIZE = 100

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SiteSpeedtestsScreen(
    navController: NavController,
    siteId: String?,
    anfrCode: String?,
    operator: String?,
    market: String = "FR",
    mcc: Int? = null,
    mnc: Int? = null
) {
    SecureScreenEffect(RemoteFeatureFlags.SecureScreens.SITE_SPEEDTESTS)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE) }
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val useOneUi = AppConfig.useOneUiDesign
    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)
    val mainBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.background
    val cardBgColor = if (useOneUi) {
        if (isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val blockShape = if (useOneUi) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)
    val buttonShape = oneUiActionButtonShape(useOneUi)
    val speedtestsErrorMessage = stringResource(R.string.appstrings_speedtests_error)

    val cleanSiteId = remember(siteId) { siteId?.trim()?.takeIf { it.isNotEmpty() } }
    val cleanAnfrCode = remember(anfrCode) { anfrCode?.trim()?.takeIf { it.isNotEmpty() } }
    val cleanOperator = remember(operator) { operator?.trim()?.takeIf { it.isNotEmpty() } }
    val cleanMarket = remember(market) { market.trim().ifEmpty { "FR" } }
    val requestMcc = remember(mcc, mnc) { mcc.takeIf { mnc != null } }
    val requestMnc = mnc
    val requestPlmn = remember(requestMcc, requestMnc) {
        requestMnc?.let { SignalQuestPlmnFilter(mcc = requestMcc, mnc = it) }
    }
    val requestOperator = remember(cleanOperator, requestMnc) { cleanOperator.takeIf { requestMnc == null } }
    val fallbackRoute = cleanAnfrCode?.let { "site_detail/$it" } ?: "emitters"
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = fallbackRoute)

    fun handleBackNavigation() {
        safeBackNavigation.navigateBack()
    }

    BackHandler(enabled = !safeBackNavigation.isLocked) {
        handleBackNavigation()
    }

    var showSettingsSheet by remember { mutableStateOf(false) }
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filterMajorEnb by rememberSaveable {
        mutableStateOf(prefs.getBoolean(SiteSpeedtestsPagePreferences.FILTER_MAJOR_ENB, SiteSpeedtestsPagePreferences.DEFAULT_FILTER_MAJOR_ENB))
    }
    var includeMissingEnb by rememberSaveable {
        mutableStateOf(prefs.getBoolean(SiteSpeedtestsPagePreferences.INCLUDE_MISSING_ENB, SiteSpeedtestsPagePreferences.DEFAULT_INCLUDE_MISSING_ENB))
    }
    var showSpeedtestsCount by rememberSaveable {
        mutableStateOf(prefs.getBoolean(SiteSpeedtestsPagePreferences.SHOW_COUNT, SiteSpeedtestsPagePreferences.DEFAULT_SHOW_COUNT))
    }
    var showRadioDetails by rememberSaveable {
        mutableStateOf(prefs.getBoolean(SiteSpeedtestsPagePreferences.SHOW_RADIO, SiteSpeedtestsPagePreferences.DEFAULT_SHOW_RADIO))
    }
    var showNetworkDetails by rememberSaveable {
        mutableStateOf(prefs.getBoolean(SiteSpeedtestsPagePreferences.SHOW_NETWORK, SiteSpeedtestsPagePreferences.DEFAULT_SHOW_NETWORK))
    }
    var showCoordinates by rememberSaveable {
        mutableStateOf(prefs.getBoolean(SiteSpeedtestsPagePreferences.SHOW_COORDINATES, SiteSpeedtestsPagePreferences.DEFAULT_SHOW_COORDINATES))
    }
    var bestMetric by rememberSaveable {
        mutableStateOf(
            SiteSpeedtestsPagePreferences.normalizeSortMetric(
                prefs.getString(SiteSpeedtestsPagePreferences.BEST_METRIC, SiteSpeedtestsPagePreferences.DEFAULT_BEST_METRIC)
            )
        )
    }
    var sortMetric by rememberSaveable {
        mutableStateOf(
            SiteSpeedtestsPagePreferences.normalizeSortMetric(
                prefs.getString(SiteSpeedtestsPagePreferences.SORT_METRIC, SiteSpeedtestsPagePreferences.DEFAULT_SORT_METRIC)
            )
        )
    }
    var sortDescending by rememberSaveable {
        mutableStateOf(prefs.getBoolean(SiteSpeedtestsPagePreferences.SORT_DESCENDING, SiteSpeedtestsPagePreferences.DEFAULT_SORT_DESCENDING))
    }

    fun updateSpeedtestsPreference(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun updateSpeedtestsStringPreference(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun resetSpeedtestsPreferences() {
        SiteSpeedtestsPagePreferences.reset(prefs)
        filterMajorEnb = SiteSpeedtestsPagePreferences.DEFAULT_FILTER_MAJOR_ENB
        includeMissingEnb = SiteSpeedtestsPagePreferences.DEFAULT_INCLUDE_MISSING_ENB
        showSpeedtestsCount = SiteSpeedtestsPagePreferences.DEFAULT_SHOW_COUNT
        showRadioDetails = SiteSpeedtestsPagePreferences.DEFAULT_SHOW_RADIO
        showNetworkDetails = SiteSpeedtestsPagePreferences.DEFAULT_SHOW_NETWORK
        showCoordinates = SiteSpeedtestsPagePreferences.DEFAULT_SHOW_COORDINATES
        bestMetric = SiteSpeedtestsPagePreferences.DEFAULT_BEST_METRIC
        sortMetric = SiteSpeedtestsPagePreferences.DEFAULT_SORT_METRIC
        sortDescending = SiteSpeedtestsPagePreferences.DEFAULT_SORT_DESCENDING
    }

    var speedtests by remember(cleanSiteId, cleanAnfrCode, cleanOperator, cleanMarket, mcc, mnc) {
        mutableStateOf<List<SqSpeedtestData>>(emptyList())
    }
    var totalCount by remember(cleanSiteId, cleanAnfrCode, cleanOperator, cleanMarket, mcc, mnc) {
        mutableStateOf<Int?>(null)
    }
    var nextOffset by remember(cleanSiteId, cleanAnfrCode, cleanOperator, cleanMarket, mcc, mnc) {
        mutableIntStateOf(0)
    }
    var lastRawPageSize by remember(cleanSiteId, cleanAnfrCode, cleanOperator, cleanMarket, mcc, mnc) {
        mutableIntStateOf(0)
    }
    var isInitialLoading by remember(cleanSiteId, cleanAnfrCode, cleanOperator, cleanMarket, mcc, mnc) {
        mutableStateOf(true)
    }
    var isRefreshing by remember(cleanSiteId, cleanAnfrCode, cleanOperator, cleanMarket, mcc, mnc) {
        mutableStateOf(false)
    }
    var isLoadingMore by remember(cleanSiteId, cleanAnfrCode, cleanOperator, cleanMarket, mcc, mnc) {
        mutableStateOf(false)
    }
    var errorMessage by remember(cleanSiteId, cleanAnfrCode, cleanOperator, cleanMarket, mcc, mnc) {
        mutableStateOf<String?>(null)
    }
    var reloadNonce by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    suspend fun loadSpeedtests(reset: Boolean, keepPreviousItems: Boolean = false) {
        if (cleanSiteId == null && cleanAnfrCode == null) {
            errorMessage = speedtestsErrorMessage
            isInitialLoading = false
            isRefreshing = false
            isLoadingMore = false
            return
        }

        if (reset) {
            if (keepPreviousItems) {
                isRefreshing = true
            } else {
                isInitialLoading = true
                speedtests = emptyList()
            }
            if (!keepPreviousItems) {
                totalCount = null
                nextOffset = 0
                lastRawPageSize = 0
            }
        } else {
            isLoadingMore = true
        }
        errorMessage = null

        val offset = if (reset) 0 else nextOffset
        try {
            val response = withContext(Dispatchers.IO) {
                SignalQuestClient.api.getSiteSpeedtests(
                    siteId = cleanSiteId,
                    anfrCode = cleanAnfrCode,
                    nationalSiteCode = cleanAnfrCode,
                    sourceCode = cleanAnfrCode,
                    operator = requestOperator,
                    mcc = requestMcc,
                    mnc = requestMnc,
                    market = cleanMarket,
                    bestOnly = false,
                    limit = SPEEDTESTS_PAGE_SIZE,
                    offset = offset
                )
            }

            if (response.isSuccessful) {
                val body = response.body()
                val rawPage = body?.data.orEmpty()
                val page = rawPage.filterBySignalQuestPlmn(requestPlmn)
                speedtests = if (reset) page else speedtests + page
                totalCount = body?.meta?.total
                nextOffset = offset + rawPage.size
                lastRawPageSize = rawPage.size
            } else {
                response.errorBody()?.close()
                AppLogger.w(TAG_SITE_SPEEDTESTS, "SignalQuest speedtests list failure code=${response.code()}")
                errorMessage = speedtestsErrorMessage
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            AppLogger.w(TAG_SITE_SPEEDTESTS, "SignalQuest speedtests network failure", e)
            errorMessage = speedtestsErrorMessage
        } catch (e: Exception) {
            AppLogger.w(TAG_SITE_SPEEDTESTS, "SignalQuest speedtests request failed", e)
            errorMessage = speedtestsErrorMessage
        } finally {
            isInitialLoading = false
            isRefreshing = false
            isLoadingMore = false
        }
    }

    LaunchedEffect(cleanSiteId, cleanAnfrCode, cleanOperator, cleanMarket, mcc, mnc, reloadNonce) {
        loadSpeedtests(reset = true)
    }

    val hasMore = lastRawPageSize >= SPEEDTESTS_PAGE_SIZE &&
        (totalCount?.let { nextOffset < it } ?: true)
    val displayedSpeedtests = remember(speedtests, filterMajorEnb, includeMissingEnb, sortMetric, sortDescending) {
        speedtests.filteredByMajorityEnb(
            enabled = filterMajorEnb,
            includeMissingEnb = includeMissingEnb
        ).sortedBySignalQuestMetric(
            metric = SignalQuestSpeedtestSortMetric.fromStorageKey(sortMetric),
            descending = sortDescending
        )
    }

    Scaffold(
        containerColor = mainBgColor,
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.appstrings_speedtests_all_title),
                onBack = { handleBackNavigation() },
                backgroundColor = mainBgColor,
                contentColor = MaterialTheme.colorScheme.onSurface,
                backEnabled = !safeBackNavigation.isLocked,
                actions = {
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.appstrings_settings_title),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        GeoTowerPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (!isRefreshing && !isInitialLoading) {
                    scope.launch {
                        loadSpeedtests(reset = true, keepPreviousItems = true)
                    }
                }
            },
            enabled = !isInitialLoading && !isLoadingMore,
            modifier = Modifier
                .fillMaxSize()
                .background(mainBgColor)
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .geoTowerLazyListFadingEdge(listState),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            item {
                SiteSpeedtestsBanner(
                    operator = cleanOperator,
                    siteId = cleanSiteId,
                    anfrCode = cleanAnfrCode,
                    speedtestCountText = if (showSpeedtestsCount) {
                        speedtestsCountText(
                            displayedCount = displayedSpeedtests.size,
                            loadedCount = speedtests.size,
                            totalCount = totalCount
                        )
                    } else {
                        null
                    },
                    bgColor = cardBgColor,
                    shape = blockShape,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            }

            item {
                SpeedtestsSortModeSelector(
                    sortMetric = sortMetric,
                    onSortMetricChange = {
                        val normalizedMetric = SiteSpeedtestsPagePreferences.normalizeSortMetric(it)
                        sortMetric = normalizedMetric
                        updateSpeedtestsStringPreference(SiteSpeedtestsPagePreferences.SORT_METRIC, normalizedMetric)
                    },
                    bgColor = cardBgColor
                )
            }

            when {
                isInitialLoading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator(modifier = Modifier.size(40.dp))
                        }
                    }
                }

                errorMessage != null && speedtests.isEmpty() -> {
                    item {
                        SpeedtestsMessageCard(
                            text = errorMessage.orEmpty(),
                            bgColor = cardBgColor,
                            onRetry = { reloadNonce++ },
                            buttonShape = buttonShape
                        )
                    }
                }

                speedtests.isEmpty() || displayedSpeedtests.isEmpty() -> {
                    item {
                        SpeedtestsMessageCard(
                            text = stringResource(R.string.appstrings_speedtest_no_data),
                            bgColor = cardBgColor,
                            onRetry = null,
                            buttonShape = buttonShape
                        )
                    }
                }

                else -> {
                    itemsIndexed(
                        items = displayedSpeedtests,
                        key = { index, item -> item.id ?: "${item.timestamp.orEmpty()}_$index" }
                    ) { _, speedtest ->
                        SpeedtestListCard(
                            speedtest = speedtest,
                            bgColor = cardBgColor,
                            shape = blockShape,
                            showRadioDetails = showRadioDetails,
                            showNetworkDetails = showNetworkDetails,
                            showCoordinates = showCoordinates
                        )
                    }

                    if (hasMore) {
                        item {
                            Button(
                                onClick = { scope.launch { loadSpeedtests(reset = false) } },
                                enabled = !isLoadingMore,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = buttonShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                if (isLoadingMore) {
                                    LoadingIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    Text(stringResource(R.string.appstrings_speedtests_load_more), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }

    if (showSettingsSheet) {
        SiteSpeedtestsSettingsSheet(
            filterMajorEnb = filterMajorEnb,
            onFilterMajorEnbChange = {
                filterMajorEnb = it
                updateSpeedtestsPreference(SiteSpeedtestsPagePreferences.FILTER_MAJOR_ENB, it)
            },
            includeMissingEnb = includeMissingEnb,
            onIncludeMissingEnbChange = {
                includeMissingEnb = it
                updateSpeedtestsPreference(SiteSpeedtestsPagePreferences.INCLUDE_MISSING_ENB, it)
            },
            showSpeedtestsCount = showSpeedtestsCount,
            onShowSpeedtestsCountChange = {
                showSpeedtestsCount = it
                updateSpeedtestsPreference(SiteSpeedtestsPagePreferences.SHOW_COUNT, it)
            },
            showRadioDetails = showRadioDetails,
            onShowRadioDetailsChange = {
                showRadioDetails = it
                updateSpeedtestsPreference(SiteSpeedtestsPagePreferences.SHOW_RADIO, it)
            },
            showNetworkDetails = showNetworkDetails,
            onShowNetworkDetailsChange = {
                showNetworkDetails = it
                updateSpeedtestsPreference(SiteSpeedtestsPagePreferences.SHOW_NETWORK, it)
            },
            showCoordinates = showCoordinates,
            onShowCoordinatesChange = {
                showCoordinates = it
                updateSpeedtestsPreference(SiteSpeedtestsPagePreferences.SHOW_COORDINATES, it)
            },
            bestMetric = bestMetric,
            onBestMetricChange = {
                val normalizedMetric = SiteSpeedtestsPagePreferences.normalizeSortMetric(it)
                bestMetric = normalizedMetric
                updateSpeedtestsStringPreference(SiteSpeedtestsPagePreferences.BEST_METRIC, normalizedMetric)
            },
            sortMetric = sortMetric,
            onSortMetricChange = {
                val normalizedMetric = SiteSpeedtestsPagePreferences.normalizeSortMetric(it)
                sortMetric = normalizedMetric
                updateSpeedtestsStringPreference(SiteSpeedtestsPagePreferences.SORT_METRIC, normalizedMetric)
            },
            sortDescending = sortDescending,
            onSortDescendingChange = {
                sortDescending = it
                updateSpeedtestsPreference(SiteSpeedtestsPagePreferences.SORT_DESCENDING, it)
            },
            onReset = { resetSpeedtestsPreferences() },
            onBack = { showSettingsSheet = false },
            sheetState = settingsSheetState,
            useOneUi = useOneUi,
            bubbleColor = cardBgColor,
            onDismiss = { showSettingsSheet = false }
        )
    }
}

@Composable
private fun SiteSpeedtestsBanner(
    operator: String?,
    siteId: String?,
    anfrCode: String?,
    speedtestCountText: String?,
    bgColor: Color,
    shape: Shape,
    contentColor: Color
) {
    val operatorName = speedtestsOperatorDisplayName(operator, stringResource(R.string.appstrings_unknown))
    val logoRes = OperatorLogos.drawableRes(operator)
    val supportValue = siteId ?: stringResource(R.string.appstrings_unavailable)
    val anfrValue = anfrCode ?: stringResource(R.string.appstrings_unavailable)

    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = bgColor, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (logoRes != null) {
                Image(
                    painter = painterResource(id = logoRes),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(speedtestsOperatorColor(operatorName), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = operatorName.trim().take(1).uppercase().ifBlank { "?" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(18.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = operatorName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${stringResource(R.string.appstrings_speedtests_support_label)} : $supportValue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.74f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${stringResource(R.string.appstrings_speedtests_anfr_label)} : $anfrValue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.74f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                speedtestCountText?.let { count ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = count,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SiteSpeedtestsHeader(
    siteId: String?,
    anfrCode: String?,
    operator: String?,
    market: String,
    totalCount: Int?,
    loadedCount: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val identifiers = buildList {
            siteId?.let { add("Site $it") }
            anfrCode?.let { add("ANFR $it") }
        }.joinToString("  •  ")
        val contextLine = buildList {
            operator?.let { add(it) }
            add(market.uppercase(Locale.US))
            if (identifiers.isNotBlank()) add(identifiers)
        }.joinToString("  •  ")

        Text(
            text = contextLine,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (loadedCount > 0) {
            val countText = totalCount?.let { "$loadedCount / $it" } ?: loadedCount.toString()
            Text(
                text = countText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun speedtestsOperatorDisplayName(operator: String?, unknownText: String): String {
    val cleanOperator = operator?.trim()?.takeIf { it.isNotEmpty() } ?: return unknownText
    return OperatorColors.keyFor(cleanOperator) ?: cleanOperator
}

private fun speedtestsOperatorColor(operator: String): Color {
    return OperatorColors.keyFor(operator)
        ?.let { Color(OperatorColors.colorArgbForKey(it)) }
        ?: Color.Gray
}

@Composable
private fun SpeedtestsSortModeSelector(
    sortMetric: String,
    onSortMetricChange: (String) -> Unit,
    bgColor: Color
) {
    val chipScrollState = rememberScrollState()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor.copy(alpha = 0.58f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.appstrings_speedtests_sort_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(chipScrollState)
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpeedtestsSortModeChip(
                    label = stringResource(R.string.appstrings_speedtests_sort_average),
                    selected = sortMetric == SiteSpeedtestsPagePreferences.SORT_AVERAGE,
                    onClick = { onSortMetricChange(SiteSpeedtestsPagePreferences.SORT_AVERAGE) }
                )
                SpeedtestsSortModeChip(
                    label = stringResource(R.string.appstrings_speedtests_sort_max),
                    selected = sortMetric == SiteSpeedtestsPagePreferences.SORT_MAX,
                    onClick = { onSortMetricChange(SiteSpeedtestsPagePreferences.SORT_MAX) }
                )
                SpeedtestsSortModeChip(
                    label = stringResource(R.string.appstrings_speedtests_sort_download),
                    selected = sortMetric == SiteSpeedtestsPagePreferences.SORT_DOWNLOAD,
                    onClick = { onSortMetricChange(SiteSpeedtestsPagePreferences.SORT_DOWNLOAD) }
                )
            }
        }
    }
}

@Composable
private fun SpeedtestsSortModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.22f)
    }
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.clip(shape)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            softWrap = false
        )
    }
}

private fun List<SqSpeedtestData>.filteredByMajorityEnb(
    enabled: Boolean,
    includeMissingEnb: Boolean
): List<SqSpeedtestData> {
    if (!enabled) return this
    val majorityEnb = majorityEnbOrNull() ?: return this
    return filter { speedtest ->
        val enb = speedtest.radio?.enb?.trim()
        enb == majorityEnb || (includeMissingEnb && enb.isNullOrBlank())
    }
}

private fun List<SqSpeedtestData>.majorityEnbOrNull(): String? {
    val counts = mapNotNull { it.radio?.enb?.trim()?.takeIf(String::isNotBlank) }
        .groupingBy { it }
        .eachCount()
    val maxCount = counts.values.maxOrNull() ?: return null
    val topEnbs = counts.filterValues { it == maxCount }.keys
    return topEnbs.singleOrNull()
}

private fun speedtestsCountText(
    displayedCount: Int,
    loadedCount: Int,
    totalCount: Int?
): String {
    return when {
        displayedCount != loadedCount -> "$displayedCount / $loadedCount speedtests"
        totalCount != null && totalCount != loadedCount -> "$loadedCount / $totalCount speedtests"
        else -> "$loadedCount speedtests"
    }
}

@Composable
private fun SpeedtestsMessageCard(
    text: String,
    bgColor: Color,
    onRetry: (() -> Unit)?,
    buttonShape: androidx.compose.ui.graphics.Shape
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = text,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onRetry != null) {
                Button(
                    onClick = onRetry,
                    shape = buttonShape
                ) {
                    Text(stringResource(R.string.appstrings_retry))
                }
            }
        }
    }
}

@Composable
private fun SpeedtestListCard(
    speedtest: SqSpeedtestData,
    bgColor: Color,
    shape: androidx.compose.ui.graphics.Shape,
    showRadioDetails: Boolean,
    showNetworkDetails: Boolean,
    showCoordinates: Boolean
) {
    val context = LocalContext.current
    val contentColor = MaterialTheme.colorScheme.onSurface
    val dateText = remember(speedtest.timestamp) { formatSpeedtestTimestamp(context, speedtest.timestamp) }
    val networkLine = remember(speedtest, showNetworkDetails) {
        if (showNetworkDetails) speedtest.networkLine() else null
    }
    val radioLine = remember(speedtest, showRadioDetails) {
        if (showRadioDetails) speedtest.radioLine() else null
    }
    val coordinatesLine = remember(speedtest.coordinates?.lat, speedtest.coordinates?.lng, showCoordinates) {
        if (showCoordinates) {
            speedtest.coordinates?.let { coordinates ->
                val lat = coordinates.lat
                val lng = coordinates.lng
                if (lat != null && lng != null) {
                    String.format(Locale.US, "%.5f, %.5f", lat, lng)
                } else {
                    null
                }
            }
        } else {
            null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = bgColor, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateText ?: speedtest.id.orEmpty().ifBlank { "--" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                speedtest.connectionType?.takeIf { it.isNotBlank() }?.let { connection ->
                    Text(
                        text = connection,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SpeedtestMetric(
                    icon = Icons.Default.KeyboardArrowDown,
                    label = stringResource(R.string.appstrings_speedtest_download),
                    value = speedValue(speedtest.downloadSpeed),
                    unit = "Mbps",
                    color = Color(0xFF4CAF50)
                )
                SpeedtestMetric(
                    icon = Icons.Default.KeyboardArrowUp,
                    label = stringResource(R.string.appstrings_speedtest_upload),
                    value = speedValue(speedtest.uploadSpeed),
                    unit = "Mbps",
                    color = Color(0xFF2196F3)
                )
                SpeedtestMetric(
                    icon = Icons.Default.Timer,
                    label = stringResource(R.string.appstrings_speedtest_ping),
                    value = speedtest.ping?.toInt()?.toString() ?: "--",
                    unit = "ms",
                    color = Color(0xFFFF9800)
                )
            }

            speedtest.averageSpeed?.let {
                SpeedtestDetailLine(
                    label = stringResource(R.string.appstrings_speedtests_average),
                    value = "${speedValue(it)} Mbps"
                )
            }
            speedtest.maxSpeed?.let {
                SpeedtestDetailLine(
                    label = stringResource(R.string.appstrings_speedtests_max),
                    value = "${speedValue(it)} Mbps"
                )
            }

            if (networkLine != null || radioLine != null || coordinatesLine != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            }

            networkLine?.let {
                SpeedtestDetailLine(
                    label = stringResource(R.string.appstrings_speedtests_network),
                    value = it
                )
            }
            radioLine?.let {
                SpeedtestDetailLine(
                    label = stringResource(R.string.appstrings_speedtests_radio),
                    value = it
                )
            }
            coordinatesLine?.let {
                SpeedtestDetailLine(
                    label = stringResource(R.string.car_coordinates),
                    value = it
                )
            }
        }
    }
}

@Composable
private fun SpeedtestMetric(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(26.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(text = unit, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SpeedtestDetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun speedValue(value: Float?): String {
    return value?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
}

private fun formatSpeedtestTimestamp(context: android.content.Context, timestamp: String?): String? {
    if (timestamp.isNullOrBlank()) return null
    val dateStr = timestamp.take(10)
    val parts = dateStr.split("-")
    if (parts.size != 3) return timestamp
    val year = parts[0]
    val month = parts[1]
    val day = parts[2].toIntOrNull()?.toString() ?: parts[2]
    val monthName = LocalizedDateLabels.monthName(context, month)
    return if (monthName.isNotEmpty()) "$day $monthName $year" else dateStr
}

private fun SqSpeedtestData.networkLine(): String? {
    return listOfNotNull(
        speedtestNetworkValue(mobileOperator),
        speedtestNetworkValue(networkType),
        speedtestNetworkValue(deviceType),
        mcc?.let { mnc?.let { pairedMnc -> "$it/$pairedMnc" } }
    )
        .distinct()
        .takeIf { it.isNotEmpty() }
        ?.joinToString("  •  ")
}

private fun speedtestNetworkValue(value: String?): String? {
    val cleanValue = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return cleanValue.takeUnless {
        it.equals("CELLULAR", ignoreCase = true) ||
            it.equals("Android", ignoreCase = true)
    }
}

private fun SqSpeedtestData.radioLine(): String? {
    val radioData = radio ?: return null
    return buildList {
        radioData.enb?.takeIf { it.isNotBlank() }?.let { add("eNB $it") }
        radioData.gnb?.takeIf { it.isNotBlank() }?.let { add("gNB $it") }
        radioData.cellId?.takeIf { it.isNotBlank() }?.let { add("Cell $it") }
        radioData.pci?.let { add("PCI $it") }
        radioData.rsrp?.let { add("RSRP ${signalValue(it)} dBm") }
        radioData.rsrq?.let { add("RSRQ ${signalValue(it)} dB") }
        radioData.snr?.let { add("SNR ${signalValue(it)} dB") }
    }
        .takeIf { it.isNotEmpty() }
        ?.joinToString("  •  ")
}

private fun signalValue(value: Float): String {
    return if (value % 1f == 0f) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
}
