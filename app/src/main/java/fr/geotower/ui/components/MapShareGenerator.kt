package fr.geotower.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import fr.geotower.utils.SharePrefs
import java.io.File
import java.io.FileOutputStream
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import fr.geotower.R

private const val TAG_MAP_SHARE = "GeoTowerMap"

fun shareFullMapCapture(
    context: Context,
    currentView: View,
    mapBitmap: Bitmap?,
    forceDarkTheme: Boolean,
    txtTitle: String,
    txtGeneratedBy: String,
    txtShareSiteVia: String,
    incAttribution: Boolean,
    txtAttribution: String,
    incSpeedometer: Boolean,
    currentSpeed: String,
    incScale: Boolean, // ✅ AJOUT
    currentZoom: Double, // ✅ AJOUT
    currentLat: Double, // ✅ AJOUT
    txtInitError: String
) {
    try {
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(currentView.findViewTreeLifecycleOwner())
            setViewTreeSavedStateRegistryOwner(currentView.findViewTreeSavedStateRegistryOwner())
            setViewTreeViewModelStoreOwner(currentView.findViewTreeViewModelStoreOwner())

            setContent {
                val colors = if (forceDarkTheme) darkColorScheme() else lightColorScheme()
                MaterialTheme(colorScheme = colors) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Column(
                            modifier = Modifier
                                .width(540.dp) // ✅ Un peu plus grand pour une meilleure résolution d'image
                                .wrapContentHeight()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(text = txtTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                            // ✅ 1. ON CALCULE LES PROPORTIONS EXACTES DE TA CARTE
                            val mapRatio = if (mapBitmap != null) mapBitmap.width.toFloat() / mapBitmap.height.toFloat() else 1f

                            // ✅ 2. ON REMPLACE LA HAUTEUR FIXE PAR LE BON RATIO (aspectRatio)
                            Box(modifier = Modifier.fillMaxWidth().aspectRatio(mapRatio).clip(RoundedCornerShape(12.dp)).border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))) {
                                if (mapBitmap != null) {
                                    Image(
                                        bitmap = mapBitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                // Superposition du compteur de vitesse
                                if (incSpeedometer && currentSpeed.isNotEmpty()) {
                                    Surface(
                                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White.copy(alpha = 0.8f)
                                    ) {
                                        Text(text = currentSpeed, color = Color.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }

                                // Superposition de l'échelle
                                if (incScale) {
                                    // ✅ On passe le bottom padding de 24.dp à 40.dp pour bien la remonter
                                    CaptureScaleBar(zoom = currentZoom, latitude = currentLat, modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 40.dp))
                                }

                                // Superposition de l'attribution / Copyright
                                if (incAttribution && txtAttribution.isNotBlank()) {
                                    Surface(
                                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                                        color = Color.White.copy(alpha = 0.8f)
                                    ) {
                                        Text(
                                            text = txtAttribution,
                                            color = Color.Black,
                                            fontSize = 9.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(2.dp)
                                        )
                                    }
                                }
                            }

                            Text(text = txtGeneratedBy, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                    }
                }
            }
        }

        val rootView = currentView.rootView as? ViewGroup ?: return
        composeView.translationX = 10000f
        rootView.addView(composeView)

        composeView.post {
            try {
                val displayMetrics = context.resources.displayMetrics
                val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(displayMetrics.widthPixels, View.MeasureSpec.EXACTLY)
                val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

                composeView.measure(widthMeasureSpec, heightMeasureSpec)
                composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)

                val bitmap = Bitmap.createBitmap(composeView.measuredWidth, composeView.measuredHeight, Bitmap.Config.ARGB_8888)
                composeView.draw(Canvas(bitmap))
                rootView.removeView(composeView)

                val cachePath = File(context.cacheDir, "images")
                cachePath.mkdirs()
                val file = File(cachePath, "Geotower_map_capture.png")
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, uri)
                    type = "image/png"
                    clipData = ClipData.newUri(context.contentResolver, "Capture", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, txtShareSiteVia))
            } catch (e: Exception) {
                AppLogger.w(TAG_MAP_SHARE, "Map share capture failed", e)
                Toast.makeText(context, txtInitError, Toast.LENGTH_SHORT).show()
                rootView.removeView(composeView)
            }
        }
    } catch (e: Exception) { AppLogger.w(TAG_MAP_SHARE, "Map share initialization failed", e) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapShareMenu(
    useOneUi: Boolean,
    globalMapRef: org.osmdroid.views.MapView?,
    currentSpeed: String,
    currentZoom: Double,
    currentLat: Double,
    azimuth: Float,
    measureOverlay: org.osmdroid.views.overlay.FolderOverlay? = null // ✅ AJOUT : Le calque des mesures
) {
    val context = LocalContext.current
    val currentView = LocalView.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    val safeClick = rememberSafeClick()

    var showShareSheet by remember { mutableStateOf(false) }
    var showSelectionSheet by remember { mutableStateOf(false) }
    var selectedShareTheme by remember { mutableStateOf(false) }

    var incAzimuths by remember { mutableStateOf(SharePrefs.mapAzimuths.read(prefs)) }
    var incSpeedometer by remember { mutableStateOf(SharePrefs.mapSpeedometer.read(prefs)) }
    var incScale by remember { mutableStateOf(SharePrefs.mapScale.read(prefs)) }
    var incAttribution by remember { mutableStateOf(SharePrefs.mapAttribution.read(prefs)) }
    var incConfidential by remember { mutableStateOf(SharePrefs.mapConfidential.read(prefs)) }

    LaunchedEffect(showShareSheet) {
        if (showShareSheet) {
            incAzimuths = SharePrefs.mapAzimuths.read(prefs)
            incSpeedometer = SharePrefs.mapSpeedometer.read(prefs)
            incScale = SharePrefs.mapScale.read(prefs)
            incAttribution = SharePrefs.mapAttribution.read(prefs)
            incConfidential = SharePrefs.mapConfidential.read(prefs)
        }
    }

    val txtTitle = stringResource(R.string.appstrings_map_title)
    val txtGeneratedBy = stringResource(R.string.appstrings_generated_by)
    val txtShareSiteVia = stringResource(R.string.appstrings_share_site_via)
    val txtThemeLight = stringResource(R.string.appstrings_theme_light)
    val txtLightModeDesc = stringResource(R.string.appstrings_light_mode_desc)
    val txtThemeDark = stringResource(R.string.appstrings_theme_dark)
    val txtDarkModeDesc = stringResource(R.string.appstrings_dark_mode_desc)
    val txtShareAs = stringResource(R.string.appstrings_share_as)
    val txtImageContent = stringResource(R.string.appstrings_image_content)
    val txtShareConfidentialOption = stringResource(R.string.appstrings_share_confidential_option)
    val txtShareConfidentialDesc = stringResource(R.string.appstrings_share_confidential_desc)
    val txtGenerateImage = stringResource(R.string.appstrings_generate_image)
    val txtInitError = stringResource(R.string.appstrings_init_error)

    val txtAzimuths = stringResource(R.string.appstrings_share_map_azimuths_option)
    val txtSpeedometer = stringResource(R.string.appstrings_share_map_speedometer_option)
    val txtScale = stringResource(R.string.appstrings_share_map_scale_option)
    val txtAttributionOption = stringResource(R.string.appstrings_share_map_attribution_option)

    val txtAttributionDesc = when(AppConfig.mapProvider.intValue) {
        0 -> stringResource(R.string.appstrings_src_ign_desc)
        1 -> stringResource(R.string.appstrings_src_osm_desc)
        2 -> "© MapLibre"
        3 -> "© OpenTopoMap"
        else -> stringResource(R.string.appstrings_src_osm_desc)
    }

    Surface(
        onClick = { safeClick { showShareSheet = true } },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(54.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showShareSheet) {
        ModalBottomSheet(onDismissRequest = { showShareSheet = false }, sheetState = sheetState, containerColor = sheetBgColor) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp, start = 16.dp, end = 16.dp)) {
                Text(txtShareAs, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp))
                fr.geotower.ui.components.ThemeOptionItem(iconVector = Icons.Outlined.LightMode, label = txtThemeLight, subLabel = txtLightModeDesc, useOneUi = useOneUi) { safeClick { selectedShareTheme = false; showShareSheet = false; showSelectionSheet = true } }
                Spacer(modifier = Modifier.height(12.dp))
                fr.geotower.ui.components.ThemeOptionItem(iconVector = Icons.Outlined.DarkMode, label = txtThemeDark, subLabel = txtDarkModeDesc, useOneUi = useOneUi) { safeClick { selectedShareTheme = true; showShareSheet = false; showSelectionSheet = true } }
            }
        }
    }

    if (showSelectionSheet) {
        ModalBottomSheet(onDismissRequest = { showSelectionSheet = false }, sheetState = sheetState, containerColor = sheetBgColor) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = 24.dp,
                            end = 24.dp,
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp
                        )
                ) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { safeClick { showSelectionSheet = false; showShareSheet = true } }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    Text(text = txtImageContent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.width(48.dp))
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Azimuts
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(txtAzimuths, modifier = Modifier.weight(1f))
                        GeoTowerSwitch(checked = incAzimuths, onCheckedChange = { incAzimuths = it }, useOneUi = useOneUi)
                    }
                    // Compteur de vitesse
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(txtSpeedometer, modifier = Modifier.weight(1f))
                        GeoTowerSwitch(checked = incSpeedometer, onCheckedChange = { incSpeedometer = it }, useOneUi = useOneUi)
                    }
                    // Échelle
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(txtScale, modifier = Modifier.weight(1f))
                        GeoTowerSwitch(checked = incScale, onCheckedChange = { incScale = it }, useOneUi = useOneUi)
                    }
                    // Crédits (Attribution)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(txtAttributionOption, modifier = Modifier.weight(1f))
                        GeoTowerSwitch(checked = incAttribution, onCheckedChange = { incAttribution = it }, useOneUi = useOneUi)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Bouton Confidentiel
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(txtShareConfidentialOption, fontWeight = FontWeight.Bold, color = if(incConfidential) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text(txtShareConfidentialDesc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        GeoTowerSwitch(checked = incConfidential, onCheckedChange = { incConfidential = it }, useOneUi = useOneUi)
                    }
                }
            }

            Button(
                        onClick = {
                            showSelectionSheet = false

                            val originalStates = mutableMapOf<org.osmdroid.views.overlay.Overlay, Boolean>()

                            // ✅ 1. GESTION DES AZIMUTS : On sauvegarde et on masque si nécessaire
                            val originalAzimuthState = AppConfig.showAzimuths.value
                            if (!incAzimuths) {
                                AppConfig.showAzimuths.value = false
                            }

                            // ✅ 2. On masque le calque des mesures si on est en confidentiel
                            measureOverlay?.let { overlay ->
                                originalStates[overlay] = overlay.isEnabled
                                if (incConfidential) overlay.isEnabled = false
                            }

                            globalMapRef?.overlays?.forEach { overlay ->
                                if (overlay is org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay) {
                                    originalStates[overlay] = overlay.isEnabled
                                    overlay.isEnabled = !incConfidential
                                }
                            }
                            globalMapRef?.invalidate()

                            currentView.postDelayed({
                                val mapBmp = try {
                                    globalMapRef?.let { map ->
                                        val bmp = Bitmap.createBitmap(map.width, map.height, Bitmap.Config.ARGB_8888)
                                        map.draw(Canvas(bmp))
                                        bmp
                                    }
                                } catch (e: Exception) { null }

                                // ✅ 3. RESTAURATION : On remet les azimuts et les calques comme avant !
                                if (!incAzimuths) {
                                    AppConfig.showAzimuths.value = originalAzimuthState
                                }
                                originalStates.forEach { (overlay, state) ->
                                    overlay.isEnabled = state
                                }
                                globalMapRef?.invalidate()

                                shareFullMapCapture(
                                    context = context,
                                    currentView = currentView,
                                    mapBitmap = mapBmp,
                                    forceDarkTheme = selectedShareTheme,
                                    txtTitle = txtTitle,
                                    txtGeneratedBy = txtGeneratedBy,
                                    txtShareSiteVia = txtShareSiteVia,
                                    incAttribution = incAttribution,
                                    txtAttribution = txtAttributionDesc,
                                    incSpeedometer = incSpeedometer,
                                    currentSpeed = currentSpeed,
                                    incScale = incScale,
                                    currentZoom = currentZoom,
                                    currentLat = currentLat,
                                    txtInitError = txtInitError
                                )
                            }, 300)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                start = 24.dp,
                                end = 24.dp,
                                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                            )
                            .fillMaxWidth()
                            .widthIn(max = 420.dp)
                            .height(56.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(txtGenerateImage, fontWeight = FontWeight.Bold)
                    }
            }
        }
    }
}
@Composable
fun CaptureScaleBar(zoom: Double, latitude: Double, modifier: Modifier = Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val maxBarWidthPx = with(density) { 100.dp.toPx() }
    val metersPerPx = 156543.03392 * Math.cos(latitude * Math.PI / 180.0) / Math.pow(2.0, zoom)
    val roundDistances = listOf(1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 50000, 100000, 200000, 300000, 500000, 1000000)
    val chosenDistance = roundDistances.lastOrNull { (it / metersPerPx) <= maxBarWidthPx } ?: 1
    val actualBarWidthDp = with(density) { (chosenDistance / metersPerPx).toFloat().toDp() }
    val label = if (chosenDistance >= 1000) "${chosenDistance / 1000} km" else "$chosenDistance m"

    Surface(color = Color.White.copy(alpha = 0.8f), shape = RoundedCornerShape(2.dp), modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            androidx.compose.foundation.Canvas(modifier = Modifier.width(actualBarWidthDp).height(6.dp)) {
                drawLine(Color.Black, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = 2.dp.toPx())
                drawLine(Color.Black, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(0f, 0f), strokeWidth = 2.dp.toPx())
                drawLine(Color.Black, androidx.compose.ui.geometry.Offset(size.width, size.height), androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = 2.dp.toPx())
            }
        }
    }
}
