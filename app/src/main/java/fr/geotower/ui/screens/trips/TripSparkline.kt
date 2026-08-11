package fr.geotower.ui.screens.trips

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import fr.geotower.data.trip.TripPlan
import kotlin.math.ceil
import kotlin.math.cos

/**
 * La **forme** du trajet en miniature, dessinée au trait sur la carte de la liste.
 *
 * Ce n'est volontairement pas une mini-carte : rendre des tuiles hors écran pour chaque ligne d'une
 * liste coûterait cher et demanderait un tout autre mécanisme. Le tracé seul suffit à reconnaître
 * une tournée d'un coup d'œil, et il se dessine à partir de ce qui est déjà en mémoire.
 */
@Composable
fun TripSparkline(plan: TripPlan, modifier: Modifier = Modifier) {
    val stroke = MaterialTheme.colorScheme.primary
    val startDot = MaterialTheme.colorScheme.tertiary
    // Recalculé seulement quand le trajet bouge : décoder les polylines de tous les segments à
    // chaque recomposition ferait ramer le défilement.
    val points = remember(plan.id, plan.updatedAtMillis) { tripShapePoints(plan) }

    Canvas(modifier) {
        if (points.size < 2) return@Canvas

        val padding = size.minDimension * 0.12f
        val usable = size.minDimension - padding * 2
        val offsetX = (size.width - usable) / 2f
        val offsetY = (size.height - usable) / 2f

        fun place(point: FloatArray) = Offset(
            x = offsetX + point[0] * usable,
            y = offsetY + point[1] * usable
        )

        val path = Path().apply {
            val first = place(points.first())
            moveTo(first.x, first.y)
            points.drop(1).forEach { point ->
                val placed = place(point)
                lineTo(placed.x, placed.y)
            }
        }

        drawPath(
            path = path,
            color = stroke,
            style = Stroke(
                width = size.minDimension * 0.07f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        drawCircle(color = startDot, radius = size.minDimension * 0.07f, center = place(points.first()))
    }
}

/** Au-delà, la miniature ne gagne rien en lisibilité et le décodage coûte pour rien. */
private const val MAX_SPARKLINE_POINTS = 240

/**
 * Le tracé ramené dans un carré `[0, 1] × [0, 1]`, nord en haut, **proportions conservées** : une
 * tournée en ligne droite doit ressortir comme une ligne droite, pas étirée aux bords du cadre.
 */
internal fun tripShapePoints(plan: TripPlan): List<FloatArray> {
    val raw = ArrayList<DoubleArray>()
    plan.legPairs().forEach { (fromIndex, toIndex) ->
        val computed = plan.legBetween(fromIndex, toIndex)?.points()?.takeIf { it.size >= 2 }
        if (computed != null) {
            raw += computed
        } else {
            raw += doubleArrayOf(plan.steps[fromIndex].latitude, plan.steps[fromIndex].longitude)
            raw += doubleArrayOf(plan.steps[toIndex].latitude, plan.steps[toIndex].longitude)
        }
    }
    // Trajet d'une seule étape, ou pas encore relié : on montre au moins les points posés.
    if (raw.isEmpty()) plan.steps.forEach { raw += doubleArrayOf(it.latitude, it.longitude) }
    if (raw.size < 2) return emptyList()

    val stride = ceil(raw.size / MAX_SPARKLINE_POINTS.toDouble()).toInt().coerceAtLeast(1)
    val sampled = raw.filterIndexed { index, _ -> index % stride == 0 || index == raw.lastIndex }

    val minLatitude = sampled.minOf { it[0] }
    val maxLatitude = sampled.maxOf { it[0] }
    val minLongitude = sampled.minOf { it[1] }
    val maxLongitude = sampled.maxOf { it[1] }

    // Un degré de longitude est plus court qu'un degré de latitude dès qu'on quitte l'équateur.
    // Sans ce facteur, une tournée nord-sud ressortirait écrasée en largeur.
    val longitudeScale = cos(Math.toRadians((minLatitude + maxLatitude) / 2.0)).coerceAtLeast(0.1)
    val width = (maxLongitude - minLongitude) * longitudeScale
    val height = maxLatitude - minLatitude
    val span = maxOf(width, height).coerceAtLeast(1e-9)

    return sampled.map { point ->
        val x = ((point[1] - minLongitude) * longitudeScale - width / 2.0) / span + 0.5
        // Latitude croissante = vers le nord = vers le haut de l'écran, donc y décroissant.
        val y = 0.5 - ((point[0] - minLatitude) - height / 2.0) / span
        floatArrayOf(x.toFloat(), y.toFloat())
    }
}
