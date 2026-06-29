package fr.geotower.ui.screens.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.geotower.R
import fr.geotower.ui.components.settingsPopupFadingEdge
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.SharePrefs

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
    photosEnabled: Boolean,
    onPhotosChange: (Boolean) -> Unit,
    idsEnabled: Boolean,
    onIdsChange: (Boolean) -> Unit,
    datesEnabled: Boolean,
    onDatesChange: (Boolean) -> Unit,
    addressEnabled: Boolean,
    onAddressChange: (Boolean) -> Unit,
    statusEnabled: Boolean,
    onStatusChange: (Boolean) -> Unit,
    speedtestEnabled: Boolean,
    onSpeedtestChange: (Boolean) -> Unit,
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
    ReorderableBlockSettingsSheet(
        title = stringResource(R.string.settings_default_share_content_title),
        order = shareOrder,
        blocks = listOf(
            ConfigurableBlock("map", { stringResource(R.string.appstrings_share_map_option) }, mapEnabled, onMapChange),
            ConfigurableBlock("elevation_profile", { stringResource(R.string.appstrings_share_elevation_profile_option) }, elevationProfileEnabled, onElevationProfileChange),
            ConfigurableBlock("support", { stringResource(R.string.appstrings_share_support_option) }, supportEnabled, onSupportChange),
            ConfigurableBlock("photos", { stringResource(R.string.appstrings_share_photos_option) }, photosEnabled, onPhotosChange),
            ConfigurableBlock("ids", { stringResource(R.string.appstrings_share_ids_option) }, idsEnabled, onIdsChange),
            ConfigurableBlock("dates", { stringResource(R.string.appstrings_share_dates_option) }, datesEnabled, onDatesChange),
            ConfigurableBlock("address", { stringResource(R.string.appstrings_share_address_option) }, addressEnabled, onAddressChange),
            ConfigurableBlock("speedtest", { stringResource(R.string.appstrings_share_speedtest_option) }, speedtestEnabled, onSpeedtestChange),
            ConfigurableBlock("throughput", { stringResource(R.string.appstrings_share_throughput_option) }, throughputEnabled, onThroughputChange),
            ConfigurableBlock("status", { stringResource(R.string.appstrings_share_status_option) }, statusEnabled, onStatusChange),
            ConfigurableBlock("freq", { stringResource(R.string.appstrings_share_freq_option) }, freqEnabled, onFreqChange)
        ),
        onOrderChange = onOrderChange,
        onReset = {
            onOrderChange(SharePrefs.DEFAULT_SITE_ORDER.split(","))
            onMapChange(true)
            onElevationProfileChange(true)
            onSplitImageChange(true)
            onSupportChange(true)
            onPhotosChange(true)
            onIdsChange(true)
            onDatesChange(true)
            onAddressChange(true)
            onSpeedtestChange(true)
            onThroughputChange(true)
            onStatusChange(true)
            onFreqChange(true)
        },
        onDismiss = onDismiss,
        onBack = onBack,
        sheetState = sheetState,
        useOneUi = useOneUi,
        bubbleColor = bubbleColor,
        contentAfterReset = { shape, border ->
            val sizing = LocalGeoTowerUiStyle.current.sizing
            Spacer(modifier = Modifier.height(sizing.spacing(24.dp)).navigationBarsPadding())
            HorizontalDivider(
                modifier = Modifier.padding(vertical = sizing.spacing(12.dp)),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
            if (freqEnabled) {
                SimpleSwitchCardWithDesc(
                    title = stringResource(R.string.appstrings_split_share_image),
                    desc = stringResource(R.string.appstrings_split_share_image_desc),
                    checked = splitImageEnabled,
                    onCheckedChange = onSplitImageChange,
                    shape = shape,
                    border = border,
                    bubbleColor = bubbleColor,
                    useOneUi = useOneUi
                )
                Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
            }
            SimpleSwitchCard("QR Code", qrEnabled, onQrChange, shape, border, bubbleColor, useOneUi)
            Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
            SimpleSwitchCardWithDesc(
                stringResource(R.string.appstrings_share_confidential_option),
                stringResource(R.string.appstrings_share_confidential_desc),
                confidentialEnabled,
                onConfidentialChange,
                shape,
                border,
                bubbleColor,
                useOneUi
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportSharePreferencesSheet(
    shareOrder: List<String>,
    onOrderChange: (List<String>) -> Unit,
    mapEnabled: Boolean,
    onMapChange: (Boolean) -> Unit,
    supportEnabled: Boolean,
    onSupportChange: (Boolean) -> Unit,
    photosEnabled: Boolean,
    onPhotosChange: (Boolean) -> Unit,
    operatorsEnabled: Boolean,
    onOperatorsChange: (Boolean) -> Unit,
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
    ReorderableBlockSettingsSheet(
        title = stringResource(R.string.appstrings_support_share_title),
        order = shareOrder,
        blocks = listOf(
            ConfigurableBlock("map", { stringResource(R.string.appstrings_share_map_option) }, mapEnabled, onMapChange),
            ConfigurableBlock("support", { stringResource(R.string.appstrings_share_support_option) }, supportEnabled, onSupportChange),
            ConfigurableBlock("photos", { stringResource(R.string.appstrings_share_photos_option) }, photosEnabled, onPhotosChange),
            ConfigurableBlock("operators", { stringResource(R.string.appstrings_operators_title) }, operatorsEnabled, onOperatorsChange)
        ),
        onOrderChange = onOrderChange,
        onReset = {
            onOrderChange(SharePrefs.DEFAULT_SUPPORT_ORDER.split(","))
            onMapChange(true)
            onSupportChange(true)
            onPhotosChange(true)
            onOperatorsChange(true)
        },
        onDismiss = onDismiss,
        onBack = onBack,
        sheetState = sheetState,
        useOneUi = useOneUi,
        bubbleColor = bubbleColor,
        contentAfterReset = { shape, border ->
            val sizing = LocalGeoTowerUiStyle.current.sizing
            Spacer(modifier = Modifier.height(sizing.spacing(24.dp)).navigationBarsPadding())
            HorizontalDivider(
                modifier = Modifier.padding(vertical = sizing.spacing(12.dp)),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
            SimpleSwitchCard("QR Code", qrEnabled, onQrChange, shape, border, bubbleColor, useOneUi)
            Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
            SimpleSwitchCardWithDesc(
                stringResource(R.string.appstrings_share_confidential_option),
                stringResource(R.string.appstrings_share_confidential_desc),
                confidentialEnabled,
                onConfidentialChange,
                shape,
                border,
                bubbleColor,
                useOneUi
            )
        }
    )
}

@Composable
fun SimpleSwitchCardWithDesc(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent

    Surface(shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(12.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = sizing.spacing(16.dp))) {
                Text(title, style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                Text(desc, style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            fr.geotower.ui.components.GeoTowerSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                useOneUi = useOneUi
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSharePreferencesSheet(
    azimuthsEnabled: Boolean,
    onAzimuthsChange: (Boolean) -> Unit,
    speedometerEnabled: Boolean,
    onSpeedometerChange: (Boolean) -> Unit,
    scaleEnabled: Boolean,
    onScaleChange: (Boolean) -> Unit,
    attributionEnabled: Boolean,
    onAttributionChange: (Boolean) -> Unit,
    qrEnabled: Boolean,
    onQrChange: (Boolean) -> Unit,
    statusEnabled: Boolean,
    onStatusChange: (Boolean) -> Unit,
    confidentialEnabled: Boolean,
    onConfidentialChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    sheetState: SheetState,
    useOneUi: Boolean,
    bubbleColor: Color
) {
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val sizing = LocalGeoTowerUiStyle.current.sizing

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBgColor,
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BottomSheetDefaults.DragHandle(modifier = Modifier.padding(top = sizing.spacing(8.dp), bottom = sizing.spacing(4.dp)))
            }
        }
    ) {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .settingsPopupFadingEdge(scrollState)
                .verticalScroll(scrollState)
                .padding(bottom = sizing.spacing(24.dp), start = sizing.spacing(24.dp), end = sizing.spacing(24.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(24.dp)), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(
                    text = stringResource(R.string.appstrings_share_map_details_title),
                    style = sizing.textStyle(MaterialTheme.typography.titleLarge),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(sizing.spacing(48.dp)))
            }

            val spacing = sizing.spacing(12.dp)
            val shape = if (useOneUi) RoundedCornerShape(sizing.component(22.dp)) else RoundedCornerShape(sizing.component(12.dp))
            val border = if (!useOneUi) BorderStroke(sizing.component(1.dp), MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null

            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                SimpleSwitchCard(stringResource(R.string.appstrings_share_map_azimuths_option), azimuthsEnabled, onAzimuthsChange, shape, border, bubbleColor, useOneUi)
                SimpleSwitchCard(stringResource(R.string.appstrings_share_status_option), statusEnabled, onStatusChange, shape, border, bubbleColor, useOneUi)
                SimpleSwitchCard(stringResource(R.string.appstrings_share_map_speedometer_option), speedometerEnabled, onSpeedometerChange, shape, border, bubbleColor, useOneUi)
                SimpleSwitchCard(stringResource(R.string.appstrings_share_map_scale_option), scaleEnabled, onScaleChange, shape, border, bubbleColor, useOneUi)
                SimpleSwitchCard(stringResource(R.string.appstrings_share_map_attribution_option), attributionEnabled, onAttributionChange, shape, border, bubbleColor, useOneUi)
                SimpleSwitchCard(stringResource(R.string.brand_qr_code), qrEnabled, onQrChange, shape, border, bubbleColor, useOneUi)
            }

            Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))
            TextButton(
                onClick = {
                    onAzimuthsChange(true)
                    onSpeedometerChange(true)
                    onScaleChange(true)
                    onAttributionChange(true)
                    onQrChange(true)
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(sizing.spacing(8.dp)))
                Text(stringResource(R.string.appstrings_reset_to_default), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(sizing.spacing(24.dp)).navigationBarsPadding())

            HorizontalDivider(
                modifier = Modifier.padding(vertical = sizing.spacing(12.dp)),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
            SimpleSwitchCardWithDesc(
                stringResource(R.string.appstrings_share_confidential_option),
                stringResource(R.string.appstrings_share_confidential_desc),
                confidentialEnabled,
                onConfidentialChange,
                shape,
                border,
                bubbleColor,
                useOneUi
            )
        }
    }
}
