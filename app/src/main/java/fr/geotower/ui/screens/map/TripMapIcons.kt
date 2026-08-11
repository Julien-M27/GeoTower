package fr.geotower.ui.screens.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/**
 * Pastille numérotée d'une étape de trajet.
 *
 * Le numéro est l'information utile sur une tournée : sans lui, une dizaine de points identiques ne
 * disent pas dans quel ordre les faire. Une étape déjà relevée est remplie en vert.
 */
fun createTripStepIcon(
    context: Context,
    number: Int,
    visited: Boolean,
    accentColor: Int = TRIP_STEP_COLOR
): Drawable {
    val density = context.resources.displayMetrics.density
    val diameter = (26f * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(diameter, diameter, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = diameter / 2f

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (visited) VISITED_COLOR else accentColor
        style = Paint.Style.FILL
    }
    canvas.drawCircle(radius, radius, radius - density, fill)

    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    canvas.drawCircle(radius, radius, radius - density, outline)

    val label = number.toString()
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        // Trois chiffres tiennent encore dans la pastille ; au-delà on rétrécit un peu plutôt que
        // de déborder.
        textSize = if (label.length >= 3) 10f * density else 12f * density
    }
    val baseline = radius - (text.descent() + text.ascent()) / 2f
    canvas.drawText(label, radius, baseline, text)

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Petit chevron posé sur le tracé pour dire dans quel sens la tournée se parcourt.
 *
 * L'angle est **gravé dans l'image** plutôt que confié à `Marker.rotation` : la convention de signe
 * d'osmdroid n'est utilisée nulle part ailleurs dans le projet, et une flèche à l'envers serait pire
 * que pas de flèche du tout. Le marqueur est posé « à plat », donc il tourne avec la carte.
 *
 * Les images sont mises en cache par tranche de [ARROW_BEARING_BUCKET_DEGREES] : un tracé sinueux
 * demande des dizaines de flèches, mais une poignée d'angles distincts suffit à l'œil.
 */
fun createTripArrowIcon(context: Context, bearingDegrees: Double): Drawable {
    val density = context.resources.displayMetrics.density
    val diameter = (16f * density).toInt().coerceAtLeast(1)
    val bucket = (
        (Math.round(bearingDegrees / ARROW_BEARING_BUCKET_DEGREES).toInt() *
            ARROW_BEARING_BUCKET_DEGREES) % 360 + 360
        ) % 360

    val bitmap = arrowBitmaps.getOrPut(bucket * 10_000 + diameter) {
        buildArrowBitmap(diameter, bucket.toFloat(), density)
    }
    return BitmapDrawable(context.resources, bitmap)
}

private const val ARROW_BEARING_BUCKET_DEGREES = 10

/** Alimenté depuis le fil principal uniquement (le rafraîchissement des calques de la carte). */
private val arrowBitmaps = mutableMapOf<Int, Bitmap>()

private fun buildArrowBitmap(diameter: Int, bearingDegrees: Float, density: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(diameter, diameter, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = diameter / 2f
    val halfWidth = diameter * 0.26f
    val halfHeight = diameter * 0.24f

    // Chevron pointant vers le haut, puis le canevas tourne du cap voulu : le nord de l'image
    // devient la direction de marche.
    val chevron = android.graphics.Path().apply {
        moveTo(center - halfWidth, center + halfHeight)
        lineTo(center, center - halfHeight)
        lineTo(center + halfWidth, center + halfHeight)
    }

    canvas.save()
    canvas.rotate(bearingDegrees, center, center)
    // Liseré sombre dessous : la flèche reste lisible quand elle déborde du tracé dans un virage.
    canvas.drawPath(
        chevron,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 3.6f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    )
    canvas.drawPath(
        chevron,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    )
    canvas.restore()
    return bitmap
}

/**
 * Bleu du tracé et des pastilles. Fixe et non repris du thème : la pastille se pose sur un fond de
 * carte, pas sur le fond de l'app, et doit rester lisible en clair comme en sombre.
 */
val TRIP_STEP_COLOR = Color.rgb(25, 118, 210)

private val VISITED_COLOR = Color.rgb(46, 125, 50)
