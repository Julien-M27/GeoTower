package fr.geotower.wear.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import fr.geotower.wear.data.model.DataSourceMode
import fr.geotower.wear.data.model.GpsFixStatus
import fr.geotower.wear.data.model.WearScreen

/**
 * Page d'accueil principale de l'application Wear OS (menu en liste comme sur Garmin).
 * Contient les options dans l'ordre exact :
 * 1. Antennes à proximité
 * 2. Boussole
 * 3. Paramètres
 */
@Composable
fun HomeScreen(
    towersCount: Int,
    gpsStatus: GpsFixStatus,
    dataSourceMode: DataSourceMode,
    onNavigate: (WearScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        state = listState,
        autoCentering = null,
        contentPadding = PaddingValues(start = 10.dp, top = 20.dp, end = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxSize().background(Color.Black)
    ) {
        // En-tête : Titre + Statut rapide
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
            ) {
                Text(
                    text = "🗼 GeoTower",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
                )
                Text(
                    text = when (gpsStatus) {
                        is GpsFixStatus.Fixed -> "GPS Montre OK (±${gpsStatus.accuracyMeters.toInt()}m) • ${dataSourceMode.shortLabel}"
                        is GpsFixStatus.Searching -> "Fix GPS en cours... • ${dataSourceMode.shortLabel}"
                        is GpsFixStatus.Error -> "GPS inactif • ${dataSourceMode.shortLabel}"
                    },
                    fontSize = 9.sp,
                    color = when (gpsStatus) {
                        is GpsFixStatus.Fixed -> Color(0xFF30D158)
                        is GpsFixStatus.Searching -> Color(0xFFFFB300)
                        is GpsFixStatus.Error -> Color(0xFFFF453A)
                    },
                    textAlign = TextAlign.Center
                )
            }
        }

        // 1. Antennes à proximité
        item {
            MenuNavigationCard(
                icon = "📡",
                title = "Antennes à proximité",
                subtitle = if (towersCount > 0) "$towersCount antennes détectées" else "Recherche autour de vous",
                onClick = { onNavigate(WearScreen.LIST) }
            )
        }

        // 2. Boussole
        item {
            MenuNavigationCard(
                icon = "🧭",
                title = "Boussole",
                subtitle = "Azimut & radar en direct",
                onClick = { onNavigate(WearScreen.RADAR) }
            )
        }

        // 3. Paramètres
        item {
            MenuNavigationCard(
                icon = "⚙️",
                title = "Paramètres",
                subtitle = "Source (${dataSourceMode.shortLabel}) & filtres",
                onClick = { onNavigate(WearScreen.SETTINGS) }
            )
        }
    }
}

@Composable
private fun MenuNavigationCard(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Chip(
        onClick = onClick,
        icon = {
            Text(text = icon, fontSize = 16.sp)
        },
        label = {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        secondaryLabel = {
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = modifier.fillMaxWidth()
    )
}
