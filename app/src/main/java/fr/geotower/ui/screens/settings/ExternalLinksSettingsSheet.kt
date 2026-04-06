package fr.geotower.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fr.geotower.R
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalLinksSettingsSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState,
    useOneUi: Boolean
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    // Design des petits blocs individuels
    val cardShape = if (useOneUi) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)
    val cardBorder = if (useOneUi) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    val bubbleColor = if (useOneUi) {
        if (isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    var showCartoradio by remember { mutableStateOf(prefs.getBoolean("link_cartoradio", true)) }
    var showAnfr by remember { mutableStateOf(prefs.getBoolean("show_anfr", true)) }
    var showCellularFr by remember { mutableStateOf(prefs.getBoolean("link_cellularfr", true)) }
    var showRncMobile by remember { mutableStateOf(prefs.getBoolean("link_rncmobile", true)) }
    var showSignalQuest by remember { mutableStateOf(prefs.getBoolean("link_signalquest", true)) }
    var showEnbAnalytics by remember { mutableStateOf(prefs.getBoolean("link_enbanalytics", true)) }

    var linksOrder by remember {
        mutableStateOf(prefs.getString("page_site_external_links_order", "cartoradio,cellularfr,signalquest,rncmobile,enbanalytics,anfr")!!.split(","))
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp, start = 24.dp, end = 24.dp)) {

            // --- EN-TÊTE AVEC BOUTON RETOUR ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Text(
                    text = AppStrings.externalLinksSettingsTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(48.dp))
            }

            val density = LocalDensity.current
            val itemHeight = 64.dp
            val spacing = 10.dp
            val stepPx = with(density) { (itemHeight + spacing).toPx() }

            var draggedItem by remember { mutableStateOf<String?>(null) }
            var dragOffset by remember { mutableFloatStateOf(0f) }

            // --- LISTE DES BLOCS INDIVIDUELS SANS CADRE EXTERIEUR ---
            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                linksOrder.forEach { linkId ->
                    key(linkId) {
                        val isDragged = draggedItem == linkId
                        val dragModifier = Modifier.pointerInput(linkId) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggedItem = linkId; dragOffset = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume(); dragOffset += dragAmount.y
                                    val currentIndex = linksOrder.indexOf(linkId)
                                    if (currentIndex < 0) return@detectDragGesturesAfterLongPress
                                    var newIndex = currentIndex
                                    while (dragOffset > stepPx * 0.5f && newIndex < linksOrder.size - 1) { dragOffset -= stepPx; newIndex++ }
                                    while (dragOffset < -stepPx * 0.5f && newIndex > 0) { dragOffset += stepPx; newIndex-- }
                                    if (newIndex != currentIndex) {
                                        val newList = linksOrder.toMutableList()
                                        val item = newList.removeAt(currentIndex)
                                        newList.add(newIndex, item)
                                        linksOrder = newList
                                        prefs.edit().putString("external_links_order", newList.joinToString(",")).apply()
                                    }
                                },
                                onDragEnd = { draggedItem = null; dragOffset = 0f },
                                onDragCancel = { draggedItem = null; dragOffset = 0f }
                            )
                        }

                        val label = when (linkId) {
                            "cartoradio" -> "Cartoradio"
                            "anfr" -> "data.gouv.fr"
                            "cellularfr" -> "CellularFR"
                            "rncmobile" -> "RNC Mobile"
                            "signalquest" -> "Signal Quest"
                            "enbanalytics" -> "eNB-Analytics"
                            else -> ""
                        }

                        val logoRes = when (linkId) {
                            "cartoradio" -> R.drawable.logo_cartoradio
                            "anfr" -> R.drawable.logo_anfr
                            "cellularfr" -> R.drawable.logo_cellularfr
                            "rncmobile" -> R.drawable.logo_rncmobile
                            "signalquest" -> R.drawable.logo_signalquest
                            "enbanalytics" -> R.drawable.logo_enbanalytics
                            else -> 0
                        }

                        val checked = when (linkId) {
                            "cartoradio" -> showCartoradio
                            "anfr" -> showAnfr
                            "cellularfr" -> showCellularFr
                            "rncmobile" -> showRncMobile
                            "signalquest" -> showSignalQuest
                            "enbanalytics" -> showEnbAnalytics
                            else -> false
                        }

                        val onChecked = { it: Boolean ->
                            when (linkId) {
                                "cartoradio" -> { showCartoradio = it; prefs.edit().putBoolean("link_cartoradio", it).apply() }
                                "anfr" -> { showAnfr = it; prefs.edit().putBoolean("show_anfr", it).apply() }
                                "cellularfr" -> { showCellularFr = it; prefs.edit().putBoolean("link_cellularfr", it).apply() }
                                "rncmobile" -> { showRncMobile = it; prefs.edit().putBoolean("link_rncmobile", it).apply() }
                                "signalquest" -> { showSignalQuest = it; prefs.edit().putBoolean("link_signalquest", it).apply() }
                                "enbanalytics" -> { showEnbAnalytics = it; prefs.edit().putBoolean("link_enbanalytics", it).apply() }
                            }
                        }

                        if (label.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(itemHeight)
                                    .zIndex(if (isDragged) 10f else 0f)
                                    .graphicsLayer {
                                        translationY = if (isDragged) dragOffset else 0f
                                        scaleX = if (isDragged) 1.02f else 1f
                                        scaleY = if (isDragged) 1.02f else 1f
                                        shadowElevation = if (isDragged) 8.dp.toPx() else 0f
                                    }
                                    .then(dragModifier),
                                shape = cardShape,
                                border = cardBorder,
                                color = if (isDragged) MaterialTheme.colorScheme.surfaceVariant else bubbleColor
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                                ) {
                                    Icon(Icons.Default.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(16.dp))

                                    if (logoRes != 0) {
                                        Image(painter = painterResource(id = logoRes), contentDescription = null, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)))
                                        Spacer(modifier = Modifier.width(16.dp))
                                    }

                                    Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)

                                    if (useOneUi) Box(modifier = Modifier.scale(0.85f)) { fr.geotower.ui.components.OneUiSwitch(checked, onChecked) }
                                    else Switch(checked = checked, onCheckedChange = onChecked, modifier = Modifier.scale(0.8f))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = {
                    showCartoradio = true
                    showAnfr = true
                    showCellularFr = true
                    showRncMobile = true
                    showSignalQuest = true
                    showEnbAnalytics = true
                    linksOrder = listOf("cartoradio", "cellularfr", "signalquest", "rncmobile", "enbanalytics", "anfr")

                    prefs.edit()
                        .putBoolean("link_cartoradio", true)
                        .putBoolean("show_anfr", true)
                        .putBoolean("link_cellularfr", true)
                        .putBoolean("link_rncmobile", true)
                        .putBoolean("link_signalquest", true)
                        .putBoolean("link_enbanalytics", true)
                        .putString("page_site_external_links_order", "cartoradio,cellularfr,signalquest,rncmobile,enbanalytics,anfr")
                        .apply()
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = AppStrings.resetToDefault,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}