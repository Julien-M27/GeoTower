package fr.geotower.ui.screens.emitters

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import fr.geotower.data.AnfrRepository
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.PageCustomizationFooter
import fr.geotower.ui.components.PageCustomizationHint
import fr.geotower.ui.components.PageScrollEdgeButtons
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.components.geoTowerLazyListFadingEdge
import fr.geotower.ui.components.pageScrollbar
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.screens.settings.SiteComparatorSettingsSheet
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.LocationHelper
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.OperatorLogos
import fr.geotower.utils.PageScrollPrefs
import fr.geotower.utils.ScreenPageTarget
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ComparatorSlot { A, B }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteComparatorScreen(
    navController: NavController,
    repository: AnfrRepository,
    initialSiteAId: String? = null,
    initialSiteBId: String? = null
) {
    val context = LocalContext.current
    val safeBack = rememberSafeBackNavigation(navController)
    val scope = rememberCoroutineScope()

    var siteAId by rememberSaveable(initialSiteAId) { mutableStateOf(initialSiteAId?.takeIf { it.isNotBlank() }) }
    var siteBId by rememberSaveable(initialSiteBId) { mutableStateOf(initialSiteBId?.takeIf { it.isNotBlank() }) }

    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val pickedSiteId by (savedStateHandle
        ?.getStateFlow<String?>("comparator_picked_site_id", null)
        ?.collectAsState()
        ?: remember { mutableStateOf(null) })
    val pickedSlot by (savedStateHandle
        ?.getStateFlow<String?>("comparator_picked_slot", null)
        ?.collectAsState()
        ?: remember { mutableStateOf(null) })
    LaunchedEffect(pickedSiteId, pickedSlot) {
        val id = pickedSiteId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (pickedSlot == "A") {
            siteAId = id
        } else {
            siteBId = id
        }
        savedStateHandle?.remove<String>("comparator_picked_site_id")
        savedStateHandle?.remove<String>("comparator_picked_slot")
    }

    var summaryA by remember { mutableStateOf<ComparableSiteSummary?>(null) }
    var summaryB by remember { mutableStateOf<ComparableSiteSummary?>(null) }
    var loadingA by remember { mutableStateOf(false) }
    var loadingB by remember { mutableStateOf(false) }

    var activePickerSlot by remember { mutableStateOf<ComparatorSlot?>(null) }
    var deviceLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var showComparatorSettings by remember { mutableStateOf(false) }
    val comparatorSettingsSheetState = rememberModalBottomSheetState()
    val uiStyle = LocalGeoTowerUiStyle.current

    LaunchedEffect(Unit) {
        val loc = withContext(Dispatchers.IO) {
            runCatching { LocationHelper(context).getLastLocation() }.getOrNull()
        }
        if (loc != null) {
            deviceLocation = loc.latitude to loc.longitude
        }
    }

    LaunchedEffect(siteAId) {
        val targetId = siteAId
        if (targetId == null) {
            summaryA = null
            loadingA = false
            return@LaunchedEffect
        }
        loadingA = true
        summaryA = loadComparableSiteSummary(repository, targetId)
        loadingA = false
    }

    LaunchedEffect(siteBId) {
        val targetId = siteBId
        if (targetId == null) {
            summaryB = null
            loadingB = false
            return@LaunchedEffect
        }
        loadingB = true
        summaryB = loadComparableSiteSummary(repository, targetId)
        loadingB = false
    }

    Scaffold(
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.site_comparator_title),
                onBack = { safeBack.navigateBack() },
                backEnabled = !safeBack.isLocked,
                actions = {
                    if (siteAId != null || siteBId != null) {
                        IconButton(
                            onClick = {
                                val prevA = siteAId
                                siteAId = siteBId
                                siteBId = prevA
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = stringResource(R.string.site_comparator_swap)
                            )
                        }
                    }
                    PageCustomizationHint(
                        page = PageScrollPrefs.SITE_COMPARATOR,
                        onOpenSettings = { showComparatorSettings = true }
                    ) {
                        IconButton(onClick = { showComparatorSettings = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings_pages_customization_title),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .geoTowerFadingEdge(scrollState)
                .pageScrollbar(PageScrollPrefs.SITE_COMPARATOR, scrollState)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Sélecteurs côte à côte Site A / Site B
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SiteSlotCard(
                    slotLabel = "A",
                    accentColor = MaterialTheme.colorScheme.primary,
                    summary = summaryA,
                    isLoading = loadingA,
                    modifier = Modifier.weight(1f),
                    onClick = { activePickerSlot = ComparatorSlot.A }
                )
                SiteSlotCard(
                    slotLabel = "B",
                    accentColor = MaterialTheme.colorScheme.secondary,
                    summary = summaryB,
                    isLoading = loadingB,
                    modifier = Modifier.weight(1f),
                    onClick = { activePickerSlot = ComparatorSlot.B }
                )
            }

            val a = summaryA
            val b = summaryB

            if (a == null && b == null && !loadingA && !loadingB) {
                EmptyComparatorPrompt(
                    onPickSiteA = { activePickerSlot = ComparatorSlot.A }
                )
            } else if (a == null || b == null) {
                val missingSlot = if (a == null) "A" else "B"
                val refSite = if (a == null) b else a
                SingleSitePrompt(
                    missingSlot = missingSlot,
                    onPickMissing = {
                        val focusParams = refSite?.let {
                            "&focusLat=${it.latitude}&focusLon=${it.longitude}"
                        }.orEmpty()
                        navController.navigate("map?compareSlot=$missingSlot$focusParams") {
                            launchSingleTop = true
                        }
                    }
                )
            } else {
                // Comparaison complète des deux sites
                DistanceAndBearingCard(siteA = a, siteB = b)

                SupportAndStatusCard(siteA = a, siteB = b)

                TechnologiesAndBandsCard(siteA = a, siteB = b)

                AzimuthAndFhCard(siteA = a, siteB = b)

                DatesComparisonCard(siteA = a, siteB = b)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            context.getSharedPreferences(fr.geotower.utils.PreferenceStores.APP, android.content.Context.MODE_PRIVATE)
                                .edit()
                                .putFloat("clicked_lat", a.latitude.toFloat())
                                .putFloat("clicked_lon", a.longitude.toFloat())
                                .apply()
                            navController.navigate("site_detail/${a.idAnfr}")
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.site_comparator_open_a),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            context.getSharedPreferences(fr.geotower.utils.PreferenceStores.APP, android.content.Context.MODE_PRIVATE)
                                .edit()
                                .putFloat("clicked_lat", b.latitude.toFloat())
                                .putFloat("clicked_lon", b.longitude.toFloat())
                                .apply()
                            navController.navigate("site_detail/${b.idAnfr}")
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.site_comparator_open_b),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            PageCustomizationFooter(onClick = { showComparatorSettings = true })
            Spacer(modifier = Modifier.height(16.dp))
        }
        PageScrollEdgeButtons(PageScrollPrefs.SITE_COMPARATOR, scrollState)
        }
    }

    if (showComparatorSettings) {
        SiteComparatorSettingsSheet(
            onDismiss = { showComparatorSettings = false },
            sheetState = comparatorSettingsSheetState,
            useOneUi = uiStyle.useOneUi
        )
    }

    activePickerSlot?.let { slot ->
        val referenceSite = if (slot == ComparatorSlot.A) summaryB else summaryA
        SitePickerBottomSheet(
            repository = repository,
            referenceLat = referenceSite?.latitude ?: deviceLocation?.first,
            referenceLon = referenceSite?.longitude ?: deviceLocation?.second,
            excludedIdAnfr = referenceSite?.idAnfr,
            onDismiss = { activePickerSlot = null },
            onSelectSite = { selected ->
                if (slot == ComparatorSlot.A) {
                    siteAId = selected.idAnfr
                } else {
                    siteBId = selected.idAnfr
                }
                activePickerSlot = null
            }
        )
    }
}

