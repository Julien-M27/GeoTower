package fr.geotower.ui.screens.speedtests

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import fr.geotower.R
import fr.geotower.data.speedtest.SpeedtestRepository
import fr.geotower.ui.components.FullScreenDialogWindow
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.PageScrollEdgeButtons
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.components.pageScrollbar
import fr.geotower.ui.screens.settings.HistoryPageSettingsSheet
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.PageScrollPrefs
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val TECH_OPTIONS = listOf("5G SA", "5G NSA", "5G", "4G+", "4G", "3G", "Wi-Fi")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedtestDetailScreen(
    entryId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiStyle = LocalGeoTowerUiStyle.current
    val scope = rememberCoroutineScope()
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSettingsSheet by remember { mutableStateOf(false) }

    val entry by remember(entryId) { SpeedtestRepository.observeById(context, entryId) }
        .collectAsState(initial = null)

    val currentEntry = entry

    Scaffold(
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.speedtests_detail_title),
                onBack = onBack,
                actionsWidth = if (currentEntry != null) 96.dp else 48.dp,
                actions = {
                    if (currentEntry != null) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    SpeedtestRepository.reanalyzeOcr(context, currentEntry)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.speedtests_rerun_ocr)
                            )
                        }
                    }
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.appstrings_settings_title)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        SpeedtestDetailContent(
            entryId = entryId,
            onDeleted = onBack,
            onSaved = onBack,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )

        if (showSettingsSheet) {
            val bubbleColor = if (uiStyle.useOneUi) uiStyle.bubbleColor else MaterialTheme.colorScheme.surfaceVariant
            HistoryPageSettingsSheet(
                title = stringResource(R.string.speedtests_detail_title),
                page = PageScrollPrefs.SPEEDTESTS,
                options = emptyList(),
                onReset = {},
                onDismiss = { showSettingsSheet = false },
                onBack = { showSettingsSheet = false },
                sheetState = settingsSheetState,
                useOneUi = AppConfig.useOneUiDesign,
                bubbleColor = bubbleColor
            )
        }
    }
}

