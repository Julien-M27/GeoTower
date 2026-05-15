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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.work.WorkInfo
import androidx.work.WorkManager
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings
import java.util.UUID

@Composable
fun GlobalUploadOverlay() {
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
        WorkManager.getInstance(context).getWorkInfosByTagFlow("sq_upload_global")
    }.collectAsState(initial = emptyList())

    var isUploadPopupHidden by remember { mutableStateOf(false) }
    var showFinishedDialog by remember { mutableStateOf(false) }

    // Statistiques du job en cours/terminé
    var currentProgress by remember { mutableIntStateOf(0) }
    var totalPhotos by remember { mutableIntStateOf(0) }
    var finalSuccessCount by remember { mutableIntStateOf(0) }
    val lifetimeScore = prefs.getInt("total_lifetime_uploads", 0)

    val completedWorkIds = remember { mutableSetOf<UUID>() }

    // NOUVEAU : Mémoire des états pour détecter les vraies transitions
    val previousStates = remember { mutableMapOf<UUID, WorkInfo.State>() }

    val activeWork = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
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

            // On sauvegarde le nouveau score global
            val newScore = lifetimeScore + finalSuccessCount
            prefs.edit().putInt("total_lifetime_uploads", newScore).apply()

            // On affiche le pop-up de victoire !
            showFinishedDialog = true
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
                    Text(AppStrings.uploadingTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                    SequentialWavyLoader(modifier = Modifier.size(56.dp), color = MaterialTheme.colorScheme.primary)

                    val textProgress = if (totalPhotos > 0) AppStrings.uploadProgressText(currentProgress, totalPhotos) else AppStrings.uploadPreparing
                    Text(textProgress, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Button(onClick = { isUploadPopupHidden = true }, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                        Text(AppStrings.hide, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // 2. POP-UP DE FIN (SUCCÈS OU ERREUR PARTIELLE)
    if (showFinishedDialog) {
        val hasErrors = finalSuccessCount < totalPhotos
        val finalIcon = if (hasErrors) Icons.Default.Warning else Icons.Default.CheckCircle
        val iconColor = if (hasErrors) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)

        Dialog(
            onDismissRequest = { /* L'utilisateur doit cliquer sur le bouton */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(shape = blockShape, color = sheetBgColor, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(finalIcon, contentDescription = null, tint = iconColor, modifier = Modifier.size(64.dp))

                    Text(AppStrings.uploadFinishedTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                    Text(
                        text = AppStrings.uploadResultText(finalSuccessCount, totalPhotos),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (hasErrors) {
                        Text(
                            text = AppStrings.uploadErrorWarning,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Text(
                        text = AppStrings.uploadLifetimeScore(lifetimeScore + finalSuccessCount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { showFinishedDialog = false },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(AppStrings.awesome, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
