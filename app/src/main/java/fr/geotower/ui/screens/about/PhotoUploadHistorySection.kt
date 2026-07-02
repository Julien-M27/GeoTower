package fr.geotower.ui.screens.about

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.geotower.data.upload.ExternalPhotoUploadHistoryEntry
import fr.geotower.data.upload.ExternalPhotoUploadHistoryStore
import fr.geotower.data.upload.ExternalPhotoUploadHistoryValidator
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.geoTowerLazyListFadingEdge
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.utils.AppConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.appstrings_upload_history_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (historyCount == 0) {
                        stringResource(R.string.upload_history_no_photo_recorded)
                    } else {
                        pluralStringResource(R.plurals.upload_history_recorded, historyCount, historyCount)
                    },
                    style = MaterialTheme.typography.bodySmall,
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

@Composable
fun PhotoUploadHistoryScreen(
    onNavigateBack: () -> Unit,
    onOpenSite: (String, String) -> Unit
) {
    val safeClick = rememberSafeClick()
    val context = LocalContext.current
    val appContext = context.applicationContext
    val listState = rememberLazyListState()
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

    Scaffold(
        containerColor = pageColor,
        topBar = {
            if (isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(pageColor)
                        .padding(top = 2.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            safeClick("photo_upload_history_toggle_all") {
                                selectedIds = if (isAllSelected) emptyList() else historyItems.map { it.id }
                            }
                        },
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(if (isAllSelected) stringResource(R.string.appstrings_clear_all) else stringResource(R.string.appstrings_select_all))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { selectedIds = emptyList() },
                        modifier = Modifier.padding(end = 4.dp)
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
                    backgroundColor = pageColor
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
                    .geoTowerLazyListFadingEdge(listState),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                        if (isSelectionMode) 76.dp else 0.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
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
                                    .padding(top = 8.dp, bottom = 24.dp)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${stringResource(R.string.appstrings_upload_history_clear)} (${formatUploadHistoryBytes(totalFreedBytes)})")
                            }
                        }
                    }
                }
            }

            UploadHistoryDateScrollbar(
                listState = listState,
                items = historyItems,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(
                        top = 12.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp
                    )
            )

            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
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
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${stringResource(R.string.appstrings_delete)} (${selectedIds.size}) - ${formatUploadHistoryBytes(selectedFreedBytes)}")
                    }
                }
            }
        }
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
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onOpenSite: (String) -> Unit,
    onSelect: () -> Unit,
    onToggleSelection: () -> Unit
) {
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
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            SelectionIndicator(isSelected)
            Spacer(modifier = Modifier.width(12.dp))
        }
        HistoryThumbnail(item.thumbnailPath)
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "${item.sourceName} - ${item.operator}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${stringResource(R.string.appstrings_upload_sq_target_site)} ${item.supportId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatUploadHistoryDate(item.createdAtMillis)} - $statusLabel",
                style = MaterialTheme.typography.bodySmall,
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
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(statusIcon, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (item.stripExifBeforeUpload) {
                            stringResource(R.string.upload_history_exif_removed)
                        } else {
                            stringResource(R.string.upload_history_exif_kept)
                        },
                        style = MaterialTheme.typography.labelSmall,
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
    Surface(
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
        border = if (isSelected) null else BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.size(24.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun HistoryThumbnail(thumbnailPath: String?) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(68.dp)
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
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// Barre de défilement latérale : pouce ancré au bord droit, bulle affichant le jour d'envoi des
// photos visibles, et saut direct dans la liste en tirant le pouce. Elle apparaît pendant le
// défilement et s'efface toute seule après une courte inactivité.
@Composable
private fun UploadHistoryDateScrollbar(
    listState: LazyListState,
    items: List<ExternalPhotoUploadHistoryEntry>,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    // Position du pouce alignée sur le défilement réel (progression inter-élément comprise).
    val listFraction by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                0f
            } else {
                val denominator = (layoutInfo.totalItemsCount - visibleItems.size).coerceAtLeast(1)
                val firstItem = visibleItems.first()
                val firstItemProgress = -firstItem.offset.toFloat() / firstItem.size.coerceAtLeast(1)
                ((firstItem.index + firstItemProgress) / denominator).coerceIn(0f, 1f)
            }
        }
    }

    val isScrollable = listState.canScrollForward || listState.canScrollBackward
    val isActive = isDragging || listState.isScrollInProgress
    var isBarVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isActive, isScrollable) {
        if (isActive && isScrollable) {
            isBarVisible = true
        } else {
            delay(1400)
            isBarVisible = false
        }
    }
    val barAlpha by animateFloatAsState(
        targetValue = if (isBarVisible) 1f else 0f,
        label = "uploadHistoryScrollbarAlpha"
    )
    if (barAlpha < 0.01f) return

    val fraction = if (isDragging) dragFraction else listFraction
    val labelIndex = if (isDragging) {
        (dragFraction * (items.size - 1)).roundToInt()
    } else {
        listState.firstVisibleItemIndex
    }.coerceIn(0, items.lastIndex)
    val dayLabel = formatUploadHistoryDay(items[labelIndex].createdAtMillis)

    BoxWithConstraints(modifier = modifier.alpha(barAlpha)) {
        val thumbHeightPx = with(LocalDensity.current) { UPLOAD_HISTORY_SCROLLBAR_THUMB_HEIGHT.toPx() }
        val maxOffsetPx = (constraints.maxHeight - thumbHeightPx).coerceAtLeast(0f)

        fun fractionForPointer(y: Float): Float {
            if (maxOffsetPx <= 0f) return 0f
            return ((y - thumbHeightPx / 2f) / maxOffsetPx).coerceIn(0f, 1f)
        }

        fun scrollToFraction(value: Float) {
            dragFraction = value
            val targetIndex = (value * (items.size - 1)).roundToInt().coerceIn(0, items.lastIndex)
            scope.launch { listState.scrollToItem(targetIndex) }
        }

        // Zone de prise en main : toute la hauteur du bord droit.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(24.dp)
                .pointerInput(items.size, maxOffsetPx) {
                    detectVerticalDragGestures(
                        onDragStart = { startOffset ->
                            isDragging = true
                            scrollToFraction(fractionForPointer(startOffset.y))
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            scrollToFraction(fractionForPointer(change.position.y))
                        }
                    )
                }
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (fraction * maxOffsetPx).roundToInt()) }
                .height(UPLOAD_HISTORY_SCROLLBAR_THUMB_HEIGHT)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = CircleShape,
                shadowElevation = 4.dp
            ) {
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Box(
                modifier = Modifier
                    .padding(end = 3.dp)
                    .width(6.dp)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

private val UPLOAD_HISTORY_SCROLLBAR_THUMB_HEIGHT = 48.dp

private fun formatUploadHistoryDay(timestamp: Long): String {
    return runCatching {
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }.getOrDefault("-")
}

private fun formatUploadHistoryDate(timestamp: Long): String {
    return runCatching {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
    }.getOrDefault("-")
}

private fun formatUploadHistoryBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 o"
    val units = listOf("o", "Ko", "Mo", "Go")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${bytes} ${units[unitIndex]}"
    } else {
        val pattern = if (value >= 10.0) "%.0f %s" else "%.1f %s"
        String.format(Locale.getDefault(), pattern, value, units[unitIndex])
    }
}
