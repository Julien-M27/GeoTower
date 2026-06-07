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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.RadioMapMarker
import fr.geotower.data.models.PhysiqueEntity // ✅ NOUVEAU
import fr.geotower.data.models.TechniqueEntity // ✅ NOUVEAU
import fr.geotower.ui.screens.emitters.DEFAULT_ELEVATION_PROFILE_FREQUENCY_MHZ
import fr.geotower.ui.screens.emitters.extractElevationProfileAntennaHeightsByFrequency
import fr.geotower.ui.screens.emitters.extractElevationProfileFrequencies
import fr.geotower.ui.screens.emitters.fetchIgnElevationProfileData
import fr.geotower.utils.formatDateToFrench
import fr.geotower.utils.formatTechnologies
import fr.geotower.ui.screens.emitters.getElevationProfileLastKnownLocation
import fr.geotower.ui.screens.emitters.getDetailLogoRes
import fr.geotower.utils.AnfrDisplayText
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import fr.geotower.utils.SharePrefs
import fr.geotower.utils.formatSpectrumDisplayDetails
import fr.geotower.utils.parseAndSortFrequencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.roundToInt
import android.net.Uri
import fr.geotower.ui.components.SiteStatusCard
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import fr.geotower.R

private const val TAG_SHARE_IMAGE = "GeoTower"

