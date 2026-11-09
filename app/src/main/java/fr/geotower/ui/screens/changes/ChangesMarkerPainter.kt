package fr.geotower.ui.screens.changes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import fr.geotower.data.models.ChangeType
import fr.geotower.utils.OperatorColors

object ChangesMarkerPainter {

    private val markerCache = LruCache<String, BitmapDrawable>(400)

    private const val PIE_OUTLINE_UNITS = 3.2f

    fun createMarker(
        context: Context,
        operators: List<String>,
        changeType: ChangeType,
        satelliteContrast: Boolean = false
    ): BitmapDrawable {
        val opsSignature = operators.sorted().joinToString(",")
        val cacheKey = "chg_marker|$opsSignature|${changeType.name}|$satelliteContrast"

        markerCache.get(cacheKey)?.let { return it }

        val density = context.resources.displayMetrics.density
        // Alignement exact du diamètre sur celui des clusters (45 dp)
        val targetSize = (48 * density).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val scale = targetSize / 200f
        canvas.scale(scale, scale)

        val size = 200f
        val center = size / 2f
        val pieRadius = 94f // 2 * 94 / 200 * 48dp = 45.12dp (diamètre identique aux clusters de 45dp)
        val pieOutlineUnits = PIE_OUTLINE_UNITS * (pieRadius / 45f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isAntiAlias = true
        }

        fun colorForOperator(op: String): Int {
            val key = OperatorColors.keyFor(op) ?: op.trim().uppercase()
            return OperatorColors.specForKey(key)?.colorHex?.let(android.graphics.Color::parseColor)
                ?: fallbackColorFor(key)
        }

        val opsOnSite = if (operators.isEmpty()) listOf("AUTRE") else operators

        // Liseré contrasté extérieur (si mode satellite ou demandé)
        if (satelliteContrast) {
            val outlineRect = RectF(
                center - pieRadius - pieOutlineUnits,
                center - pieRadius - pieOutlineUnits,
                center + pieRadius + pieOutlineUnits,
                center + pieRadius + pieOutlineUnits
            )
            paint.style = Paint.Style.FILL
            drawSlices(canvas, outlineRect, opsOnSite, paint) { op ->
                contrastOutline(colorForOperator(op))
            }
        }

        // Camembert des opérateurs
        paint.style = Paint.Style.FILL
        val pieRect = RectF(center - pieRadius, center - pieRadius, center + pieRadius, center + pieRadius)
        drawSlices(canvas, pieRect, opsOnSite, paint, ::colorForOperator)

        // Disque intérieur blanc
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(center, center, pieRadius * 0.72f, paint)

        // Disque central gris très clair pour faire ressortir l'icône
        paint.color = android.graphics.Color.parseColor("#F5F7FA")
        canvas.drawCircle(center, center, pieRadius * 0.65f, paint)

        // Tracé du pictogramme central selon le changement (agrandi à 0.70f pour une visibilité nette)
        drawCenterIcon(context, canvas, center, pieRadius * 0.70f, changeType)

        val drawable = BitmapDrawable(context.resources, bitmap)
        markerCache.put(cacheKey, drawable)
        return drawable
    }

    private fun drawSlices(
        canvas: Canvas,
        rect: RectF,
        operators: List<String>,
        paint: Paint,
        colorProvider: (String) -> Int
    ) {
        val sliceAngle = 360f / operators.size
        var startAngle = -90f // Démarrage à 12h

        operators.forEach { op ->
            paint.color = colorProvider(op)
            canvas.drawArc(rect, startAngle, sliceAngle, true, paint)
            startAngle += sliceAngle
        }
    }

