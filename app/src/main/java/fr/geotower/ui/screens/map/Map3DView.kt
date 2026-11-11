package fr.geotower.ui.screens.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.geotower.data.db.AppDatabase
import fr.geotower.data.hidden.HiddenSitesStore
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.RadioMapMarker
import fr.geotower.data.models.SiteHsEntity
import fr.geotower.data.models.physicalSiteKey
import fr.geotower.data.trip.TripPlan
import fr.geotower.data.trip.TripRouteProgress
import fr.geotower.data.trip.remainingLegPoints
import fr.geotower.data.trip.tripBoundingBox
import fr.geotower.data.trip.tripDirectionArrows
import fr.geotower.data.trip.tripFlowTrack
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import fr.geotower.utils.MapClusterStrengthProfile
import fr.geotower.utils.MapUtils
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.PowerProfile
import fr.geotower.utils.zbPotentialOutages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.roundToInt

private const val ANTENNAS_SOURCE_ID = "geotower-antennas-source"
private const val ANTENNAS_LAYER_ID = "geotower-antennas-layer"
private const val CLUSTERS_LAYER_ID = "geotower-clusters-layer"
private const val USER_LOCATION_SOURCE_ID = "geotower-user-location-source"
private const val USER_ARROW_LAYER_ID = "geotower-user-arrow-layer"
private const val USER_ARROW_ICON_ID = "geotower-user-arrow-icon"

private const val TRIP_ROUTE_SOURCE_ID = "geotower-trip-route-source"
private const val TRIP_ROUTE_CASING_LAYER_ID = "geotower-trip-route-casing-layer"
private const val TRIP_ROUTE_LAYER_ID = "geotower-trip-route-layer"
private const val TRIP_ROUTE_DASHED_LAYER_ID = "geotower-trip-route-dashed-layer"

private const val TRIP_APPROACH_SOURCE_ID = "geotower-trip-approach-source"
private const val TRIP_APPROACH_CASING_LAYER_ID = "geotower-trip-approach-casing-layer"
private const val TRIP_APPROACH_LAYER_ID = "geotower-trip-approach-layer"
private const val TRIP_APPROACH_DASHED_LAYER_ID = "geotower-trip-approach-dashed-layer"

private const val TRIP_ARROWS_SOURCE_ID = "geotower-trip-arrows-source"
private const val TRIP_ARROWS_LAYER_ID = "geotower-trip-arrows-layer"

private const val TRIP_STEPS_SOURCE_ID = "geotower-trip-steps-source"
private const val TRIP_STEPS_LAYER_ID = "geotower-trip-steps-layer"

private const val TRIP_FLOW_CYCLE_MS = 3200
private const val TRIP_FLOW_STRIDE = 28

private data class TripRenderData(
    val approachCollection: FeatureCollection,
    val routeCollection: FeatureCollection,
    val arrowsCollection: FeatureCollection,
    val stepsCollection: FeatureCollection,
    val stepImages: Map<String, Bitmap>
)

/**
 * Dessine un chevron animé du fil de trajet avec son contour d'ombre et son remplissage blanc.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTripFlowChevron(
    centerX: Float,
    centerY: Float,
    angleDegrees: Float,
    alpha: Float
) {
    val halfWidth = 5f * density
    val halfHeight = 4.5f * density
    val path = Path().apply {
        moveTo(centerX - halfWidth, centerY + halfHeight)
        lineTo(centerX, centerY - halfHeight)
        lineTo(centerX + halfWidth, centerY + halfHeight)
    }

    rotate(degrees = angleDegrees, pivot = Offset(centerX, centerY)) {
        drawPath(
            path = path,
            color = Color.Black.copy(alpha = 0.4f * alpha),
            style = Stroke(
                width = 3.6f * density,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        drawPath(
            path = path,
            color = Color.White.copy(alpha = alpha),
            style = Stroke(
                width = 2.0f * density,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

private val croppedMarkerCache = android.util.LruCache<String, Bitmap>(300)
private val pylonMarkerCache = android.util.LruCache<String, Bitmap>(500)

/**
 * Trie les clés d'opérateurs pour un azimut en priorisant l'opérateur par défaut de l'utilisateur.
 */
private fun sortAzimuthOperatorKeys(
    operatorKeys: Set<String>,
    defaultOperatorKey: String?
): List<String> {
    val baseOrder = listOf(
        OperatorColors.ORANGE_KEY,
        OperatorColors.BOUYGUES_KEY,
        OperatorColors.SFR_KEY,
        OperatorColors.FREE_KEY
    )
    val ordered = mutableListOf<String>()
    if (defaultOperatorKey != null && defaultOperatorKey in operatorKeys) {
        ordered += defaultOperatorKey
    }
    baseOrder.forEach { key ->
        if (key in operatorKeys && key !in ordered) ordered += key
    }
    OperatorColors.orderedKeys.forEach { key ->
        if (key in operatorKeys && key !in ordered) ordered += key
    }
    return ordered.ifEmpty { operatorKeys.toList() }
}

/**
 * Dessine un secteur d'azimut (cône) de 70° derrière le trait d'antenne mobile.
 */
private fun drawAzimuthCone(
    canvas: Canvas,
    cx: Float,
    cy: Float,
    radius: Float,
    visualAngleDeg: Float,
    colorInt: Int,
    density: Float
) {
    val coneRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
    // 0° est orienté vers le haut (Nord, soit -90° dans le repère Canvas standard)
    val startAngle = visualAngleDeg - 35f - 90f
    val sweepAngle = 70f

    val conePath = android.graphics.Path()
    conePath.moveTo(cx, cy)
    conePath.arcTo(coneRect, startAngle, sweepAngle)
    conePath.close()

    val conePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = colorInt
        alpha = 0x26 // ~15%
    }
    canvas.drawPath(conePath, conePaint)

    val edgePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        color = colorInt
        alpha = 0x66 // ~40%
        strokeWidth = 1.0f * density
    }
    canvas.drawPath(conePath, edgePaint)
}

/**
 * Dessine un trait rayonnant d'azimut (style officiel GeoTower).
 */
private fun drawAzimuthLine(
    canvas: Canvas,
    cx: Float,
    cy: Float,
    startDist: Float,
    endDist: Float,
    visualAngleDeg: Float,
    colorInt: Int,
    density: Float,
    isDashed: Boolean = false,
    satelliteContrast: Boolean = false
) {
    val rad = Math.toRadians((visualAngleDeg - 90.0))
    val dirX = Math.cos(rad).toFloat()
    val dirY = Math.sin(rad).toFloat()

    val startX = cx + startDist * dirX
    val startY = cy + startDist * dirY
    val endX = cx + endDist * dirX
    val endY = cy + endDist * dirY

    if (satelliteContrast) {
        val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            color = android.graphics.Color.parseColor("#1A202C")
            strokeWidth = 3.6f * density
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
        if (isDashed) {
            shadowPaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f * density, 3f * density), 0f)
        }
        canvas.drawLine(startX, startY, endX, endY, shadowPaint)
    }

    val linePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        color = colorInt
        strokeWidth = 2.0f * density
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    if (isDashed) {
        linePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f * density, 3f * density), 0f)
    }
    canvas.drawLine(startX, startY, endX, endY, linePaint)
}

/**
 * Dessine les pastilles rondes colorées d'opérateurs au bout de l'azimut (style officiel GeoTower).
 * Si plusieurs opérateurs partagent le même azimut, les pastilles sont empilées le long du rayon.
 */
