package fr.geotower.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.ui.screens.emitters.ELEVATION_USER_EYE_HEIGHT_METERS
import fr.geotower.ui.screens.emitters.ElevationProfileDataResult
import fr.geotower.ui.screens.emitters.calculateElevationFresnelObstruction
import fr.geotower.ui.screens.emitters.calculateElevationLineObstruction
import fr.geotower.ui.screens.emitters.elevationFresnelClearanceMeters
import fr.geotower.ui.screens.emitters.elevationLineHeightAt
import fr.geotower.ui.screens.emitters.formatElevationProfileDistance
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

data class ElevationProfileShareTexts(
    val title: String,
    val distance: String,
    val supportHeight: String,
    val supportHeightDetail: String,
    val startAltitude: String,
    val startAltitudeDetail: String,
    val siteAltitude: String,
    val siteAltitudeDetail: String,
    val frequency: String,
    val directLine: String,
    val fresnelZone: String,
    val lineClear: String,
    val lineBlocked: String,
    val fresnelClear: String,
    val fresnelBlocked: String,
    val fresnelExplanation: String,
    val ignSource: String,
    val generatedBy: String,
    val unknown: String
)

private data class ProfileMetric(
    val label: String,
    val value: String,
    val detail: String? = null
)

fun createElevationProfileShareBitmap(
    info: LocalisationEntity,
    profile: ElevationProfileDataResult,
    supportHeightMeters: Double?,
    frequencyMHz: Int,
    forceDarkTheme: Boolean,
    texts: ElevationProfileShareTexts
): Bitmap {
    val width = 1080
    val height = 2300
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val colors = if (forceDarkTheme) {
        ShareProfileColors(
            background = AndroidColor.rgb(16, 17, 20),
            card = AndroidColor.rgb(34, 35, 40),
            text = AndroidColor.WHITE,
            secondaryText = AndroidColor.rgb(186, 188, 196),
            grid = AndroidColor.argb(70, 255, 255, 255),
            terrain = AndroidColor.rgb(86, 171, 118),
            terrainFill = AndroidColor.argb(80, 86, 171, 118),
            signal = AndroidColor.rgb(226, 226, 226),
            fresnel = AndroidColor.argb(65, 109, 159, 255),
            warning = AndroidColor.rgb(255, 178, 88)
        )
    } else {
        ShareProfileColors(
            background = AndroidColor.rgb(247, 248, 250),
            card = AndroidColor.WHITE,
            text = AndroidColor.rgb(22, 24, 29),
            secondaryText = AndroidColor.rgb(92, 96, 106),
            grid = AndroidColor.argb(85, 10, 20, 30),
            terrain = AndroidColor.rgb(37, 123, 76),
            terrainFill = AndroidColor.argb(75, 37, 123, 76),
            signal = AndroidColor.rgb(78, 82, 92),
            fresnel = AndroidColor.argb(55, 62, 117, 255),
            warning = AndroidColor.rgb(176, 97, 0)
        )
    }

    canvas.drawColor(colors.background)

    val titlePaint = textPaint(colors.text, 42f, Typeface.BOLD)
    val subtitlePaint = textPaint(colors.secondaryText, 26f, Typeface.NORMAL)
    val labelPaint = textPaint(colors.secondaryText, 24f, Typeface.NORMAL)
    val valuePaint = textPaint(colors.text, 32f, Typeface.BOLD)
    val smallPaint = textPaint(colors.secondaryText, 22f, Typeface.NORMAL)

    var y = 64f
    canvas.drawText(texts.title, 48f, y, titlePaint)
    y += 42f
    canvas.drawText("${info.operateur ?: texts.unknown} - ${info.idAnfr}", 48f, y, subtitlePaint)

    val chartRect = RectF(48f, 150f, width - 48f, 720f)
    drawRoundedCard(canvas, chartRect, colors.card)
    drawElevationProfileChart(
        canvas = canvas,
        rect = RectF(chartRect.left + 24f, chartRect.top + 24f, chartRect.right - 24f, chartRect.bottom - 30f),
        profile = profile,
        supportHeightMeters = supportHeightMeters ?: 0.0,
        frequencyMHz = frequencyMHz,
        colors = colors
    )

    y = 780f
    val metricCardHeight = 170f
    val metricGap = 20f
    val metricWidth = (width - 96f - metricGap) / 2f
    val startHeightMeters = profile.points.first().elevation + ELEVATION_USER_EYE_HEIGHT_METERS
    val arrivalHeightMeters = supportHeightMeters?.let { profile.points.last().elevation + it }
    val metrics = listOf(
        ProfileMetric(texts.distance, formatElevationProfileDistance(profile.distanceMeters)),
        ProfileMetric(
            label = texts.supportHeight,
            value = supportHeightMeters?.let { "${it.roundToInt()} m" } ?: "--",
            detail = texts.supportHeightDetail
        ),
        ProfileMetric(
            label = texts.startAltitude,
            value = "${startHeightMeters.roundToInt()} m",
            detail = texts.startAltitudeDetail
        ),
        ProfileMetric(
            label = texts.siteAltitude,
            value = arrivalHeightMeters?.let { "${it.roundToInt()} m" } ?: "--",
            detail = texts.siteAltitudeDetail
        ),
        ProfileMetric(texts.frequency, "$frequencyMHz MHz")
    )

    metrics.forEachIndexed { index, metric ->
        val col = index % 2
        val row = index / 2
        val left = 48f + col * (metricWidth + metricGap)
        val top = y + row * (metricCardHeight + metricGap)
        drawRoundedCard(canvas, RectF(left, top, left + metricWidth, top + metricCardHeight), colors.card)
        canvas.drawText(metric.label, left + 26f, top + 45f, labelPaint)
        canvas.drawText(metric.value, left + 26f, top + 94f, valuePaint)
        metric.detail?.let { detail ->
            drawWrappedText(canvas, detail, left + 26f, top + 126f, metricWidth - 52f, smallPaint, 26f, colors.warning)
        }
    }

    val statusTop = y + 3 * (metricCardHeight + metricGap) + 8f
    val supportHeight = supportHeightMeters ?: 0.0
    val lineObstruction = calculateElevationLineObstruction(profile, supportHeight)
    val fresnelObstruction = calculateElevationFresnelObstruction(profile, supportHeight, frequencyMHz)

    val lineStatus = if (lineObstruction <= 0.0) {
        texts.lineClear
    } else {
        "${texts.lineBlocked} (+${String.format(Locale.US, "%.1f", lineObstruction)} m)"
    }
    val fresnelStatus = if (fresnelObstruction <= 0.0) {
        texts.fresnelClear
    } else {
        "${texts.fresnelBlocked} (+${String.format(Locale.US, "%.1f", fresnelObstruction)} m)"
    }

    val statusTextWidth = width - 96f - 56f
    val statusHeight = max(
        270f,
        190f + (wrappedLineCount(lineStatus, statusTextWidth, valuePaint) +
            wrappedLineCount(fresnelStatus, statusTextWidth, valuePaint)) * 38f
    )
    val statusRect = RectF(48f, statusTop, width - 48f, statusTop + statusHeight)
    drawRoundedCard(canvas, statusRect, colors.card)
    var statusY = statusRect.top + 48f
    canvas.drawText(texts.directLine, statusRect.left + 28f, statusY, labelPaint)
    statusY = drawWrappedText(canvas, lineStatus, statusRect.left + 28f, statusY + 42f, statusRect.width() - 56f, valuePaint, 38f, colors.warning)
    statusY += 32f
    canvas.drawText(texts.fresnelZone, statusRect.left + 28f, statusY, labelPaint)
    drawWrappedText(canvas, fresnelStatus, statusRect.left + 28f, statusY + 42f, statusRect.width() - 56f, valuePaint, 38f, colors.warning)

    val explanationTop = statusRect.bottom + 28f
    val explanationTextWidth = width - 96f - 56f
    val explanationLineHeight = 31f
    val explanationHeight = 92f + wrappedLineCount(texts.fresnelExplanation, explanationTextWidth, smallPaint) * explanationLineHeight
    val explanationRect = RectF(48f, explanationTop, width - 48f, explanationTop + explanationHeight)
    drawRoundedCard(canvas, explanationRect, colors.card)
    canvas.drawText(texts.fresnelZone, explanationRect.left + 28f, explanationRect.top + 44f, labelPaint)
    drawWrappedText(
        canvas = canvas,
        text = texts.fresnelExplanation,
        x = explanationRect.left + 28f,
        y = explanationRect.top + 84f,
        maxWidth = explanationTextWidth,
        paint = smallPaint,
        lineHeight = explanationLineHeight,
        warningColor = colors.warning
    )

    val footerTop = explanationRect.bottom + 36f
    val footerRect = RectF(48f, footerTop, width - 48f, footerTop + 178f)
    drawRoundedCard(canvas, footerRect, colors.card)
    val sourceY = footerRect.top + 68f
    canvas.drawText(texts.ignSource, footerRect.left + 28f, sourceY, smallPaint)
    canvas.drawText(texts.generatedBy, footerRect.left + 28f, sourceY + 34f, smallPaint)

    val qrBitmap = generateQrCodeBitmap("geotower://site/${info.idAnfr}", 260)
    if (qrBitmap != null) {
        val qrOuterSize = 132f
        val qrOuterRect = RectF(
            footerRect.right - 28f - qrOuterSize,
            footerRect.top + 23f,
            footerRect.right - 28f,
            footerRect.top + 23f + qrOuterSize
        )
        drawRoundedCard(canvas, qrOuterRect, AndroidColor.WHITE)
        val qrInset = 10f
        val qrRect = RectF(
            qrOuterRect.left + qrInset,
            qrOuterRect.top + qrInset,
            qrOuterRect.right - qrInset,
            qrOuterRect.bottom - qrInset
        )
        canvas.drawBitmap(qrBitmap, null, qrRect, null)
    }

    return bitmap
}

