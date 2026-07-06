package fr.geotower.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.ExistingWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.workDataOf
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.api.DownloadManifestVerifier
import fr.geotower.data.api.RetrofitClient
import fr.geotower.data.models.OfflineMapDto
import fr.geotower.data.workers.DownloadNotificationCenter
import fr.geotower.data.workers.OfflineMapDownloadValidator
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.OfflineMapDisplayNames
import java.io.File
import kotlin.math.abs
import androidx.compose.ui.res.stringResource
import fr.geotower.R

private data class MapRowViewportBounds(
    val top: Float = Float.NaN,
    val height: Int = 0
) {
    val bottom: Float
        get() = top + height
}

private fun isMapRowAtBestViewportPosition(
    bounds: MapRowViewportBounds,
    viewportTop: Float,
    viewportBottom: Float,
    scrollValue: Int,
    scrollMaxValue: Int
): Boolean {
    if (bounds.top.isNaN() || bounds.height <= 0 || viewportTop.isNaN() || viewportBottom.isNaN()) return false

    val viewportHeight = viewportBottom - viewportTop
    if (viewportHeight <= 0f) return false

    val visibleTop = maxOf(bounds.top, viewportTop)
    val visibleBottom = minOf(bounds.bottom, viewportBottom)
    val visibleHeight = (visibleBottom - visibleTop).coerceAtLeast(0f)
    val maxVisibleHeight = minOf(bounds.height.toFloat(), viewportHeight)
    if (visibleHeight < maxVisibleHeight - 2f) return false

    val idealScroll = (scrollValue + bounds.top - viewportTop)
        .coerceIn(0f, scrollMaxValue.toFloat())
    val topTolerance = maxOf(12f, bounds.height * 0.1f)

    return abs(scrollValue - idealScroll) <= topTolerance
}