private fun drawAzimuthDots(
    canvas: Canvas,
    cx: Float,
    cy: Float,
    baseDist: Float,
    visualAngleDeg: Float,
    operatorKeys: List<String>,
    density: Float,
    isFh: Boolean = false,
    satelliteContrast: Boolean = false
) {
    val rad = Math.toRadians((visualAngleDeg - 90.0))
    val dirX = Math.cos(rad).toFloat()
    val dirY = Math.sin(rad).toFloat()

    val dotRadius = if (isFh) 2.4f * density else 3.4f * density
    val gap = dotRadius * 2.1f

    operatorKeys.forEachIndexed { index, opKey ->
        val opColor = OperatorColors.colorInt(opKey)
        val dist = baseDist + (index * gap)
        val dotX = cx + dist * dirX
        val dotY = cy + dist * dirY

        // Liseré de contraste pour détacher la pastille sur le ciel / décor 3D
        val outlinePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            color = if (satelliteContrast) MapUtils.contrastOutlineColor(opColor) else android.graphics.Color.parseColor("#1A202C")
            strokeWidth = 1.2f * density
        }
        canvas.drawCircle(dotX, dotY, dotRadius, outlinePaint)

        // Pastille pleine de l'opérateur
        val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            color = opColor
        }
        canvas.drawCircle(dotX, dotY, (dotRadius - 0.2f * density).coerceAtLeast(0.5f), fillPaint)
    }
}

/**
 * Rogne le carré transparent issu de createAdaptiveMarker pour ne garder que le disque
 * de l'antenne (avec son liseré de contraste éventuel).
 */
private fun cropToMarkerCircle(bitmap: Bitmap, clusterStrength: Int): Bitmap {
    val normalizedStrength = MapClusterStrengthProfile.normalize(clusterStrength)
    val coreRadiusUnits = MapClusterStrengthProfile.antennaCoreRadiusUnits(normalizedStrength)
    val scale = bitmap.width / 230f
    val radiusPx = ((coreRadiusUnits + 6f) * scale).toInt() + 1
    val centerPx = bitmap.width / 2
    val left = (centerPx - radiusPx).coerceAtLeast(0)
    val top = (centerPx - radiusPx).coerceAtLeast(0)
    val cropSize = (radiusPx * 2).coerceAtMost(minOf(bitmap.width - left, bitmap.height - top))
    if (cropSize <= 0) return bitmap
    return Bitmap.createBitmap(bitmap, left, top, cropSize, cropSize)
}

/**
 * Types visuels de support 3D dérivés de la nature ANFR (ref_nature).
 */
private enum class SupportVisualType {
    LATTICE,        // Pylône treillis / autostable / autoportant
    TUBULAR,        // Pylône tubulaire / Mât / Fût
    GUYED,          // Pylône haubané
    WATER_TOWER,    // Château d'eau / réservoir / silo
    ROOFTOP_MAST,   // Immeuble / Bâtiment / Toit terrasse
    STEALTH_TREE    // Pylône arbre
}

private fun resolveSupportVisualType(nature: String?): SupportVisualType {
    if (nature == null) return SupportVisualType.LATTICE
    val n = nature.lowercase()
    return when {
        n.contains("arbre") -> SupportVisualType.STEALTH_TREE
        n.contains("chateau") || n.contains("château") || n.contains("réservoir") || n.contains("reservoir") || n.contains("silo") -> SupportVisualType.WATER_TOWER
        n.contains("haubane") || n.contains("haubané") -> SupportVisualType.GUYED
        n.contains("immeuble") || n.contains("bâtiment") || n.contains("batiment") || n.contains("toit") || n.contains("terrasse") || n.contains("local") -> SupportVisualType.ROOFTOP_MAST
        n.contains("tubulaire") || n.contains("mât") || n.contains("mat") || n.contains("fût") || n.contains("fut") || n.contains("béton") || n.contains("beton") -> SupportVisualType.TUBULAR
        else -> SupportVisualType.LATTICE
    }
}

/**
 * Dessine la structure physique du support en fonction de sa nature ANFR.
 */
