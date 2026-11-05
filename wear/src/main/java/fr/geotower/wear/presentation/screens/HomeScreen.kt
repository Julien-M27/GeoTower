package fr.geotower.wear.presentation.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
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
 * 3. Carte
 * 4. Paramètres
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
    val context = LocalContext.current
    val appIconBitmap = remember(context) {
        try {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            drawable.toBitmap(width = 48, height = 48)
        } catch (e: Exception) {
            null
        }
    }

    ScalingLazyColumn(
        state = listState,
        autoCentering = AutoCenteringParams(itemIndex = 1),
        contentPadding = PaddingValues(start = 10.dp, top = 20.dp, end = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxSize().background(Color.Black)
    ) {
        // En-tête : Vrai Logo de l'application + Nom GeoTower + Statut rapide
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (appIconBitmap != null) {
                        Image(
                            bitmap = appIconBitmap.asImageBitmap(),
                            contentDescription = "Logo GeoTower",
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = "GeoTower",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary
                    )
                }
                Spacer(Modifier.height(2.dp))
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
                icon = Icons.Default.MyLocation,
                title = "Antennes à proximité",
                onClick = { onNavigate(WearScreen.LIST) }
            )
        }

        // 2. Boussole
        item {
            MenuNavigationCard(
                icon = Icons.Default.Explore,
                title = "Boussole",
                onClick = { onNavigate(WearScreen.RADAR) }
            )
        }

        // 3. Carte
        item {
            MenuNavigationCard(
                icon = Icons.Default.Map,
                title = "Carte",
                onClick = { onNavigate(WearScreen.MAP) }
            )
        }

        // 4. Paramètres
        item {
            MenuNavigationCard(
                icon = Icons.Default.Settings,
                title = "Paramètres",
                onClick = { onNavigate(WearScreen.SETTINGS) }
            )
        }
    }
}

@Composable
private fun MenuNavigationCard(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Chip(
        onClick = onClick,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colors.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        label = {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = modifier.fillMaxWidth()
    )
}
