package fr.geotower.wear.presentation.screens

import androidx.compose.foundation.background
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
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import fr.geotower.wear.data.model.WearTower
import fr.geotower.wear.presentation.theme.getOperatorColor
import java.util.Locale

@Composable
fun TowerDetailScreen(
    tower: WearTower,
    isTarget: Boolean,
    onLockTarget: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()
    val opColor = getOperatorColor(tower.operator)

    ScalingLazyColumn(
        state = listState,
        autoCentering = AutoCenteringParams(itemIndex = 1),
        contentPadding = PaddingValues(start = 10.dp, top = 20.dp, end = 10.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier.fillMaxSize().background(Color.Black)
    ) {
        // En-tête : Opérateur + ID ANFR
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(opColor)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = tower.operator,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "ID ANFR : ${tower.id}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }
        }

        // Distance & Cap
        item {
            DetailItemCard(
                title = "Distance & Azimut",
                value = "${tower.distanceMeters} m • ${tower.cardinalDirection} (${tower.bearingDegrees}°)",
                accentColor = MaterialTheme.colors.primary
            )
        }

        // Technologies
        item {
            DetailItemCard(
                title = "Technologies actives",
                value = tower.technologies,
                accentColor = MaterialTheme.colors.secondary
            )
        }

        // Type de support
        item {
            val supportLabel = if (tower.supportHeight != null && tower.supportHeight > 0) {
                "${tower.supportType} (${tower.supportHeight} m)"
            } else {
                tower.supportType
            }
            DetailItemCard(
                title = "Support",
                value = supportLabel,
                accentColor = Color(0xFFFFB300)
            )
        }

        // Commune & Adresse
        if (tower.address.isNotBlank()) {
            item {
                DetailItemCard(
                    title = "Commune / Adresse",
                    value = tower.address,
                    accentColor = Color.White
                )
            }
        }

        // Coordonnées GPS
        item {
            DetailItemCard(
                title = "Coordonnées pylône",
                value = String.format(Locale.US, "%.5f, %.5f", tower.latitude, tower.longitude),
                accentColor = MaterialTheme.colors.onSurfaceVariant
            )
        }

        // Bouton Verrouiller cible
        item {
            Spacer(Modifier.height(4.dp))
            CompactChip(
                onClick = onLockTarget,
                label = {
                    Text(
                        text = if (isTarget) "✓ Cible active sur le radar" else "Verrouiller sur le radar",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Bouton Retour
        item {
            CompactChip(
                onClick = onBack,
                label = {
                    Text(
                        text = "Retour",
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

@Composable
private fun DetailItemCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                fontSize = 9.sp,
                color = MaterialTheme.colors.onSurfaceVariant
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )
        }
    }
}