private fun drawSupportStructure(
    canvas: Canvas,
    cx: Float,
    towerTop: Float,
    towerBottom: Float,
    pylonHeight: Float,
    clampedHeightM: Float,
    visualType: SupportVisualType,
    density: Float
) {
    when (visualType) {
        SupportVisualType.LATTICE -> {
            val baseWidth = (7.5f + clampedHeightM * 0.08f) * density
            val topWidth = 5.0f * density
            val numSections = when {
                clampedHeightM < 16f -> 2
                clampedHeightM < 32f -> 3
                clampedHeightM < 50f -> 4
                else -> 5
            }
            val sectionHeight = (towerBottom - towerTop) / numSections
            val path = android.graphics.Path()

            val leftBase = cx - baseWidth / 2f
            val rightBase = cx + baseWidth / 2f
            val leftTop = cx - topWidth / 2f
            val rightTop = cx + topWidth / 2f

            path.moveTo(leftBase, towerBottom)
            path.lineTo(leftTop, towerTop)
            path.moveTo(rightBase, towerBottom)
            path.lineTo(rightTop, towerTop)

            // Platine d'ancrage au sol
            path.moveTo(leftBase - 2f * density, towerBottom)
            path.lineTo(rightBase + 2f * density, towerBottom)

            // Croisillons en X et entretoises horizontales
            for (i in 0 until numSections) {
                val y0 = towerBottom - (i * sectionHeight)
                val y1 = towerBottom - ((i + 1) * sectionHeight)
                val t0 = i.toFloat() / numSections
                val t1 = (i + 1).toFloat() / numSections

                val w0 = baseWidth + (topWidth - baseWidth) * t0
                val w1 = baseWidth + (topWidth - baseWidth) * t1

                val x0L = cx - w0 / 2f
                val x0R = cx + w0 / 2f
                val x1L = cx - w1 / 2f
                val x1R = cx + w1 / 2f

                path.moveTo(x1L, y1)
                path.lineTo(x1R, y1)

                path.moveTo(x0L, y0)
                path.lineTo(x1R, y1)
                path.moveTo(x0R, y0)
                path.lineTo(x1L, y1)
            }

            val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#1A202C")
                strokeWidth = 2.8f * density
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
            }
            canvas.drawPath(path, shadowPaint)

            val corePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#FFFFFF")
                strokeWidth = 1.4f * density
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
            }
            canvas.drawPath(path, corePaint)
        }

        SupportVisualType.TUBULAR -> {
            val baseWidth = (5.2f + clampedHeightM * 0.05f) * density
            val topWidth = 3.6f * density

            val tubePath = android.graphics.Path().apply {
                moveTo(cx - baseWidth / 2f, towerBottom)
                lineTo(cx - topWidth / 2f, towerTop)
                lineTo(cx + topWidth / 2f, towerTop)
                lineTo(cx + baseWidth / 2f, towerBottom)
                close()
            }

            val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#1A202C")
                strokeWidth = 2.4f * density
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
            }
            canvas.drawPath(tubePath, shadowPaint)

            val bodyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
                color = android.graphics.Color.parseColor("#94A3B8")
            }
            canvas.drawPath(tubePath, bodyPaint)

            // Reflet spéculaire métallique vertical
            val highlightPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#F8FAFC")
                strokeWidth = 1.3f * density
            }
            canvas.drawLine(cx, towerTop, cx, towerBottom, highlightPaint)

            // Brides horizontales intermédiaires
            val numFlanges = when {
                clampedHeightM < 20f -> 1
                clampedHeightM < 40f -> 2
                else -> 3
            }
            val flangePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#1A202C")
                strokeWidth = 1.8f * density
            }
            for (f in 1..numFlanges) {
                val fy = towerBottom - (f.toFloat() / (numFlanges + 1)) * (towerBottom - towerTop)
                val t = (towerBottom - fy) / (towerBottom - towerTop)
                val fw = (baseWidth + (topWidth - baseWidth) * t) * 0.7f
                canvas.drawLine(cx - fw, fy, cx + fw, fy, flangePaint)
            }
            canvas.drawLine(cx - baseWidth * 0.8f, towerBottom, cx + baseWidth * 0.8f, towerBottom, shadowPaint)
        }

        SupportVisualType.GUYED -> {
            val mastWidth = 3.2f * density
            val guySpread = (8f * density + clampedHeightM * 0.18f * density).coerceAtLeast(14f * density)

            val guyPath = android.graphics.Path()
            val leftAnchorX = cx - guySpread
            val rightAnchorX = cx + guySpread

            val attachY1 = towerBottom - (towerBottom - towerTop) * 0.60f
            val attachY2 = towerBottom - (towerBottom - towerTop) * 0.88f

            guyPath.moveTo(leftAnchorX, towerBottom)
            guyPath.lineTo(cx, attachY1)
            guyPath.moveTo(rightAnchorX, towerBottom)
            guyPath.lineTo(cx, attachY1)

            guyPath.moveTo(leftAnchorX, towerBottom)
            guyPath.lineTo(cx, attachY2)
            guyPath.moveTo(rightAnchorX, towerBottom)
            guyPath.lineTo(cx, attachY2)

            val cableShadow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#1A202C")
                strokeWidth = 2.0f * density
            }
            canvas.drawPath(guyPath, cableShadow)

            val cablePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#CBD5E1")
                strokeWidth = 1.0f * density
            }
            canvas.drawPath(guyPath, cablePaint)

            val mastPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#1A202C")
                strokeWidth = mastWidth + 1.2f * density
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            canvas.drawLine(cx, towerTop, cx, towerBottom, mastPaint)

            val mastCore = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#FFFFFF")
                strokeWidth = mastWidth
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            canvas.drawLine(cx, towerTop, cx, towerBottom, mastCore)

            val stakePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#1A202C")
                strokeWidth = 2.4f * density
            }
            canvas.drawLine(leftAnchorX - 2f * density, towerBottom, leftAnchorX + 2f * density, towerBottom, stakePaint)
            canvas.drawLine(rightAnchorX - 2f * density, towerBottom, rightAnchorX + 2f * density, towerBottom, stakePaint)
            canvas.drawLine(cx - 3f * density, towerBottom, cx + 3f * density, towerBottom, stakePaint)
        }

        SupportVisualType.WATER_TOWER -> {
            // Château d'eau / réservoir : l'antenne est posée sur la toiture / coupole du réservoir 3D
            val mastLength = 6.5f * density
            val domeY = towerTop + mastLength
            val flangeWidth = 8.5f * density

            // Embase de fixation sur le dôme
            val flangePath = android.graphics.Path().apply {
                moveTo(cx - flangeWidth / 2f, domeY)
                lineTo(cx + flangeWidth / 2f, domeY)
                moveTo(cx - flangeWidth * 0.3f, domeY)
                lineTo(cx, domeY - 3f * density)
                moveTo(cx + flangeWidth * 0.3f, domeY)
                lineTo(cx, domeY - 3f * density)
            }
            val flangeShadow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#1A202C")
                strokeWidth = 2.4f * density
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            canvas.drawPath(flangePath, flangeShadow)

            val flangePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#94A3B8")
                strokeWidth = 1.2f * density
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            canvas.drawPath(flangePath, flangePaint)

            // Court mât tubulaire
            val mastPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#1A202C")
                strokeWidth = 3.2f * density
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            canvas.drawLine(cx, towerTop, cx, domeY, mastPaint)

            val mastCore = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#FFFFFF")
                strokeWidth = 1.6f * density
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            canvas.drawLine(cx, towerTop, cx, domeY, mastCore)
            // L'espace sous domeY jusqu'au sol reste transparent pour laisser voir le château d'eau 3D !
        }

        SupportVisualType.ROOFTOP_MAST -> {
            // Immeuble / Bâtiment / Toit terrasse : l'antenne est posée sur le toit du bâtiment 3D
            val mastLength = 7f * density
            val roofY = towerTop + mastLength
            val footWidth = 10f * density

            // Platine / socle de toiture compact
            val footPath = android.graphics.Path().apply {
                moveTo(cx - footWidth / 2f, roofY)
                lineTo(cx + footWidth / 2f, roofY)
                moveTo(cx - footWidth * 0.35f, roofY)
                lineTo(cx, roofY - 3.5f * density)
                moveTo(cx + footWidth * 0.35f, roofY)
                lineTo(cx, roofY - 3.5f * density)
            }
            val footShadow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#1A202C")
                strokeWidth = 2.4f * density
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            canvas.drawPath(footPath, footShadow)

            val footPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#CBD5E1")
                strokeWidth = 1.2f * density
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            canvas.drawPath(footPath, footPaint)

            // Tube court du mât de toiture
            val mastPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#1A202C")
                strokeWidth = 3.2f * density
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            canvas.drawLine(cx, towerTop, cx, roofY, mastPaint)

            val mastCore = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#FFFFFF")
                strokeWidth = 1.6f * density
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            canvas.drawLine(cx, towerTop, cx, roofY, mastCore)
            // L'espace sous roofY jusqu'au sol reste transparent pour laisser voir le bâtiment 3D !
        }

        SupportVisualType.STEALTH_TREE -> {
            val trunkWidth = (5.5f + clampedHeightM * 0.05f) * density
            val trunkTopWidth = 3.5f * density

            val trunkPath = android.graphics.Path().apply {
                moveTo(cx - trunkWidth / 2f, towerBottom)
                lineTo(cx - trunkTopWidth / 2f, towerTop)
                lineTo(cx + trunkTopWidth / 2f, towerTop)
                lineTo(cx + trunkWidth / 2f, towerBottom)
                close()
            }
            val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#1A202C")
                strokeWidth = 2.2f * density
            }
            canvas.drawPath(trunkPath, shadowPaint)

            val trunkPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
                color = android.graphics.Color.parseColor("#4E342E")
            }
            canvas.drawPath(trunkPath, trunkPaint)

            val numTiers = 4
            val branchPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
                color = android.graphics.Color.parseColor("#2E7D32")
            }
            val branchStroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#1B5E20")
                strokeWidth = 1.2f * density
            }
            val foliageBottom = towerBottom - (towerBottom - towerTop) * 0.35f
            for (t in 0 until numTiers) {
                val y = foliageBottom - t * ((foliageBottom - towerTop) / numTiers)
                val tierFrac = (numTiers - t).toFloat() / numTiers
                val branchSpread = (4f * density + tierFrac * 8f * density)

                val bPath = android.graphics.Path().apply {
                    moveTo(cx - branchSpread, y + 2.5f * density)
                    lineTo(cx, y - 2f * density)
                    lineTo(cx + branchSpread, y + 2.5f * density)
                    lineTo(cx, y)
                    close()
                }
                canvas.drawPath(bPath, branchPaint)
                canvas.drawPath(bPath, branchStroke)
            }
            canvas.drawLine(cx - trunkWidth * 0.7f, towerBottom, cx + trunkWidth * 0.7f, towerBottom, shadowPaint)
        }
    }
}

/**
 * Dessine un pylône treillis réaliste à l'échelle portant le macaron GeoTower au sommet,
 * avec le style authentique GeoTower pour les azimuts (traits, pastilles opérateurs empilées, cônes)
 * rayonnant depuis la tête du pylône dans les airs.
 * La taille et la forme du pylône sont adaptées à la hauteur et à la nature réelle du support (ANFR).
 * La base du mât est située tout en bas du bitmap (ancrage au sol en 3D).
 */
