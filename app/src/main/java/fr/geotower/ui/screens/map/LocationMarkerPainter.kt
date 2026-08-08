package fr.geotower.ui.screens.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/**
 * Dessin du repère « ma position » (rond aux couleurs du thème + trait et flèche de cap).
 *
 * Extrait du calque osmdroid pour être partagé avec la couche de rendu fluide, qui peint le même
 * repère au-dessus de la carte : les deux chemins doivent donner un résultat au pixel près, sans
 * quoi activer ou couper le lissage ferait « changer » le curseur.
 *
 * Les pinceaux et le chemin de la flèche sont construits une fois pour toutes : ce code tourne à
 * chaque image quand le lissage est actif, il ne doit rien allouer.
 */
class LocationMarkerPainter(density: Float, primaryColor: Int) {

    private val radius = 18f * density
    private val stemLength = 35f * density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = primaryColor
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = primaryColor
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 5f * density
    }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val arrowHead = Path().apply {
        val tipY = -(radius + stemLength + 15f)
        val baseY = -(radius + stemLength - 5f)
        moveTo(0f, tipY)
        lineTo(-12f, baseY)
        lineTo(12f, baseY)
        close()
    }

    fun draw(canvas: Canvas, x: Float, y: Float, rotationDegrees: Float, showDirection: Boolean) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotationDegrees)

        canvas.drawCircle(0f, 0f, radius, fillPaint)
        canvas.drawCircle(0f, 0f, radius * 0.65f, whitePaint)
        canvas.drawCircle(0f, 0f, radius * 0.35f, fillPaint)

        if (showDirection) {
            canvas.drawLine(0f, -radius, 0f, -(radius + stemLength), strokePaint)
            canvas.drawPath(arrowHead, fillPaint)
        }

        canvas.restore()
    }
}
