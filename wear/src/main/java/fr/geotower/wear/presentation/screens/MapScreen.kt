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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import fr.geotower.wear.data.model.GpsFixStatus
import fr.geotower.wear.data.model.WearTower
import fr.geotower.wear.presentation.theme.getOperatorColor
import kotlin.math.cos
import kotlin.math.sin

/**
 * Écran Carte tactique Garmin pour Wear OS.
 * Affiche la position de la montre au centre, les cercles d'échelle de portée,
 * les antennes relais géolocalisées avec la couleur de leur opérateur et des boutons de zoom (+/-).
 */
@Composable
fun MapScreen(
    towers: List<WearTower>,
    selectedTower: WearTower?,
    gpsStatus: GpsFixStatus,
    onTowerSelected: (WearTower) -> Unit,
    onOpenDetails: (WearTower) -> Unit,
    modifier: Modifier = Modifier
) {
    // Échelle de portée de la carte en mètres : 250m, 500m, 1000m, 2000m, 5000m
    val zoomScales = listOf(250f, 500f, 1000f, 2000f, 5000f)
    var zoomIndex by remember { mutableStateOf(2) } // 1000m par défaut
    val maxRange = zoomScales[zoomIndex]

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E14)),
        contentAlignment = Alignment.Center
    ) {
        // Dessin de la carte vectorielle Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val mapRadius = (size.minDimension / 2f) * 0.74f

            // 1. Cercles de distance concentriques (Garmin Range Rings)
            val ringSteps = 3
            for (step in 1..ringSteps) {
                val radius = mapRadius * (step / ringSteps.toFloat())
                drawCircle(
                    color = Color(0xFF1E2630),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // 2. Axes Nord-Sud et Est-Ouest
            drawLine(
                color = Color(0xFF1E2630),
                start = Offset(center.x, center.y - mapRadius),
                end = Offset(center.x, center.y + mapRadius),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color(0xFF1E2630),
                start = Offset(center.x - mapRadius, center.y),
                end = Offset(center.x + mapRadius, center.y),
                strokeWidth = 1.dp.toPx()
            )

            // Indicateur "N" au Nord
            // 3. Dessin des antennes réparties sur la carte selon leur azimut et distance
            towers.forEach { tower ->
                val distFraction = (tower.distanceMeters.toFloat() / maxRange).coerceAtMost(1.05f)
                val canvasDist = mapRadius * distFraction
                val angleRad = Math.toRadians((tower.bearingDegrees - 90).toDouble())
                val pointX = center.x + (canvasDist * cos(angleRad)).toFloat()
                val pointY = center.y + (canvasDist * sin(angleRad)).toFloat()
                val opColor = getOperatorColor(tower.operator)
                val isSelected = selectedTower?.id == tower.id

                // Point antenne
                drawCircle(
                    color = opColor,
                    radius = if (isSelected) 5.dp.toPx() else 3.5.dp.toPx(),
                    center = Offset(pointX, pointY)
                )

                if (isSelected) {
                    drawCircle(
                        color = Color.White,
                        radius = 8.dp.toPx(),
                        center = Offset(pointX, pointY),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }

            // 4. Position de l'utilisateur (Montre) au centre
            drawCircle(
                color = Color(0xFF007AFF),
                radius = 5.dp.toPx(),
                center = center
            )
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = center
            )
        }

        // Éléments d'interface superposés
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Haut : Titre Carte + Échelle de portée (Zoom)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .padding(top = 30.dp)
            ) {
                Text(
                    text = "Carte",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
                )
                Text(
                    text = if (maxRange >= 1000f) "${(maxRange / 1000).toInt()} km" else "${maxRange.toInt()} m",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF30D158)
                )
            }

            Spacer(Modifier.weight(1f))

            // Bas : Commandes de zoom Garmin (+ / -) et Antenne sélectionnée
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .padding(bottom = 26.dp)
            ) {
                // Zoom Moins
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C2C2E))
                        .clickable(enabled = zoomIndex < zoomScales.lastIndex) {
                            if (zoomIndex < zoomScales.lastIndex) zoomIndex++
                        }
                ) {
                    Text(
                        text = "−",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (zoomIndex < zoomScales.lastIndex) Color.White else Color(0xFF555555)
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Carte résumé antenne verrouillée
                if (selectedTower != null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1C1C1E).copy(alpha = 0.92f))
                            .clickable { onOpenDetails(selectedTower) }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${selectedTower.operator} • ${selectedTower.distanceMeters}m",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                            Text(
                                text = "${selectedTower.cardinalDirection} ${selectedTower.bearingDegrees}°",
                                fontSize = 8.sp,
                                color = MaterialTheme.colors.primary
                            )
                        }
                    }
                } else {
                    Text(
                        text = "${towers.size} antennes",
                        fontSize = 9.sp,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Zoom Plus
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C2C2E))
                        .clickable(enabled = zoomIndex > 0) {
                            if (zoomIndex > 0) zoomIndex--
                        }
                ) {
                    Text(
                        text = "+",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (zoomIndex > 0) Color.White else Color(0xFF555555)
                    )
                }
            }
        }
    }
}
