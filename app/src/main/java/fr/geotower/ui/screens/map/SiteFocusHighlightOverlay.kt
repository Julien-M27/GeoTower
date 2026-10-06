package fr.geotower.ui.screens.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.os.SystemClock
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.sin

/**
 * Calque d'animation temporaire pour mettre visuellement en valeur un site ou support sur la carte
 * (lors d'un clic sur « Ouvrir la carte »).
 *
 * Émet des ondes circulaires lumineuses (effet radar/ripple) qui émanent du contour du cercle
 * représentant le site, sans point central, pendant environ 2,6 secondes,
 * puis se retire automatiquement de la carte.
 */
class SiteFocusHighlightOverlay(
    private val mapView: MapView,
    val targetPoint: GeoPoint,
    private val primaryColor: Int
) : Overlay() {

    private val startTime = SystemClock.elapsedRealtime()
    private val density = mapView.context.resources.displayMetrics.density
    private val screenPoint = Point()

    // Rayon du cercle du marqueur de site sur l'écran (environ 21dp)
    private val baseCircleRadius = 21f * density

    // Halo lumineux extérieur de l'onde
    private val waveGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    // Liseré blanc de contraste pour une lisibilité optimale sur fond sombre, clair ou satellite
    private val waveOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }

    // Trait principal coloré de l'onde
    private val waveStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    // Pulsation discrète sur le pourtour même du cercle du site (point de départ des ondes)
    private val circleRimGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas, projection: Projection) {
        val elapsed = SystemClock.elapsedRealtime() - startTime
        if (elapsed >= TOTAL_DURATION_MS) {
            mapView.post {
                mapView.overlays.remove(this)
                mapView.invalidate()
            }
            return
        }

        projection.toPixels(targetPoint, screenPoint)
        val cx = screenPoint.x.toFloat()
        val cy = screenPoint.y.toFloat()

        val maxRadius = baseCircleRadius + MAX_EXPAND_DP * density + 8f * density
        if (cx + maxRadius < 0 || cx - maxRadius > canvas.width ||
            cy + maxRadius < 0 || cy - maxRadius > canvas.height
        ) {
            mapView.postInvalidateOnAnimation()
            return
        }

        val baseRed = Color.red(primaryColor)
        val baseGreen = Color.green(primaryColor)
        val baseBlue = Color.blue(primaryColor)

        val globalFadeOut = if (elapsed > FADE_OUT_START_MS) {
            ((TOTAL_DURATION_MS - elapsed).toFloat() / (TOTAL_DURATION_MS - FADE_OUT_START_MS)).coerceIn(0f, 1f)
        } else {
            1f
        }

        // --- 1. Ondes circulaires qui émanent du contour du cercle ---
        val maxExpandPx = MAX_EXPAND_DP * density
        for (i in 0 until WAVE_COUNT) {
            val waveStart = i * WAVE_INTERVAL_MS
            val waveElapsed = elapsed - waveStart
            if (waveElapsed in 0L..WAVE_DURATION_MS) {
                val progress = waveElapsed.toFloat() / WAVE_DURATION_MS
                // Décélération sinusoïdale douce : l'onde démarre calmement et se propage sans à-coup
                val ease = sin(progress * (Math.PI / 2)).toFloat()

                // L'onde part exactement du cercle et s'étend vers l'extérieur
                val radius = baseCircleRadius + ease * maxExpandPx
                val alphaFraction = (1f - ease) * globalFadeOut

                val strokeWidth = (3.5f - ease * 1.8f).coerceAtLeast(1.4f) * density
                val glowWidth = strokeWidth + 4f * density
                val outlineWidth = strokeWidth + 2f * density

                val glowAlpha = (alphaFraction * 75f).toInt().coerceIn(0, 255)
                val outlineAlpha = (alphaFraction * 140f).toInt().coerceIn(0, 255)
                val strokeAlpha = (alphaFraction * 230f).toInt().coerceIn(0, 255)

                // 1. Halo doux
                waveGlowPaint.strokeWidth = glowWidth
                waveGlowPaint.color = Color.argb(glowAlpha, baseRed, baseGreen, baseBlue)
                canvas.drawCircle(cx, cy, radius, waveGlowPaint)

                // 2. Liseré blanc contrastant
                waveOutlinePaint.strokeWidth = outlineWidth
                waveOutlinePaint.alpha = outlineAlpha
                canvas.drawCircle(cx, cy, radius, waveOutlinePaint)

                // 3. Onde principale colorée
                waveStrokePaint.strokeWidth = strokeWidth
                waveStrokePaint.color = Color.argb(strokeAlpha, baseRed, baseGreen, baseBlue)
                canvas.drawCircle(cx, cy, radius, waveStrokePaint)
            }
        }

        // --- 2. Léger halo pulsant sur le pourtour même du cercle ---
        val rimPulse = (0.5f + 0.5f * sin(elapsed / 380.0).toFloat()) * globalFadeOut
        val rimAlpha = (rimPulse * 160f).toInt().coerceIn(0, 255)
        if (rimAlpha > 0) {
            circleRimGlowPaint.strokeWidth = 2.5f * density
            circleRimGlowPaint.color = Color.argb(rimAlpha, baseRed, baseGreen, baseBlue)
            canvas.drawCircle(cx, cy, baseCircleRadius, circleRimGlowPaint)
        }

        mapView.postInvalidateOnAnimation()
    }

    companion object {
        private const val WAVE_COUNT = 5
        private const val TOTAL_DURATION_MS = 6600L
        private const val WAVE_DURATION_MS = 3200L
        private const val WAVE_INTERVAL_MS = 850L
        private const val FADE_OUT_START_MS = 5600L
        private const val MAX_EXPAND_DP = 68f
    }
}
