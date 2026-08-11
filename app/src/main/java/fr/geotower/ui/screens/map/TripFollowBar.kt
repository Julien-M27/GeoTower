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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.R
import fr.geotower.data.trip.TripFollowStatus
import fr.geotower.data.trip.TripPlan
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.formatSiteDistanceMeters

/**
 * Barre du **suivi** de tournée, distincte de celle de l'édition : sur le terrain on ne pose plus
 * de points, on veut savoir où aller et cocher ce qui est fait.
 */
@Composable
fun TripFollowBar(
    plan: TripPlan,
    status: TripFollowStatus?,
    distanceUnit: Int,
    onCheckNext: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val nextIndex = status?.nextStepIndex
    val nextStep = nextIndex?.let { plan.steps.getOrNull(it) }

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
            if (status?.isOffRoute == true) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = sizing.spacing(4.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(sizing.component(16.dp))
                    )
                    Text(
                        text = stringResource(R.string.trips_follow_off_route),
                        fontSize = sizing.text(12.sp),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = sizing.spacing(6.dp))
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            nextStep == null -> stringResource(R.string.trips_follow_done)
                            else -> stringResource(
                                R.string.trips_follow_next,
                                nextStep.label.ifBlank {
                                    stringResource(R.string.trips_step_fallback_pattern, nextIndex + 1)
                                }
                            )
                        },
                        fontSize = sizing.text(15.sp),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = followDetail(plan, status, distanceUnit),
                        fontSize = sizing.text(12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (nextStep != null) {
                    IconButton(onClick = onCheckNext) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.trips_follow_check),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(sizing.component(22.dp))
                        )
                    }
                }
                IconButton(onClick = onStop) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.trips_follow_finish),
                        modifier = Modifier.size(sizing.component(22.dp))
                    )
                }
            }
        }
    }
}

/** « 1,2 km — 3/8 relevées — reste 14,5 km », sans annoncer ce qu'on ne sait pas. */
@Composable
private fun followDetail(plan: TripPlan, status: TripFollowStatus?, distanceUnit: Int): String {
    val parts = mutableListOf<String>()
    status?.distanceToNextMeters?.let { parts += formatSiteDistanceMeters(it, distanceUnit) }
    parts += stringResource(R.string.trips_progress, plan.visitedCount(), plan.relevantStepCount())
    if (status != null && status.nextStepIndex != null) {
        parts += stringResource(
            R.string.trips_follow_remaining,
            formatSiteDistanceMeters(status.remainingDistanceMeters, distanceUnit)
        )
    }
    return parts.joinToString(" - ")
}