private fun drawElevationProfileChart(
    canvas: Canvas,
    rect: RectF,
    profile: ElevationProfileDataResult,
    supportHeightMeters: Double,
    frequencyMHz: Int,
    colors: ShareProfileColors
) {
    if (profile.points.size < 2) return

    val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colors.grid
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    val terrainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colors.terrain
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }
    val terrainFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colors.terrainFill
        style = Paint.Style.FILL
    }
    val fresnelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colors.fresnel
        style = Paint.Style.FILL
    }
    val signalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colors.signal
        strokeWidth = 4f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(18f, 14f), 0f)
    }
    val labelPaint = textPaint(colors.secondaryText, 20f, Typeface.NORMAL).apply {
        textAlign = Paint.Align.RIGHT
    }

    val left = rect.left + 48f
    val top = rect.top + 16f
    val right = rect.right - 14f
    val bottom = rect.bottom - 42f
    val chartWidth = right - left
    val chartHeight = bottom - top

    val startLineHeight = profile.points.first().elevation + ELEVATION_USER_EYE_HEIGHT_METERS
    val endLineHeight = profile.points.last().elevation + supportHeightMeters
    val totalDistance = profile.distanceMeters.coerceAtLeast(1f)
    val terrainMin = profile.points.minOf { it.elevation }
    val terrainMax = profile.points.maxOf { it.elevation }
    val fresnelBounds = profile.points.map { point ->
        val lineHeight = elevationLineHeightAt(point.distanceMeters.toDouble(), totalDistance.toDouble(), startLineHeight, endLineHeight)
        val clearance = elevationFresnelClearanceMeters(point.distanceMeters.toDouble(), totalDistance.toDouble(), frequencyMHz)
        lineHeight - clearance to lineHeight + clearance
    }
    val rawMin = minOf(terrainMin, startLineHeight, endLineHeight, fresnelBounds.minOf { it.first })
    val rawMax = maxOf(terrainMax, startLineHeight, endLineHeight, fresnelBounds.maxOf { it.second })
    val rawRange = max(rawMax - rawMin, 10.0)
    val yMin = floor((rawMin - rawRange * 0.12) / 5.0) * 5.0
    val yMax = floor((rawMax + rawRange * 0.18) / 5.0 + 1.0) * 5.0
    val yRange = max(yMax - yMin, 1.0)

    fun x(distanceMeters: Float): Float = left + (distanceMeters / totalDistance) * chartWidth
    fun y(elevation: Double): Float = top + ((yMax - elevation) / yRange).toFloat() * chartHeight

    repeat(5) { index ->
        val fraction = index / 4f
        val gridY = top + chartHeight * fraction
        canvas.drawLine(left, gridY, right, gridY, axisPaint)
        val labelValue = yMax - yRange * fraction
        canvas.drawText("${labelValue.roundToInt()}", left - 10f, gridY + 7f, labelPaint)
    }
    repeat(5) { index ->
        val gridX = left + chartWidth * (index / 4f)
        canvas.drawLine(gridX, top, gridX, bottom, axisPaint)
    }

    val fresnelPath = Path()
    profile.points.forEachIndexed { index, point ->
        val lineHeight = elevationLineHeightAt(point.distanceMeters.toDouble(), totalDistance.toDouble(), startLineHeight, endLineHeight)
        val clearance = elevationFresnelClearanceMeters(point.distanceMeters.toDouble(), totalDistance.toDouble(), frequencyMHz)
        val px = x(point.distanceMeters)
        val py = y(lineHeight + clearance)
        if (index == 0) fresnelPath.moveTo(px, py) else fresnelPath.lineTo(px, py)
    }
    profile.points.asReversed().forEach { point ->
        val lineHeight = elevationLineHeightAt(point.distanceMeters.toDouble(), totalDistance.toDouble(), startLineHeight, endLineHeight)
        val clearance = elevationFresnelClearanceMeters(point.distanceMeters.toDouble(), totalDistance.toDouble(), frequencyMHz)
        fresnelPath.lineTo(x(point.distanceMeters), y(lineHeight - clearance))
    }
    fresnelPath.close()
    canvas.drawPath(fresnelPath, fresnelPaint)

    val fillPath = Path().apply {
        moveTo(x(0f), y(yMin))
        profile.points.forEach { point -> lineTo(x(point.distanceMeters), y(point.elevation)) }
        lineTo(x(totalDistance), y(yMin))
        close()
    }
    canvas.drawPath(fillPath, terrainFillPaint)

    val terrainPath = Path()
    profile.points.forEachIndexed { index, point ->
        val px = x(point.distanceMeters)
        val py = y(point.elevation)
        if (index == 0) terrainPath.moveTo(px, py) else terrainPath.lineTo(px, py)
    }
    canvas.drawPath(terrainPath, terrainPaint)
    canvas.drawLine(x(0f), y(startLineHeight), x(totalDistance), y(endLineHeight), signalPaint)
}

