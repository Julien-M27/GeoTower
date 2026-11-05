package fr.geotower.wear.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import fr.geotower.wear.data.model.WearOperatorSite
import fr.geotower.wear.data.model.WearTower
import fr.geotower.wear.presentation.theme.getOperatorColor

/**
 * Fiche détaillée du site / opérateur spécifique (identique à Garmin OperatorDetailView).
 * Affiche les technologies, les bandes de fréquences en MHz et les secteurs ANFR réels.
 */
@Composable
fun OperatorDetailScreen(
    tower: WearTower,
    operatorSite: WearOperatorSite,
    operatorIndex: Int,
    totalOperators: Int,
    onOpenCompass: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()
    val opColor = getOperatorColor(operatorSite.operator)

    ScalingLazyColumn(
        state = listState,
        autoCentering = AutoCenteringParams(itemIndex = 1),
        contentPadding = PaddingValues(start = 10.dp, top = 20.dp, end = 10.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier.fillMaxSize().background(Color.Black)
    ) {
        // En-tête : Index de l'opérateur sur le site (ex: "Opérateur 1/2")
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
            ) {
                Text(
                    text = "Opérateur $operatorIndex/$totalOperators",
                    fontSize = 9.sp,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(opColor)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = operatorSite.operator,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Section 1 : Technologies actives
        item {
            OperatorDetailCard(
                title = "Technologies",
                value = operatorSite.technologies,
                accentColor = Color.White
            )
        }

        // Section 2 : Fréquences & Bandes actives (défilement continu Marquee comme sur Garmin)
        item {
            OperatorDetailCard(
                title = "Fréquences actives",
                value = operatorSite.bands,
                accentColor = Color.White,
                isMarquee = true
            )
        }

        // Section 3 : Azimuts des secteurs radio ANFR
        item {
            OperatorDetailCard(
                title = "Secteurs ANFR",
                value = operatorSite.azimuthsString,
                accentColor = Color(0xFF30D158), // Vert cap Garmin
                isMarquee = true
            )
        }

        // Section 4 : Support associé
        item {
            val supDesc = if (tower.supportHeight != null && tower.supportHeight > 0) {
                "${tower.supportType} (${tower.supportHeight} m) • ${tower.distanceMeters} m"
            } else {
                "${tower.supportType} • ${tower.distanceMeters} m"
            }
            OperatorDetailCard(
                title = "Support physique",
                value = supDesc,
                accentColor = MaterialTheme.colors.onSurfaceVariant,
                isMarquee = true
            )
        }

        // Bouton Boussole / Radar
        item {
            Spacer(Modifier.height(4.dp))
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
private fun OperatorDetailCard(
    title: String,
    value: String,
    accentColor: Color,
    isMarquee: Boolean = false,
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
                color = accentColor,
                maxLines = if (isMarquee) 1 else 3,
                softWrap = !isMarquee,
                modifier = if (isMarquee) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier
            )
        }
    }
}
