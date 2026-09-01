package fr.geotower.ui.screens.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.car.app.model.CarIcon
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.OperatorLogos
import java.util.Locale
import kotlin.math.roundToInt

private const val OPERATOR_GRID_SIZE = 224
private const val OPERATOR_GRID_GAP = 8
private const val OPERATOR_TILE_RADIUS = 18f
private const val OPERATOR_LOGO_INSET = 10

/**
 * Une seule image est autorisée dans une ligne Android Auto. On compose donc ici le même carré
 * 2x2 que celui de l'écran téléphone, afin de garder les logos visibles sans allonger la liste.
 */
internal fun carOperatorGridIcon(context: android.content.Context, rawOperators: String): CarIcon {
    val keys = OperatorColors.orderedKeys
        .filter { it in OperatorColors.keysFor(rawOperators) }
        .take(4)

    if (keys.isEmpty()) return CarIcon.APP_ICON

    return runCatching {
        val bitmap = Bitmap.createBitmap(
            OPERATOR_GRID_SIZE,
            OPERATOR_GRID_SIZE,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cellSize = (OPERATOR_GRID_SIZE - OPERATOR_GRID_GAP) / 2

        repeat(4) { index ->
            val key = keys.getOrNull(index)
            val left = (index % 2) * (cellSize + OPERATOR_GRID_GAP)
            val top = (index / 2) * (cellSize + OPERATOR_GRID_GAP)
            val tile = RectF(
                left.toFloat(),
                top.toFloat(),
                (left + cellSize).toFloat(),
                (top + cellSize).toFloat()
            )

            val spec = key?.let(OperatorColors::specForKey)
            val logoRes = key?.let(OperatorLogos::drawableRes)
            paint.color = when {
                // Les PNG de certains opérateurs ont déjà leur propre fond. Ajouter un carré blanc
                // ici créait une bordure visible autour des autres logos (notamment Orange/SFR).
                logoRes != null -> Color.TRANSPARENT
                spec != null -> withAlpha(spec.colorArgb.toInt(), 0x26)
                else -> 0x14000000
            }
            canvas.drawRoundRect(tile, OPERATOR_TILE_RADIUS, OPERATOR_TILE_RADIUS, paint)

            if (logoRes != null) {
                ContextCompat.getDrawable(context, logoRes)?.let { drawable ->
                    drawCenteredDrawable(canvas, drawable, left, top, cellSize)
                }
            } else if (spec != null) {
                paint.color = spec.colorArgb.toInt()
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = 54f
                paint.isFakeBoldText = true
                canvas.drawText(
                    spec.label.take(1).uppercase(Locale.ROOT),
                    left + cellSize / 2f,
                    top + cellSize / 2f - (paint.ascent() + paint.descent()) / 2f,
                    paint
                )
                paint.isFakeBoldText = false
            }
        }

        CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
    }.getOrElse {
        // Un logo défectueux ne doit jamais empêcher l'affichage de toute la liste voiture.
        carLog("Impossible de composer les logos opérateurs : ${it.javaClass.simpleName}")
        CarIcon.APP_ICON
    }
}

private fun drawCenteredDrawable(
    canvas: Canvas,
    drawable: Drawable,
    left: Int,
    top: Int,
    cellSize: Int
) {
    val intrinsicWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
    val intrinsicHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
    val availableSize = (cellSize - OPERATOR_LOGO_INSET * 2).coerceAtLeast(1)
    val scale = minOf(
        availableSize.toFloat() / intrinsicWidth,
        availableSize.toFloat() / intrinsicHeight
    )
    val width = (intrinsicWidth * scale).roundToInt().coerceAtLeast(1)
    val height = (intrinsicHeight * scale).roundToInt().coerceAtLeast(1)
    val bounds = Rect(
        left + (cellSize - width) / 2,
        top + (cellSize - height) / 2,
        left + (cellSize - width) / 2 + width,
        top + (cellSize - height) / 2 + height
    )
    drawable.mutate().bounds = bounds
    drawable.draw(canvas)
}

private fun withAlpha(color: Int, alpha: Int): Int {
    return Color.argb(
        alpha,
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )
}