private fun drawRoundedCard(canvas: Canvas, rect: RectF, color: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(rect, 30f, 30f, paint)
}

private fun textPaint(color: Int, size: Float, typefaceStyle: Int): Paint {
    return Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = Typeface.create(Typeface.DEFAULT, typefaceStyle)
    }
}

private fun drawWrappedText(
    canvas: Canvas,
    text: String,
    x: Float,
    y: Float,
    maxWidth: Float,
    paint: Paint,
    lineHeight: Float,
    warningColor: Int
): Float {
    val originalColor = paint.color
    if (text.contains("(+")) paint.color = warningColor
    var currentY = y
    var currentLine = ""
    text.split(" ").forEach { word ->
        val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
        if (paint.measureText(candidate) <= maxWidth) {
            currentLine = candidate
        } else {
            if (currentLine.isNotEmpty()) {
                canvas.drawText(currentLine, x, currentY, paint)
                currentY += lineHeight
            }
            currentLine = word
        }
    }
    if (currentLine.isNotEmpty()) {
        canvas.drawText(currentLine, x, currentY, paint)
    }
    paint.color = originalColor
    return currentY
}

private fun wrappedLineCount(text: String, maxWidth: Float, paint: Paint): Int {
    var lineCount = 0
    var currentLine = ""
    text.split(" ").forEach { word ->
        val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
        if (paint.measureText(candidate) <= maxWidth) {
            currentLine = candidate
        } else {
            if (currentLine.isNotEmpty()) lineCount++
            currentLine = word
        }
    }
    if (currentLine.isNotEmpty()) lineCount++
    return lineCount.coerceAtLeast(1)
}

private data class ShareProfileColors(
    val background: Int,
    val card: Int,
    val text: Int,
    val secondaryText: Int,
    val grid: Int,
    val terrain: Int,
    val terrainFill: Int,
    val signal: Int,
    val fresnel: Int,
    val warning: Int
)