@Composable
fun SpeedtestDetailContent(
    entryId: String,
    onDeleted: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val entry by remember(entryId) { SpeedtestRepository.observeById(context, entryId) }
        .collectAsState(initial = null)

    var showFullscreenImage by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMapPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var isReanalyzingOcr by remember { mutableStateOf(false) }

    var testedAtMillis by remember(entry?.id) { mutableLongStateOf(entry?.testedAtMillis ?: System.currentTimeMillis()) }
    var downloadText by remember(entry?.id) { mutableStateOf(entry?.downloadMbps?.toString().orEmpty()) }
    var uploadText by remember(entry?.id) { mutableStateOf(entry?.uploadMbps?.toString().orEmpty()) }
    var pingText by remember(entry?.id) { mutableStateOf(entry?.pingMs?.toString().orEmpty()) }
    var jitterText by remember(entry?.id) { mutableStateOf(entry?.jitterMs?.toString().orEmpty()) }
    var operatorText by remember(entry?.id) { mutableStateOf(entry?.operatorName.orEmpty()) }
    var networkText by remember(entry?.id) { mutableStateOf(entry?.networkName.orEmpty()) }
    var techText by remember(entry?.id) { mutableStateOf(entry?.networkTech.orEmpty()) }
    var bandText by remember(entry?.id) { mutableStateOf(entry?.band.orEmpty()) }
    var cellIdText by remember(entry?.id) { mutableStateOf(entry?.cellIdentifier.orEmpty()) }
    var locationLabelText by remember(entry?.id) { mutableStateOf(entry?.locationLabel.orEmpty()) }
    var latText by remember(entry?.id) { mutableStateOf(entry?.latitude?.toString().orEmpty()) }
    var lonText by remember(entry?.id) { mutableStateOf(entry?.longitude?.toString().orEmpty()) }
    var noteText by remember(entry?.id) { mutableStateOf(entry?.note.orEmpty()) }

    LaunchedEffect(entry) {
        val current = entry ?: return@LaunchedEffect
        testedAtMillis = current.testedAtMillis
        downloadText = current.downloadMbps?.toString().orEmpty()
        uploadText = current.uploadMbps?.toString().orEmpty()
        pingText = current.pingMs?.toString().orEmpty()
        jitterText = current.jitterMs?.toString().orEmpty()
        operatorText = current.operatorName.orEmpty()
        networkText = current.networkName.orEmpty()
        techText = current.networkTech.orEmpty()
        bandText = current.band.orEmpty()
        cellIdText = current.cellIdentifier.orEmpty()
        locationLabelText = current.locationLabel.orEmpty()
        latText = current.latitude?.toString().orEmpty()
        lonText = current.longitude?.toString().orEmpty()
        noteText = current.note.orEmpty()
    }

    val currentEntry = entry

    if (currentEntry == null) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.speedtests_not_found))
        }
        return
    }

    val imageFile = remember(currentEntry.imageFileName) {
        SpeedtestRepository.imageFile(context, currentEntry.imageFileName)
    }

    BoxWithConstraints(modifier = modifier) {
        val isWidePane = maxWidth >= 600.dp

        if (isWidePane) {
            // Mode Fold / tablette : image + date à gauche, formulaire à droite
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(sizing.spacing(16.dp)),
                horizontalArrangement = Arrangement.spacedBy(sizing.spacing(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.44f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showFullscreenImage = true }
                    ) {
                        AsyncImage(
                            model = imageFile,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                .padding(6.dp)
                        )
                    }

                    val formattedDate = remember(testedAtMillis) {
                        SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault()).format(Date(testedAtMillis))
                    }
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(formattedDate)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(0.56f)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .geoTowerFadingEdge(scrollState)
                            .pageScrollbar(PageScrollPrefs.SPEEDTESTS, scrollState)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
                    ) {
                        SpeedtestDetailFormFields(
                            downloadText = downloadText,
                            onDownloadChange = { downloadText = it },
                            uploadText = uploadText,
                            onUploadChange = { uploadText = it },
                            pingText = pingText,
                            onPingChange = { pingText = it },
                            jitterText = jitterText,
                            onJitterChange = { jitterText = it },
                            operatorText = operatorText,
                            onOperatorChange = { operatorText = it },
                            networkText = networkText,
                            onNetworkChange = { networkText = it },
                            techText = techText,
                            onTechChange = { techText = it },
                            bandText = bandText,
                            onBandChange = { bandText = it },
                            cellIdText = cellIdText,
                            onCellIdChange = { cellIdText = it },
                            locationLabelText = locationLabelText,
                            onLocationLabelChange = { locationLabelText = it },
                            latText = latText,
                            onLatChange = { latText = it },
                            lonText = lonText,
                            onLonChange = { lonText = it },
                            noteText = noteText,
                            onNoteChange = { noteText = it },
                            rawOcr = currentEntry.ocrRawText,
                            isReanalyzingOcr = isReanalyzingOcr,
                            onRerunOcr = {
                                if (!isReanalyzingOcr) {
                                    isReanalyzingOcr = true
                                    scope.launch {
                                        SpeedtestRepository.reanalyzeOcr(context, currentEntry)
                                        isReanalyzingOcr = false
                                    }
                                }
                            },
                            onShare = { SpeedtestRepository.shareImages(context, listOf(currentEntry)) },
                            onUseCurrentLocation = {
                                val hasFine = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                val hasCoarse = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasFine || hasCoarse) {
                                    scope.launch {
                                        val loc = getLastKnownLocationSafe(context)
                                        if (loc != null) {
                                            latText = String.format(Locale.US, "%.6f", loc.first)
                                            lonText = String.format(Locale.US, "%.6f", loc.second)
                                        }
                                    }
                                }
                            },
                            onPickOnMap = { showMapPicker = true },
                            onSave = {
                                scope.launch {
                                    val updated = currentEntry.copy(
                                        testedAtMillis = testedAtMillis,
                                        downloadMbps = parseOptionalDouble(downloadText),
                                        uploadMbps = parseOptionalDouble(uploadText),
                                        pingMs = parseOptionalDouble(pingText),
                                        jitterMs = parseOptionalDouble(jitterText),
                                        operatorName = operatorText.trim().ifEmpty { null },
                                        networkName = networkText.trim().ifEmpty { null },
                                        networkTech = techText.trim().ifEmpty { null },
                                        band = bandText.trim().ifEmpty { null },
                                        cellIdentifier = cellIdText.trim().ifEmpty { null },
                                        locationLabel = locationLabelText.trim().ifEmpty { null },
                                        latitude = parseOptionalDouble(latText),
                                        longitude = parseOptionalDouble(lonText),
                                        note = noteText.trim().ifEmpty { null }
                                    )
                                    SpeedtestRepository.upsert(context, updated)
                                    onSaved()
                                }
                            },
                            onDelete = { showDeleteConfirm = true }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    PageScrollEdgeButtons(PageScrollPrefs.SPEEDTESTS, scrollState)
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .geoTowerFadingEdge(scrollState)
                        .pageScrollbar(PageScrollPrefs.SPEEDTESTS, scrollState)
                        .verticalScroll(scrollState)
                        .padding(sizing.spacing(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showFullscreenImage = true }
                    ) {
                        AsyncImage(
                            model = imageFile,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                .padding(6.dp)
                        )
                    }

                    val formattedDate = remember(testedAtMillis) {
                        SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault()).format(Date(testedAtMillis))
                    }
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(formattedDate)
                    }

                    SpeedtestDetailFormFields(
                        downloadText = downloadText,
                        onDownloadChange = { downloadText = it },
                        uploadText = uploadText,
                        onUploadChange = { uploadText = it },
                        pingText = pingText,
                        onPingChange = { pingText = it },
                        jitterText = jitterText,
                        onJitterChange = { jitterText = it },
                        operatorText = operatorText,
                        onOperatorChange = { operatorText = it },
                        networkText = networkText,
                        onNetworkChange = { networkText = it },
                        techText = techText,
                        onTechChange = { techText = it },
                        bandText = bandText,
                        onBandChange = { bandText = it },
                        cellIdText = cellIdText,
                        onCellIdChange = { cellIdText = it },
                        locationLabelText = locationLabelText,
                        onLocationLabelChange = { locationLabelText = it },
                        latText = latText,
                        onLatChange = { latText = it },
                        lonText = lonText,
                        onLonChange = { lonText = it },
                        noteText = noteText,
                        onNoteChange = { noteText = it },
                        rawOcr = currentEntry.ocrRawText,
                        isReanalyzingOcr = isReanalyzingOcr,
                        onRerunOcr = {
                            if (!isReanalyzingOcr) {
                                isReanalyzingOcr = true
                                scope.launch {
                                    SpeedtestRepository.reanalyzeOcr(context, currentEntry)
                                    isReanalyzingOcr = false
                                }
                            }
                        },
                        onShare = { SpeedtestRepository.shareImages(context, listOf(currentEntry)) },
                        onUseCurrentLocation = {
                            val hasFine = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            val hasCoarse = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasFine || hasCoarse) {
                                scope.launch {
                                    val loc = getLastKnownLocationSafe(context)
                                    if (loc != null) {
                                        latText = String.format(Locale.US, "%.6f", loc.first)
                                        lonText = String.format(Locale.US, "%.6f", loc.second)
                                    }
                                }
                            }
                        },
                        onPickOnMap = { showMapPicker = true },
                        onSave = {
                            scope.launch {
                                val updated = currentEntry.copy(
                                    testedAtMillis = testedAtMillis,
                                    downloadMbps = parseOptionalDouble(downloadText),
                                    uploadMbps = parseOptionalDouble(uploadText),
                                    pingMs = parseOptionalDouble(pingText),
                                    jitterMs = parseOptionalDouble(jitterText),
                                    operatorName = operatorText.trim().ifEmpty { null },
                                    networkTech = techText.trim().ifEmpty { null },
                                    band = bandText.trim().ifEmpty { null },
                                    cellIdentifier = cellIdText.trim().ifEmpty { null },
                                    locationLabel = locationLabelText.trim().ifEmpty { null },
                                    latitude = parseOptionalDouble(latText),
                                    longitude = parseOptionalDouble(lonText),
                                    note = noteText.trim().ifEmpty { null }
                                )
                                SpeedtestRepository.upsert(context, updated)
                                onSaved()
                            }
                        },
                        onDelete = { showDeleteConfirm = true }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
                PageScrollEdgeButtons(PageScrollPrefs.SPEEDTESTS, scrollState)
            }
        }
    }

        if (showFullscreenImage) {
            FullscreenZoomImageDialog(
                model = imageFile,
                onDismiss = { showFullscreenImage = false }
            )
        }

        if (showMapPicker) {
            SpeedtestMapPickerDialog(
                initialLat = parseOptionalDouble(latText),
                initialLon = parseOptionalDouble(lonText),
                onConfirm = { lat, lon ->
                    latText = String.format(Locale.US, "%.6f", lat)
                    lonText = String.format(Locale.US, "%.6f", lon)
                    showMapPicker = false
                },
                onDismiss = { showMapPicker = false }
            )
        }

        if (showDatePicker) {
            SpeedtestDatePickerDialog(
                initialMillis = testedAtMillis,
                onConfirm = { newMillis ->
                    testedAtMillis = newMillis
                    showDatePicker = false
                    showTimePicker = true
                },
                onDismiss = { showDatePicker = false }
            )
        }

        if (showTimePicker) {
            SpeedtestTimePickerDialog(
                initialMillis = testedAtMillis,
                onConfirm = { newMillis ->
                    testedAtMillis = newMillis
                    showTimePicker = false
                },
                onDismiss = { showTimePicker = false }
            )
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(stringResource(R.string.speedtests_delete_title)) },
                text = { Text(stringResource(R.string.speedtests_delete_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            scope.launch {
                                SpeedtestRepository.delete(context, currentEntry)
                                onDeleted()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }
    }

@Composable
private fun SpeedtestDetailFormFields(
    downloadText: String,
    onDownloadChange: (String) -> Unit,
    uploadText: String,
    onUploadChange: (String) -> Unit,
    pingText: String,
    onPingChange: (String) -> Unit,
    jitterText: String,
    onJitterChange: (String) -> Unit,
    operatorText: String,
    onOperatorChange: (String) -> Unit,
    networkText: String,
    onNetworkChange: (String) -> Unit,
    techText: String,
    onTechChange: (String) -> Unit,
    bandText: String,
    onBandChange: (String) -> Unit,
    cellIdText: String,
    onCellIdChange: (String) -> Unit,
    locationLabelText: String,
    onLocationLabelChange: (String) -> Unit,
    latText: String,
    onLatChange: (String) -> Unit,
    lonText: String,
    onLonChange: (String) -> Unit,
    noteText: String,
    onNoteChange: (String) -> Unit,
    rawOcr: String?,
    isReanalyzingOcr: Boolean,
    onRerunOcr: () -> Unit,
    onShare: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onPickOnMap: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = downloadText,
                onValueChange = onDownloadChange,
                label = { Text(stringResource(R.string.speedtests_field_download)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = uploadText,
                onValueChange = onUploadChange,
                label = { Text(stringResource(R.string.speedtests_field_upload)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = pingText,
                onValueChange = onPingChange,
                label = { Text(stringResource(R.string.speedtests_field_ping)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = jitterText,
                onValueChange = onJitterChange,
                label = { Text(stringResource(R.string.speedtests_field_jitter)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = operatorText,
                onValueChange = onOperatorChange,
                label = { Text(stringResource(R.string.speedtests_field_operator)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = networkText,
                onValueChange = onNetworkChange,
                label = { Text(stringResource(R.string.speedtests_field_network)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TECH_OPTIONS.forEach { tech ->
                FilterChip(
                    selected = techText.equals(tech, ignoreCase = true),
                    onClick = { onTechChange(if (techText.equals(tech, ignoreCase = true)) "" else tech) },
                    label = { Text(tech) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = bandText,
                onValueChange = onBandChange,
                label = { Text(stringResource(R.string.speedtests_field_band)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = cellIdText,
                onValueChange = onCellIdChange,
                label = { Text(stringResource(R.string.speedtests_field_cell_id)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = locationLabelText,
            onValueChange = onLocationLabelChange,
            label = { Text(stringResource(R.string.speedtests_field_location)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = latText,
                onValueChange = onLatChange,
                label = { Text("Lat") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = lonText,
                onValueChange = onLonChange,
                label = { Text("Lon") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onUseCurrentLocation) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = stringResource(R.string.speedtests_use_current_location)
                )
            }
        }

        OutlinedButton(
            onClick = onPickOnMap,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.speedtests_pick_on_map))
        }

        OutlinedTextField(
            value = noteText,
            onValueChange = onNoteChange,
            label = { Text(stringResource(R.string.speedtests_field_note)) },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.common_save))
        }

        OutlinedButton(
            onClick = onShare,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.speedtests_share_image))
        }

        OutlinedButton(
            onClick = onRerunOcr,
            enabled = !isReanalyzingOcr,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (isReanalyzingOcr) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.speedtests_rerun_ocr))
        }

        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            )
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.common_delete))
        }

        if (!rawOcr.isNullOrBlank()) {
            var showRawOcr by remember { mutableStateOf(false) }
            TextButton(
                onClick = { showRawOcr = !showRawOcr },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = stringResource(
                        if (showRawOcr) R.string.speedtests_hide_raw_ocr else R.string.speedtests_show_raw_ocr
                    ),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (showRawOcr) {
                val context = LocalContext.current
                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                val copiedMessage = stringResource(R.string.speedtests_ocr_copied)
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(rawOcr))
                                    android.widget.Toast.makeText(
                                        context,
                                        copiedMessage,
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.speedtests_copy_ocr))
                            }
                        }
                        Text(
                            text = rawOcr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedtestDatePickerDialog(
    initialMillis: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val initialLocal = remember(initialMillis) {
        Calendar.getInstance().apply { timeInMillis = initialMillis }
    }
    val initialUtcMillis = remember(initialMillis) {
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                initialLocal.get(Calendar.YEAR),
                initialLocal.get(Calendar.MONTH),
                initialLocal.get(Calendar.DAY_OF_MONTH)
            )
        }.timeInMillis
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedUtc = state.selectedDateMillis
                    if (selectedUtc != null) {
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = selectedUtc
                        }
                        val merged = Calendar.getInstance().apply {
                            timeInMillis = initialMillis
                            set(
                                utcCal.get(Calendar.YEAR),
                                utcCal.get(Calendar.MONTH),
                                utcCal.get(Calendar.DAY_OF_MONTH)
                            )
                        }
                        onConfirm(merged.timeInMillis)
                    } else {
                        onConfirm(initialMillis)
                    }
                }
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedtestTimePickerDialog(
    initialMillis: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val cal = remember(initialMillis) {
        Calendar.getInstance().apply { timeInMillis = initialMillis }
    }
    val state = rememberTimePickerState(
        initialHour = cal.get(Calendar.HOUR_OF_DAY),
        initialMinute = cal.get(Calendar.MINUTE),
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val updated = Calendar.getInstance().apply {
                        timeInMillis = initialMillis
                        set(Calendar.HOUR_OF_DAY, state.hour)
                        set(Calendar.MINUTE, state.minute)
                        set(Calendar.SECOND, 0)
                    }
                    onConfirm(updated.timeInMillis)
                }
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        text = {
            TimePicker(state = state)
        }
    )
}

@Composable
private fun FullscreenZoomImageDialog(
    model: Any,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        FullScreenDialogWindow()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = newScale
                        offset = if (newScale > 1f) offset + pan else Offset.Zero
                    }
                }
        ) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                        RoundedCornerShape(50)
                    )
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
private suspend fun getLastKnownLocationSafe(context: android.content.Context): Pair<Double, Double>? {
    return runCatching {
        val loc = LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
        if (loc != null && loc.latitude.isFinite() && loc.longitude.isFinite()) {
            loc.latitude to loc.longitude
        } else null
    }.getOrNull()
}

private fun parseOptionalDouble(raw: String): Double? {
    return raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
}
