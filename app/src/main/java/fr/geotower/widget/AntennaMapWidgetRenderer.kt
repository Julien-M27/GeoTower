package fr.geotower.widget

import android.content.Context
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.ColorUtils
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.utils.AppConfig
import fr.geotower.utils.MapUtils
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.isNetworkAvailable
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.rendertheme.InternalRenderTheme
import org.osmdroid.mapsforge.MapsForgeTileSource
import org.osmdroid.util.MapTileIndex
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sinh

internal const val PREF_WIDGET_MAP_IMAGE_PATH = "widget_map_image_path"
internal const val PREF_WIDGET_MAP_IMAGE_WIDE_PATH = "widget_map_image_wide_path"
internal const val PREF_WIDGET_MAP_IMAGE_SQUARE_PATH = "widget_map_image_square_path"
internal const val PREF_WIDGET_MAP_IMAGE_WIDE_EXPANDED_PATH = "widget_map_image_wide_expanded_path"
internal const val PREF_WIDGET_MAP_IMAGE_SQUARE_EXPANDED_PATH = "widget_map_image_square_expanded_path"
internal const val PREF_WIDGET_MAP_SITE_COUNT = "widget_map_site_count"
internal const val PREF_WIDGET_MAP_CENTER_LAT = "widget_map_center_lat"
internal const val PREF_WIDGET_MAP_CENTER_LON = "widget_map_center_lon"

data class WidgetMapData(
    val userLat: Double,
    val userLon: Double,
    val sites: List<WidgetMapSiteData>
)

data class WidgetMapSiteData(
    val id: String,
    val operatorKeys: List<String>,
    val distanceMeters: Float,
    val distanceLabel: String,
    val latitude: Double,
    val longitude: Double,
    val hasOutage: Boolean = false,
    val antennas: List<WidgetMapAntennaData> = emptyList()
)

data class WidgetMapAntennaData(
    val id: String,
    val operatorName: String?,
    val azimuts: String?,
    val azimutsFh: String?
)

data class WidgetMapImagePaths(
    val widePath: String,
    val squarePath: String,
    val wideExpandedPath: String,
    val squareExpandedPath: String
)

data class WidgetMapRenderOptions(
    val defaultOperator: String,
    val showAzimuths: Boolean,
    val showAzimuthCones: Boolean,
    val showTechnoFh: Boolean
)

object AntennaMapWidgetRenderer {
    private const val TILE_SIZE = 256
    private const val METERS_PER_DEGREE_LAT = 111_320.0
    private const val MIN_ZOOM = 4
    private const val MAX_ZOOM = 17
    private val WIDE_SPEC = RenderSpec(
        fileSuffix = "wide",
        width = 1280,
        height = 768,
        logicalWidth = 640,
        logicalHeight = 384,
        paddingPx = 72.0,
        viewportScale = 1.0,
        renderZoomBoost = 1
    )
    private val WIDE_EXPANDED_SPEC = RenderSpec(
        fileSuffix = "wide_expanded",
        width = 1280,
        height = 768,
        logicalWidth = 640,
        logicalHeight = 384,
        paddingPx = 72.0,
        viewportScale = 1.3,
        renderZoomBoost = 2
    )
    private val SQUARE_SPEC = RenderSpec(
        fileSuffix = "square",
        width = 960,
        height = 960,
        logicalWidth = 480,
        logicalHeight = 480,
        paddingPx = 72.0,
        viewportScale = 1.0,
        renderZoomBoost = 1
    )
    private val SQUARE_EXPANDED_SPEC = RenderSpec(
        fileSuffix = "square_expanded",
        width = 960,
        height = 960,
        logicalWidth = 480,
        logicalHeight = 480,
        paddingPx = 72.0,
        viewportScale = 1.3,
        renderZoomBoost = 2
    )

