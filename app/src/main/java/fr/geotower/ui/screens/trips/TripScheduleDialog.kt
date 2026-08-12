package fr.geotower.ui.screens.trips

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.R
import fr.geotower.data.trip.TripPlan
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/** Délais de rappel proposés, du plus lointain au plus proche. */
private val REMINDER_CHOICES = listOf(24 * 60, 3 * 60, 60)

/** Temps d'arrêt proposés par étape, en minutes. */
private val STOP_CHOICES = listOf(0, 5, 10, 15, 30)

/** Heure de départ prise par défaut quand on pose une date sans avoir choisi d'heure. */
private const val DEFAULT_DEPARTURE_HOUR = 9

/**
 * Date prévue, rappels et temps d'arrêt d'un trajet.
 *
 * Rien n'est écrit tant que « Valider » n'est pas touché : c'est le seul endroit de la
 * fonctionnalité qui ne s'enregistre pas à chaque geste, parce qu'une date à demi choisie
 * (le jour sans l'heure) n'aurait pas de sens à programmer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScheduleDialog(
    plan: TripPlan,
    onDismiss: () -> Unit,
    /**
     * Ajoute le champ « nom » en tête et intitule la boîte « Enregistrer » : c'est la forme
     * utilisée en quittant l'édition, où l'on nomme et date la tournée d'un seul geste.
     */
    editableName: Boolean = false,
    onConfirm: (
        name: String,
        plannedAtMillis: Long?,
        reminderOffsetsMinutes: List<Int>,
        stopMinutes: Int
    ) -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val locale = LocalConfiguration.current.locales[0]

    var name by remember { mutableStateOf(plan.name) }
    var plannedAt by remember { mutableStateOf(plan.plannedAtMillis) }
    var reminders by remember { mutableStateOf(plan.reminderOffsetsMinutes.toSet()) }
    var stopMinutes by remember { mutableStateOf(plan.stopDurationMinutes) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateLabel = plannedAt
        ?.let { DateFormat.getDateInstance(DateFormat.FULL, locale).format(Date(it)) }
        ?: stringResource(R.string.trips_schedule_no_date)
    val timeLabel = plannedAt
        ?.let { DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(it)) }
        ?: "--:--"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (editableName) R.string.trips_save_dialog_title else R.string.trips_schedule_title
                )
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (editableName) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(80) },
                        label = { Text(stringResource(R.string.trips_rename_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))
                }

                ScheduleRow(
                    icon = Icons.Default.CalendarMonth,
                    label = stringResource(R.string.trips_schedule_date),
                    value = dateLabel,
                    onClick = { showDatePicker = true },
                    // Sans date posée, la valeur est une invite (« Aucune date ») : elle ne doit pas
                    // se lire comme un choix déjà fait.
                    highlighted = plannedAt != null
                )
                Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
                ScheduleRow(
                    icon = Icons.Default.Schedule,
                    label = stringResource(R.string.trips_schedule_time),
                    value = timeLabel,
                    onClick = { if (plannedAt != null) showTimePicker = true },
                    enabled = plannedAt != null
                )

                if (plannedAt != null) {
                    TextButton(onClick = { plannedAt = null }) {
                        Text(stringResource(R.string.trips_schedule_clear))
                    }
                }

                Text(
                    text = stringResource(R.string.trips_schedule_reminders),
                    fontSize = sizing.text(14.sp),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = sizing.spacing(8.dp))
                )
                REMINDER_CHOICES.forEach { offset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = plannedAt != null) {
                                reminders = if (offset in reminders) reminders - offset else reminders + offset
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = offset in reminders,
                            onCheckedChange = {
                                reminders = if (it) reminders + offset else reminders - offset
                            },
                            enabled = plannedAt != null
                        )
                        Text(
                            text = stringResource(reminderLabel(offset)),
                            fontSize = sizing.text(14.sp)
                        )
                    }
                }

                // Dit clairement ce que WorkManager garantit -- et ce qu'il ne garantit pas.
                Text(
                    text = stringResource(R.string.trips_schedule_approximate),
                    fontSize = sizing.text(11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = stringResource(R.string.trips_schedule_stop_duration),
                    fontSize = sizing.text(14.sp),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = sizing.spacing(10.dp))
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(sizing.spacing(6.dp))
                ) {
                    STOP_CHOICES.forEach { minutes ->
                        FilterChip(
                            selected = stopMinutes == minutes,
                            onClick = { stopMinutes = minutes },
                            label = {
                                Text(
                                    text = stringResource(R.string.trips_schedule_stop_minutes, minutes),
                                    fontSize = sizing.text(12.sp)
                                )
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Sans date, les délais de rappel n'ont rien à quoi se rattacher.
                val keptReminders = if (plannedAt == null) emptyList() else reminders.sortedDescending()
                onConfirm(name, plannedAt, keptReminders, stopMinutes)
            }) {
                Text(stringResource(R.string.appstrings_validate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.appstrings_cancel)) }
        }
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = plannedAt)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { plannedAt = withDate(plannedAt, it) }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.appstrings_validate))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.appstrings_cancel))
                }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val current = Calendar.getInstance().apply { timeInMillis = plannedAt ?: System.currentTimeMillis() }
        val state = rememberTimePickerState(
            initialHour = current.get(Calendar.HOUR_OF_DAY),
            initialMinute = current.get(Calendar.MINUTE),
            is24Hour = android.text.format.DateFormat.is24HourFormat(
                androidx.compose.ui.platform.LocalContext.current
            )
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.trips_schedule_time)) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    plannedAt = withTime(plannedAt, state.hour, state.minute)
                    showTimePicker = false
                }) {
                    Text(stringResource(R.string.appstrings_validate))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.appstrings_cancel))
                }
            }
        )
    }
}