private fun Int.dpToPx(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()

private fun formatShareHeightMeters(heightMeters: Double?): String {
    if (heightMeters == null) return "--"
    return if (AppConfig.distanceUnit.intValue == 1) {
        "${(heightMeters * 3.28084).roundToInt()} ft"
    } else {
        if (heightMeters % 1.0 == 0.0) "${heightMeters.toInt()} m" else String.format(Locale.US, "%.1f m", heightMeters)
    }
}

private fun formatSharePanelHeightMeters(heightMeters: Double, distanceUnit: Int = AppConfig.distanceUnit.intValue): String {
    return if (distanceUnit == 1) {
        "${(heightMeters * 3.28084).roundToInt()} ft"
    } else {
        if (heightMeters % 1.0 == 0.0) "${heightMeters.toInt()} m" else String.format(Locale.US, "%.1f m", heightMeters)
    }
}

private fun isOnlyElevationProfileSelected(
    shareOrder: List<String>,
    incElevationProfile: Boolean,
    incMap: Boolean,
    incSupport: Boolean,
    incIds: Boolean,
    incDates: Boolean,
    incAddress: Boolean,
    incFreqs: Boolean,
    incSpeedtest: Boolean,
    incThroughput: Boolean
): Boolean {
    if (!incElevationProfile || !shareOrder.contains("elevation_profile")) return false
    return shareOrder.none { block ->
        when (block) {
            "map" -> incMap
            "support" -> incSupport
            "ids" -> incIds
            "dates" -> incDates
            "address" -> incAddress
            "speedtest" -> incSpeedtest
            "throughput" -> incThroughput
            "status" -> AppConfig.shareSiteStatus.value
            "freq" -> incFreqs
            else -> false
        }
    }
}

private fun shareElevationProfileBitmapOnly(
    context: Context,
    info: LocalisationEntity,
    bitmap: Bitmap,
    txtShareSiteVia: String
) {
    val imagesDir = File(context.cacheDir, "images")
    imagesDir.mkdirs()
    val file = File(imagesDir, "Geotower_site_${info.idAnfr}_profil_altimetrique.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "Profil altimetrique", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, txtShareSiteVia))
}

private fun Bitmap.trimTransparentBottom(): Bitmap {
    val row = IntArray(width)
    var bottom = height - 1

    while (bottom >= 0) {
        getPixels(row, 0, width, 0, bottom, width, 1)
        if (row.any { pixel -> (pixel ushr 24) != 0 }) break
        bottom--
    }

    if (bottom < 0 || bottom == height - 1) return this
    return Bitmap.createBitmap(this, 0, 0, width, bottom + 1)
}

private fun captureShareMapBitmap(mapView: org.osmdroid.views.MapView?): Bitmap? {
    val map = mapView ?: return null
    if (map.width <= 0 || map.height <= 0) return null
    return runCatching {
        Bitmap.createBitmap(map.width, map.height, Bitmap.Config.ARGB_8888).also { bitmap ->
            map.draw(Canvas(bitmap))
        }
    }.getOrNull()
}

private fun String.shareSafeFilePart(): String {
    return replace(Regex("[^A-Za-z0-9_.-]+"), "_")
        .trim('_')
        .take(80)
        .ifBlank { "radio" }
}

private fun String.shareLabelText(): String {
    return trim().trimEnd(':').trim()
}

private const val RADIO_SHARE_HEADER = "header"
private const val RADIO_SHARE_BEARING_HEIGHT = "bearing_height"
private const val RADIO_SHARE_MAP = "map"
private const val RADIO_SHARE_SUPPORT = "support"
private const val RADIO_SHARE_IDS = "ids"
private const val RADIO_SHARE_ADDRESS = "address"
private const val RADIO_SHARE_RADIO = "radio"
private const val RADIO_SHARE_PROGRAMS = "programs"
private const val RADIO_SHARE_AZIMUTHS = "azimuths"
private const val RADIO_SHARE_EXTRA = "extra"
private const val RADIO_SHARE_SUPPORT_ENTRIES = "support_entries"
private const val SUPPORT_SHARE_RADIO_ENTRIES = "radio_entries"

private val DEFAULT_RADIO_SITE_SHARE_ORDER = listOf(
    RADIO_SHARE_BEARING_HEIGHT,
    RADIO_SHARE_MAP,
    RADIO_SHARE_SUPPORT,
    RADIO_SHARE_IDS,
    RADIO_SHARE_ADDRESS,
    RADIO_SHARE_RADIO,
    RADIO_SHARE_PROGRAMS,
    RADIO_SHARE_AZIMUTHS,
    RADIO_SHARE_EXTRA
)

private val DEFAULT_RADIO_SUPPORT_SHARE_ORDER = listOf(
    RADIO_SHARE_MAP,
    RADIO_SHARE_SUPPORT,
    RADIO_SHARE_SUPPORT_ENTRIES
)

private data class RadioShareBlock(
    val id: String,
    val label: String
)

private fun normalizeSupportShareOrder(
    order: List<String>,
    hasRadioEntries: Boolean
): List<String> {
    val defaultOrder = listOf("map", "support", "operators") +
        if (hasRadioEntries) listOf(SUPPORT_SHARE_RADIO_ENTRIES) else emptyList()
    val kept = order.filter { it in defaultOrder }.distinct()
    val missing = defaultOrder.filter { it !in kept }
    return (kept + missing).ifEmpty { defaultOrder }
}

private fun persistSupportShareOrder(prefs: android.content.SharedPreferences, order: List<String>) {
    prefs.edit()
        .putString(
            SharePrefs.SUPPORT_ORDER,
            order.filter { it != SUPPORT_SHARE_RADIO_ENTRIES }.joinToString(",")
        )
        .apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioShareMenu(
    marker: RadioMapMarker,
    markers: List<RadioMapMarker> = listOf(marker),
    isSupportShare: Boolean = false,
    distanceStr: String,
    bearingStr: String,
    useOneUi: Boolean,
    buttonShape: Shape,
    globalMapRef: org.osmdroid.views.MapView?,
    modifier: Modifier = Modifier,
    outlinedButton: Boolean = false
) {
    val context = LocalContext.current
    val currentView = LocalView.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val safeClick = rememberSafeClick()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    val availableBlocks = radioShareAvailableBlocks(marker, markers, isSupportShare)
    val availableIds = remember(availableBlocks) { availableBlocks.map { it.id }.toSet() }
    var shareOrder by remember(marker.id, markers.size, isSupportShare) {
        mutableStateOf(radioShareDefaultBlocks(isSupportShare))
    }
    var selectedBlockIds by remember(marker.id, markers.size, isSupportShare) {
        mutableStateOf(availableIds)
    }
    LaunchedEffect(availableIds) {
        shareOrder = radioShareNormalizeOrder(shareOrder, availableIds, isSupportShare)
        selectedBlockIds = selectedBlockIds.intersect(availableIds).ifEmpty {
            radioShareDefaultSelectedBlocks(isSupportShare, availableIds)
        }
    }

    var showThemeSheet by remember { mutableStateOf(false) }
    var showContentSheet by remember { mutableStateOf(false) }
    var selectedShareTheme by remember { mutableStateOf(isDark) }
    var isGeneratingShare by remember { mutableStateOf(false) }
    var incQrCode by remember(marker.id, isSupportShare) { mutableStateOf(true) }
    var incConfidential by remember(marker.id, isSupportShare) {
        mutableStateOf(
            if (isSupportShare) {
                SharePrefs.supportConfidentialEnabled.read(prefs)
            } else {
                SharePrefs.siteConfidentialEnabled.read(prefs)
            }
        )
    }

    val txtShareSite = stringResource(R.string.appstrings_share_site)
    val txtShareAs = stringResource(R.string.appstrings_share_as)
    val txtThemeLight = stringResource(R.string.appstrings_theme_light)
    val txtLightModeDesc = stringResource(R.string.appstrings_light_mode_desc)
    val txtThemeDark = stringResource(R.string.appstrings_theme_dark)
    val txtDarkModeDesc = stringResource(R.string.appstrings_dark_mode_desc)
    val txtImageContent = stringResource(R.string.appstrings_image_content)
    val txtGenerateImage = stringResource(R.string.appstrings_generate_image)
    val txtShareSiteVia = stringResource(R.string.appstrings_share_site_via)
    val txtGeneratedBy = stringResource(R.string.appstrings_generated_by)
    val txtInitError = stringResource(R.string.appstrings_init_error)
    val txtPreparing = stringResource(R.string.appstrings_share_image_preparing_in_progress)
    val txtMove = stringResource(R.string.appstrings_move)
    val txtShareConfidentialOption = stringResource(R.string.appstrings_share_confidential_option)
    val txtShareConfidentialDesc = stringResource(R.string.appstrings_share_confidential_desc)

    LaunchedEffect(showThemeSheet) {
        if (showThemeSheet) {
            incConfidential = if (isSupportShare) {
                SharePrefs.supportConfidentialEnabled.read(prefs)
            } else {
                SharePrefs.siteConfidentialEnabled.read(prefs)
            }
        }
    }

    if (outlinedButton) {
        OutlinedButton(
            onClick = { safeClick { showThemeSheet = true } },
            modifier = modifier.fillMaxWidth().height(56.dp),
            shape = buttonShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(txtShareSite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    } else {
        Button(
            onClick = { safeClick { showThemeSheet = true } },
            modifier = modifier.fillMaxWidth().height(56.dp),
            shape = buttonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(txtShareSite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }

    if (isGeneratingShare) {
        ShareGenerationDialog(txtPreparing)
    }

    if (showThemeSheet) {
        ModalBottomSheet(onDismissRequest = { showThemeSheet = false }, sheetState = sheetState, containerColor = sheetBgColor) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp, start = 16.dp, end = 16.dp)) {
                Text(txtShareAs, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp))
                ThemeOptionItem(iconVector = Icons.Outlined.LightMode, label = txtThemeLight, subLabel = txtLightModeDesc, useOneUi = useOneUi) {
                    safeClick {
                        selectedShareTheme = false
                        showThemeSheet = false
                        showContentSheet = true
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                ThemeOptionItem(iconVector = Icons.Outlined.DarkMode, label = txtThemeDark, subLabel = txtDarkModeDesc, useOneUi = useOneUi) {
                    safeClick {
                        selectedShareTheme = true
                        showThemeSheet = false
                        showContentSheet = true
                    }
                }
            }
        }
    }

    if (showContentSheet) {
        ModalBottomSheet(onDismissRequest = { showContentSheet = false }, sheetState = sheetState, containerColor = sheetBgColor) {
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
                        IconButton(onClick = { safeClick { showContentSheet = false; showThemeSheet = true } }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                        Text(text = txtImageContent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.width(48.dp))
                    }

                    val itemHeight = 48.dp
                    val reorderState = rememberReorderableDragState(
                        items = shareOrder,
                        itemHeight = itemHeight,
                        onOrderChange = { newOrder ->
                            shareOrder = radioShareNormalizeOrder(newOrder, availableIds, isSupportShare)
                        }
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        shareOrder.forEach { blockId ->
                            val block = availableBlocks.firstOrNull { it.id == blockId } ?: return@forEach
                            key(blockId) {
                                val isDragged = reorderState.isDragged(blockId)
                                val dragModifier = reorderState.dragModifier(blockId)
                                val dragOffset = reorderState.offsetFor(blockId)

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(itemHeight)
                                        .zIndex(if (isDragged) 10f else 0f)
                                        .graphicsLayer { translationY = if (isDragged) dragOffset else 0f; scaleX = if (isDragged) 1.02f else 1f; scaleY = if (isDragged) 1.02f else 1f; shadowElevation = if (isDragged) 8.dp.toPx() else 0f }
                                        .background(if (isDragged) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(8.dp))
                                        .then(dragModifier)
                                        .padding(start = 8.dp)
                                ) {
                                    Icon(Icons.Default.DragHandle, contentDescription = txtMove, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(block.label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                                    GeoTowerSwitch(
                                        checked = block.id in selectedBlockIds,
                                        onCheckedChange = { checked ->
                                            selectedBlockIds = if (checked) {
                                                selectedBlockIds + block.id
                                            } else {
                                                selectedBlockIds - block.id
                                            }
                                        },
                                        modifier = Modifier.scale(if (useOneUi) 0.85f else 0.8f),
                                        useOneUi = useOneUi
                                    )
                                }
                            }
                        }

                        TextButton(
                            onClick = {
                                shareOrder = radioShareDefaultBlocks(isSupportShare).filter { it in availableIds }
                                selectedBlockIds = radioShareDefaultSelectedBlocks(isSupportShare, availableIds)
                                incQrCode = true
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.appstrings_reset_to_default), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(stringResource(R.string.brand_qr_code), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                            GeoTowerSwitch(
                                checked = incQrCode,
                                onCheckedChange = { incQrCode = it },
                                modifier = Modifier.scale(if (useOneUi) 0.85f else 0.8f),
                                useOneUi = useOneUi
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(txtShareConfidentialOption, fontWeight = FontWeight.Bold, color = if (incConfidential) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                Text(txtShareConfidentialDesc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            GeoTowerSwitch(
                                checked = incConfidential,
                                onCheckedChange = { incConfidential = it },
                                modifier = Modifier.scale(if (useOneUi) 0.85f else 0.8f),
                                useOneUi = useOneUi
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        if (isGeneratingShare || (isSupportShare && selectedBlockIds.isEmpty())) return@Button
                        isGeneratingShare = true
                        showContentSheet = false
                        currentView.postDelayed({
                            if (isSupportShare) {
                                shareRadioSupportCapture(
                                    context = context,
                                    currentView = currentView,
                                    markers = markers,
                                    distanceStr = distanceStr,
                                    bearingStr = bearingStr,
                                    forceDarkTheme = selectedShareTheme,
                                    txtShareSiteVia = txtShareSiteVia,
                                    txtGeneratedBy = txtGeneratedBy,
                                    txtInitError = txtInitError,
                                    mapView = globalMapRef,
                                    useOneUi = useOneUi,
                                    shareOrder = shareOrder,
                                    includedBlocks = selectedBlockIds,
                                    incQrCode = incQrCode,
                                    incConfidential = incConfidential,
                                    onComplete = { isGeneratingShare = false }
                                )
                            } else {
                                shareRadioSiteCapture(
                                    context = context,
                                    currentView = currentView,
                                    marker = marker,
                                    distanceStr = distanceStr,
                                    bearingStr = bearingStr,
                                    forceDarkTheme = selectedShareTheme,
                                    txtShareSiteVia = txtShareSiteVia,
                                    txtGeneratedBy = txtGeneratedBy,
                                    txtInitError = txtInitError,
                                    mapView = globalMapRef,
                                    useOneUi = useOneUi,
                                    shareOrder = shareOrder,
                                    includedBlocks = selectedBlockIds,
                                    incQrCode = incQrCode,
                                    incConfidential = incConfidential,
                                    onComplete = { isGeneratingShare = false }
                                )
                            }
                        }, 250)
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
                    shape = CircleShape,
                    enabled = selectedBlockIds.isNotEmpty() && !isGeneratingShare
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(txtGenerateImage, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

fun shareRadioSiteCapture(
    context: Context,
    currentView: View,
    marker: RadioMapMarker,
    distanceStr: String,
    bearingStr: String,
    forceDarkTheme: Boolean,
    txtShareSiteVia: String,
    txtGeneratedBy: String,
    txtInitError: String,
    mapView: org.osmdroid.views.MapView?,
    useOneUi: Boolean = AppConfig.useOneUiDesign,
    shareOrder: List<String> = DEFAULT_RADIO_SITE_SHARE_ORDER,
    includedBlocks: Set<String> = shareOrder.toSet(),
    incQrCode: Boolean = true,
    incConfidential: Boolean = false,
    onComplete: (() -> Unit)? = null
) {
    shareRadioCapture(
        context = context,
        currentView = currentView,
        title = context.getString(R.string.appstrings_site_detail_title),
        mainMarker = marker,
        markers = listOf(marker),
        distanceStr = distanceStr,
        bearingStr = bearingStr,
        forceDarkTheme = forceDarkTheme,
        txtShareSiteVia = txtShareSiteVia,
        txtGeneratedBy = txtGeneratedBy,
        txtInitError = txtInitError,
        mapView = mapView,
        useOneUi = useOneUi,
        isSupportShare = false,
        shareOrder = shareOrder,
        includedBlocks = includedBlocks,
        incQrCode = incQrCode,
        incConfidential = incConfidential,
        filePrefix = "Geotower_radio_site",
        onComplete = onComplete
    )
}

fun shareRadioSupportCapture(
    context: Context,
    currentView: View,
    markers: List<RadioMapMarker>,
    distanceStr: String,
    bearingStr: String,
    forceDarkTheme: Boolean,
    txtShareSiteVia: String,
    txtGeneratedBy: String,
    txtInitError: String,
    mapView: org.osmdroid.views.MapView?,
    useOneUi: Boolean = AppConfig.useOneUiDesign,
    shareOrder: List<String> = DEFAULT_RADIO_SUPPORT_SHARE_ORDER,
    includedBlocks: Set<String> = shareOrder.toSet(),
    incQrCode: Boolean = true,
    incConfidential: Boolean = false,
    onComplete: (() -> Unit)? = null
) {
    val mainMarker = markers.firstOrNull { !it.isCluster } ?: markers.firstOrNull() ?: run {
        onComplete?.invoke()
        return
    }
    shareRadioCapture(
        context = context,
        currentView = currentView,
        title = context.getString(R.string.appstrings_support_detail_title),
        mainMarker = mainMarker,
        markers = markers.filterNot { it.isCluster },
        distanceStr = distanceStr,
        bearingStr = bearingStr,
        forceDarkTheme = forceDarkTheme,
        txtShareSiteVia = txtShareSiteVia,
        txtGeneratedBy = txtGeneratedBy,
        txtInitError = txtInitError,
        mapView = mapView,
        useOneUi = useOneUi,
        isSupportShare = true,
        shareOrder = shareOrder,
        includedBlocks = includedBlocks,
        incQrCode = incQrCode,
        incConfidential = incConfidential,
        filePrefix = "Geotower_radio_support",
        onComplete = onComplete
    )
}

private fun shareRadioCapture(
    context: Context,
    currentView: View,
    title: String,
    mainMarker: RadioMapMarker,
    markers: List<RadioMapMarker>,
    distanceStr: String,
    bearingStr: String,
    forceDarkTheme: Boolean,
    txtShareSiteVia: String,
    txtGeneratedBy: String,
    txtInitError: String,
    mapView: org.osmdroid.views.MapView?,
    useOneUi: Boolean,
    isSupportShare: Boolean,
    shareOrder: List<String>,
    includedBlocks: Set<String>,
    incQrCode: Boolean,
    incConfidential: Boolean,
    filePrefix: String,
    onComplete: (() -> Unit)?
) {
    val mapBitmap = if (RADIO_SHARE_MAP in includedBlocks) captureShareMapBitmap(mapView) else null
    val visibleMarkers = markers.ifEmpty { listOf(mainMarker) }
    try {
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(currentView.findViewTreeLifecycleOwner())
            setViewTreeSavedStateRegistryOwner(currentView.findViewTreeSavedStateRegistryOwner())
            setViewTreeViewModelStoreOwner(currentView.findViewTreeViewModelStoreOwner())

            setContent {
                val colors = if (forceDarkTheme) darkColorScheme() else lightColorScheme()
                MaterialTheme(colorScheme = colors) {
                    RadioShareCaptureContent(
                        title = title,
                        mainMarker = mainMarker,
                        markers = visibleMarkers,
                        distanceStr = distanceStr,
                        bearingStr = bearingStr,
                        mapBitmap = mapBitmap,
                        txtGeneratedBy = txtGeneratedBy,
                        useOneUi = useOneUi,
                        forceDarkTheme = forceDarkTheme,
                        isSupportShare = isSupportShare,
                        shareOrder = shareOrder,
                        includedBlocks = includedBlocks,
                        incQrCode = incQrCode,
                        incConfidential = incConfidential
                    )
                }
            }
        }

        val rootView = currentView.rootView as? ViewGroup ?: run {
            onComplete?.invoke()
            return
        }
        composeView.translationX = 10000f
        rootView.addView(composeView)

        composeView.post {
            try {
                val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(420.dpToPx(context), View.MeasureSpec.EXACTLY)
                val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                composeView.measure(widthMeasureSpec, heightMeasureSpec)
                composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)

                val bitmap = Bitmap.createBitmap(composeView.measuredWidth, composeView.measuredHeight, Bitmap.Config.ARGB_8888)
                composeView.draw(Canvas(bitmap))
                rootView.removeView(composeView)

                val cachePath = File(context.cacheDir, "images")
                cachePath.mkdirs()
                val safeId = listOf(mainMarker.supportId, mainMarker.stationId, mainMarker.id)
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
                    .shareSafeFilePart()
                val file = File(cachePath, "${filePrefix}_$safeId.png")
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, uri)
                    type = "image/png"
                    clipData = ClipData.newUri(context.contentResolver, "Capture", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, txtShareSiteVia))
                onComplete?.invoke()
            } catch (e: Exception) {
                AppLogger.w(TAG_SHARE_IMAGE, "Radio share capture failed", e)
                Toast.makeText(context, txtInitError, Toast.LENGTH_SHORT).show()
                if (composeView.parent != null) rootView.removeView(composeView)
                onComplete?.invoke()
            }
        }
    } catch (e: Exception) {
        AppLogger.w(TAG_SHARE_IMAGE, "Radio share initialization failed", e)
        Toast.makeText(context, txtInitError, Toast.LENGTH_SHORT).show()
        onComplete?.invoke()
    }
}

@Composable
private fun RadioShareCaptureContent(
    title: String,
    mainMarker: RadioMapMarker,
    markers: List<RadioMapMarker>,
    distanceStr: String,
    bearingStr: String,
    mapBitmap: Bitmap?,
    txtGeneratedBy: String,
    useOneUi: Boolean,
    forceDarkTheme: Boolean,
    isSupportShare: Boolean,
    shareOrder: List<String>,
    includedBlocks: Set<String>,
    incQrCode: Boolean,
    incConfidential: Boolean
) {
    val txtSupportDetails = stringResource(R.string.appstrings_support_details_title)
    val txtIdSupport = stringResource(R.string.appstrings_id_support_label).shareLabelText()
    val txtStationAnfr = stringResource(R.string.appstrings_anfr_station_number).shareLabelText()
    val txtAddress = stringResource(R.string.appstrings_address_label).shareLabelText()
    val txtGps = stringResource(R.string.appstrings_gps_label).shareLabelText()
    val txtSupportHeight = stringResource(R.string.appstrings_support_height).shareLabelText()
    val txtSupportNature = stringResource(R.string.appstrings_support_nature).shareLabelText()
    val txtOwner = stringResource(R.string.appstrings_owner).shareLabelText()
    val txtDistance = stringResource(R.string.appstrings_distance_label).shareLabelText()
    val txtFromMyPosition = stringResource(R.string.appstrings_from_my_position)
    val txtBearing = stringResource(R.string.appstrings_bearing_label).shareLabelText()
    val txtFrequencies = stringResource(R.string.appstrings_frequencies_title)
    val txtRadioTitle = stringResource(R.string.appstrings_radio_share_radio_title)
    val txtCategories = stringResource(R.string.appstrings_radio_share_categories)
    val txtNetwork = stringResource(R.string.appstrings_radio_share_network)
    val txtSystems = stringResource(R.string.appstrings_radio_share_systems)
    val txtEmitters = stringResource(R.string.appstrings_radio_share_emitters)
    val txtAntennas = stringResource(R.string.appstrings_radio_share_antennas)
    val txtAzimuths = stringResource(R.string.appstrings_radio_share_block_azimuths)
    val txtDetailsAnfr = stringResource(R.string.appstrings_radio_share_block_extra)
    val txtPrograms = stringResource(R.string.appstrings_radio_share_block_programs)
    val txtProgramFallback = stringResource(R.string.appstrings_radio_share_program_fallback)
    val txtOther = stringResource(R.string.appstrings_radio_share_other)
    val txtSupportEntriesTitle = stringResource(R.string.appstrings_radio_share_block_support_entries)
    val txtCapMeasured = stringResource(R.string.appstrings_radio_share_cap_measured_short)
    val txtSupportHeightShort = stringResource(R.string.appstrings_radio_share_support_height_short)
    val txtElements = stringResource(R.string.appstrings_radio_share_elements)
    val blockShape = if (useOneUi) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)
    val cardBgColor = if (useOneUi && forceDarkTheme) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!isSupportShare) {
                RadioShareHeaderBlock(mainMarker, cardBgColor, blockShape)
            }

            shareOrder.forEach { blockId ->
                if (blockId !in includedBlocks) return@forEach
                when (blockId) {
                    RADIO_SHARE_HEADER -> Unit
                    RADIO_SHARE_BEARING_HEIGHT -> if (!isSupportShare) {
                        RadioShareBearingHeightBlock(
                            marker = mainMarker,
                            bearingStr = bearingStr,
                            txtCapMeasured = txtCapMeasured,
                            txtSupportHeightShort = txtSupportHeightShort,
                            incConfidential = incConfidential,
                            cardBgColor = cardBgColor,
                            blockShape = blockShape
                        )
                    }
                    RADIO_SHARE_MAP -> {
                        RadioShareMapBlock(mapBitmap, cardBgColor, blockShape)
                    }
                    RADIO_SHARE_SUPPORT -> {
                        RadioShareSupportBlock(
                            marker = mainMarker,
                            isSupportShare = isSupportShare,
                            distanceStr = distanceStr,
                            bearingStr = bearingStr,
                            txtSupportDetails = txtSupportDetails,
                            txtIdSupport = txtIdSupport,
                            txtAddress = txtAddress,
                            txtGps = txtGps,
                            txtSupportHeight = txtSupportHeight,
                            txtSupportNature = txtSupportNature,
                            txtOwner = txtOwner,
                            txtDistance = txtDistance,
                            txtFromMyPosition = txtFromMyPosition,
                            txtBearing = txtBearing,
                            incConfidential = incConfidential,
                            cardBgColor = cardBgColor,
                            blockShape = blockShape
                        )
                    }
                    RADIO_SHARE_IDS -> if (!isSupportShare) {
                        RadioShareInfoCard(
                            title = stringResource(R.string.appstrings_identifiers),
                            icon = Icons.Default.Tag,
                            cardBgColor = cardBgColor,
                            blockShape = blockShape
                        ) {
                            RadioShareInfoLine(txtIdSupport, mainMarker.supportId.ifBlank { "--" })
                            RadioShareInfoLine(txtStationAnfr, mainMarker.stationId.ifBlank { "--" })
                        }
                    }
                    RADIO_SHARE_ADDRESS -> if (!isSupportShare) {
                        RadioShareInfoCard(
                            title = txtAddress,
                            icon = Icons.Default.Info,
                            cardBgColor = cardBgColor,
                            blockShape = blockShape
                        ) {
                            RadioShareInfoLine(txtAddress, mainMarker.addressSummary)
                            RadioShareInfoLine(txtGps, formatRadioShareGps(mainMarker.latitude, mainMarker.longitude))
                            if (incConfidential) {
                                Text(
                                    stringResource(R.string.appstrings_distance_hidden),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                RadioShareInfoLine(txtDistance, "$distanceStr $txtFromMyPosition")
                            }
                        }
                    }
                    RADIO_SHARE_RADIO -> {
                        RadioShareRadioBlock(
                            marker = mainMarker,
                            txtRadioTitle = txtRadioTitle,
                            txtCategories = txtCategories,
                            txtNetwork = txtNetwork,
                            txtSystems = txtSystems,
                            txtFrequencies = txtFrequencies,
                            txtEmitters = txtEmitters,
                            txtAntennas = txtAntennas,
                            cardBgColor = cardBgColor,
                            blockShape = blockShape
                        )
                    }
                    RADIO_SHARE_PROGRAMS -> {
                        RadioShareProgramsBlock(
                            marker = mainMarker,
                            txtPrograms = txtPrograms,
                            txtProgramFallback = txtProgramFallback,
                            txtOther = txtOther,
                            cardBgColor = cardBgColor,
                            blockShape = blockShape
                        )
                    }
                    RADIO_SHARE_AZIMUTHS -> {
                        RadioShareAzimuthsBlock(mainMarker, txtAzimuths, txtOther, txtAntennas, cardBgColor, blockShape)
                    }
                    RADIO_SHARE_EXTRA -> {
                        RadioShareExtraDetailsBlock(mainMarker, txtDetailsAnfr, cardBgColor, blockShape)
                    }
                    RADIO_SHARE_SUPPORT_ENTRIES -> if (isSupportShare) {
                        RadioShareSupportEntriesBlock(markers, txtSupportEntriesTitle, txtOther, txtElements, cardBgColor, blockShape)
                    }
                }
            }

            if (incQrCode) {
                RadioShareQrBlock(mainMarker = mainMarker, isSupportShare = isSupportShare)
            }

            Text(
                text = txtGeneratedBy,
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun RadioShareHeaderBlock(
    marker: RadioMapMarker,
    cardBgColor: Color,
    blockShape: RoundedCornerShape
) {
    Card(
        shape = blockShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)),
                contentAlignment = Alignment.Center
            ) {
                RadioUsageIcon(
                    serviceMask = marker.serviceMask,
                    systemMask = marker.systemMask,
                    size = 48.dp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = marker.networkName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = marker.systemSummary ?: marker.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RadioShareBearingHeightBlock(
    marker: RadioMapMarker,
    bearingStr: String,
    txtCapMeasured: String,
    txtSupportHeightShort: String,
    incConfidential: Boolean,
    cardBgColor: Color,
    blockShape: RoundedCornerShape
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val rotation = bearingStr.removeSuffix("\u00B0").toFloatOrNull() ?: 0f
        if (!incConfidential) {
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = blockShape,
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(txtCapMeasured, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Icon(
                        Icons.Default.Navigation,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).rotate(rotation),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(bearingStr, fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            modifier = (if (incConfidential) Modifier.fillMaxWidth() else Modifier.weight(1f)).fillMaxHeight(),
            shape = blockShape,
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(txtSupportHeightShort, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Icon(
                    Icons.Default.VerticalAlignTop,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(marker.supportHeightSummary ?: "--", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RadioShareMapBlock(
    mapBitmap: Bitmap?,
    cardBgColor: Color,
    blockShape: RoundedCornerShape
) {
    if (mapBitmap == null) return
    Card(
        shape = blockShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Image(
            bitmap = mapBitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(180.dp),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun RadioShareInfoCard(
    title: String,
    icon: ImageVector,
    cardBgColor: Color,
    blockShape: RoundedCornerShape,
    leadingContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = blockShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingContent != null) {
                    leadingContent()
                } else {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )
            content()
        }
    }
}

@Composable
private fun RadioShareSupportBlock(
    marker: RadioMapMarker,
    isSupportShare: Boolean,
    distanceStr: String,
    bearingStr: String,
    txtSupportDetails: String,
    txtIdSupport: String,
    txtAddress: String,
    txtGps: String,
    txtSupportHeight: String,
    txtSupportNature: String,
    txtOwner: String,
    txtDistance: String,
    txtFromMyPosition: String,
    txtBearing: String,
    incConfidential: Boolean,
    cardBgColor: Color,
    blockShape: RoundedCornerShape
) {
    RadioShareInfoCard(
        title = txtSupportDetails,
        icon = Icons.Default.Info,
        cardBgColor = cardBgColor,
        blockShape = blockShape
    ) {
        if (isSupportShare) {
            RadioShareInfoLine(txtIdSupport, marker.supportId.ifBlank { "--" })
            RadioShareInfoLine(txtAddress, marker.addressSummary)
            RadioShareInfoLine(txtGps, formatRadioShareGps(marker.latitude, marker.longitude))
            RadioShareInfoLine(txtSupportHeight, marker.supportHeightSummary)
        }
        RadioShareInfoLine(txtSupportNature, marker.supportNatureSummary)
        RadioShareInfoLine(txtOwner, marker.supportOwnerSummary)
        if (incConfidential) {
            Text(
                stringResource(R.string.appstrings_distance_hidden),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            RadioShareInfoLine(txtDistance, "$distanceStr $txtFromMyPosition")
            RadioShareInfoLine(txtBearing, bearingStr)
        }
    }
}

@Composable
private fun RadioShareRadioBlock(
    marker: RadioMapMarker,
    txtRadioTitle: String,
    txtCategories: String,
    txtNetwork: String,
    txtSystems: String,
    txtFrequencies: String,
    txtEmitters: String,
    txtAntennas: String,
    cardBgColor: Color,
    blockShape: RoundedCornerShape
) {
    RadioShareInfoCard(
        title = txtRadioTitle,
        icon = Icons.Default.Info,
        cardBgColor = cardBgColor,
        blockShape = blockShape,
        leadingContent = {
            RadioUsageIcon(
                serviceMask = marker.serviceMask,
                systemMask = marker.systemMask,
                size = 22.dp
            )
        }
    ) {
        RadioShareInfoLine(txtCategories, radioShareUsageSummary(marker))
        RadioShareInfoLine(txtNetwork, marker.networkName)
        RadioShareInfoLine(txtSystems, marker.systemSummary)
        RadioShareInfoLine(txtFrequencies, marker.frequencySummary)
        RadioShareInfoLine(txtEmitters, marker.emitterCount.takeIf { it > 0 }?.toString())
        RadioShareInfoLine(txtAntennas, marker.antennaCount.takeIf { it > 0 }?.toString())
    }
}

@Composable
private fun RadioShareProgramsBlock(
    marker: RadioMapMarker,
    txtPrograms: String,
    txtProgramFallback: String,
    txtOther: String,
    cardBgColor: Color,
    blockShape: RoundedCornerShape
) {
    val programs = marker.broadcastPrograms
    if (programs.isEmpty()) return
    RadioShareInfoCard(
        title = txtPrograms,
        icon = Icons.Default.Info,
        cardBgColor = cardBgColor,
        blockShape = blockShape,
        leadingContent = {
            RadioUsageIcon(
                serviceMask = marker.serviceMask,
                systemMask = marker.systemMask,
                size = 22.dp
            )
        }
    ) {
        programs.take(12).forEach { program ->
            RadioShareInfoLine(program.serviceName, program.detailLabel ?: txtProgramFallback)
        }
        if (programs.size > 12) {
            RadioShareInfoLine(txtOther, "+${programs.size - 12} ${txtPrograms.lowercase(Locale.ROOT)}")
        }
    }
}

@Composable
private fun RadioShareAzimuthsBlock(
    marker: RadioMapMarker,
    txtAzimuths: String,
    txtOther: String,
    txtAntennas: String,
    cardBgColor: Color,
    blockShape: RoundedCornerShape
) {
    if (marker.antennaLines.isEmpty()) return
    RadioShareInfoCard(
        title = txtAzimuths,
        icon = Icons.Default.Navigation,
        cardBgColor = cardBgColor,
        blockShape = blockShape
    ) {
        marker.antennaLines.take(12).forEach { line ->
            val label = line.substringBefore(":", txtAntennas).trim().ifBlank { txtAntennas }
            val value = line.substringAfter(":", line).trim()
            RadioShareInfoLine(label, value)
        }
        if (marker.antennaLines.size > 12) {
            RadioShareInfoLine(txtOther, "+${marker.antennaLines.size - 12} ${txtAntennas.lowercase(Locale.ROOT)}")
        }
    }
}

@Composable
private fun RadioShareExtraDetailsBlock(
    marker: RadioMapMarker,
    txtDetailsAnfr: String,
    cardBgColor: Color,
    blockShape: RoundedCornerShape
) {
    val details = radioShareExtraDetailLines(marker)
    if (details.isEmpty()) return
    RadioShareInfoCard(
        title = txtDetailsAnfr,
        icon = Icons.Default.Info,
        cardBgColor = cardBgColor,
        blockShape = blockShape
    ) {
        details.forEach { (label, value) ->
            RadioShareInfoLine(label, value)
        }
    }
}

@Composable
private fun RadioShareSupportEntriesBlock(
    markers: List<RadioMapMarker>,
    txtSupportEntriesTitle: String,
    txtOther: String,
    txtElements: String,
    cardBgColor: Color,
    blockShape: RoundedCornerShape
) {
    if (markers.isEmpty()) return
    RadioShareInfoCard(
        title = txtSupportEntriesTitle,
        icon = Icons.Default.WifiTethering,
        cardBgColor = cardBgColor,
        blockShape = blockShape
    ) {
        markers.take(16).forEach { marker ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)),
                    contentAlignment = Alignment.Center
                ) {
                    RadioUsageIcon(
                        serviceMask = marker.serviceMask,
                        systemMask = marker.systemMask,
                        size = 34.dp
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = marker.networkName,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    )
                    Text(
                        text = marker.broadcastProgramSummary ?: marker.systemSummary ?: marker.subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        if (markers.size > 16) {
            RadioShareInfoLine(txtOther, "+${markers.size - 16} $txtElements")
        }
    }
}

@Composable
private fun RadioShareQrBlock(
    mainMarker: RadioMapMarker,
    isSupportShare: Boolean
) {
    val qrUri = remember(mainMarker.stationId, mainMarker.supportId, isSupportShare) {
        radioShareQrUri(mainMarker, isSupportShare)
    }
    val qrBitmap = remember(qrUri) { generateQrCodeBitmap(qrUri, 200) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.brand_qr_code),
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(horizontalAlignment = Alignment.Start) {
            Text(text = stringResource(R.string.appstrings_scan_to_open), fontSize = 11.sp, color = Color.Gray)
            Text(
                text = stringResource(R.string.appstrings_geo_tower_app),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun RadioShareInfoLine(
    label: String,
    value: String?
) {
    val cleanValue = value?.takeIf { it.isNotBlank() } ?: return
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                append("$label : ")
            }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                append(cleanValue)
            }
        },
        fontSize = 14.sp,
        lineHeight = 19.sp,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

private fun formatRadioShareGps(latitude: Double, longitude: Double): String {
    return String.format(Locale.US, "%.5f%s, %.5f%s", latitude, "\u00B0", longitude, "\u00B0")
}

@Composable
private fun radioShareUsageSummary(marker: RadioMapMarker): String {
    val tv = stringResource(R.string.appstrings_radio_category_tv)
    val radio = stringResource(R.string.appstrings_radio_category_radio)
    val privateMobile = stringResource(R.string.appstrings_radio_category_private_mobile)
    val fh = stringResource(R.string.appstrings_radio_category_fh)
    val other = stringResource(R.string.appstrings_radio_category_other)
    return radioUsageKindsFor(marker.serviceMask, marker.systemMask)
        .map { kind ->
            when (kind) {
                RadioUsageKind.Tv -> tv
                RadioUsageKind.Radio -> radio
                RadioUsageKind.PrivateMobile -> privateMobile
                RadioUsageKind.Fh -> fh
                RadioUsageKind.Other -> other
            }
        }
        .distinct()
        .joinToString(", ")
}

@Composable
private fun radioShareAvailableBlocks(
    marker: RadioMapMarker,
    markers: List<RadioMapMarker>,
    isSupportShare: Boolean
): List<RadioShareBlock> {
    val order = radioShareDefaultBlocks(isSupportShare)
    return order.mapNotNull { blockId ->
        when (blockId) {
            RADIO_SHARE_HEADER -> null
            RADIO_SHARE_BEARING_HEIGHT -> if (!isSupportShare) RadioShareBlock(blockId, stringResource(R.string.appstrings_radio_share_block_bearing_height)) else null
            RADIO_SHARE_MAP -> RadioShareBlock(blockId, stringResource(R.string.appstrings_radio_share_block_map))
            RADIO_SHARE_SUPPORT -> RadioShareBlock(blockId, stringResource(R.string.appstrings_share_support_option))
            RADIO_SHARE_IDS -> if (!isSupportShare) RadioShareBlock(blockId, stringResource(R.string.appstrings_share_ids_option)) else null
            RADIO_SHARE_ADDRESS -> if (!isSupportShare) RadioShareBlock(blockId, stringResource(R.string.appstrings_share_address_option)) else null
            RADIO_SHARE_RADIO -> if (!isSupportShare) RadioShareBlock(blockId, stringResource(R.string.appstrings_radio_share_block_radio_freqs)) else null
            RADIO_SHARE_PROGRAMS -> if (!isSupportShare && marker.broadcastPrograms.isNotEmpty()) RadioShareBlock(blockId, stringResource(R.string.appstrings_radio_share_block_programs)) else null
            RADIO_SHARE_AZIMUTHS -> if (!isSupportShare && marker.antennaLines.isNotEmpty()) RadioShareBlock(blockId, stringResource(R.string.appstrings_radio_share_block_azimuths)) else null
            RADIO_SHARE_EXTRA -> if (!isSupportShare && radioShareExtraDetailLines(marker).isNotEmpty()) RadioShareBlock(blockId, stringResource(R.string.appstrings_radio_share_block_extra)) else null
            RADIO_SHARE_SUPPORT_ENTRIES -> if (isSupportShare && markers.isNotEmpty()) RadioShareBlock(blockId, stringResource(R.string.appstrings_radio_share_block_support_entries)) else null
            else -> null
        }
    }
}

private fun radioShareDefaultBlocks(isSupportShare: Boolean): List<String> {
    return if (isSupportShare) DEFAULT_RADIO_SUPPORT_SHARE_ORDER else DEFAULT_RADIO_SITE_SHARE_ORDER
}

private fun radioShareDefaultSelectedBlocks(
    isSupportShare: Boolean,
    availableIds: Set<String>
): Set<String> {
    return radioShareDefaultBlocks(isSupportShare)
        .filter { it in availableIds }
        .toSet()
        .ifEmpty { availableIds }
}

private fun radioShareNormalizeOrder(
    currentOrder: List<String>,
    availableIds: Set<String>,
    isSupportShare: Boolean
): List<String> {
    val defaultOrder = radioShareDefaultBlocks(isSupportShare)
    val kept = currentOrder.filter { it in availableIds }.distinct()
    val missing = defaultOrder.filter { it in availableIds && it !in kept }
    return (kept + missing).ifEmpty { defaultOrder.filter { it in availableIds } }
}

private fun radioShareQrUri(marker: RadioMapMarker, isSupportShare: Boolean): String {
    val supportId = marker.supportId.ifBlank { marker.id }
    return if (isSupportShare || marker.stationId.isBlank()) {
        "geotower://support/${Uri.encode(supportId)}"
    } else {
        "geotower://radio/${Uri.encode(marker.stationId)}/${Uri.encode(supportId)}"
    }
}

private fun radioShareExtraDetailLines(marker: RadioMapMarker): List<Pair<String, String>> {
    val alreadyDisplayed = setOf("adresse", "support", "systemes", "frequences", "programmes", "antennes")
    return marker.detailText
        ?.lineSequence()
        ?.mapNotNull { rawLine ->
            val label = rawLine.substringBefore(":", missingDelimiterValue = "").trim()
            val value = rawLine.substringAfter(":", missingDelimiterValue = "").trim()
            if (label.isBlank() || value.isBlank() || label.lowercase(Locale.ROOT) in alreadyDisplayed) {
                null
            } else {
                label to value
            }
        }
        ?.distinct()
        ?.toList()
        .orEmpty()
}

@Composable
private fun ShareSiteFrequenciesBlock(
    info: LocalisationEntity,
    technique: TechniqueEntity?
) {
    SiteFrequenciesBlock(
        info = info,
        technique = technique,
        formattedAzimuths = "",
        cardBgColor = MaterialTheme.colorScheme.surfaceVariant,
        blockShape = RoundedCornerShape(12.dp)
    )
}

fun shareFullAntennaCapture(
    context: Context,
    currentView: View,
    info: LocalisationEntity,
    physique: PhysiqueEntity?,
    technique: TechniqueEntity?,
    hsDataMap: Map<String, fr.geotower.data.models.SiteHsEntity>,
    speedtestData: fr.geotower.data.api.SqSpeedtestData?, // 🚨 NEW
    distanceStr: String,
    bearingStr: String,
    forceDarkTheme: Boolean,
    txtTitle: String,
    txtAddressLabel: String,
    txtNotSpecified: String,
    txtGpsLabel: String,
    txtSupportHeight: String,
    txtDistanceLabel: String,
    txtFromMyPosition: String,
    txtBearingLabel: String,
    txtGeneratedBy: String,
    txtShareSiteVia: String,
    txtimplementation: String,
    txtLastModification: String,
    txtIdentifiers: String,
    txtIdNumber: String,
    txtFrequenciesTitle: String,
    txtBandsNotSpecified: String,
    txtInService: String,
    txtTechnically: String,
    txtUnknownStatus: String,
    txtAnfrStationNumber: String,
    txtDates: String,
    txtError: String,
    txtProjectApproved: String,
    txtActivatedOn: String,
    txtDateNotSpecifiedAnfr: String,
    txtPanelHeightsTitle: String,
    txtAzimuths: String,
    txtIdSupportLabel: String,
    txtSupportDetailsTitle: String,
    txtSupportNature: String,
    txtOwner: String,
    txtExploitant: String,
    txtAntennaType: String,
    mapBitmap: Bitmap?,
    txtInitError: String,
    photoBitmaps: List<Bitmap>,
    txtCommunityPhotosTitle: String,
    incMap: Boolean,
    incSupport: Boolean,
    incHeights: Boolean,
    incIds: Boolean,
    incDates: Boolean,
    incAddress: Boolean,
    incFreqs: Boolean,
    incSpeedtest: Boolean, // 🚨 NEW
    incThroughput: Boolean,
    incConfidential: Boolean,
    incQrCode: Boolean,
    incSplitImage: Boolean,
    shareOrder: List<String>,
    elevationProfileBitmap: Bitmap? = null,
    onComplete: (() -> Unit)? = null
) {
    val composeView = ComposeView(context).apply {
        setViewTreeLifecycleOwner(currentView.findViewTreeLifecycleOwner())
        setViewTreeSavedStateRegistryOwner(currentView.findViewTreeSavedStateRegistryOwner())
        setViewTreeViewModelStoreOwner(currentView.findViewTreeViewModelStoreOwner())
        setContent {
            MaterialTheme(colorScheme = if (forceDarkTheme) darkColorScheme() else lightColorScheme()) {
                Surface(color = Color.Transparent) { // ⚠️ TRÈS IMPORTANT : Fond transparent pour ne pas colorer le vide

                    val distanceUnit = AppConfig.distanceUnit.intValue
                    val formattedHeights = if (info.azimuts.isNullOrBlank()) ""
                    else {
                        val heights = info.azimuts?.split(",")
                            ?.mapNotNull {
                                it.substringAfter("(", "").substringBefore("m", "").trim()
                                    .toFloatOrNull()
                            }
                            ?.filter { it > 0f }?.distinct()?.sorted() ?: emptyList()
                        if (heights.isNotEmpty()) heights.joinToString(" - ") { formatSharePanelHeightMeters(it.toDouble(), distanceUnit) } else ""
                    }

                    // ✅ DÉTECTION DU MODE SCINDÉ
                    val isSplit = incSplitImage && incFreqs

                    // On génère le QR Code une seule fois pour l'utiliser sur les deux images
                    val qrUri = "geotower://site/${info.idAnfr}"
                    val qrBitmap = remember(qrUri) { generateQrCodeBitmap(qrUri, 200) }

                    // ✅ ROW GLOBALE (800dp de large au total si split)
                    Row(modifier = Modifier.wrapContentSize()) {

                        // ==========================================
                        // 📸 IMAGE 1 : Contenu principal
                        // ==========================================
                        Column(
                            modifier = Modifier
                                .width(400.dp)
                                .wrapContentHeight()
                                .background(MaterialTheme.colorScheme.background) // ✅ Le fond est appliqué ici
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                txtTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val logoRes = getDetailLogoRes(info.operateur ?: "")
                                    if (logoRes != null) {
                                        Image(
                                            painter = painterResource(id = logoRes),
                                            contentDescription = null,
                                            modifier = Modifier.size(60.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            info.operateur ?: stringResource(R.string.appstrings_unknown),
                                            fontWeight = FontWeight.Bold
                                        )
                                        val rawTechs =
                                            technique?.technologies?.takeIf { it.isNotBlank() }
                                                ?: info.frequences
                                        Text(
                                            formatTechnologies(rawTechs, stringResource(R.string.appstrings_unknown)),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (info.isZb == 1) {
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "ZB",
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            if (incSupport) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    if (!incConfidential) {
                                        val rotation =
                                            bearingStr.replace("°", "").toFloatOrNull() ?: 0f
                                        Card(
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp).fillMaxSize(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    txtBearingLabel.replace(" : ", ""),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    textAlign = TextAlign.Center
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                Icon(
                                                    Icons.Default.Navigation,
                                                    null,
                                                    Modifier.size(40.dp).rotate(rotation),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                Text(bearingStr, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Card(
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp).fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                txtSupportHeight.replace(" : ", ""),
                                                style = MaterialTheme.typography.labelMedium,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Icon(
                                                Icons.Default.VerticalAlignTop,
                                                null,
                                                Modifier.size(40.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                formatShareHeightMeters(physique?.hauteur),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // 🚨 ON FILTRE L'ORDRE POUR L'IMAGE 1 : Pas de bloc "freq" ni "ids" si l'image est scindée
                            val orderImage1 =
                                if (isSplit) shareOrder.filter { it != "freq" && it != "ids" } else shareOrder

                            orderImage1.forEach { block ->
                                when (block) {
                                    "map" -> {
                                        if (incMap && mapBitmap != null) {
                                            Image(
                                                bitmap = mapBitmap.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxWidth().height(180.dp)
                                                    .clip(RoundedCornerShape(12.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }

                                    "support" -> {
                                        if (incSupport) {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(16.dp)
                                                        .fillMaxWidth()
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            Icons.Default.Info,
                                                            null,
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            txtSupportDetailsTitle,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    HorizontalDivider(
                                                        modifier = Modifier.padding(
                                                            vertical = 12.dp
                                                        ),
                                                        color = MaterialTheme.colorScheme.outlineVariant.copy(
                                                            alpha = 0.5f
                                                        )
                                                    )
                                                    val nature =
                                                        physique?.natureSupport?.takeIf { it.isNotBlank() }
                                                            ?: txtNotSpecified
                                                    val proprietaire =
                                                        physique?.proprietaire?.takeIf { it.isNotBlank() }
                                                            ?: stringResource(R.string.appstrings_unknown)
                                                    val exploitant =
                                                        physique?.exploitant?.takeIf { it.isNotBlank() }
                                                            ?: txtNotSpecified
                                                    Text(
                                                        "$txtSupportNature : $nature",
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        "$txtOwner : $proprietaire",
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        "$txtExploitant : $exploitant",
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    "ids" -> {
                                        if (incIds) {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(16.dp)
                                                        .fillMaxWidth()
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            Icons.Default.Tag,
                                                            null,
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            txtIdentifiers,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    HorizontalDivider(
                                                        modifier = Modifier.padding(
                                                            vertical = 12.dp
                                                        ),
                                                        color = MaterialTheme.colorScheme.outlineVariant.copy(
                                                            alpha = 0.5f
                                                        )
                                                    )
                                                    val idSupportValue =
                                                        physique?.idSupport?.takeIf { it.isNotBlank() }
                                                            ?: txtNotSpecified
                                                    Text(
                                                        "$txtIdSupportLabel $idSupportValue",
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        "$txtAnfrStationNumber ${info.idAnfr}",
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    val arcepNidtValue =
                                                        info.arcepNidt?.takeIf { it.isNotBlank() }
                                                            ?: txtNotSpecified
                                                    Text(
                                                        "${stringResource(arcepIdentifierLabelResId(info.operateur))}$arcepNidtValue",
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    "dates" -> {
                                        if (incDates) {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(16.dp)
                                                        .fillMaxWidth()
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            Icons.Default.DateRange,
                                                            null,
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(txtDates, fontWeight = FontWeight.Bold)
                                                    }
                                                    HorizontalDivider(
                                                        modifier = Modifier.padding(
                                                            vertical = 12.dp
                                                        ),
                                                        color = MaterialTheme.colorScheme.outlineVariant.copy(
                                                            alpha = 0.5f
                                                        )
                                                    )
                                                    val dateImpStr =
                                                        technique?.dateImplantation?.takeIf { it.isNotBlank() }
                                                            ?.let { formatDateToFrench(it) } ?: "-"
                                                    val dateSerStr =
                                                        technique?.dateService?.takeIf { it.isNotBlank() }
                                                            ?.let { formatDateToFrench(it) } ?: "-"
                                                    val dateModStr =
                                                        technique?.dateModif?.takeIf { it.isNotBlank() }
                                                            ?.let { formatDateToFrench(it) } ?: "-"

                                                    Text(
                                                        "$txtimplementation $dateImpStr",
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        "$txtActivatedOn $dateSerStr",
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    if (dateModStr != "-") {
                                                        Text(
                                                            "$txtLastModification $dateModStr",
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    "address" -> {
                                        if (incAddress) {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(16.dp)
                                                        .fillMaxWidth()
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            Icons.Default.Place,
                                                            null,
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            txtAddressLabel.replace(" : ", ""),
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    HorizontalDivider(
                                                        modifier = Modifier.padding(
                                                            vertical = 12.dp
                                                        ),
                                                        color = MaterialTheme.colorScheme.outlineVariant.copy(
                                                            alpha = 0.5f
                                                        )
                                                    )
                                                    val fullAddress =
                                                        technique?.adresse?.takeIf { it.isNotBlank() }
                                                            ?: stringResource(R.string.appstrings_not_specified)
                                                    Text(
                                                        fullAddress,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        "$txtGpsLabel ${
                                                            String.format(
                                                                Locale.US,
                                                                "%.5f, %.5f",
                                                                info.latitude,
                                                                info.longitude
                                                            )
                                                        }",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    if (incConfidential) {
                                                        Text(
                                                            stringResource(R.string.appstrings_distance_hidden),
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    } else {
                                                        Text(
                                                            "$txtDistanceLabel $distanceStr $txtFromMyPosition",
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    "speedtest" -> {
                                        if (incSpeedtest && speedtestData != null) {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(16.dp)
                                                        .fillMaxWidth()
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            Icons.Default.Speed,
                                                            null,
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            stringResource(R.string.appstrings_speedtest_title),
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    HorizontalDivider(
                                                        modifier = Modifier.padding(
                                                            vertical = 12.dp
                                                        ),
                                                        color = MaterialTheme.colorScheme.outlineVariant.copy(
                                                            alpha = 0.5f
                                                        )
                                                    )

                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceEvenly
                                                    ) {
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                            Icon(
                                                                Icons.Default.KeyboardArrowDown,
                                                                null,
                                                                tint = Color(0xFF4CAF50),
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                            Text(
                                                                if (speedtestData.downloadSpeed != null) String.format(
                                                                    Locale.US,
                                                                    "%.1f",
                                                                    speedtestData.downloadSpeed
                                                                ) else "--",
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 16.sp
                                                            )
                                                            Text(
                                                                "Mbps",
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Text(
                                                                stringResource(R.string.appstrings_speedtest_download),
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                            Icon(
                                                                Icons.Default.KeyboardArrowUp,
                                                                null,
                                                                tint = Color(0xFF2196F3),
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                            Text(
                                                                if (speedtestData.uploadSpeed != null) String.format(
                                                                    Locale.US,
                                                                    "%.1f",
                                                                    speedtestData.uploadSpeed
                                                                ) else "--",
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 16.sp
                                                            )
                                                            Text(
                                                                "Mbps",
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Text(
                                                                stringResource(R.string.appstrings_speedtest_upload),
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                            Icon(
                                                                Icons.Default.Timer,
                                                                null,
                                                                tint = Color(0xFFFF9800),
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                            Text(
                                                                if (speedtestData.ping != null) speedtestData.ping.toInt()
                                                                    .toString() else "--",
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 16.sp
                                                            )
                                                            Text(
                                                                "ms",
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Text(
                                                                stringResource(R.string.appstrings_speedtest_ping),
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    "throughput" -> {
                                        if (incThroughput) {
                                            ShareThroughputCalculatorBlock(
                                                info = info,
                                                technique = technique,
                                                physique = physique,
                                                prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE),
                                                cardBgColor = MaterialTheme.colorScheme.surfaceVariant,
                                                blockShape = RoundedCornerShape(12.dp)
                                            )
                                        }
                                    }

                                    "status" -> {
                                        if (AppConfig.shareSiteStatus.value) {
                                            val hsEntity = hsDataMap[info.idAnfr]
                                            val isOutage = hsEntity != null
                                            val outageText =
                                                hsEntity?.let { formatOutageDetails(it) }

                                            val rawTechs =
                                                technique?.technologies?.takeIf { it.isNotBlank() }
                                                    ?: info.frequences ?: ""
                                            val has2G = rawTechs.contains("2G", ignoreCase = true)
                                            val has3G = rawTechs.contains("3G", ignoreCase = true)
                                            val has4G = rawTechs.contains("4G", ignoreCase = true)
                                            val has5G = rawTechs.contains("5G", ignoreCase = true)

                                            val detailsStr = technique?.detailsFrequences ?: ""
                                            val globalStatut = technique?.statut ?: ""
                                            val globalIsProject =
                                                globalStatut.contains("Projet", ignoreCase = true)

                                            fun isTechPlanned(keywords: List<String>): Boolean {
                                                if (detailsStr.isBlank()) return globalIsProject
                                                val lines = detailsStr.split("\n").filter { line ->
                                                    keywords.any { k ->
                                                        line.contains(
                                                            k,
                                                            ignoreCase = true
                                                        )
                                                    }
                                                }
                                                if (lines.isEmpty()) return globalIsProject
                                                return lines.all {
                                                    it.contains(
                                                        "Projet",
                                                        ignoreCase = true
                                                    )
                                                }
                                            }

                                            val is2gProject =
                                                has2G && isTechPlanned(listOf("GSM", "2G"))
                                            val is3gProject =
                                                has3G && isTechPlanned(listOf("UMTS", "3G"))
                                            val is4gProject =
                                                has4G && isTechPlanned(listOf("LTE", "4G"))
                                            val is5gProject =
                                                has5G && isTechPlanned(listOf("NR", "5G"))

                                            val totalTechs =
                                                listOf(has2G, has3G, has4G, has5G).count { it }
                                            val projectTechs = listOf(
                                                is2gProject,
                                                is3gProject,
                                                is4gProject,
                                                is5gProject
                                            ).count { it }
                                            val isEntirelyProject =
                                                totalTechs > 0 && totalTechs == projectTechs

                                            fun serviceStatus(hasTech: Boolean, rawStatus: String?): Boolean? {
                                                return serviceAvailabilityFromOutageCode(
                                                    hasTechnology = hasTech,
                                                    outageCode = rawStatus,
                                                    isOutage = isOutage
                                                )
                                            }

                                            fun isOutageStatusCode(rawStatus: String?): Boolean {
                                                val code = rawStatus
                                                    ?.trim()
                                                    ?.uppercase(Locale.ROOT)
                                                return code == "HS" || code == "DE"
                                            }

                                            val is5gVoiceProject =
                                                is5gProject || isOutageStatusCode(hsEntity?.voix5g)
                                            val is5gDataProject =
                                                is5gProject || (!has5G && isOutageStatusCode(hsEntity?.data5g))

                                            val realTechStatus = mapOf(
                                                "2G" to ServiceStatus(
                                                    isVoixOk = serviceStatus(has2G, hsEntity?.voix2g),
                                                    isInternetOk = serviceStatus(has2G, hsEntity?.data2g),
                                                    isProject = is2gProject),
                                                "3G" to ServiceStatus(
                                                    isVoixOk = serviceStatus(has3G, hsEntity?.voix3g),
                                                    isInternetOk = serviceStatus(has3G, hsEntity?.data3g),
                                                    isProject = is3gProject),
                                                "4G" to ServiceStatus(
                                                    isVoixOk = serviceStatus(has4G, hsEntity?.voix4g),
                                                    isInternetOk = serviceStatus(has4G, hsEntity?.data4g),
                                                    isProject = is4gProject),
                                                "5G" to ServiceStatus(
                                                    isVoixOk = serviceStatus(has5G, hsEntity?.voix5g),
                                                    isInternetOk = serviceStatus(has5G, hsEntity?.data5g),
                                                    isProject = is5gProject,
                                                    isVoixProject = is5gVoiceProject,
                                                    isInternetProject = is5gDataProject)
                                            )

                                            SiteStatusCard(
                                                isProjectSite = isEntirelyProject,
                                                isOutage = isOutage,
                                                outageText = outageText,
                                                outageStartDate = hsEntity?.dateDebut,
                                                outageExpectedRestorationDate = hsEntity?.dateFin,
                                                cardBgColor = MaterialTheme.colorScheme.surfaceVariant,
                                                blockShape = RoundedCornerShape(12.dp),
                                                techStatus = realTechStatus,
                                                outageDetails = hsEntity
                                            )
                                        }
                                    }

                                    "freq" -> {
                                        // Géré dans l'image 2 si scindé, sinon affiché ici (code d'origine)
                                        if (incFreqs) {
                                            if (AppConfig.siteFreqGridDisplay.value) {
                                                ShareSiteFrequenciesBlock(
                                                    info = info,
                                                    technique = technique
                                                )
                                                return@forEach
                                            }

                                            val rawFreqs =
                                                technique?.detailsFrequences ?: info.frequences
                                            val parsedBands = parseAndSortFrequencies(
                                                rawFreqs,
                                                stringResource(R.string.appstrings_unknown),
                                                stringResource(R.string.appstrings_azimuth_not_specified)
                                            ).filter { band ->
                                                when (band.gen) {
                                                    5 -> AppConfig.siteShowTechno5G.value && when (band.value) {
                                                        700 -> AppConfig.siteF5G_700.value; 1400 -> AppConfig.siteF5G_1400.value; 2100 -> AppConfig.siteF5G_2100.value; 3500 -> AppConfig.siteF5G_3500.value; 4200 -> AppConfig.siteF5G_4200.value; 26000 -> AppConfig.siteF5G_26000.value; else -> true
                                                    }

                                                    4 -> AppConfig.siteShowTechno4G.value && when (band.value) {
                                                        700 -> AppConfig.siteF4G_700.value; 800 -> AppConfig.siteF4G_800.value; 900 -> AppConfig.siteF4G_900.value; 1800 -> AppConfig.siteF4G_1800.value; 2100 -> AppConfig.siteF4G_2100.value; 2600 -> AppConfig.siteF4G_2600.value; else -> true
                                                    }

                                                    3 -> AppConfig.siteShowTechno3G.value && when (band.value) {
                                                        900 -> AppConfig.siteF3G_900.value; 2100 -> AppConfig.siteF3G_2100.value; else -> true
                                                    }

                                                    2 -> AppConfig.siteShowTechno2G.value && when (band.value) {
                                                        900 -> AppConfig.siteF2G_900.value; 1800 -> AppConfig.siteF2G_1800.value; else -> true
                                                    }

                                                    else -> AppConfig.siteShowTechnoFH.value
                                                }
                                            }

                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(16.dp)
                                                        .fillMaxWidth()
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            Icons.Default.WifiTethering,
                                                            null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            txtFrequenciesTitle,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 16.sp
                                                        )
                                                    }
                                                    HorizontalDivider(
                                                        modifier = Modifier.padding(
                                                            vertical = 12.dp
                                                        ),
                                                        color = MaterialTheme.colorScheme.outlineVariant.copy(
                                                            alpha = 0.5f
                                                        )
                                                    )

                                                    if (parsedBands.isEmpty()) {
                                                        Text(
                                                            txtBandsNotSpecified,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    } else {
                                                        parsedBands.forEachIndexed { bandIndex, band ->
                                                            if (bandIndex > 0) HorizontalDivider(
                                                                modifier = Modifier.padding(vertical = 12.dp),
                                                                color = MaterialTheme.colorScheme.outlineVariant.copy(
                                                                    alpha = 0.3f
                                                                )
                                                            )
                                                            val (statusColor, statusText) = when {
                                                                band.status.contains(
                                                                    "En service",
                                                                    true
                                                                ) -> Pair(
                                                                    Color(0xFF4CAF50),
                                                                    txtInService
                                                                )

                                                                band.status.contains(
                                                                    "Techniquement",
                                                                    true
                                                                ) -> Pair(
                                                                    Color(0xFF4CAF50),
                                                                    txtTechnically
                                                                )

                                                                band.status.contains(
                                                                    "Approuvé",
                                                                    true
                                                                ) -> Pair(
                                                                    Color(0xFF2196F3),
                                                                    txtProjectApproved
                                                                )

                                                                else -> Pair(
                                                                    Color.Gray,
                                                                    txtUnknownStatus
                                                                )
                                                            }
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(
                                                                    text = band.rawFreq.substringBefore(
                                                                        ":"
                                                                    ).trim(),
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = MaterialTheme.colorScheme.onSurface,
                                                                    fontSize = 14.sp,
                                                                    modifier = Modifier.weight(1f)
                                                                )
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Text(
                                                                        text = statusText,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                        fontWeight = FontWeight.Normal,
                                                                        fontSize = 12.sp
                                                                    ); Spacer(
                                                                    modifier = Modifier.width(
                                                                        6.dp
                                                                    )
                                                                ); Icon(
                                                                    Icons.Default.Circle,
                                                                    null,
                                                                    tint = statusColor,
                                                                    modifier = Modifier.size(10.dp)
                                                                )
                                                                }
                                                            }
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                                                val preciseFreqs =
                                                                    band.rawFreq.substringAfter(
                                                                        ":",
                                                                        ""
                                                                    ).trim()
                                                                if (AppConfig.siteShowSpectrum.value && preciseFreqs.isNotBlank() && preciseFreqs != band.rawFreq.trim()) {
                                                                    val spectrumDisplay = formatSpectrumDisplayDetails(preciseFreqs)
                                                                    if (AppConfig.siteShowSpectrumBand.value) {
                                                                        Text(
                                                                            text = "${stringResource(R.string.appstrings_spectrum_by_band)} :\n\n${spectrumDisplay.detailedFrequencies}",
                                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                            fontSize = 12.sp,
                                                                            fontWeight = FontWeight.Normal,
                                                                            lineHeight = 16.sp
                                                                        )
                                                                    }
                                                                    if (AppConfig.siteShowSpectrumTotal.value && spectrumDisplay.hasTotal) {
                                                                        if (AppConfig.siteShowSpectrumBand.value) Spacer(
                                                                            modifier = Modifier.height(
                                                                                2.dp
                                                                            )
                                                                        )
                                                                        Text(
                                                                            text = "${stringResource(R.string.appstrings_totalspectrum)} : ${spectrumDisplay.totalBandwidth} ${spectrumDisplay.totalUnit}",
                                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                            fontSize = 12.sp,
                                                                            fontWeight = FontWeight.Medium
                                                                        )
                                                                    }
                                                                    if (AppConfig.siteShowSpectrumBand.value || (AppConfig.siteShowSpectrumTotal.value && spectrumDisplay.hasTotal)) {
                                                                        Spacer(
                                                                            modifier = Modifier.height(
                                                                                4.dp
                                                                            )
                                                                        )
                                                                    }
                                                                }
                                                                val dateFormatted =
                                                                    formatDateToFrench(band.date)
                                                                if (dateFormatted.isNotBlank() && dateFormatted != "-") {
                                                                    Text(
                                                                        "$txtActivatedOn$dateFormatted",
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                        fontSize = 12.sp
                                                                    )
                                                                } else {
                                                                    Text(
                                                                        txtDateNotSpecifiedAnfr,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                        fontSize = 12.sp
                                                                    )
                                                                }
                                                                if (band.physDetails.isNotEmpty()) {
                                                                    Spacer(
                                                                        modifier = Modifier.height(
                                                                            6.dp
                                                                        )
                                                                    )
                                                                    band.physDetails.forEach { physDetail ->
                                                                        Row(
                                                                            verticalAlignment = Alignment.CenterVertically,
                                                                            modifier = Modifier.padding(
                                                                                top = 4.dp
                                                                            )
                                                                        ) {
                                                                            Icon(
                                                                                Icons.Default.Explore,
                                                                                null,
                                                                                tint = MaterialTheme.colorScheme.primary,
                                                                                modifier = Modifier.size(
                                                                                    14.dp
                                                                                )
                                                                            ); Spacer(
                                                                            modifier = Modifier.width(
                                                                                6.dp
                                                                            )
                                                                        )
                                                                            val typePart =
                                                                                physDetail.substringBefore(
                                                                                    " : "
                                                                                ).trim()
                                                                            val restPart =
                                                                                physDetail.substringAfter(
                                                                                    " : ",
                                                                                    ""
                                                                                ).trim()
                                                                            val translatedType =
                                                                                AnfrDisplayText.antennaType(
                                                                                    typePart
                                                                                )
                                                                            val finalPhysText =
                                                                                if (restPart.isNotEmpty()) "$translatedType : $restPart" else translatedType
                                                                            Text(
                                                                                text = finalPhysText,
                                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                                fontWeight = FontWeight.Medium,
                                                                                fontSize = 12.sp
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (incQrCode) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (qrBitmap != null) {
                                        Image(
                                            bitmap = qrBitmap.asImageBitmap(),
                                            contentDescription = "QR Code",
                                            modifier = Modifier.size(56.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        ); Spacer(modifier = Modifier.width(16.dp))
                                    }
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text(
                                            text = stringResource(R.string.appstrings_scan_to_open),
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        ); Text(
                                        text = stringResource(R.string.appstrings_geo_tower_app),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    }
                                }
                            }
                            Text(
                                text = txtGeneratedBy,
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }

                        // ==========================================
                        // 📸 IMAGE 2 : Identifiants & Fréquences (Uniquement si Split)
                        // ==========================================
                        if (isSplit) {
                            Column(
                                modifier = Modifier
                                    .width(400.dp)
                                    .wrapContentHeight()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    txtTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val logoRes = getDetailLogoRes(info.operateur ?: "")
                                        if (logoRes != null) {
                                            Image(
                                                painter = painterResource(id = logoRes),
                                                contentDescription = null,
                                                modifier = Modifier.size(60.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                info.operateur ?: stringResource(R.string.appstrings_unknown),
                                                fontWeight = FontWeight.Bold
                                            )
                                            val rawTechs =
                                                technique?.technologies?.takeIf { it.isNotBlank() }
                                                    ?: info.frequences
                                            Text(
                                                formatTechnologies(rawTechs, stringResource(R.string.appstrings_unknown)),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (info.isZb == 1) {
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "ZB",
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                if (incIds && shareOrder.contains("ids")) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Tag,
                                                    null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                ); Spacer(modifier = Modifier.width(8.dp)); Text(
                                                txtIdentifiers,
                                                fontWeight = FontWeight.Bold
                                            )
                                            }
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 12.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(
                                                    alpha = 0.5f
                                                )
                                            )
                                            val idSupportValue =
                                                physique?.idSupport?.takeIf { it.isNotBlank() }
                                                    ?: txtNotSpecified
                                            Text(
                                                "$txtIdSupportLabel $idSupportValue",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                "$txtAnfrStationNumber ${info.idAnfr}",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            val arcepNidtValue =
                                                info.arcepNidt?.takeIf { it.isNotBlank() }
                                                    ?: txtNotSpecified
                                            Text(
                                                "${stringResource(arcepIdentifierLabelResId(info.operateur))}$arcepNidtValue",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (incFreqs && shareOrder.contains("freq")) {
                                    if (AppConfig.siteFreqGridDisplay.value) {
                                        ShareSiteFrequenciesBlock(
                                            info = info,
                                            technique = technique
                                        )
                                    } else {
                                    val rawFreqs = technique?.detailsFrequences ?: info.frequences
                                    val parsedBands = parseAndSortFrequencies(
                                        rawFreqs,
                                        stringResource(R.string.appstrings_unknown),
                                        stringResource(R.string.appstrings_azimuth_not_specified)
                                    ).filter { band ->
                                        when (band.gen) {
                                            5 -> AppConfig.siteShowTechno5G.value && when (band.value) {
                                                700 -> AppConfig.siteF5G_700.value; 1400 -> AppConfig.siteF5G_1400.value; 2100 -> AppConfig.siteF5G_2100.value; 3500 -> AppConfig.siteF5G_3500.value; 4200 -> AppConfig.siteF5G_4200.value; 26000 -> AppConfig.siteF5G_26000.value; else -> true
                                            }

                                            4 -> AppConfig.siteShowTechno4G.value && when (band.value) {
                                                700 -> AppConfig.siteF4G_700.value; 800 -> AppConfig.siteF4G_800.value; 900 -> AppConfig.siteF4G_900.value; 1800 -> AppConfig.siteF4G_1800.value; 2100 -> AppConfig.siteF4G_2100.value; 2600 -> AppConfig.siteF4G_2600.value; else -> true
                                            }

                                            3 -> AppConfig.siteShowTechno3G.value && when (band.value) {
                                                900 -> AppConfig.siteF3G_900.value; 2100 -> AppConfig.siteF3G_2100.value; else -> true
                                            }

                                            2 -> AppConfig.siteShowTechno2G.value && when (band.value) {
                                                900 -> AppConfig.siteF2G_900.value; 1800 -> AppConfig.siteF2G_1800.value; else -> true
                                            }

                                            else -> AppConfig.siteShowTechnoFH.value
                                        }
                                    }

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.WifiTethering,
                                                    null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                ); Spacer(modifier = Modifier.width(8.dp)); Text(
                                                txtFrequenciesTitle,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                            }
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 12.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(
                                                    alpha = 0.5f
                                                )
                                            )

                                            if (parsedBands.isEmpty()) {
                                                Text(
                                                    txtBandsNotSpecified,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                parsedBands.forEachIndexed { bandIndex, band ->
                                                    if (bandIndex > 0) HorizontalDivider(
                                                        modifier = Modifier.padding(
                                                            vertical = 12.dp
                                                        ),
                                                        color = MaterialTheme.colorScheme.outlineVariant.copy(
                                                            alpha = 0.3f
                                                        )
                                                    )
                                                    val (statusColor, statusText) = when {
                                                        band.status.contains(
                                                            "En service",
                                                            true
                                                        ) -> Pair(Color(0xFF4CAF50), txtInService)

                                                        band.status.contains(
                                                            "Techniquement",
                                                            true
                                                        ) -> Pair(Color(0xFF4CAF50), txtTechnically)

                                                        band.status.contains(
                                                            "Approuvé",
                                                            true
                                                        ) -> Pair(
                                                            Color(0xFF2196F3),
                                                            txtProjectApproved
                                                        )

                                                        else -> Pair(Color.Gray, txtUnknownStatus)
                                                    }
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = band.rawFreq.substringBefore(":")
                                                                .trim(),
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            fontSize = 14.sp,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = statusText,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                fontWeight = FontWeight.Normal,
                                                                fontSize = 12.sp
                                                            ); Spacer(modifier = Modifier.width(6.dp)); Icon(
                                                            Icons.Default.Circle,
                                                            null,
                                                            tint = statusColor,
                                                            modifier = Modifier.size(10.dp)
                                                        )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Column(modifier = Modifier.padding(start = 8.dp)) {
                                                        val preciseFreqs =
                                                            band.rawFreq.substringAfter(":", "")
                                                                .trim()
                                                        if (AppConfig.siteShowSpectrum.value && preciseFreqs.isNotBlank() && preciseFreqs != band.rawFreq.trim()) {
                                                            val spectrumDisplay = formatSpectrumDisplayDetails(preciseFreqs)
                                                            if (AppConfig.siteShowSpectrumBand.value) {
                                                                Text(
                                                                    text = "${stringResource(R.string.appstrings_spectrum_by_band)} :\n\n${spectrumDisplay.detailedFrequencies}",
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Normal,
                                                                    lineHeight = 16.sp
                                                                )
                                                            }
                                                            if (AppConfig.siteShowSpectrumTotal.value && spectrumDisplay.hasTotal) {
                                                                if (AppConfig.siteShowSpectrumBand.value) Spacer(
                                                                    modifier = Modifier.height(2.dp)
                                                                )
                                                                Text(
                                                                    text = "${stringResource(R.string.appstrings_totalspectrum)} : ${spectrumDisplay.totalBandwidth} ${spectrumDisplay.totalUnit}",
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Medium
                                                                )
                                                            }
                                                            if (AppConfig.siteShowSpectrumBand.value || (AppConfig.siteShowSpectrumTotal.value && spectrumDisplay.hasTotal)) {
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                            }
                                                        }
                                                        val dateFormatted =
                                                            formatDateToFrench(band.date)
                                                        if (dateFormatted.isNotBlank() && dateFormatted != "-") {
                                                            Text(
                                                                "$txtActivatedOn$dateFormatted",
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                fontSize = 12.sp
                                                            )
                                                        } else {
                                                            Text(
                                                                txtDateNotSpecifiedAnfr,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                fontSize = 12.sp
                                                            )
                                                        }
                                                        if (band.physDetails.isNotEmpty()) {
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            band.physDetails.forEach { physDetail ->
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    modifier = Modifier.padding(top = 4.dp)
                                                                ) {
                                                                    Icon(
                                                                        Icons.Default.Explore,
                                                                        null,
                                                                        tint = MaterialTheme.colorScheme.primary,
                                                                        modifier = Modifier.size(14.dp)
                                                                    ); Spacer(
                                                                    modifier = Modifier.width(
                                                                        6.dp
                                                                    )
                                                                )
                                                                    val typePart =
                                                                        physDetail.substringBefore(" : ")
                                                                            .trim()
                                                                    val restPart =
                                                                        physDetail.substringAfter(
                                                                            " : ",
                                                                            ""
                                                                        ).trim()
                                                                    val translatedType =
                                                                        AnfrDisplayText.antennaType(
                                                                            typePart
                                                                        )
                                                                    val finalPhysText =
                                                                        if (restPart.isNotEmpty()) "$translatedType : $restPart" else translatedType
                                                                    Text(
                                                                        text = finalPhysText,
                                                                        color = MaterialTheme.colorScheme.onSurface,
                                                                        fontWeight = FontWeight.Medium,
                                                                        fontSize = 12.sp
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    }
                                }

                                if (incQrCode) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        if (qrBitmap != null) {
                                            Image(
                                                bitmap = qrBitmap.asImageBitmap(),
                                                contentDescription = "QR Code",
                                                modifier = Modifier.size(56.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            ); Spacer(modifier = Modifier.width(16.dp))
                                        }
                                        Column(horizontalAlignment = Alignment.Start) {
                                            Text(
                                                text = stringResource(R.string.appstrings_scan_to_open),
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            ); Text(
                                            text = stringResource(R.string.appstrings_geo_tower_app),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        }
                                    }
                                }
                                Text(
                                    text = txtGeneratedBy,
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val rootView = currentView.rootView as ViewGroup
    composeView.translationX = 10000f

    try {
        if (composeView.parent != null) {
            (composeView.parent as ViewGroup).removeView(composeView)
        }

        // ✅ CORRECTION : On s'assure que la vue fait 800dp de large si elle est scindée
        val isSplit = incSplitImage && incFreqs
        val expectedWidthDp = if (isSplit) 800 else 400
        rootView.addView(composeView, ViewGroup.LayoutParams(expectedWidthDp.dpToPx(context), ViewGroup.LayoutParams.WRAP_CONTENT))

    } catch (e: Exception) {
        AppLogger.w(TAG_SHARE_IMAGE, "Site share view setup failed", e)
        Toast.makeText(context, txtInitError, Toast.LENGTH_SHORT).show()
        return
    }

    composeView.postDelayed({
        try {
            val isSplit = incSplitImage && incFreqs
            // Si on split, la largeur demandée est de 800dp (400 x 2)
            val expectedWidthDp = if (isSplit) 800 else 400
            val widthSpec = View.MeasureSpec.makeMeasureSpec(expectedWidthDp.dpToPx(context), View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            composeView.measure(widthSpec, heightSpec)
            composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)

            if (composeView.measuredWidth > 0 && composeView.measuredHeight > 0) {
                val fullBitmap = Bitmap.createBitmap(composeView.measuredWidth, composeView.measuredHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(fullBitmap)
                composeView.draw(canvas)

                rootView.removeView(composeView)
                val imagesDir = File(context.cacheDir, "images")
                imagesDir.mkdirs()

                val urisToShare = java.util.ArrayList<Uri>()

                if (isSplit) {
                    val halfWidth = fullBitmap.width / 2
                    val bmp1 = Bitmap.createBitmap(fullBitmap, 0, 0, halfWidth, fullBitmap.height)
                        .trimTransparentBottom()
                    val bmp2 = Bitmap.createBitmap(fullBitmap, halfWidth, 0, halfWidth, fullBitmap.height)
                        .trimTransparentBottom()

                    val file1 = File(imagesDir, "Geotower_site_${info.idAnfr}_part1.png")
                    val file2 = File(imagesDir, "Geotower_site_${info.idAnfr}_part2.png")

                    FileOutputStream(file1).use { bmp1.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    FileOutputStream(file2).use { bmp2.compress(Bitmap.CompressFormat.PNG, 100, it) }

                    urisToShare.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file1))
                    urisToShare.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file2))
                } else {
                    // Image unique classique
                    val file = File(imagesDir, "Geotower_site_${info.idAnfr}.png")
                    FileOutputStream(file).use { fullBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    urisToShare.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                }

                elevationProfileBitmap?.let { profileBitmap ->
                    val profileFile = File(imagesDir, "Geotower_site_${info.idAnfr}_profil_altimetrique.png")
                    FileOutputStream(profileFile).use { profileBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    urisToShare.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", profileFile))
                }

                // Lancement de l'intention de partage
                val sendMultipleImages = urisToShare.size > 1
                val intent = Intent(if (sendMultipleImages) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
                    type = "image/png"
                    if (sendMultipleImages) {
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, urisToShare)
                        // ✅ CORRECTION : Ajout de toutes les URIs au ClipData pour autoriser la lecture
                        val clipDataMulti = ClipData.newUri(context.contentResolver, "Capture", urisToShare[0])
                        for (i in 1 until urisToShare.size) {
                            clipDataMulti.addItem(ClipData.Item(urisToShare[i]))
                        }
                        clipData = clipDataMulti
                    } else {
                        putExtra(Intent.EXTRA_STREAM, urisToShare.first())
                        clipData = ClipData.newUri(context.contentResolver, "Capture", urisToShare.first())
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(Intent.createChooser(intent, txtShareSiteVia))
                onComplete?.invoke()
            } else {
                rootView.removeView(composeView)
                onComplete?.invoke()
            }
        } catch (e: Exception) {
            AppLogger.w(TAG_SHARE_IMAGE, "Site share capture failed", e)
            if (composeView.parent != null) rootView.removeView(composeView)
            onComplete?.invoke()
        }
    }, 500)
}

fun shareFullSiteCapture(
    context: Context,
    currentView: View,
    siteId: Long,
    mainInfo: LocalisationEntity,
    antennas: List<LocalisationEntity>,
    physique: PhysiqueEntity?,
    techniquesMap: Map<String, TechniqueEntity>,
    hsDataMap: Map<String, fr.geotower.data.models.SiteHsEntity>, // 🚨 AJOUT
    distanceStr: String,
    bearingStr: String,
    forceDarkTheme: Boolean,
    txtTitle: String,
    txtAddressLabel: String,
    txtNotSpecified: String,
    txtGpsLabel: String,
    txtSupportHeight: String,
    txtDistanceLabel: String,
    txtFromMyPosition: String,
    txtBearingLabel: String,
    txtOperatorsTitle: String,
    txtGeneratedBy: String,
    txtShareSiteVia: String,
    txtIdNumber: String,
    txtInitError: String,
    txtSupportNature: String,
    txtOwner: String,
    mapBitmap: Bitmap?,
    incMap: Boolean,
    incSupport: Boolean,
    incOperators: Boolean,
    incConfidential: Boolean,
    incQrCode: Boolean,
    shareOrder: List<String>,
    radioMarkers: List<RadioMapMarker> = emptyList(),
    incRadioEntries: Boolean = radioMarkers.isNotEmpty(),
    onComplete: (() -> Unit)? = null
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
                                .width(400.dp)
                                .wrapContentHeight()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(text = txtTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                            shareOrder.forEach { block ->
                                when (block) {
                                    "map" -> {
                                        if (incMap && mapBitmap != null) {
                                            Image(
                                                bitmap = mapBitmap.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                    "support" -> {
                                        if (incSupport) {
                                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(stringResource(R.string.appstrings_support_details_title), fontWeight = FontWeight.Bold)
                                                    }
                                                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                                    // ✅ ON UTILISE LES VRAIES DONNÉES DU PYLÔNE
                                                    val technique = techniquesMap[mainInfo.idAnfr]
                                                    val fullAddress = technique?.adresse?.takeIf { it.isNotBlank() } ?: stringResource(R.string.appstrings_not_specified)
                                                    val nature = physique?.natureSupport?.takeIf { it.isNotBlank() } ?: txtNotSpecified
                                                    val proprietaire = physique?.proprietaire?.takeIf { it.isNotBlank() } ?: stringResource(R.string.appstrings_unknown)
                                                    val hauteur = formatShareHeightMeters(physique?.hauteur)
                                                    val idSupportValue = physique?.idSupport?.takeIf { it.isNotBlank() } ?: txtNotSpecified

                                                    Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append("$txtIdNumber ") }; withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(idSupportValue) } }, fontSize = 14.sp)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append("$txtAddressLabel ") }; withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(fullAddress) } }, fontSize = 14.sp)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append("$txtGpsLabel ") }; withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(String.format(Locale.US, "%.5f, %.5f", mainInfo.latitude, mainInfo.longitude)) } }, fontSize = 14.sp)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append("$txtSupportNature : ") }; withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(nature) } }, fontSize = 14.sp)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append("$txtOwner : ") }; withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(proprietaire) } }, fontSize = 14.sp)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append("$txtSupportHeight ") }; withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(hauteur) } }, fontSize = 14.sp)

                                                    if (incConfidential) {
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(stringResource(R.string.appstrings_distance_hidden), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                    } else {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append("$txtDistanceLabel ") }; withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("$distanceStr $txtFromMyPosition") } }, fontSize = 14.sp)
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append("$txtBearingLabel ") }; withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(bearingStr) } }, fontSize = 14.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "operators" -> {
                                        if (incOperators) {
                                            Column {
                                                Text(text = "$txtOperatorsTitle (${antennas.size})", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                antennas.forEach { antenna ->
                                                    OperatorDetailItem(
                                                        antenna = antenna,
                                                        technique = techniquesMap[antenna.idAnfr],
                                                        hsEntity = hsDataMap[antenna.idAnfr], // 🚨 PASSAGE DE LA PANNE ICI
                                                        cardBgColor = Color.Transparent,
                                                        blockShape = RoundedCornerShape(0.dp),
                                                        useOneUi = false,
                                                        onClick = {}
                                                    )
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                                                }
                                            }
                                        }
                                    }
                                    SUPPORT_SHARE_RADIO_ENTRIES -> {
                                        if (incRadioEntries && radioMarkers.isNotEmpty()) {
                                            RadioShareSupportEntriesBlock(
                                                markers = radioMarkers,
                                                txtSupportEntriesTitle = stringResource(R.string.appstrings_radio_share_block_support_entries),
                                                txtOther = stringResource(R.string.appstrings_radio_share_other),
                                                txtElements = stringResource(R.string.appstrings_radio_share_elements),
                                                cardBgColor = MaterialTheme.colorScheme.surfaceVariant,
                                                blockShape = RoundedCornerShape(12.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // ✅ CORRECTION : On entoure avec if (incQrCode)
                            if (incQrCode) {
                                val qrUri = "geotower://support/$siteId"
                                val qrBitmap = remember(qrUri) { generateQrCodeBitmap(qrUri, 200) }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (qrBitmap != null) {
                                        Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
                                        Spacer(modifier = Modifier.width(16.dp))
                                    }
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text(text = stringResource(R.string.appstrings_scan_to_open), fontSize = 11.sp, color = Color.Gray)
                                        Text(text = stringResource(R.string.appstrings_geo_tower_app), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }

                            Text(text = txtGeneratedBy, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                    }
                }
            }
        }

        val rootView = currentView.rootView as? ViewGroup ?: run {
            onComplete?.invoke()
            return
        }
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
                val safeId = physique?.idSupport?.takeIf { it.isNotBlank() } ?: siteId.toString()
                val file = File(cachePath, "Geotower_support_$safeId.png")
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, uri)
                    type = "image/png"
                    clipData = ClipData.newUri(context.contentResolver, "Capture", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, txtShareSiteVia))
                onComplete?.invoke()
            } catch (e: Exception) {
                AppLogger.w(TAG_SHARE_IMAGE, "Support share capture failed", e)
                Toast.makeText(context, txtInitError, Toast.LENGTH_SHORT).show()
                if (composeView.parent != null) rootView.removeView(composeView)
                onComplete?.invoke()
            }
        }
    } catch (e: Exception) {
        AppLogger.w(TAG_SHARE_IMAGE, "Support share initialization failed", e)
        onComplete?.invoke()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShareGenerationDialog(message: String) {
    Dialog(onDismissRequest = {}) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularWavyProgressIndicator(modifier = Modifier.size(58.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ThemeOptionItem(
    iconVector: ImageVector? = null,
    iconRes: Int? = null,
    label: String,
    subLabel: String? = null,
    trailingIcon: ImageVector? = null,
    isSubItem: Boolean = false,
    useOneUi: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(if (useOneUi) RoundedCornerShape(16.dp) else RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (isSubItem) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconSize = 24.dp
        if (iconRes != null) {
            Image(painter = painterResource(id = iconRes), contentDescription = null, modifier = Modifier.size(iconSize).clip(RoundedCornerShape(4.dp)))
        } else if (iconVector != null) {
            Icon(imageVector = iconVector, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(iconSize))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontWeight = if (isSubItem) FontWeight.Medium else FontWeight.SemiBold, fontSize = if (isSubItem) 15.sp else 16.sp, color = MaterialTheme.colorScheme.onSurface)
            if (subLabel != null) { Text(text = subLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (trailingIcon != null) { Icon(trailingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntennaShareMenu(
    info: LocalisationEntity,
    physique: PhysiqueEntity?,
    technique: TechniqueEntity?,
    hsDataMap: Map<String, fr.geotower.data.models.SiteHsEntity>, // 🚨 AJOUT
    distanceStr: String,
    bearingStr: String,
    useOneUi: Boolean,
    buttonShape: Shape,
    globalMapRef: org.osmdroid.views.MapView?,
    communityPhotosSize: Int,
    speedtestData: fr.geotower.data.api.SqSpeedtestData? // 🚨 NEW
) {
    val context = LocalContext.current
    val currentView = LocalView.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    val safeClick = rememberSafeClick()

    var showShareSheet by remember { mutableStateOf(false) }
    var showSelectionSheet by remember { mutableStateOf(false) }
    var selectedShareTheme by remember { mutableStateOf(false) }
    var isGeneratingShare by remember { mutableStateOf(false) }
    var generationMessage by remember { mutableStateOf("") }

    var incMap by remember { mutableStateOf(SharePrefs.siteMapEnabled.read(prefs)) }
    var incElevationProfile by remember { mutableStateOf(SharePrefs.siteElevationProfileEnabled.read(prefs)) }
    var incSupport by remember { mutableStateOf(SharePrefs.siteSupportEnabled.read(prefs)) }
    var incHeights by remember { mutableStateOf(prefs.getBoolean("share_heights_enabled", true)) }
    var incIds by remember { mutableStateOf(SharePrefs.siteIdsEnabled.read(prefs)) }
    var incDates by remember { mutableStateOf(SharePrefs.siteDatesEnabled.read(prefs)) }
    var incAddress by remember { mutableStateOf(SharePrefs.siteAddressEnabled.read(prefs)) }
    var incSpeedtest by remember { mutableStateOf(SharePrefs.siteSpeedtestEnabled.read(prefs)) } // 🚨 NEW
    var incThroughput by remember { mutableStateOf(SharePrefs.siteThroughputEnabled.read(prefs)) }
    var incFreqs by remember { mutableStateOf(SharePrefs.siteFrequencyEnabled.read(prefs)) }
    var incConfidential by remember { mutableStateOf(SharePrefs.siteConfidentialEnabled.read(prefs)) }
    var incQrCode by remember { mutableStateOf(SharePrefs.siteQrEnabled.read(prefs)) }
    var incSplitImage by remember { mutableStateOf(SharePrefs.siteSplitImageEnabled.read(prefs)) } // ✅ NOUVELLE VARIABLE SCINDER
    var shareOrder by remember {
        mutableStateOf(
            SharePrefs.siteOrder(prefs)
        )
    }

    // ✅ FORCE LE RECHARGEMENT DES PARAMÈTRES PAR DÉFAUT À CHAQUE OUVERTURE
    LaunchedEffect(showShareSheet) {
        if (showShareSheet) {
            incMap = SharePrefs.siteMapEnabled.read(prefs)
            incElevationProfile = SharePrefs.siteElevationProfileEnabled.read(prefs)
            incSupport = SharePrefs.siteSupportEnabled.read(prefs)
            incIds = SharePrefs.siteIdsEnabled.read(prefs)
            incDates = SharePrefs.siteDatesEnabled.read(prefs)
            incAddress = SharePrefs.siteAddressEnabled.read(prefs)
            incSpeedtest = SharePrefs.siteSpeedtestEnabled.read(prefs) // 🚨 NEW
            incThroughput = SharePrefs.siteThroughputEnabled.read(prefs)
            incFreqs = SharePrefs.siteFrequencyEnabled.read(prefs)
            incConfidential = SharePrefs.siteConfidentialEnabled.read(prefs)
            incQrCode = SharePrefs.siteQrEnabled.read(prefs)
            incSplitImage = SharePrefs.siteSplitImageEnabled.read(prefs) // ✅ CHARGEMENT DE L'ÉTAT
            shareOrder = SharePrefs.siteOrder(prefs)
        }
    }

    val txtSiteDetailsTitle = stringResource(R.string.appstrings_site_detail_title)
    val txtAddressLabel = stringResource(R.string.appstrings_address_label)
    val txtNotSpecified = stringResource(R.string.appstrings_not_specified)
    val txtGpsLabel = stringResource(R.string.appstrings_gps_label)
    val txtSupportHeight = stringResource(R.string.appstrings_support_height)
    val txtDistanceLabel = stringResource(R.string.appstrings_distance_label)
    val txtFromMyPosition = stringResource(R.string.appstrings_from_my_position)
    val txtBearingLabel = stringResource(R.string.appstrings_bearing_label)
    val txtGeneratedBy = stringResource(R.string.appstrings_generated_by)
    val txtShareSiteVia = stringResource(R.string.appstrings_share_site_via)
    val txtimplementation = stringResource(R.string.appstrings_implementation)
    val txtLastModification = stringResource(R.string.appstrings_last_modification)
    val txtIdentifiers = stringResource(R.string.appstrings_identifiers)
    val txtIdNumber = stringResource(R.string.appstrings_id_number)
    val txtFrequenciesTitle = stringResource(R.string.appstrings_frequencies_title)
    val txtBandsNotSpecified = stringResource(R.string.appstrings_bands_not_specified)
    val txtInService = stringResource(R.string.appstrings_in_service)
    val txtTechnically = stringResource(R.string.appstrings_technically)
    val txtUnknownStatus = stringResource(R.string.appstrings_unknown_status)
    val txtAnfrStationNumber = stringResource(R.string.appstrings_anfr_station_number)
    val txtDates = stringResource(R.string.appstrings_dates)
    val txtError = stringResource(R.string.appstrings_error)
    val txtProjectApproved = stringResource(R.string.appstrings_project_approved)
    val txtActivatedOn = stringResource(R.string.appstrings_activated_on)
    val txtDateNotSpecifiedAnfr = stringResource(R.string.appstrings_date_not_specified_anfr)
    val txtPanelHeightsTitle = stringResource(R.string.appstrings_panel_heights_title)
    val txtAzimuths = stringResource(R.string.appstrings_azimuths_label)
    val txtIdSupportLabel = stringResource(R.string.appstrings_id_support_label)
    val txtSupportDetailsTitle = stringResource(R.string.appstrings_support_details_title)
    val txtSupportNature = stringResource(R.string.appstrings_support_nature)
    val txtOwner = stringResource(R.string.appstrings_owner)
    val txtExploitant = stringResource(R.string.appstrings_exploitant)
    val txtAntennaType = stringResource(R.string.appstrings_antenna_type)
    val txtCommunityPhotosTitle = pluralStringResource(R.plurals.community_photos_title_short, communityPhotosSize, communityPhotosSize)
    val txtThemeLight = stringResource(R.string.appstrings_theme_light)
    val txtLightModeDesc = stringResource(R.string.appstrings_light_mode_desc)
    val txtThemeDark = stringResource(R.string.appstrings_theme_dark)
    val txtDarkModeDesc = stringResource(R.string.appstrings_dark_mode_desc)
    val txtShareSite = stringResource(R.string.appstrings_share_site)
    val txtShareAs = stringResource(R.string.appstrings_share_as)
    val txtImageContent = stringResource(R.string.appstrings_image_content)
    val txtShareConfidentialOption = stringResource(R.string.appstrings_share_confidential_option)
    val txtShareConfidentialDesc = stringResource(R.string.appstrings_share_confidential_desc)
    val txtGenerateImage = stringResource(R.string.appstrings_generate_image)
    val txtMove = stringResource(R.string.appstrings_move)
    val txtInitError = stringResource(R.string.appstrings_init_error)
    val txtUnknown = stringResource(R.string.appstrings_unknown)
    val txtShareImageGenerationInProgress = stringResource(R.string.appstrings_share_image_generation_in_progress)
    val txtShareImagePreparingInProgress = stringResource(R.string.appstrings_share_image_preparing_in_progress)
    val txtShareElevationProfileOnlyUnavailable = stringResource(R.string.appstrings_share_elevation_profile_only_unavailable)
    val txtElevationProfileTitle = stringResource(R.string.appstrings_elevation_profile_title)
    val txtElevationProfileLoading = stringResource(R.string.appstrings_elevation_profile_loading)
    val txtElevationProfileDistance = stringResource(R.string.appstrings_elevation_profile_distance)
    val txtElevationProfileSupportHeight = stringResource(R.string.appstrings_elevation_profile_support_height)
    val txtElevationProfileSupportHeightDetail = stringResource(R.string.appstrings_elevation_profile_support_height_detail)
    val txtElevationProfileStartAltitude = stringResource(R.string.appstrings_elevation_profile_start_altitude)
    val txtElevationProfileStartAltitudeDetail = stringResource(R.string.appstrings_elevation_profile_start_altitude_detail)
    val txtElevationProfileSiteAltitude = stringResource(R.string.appstrings_elevation_profile_site_altitude)
    val txtElevationProfileSiteAltitudeDetail = stringResource(R.string.appstrings_elevation_profile_site_altitude_detail)
    val txtElevationProfileFrequency = stringResource(R.string.appstrings_elevation_profile_frequency)
    val txtElevationProfileDirectLineLabel = stringResource(R.string.appstrings_elevation_profile_direct_line_label)
    val txtElevationProfileFresnelLabel = stringResource(R.string.appstrings_elevation_profile_fresnel_label)
    val txtElevationProfileLineClear = stringResource(R.string.appstrings_elevation_profile_line_clear)
    val txtElevationProfileLineBlocked = stringResource(R.string.appstrings_elevation_profile_line_blocked)
    val txtElevationProfileFresnelClear = stringResource(R.string.appstrings_elevation_profile_fresnel_clear)
    val txtElevationProfileFresnelBlocked = stringResource(R.string.appstrings_elevation_profile_fresnel_blocked)
    val txtElevationProfileFresnelExplanation = stringResource(R.string.appstrings_elevation_profile_fresnel_explanation)
    val txtElevationProfileIgnSource = stringResource(R.string.appstrings_elevation_profile_ign_source)

    Button(
        onClick = { safeClick { showShareSheet = true } },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = buttonShape,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
    ) {
        Icon(Icons.Default.Share, null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(txtShareSite, fontWeight = FontWeight.Bold)
    }

    if (isGeneratingShare) {
        ShareGenerationDialog(generationMessage.ifBlank { txtShareImageGenerationInProgress })
    }

    if (showShareSheet) {
        ModalBottomSheet(onDismissRequest = { showShareSheet = false }, sheetState = sheetState, containerColor = sheetBgColor) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp, start = 16.dp, end = 16.dp)) {
                Text(txtShareAs, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp))
                ThemeOptionItem(iconVector = Icons.Outlined.LightMode, label = txtThemeLight, subLabel = txtLightModeDesc, useOneUi = useOneUi) { safeClick { selectedShareTheme = false; showShareSheet = false; showSelectionSheet = true } }
                Spacer(modifier = Modifier.height(12.dp))
                ThemeOptionItem(iconVector = Icons.Outlined.DarkMode, label = txtThemeDark, subLabel = txtDarkModeDesc, useOneUi = useOneUi) { safeClick { selectedShareTheme = true; showShareSheet = false; showSelectionSheet = true } }
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

                val itemHeight = 48.dp
                val reorderState = rememberReorderableDragState(
                    items = shareOrder,
                    itemHeight = itemHeight,
                    onOrderChange = { newOrder ->
                        shareOrder = newOrder
                        prefs.edit().putString(SharePrefs.SITE_ORDER, newOrder.joinToString(",")).apply()
                    }
                )

                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    shareOrder.forEach { blockId ->
                        key(blockId) {
                            val isDragged = reorderState.isDragged(blockId)
                            val dragModifier = reorderState.dragModifier(blockId)
                            val dragOffset = reorderState.offsetFor(blockId)

                            // Dans AntennaShareMenu -> showSelectionSheet -> shareOrder.forEach
                            val (label, checked, onChecked) = when (blockId) {
                                // ✅ ON SUPPRIME prefs.edit() : les changements restent locaux au menu
                                "map" -> Triple(stringResource(R.string.appstrings_share_map_option), incMap, { it: Boolean -> incMap = it })
                                "elevation_profile" -> Triple(stringResource(R.string.appstrings_share_elevation_profile_option), incElevationProfile, { it: Boolean -> incElevationProfile = it })
                                "support" -> Triple(stringResource(R.string.appstrings_share_support_option), incSupport, { it: Boolean -> incSupport = it })
                                "ids" -> Triple(stringResource(R.string.appstrings_share_ids_option), incIds, { it: Boolean -> incIds = it })
                                "dates" -> Triple(stringResource(R.string.appstrings_share_dates_option), incDates, { it: Boolean -> incDates = it })
                                "address" -> Triple(stringResource(R.string.appstrings_share_address_option), incAddress, { it: Boolean -> incAddress = it })
                                "speedtest" -> Triple(stringResource(R.string.appstrings_share_speedtest_option), incSpeedtest, { it: Boolean -> incSpeedtest = it })
                                "throughput" -> Triple(stringResource(R.string.appstrings_share_throughput_option), incThroughput, { it: Boolean -> incThroughput = it })
                                "status" -> Triple(stringResource(R.string.appstrings_share_status_option), AppConfig.shareSiteStatus.value, { it: Boolean -> AppConfig.shareSiteStatus.value = it })
                                "freq" -> Triple(stringResource(R.string.appstrings_share_freq_option), incFreqs, { it: Boolean -> incFreqs = it })
                                else -> Triple("", false, { _: Boolean -> })
                            }

                            if (label.toString().isNotEmpty()) {
                                @Suppress("UNCHECKED_CAST") val safeOnChecked = onChecked as (Boolean) -> Unit
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(itemHeight)
                                        .zIndex(if (isDragged) 10f else 0f)
                                        .graphicsLayer { translationY = if (isDragged) dragOffset else 0f; scaleX = if (isDragged) 1.02f else 1f; scaleY = if (isDragged) 1.02f else 1f; shadowElevation = if (isDragged) 8.dp.toPx() else 0f }
                                        .background(if (isDragged) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(8.dp))
                                        .then(dragModifier)
                                        .padding(start = 8.dp)
                                ) {
                                    Icon(Icons.Default.DragHandle, contentDescription = txtMove, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(label.toString(), modifier = Modifier.weight(1f))
                                    GeoTowerSwitch(
                                        checked = checked as Boolean,
                                        onCheckedChange = safeOnChecked,
                                        modifier = Modifier.scale(if (useOneUi) 0.85f else 0.8f),
                                        useOneUi = useOneUi
                                    )
                                }
                            }
                        }
                    }

                    // ✅ AJOUT DU BOUTON RÉINITIALISER
                    TextButton(
                        onClick = {
                            shareOrder = SharePrefs.DEFAULT_SITE_ORDER.split(",")
                            prefs.edit().putString(SharePrefs.SITE_ORDER, shareOrder.joinToString(",")).apply()
                            incMap = true; incElevationProfile = true; incSupport = true; incIds = true; incDates = true; incAddress = true; incSpeedtest = true; incThroughput = true; incFreqs = true; incQrCode = true; incSplitImage = true
                            AppConfig.shareSiteStatus.value = true
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.appstrings_reset_to_default), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // ✅ INTERRUPTEUR : SCINDER L'IMAGE
                    if (incFreqs) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.appstrings_split_share_image), fontWeight = FontWeight.Bold, color = if(incSplitImage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                Text(stringResource(R.string.appstrings_split_share_image_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            GeoTowerSwitch(
                                checked = incSplitImage,
                                onCheckedChange = { incSplitImage = it; prefs.edit().putBoolean(SharePrefs.siteSplitImageEnabled.key, it).apply() },
                                modifier = Modifier.scale(if (useOneUi) 0.85f else 0.8f),
                                useOneUi = useOneUi
                            )
                        }
                    }

                    // ✅ INTERRUPTEUR : QR CODE
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("QR Code", modifier = Modifier.weight(1f))
                        GeoTowerSwitch(
                            checked = incQrCode,
                            onCheckedChange = { incQrCode = it },
                            modifier = Modifier.scale(if (useOneUi) 0.85f else 0.8f),
                            useOneUi = useOneUi
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(txtShareConfidentialOption, fontWeight = FontWeight.Bold, color = if(incConfidential) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text(txtShareConfidentialDesc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        GeoTowerSwitch(
                            checked = incConfidential,
                            onCheckedChange = { incConfidential = it },
                            modifier = Modifier.scale(if (useOneUi) 0.85f else 0.8f),
                            useOneUi = useOneUi
                        )
                    }
                }
            }

            Button(
                        onClick = {
                            if (isGeneratingShare) return@Button
                            isGeneratingShare = true
                            generationMessage = txtShareImageGenerationInProgress
                            showSelectionSheet = false
                            val shareOnlyElevationProfile = isOnlyElevationProfileSelected(
                                shareOrder = shareOrder,
                                incElevationProfile = incElevationProfile,
                                incMap = incMap,
                                incSupport = incSupport,
                                incIds = incIds,
                                incDates = incDates,
                                incAddress = incAddress,
                                incFreqs = incFreqs,
                                incSpeedtest = incSpeedtest,
                                incThroughput = incThroughput
                            )
                            val elevationProfileTexts = ElevationProfileShareTexts(
                                title = txtElevationProfileTitle,
                                distance = txtElevationProfileDistance,
                                supportHeight = txtElevationProfileSupportHeight,
                                supportHeightDetail = txtElevationProfileSupportHeightDetail,
                                startAltitude = txtElevationProfileStartAltitude,
                                startAltitudeDetail = txtElevationProfileStartAltitudeDetail,
                                siteAltitude = txtElevationProfileSiteAltitude,
                                siteAltitudeDetail = txtElevationProfileSiteAltitudeDetail,
                                frequency = txtElevationProfileFrequency,
                                directLine = txtElevationProfileDirectLineLabel,
                                fresnelZone = txtElevationProfileFresnelLabel,
                                lineClear = txtElevationProfileLineClear,
                                lineBlocked = txtElevationProfileLineBlocked,
                                fresnelClear = txtElevationProfileFresnelClear,
                                fresnelBlocked = txtElevationProfileFresnelBlocked,
                                fresnelExplanation = txtElevationProfileFresnelExplanation,
                                ignSource = txtElevationProfileIgnSource,
                                generatedBy = txtGeneratedBy,
                                unknown = txtUnknown
                            )

                            scope.launch {
                                val elevationProfileBitmap = if (
                                    incElevationProfile &&
                                    shareOrder.contains("elevation_profile") &&
                                    !incConfidential
                                ) {
                                    generationMessage = txtElevationProfileLoading
                                    runCatching {
                                        val userLocation = withContext(Dispatchers.IO) {
                                            getElevationProfileLastKnownLocation(context)
                                        } ?: return@runCatching null
                                        val rawElevationProfileFrequencies = technique?.detailsFrequences?.takeIf { it.isNotBlank() } ?: info.frequences
                                        val frequency = extractElevationProfileFrequencies(rawElevationProfileFrequencies).firstOrNull() ?: DEFAULT_ELEVATION_PROFILE_FREQUENCY_MHZ
                                        val antennaHeight = extractElevationProfileAntennaHeightsByFrequency(rawElevationProfileFrequencies)[frequency]
                                        val profile = withContext(Dispatchers.IO) {
                                            fetchIgnElevationProfileData(
                                                fromLatitude = userLocation.latitude,
                                                fromLongitude = userLocation.longitude,
                                                toLatitude = info.latitude,
                                                toLongitude = info.longitude
                                            )
                                        }
                                        createElevationProfileShareBitmap(
                                            info = info,
                                            profile = profile,
                                            supportHeightMeters = antennaHeight ?: physique?.hauteur,
                                            frequencyMHz = frequency,
                                            forceDarkTheme = selectedShareTheme,
                                            texts = elevationProfileTexts
                                        )
                                    }.getOrNull()
                                } else {
                                    null
                                }

                                if (shareOnlyElevationProfile && elevationProfileBitmap == null) {
                                    Toast.makeText(context, txtShareElevationProfileOnlyUnavailable, Toast.LENGTH_SHORT).show()
                                    isGeneratingShare = false
                                    return@launch
                                }

                                generationMessage = txtShareImagePreparingInProgress
                                currentView.postDelayed({
                                    try {
                                        if (shareOnlyElevationProfile && elevationProfileBitmap != null) {
                                            shareElevationProfileBitmapOnly(context, info, elevationProfileBitmap, txtShareSiteVia)
                                            isGeneratingShare = false
                                        } else {
                                            val mapBmp = if (incMap) { globalMapRef?.let { map -> try { val bmp = Bitmap.createBitmap(map.width, map.height, Bitmap.Config.ARGB_8888); map.draw(Canvas(bmp)); bmp } catch (e: Exception) { null } } } else null
                                            shareFullAntennaCapture(
                                                context, currentView, info,
                                                physique, technique,
                                                hsDataMap,
                                                speedtestData,
                                                distanceStr, bearingStr, selectedShareTheme,
                                                txtSiteDetailsTitle, txtAddressLabel, txtNotSpecified, txtGpsLabel, txtSupportHeight, txtDistanceLabel, txtFromMyPosition, txtBearingLabel, txtGeneratedBy, txtShareSiteVia, txtimplementation, txtLastModification, txtIdentifiers, txtIdNumber, txtFrequenciesTitle, txtBandsNotSpecified, txtInService, txtTechnically, txtUnknownStatus, txtAnfrStationNumber, txtDates, txtError, txtProjectApproved, txtActivatedOn, txtDateNotSpecifiedAnfr, txtPanelHeightsTitle, txtAzimuths, txtIdSupportLabel, txtSupportDetailsTitle, txtSupportNature, txtOwner, txtExploitant, txtAntennaType,
                                                mapBmp, txtInitError, emptyList(), txtCommunityPhotosTitle,
                                                incMap, incSupport, incHeights, incIds, incDates, incAddress, incFreqs, incSpeedtest, incThroughput, incConfidential, incQrCode,
                                                incSplitImage,
                                                shareOrder,
                                                elevationProfileBitmap = elevationProfileBitmap,
                                                onComplete = { isGeneratingShare = false }
                                            )
                                        }
                                    } catch (e: Exception) {
                                        AppLogger.w(TAG_SHARE_IMAGE, "Site share generation failed", e)
                                        isGeneratingShare = false
                                    }
                                }, 300)
                            }
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
                        shape = CircleShape,
                        enabled = !isGeneratingShare
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(txtGenerateImage, fontWeight = FontWeight.Bold)
                    }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportShareMenu(
    siteId: Long,
    antennas: List<LocalisationEntity>,
    physique: PhysiqueEntity?,
    techniquesMap: Map<String, TechniqueEntity>,
    hsDataMap: Map<String, fr.geotower.data.models.SiteHsEntity>, // 🚨 AJOUT
    distanceStr: String,
    bearingStr: String,
    useOneUi: Boolean,
    buttonShape: Shape,
    globalMapRef: org.osmdroid.views.MapView?,
    radioMarkers: List<RadioMapMarker> = emptyList()
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
    var isGeneratingShare by remember { mutableStateOf(false) }
    var generationMessage by remember { mutableStateOf("") }

    var incMap by remember { mutableStateOf(SharePrefs.supportMapEnabled.read(prefs)) }
    var incSupport by remember { mutableStateOf(SharePrefs.supportDetailsEnabled.read(prefs)) }
    var incOperators by remember { mutableStateOf(SharePrefs.supportOperatorsEnabled.read(prefs)) }
    var incConfidential by remember { mutableStateOf(SharePrefs.supportConfidentialEnabled.read(prefs)) }
    val hasRadioEntries = radioMarkers.isNotEmpty()
    var incQrCode by remember { mutableStateOf(SharePrefs.supportQrEnabled.read(prefs)) } // ✅ NOUVELLE VARIABLE
    var incRadioEntries by remember(hasRadioEntries) { mutableStateOf(hasRadioEntries) }
    var shareOrder by remember(hasRadioEntries) {
        mutableStateOf(normalizeSupportShareOrder(SharePrefs.supportOrder(prefs), hasRadioEntries))
    }

    // ✅ FORCE LE RECHARGEMENT À CHAQUE OUVERTURE
    LaunchedEffect(showShareSheet) {
        if (showShareSheet) {
            incMap = SharePrefs.supportMapEnabled.read(prefs)
            incSupport = SharePrefs.supportDetailsEnabled.read(prefs)
            incOperators = SharePrefs.supportOperatorsEnabled.read(prefs)
            incConfidential = SharePrefs.supportConfidentialEnabled.read(prefs)
            incQrCode = SharePrefs.supportQrEnabled.read(prefs) // ✅ RECHARGEMENT
            incRadioEntries = hasRadioEntries
            shareOrder = normalizeSupportShareOrder(SharePrefs.supportOrder(prefs), hasRadioEntries)
        }
    }

    val txtSupportDetailTitle = stringResource(R.string.appstrings_support_detail_title)
    val txtAddressLabel = stringResource(R.string.appstrings_address_label)
    val txtNotSpecified = stringResource(R.string.appstrings_not_specified)
    val txtGpsLabel = stringResource(R.string.appstrings_gps_label)
    val txtSupportHeight = stringResource(R.string.appstrings_support_height)
    val txtDistanceLabel = stringResource(R.string.appstrings_distance_label)
    val txtFromMyPosition = stringResource(R.string.appstrings_from_my_position)
    val txtBearingLabel = stringResource(R.string.appstrings_bearing_label)
    val txtOperatorsTitle = stringResource(R.string.appstrings_operators_title)
    val txtGeneratedBy = stringResource(R.string.appstrings_generated_by)
    val txtShareSiteVia = stringResource(R.string.appstrings_share_site_via)
    val txtThemeLight = stringResource(R.string.appstrings_theme_light)
    val txtLightModeDesc = stringResource(R.string.appstrings_light_mode_desc)
    val txtThemeDark = stringResource(R.string.appstrings_theme_dark)
    val txtDarkModeDesc = stringResource(R.string.appstrings_dark_mode_desc)
    val txtIdNumber = stringResource(R.string.appstrings_id_number)
    val txtSupportNature = stringResource(R.string.appstrings_support_nature)
    val txtOwner = stringResource(R.string.appstrings_owner) // ✅ AJOUT DE LA TRADUCTION
    val txtShareSite = stringResource(R.string.appstrings_share_site)
    val txtShareAs = stringResource(R.string.appstrings_share_as)
    val txtImageContent = stringResource(R.string.appstrings_image_content)
    val txtShareConfidentialOption = stringResource(R.string.appstrings_share_confidential_option)
    val txtShareConfidentialDesc = stringResource(R.string.appstrings_share_confidential_desc)
    val txtGenerateImage = stringResource(R.string.appstrings_generate_image)
    val txtShareImagePreparingInProgress = stringResource(R.string.appstrings_share_image_preparing_in_progress)
    val txtMove = stringResource(R.string.appstrings_move)
    val txtInitError = stringResource(R.string.appstrings_init_error)
    val txtRadioEntriesTitle = stringResource(R.string.appstrings_radio_share_block_support_entries)

    Button(
        onClick = { safeClick { showShareSheet = true } },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = buttonShape,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(txtShareSite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }

    if (isGeneratingShare) {
        ShareGenerationDialog(generationMessage.ifBlank { txtShareImagePreparingInProgress })
    }

    if (showShareSheet && antennas.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { showShareSheet = false }, sheetState = sheetState, containerColor = sheetBgColor) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp, start = 16.dp, end = 16.dp)) {
                Text(txtShareAs, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp))
                ThemeOptionItem(iconVector = Icons.Outlined.LightMode, label = txtThemeLight, subLabel = txtLightModeDesc, useOneUi = useOneUi) { safeClick { selectedShareTheme = false; showShareSheet = false; showSelectionSheet = true } }
                Spacer(modifier = Modifier.height(12.dp))
                ThemeOptionItem(iconVector = Icons.Outlined.DarkMode, label = txtThemeDark, subLabel = txtDarkModeDesc, useOneUi = useOneUi) { safeClick { selectedShareTheme = true; showShareSheet = false; showSelectionSheet = true } }
            }
        }
    }

    if (showSelectionSheet && antennas.isNotEmpty()) {
        val mainInfo = antennas.first()
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

                val itemHeight = 48.dp
                val reorderState = rememberReorderableDragState(
                    items = shareOrder,
                    itemHeight = itemHeight,
                    onOrderChange = { newOrder ->
                        val normalizedOrder = normalizeSupportShareOrder(newOrder, hasRadioEntries)
                        shareOrder = normalizedOrder
                        persistSupportShareOrder(prefs, normalizedOrder)
                    }
                )

                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    shareOrder.forEach { blockId ->
                        key(blockId) {
                            val isDragged = reorderState.isDragged(blockId)
                            val dragModifier = reorderState.dragModifier(blockId)
                            val dragOffset = reorderState.offsetFor(blockId)

                            // Dans SupportShareMenu -> showSelectionSheet -> shareOrder.forEach
                            val (label, checked, onChecked) = when (blockId) {
                                "map" -> Triple(stringResource(R.string.appstrings_share_map_option), incMap, { it: Boolean -> incMap = it })
                                "support" -> Triple(stringResource(R.string.appstrings_share_support_option), incSupport, { it: Boolean -> incSupport = it })
                                "operators" -> Triple(stringResource(R.string.appstrings_operators_title), incOperators, { it: Boolean -> incOperators = it })
                                SUPPORT_SHARE_RADIO_ENTRIES -> Triple(txtRadioEntriesTitle, incRadioEntries, { it: Boolean -> incRadioEntries = it })
                                else -> Triple("", false, { _: Boolean -> })
                            }

                            if (label.toString().isNotEmpty()) {
                                @Suppress("UNCHECKED_CAST") val safeOnChecked = onChecked as (Boolean) -> Unit
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(itemHeight)
                                        .zIndex(if (isDragged) 10f else 0f)
                                        .graphicsLayer { translationY = if (isDragged) dragOffset else 0f; scaleX = if (isDragged) 1.02f else 1f; scaleY = if (isDragged) 1.02f else 1f; shadowElevation = if (isDragged) 8.dp.toPx() else 0f }
                                        .background(if (isDragged) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(8.dp))
                                        .then(dragModifier)
                                        .padding(start = 8.dp)
                                ) {
                                    Icon(Icons.Default.DragHandle, contentDescription = txtMove, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(label.toString(), modifier = Modifier.weight(1f))
                                    GeoTowerSwitch(
                                        checked = checked as Boolean,
                                        onCheckedChange = safeOnChecked,
                                        modifier = Modifier.scale(if (useOneUi) 0.85f else 0.8f),
                                        useOneUi = useOneUi
                                    )
                                }
                            }
                        }
                    }

                    // ✅ AJOUT DU BOUTON RÉINITIALISER
                    TextButton(
                        onClick = {
                            val normalizedOrder = normalizeSupportShareOrder(listOf("map", "support", "operators"), hasRadioEntries)
                            shareOrder = normalizedOrder
                            persistSupportShareOrder(prefs, normalizedOrder)
                            incMap = true; incSupport = true; incOperators = true; incRadioEntries = hasRadioEntries; incQrCode = true
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.appstrings_reset_to_default), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // ✅ NOUVEAU BOUTON : INTERRUPTEUR QR CODE
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("QR Code", modifier = Modifier.weight(1f))
                        GeoTowerSwitch(
                            checked = incQrCode,
                            onCheckedChange = { incQrCode = it },
                            modifier = Modifier.scale(if (useOneUi) 0.85f else 0.8f),
                            useOneUi = useOneUi
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(txtShareConfidentialOption, fontWeight = FontWeight.Bold, color = if(incConfidential) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text(txtShareConfidentialDesc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        GeoTowerSwitch(
                            checked = incConfidential,
                            onCheckedChange = { incConfidential = it },
                            modifier = Modifier.scale(if (useOneUi) 0.85f else 0.8f),
                            useOneUi = useOneUi
                        )
                    }
                }
            }

            Button(
                        onClick = {
                            if (isGeneratingShare) return@Button
                            isGeneratingShare = true
                            generationMessage = txtShareImagePreparingInProgress
                            showSelectionSheet = false
                            globalMapRef?.let { map -> map.controller.setZoom(17.5); map.controller.setCenter(org.osmdroid.util.GeoPoint(mainInfo.latitude, mainInfo.longitude)) }
                            currentView.postDelayed({
                                try {
                                    val mapBmp = if (incMap) { try { globalMapRef?.let { map -> val bmp = Bitmap.createBitmap(map.width, map.height, Bitmap.Config.ARGB_8888); map.draw(Canvas(bmp)); bmp } } catch (e: Exception) { null } } else null
                                    shareFullSiteCapture(
                                        context, currentView, siteId, antennas.first(), antennas,
                                        physique, techniquesMap,
                                        hsDataMap,
                                        distanceStr, bearingStr, selectedShareTheme,
                                        txtSupportDetailTitle, txtAddressLabel, txtNotSpecified, txtGpsLabel, txtSupportHeight, txtDistanceLabel, txtFromMyPosition, txtBearingLabel, txtOperatorsTitle, txtGeneratedBy, txtShareSiteVia, txtIdNumber,
                                        txtInitError, txtSupportNature, txtOwner, mapBmp,
                                        incMap, incSupport, incOperators, incConfidential, incQrCode, shareOrder,
                                        radioMarkers = radioMarkers,
                                        incRadioEntries = incRadioEntries,
                                        onComplete = { isGeneratingShare = false }
                                    )
                                } catch (e: Exception) {
                                    AppLogger.w(TAG_SHARE_IMAGE, "Support share generation failed", e)
                                    isGeneratingShare = false
                                }
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
                        shape = CircleShape,
                        enabled = !isGeneratingShare
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(txtGenerateImage, fontWeight = FontWeight.Bold)
                    }
            }
        }
    }

}

// ✅ NOUVEAU : Fonction pour générer le QR Code
fun generateQrCodeBitmap(text: String, size: Int): Bitmap? {
    return try {
        val hints = java.util.EnumMap<com.google.zxing.EncodeHintType, Any>(com.google.zxing.EncodeHintType::class.java)
        hints[com.google.zxing.EncodeHintType.MARGIN] = 1
        val bitMatrix = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        bitmap
    } catch (e: Exception) {
        AppLogger.w(TAG_SHARE_IMAGE, "QR code generation failed", e)
        null
    }
}