    private fun defaultRenderOptions(): WidgetMapRenderOptions {
        return WidgetMapRenderOptions(
            defaultOperator = AppConfig.defaultOperator.value,
            showAzimuths = AppConfig.showAzimuths.value,
            showAzimuthCones = AppConfig.showAzimuthsCone.value,
            showTechnoFh = AppConfig.showTechnoFH.value
        )
    }

    fun renderAndSaveVariants(
        context: Context,
        data: WidgetMapData,
        mapProvider: Int,
        ignStyle: Int,
        options: WidgetMapRenderOptions = defaultRenderOptions()
    ): WidgetMapImagePaths {
        return WidgetMapImagePaths(
            widePath = renderAndSave(context, data, mapProvider, ignStyle, WIDE_SPEC, options),
            squarePath = renderAndSave(context, data, mapProvider, ignStyle, SQUARE_SPEC, options),
            wideExpandedPath = renderAndSave(context, data, mapProvider, ignStyle, WIDE_EXPANDED_SPEC, options),
            squareExpandedPath = renderAndSave(context, data, mapProvider, ignStyle, SQUARE_EXPANDED_SPEC, options)
        )
    }

    private fun renderAndSave(
        context: Context,
        data: WidgetMapData,
        mapProvider: Int,
        ignStyle: Int,
        spec: RenderSpec,
        options: WidgetMapRenderOptions
    ): String {
        val dir = File(context.filesDir, "widgets")
        if (!dir.exists()) dir.mkdirs()

        val file = File(dir, "antenna_map_widget_${spec.fileSuffix}.png")
        val bitmap = render(context, data, mapProvider, ignStyle, spec, options)
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 92, output)
        }
        bitmap.recycle()
        return file.absolutePath
    }

    private fun render(
        context: Context,
        data: WidgetMapData,
        mapProvider: Int,
        ignStyle: Int,
        spec: RenderSpec = WIDE_SPEC,
        options: WidgetMapRenderOptions = defaultRenderOptions()
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(spec.width, spec.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val density = spec.width.toFloat() / spec.logicalWidth
        val tileSource = tileSourceFor(context, mapProvider, ignStyle)
        val viewport = widgetViewport(data, spec, tileSource.maxRenderZoom)

        try {
            val tilesDrawn = drawTileBackground(context, canvas, viewport, tileSource, spec)
            if (tilesDrawn == 0) {
                drawFallbackBackground(canvas, density, spec)
            }

            drawSites(context, canvas, data, viewport, density, spec, options)
            drawUserMarker(canvas, viewport.projectX(data.userLon), viewport.projectY(data.userLat), density)
        } finally {
            tileSource.mapsForgeTileSource?.dispose()
        }
        return bitmap
    }

    private fun widgetViewport(data: WidgetMapData, spec: RenderSpec, maxRenderZoom: Int): WidgetViewport {
        val fitSites = chooseFitSites(data)
        val points = buildList {
            add(data.userLat to data.userLon)
            fitSites.forEach { add(it.latitude to it.longitude) }
        }

        val logicalZoom = (MAX_ZOOM downTo MIN_ZOOM).firstOrNull { candidateZoom ->
            val bounds = pixelBounds(points, candidateZoom)
            bounds.width <= spec.logicalWidth - spec.paddingPx * 2.0 &&
                bounds.height <= spec.logicalHeight - spec.paddingPx * 2.0
        } ?: MIN_ZOOM

        val bounds = pixelBounds(points, logicalZoom)
        val centerPixelX = bounds.centerX
        val centerPixelY = bounds.centerY
        val centerLat = pixelYToLat(centerPixelY, logicalZoom)
        val centerLon = pixelXToLon(centerPixelX, logicalZoom)
        val renderZoom = (logicalZoom + spec.renderZoomBoost).coerceAtMost(maxRenderZoom)
        val effectiveZoomBoost = (renderZoom - logicalZoom).coerceAtLeast(0)
        val sourcePixelsPerOutputPixel =
            ((1 shl effectiveZoomBoost).toDouble() * spec.viewportScale) / spec.outputDensity
        val renderCenterPixelX = lonToPixelX(centerLon, renderZoom)
        val renderCenterPixelY = latToPixelY(centerLat, renderZoom)
        val topLeftX = renderCenterPixelX - spec.width * sourcePixelsPerOutputPixel / 2.0
        val topLeftY = renderCenterPixelY - spec.height * sourcePixelsPerOutputPixel / 2.0

        return WidgetViewport(
            zoom = renderZoom,
            logicalZoom = logicalZoom,
            centerLat = centerLat,
            centerLon = centerLon,
            topLeftPixelX = topLeftX,
            topLeftPixelY = topLeftY,
            sourcePixelsPerOutputPixel = sourcePixelsPerOutputPixel
        )
    }

    private fun chooseFitSites(data: WidgetMapData): List<WidgetMapSiteData> {
        val sortedSites = data.sites.sortedBy { it.distanceMeters }
        val nearest = sortedSites.firstOrNull() ?: return emptyList()
        val targetDistance = max(nearest.distanceMeters * 2.2f, 900f)
        val nearbyCluster = sortedSites
            .takeWhile { it.distanceMeters <= targetDistance }
            .take(6)
        return nearbyCluster.ifEmpty { listOf(nearest) }
    }

    private fun drawTileBackground(
        context: Context,
        canvas: Canvas,
        viewport: WidgetViewport,
        source: WidgetTileSource,
        spec: RenderSpec
    ): Int {
        val tilesPerAxis = 1 shl viewport.zoom
        val sourceOriginX = floor(viewport.topLeftPixelX).toInt()
        val sourceOriginY = floor(viewport.topLeftPixelY).toInt()
        val sourceWidth = ceil(
            viewport.topLeftPixelX + spec.width * viewport.sourcePixelsPerOutputPixel - sourceOriginX
        ).toInt().coerceAtLeast(1)
        val sourceHeight = ceil(
            viewport.topLeftPixelY + spec.height * viewport.sourcePixelsPerOutputPixel - sourceOriginY
        ).toInt().coerceAtLeast(1)
        val startTileX = floor(sourceOriginX / TILE_SIZE.toDouble()).toInt()
        val endTileX = floor((sourceOriginX + sourceWidth) / TILE_SIZE.toDouble()).toInt()
        val startTileY = floor(sourceOriginY / TILE_SIZE.toDouble()).toInt()
        val endTileY = floor((sourceOriginY + sourceHeight) / TILE_SIZE.toDouble()).toInt()
        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = if (source.invertColors) MapUtils.getInvertFilter() else null
        }
        val scalePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val sourceBitmap = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888)
        val sourceCanvas = Canvas(sourceBitmap)

        var drawn = 0
        for (tileY in startTileY..endTileY) {
            if (tileY !in 0 until tilesPerAxis) continue
            for (tileXRaw in startTileX..endTileX) {
                val tileX = floorMod(tileXRaw, tilesPerAxis)
                val tileBitmap = loadTile(context, source, viewport.zoom, tileX, tileY) ?: continue
                val dstLeft = (tileXRaw * TILE_SIZE - sourceOriginX).toFloat()
                val dstTop = (tileY * TILE_SIZE - sourceOriginY).toFloat()
                sourceCanvas.drawBitmap(tileBitmap, dstLeft, dstTop, tilePaint)
                drawn++
            }
        }

        if (drawn > 0) {
            val offsetX = viewport.topLeftPixelX - sourceOriginX
            val offsetY = viewport.topLeftPixelY - sourceOriginY
            val dst = RectF(
                (-offsetX / viewport.sourcePixelsPerOutputPixel).toFloat(),
                (-offsetY / viewport.sourcePixelsPerOutputPixel).toFloat(),
                ((sourceWidth - offsetX) / viewport.sourcePixelsPerOutputPixel).toFloat(),
                ((sourceHeight - offsetY) / viewport.sourcePixelsPerOutputPixel).toFloat()
            )
            canvas.drawBitmap(sourceBitmap, Rect(0, 0, sourceWidth, sourceHeight), dst, scalePaint)
        }
        sourceBitmap.recycle()
        return drawn
    }

    private fun loadTile(
        context: Context,
        source: WidgetTileSource,
        zoom: Int,
        x: Int,
        y: Int
    ): Bitmap? {
        val tileFile = File(context.cacheDir, "widget_tiles/${source.cacheKey}/$zoom/$x/$y.png")
        if (tileFile.isFile) {
            BitmapFactory.decodeFile(tileFile.absolutePath)?.let { return it }
        }

        source.mapsForgeTileSource?.let { mapsForgeSource ->
            return runCatching {
                val drawable = mapsForgeSource.renderTile(MapTileIndex.getTileIndex(zoom, x, y))
                val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return@runCatching null
                tileFile.parentFile?.mkdirs()
                FileOutputStream(tileFile).use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 92, output)
                }
                bitmap
            }.getOrNull()
        }

        return runCatching {
            val urlFor = source.urlFor ?: return@runCatching null
            val connection = (URL(urlFor(zoom, x, y)).openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 3500
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "${context.packageName} GeoTower widget")
            }
            connection.inputStream.use { input ->
                val bytes = input.readBytes()
                if (bytes.isEmpty()) return@runCatching null
                tileFile.parentFile?.mkdirs()
                tileFile.writeBytes(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
    }

    private fun drawFallbackBackground(canvas: Canvas, density: Float, spec: RenderSpec) {
        canvas.drawColor(Color.rgb(18, 29, 40))
        val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(42, 224, 238, 244)
            strokeWidth = 3f * density
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        val minorRoadPaint = Paint(roadPaint).apply {
            color = Color.argb(24, 224, 238, 244)
            strokeWidth = 2f * density
        }

        for (offset in -320..960 step 160) {
            canvas.drawLine(offset.toFloat(), 0f, offset + 520f, spec.height.toFloat(), minorRoadPaint)
            canvas.drawLine(offset.toFloat(), spec.height.toFloat(), offset + 580f, 0f, minorRoadPaint)
        }

        canvas.drawLine(-30f, spec.height * 0.75f, spec.width + 30f, spec.height * 0.55f, roadPaint)
    }

    private fun drawSites(
        context: Context,
        canvas: Canvas,
        data: WidgetMapData,
        viewport: WidgetViewport,
        density: Float,
        spec: RenderSpec,
        options: WidgetMapRenderOptions
    ) {
        data.sites
            .map { site ->
                ProjectedSite(
                    site = site,
                    x = viewport.projectX(site.longitude),
                    y = viewport.projectY(site.latitude)
                )
            }
            .filter { it.x in -32f..(spec.width + 32f) && it.y in -32f..(spec.height + 32f) }
            .sortedByDescending { it.site.distanceMeters }
            .forEach { projected -> drawAntennaMarker(context, canvas, projected, density, viewport, options) }
    }

    private fun drawAntennaMarker(
        context: Context,
        canvas: Canvas,
        projected: ProjectedSite,
        density: Float,
        viewport: WidgetViewport,
        options: WidgetMapRenderOptions
    ) {
        val site = projected.site
        val x = projected.x
        val y = projected.y
        val antennas = site.toLocalisationEntities()

        drawAntennaAzimuths(canvas, x, y, antennas, density, viewport.logicalZoom, options)

        val marker = MapUtils.createAdaptiveMarker(
            context = context,
            siteAntennas = antennas,
            showAzimuths = false,
            defaultOp = options.defaultOperator
        )
        val markerBitmap = marker.bitmap ?: return
        val iconSize = 72f * density
        val dst = RectF(
            x - iconSize / 2f,
            y - iconSize / 2f,
            x + iconSize / 2f,
            y + iconSize / 2f
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(markerBitmap, null, dst, paint)
        if (site.hasOutage) {
            drawHsBadge(canvas, x, y, density)
        }
    }

    private fun drawHsBadge(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        density: Float
    ) {
        val radius = 16f * density
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F5F5F5")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, radius, maskPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E53935")
            textSize = 24f * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(
            "!",
            centerX,
            centerY - (textPaint.ascent() + textPaint.descent()) / 2f,
            textPaint
        )
    }

    private fun drawAntennaAzimuths(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        antennas: List<LocalisationEntity>,
        density: Float,
        logicalZoom: Int,
        options: WidgetMapRenderOptions
    ) {
        if (logicalZoom < 14 || (!options.showAzimuths && !options.showAzimuthCones)) return

        val mobileAzimuths = groupedAzimuths(
            antennas = antennas,
            useFh = false,
            defaultOperator = options.defaultOperator,
            density = density
        )
        val fhAzimuths = if (options.showTechnoFh) {
            groupedAzimuths(
                antennas = antennas,
                useFh = true,
                defaultOperator = options.defaultOperator,
                density = density
            )
        } else {
            emptyList()
        }

        val beamLengthPx = when {
            logicalZoom >= 18 -> 60f * density
            logicalZoom >= 17 -> 50f * density
            logicalZoom >= 16 -> 40f * density
            logicalZoom >= 15 -> 30f * density
            else -> 25f * density
        }
        val pointRadius = 3.5f * density
        val fhRadius = pointRadius * 0.7f
        val circleOffsetPx = 0f
        val totalRadiusPx = beamLengthPx
        val mobileGap = pointRadius * 2f
        val fhGap = fhRadius * 2f
        val coneRect = RectF(
            centerX - totalRadiusPx,
            centerY - totalRadiusPx,
            centerX + totalRadiusPx,
            centerY + totalRadiusPx
        )

        mobileAzimuths.forEach { data ->
            if (options.showAzimuthCones) {
                val startAngle = data.azimuth - 90f - 35f
                data.conePaint?.let { canvas.drawArc(coneRect, startAngle, 70f, true, it) }
                data.coneEdgePaint?.let { drawConeEdgeLines(canvas, centerX, centerY, data.azimuth, circleOffsetPx, totalRadiusPx, it) }
            }

            if (options.showAzimuths) {
                drawAzimuthLine(canvas, centerX, centerY, data, circleOffsetPx, totalRadiusPx, pointRadius, mobileGap)
            }
        }

        if (options.showTechnoFh && options.showAzimuths) {
            fhAzimuths.forEach { data ->
                drawAzimuthLine(canvas, centerX, centerY, data, circleOffsetPx, totalRadiusPx, fhRadius, fhGap)
            }
        }
    }

    private fun groupedAzimuths(
        antennas: List<LocalisationEntity>,
        useFh: Boolean,
        defaultOperator: String,
        density: Float
    ): List<GroupedAzimuthData> {
        val angleToColors = mutableMapOf<Float, MutableSet<Int>>()
        antennas.forEach { antenna ->
            val operatorColor = OperatorColors.colorInt(antenna.operateur, fallback = OperatorColors.UNKNOWN_ARGB.toInt())
            val rawAzimuths = if (useFh) antenna.azimutsFh else antenna.azimuts
            parseAzimuths(rawAzimuths).forEach { azimuth ->
                angleToColors.getOrPut(azimuth) { mutableSetOf() }.add(operatorColor)
            }
        }

        val defaultColor = OperatorColors.colorInt(defaultOperator, fallback = OperatorColors.UNKNOWN_ARGB.toInt())
        return angleToColors.map { (azimuth, colors) ->
            val rad = Math.toRadians(azimuth - 90.0)
            val sortedColors = colors.toList().sortedByDescending { it == defaultColor }
            val mainColor = sortedColors.firstOrNull() ?: OperatorColors.UNKNOWN_ARGB.toInt()
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = if (useFh) {
                    Color.argb(200, Color.red(mainColor), Color.green(mainColor), Color.blue(mainColor))
                } else {
                    mainColor
                }
                strokeWidth = if (useFh) 3f * density else 3.5f * density
                strokeCap = Paint.Cap.ROUND
                if (useFh) {
                    pathEffect = DashPathEffect(floatArrayOf(5f * density, 5f * density), 0f)
                }
            }
            val conePaint = if (useFh) {
                null
            } else {
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = ColorUtils.setAlphaComponent(mainColor, 50)
                }
            }
            val coneEdgePaint = if (useFh) {
                null
            } else {
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = ColorUtils.setAlphaComponent(mainColor, 170)
                    strokeWidth = 2.2f * density
                    strokeCap = Paint.Cap.ROUND
                }
            }

            GroupedAzimuthData(
                azimuth = azimuth,
                cos = kotlin.math.cos(rad).toFloat(),
                sin = kotlin.math.sin(rad).toFloat(),
                linePaint = linePaint,
                conePaint = conePaint,
                coneEdgePaint = coneEdgePaint,
                dotColors = sortedColors
            )
        }
    }

    private fun parseAzimuths(value: String?): List<Float> {
        val raw = value?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: return emptyList()
        return raw
            .split(",", ";", "/", "|", "\n")
            .mapNotNull { part ->
                part.trim().replace("°", "").replace(",", ".").toFloatOrNull()
                    ?: Regex("""-?\d+(?:[\.,]\d+)?""").find(part)?.value?.replace(",", ".")?.toFloatOrNull()
            }
            .map { azimuth -> ((azimuth % 360f) + 360f) % 360f }
            .distinct()
    }

    private fun drawAzimuthLine(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        data: GroupedAzimuthData,
        startRadiusPx: Float,
        endRadiusPx: Float,
        pointRadius: Float,
        gap: Float
    ) {
        val startX = centerX + startRadiusPx * data.cos
        val startY = centerY + startRadiusPx * data.sin
        val endX = centerX + endRadiusPx * data.cos
        val endY = centerY + endRadiusPx * data.sin
        canvas.drawLine(startX, startY, endX, endY, data.linePaint)

        data.dotColors.forEachIndexed { index, color ->
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                this.color = color
            }
            val offset = index * gap
            canvas.drawCircle(
                endX + data.cos * offset,
                endY + data.sin * offset,
                pointRadius,
                dotPaint
            )
        }
    }

    private fun drawConeEdgeLines(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        azimuth: Float,
        startRadiusPx: Float,
        endRadiusPx: Float,
        paint: Paint
    ) {
        listOf(azimuth - 35f, azimuth + 35f).forEach { edgeAzimuth ->
            val edgeRad = Math.toRadians(edgeAzimuth - 90.0)
            val edgeCos = kotlin.math.cos(edgeRad).toFloat()
            val edgeSin = kotlin.math.sin(edgeRad).toFloat()
            canvas.drawLine(
                centerX + startRadiusPx * edgeCos,
                centerY + startRadiusPx * edgeSin,
                centerX + endRadiusPx * edgeCos,
                centerY + endRadiusPx * edgeSin,
                paint
            )
        }
    }

    private fun drawUserMarker(canvas: Canvas, centerX: Float, centerY: Float, density: Float) {
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(64, 75, 184, 255)
            style = Paint.Style.FILL
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(72, 166, 255)
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 3.5f * density
            style = Paint.Style.STROKE
        }

        canvas.drawCircle(centerX, centerY, 25f * density, haloPaint)
        canvas.drawCircle(centerX, centerY, 10.5f * density, fillPaint)
        canvas.drawCircle(centerX, centerY, 10.5f * density, strokePaint)
    }

    private fun tileSourceFor(context: Context, mapProvider: Int, ignStyle: Int): WidgetTileSource {
        if (!isNetworkAvailable(context)) {
            return offlineTileSource(context) ?: fallbackTileSource()
        }

        return when (mapProvider) {
            0 -> WidgetTileSource(
                cacheKey = if (ignStyle == 2) "ign_satellite" else "ign_plan_${ignStyle}",
                invertColors = ignStyle == 1,
                maxRenderZoom = 19,
                urlFor = { z, x, y ->
                    val layer = if (ignStyle == 2) "ORTHOIMAGERY.ORTHOPHOTOS" else "GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2"
                    val format = if (ignStyle == 2) "image/jpeg" else "image/png"
                    "https://data.geopf.fr/wmts?SERVICE=WMTS&VERSION=1.0.0&REQUEST=GetTile&LAYER=$layer&STYLE=normal&FORMAT=$format&TILEMATRIXSET=PM&TILEMATRIX=$z&TILEROW=$y&TILECOL=$x"
                }
            )
            2 -> WidgetTileSource(
                cacheKey = if (ignStyle == 1) "carto_dark" else "carto_voyager",
                invertColors = false,
                maxRenderZoom = 20,
                urlFor = { z, x, y ->
                    val layer = if (ignStyle == 1) "dark_all" else "voyager"
                    "https://basemaps.cartocdn.com/rastertiles/$layer/$z/$x/$y.png"
                }
            )
            3 -> WidgetTileSource(
                cacheKey = "opentopo",
                invertColors = false,
                maxRenderZoom = 17,
                urlFor = { z, x, y -> "https://a.tile.opentopomap.org/$z/$x/$y.png" }
            )
            4 -> offlineTileSource(context) ?: osmTileSource(ignStyle)
            1 -> osmTileSource(ignStyle)
            else -> WidgetTileSource(
                cacheKey = "osm_${ignStyle}",
                invertColors = ignStyle == 1,
                maxRenderZoom = 19,
                urlFor = { z, x, y -> "https://tile.openstreetmap.org/$z/$x/$y.png" }
            )
        }
    }

    private fun fallbackTileSource(): WidgetTileSource {
        return WidgetTileSource(
            cacheKey = "fallback",
            invertColors = false,
            maxRenderZoom = 19,
            urlFor = null
        )
    }

    private fun offlineTileSource(context: Context): WidgetTileSource? {
        val offlineDir = File(context.getExternalFilesDir(null), "maps")
        val files = offlineDir.listFiles { file -> file.extension == "map" && file.length() > 0L }
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return runCatching {
            if (AndroidGraphicFactory.INSTANCE == null) {
                MapsForgeTileSource.createInstance(context.applicationContext as Application)
            }
            WidgetTileSource(
                cacheKey = "offline_${files.size}_${files.sumOf { it.length() }}",
                invertColors = false,
                maxRenderZoom = 20,
                urlFor = null,
                mapsForgeTileSource = MapsForgeTileSource.createFromFiles(
                    files,
                    InternalRenderTheme.OSMARENDER,
                    "osmarender"
                )
            )
        }.getOrNull()
    }

    private fun osmTileSource(ignStyle: Int): WidgetTileSource {
        return WidgetTileSource(
            cacheKey = "osm_${ignStyle}",
            invertColors = ignStyle == 1,
            maxRenderZoom = 19,
            urlFor = { z, x, y -> "https://tile.openstreetmap.org/$z/$x/$y.png" }
        )
    }

    private fun pixelBounds(points: List<Pair<Double, Double>>, zoom: Int): PixelBounds {
        val xs = points.map { (_, lon) -> lonToPixelX(lon, zoom) }
        val ys = points.map { (lat, _) -> latToPixelY(lat, zoom) }
        val minX = xs.minOrNull() ?: lonToPixelX(0.0, zoom)
        val maxX = xs.maxOrNull() ?: minX
        val minY = ys.minOrNull() ?: latToPixelY(0.0, zoom)
        val maxY = ys.maxOrNull() ?: minY
        return PixelBounds(minX, maxX, minY, maxY)
    }

    private fun lonToPixelX(lon: Double, zoom: Int): Double {
        val worldSize = (1 shl zoom) * TILE_SIZE.toDouble()
        return ((lon + 180.0) / 360.0) * worldSize
    }

    private fun latToPixelY(lat: Double, zoom: Int): Double {
        val safeLat = lat.coerceIn(-85.05112878, 85.05112878)
        val sinLat = kotlin.math.sin(Math.toRadians(safeLat))
        val worldSize = (1 shl zoom) * TILE_SIZE.toDouble()
        return (0.5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI)) * worldSize
    }

    private fun pixelXToLon(pixelX: Double, zoom: Int): Double {
        val worldSize = (1 shl zoom) * TILE_SIZE.toDouble()
        return pixelX / worldSize * 360.0 - 180.0
    }

    private fun pixelYToLat(pixelY: Double, zoom: Int): Double {
        val worldSize = (1 shl zoom) * TILE_SIZE.toDouble()
        val y = 0.5 - pixelY / worldSize
        return Math.toDegrees(atan(sinh(2.0 * PI * y)))
    }

    private fun floorMod(value: Int, divisor: Int): Int {
        val mod = value % divisor
        return if (mod < 0) mod + divisor else mod
    }

    private data class WidgetTileSource(
        val cacheKey: String,
        val invertColors: Boolean,
        val maxRenderZoom: Int,
        val urlFor: ((Int, Int, Int) -> String)?,
        val mapsForgeTileSource: MapsForgeTileSource? = null
    )

    private data class RenderSpec(
        val fileSuffix: String,
        val width: Int,
        val height: Int,
        val logicalWidth: Int,
        val logicalHeight: Int,
        val paddingPx: Double,
        val viewportScale: Double,
        val renderZoomBoost: Int
    ) {
        val outputDensity: Double = width.toDouble() / logicalWidth.toDouble()
    }

    private data class WidgetViewport(
        val zoom: Int,
        val logicalZoom: Int,
        val centerLat: Double,
        val centerLon: Double,
        val topLeftPixelX: Double,
        val topLeftPixelY: Double,
        val sourcePixelsPerOutputPixel: Double
    ) {
        fun projectX(lon: Double): Float = ((lonToPixelX(lon, zoom) - topLeftPixelX) / sourcePixelsPerOutputPixel).toFloat()
        fun projectY(lat: Double): Float = ((latToPixelY(lat, zoom) - topLeftPixelY) / sourcePixelsPerOutputPixel).toFloat()
    }

    private data class PixelBounds(
        val minX: Double,
        val maxX: Double,
        val minY: Double,
        val maxY: Double
    ) {
        val width: Double = max(1.0, maxX - minX)
        val height: Double = max(1.0, maxY - minY)
        val centerX: Double = (minX + maxX) / 2.0
        val centerY: Double = (minY + maxY) / 2.0
    }

    private data class ProjectedSite(
        val site: WidgetMapSiteData,
        val x: Float,
        val y: Float
    )

    private data class GroupedAzimuthData(
        val azimuth: Float,
        val cos: Float,
        val sin: Float,
        val linePaint: Paint,
        val conePaint: Paint?,
        val coneEdgePaint: Paint?,
        val dotColors: List<Int>
    )

    private fun WidgetMapSiteData.toLocalisationEntities(): List<LocalisationEntity> {
        val siteAntennas = antennas.ifEmpty {
            operatorKeys.map { operatorKey ->
                WidgetMapAntennaData(
                    id = id,
                    operatorName = operatorKey,
                    azimuts = null,
                    azimutsFh = null
                )
            }
        }

        return siteAntennas.map { antenna ->
            LocalisationEntity(
                idAnfr = antenna.id,
                operateur = antenna.operatorName,
                latitude = latitude,
                longitude = longitude,
                azimuts = antenna.azimuts,
                codeInsee = null,
                azimutsFh = antenna.azimutsFh
            )
        }
    }
}
