package fr.geotower.ui.screens.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.geotower.R
import fr.geotower.data.backup.AppBackupManager
import fr.geotower.data.backup.BackupImportPreview
import fr.geotower.data.backup.BackupImportResult
import fr.geotower.data.backup.BackupSection
import fr.geotower.data.backup.BackupSectionPreview
import fr.geotower.data.backup.BackupSectionSize
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import java.text.DateFormat
import java.util.Date

/**
 * Page « Sauvegarde et restauration » : produire le fichier qui emporte les données personnelles
 * vers un autre téléphone, et relire celui d'un autre téléphone.
 *
 * L'écran dit deux fois la même chose, parce que c'est la promesse à tenir : **rien n'est jamais
 * supprimé ni remplacé**. L'aperçu affiché avant de confirmer l'import montre, rubrique par
 * rubrique, ce qui manque ici — et rien d'autre ne sera écrit.
 *
 * Toute la logique vit dans [AppBackupManager] ; cette page ne fait que choisir les rubriques et
 * rendre compte.
 */
@Composable
fun BackupScreen(navController: NavController) {
    val context = LocalContext.current
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val scrollState = rememberScrollState()
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "settings")

    var sizes by remember { mutableStateOf<List<BackupSectionSize>>(emptyList()) }
    var reloadTick by remember { mutableStateOf(0) }
    var exportSections by remember { mutableStateOf(BackupSection.ALL.toSet()) }
    var includeThumbnails by remember { mutableStateOf(true) }
    var pendingExportJson by remember { mutableStateOf<String?>(null) }

    var importPreview by remember { mutableStateOf<BackupImportPreview?>(null) }
    var importSections by remember { mutableStateOf<Set<String>>(emptySet()) }
    var importResult by remember { mutableStateOf<BackupImportResult?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = !safeBackNavigation.isLocked) { safeBackNavigation.navigateBack() }

    LaunchedEffect(reloadTick) {
        sizes = AppBackupManager.deviceSizes(context)
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(AppBackupManager.MIME_TYPE)
    ) { uri: Uri? ->
        val json = pendingExportJson
        pendingExportJson = null
        if (uri == null || json == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
            } ?: error("flux indisponible")
        }.onSuccess {
            Toast.makeText(context, R.string.backup_export_saved, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, R.string.backup_export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            } ?: error("fichier illisible")
            AppBackupManager.preview(context, text)
        }.onSuccess { preview ->
            importPreview = preview
            importSections = preview.sections.filter { it.hasChanges }.map { it.section }.toSet()
        }.onFailure {
            importError = context.getString(R.string.backup_import_failed)
        }
    }

    Scaffold(
        containerColor = uiStyle.backgroundColor,
        // Les routes du NavHost padent déjà avec l'innerPadding racine.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.backup_title),
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
                text = stringResource(R.string.backup_desc),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            BackupCard(title = stringResource(R.string.backup_export_title)) {
                Text(
                    text = stringResource(R.string.backup_export_desc),
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(sizing.spacing(4.dp)))

                sizes.forEach { size ->
                    BackupCheckRow(
                        checked = size.section in exportSections,
                        enabled = size.itemCount > 0,
                        title = stringResource(backupSectionTitleRes(size.section)),
                        subtitle = sectionCountLabel(size.section, size.itemCount),
                        onCheckedChange = { checked ->
                            exportSections = if (checked) {
                                exportSections + size.section
                            } else {
                                exportSections - size.section
                            }
                        }
                    )
                }

                if (sizes.any { it.section == BackupSection.PHOTO_UPLOADS && it.itemCount > 0 }) {
                    BackupCheckRow(
                        checked = includeThumbnails,
                        enabled = BackupSection.PHOTO_UPLOADS in exportSections,
                        title = stringResource(R.string.backup_export_thumbnails),
                        subtitle = stringResource(R.string.backup_export_thumbnails_desc),
                        onCheckedChange = { includeThumbnails = it }
                    )
                }

                Spacer(modifier = Modifier.height(sizing.spacing(4.dp)))

                // Une rubrique vide n'entre pas dans le fichier : l'aperçu de l'import ne doit pas
                // annoncer une rubrique qui n'apportera rien.
                val filledSections = exportSections.filterTo(mutableSetOf()) { section ->
                    sizes.any { it.section == section && it.itemCount > 0 }
                }
                val exportable = filledSections.isNotEmpty()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp))
                ) {
                    Button(
                        enabled = exportable,
                        onClick = {
                            pendingExportJson = AppBackupManager.buildBackup(
                                context = context,
                                sections = filledSections,
                                includeThumbnails = includeThumbnails
                            )
                            saveLauncher.launch(AppBackupManager.suggestedFileName())
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(sizing.component(18.dp))
                        )
                        Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                        Text(stringResource(R.string.backup_export_save))
                    }
                    OutlinedButton(
                        enabled = exportable,
                        onClick = {
                            val json = AppBackupManager.buildBackup(
                                context = context,
                                sections = filledSections,
                                includeThumbnails = includeThumbnails
                            )
                            runCatching {
                                AppBackupManager.shareBackup(
                                    context = context,
                                    json = json,
                                    fileName = AppBackupManager.suggestedFileName(),
                                    chooserTitle = context.getString(R.string.backup_title)
                                )
                            }.onFailure {
                                Toast.makeText(context, R.string.backup_export_failed, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(sizing.component(18.dp))
                        )
                        Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                        Text(stringResource(R.string.backup_export_share))
                    }
                }
            }

            BackupCard(title = stringResource(R.string.backup_import_title)) {
                Text(
                    text = stringResource(R.string.backup_import_desc),
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(sizing.spacing(4.dp)))
                Button(
                    onClick = { pickLauncher.launch(arrayOf(AppBackupManager.MIME_TYPE, "*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(sizing.component(18.dp))
                    )
                    Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                    Text(stringResource(R.string.backup_import_pick))
                }
            }

            Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))
        }
    }

    importPreview?.let { preview ->
        BackupImportPreviewDialog(
            preview = preview,
            selectedSections = importSections,
            onToggleSection = { section, checked ->
                importSections = if (checked) importSections + section else importSections - section
            },
            onDismiss = { importPreview = null },
            onConfirm = {
                importResult = AppBackupManager.importBackup(context, preview, importSections)
                importPreview = null
                reloadTick++
            }
        )
    }

    importResult?.let { result ->
        BackupImportResultDialog(result = result, onDismiss = { importResult = null })
    }

    importError?.let { message ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text(stringResource(R.string.backup_import_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { importError = null }) {
                    Text(stringResource(R.string.appstrings_close))
                }
            }
        )
    }
}