private suspend fun loadComparableSiteSummary(
    repository: AnfrRepository,
    idAnfr: String
): ComparableSiteSummary? = withContext(Dispatchers.IO) {
    runCatching {
        val localisations = repository.getAntennasByExactId(idAnfr)
        val loc = localisations.firstOrNull() ?: return@withContext null
        val techDeferred = async { repository.getTechniqueDetails(idAnfr) }
        val physDeferred = async { repository.getPhysiqueDetails(idAnfr) }
        val fhDeferred = async { repository.getFaisceauxDetails(idAnfr) }
        buildComparableSiteSummary(
            localisation = loc,
            technique = techDeferred.await(),
            physique = physDeferred.await(),
            faisceaux = fhDeferred.await(),
            order = AppConfig.resolveOrderForScreen(ScreenPageTarget.SITE_DETAIL)
        )
    }.getOrNull()
}

@Composable
private fun SiteSlotCard(
    slotLabel: String,
    accentColor: Color,
    summary: ComparableSiteSummary?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val operatorColor = summary?.operatorName?.let { name ->
        OperatorColors.keyFor(name)?.let { Color(OperatorColors.colorArgbForKey(it)) }
    } ?: accentColor

    Card(
        modifier = modifier
            .height(122.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, operatorColor.copy(alpha = 0.65f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.Center),
                    strokeWidth = 2.5.dp
                )
            } else if (summary == null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = accentColor
                        )
                    }
                    Text(
                        text = stringResource(R.string.site_comparator_pick_site, slotLabel),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ComparatorOperatorLogo(
                            operatorName = summary.operatorName,
                            fallbackLabel = slotLabel,
                            fallbackColor = operatorColor,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = summary.operatorName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = slotLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = formatComparatorSiteIdLabel(summary.idAnfr),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = summary.address.ifBlank {
                            String.format(Locale.US, "%.4f, %.4f", summary.latitude, summary.longitude)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparatorOperatorLogo(
    operatorName: String?,
    fallbackLabel: String?,
    fallbackColor: Color,
    modifier: Modifier = Modifier
) {
    val logoRes = OperatorLogos.drawableRes(operatorName)
    if (logoRes != null) {
        Image(
            painter = painterResource(id = logoRes),
            contentDescription = operatorName,
            contentScale = ContentScale.Fit,
            modifier = modifier.clip(RoundedCornerShape(6.dp))
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .background(fallbackColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            if (!fallbackLabel.isNullOrBlank()) {
                Text(
                    text = fallbackLabel,
                    color = fallbackColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CellTower,
                    contentDescription = null,
                    tint = fallbackColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyComparatorPrompt(onPickSiteA: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = stringResource(R.string.site_comparator_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.site_comparator_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onPickSiteA,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.site_comparator_pick_site, "A"))
            }
        }
    }
}

@Composable
private fun SingleSitePrompt(
    missingSlot: String,
    onPickMissing: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.site_comparator_pick_second_title, missingSlot),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.site_comparator_pick_second_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onPickMissing,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors()
            ) {
                Text(stringResource(R.string.site_comparator_choose))
            }
        }
    }
}

@Composable
private fun DistanceAndBearingCard(
    siteA: ComparableSiteSummary,
    siteB: ComparableSiteSummary
) {
    val distanceMeters = computeDistanceMeters(
        siteA.latitude, siteA.longitude,
        siteB.latitude, siteB.longitude
    )
    val bearing = computeBearingDegrees(
        siteA.latitude, siteA.longitude,
        siteB.latitude, siteB.longitude
    )

    ComparisonSectionCard(title = stringResource(R.string.site_comparator_section_distance)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatComparatorDistance(distanceMeters),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.site_comparator_distance_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${bearing}°",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = stringResource(R.string.site_comparator_bearing_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SupportAndStatusCard(
    siteA: ComparableSiteSummary,
    siteB: ComparableSiteSummary
) {
    val heightA = siteA.maxSupportHeightMeters
    val heightB = siteB.maxSupportHeightMeters
    val tallerA = heightA != null && (heightB == null || heightA > heightB)
    val tallerB = heightB != null && (heightA == null || heightB > heightA)

    ComparisonSectionCard(title = stringResource(R.string.site_comparator_section_support)) {
        ComparisonRow(
            label = stringResource(R.string.site_comparator_status),
            valueA = siteA.status ?: "—",
            valueB = siteB.status ?: "—",
            highlightA = siteA.isActive && !siteB.isActive,
            highlightB = siteB.isActive && !siteA.isActive
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
        ComparisonRow(
            label = stringResource(R.string.site_comparator_height),
            valueA = heightA?.let { String.format(Locale.getDefault(), "%.1f m", it) } ?: "—",
            valueB = heightB?.let { String.format(Locale.getDefault(), "%.1f m", it) } ?: "—",
            highlightA = tallerA,
            highlightB = tallerB
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
        ComparisonRow(
            label = stringResource(R.string.site_comparator_support_nature),
            valueA = siteA.supportNature ?: "—",
            valueB = siteB.supportNature ?: "—"
        )
        val bothCanadian = siteA.idAnfr.startsWith("CA-", ignoreCase = true) &&
            siteB.idAnfr.startsWith("CA-", ignoreCase = true)
        if (!bothCanadian) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
            ComparisonRow(
                label = stringResource(R.string.site_comparator_support_owner),
                valueA = siteA.supportOwner ?: "—",
                valueB = siteB.supportOwner ?: "—"
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TechnologiesAndBandsCard(
    siteA: ComparableSiteSummary,
    siteB: ComparableSiteSummary
) {
    val detailOrder = AppConfig.resolveOrderForScreen(ScreenPageTarget.SITE_DETAIL)
    val bandComparison = remember(siteA.bandLabels, siteB.bandLabels, detailOrder) {
        compareSiteBands(siteA.bandLabels, siteB.bandLabels, detailOrder)
    }

    ComparisonSectionCard(title = stringResource(R.string.site_comparator_section_radio)) {
        ComparisonRow(
            label = stringResource(R.string.site_comparator_technologies),
            valueA = siteA.technologies.joinToString(" • ").ifBlank { "—" },
            valueB = siteB.technologies.joinToString(" • ").ifBlank { "—" }
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (bandComparison.common.isNotEmpty()) {
            Text(
                text = stringResource(R.string.site_comparator_bands_common),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                bandComparison.common.forEach { band ->
                    BandChip(text = band, containerColor = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (bandComparison.onlyA.isNotEmpty()) {
            Text(
                text = stringResource(R.string.site_comparator_bands_only, "A"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                bandComparison.onlyA.forEach { band ->
                    BandChip(
                        text = band,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (bandComparison.onlyB.isNotEmpty()) {
            Text(
                text = stringResource(R.string.site_comparator_bands_only, "B"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                bandComparison.onlyB.forEach { band ->
                    BandChip(
                        text = band,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun AzimuthAndFhCard(
    siteA: ComparableSiteSummary,
    siteB: ComparableSiteSummary
) {
    val bothCanadian = siteA.idAnfr.startsWith("CA-", ignoreCase = true) &&
        siteB.idAnfr.startsWith("CA-", ignoreCase = true)
    val title = if (bothCanadian) {
        stringResource(R.string.site_comparator_section_azimuths)
    } else {
        stringResource(R.string.site_comparator_section_azimuth_fh)
    }
    ComparisonSectionCard(title = title) {
        ComparisonRow(
            label = stringResource(R.string.site_comparator_azimuths),
            valueA = siteA.azimuths.takeIf { it.isNotEmpty() }?.joinToString("°, ", postfix = "°") ?: "—",
            valueB = siteB.azimuths.takeIf { it.isNotEmpty() }?.joinToString("°, ", postfix = "°") ?: "—"
        )
        if (!bothCanadian) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
            val yesText = stringResource(R.string.site_comparator_yes)
            val noText = stringResource(R.string.site_comparator_no)
            ComparisonRow(
                label = stringResource(R.string.site_comparator_fh_links),
                valueA = if (siteA.hasFh) {
                    if (siteA.fhAzimuthsCount > 0) "$yesText (${siteA.fhAzimuthsCount})" else yesText
                } else {
                    noText
                },
                valueB = if (siteB.hasFh) {
                    if (siteB.fhAzimuthsCount > 0) "$yesText (${siteB.fhAzimuthsCount})" else yesText
                } else {
                    noText
                }
            )
        }
    }
}

@Composable
private fun DatesComparisonCard(
    siteA: ComparableSiteSummary,
    siteB: ComparableSiteSummary
) {
    val isACanadian = siteA.idAnfr.startsWith("CA-", ignoreCase = true)
    val isBCanadian = siteB.idAnfr.startsWith("CA-", ignoreCase = true)
    val bothCanadian = isACanadian && isBCanadian
    val anyCanadian = isACanadian || isBCanadian
    val title = when {
        bothCanadian -> stringResource(R.string.site_comparator_section_dates_ised)
        anyCanadian -> stringResource(R.string.site_comparator_section_dates_mixed)
        else -> stringResource(R.string.site_comparator_section_dates)
    }
    ComparisonSectionCard(title = title) {
        if (!bothCanadian) {
            ComparisonRow(
                label = stringResource(R.string.site_comparator_date_implantation),
                valueA = siteA.dateImplantation ?: "—",
                valueB = siteB.dateImplantation ?: "—"
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
            ComparisonRow(
                label = stringResource(R.string.site_comparator_date_service),
                valueA = siteA.dateService ?: "—",
                valueB = siteB.dateService ?: "—"
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
        }
        ComparisonRow(
            label = stringResource(R.string.site_comparator_date_modif),
            valueA = siteA.dateModif ?: "—",
            valueB = siteB.dateModif ?: "—"
        )
    }
}

@Composable
private fun ComparisonSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    valueA: String,
    valueB: String,
    highlightA: Boolean = false,
    highlightB: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = valueA,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (highlightA) FontWeight.Bold else FontWeight.Medium,
                color = if (highlightA) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueB,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (highlightB) FontWeight.Bold else FontWeight.Medium,
                color = if (highlightB) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun BandChip(
    text: String,
    containerColor: Color,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SitePickerBottomSheet(
    repository: AnfrRepository,
    referenceLat: Double?,
    referenceLon: Double?,
    excludedIdAnfr: String?,
    onDismiss: () -> Unit,
    onSelectSite: (LocalisationEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<LocalisationEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(referenceLat, referenceLon) {
        if (query.isBlank() && referenceLat != null && referenceLon != null) {
            isLoading = true
            results = withContext(Dispatchers.IO) {
                runCatching {
                    repository.getNearest(referenceLat, referenceLon, limit = 30)
                        .filter { it.idAnfr != excludedIdAnfr }
                        .sortedBy { computeDistanceMeters(referenceLat, referenceLon, it.latitude, it.longitude) }
                }.getOrDefault(emptyList())
            }
            isLoading = false
        }
    }

    fun triggerSearch(newQuery: String) {
        query = newQuery
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(250)
            isLoading = true
            val trimmed = newQuery.trim()
            results = withContext(Dispatchers.IO) {
                runCatching {
                    if (trimmed.isEmpty()) {
                        if (referenceLat != null && referenceLon != null) {
                            repository.getNearest(referenceLat, referenceLon, limit = 30)
                        } else {
                            emptyList()
                        }
                    } else if (trimmed.all { it.isDigit() }) {
                        repository.searchAntennasById(trimmed)
                    } else {
                        (repository.searchAntennasByText(trimmed, limit = 40) +
                            repository.searchAntennasByAddress(trimmed, limit = 40))
                            .distinctBy { it.idAnfr }
                    }.filter { it.idAnfr != excludedIdAnfr }
                }.getOrDefault(emptyList())
            }
            isLoading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.site_comparator_picker_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            val isCanadaPicker = excludedIdAnfr?.startsWith("CA-", ignoreCase = true) == true ||
                fr.geotower.data.db.DatabaseCountryStore.activeCountryState.value == fr.geotower.data.db.DatabaseCountry.CA
            val searchPlaceholder = if (isCanadaPicker) {
                if (androidx.compose.ui.platform.LocalConfiguration.current.locales[0]?.language.equals("fr", ignoreCase = true)) {
                    "ID ISDE, ville ou adresse..."
                } else {
                    "ISED ID, city or address..."
                }
            } else {
                stringResource(R.string.site_comparator_search_hint)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { triggerSearch(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(searchPlaceholder) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { triggerSearch("") }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (results.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.site_comparator_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val pickerOrder = AppConfig.resolveOrderForScreen(ScreenPageTarget.SEARCH_LIST)
                val pickerListState = rememberLazyListState()
                LazyColumn(
                    state = pickerListState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .geoTowerLazyListFadingEdge(pickerListState)
                ) {
                    items(results, key = { "${it.idAnfr}_${it.operateur}_${it.latitude}" }) { item ->
                        val opColor = OperatorColors.keyFor(item.operateur)
                            ?.let { Color(OperatorColors.colorArgbForKey(it)) }
                            ?: MaterialTheme.colorScheme.primary
                        val distanceText = if (referenceLat != null && referenceLon != null) {
                            formatComparatorDistance(
                                computeDistanceMeters(referenceLat, referenceLon, item.latitude, item.longitude)
                            )
                        } else {
                            null
                        }
                        val orderedBands = remember(item.bandMask, pickerOrder) {
                            formatBandTokensFromMask(item.bandMask, pickerOrder)
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectSite(item) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ComparatorOperatorLogo(
                                    operatorName = item.operateur,
                                    fallbackLabel = null,
                                    fallbackColor = opColor,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(38.dp)
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = item.operateur ?: "?",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (distanceText != null) {
                                            Spacer(Modifier.width(8.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = distanceText,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = formatComparatorSiteIdLabel(item.idAnfr),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (orderedBands.isNotEmpty()) {
                                        @OptIn(ExperimentalLayoutApi::class)
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            orderedBands.forEach { band ->
                                                val is5G = band.startsWith("5G")
                                                Surface(
                                                    color = if (is5G) {
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                                    } else {
                                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                                    },
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = band,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = if (is5G) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (is5G) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        },
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = String.format(Locale.US, "%.4f, %.4f", item.latitude, item.longitude),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
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

private fun formatComparatorSiteIdLabel(siteId: String): String {
    if (siteId.startsWith("CA-", ignoreCase = true)) {
        val prefix = if (Locale.getDefault().language.equals("fr", ignoreCase = true)) "ISDE" else "ISED"
        return "$prefix $siteId"
    }
    return "ANFR $siteId"
}
