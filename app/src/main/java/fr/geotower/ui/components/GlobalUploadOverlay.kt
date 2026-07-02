package fr.geotower.ui.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.work.WorkInfo
import androidx.work.WorkManager
import coil.compose.AsyncImage
import fr.geotower.AppGlobalState
import fr.geotower.data.upload.ExternalPhotoUploadHistoryStore
import fr.geotower.data.upload.SignalQuestUploadQueue
import fr.geotower.data.workers.SignalQuestUploadScheduler
import fr.geotower.data.workers.SignalQuestUploadWorker
import fr.geotower.utils.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import fr.geotower.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GlobalUploadOverlay(
    onOpenUploadHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

    // Thème dynamique
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val useOneUi = AppConfig.useOneUiDesign
    val isDark = (themeMode == 2) || (themeMode == 0 && androidx.compose.foundation.isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val blockShape = if (useOneUi) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)

    // Écoute TOUS les envois ayant le tag "sq_upload_global"
    val workInfos by remember {
        WorkManager.getInstance(context).getWorkInfosByTagFlow(SignalQuestUploadScheduler.GLOBAL_TAG)
    }.collectAsState(initial = emptyList())

    var isUploadPopupHidden by remember { mutableStateOf(false) }
    var showFinishedDialog by remember { mutableStateOf(false) }
    var finishedDialogMessageOverride by remember { mutableStateOf<String?>(null) }
    var finishedDialogHasErrorsOverride by remember { mutableStateOf<Boolean?>(null) }
    var finishedDialogUploadId by remember { mutableStateOf<String?>(null) }

    // Statistiques du job en cours/terminé
    var currentProgress by remember { mutableIntStateOf(0) }
    var totalPhotos by remember { mutableIntStateOf(0) }
    var finalSuccessCount by remember { mutableIntStateOf(0) }
    val lifetimeScore = prefs.getInt("total_lifetime_uploads", 0)

    val completedWorkIds = remember { mutableSetOf<UUID>() }
    val finalizedCancelledIds = remember { mutableSetOf<UUID>() }

    // NOUVEAU : Mémoire des états pour détecter les vraies transitions
    val previousStates = remember { mutableMapOf<UUID, WorkInfo.State>() }

    val activeWork = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }
        ?: workInfos.firstOrNull { it.state == WorkInfo.State.ENQUEUED }
    val isUploading = activeWork != null

    LaunchedEffect(workInfos) {
        if (isUploading) {
            currentProgress = activeWork!!.progress.getInt("current", 0)
            totalPhotos = activeWork.progress.getInt("total", 0)
        }

        var justFinishedWork: WorkInfo? = null

        // On analyse chaque job du WorkManager
        for (work in workInfos) {
            val prevState = previousStates[work.id]
            val currentState = work.state

            // 1. Si on détecte un TOUT NOUVEAU job, on réaffiche le pop-up s'il était caché
            if (prevState == null && (currentState == WorkInfo.State.RUNNING || currentState == WorkInfo.State.ENQUEUED)) {
                isUploadPopupHidden = false
            }

            // 2. Si un job était EN COURS et vient JUSTE de se terminer (Succès ou Échec)
            if (prevState != null &&
                (prevState == WorkInfo.State.RUNNING || prevState == WorkInfo.State.ENQUEUED) &&
                (currentState == WorkInfo.State.SUCCEEDED || currentState == WorkInfo.State.FAILED)
            ) {
                if (!completedWorkIds.contains(work.id)) {
                    justFinishedWork = work
                    completedWorkIds.add(work.id)
                }
            }

            // On met à jour notre mémoire des états
            previousStates[work.id] = currentState
        }

        // Si on a capturé une fin d'envoi en direct :
        if (justFinishedWork != null) {
            finalSuccessCount = justFinishedWork.outputData.getInt("success_count", currentProgress)
            totalPhotos = justFinishedWork.outputData.getInt("total", totalPhotos)
            finishedDialogMessageOverride = null
            finishedDialogHasErrorsOverride = null
            finishedDialogUploadId = SignalQuestUploadScheduler.uploadIdFromTags(justFinishedWork.tags)

            // On sauvegarde le nouveau score global
            val newScore = lifetimeScore + finalSuccessCount
            prefs.edit().putInt("total_lifetime_uploads", newScore).apply()

            // On affiche le pop-up de victoire !
            showFinishedDialog = true
        }

        // Envois annulés sans worker actif (annulation pendant l'attente d'un nouvel essai, envois
        // suivants de la file, ou restes d'une session précédente) : on marque l'historique,
        // on purge le cache et les notifications résiduelles. Idempotent avec le nettoyage
        // qu'effectue le worker quand il est annulé en pleine exécution.
        val cancelledWork = workInfos.filter {
            it.state == WorkInfo.State.CANCELLED && finalizedCancelledIds.add(it.id)
        }
        if (cancelledWork.isNotEmpty()) {
            val appContext = context.applicationContext
            withContext(Dispatchers.IO) {
                cancelledWork.forEach { work ->
                    SignalQuestUploadScheduler.uploadIdFromTags(work.tags)?.let { uploadId ->
                        if (SignalQuestUploadQueue.finalizeCancelledUpload(appContext, uploadId) != null) {
                            SignalQuestUploadWorker.cancelUploadNotifications(appContext, uploadId)
                        }
                    }
                }
            }
        }
    }

    val showNotificationUploadResultPopup by AppGlobalState.showUploadResultPopup
    LaunchedEffect(showNotificationUploadResultPopup) {
        if (showNotificationUploadResultPopup) {
            finalSuccessCount = AppGlobalState.uploadResultPopupSuccessCount.intValue
            totalPhotos = AppGlobalState.uploadResultPopupTotal.intValue
            finishedDialogMessageOverride = AppGlobalState.uploadResultPopupMessage.value
            finishedDialogHasErrorsOverride = AppGlobalState.uploadResultPopupHasErrors.value
            finishedDialogUploadId = AppGlobalState.uploadResultPopupUploadId.value
            showFinishedDialog = true
            AppGlobalState.showUploadResultPopup.value = false
        }
    }

    // 1. POP-UP D'ENVOI EN COURS
    if (isUploading && !isUploadPopupHidden) {
        Dialog(
            onDismissRequest = { isUploadPopupHidden = true },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(shape = blockShape, color = sheetBgColor, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(stringResource(R.string.appstrings_uploading_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(56.dp),
                        color = MaterialTheme.colorScheme.primary
                    )

                    val textProgress = if (totalPhotos > 0) {
                        stringResource(R.string.upload_progress_text, currentProgress, totalPhotos)
                    } else {
                        stringResource(R.string.appstrings_upload_preparing)
                    }
                    Text(textProgress, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                activeWork?.id?.let { workId ->
                                    WorkManager.getInstance(context).cancelWorkById(workId)
                                }
                                isUploadPopupHidden = true
                            },
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.appstrings_upload_cancel), fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { isUploadPopupHidden = true },
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(stringResource(R.string.appstrings_hide), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // 2. POP-UP DE FIN (SUCCÈS OU ERREUR PARTIELLE)
    if (showFinishedDialog) {
        val hasErrors = finishedDialogHasErrorsOverride ?: (totalPhotos > 0 && finalSuccessCount < totalPhotos)
        val finalIcon = if (hasErrors) Icons.Default.Warning else Icons.Default.CheckCircle
        val iconColor = if (hasErrors) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)

        // Miniature d'une des photos envoyées (persistée dans l'historique d'upload), affichée à
        // la place de la coche verte avec un badge « +N » pour les autres photos du lot. En cas
        // d'erreur ou de miniature introuvable (historique effacé...), on garde les icônes.
        var successThumbnailPath by remember(finishedDialogUploadId) { mutableStateOf<String?>(null) }
        LaunchedEffect(finishedDialogUploadId, hasErrors) {
            val uploadId = finishedDialogUploadId
            if (hasErrors || uploadId == null) return@LaunchedEffect
            val appContext = context.applicationContext
            successThumbnailPath = withContext(Dispatchers.IO) {
                ExternalPhotoUploadHistoryStore.read(appContext)
                    .asSequence()
                    .filter { it.uploadId == uploadId }
                    .filter {
                        it.status == ExternalPhotoUploadHistoryStore.STATUS_SUCCESS ||
                            it.status == ExternalPhotoUploadHistoryStore.STATUS_AWAITING_VALIDATION
                    }
                    .mapNotNull { it.thumbnailPath }
                    .firstOrNull { File(it).isFile }
            }
        }

        Dialog(
            onDismissRequest = { /* L'utilisateur doit cliquer sur le bouton */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(shape = blockShape, color = sheetBgColor, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val thumbnailPath = successThumbnailPath
                    if (!hasErrors && thumbnailPath != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = File(thumbnailPath),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(18.dp))
                            )
                            if (finalSuccessCount > 1) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
                                ) {
                                    Text(
                                        text = "+${finalSuccessCount - 1}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Icon(finalIcon, contentDescription = null, tint = iconColor, modifier = Modifier.size(64.dp))
                    }

                    Text(stringResource(R.string.appstrings_upload_finished_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                    Text(
                        text = finishedDialogMessageOverride ?: stringResource(R.string.upload_result_text, finalSuccessCount, totalPhotos),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (hasErrors) {
                        Text(
                            text = stringResource(R.string.appstrings_upload_error_warning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Text(
                        text = pluralStringResource(
                            R.plurals.upload_lifetime_score,
                            lifetimeScore + finalSuccessCount,
                            lifetimeScore + finalSuccessCount
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            showFinishedDialog = false
                            onOpenUploadHistory()
                        },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.appstrings_upload_history_title), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showFinishedDialog = false },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(stringResource(R.string.appstrings_db_download_termine), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
