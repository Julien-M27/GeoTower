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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import fr.geotower.utils.AppStrings
import java.io.File

@Composable
fun MapDownloadCard(
    useOneUi: Boolean,
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    onSafeClick: (() -> Unit) -> Unit = { it() }
) {
    val context = LocalContext.current
    val workManager = remember { androidx.work.WorkManager.getInstance(context) }

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
        } catch (e: Exception) {
            isError = true
        } finally {
            isLoading = false
        }
    }

    // Vérifie s'il y a au moins une carte téléchargée pour afficher le bouton "Tout supprimer"
    val hasDownloadedMaps = remember(fileRefreshTrigger, workInfos) {
        val files = mapsDir.listFiles { f -> f.extension == "map" }
        !files.isNullOrEmpty()
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
                            onSafeClick {
                                catalog.forEach { map ->
                                    val isDownloaded = File(mapsDir, map.mapFilename).exists()
                                    val currentWork = workInfos.find { it.tags.contains("map_id_${map.id}") }
                                    val isSyncing = currentWork?.state == WorkInfo.State.RUNNING || currentWork?.state == WorkInfo.State.ENQUEUED

                                    if (!isDownloaded && !isSyncing) {
                                        val data = workDataOf(
                                            "zip_url" to map.zipUrl,
                                            "map_filename" to map.mapFilename,
                                            "estimated_size_mb" to map.estimatedZipSizeMb
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
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (isError) {
                Text(AppStrings.networkErrorSearch, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                catalog.forEachIndexed { index, map ->
                    val currentWork = workInfos.find { it.tags.contains("map_id_${map.id}") }
                    val isSyncing = currentWork?.state == WorkInfo.State.RUNNING || currentWork?.state == WorkInfo.State.ENQUEUED
                    val progressValue = currentWork?.progress?.getInt("progress", 0) ?: 0
                    val workState = currentWork?.progress?.getString("state") ?: ""
                    val isDownloaded = remember(fileRefreshTrigger, currentWork?.state) {
                        File(mapsDir, map.mapFilename).exists()
                    }

                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                // On utilise l'ID pour aller chercher le nom traduit dans nos strings
                                Text(
                                    text = AppStrings.getMapName(map.id),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(text = "~ ${map.estimatedZipSizeMb} Mo", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            if (isSyncing) {
                                IconButton(onClick = { onSafeClick { workManager.cancelUniqueWork("map_dl_${map.id}") } }) {
                                    Icon(Icons.Default.Close, contentDescription = "Annuler", tint = MaterialTheme.colorScheme.error)
                                }
                            } else if (isDownloaded) {
                                IconButton(onClick = { onSafeClick { mapToDelete = map } }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        onSafeClick {
                                            val data = workDataOf("zip_url" to map.zipUrl, "map_filename" to map.mapFilename, "estimated_size_mb" to map.estimatedZipSizeMb)
                                            val request = OneTimeWorkRequestBuilder<fr.geotower.data.workers.MapDownloadWorker>()
                                                .setInputData(data).addTag("map_download").addTag("map_id_${map.id}").build()
                                            workManager.enqueueUniqueWork("map_dl_${map.id}", ExistingWorkPolicy.REPLACE, request)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = "Télécharger", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        if (isSyncing) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progressValue / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            val statusText = if (workState == "EXTRACTING") AppStrings.mapExtracting else AppStrings.downloadProgress(progressValue)
                            Text(text = statusText, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (index < catalog.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
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
                    File(mapsDir, mapToDelete!!.mapFilename).delete()
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
                    mapsDir.listFiles()?.forEach { it.delete() }
                    fileRefreshTrigger++
                    showDeleteAllDialog = false
                }) { Text(AppStrings.yes, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            confirmButton = { Button(onClick = { showDeleteAllDialog = false }, shape = RoundedCornerShape(8.dp)) { Text(AppStrings.no, fontWeight = FontWeight.Bold) } }
        )
    }
}