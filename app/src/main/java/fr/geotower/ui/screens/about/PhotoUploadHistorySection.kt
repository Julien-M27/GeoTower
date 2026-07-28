package fr.geotower.ui.screens.about

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.geotower.data.upload.ExternalPhotoUploadHistoryEntry
import fr.geotower.data.upload.ExternalPhotoUploadHistoryStore
import fr.geotower.data.upload.ExternalPhotoUploadHistoryValidator
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.GeoTowerDateScrollbar
import fr.geotower.ui.components.PageScrollEdgeButtons
import fr.geotower.ui.components.pageScrollbar
import fr.geotower.ui.components.formatHistoryDateTime
import fr.geotower.ui.components.formatHistoryStorageBytes
import fr.geotower.ui.components.geoTowerLazyListFadingEdge
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.PageScrollPrefs
import fr.geotower.utils.PreferenceStores
import fr.geotower.ui.screens.settings.HistoryPageOption
import fr.geotower.ui.screens.settings.HistoryPagePreferences
import fr.geotower.ui.screens.settings.HistoryPageSettingsSheet
import kotlinx.coroutines.delay
import java.io.File
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import fr.geotower.R

private const val VALIDATION_REFRESH_ATTEMPTS = 12
private const val VALIDATION_REFRESH_INTERVAL_MS = 10_000L

