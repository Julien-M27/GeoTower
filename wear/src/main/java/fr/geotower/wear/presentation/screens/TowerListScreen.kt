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
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
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
    isLoading: Boolean,
    errorMessage: String?,
    onSupportSelected: (WearTower) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        state = listState,
        autoCentering = AutoCenteringParams(itemIndex = if (towers.isNotEmpty()) 1 else 0),
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
                    text = "Antennes à proximité",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
                )
                Text(
                    text = if (towers.isNotEmpty()) "${towers.size} supports physiques détectés" else "Recherche...",
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
            val primaryOpColor = getOperatorColor(tower.operator)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colors.surface)
                    .clickable {
                        onSupportSelected(tower)
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Ligne 1 : Badges des opérateurs présents sur ce support (Multi-opérateurs) + Direction
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Badges opérateurs
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            val opsToShow = if (tower.operators.isNotEmpty()) tower.operators else listOf(
                                fr.geotower.wear.data.model.WearOperatorSite(operator = tower.operator)
                            )
                            opsToShow.take(3).forEach { opSite ->
                                val color = getOperatorColor(opSite.operator)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(color)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = opSite.operator,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            if (opsToShow.size > 3) {
                                Text(text = "+${opsToShow.size - 3}", fontSize = 8.sp, color = Color.White)
                            }
                        }

                        // Cap & Azimut
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tower.arrowSymbol,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.primary
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = "${tower.cardinalDirection} (${tower.bearingDegrees}°)",
                                fontSize = 9.sp,
                                color = MaterialTheme.colors.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(3.dp))

                    // Ligne 2 : Distance & Type de Support physique (comme sur Garmin)
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
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        val supDesc = if (tower.supportHeight != null && tower.supportHeight > 0) {
                            "${tower.supportType} (${tower.supportHeight}m)"
                        } else {
                            tower.supportType
                        }
                        Text(
                            text = supDesc,
                            fontSize = 9.sp,
                            color = Color(0xFFFFB300),
                            maxLines = 1
                        )
                    }

                    // Ligne 3 : Adresse ou Commune
                    if (tower.address.isNotBlank()) {
                        Text(
                            text = tower.address,
                            fontSize = 8.sp,
                            color = MaterialTheme.colors.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 2.dp)
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
