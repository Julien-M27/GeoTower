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

    // Liseré de lisibilité des marqueurs sur fond satellite : l'orthophoto occupe toute la gamme
    // des gris (bitume, toitures, terres nues), le gris Free s'y dissout et les autres aplats
    // perdent leur netteté. On cerne donc chaque aplat d'un trait clair ou sombre, et UNIQUEMENT
    // là : sur le plan IGN / OSM les couleurs ressortent déjà, le liseré ne ferait qu'alourdir.
    private const val PIE_OUTLINE_UNITS = 4f // repère du camembert (canvas de 230 unités)
    private const val AZIMUTH_OUTLINE_UNITS = 2.5f // débord de part et d'autre d'un épi d'azimut
    private val DARK_OUTLINE_COLOR = android.graphics.Color.parseColor("#1B1B1B")

    /**
     * Vrai quand les tuiles réellement affichées sont des orthophotos. Le style « satellite »
     * n'existe que sur les fonds IGN (0 et repli) et OSM (1) : les fonds 2 (Carto) et 3
     * (OpenTopo) n'en ont pas, et le 4 est une carte hors ligne. À garder aligné sur le `when`
     * qui choisit la source de tuiles dans MapScreen / SharedMiniMapCard.
     */
    fun isSatelliteBasemap(provider: Int, ignStyle: Int): Boolean {
        return ignStyle == 2 && provider != 2 && provider != 3 && provider != 4
    }

    /** Trait de contraste d'un aplat : blanc sur couleur sombre, anthracite sur couleur claire. */
    fun contrastOutlineColor(fillColor: Int): Int {
        val luminance = (
            0.2126f * android.graphics.Color.red(fillColor) +
                0.7152f * android.graphics.Color.green(fillColor) +
                0.0722f * android.graphics.Color.blue(fillColor)
            ) / 255f
        return if (luminance < 0.6f) android.graphics.Color.WHITE else DARK_OUTLINE_COLOR
    }

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

    // Vue satellite associée au fond OSM (Esri World Imagery, couverture mondiale).
    object EsriSource {
        val SATELLITE = object : OnlineTileSourceBase(
            "Esri Satellite", 0, 19, 256, "",
            arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                return baseUrl.replace("{z}", MapTileIndex.getZoom(pMapTileIndex).toString())
                    .replace("{x}", MapTileIndex.getX(pMapTileIndex).toString())
                    .replace("{y}", MapTileIndex.getY(pMapTileIndex).toString())
            }
        }
    }

    /**
     * Crédits du fond de carte affiché (coin bas gauche de la carte, et image partagée).
     *
     * Le texte doit nommer la source des tuiles RÉELLEMENT affichées : elle dépend du fond
     * (`provider`) mais aussi du style (`ignStyle == 2` = satellite → orthophotos IGN sur le
     * fond IGN, imagerie Esri sur le fond OSM). Les fonds 2 et 3 servent des tuiles bâties
     * sur des données OpenStreetMap : on cite OSM en plus du fournisseur de tuiles.
     *
     * Volontairement court (le bandeau tient sur une ligne) : le détail complet des licences
     * est derrière le lien, c'est `url()` qui porte la mention légale exhaustive.
     */
    object MapAttribution {
        /** `provider` : 0 IGN, 1 OSM, 2 CARTO, 3 OpenTopoMap, 4 hors-ligne (données OSM). */
        fun text(provider: Int, ignStyle: Int): String = when {
            provider == 0 -> "© IGN"
            provider == 1 && ignStyle == 2 -> "© Esri"
            provider == 2 -> "© CARTO, OSM"
            provider == 3 -> "© OpenTopoMap, OSM"
            else -> "© OpenStreetMap"
        }

        fun url(provider: Int, ignStyle: Int): String = when {
            provider == 0 -> "https://geoservices.ign.fr/"
            provider == 1 && ignStyle == 2 -> "https://www.arcgis.com/home/item.html?id=10df2279f9684e4a9f6a7f08febac2a9"
            provider == 2 -> "https://carto.com/attributions"
            provider == 3 -> "https://opentopomap.org/about"
            else -> "https://www.openstreetmap.org/copyright"
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
        inactiveOperatorKeys: Set<String> = emptySet(),
        satelliteContrast: Boolean = false
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
            "m3|$showAzimuths|$def|${operatorsOnSite.joinToString(",")}|$inactiveSignature|$azimuthSignature|$satelliteContrast"

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
                paint.strokeCap = Paint.Cap.ROUND

                // Les segments d'un même épi sont bout à bout : on pose TOUS les liserés d'abord,
                // sinon celui d'un segment mangerait la couleur de son voisin. Le débord côté
                // centre est ensuite recouvert par le camembert, dessiné juste après.
                if (satelliteContrast) {
                    paint.strokeWidth = strokeWidth + 2f * AZIMUTH_OUTLINE_UNITS
                    sortedOpsForAz.forEachIndexed { index, op ->
                        paint.color = contrastOutlineColor(colorForOperator(op))
                        val startY = center - innerRadius - (index * segmentLength)
                        val endY = startY - segmentLength
                        canvas.drawLine(center, startY, center, endY, paint)
                    }
                }

                paint.strokeWidth = strokeWidth
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
            if (satelliteContrast) {
                paint.color = contrastOutlineColor(android.graphics.Color.GRAY)
                canvas.drawCircle(center, center, pieRadius + PIE_OUTLINE_UNITS, paint)
            }
            paint.color = android.graphics.Color.GRAY
            canvas.drawCircle(center, center, pieRadius, paint)
        } else {
            if (satelliteContrast) {
                // Le liseré est le même camembert, à peine plus grand et peint dessous : la bande
                // de couleur garde toute son épaisseur, le trait ne fait que déborder au-dehors.
                val outlineRect = android.graphics.RectF(
                    center - pieRadius - PIE_OUTLINE_UNITS,
                    center - pieRadius - PIE_OUTLINE_UNITS,
                    center + pieRadius + PIE_OUTLINE_UNITS,
                    center + pieRadius + PIE_OUTLINE_UNITS
                )
                drawOperatorSlices(canvas, outlineRect, operatorsOnSite, paint) { op ->
                    contrastOutlineColor(colorForOperator(op))
                }
            }
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

    fun createClusterIcon(
        context: Context,
        operators: List<String>,
        count: Int,
        defaultOp: String,
        satelliteContrast: Boolean = false
    ): BitmapDrawable {
        // ✅ CORRECTION : On intègre l'opérateur par défaut dans le cache pour forcer le redessin si on change d'avis !
        val cacheKey = "${operators.sorted().joinToString("_")}_${count}_${defaultOp}_$satelliteContrast"

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
            drawOperatorRing(canvas, size.toFloat(), sortedOps, colorMap, paint, satelliteContrast)
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
        paint: Paint,
        satelliteContrast: Boolean = false
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

        // L'anneau touche déjà le bord du bitmap : impossible de déborder comme sur le camembert,
        // le liseré est donc posé sur la tranche extérieure de l'anneau.
        if (satelliteContrast) {
            val outlineWidth = size * 0.05f
            val outlineRadius = center - outlineWidth / 2f
            val outlineRect = android.graphics.RectF(
                center - outlineRadius,
                center - outlineRadius,
                center + outlineRadius,
                center + outlineRadius
            )
            paint.strokeWidth = outlineWidth
            operators.forEachIndexed { index, op ->
                paint.color = contrastOutlineColor(colorMap[op] ?: android.graphics.Color.GRAY)
                canvas.drawArc(outlineRect, -90f + index * sweep, sweep + overlap, false, paint)
            }
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
