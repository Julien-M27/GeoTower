package fr.geotower.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.data.hidden.HiddenSiteRecord
import fr.geotower.data.hidden.HiddenSitesStore
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.physicalSiteKey
import fr.geotower.services.LiveSitePhotoSelector
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.PageScrollEdgeButtons
import fr.geotower.ui.components.PhotoAsyncImage
import fr.geotower.ui.components.SharedMiniMapCard
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.components.pageScrollbar
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.PageScrollPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenSitesScreen(
    navController: NavController,
    repository: AnfrRepository
) {
    val context = LocalContext.current
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val scrollState = rememberScrollState()
    val safeClick = rememberSafeClick()
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "settings")
    val records by HiddenSitesStore.flow(context).collectAsState()
    var showSettingsSheet by remember { mutableStateOf(false) }
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var addressesBySite by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var photosBySite by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var azimuthsByRecordKey by remember { mutableStateOf<Map<String, HiddenSiteAzimuths>>(emptyMap()) }

    val siteGroups = records
        .groupBy { it.physicalSiteKey }
        .toList()
        .sortedBy { (_, siteRecords) -> siteRecords.firstOrNull()?.idAnfr.orEmpty() }

    BackHandler(enabled = !safeBackNavigation.isLocked) {
        safeBackNavigation.navigateBack()
    }

    // L'adresse et la photo sont des enrichissements facultatifs : la carte reste utilisable
    // immédiatement avec les données conservées au moment du masquage.
    LaunchedEffect(records) {
        addressesBySite = emptyMap()
        photosBySite = emptyMap()
        azimuthsByRecordKey = emptyMap()
        if (records.isEmpty()) return@LaunchedEffect

        val anfrIds = records.map { it.idAnfr }.filter { it.isNotBlank() }.distinct()
        val techniques = try {
            repository.getTechniqueSummariesByIds(anfrIds)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyMap()
        }
        val physiques = try {
            repository.getPhysiqueSummariesByIds(anfrIds)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyMap()
        }
        val idsNeedingAzimuths = records
            .filter { it.azimuts.isNullOrBlank() || it.azimutsFh.isNullOrBlank() }
            .map { it.idAnfr }
            .filter { it.isNotBlank() }
            .distinct()
        val fallbackAntennas = if (idsNeedingAzimuths.isEmpty()) {
            emptyMap()
        } else {
            try {
                repository.getAntennasForHiddenSites(idsNeedingAzimuths)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyMap()
            }
        }

        azimuthsByRecordKey = records.associate { record ->
            val fallback = fallbackAntennas[record.idAnfr]
                .orEmpty()
                .firstOrNull { it.physicalSiteKey() == record.physicalSiteKey }
            hiddenRecordKey(record) to HiddenSiteAzimuths(
                azimuts = record.azimuts ?: fallback?.azimuts,
                azimutsFh = record.azimutsFh ?: fallback?.azimutsFh
            )
        }

        addressesBySite = siteGroups.associate { (siteKey, group) ->
            siteKey to group.asSequence()
                .mapNotNull { record -> techniques[record.idAnfr]?.adresse?.trim() }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
        }

        val photoEntries = coroutineScope {
            siteGroups.map { (siteKey, group) ->
                async(Dispatchers.IO) {
                    var candidate: String? = null
                    for (record in group.sortedBy { it.operatorLabel }) {
                        if (candidate != null) break
                        val supportId = physiques[record.idAnfr]
                            ?.firstOrNull { it.idSupport.isNotBlank() }
                            ?.idSupport
                            ?.takeIf { it.isNotBlank() }
                            ?: record.idAnfr.takeIf { it.isNotBlank() }
                        if (supportId == null) continue

                        candidate = try {
                                LiveSitePhotoSelector
                                    .firstCandidate(context, supportId, record.operatorLabel)
                                    ?.url
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            null
                        }
                    }
                    candidate?.let { siteKey to it }
                }
            }.awaitAll().filterNotNull()
        }
        photosBySite = photoEntries.toMap()
    }

    Scaffold(
        containerColor = uiStyle.backgroundColor,
        // Le NavHost fournit déjà les insets de la racine.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.hidden_sites_title),
                onBack = { safeBackNavigation.navigateBack() },
                backEnabled = !safeBackNavigation.isLocked,
                backgroundColor = uiStyle.backgroundColor,
                actions = {
                    IconButton(
                        onClick = { safeClick("hidden_sites_settings") { showSettingsSheet = true } }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.appstrings_settings_title),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .geoTowerFadingEdge(scrollState, fadeHeight = sizing.component(72.dp))
                    .pageScrollbar(PageScrollPrefs.HIDDEN_SITES, scrollState)
                    .verticalScroll(scrollState)
                    .navigationBarsPadding()
                    .padding(
                        horizontal = sizing.spacing(16.dp),
                        vertical = sizing.spacing(8.dp)
                    ),
                verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
            ) {
                Text(
                    text = stringResource(R.string.hidden_sites_description),
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                if (siteGroups.isEmpty()) {
                    HiddenSitesEmptyState()
                } else {
                    siteGroups.forEach { (siteKey, group) ->
                        group.sortedBy { it.operatorLabel }.forEach { record ->
                            val azimuths = azimuthsByRecordKey[hiddenRecordKey(record)]
                            val displayRecord = record.copy(
                                azimuts = azimuths?.azimuts ?: record.azimuts,
                                azimutsFh = azimuths?.azimutsFh ?: record.azimutsFh
                            )
                            HiddenSiteCard(
                                record = displayRecord,
                                address = addressesBySite[siteKey],
                                photoUrl = photosBySite[siteKey],
                                onRestore = { HiddenSitesStore.restore(context, record) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))
            }

            PageScrollEdgeButtons(PageScrollPrefs.HIDDEN_SITES, scrollState)
        }
    }

    if (showSettingsSheet) {
        HistoryPageSettingsSheet(
            title = stringResource(R.string.hidden_sites_title),
            page = PageScrollPrefs.HIDDEN_SITES,
            options = emptyList(),
            onReset = {},
            onDismiss = { showSettingsSheet = false },
            onBack = { showSettingsSheet = false },
            sheetState = settingsSheetState,
            useOneUi = uiStyle.useOneUi,
            bubbleColor = uiStyle.bubbleColor
        )
    }
}

@Composable
private fun HiddenSitesEmptyState() {
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing

    Surface(
        shape = uiStyle.cardShape,
        border = uiStyle.cardBorder,
        color = hiddenSiteCardColor(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(sizing.spacing(24.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.VisibilityOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(sizing.component(40.dp))
            )
            Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))
            Text(
                text = stringResource(R.string.hidden_sites_empty),
                style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun HiddenSiteCard(
    record: HiddenSiteRecord,
    address: String?,
    photoUrl: String?,
    onRestore: () -> Unit
) {
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val addressText = address?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.appstrings_unknown_address)
    val anfrText = record.idAnfr.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.appstrings_unknown)

    Surface(
        shape = uiStyle.cardShape,
        border = uiStyle.cardBorder,
        color = hiddenSiteCardColor(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(sizing.spacing(16.dp))) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.operatorLabel,
                        style = sizing.textStyle(MaterialTheme.typography.titleLarge),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(sizing.spacing(6.dp)))
                    Text(
                        text = addressText,
                        style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(sizing.spacing(3.dp)))
                    Text(
                        text = stringResource(R.string.hidden_sites_anfr_id_label) + " : " + anfrText,
                        style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!photoUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
                    PhotoAsyncImage(
                        model = photoUrl,
                        contentDescription = record.operatorLabel,
                        modifier = Modifier
                            .size(sizing.component(76.dp))
                            .clip(RoundedCornerShape(sizing.component(12.dp))),
                        contentScale = ContentScale.Crop,
                        containerColor = uiStyle.secondaryCardColor,
                        indicatorColor = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(sizing.spacing(14.dp)))
            SharedMiniMapCard(
                modifier = Modifier.fillMaxWidth(),
                centerLat = record.latitude,
                centerLon = record.longitude,
                mappedAntennas = listOf(record.toMapAntenna()),
                blockShape = uiStyle.smallItemShape,
                cardBorder = null,
                onMapReady = {},
                allowGestures = false,
                initialZoom = 16.5,
                showAzimuths = true,
                showZoomControls = false
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = sizing.spacing(4.dp)),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onRestore) {
                    Text(
                        text = stringResource(R.string.hidden_sites_restore),
                        style = sizing.textStyle(MaterialTheme.typography.labelLarge)
                    )
                }
            }
        }
    }
}

@Composable
private fun hiddenSiteCardColor() = LocalGeoTowerUiStyle.current.cardColor

private data class HiddenSiteAzimuths(
    val azimuts: String?,
    val azimutsFh: String?
)

private fun hiddenRecordKey(record: HiddenSiteRecord): String =
    "${record.physicalSiteKey}|${record.operatorKey}"

private fun HiddenSiteRecord.toMapAntenna(): LocalisationEntity = LocalisationEntity(
    idAnfr = idAnfr.takeIf { it.isNotBlank() } ?: "HIDDEN_$physicalSiteKey",
    operateur = operatorLabel,
    latitude = latitude,
    longitude = longitude,
    azimuts = azimuts,
    codeInsee = null,
    azimutsFh = azimutsFh
)
