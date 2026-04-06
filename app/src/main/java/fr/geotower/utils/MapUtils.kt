package fr.geotower.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import fr.geotower.data.models.LocalisationEntity
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

object MapUtils {

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

    fun createAdaptiveMarker(
        context: Context,
        siteAntennas: List<LocalisationEntity>,
        showAzimuths: Boolean,
        defaultOp: String
    ): BitmapDrawable {
        val siteId = siteAntennas.firstOrNull()?.idAnfr ?: "0"
        val cacheKey = "${siteId}_${showAzimuths}_${defaultOp}"

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

        val def = defaultOp.uppercase()
        val baseOrder = listOf("ORANGE", "BOUYGUES", "SFR", "FREE")
        val priorityList = mutableListOf<String>()

        if (def != "AUCUN" && baseOrder.any { def.contains(it) }) {
            priorityList.add(baseOrder.first { def.contains(it) })
        }
        baseOrder.forEach { if (!priorityList.contains(it)) priorityList.add(it) }

        val colorMap = mapOf(
            "ORANGE" to android.graphics.Color.parseColor("#FF7900"),
            "BOUYGUES" to android.graphics.Color.parseColor("#00295F"),
            "SFR" to android.graphics.Color.parseColor("#E2001A"),
            "FREE" to android.graphics.Color.parseColor("#757575")
        )

        val operatorsOnSite = siteAntennas.mapNotNull { it.operateur }
            .flatMap { it.split(Regex("[/,\\-]")) }
            .map { it.trim().uppercase() }
            .filter { op -> colorMap.keys.any { op.contains(it) } }
            .map { op -> colorMap.keys.first { op.contains(it) } }
            .distinct()
            .sortedBy { op -> priorityList.indexOf(op) }

        if (showAzimuths) {
            val azimutMap = mutableMapOf<Int, MutableList<String>>()

            siteAntennas.forEach { antenna ->
                val opRaw = antenna.operateur?.uppercase() ?: return@forEach
                val opClean = colorMap.keys.firstOrNull { opRaw.contains(it) } ?: return@forEach

                val azStr = antenna.azimuts
                if (!azStr.isNullOrBlank() && azStr != "null") {
                    // NOUVEAU : On extrait uniquement les chiffres avant le symbole °
                    val regex = Regex("(\\d+)°")
                    val matches = regex.findAll(azStr)

                    matches.forEach { matchResult ->
                        val angle = matchResult.groupValues[1].toIntOrNull()
                        if (angle != null) {
                            if (!azimutMap.getOrPut(angle) { mutableListOf() }.contains(opClean)) {
                                azimutMap[angle]!!.add(opClean)
                            }
                        }
                    }
                }
            }

            val innerRadius = pieRadius + 4f
            val outerRadius = pieRadius + 60f
            val strokeWidth = 6f
            val dotRadius = strokeWidth * 1.5f

            azimutMap.forEach { (angle, ops) ->
                val sortedOpsForAz = ops.sortedBy { priorityList.indexOf(it) }

                canvas.save()
                canvas.rotate(angle.toFloat(), center, center)

                val segmentLength = (outerRadius - innerRadius) / sortedOpsForAz.size

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = strokeWidth
                paint.strokeCap = Paint.Cap.ROUND

                sortedOpsForAz.forEachIndexed { index, op ->
                    paint.color = colorMap[op] ?: android.graphics.Color.GRAY
                    val startY = center - innerRadius - (index * segmentLength)
                    val endY = startY - segmentLength
                    canvas.drawLine(center, startY, center, endY, paint)
                }

                paint.style = Paint.Style.FILL
                paint.color = android.graphics.Color.WHITE
                canvas.drawCircle(center, center - outerRadius, dotRadius, paint)

                canvas.restore()
            }
        }

        paint.style = Paint.Style.FILL
        val rect = android.graphics.RectF(center - pieRadius, center - pieRadius, center + pieRadius, center + pieRadius)

        if (operatorsOnSite.isEmpty()) {
            paint.color = android.graphics.Color.GRAY
            canvas.drawCircle(center, center, pieRadius, paint)
        } else {
            when (operatorsOnSite.size) {
                1 -> { paint.color = colorMap[operatorsOnSite[0]]!!; canvas.drawArc(rect, 0f, 360f, true, paint) }
                2 -> { paint.color = colorMap[operatorsOnSite[0]]!!; canvas.drawArc(rect, 180f, 180f, true, paint); paint.color = colorMap[operatorsOnSite[1]]!!; canvas.drawArc(rect, 0f, 180f, true, paint) }
                3 -> { paint.color = colorMap[operatorsOnSite[0]]!!; canvas.drawArc(rect, 210f, 120f, true, paint); paint.color = colorMap[operatorsOnSite[1]]!!; canvas.drawArc(rect, 330f, 120f, true, paint); paint.color = colorMap[operatorsOnSite[2]]!!; canvas.drawArc(rect, 90f, 120f, true, paint) }
                4 -> { val angles = listOf(180f, 270f, 0f, 90f); operatorsOnSite.forEachIndexed { index, op -> paint.color = colorMap[op]!!; canvas.drawArc(rect, angles[index], 90f, true, paint) } }
            }
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

        val colorMap = mapOf(
            "ORANGE" to android.graphics.Color.parseColor("#FF7900"),
            "BOUYGUES" to android.graphics.Color.parseColor("#00295F"),
            "SFR" to android.graphics.Color.parseColor("#E2001A"),
            "FREE" to android.graphics.Color.parseColor("#757575")
        )

        // ✅ CORRECTION : Tri intelligent selon l'opérateur préféré
        val def = defaultOp.uppercase()
        val baseOrder = listOf("ORANGE", "BOUYGUES", "SFR", "FREE")
        val priorityList = mutableListOf<String>()

        if (def != "AUCUN" && baseOrder.any { def.contains(it) }) {
            priorityList.add(baseOrder.first { def.contains(it) })
        }
        baseOrder.forEach { if (!priorityList.contains(it)) priorityList.add(it) }

        val sortedOps = operators
            .filter { op -> colorMap.keys.any { op.contains(it) } }
            .map { op -> colorMap.keys.first { op.contains(it) } }
            .distinct()
            .sortedBy { op -> priorityList.indexOf(op) }

        val rect = android.graphics.RectF(0f, 0f, size.toFloat(), size.toFloat())

        if (sortedOps.isEmpty()) {
            paint.color = android.graphics.Color.GRAY
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        } else {
            when (sortedOps.size) {
                1 -> { paint.color = colorMap[sortedOps[0]]!!; canvas.drawArc(rect, 0f, 360f, true, paint) }
                2 -> { paint.color = colorMap[sortedOps[0]]!!; canvas.drawArc(rect, 180f, 180f, true, paint); paint.color = colorMap[sortedOps[1]]!!; canvas.drawArc(rect, 0f, 180f, true, paint) }
                3 -> { paint.color = colorMap[sortedOps[0]]!!; canvas.drawArc(rect, 210f, 120f, true, paint); paint.color = colorMap[sortedOps[1]]!!; canvas.drawArc(rect, 330f, 120f, true, paint); paint.color = colorMap[sortedOps[2]]!!; canvas.drawArc(rect, 90f, 120f, true, paint) }
                4 -> { val angles = listOf(180f, 270f, 0f, 90f); sortedOps.forEachIndexed { index, op -> paint.color = colorMap[op]!!; canvas.drawArc(rect, angles[index], 90f, true, paint) } }
            }
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
}