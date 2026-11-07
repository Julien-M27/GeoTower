package fr.geotower.ui.screens.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.R
import fr.geotower.ui.components.settingsPopupFadingEdge
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.ScreenPageTarget
import fr.geotower.utils.TechFrequencyOrder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechOrderSettingsSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    useOneUi: Boolean = AppConfig.useOneUiDesign,
    bubbleColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    val globalOrder by AppConfig.globalTechOrder
    val scrollState = rememberScrollState()

    var isClosing by remember { mutableStateOf(false) }

    fun handleClose() {
        if (isClosing) return
        isClosing = true
        coroutineScope.launch {
            try {
                sheetState.hide()
            } catch (_: Exception) {
            } finally {
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBgColor
    ) {
        BackHandler(onBack = { handleClose() })

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .settingsPopupFadingEdge(scrollState)
                .verticalScroll(scrollState)
                .padding(bottom = sizing.spacing(48.dp), start = sizing.spacing(24.dp), end = sizing.spacing(24.dp))
        ) {
            // En-tête
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = sizing.spacing(24.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { handleClose() },
                    modifier = Modifier.size(sizing.component(48.dp))
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.appstrings_back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = stringResource(R.string.settings_tech_order_title),
                    style = sizing.textStyle(MaterialTheme.typography.titleLarge),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(sizing.spacing(48.dp)))
            }

            // Section 1 : Ordre Global par défaut
            Text(
                text = stringResource(R.string.pref_order_global_title),
                style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = sizing.spacing(4.dp), start = sizing.spacing(4.dp))
            )
            Text(
                text = stringResource(R.string.pref_order_global_desc),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = sizing.spacing(12.dp), start = sizing.spacing(4.dp))
            )

            Column(verticalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp))) {
                TechOrderGlobalOptionCard(
                    title = stringResource(R.string.pref_order_descending),
                    subtitle = stringResource(R.string.pref_order_descending_sub),
                    isSelected = globalOrder == TechFrequencyOrder.DESCENDING,
                    useOneUi = useOneUi,
                    bubbleColor = bubbleColor,
                    onClick = {
                        AppConfig.setGlobalTechOrder(context, TechFrequencyOrder.DESCENDING)
                    }
                )

                TechOrderGlobalOptionCard(
                    title = stringResource(R.string.pref_order_ascending),
                    subtitle = stringResource(R.string.pref_order_ascending_sub),
                    isSelected = globalOrder == TechFrequencyOrder.ASCENDING,
                    useOneUi = useOneUi,
                    bubbleColor = bubbleColor,
                    onClick = {
                        AppConfig.setGlobalTechOrder(context, TechFrequencyOrder.ASCENDING)
                    }
                )
            }

            Spacer(Modifier.height(sizing.spacing(28.dp)))

            // Section 2 : Personnalisation par page / écran
            Text(
                text = stringResource(R.string.pref_order_per_page_title),
                style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = sizing.spacing(12.dp), start = sizing.spacing(4.dp))
            )

            val pageTargets = listOf(
                ScreenPageTarget.SITE_DETAIL,
                ScreenPageTarget.MAP,
                ScreenPageTarget.SEARCH_LIST,
                ScreenPageTarget.LIVE_MONITOR
            )

            Column(verticalArrangement = Arrangement.spacedBy(sizing.spacing(10.dp))) {
                pageTargets.forEach { target ->
                    val currentPref = AppConfig.pageOrders[target]?.value ?: "global"
                    TechOrderPageCard(
                        target = target,
                        currentValue = currentPref,
                        globalOrder = globalOrder,
                        useOneUi = useOneUi,
                        bubbleColor = bubbleColor,
                        onSelectionChanged = { newValue ->
                            AppConfig.setScreenTechOrder(context, target, newValue)
                        }
                    )
                }
            }

            Spacer(Modifier.height(sizing.spacing(16.dp)))
        }
    }
}

@Composable
private fun TechOrderGlobalOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    useOneUi: Boolean,
    bubbleColor: Color,
    onClick: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val cardColor = if (useOneUi) bubbleColor else Color.Transparent
    val cardShape = RoundedCornerShape(12.dp)
    val cardBorder = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        color = cardColor,
        border = cardBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(sizing.spacing(14.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(sizing.component(22.dp))
                    .border(
                        2.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        CircleShape
                    )
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(sizing.component(14.dp))
                    )
                }
            }

            Spacer(Modifier.width(sizing.spacing(12.dp)))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = sizing.textStyle(MaterialTheme.typography.bodyLarge),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TechOrderPageCard(
    target: ScreenPageTarget,
    currentValue: String,
    globalOrder: TechFrequencyOrder,
    useOneUi: Boolean,
    bubbleColor: Color,
    onSelectionChanged: (String) -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val cardColor = if (useOneUi) bubbleColor else Color.Transparent
    val cardShape = RoundedCornerShape(12.dp)
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

    val globalOrderLabel = if (globalOrder == TechFrequencyOrder.DESCENDING) "5G → 2G" else "2G → 5G"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        color = cardColor,
        border = cardBorder
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(sizing.spacing(12.dp))
        ) {
            Text(
                text = stringResource(target.labelRes),
                style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = sizing.spacing(8.dp))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(sizing.spacing(6.dp))
            ) {
                val isGlobal = currentValue == "global"
                TechOrderChip(
                    label = "${stringResource(R.string.pref_order_global)} ($globalOrderLabel)",
                    isSelected = isGlobal,
                    modifier = Modifier.weight(1.4f),
                    onClick = { onSelectionChanged("global") }
                )

                val isDesc = currentValue == "descending"
                TechOrderChip(
                    label = "5G → 2G",
                    isSelected = isDesc,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectionChanged("descending") }
                )

                val isAsc = currentValue == "ascending"
                TechOrderChip(
                    label = "2G → 5G",
                    isSelected = isAsc,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectionChanged("ascending") }
                )
            }
        }
    }
}

@Composable
private fun TechOrderChip(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = sizing.spacing(8.dp), horizontal = sizing.spacing(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isSelected) "✓ $label" else label,
            fontSize = sizing.text(11.sp),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