    private fun drawCenterIcon(
        context: Context,
        canvas: Canvas,
        center: Float,
        iconBoxSize: Float,
        changeType: ChangeType
    ) {
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val u = iconBoxSize / 24f // Système de coordonnées relatif 24x24

        when (changeType) {
            ChangeType.ACTIVATION, ChangeType.STATUS_CHANGED -> {
                drawVector(
                    context = context,
                    canvas = canvas,
                    drawableId = fr.geotower.R.drawable.ic_change_activation,
                    centerX = center,
                    centerY = center,
                    size = iconBoxSize * 1.15f,
                    tintColor = android.graphics.Color.BLACK
                )
            }

            ChangeType.SITE_ADDED, ChangeType.SYSTEM_ADDED -> {
                drawVector(
                    context = context,
                    canvas = canvas,
                    drawableId = fr.geotower.R.drawable.ic_change_ajout,
                    centerX = center,
                    centerY = center,
                    size = iconBoxSize * 1.15f,
                    tintColor = android.graphics.Color.BLACK
                )
            }

            ChangeType.EXTINCTION, ChangeType.SYSTEM_REMOVED -> {
                drawVector(
                    context = context,
                    canvas = canvas,
                    drawableId = fr.geotower.R.drawable.ic_change_extinction,
                    centerX = center,
                    centerY = center,
                    size = iconBoxSize * 1.15f,
                    tintColor = android.graphics.Color.BLACK
                )
            }

            ChangeType.SITE_REMOVED -> {
                drawVector(
                    context = context,
                    canvas = canvas,
                    drawableId = fr.geotower.R.drawable.ic_change_suppression,
                    centerX = center,
                    centerY = center,
                    size = iconBoxSize * 1.25f,
                    tintColor = android.graphics.Color.BLACK
                )
            }

            ChangeType.OPERATOR_ADDED -> {
                // 👤 Silhouette nouvel opérateur
                fillPaint.color = android.graphics.Color.parseColor("#0D47A1") // Bleu profond
                // Tête
                canvas.drawCircle(center, center - 4f * u, 3.8f * u, fillPaint)
                // Épaules / buste
                val body = Path().apply {
                    moveTo(center - 7f * u, center + 8f * u)
                    quadTo(center - 6f * u, center + 1f * u, center, center + 1f * u)
                    quadTo(center + 6f * u, center + 1f * u, center + 7f * u, center + 8f * u)
                    close()
                }
                canvas.drawPath(body, fillPaint)
            }

            ChangeType.MOVED -> {
                // ↔ Double flèche horizontale
                strokePaint.color = android.graphics.Color.parseColor("#455A64")
                strokePaint.strokeWidth = 3.2f * u
                val arrowPath = Path().apply {
                    // Ligne centrale
                    moveTo(center - 8f * u, center)
                    lineTo(center + 8f * u, center)
                    // Pointe gauche
                    moveTo(center - 4f * u, center - 3.5f * u)
                    lineTo(center - 8f * u, center)
                    lineTo(center - 4f * u, center + 3.5f * u)
                    // Pointe droite
                    moveTo(center + 4f * u, center - 3.5f * u)
                    lineTo(center + 8f * u, center)
                    lineTo(center + 4f * u, center + 3.5f * u)
                }
                canvas.drawPath(arrowPath, strokePaint)
            }

            ChangeType.OPERATOR_REMOVED, ChangeType.UNKNOWN -> {
                // Picto d'information / point central
                fillPaint.color = android.graphics.Color.parseColor("#455A64")
                canvas.drawCircle(center, center, 4.5f * u, fillPaint)
            }
        }
    }

    private fun drawVector(
        context: Context,
        canvas: Canvas,
        drawableId: Int,
        centerX: Float,
        centerY: Float,
        size: Float,
        tintColor: Int
    ) {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, drawableId)?.mutate() ?: return
        val half = size / 2f
        drawable.setBounds(
            (centerX - half).toInt(),
            (centerY - half).toInt(),
            (centerX + half).toInt(),
            (centerY + half).toInt()
        )
        drawable.colorFilter = android.graphics.PorterDuffColorFilter(tintColor, android.graphics.PorterDuff.Mode.SRC_IN)
        drawable.draw(canvas)
    }

    private fun contrastOutline(color: Int): Int {
        val r = android.graphics.Color.red(color)
        val g = android.graphics.Color.green(color)
        val b = android.graphics.Color.blue(color)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return if (luminance > 0.5) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }

    private val FALLBACK_PALETTE = intArrayOf(
        android.graphics.Color.parseColor("#9C27B0"), // Violet
        android.graphics.Color.parseColor("#009688"), // Teal
        android.graphics.Color.parseColor("#3F51B5"), // Indigo
        android.graphics.Color.parseColor("#E91E63"), // Rose vif
        android.graphics.Color.parseColor("#FF5722"), // Orange profond
        android.graphics.Color.parseColor("#4CAF50"), // Vert
        android.graphics.Color.parseColor("#795548"), // Marron
        android.graphics.Color.parseColor("#607D8B")  // Gris-bleu
    )

    fun fallbackColorFor(op: String): Int {
        val hash = kotlin.math.abs(op.hashCode())
        return FALLBACK_PALETTE[hash % FALLBACK_PALETTE.size]
    }
}
