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

@Composable
fun AntennaMapToolBox(
    isToolboxExpanded: Boolean,
    onToggleToolbox: () -> Unit,
    isSearchActive: Boolean,
    onToggleSearch: () -> Unit,
    isMeasuringMode: Boolean,
    onToggleMeasure: () -> Unit,
    onOpenLayers: () -> Unit,
    onOpenSettings: () -> Unit
) {
    // L'animation est gérée en interne par le composant !
    val toolboxRotation by animateFloatAsState(
        targetValue = if (isToolboxExpanded) 180f else 0f,
        animationSpec = tween(350),
        label = "toolboxRotation"
    )

    Surface(
        modifier = Modifier.width(54.dp),
        shape = RoundedCornerShape(27.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(visible = isToolboxExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = onToggleSearch,
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (isSearchActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Rechercher",
                            tint = if (isSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleMeasure,
                        modifier = Modifier
                            .size(40.dp)
                            .background(if (isMeasuringMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Straighten,
                            contentDescription = "Règle",
                            tint = if (isMeasuringMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = onOpenLayers, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Layers, contentDescription = "Calques", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Paramètres", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    }
                    HorizontalDivider(modifier = Modifier.width(32.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
            }
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clickable { onToggleToolbox() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isToolboxExpanded) Icons.Default.Close else Icons.Default.Build,
                    contentDescription = "Outils",
                    modifier = Modifier.size(24.dp).rotate(toolboxRotation)
                )
            }
        }
    }
}
