package fr.geotower.wear.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import fr.geotower.wear.data.model.WearTower
import fr.geotower.wear.presentation.theme.getOperatorColor

@Composable
fun TowerCard(
    tower: WearTower,
    modifier: Modifier = Modifier
) {
    val operatorColor = getOperatorColor(tower.operator)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colors.surface)
            .border(1.dp, operatorColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Ligne du haut : Badge Opérateur + Direction / Azimut
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pastille Opérateur
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(operatorColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = tower.operator,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Cap et flèche directionnelle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tower.arrowSymbol,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colors.primary
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "${tower.cardinalDirection} (${tower.bearingDegrees}°)",
                        fontSize = 10.sp,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Ligne médiane : Distance principale + Technologies
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val distanceLabel = if (tower.distanceMeters >= 1000) {
                    String.format(java.util.Locale.US, "%.1f km", tower.distanceMeters / 1000f)
                } else {
                    "${tower.distanceMeters} m"
                }

                Text(
                    text = distanceLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurface
                )

                Text(
                    text = tower.technologies,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colors.secondary
                )
            }

            // Adresse / Commune si présente
            if (tower.address.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = tower.address,
                    fontSize = 9.sp,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
