package fr.geotower.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.geotower.services.garmin.GarminCompanionManager
import fr.geotower.ui.components.GeoTowerSwitch
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppFileLog

@Composable
fun GarminSettingsCard(
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean,
    modifier: Modifier = Modifier,
    initialOpenDialog: Boolean = false
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var showDialog by remember(initialOpenDialog) { mutableStateOf(initialOpenDialog) }

    androidx.compose.runtime.LaunchedEffect(initialOpenDialog) {
        if (initialOpenDialog) {
            showDialog = true
        }
    }

    val isGcmInstalled = remember { GarminCompanionManager.isGarminConnectInstalled(context) }
    val isReady by GarminCompanionManager.isSdkReady.collectAsStateWithLifecycle()
    val watches by GarminCompanionManager.connectedWatches.collectAsStateWithLifecycle()
    val lastSync by GarminCompanionManager.lastSyncTimestamp.collectAsStateWithLifecycle()
    val liveLogs by GarminCompanionManager.logs.collectAsStateWithLifecycle()
    val lastError by GarminCompanionManager.lastError.collectAsStateWithLifecycle()
    val lastReceivedPayload by GarminCompanionManager.lastReceivedPayload.collectAsStateWithLifecycle()
    val sdkStatusMsg by GarminCompanionManager.lastStatusMessage.collectAsStateWithLifecycle()

    // Lecture du journal persistant sur disque (conserve les plantages précédents même après redémarrage de l'app)
    var diskLogText by remember(showDialog) {
        mutableStateOf(if (showDialog) AppFileLog.readText(context) else "")
    }

    androidx.compose.runtime.LaunchedEffect(showDialog) {
        if (showDialog) {
            diskLogText = AppFileLog.readText(context)
            if (isGcmInstalled) {
                GarminCompanionManager.refreshConnectedDevices()
            }
        }
    }

    val diskCrash = remember(diskLogText) {
        extractLastCrash(diskLogText)
    }

    val combinedLogs = remember(diskLogText, liveLogs) {
        val diskGarminLines = diskLogText.lines().filter {
            it.contains("GarminBridge") || it.contains("E/Crash") || it.contains("CrashHandler")
        }.takeLast(60)
        (diskGarminLines + liveLogs).distinct().takeLast(80)
    }

    val subtitle = remember(isGcmInstalled, isReady, watches, lastSync) {
        when {
            !isGcmInstalled -> "Optionnel • Requis uniquement si vous utilisez une montre Garmin"
            !isReady -> "En attente de Garmin Connect Mobile"
            watches.isNotEmpty() -> {
                val watchNames = watches.joinToString(", ") { it.name }
                if (lastSync > 0) {
                    "$watchNames • Synchronisé en BLE"
                } else {
                    "$watchNames • Connecté en BLE"
                }
            }
            else -> "Service actif • En attente de connexion avec la montre"
        }
    }

    Surface(
        onClick = {
            if (isGcmInstalled) {
                GarminCompanionManager.refreshConnectedDevices()
            }
            diskLogText = AppFileLog.readText(context)
            showDialog = true
        },
        shape = shape,
        border = border,
        color = cardBg,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(sizing.spacing(16.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Watch,
                contentDescription = null,
                tint = if (watches.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(sizing.component(24.dp))
            )
            Spacer(Modifier.width(sizing.spacing(16.dp)))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Montres connectées Garmin (BLE)",
                    style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(sizing.spacing(8.dp)))
            Icon(
                Icons.Default.BugReport,
                contentDescription = "Diagnostic BLE",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(sizing.component(20.dp))
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            icon = {
                Icon(
                    Icons.Default.Watch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Moniteur & Journal BLE Garmin",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Statut de connexion et SDK
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "État du service BLE :",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = "• SDK Connect IQ : ${sdkStatusMsg ?: if (isReady) "Prêt" else "En attente"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            val watchDetails = if (watches.isNotEmpty()) {
                                watches.joinToString(", ") { "${it.name} (${it.status})" }
                            } else {
                                "Aucune montre détectée (vérifiez Garmin Connect)"
                            }
                            Text(
                                text = "• Montres : $watchDetails",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (lastReceivedPayload != null) {
                                Text(
                                    text = "• Dernier échange : $lastReceivedPayload",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // Option Service d'arrière-plan écran éteint
                    val isBgEnabled by GarminCompanionManager.isBackgroundServiceEnabled.collectAsStateWithLifecycle()
                    val selectedDuration by GarminCompanionManager.backgroundDurationMs.collectAsStateWithLifecycle()
                    val startTime by GarminCompanionManager.serviceStartTimeMs.collectAsStateWithLifecycle()

                    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                    androidx.compose.runtime.LaunchedEffect(isBgEnabled) {
                        while (isBgEnabled) {
                            kotlinx.coroutines.delay(1000)
                            now = System.currentTimeMillis()
                        }
                    }

                    val timerText = remember(isBgEnabled, selectedDuration, startTime, now) {
                        if (!isBgEnabled || startTime <= 0L) null
                        else if (selectedDuration == 0L) {
                            val elapsedSec = maxOf(0L, (now - startTime) / 1000L)
                            val h = elapsedSec / 3600
                            val m = (elapsedSec % 3600) / 60
                            val s = elapsedSec % 60
                            String.format(java.util.Locale.ROOT, "⏱️ Chrono : %02d:%02d:%02d (Illimité)", h, m, s)
                        } else {
                            val remainingSec = maxOf(0L, ((startTime + selectedDuration) - now) / 1000L)
                            val h = remainingSec / 3600
                            val m = (remainingSec % 3600) / 60
                            val s = remainingSec % 60
                            val totalHours = selectedDuration / 3600000L
                            String.format(java.util.Locale.ROOT, "⏳ Décompte : %02d:%02d:%02d restant(s) sur %dh", h, m, s, totalHours)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = sizing.spacing(12.dp), vertical = sizing.spacing(8.dp))) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = sizing.spacing(12.dp))) {
                                    Text(
                                        text = "Liaison écran éteint",
                                        style = sizing.textStyle(MaterialTheme.typography.titleSmall),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Répondre avec le téléphone dans la poche",
                                        style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                GeoTowerSwitch(
                                    checked = isBgEnabled,
                                    onCheckedChange = { checked ->
                                        GarminCompanionManager.setBackgroundServiceEnabled(context, checked)
                                    },
                                    useOneUi = useOneUi
                                )
                            }

                            // Sélecteur de durée (Illimité, 1h, 2h, 4h, 8h)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = sizing.spacing(6.dp))
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(sizing.spacing(6.dp))
                            ) {
                                GarminCompanionManager.DURATION_CHOICES.forEach { (durationMs, label) ->
                                    FilterChip(
                                        selected = selectedDuration == durationMs,
                                        onClick = {
                                            GarminCompanionManager.setBackgroundDuration(context, durationMs)
                                        },
                                        label = {
                                            Text(
                                                text = label,
                                                fontSize = sizing.text(12.sp)
                                            )
                                        }
                                    )
                                }
                            }

                            // Compteur ou information de durée
                            Spacer(Modifier.height(sizing.spacing(4.dp)))
                            if (timerText != null) {
                                Text(
                                    text = timerText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = if (selectedDuration == 0L) "⏱️ Durée : Illimité" else "⏱️ Durée : ${selectedDuration / 3600000L}h avant coupure automatique",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // Crash précédent intercepté sur disque (survit au redémarrage du processus)
                    if (diskCrash != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Plantage détecté (enregistré sur disque) :",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = diskCrash,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 140.dp)
                                        .verticalScroll(rememberScrollState())
                                )
                                Spacer(Modifier.height(6.dp))
                                Button(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(diskCrash))
                                        Toast.makeText(context, "Rapport de crash copié !", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copier le crash", fontSize = 12.sp)
                                }
                            }
                        }
                    } else if (lastError != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = lastError ?: "",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Boutons d'action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                GarminCompanionManager.pingConnectedWatches()
                                Toast.makeText(context, "Ping BLE envoyé !", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Ping test", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val fullLog = combinedLogs.joinToString("\n")
                                if (fullLog.isNotBlank()) {
                                    clipboardManager.setText(AnnotatedString(fullLog))
                                    Toast.makeText(context, "Logs BLE copiés dans le presse-papier", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Aucun log à copier", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copier", fontSize = 12.sp)
                        }
                    }

                    // Partage complet du journal de diagnostic
                    OutlinedButton(
                        onClick = {
                            if (!AppFileLog.share(context, "Partager le journal GeoTower")) {
                                Toast.makeText(context, "Journal vide ou impossible à partager", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Partager tout le journal de débogage (disque)", fontSize = 12.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Journal consolidé (${combinedLogs.size} entrées) :",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(
                            onClick = {
                                GarminCompanionManager.clearLogs()
                                AppFileLog.clear(context)
                                diskLogText = ""
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Effacer", fontSize = 11.sp, maxLines = 1, softWrap = false)
                        }
                    }

                    // Zone de log style terminal
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 240.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (combinedLogs.isEmpty()) {
                            Text(
                                text = "Aucun événement BLE enregistré.\n\nLancez la recherche d'antennes sur la montre Garmin pour observer les échanges et requêtes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        } else {
                            Text(
                                text = combinedLogs.joinToString("\n"),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showDialog = false }) {
                    Text("Fermer")
                }
            }
        )
    }
}

private fun extractLastCrash(fullLog: String): String? {
    if (fullLog.isBlank()) return null
    val marker = "E/Crash:"
    val crashIndex = fullLog.lastIndexOf(marker)
    if (crashIndex == -1) return null
    // Remonter un peu avant le marqueur pour avoir l'horodatage
    val lineStart = fullLog.lastIndexOf('\n', crashIndex).let { if (it == -1) 0 else it + 1 }
    val snippet = fullLog.substring(lineStart)
    return snippet.take(2500).trim()
}
