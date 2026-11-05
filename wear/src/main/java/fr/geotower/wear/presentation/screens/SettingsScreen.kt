package fr.geotower.wear.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import fr.geotower.wear.data.model.DataSourceMode
import fr.geotower.wear.data.model.GpsFixStatus
import fr.geotower.wear.data.model.GpsRefreshInterval
import fr.geotower.wear.presentation.components.GpsStatusHeader

@Composable
fun SettingsScreen(
    currentSource: DataSourceMode,
    gpsRefreshInterval: GpsRefreshInterval,
    gpsStatus: GpsFixStatus,
    onSourceChanged: (DataSourceMode) -> Unit,
    onGpsRefreshIntervalChanged: (GpsRefreshInterval) -> Unit,
    onForceRefresh: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()

    val sourceModes = listOf(
        DataSourceMode.AUTO to "Auto (Tél. puis Web)",
        DataSourceMode.PHONE_LOCAL to "Base locale tél. (Bluetooth)",
        DataSourceMode.WEB_API to "API Web directe (Wi-Fi/4G)"
    )

    ScalingLazyColumn(
        state = listState,
        autoCentering = AutoCenteringParams(itemIndex = 1),
        contentPadding = PaddingValues(start = 10.dp, top = 16.dp, end = 10.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxSize().background(Color.Black)
    ) {
        // En-tête compactée pour placer le bandeau de sélection directement au centre
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
            ) {
                Text(
                    text = "Paramètres",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "SOURCE DES DONNÉES",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }
        }

        // Section 1 : Source des données (avec Auto au centre de l'écran par défaut)
        items(sourceModes.size) { index ->
            val (mode, desc) = sourceModes[index]
            val isSelected = currentSource == mode

            Chip(
                onClick = { onSourceChanged(mode) },
                label = {
                    Text(
                        text = if (isSelected) "✓ ${mode.label}" else mode.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.White else MaterialTheme.colors.onSurfaceVariant
                    )
                },
                secondaryLabel = {
                    Text(
                        text = desc,
                        fontSize = 9.sp,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                },
                colors = if (isSelected) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Section 2 : Actualisation du GPS (comme sur Garmin)
        item {
            Text(
                text = "ACTUALISATION GPS (GARMIN)",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        item {
            Chip(
                onClick = { onGpsRefreshIntervalChanged(gpsRefreshInterval.next()) },
                label = {
                    Text(
                        text = gpsRefreshInterval.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                secondaryLabel = {
                    Text(
                        text = gpsRefreshInterval.description,
                        fontSize = 9.sp,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Section 3 : Statut GPS Matériel
        item {
            Text(
                text = "GPS MATÉRIEL MONTRE",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        item {
            GpsStatusHeader(gpsStatus = gpsStatus)
        }

        // Section 4 : À propos
        item {
            Chip(
                onClick = onOpenAbout,
                label = {
                    Text(
                        text = "À propos",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                },
                secondaryLabel = {
                    Text(
                        text = "GeoTower Wear OS v2.0",
                        fontSize = 9.sp,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Bouton Rafraîchir
        item {
            Spacer(Modifier.height(4.dp))
            CompactChip(
                onClick = onForceRefresh,
                label = {
                    Text(
                        text = "🔄 Forcer actualisation",
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
