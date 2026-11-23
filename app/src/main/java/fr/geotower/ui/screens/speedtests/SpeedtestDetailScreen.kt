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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
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
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val TECH_OPTIONS = listOf("5G SA", "5G NSA", "5G", "4G+", "4G", "3G", "Wi-Fi")

@Composable
fun SpeedtestDetailScreen(
    entryId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val scope = rememberCoroutineScope()

    val entry by remember(entryId) { SpeedtestRepository.observeById(context, entryId) }
        .collectAsState(initial = null)

    var showFullscreenImage by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMapPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    var testedAtMillis by remember(entry?.id) { mutableLongStateOf(entry?.testedAtMillis ?: System.currentTimeMillis()) }
    var downloadText by remember(entry?.id) { mutableStateOf(entry?.downloadMbps?.toString().orEmpty()) }
    var uploadText by remember(entry?.id) { mutableStateOf(entry?.uploadMbps?.toString().orEmpty()) }
    var pingText by remember(entry?.id) { mutableStateOf(entry?.pingMs?.toString().orEmpty()) }
    var jitterText by remember(entry?.id) { mutableStateOf(entry?.jitterMs?.toString().orEmpty()) }
    var operatorText by remember(entry?.id) { mutableStateOf(entry?.operatorName.orEmpty()) }
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
        techText = current.networkTech.orEmpty()
        bandText = current.band.orEmpty()
        cellIdText = current.cellIdentifier.orEmpty()
        locationLabelText = current.locationLabel.orEmpty()
        latText = current.latitude?.toString().orEmpty()
        lonText = current.longitude?.toString().orEmpty()
        noteText = current.note.orEmpty()
    }

    val currentEntry = entry

    Scaffold(
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.speedtests_detail_title),
                onBack = onBack,
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
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.common_delete)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (currentEntry == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.speedtests_not_found))
            }
            return@Scaffold
        }

        val imageFile = remember(currentEntry.imageFileName) {
            SpeedtestRepository.imageFile(context, currentEntry.imageFileName)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(sizing.spacing(16.dp)),
            verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
        ) {
            // Aperçu cliquable pour zoom plein écran
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

            // Date & heure modifiables
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

            // Débits & latence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = downloadText,
                    onValueChange = { downloadText = it },
                    label = { Text(stringResource(R.string.speedtests_field_download)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = uploadText,
                    onValueChange = { uploadText = it },
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
                    onValueChange = { pingText = it },
                    label = { Text(stringResource(R.string.speedtests_field_ping)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = jitterText,
                    onValueChange = { jitterText = it },
                    label = { Text(stringResource(R.string.speedtests_field_jitter)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // Opérateur & Techno
            OutlinedTextField(
                value = operatorText,
                onValueChange = { operatorText = it },
                label = { Text(stringResource(R.string.speedtests_field_operator)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TECH_OPTIONS.forEach { tech ->
                    FilterChip(
                        selected = techText.equals(tech, ignoreCase = true),
                        onClick = { techText = if (techText.equals(tech, ignoreCase = true)) "" else tech },
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
                    onValueChange = { bandText = it },
                    label = { Text(stringResource(R.string.speedtests_field_band)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = cellIdText,
                    onValueChange = { cellIdText = it },
                    label = { Text(stringResource(R.string.speedtests_field_cell_id)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // Lieu & Coordonnées GPS
            OutlinedTextField(
                value = locationLabelText,
                onValueChange = { locationLabelText = it },
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
                    onValueChange = { latText = it },
                    label = { Text("Lat") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = lonText,
                    onValueChange = { lonText = it },
                    label = { Text("Lon") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
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
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = stringResource(R.string.speedtests_use_current_location)
                    )
                }
            }

            OutlinedButton(
                onClick = { showMapPicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.speedtests_pick_on_map))
            }

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text(stringResource(R.string.speedtests_field_note)) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
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
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.common_save))
            }

            OutlinedButton(
                onClick = { showDeleteConfirm = true },
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

            Spacer(modifier = Modifier.height(16.dp))
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
                                onBack()
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
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
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
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
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
