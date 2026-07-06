package fr.geotower.ui.components

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.WifiTethering
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.R
import fr.geotower.data.api.RadioDatabaseDownloader
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.db.DatabaseVersionPolicy
import fr.geotower.data.db.RadioDatabaseValidator
import fr.geotower.data.workers.RadioDatabaseDownloadWorker
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RadioDatabaseDownloadCard(
    useOneUi: Boolean,
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    onSafeClick: SafeClick? = null
) {
    val context = LocalContext.current
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val workManager = remember { androidx.work.WorkManager.getInstance(context) }
    val safeClick = onSafeClick ?: rememberSafeClick()
    val featureFlags by RemoteFeatureFlags.config
    val workInfos by workManager.getWorkInfosForUniqueWorkFlow(RadioDatabaseDownloadWorker.UNIQUE_WORK_NAME).collectAsState(initial = emptyList())
    val currentWork = workInfos.firstOrNull()

    val isSyncing = currentWork?.state == androidx.work.WorkInfo.State.RUNNING ||
        currentWork?.state == androidx.work.WorkInfo.State.ENQUEUED
    val downloadProgress = currentWork?.progress?.getInt(RadioDatabaseDownloadWorker.KEY_PROGRESS, 0)?.div(100f) ?: 0f

    val txtSearching = stringResource(R.string.database_searching)
    val txtUnknown = stringResource(R.string.database_unknown)
    val txtNoDb = stringResource(R.string.database_not_installed)
    val txtInvalidDb = stringResource(R.string.database_invalid_local)
    val txtLatestDb = stringResource(R.string.database_latest_available)
    val txtDownloadedDb = stringResource(R.string.database_currently_downloaded)
    val canStartDownload =
        featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.DATABASE_DOWNLOAD) &&
            featureFlags.isActionEnabled(RemoteFeatureFlags.Actions.START_DATABASE_DOWNLOAD) &&
            featureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.DATABASE_DOWNLOAD)

    var dbSizeMb by remember { mutableDoubleStateOf(-1.0) }
    var localVersion by remember { mutableStateOf(txtSearching) }
    var localVersionRaw by remember { mutableStateOf<String?>(null) }
    var remoteVersion by remember { mutableStateOf(txtSearching) }
    var remoteVersionRaw by remember { mutableStateOf<String?>(null) }
    var localAnfrDate by remember { mutableStateOf("") }
    var localRowCount by remember { mutableStateOf<Int?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(isSyncing, refreshTrigger, txtSearching, txtUnknown, txtNoDb, txtInvalidDb, featureFlags) {
        withContext(Dispatchers.IO) {
            val dbPath = context.getDatabasePath(RadioDatabaseValidator.DB_NAME)
            var nextLocalVersion = txtNoDb
            var nextLocalVersionRaw: String? = null
            var nextLocalAnfrDate = ""
            var nextRowCount: Int? = null

            if (dbPath.exists()) {
                val validation = RadioDatabaseValidator.validateDatabaseFile(dbPath)
                if (validation.isValid) {
                    readRadioMetadata(dbPath)?.let { metadata ->
                        nextLocalVersionRaw = metadata.version
                        nextLocalVersion = formatRadioVersion(metadata.version) ?: txtUnknown
                        nextLocalAnfrDate = formatRadioDate(metadata.dateMajAnfr).orEmpty()
                        nextRowCount = metadata.rowCount
                    } ?: run {
                        nextLocalVersion = txtInvalidDb
                    }
                } else {
                    nextLocalVersion = txtInvalidDb
                }
            }

            localVersion = nextLocalVersion
            localVersionRaw = nextLocalVersionRaw
            localAnfrDate = nextLocalAnfrDate
            localRowCount = nextRowCount

            remoteVersion = try {
                val remote = RadioDatabaseDownloader.getLatestDatabaseVersion()
                remoteVersionRaw = remote
                formatRadioVersion(remote) ?: txtUnknown
            } catch (e: Exception) {
                remoteVersionRaw = null
                txtUnknown
            }

            dbSizeMb = try {
                RadioDatabaseDownloader.getDatabaseSize()
            } catch (e: Exception) {
                -1.0
            }
        }
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
                        imageVector = Icons.Default.WifiTethering,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(sizing.component(24.dp))
                    )
                }
                Spacer(modifier = Modifier.width(textStartPadding))
                Text(
                    text = stringResource(R.string.appstrings_radio_data_title),
                    fontWeight = FontWeight.Bold,
                    style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                    textAlign = TextAlign.Start
                )
            }

            RadioInfoRow(
                icon = { Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(sizing.component(18.dp))) },
                label = txtLatestDb,
                value = remoteVersion,
                iconColumnWidth = iconColumnWidth,
                textStartPadding = textStartPadding
            )
            RadioInfoRow(
                icon = { Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(sizing.component(18.dp))) },
                label = txtDownloadedDb,
                value = localVersion,
                iconColumnWidth = iconColumnWidth,
                textStartPadding = textStartPadding
            )

            val sizeText = when {
                dbSizeMb < 0.0 -> stringResource(R.string.database_calculating_size)
                dbSizeMb == 0.0 -> stringResource(R.string.database_unknown_size)
                else -> stringResource(R.string.database_size_warning, dbSizeMb)
            }

            Text(
                text = sizeText,
                modifier = Modifier.fillMaxWidth(),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            localRowCount?.takeIf { it > 0 }?.let { count ->
                Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
                Text(
                    text = "Sites radio : ${"%,d".format(count)}",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = sizing.text(12.sp),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))

            if (isSyncing) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    LinearWavyProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth().height(sizing.component(8.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
                    Text(
                        text = stringResource(R.string.database_download_progress, (downloadProgress * 100).toInt()),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))
                    OutlinedButton(
                        onClick = {
                            safeClick("radio_database_cancel_download") {
                                workManager.cancelUniqueWork(RadioDatabaseDownloadWorker.UNIQUE_WORK_NAME)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = sizing.component(50.dp)),
                        shape = RoundedCornerShape(sizing.component(12.dp)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(sizing.component(24.dp)))
                        Spacer(Modifier.width(sizing.spacing(8.dp)))
                        Text(text = stringResource(R.string.database_cancel_download), fontWeight = FontWeight.Bold, style = sizing.textStyle(MaterialTheme.typography.labelLarge))
                    }
                }
            } else {
                val isUpToDate = DatabaseVersionPolicy.isLocalCurrentOrNewer(remoteVersionRaw, localVersionRaw)
                val isSearching = localVersion == txtSearching || remoteVersion == txtSearching
                val canDownload = remoteVersionRaw != null && canStartDownload

                Button(
                    onClick = {
                        safeClick("radio_database_start_download") {
                            if (RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.DATABASE_DOWNLOAD) &&
                                RemoteFeatureFlags.isActionEnabled(RemoteFeatureFlags.Actions.START_DATABASE_DOWNLOAD) &&
                                RemoteFeatureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.DATABASE_DOWNLOAD)
                            ) {
                                RadioDatabaseDownloadWorker.enqueue(workManager)
                            }
                        }
                    },
                    enabled = canDownload && !isUpToDate && !isSearching,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = sizing.component(56.dp)),
                    shape = RoundedCornerShape(sizing.component(12.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isUpToDate) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                        contentColor = if (isUpToDate) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isUpToDate) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(sizing.component(24.dp)))
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(sizing.component(24.dp)))
                    }
                    Spacer(Modifier.width(sizing.spacing(8.dp)))
                    Text(
                        text = if (isUpToDate) stringResource(R.string.database_up_to_date) else "Télécharger les radios",
                        fontWeight = FontWeight.Bold,
                        style = sizing.textStyle(MaterialTheme.typography.labelLarge)
                    )
                }
            }

            if (localAnfrDate.isNotEmpty() && localAnfrDate != txtUnknown) {
                Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))
                Text(
                    text = stringResource(R.string.database_weekly_downloaded_from, localAnfrDate),
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = sizing.text(12.sp),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            if (localVersion != txtNoDb && localVersion != txtUnknown && localVersion != txtInvalidDb && !isSyncing) {
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
                    Text(text = stringResource(R.string.database_delete_data), fontWeight = FontWeight.Bold, style = sizing.textStyle(MaterialTheme.typography.labelLarge))
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = stringResource(R.string.database_delete_warning_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.database_delete_warning_desc)) },
            shape = shape,
            containerColor = MaterialTheme.colorScheme.surface,
            dismissButton = {
                DialogDestructiveButton(text = stringResource(R.string.common_yes), onClick = {
                    showDeleteDialog = false
                    deleteRadioDatabase(context)
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
private fun RadioInfoRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    iconColumnWidth: androidx.compose.ui.unit.Dp,
    textStartPadding: androidx.compose.ui.unit.Dp
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

private data class RadioMetadata(
    val version: String,
    val dateMajAnfr: String?,
    val rowCount: Int?
)

private fun readRadioMetadata(dbPath: File): RadioMetadata? {
    val db = SQLiteDatabase.openDatabase(dbPath.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    return db.use {
        val cursor = it.rawQuery("SELECT version, date_maj_anfr, row_count FROM metadata LIMIT 1", null)
        cursor.use { c ->
            if (!c.moveToFirst()) {
                null
            } else {
                RadioMetadata(
                    version = c.getString(0),
                    dateMajAnfr = if (c.isNull(1)) null else c.getString(1),
                    rowCount = if (c.isNull(2)) null else c.getInt(2)
                )
            }
        }
    }
}

private fun formatRadioVersion(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val match = Regex("""(20\d{2})\D?(\d{2})\D?(\d{2})(?:\D?(\d{2})\D?(\d{2}))?""")
        .find(raw)
        ?: return raw
    val date = "${match.groupValues[3]}/${match.groupValues[2]}/${match.groupValues[1]}"
    val hour = match.groupValues.getOrNull(4).orEmpty()
    val minute = match.groupValues.getOrNull(5).orEmpty()
    return if (hour.isNotBlank() && minute.isNotBlank()) "$date - $hour:$minute" else date
}

private fun formatRadioDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val parts = raw.substringBefore("T").split("-")
    return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else raw
}

private fun deleteRadioDatabase(context: Context) {
    val dbFile = context.getDatabasePath(RadioDatabaseValidator.DB_NAME)
    context.deleteDatabase(RadioDatabaseValidator.DB_NAME)
    listOf(
        File(dbFile.path + "-wal"),
        File(dbFile.path + "-shm"),
        File(dbFile.path + ".download"),
        File(dbFile.path + ".backup")
    ).forEach { file ->
        if (file.exists()) file.delete()
    }
}