private fun createPylonAntennaMarkerBitmap(
    circleBitmap: Bitmap,
    density: Float,
    azimuths: Map<Float, Set<String>>,
    fhAzimuths: Map<Float, Set<String>>,
    defaultOp: String,
    showLines: Boolean,
    showCones: Boolean,
    showFh: Boolean,
    cameraBearing: Float,
    supportHeightMeters: Double,
    visualType: SupportVisualType = SupportVisualType.LATTICE,
    satelliteContrast: Boolean = false
): Bitmap {
    val rCircle = circleBitmap.width / 2f
    val beamLength = 22f * density
    val pointRadius = 3.4f * density
    val gapMobile = pointRadius * 2.1f

    var maxDotCount = 1
    if (showLines && azimuths.isNotEmpty()) {
        maxDotCount = maxOf(maxDotCount, azimuths.values.maxOfOrNull { it.size } ?: 1)
    }
    if (showFh && fhAzimuths.isNotEmpty()) {
        maxDotCount = maxOf(maxDotCount, fhAzimuths.values.maxOfOrNull { it.size } ?: 1)
    }

    val hasRays = (showLines && azimuths.isNotEmpty()) || (showFh && fhAzimuths.isNotEmpty()) || (showCones && azimuths.isNotEmpty())
    val rayExtension = if (hasRays) beamLength + (maxDotCount - 1) * gapMobile + pointRadius + 4f * density else 0f

    // Hauteur du support ANFR bornée de façon réaliste (entre 8m et 75m)
    val clampedHeightM = supportHeightMeters.coerceIn(8.0, 75.0).toFloat()
    // Facteur d'échelle : 10m -> 15.5dp, 25m -> 23.8dp (~24dp), 40m -> 32dp, 60m -> 43dp
    val pylonHeightDp = (10f + clampedHeightM * 0.55f).coerceIn(14f, 48f)
    val pylonHeight = pylonHeightDp * density

    val minSupportMargin = when (visualType) {
        SupportVisualType.GUYED -> (8f * density + clampedHeightM * 0.18f * density).coerceAtLeast(14f * density) + 3f * density
        SupportVisualType.WATER_TOWER -> 10f * density
        else -> 8f * density
    }
    val maxRadius = maxOf(rCircle + rayExtension + 4f * density, minSupportMargin)

    val cx = maxRadius
    val cy = maxRadius

    val pylonBottom = cy + rCircle + pylonHeight
    val totalHeight = (pylonBottom + 3f * density).toInt()
    val totalWidth = (maxRadius * 2f).toInt()

    val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // 1. Structure du mât / support selon sa nature ANFR
    val towerTop = cy + rCircle - 3f * density
    val towerBottom = pylonBottom
    drawSupportStructure(
        canvas = canvas,
        cx = cx,
        towerTop = towerTop,
        towerBottom = towerBottom,
        pylonHeight = pylonHeight,
        clampedHeightM = clampedHeightM,
        visualType = visualType,
        density = density
    )

    // 2. Cônes d'azimut en arrière-plan (si activés)
    if (showCones) {
        azimuths.forEach { (az, ops) ->
            val sortedOps = sortAzimuthOperatorKeys(ops, defaultOp)
            val mainOp = sortedOps.first()
            val opColor = OperatorColors.colorInt(mainOp)
            val visualAngle = az - cameraBearing
            drawAzimuthCone(canvas, cx, cy, rCircle + beamLength, visualAngle, opColor, density)
        }
    }

    // 3. Traits et pastilles d'azimut mobiles (style officiel GeoTower)
    if (showLines) {
        azimuths.forEach { (az, ops) ->
            val sortedOps = sortAzimuthOperatorKeys(ops, defaultOp)
            val mainOp = sortedOps.first()
            val opColor = OperatorColors.colorInt(mainOp)
            val visualAngle = az - cameraBearing
            drawAzimuthLine(
                canvas = canvas,
                cx = cx,
                cy = cy,
                startDist = rCircle - 1.5f * density,
                endDist = rCircle + beamLength,
                visualAngleDeg = visualAngle,
                colorInt = opColor,
                density = density,
                isDashed = false,
                satelliteContrast = satelliteContrast
            )
            drawAzimuthDots(
                canvas = canvas,
                cx = cx,
                cy = cy,
                baseDist = rCircle + beamLength,
                visualAngleDeg = visualAngle,
                operatorKeys = sortedOps,
                density = density,
                isFh = false,
                satelliteContrast = satelliteContrast
            )
        }
    }

    // 4. Traits et pastilles d'azimut FH (faisceaux hertziens en pointillés)
    if (showFh) {
        fhAzimuths.forEach { (az, ops) ->
            val sortedOps = sortAzimuthOperatorKeys(ops, defaultOp)
            val mainOp = sortedOps.first()
            val opColor = OperatorColors.colorInt(mainOp)
            val visualAngle = az - cameraBearing
            drawAzimuthLine(
                canvas = canvas,
                cx = cx,
                cy = cy,
                startDist = rCircle - 1.5f * density,
                endDist = rCircle + beamLength,
                visualAngleDeg = visualAngle,
                colorInt = opColor,
                density = density,
                isDashed = true,
                satelliteContrast = satelliteContrast
            )
            drawAzimuthDots(
                canvas = canvas,
                cx = cx,
                cy = cy,
                baseDist = rCircle + beamLength,
                visualAngleDeg = visualAngle,
                operatorKeys = sortedOps,
                density = density,
                isFh = true,
                satelliteContrast = satelliteContrast
            )
        }
    }

    // 5. Macaron officiel GeoTower au sommet (recouvre la jonction des traits et du mât)
    val circleLeft = cx - rCircle
    val circleTop = cy - rCircle
    canvas.drawBitmap(circleBitmap, circleLeft, circleTop, null)

    return bitmap
}

/**
 * Crée un bitmap avec le repère de position personnalisé exact de GeoTower (LocationMarkerPainter).
 */
private fun createCustomLocationMarkerBitmap(density: Float, primaryColorInt: Int): Bitmap {
    val radius = 18f * density
    val stemLength = 35f * density
    val tipDistance = radius + stemLength + 15f
    val halfSize = (tipDistance + 8f * density).toInt()
    val sizePx = (halfSize * 2).coerceAtLeast(64)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val painter = LocationMarkerPainter(density, primaryColorInt)
    val cx = sizePx / 2f
    val cy = sizePx / 2f
    painter.draw(canvas, cx, cy, rotationDegrees = 0f, showDirection = true)
    return bitmap
}

/**
 * Composant de carte 3D propulsé par MapLibre Native.
 * Fournit la perspective inclinée (pitch/tilt 55°), les bâtiments 3D extrudés et le relief,
 * avec des pylônes en treillis proportionnés et des flèches d'azimut au sommet.
 */
