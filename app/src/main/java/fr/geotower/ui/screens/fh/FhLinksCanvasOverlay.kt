package fr.geotower.ui.screens.fh

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.view.MotionEvent
import androidx.core.graphics.ColorUtils
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Calque haute performance dessinant toutes les liaisons FH filtrées directement sur Canvas.
 * Utilise des coordonnées Mercator précalculées et un rendu groupé par opérateur via
 * `canvas.drawLines` pour conserver 60 fps même avec des dizaines de milliers de traits.
 */
class FhLinksCanvasOverlay(
    context: Context,
    var onLinkClick: ((FhPreparedLink) -> Unit)? = null
) : Overlay() {

    private class ColorGroup(
        val colorInt: Int,
        val links: List<FhPreparedLink>
    )

    private val density = context.resources.displayMetrics.density
    private val pt0 = Point()
    private val ptX = Point()
    private val ptY = Point()
    private val geoRef = GeoPoint(0.0, 0.0)

    private var items: List<FhPreparedLink> = emptyList()
    private var colorGroups: List<ColorGroup> = emptyList()
    private var selectedLinkKey: String? = null
    private var lineBuffer = FloatArray(4096)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val selectedHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }

    private val selectedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    fun setLinks(links: List<FhPreparedLink>) {
        if (items === links) return
        items = links
        colorGroups = if (links.isEmpty()) {
            emptyList()
        } else {
            links.groupBy { it.colorInt }.map { (color, group) -> ColorGroup(color, group) }
        }
    }

    fun setSelectedLink(link: FhPreparedLink?) {
        selectedLinkKey = link?.let { linkKey(it) }
    }

    override fun draw(canvas: Canvas, projection: Projection) {
        val groups = colorGroups
        if (groups.isEmpty()) return

        val bb = projection.boundingBox
        val zoom = projection.zoomLevel
        val pad = if (zoom < 7.0) 1.0 else 0.15
        val viewMinLat = bb.latSouth - pad
        val viewMaxLat = bb.latNorth + pad
        val viewMinLon = bb.lonWest - pad
        val viewMaxLon = bb.lonEast + pad

        // Calcul de la transformation affine Mercator -> pixels écran à partir de 3 points dans l'emprise
        val cLat = bb.centerLatitude
        val cLon = bb.centerLongitude
        val dLon = ((bb.lonEast - bb.lonWest) * 0.25).coerceAtLeast(1e-5)
        val dLat = ((bb.latNorth - bb.latSouth) * 0.25).coerceAtLeast(1e-5)

        val mx0 = FhOperatorResolver.mercatorX(cLon)
        val my0 = FhOperatorResolver.mercatorY(cLat)
        val dMx = FhOperatorResolver.mercatorX(cLon + dLon) - mx0
        val dMy = FhOperatorResolver.mercatorY((cLat + dLat).coerceAtMost(85.0)) - my0
        if (dMx == 0.0 || dMy == 0.0) return

        geoRef.setCoords(cLat, cLon)
        projection.toPixels(geoRef, pt0)
        geoRef.setCoords(cLat, cLon + dLon)
        projection.toPixels(geoRef, ptX)
        geoRef.setCoords((cLat + dLat).coerceAtMost(85.0), cLon)
        projection.toPixels(geoRef, ptY)

        val ox = pt0.x.toDouble()
        val oy = pt0.y.toDouble()
        val ax = (ptX.x - pt0.x) / dMx
        val ay = (ptX.y - pt0.y) / dMx
        val bx = (ptY.x - pt0.x) / dMy
        val by = (ptY.y - pt0.y) / dMy

        val baseStrokePx = when {
            zoom < 7.5 -> 1.4f * density
            zoom < 10.0 -> 2.0f * density
            else -> 2.6f * density
        }
        val baseAlpha = when {
            zoom < 7.5 -> 165
            zoom < 10.0 -> 195
            else -> 220
        }
        val skipTinyLines = zoom < 9.0
        val drawDots = zoom >= 10.5
        val dotRadius = if (zoom >= 13.0) 3.4f * density else 2.4f * density

        linePaint.strokeWidth = baseStrokePx
        linePaint.isAntiAlias = zoom >= 9.0
        linePaint.strokeCap = if (zoom >= 9.0) Paint.Cap.ROUND else Paint.Cap.BUTT

        var selectedItem: FhPreparedLink? = null
        val selKey = selectedLinkKey

        for (g in groups.indices) {
            val group = groups[g]
            val groupLinks = group.links
            var count = 0

            for (i in groupLinks.indices) {
                val item = groupLinks[i]
                if (item.maxLat < viewMinLat || item.minLat > viewMaxLat ||
                    item.maxLon < viewMinLon || item.minLon > viewMaxLon
                ) {
                    continue
                }

                if (selKey != null && linkKey(item) == selKey) {
                    selectedItem = item
                    continue
                }

                val dxA = item.mxA - mx0
                val dyA = item.myA - my0
                val x1 = (ox + dxA * ax + dyA * bx).toFloat()
                val y1 = (oy + dxA * ay + dyA * by).toFloat()

                val dxB = item.mxB - mx0
                val dyB = item.myB - my0
                val x2 = (ox + dxB * ax + dyB * bx).toFloat()
                val y2 = (oy + dxB * ay + dyB * by).toFloat()

                if (skipTinyLines && abs(x2 - x1) + abs(y2 - y1) < 1.5f) {
                    continue
                }

                if (count + 4 > lineBuffer.size) {
                    lineBuffer = lineBuffer.copyOf(lineBuffer.size * 2)
                }
                lineBuffer[count++] = x1
                lineBuffer[count++] = y1
                lineBuffer[count++] = x2
                lineBuffer[count++] = y2
            }

            if (count > 0) {
                linePaint.color = ColorUtils.setAlphaComponent(group.colorInt, baseAlpha)
                canvas.drawLines(lineBuffer, 0, count, linePaint)

                if (drawDots) {
                    dotPaint.color = group.colorInt
                    var idx = 0
                    while (idx < count) {
                        canvas.drawCircle(lineBuffer[idx], lineBuffer[idx + 1], dotRadius, dotPaint)
                        canvas.drawCircle(lineBuffer[idx + 2], lineBuffer[idx + 3], dotRadius, dotPaint)
                        idx += 4
                    }
                }
            }
        }

        selectedItem?.let { item ->
            val dxA = item.mxA - mx0
            val dyA = item.myA - my0
            val x1 = (ox + dxA * ax + dyA * bx).toFloat()
            val y1 = (oy + dxA * ay + dyA * by).toFloat()

            val dxB = item.mxB - mx0
            val dyB = item.myB - my0
            val x2 = (ox + dxB * ax + dyB * bx).toFloat()
            val y2 = (oy + dxB * ay + dyB * by).toFloat()

            selectedHaloPaint.strokeWidth = baseStrokePx + 4.5f * density
            canvas.drawLine(x1, y1, x2, y2, selectedHaloPaint)

            selectedStrokePaint.strokeWidth = baseStrokePx + 2.0f * density
            selectedStrokePaint.color = item.colorInt
            canvas.drawLine(x1, y1, x2, y2, selectedStrokePaint)

            val selectedDotRadius = 5.0f * density
            dotPaint.color = Color.WHITE
            canvas.drawCircle(x1, y1, selectedDotRadius + 1.5f * density, dotPaint)
            canvas.drawCircle(x2, y2, selectedDotRadius + 1.5f * density, dotPaint)
            dotPaint.color = item.colorInt
            canvas.drawCircle(x1, y1, selectedDotRadius, dotPaint)
            canvas.drawCircle(x2, y2, selectedDotRadius, dotPaint)
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val currentItems = items
        if (currentItems.isEmpty()) return false
        val callback = onLinkClick ?: return false

        val projection = mapView.projection ?: return false
        val bb = projection.boundingBox
        val pad = 0.2
        val viewMinLat = bb.latSouth - pad
        val viewMaxLat = bb.latNorth + pad
        val viewMinLon = bb.lonWest - pad
        val viewMaxLon = bb.lonEast + pad

        val tapX = e.x
        val tapY = e.y
        val hitThreshold = 22f * density
        var bestDist = hitThreshold
        var bestItem: FhPreparedLink? = null

        for (i in currentItems.indices) {
            val item = currentItems[i]
            if (item.maxLat < viewMinLat || item.minLat > viewMaxLat ||
                item.maxLon < viewMinLon || item.minLon > viewMaxLon
            ) {
                continue
            }
            geoRef.setCoords(item.link.latA, item.link.lonA)
            projection.toPixels(geoRef, pt0)
            val x1 = pt0.x.toFloat()
            val y1 = pt0.y.toFloat()

            geoRef.setCoords(item.link.latB, item.link.lonB)
            projection.toPixels(geoRef, pt0)
            val x2 = pt0.x.toFloat()
            val y2 = pt0.y.toFloat()

            val dist = distanceToSegment(tapX, tapY, x1, y1, x2, y2)
            if (dist < bestDist) {
                bestDist = dist
                bestItem = item
            }
        }

        if (bestItem != null) {
            callback(bestItem)
            return true
        }
        return false
    }

    private fun linkKey(item: FhPreparedLink): String =
        "${item.link.idAnfrA}|${item.link.idAnfrB}|${item.operatorId}"

    private fun distanceToSegment(
        px: Float,
        py: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0f) return hypot(px - x1, py - y1)
        val t = (((px - x1) * dx + (py - y1) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = x1 + t * dx
        val projY = y1 + t * dy
        return hypot(px - projX, py - projY)
    }
}
