package fr.geotower.ui.screens.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.LowPriority
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import fr.geotower.data.api.RouteApi
import fr.geotower.data.trip.TripPlan
import fr.geotower.data.trip.formatTripDuration
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.formatSiteDistanceMeters

/**
 * Barre du mode planificateur, en bas de la carte : le résumé de la tournée en cours et les
 * quelques gestes qui ne se font pas en touchant la carte.
 *
 * Il n'y a pas de bouton « enregistrer » : chaque geste écrit dans le trajet. « Terminer » ne fait
 * que quitter le mode.
 */
@Composable
fun TripPlannerBar(
    plan: TripPlan,
    busy: Boolean,
    distanceUnit: Int,
    onToggleProfile: () -> Unit,
    onToggleReturnToStart: () -> Unit,
    onOptimize: () -> Unit,
    onUndo: () -> Unit,
    onFinish: () -> Unit,
    /** Numéro qu'aura l'étape insérée, quand un point d'insertion est armé. */
    insertAfterNumber: Int? = null,
    modifier: Modifier = Modifier
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val walking = plan.profile == RouteApi.PROFILE_PEDESTRIAN

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            // La surface va jusqu'au bas de l'écran ; c'est ici que le contenu s'écarte de la barre
            // système, sinon les libellés passeraient sous les boutons de navigation.
            modifier = Modifier
                .navigationBarsPadding()
                .padding(
                    horizontal = sizing.spacing(14.dp),
                    vertical = sizing.spacing(10.dp)
                )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plan.name.ifBlank { stringResource(R.string.trips_untitled) },
                    fontSize = sizing.text(15.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(sizing.component(16.dp)),
                        strokeWidth = 2.dp
                    )
                }
            }

            Text(
                text = when {
                    // Une insertion armée change ce que fera le prochain toucher : le dire, sinon
                    // le point atterrit au milieu de la tournée sans prévenir.
                    insertAfterNumber != null ->
                        stringResource(R.string.trips_planner_insert_hint, insertAfterNumber)
                    plan.steps.isEmpty() -> stringResource(R.string.trips_planner_hint)
                    else -> plannerSummary(plan, distanceUnit)
                },
                fontSize = sizing.text(12.sp),
                color = if (insertAfterNumber != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = sizing.spacing(2.dp))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = sizing.spacing(4.dp)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlannerAction(
                    icon = if (walking) Icons.Default.DirectionsWalk else Icons.Default.DirectionsCar,
                    labelRes = if (walking) {
                        R.string.trips_planner_profile_pedestrian
                    } else {
                        R.string.trips_planner_profile_car
                    },
                    onClick = onToggleProfile
                )
                PlannerAction(
                    icon = Icons.Default.Loop,
                    labelRes = R.string.trips_planner_return_to_start,
                    highlighted = plan.returnToStart,
                    onClick = onToggleReturnToStart
                )
                PlannerAction(
                    icon = Icons.Default.LowPriority,
                    labelRes = R.string.trips_planner_optimize,
                    enabled = plan.steps.size >= 3 && !busy,
                    onClick = onOptimize
                )
                PlannerAction(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    labelRes = R.string.trips_planner_undo,
                    enabled = plan.steps.isNotEmpty(),
                    onClick = onUndo
                )
                PlannerAction(
                    icon = Icons.Default.Check,
                    labelRes = R.string.trips_planner_finish,
                    onClick = onFinish
                )
            }
        }
    }
}

@Composable
private fun PlannerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelRes: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
    highlighted: Boolean = false
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(labelRes),
                modifier = Modifier.size(sizing.component(20.dp)),
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    highlighted -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Text(
            text = stringResource(labelRes),
            fontSize = sizing.text(10.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * Actions sur une étape déjà posée, ouvertes en touchant sa pastille.
 *
 * Le déplacement se fait d'un cran à la fois plutôt qu'au glisser : sur une carte, un glissé sert
 * déjà à se déplacer, et « Optimiser l'ordre » couvre les gros remaniements.
 */
@Composable
fun TripStepActionsDialog(
    stepNumber: Int,
    label: String,
    visited: Boolean,
    /** Photos parties depuis cette étape, et note de terrain : le compte rendu du passage. */
    photosSentCount: Int,
    note: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onInsertAfter: () -> Unit,
    onToggleVisited: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = label.ifBlank {
                    stringResource(R.string.trips_step_fallback_pattern, stepNumber)
                },
                fontSize = sizing.text(17.sp)
            )
        },
        text = {
            Column {
                // Ce que l'étape a produit sur le terrain, avant les actions : c'est ce qu'on vient
                // relire en rouvrant une tournée faite.
                if (photosSentCount > 0) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.trips_arrival_already_sent,
                            photosSentCount,
                            photosSentCount
                        ),
                        fontSize = sizing.text(13.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (note.isNotBlank()) {
                    Text(
                        text = note,
                        fontSize = sizing.text(13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StepActionRow(
                    icon = Icons.Default.KeyboardArrowUp,
                    labelRes = R.string.trips_step_move_up,
                    enabled = canMoveUp,
                    onClick = onMoveUp
                )
                StepActionRow(
                    icon = Icons.Default.KeyboardArrowDown,
                    labelRes = R.string.trips_step_move_down,
                    enabled = canMoveDown,
                    onClick = onMoveDown
                )
                StepActionRow(
                    icon = Icons.Default.AddLocationAlt,
                    labelRes = R.string.trips_step_insert_after,
                    onClick = onInsertAfter
                )
                StepActionRow(
                    icon = Icons.Default.Check,
                    labelRes = if (visited) {
                        R.string.trips_step_mark_not_visited
                    } else {
                        R.string.trips_step_mark_visited
                    },
                    onClick = onToggleVisited
                )
                StepActionRow(
                    icon = Icons.Default.Delete,
                    labelRes = R.string.trips_step_delete,
                    destructive = true,
                    onClick = onDelete
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.appstrings_close)) }
        }
    )
}

@Composable
private fun StepActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelRes: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = sizing.spacing(10.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(sizing.component(20.dp))
        )
        Text(
            text = stringResource(labelRes),
            fontSize = sizing.text(14.sp),
            color = tint,
            modifier = Modifier.padding(start = sizing.spacing(12.dp))
        )
    }
}

/** « 8 étapes - 42,3 km - 1 h 05 », en n'affichant la route que si elle est calculée. */
@Composable
private fun plannerSummary(plan: TripPlan, distanceUnit: Int): String {
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
    return parts.joinToString(" - ")
}