@Composable
fun Map3DView(
    centerLat: Double,
    centerLon: Double,
    zoom: Double,
    bearing: Float,
    userLatitude: Double?,
    userLongitude: Double?,
    userHeading: Float,
    primaryColorInt: Int,
    antennas: List<LocalisationEntity>,
    sitesHs: List<SiteHsEntity> = emptyList(),
    clusterStrength: Int = AppConfig.DEFAULT_MAP_CLUSTER_STRENGTH,
    satelliteMarkerContrast: Boolean = false,
    radioMarkers: List<RadioMapMarker> = emptyList(),
    recenterTrigger: Long = 0L,
    styleUri: String = "https://tiles.openfreemap.org/styles/liberty",
    tripPlan: TripPlan? = null,
    tripApproachPoints: List<DoubleArray>? = null,
    tripApproachRouted: Boolean = true,
    tripProgress: TripRouteProgress? = null,
    tripMode: String = TRIP_MODE_VIEW,
    navCameraLocked: Boolean = false,
    onStepClick: ((Int) -> Unit)? = null,
    onMapClick: ((lat: Double, lon: Double) -> Unit)? = null,
    onUserInteraction: (() -> Unit)? = null,
    onCameraChange: (lat: Double, lon: Double, zoom: Double, bearing: Float) -> Unit,
    onViewportChanged: (zoom: Double, latNorth: Double, lonEast: Double, latSouth: Double, lonWest: Double) -> Unit,
    onSiteClick: (LocalisationEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialisation du singleton MapLibre
    remember(context) {
        try {
            MapLibre.getInstance(context)
        } catch (e: Exception) {
            AppLogger.w("Map3DView", "MapLibre.getInstance failed", e)
        }
    }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
        }
    }

    var mapLibreMapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var isStyleLoaded by remember { mutableStateOf(false) }
    var sitesMap by remember { mutableStateOf<Map<String, LocalisationEntity>>(emptyMap()) }
    var currentLoadedStyleUri by remember { mutableStateOf<String?>(null) }

    fun setup3DStyle(style: Style, isDark: Boolean) {
        // 0. Bâtiments 3D extrudés (si non présents dans le style, ex: style sombre ou épuré)
        if (style.getLayer("building-3d") == null && style.getSource("openmaptiles") != null) {
            val b3d = FillExtrusionLayer("building-3d", "openmaptiles").apply {
                sourceLayer = "building"
                minZoom = 14f
                setFilter(Expression.not(Expression.has("hide_3d")))
                setProperties(
                    PropertyFactory.fillExtrusionBase(Expression.get("render_min_height")),
                    PropertyFactory.fillExtrusionHeight(Expression.get("render_height")),
                    PropertyFactory.fillExtrusionColor(if (isDark) "#2c333d" else "#d8d3c5"),
                    PropertyFactory.fillExtrusionOpacity(0.85f),
                    PropertyFactory.fillExtrusionVerticalGradient(true)
                )
            }
            style.addLayer(b3d)
        }

        // 1. Couches pour l'itinéraire et les étapes de tournée
        // Trajet d'approche
        val approachSource = GeoJsonSource(TRIP_APPROACH_SOURCE_ID)
        style.addSource(approachSource)

        val approachCasingLayer = LineLayer(TRIP_APPROACH_CASING_LAYER_ID, TRIP_APPROACH_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.lineColor(if (isDark) "#102027" else "#FFFFFF"),
                PropertyFactory.lineWidth(8f),
                PropertyFactory.lineOpacity(0.6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        }
        style.addLayer(approachCasingLayer)

        val approachLayer = LineLayer(TRIP_APPROACH_LAYER_ID, TRIP_APPROACH_SOURCE_ID).apply {
            setFilter(Expression.eq(Expression.get("dashed"), Expression.literal(false)))
            setProperties(
                PropertyFactory.lineColor("#00897B"),
                PropertyFactory.lineWidth(5.5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        }
        style.addLayer(approachLayer)

        val approachDashedLayer = LineLayer(TRIP_APPROACH_DASHED_LAYER_ID, TRIP_APPROACH_SOURCE_ID).apply {
            setFilter(Expression.eq(Expression.get("dashed"), Expression.literal(true)))
            setProperties(
                PropertyFactory.lineColor("#00897B"),
                PropertyFactory.lineWidth(5.5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineDasharray(arrayOf(2f, 2f))
            )
        }
        style.addLayer(approachDashedLayer)

        // Segments de la tournée principale
        val routeSource = GeoJsonSource(TRIP_ROUTE_SOURCE_ID)
        style.addSource(routeSource)

        val routeCasingLayer = LineLayer(TRIP_ROUTE_CASING_LAYER_ID, TRIP_ROUTE_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.lineColor(if (isDark) "#0D47A1" else "#FFFFFF"),
                PropertyFactory.lineWidth(9f),
                PropertyFactory.lineOpacity(0.6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        }
        style.addLayer(routeCasingLayer)

        val routeLayer = LineLayer(TRIP_ROUTE_LAYER_ID, TRIP_ROUTE_SOURCE_ID).apply {
            setFilter(Expression.eq(Expression.get("dashed"), Expression.literal(false)))
            setProperties(
                PropertyFactory.lineColor("#1976D2"),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        }
        style.addLayer(routeLayer)

        val routeDashedLayer = LineLayer(TRIP_ROUTE_DASHED_LAYER_ID, TRIP_ROUTE_SOURCE_ID).apply {
            setFilter(Expression.eq(Expression.get("dashed"), Expression.literal(true)))
            setProperties(
                PropertyFactory.lineColor("#1976D2"),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineDasharray(arrayOf(2f, 2f))
            )
        }
        style.addLayer(routeDashedLayer)

        // Flèches de sens posées à plat sur la route
        val chevronBitmap = (createTripArrowIcon(context, 0.0) as? BitmapDrawable)?.bitmap
        if (chevronBitmap != null && style.getImage("trip_direction_chevron") == null) {
            style.addImage("trip_direction_chevron", chevronBitmap)
        }

        val arrowsSource = GeoJsonSource(TRIP_ARROWS_SOURCE_ID)
        style.addSource(arrowsSource)

        val arrowsLayer = SymbolLayer(TRIP_ARROWS_LAYER_ID, TRIP_ARROWS_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.iconImage("trip_direction_chevron"),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconRotate(Expression.get("bearing")),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(1.0f)
            )
        }
        style.addLayer(arrowsLayer)

        // Pastilles numérotées des étapes (alignées avec la vue pour rester lisibles)
        val stepsSource = GeoJsonSource(TRIP_STEPS_SOURCE_ID)
        style.addSource(stepsSource)

        val stepsLayer = SymbolLayer(TRIP_STEPS_LAYER_ID, TRIP_STEPS_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(1.0f)
            )
        }
        style.addLayer(stepsLayer)

        // 2. Repère de localisation utilisateur personnalisé
        val density = context.resources.displayMetrics.density
        val markerBitmap = createCustomLocationMarkerBitmap(density, primaryColorInt)
        style.addImage(USER_ARROW_ICON_ID, markerBitmap)

        val userLocationSource = GeoJsonSource(USER_LOCATION_SOURCE_ID)
        style.addSource(userLocationSource)

        val userArrowLayer = SymbolLayer(USER_ARROW_LAYER_ID, USER_LOCATION_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.iconImage(USER_ARROW_ICON_ID),
                PropertyFactory.iconRotate(Expression.get("bearing")),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(1.0f)
            )
        }
        style.addLayer(userArrowLayer)

        // 3. Source et couches de symboles pour les antennes (pylônes) et clusters
        val antennasSource = GeoJsonSource(ANTENNAS_SOURCE_ID)
        style.addSource(antennasSource)

        // Pylônes d'antennes individuelles (ancrés au sol par le bas pour s'élever à l'échelle)
        val antennasLayer = SymbolLayer(ANTENNAS_LAYER_ID, ANTENNAS_SOURCE_ID).apply {
            setFilter(Expression.eq(Expression.get("isCluster"), Expression.literal(false)))
            setProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                // Échelle proportionnée aux immeubles et rues selon le zoom
                PropertyFactory.iconSize(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(10f, 0.40f),
                        Expression.stop(13f, 0.55f),
                        Expression.stop(15f, 0.72f),
                        Expression.stop(17f, 0.88f),
                        Expression.stop(18f, 1.0f)
                    )
                )
            )
        }
        style.addLayer(antennasLayer)

        // Clusters macro (ancrés au centre)
        val clustersLayer = SymbolLayer(CLUSTERS_LAYER_ID, ANTENNAS_SOURCE_ID).apply {
            setFilter(Expression.eq(Expression.get("isCluster"), Expression.literal(true)))
            setProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconSize(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(8f, 0.6f),
                        Expression.stop(12f, 0.8f),
                        Expression.stop(15f, 1.0f)
                    )
                )
            )
        }
        style.addLayer(clustersLayer)

        isStyleLoaded = true
        AppLogger.d("Map3DView", "3D Map style ($styleUri) loaded")
    }

    // Chargement et rechargement dynamique du style de carte 3D (clair, sombre, etc.)
    LaunchedEffect(styleUri, mapLibreMapInstance) {
        val map = mapLibreMapInstance ?: return@LaunchedEffect
        if (currentLoadedStyleUri == styleUri && isStyleLoaded) return@LaunchedEffect
        isStyleLoaded = false
        currentLoadedStyleUri = styleUri
        val isDark = styleUri.contains("dark")
        map.setStyle(Style.Builder().fromUri(styleUri)) { style ->
            setup3DStyle(style, isDark)
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            } catch (e: Exception) {
                AppLogger.w("Map3DView", "Error destroying MapView", e)
            }
        }
    }

    // Recentrage caméra
    LaunchedEffect(recenterTrigger) {
        val map = mapLibreMapInstance ?: return@LaunchedEffect
        if (recenterTrigger > 0L) {
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(centerLat, centerLon))
                        .zoom(zoom.coerceIn(2.0, 20.0))
                        .tilt(55.0)
                        .bearing(bearing.toDouble())
                        .build()
                )
            )
        }
    }

    // Position utilisateur (repère personnalisé au sol)
    LaunchedEffect(userLatitude, userLongitude, userHeading, isStyleLoaded, mapLibreMapInstance) {
        val map = mapLibreMapInstance ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        if (!isStyleLoaded) return@LaunchedEffect

        val source = style.getSourceAs<GeoJsonSource>(USER_LOCATION_SOURCE_ID) ?: return@LaunchedEffect
        if (userLatitude != null && userLongitude != null) {
            val pt = Point.fromLngLat(userLongitude, userLatitude)
            val feature = Feature.fromGeometry(pt)
            feature.addNumberProperty("bearing", userHeading)
            source.setGeoJson(FeatureCollection.fromFeatures(listOf(feature)))
        } else {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
    }

    // Cadrage automatique de la tournée complète en mode consultation
    var lastFramedPlanId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tripPlan?.id, tripMode, isStyleLoaded, mapLibreMapInstance) {
        val plan = tripPlan ?: return@LaunchedEffect
        val map = mapLibreMapInstance ?: return@LaunchedEffect
        if (!isStyleLoaded || tripMode != TRIP_MODE_VIEW || lastFramedPlanId == plan.id) return@LaunchedEffect
        val bbox = tripBoundingBox(plan) ?: return@LaunchedEffect
        lastFramedPlanId = plan.id
        try {
            val bounds = LatLngBounds.Builder()
                .include(LatLng(bbox[0], bbox[1]))
                .include(LatLng(bbox[2], bbox[3]))
                .build()
            val padding = (64 * context.resources.displayMetrics.density).toInt()
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding), 1000)
        } catch (e: Exception) {
            AppLogger.w("Map3DView", "Error framing trip plan in 3D", e)
        }
    }

    // Suivi GPS de navigation en mode 3D cockpit
    LaunchedEffect(userLatitude, userLongitude, userHeading, navCameraLocked, tripMode, isStyleLoaded, mapLibreMapInstance) {
        if (!isStyleLoaded || tripMode != TRIP_MODE_FOLLOW || !navCameraLocked) return@LaunchedEffect
        val map = mapLibreMapInstance ?: return@LaunchedEffect
        if (userLatitude == null || userLongitude == null) return@LaunchedEffect
        val cam = map.cameraPosition
        val targetZoom = if (cam.zoom < 15.0) 17.5 else cam.zoom
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(userLatitude, userLongitude))
                    .zoom(targetZoom)
                    .bearing(userHeading.toDouble())
                    .tilt(55.0)
                    .build()
            ),
            800
        )
    }

    // Mise à jour des tracés de trajets, chevrons et pastilles d'étapes dans MapLibre
    LaunchedEffect(
        tripPlan,
        tripApproachPoints,
        tripApproachRouted,
        tripProgress,
        tripMode,
        isStyleLoaded,
        mapLibreMapInstance
    ) {
        val map = mapLibreMapInstance ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        if (!isStyleLoaded) return@LaunchedEffect

        val approachSource = style.getSourceAs<GeoJsonSource>(TRIP_APPROACH_SOURCE_ID) ?: return@LaunchedEffect
        val routeSource = style.getSourceAs<GeoJsonSource>(TRIP_ROUTE_SOURCE_ID) ?: return@LaunchedEffect
        val arrowsSource = style.getSourceAs<GeoJsonSource>(TRIP_ARROWS_SOURCE_ID) ?: return@LaunchedEffect
        val stepsSource = style.getSourceAs<GeoJsonSource>(TRIP_STEPS_SOURCE_ID) ?: return@LaunchedEffect

        val renderData = withContext(Dispatchers.Default) {
            val approachFeatures = mutableListOf<Feature>()
            val routeFeatures = mutableListOf<Feature>()
            val arrowFeatures = mutableListOf<Feature>()
            val stepFeatures = mutableListOf<Feature>()
            val images = mutableMapOf<String, Bitmap>()

            // 1. Trajet d'approche (position courante vers 1ère étape)
            val approach = tripApproachPoints
            if (approach != null && approach.size >= 2) {
                val pts = approach.map { Point.fromLngLat(it[1], it[0]) }
                val feat = Feature.fromGeometry(LineString.fromLngLats(pts))
                feat.addBooleanProperty("dashed", !tripApproachRouted)
                approachFeatures.add(feat)
            }

            // 2. Segments de tournée et flèches de direction
            val plan = tripPlan
            if (plan != null) {
                plan.legPairs().forEachIndexed { ordinal, (fromIndex, toIndex) ->
                    val computed = plan.legBetween(fromIndex, toIndex)?.points()?.takeIf { it.size >= 2 }
                    val rawPoints = computed ?: listOf(
                        doubleArrayOf(plan.steps[fromIndex].latitude, plan.steps[fromIndex].longitude),
                        doubleArrayOf(plan.steps[toIndex].latitude, plan.steps[toIndex].longitude)
                    )

                    val remainingPoints = when {
                        tripProgress == null -> rawPoints
                        ordinal < tripProgress.legOrdinal -> emptyList()
                        ordinal > tripProgress.legOrdinal -> rawPoints
                        else -> remainingLegPoints(rawPoints, tripProgress.distanceIntoLegMeters)
                    }

                    if (remainingPoints.size >= 2) {
                        val pts = remainingPoints.map { Point.fromLngLat(it[1], it[0]) }
                        val feat = Feature.fromGeometry(LineString.fromLngLats(pts))
                        feat.addBooleanProperty("dashed", computed == null)
                        routeFeatures.add(feat)
                    }

                    // Flèches de direction statiques posées à plat sur la route (en suivi de navigation)
                    val staticArrows = if (tripMode == TRIP_MODE_FOLLOW) {
                        tripDirectionArrows(rawPoints)
                    } else {
                        emptyList()
                    }

                    val visibleArrows = when {
                        tripProgress == null -> staticArrows
                        ordinal < tripProgress.legOrdinal -> emptyList()
                        ordinal > tripProgress.legOrdinal -> staticArrows
                        else -> staticArrows.filter { it.distanceAlongMeters > tripProgress.distanceIntoLegMeters }
                    }

                    visibleArrows.forEach { arrow ->
                        val feat = Feature.fromGeometry(Point.fromLngLat(arrow.longitude, arrow.latitude))
                        feat.addNumberProperty("bearing", arrow.bearingDegrees.toFloat())
                        arrowFeatures.add(feat)
                    }
                }

                // 3. Pastilles numérotées des étapes
                plan.steps.forEachIndexed { index, step ->
                    val iconKey = "trip_step_${index + 1}_${step.visitedAtMillis != null}"
                    if (!images.containsKey(iconKey)) {
                        val drawable = createTripStepIcon(context, index + 1, step.visitedAtMillis != null)
                        val bmp = (drawable as? BitmapDrawable)?.bitmap
                        if (bmp != null) {
                            images[iconKey] = bmp
                        }
                    }

                    val feat = Feature.fromGeometry(Point.fromLngLat(step.longitude, step.latitude))
                    feat.addStringProperty("icon", iconKey)
                    feat.addNumberProperty("stepIndex", index)
                    stepFeatures.add(feat)
                }
            }

            TripRenderData(
                approachCollection = FeatureCollection.fromFeatures(approachFeatures),
                routeCollection = FeatureCollection.fromFeatures(routeFeatures),
                arrowsCollection = FeatureCollection.fromFeatures(arrowFeatures),
                stepsCollection = FeatureCollection.fromFeatures(stepFeatures),
                stepImages = images
            )
        }

        renderData.stepImages.forEach { (key, bmp) ->
            if (style.getImage(key) == null) {
                style.addImage(key, bmp)
            }
        }

        approachSource.setGeoJson(renderData.approachCollection)
        routeSource.setGeoJson(renderData.routeCollection)
        arrowsSource.setGeoJson(renderData.arrowsCollection)
        stepsSource.setGeoJson(renderData.stepsCollection)
    }

    // Pas de rotation de la caméra arrondi à 5° pour limiter les reconstructions de textures
    val bearingStep = (bearing / 5f).roundToInt() * 5

    // Mise à jour des antennes et clusters dans la couche GeoJSON MapLibre
    LaunchedEffect(
        antennas,
        sitesHs,
        clusterStrength,
        satelliteMarkerContrast,
        bearingStep,
        AppConfig.showAzimuths.value,
        AppConfig.showAzimuthsCone.value,
        AppConfig.showTechnoFH.value,
        isStyleLoaded,
        mapLibreMapInstance
    ) {
        val map = mapLibreMapInstance ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        if (!isStyleLoaded) return@LaunchedEffect

        val (collection, imagesToAdd, newSitesMap) = withContext(Dispatchers.Default) {
            val selectedOperators = AppConfig.selectedOperatorKeys.value
            val showSitesInService = AppConfig.showSitesInService.value
            val showSitesOutOfService = AppConfig.showSitesOutOfService.value
            val showProjectSites = AppConfig.showProjectSites.value
            val defaultOp = AppConfig.defaultOperator.value
            val showAzimuthLines = AppConfig.showAzimuths.value
            val showAzimuthCones = PowerProfile.drawAzimuthCones
            val showFh = AppConfig.showTechnoFH.value

            val hsOperatorMap = buildHsOperatorMap(sitesHs)
            val zbPotentialHs = zbPotentialOutages(antennas, sitesHs)
            val hsColorOperatorMap = if (zbPotentialHs.isEmpty()) {
                hsOperatorMap
            } else {
                buildHsOperatorMap(sitesHs + zbPotentialHs)
            }

            val clusterAntennas = antennas.filter { it.idAnfr.startsWith("CLUSTER_") }
            val directAntennas = antennas.filterNot { it.idAnfr.startsWith("CLUSTER_") }

            // Chargement asynchrone des informations réelles de supports ANFR (hauteur et nature)
            val visibleAnfrIds = directAntennas.map { it.idAnfr }.distinct()
            val supportInfoMap = if (visibleAnfrIds.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    try {
                        val dao = AppDatabase.getDatabase(context).geoTowerDao()
                        visibleAnfrIds.chunked(500).flatMap { dao.getSupportInfoForAnfrIds(it) }
                            .groupBy { it.idAnfr }
                            .mapValues { (_, rows) -> rows.first() }
                    } catch (e: Exception) {
                        AppLogger.w("Map3DView", "Failed to load support info", e)
                        emptyMap()
                    }
                }
            } else {
                emptyMap()
            }

            val features = mutableListOf<Feature>()
            val images = mutableMapOf<String, Bitmap>()
            val siteLookup = mutableMapOf<String, LocalisationEntity>()
            val density = context.resources.displayMetrics.density

            // 1. Clusters (vue macro)
            clusterAntennas.forEach { fakeAntenna ->
                if (fakeAntenna.latitude == 0.0 && fakeAntenna.longitude == 0.0) return@forEach
                val count = fakeAntenna.idAnfr.removePrefix("CLUSTER_").toIntOrNull() ?: 1
                val activeOps = visibleOperatorKeysForAntenna(
                    antenna = fakeAntenna,
                    hsOperatorMap = hsOperatorMap,
                    showSitesInService = showSitesInService,
                    showSitesOutOfService = showSitesOutOfService,
                    showProjectSites = showProjectSites,
                    selectedOperatorKeys = selectedOperators
                )
                val iconKey = "cluster_${activeOps.sorted().joinToString("_")}_${count}_${defaultOp}_${satelliteMarkerContrast}_$clusterStrength"
                if (!images.containsKey(iconKey)) {
                    val clusterDrawable = MapUtils.createClusterIcon(
                        context,
                        activeOps,
                        count,
                        defaultOp,
                        satelliteMarkerContrast,
                        clusterStrength = clusterStrength
                    )
                    images[iconKey] = clusterDrawable.bitmap
                }

                val feat = Feature.fromGeometry(Point.fromLngLat(fakeAntenna.longitude, fakeAntenna.latitude))
                feat.addStringProperty("icon", iconKey)
                feat.addStringProperty("idAnfr", fakeAntenna.idAnfr)
                feat.addBooleanProperty("isCluster", true)
                features.add(feat)
            }

            // 2. Antennes physiques directes avec pylône et azimuts au sommet
            val groupedSites = directAntennas.groupBy { it.physicalSiteKey() }.values.take(PowerProfile.mapMarkerCap)
            groupedSites.forEach { siteAntennas ->
                val filteredSiteAntennas = siteAntennas.mapNotNull { antenna ->
                    val activeOps = visibleOperatorKeysForAntenna(
                        antenna = antenna,
                        hsOperatorMap = hsOperatorMap,
                        showSitesInService = showSitesInService,
                        showSitesOutOfService = showSitesOutOfService,
                        showProjectSites = showProjectSites,
                        selectedOperatorKeys = selectedOperators
                    )
                    val notHiddenOps = activeOps.filterNot { operatorKey ->
                        HiddenSitesStore.isHidden(context, antenna.physicalSiteKey(), operatorKey)
                    }
                    if (notHiddenOps.isEmpty()) null else antenna.copy(operateur = notHiddenOps.joinToString(", "))
                }
                if (filteredSiteAntennas.isEmpty()) return@forEach

                val mainAntenna = filteredSiteAntennas.first()
                if (mainAntenna.latitude == 0.0 && mainAntenna.longitude == 0.0) return@forEach

                val isHs = filteredSiteAntennas.any { antenna ->
                    hasVisibleHsOperator(antenna, hsColorOperatorMap)
                }

                val opsOnSite = filteredSiteAntennas.mapNotNull { it.operateur }
                    .flatMap { OperatorColors.keysFor(it) }
                    .distinct()
                    .sorted()

                // Extraction des azimuts de ce site
                val mobileAzimuths = mutableMapOf<Float, MutableSet<String>>()
                val fhAzimuths = mutableMapOf<Float, MutableSet<String>>()

                filteredSiteAntennas.forEach { antenna ->
                    val operatorKeys = OperatorColors.keysFor(antenna.operateur)
                    if (operatorKeys.isEmpty()) return@forEach

                    if (!antenna.azimuts.isNullOrBlank()) {
                        antenna.azimuts.split(",").mapNotNull { it.trim().toFloatOrNull() }.forEach { az ->
                            mobileAzimuths.getOrPut(az) { mutableSetOf() }.addAll(operatorKeys)
                        }
                    }

                    if (showFh && !antenna.azimutsFh.isNullOrBlank()) {
                        antenna.azimutsFh.split(",").mapNotNull { it.trim().toFloatOrNull() }.forEach { az ->
                            fhAzimuths.getOrPut(az) { mutableSetOf() }.addAll(operatorKeys)
                        }
                    }
                }

                val azSig = if (showAzimuthLines) mobileAzimuths.entries.sortedBy { it.key }.joinToString(";") { "${it.key.toInt()}:${it.value.sorted().joinToString("+")}" } else ""
                val fhSig = if (showFh) fhAzimuths.entries.sortedBy { it.key }.joinToString(";") { "${it.key.toInt()}:${it.value.sorted().joinToString("+")}" } else ""

                // Support ANFR pour ce site : hauteur et nature
                val siteSupportInfo = siteAntennas.mapNotNull { supportInfoMap[it.idAnfr] }.maxByOrNull { it.hauteur ?: 0.0 }
                val siteHeight = siteSupportInfo?.hauteur ?: 25.0
                val visualType = resolveSupportVisualType(siteSupportInfo?.nature)
                val heightBucket = ((siteHeight / 2.0).roundToInt() * 2).coerceIn(8, 76)
                val coneSig = if (showAzimuthCones) "1" else "0"

                val iconKey = "pylon_ant_${visualType.name}_${isHs}_${satelliteMarkerContrast}_${clusterStrength}_${defaultOp}_${opsOnSite.joinToString("_")}_h${heightBucket}_b${bearingStep}_az[$azSig]_fh[$fhSig]_c$coneSig"
                if (!images.containsKey(iconKey)) {
                    val cached = pylonMarkerCache.get(iconKey)
                    if (cached != null) {
                        images[iconKey] = cached
                    } else {
                        val baseIcon = MapUtils.createAdaptiveMarker(
                            context,
                            filteredSiteAntennas,
                            false,
                            defaultOp,
                            satelliteContrast = satelliteMarkerContrast,
                            clusterStrength = clusterStrength
                        )
                        val iconDrawable = if (isHs) {
                            createHsMarkerIcon(context, baseIcon, clusterStrength)
                        } else {
                            baseIcon
                        }
                        val cropped = cropToMarkerCircle(iconDrawable.bitmap, clusterStrength)
                        val pylonBitmap = createPylonAntennaMarkerBitmap(
                            circleBitmap = cropped,
                            density = density,
                            azimuths = mobileAzimuths,
                            fhAzimuths = fhAzimuths,
                            defaultOp = defaultOp,
                            showLines = showAzimuthLines,
                            showCones = showAzimuthCones,
                            showFh = showFh,
                            cameraBearing = bearingStep.toFloat(),
                            supportHeightMeters = siteHeight,
                            visualType = visualType,
                            satelliteContrast = satelliteMarkerContrast
                        )
                        pylonMarkerCache.put(iconKey, pylonBitmap)
                        images[iconKey] = pylonBitmap
                    }
                }

                val feat = Feature.fromGeometry(Point.fromLngLat(mainAntenna.longitude, mainAntenna.latitude))
                feat.addStringProperty("icon", iconKey)
                feat.addStringProperty("idAnfr", mainAntenna.idAnfr)
                feat.addBooleanProperty("isCluster", false)
                features.add(feat)

                siteLookup[mainAntenna.idAnfr] = mainAntenna
            }

            Triple(FeatureCollection.fromFeatures(features), images, siteLookup)
        }

        // Ajout des images manquantes au style MapLibre
        imagesToAdd.forEach { (iconId, bitmap) ->
            if (style.getImage(iconId) == null) {
                style.addImage(iconId, bitmap)
            }
        }

        sitesMap = newSitesMap
        val source = style.getSourceAs<GeoJsonSource>(ANTENNAS_SOURCE_ID)
        source?.setGeoJson(collection)
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                mapView.apply {
                    getMapAsync { map ->
                        mapLibreMapInstance = map

                        // Désactive les widgets natifs MapLibre (logo, attribution & boussole)
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = false
                        map.uiSettings.isCompassEnabled = false

                        val initialPosition = CameraPosition.Builder()
                            .target(LatLng(centerLat, centerLon))
                            .zoom(zoom.coerceIn(2.0, 20.0))
                            .tilt(55.0)
                            .bearing(bearing.toDouble())
                            .build()
                        map.cameraPosition = initialPosition

                        // Détection des clics sur les étapes, antennes et clusters, ou la carte
                        map.addOnMapClickListener { latLng ->
                            val screenPoint = map.projection.toScreenLocation(latLng)
                            val touchSlop = 36f
                            val rect = RectF(
                                screenPoint.x - touchSlop,
                                screenPoint.y - touchSlop,
                                screenPoint.x + touchSlop,
                                screenPoint.y + touchSlop
                            )

                            // 1. Clic sur une pastille d'étape de trajet
                            val renderedSteps = map.queryRenderedFeatures(rect, TRIP_STEPS_LAYER_ID)
                            if (renderedSteps.isNotEmpty()) {
                                val stepIndex = renderedSteps[0].getNumberProperty("stepIndex")?.toInt()
                                if (stepIndex != null && onStepClick != null) {
                                    onStepClick(stepIndex)
                                    return@addOnMapClickListener true
                                }
                            }

                            // 2. Clic sur antennes ou clusters
                            val rendered = map.queryRenderedFeatures(rect, ANTENNAS_LAYER_ID, CLUSTERS_LAYER_ID)
                            if (rendered.isNotEmpty()) {
                                val feat = rendered[0]
                                val isCluster = feat.getBooleanProperty("isCluster") ?: false
                                if (isCluster) {
                                    val geom = feat.geometry() as? Point
                                    if (geom != null) {
                                        val cam = map.cameraPosition
                                        map.animateCamera(
                                            CameraUpdateFactory.newCameraPosition(
                                                CameraPosition.Builder()
                                                    .target(LatLng(geom.latitude(), geom.longitude()))
                                                    .zoom(cam.zoom + 1.5)
                                                    .tilt(cam.tilt)
                                                    .bearing(cam.bearing)
                                                    .build()
                                            )
                                        )
                                        return@addOnMapClickListener true
                                    }
                                } else {
                                    val idAnfr = feat.getStringProperty("idAnfr")
                                    val match = sitesMap[idAnfr] ?: antennas.find { it.idAnfr == idAnfr }
                                    if (match != null) {
                                        onSiteClick(match)
                                        return@addOnMapClickListener true
                                    }
                                }
                            }

                            // 3. Clic sur la carte libre (ex: ajout d'étape manuelle en édition)
                            if (onMapClick != null) {
                                onMapClick(latLng.latitude, latLng.longitude)
                                return@addOnMapClickListener true
                            }

                            false
                        }

                        // Détection de l'interaction utilisateur pour libérer le suivi caméra
                        map.addOnCameraMoveStartedListener { reason ->
                            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                                onUserInteraction?.invoke()
                            }
                        }

                        // Notification de déplacement caméra pour mise à jour viewport et rechargement des antennes
                        map.addOnCameraIdleListener {
                            val cam = map.cameraPosition
                            val target = cam.target
                            if (target != null) {
                                onCameraChange(
                                    target.latitude,
                                    target.longitude,
                                    cam.zoom,
                                    cam.bearing.toFloat()
                                )
                                val visibleRegion = map.projection.visibleRegion
                                val bounds = visibleRegion.latLngBounds
                                onViewportChanged(
                                    cam.zoom,
                                    bounds.latitudeNorth,
                                    bounds.longitudeEast,
                                    bounds.latitudeSouth,
                                    bounds.longitudeWest
                                )
                            }
                        }
                    }
                }
            }
        )

        // Fil animé des chevrons de direction en mode 3D (consultation / édition)
        if (isStyleLoaded && tripMode != TRIP_MODE_FOLLOW && tripPlan != null) {
            val flowTransition = rememberInfiniteTransition(label = "tripFlow3D")
            val flowCursor by flowTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(TRIP_FLOW_CYCLE_MS, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "tripFlowCursor3D"
            )
            val track = remember(tripPlan) { tripFlowTrack(tripPlan) }
            if (track.size >= 2) {
                ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                    val map = mapLibreMapInstance ?: return@ComposeCanvas
                    val advance = flowCursor * TRIP_FLOW_STRIDE
                    val ratio = advance - advance.toInt()
                    val lastIndex = track.size - 1
                    val fadeSpan = TRIP_FLOW_STRIDE.toFloat()

                    var position = advance
                    while (position < lastIndex) {
                        val base = position.toInt()
                        val from = track[base]
                        val to = track[base + 1]
                        val lat = from.latitude + (to.latitude - from.latitude) * ratio
                        val lon = from.longitude + (to.longitude - from.longitude) * ratio

                        val screenPoint = map.projection.toScreenLocation(LatLng(lat, lon))
                        val x = screenPoint.x
                        val y = screenPoint.y

                        // Calcul de l'orientation exacte du vecteur sur l'écran en 3D
                        val nextLat = to.latitude
                        val nextLon = to.longitude
                        val nextScreenPoint = map.projection.toScreenLocation(LatLng(nextLat, nextLon))
                        val dx = nextScreenPoint.x - x
                        val dy = nextScreenPoint.y - y
                        val screenAngle = if (dx * dx + dy * dy > 0.01f) {
                            (Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f)
                        } else {
                            from.bearingDegrees.toFloat() - map.cameraPosition.bearing.toFloat()
                        }

                        val onScreen = x > -60f && y > -60f &&
                            x < size.width + 60f && y < size.height + 60f
                        if (onScreen) {
                            val alpha = minOf(
                                1f,
                                position / fadeSpan,
                                (lastIndex - position) / fadeSpan
                            ).coerceIn(0f, 1f)

                            drawTripFlowChevron(
                                centerX = x,
                                centerY = y,
                                angleDegrees = screenAngle,
                                alpha = alpha
                            )
                        }
                        position += TRIP_FLOW_STRIDE
                    }
                }
            }
        }
    }
}
