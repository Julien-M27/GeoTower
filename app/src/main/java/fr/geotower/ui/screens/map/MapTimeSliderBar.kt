package fr.geotower.ui.screens.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.R
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.OperatorColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Statistiques affichees par la barre temporelle :
 * nombre de sites visibles par operateur a la date courante, + sites sans date exploitable.
 */
data class TimeSliderStats(
    val countsByOperator: Map<String, Int>,
    val undated: Int
)

private fun monthIndexOf(dateInt: Int): Int {
    val year = dateInt / 10000
    val month0 = ((dateInt / 100) % 100 - 1).coerceIn(0, 11)
    return year * 12 + month0
}

/** Fin du mois selectionne (jj=31) : un site mis en service durant ce mois est inclus via la comparaison yyyymmdd <=. */
private fun monthIndexToThreshold(monthIndex: Int): Int {
    val year = monthIndex / 12
    val month1 = monthIndex % 12 + 1
    return year * 10000 + month1 * 100 + 31
}

private fun formatMonth(monthIndex: Int): String {
    val cal = Calendar.getInstance()
    cal.set(Calendar.YEAR, monthIndex / 12)
    cal.set(Calendar.MONTH, monthIndex % 12)
    cal.set(Calendar.DAY_OF_MONTH, 1)
    return SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        .format(cal.time)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

/** Libelle "Mois AAAA" pour un seuil yyyymmdd du slider (utilise par l'image de partage). */
fun timeSliderMonthLabel(thresholdInt: Int): String = formatMonth(monthIndexOf(thresholdInt))

/**
 * Barre flottante de la carte : un slider mensuel pour "rejouer" l'apparition des sites par date
 * de mise en service. Borne par [oldestDateInt]..[newestDateInt] (format yyyymmdd).
 * [thresholdInt] null = aucune limite (tout afficher / present). Respecte le style One UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapTimeSliderBar(
    oldestDateInt: Int,
    newestDateInt: Int,
    thresholdInt: Int?,
    countsByOperator: Map<String, Int>,
    undatedCount: Int,
    onThresholdChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalGeoTowerUiStyle.current
    val useOneUi = style.useOneUi
    val sizing = style.sizing
    val accentColor = MaterialTheme.colorScheme.primary

    val minMonth = monthIndexOf(oldestDateInt)
    val maxMonth = monthIndexOf(newestDateInt).coerceAtLeast(minMonth)
    val monthsSpan = (maxMonth - minMonth).coerceAtLeast(1)
    val currentMonth = (thresholdInt?.let { monthIndexOf(it) } ?: maxMonth).coerceIn(minMonth, maxMonth)
    val sliderValue = (currentMonth - minMonth).toFloat()
    val activeFraction = (sliderValue / monthsSpan.toFloat()).coerceIn(0f, 1f)

    Surface(
        // Plafond de largeur : en paysage la barre ne s'etire pas sur tout l'ecran.
        modifier = modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth(),
        shape = if (useOneUi) RoundedCornerShape(28.dp) else RoundedCornerShape(20.dp),
        color = if (useOneUi) style.bubbleColor else MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        border = if (useOneUi) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp))) {
            Text(
                text = formatMonth(currentMonth),
                style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val onValueChange: (Float) -> Unit = { v ->
                val mi = (minMonth + v.roundToInt()).coerceIn(minMonth, maxMonth)
                onThresholdChange(monthIndexToThreshold(mi))
            }

            if (useOneUi) {
                Slider(
                    value = sliderValue,
                    onValueChange = onValueChange,
                    valueRange = 0f..monthsSpan.toFloat(),
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(sizing.component(24.dp))
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                .border(sizing.component(3.dp), accentColor, CircleShape)
                        )
                    },
                    track = { _ ->
                        Canvas(modifier = Modifier.fillMaxWidth().height(sizing.component(14.dp))) {
                            val y = size.height / 2f
                            val stroke = size.height
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.3f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round
                            )
                            val activeX = size.width * activeFraction
                            if (activeX > 0f) {
                                drawLine(
                                    color = accentColor,
                                    start = Offset(0f, y),
                                    end = Offset(activeX, y),
                                    strokeWidth = stroke,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
                )
            } else {
                Slider(
                    value = sliderValue,
                    onValueChange = onValueChange,
                    valueRange = 0f..monthsSpan.toFloat()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OperatorColors.orderedKeys.forEach { key ->
                    val count = countsByOperator[key] ?: 0
                    if (count > 0) {
                        OperatorCountChip(operatorKey = key, count = count)
                    }
                }
                if (undatedCount > 0) {
                    Text(
                        text = stringResource(R.string.appstrings_time_slider_undated, undatedCount),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun OperatorCountChip(operatorKey: String, count: Int) {
    val label = OperatorColors.specForKey(operatorKey)?.label ?: operatorKey
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(Color(OperatorColors.colorIntForKey(operatorKey)), CircleShape)
        )
        Text(
            text = "$label $count",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
