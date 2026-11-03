package fr.geotower.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.geotower.services.garmin.GarminCompanionManager
import fr.geotower.ui.theme.LocalGeoTowerUiStyle

@Composable
fun GarminSettingsCard(
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean,
    modifier: Modifier = Modifier
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent
    val context = LocalContext.current

    val isGcmInstalled = remember { GarminCompanionManager.isGarminConnectInstalled(context) }
    val isReady by GarminCompanionManager.isSdkReady.collectAsStateWithLifecycle()
    val watches by GarminCompanionManager.connectedWatches.collectAsStateWithLifecycle()
    val lastSync by GarminCompanionManager.lastSyncTimestamp.collectAsStateWithLifecycle()

    val subtitle = remember(isGcmInstalled, isReady, watches, lastSync) {
        when {
            !isGcmInstalled -> "Optionnel • Requis uniquement si vous utilisez une montre Garmin"
            !isReady -> "En attente de Garmin Connect Mobile"
            watches.isNotEmpty() -> {
                val watchNames = watches.joinToString(", ") { it.name }
                if (lastSync > 0) {
                    "$watchNames • Synchronisé en BLE"
                } else {
                    "$watchNames • Connecté en BLE"
                }
            }
            else -> "Service actif • En attente de connexion avec la montre"
        }
    }

    Surface(
        onClick = {
            if (isGcmInstalled) {
                GarminCompanionManager.refreshConnectedDevices()
            }
        },
        shape = shape,
        border = border,
        color = cardBg,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(sizing.spacing(16.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Watch,
                contentDescription = null,
                tint = if (watches.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(sizing.component(24.dp))
            )
            Spacer(Modifier.width(sizing.spacing(16.dp)))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Montres connectées Garmin (BLE)",
                    style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                    fontWeight = FontWeight.Bold
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