@Composable
private fun BackupImportPreviewDialog(
    preview: BackupImportPreview,
    selectedSections: Set<String>,
    onToggleSection: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val scrollState = rememberScrollState()
    val changed = preview.sections.filter { it.hasChanges }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_import_preview_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = sizing.component(420.dp))
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(sizing.spacing(6.dp))
            ) {
                Text(
                    text = listOfNotNull(
                        preview.deviceLabel.takeIf { it.isNotBlank() },
                        preview.appVersionName.takeIf { it.isNotBlank() }
                            ?.let { stringResource(R.string.backup_import_preview_version, it) },
                        preview.exportedAtMillis.takeIf { it > 0L }?.let { formatBackupDate(it) }
                    ).joinToString(" · "),
                    style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (changed.isEmpty()) {
                    Text(
                        text = stringResource(R.string.backup_import_preview_nothing),
                        style = sizing.textStyle(MaterialTheme.typography.bodyMedium)
                    )
                } else {
                    changed.forEach { section ->
                        BackupCheckRow(
                            checked = section.section in selectedSections,
                            enabled = true,
                            title = stringResource(backupSectionTitleRes(section.section)),
                            subtitle = sectionPreviewLabel(section),
                            onCheckedChange = { checked -> onToggleSection(section.section, checked) }
                        )
                    }

                    if (BackupSection.SETTINGS_PROFILES in selectedSections) {
                        Text(
                            text = stringResource(R.string.backup_import_profiles_note),
                            style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (preview.unknownSections.isNotEmpty()) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.backup_unknown_sections,
                            preview.unknownSections.size,
                            preview.unknownSections.size
                        ),
                        style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = changed.isNotEmpty() && selectedSections.isNotEmpty(),
                onClick = onConfirm
            ) {
                Text(stringResource(R.string.backup_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.appstrings_cancel)) }
        }
    )
}