@Composable
fun PhotoUploadHistoryShortcut(
    cardShape: Shape,
    cardColor: Color,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
    val safeClick = rememberSafeClick()
    var historyCount by remember { mutableStateOf(0) }
    val sizing = LocalGeoTowerUiStyle.current.sizing

    LaunchedEffect(Unit) {
        historyCount = ExternalPhotoUploadHistoryStore.read(context).size
    }

    Surface(
        onClick = {
            safeClick("photo_upload_history_shortcut") {
                onOpenHistory()
            }
        },
        color = cardColor,
        shape = cardShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(sizing.spacing(16.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.appstrings_upload_history_title),
                    style = sizing.textStyle(MaterialTheme.typography.titleSmall),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (historyCount == 0) {
                        stringResource(R.string.upload_history_no_photo_recorded)
                    } else {
                        pluralStringResource(R.plurals.upload_history_recorded, historyCount, historyCount)
                    },
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoUploadHistoryScreen(
    onNavigateBack: () -> Unit,
    onOpenSite: (String, String) -> Unit
) {
    val safeClick = rememberSafeClick()
    val context = LocalContext.current
    val appContext = context.applicationContext
    val listState = rememberLazyListState()
    val prefs = remember(appContext) {
        appContext.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
    }
    var showSettingsSheet by remember { mutableStateOf(false) }
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showThumbnails by remember { mutableStateOf(HistoryPagePreferences.read(prefs, HistoryPagePreferences.UPLOAD_THUMBNAILS)) }
    var showDateBar by remember { mutableStateOf(HistoryPagePreferences.read(prefs, HistoryPagePreferences.UPLOAD_DATE_BAR)) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var historyItems by remember { mutableStateOf<List<ExternalPhotoUploadHistoryEntry>>(emptyList()) }
    var selectedIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val selectedIdSet = selectedIds.toSet()
    val isSelectionMode = selectedIds.isNotEmpty()
    val selectedItems = historyItems.filter { it.id in selectedIdSet }
    val selectedFreedBytes = ExternalPhotoUploadHistoryStore.estimatedFreedBytes(selectedItems)
    val totalFreedBytes = ExternalPhotoUploadHistoryStore.estimatedFreedBytes(historyItems)
    val isAllSelected = historyItems.isNotEmpty() && selectedIds.size == historyItems.size

    BackHandler {
        if (isSelectionMode) {
            selectedIds = emptyList()
        } else {
            safeClick("photo_upload_history_back") {
                onNavigateBack()
            }
        }
    }

    fun reloadHistory() {
        val nextItems = ExternalPhotoUploadHistoryStore.read(appContext)
        historyItems = nextItems
        val nextIds = nextItems.map { it.id }.toSet()
        selectedIds = selectedIds.filter { it in nextIds }
    }

    fun selectHistoryItem(id: String) {
        if (id !in selectedIds) {
            selectedIds = selectedIds + id
        }
    }

    fun toggleHistoryItem(id: String) {
        selectedIds = if (id in selectedIds) {
            selectedIds.filterNot { it == id }
        } else {
            selectedIds + id
        }
    }

    LaunchedEffect(appContext) {
        repeat(VALIDATION_REFRESH_ATTEMPTS) { attempt ->
            reloadHistory()

            if (ExternalPhotoUploadHistoryStore.hasAwaitingValidation(appContext)) {
                ExternalPhotoUploadHistoryValidator.refreshPendingSignalQuestPhotos(appContext)
                reloadHistory()
            }

            if (attempt < VALIDATION_REFRESH_ATTEMPTS - 1) {
                delay(VALIDATION_REFRESH_INTERVAL_MS)
            }
        }
    }

    val themeMode by AppConfig.themeMode
    val isOled by AppConfig.isOledMode
    val isDark = themeMode == 2 || (themeMode == 0 && isSystemInDarkTheme())
    val pageColor = if (isDark && isOled) Color.Black else MaterialTheme.colorScheme.background
    val cardColor = if (AppConfig.useOneUiDesign) {
        if (isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    val cardShape = if (AppConfig.useOneUiDesign) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)
    val sizing = LocalGeoTowerUiStyle.current.sizing

    Scaffold(
        containerColor = pageColor,
        topBar = {
            if (isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(pageColor)
                        .padding(top = sizing.spacing(2.dp), bottom = sizing.spacing(6.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            safeClick("photo_upload_history_toggle_all") {
                                selectedIds = if (isAllSelected) emptyList() else historyItems.map { it.id }
                            }
                        },
                        modifier = Modifier.padding(start = sizing.spacing(4.dp))
                    ) {
                        Text(if (isAllSelected) stringResource(R.string.appstrings_clear_all) else stringResource(R.string.appstrings_select_all))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { selectedIds = emptyList() },
                        modifier = Modifier.padding(end = sizing.spacing(4.dp))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.appstrings_cancel))
                    }
                }
            } else {
                GeoTowerBackTopBar(
                    title = stringResource(R.string.appstrings_upload_history_title),
                    onBack = {
                        safeClick("photo_upload_history_back") {
                            onNavigateBack()
                        }
                    },
                    backgroundColor = pageColor,
                    actions = {
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.appstrings_settings_title),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .geoTowerLazyListFadingEdge(listState)
                    .pageScrollbar(PageScrollPrefs.PHOTO_UPLOAD_HISTORY, listState),
                contentPadding = PaddingValues(
                    start = sizing.spacing(16.dp),
                    top = sizing.spacing(16.dp),
                    end = sizing.spacing(16.dp),
                    bottom = sizing.spacing(16.dp) +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                        if (isSelectionMode) sizing.spacing(76.dp) else 0.dp
                ),
                verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
            ) {
                if (historyItems.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardColor),
                            shape = cardShape,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.appstrings_upload_history_empty),
                                style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(sizing.spacing(16.dp))
                            )
                        }
                    }
                } else {
                    itemsIndexed(historyItems, key = { _, item -> item.id }) { _, item ->
                        val isSelected = item.id in selectedIdSet
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f)
                                } else {
                                    cardColor
                                }
                            ),
                            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            shape = cardShape,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PhotoUploadHistoryRow(
                                item = item,
                                showThumbnail = showThumbnails,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onOpenSite = { supportId ->
                                    safeClick("photo_upload_history_open_${item.id}") {
                                        onOpenSite(supportId, item.operator)
                                    }
                                },
                                onSelect = { selectHistoryItem(item.id) },
                                onToggleSelection = {
                                    toggleHistoryItem(item.id)
                                }
                            )
                        }
                    }

                    if (!isSelectionMode) {
                        item {
                            TextButton(
                                onClick = {
                                    safeClick("photo_upload_history_clear_open") {
                                        showClearDialog = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = sizing.spacing(8.dp), bottom = sizing.spacing(24.dp))
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(sizing.component(18.dp)))
                                Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                                Text("${stringResource(R.string.appstrings_upload_history_clear)} (${formatUploadHistoryBytes(totalFreedBytes)})")
                            }
                        }
                    }
                }
            }

            PageScrollEdgeButtons(PageScrollPrefs.PHOTO_UPLOAD_HISTORY, listState)

            GeoTowerDateScrollbar(
                listState = listState,
                timestamps = remember(historyItems, showDateBar) {
                    if (showDateBar) historyItems.map { it.createdAtMillis } else emptyList()
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(
                        top = sizing.spacing(12.dp),
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + sizing.spacing(12.dp)
                    )
            )

            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = sizing.spacing(16.dp),
                            end = sizing.spacing(16.dp),
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + sizing.spacing(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val floatingShape = CircleShape
                    Button(
                        onClick = {
                            safeClick("photo_upload_history_delete_selected") {
                                ExternalPhotoUploadHistoryStore.removeEntries(appContext, selectedIds)
                                selectedIds = emptyList()
                                reloadHistory()
                            }
                        },
                        shape = floatingShape
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(sizing.component(18.dp)))
                        Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                        Text("${stringResource(R.string.appstrings_delete)} (${selectedIds.size}) - ${formatUploadHistoryBytes(selectedFreedBytes)}")
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        HistoryPageSettingsSheet(
            title = stringResource(R.string.upload_history_settings_title),
            page = PageScrollPrefs.PHOTO_UPLOAD_HISTORY,
            options = listOf(
                HistoryPageOption(
                    title = stringResource(R.string.upload_history_option_thumbnails),
                    checked = showThumbnails,
                    onCheckedChange = {
                        showThumbnails = it
                        HistoryPagePreferences.write(prefs, HistoryPagePreferences.UPLOAD_THUMBNAILS, it)
                    }
                ),
                HistoryPageOption(
                    title = stringResource(R.string.history_option_date_bar),
                    checked = showDateBar,
                    onCheckedChange = {
                        showDateBar = it
                        HistoryPagePreferences.write(prefs, HistoryPagePreferences.UPLOAD_DATE_BAR, it)
                    }
                )
            ),
            onReset = {
                showThumbnails = HistoryPagePreferences.DEFAULT_ENABLED
                showDateBar = HistoryPagePreferences.DEFAULT_ENABLED
                HistoryPagePreferences.write(prefs, HistoryPagePreferences.UPLOAD_THUMBNAILS, HistoryPagePreferences.DEFAULT_ENABLED)
                HistoryPagePreferences.write(prefs, HistoryPagePreferences.UPLOAD_DATE_BAR, HistoryPagePreferences.DEFAULT_ENABLED)
            },
            onDismiss = { showSettingsSheet = false },
            onBack = { showSettingsSheet = false },
            sheetState = settingsSheetState,
            useOneUi = AppConfig.useOneUiDesign,
            bubbleColor = cardColor
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = {
                safeClick("photo_upload_history_clear_dismiss") {
                    showClearDialog = false
                }
            },
            title = { Text(stringResource(R.string.appstrings_upload_history_clear_title)) },
            text = { Text(stringResource(R.string.appstrings_upload_history_clear_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        safeClick("photo_upload_history_clear_confirm") {
                            ExternalPhotoUploadHistoryStore.clear(appContext)
                            reloadHistory()
                            showClearDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.appstrings_upload_history_clear))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        safeClick("photo_upload_history_clear_dismiss") {
                            showClearDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.appstrings_cancel))
                }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PhotoUploadHistoryRow(
    item: ExternalPhotoUploadHistoryEntry,
    showThumbnail: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onOpenSite: (String) -> Unit,
    onSelect: () -> Unit,
    onToggleSelection: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val statusLabel = uploadHistoryStatusLabel(item.status)
    val statusColor = when (item.status) {
        ExternalPhotoUploadHistoryStore.STATUS_SUCCESS -> Color(0xFF4CAF50)
        ExternalPhotoUploadHistoryStore.STATUS_FAILED -> MaterialTheme.colorScheme.error
        ExternalPhotoUploadHistoryStore.STATUS_RETRY -> MaterialTheme.colorScheme.tertiary
        ExternalPhotoUploadHistoryStore.STATUS_CANCELLED -> MaterialTheme.colorScheme.outline
        ExternalPhotoUploadHistoryStore.STATUS_AWAITING_VALIDATION -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }
    val statusIcon = when (item.status) {
        ExternalPhotoUploadHistoryStore.STATUS_SUCCESS -> Icons.Default.CheckCircle
        ExternalPhotoUploadHistoryStore.STATUS_FAILED -> Icons.Default.Error
        ExternalPhotoUploadHistoryStore.STATUS_CANCELLED -> Icons.Default.Cancel
        else -> Icons.Default.CloudUpload
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelection()
                    } else if (item.supportId.isNotBlank()) {
                        onOpenSite(item.supportId)
                    }
                },
                onLongClick = onSelect
            )
            .padding(horizontal = sizing.spacing(14.dp), vertical = sizing.spacing(12.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            SelectionIndicator(isSelected)
            Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
        }
        if (showThumbnail) {
            HistoryThumbnail(item.thumbnailPath)
            Spacer(modifier = Modifier.width(sizing.spacing(14.dp)))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(sizing.spacing(3.dp))
        ) {
            Text(
                text = "${item.sourceName} - ${item.operator}",
                style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${stringResource(R.string.appstrings_upload_sq_target_site)} ${item.supportId}",
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatUploadHistoryDate(item.createdAtMillis)} - $statusLabel",
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Surface(
                color = statusColor.copy(alpha = 0.12f),
                contentColor = statusColor,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = sizing.spacing(8.dp), vertical = sizing.spacing(3.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(statusIcon, contentDescription = null, modifier = Modifier.size(sizing.component(14.dp)))
                    Spacer(modifier = Modifier.width(sizing.spacing(4.dp)))
                    Text(
                        text = if (item.stripExifBeforeUpload) {
                            stringResource(R.string.upload_history_exif_removed)
                        } else {
                            stringResource(R.string.upload_history_exif_kept)
                        },
                        style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun uploadHistoryStatusLabel(status: String): String = when (status) {
    ExternalPhotoUploadHistoryStore.STATUS_SUCCESS -> stringResource(R.string.upload_history_status_success)
    ExternalPhotoUploadHistoryStore.STATUS_AWAITING_VALIDATION -> stringResource(R.string.upload_history_status_awaiting_validation)
    ExternalPhotoUploadHistoryStore.STATUS_FAILED -> stringResource(R.string.upload_history_status_failed)
    ExternalPhotoUploadHistoryStore.STATUS_RETRY -> stringResource(R.string.upload_history_status_retry)
    ExternalPhotoUploadHistoryStore.STATUS_CANCELLED -> stringResource(R.string.upload_history_status_cancelled)
    else -> stringResource(R.string.upload_history_status_in_progress)
}

@Composable
private fun SelectionIndicator(isSelected: Boolean) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Surface(
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
        border = if (isSelected) null else BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.size(sizing.component(24.dp))
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(sizing.component(16.dp)))
            }
        }
    }
}

@Composable
private fun HistoryThumbnail(thumbnailPath: String?) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(sizing.component(68.dp))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val file = thumbnailPath?.let(::File)
        if (file?.isFile == true) {
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
            )
        } else {
            Icon(
                Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(sizing.component(28.dp))
            )
        }
    }
}

private fun formatUploadHistoryDate(timestamp: Long): String = formatHistoryDateTime(timestamp)

private fun formatUploadHistoryBytes(bytes: Long): String = formatHistoryStorageBytes(bytes)
