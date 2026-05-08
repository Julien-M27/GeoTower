package fr.geotower.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharePreferencesSheet(
    shareOrder: List<String>,
    onOrderChange: (List<String>) -> Unit,
    mapEnabled: Boolean,
    onMapChange: (Boolean) -> Unit,
    elevationProfileEnabled: Boolean,
    onElevationProfileChange: (Boolean) -> Unit,
    supportEnabled: Boolean,
    onSupportChange: (Boolean) -> Unit,
    idsEnabled: Boolean,
    onIdsChange: (Boolean) -> Unit,
    datesEnabled: Boolean,
    onDatesChange: (Boolean) -> Unit,
    addressEnabled: Boolean,
    onAddressChange: (Boolean) -> Unit,
    statusEnabled: Boolean,
    onStatusChange: (Boolean) -> Unit,
    speedtestEnabled: Boolean, // 🚨 NEW
    onSpeedtestChange: (Boolean) -> Unit, // 🚨 NEW
    throughputEnabled: Boolean,
    onThroughputChange: (Boolean) -> Unit,
    freqEnabled: Boolean,
    onFreqChange: (Boolean) -> Unit,
    splitImageEnabled: Boolean,
    onSplitImageChange: (Boolean) -> Unit,
    qrEnabled: Boolean,
    onQrChange: (Boolean) -> Unit,
    confidentialEnabled: Boolean,
    onConfidentialChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    sheetState: SheetState,
    useOneUi: Boolean,
    bubbleColor: Color
) {
    val currentOrder by rememberUpdatedState(shareOrder)

    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBgColor,
        dragHandle = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BottomSheetDefaults.DragHandle(
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
        }
    ) {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(bottom = 24.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- NOUVEL EN-TÊTE AVEC BOUTON RETOUR ---
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(
                    text = AppStrings.defaultShareContentTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(48.dp)) // Pour équilibrer le bouton
            }
            Text(AppStrings.dragToReorderHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp), textAlign = TextAlign.Center)

            val density = LocalDensity.current
            val cardHeight = 64.dp
            val spacing = 12.dp
            val stepPx = with(density) { (cardHeight + spacing).toPx() }

            var draggedItem by remember { mutableStateOf<String?>(null) }
            var dragOffset by remember { mutableFloatStateOf(0f) }

            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                val shape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)
                val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null

                currentOrder.forEach { pageId ->
                    key(pageId) {
                        val isDragged = draggedItem == pageId

                        val dragModifier = Modifier.pointerInput(pageId) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggedItem = pageId
                                    dragOffset = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.y

                                    val currentIndex = currentOrder.indexOf(pageId)
                                    if (currentIndex < 0) return@detectDragGesturesAfterLongPress

                                    var newIndex = currentIndex

                                    while (dragOffset > stepPx * 0.5f && newIndex < currentOrder.size - 1) {
                                        dragOffset -= stepPx
                                        newIndex++
                                    }
                                    while (dragOffset < -stepPx * 0.5f && newIndex > 0) {
                                        dragOffset += stepPx
                                        newIndex--
                                    }

                                    if (newIndex != currentIndex) {
                                        val newList = currentOrder.toMutableList()
                                        val item = newList.removeAt(currentIndex)
                                        newList.add(newIndex, item)
                                        onOrderChange(newList)
                                    }
                                },
                                onDragEnd = { draggedItem = null; dragOffset = 0f },
                                onDragCancel = { draggedItem = null; dragOffset = 0f }
                            )
                        }

                        when (pageId) {
                            "map" -> DraggableSwitchCard(AppStrings.shareMapOption, mapEnabled, onMapChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "elevation_profile" -> DraggableSwitchCard(AppStrings.shareElevationProfileOption, elevationProfileEnabled, onElevationProfileChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "support" -> DraggableSwitchCard(AppStrings.shareSupportOption, supportEnabled, onSupportChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "ids" -> DraggableSwitchCard(AppStrings.shareIdsOption, idsEnabled, onIdsChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "dates" -> DraggableSwitchCard(AppStrings.shareDatesOption, datesEnabled, onDatesChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "address" -> DraggableSwitchCard(AppStrings.shareAddressOption, addressEnabled, onAddressChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "speedtest" -> DraggableSwitchCard(AppStrings.shareSpeedtestOption, speedtestEnabled, onSpeedtestChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "throughput" -> DraggableSwitchCard(AppStrings.shareThroughputOption, throughputEnabled, onThroughputChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "status" -> DraggableSwitchCard(AppStrings.shareStatusOption, statusEnabled, onStatusChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "freq" -> DraggableSwitchCard(AppStrings.shareFreqOption, freqEnabled, onFreqChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                        }
                    }
                }
            } // Fin de la Column(verticalArrangement = Arrangement.spacedBy(spacing))

            Spacer(modifier = Modifier.height(24.dp))

            // --- BOUTON RÉINITIALISER (TOUT PAR DÉFAUT) ---
            TextButton(
                onClick = {
                    onOrderChange(listOf("map", "elevation_profile", "support", "ids", "dates", "address", "speedtest", "throughput", "status", "freq"))
                    onMapChange(true)
                    onElevationProfileChange(true)
                    onSplitImageChange(true) // Réinitialise aussi la scission
                    onSupportChange(true)
                    onIdsChange(true)
                    onDatesChange(true)
                    onAddressChange(true)
                    onSpeedtestChange(true)
                    onThroughputChange(true)
                    onStatusChange(true)
                    onFreqChange(true)
                    // Note : On ne réinitialise volontairement pas le mode "Confidentiel" par sécurité
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(AppStrings.resetToDefault, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(24.dp).navigationBarsPadding())

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            val shape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)
            val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null

            // ✅ INTERRUPTEUR : SCINDER L'IMAGE (Juste au-dessus du QR Code)
            if (freqEnabled) {
                SimpleSwitchCardWithDesc(
                    title = AppStrings.splitShareImage,
                    desc = AppStrings.splitShareImageDesc,
                    checked = splitImageEnabled,
                    onCheckedChange = onSplitImageChange,
                    shape = shape,
                    border = border,
                    bubbleColor = bubbleColor,
                    useOneUi = useOneUi
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ✅ LE BOUTON QR CODE EST ICI !
            SimpleSwitchCard("QR Code", qrEnabled, onQrChange, shape, border, bubbleColor, useOneUi)
            Spacer(modifier = Modifier.height(8.dp))

            // --- LE BOUTON CONFIDENTIEL (Fixé en bas) ---
            SimpleSwitchCardWithDesc(AppStrings.shareConfidentialOption, AppStrings.shareConfidentialDesc, confidentialEnabled, onConfidentialChange, shape, border, bubbleColor, useOneUi)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportSharePreferencesSheet(
    shareOrder: List<String>, onOrderChange: (List<String>) -> Unit,
    mapEnabled: Boolean, onMapChange: (Boolean) -> Unit,
    supportEnabled: Boolean, onSupportChange: (Boolean) -> Unit,
    operatorsEnabled: Boolean, onOperatorsChange: (Boolean) -> Unit,

    // ✅ LE PARAMÈTRE MANQUANT ÉTAIT LÀ !
    qrEnabled: Boolean, onQrChange: (Boolean) -> Unit,

    confidentialEnabled: Boolean, onConfidentialChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    sheetState: SheetState, useOneUi: Boolean, bubbleColor: Color
) {
    val currentOrder by rememberUpdatedState(shareOrder)
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBgColor,
        dragHandle = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BottomSheetDefaults.DragHandle(
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
        }
    ) {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(bottom = 24.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- NOUVEL EN-TÊTE AVEC BOUTON RETOUR ---
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(
                    text = AppStrings.supportShareTitle, // <-- MODIFIÉ ICI (Le bon titre)
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(48.dp)) // Pour équilibrer le bouton
            }
            Text(AppStrings.dragToReorderHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp), textAlign = TextAlign.Center)

            val density = LocalDensity.current
            val cardHeight = 64.dp
            val spacing = 12.dp
            val stepPx = with(density) { (cardHeight + spacing).toPx() }

            var draggedItem by remember { mutableStateOf<String?>(null) }
            var dragOffset by remember { mutableFloatStateOf(0f) }

            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                val shape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)
                val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null

                currentOrder.forEach { pageId ->
                    key(pageId) {
                        val isDragged = draggedItem == pageId
                        val dragModifier = Modifier.pointerInput(pageId) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggedItem = pageId; dragOffset = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.y
                                    val currentIndex = currentOrder.indexOf(pageId)
                                    if (currentIndex < 0) return@detectDragGesturesAfterLongPress
                                    var newIndex = currentIndex
                                    while (dragOffset > stepPx * 0.5f && newIndex < currentOrder.size - 1) { dragOffset -= stepPx; newIndex++ }
                                    while (dragOffset < -stepPx * 0.5f && newIndex > 0) { dragOffset += stepPx; newIndex-- }
                                    if (newIndex != currentIndex) {
                                        val newList = currentOrder.toMutableList()
                                        val item = newList.removeAt(currentIndex)
                                        newList.add(newIndex, item)
                                        onOrderChange(newList)
                                    }
                                },
                                onDragEnd = { draggedItem = null; dragOffset = 0f },
                                onDragCancel = { draggedItem = null; dragOffset = 0f }
                            )
                        }

                        when (pageId) {
                            "map" -> DraggableSwitchCard(AppStrings.shareMapOption, mapEnabled, onMapChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "support" -> DraggableSwitchCard(AppStrings.shareSupportOption, supportEnabled, onSupportChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                            "operators" -> DraggableSwitchCard(AppStrings.operatorsTitle, operatorsEnabled, onOperatorsChange, shape, border, bubbleColor, useOneUi, dragModifier, isDragged, dragOffset, cardHeight)
                        }
                    }
                }
            }

            // --- BOUTON RÉINITIALISER (TOUT PAR DÉFAUT) ---
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(
                onClick = {
                    onOrderChange(listOf("map", "support", "operators"))
                    onMapChange(true)
                    onSupportChange(true)
                    onOperatorsChange(true)
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(AppStrings.resetToDefault, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(24.dp).navigationBarsPadding())

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            val shape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)
            val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null

            // ✅ LE BOUTON QR CODE DU PYLÔNE !
            SimpleSwitchCard("QR Code", qrEnabled, onQrChange, shape, border, bubbleColor, useOneUi)
            Spacer(modifier = Modifier.height(8.dp))

            SimpleSwitchCardWithDesc(AppStrings.shareConfidentialOption, AppStrings.shareConfidentialDesc, confidentialEnabled, onConfidentialChange, shape, border, bubbleColor, useOneUi)
        }
    }
}

@Composable
fun SimpleSwitchCardWithDesc(title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean) {
    val themeMode by AppConfig.themeMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val paleBgColor = if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent

    Surface(shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (useOneUi) {
                fr.geotower.ui.components.OneUiSwitch(checked, onCheckedChange)
            } else {
                Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary))
            }
        }
    }
}
// === NOUVEAU MENU : PRÉFÉRENCES DE PARTAGE DE LA CARTE ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSharePreferencesSheet(
    azimuthsEnabled: Boolean, onAzimuthsChange: (Boolean) -> Unit,
    speedometerEnabled: Boolean, onSpeedometerChange: (Boolean) -> Unit,
    scaleEnabled: Boolean, onScaleChange: (Boolean) -> Unit,
    attributionEnabled: Boolean, onAttributionChange: (Boolean) -> Unit,
    statusEnabled: Boolean, // 🚨 AJOUT
    onStatusChange: (Boolean) -> Unit, // 🚨 AJOUT
    confidentialEnabled: Boolean, onConfidentialChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    sheetState: SheetState, useOneUi: Boolean, bubbleColor: Color
) {
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBgColor,
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BottomSheetDefaults.DragHandle(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            }
        }
    ) {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(bottom = 24.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(
                    text = AppStrings.shareMapDetailsTitle, // "Carte"
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(48.dp))
            }

            val spacing = 12.dp
            val shape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)
            val border = if (!useOneUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null

            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                SimpleSwitchCard(AppStrings.shareMapAzimuthsOption, azimuthsEnabled, onAzimuthsChange, shape, border, bubbleColor, useOneUi)
                SimpleSwitchCard(AppStrings.shareStatusOption, statusEnabled, onStatusChange, shape, border, bubbleColor, useOneUi) // 🚨 AJOUT
                SimpleSwitchCard(AppStrings.shareMapSpeedometerOption, speedometerEnabled, onSpeedometerChange, shape, border, bubbleColor, useOneUi)
                SimpleSwitchCard(AppStrings.shareMapScaleOption, scaleEnabled, onScaleChange, shape, border, bubbleColor, useOneUi)
                SimpleSwitchCard(AppStrings.shareMapAttributionOption, attributionEnabled, onAttributionChange, shape, border, bubbleColor, useOneUi)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bouton de réinitialisation (Confidentialité non affectée par sécurité)
            TextButton(
                onClick = {
                    onAzimuthsChange(true)
                    onSpeedometerChange(true)
                    onScaleChange(true)
                    onAttributionChange(true)
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(AppStrings.resetToDefault, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(24.dp).navigationBarsPadding())

            // Bouton mode confidentiel séparé
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            SimpleSwitchCardWithDesc(AppStrings.shareConfidentialOption, AppStrings.shareConfidentialDesc, confidentialEnabled, onConfidentialChange, shape, border, bubbleColor, useOneUi)
        }
    }
}
