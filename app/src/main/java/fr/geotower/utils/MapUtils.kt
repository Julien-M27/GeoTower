package fr.geotower.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.RadioServiceMasks
import fr.geotower.data.models.RadioSystemMasks
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

object MapUtils {
    private val INACTIVE_OPERATOR_COLOR = android.graphics.Color.rgb(196, 199, 204)
    private val markerAzimuthWithUnitRegex = Regex(
        "([0-9]{1,3}(?:[.,][0-9]+)?)\\s*(?:\\u00B0|\\u00C2\\u00B0|deg(?:res|ree|rees)?|degrees?)",
        RegexOption.IGNORE_CASE
    )

    fun getInvertFilter(): ColorMatrixColorFilter = ColorMatrixColorFilter(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))

    val OSM_Source = object : OnlineTileSourceBase("OSM", 0, 19, 256, "", arrayOf("https://tile.openstreetmap.org/")) {
        override fun getTileURLString(pMapTileIndex: Long): String = baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + ".png"
    }

    object IgnSource {
        val PLAN_IGN = object : OnlineTileSourceBase(
            "IGN Plan", 0, 19, 256, "",
            arrayOf("https://data.geopf.fr/wmts?SERVICE=WMTS&VERSION=1.0.0&REQUEST=GetTile&LAYER=GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2&STYLE=normal&FORMAT=image/png&TILEMATRIXSET=PM&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                return baseUrl.replace("{z}", MapTileIndex.getZoom(pMapTileIndex).toString())
                    .replace("{x}", MapTileIndex.getX(pMapTileIndex).toString())
                    .replace("{y}", MapTileIndex.getY(pMapTileIndex).toString())
            }
        }

        val SATELLITE = object : OnlineTileSourceBase(
            "IGN Satellite", 0, 19, 256, "",
            arrayOf("https://data.geopf.fr/wmts?SERVICE=WMTS&VERSION=1.0.0&REQUEST=GetTile&LAYER=ORTHOIMAGERY.ORTHOPHOTOS&STYLE=normal&FORMAT=image/jpeg&TILEMATRIXSET=PM&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                return baseUrl.replace("{z}", MapTileIndex.getZoom(pMapTileIndex).toString())
                    .replace("{x}", MapTileIndex.getX(pMapTileIndex).toString())
                    .replace("{y}", MapTileIndex.getY(pMapTileIndex).toString())
            }
        }
    }

    val markerIconCache = android.util.LruCache<String, BitmapDrawable>(500)
    val clusterIconCache = android.util.LruCache<String, BitmapDrawable>(200)
    val radioIconCache = android.util.LruCache<String, BitmapDrawable>(300)

    fun createAdaptiveMarker(
        context: Context,
        siteAntennas: List<LocalisationEntity>,
        showAzimuths: Boolean,
        defaultOp: String,
        inactiveOperatorKeys: Set<String> = emptySet()
    ): BitmapDrawable {
        // --- Ordre de priorité des opérateurs (selon l'opérateur par défaut) ---
        val def = defaultOp.uppercase()
        val baseOrder = OperatorColors.orderedKeys
        val priorityList = mutableListOf<String>()
        OperatorColors.keyFor(def)?.let { priorityList.add(it) }
        baseOrder.forEach { if (!priorityList.contains(it)) priorityList.add(it) }

        val operatorsOnSite = siteAntennas.mapNotNull { it.operateur }
            .flatMap { OperatorColors.keysFor(it) }
            .distinct()
            .sortedBy { op -> priorityList.indexOf(op) }

        // Carte angle -> opérateurs (uniquement si les azimuts sont affichés).
        val azimutMap: Map<Int, List<String>> = if (showAzimuths) {
            val map = mutableMapOf<Int, MutableList<String>>()
            siteAntennas.forEach { antenna ->
                val operatorKeys = OperatorColors.keysFor(antenna.operateur)
                if (operatorKeys.isEmpty()) return@forEach
                val azStr = antenna.azimuts
                if (!azStr.isNullOrBlank() && azStr != "null") {
                    parseMarkerAzimuths(azStr).forEach { angle ->
                        val list = map.getOrPut(angle) { mutableListOf() }
                        operatorKeys.forEach { opClean ->
                            if (!list.contains(opClean)) list.add(opClean)
                        }
                    }
                }
            }
            map
        } else {
            emptyMap()
        }

        // --- Clé de cache basée UNIQUEMENT sur le rendu visuel (plus d'idAnfr) ---
        // Deux sites avec les mêmes opérateurs (et mêmes azimuts si affichés) partagent
        // la même icône : le taux de réussite du cache grimpe fortement.
        val inactiveSignature = inactiveOperatorKeys.sorted().joinToString(",")
        val azimuthSignature = if (showAzimuths) {
            azimutMap.entries.sortedBy { it.key }.joinToString("|") { (angle, ops) ->
                "$angle>" + ops.sortedBy { priorityList.indexOf(it) }.joinToString("+")
            }
        } else {
            ""
        }
        val cacheKey =
            "m2|$showAzimuths|$def|${operatorsOnSite.joinToString(",")}|$inactiveSignature|$azimuthSignature"

        markerIconCache.get(cacheKey)?.let { return it }

        val metrics = context.resources.displayMetrics
        val density = metrics.density

        // ✅ CORRECTION : Taille cible proportionnelle en DP (~85dp)
        val targetSize = (105 * density).toInt()
        val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // ✅ ASTUCE MAGIQUE : On met le canvas à l'échelle pour ne pas avoir à
        // modifier tes formules géométriques qui sont basées sur "230"
        val scale = targetSize / 230f
        canvas.scale(scale, scale)

        val size = 230 // On laisse cette valeur à 230 pour ton repère mathématique !
        val center = size / 2f
        val pieRadius = 45f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isAntiAlias = true
        }

        val colorMap = OperatorColors.androidColorMap()
        fun colorForOperator(op: String): Int {
            return if (op in inactiveOperatorKeys) {
                INACTIVE_OPERATOR_COLOR
            } else {
                colorMap[op] ?: android.graphics.Color.GRAY
            }
        }

        if (showAzimuths && azimutMap.isNotEmpty()) {
            val innerRadius = pieRadius + 4f
            val outerRadius = pieRadius + 60f
            val strokeWidth = 6f

            azimutMap.forEach { (angle, ops) ->
                val sortedOpsForAz = ops.sortedBy { priorityList.indexOf(it) }

                canvas.save()
                canvas.rotate(angle.toFloat(), center, center)

                val segmentLength = (outerRadius - innerRadius) / sortedOpsForAz.size

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = strokeWidth
                paint.strokeCap = Paint.Cap.ROUND

                sortedOpsForAz.forEachIndexed { index, op ->
                    paint.color = colorForOperator(op)
                    val startY = center - innerRadius - (index * segmentLength)
                    val endY = startY - segmentLength
                    canvas.drawLine(center, startY, center, endY, paint)
                }

                canvas.restore()
            }
        }

        paint.style = Paint.Style.FILL
        val rect = android.graphics.RectF(center - pieRadius, center - pieRadius, center + pieRadius, center + pieRadius)

        if (operatorsOnSite.isEmpty()) {
            paint.color = android.graphics.Color.GRAY
            canvas.drawCircle(center, center, pieRadius, paint)
        } else {
            drawOperatorSlices(canvas, rect, operatorsOnSite, paint, ::colorForOperator)
        }

        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(center, center, pieRadius * 0.40f, paint)

        paint.color = android.graphics.Color.parseColor("#EBEBEB")
        canvas.drawCircle(center, center, pieRadius * 0.80f, paint)

        val iconScale = 100f
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isAntiAlias = true
            style = Paint.Style.STROKE; strokeWidth = iconScale * 0.035f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = android.graphics.Color.parseColor("#34404A")
        }

        val cx = center
        val cy = center + (iconScale * 0.04f)
        val u = iconScale / 200f

        val tower = android.graphics.Path()
        tower.moveTo(cx - 20f * u, cy + 45f * u); tower.lineTo(cx - 6f * u, cy - 5f * u)
        tower.moveTo(cx + 20f * u, cy + 45f * u); tower.lineTo(cx + 6f * u, cy - 5f * u)
        tower.moveTo(cx - 6f * u, cy - 5f * u); tower.lineTo(cx + 6f * u, cy - 5f * u)
        val hY = cy + 20f * u; val hX = 13f * u
        tower.moveTo(cx - hX, hY); tower.lineTo(cx + hX, hY)
        tower.moveTo(cx - 20f * u, cy + 45f * u); tower.lineTo(cx + hX, hY)
        tower.moveTo(cx + 20f * u, cy + 45f * u); tower.lineTo(cx - hX, hY)
        canvas.drawPath(tower, iconPaint)

        val dy = cy - 22f * u; val dr = 6.5f * u
        val diamond = android.graphics.Path()
        diamond.moveTo(cx, dy - dr); diamond.lineTo(cx + dr, dy); diamond.lineTo(cx, dy + dr); diamond.lineTo(cx - dr, dy); diamond.close()
        canvas.drawPath(diamond, iconPaint)

        val dotPaint = Paint(iconPaint).apply { style = Paint.Style.FILL }
        canvas.drawCircle(cx, dy, 2.5f * u, dotPaint)

        val waveInner = 17f * u; val waveOuter = 29f * u
        val rectInner = android.graphics.RectF(cx - waveInner, dy - waveInner, cx + waveInner, dy + waveInner)
        val rectOuter = android.graphics.RectF(cx - waveOuter, dy - waveOuter, cx + waveOuter, dy + waveOuter)
        canvas.drawArc(rectInner, -40f, 80f, false, iconPaint); canvas.drawArc(rectOuter, -40f, 80f, false, iconPaint)
        canvas.drawArc(rectInner, 140f, 80f, false, iconPaint); canvas.drawArc(rectOuter, 140f, 80f, false, iconPaint)

        val finalDrawable = BitmapDrawable(context.resources, bitmap)
        markerIconCache.put(cacheKey, finalDrawable)

        return finalDrawable
    }

    private fun parseMarkerAzimuths(rawAzimuths: String): List<Int> {
        val explicitAngles = markerAzimuthWithUnitRegex.findAll(rawAzimuths)
            .mapNotNull { match -> normalizeMarkerAzimuth(match.groupValues.getOrNull(1)) }
            .toList()
        if (explicitAngles.isNotEmpty()) return explicitAngles.distinct()

        return rawAzimuths.split(",")
            .mapNotNull { value -> normalizeMarkerAzimuth(value.trim()) }
            .distinct()
    }

    private fun normalizeMarkerAzimuth(rawValue: String?): Int? {
        val angle = rawValue
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?.toInt()
            ?: return null
        if (angle !in 0..360) return null
        return if (angle == 360) 0 else angle
    }

    fun createRadioMarkerIcon(context: Context, serviceMask: Int, systemMask: Int, count: Int): BitmapDrawable {
        val safeCount = count.coerceAtLeast(1)
        val cacheKey = "radio_v4_${serviceMask}_${systemMask}_$safeCount"
        radioIconCache.get(cacheKey)?.let { return it }

        val density = context.resources.displayMetrics.density
        val isCluster = safeCount > 1
        val size = ((if (isCluster) 46 else 105) * density).toInt().coerceAtLeast(24)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = radioMarkerColor(serviceMask, systemMask)
        }

        if (isCluster) {
            val centerPoint = size / 2f
            drawRadioClusterRing(canvas, size.toFloat(), radioMarkerColors(serviceMask, systemMask), paint)

            paint.style = Paint.Style.FILL
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(centerPoint, centerPoint, centerPoint * 0.80f, paint)

            paint.color = android.graphics.Color.parseColor("#37474F")
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.CENTER
            val text = safeCount.toString()
            paint.textSize = when (text.length) {
                1, 2 -> size * 0.36f
                3 -> size * 0.30f
                4 -> size * 0.24f
                else -> size * 0.19f
            }
            val textOffset = (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(text, centerPoint, centerPoint - textOffset, paint)
        } else {
            val scale = size / 230f
            canvas.scale(scale, scale)

            val center = 115f
            val pieRadius = 45f
            val radioColors = radioMarkerColors(serviceMask, systemMask)
            val rect = android.graphics.RectF(center - pieRadius, center - pieRadius, center + pieRadius, center + pieRadius)
            if (radioColors.size <= 1) {
                paint.color = radioColors.firstOrNull() ?: radioMarkerColor(serviceMask, systemMask)
                canvas.drawCircle(center, center, pieRadius, paint)
            } else {
                drawRadioMarkerSlices(canvas, rect, radioColors, paint)
            }

            paint.style = Paint.Style.FILL
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(center, center, pieRadius * 0.40f, paint)

            paint.color = android.graphics.Color.parseColor("#EBEBEB")
            canvas.drawCircle(center, center, pieRadius * 0.80f, paint)

            val iconScale = 100f
            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = iconScale * 0.035f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = android.graphics.Color.parseColor("#34404A")
            }

            val cx = center
            val cy = center + (iconScale * 0.04f)
            val u = iconScale / 200f

            val tower = android.graphics.Path()
            tower.moveTo(cx - 20f * u, cy + 45f * u); tower.lineTo(cx - 6f * u, cy - 5f * u)
            tower.moveTo(cx + 20f * u, cy + 45f * u); tower.lineTo(cx + 6f * u, cy - 5f * u)
            tower.moveTo(cx - 6f * u, cy - 5f * u); tower.lineTo(cx + 6f * u, cy - 5f * u)
            val hY = cy + 20f * u; val hX = 13f * u
            tower.moveTo(cx - hX, hY); tower.lineTo(cx + hX, hY)
            tower.moveTo(cx - 20f * u, cy + 45f * u); tower.lineTo(cx + hX, hY)
            tower.moveTo(cx + 20f * u, cy + 45f * u); tower.lineTo(cx - hX, hY)
            canvas.drawPath(tower, iconPaint)

            val dy = cy - 22f * u; val dr = 6.5f * u
            val diamond = android.graphics.Path()
            diamond.moveTo(cx, dy - dr); diamond.lineTo(cx + dr, dy); diamond.lineTo(cx, dy + dr); diamond.lineTo(cx - dr, dy); diamond.close()
            canvas.drawPath(diamond, iconPaint)

            val dotPaint = Paint(iconPaint).apply { style = Paint.Style.FILL }
            canvas.drawCircle(cx, dy, 2.5f * u, dotPaint)

            val waveInner = 17f * u
            val waveOuter = 29f * u
            val rectInner = android.graphics.RectF(cx - waveInner, dy - waveInner, cx + waveInner, dy + waveInner)
            val rectOuter = android.graphics.RectF(cx - waveOuter, dy - waveOuter, cx + waveOuter, dy + waveOuter)
            listOf(0f, 180f).forEach { start ->
                canvas.drawArc(
                    rectInner,
                    start - 55f,
                    110f,
                    false,
                    iconPaint
                )
                canvas.drawArc(
                    rectOuter,
                    start - 55f,
                    110f,
                    false,
                    iconPaint
                )
            }
        }

        val drawable = BitmapDrawable(context.resources, bitmap)
        radioIconCache.put(cacheKey, drawable)
        return drawable
    }

    fun createTransparentMarkerIcon(context: Context): BitmapDrawable {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        return BitmapDrawable(context.resources, bitmap)
    }

    fun radioMarkerColor(serviceMask: Int, systemMask: Int): Int {
        return when {
            (serviceMask and RadioServiceMasks.FH) != 0 -> android.graphics.Color.parseColor("#0D47A1")
            (systemMask and RadioSystemMasks.TV) != 0 -> android.graphics.Color.parseColor("#8BC34A")
            (systemMask and RadioSystemMasks.RADIO) != 0 -> android.graphics.Color.parseColor("#FDD835")
            (serviceMask and (RadioServiceMasks.PRIVATE or RadioServiceMasks.RAIL or RadioServiceMasks.TRANSPORT)) != 0 ->
                android.graphics.Color.parseColor("#006D77")
            else -> android.graphics.Color.parseColor("#111111")
        }
    }

    private fun radioMarkerColors(serviceMask: Int, systemMask: Int): List<Int> {
        val colors = mutableListOf<Int>()
        if ((systemMask and RadioSystemMasks.TV) != 0) {
            colors += android.graphics.Color.parseColor("#8BC34A")
        }
        if ((systemMask and RadioSystemMasks.RADIO) != 0) {
            colors += android.graphics.Color.parseColor("#FDD835")
        }
        if ((serviceMask and (RadioServiceMasks.PRIVATE or RadioServiceMasks.RAIL or RadioServiceMasks.TRANSPORT)) != 0) {
            colors += android.graphics.Color.parseColor("#006D77")
        }
        if ((serviceMask and RadioServiceMasks.FH) != 0) {
            colors += android.graphics.Color.parseColor("#0D47A1")
        }
        if ((serviceMask and (RadioServiceMasks.SATELLITE or RadioServiceMasks.RADAR or RadioServiceMasks.OTHER)) != 0 ||
            colors.isEmpty()
        ) {
            colors += android.graphics.Color.parseColor("#111111")
        }
        return colors.distinct()
    }

    fun createClusterIcon(context: Context, operators: List<String>, count: Int, defaultOp: String): BitmapDrawable {
        // ✅ CORRECTION : On intègre l'opérateur par défaut dans le cache pour forcer le redessin si on change d'avis !
        val cacheKey = "${operators.sorted().joinToString("_")}_${count}_$defaultOp"

        clusterIconCache.get(cacheKey)?.let { return it }

        val metrics = context.resources.displayMetrics
        val density = metrics.density

        // ✅ CORRECTION : La taille s'adapte maintenant à la densité de l'écran (environ 38dp)
        val size = (45 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val colorMap = OperatorColors.androidColorMap()

        // ✅ CORRECTION : Tri intelligent selon l'opérateur préféré
        val def = defaultOp.uppercase()
        val baseOrder = OperatorColors.orderedKeys
        val priorityList = mutableListOf<String>()

        OperatorColors.keyFor(def)?.let { priorityList.add(it) }
        baseOrder.forEach { if (!priorityList.contains(it)) priorityList.add(it) }

        val sortedOps = operators
            .flatMap { OperatorColors.keysFor(it) }
            .distinct()
            .sortedBy { op -> priorityList.indexOf(op) }

        if (sortedOps.isEmpty()) {
            paint.color = android.graphics.Color.GRAY
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        } else {
            drawOperatorRing(canvas, size.toFloat(), sortedOps, colorMap, paint)
        }

        val centerPoint = size / 2f
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(centerPoint, centerPoint, centerPoint * 0.80f, paint)

        paint.color = android.graphics.Color.parseColor("#37474F")
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER

        // Adaptation de la taille du texte
        val countStr = count.toString()
        paint.textSize = when (countStr.length) {
            1, 2 -> size * 0.40f
            3 -> size * 0.32f
            4 -> size * 0.25f
            else -> size * 0.20f
        }

        val textOffset = (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(countStr, centerPoint, centerPoint - textOffset, paint)

        val finalDrawable = BitmapDrawable(context.resources, bitmap)
        clusterIconCache.put(cacheKey, finalDrawable)

        return finalDrawable
    }

    private fun drawOperatorSlices(
        canvas: Canvas,
        rect: android.graphics.RectF,
        operators: List<String>,
        paint: Paint,
        colorForOperator: (String) -> Int
    ) {
        val sweep = 360f / operators.size.coerceAtLeast(1)
        operators.forEachIndexed { index, op ->
            paint.color = colorForOperator(op)
            canvas.drawArc(rect, -90f + index * sweep, sweep, true, paint)
        }
    }

    private fun drawOperatorRing(
        canvas: Canvas,
        size: Float,
        operators: List<String>,
        colorMap: Map<String, Int>,
        paint: Paint
    ) {
        val center = size / 2f
        val strokeWidth = size * 0.22f
        val radius = center - strokeWidth / 2f
        val ringRect = android.graphics.RectF(
            center - radius,
            center - radius,
            center + radius,
            center + radius
        )
        val sweep = 360f / operators.size.coerceAtLeast(1)
        val overlap = if (operators.size > 1) 0.8f else 0f

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.strokeCap = Paint.Cap.BUTT

        operators.forEachIndexed { index, op ->
            paint.color = colorMap[op] ?: android.graphics.Color.GRAY
            canvas.drawArc(ringRect, -90f + index * sweep, sweep + overlap, false, paint)
        }

        paint.style = Paint.Style.FILL
    }

    private fun drawRadioClusterRing(
        canvas: Canvas,
        size: Float,
        colors: List<Int>,
        paint: Paint
    ) {
        val center = size / 2f
        val strokeWidth = size * 0.22f
        val radius = center - strokeWidth / 2f
        val ringRect = android.graphics.RectF(
            center - radius,
            center - radius,
            center + radius,
            center + radius
        )
        val safeColors = colors.ifEmpty { listOf(android.graphics.Color.parseColor("#111111")) }
        val sweep = 360f / safeColors.size.coerceAtLeast(1)
        val overlap = if (safeColors.size > 1) 0.8f else 0f

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.strokeCap = Paint.Cap.BUTT

        safeColors.forEachIndexed { index, color ->
            paint.color = color
            canvas.drawArc(ringRect, -90f + index * sweep, sweep + overlap, false, paint)
        }

        paint.style = Paint.Style.FILL
    }

    private fun drawRadioMarkerSlices(
        canvas: Canvas,
        rect: android.graphics.RectF,
        colors: List<Int>,
        paint: Paint
    ) {
        val safeColors = colors.ifEmpty { listOf(android.graphics.Color.parseColor("#111111")) }
        val sweep = 360f / safeColors.size.coerceAtLeast(1)
        val overlap = if (safeColors.size > 1) 0.8f else 0f

        paint.style = Paint.Style.FILL
        safeColors.forEachIndexed { index, color ->
            paint.color = color
            canvas.drawArc(rect, -90f + index * sweep, sweep + overlap, true, paint)
        }
    }
}
