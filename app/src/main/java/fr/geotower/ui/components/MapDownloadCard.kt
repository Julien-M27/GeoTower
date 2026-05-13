package fr.geotower.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
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
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.workDataOf
import fr.geotower.data.api.RetrofitClient
import fr.geotower.data.models.OfflineMapDto
import fr.geotower.data.workers.DownloadNotificationCenter
import fr.geotower.data.workers.OfflineMapDownloadValidator
import fr.geotower.utils.AppStrings
import java.io.File
import kotlin.math.abs

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
    return map.name.takeIf { it.isNotBlank() } ?: AppStrings.formatMapName(map.mapFilename)
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
    onSafeClick: SafeClick? = null
) {
    val context = LocalContext.current
    val workManager = remember { androidx.work.WorkManager.getInstance(context) }
    val safeClick = onSafeClick ?: rememberSafeClick()

    val workInfos by workManager.getWorkInfosByTagFlow("map_download").collectAsState(initial = emptyList())

    var catalog by remember { mutableStateOf<List<OfflineMapDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    var fileRefreshTrigger by remember { mutableIntStateOf(0) }
    var mapToDelete by remember { mutableStateOf<OfflineMapDto?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    val mapsDir = remember { File(context.getExternalFilesDir(null), "maps") }

    LaunchedEffect(Unit) {
        try {
            catalog = RetrofitClient.apiService.getMapsCatalog()
                .filter { OfflineMapDownloadValidator.isValidCatalogEntry(it) }
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

    Surface(
        shape = shape,
        border = border,
        color = if (useOneUi) bubbleColor else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // EN-TÊTE AVEC BOUTON "TOUT TÉLÉCHARGER"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = AppStrings.offlineMapsTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(text = AppStrings.offlineMapsDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // 🚀 BOUTON : TOUT TÉLÉCHARGER
                if (!isLoading && !isError && catalog.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            safeClick("map_download_all") {
                                catalog.forEach { map ->
                                    val displayName = mapDisplayName(map)
                                    val mapFile = OfflineMapDownloadValidator.safeMapFile(mapsDir, map.mapFilename)
                                    val isDownloaded = mapFile?.exists() == true
                                    val currentWork = workInfos.find { it.tags.contains("map_id_${map.id}") }
                                    val isSyncing = currentWork?.state == WorkInfo.State.RUNNING || currentWork?.state == WorkInfo.State.ENQUEUED

                                    if (mapFile != null && !isDownloaded && !isSyncing) {
                                        val data = workDataOf(
                                            "map_url" to map.mapUrl,
                                            "map_name" to displayName,
                                            "map_filename" to map.mapFilename,
                                            "estimated_size_mb" to map.estimatedSizeMb,
                                            "map_sha256" to map.sha256.orEmpty()
                                        )
                                        val request = OneTimeWorkRequestBuilder<fr.geotower.data.workers.MapDownloadWorker>()
                                            .setInputData(data)
                                            .addTag("map_download")
                                            .addTag("map_id_${map.id}")
                                            .build()

                                        workManager.enqueueUniqueWork("map_dl_${map.id}", ExistingWorkPolicy.REPLACE, request)
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = AppStrings.downloadAll, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                LoadingIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (isError) {
                Text(AppStrings.networkErrorSearch, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.CenterHorizontally))
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
                            .padding(vertical = 8.dp)
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
                                    fontSize = 15.sp
                                )
                                Text(text = "${map.estimatedSizeMb} Mo", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            if (isSyncing) {
                                IconButton(onClick = { safeClick("map_cancel_${map.id}") { workManager.cancelUniqueWork("map_dl_${map.id}") } }) {
                                    Icon(Icons.Default.Close, contentDescription = AppStrings.cancel, tint = MaterialTheme.colorScheme.error)
                                }
                            } else if (isDownloaded) {
                                IconButton(onClick = { safeClick("map_delete_${map.id}") { mapToDelete = map } }) {
                                    Icon(Icons.Default.Delete, contentDescription = AppStrings.delete, tint = MaterialTheme.colorScheme.error)
                                }
                            } else {
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
                                                .setInputData(data).addTag("map_download").addTag("map_id_${map.id}").build()
                                            workManager.enqueueUniqueWork("map_dl_${map.id}", ExistingWorkPolicy.REPLACE, request)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = AppStrings.download, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        if (isSyncing) {
                            Spacer(Modifier.height(8.dp))
                            LinearWavyProgressIndicator(
                                progress = { progressValue / 100f },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            // 🚨 L'extraction n'existe plus, on affiche juste la progression !
                            val statusText = AppStrings.downloadProgress(progressValue)
                            Text(text = statusText, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (index < catalog.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }

                // 🚀 NOUVEAU : Bouton pour annuler TOUS les téléchargements de cartes
                val isAnySyncing = workInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                if (isAnySyncing) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            safeClick("map_cancel_all") {
                                workManager.cancelAllWorkByTag("map_download")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(text = AppStrings.cancelDownload, fontWeight = FontWeight.Bold)
                    }
                }

                // 🚀 BOUTON : TOUT SUPPRIMER (Style calqué sur DatabaseDownloadCard)
                if (hasDownloadedMaps) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { showDeleteAllDialog = true },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(text = AppStrings.deleteAllMaps, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // POP-UP : Suppression individuelle (Inchangé)
    if (mapToDelete != null) {
        AlertDialog(
            onDismissRequest = { mapToDelete = null },
            title = { Text(text = AppStrings.mapDeleteWarningTitle, fontWeight = FontWeight.Bold) },
            text = { Text(AppStrings.mapDeleteWarningDesc) },
            shape = shape,
            containerColor = MaterialTheme.colorScheme.surface,
            dismissButton = {
                TextButton(onClick = {
                    OfflineMapDownloadValidator.safeMapFile(mapsDir, mapToDelete!!.mapFilename)?.delete()
                    fileRefreshTrigger++
                    mapToDelete = null
                }) { Text(AppStrings.yes, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            confirmButton = { Button(onClick = { mapToDelete = null }, shape = RoundedCornerShape(8.dp)) { Text(AppStrings.no, fontWeight = FontWeight.Bold) } }
        )
    }

    // 🚀 POP-UP : Suppression de TOUTES les cartes
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(text = AppStrings.deleteAllMapsWarningTitle, fontWeight = FontWeight.Bold) },
            text = { Text(AppStrings.deleteAllMapsWarningDesc) },
            shape = shape,
            containerColor = MaterialTheme.colorScheme.surface,
            dismissButton = {
                TextButton(onClick = {
                    OfflineMapDownloadValidator.deleteAllSafeMapFiles(mapsDir)
                    fileRefreshTrigger++
                    showDeleteAllDialog = false
                }) { Text(AppStrings.yes, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            confirmButton = { Button(onClick = { showDeleteAllDialog = false }, shape = RoundedCornerShape(8.dp)) { Text(AppStrings.no, fontWeight = FontWeight.Bold) } }
        )
    }
}
