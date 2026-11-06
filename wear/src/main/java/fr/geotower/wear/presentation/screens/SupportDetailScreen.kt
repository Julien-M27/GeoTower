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
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import fr.geotower.wear.data.model.DistanceUnit
import fr.geotower.wear.data.model.WearOperatorSite
import fr.geotower.wear.data.model.WearTower
import fr.geotower.wear.data.model.formatDistance
import fr.geotower.wear.data.model.formatHeight
import fr.geotower.wear.presentation.theme.getOperatorColor
import fr.geotower.wear.presentation.theme.getOperatorShortName
import java.util.Locale

/**
 * Fiche détaillée du support physique (comme sur Garmin).
 * Affiche le type de support, la hauteur, l'adresse, la distance
 * et la liste de tous les opérateurs présents avec accès à leur fiche détaillée.
 */
@Composable
fun SupportDetailScreen(
    tower: WearTower,
    onOperatorClick: (WearOperatorSite) -> Unit,
    onOpenMap: () -> Unit,
    onOpenCompass: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    distanceUnit: DistanceUnit = DistanceUnit.METRIC
) {
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        state = listState,
        autoCentering = AutoCenteringParams(itemIndex = 0),
        contentPadding = PaddingValues(start = 10.dp, top = 24.dp, end = 10.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier.fillMaxSize().background(Color.Black)
    ) {
        // En-tête : Titre
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
            ) {
                Text(
                    text = "Détail du support",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
                )
            }
        }

        // Support & Hauteur
        item {
            val formattedH = formatHeight(tower.supportHeight, distanceUnit)
            val supTitle = if (formattedH != null) {
                "${tower.supportType} ($formattedH)"
            } else {
                tower.supportType
            }
            DetailItemCard(
                title = "Support physique",
                value = supTitle,
                accentColor = Color(0xFFFFB300)
            )
        }

        // Distance & Cap
        item {
            val distText = formatDistance(tower.distanceMeters, distanceUnit)
            DetailItemCard(
                title = "Distance & Azimut",
                value = "$distText • ${tower.cardinalDirection} (${tower.bearingDegrees}°)",
                accentColor = MaterialTheme.colors.primary
            )
        }

        // Adresse
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

        // Section Opérateurs sur ce support (Multi-opérateurs)
        item {
            Text(
                text = "OPÉRATEURS SUR CE SUPPORT",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        val operators = (if (tower.operators.isNotEmpty()) tower.operators else listOf(
            WearOperatorSite(operator = tower.operator, technologies = tower.technologies)
        )).distinctBy { getOperatorShortName(it.operator) }

        items(operators.size) { index ->
            val op = operators[index]
            val opColor = getOperatorColor(op.operator)

            Chip(
                onClick = { onOperatorClick(op) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(opColor)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = getOperatorShortName(op.operator),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Spacer(Modifier.padding(start = 6.dp))
                        Text(
                            text = op.technologies,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                },
                secondaryLabel = {
                    Text(
                        text = "Voir le détail du site >",
                        fontSize = 8.sp,
                        color = MaterialTheme.colors.primary
                    )
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Bouton Carte
        item {
            Spacer(Modifier.height(4.dp))
            CompactChip(
                onClick = onOpenMap,
                label = {
                    Text(
                        text = "Voir sur la carte",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Bouton Boussole
        item {
            CompactChip(
                onClick = onOpenCompass,
                label = {
                    Text(
                        text = "Boussole / Radar",
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
