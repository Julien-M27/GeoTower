package fr.geotower.ui.screens.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.geotower.data.community.CommunityDataFeature
import fr.geotower.data.community.CommunityDataPreferences
import fr.geotower.ui.components.GeoTowerSwitch
import fr.geotower.ui.components.settingsPopupFadingEdge
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings
import fr.geotower.utils.OperatorColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityDataSettingsSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState,
    useOneUi: Boolean
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

    val themeMode = AppConfig.themeMode.value
    val isOledMode = AppConfig.isOledMode.value
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val cardShape = if (useOneUi) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)
    val cardBorder = if (useOneUi) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    val cardColor = if (useOneUi) {
        if (isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val scrollState = rememberScrollState()
    val communityOperators = CommunityDataPreferences.orderedOperators(AppConfig.defaultOperator.value)
    val sheetMaxHeight = (configuration.screenHeightDp.dp * 0.7f).coerceAtMost(560.dp)

    val enabledStates = remember {
        mutableStateMapOf<String, Boolean>().apply {
            CommunityDataPreferences.operators.forEach { operator ->
                operator.features.forEach { feature ->
                    feature.sources.forEach { source ->
                        val key = CommunityDataPreferences.prefKey(operator.key, feature.id, source.id)
                        this[key] = CommunityDataPreferences.isEnabled(prefs, operator.key, feature.id, source.id)
                    }
                }
            }
        }
    }
    val photosEnabledStates = remember {
        mutableStateMapOf<String, Boolean>().apply {
            CommunityDataPreferences.operators.forEach { operator ->
                this[operator.key] = CommunityDataPreferences.isPhotosEnabled(prefs, operator.key)
            }
        }
    }
    val sourceOrderStates = remember {
        mutableStateMapOf<String, List<String>>().apply {
            CommunityDataPreferences.operators.forEach { operator ->
                operator.features.forEach { feature ->
                    this[CommunityDataPreferences.sourceOrderPrefKey(operator.key, feature.id)] =
                        CommunityDataPreferences.orderedSources(prefs, operator.key, feature).map { it.id }
                }
            }
        }
    }
    val fallbackOnlyStates = remember {
        mutableStateMapOf<String, Boolean>().apply {
            CommunityDataPreferences.operators.forEach { operator ->
                operator.features.forEach { feature ->
                    feature.sources.forEach { source ->
                        val key = CommunityDataPreferences.sourceFallbackPrefKey(operator.key, feature.id, source.id)
                        this[key] = CommunityDataPreferences.isSourceFallbackOnly(prefs, operator.key, feature.id, source.id)
                    }
                }
            }
        }
    }

    fun updateSource(operatorKey: String, featureId: String, sourceId: String, enabled: Boolean) {
        val key = CommunityDataPreferences.prefKey(operatorKey, featureId, sourceId)
        enabledStates[key] = enabled
        CommunityDataPreferences.setEnabled(prefs, operatorKey, featureId, sourceId, enabled)
    }

    fun updatePhotosEnabled(operatorKey: String, enabled: Boolean) {
        photosEnabledStates[operatorKey] = enabled
        CommunityDataPreferences.setPhotosEnabled(prefs, operatorKey, enabled)
    }

    fun updateFallbackOnly(operatorKey: String, featureId: String, sourceId: String, fallbackOnly: Boolean) {
        val key = CommunityDataPreferences.sourceFallbackPrefKey(operatorKey, featureId, sourceId)
        fallbackOnlyStates[key] = fallbackOnly
        CommunityDataPreferences.setSourceFallbackOnly(prefs, operatorKey, featureId, sourceId, fallbackOnly)
    }

    fun moveSource(operatorKey: String, feature: CommunityDataFeature, sourceId: String, offset: Int) {
        val orderKey = CommunityDataPreferences.sourceOrderPrefKey(operatorKey, feature.id)
        val currentOrder = sourceOrderStates[orderKey]
            ?: CommunityDataPreferences.orderedSources(prefs, operatorKey, feature).map { it.id }
        val index = currentOrder.indexOf(sourceId)
        val targetIndex = (index + offset).coerceIn(0, currentOrder.lastIndex)
        if (index < 0 || index == targetIndex) return

        val nextOrder = currentOrder.toMutableList().apply {
            removeAt(index)
            add(targetIndex, sourceId)
        }
        sourceOrderStates[orderKey] = nextOrder
        CommunityDataPreferences.setSourceOrder(prefs, operatorKey, feature.id, nextOrder)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        BackHandler(onBack = onDismiss)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = sheetMaxHeight)
                .navigationBarsPadding()
                .settingsPopupFadingEdge(scrollState)
                .verticalScroll(scrollState)
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Text(
                    text = AppStrings.communityDataSettingsTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(48.dp))
            }

            Text(
                text = AppStrings.communityDataSettingsDesc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            communityOperators.forEach { operator ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = cardShape,
                    border = cardBorder,
                    color = cardColor
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(OperatorColors.colorIntForKey(operator.key)), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(operator.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        operator.features.forEach { feature ->
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 14.dp, bottom = 12.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                            )
                            Text(
                                text = when (feature.id) {
                                    CommunityDataPreferences.FEATURE_PHOTOS -> AppStrings.communityDataPhotos
                                    CommunityDataPreferences.FEATURE_SPEEDTEST -> AppStrings.communityDataSpeedtest
                                    else -> feature.label
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            if (feature.id == CommunityDataPreferences.FEATURE_PHOTOS) {
                                val photosChecked = photosEnabledStates[operator.key] ?: true
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = AppStrings.communityDataShowOperatorPhotos,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    GeoTowerSwitch(
                                        checked = photosChecked,
                                        onCheckedChange = { updatePhotosEnabled(operator.key, it) },
                                        modifier = Modifier.scale(if (useOneUi) 0.85f else 0.8f),
                                        useOneUi = useOneUi
                                    )
                                }
                                if (photosChecked && feature.sources.size > 1) {
                                    Text(
                                        text = AppStrings.communityDataPhotoSourceOrder,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                    )
                                }
                            }
                            val sourceOrderKey = CommunityDataPreferences.sourceOrderPrefKey(operator.key, feature.id)
                            val sourceOrder = sourceOrderStates[sourceOrderKey]
                                ?: CommunityDataPreferences.orderedSources(prefs, operator.key, feature).map { it.id }
                            val sourcesById = feature.sources.associateBy { it.id }
                            val orderedSources = sourceOrder.mapNotNull { sourcesById[it] } +
                                feature.sources.filterNot { it.id in sourceOrder }
                            val showSources = feature.id != CommunityDataPreferences.FEATURE_PHOTOS ||
                                (photosEnabledStates[operator.key] ?: true)
                            if (showSources) orderedSources.forEachIndexed { index, source ->
                                val key = CommunityDataPreferences.prefKey(operator.key, feature.id, source.id)
                                val checked = enabledStates[key] ?: true
                                val canReorder = feature.id == CommunityDataPreferences.FEATURE_PHOTOS && orderedSources.size > 1
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = source.label,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (canReorder) {
                                        IconButton(
                                            onClick = { moveSource(operator.key, feature, source.id, -1) },
                                            enabled = index > 0,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                                        }
                                        IconButton(
                                            onClick = { moveSource(operator.key, feature, source.id, 1) },
                                            enabled = index < orderedSources.lastIndex,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    GeoTowerSwitch(
                                        checked = checked,
                                        onCheckedChange = { newValue ->
                                            updateSource(operator.key, feature.id, source.id, newValue)
                                        },
                                        modifier = Modifier.scale(if (useOneUi) 0.85f else 0.8f),
                                        useOneUi = useOneUi
                                    )
                                }
                                if (canReorder && checked && index > 0) {
                                    val fallbackKey = CommunityDataPreferences.sourceFallbackPrefKey(operator.key, feature.id, source.id)
                                    val fallbackChecked = fallbackOnlyStates[fallbackKey] ?: false
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 2.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = AppStrings.communityDataPhotoSourceFallbackOnly,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        GeoTowerSwitch(
                                            checked = fallbackChecked,
                                            onCheckedChange = { newValue ->
                                                updateFallbackOnly(operator.key, feature.id, source.id, newValue)
                                            },
                                            modifier = Modifier.scale(if (useOneUi) 0.75f else 0.7f),
                                            useOneUi = useOneUi
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = {
                    CommunityDataPreferences.reset(prefs)
                    enabledStates.clear()
                    photosEnabledStates.clear()
                    sourceOrderStates.clear()
                    fallbackOnlyStates.clear()
                    CommunityDataPreferences.operators.forEach { operator ->
                        photosEnabledStates[operator.key] = true
                        operator.features.forEach { feature ->
                            sourceOrderStates[CommunityDataPreferences.sourceOrderPrefKey(operator.key, feature.id)] =
                                feature.sources.map { it.id }
                            feature.sources.forEach { source ->
                                enabledStates[CommunityDataPreferences.prefKey(operator.key, feature.id, source.id)] = true
                                fallbackOnlyStates[CommunityDataPreferences.sourceFallbackPrefKey(operator.key, feature.id, source.id)] = false
                            }
                        }
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(AppStrings.resetToDefault, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
