package fr.geotower.ui.components

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.outages.OutageLocalConfig
import fr.geotower.data.outages.OutageServerInfo
import fr.geotower.data.outages.ServerOutageCache
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Ce que la carte sait de la copie conservée : lu d'un coup, hors du fil principal. */
private data class ServerOutageSummary(
    val downloadedAtMillis: Long = 0L,
    val serverGeneratedAtMillis: Long = 0L,
    val serverDate: String = "-",
    val count: Int = -1,
    val breakdown: List<Pair<String, Int>> = emptyList(),
    val sizeBytes: Long = 0L,
) {
    val hasCopy: Boolean get() = sizeBytes > 0L
}

/**
 * Carte « Sites en panne » de la section Base de données des réglages.
 *
 * Elle suit la source RÉELLEMENT active plutôt que d'en imposer une :
 *  - traitement local au niveau 0 → carte de **téléchargement** du fichier national du serveur,
 *    conservé sur l'appareil pour l'afficher au lancement et hors ligne ;
 *  - traitement local aux crans « pannes en local » → carte de **génération**, mêmes commandes
 *    que l'écran « Provenance des données » ([OutageLocalGenerationControls]).
 *
 * Avant, la variante locale se contentait de griser un bouton de téléchargement inutile : l'unique
 * emplacement « pannes » de la section pointait vers une action qui n'était pas celle en vigueur.
 *
 * Pas de WorkManager pour la variante serveur, contrairement aux bases : le fichier est petit et le
 * téléchargement tient dans la durée de vie de l'écran, donc l'état vit dans la composition.
 */
@Composable
fun OutageDownloadCard(
    repository: AnfrRepository,
    useOneUi: Boolean,
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    onSafeClick: SafeClick? = null,
    refreshState: DatabaseRefreshState? = null
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val safeClick = onSafeClick ?: rememberSafeClick()

    // Les pannes sont-elles préparées sur l'appareil ? C'est cette réponse qui décide du rôle
    // de la carte (cf. AppConfig.outagesLocal, seule vérité sur la source).
    val locallyGenerated = AppConfig.outagesLocal()

    // Actualisation de toute la section « Base de données » : clé partagée par les cartes.
    val sectionRefreshKey = refreshState?.refreshKey ?: 0
    DatabaseRefreshMembership(refreshState, DatabaseRefreshIds.OUTAGES)
    // Variante locale : rien à relire côté serveur, la carte rend la main tout de suite pour ne
    // pas retenir l'indicateur d'actualisation de la section.
    LaunchedEffect(sectionRefreshKey, locallyGenerated) {
        if (locallyGenerated) refreshState?.reportRefreshed(DatabaseRefreshIds.OUTAGES, sectionRefreshKey)
    }

    Surface(
        shape = shape,
        border = border,
        color = if (useOneUi) bubbleColor else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(sizing.spacing(16.dp)), horizontalAlignment = Alignment.CenterHorizontally) {
            val iconColumnWidth = sizing.component(24.dp)
            val textStartPadding = sizing.spacing(12.dp)

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.width(iconColumnWidth), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(sizing.component(24.dp))
                    )
                }
                Spacer(modifier = Modifier.width(textStartPadding))
                Text(
                    text = stringResource(R.string.outage_download_title),
                    fontWeight = FontWeight.Bold,
                    style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                    textAlign = TextAlign.Start
                )
            }

            if (locallyGenerated) {
                Text(
                    text = stringResource(R.string.outage_download_local_source_note),
                    modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(12.dp)),
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutageLocalGenerationControls()
            } else {
                OutageServerCopySection(
                    repository = repository,
                    shape = shape,
                    safeClick = safeClick,
                    refreshState = refreshState,
                    sectionRefreshKey = sectionRefreshKey,
                    iconColumnWidth = iconColumnWidth,
                    textStartPadding = textStartPadding,
                )
            }
        }
    }
}