@Composable
private fun BackupImportResultDialog(result: BackupImportResult, onDismiss: () -> Unit) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val touched = result.outcomes.filter { it.touched > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_import_result_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(sizing.spacing(4.dp))) {
                if (touched.isEmpty()) {
                    Text(
                        text = stringResource(R.string.backup_import_result_nothing),
                        style = sizing.textStyle(MaterialTheme.typography.bodyMedium)
                    )
                } else {
                    touched.forEach { outcome ->
                        val detail = listOfNotNull(
                            outcome.added.takeIf { it > 0 }?.let {
                                pluralStringResource(R.plurals.backup_added_items, it, it)
                            },
                            outcome.refreshed.takeIf { it > 0 }?.let {
                                stringResource(R.string.backup_refreshed_items, it)
                            }
                        ).joinToString(" · ")
                        Text(
                            text = "${stringResource(backupSectionTitleRes(outcome.section))} — $detail",
                            style = sizing.textStyle(MaterialTheme.typography.bodyMedium)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.appstrings_close)) }
        }
    )
}

@Composable
private fun BackupCard(title: String, content: @Composable () -> Unit) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Card(
        shape = RoundedCornerShape(sizing.component(14.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(sizing.spacing(14.dp)),
            verticalArrangement = Arrangement.spacedBy(sizing.spacing(6.dp))
        ) {
            Text(
                text = title,
                style = sizing.textStyle(MaterialTheme.typography.titleSmall),
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}

@Composable
private fun BackupCheckRow(
    checked: Boolean,
    enabled: Boolean,
    title: String,
    subtitle: String,
    onCheckedChange: (Boolean) -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked && enabled, enabled = enabled, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(sizing.spacing(4.dp)))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = subtitle,
                style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Ce que la rubrique contient sur cet appareil. Le compteur cumulé n'est pas une collection
 * d'éléments mais un nombre de photos : il se dit autrement, sinon la ligne annoncerait
 * « 1 248 éléments » pour une seule valeur.
 */
@Composable
private fun sectionCountLabel(section: String, count: Int): String = when {
    count == 0 -> stringResource(R.string.backup_section_empty)
    section == BackupSection.COUNTERS -> pluralStringResource(R.plurals.backup_photos_sent, count, count)
    else -> pluralStringResource(R.plurals.backup_items, count, count)
}

/** Ce que la rubrique apporterait ici. Le compteur, lui, monte ou ne bouge pas. */
@Composable
private fun sectionPreviewLabel(preview: BackupSectionPreview): String {
    if (preview.section == BackupSection.COUNTERS) {
        return stringResource(R.string.backup_section_counter_higher)
    }
    return listOfNotNull(
        preview.newCount.takeIf { it > 0 }?.let {
            pluralStringResource(R.plurals.backup_new_items, it, it)
        },
        preview.refreshableCount.takeIf { it > 0 }?.let {
            stringResource(R.string.backup_section_to_update, it)
        },
        preview.alreadyPresentCount.takeIf { it > 0 }?.let {
            pluralStringResource(R.plurals.backup_already_present, it, it)
        }
    ).joinToString(" · ")
}

private fun backupSectionTitleRes(section: String): Int = when (section) {
    BackupSection.SHARE_HISTORY -> R.string.backup_section_share_history
    BackupSection.NOTIFICATION_HISTORY -> R.string.backup_section_notification_history
    BackupSection.PHOTO_UPLOADS -> R.string.backup_section_photo_uploads
    BackupSection.PHOTO_REPORTS -> R.string.backup_section_photo_reports
    BackupSection.TRIPS -> R.string.backup_section_trips
    BackupSection.PHOTO_FAVORITES -> R.string.backup_section_photo_favorites
    BackupSection.COUNTERS -> R.string.backup_section_counters
    BackupSection.SETTINGS_PROFILES -> R.string.backup_section_settings_profiles
    else -> R.string.backup_section_unknown
}

private fun formatBackupDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
