package fr.geotower.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.MapUtils
import kotlin.math.cos
import kotlin.math.pow

@Composable
fun MapScaleAndAttribution(
    zoom: Double,
    latitude: Double,
    effectiveProvider: Int,
    ignStyle: Int,
    showScale: Boolean = true,
    showAttribution: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!showScale && !showAttribution) return
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(sizing.spacing(4.dp))
    ) {
        if (showScale) {
            GeoTowerMapScaleBar(zoom = zoom, latitude = latitude)
        }

        if (showAttribution) {
            val attributionText = MapUtils.MapAttribution.text(effectiveProvider, ignStyle)
            val attributionUrl = MapUtils.MapAttribution.url(effectiveProvider, ignStyle)

            Surface(
                color = Color.White.copy(alpha = 0.8f),
                shape = RoundedCornerShape(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clickable { uriHandler.openUri(attributionUrl) }
                        .padding(horizontal = sizing.spacing(6.dp), vertical = sizing.spacing(4.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (effectiveProvider == 0) {
                        Row(
                            modifier = Modifier.size(
                                width = sizing.component(14.dp),
                                height = sizing.component(10.dp)
                            )
                        ) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF002395)))
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color.White))
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFED2939)))
                        }
                        Spacer(modifier = Modifier.width(sizing.spacing(6.dp)))
                    }
                    Text(
                        text = attributionText,
                        fontSize = sizing.text(11.sp),
                        color = Color(0xFF0078A8)
                    )
                }
            }
        }
    }
}

@Composable
fun GeoTowerMapScaleBar(
    zoom: Double,
    latitude: Double
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val density = LocalDensity.current
    val maxBarWidthDp = sizing.component(100.dp)
    val maxBarWidthPx = with(density) { maxBarWidthDp.toPx() }
    val metersPerPx = 156543.03392 * cos(latitude * Math.PI / 180.0) / 2.0.pow(zoom)

    val roundDistances = listOf(
        1, 2, 5, 10, 20, 50, 100, 200, 500,
        1000, 2000, 5000, 10000, 20000, 50000, 100000, 200000, 300000, 500000, 1000000
    )
    val chosenDistance = roundDistances.lastOrNull { (it / metersPerPx) <= maxBarWidthPx } ?: 1
    val actualBarWidthDp = with(density) { (chosenDistance / metersPerPx).toFloat().toDp() }
    val label = if (chosenDistance >= 1000) "${chosenDistance / 1000} km" else "$chosenDistance m"

    Surface(color = Color.White.copy(alpha = 0.8f), shape = RoundedCornerShape(2.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = sizing.spacing(6.dp), vertical = sizing.spacing(2.dp))
        ) {
            Text(label, fontSize = sizing.text(10.sp), fontWeight = FontWeight.Bold, color = Color.Black)
            Canvas(modifier = Modifier.width(actualBarWidthDp).height(sizing.component(6.dp))) {
                drawLine(Color.Black, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2.dp.toPx())
                drawLine(Color.Black, Offset(0f, size.height), Offset(0f, 0f), strokeWidth = 2.dp.toPx())
                drawLine(Color.Black, Offset(size.width, size.height), Offset(size.width, 0f), strokeWidth = 2.dp.toPx())
            }
        }
    }
}
