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
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Dessin du Radar et de la Boussole Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadarRadius = (size.minDimension / 2f) * 0.72f

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

            // 3. Portée maximale calculée pour la mise à l'échelle
            val maxDist = max(1000f, towers.maxOfOrNull { it.distanceMeters.toFloat() } ?: 3000f)

            // 4. Points radar pour chaque antenne
            towers.take(8).forEach { tower ->
                val distFraction = (tower.distanceMeters / maxDist).coerceIn(0.15f, 1f)
                val radarDist = maxRadarRadius * distFraction
                val angleRad = Math.toRadians((tower.bearingDegrees - 90).toDouble())
                val pointX = center.x + (radarDist * cos(angleRad)).toFloat()
                val pointY = center.y + (radarDist * sin(angleRad)).toFloat()
                val opColor = getOperatorColor(tower.operator)
                val isSelected = selectedTower?.id == tower.id

                // Point antenne
                drawCircle(
                    color = opColor,
                    radius = if (isSelected) 5.5.dp.toPx() else 4.dp.toPx(),
                    center = Offset(pointX, pointY)
                )

                // Anneau de surbrillance si sélectionnée
                if (isSelected) {
                    drawCircle(
                        color = Color.White,
                        radius = 8.5.dp.toPx(),
                        center = Offset(pointX, pointY),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }

            // 5. Aiguille de cap au centre vers l'antenne verrouillée
            if (selectedTower != null) {
                val arrowBearing = selectedTower.bearingDegrees.toFloat()
                rotate(degrees = arrowBearing, pivot = center) {
                    val arrowPath = Path().apply {
                        moveTo(center.x, center.y - 32.dp.toPx()) // Pointe vers le haut
                        lineTo(center.x - 7.dp.toPx(), center.y - 12.dp.toPx())
                        lineTo(center.x, center.y - 16.dp.toPx())
                        lineTo(center.x + 7.dp.toPx(), center.y - 12.dp.toPx())
                        close()
                    }
                    drawPath(
                        path = arrowPath,
                        color = getOperatorColor(selectedTower.operator)
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
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Haut : Titre Boussole + Statut GPS
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
            ) {
                Text(
                    text = "🧭 Boussole",
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
                    fontSize = 9.sp,
                    color = when (gpsStatus) {
                        is GpsFixStatus.Fixed -> Color(0xFF30D158)
                        is GpsFixStatus.Searching -> Color(0xFFFFB300)
                        is GpsFixStatus.Error -> Color(0xFFFF453A)
                    }
                )
            }

            Spacer(Modifier.weight(1f))

            // Bas : Cartouche d'informations sur l'antenne verrouillée
            if (selectedTower != null) {
                val opColor = getOperatorColor(selectedTower.operator)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1C1C1E).copy(alpha = 0.92f))
                        .clickable { onOpenDetails(selectedTower) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
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
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = selectedTower.operator,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Distance
                            val distText = if (selectedTower.distanceMeters >= 1000) {
                                String.format(java.util.Locale.US, "%.1f km", selectedTower.distanceMeters / 1000f)
                            } else {
                                "${selectedTower.distanceMeters} m"
                            }
                            Text(
                                text = distText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )

                            // Cap
                            Text(
                                text = "${selectedTower.cardinalDirection} ${selectedTower.bearingDegrees}°",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colors.primary
                            )
                        }

                        Spacer(Modifier.height(2.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = selectedTower.technologies,
                                fontSize = 9.sp,
                                color = MaterialTheme.colors.secondary
                            )
                            Text(
                                text = "🔍 Fiche détails >",
                                fontSize = 9.sp,
                                color = MaterialTheme.colors.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1C1C1E))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (towers.isEmpty()) "Aucune antenne détectée" else "Touchez la liste pour cibler",
                        fontSize = 10.sp,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
        }
    }
}
