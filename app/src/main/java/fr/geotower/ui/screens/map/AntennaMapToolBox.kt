package fr.geotower.ui.screens.map

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import fr.geotower.R

private val ToolboxButtonDiameter = 54.dp
private val ToolboxIconDiameter = 24.dp

@Composable
fun AntennaMapToolBox(
    isToolboxExpanded: Boolean,
    onToggleToolbox: () -> Unit,
    isSearchActive: Boolean,
    onToggleSearch: () -> Unit,
    isMeasuringMode: Boolean,
    onToggleMeasure: () -> Unit,
    isTimeSliderActive: Boolean,
    onToggleTimeSlider: () -> Unit,
    onOpenLayers: () -> Unit,
    onOpenSettings: () -> Unit,
    showSearch: Boolean = true,
    showMeasure: Boolean = true,
    showTimeSlider: Boolean = true,
    showLayers: Boolean = true,
    showSettings: Boolean = true,
    expandLeft: Boolean = false
) {
    // L'animation est gérée en interne par le composant !
    val toolboxRotation by animateFloatAsState(
        targetValue = if (isToolboxExpanded) 180f else 0f,
        animationSpec = tween(350),
        label = "toolboxRotation"
    )

    // Une fonction active alors que la toolbox est repliée : on met en avant le
    // bouton de la toolbox (comme les boutons internes) pour le signaler.
    val hasActiveFunction = isSearchActive || isMeasuringMode || isTimeSliderActive
    val highlightCollapsed = hasActiveFunction && !isToolboxExpanded

    if (expandLeft) {
        Surface(
            modifier = Modifier.height(ToolboxButtonDiameter),
            shape = RoundedCornerShape(ToolboxButtonDiameter / 2f),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = isToolboxExpanded,
                    enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        if (showSearch) {
                            IconButton(
                                onClick = onToggleSearch,
                                modifier = Modifier
                                    .size(ToolboxButtonDiameter)
                                    .background(
                                        if (isSearchActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(R.string.appstrings_search),
                                    tint = if (isSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(ToolboxIconDiameter)
                                )
                            }
                        }
                        if (showMeasure) {
                            IconButton(
                                onClick = onToggleMeasure,
                                modifier = Modifier
                                    .size(ToolboxButtonDiameter)
                                    .background(if (isMeasuringMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Straighten,
                                    contentDescription = stringResource(R.string.appstrings_ruler),
                                    tint = if (isMeasuringMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(ToolboxIconDiameter)
                                )
                            }
                        }
                        if (showTimeSlider) {
                            IconButton(
                                onClick = onToggleTimeSlider,
                                modifier = Modifier
                                    .size(ToolboxButtonDiameter)
                                    .background(if (isTimeSliderActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = stringResource(R.string.appstrings_time_slider),
                                    tint = if (isTimeSliderActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(ToolboxIconDiameter)
                                )
                            }
                        }
                        if (showLayers) {
                            IconButton(onClick = onOpenLayers, modifier = Modifier.size(ToolboxButtonDiameter)) {
                                Icon(Icons.Default.Layers, contentDescription = stringResource(R.string.appstrings_layers), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(ToolboxIconDiameter))
                            }
                        }
                        if (showSettings) {
                            IconButton(onClick = onOpenSettings, modifier = Modifier.size(ToolboxButtonDiameter)) {
                                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.appstrings_settings_title), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(ToolboxIconDiameter))
                            }
                        }
                        VerticalDivider(
                            modifier = Modifier
                                .height(32.dp)
                                .padding(horizontal = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(ToolboxButtonDiameter)
                        .background(
                            if (highlightCollapsed) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            CircleShape
                        )
                        .clickable { onToggleToolbox() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isToolboxExpanded) Icons.Default.Close else Icons.Default.Build,
                        contentDescription = stringResource(R.string.appstrings_tools),
                        tint = if (highlightCollapsed) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        modifier = Modifier.size(ToolboxIconDiameter).rotate(toolboxRotation)
                    )
                }
            }
        }
    } else {
    Surface(
        modifier = Modifier.width(ToolboxButtonDiameter),
        shape = RoundedCornerShape(ToolboxButtonDiameter / 2f),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(visible = isToolboxExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (showSearch) {
                        IconButton(
                            onClick = onToggleSearch,
                            modifier = Modifier
                                .size(ToolboxButtonDiameter)
                                .background(
                                    if (isSearchActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.appstrings_search),
                                tint = if (isSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(ToolboxIconDiameter)
                            )
                        }
                    }
                    if (showMeasure) {
                        IconButton(
                            onClick = onToggleMeasure,
                            modifier = Modifier
                                .size(ToolboxButtonDiameter)
                                .background(if (isMeasuringMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Straighten,
                                contentDescription = stringResource(R.string.appstrings_ruler),
                                tint = if (isMeasuringMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(ToolboxIconDiameter)
                            )
                        }
                    }
                    if (showTimeSlider) {
                        IconButton(
                            onClick = onToggleTimeSlider,
                            modifier = Modifier
                                .size(ToolboxButtonDiameter)
                                .background(if (isTimeSliderActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = stringResource(R.string.appstrings_time_slider),
                                tint = if (isTimeSliderActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(ToolboxIconDiameter)
                            )
                        }
                    }
                    if (showLayers) {
                        IconButton(onClick = onOpenLayers, modifier = Modifier.size(ToolboxButtonDiameter)) {
                            Icon(Icons.Default.Layers, contentDescription = stringResource(R.string.appstrings_layers), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(ToolboxIconDiameter))
                        }
                    }
                    if (showSettings) {
                        IconButton(onClick = onOpenSettings, modifier = Modifier.size(ToolboxButtonDiameter)) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.appstrings_settings_title), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(ToolboxIconDiameter))
                        }
                    }
                    HorizontalDivider(modifier = Modifier.width(32.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
            }
            Box(
                modifier = Modifier
                    .size(ToolboxButtonDiameter)
                    .background(
                        if (highlightCollapsed) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        CircleShape
                    )
                    .clickable { onToggleToolbox() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isToolboxExpanded) Icons.Default.Close else Icons.Default.Build,
                    contentDescription = stringResource(R.string.appstrings_tools),
                    tint = if (highlightCollapsed) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    modifier = Modifier.size(ToolboxIconDiameter).rotate(toolboxRotation)
                )
            }
        }
    }
    }
}
