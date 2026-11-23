package fr.geotower.ui.screens.speedtests

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.geotower.R
import fr.geotower.data.speedtest.SpeedtestEntryEntity
import fr.geotower.data.speedtest.SpeedtestRepository
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SpeedtestsScreen(
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit
) {
    val context = LocalContext.current
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val scope = rememberCoroutineScope()

    val entries by remember { SpeedtestRepository.observeAll(context) }
        .collectAsState(initial = emptyList())

    var isImporting by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedOperator by remember { mutableStateOf<String?>(null) }
    var selectedTech by remember { mutableStateOf<String?>(null) }

    var selectedIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty() && !isImporting) {
            isImporting = true
            scope.launch {
                SpeedtestRepository.importImages(context, uris)
                isImporting = false
            }
        }
    }

    val operators = remember(entries) {
        entries.mapNotNull { it.operatorName?.trim()?.takeIf(String::isNotEmpty) }.distinct().sorted()
    }
    val techs = remember(entries) {
        entries.mapNotNull { it.networkTech?.trim()?.takeIf(String::isNotEmpty) }.distinct().sorted()
    }

    val filteredEntries = remember(entries, searchQuery, selectedOperator, selectedTech) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        entries.filter { entry ->
            if (selectedOperator != null && entry.operatorName != selectedOperator) return@filter false
            if (selectedTech != null && entry.networkTech != selectedTech) return@filter false
            if (q.isNotEmpty()) {
                val haystack = listOfNotNull(
                    entry.operatorName,
                    entry.networkTech,
                    entry.band,
                    entry.cellIdentifier,
                    entry.locationLabel,
                    entry.note
                ).joinToString(" ").lowercase(Locale.ROOT)
                if (!haystack.contains(q)) return@filter false
            }
            true
        }
    }

    val visibleIds = remember(filteredEntries) { filteredEntries.map { it.id }.toSet() }
    LaunchedEffect(visibleIds) {
        if (selectedIds.any { it !in visibleIds }) {
            selectedIds = selectedIds.filter { it in visibleIds }
        }
    }

    val selectedIdSet = selectedIds.toSet()
    val isSelectionMode = selectedIds.isNotEmpty()
    val isAllSelected = filteredEntries.isNotEmpty() && filteredEntries.all { it.id in selectedIdSet }

    BackHandler(enabled = isSelectionMode) {
        selectedIds = emptyList()
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                GeoTowerBackTopBar(
                    title = pluralStringResource(R.plurals.speedtests_selected_count, selectedIds.size, selectedIds.size),
                    onBack = { selectedIds = emptyList() },
                    actions = {
                        IconButton(
                            onClick = {
                                selectedIds = if (isAllSelected) emptyList() else filteredEntries.map { it.id }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = null,
                                tint = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { showDeleteSelectedConfirm = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = stringResource(R.string.common_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            } else {
                GeoTowerBackTopBar(
                    title = stringResource(R.string.speedtests_title),
                    onBack = onBack,
                    actions = {
                        if (entries.isNotEmpty()) {
                            IconButton(onClick = { showSearch = !showSearch }) {
                                Icon(
                                    imageVector = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = stringResource(R.string.speedtests_search_hint)
                                )
                            }
                            IconButton(onClick = { showStats = !showStats }) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = stringResource(R.string.speedtests_stats_toggle),
                                    tint = if (showStats) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (isSelectionMode) {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = { showDeleteSelectedConfirm = true },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null
                        )
                    },
                    text = {
                        Text("${stringResource(R.string.common_delete)} (${selectedIds.size})")
                    }
                )
            } else {
                FloatingActionButton(
                    onClick = {
                        if (!isImporting) {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.speedtests_import_action)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isImporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            AnimatedVisibility(visible = showSearch && entries.isNotEmpty() && !isSelectionMode) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.speedtests_search_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp))
                )
            }

            if (operators.isNotEmpty() || techs.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(4.dp)),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    operators.forEach { op ->
                        FilterChip(
                            selected = selectedOperator == op,
                            onClick = { selectedOperator = if (selectedOperator == op) null else op },
                            label = { Text(op) }
                        )
                    }
                    techs.forEach { tech ->
                        FilterChip(
                            selected = selectedTech == tech,
                            onClick = { selectedTech = if (selectedTech == tech) null else tech },
                            label = { Text(tech) }
                        )
                    }
                }
            }

            AnimatedVisibility(visible = showStats && filteredEntries.isNotEmpty() && !isSelectionMode) {
                SpeedtestStatsPanel(
                    entries = filteredEntries,
                    modifier = Modifier.padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp))
                )
            }

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(sizing.spacing(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                        Text(
                            text = stringResource(R.string.speedtests_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.speedtests_empty_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(sizing.spacing(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.speedtests_no_filter_match),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = sizing.spacing(12.dp),
                        end = sizing.spacing(16.dp),
                        top = sizing.spacing(8.dp),
                        bottom = sizing.spacing(88.dp)
                    )
                ) {
                    itemsIndexed(filteredEntries, key = { _, item -> item.id }) { index, entry ->
                        val isSelected = entry.id in selectedIdSet
                        SpeedtestTimelineRow(
                            entry = entry,
                            isFirst = index == 0,
                            isLast = index == filteredEntries.lastIndex,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedIds = if (isSelected) {
                                        selectedIds.filterNot { it == entry.id }
                                    } else {
                                        selectedIds + entry.id
                                    }
                                } else {
                                    onOpenEntry(entry.id)
                                }
                            },
                            onLongClick = {
                                if (entry.id !in selectedIds) {
                                    selectedIds = selectedIds + entry.id
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteSelectedConfirm) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { showDeleteSelectedConfirm = false },
            title = { Text(stringResource(R.string.speedtests_delete_title)) },
            text = {
                Text(
                    pluralStringResource(R.plurals.speedtests_delete_selected_confirm, count, count)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = entries.filter { it.id in selectedIdSet }
                        showDeleteSelectedConfirm = false
                        selectedIds = emptyList()
                        scope.launch {
                            toDelete.forEach { SpeedtestRepository.delete(context, it) }
                        }
                    }
                ) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedtestTimelineRow(
    entry: SpeedtestEntryEntity,
    isFirst: Boolean,
    isLast: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val imageFile = remember(entry.imageFileName) {
        SpeedtestRepository.imageFile(context, entry.imageFileName)
    }

    val dayMonth = remember(entry.testedAtMillis) {
        SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(entry.testedAtMillis))
    }
    val yearText = remember(entry.testedAtMillis) {
        SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(entry.testedAtMillis))
    }
    val timeText = remember(entry.testedAtMillis) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entry.testedAtMillis))
    }

    val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val dotColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Fil vertical reliant chaque date
        Canvas(
            modifier = Modifier
                .width(18.dp)
                .fillMaxHeight()
        ) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val dotRadius = 4.5.dp.toPx()
            val stroke = 2.dp.toPx()

            if (!isFirst) {
                drawLine(
                    color = lineColor,
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, (centerY - dotRadius - 2.dp.toPx()).coerceAtLeast(0f)),
                    strokeWidth = stroke
                )
            }
            if (!isLast) {
                drawLine(
                    color = lineColor,
                    start = Offset(centerX, (centerY + dotRadius + 2.dp.toPx()).coerceAtMost(size.height)),
                    end = Offset(centerX, size.height),
                    strokeWidth = stroke
                )
            }
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset(centerX, centerY)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
            ),
            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = sizing.spacing(5.dp))
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(sizing.spacing(12.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Date à gauche
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(60.dp)
                ) {
                    Text(
                        text = dayMonth,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = yearText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.width(sizing.spacing(10.dp)))

                // Miniature de la capture
                AsyncImage(
                    model = imageFile,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(56.dp)
                        .height(80.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )

                Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))

                // Valeurs extraites / métadonnées
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val metricsLine = buildSpeedMetricsSummary(entry)
                    if (metricsLine.isNotEmpty()) {
                        Text(
                            text = metricsLine,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.speedtests_no_metrics),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val metaBadges = listOfNotNull(
                        entry.operatorName,
                        entry.networkTech,
                        entry.band
                    )
                    if (metaBadges.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            metaBadges.forEach { badge ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    val subLine = entry.locationLabel?.takeIf { it.isNotBlank() }
                        ?: entry.note?.takeIf { it.isNotBlank() }
                    if (subLine != null) {
                        Text(
                            text = subLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isSelectionMode) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedtestStatsPanel(
    entries: List<SpeedtestEntryEntity>,
    modifier: Modifier = Modifier
) {
    val downloads = entries.mapNotNull { it.downloadMbps }
    val uploads = entries.mapNotNull { it.uploadMbps }
    val pings = entries.mapNotNull { it.pingMs }

    val avgDl = downloads.takeIf { it.isNotEmpty() }?.average()
    val maxDl = downloads.maxOrNull()
    val avgUl = uploads.takeIf { it.isNotEmpty() }?.average()
    val minPing = pings.minOrNull()

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatChip(
                    label = stringResource(R.string.speedtests_stat_avg_dl),
                    value = avgDl?.let { formatMbps(it) } ?: "—"
                )
                StatChip(
                    label = stringResource(R.string.speedtests_stat_max_dl),
                    value = maxDl?.let { formatMbps(it) } ?: "—"
                )
                StatChip(
                    label = stringResource(R.string.speedtests_stat_avg_ul),
                    value = avgUl?.let { formatMbps(it) } ?: "—"
                )
                StatChip(
                    label = stringResource(R.string.speedtests_stat_min_ping),
                    value = minPing?.let { "${formatNum(it)} ms" } ?: "—"
                )
            }

            val chronological = remember(entries) {
                entries.sortedBy { it.testedAtMillis }.filter { it.downloadMbps != null || it.uploadMbps != null }
            }
            if (chronological.size >= 2) {
                SpeedtestEvolutionChart(
                    entries = chronological,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SpeedtestEvolutionChart(
    entries: List<SpeedtestEntryEntity>,
    modifier: Modifier = Modifier
) {
    val dlColor = MaterialTheme.colorScheme.primary
    val ulColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    val maxSpeed = remember(entries) {
        val m = entries.maxOfOrNull { maxOf(it.downloadMbps ?: 0.0, it.uploadMbps ?: 0.0) } ?: 1.0
        maxOf(m, 1.0)
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pad = 8.dp.toPx()
        val usableW = (w - 2 * pad).coerceAtLeast(1f)
        val usableH = (h - 2 * pad).coerceAtLeast(1f)

        for (step in 0..2) {
            val y = pad + usableH * (step / 2f)
            drawLine(
                color = gridColor,
                start = Offset(pad, y),
                end = Offset(w - pad, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        fun drawSeries(values: List<Double?>, color: Color) {
            val points = values.mapIndexedNotNull { index, v ->
                if (v == null) null
                else {
                    val x = if (values.size <= 1) pad + usableW / 2f
                    else pad + usableW * (index.toFloat() / (values.size - 1).toFloat())
                    val y = pad + usableH * (1f - (v / maxSpeed).toFloat().coerceIn(0f, 1f))
                    Offset(x, y)
                }
            }
            if (points.size >= 2) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            points.forEach { pt ->
                drawCircle(color = color, radius = 3.5.dp.toPx(), center = pt)
            }
        }

        drawSeries(entries.map { it.uploadMbps }, ulColor)
        drawSeries(entries.map { it.downloadMbps }, dlColor)
    }
}

private fun buildSpeedMetricsSummary(entry: SpeedtestEntryEntity): String {
    val parts = mutableListOf<String>()
    entry.downloadMbps?.let { parts.add("↓ ${formatMbps(it)}") }
    entry.uploadMbps?.let { parts.add("↑ ${formatMbps(it)}") }
    entry.pingMs?.let { parts.add("${formatNum(it)} ms") }
    return parts.joinToString("  •  ")
}

private fun formatMbps(value: Double): String = "${formatNum(value)} Mb/s"

private fun formatNum(value: Double): String {
    return if (value >= 100.0 || value % 1.0 == 0.0) {
        String.format(Locale.getDefault(), "%.0f", value)
    } else {
        String.format(Locale.getDefault(), "%.1f", value)
    }
}
