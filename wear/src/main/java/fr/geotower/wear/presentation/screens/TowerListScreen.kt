package fr.geotower.wear.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import fr.geotower.wear.data.model.WearTower
import fr.geotower.wear.presentation.theme.getOperatorColor

@Composable
fun TowerListScreen(
    towers: List<WearTower>,
    selectedTower: WearTower?,
    isLoading: Boolean,
    errorMessage: String?,
    onTowerSelected: (WearTower) -> Unit,
    onOpenDetails: (WearTower) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()

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
                    text = "📡 Antennes à proximité",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
                )
                Text(
                    text = if (towers.isNotEmpty()) "${towers.size} antennes détectées" else "Recherche...",
                    fontSize = 10.sp,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }
        }

        if (isLoading) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }

        if (errorMessage != null && !isLoading) {
            item {
                Text(
                    text = errorMessage,
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
                )
            }
        }

        items(towers) { tower ->
            val isTarget = selectedTower?.id == tower.id
            val opColor = getOperatorColor(tower.operator)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colors.surface)
                    .border(
                        width = if (isTarget) 1.5.dp else 0.5.dp,
                        color = if (isTarget) Color.White else opColor.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        onTowerSelected(tower)
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Indicateur cible verrouillée
                    if (isTarget) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 3.dp)
                        ) {
                            Text(
                                text = "🎯 CIBLE RADAR VERROUILLÉE",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }

                    // Ligne supérieure : Opérateur + Direction / Azimut
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(opColor)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = tower.operator,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tower.arrowSymbol,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.primary
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "${tower.cardinalDirection} (${tower.bearingDegrees}°)",
                                fontSize = 9.sp,
                                color = MaterialTheme.colors.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(3.dp))

                    // Ligne médiane : Distance & Technologies
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val distanceText = if (tower.distanceMeters >= 1000) {
                            String.format(java.util.Locale.US, "%.1f km", tower.distanceMeters / 1000f)
                        } else {
                            "${tower.distanceMeters} m"
                        }

                        Text(
                            text = distanceText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = tower.technologies,
                            fontSize = 10.sp,
                            color = MaterialTheme.colors.secondary
                        )
                    }

                    // Bouton fiche détails
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Fiche détails >",
                            fontSize = 9.sp,
                            color = MaterialTheme.colors.primary,
                            modifier = Modifier.clickable { onOpenDetails(tower) }
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            CompactChip(
                onClick = onRefresh,
                label = {
                    Text(
                        text = "🔄 Actualiser",
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