private fun mapDisplayName(map: OfflineMapDto): String {
    return map.name.takeIf { it.isNotBlank() } ?: OfflineMapDisplayNames.formatMapName(map.mapFilename)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MapDownloadCard(
    useOneUi: Boolean,
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    viewportTop: Float = Float.NaN,
    viewportBottom: Float = Float.NaN,
    scrollValue: Int = 0,
    scrollMaxValue: Int = 0,
    targetMapFilename: String? = null,
    onTargetMapPositioned: (Float, Int) -> Unit = { _, _ -> },
    onExpandedChange: (Boolean) -> Unit = {},
    onSafeClick: SafeClick? = null
) {
    val context = LocalContext.current
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val workManager = remember { androidx.work.WorkManager.getInstance(context) }
    val safeClick = onSafeClick ?: rememberSafeClick()
    val featureFlags by RemoteFeatureFlags.config
    val canLoadCatalog = featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.OFFLINE_MAPS_CATALOG)
    val canDownloadMaps =
        featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.OFFLINE_MAPS_DOWNLOAD) &&
            featureFlags.isActionEnabled(RemoteFeatureFlags.Actions.START_OFFLINE_MAP_DOWNLOAD) &&
            featureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.OFFLINE_MAP_DOWNLOAD)

    val workInfos by workManager.getWorkInfosByTagFlow("map_download").collectAsState(initial = emptyList())

    var catalog by remember { mutableStateOf<List<OfflineMapDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    var fileRefreshTrigger by remember { mutableIntStateOf(0) }
    var mapToDelete by remember { mutableStateOf<OfflineMapDto?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var isExpanded by rememberSaveable(targetMapFilename) { mutableStateOf(targetMapFilename != null) }

    val mapsDir = remember { File(context.getExternalFilesDir(null), "maps") }

    LaunchedEffect(canLoadCatalog) {
        if (!canLoadCatalog) {
            catalog = emptyList()
            isLoading = false
            isError = false
            return@LaunchedEffect
        }
        try {
            val rawManifest = RetrofitClient.apiService.getDownloadManifest().use { it.string() }
            catalog = DownloadManifestVerifier.verifyAndParse(rawManifest)
                ?.maps
                ?.filter { OfflineMapDownloadValidator.isValidCatalogEntry(it) }
                .orEmpty()
        } catch (e: Exception) {
            isError = true
        } finally {
            isLoading = false
        }
    }

    // Vérifie s'il y a au moins une carte téléchargée pour afficher le bouton "Tout supprimer"
    val hasDownloadedMaps = remember(fileRefreshTrigger, workInfos) {
        OfflineMapDownloadValidator.listSafeMapFiles(mapsDir).isNotEmpty()
    }
    val isAnySyncing = workInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

    LaunchedEffect(targetMapFilename) {
        if (!targetMapFilename.isNullOrBlank()) {
            isExpanded = true
        }
    }

    LaunchedEffect(isAnySyncing) {
        if (isAnySyncing) {
            isExpanded = true
        }
    }

    LaunchedEffect(isExpanded) {
        onExpandedChange(isExpanded)
    }

    Surface(
        shape = shape,
        border = border,
        color = if (useOneUi) bubbleColor else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(sizing.spacing(16.dp))) {
            // EN-TÊTE AVEC BOUTON "TOUT TÉLÉCHARGER"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            safeClick("offline_maps_dropdown_toggle") {
                                isExpanded = !isExpanded
                            }
                        }
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(sizing.component(24.dp)))
                    Spacer(Modifier.width(sizing.spacing(12.dp)))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.appstrings_offline_maps_title), fontWeight = FontWeight.Bold, style = sizing.textStyle(MaterialTheme.typography.titleMedium))
                        Text(text = stringResource(R.string.appstrings_offline_maps_desc), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // 🚀 BOUTON : TOUT TÉLÉCHARGER
                if (!isLoading && !isError && catalog.isNotEmpty() && canDownloadMaps) {
                    IconButton(
                        onClick = {
                            safeClick("map_download_all") {
                                catalog.forEach { map ->
                                    val displayName = mapDisplayName(map)
                                    val mapFile = OfflineMapDownloadValidator.safeMapFile(mapsDir, map.mapFilename)
                                    val isDownloaded = mapFile?.exists() == true
                                    val currentWork = workInfos.find { it.tags.contains("map_id_${map.id}") }
                                    val isSyncing = currentWork?.state == WorkInfo.State.RUNNING || currentWork?.state == WorkInfo.State.ENQUEUED

                                    if (mapFile != null && !isDownloaded && !isSyncing && canDownloadMaps) {
                                        val data = workDataOf(
                                            "map_url" to map.mapUrl,
                                            "map_name" to displayName,
                                            "map_filename" to map.mapFilename,
                                            "estimated_size_mb" to map.estimatedSizeMb,
                                            "map_sha256" to map.sha256.orEmpty()
                                        )
                                        val request = OneTimeWorkRequestBuilder<fr.geotower.data.workers.MapDownloadWorker>()
                                            .setInputData(data)
                                            .setConstraints(
                                                Constraints.Builder()
                                                    .setRequiredNetworkType(NetworkType.CONNECTED)
                                                    .build()
                                            )
                                            .addTag("map_download")
                                            .addTag("map_id_${map.id}")
                                            .build()

                                        workManager.enqueueUniqueWork("map_dl_${map.id}", ExistingWorkPolicy.REPLACE, request)
                                    }
                                }
                                isExpanded = true
                            }
                        }
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = stringResource(R.string.appstrings_download_all), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(
                    onClick = {
                        safeClick("offline_maps_dropdown_toggle_icon") {
                            isExpanded = !isExpanded
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))

                    if (isLoading) {
                        LoadingIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else if (isError) {
                        Text(stringResource(R.string.appstrings_network_error_search), color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                catalog.forEachIndexed { index, map ->
                    val displayName = mapDisplayName(map)
                    val currentWork = workInfos.find { it.tags.contains("map_id_${map.id}") }
                    val isSyncing = currentWork?.state == WorkInfo.State.RUNNING || currentWork?.state == WorkInfo.State.ENQUEUED
                    val progressValue = currentWork?.progress?.getInt("progress", 0) ?: 0
                    var rowBounds by remember(map.mapFilename) { mutableStateOf(MapRowViewportBounds()) }
                    val isTargetMap = targetMapFilename == map.mapFilename
                    val isDownloaded = remember(fileRefreshTrigger, currentWork?.state) {
                        OfflineMapDownloadValidator.safeMapFile(mapsDir, map.mapFilename)?.exists() == true
                    }

                    LaunchedEffect(map.mapFilename, rowBounds, viewportTop, viewportBottom, scrollValue, scrollMaxValue) {
                        if (isMapRowAtBestViewportPosition(rowBounds, viewportTop, viewportBottom, scrollValue, scrollMaxValue)) {
                            DownloadNotificationCenter.clearOfflineMapResultNotification(context, map.mapFilename)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = sizing.spacing(8.dp))
                            .onGloballyPositioned { coordinates ->
                                rowBounds = MapRowViewportBounds(
                                    top = coordinates.positionInRoot().y,
                                    height = coordinates.size.height
                                )
                                if (isTargetMap) {
                                    onTargetMapPositioned(rowBounds.top, rowBounds.height)
                                }
                            }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                // On utilise notre nouvelle fonction de formatage pour un affichage propre
                                Text(
                                    text = displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = sizing.text(15.sp)
                                )
                                Text(text = stringResource(R.string.map_file_size_mb, map.estimatedSizeMb), fontSize = sizing.text(13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            if (isSyncing) {
                                IconButton(onClick = { safeClick("map_cancel_${map.id}") { workManager.cancelUniqueWork("map_dl_${map.id}") } }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.appstrings_cancel), tint = MaterialTheme.colorScheme.error)
                                }
                            } else if (isDownloaded) {
                                IconButton(onClick = { safeClick("map_delete_${map.id}") { mapToDelete = map } }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.appstrings_delete), tint = MaterialTheme.colorScheme.error)
                                }
                            } else if (canDownloadMaps) {
                                IconButton(
                                    onClick = {
                                        safeClick("map_download_${map.id}") {
                                            val data = workDataOf(
                                                "map_url" to map.mapUrl,
                                                "map_name" to displayName,
                                                "map_filename" to map.mapFilename,
                                                "estimated_size_mb" to map.estimatedSizeMb,
                                                "map_sha256" to map.sha256.orEmpty()
                                            )
                                            val request = OneTimeWorkRequestBuilder<fr.geotower.data.workers.MapDownloadWorker>()
                                                .setInputData(data)
                                                .setConstraints(
                                                    Constraints.Builder()
                                                        .setRequiredNetworkType(NetworkType.CONNECTED)
                                                        .build()
                                                )
                                                .addTag("map_download")
                                                .addTag("map_id_${map.id}")
                                                .build()
                                            workManager.enqueueUniqueWork("map_dl_${map.id}", ExistingWorkPolicy.REPLACE, request)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = stringResource(R.string.appstrings_download), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        if (isSyncing) {
                            Spacer(Modifier.height(sizing.spacing(8.dp)))
                            LinearWavyProgressIndicator(
                                progress = { progressValue / 100f },
                                modifier = Modifier.fillMaxWidth().height(sizing.component(8.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            // 🚨 L'extraction n'existe plus, on affiche juste la progression !
                            val statusText = stringResource(R.string.map_download_progress_inline, progressValue)
                            Text(text = statusText, fontSize = sizing.text(12.sp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (index < catalog.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }

                // 🚀 NOUVEAU : Bouton pour annuler TOUS les téléchargements de cartes
                if (isAnySyncing) {
                    Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))
                    OutlinedButton(
                        onClick = {
                            safeClick("map_cancel_all") {
                                workManager.cancelAllWorkByTag("map_download")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = sizing.component(50.dp)),
                        shape = RoundedCornerShape(sizing.component(12.dp)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(sizing.component(24.dp)))
                        Spacer(Modifier.width(sizing.spacing(8.dp)))
                        Text(text = stringResource(R.string.appstrings_cancel_download), fontWeight = FontWeight.Bold, style = sizing.textStyle(MaterialTheme.typography.labelLarge))
                    }
                }

                // 🚀 BOUTON : TOUT SUPPRIMER (Style calqué sur DatabaseDownloadCard)
                if (hasDownloadedMaps) {
                    Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))
                    OutlinedButton(
                        onClick = { showDeleteAllDialog = true },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = sizing.component(50.dp)),
                        shape = RoundedCornerShape(sizing.component(12.dp)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(sizing.component(24.dp)))
                        Spacer(Modifier.width(sizing.spacing(8.dp)))
                        Text(text = stringResource(R.string.appstrings_delete_all_maps), fontWeight = FontWeight.Bold, style = sizing.textStyle(MaterialTheme.typography.labelLarge))
                    }
                }
                    }
                }
            }
        }
    }

    // POP-UP : Suppression individuelle (Inchangé)
    if (mapToDelete != null) {
        AlertDialog(
            onDismissRequest = { mapToDelete = null },
            title = { Text(text = stringResource(R.string.appstrings_map_delete_warning_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.appstrings_map_delete_warning_desc)) },
            shape = shape,
            containerColor = MaterialTheme.colorScheme.surface,
            dismissButton = {
                DialogDestructiveButton(text = stringResource(R.string.appstrings_yes), onClick = {
                    OfflineMapDownloadValidator.safeMapFile(mapsDir, mapToDelete!!.mapFilename)?.delete()
                    fileRefreshTrigger++
                    mapToDelete = null
                })
            },
            confirmButton = { DialogNeutralButton(text = stringResource(R.string.appstrings_no), onClick = { mapToDelete = null }) }
        )
    }

    // 🚀 POP-UP : Suppression de TOUTES les cartes
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(text = stringResource(R.string.appstrings_delete_all_maps_warning_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.appstrings_delete_all_maps_warning_desc)) },
            shape = shape,
            containerColor = MaterialTheme.colorScheme.surface,
            dismissButton = {
                DialogDestructiveButton(text = stringResource(R.string.appstrings_yes), onClick = {
                    OfflineMapDownloadValidator.deleteAllSafeMapFiles(mapsDir)
                    fileRefreshTrigger++
                    showDeleteAllDialog = false
                })
            },
            confirmButton = { DialogNeutralButton(text = stringResource(R.string.appstrings_no), onClick = { showDeleteAllDialog = false }) }
        )
    }
}
