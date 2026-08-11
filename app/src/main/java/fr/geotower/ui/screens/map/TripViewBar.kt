package fr.geotower.ui.screens.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.R
import fr.geotower.data.trip.TripPlan
import fr.geotower.data.trip.formatTripDuration
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.formatSiteDistanceMeters

/**
 * Barre de **consultation** d'une tournée déjà tracée : son résumé, et le choix entre la parcourir
 * et la retoucher.
 *
 * C'est l'état par défaut quand on ouvre un trajet depuis la liste. Ouvrir directement en édition
 * exposait à déplacer une tournée finie d'un simple doigt posé sur la carte.
 */
@Composable
fun TripViewBar(
    plan: TripPlan,
    distanceUnit: Int,
    onFollow: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = sizing.spacing(14.dp), vertical = sizing.spacing(10.dp))
        ) {
            Text(
                text = plan.name.ifBlank { stringResource(R.string.trips_untitled) },
                fontSize = sizing.text(15.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = tripViewSummary(plan, distanceUnit),
                fontSize = sizing.text(12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = sizing.spacing(2.dp))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = sizing.spacing(10.dp)),
                horizontalArrangement = Arrangement.spacedBy(sizing.spacing(10.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onFollow,
                    modifier = Modifier.weight(1f),
                    // Suivre une tournée vide n'aurait rien à annoncer.
                    enabled = plan.steps.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(sizing.component(18.dp))
                    )
                    Text(
                        text = stringResource(R.string.trips_view_follow),
                        fontSize = sizing.text(14.sp),
                        modifier = Modifier.padding(start = sizing.spacing(6.dp))
                    )
                }
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(sizing.component(18.dp))
                    )
                    Text(
                        text = stringResource(R.string.trips_view_edit),
                        fontSize = sizing.text(14.sp),
                        modifier = Modifier.padding(start = sizing.spacing(6.dp))
                    )
                }
            }
        }
    }
}

/** « 8 étapes - 42,3 km - 1 h 05 - 3/8 relevées », en n'affichant que ce qui est connu. */
@Composable
private fun tripViewSummary(plan: TripPlan, distanceUnit: Int): String {
    val parts = mutableListOf(
        pluralStringResource(R.plurals.trips_steps, plan.steps.size, plan.steps.size)
    )
    if (plan.steps.size >= 2) {
        parts += formatSiteDistanceMeters(plan.totalDistanceMeters(), distanceUnit)
        parts += formatTripDuration(
            seconds = plan.totalDurationWithStopsSeconds(),
            hourLabel = stringResource(R.string.trips_duration_hour_short),
            minuteLabel = stringResource(R.string.trips_duration_minute_short)
        )
        if (!plan.isRouteComplete()) parts += stringResource(R.string.trips_route_incomplete)
    }
    if (plan.visitedCount() > 0) {
        parts += stringResource(R.string.trips_progress, plan.visitedCount(), plan.relevantStepCount())
    }
    return parts.joinToString(" - ")
}
