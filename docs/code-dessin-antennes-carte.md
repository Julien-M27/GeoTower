# Code du dessin des antennes sur la carte — GeoTower

> Document de référence regroupant **tout le code qui dessine les marqueurs d'antennes** (et apparentés) sur la carte OSMDroid.
> Généré le 2026-06-30. Le code ci-dessous est copié des sources ; en cas de doute, la source fait foi.

## Sommaire

1. [Vue d'ensemble](#1-vue-densemble)
2. [Les couleurs des opérateurs — `OperatorColors.kt`](#2-les-couleurs-des-opérateurs)
3. [L'icône d'antenne — `createAdaptiveMarker()`](#3-licône-dantenne--createadaptivemarker)
4. [Le camembert / le tri des azimuts (helpers)](#4-helpers-de-licône--camembert--parsing-azimuts)
5. [Les faisceaux projetés — classe `AntennaMarker`](#5-les-faisceaux-projetés--classe-antennamarker)
6. [Les clusters — `createClusterIcon()`](#6-les-clusters--createclustericon)
7. [Les marqueurs radio — `createRadioMarkerIcon()` + `RadioMarker`](#7-les-marqueurs-radio)
8. [Les badges « hors service » (HS)](#8-les-badges-hors-service-hs)
9. [Les points de couverture SignalQuest](#9-les-points-de-couverture-signalquest)
10. [Récapitulatif : caches & réglages visuels](#10-récapitulatif--caches--réglages-visuels)

---

## 1. Vue d'ensemble

Le dessin d'une antenne se fait sur **deux couches superposées** :

| Couche | Quoi | Où | Nature |
|--------|------|-----|--------|
| **A — l'icône (le pin)** | cercle camembert par opérateur + pictogramme antenne | `MapUtils.createAdaptiveMarker()` | bitmap **figé** mis en cache |
| **B — les faisceaux** | traits d'azimut, cônes, pastilles par opérateur | classe `AntennaMarker.draw()` | **redessiné à chaque frame** (s'adapte au zoom) |

`AntennaMarker.draw()` dessine d'abord les faisceaux, puis appelle `super.draw()` qui pose l'icône (couche A) **par-dessus**.

Fichiers concernés :

- `app/src/main/java/fr/geotower/utils/MapUtils.kt` — fabrique des bitmaps (icône, cluster, radio).
- `app/src/main/java/fr/geotower/ui/screens/map/MapScreen.kt` — classes `Marker` custom (faisceaux), badges HS, couverture.
- `app/src/main/java/fr/geotower/utils/OperatorColors.kt` — palette + résolution des couleurs par opérateur.

Variantes du même principe : **clusters** (dézoomé), **radio/TV/FH**, **badge HS**, **points de couverture**.

---

## 2. Les couleurs des opérateurs

`app/src/main/java/fr/geotower/utils/OperatorColors.kt` — source unique de vérité pour les couleurs. Chaque opérateur a une clé, un libellé, une couleur hex/ARGB, des alias (pour parser le texte brut de la base) et une région (métropole / outre-mer).

```kotlin
package fr.geotower.utils

import android.graphics.Color as AndroidColor

data class OperatorColorSpec(
    val key: String,
    val label: String,
    val colorHex: String,
    val colorArgb: Long,
    val aliases: List<String>,
    val region: OperatorRegion = OperatorRegion.METRO
)

enum class OperatorRegion { METRO, OVERSEAS }

object OperatorColors {
    const val ORANGE_KEY = "ORANGE"
    const val BOUYGUES_KEY = "BOUYGUES"
    const val SFR_KEY = "SFR"
    const val FREE_KEY = "FREE"
    // … + 16 clés outre-mer (DIGICEL, SRR, ZEOP, OPT_NC, ONATI, VITI, SPT…)

    const val ORANGE_HEX = "#FF7900"
    const val BOUYGUES_HEX = "#009FE3"
    const val SFR_HEX = "#E2001A"
    const val FREE_HEX = "#757575"
    const val UNKNOWN_HEX = "#808080"

    val all: List<OperatorColorSpec> = listOf(
        OperatorColorSpec(ORANGE_KEY,   "Orange",           ORANGE_HEX,   ORANGE_ARGB,   listOf("ORANGE", "ORANGE FRANCE")),
        OperatorColorSpec(BOUYGUES_KEY, "Bouygues Telecom", BOUYGUES_HEX, BOUYGUES_ARGB, listOf("BOUYGUES", "BOUYGUES TELECOM", "BYTEL")),
        OperatorColorSpec(SFR_KEY,      "SFR",              SFR_HEX,      SFR_ARGB,      listOf("SFR", "SOCIETE FRANCAISE DU RADIOTELEPHONE")),
        OperatorColorSpec(FREE_KEY,     "Free Mobile",      FREE_HEX,     FREE_ARGB,     listOf("FREE", "FREE MOBILE")),
        OperatorColorSpec(DIGICEL_KEY,  "Digicel",          "#F4DA95",    0xFFF4DA95,    listOf("DIGICEL", "DIGICEL AFG"), OperatorRegion.OVERSEAS),
        // … (Free Caraïbe, Outremer Telecom, UTS, Dauphin, SRR, Telco OI, ZEOP,
        //    Maore Mobile, SPM, Globaltel, OPT NC, ONATi/Vini, PMT/Vodafone, Ora, SPT)
    )

    val orderedKeys: List<String> = all.map { it.key }

    private val specsByKey: Map<String, OperatorColorSpec> = all.associateBy { it.key }

    /** Détecte la clé d'un opérateur depuis un texte brut, via les alias (alias le plus long gagne). */
    fun keyFor(raw: String?): String? {
        val normalized = raw?.uppercase()?.trim().orEmpty()
        if (normalized.isBlank()) return null
        if (normalized in specsByKey) return normalized
        return all.asSequence()
            .flatMap { spec -> spec.aliases.asSequence().map { alias -> spec to alias.uppercase() } }
            .filter { (_, alias) -> normalized.contains(alias) }
            .maxByOrNull { (_, alias) -> alias.length }
            ?.first?.key
    }

    /** Parse un texte multi-opérateurs ("Orange - SFR / Free") → liste de clés. */
    fun keysFor(raw: String?): List<String> {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return emptyList()
        return value
            .split(Regex("\\s+-\\s+|[,;/\\u2022\\r\\n]+"))
            .mapNotNull { keyFor(it) }
            .distinct()
            .ifEmpty { keyFor(value)?.let(::listOf) ?: emptyList() }
    }

    fun colorInt(raw: String?, fallback: Int = AndroidColor.GRAY): Int =
        keyFor(raw)?.let { colorIntForKey(it) } ?: fallback

    fun colorIntForKey(key: String, fallback: Int = AndroidColor.GRAY): Int =
        specsByKey[key]?.colorHex?.let(AndroidColor::parseColor) ?: fallback

    /** Map clé → couleur Android, utilisée par tous les dessins (camembert, anneau cluster). */
    fun androidColorMap(): Map<String, Int> =
        specsByKey.mapValues { (_, spec) -> AndroidColor.parseColor(spec.colorHex) }
}
```

**Couleurs métropole :** Orange `#FF7900` · Bouygues `#009FE3` · SFR `#E2001A` · Free `#757575`. Les 16 opérateurs outre-mer ont chacun leur teinte (voir `all`). Un opérateur inactif est dessiné en gris `rgb(196,199,204)` (constante `INACTIVE_OPERATOR_COLOR` dans `MapUtils`).

---

## 3. L'icône d'antenne — `createAdaptiveMarker()`

`app/src/main/java/fr/geotower/utils/MapUtils.kt:56`. Construit le bitmap du pin (≈105 dp) : traits d'azimut optionnels → camembert opérateurs → pastilles blanches/grises → pictogramme antenne. Le tout est mis en cache (`markerIconCache`, 500 entrées) sur une clé **purement visuelle** (mêmes opérateurs + mêmes azimuts ⇒ même bitmap partagé entre sites).

```kotlin
private val INACTIVE_OPERATOR_COLOR = android.graphics.Color.rgb(196, 199, 204)

val markerIconCache = android.util.LruCache<String, BitmapDrawable>(500)

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
                    operatorKeys.forEach { opClean -> if (!list.contains(opClean)) list.add(opClean) }
                }
            }
        }
        map
    } else emptyMap()

    // --- Clé de cache basée UNIQUEMENT sur le rendu visuel ---
    val inactiveSignature = inactiveOperatorKeys.sorted().joinToString(",")
    val azimuthSignature = if (showAzimuths) {
        azimutMap.entries.sortedBy { it.key }.joinToString("|") { (angle, ops) ->
            "$angle>" + ops.sortedBy { priorityList.indexOf(it) }.joinToString("+")
        }
    } else ""
    val cacheKey = "m2|$showAzimuths|$def|${operatorsOnSite.joinToString(",")}|$inactiveSignature|$azimuthSignature"
    markerIconCache.get(cacheKey)?.let { return it }

    val metrics = context.resources.displayMetrics
    val density = metrics.density

    // Taille cible proportionnelle en DP (~105dp)
    val targetSize = (105 * density).toInt()
    val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // On met le canvas à l'échelle pour garder les formules géométriques basées sur "230"
    val scale = targetSize / 230f
    canvas.scale(scale, scale)

    val size = 230            // repère mathématique
    val center = size / 2f
    val pieRadius = 45f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { isAntiAlias = true }

    val colorMap = OperatorColors.androidColorMap()
    fun colorForOperator(op: String): Int =
        if (op in inactiveOperatorKeys) INACTIVE_OPERATOR_COLOR
        else colorMap[op] ?: android.graphics.Color.GRAY

    // --- 1) Traits d'azimut autour du cercle (segmentés par opérateur) ---
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

    // --- 2) Le camembert central (un secteur par opérateur) ---
    paint.style = Paint.Style.FILL
    val rect = android.graphics.RectF(center - pieRadius, center - pieRadius, center + pieRadius, center + pieRadius)
    if (operatorsOnSite.isEmpty()) {
        paint.color = android.graphics.Color.GRAY
        canvas.drawCircle(center, center, pieRadius, paint)
    } else {
        drawOperatorSlices(canvas, rect, operatorsOnSite, paint, ::colorForOperator)
    }

    // Pastilles concentriques (fond du pictogramme)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(center, center, pieRadius * 0.40f, paint)
    paint.color = android.graphics.Color.parseColor("#EBEBEB")
    canvas.drawCircle(center, center, pieRadius * 0.80f, paint)

    // --- 3) Le pictogramme antenne (pylône + losange + ondes) ---
    val iconScale = 100f
    val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isAntiAlias = true
        style = Paint.Style.STROKE; strokeWidth = iconScale * 0.035f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
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

    val waveInner = 17f * u; val waveOuter = 29f * u
    val rectInner = android.graphics.RectF(cx - waveInner, dy - waveInner, cx + waveInner, dy + waveInner)
    val rectOuter = android.graphics.RectF(cx - waveOuter, dy - waveOuter, cx + waveOuter, dy + waveOuter)
    canvas.drawArc(rectInner, -40f, 80f, false, iconPaint); canvas.drawArc(rectOuter, -40f, 80f, false, iconPaint)
    canvas.drawArc(rectInner, 140f, 80f, false, iconPaint); canvas.drawArc(rectOuter, 140f, 80f, false, iconPaint)

    val finalDrawable = BitmapDrawable(context.resources, bitmap)
    markerIconCache.put(cacheKey, finalDrawable)
    return finalDrawable
}
```

---

## 4. Helpers de l'icône — camembert & parsing azimuts

`app/src/main/java/fr/geotower/utils/MapUtils.kt`. `drawOperatorSlices()` (l.469) trace chaque part du camembert ; le parsing accepte de nombreux formats d'azimut (`45`, `45°`, `45deg`, `45,5`…).

```kotlin
// l.469 — le camembert : une part par opérateur, à parts égales
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

// l.17 — regex tolérante aux unités
private val markerAzimuthWithUnitRegex = Regex(
    "([0-9]{1,3}(?:[.,][0-9]+)?)\\s*(?:\\u00B0|\\u00C2\\u00B0|deg(?:res|ree|rees)?|degrees?)",
    RegexOption.IGNORE_CASE
)

// l.226 — extrait les angles d'une chaîne d'azimuts
private fun parseMarkerAzimuths(rawAzimuths: String): List<Int> {
    val explicitAngles = markerAzimuthWithUnitRegex.findAll(rawAzimuths)
        .mapNotNull { match -> normalizeMarkerAzimuth(match.groupValues.getOrNull(1)) }
        .toList()
    if (explicitAngles.isNotEmpty()) return explicitAngles.distinct()
    return rawAzimuths.split(",")
        .mapNotNull { value -> normalizeMarkerAzimuth(value.trim()) }
        .distinct()
}

// l.237 — normalise un angle dans [0,360) (360 → 0)
private fun normalizeMarkerAzimuth(rawValue: String?): Int? {
    val angle = rawValue?.replace(',', '.')?.toDoubleOrNull()?.toInt() ?: return null
    if (angle !in 0..360) return null
    return if (angle == 360) 0 else angle
}
```

---

## 5. Les faisceaux projetés — classe `AntennaMarker`

`app/src/main/java/fr/geotower/ui/screens/map/MapScreen.kt:4169`. Hérite de `org.osmdroid.views.overlay.Marker`. Tout ce qui dépend du zoom (longueur des faisceaux) est dessiné ici, par-dessus la carte. Les angles (`cos`/`sin`) et les `Paint` sont **précalculés dans le `init {}`** pour ne pas les recréer 60×/s.

```kotlin
class AntennaMarker(
    private val mapView: org.osmdroid.views.MapView,
    private val siteAntennas: List<LocalisationEntity>,
    private val primaryColor: Int
) : org.osmdroid.views.overlay.Marker(mapView) {

    private val density = mapView.context.resources.displayMetrics.density
    private val ptCenter = android.graphics.Point()

    // HitBox 100% ronde : on ignore les faisceaux et le carré transparent du bitmap
    override fun hitTest(event: android.view.MotionEvent, mapView: org.osmdroid.views.MapView): Boolean {
        val pj = mapView.projection
        val screenCoords = android.graphics.Point()
        pj.toPixels(position, screenCoords)
        val dx = event.x - screenCoords.x
        val dy = event.y - screenCoords.y
        val clickRadius = 22f * density        // rayon cliquable fixe de 22dp
        return (dx * dx + dy * dy) <= (clickRadius * clickRadius)
    }

    // Une entrée = un azimut, avec ses couleurs/pinceaux pré-mâchés
    private class GroupedAzimuthData(
        val azimuth: Float,
        val cos: Float,
        val sin: Float,
        val linePaint: android.graphics.Paint,
        val conePaint: android.graphics.Paint?,      // pinceau translucide du cône
        val coneEdgePaint: android.graphics.Paint?,
        val dotColors: List<Int>
    )

    private val precalculatedMobileAzimuths = mutableListOf<GroupedAzimuthData>()
    private val precalculatedFhAzimuths = mutableListOf<GroupedAzimuthData>()

    private val dotPaints = mutableMapOf<Int, android.graphics.Paint>()
    private fun getDotPaint(colorInt: Int): android.graphics.Paint =
        dotPaints.getOrPut(colorInt) {
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
                color = colorInt
            }
        }

    // Ordre des opérateurs sur un même axe (favori d'abord, puis Orange/Bouygues/SFR/Free, puis le reste)
    private fun sortAzimuthOperatorKeys(operatorKeys: Set<String>, defaultOperatorKey: String?): List<String> {
        val baseOrder = listOf(OperatorColors.ORANGE_KEY, OperatorColors.BOUYGUES_KEY, OperatorColors.SFR_KEY, OperatorColors.FREE_KEY)
        val ordered = mutableListOf<String>()
        if (defaultOperatorKey != null && defaultOperatorKey in operatorKeys) ordered += defaultOperatorKey
        baseOrder.forEach { key -> if (key in operatorKeys && key !in ordered) ordered += key }
        OperatorColors.orderedKeys.forEach { key -> if (key in operatorKeys && key !in ordered) ordered += key }
        return ordered.ifEmpty { operatorKeys.toList() }
    }

    init {
        // 1) Regrouper : angle -> ensemble d'opérateurs (mobile et FH séparés)
        val angleToOperatorsMobile = mutableMapOf<Float, MutableSet<String>>()
        val angleToOperatorsFh = mutableMapOf<Float, MutableSet<String>>()

        siteAntennas.forEach { antenna ->
            val operatorKeys = OperatorColors.keysFor(antenna.operateur)
            if (operatorKeys.isEmpty()) return@forEach
            if (!antenna.azimuts.isNullOrBlank()) {
                antenna.azimuts.split(",").mapNotNull { it.trim().toFloatOrNull() }.forEach { az ->
                    angleToOperatorsMobile.getOrPut(az) { mutableSetOf() }.addAll(operatorKeys)
                }
            }
            if (fr.geotower.utils.AppConfig.showTechnoFH.value && !antenna.azimutsFh.isNullOrBlank()) {
                antenna.azimutsFh.split(",").mapNotNull { it.trim().toFloatOrNull() }.forEach { az ->
                    angleToOperatorsFh.getOrPut(az) { mutableSetOf() }.addAll(operatorKeys)
                }
            }
        }

        val defaultOperatorKey = OperatorColors.keyFor(fr.geotower.utils.AppConfig.defaultOperator.value)

        // 2) Transformer en données de dessin (cos/sin + pinceaux)
        angleToOperatorsMobile.forEach { (az, operatorKeys) ->
            val rad = Math.toRadians(az - 90.0)          // azimut 0 = Nord ; Android 0 = Est → -90°
            val cos = Math.cos(rad).toFloat()
            val sin = Math.sin(rad).toFloat()

            val sortedColors = sortAzimuthOperatorKeys(operatorKeys, defaultOperatorKey)
                .map { OperatorColors.colorIntForKey(it, fallback = primaryColor) }
            val mainColor = sortedColors.first()

            val linePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = mainColor
                strokeWidth = 3.5f * density
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            val conePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
                color = androidx.core.graphics.ColorUtils.setAlphaComponent(mainColor, 50)   // ~20% d'opacité
            }
            val coneEdgePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = androidx.core.graphics.ColorUtils.setAlphaComponent(mainColor, 170)
                strokeWidth = 2.2f * density
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            precalculatedMobileAzimuths.add(GroupedAzimuthData(az, cos, sin, linePaint, conePaint, coneEdgePaint, sortedColors))
        }

        // Faisceaux hertziens (FH) : traits en pointillés, pas de cône
        angleToOperatorsFh.forEach { (az, operatorKeys) ->
            val rad = Math.toRadians(az - 90.0)
            val cos = Math.cos(rad).toFloat()
            val sin = Math.sin(rad).toFloat()
            val sortedColors = sortAzimuthOperatorKeys(operatorKeys, defaultOperatorKey)
                .map { OperatorColors.colorIntForKey(it, fallback = primaryColor) }
            val mainColor = sortedColors.first()
            val dashedPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.argb(200, android.graphics.Color.red(mainColor), android.graphics.Color.green(mainColor), android.graphics.Color.blue(mainColor))
                strokeWidth = 3f * density
                strokeCap = android.graphics.Paint.Cap.ROUND
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(5f * density, 5f * density), 0f)
            }
            precalculatedFhAzimuths.add(GroupedAzimuthData(az, cos, sin, dashedPaint, null, null, sortedColors))
        }
    }

    override fun draw(canvas: android.graphics.Canvas, projection: org.osmdroid.views.Projection) {
        val zoom = mapView.zoomLevelDouble
        val showLines = fr.geotower.utils.AppConfig.showAzimuths.value
        val showCones = fr.geotower.utils.AppConfig.showAzimuthsCone.value

        if (zoom >= 14.0 && (showLines || showCones)) {
            projection.toPixels(mPosition, ptCenter)

            val beamLengthPx = when {                  // longueur du faisceau selon le zoom
                zoom >= 18.0 -> 60f * density
                zoom >= 17.0 -> 50f * density
                zoom >= 16.0 -> 40f * density
                zoom >= 15.0 -> 30f * density
                else         -> 25f * density
            }
            val pointRadius = 3.5f * density
            val fhRadius = pointRadius * 0.7f
            val circleOffsetPx = 17f * density          // on part du bord du rond, pas du centre
            val totalRadiusPx = circleOffsetPx + beamLengthPx
            val gapMobile = pointRadius * 2.0f
            val gapFh = fhRadius * 2.0f

            val rectF = android.graphics.RectF(
                ptCenter.x - totalRadiusPx, ptCenter.y - totalRadiusPx,
                ptCenter.x + totalRadiusPx, ptCenter.y + totalRadiusPx
            )

            // --- MOBILES ---
            precalculatedMobileAzimuths.forEach { data ->
                // 1) le cône de 70° (au fond)
                if (showCones && data.conePaint != null) {
                    val startAngle = data.azimuth - 90f - 35f       // centre le cône sur l'azimut exact
                    canvas.drawArc(rectF, startAngle, 70f, true, data.conePaint)
                    data.coneEdgePaint?.let { edgePaint ->
                        drawConeEdgeLines(canvas, data.azimuth, circleOffsetPx, totalRadiusPx, edgePaint)
                    }
                }
                // 2) le trait + les pastilles (une par opérateur)
                if (showLines) {
                    val startX = ptCenter.x + circleOffsetPx * data.cos
                    val startY = ptCenter.y + circleOffsetPx * data.sin
                    val endX = ptCenter.x + totalRadiusPx * data.cos
                    val endY = ptCenter.y + totalRadiusPx * data.sin
                    canvas.drawLine(startX, startY, endX, endY, data.linePaint)
                    data.dotColors.forEachIndexed { index, colorInt ->
                        val offsetMag = index * gapMobile
                        canvas.drawCircle(endX + data.cos * offsetMag, endY + data.sin * offsetMag, pointRadius, getDotPaint(colorInt))
                    }
                }
            }

            // --- FAISCEAUX HERTZIENS (FH) ---
            if (fr.geotower.utils.AppConfig.showTechnoFH.value && showLines) {
                precalculatedFhAzimuths.forEach { data ->
                    val startX = ptCenter.x + circleOffsetPx * data.cos
                    val startY = ptCenter.y + circleOffsetPx * data.sin
                    val endX = ptCenter.x + totalRadiusPx * data.cos
                    val endY = ptCenter.y + totalRadiusPx * data.sin
                    canvas.drawLine(startX, startY, endX, endY, data.linePaint)
                    data.dotColors.forEachIndexed { index, colorInt ->
                        val offsetMag = index * gapFh
                        canvas.drawCircle(endX + data.cos * offsetMag, endY + data.sin * offsetMag, fhRadius, getDotPaint(colorInt))
                    }
                }
            }
        }
        super.draw(canvas, projection)       // ← pose l'icône (createAdaptiveMarker) par-dessus
    }

    // Les deux bords du cône (±35° autour de l'azimut)
    private fun drawConeEdgeLines(
        canvas: android.graphics.Canvas, azimuth: Float,
        startRadiusPx: Float, endRadiusPx: Float, paint: android.graphics.Paint
    ) {
        listOf(azimuth - 35f, azimuth + 35f).forEach { edgeAzimuth ->
            val edgeRad = Math.toRadians(edgeAzimuth - 90.0)
            val edgeCos = Math.cos(edgeRad).toFloat()
            val edgeSin = Math.sin(edgeRad).toFloat()
            canvas.drawLine(
                ptCenter.x + startRadiusPx * edgeCos, ptCenter.y + startRadiusPx * edgeSin,
                ptCenter.x + endRadiusPx * edgeCos, ptCenter.y + endRadiusPx * edgeSin, paint
            )
        }
    }
}
```

---

## 6. Les clusters — `createClusterIcon()`

`app/src/main/java/fr/geotower/utils/MapUtils.kt:402`. En dézoomé : un **anneau** segmenté par opérateur + le **nombre** de sites au centre. Cache `clusterIconCache` (200 entrées).

```kotlin
val clusterIconCache = android.util.LruCache<String, BitmapDrawable>(200)

fun createClusterIcon(context: Context, operators: List<String>, count: Int, defaultOp: String): BitmapDrawable {
    val cacheKey = "${operators.sorted().joinToString("_")}_${count}_$defaultOp"
    clusterIconCache.get(cacheKey)?.let { return it }

    val density = context.resources.displayMetrics.density
    val size = (45 * density).toInt()            // ~45dp, s'adapte à la densité
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { isAntiAlias = true; style = Paint.Style.FILL }

    val colorMap = OperatorColors.androidColorMap()

    // Tri selon l'opérateur préféré
    val def = defaultOp.uppercase()
    val priorityList = mutableListOf<String>()
    OperatorColors.keyFor(def)?.let { priorityList.add(it) }
    OperatorColors.orderedKeys.forEach { if (!priorityList.contains(it)) priorityList.add(it) }
    val sortedOps = operators.flatMap { OperatorColors.keysFor(it) }.distinct().sortedBy { op -> priorityList.indexOf(op) }

    if (sortedOps.isEmpty()) {
        paint.color = android.graphics.Color.GRAY
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    } else {
        drawOperatorRing(canvas, size.toFloat(), sortedOps, colorMap, paint)
    }

    // Disque blanc central + nombre
    val centerPoint = size / 2f
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(centerPoint, centerPoint, centerPoint * 0.80f, paint)

    paint.color = android.graphics.Color.parseColor("#37474F")
    paint.isFakeBoldText = true
    paint.textAlign = Paint.Align.CENTER
    val countStr = count.toString()
    paint.textSize = when (countStr.length) {     // texte adapté au nb de chiffres
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

// l.483 — l'anneau : un arc par opérateur (léger chevauchement pour éviter les liserés)
private fun drawOperatorRing(canvas: Canvas, size: Float, operators: List<String>, colorMap: Map<String, Int>, paint: Paint) {
    val center = size / 2f
    val strokeWidth = size * 0.22f
    val radius = center - strokeWidth / 2f
    val ringRect = android.graphics.RectF(center - radius, center - radius, center + radius, center + radius)
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
```

---

## 7. Les marqueurs radio

Pour les services **non-mobiles** (TV, Radio FM, FH, ferroviaire, satellite…). Couleur déterminée par les masques de service/système.

### 7a. Couleurs radio — `radioMarkerColor()` / `radioMarkerColors()`

`app/src/main/java/fr/geotower/utils/MapUtils.kt:369`.

```kotlin
fun radioMarkerColor(serviceMask: Int, systemMask: Int): Int = when {
    (serviceMask and RadioServiceMasks.FH) != 0 -> android.graphics.Color.parseColor("#0D47A1")   // FH : bleu foncé
    (systemMask and RadioSystemMasks.TV) != 0 -> android.graphics.Color.parseColor("#8BC34A")      // TV : vert
    (systemMask and RadioSystemMasks.RADIO) != 0 -> android.graphics.Color.parseColor("#FDD835")   // Radio : jaune
    (serviceMask and (RadioServiceMasks.PRIVATE or RadioServiceMasks.RAIL or RadioServiceMasks.TRANSPORT)) != 0 ->
        android.graphics.Color.parseColor("#006D77")                                               // privé/rail/transport : teal
    else -> android.graphics.Color.parseColor("#111111")                                           // autre/satellite/radar : noir
}

// Version multi-couleurs (camembert/anneau radio) : une couleur par catégorie présente
private fun radioMarkerColors(serviceMask: Int, systemMask: Int): List<Int> {
    val colors = mutableListOf<Int>()
    if ((systemMask and RadioSystemMasks.TV) != 0) colors += android.graphics.Color.parseColor("#8BC34A")
    if ((systemMask and RadioSystemMasks.RADIO) != 0) colors += android.graphics.Color.parseColor("#FDD835")
    if ((serviceMask and (RadioServiceMasks.PRIVATE or RadioServiceMasks.RAIL or RadioServiceMasks.TRANSPORT)) != 0) colors += android.graphics.Color.parseColor("#006D77")
    if ((serviceMask and RadioServiceMasks.FH) != 0) colors += android.graphics.Color.parseColor("#0D47A1")
    if ((serviceMask and (RadioServiceMasks.SATELLITE or RadioServiceMasks.RADAR or RadioServiceMasks.OTHER)) != 0 || colors.isEmpty())
        colors += android.graphics.Color.parseColor("#111111")
    return colors.distinct()
}
```

### 7b. Icône radio — `createRadioMarkerIcon()`

`app/src/main/java/fr/geotower/utils/MapUtils.kt:247`. Même pictogramme antenne que le mobile, mais cercle coloré par service. En cluster : anneau + nombre. Cache `radioIconCache` (300 entrées).

```kotlin
val radioIconCache = android.util.LruCache<String, BitmapDrawable>(300)

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
        isAntiAlias = true; style = Paint.Style.FILL; color = radioMarkerColor(serviceMask, systemMask)
    }

    if (isCluster) {
        val centerPoint = size / 2f
        drawRadioClusterRing(canvas, size.toFloat(), radioMarkerColors(serviceMask, systemMask), paint)
        paint.style = Paint.Style.FILL; paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(centerPoint, centerPoint, centerPoint * 0.80f, paint)
        paint.color = android.graphics.Color.parseColor("#37474F")
        paint.isFakeBoldText = true; paint.textAlign = Paint.Align.CENTER
        val text = safeCount.toString()
        paint.textSize = when (text.length) { 1, 2 -> size * 0.36f; 3 -> size * 0.30f; 4 -> size * 0.24f; else -> size * 0.19f }
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
        paint.style = Paint.Style.FILL; paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(center, center, pieRadius * 0.40f, paint)
        paint.color = android.graphics.Color.parseColor("#EBEBEB")
        canvas.drawCircle(center, center, pieRadius * 0.80f, paint)

        // … même pictogramme (pylône + losange) que createAdaptiveMarker,
        //    mais les ondes sont 2 arcs de 110° (gauche/droite) au lieu de 4 arcs de 80°.
        val iconScale = 100f
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = iconScale * 0.035f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = android.graphics.Color.parseColor("#34404A")
        }
        val cx = center; val cy = center + (iconScale * 0.04f); val u = iconScale / 200f
        /* pylône + losange + point : identiques à la §3 */
        val waveInner = 17f * u; val waveOuter = 29f * u
        val rectInner = android.graphics.RectF(cx - waveInner, /*…*/ cy /* … */ + waveInner, cx + waveInner, cy + waveInner)
        val rectOuter = android.graphics.RectF(cx - waveOuter, cy - waveOuter, cx + waveOuter, cy + waveOuter)
        listOf(0f, 180f).forEach { start ->
            canvas.drawArc(rectInner, start - 55f, 110f, false, iconPaint)
            canvas.drawArc(rectOuter, start - 55f, 110f, false, iconPaint)
        }
    }

    val drawable = BitmapDrawable(context.resources, bitmap)
    radioIconCache.put(cacheKey, drawable)
    return drawable
}
```

> Helpers associés dans le même fichier : `drawRadioClusterRing()` (l.514, l'anneau radio) et `drawRadioMarkerSlices()` (l.545, le camembert radio) — structurellement identiques à `drawOperatorRing` / `drawOperatorSlices` mais prennent une liste de couleurs au lieu de clés d'opérateurs.

### 7c. Faisceaux radio — classe `RadioMarker`

`app/src/main/java/fr/geotower/ui/screens/map/MapScreen.kt:4440`. Équivalent de `AntennaMarker` pour la radio : couleur unique, pas de cône, pastille simple en bout de trait.

```kotlin
class RadioMarker(
    private val mapView: org.osmdroid.views.MapView,
    private val radioMarker: RadioMapMarker,
    private val showCircle: Boolean
) : org.osmdroid.views.overlay.Marker(mapView) {

    private data class RadioAzimuthLine(val cos: Float, val sin: Float)

    private val density = mapView.context.resources.displayMetrics.density
    private val ptCenter = android.graphics.Point()
    private val color = MapUtils.radioMarkerColor(radioMarker.serviceMask, radioMarker.systemMask)
    private val azimuthLines = radioMarker.azimuths.map { azimuth ->
        val rad = Math.toRadians(azimuth - 90.0)
        RadioAzimuthLine(Math.cos(rad).toFloat(), Math.sin(rad).toFloat())
    }
    private val linePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        color = androidx.core.graphics.ColorUtils.setAlphaComponent(this@RadioMarker.color, 210)
        strokeWidth = 2.35f * density
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    private val dotPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = androidx.core.graphics.ColorUtils.setAlphaComponent(this@RadioMarker.color, 230)
    }

    override fun hitTest(event: android.view.MotionEvent, mapView: org.osmdroid.views.MapView): Boolean {
        if (!showCircle) return false
        val pj = mapView.projection
        val screenCoords = android.graphics.Point()
        pj.toPixels(position, screenCoords)
        val dx = event.x - screenCoords.x; val dy = event.y - screenCoords.y
        val clickRadius = 18f * density
        return (dx * dx + dy * dy) <= (clickRadius * clickRadius)
    }

    override fun draw(canvas: android.graphics.Canvas, projection: org.osmdroid.views.Projection) {
        val zoom = mapView.zoomLevelDouble
        if (!radioMarker.isCluster && zoom >= 14.0 && AppConfig.showAzimuths.value && azimuthLines.isNotEmpty()) {
            projection.toPixels(mPosition, ptCenter)
            val beamLengthPx = when {
                zoom >= 18.0 -> 56f * density
                zoom >= 17.0 -> 47f * density
                zoom >= 16.0 -> 38f * density
                zoom >= 15.0 -> 29f * density
                else         -> 23f * density
            }
            val circleOffsetPx = 17f * density
            val totalRadiusPx = circleOffsetPx + beamLengthPx
            val dotRadius = 2.8f * density
            azimuthLines.forEach { data ->
                val startX = ptCenter.x + circleOffsetPx * data.cos
                val startY = ptCenter.y + circleOffsetPx * data.sin
                val endX = ptCenter.x + totalRadiusPx * data.cos
                val endY = ptCenter.y + totalRadiusPx * data.sin
                canvas.drawLine(startX, startY, endX, endY, linePaint)
                canvas.drawCircle(endX, endY, dotRadius, dotPaint)
            }
        }
        super.draw(canvas, projection)
    }
}
```

---

## 8. Les badges « hors service » (HS)

`app/src/main/java/fr/geotower/ui/screens/map/MapScreen.kt:4767`. Un petit badge rond gris avec un « ! » rouge, composé **par-dessus** le marqueur de base pour signaler une antenne en panne.

```kotlin
// Dessine le badge "!" (rond gris clair + point d'exclamation rouge), avec cache
fun createHsBadge(context: Context): android.graphics.drawable.BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = (32 * density).roundToInt().coerceAtLeast(1)
    hsBadgeDrawableCache.get(size)?.let { return it }
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // 1. Le fond (masque le logo de l'antenne)
    val maskPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#F5F5F5")
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, maskPaint)

    // 2. Le "!" rouge
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#E53935")
        textSize = 24f * density
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    canvas.drawText("!", size / 2f, size / 2f - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap).also { hsBadgeDrawableCache.put(size, it) }
}

// Compose le badge au centre d'un marqueur existant
private fun createHsMarkerIcon(context: Context, baseIcon: BitmapDrawable): BitmapDrawable {
    val cacheKey = "${System.identityHashCode(baseIcon)}_${baseIcon.intrinsicWidth}x${baseIcon.intrinsicHeight}"
    hsMarkerIconCache.get(cacheKey)?.let { return it }

    val badgeIcon = createHsBadge(context)
    val combinedBitmap = android.graphics.Bitmap.createBitmap(baseIcon.intrinsicWidth, baseIcon.intrinsicHeight, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(combinedBitmap)

    baseIcon.setBounds(0, 0, canvas.width, canvas.height)
    baseIcon.draw(canvas)

    val offsetX = (canvas.width - badgeIcon.intrinsicWidth) / 2
    val offsetY = (canvas.height - badgeIcon.intrinsicHeight) / 2
    badgeIcon.setBounds(offsetX, offsetY, offsetX + badgeIcon.intrinsicWidth, offsetY + badgeIcon.intrinsicHeight)
    badgeIcon.draw(canvas)

    return android.graphics.drawable.BitmapDrawable(context.resources, combinedBitmap).also { hsMarkerIconCache.put(cacheKey, it) }
}
```

> ⚠️ `createHsBadge()` est dupliqué à l'identique dans `app/src/main/java/fr/geotower/ui/components/SharedMiniMapCard.kt:1206` (pour les mini-cartes de partage).

---

## 9. Les points de couverture SignalQuest

`app/src/main/java/fr/geotower/ui/screens/map/MapScreen.kt:4519`. Overlay qui pose un petit disque coloré par point mesuré, du vert (bon signal) au rouge (mauvais), selon le RSRP.

```kotlin
private class SignalQuestCoverageOverlay(context: Context) : org.osmdroid.views.overlay.Overlay() {
    private val density = context.resources.displayMetrics.density
    private val point = android.graphics.Point()
    private val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.FILL }
    private var points: List<SignalQuestCoveragePoint> = emptyList()
    var onPointClick: ((SignalQuestCoveragePoint) -> Unit)? = null

    fun setPoints(nextPoints: List<SignalQuestCoveragePoint>) { points = nextPoints }

    override fun draw(canvas: android.graphics.Canvas, projection: org.osmdroid.views.Projection) {
        if (points.isEmpty()) return
        val radius = 3.6f * density
        points.forEach { coveragePoint ->
            projection.toPixels(GeoPoint(coveragePoint.latitude, coveragePoint.longitude), point)
            fillPaint.color = rsrpColor(coveragePoint.signalStrength)
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radius, fillPaint)
        }
    }
    // onSingleTapConfirmed(...) : sélectionne le point le plus proche dans un rayon de 18dp
}

/** Couleur d'un point de couverture selon le RSRP (dBm), du vert (bon) au rouge (mauvais). */
private fun rsrpColor(signalStrength: Float?): Int {
    val rsrp = signalStrength ?: return android.graphics.Color.GRAY
    return when {
        rsrp >= -80f  -> android.graphics.Color.parseColor("#1B7F2E")  // vert foncé (excellent)
        rsrp >= -95f  -> android.graphics.Color.parseColor("#66BB6A")  // vert clair (bon)
        rsrp >= -105f -> android.graphics.Color.parseColor("#FDD835")  // jaune (correct)
        rsrp >= -115f -> android.graphics.Color.parseColor("#FB8C00")  // orange (faible)
        else          -> android.graphics.Color.parseColor("#E53935")  // rouge (mauvais)
    }
}
```

---

## 10. Récapitulatif — caches & réglages visuels

### Caches d'icônes

| Cache | Taille | Contenu | Déclaré dans |
|-------|--------|---------|--------------|
| `markerIconCache` | 500 | icônes d'antennes (`createAdaptiveMarker`) | `MapUtils.kt:52` |
| `clusterIconCache` | 200 | icônes de clusters | `MapUtils.kt:53` |
| `radioIconCache` | 300 | icônes radio | `MapUtils.kt:54` |
| `hsBadgeDrawableCache` | — | badges « ! » | `MapScreen.kt` |
| `hsMarkerIconCache` | — | marqueurs HS composités | `MapScreen.kt` |

### Où régler quoi

| Réglage visuel | Emplacement |
|----------------|-------------|
| Couleur d'un opérateur | `OperatorColors.kt` → `all` (champ `colorHex`/`colorArgb`) |
| Couleur opérateur inactif | `MapUtils.INACTIVE_OPERATOR_COLOR` (`MapUtils.kt:16`) |
| Taille de l'icône antenne | `MapUtils.kt:116` → `targetSize = 105 * density` |
| Taille de l'icône cluster | `MapUtils.kt:412` → `45 * density` |
| Rayon du camembert / pictogramme | `pieRadius = 45f` (repère « 230 ») |
| Épaisseur des traits d'azimut | `AntennaMarker.init` → `strokeWidth = 3.5f * density` |
| Opacité du cône | `AntennaMarker.init` → `setAlphaComponent(mainColor, 50)` |
| Ouverture du cône | `AntennaMarker.draw` → `drawArc(..., 70f, ...)` + `±35f` |
| Longueur des faisceaux par zoom | `AntennaMarker.draw` → `beamLengthPx` (et `RadioMarker.draw`) |
| Rayon cliquable | `AntennaMarker.hitTest` → `22f * density` (radio : `18f`) |
| Couleurs radio (TV/Radio/FH…) | `MapUtils.radioMarkerColor` / `radioMarkerColors` |
| Seuils/couleurs de couverture | `rsrpColor()` (`MapScreen.kt`) |
| Zoom d'apparition des faisceaux | `AntennaMarker.draw` → `zoom >= 14.0` |

### Préférences runtime (`AppConfig`)

- `AppConfig.showAzimuths` — afficher les traits d'azimut.
- `AppConfig.showAzimuthsCone` — afficher les cônes de couverture.
- `AppConfig.showTechnoFH` — afficher les faisceaux hertziens (FH) en pointillés.
- `AppConfig.defaultOperator` — opérateur prioritaire (couleur de tête, ordre des secteurs).
