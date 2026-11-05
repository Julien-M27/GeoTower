package fr.geotower.wear.presentation.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import fr.geotower.wear.data.model.GpsFixStatus
import fr.geotower.wear.data.model.WearMapTheme
import fr.geotower.wear.data.model.WearTower
import fr.geotower.wear.data.network.WearWebApiClient
import fr.geotower.wear.presentation.theme.getOperatorColor
import fr.geotower.wear.presentation.theme.getOperatorShortName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.tan

/**
 * Écran Carte avec fond de carte tuilé (Dark Map Tiles) et tracé GPS comme sur Garmin.
 */
@Composable
fun MapScreen(
    towers: List<WearTower>,
    selectedTower: WearTower?,
    gpsStatus: GpsFixStatus,
    mapTheme: WearMapTheme = WearMapTheme.DARK,
    onTowerSelected: (WearTower) -> Unit,
    onOpenDetails: (WearTower) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTower = selectedTower ?: towers.firstOrNull()

    // Échelle de portée de la carte en mètres : 250m, 500m, 1000m, 2000m, 5000m
    val zoomScales = listOf(250f, 500f, 1000f, 2000f, 5000f)
    val tileZooms = listOf(16, 15, 14, 13, 12)
    var zoomIndex by remember { mutableStateOf(2) } // 1000m (zoom 14) par défaut

    // Auto-zoom dynamique : si l'antenne est trop loin/en dehors du champ de vision, on dézoome ;
    // et inversement, lorsqu'on revient sur une antenne plus proche, on rezoome automatiquement
    LaunchedEffect(activeTower?.id) {
        if (activeTower != null) {
            val idealIndex = zoomScales.indexOfFirst { it * 0.85f >= activeTower.distanceMeters }
                .let { if (it >= 0) it else zoomScales.lastIndex }
            zoomIndex = idealIndex
        }
    }

    val maxRange = zoomScales[zoomIndex]
    val tileZoom = tileZooms[zoomIndex]

    // Mode d'affichage de la carte : Fond OSM (tuiles OpenStreetMap gratuites sans clé API) ou Radar tactique noir pur
    var isOsmMapEnabled by remember { mutableStateOf(true) }

    // Matrice de couleur sombre nocturne pour adapter les tuiles claires OpenStreetMap à l'écran OLED noir
    val nightFilter = remember {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -0.75f, 0f, 0f, 0f, 215f,
                    0f, -0.75f, 0f, 0f, 215f,
                    0f, 0f, -0.75f, 0f, 215f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    // Coordonnées utilisateur
    val userLat = (gpsStatus as? GpsFixStatus.Fixed)?.latitude ?: towers.firstOrNull()?.latitude ?: 48.005678
    val userLon = (gpsStatus as? GpsFixStatus.Fixed)?.longitude ?: towers.firstOrNull()?.longitude ?: 0.201598

    // Cache des tuiles de fond de carte
    val tileCache = remember { mutableStateMapOf<String, ImageBitmap>() }

    // Chargement asynchrone des tuiles OpenStreetMap gratuites (AUCUNE clé API nécessaire)
    LaunchedEffect(tileZoom, userLat, userLon, isOsmMapEnabled) {
        if (!isOsmMapEnabled) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val n = 1 shl tileZoom
            val centerTileX = ((userLon + 180.0) / 360.0 * n).toInt()
            val latRad = Math.toRadians(userLat)
            val centerTileY = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n).toInt()

            // Télécharge les tuiles 3x3 autour du centre
            for (dx in -1..1) {
                for (dy in -1..1) {
                    val tx = centerTileX + dx
                    val ty = centerTileY + dy
                    val key = "$tileZoom/$tx/$ty"
                    if (!tileCache.containsKey(key)) {
                        try {
                            // OpenStreetMap officiel : gratuit, public, sans aucune clé API requise
                            val url = "https://tile.openstreetmap.org/$tileZoom/$tx/$ty.png"
                            val req = Request.Builder()
                                .url(url)
                                .header("User-Agent", "GeoTower-WearOS/2.1 (contact@geotower.fr)")
                                .build()
                            val resp = WearWebApiClient.okHttpClient.newCall(req).execute()
                            if (resp.isSuccessful) {
                                val bytes = resp.body?.bytes()
                                if (bytes != null) {
                                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    if (bmp != null) {
                                        tileCache[key] = bmp.asImageBitmap()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // En cas d'absence de réseau, le fond géométrique Canvas radar prend le relais
                        }
                    }
                }
            }
        }
    }

    val bgMapColor = if (mapTheme == WearMapTheme.DARK) Color(0xFF0F1218) else Color(0xFFE5E5EA)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgMapColor),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val mapRadius = (size.minDimension / 2f) * 0.74f

            // 1. Dessin des tuiles OpenStreetMap avec filtre sombre si thème DARK
            val n = 1 shl tileZoom
            val exactX = ((userLon + 180.0) / 360.0 * n)
            val latRad = Math.toRadians(userLat)
            val exactY = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n)
            val centerTileX = exactX.toInt()
            val centerTileY = exactY.toInt()

            val tileSizePx = 256f
            val offsetXInTiles = (exactX - centerTileX) * tileSizePx
            val offsetYInTiles = (exactY - centerTileY) * tileSizePx

            var drawnAnyTile = false
            if (isOsmMapEnabled) {
                val activeTileFilter = if (mapTheme == WearMapTheme.DARK) nightFilter else null
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        val tx = centerTileX + dx
                        val ty = centerTileY + dy
                        val key = "$tileZoom/$tx/$ty"
                        val tile = tileCache[key]
                        if (tile != null) {
                            drawnAnyTile = true
                            val drawX = center.x + (dx * tileSizePx) - offsetXInTiles.toFloat()
                            val drawY = center.y + (dy * tileSizePx) - offsetYInTiles.toFloat()
                            drawImage(
                                image = tile,
                                dstOffset = IntOffset(drawX.toInt(), drawY.toInt()),
                                dstSize = IntSize(tileSizePx.toInt(), tileSizePx.toInt()),
                                colorFilter = activeTileFilter
                            )
                        }
                    }
                }
            }

            // Calque sombre semi-transparent uniquement en mode sombre pour faire ressortir les antennes
            if (drawnAnyTile && mapTheme == WearMapTheme.DARK) {
                drawCircle(
                    color = Color.Black.copy(alpha = 0.35f),
                    radius = size.minDimension / 2f,
                    center = center
                )
            }

            // 2. Cercles de distance Garmin concentriques adaptés à l'échelle Mercator
            val metersPerPixel = (40075016.686 * cos(latRad)) / (n * tileSizePx)
            val ringStepMeters = maxRange / 3f
            val ringColor = if (mapTheme == WearMapTheme.DARK) Color(0xFF2C3847).copy(alpha = 0.8f) else Color(0xFF555555).copy(alpha = 0.65f)
            val crossColor = if (mapTheme == WearMapTheme.DARK) Color(0xFF2C3847).copy(alpha = 0.6f) else Color(0xFF555555).copy(alpha = 0.45f)

            for (step in 1..3) {
                val ringRadius = (step * ringStepMeters / metersPerPixel).toFloat()
                drawCircle(
                    color = ringColor,
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // 3. Axes Nord-Sud et Est-Ouest
            val maxRadiusPx = (maxRange / metersPerPixel).toFloat().coerceAtMost(size.minDimension / 2f)
            drawLine(
                color = crossColor,
                start = Offset(center.x, center.y - maxRadiusPx),
                end = Offset(center.x, center.y + maxRadiusPx),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = crossColor,
                start = Offset(center.x - maxRadiusPx, center.y),
                end = Offset(center.x + maxRadiusPx, center.y),
                strokeWidth = 1.dp.toPx()
            )

            // 4. Tracé direct (Polyline) de l'utilisateur vers l'antenne sélectionnée
            val target = selectedTower ?: towers.firstOrNull()
            if (target != null) {
                val targetExactX = (target.longitude + 180.0) / 360.0 * n
                val targetLatRad = Math.toRadians(target.latitude.coerceIn(-85.0, 85.0))
                val targetExactY = (1.0 - ln(tan(targetLatRad) + 1.0 / cos(targetLatRad)) / Math.PI) / 2.0 * n
                val targetPointX = center.x + ((targetExactX - exactX) * tileSizePx).toFloat()
                val targetPointY = center.y + ((targetExactY - exactY) * tileSizePx).toFloat()
                val targetOpColor = getOperatorColor(target.operator)

                // Ligne de liaison Garmin vers le point exact
                drawLine(
                    color = targetOpColor.copy(alpha = 0.9f),
                    start = center,
                    end = Offset(targetPointX, targetPointY),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // 5. Marqueurs des antennes relais spatialisées (découpées par opérateur comme sur smartphone)
            towers.forEach { tower ->
                val towerExactX = (tower.longitude + 180.0) / 360.0 * n
                val towerLatRad = Math.toRadians(tower.latitude.coerceIn(-85.0, 85.0))
                val towerExactY = (1.0 - ln(tan(towerLatRad) + 1.0 / cos(towerLatRad)) / Math.PI) / 2.0 * n

                val pointX = center.x + ((towerExactX - exactX) * tileSizePx).toFloat()
                val pointY = center.y + ((towerExactY - exactY) * tileSizePx).toFloat()

                val isSelected = target?.id == tower.id
                val pinRadius = if (isSelected) 6.dp.toPx() else 4.5.dp.toPx()
                val pinRectSize = Size(pinRadius * 2f, pinRadius * 2f)
                val pinTopLeft = Offset(pointX - pinRadius, pointY - pinRadius)

                val ops = tower.operators.ifEmpty {
                    listOf(fr.geotower.wear.data.model.WearOperatorSite(operator = tower.operator))
                }

                if (ops.size <= 1) {
                    val opColor = getOperatorColor(ops.firstOrNull()?.operator ?: tower.operator)
                    drawCircle(
                        color = opColor,
                        radius = pinRadius,
                        center = Offset(pointX, pointY)
                    )
                } else {
                    val sweepAngle = 360f / ops.size
                    ops.forEachIndexed { i, op ->
                        drawArc(
                            color = getOperatorColor(op.operator),
                            startAngle = -90f + (i * sweepAngle),
                            sweepAngle = sweepAngle,
                            useCenter = true,
                            topLeft = pinTopLeft,
                            size = pinRectSize
                        )
                    }
                }

                // Contour fin noir contrasté
                drawCircle(
                    color = Color.Black.copy(alpha = 0.85f),
                    radius = pinRadius,
                    center = Offset(pointX, pointY),
                    style = Stroke(width = 1.dp.toPx())
                )

                // Si sélectionné : anneau de ciblage blanc distinctif
                if (isSelected) {
                    drawCircle(
                        color = Color.White,
                        radius = pinRadius + 3.dp.toPx(),
                        center = Offset(pointX, pointY),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            // 6. Marqueur Position Utilisateur (Moi)
            drawCircle(
                color = Color(0xFF007AFF),
                radius = 5.dp.toPx(),
                center = center
            )
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = center
            )
        }

        // Éléments d'interface superposés (Bandeaux style Garmin)
        val currentIndex = towers.indexOfFirst { it.id == activeTower?.id }.let { if (it >= 0) it else 0 }
        val hasPrev = currentIndex > 0
        val hasNext = currentIndex < towers.lastIndex

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Bandeau supérieur style Garmin (Description Mini map • Bascule OSM/RADAR • Échelle)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .padding(top = 34.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.88f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Mini map",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(Modifier.width(4.dp))

                    // Bascule directe 1 clic : OSM (fond tuiles gratuit sans clé) / RADAR (mode tactique noir Garmin)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isOsmMapEnabled) Color(0xFF007AFF) else Color(0xFF333333))
                            .clickable { isOsmMapEnabled = !isOsmMapEnabled }
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (isOsmMapEnabled) "OSM" else "RADAR",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    Text(
                        text = if (maxRange >= 1000f) "${(maxRange / 1000).toInt()} km" else "${maxRange.toInt()} m",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF30D158)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Bas de l'écran : Navigation entre sites et commandes de zoom
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Rangée 1 : Navigation de site en site (flèche gauche ◀, fiche détails cliquable, flèche droite ▶)
                if (activeTower != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth(0.86f)
                            .padding(bottom = 3.dp)
                    ) {
                        // Flèche précédent ◀
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (hasPrev) Color(0xFF2C2C2E).copy(alpha = 0.95f) else Color(0x661E1E20))
                                .clickable(enabled = hasPrev) {
                                    if (hasPrev) onTowerSelected(towers[currentIndex - 1])
                                }
                        ) {
                            Text(
                                text = "◀",
                                fontSize = 10.sp,
                                color = if (hasPrev) Color.White else Color(0xFF555555)
                            )
                        }

                        Spacer(Modifier.width(4.dp))

                        // Cartouche central cliquable : Fiche détails du site (comme sur Garmin)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.92f))
                                .clickable { onOpenDetails(activeTower) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val opBadge = activeTower.operators.firstOrNull()?.operator ?: activeTower.operator
                                Text(
                                    text = "$opBadge • ${activeTower.distanceMeters} m • ${activeTower.cardinalDirection}",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                val supDesc = if (activeTower.supportHeight != null && activeTower.supportHeight > 0) {
                                    "${activeTower.supportType} (${activeTower.supportHeight}m)"
                                } else {
                                    activeTower.supportType
                                }
                                Text(
                                    text = if (towers.isNotEmpty()) "${currentIndex + 1}/${towers.size} • $supDesc" else supDesc,
                                    fontSize = 7.sp,
                                    color = Color(0xFFFFB300),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Spacer(Modifier.width(4.dp))

                        // Flèche suivant ▶
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (hasNext) Color(0xFF2C2C2E).copy(alpha = 0.95f) else Color(0x661E1E20))
                                .clickable(enabled = hasNext) {
                                    if (hasNext) onTowerSelected(towers[currentIndex + 1])
                                }
                        ) {
                            Text(
                                text = "▶",
                                fontSize = 10.sp,
                                color = if (hasNext) Color.White else Color(0xFF555555)
                            )
                        }
                    }
                }

                // Rangée 2 : Boutons de zoom réhaussés et centrés (étroits, non mangés par les bords circulaires)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth(0.52f)
                        .padding(bottom = 14.dp)
                ) {
                    // Zoom Moins (−)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2C2C2E).copy(alpha = 0.95f))
                            .clickable(enabled = zoomIndex < zoomScales.lastIndex) {
                                if (zoomIndex < zoomScales.lastIndex) zoomIndex++
                            }
                    ) {
                        Text(
                            text = "−",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (zoomIndex < zoomScales.lastIndex) Color.White else Color(0xFF666666)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Text(
                        text = if (maxRange >= 1000f) "${(maxRange / 1000).toInt()} km" else "${maxRange.toInt()} m",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF30D158)
                    )

                    Spacer(Modifier.width(10.dp))

                    // Zoom Plus (+)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2C2C2E).copy(alpha = 0.95f))
                            .clickable(enabled = zoomIndex > 0) {
                                if (zoomIndex > 0) zoomIndex--
                            }
                    ) {
                        Text(
                            text = "+",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (zoomIndex > 0) Color.White else Color(0xFF666666)
                        )
                    }
                }
            }
        }
    }
}