/**
 * Variante SERVEUR : état de la copie conservée sur l'appareil, téléchargement/actualisation à la
 * demande, et suppression. Séparée pour que la carte reste lisible et que la variante locale n'ait
 * pas à porter cet état.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OutageServerCopySection(
    repository: AnfrRepository,
    shape: Shape,
    safeClick: SafeClick,
    refreshState: DatabaseRefreshState?,
    sectionRefreshKey: Int,
    iconColumnWidth: Dp,
    textStartPadding: Dp,
) {
    val context = LocalContext.current
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val scope = rememberCoroutineScope()
    val featureFlags by RemoteFeatureFlags.config

    val outagesAvailable =
        featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.OUTAGES_DATA) &&
            featureFlags.isProviderEnabled(RemoteFeatureFlags.Providers.OUTAGES_GEOTOWER)
    // Kill-switch propre à la demande de régénération : la donnée peut rester servie alors que le
    // serveur ne veut plus qu'on lui en réclame (maintenance, générateur en travaux).
    val rebuildAvailable =
        outagesAvailable && featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.OUTAGES_REBUILD)

    var summary by remember { mutableStateOf(ServerOutageSummary()) }
    var isDownloading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTrigger, sectionRefreshKey, isDownloading) {
        summary = withContext(Dispatchers.IO) { readServerOutageSummary(context) }
        refreshState?.reportRefreshed(DatabaseRefreshIds.OUTAGES, sectionRefreshKey)
    }
    // Un téléchargement automatique a pu aboutir pendant que la page dormait (carte, fiche, widget).
    LifecycleResumeEffect(Unit) {
        refreshTrigger++
        onPauseOrDispose { }
    }

    val txtNever = stringResource(R.string.outage_download_never)
    val txtFailed = stringResource(R.string.outage_download_failed)

    Text(
        text = stringResource(R.string.outage_download_desc),
        modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(12.dp)),
        style = sizing.textStyle(MaterialTheme.typography.bodySmall),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    OutageInfoRow(
        icon = {
            Icon(
                Icons.Default.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(sizing.component(18.dp))
            )
        },
        label = stringResource(R.string.outage_download_server_data),
        value = when {
            summary.serverGeneratedAtMillis > 0L -> formatOutageDateTime(summary.serverGeneratedAtMillis)
            summary.serverDate != "-" -> summary.serverDate
            else -> txtNever
        },
        iconColumnWidth = iconColumnWidth,
        textStartPadding = textStartPadding
    )
    OutageInfoRow(
        icon = {
            Icon(
                Icons.Default.CloudDone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(sizing.component(18.dp))
            )
        },
        label = stringResource(R.string.outage_download_local_copy),
        value = if (summary.hasCopy && summary.downloadedAtMillis > 0L) {
            formatOutageDateTime(summary.downloadedAtMillis)
        } else {
            stringResource(R.string.outage_download_no_copy)
        },
        iconColumnWidth = iconColumnWidth,
        textStartPadding = textStartPadding
    )

    if (summary.hasCopy && summary.count >= 0) {
        Text(
            text = stringResource(R.string.outage_download_count, summary.count),
            modifier = Modifier.fillMaxWidth(),
            fontSize = sizing.text(13.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        if (summary.breakdown.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = sizing.spacing(8.dp)),
                verticalArrangement = Arrangement.spacedBy(sizing.spacing(4.dp)),
            ) {
                summary.breakdown.forEach { (operateur, nombre) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = operateur,
                            style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = nombre.toString(),
                            style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        Text(
            text = stringResource(
                R.string.outage_download_size,
                Formatter.formatShortFileSize(context, summary.sizeBytes)
            ),
            modifier = Modifier.fillMaxWidth().padding(top = sizing.spacing(8.dp)),
            fontSize = sizing.text(12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))

    if (isDownloading) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            // Le serveur ne publie pas la taille du fichier : progression indéterminée.
            LinearWavyProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(sizing.component(8.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
            Text(
                text = stringResource(R.string.outage_download_running),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    } else {
        if (!outagesAvailable) {
            Text(
                text = stringResource(R.string.outage_download_unavailable),
                modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(8.dp)),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Button(
            onClick = {
                safeClick("outage_start_download") {
                    errorText = null
                    isDownloading = true
                    scope.launch {
                        val result = runCatching { repository.downloadServerSitesHs() }
                        isDownloading = false
                        errorText = result.exceptionOrNull()?.let { error ->
                            error.message?.takeIf { it.isNotBlank() } ?: txtFailed
                        }
                        refreshTrigger++
                    }
                }
            },
            enabled = outagesAvailable,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = sizing.component(56.dp)),
            shape = RoundedCornerShape(sizing.component(12.dp)),
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(sizing.component(24.dp)))
            Spacer(Modifier.width(sizing.spacing(8.dp)))
            Text(
                text = stringResource(
                    if (summary.hasCopy) R.string.outage_download_refresh else R.string.outage_download_now
                ),
                fontWeight = FontWeight.Bold,
                style = sizing.textStyle(MaterialTheme.typography.labelLarge),
                textAlign = TextAlign.Center
            )
        }
    }

    errorText?.let { message ->
        Text(
            text = stringResource(R.string.outage_download_error, message),
            modifier = Modifier.fillMaxWidth().padding(top = sizing.spacing(8.dp)),
            style = sizing.textStyle(MaterialTheme.typography.bodySmall),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
    }

    // Le relevé du serveur peut être plus vieux que la panne constatée : on lui demande alors
    // d'aller le refaire, dans la limite de ce qu'il accepte (deux générations par heure).
    if (rebuildAvailable) {
        Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))
        OutageServerRebuildControls(
            repository = repository,
            enabled = !isDownloading,
            onOutagesRefreshed = { refreshTrigger++ },
        )
    }

    if (summary.hasCopy && !isDownloading) {
        Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))
        OutlinedButton(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = sizing.component(50.dp)),
            shape = RoundedCornerShape(sizing.component(12.dp)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(sizing.component(24.dp)))
            Spacer(Modifier.width(sizing.spacing(8.dp)))
            Text(
                text = stringResource(R.string.database_delete_data),
                fontWeight = FontWeight.Bold,
                style = sizing.textStyle(MaterialTheme.typography.labelLarge)
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = stringResource(R.string.database_delete_warning_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.outage_download_delete_desc)) },
            shape = shape,
            containerColor = MaterialTheme.colorScheme.surface,
            dismissButton = {
                DialogDestructiveButton(text = stringResource(R.string.common_yes), onClick = {
                    showDeleteDialog = false
                    repository.clearServerSitesHs()
                    errorText = null
                    refreshTrigger++
                })
            },
            confirmButton = {
                DialogNeutralButton(text = stringResource(R.string.common_no), onClick = { showDeleteDialog = false })
            }
        )
    }
}

@Composable
private fun OutageInfoRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    iconColumnWidth: Dp,
    textStartPadding: Dp
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(8.dp))
    ) {
        Box(
            modifier = Modifier.width(iconColumnWidth).padding(top = sizing.spacing(2.dp)),
            contentAlignment = Alignment.TopCenter
        ) {
            icon()
        }
        Spacer(modifier = Modifier.width(textStartPadding))
        Column {
            Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = sizing.text(14.sp), color = MaterialTheme.colorScheme.onSurface)
            Text(text = value, fontWeight = FontWeight.Bold, fontSize = sizing.text(13.sp), color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Résumé lu des prefs (écrites au téléchargement) + taille réelle du fichier conservé. */
private fun readServerOutageSummary(context: Context): ServerOutageSummary {
    val prefs = context.getSharedPreferences(OutageLocalConfig.PREFS_NAME, Context.MODE_PRIVATE)
    return ServerOutageSummary(
        downloadedAtMillis = OutageServerInfo.downloadedAtMillis(prefs),
        serverGeneratedAtMillis = OutageServerInfo.generatedAtMillis(prefs),
        serverDate = OutageServerInfo.lastUpdate(prefs),
        count = OutageServerInfo.count(prefs),
        breakdown = OutageServerInfo.breakdown(prefs),
        sizeBytes = ServerOutageCache(context).sizeBytes(),
    )
}

private fun formatOutageDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault()).format(Date(millis))
