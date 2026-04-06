package fr.geotower.ui.screens.emitters

// NOUVEAUX IMPORTS POUR LA CARTE
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings
import fr.geotower.utils.MapUtils
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import fr.geotower.data.models.LocalisationEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalQuestUploadScreen(
    imageUris: List<String>,
    siteId: String,
    operatorName: String,
    lat: Double,
    lon: Double,
    azimuts: String,
    onNavigateBack: () -> Unit,
    onStartUpload: (List<String>, String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // --- 0. ANTI-SPAM CLIC (Debounce) ---
    var lastClickTime by remember { mutableLongStateOf(0L) }
    val debounceTime = 700L

    fun safeClick(action: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > debounceTime) {
            lastClickTime = currentTime
            action()
        }
    }

    // --- 1. ÉTATS ---
    val currentUris = remember { mutableStateListOf<String>().apply { addAll(imageUris) } }
    var description by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }

    // Pour la carte
    val mapProvider by AppConfig.mapProvider
    val ignStyle by AppConfig.ignStyle

    // --- 1.5 LANCEUR DE GALERIE (Si la liste est vidée) ---
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            currentUris.clear()
            currentUris.addAll(uris.map { it.toString() })
        }
    }

    // Thème et couleurs
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val useOneUi by AppConfig.forceOneUiTheme // <-- NOUVEAU : Lecture du paramètre One UI
    val isDark = (themeMode == 2) || (themeMode == 0 && androidx.compose.foundation.isSystemInDarkTheme())

    // --- NOUVEAU : FORMES DYNAMIQUES SELON LE MODE ---
    val photoShape = if (useOneUi) RoundedCornerShape(32.dp) else RoundedCornerShape(24.dp)
    val blockShape = if (useOneUi) RoundedCornerShape(24.dp) else RoundedCornerShape(16.dp)
    val buttonShape = if (useOneUi) CircleShape else RoundedCornerShape(16.dp)
    val mapShape = if (useOneUi) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)

    val effectiveOled = isOledMode && isDark
    val activeColor = MaterialTheme.colorScheme.primary
    val backgroundColor = when { effectiveOled -> Color.Black; isDark -> Color(0xFF141414); else -> MaterialTheme.colorScheme.background }
    val surfaceColor = when { effectiveOled -> Color(0xFF222222); isDark -> Color(0xFF2C2C2C); else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) }
    val textColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .padding(top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { safeClick { onNavigateBack() } },
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = AppStrings.back,
                        tint = textColor
                    )
                }
                Text(
                    text = AppStrings.uploadSqTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.width(48.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- 2. CARROUSEL OU IMAGE CENTRÉE ---
            if (currentUris.isEmpty()) {
                Card(
                    modifier = Modifier
                        .size(width = 240.dp, height = 280.dp)
                        .clip(photoShape)
                        .clickable { safeClick { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
                    shape = photoShape,
                    colors = CardDefaults.cardColors(containerColor = surfaceColor.copy(alpha = 0.3f)),
                    border = BorderStroke(2.dp, activeColor.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(48.dp), tint = activeColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = AppStrings.get("Ajouter des photos", "Add photos", "Adicionar fotos"),
                            color = textColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = if (currentUris.size == 1) Arrangement.Center else Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(currentUris) { uri ->
                        Box(modifier = Modifier.size(width = 240.dp, height = 280.dp)) {
                            Card(
                                modifier = Modifier.fillMaxSize(),
                                shape = photoShape,
                                colors = CardDefaults.cardColors(containerColor = surfaceColor)
                            ) {
                                AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                            // Bouton de suppression avec marge dynamique
                            IconButton(
                                onClick = { safeClick { currentUris.remove(uri) } },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    // NOUVEAU : 16.dp pour OneUI (radius 32), 8.dp pour Classique (radius 24)
                                    .padding(if (useOneUi) 16.dp else 8.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- 3. CHAMP DESCRIPTION ---
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(AppStrings.uploadSqDescPlaceholder, color = textColor.copy(alpha = 0.5f)) },
                minLines = 3, shape = blockShape,
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = surfaceColor, focusedContainerColor = surfaceColor, unfocusedTextColor = textColor, focusedTextColor = textColor)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Récapitulatif
            Card(modifier = Modifier.fillMaxWidth(), shape = blockShape, colors = CardDefaults.cardColors(containerColor = surfaceColor)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = activeColor)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("${AppStrings.uploadSqTargetSite} $siteId", fontWeight = FontWeight.Bold, color = textColor)
                        Text("${AppStrings.uploadSqTargetOperator} $operatorName", fontSize = 14.sp, color = textColor.copy(alpha = 0.7f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- 4. BOUTON D'ENVOI ---
            Button(
                onClick = {
                    safeClick {
                        if (currentUris.isNotEmpty()) {
                            showConfirmDialog = true
                        }
                    }
                },
                enabled = currentUris.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = buttonShape,
                colors = ButtonDefaults.buttonColors(containerColor = activeColor)
            ) {
                Icon(Icons.Default.CloudUpload, null)
                Spacer(Modifier.width(8.dp))
                Text(AppStrings.uploadSqButton(currentUris.size), fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(AppStrings.uploadSqLimit, fontSize = 12.sp, color = textColor.copy(alpha = 0.5f))
        }

        // --- 5. POP-UP DE CONFIRMATION AVEC LA CARTE ---
        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                shape = blockShape,
                title = { Text(text = AppStrings.uploadConfirmTitle, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(text = AppStrings.uploadConfirmMessage(currentUris.size))

                        // NOUVEAU : LA MINI-CARTE DANS LE POP-UP
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(mapShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), mapShape)
                        ) {
                            // ✅ 1. On prépare l'antenne et les couleurs avant la carte
                            val rawPrimaryColor = MaterialTheme.colorScheme.primary.toArgb()
                            val isColorTooLight = androidx.core.graphics.ColorUtils.calculateLuminance(rawPrimaryColor) > 0.85
                            val safePrimaryColor = if (isColorTooLight) android.graphics.Color.parseColor("#2196F3") else rawPrimaryColor

                            val mappedAntennas = remember(siteId, operatorName, lat, lon, azimuts) {
                                listOf(LocalisationEntity(
                                    idAnfr = siteId,
                                    operateur = operatorName,
                                    latitude = lat,
                                    longitude = lon,
                                    azimuts = azimuts,
                                    codeInsee = null,
                                    azimutsFh = null,
                                    filtres = null
                                ))
                            }

                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    MapView(ctx).apply {
                                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                                        setMultiTouchControls(false)
                                        setOnTouchListener { _, _ -> true } // Bloque le tactile manuel
                                        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                                        controller.setZoom(17.5)
                                        controller.setCenter(GeoPoint(lat, lon))

                                        // ✅ 2. ON UTILISE TON SUPER MARQUEUR DÉDIÉ (Celui qui trace les lignes !)
                                        val marker = fr.geotower.ui.components.MiniMapAntennaMarker(
                                            this,
                                            mappedAntennas,
                                            safePrimaryColor,
                                            operatorName
                                        ).apply {
                                            position = GeoPoint(lat, lon)
                                            setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
                                            infoWindow = null
                                            setOnMarkerClickListener { _, _ -> true }
                                        }
                                        overlays.add(marker)
                                    }
                                },
                                update = { map ->
                                    val newSource = when {
                                        mapProvider == 1 -> MapUtils.OSM_Source
                                        ignStyle == 2 -> MapUtils.IgnSource.SATELLITE
                                        else -> MapUtils.IgnSource.PLAN_IGN
                                    }
                                    if (map.tileProvider.tileSource.name() != newSource.name()) {
                                        map.setTileSource(newSource)
                                    }

                                    val shouldInvertColors = (mapProvider == 0 && ignStyle == 1)
                                    map.overlayManager.tilesOverlay.setColorFilter(if (shouldInvertColors) MapUtils.getInvertFilter() else null)

                                    // ✅ 3. ON MET À JOUR LE MARQUEUR PERSONNALISÉ
                                    val marker = map.overlays.filterIsInstance<fr.geotower.ui.components.MiniMapAntennaMarker>().firstOrNull()
                                    if (marker != null) {
                                        marker.icon = MapUtils.createAdaptiveMarker(context, mappedAntennas, true, operatorName)
                                    }
                                    map.invalidate()
                                }
                            )
                        }
                    }
                },
                containerColor = surfaceColor,
                titleContentColor = textColor,
                textContentColor = textColor,
                confirmButton = {
                    Button(
                        onClick = {
                            safeClick {
                                showConfirmDialog = false
                                onStartUpload(currentUris.toList(), description)
                            }
                        },
                        shape = buttonShape,
                        colors = ButtonDefaults.buttonColors(containerColor = activeColor)
                    ) {
                        Text(AppStrings.validate, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { safeClick { showConfirmDialog = false } }) {
                        Text(AppStrings.cancel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    }
}