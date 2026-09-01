package fr.geotower.ui.screens.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.platform.LocalContext
import fr.geotower.R
import fr.geotower.data.api.SignalQuestPhotoReportReasons
import fr.geotower.data.community.PhotoReportHistoryEntry
import fr.geotower.data.community.PhotoReportHistoryStore
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.LocalizedDateLabels

/**
 * Page « Mes signalements » : ce que l'utilisateur a signalé à SignalQuest, et où ça en est.
 *
 * Deux états seulement, et c'est une limite de l'API, pas un raccourci : elle est en écriture seule.
 * On sait qu'un signalement est **parti**, et on voit qu'une photo a fini par **disparaître** des
 * photos publiées. Un refus, lui, ressemble en tout point à une attente — la page ne prétend donc
 * jamais dire « refusé ».
 */
@Composable
fun PhotoReportsScreen(navController: NavController) {
    val context = LocalContext.current
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val scrollState = rememberScrollState()
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "settings")

    var entries by remember { mutableStateOf<List<PhotoReportHistoryEntry>>(emptyList()) }
    var pendingDeletion by remember { mutableStateOf<PhotoReportHistoryEntry?>(null) }
    var reloadTick by remember { mutableStateOf(0) }

    BackHandler(enabled = !safeBackNavigation.isLocked) {
        safeBackNavigation.navigateBack()
    }

    LaunchedEffect(reloadTick) {
        entries = PhotoReportHistoryStore.read(context)
    }

    Scaffold(
        containerColor = uiStyle.backgroundColor,
        // Les routes du NavHost padent déjà avec l'innerPadding racine.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.photo_reports_title),
                onBack = { safeBackNavigation.navigateBack() },
                backEnabled = !safeBackNavigation.isLocked
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .geoTowerFadingEdge(scrollState)
                .verticalScroll(scrollState)
                .padding(horizontal = sizing.spacing(16.dp)),
            verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
        ) {
            Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))

            Text(
                text = stringResource(R.string.photo_reports_intro),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = sizing.spacing(48.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.photo_reports_empty),
                        style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            entries.forEach { entry ->
                PhotoReportRow(
                    entry = entry,
                    onDelete = { pendingDeletion = entry }
                )
            }

            Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))
        }
    }

    pendingDeletion?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text(stringResource(R.string.photo_reports_delete_title)) },
            text = { Text(stringResource(R.string.photo_reports_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    PhotoReportHistoryStore.remove(context, listOf(entry.id))
                    pendingDeletion = null
                    reloadTick++
                }) {
                    Text(stringResource(R.string.photo_reports_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text(stringResource(R.string.appstrings_cancel))
                }
            }
        )
    }
}

@Composable
private fun PhotoReportRow(
    entry: PhotoReportHistoryEntry,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val removed = entry.status == PhotoReportHistoryStore.STATUS_REMOVED

    Card(
        shape = RoundedCornerShape(sizing.component(14.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(sizing.spacing(14.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (removed) Icons.Default.CheckCircle else Icons.Default.Schedule,
                contentDescription = null,
                tint = if (removed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(sizing.component(24.dp))
            )
            Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(photoReportReasonLabelRes(entry.reason)),
                    style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        if (removed) {
                            R.string.photo_reports_status_removed
                        } else {
                            R.string.photo_reports_status_sent
                        }
                    ),
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val subtitle = listOfNotNull(
                    entry.operatorLabel?.takeIf { it.isNotBlank() },
                    entry.siteId.takeIf { it.isNotBlank() },
                    formatReportDate(context, entry.createdAtMillis, AppConfig.appLanguage.value)
                ).joinToString(" · ")
                Text(
                    text = subtitle,
                    style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.photo_reports_delete_title),
                    modifier = Modifier.size(sizing.component(20.dp))
                )
            }
        }
    }
}

private fun formatReportDate(context: Context, millis: Long, languagePreference: String): String =
    LocalizedDateLabels.formatLongDate(context, millis, languagePreference)

private fun photoReportReasonLabelRes(reason: String): Int = when (reason) {
    SignalQuestPhotoReportReasons.WRONG_LOCATION -> R.string.appstrings_photo_report_reason_wrong_location
    SignalQuestPhotoReportReasons.QUALITY -> R.string.appstrings_photo_report_reason_quality
    SignalQuestPhotoReportReasons.INAPPROPRIATE -> R.string.appstrings_photo_report_reason_inappropriate
    SignalQuestPhotoReportReasons.COPYRIGHT -> R.string.appstrings_photo_report_reason_copyright
    SignalQuestPhotoReportReasons.SPAM -> R.string.appstrings_photo_report_reason_spam
    else -> R.string.appstrings_photo_report_reason_other
}
