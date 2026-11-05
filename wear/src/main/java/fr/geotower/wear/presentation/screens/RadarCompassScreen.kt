package fr.geotower.wear.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import fr.geotower.wear.data.model.DataSourceMode
import fr.geotower.wear.data.model.GpsFixStatus
import fr.geotower.wear.data.model.WearTower
import fr.geotower.wear.presentation.theme.getOperatorColor
import fr.geotower.wear.presentation.theme.getOperatorShortName
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@Composable
fun RadarCompassScreen(
    towers: List<WearTower>,
    selectedTower: WearTower?,
    gpsStatus: GpsFixStatus,
    dataSourceMode: DataSourceMode,
    onTowerSelected: (WearTower) -> Unit,
    onOpenDetails: (WearTower) -> Unit,
    onNavigateToList: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Portées de zoom sélectionnables en mètres : 250m, 500m, 1000m, 2000m, 5000m
    val zoomScales = listOf(250f, 500f, 1000f, 2000f, 5000f)
    var zoomIndex by remember { mutableStateOf(2) } // 1000m par défaut
    val maxDist = zoomScales[zoomIndex]

    // Index du site actuellement sélectionné
    val activeTower = selectedTower ?: towers.firstOrNull()
    val currentIndex = towers.indexOfFirst { it.id == activeTower?.id }.let { if (it >= 0) it else 0 }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Dessin du Radar et de la Boussole Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadarRadius = (size.minDimension / 2f) * 0.65f

            // 1. Cercles concentriques du radar
            val ringCount = 3
            for (i in 1..ringCount) {
                val radius = maxRadarRadius * (i / ringCount.toFloat())
                drawCircle(
                    color = Color(0xFF2C2C2E),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // 2. Réticule en croix (Axes Nord-Sud et Est-Ouest)
            drawLine(
                color = Color(0xFF3A3A3C),
                start = Offset(center.x, center.y - maxRadarRadius),
                end = Offset(center.x, center.y + maxRadarRadius),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color(0xFF3A3A3C),
                start = Offset(center.x - maxRadarRadius, center.y),
                end = Offset(center.x + maxRadarRadius, center.y),
                strokeWidth = 1.dp.toPx()
            )

            // 3. Points radar pour chaque antenne
            towers.take(12).forEach { tower ->
                val distFraction = (tower.distanceMeters / maxDist).coerceIn(0.12f, 1.05f)
                val radarDist = maxRadarRadius * distFraction
                val angleRad = Math.toRadians((tower.bearingDegrees - 90).toDouble())
                val pointX = center.x + (radarDist * cos(angleRad)).toFloat()
                val pointY = center.y + (radarDist * sin(angleRad)).toFloat()
                val opColor = getOperatorColor(tower.operator)
                val isSelected = activeTower?.id == tower.id

                // Point antenne (sans cible blanche)
                drawCircle(
                    color = opColor,
                    radius = if (isSelected) 5.dp.toPx() else 3.5.dp.toPx(),
                    center = Offset(pointX, pointY)
                )
            }

            // 4. Aiguille de cap au centre vers l'antenne sélectionnée
            if (activeTower != null) {
                val arrowBearing = activeTower.bearingDegrees.toFloat()
                rotate(degrees = arrowBearing, pivot = center) {
                    val arrowPath = Path().apply {
                        moveTo(center.x, center.y - 30.dp.toPx()) // Pointe vers le haut
                        lineTo(center.x - 6.5.dp.toPx(), center.y - 11.dp.toPx())
                        lineTo(center.x, center.y - 15.dp.toPx())
                        lineTo(center.x + 6.5.dp.toPx(), center.y - 11.dp.toPx())
                        close()
                    }
                    drawPath(
                        path = arrowPath,
                        color = getOperatorColor(activeTower.operator)
                    )
                }
            }

            // Centre utilisateur
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = center
            )
        }

        // Éléments d'interface utilisateur superposés
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Haut : Titre Boussole + Statut GPS (calé sous l'heure de l'écran circulaire)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .padding(top = 34.dp)
            ) {
                Text(
                    text = "Boussole",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
                )
                Text(
                    text = when (gpsStatus) {
                        is GpsFixStatus.Fixed -> "Fix ±${gpsStatus.accuracyMeters.toInt()}m"
                        is GpsFixStatus.Searching -> "Fix..."
                        is GpsFixStatus.Error -> "Pas de GPS"
                    },
                    fontSize = 8.5.sp,
                    color = when (gpsStatus) {
                        is GpsFixStatus.Fixed -> Color(0xFF30D158)
                        is GpsFixStatus.Searching -> Color(0xFFFFB300)
                        is GpsFixStatus.Error -> Color(0xFFFF453A)
                    }
                )
            }

            Spacer(Modifier.weight(1f))

            // Bas : Commandes interactives de Zoom & Défilement des sites + Cartouche info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .padding(bottom = 18.dp)
            ) {
                // Barre de commandes : [◀ Site] [− Zoom] [Échelle] [+ Zoom] [Site ▶]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Site précédent ◀
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2C2C2E).copy(alpha = 0.95f))
                            .clickable(enabled = towers.size > 1) {
                                if (towers.isNotEmpty()) {
                                    val prev = if (currentIndex > 0) currentIndex - 1 else towers.lastIndex
                                    onTowerSelected(towers[prev])
                                }
                            }
                    ) {
                        Text(
                            text = "◀",
                            fontSize = 10.sp,
                            color = if (towers.size > 1) Color.White else Color(0xFF555555)
                        )
                    }

                    // Zoom Moins −
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2C2C2E).copy(alpha = 0.95f))
                            .clickable(enabled = zoomIndex < zoomScales.lastIndex) {
                                if (zoomIndex < zoomScales.lastIndex) zoomIndex++
                            }
                    ) {
                        Text(
                            text = "−",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (zoomIndex < zoomScales.lastIndex) Color.White else Color(0xFF555555)
                        )
                    }

                    // Échelle de portée active (ex: 1 km)
                    Text(
                        text = if (maxDist >= 1000f) "${(maxDist / 1000).toInt()} km" else "${maxDist.toInt()} m",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF30D158)
                    )

                    // Zoom Plus +
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2C2C2E).copy(alpha = 0.95f))
                            .clickable(enabled = zoomIndex > 0) {
                                if (zoomIndex > 0) zoomIndex--
                            }
                    ) {
                        Text(
                            text = "+",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (zoomIndex > 0) Color.White else Color(0xFF555555)
                        )
                    }

                    // Site suivant ▶
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2C2C2E).copy(alpha = 0.95f))
                            .clickable(enabled = towers.size > 1) {
                                if (towers.isNotEmpty()) {
                                    val next = if (currentIndex < towers.lastIndex) currentIndex + 1 else 0
                                    onTowerSelected(towers[next])
                                }
                            }
                    ) {
                        Text(
                            text = "▶",
                            fontSize = 10.sp,
                            color = if (towers.size > 1) Color.White else Color(0xFF555555)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Cartouche d'informations sur l'antenne sélectionnée
                if (activeTower != null) {
                    val opColor = getOperatorColor(activeTower.operator)
                    val shortOp = getOperatorShortName(activeTower.operator)
                    val siteIndexLabel = if (towers.isNotEmpty()) "[${currentIndex + 1}/${towers.size}]" else ""

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1C1C1E).copy(alpha = 0.92f))
                            .clickable { onOpenDetails(activeTower) }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Badge opérateur
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(opColor)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = shortOp,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                // Distance & Cap
                                val distText = if (activeTower.distanceMeters >= 1000) {
                                    String.format(java.util.Locale.US, "%.1f km", activeTower.distanceMeters / 1000f)
                                } else {
                                    "${activeTower.distanceMeters} m"
                                }
                                Text(
                                    text = "$distText • ${activeTower.cardinalDirection} (${activeTower.bearingDegrees}°)",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false
                                )

                                // Index
                                Text(
                                    text = siteIndexLabel,
                                    fontSize = 8.sp,
                                    color = MaterialTheme.colors.onSurfaceVariant
                                )
                            }

                            Spacer(Modifier.height(1.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val supDesc = if (activeTower.supportHeight != null && activeTower.supportHeight > 0) {
                                    "${activeTower.supportType} (${activeTower.supportHeight}m)"
                                } else {
                                    activeTower.supportType
                                }
                                Text(
                                    text = supDesc,
                                    fontSize = 7.5.sp,
                                    color = Color(0xFFFFB300),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Détails >",
                                    fontSize = 7.5.sp,
                                    color = MaterialTheme.colors.primary
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1C1C1E))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (towers.isEmpty()) "Aucune antenne" else "Touchez la liste",
                            fontSize = 8.sp,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
