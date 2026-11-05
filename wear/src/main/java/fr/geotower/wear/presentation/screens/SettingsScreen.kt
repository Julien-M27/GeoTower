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
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import fr.geotower.wear.data.model.DataSourceMode
import fr.geotower.wear.data.model.GpsFixStatus
import fr.geotower.wear.presentation.components.GpsStatusHeader

@Composable
fun SettingsScreen(
    currentSource: DataSourceMode,
    operatorFilter: String?,
    gpsStatus: GpsFixStatus,
    onSourceChanged: (DataSourceMode) -> Unit,
    onOperatorFilterChanged: (String?) -> Unit,
    onForceRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()

    val operators = listOf(
        null to "Tous les opérateurs",
        "ORANGE" to "Orange",
        "SFR" to "SFR",
        "BOUYGUES" to "Bouygues Telecom",
        "FREE" to "Free Mobile"
    )

    ScalingLazyColumn(
        state = listState,
        autoCentering = null,
        contentPadding = PaddingValues(start = 8.dp, top = 20.dp, end = 8.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxSize().background(Color.Black)
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
            ) {
                Text(
                    text = "⚙️ Paramètres",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
                )
            }
        }

        // Section 1 : Source des données
        item {
            Text(
                text = "SOURCE DES DONNÉES",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        item {
            Chip(
                onClick = {
                    onSourceChanged(
                        if (currentSource == DataSourceMode.PHONE_LOCAL) DataSourceMode.WEB_API
                        else DataSourceMode.PHONE_LOCAL
                    )
                },
                label = {
                    Text(
                        text = currentSource.shortLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                secondaryLabel = {
                    Text(
                        text = when (currentSource) {
                            DataSourceMode.PHONE_LOCAL -> "Room SQLite via Bluetooth"
                            DataSourceMode.WEB_API -> "API Web HTTPS directe"
                        },
                        fontSize = 9.sp,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Section 2 : Filtres Opérateurs
        item {
            Text(
                text = "FILTRER PAR OPÉRATEUR",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        items(operators.size) { index ->
            val (key, label) = operators[index]
            val isSelected = (operatorFilter == key) || (operatorFilter == null && key == null)

            Chip(
                onClick = { onOperatorFilterChanged(key) },
                label = {
                    Text(
                        text = if (isSelected) "✓ $label" else label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.White else MaterialTheme.colors.onSurfaceVariant
                    )
                },
                colors = if (isSelected) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
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
