package fr.geotower.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.geotower.services.garmin.GarminCompanionManager
import fr.geotower.ui.theme.LocalGeoTowerUiStyle

@Composable
fun GarminSettingsCard(
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean,
    modifier: Modifier = Modifier
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var showDialog by remember { mutableStateOf(false) }

    val isGcmInstalled = remember { GarminCompanionManager.isGarminConnectInstalled(context) }
    val isReady by GarminCompanionManager.isSdkReady.collectAsStateWithLifecycle()
    val watches by GarminCompanionManager.connectedWatches.collectAsStateWithLifecycle()
    val lastSync by GarminCompanionManager.lastSyncTimestamp.collectAsStateWithLifecycle()
    val logs by GarminCompanionManager.logs.collectAsStateWithLifecycle()
    val lastError by GarminCompanionManager.lastError.collectAsStateWithLifecycle()
    val lastReceivedPayload by GarminCompanionManager.lastReceivedPayload.collectAsStateWithLifecycle()
    val sdkStatusMsg by GarminCompanionManager.lastStatusMessage.collectAsStateWithLifecycle()

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

                    // Erreur récente mise en avant
                    if (lastError != null) {
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
                                val fullLog = logs.joinToString("\n")
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Journal en direct (${logs.size} entrées) :",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = { GarminCompanionManager.clearLogs() }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Effacer", fontSize = 11.sp)
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
                        if (logs.isEmpty()) {
                            Text(
                                text = "Aucun événement BLE enregistré.\n\nLancez la recherche d'antennes sur la montre Garmin pour observer les échanges et requêtes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        } else {
                            Text(
                                text = logs.joinToString("\n"),
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