/**
 * Un champ à toucher : cadre, icône, intitulé au-dessus de la valeur et chevron.
 *
 * Deux textes côte à côte ne disaient pas qu'on pouvait appuyer — on lisait une fiche, pas un
 * choix. C'est la même forme que les autres panneaux de l'app (cadre arrondi, liseré discret).
 */
@Composable
private fun ScheduleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    highlighted: Boolean = true
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val accent = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        highlighted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(sizing.component(12.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(sizing.component(1.dp), MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = sizing.spacing(14.dp),
                vertical = sizing.spacing(12.dp)
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(sizing.component(20.dp))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = sizing.spacing(12.dp))
            ) {
                Text(
                    text = label,
                    fontSize = sizing.text(11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    fontSize = sizing.text(15.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(sizing.component(18.dp))
            )
        }
    }
}

private fun reminderLabel(offsetMinutes: Int): Int = when (offsetMinutes) {
    24 * 60 -> R.string.trips_schedule_reminder_day
    3 * 60 -> R.string.trips_schedule_reminder_3h
    else -> R.string.trips_schedule_reminder_1h
}

/**
 * Remplace le jour en gardant l'heure. Le sélecteur de Material rend un instant **UTC** à minuit :
 * relire ses champs dans le fuseau local décalerait d'un jour près des bords: on extrait donc
 * l'année, le mois et le jour en UTC avant de les reposer dans le calendrier local.
 */
private fun withDate(currentMillis: Long?, pickedUtcMillis: Long): Long {
    val picked = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickedUtcMillis }
    val result = Calendar.getInstance()
    if (currentMillis != null) {
        result.timeInMillis = currentMillis
    } else {
        result.set(Calendar.HOUR_OF_DAY, DEFAULT_DEPARTURE_HOUR)
        result.set(Calendar.MINUTE, 0)
    }
    result.set(Calendar.YEAR, picked.get(Calendar.YEAR))
    result.set(Calendar.MONTH, picked.get(Calendar.MONTH))
    result.set(Calendar.DAY_OF_MONTH, picked.get(Calendar.DAY_OF_MONTH))
    result.set(Calendar.SECOND, 0)
    result.set(Calendar.MILLISECOND, 0)
    return result.timeInMillis
}

private fun withTime(currentMillis: Long?, hour: Int, minute: Int): Long {
    val result = Calendar.getInstance()
    if (currentMillis != null) result.timeInMillis = currentMillis
    result.set(Calendar.HOUR_OF_DAY, hour)
    result.set(Calendar.MINUTE, minute)
    result.set(Calendar.SECOND, 0)
    result.set(Calendar.MILLISECOND, 0)
    return result.timeInMillis
}
