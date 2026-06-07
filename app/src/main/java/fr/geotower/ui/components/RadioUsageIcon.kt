package fr.geotower.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.geotower.data.models.RadioServiceMasks
import fr.geotower.data.models.RadioSystemMasks

enum class RadioUsageKind {
    Tv,
    Radio,
    PrivateMobile,
    Fh,
    Other
}

@Composable
fun RadioUsageIcon(
    serviceMask: Int,
    systemMask: Int,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    RadioUsageIcon(
        kind = primaryRadioUsageKindFor(serviceMask, systemMask),
        modifier = modifier,
        size = size
    )
}

@Composable
fun RadioUsageIcon(
    kind: RadioUsageKind,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val color = radioUsageColor(kind)

    Canvas(modifier = modifier.then(Modifier.size(size))) {
        val width = this.size.width
        val height = this.size.height
        val minSide = minOf(width, height)
        val strokeWidth = maxOf(1.6.dp.toPx(), minSide * 0.10f)
        val pad = strokeWidth / 2f + minSide * 0.08f
        val stroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        when (kind) {
            RadioUsageKind.Tv -> {
                val path = Path().apply {
                    moveTo(width / 2f, pad)
                    lineTo(width - pad, height - pad)
                    lineTo(pad, height - pad)
                    close()
                }
                drawPath(path, color.copy(alpha = 0.16f))
                drawPath(path, color, style = stroke)
            }
            RadioUsageKind.Radio -> {
                val path = Path().apply {
                    moveTo(pad, pad)
                    lineTo(width - pad, pad)
                    lineTo(width / 2f, height - pad)
                    close()
                }
                drawPath(path, color.copy(alpha = 0.16f))
                drawPath(path, color, style = stroke)
            }
            RadioUsageKind.PrivateMobile -> {
                val path = Path().apply {
                    moveTo(width / 2f, pad)
                    lineTo(width - pad, height / 2f)
                    lineTo(width / 2f, height - pad)
                    lineTo(pad, height / 2f)
                    close()
                }
                drawPath(path, color.copy(alpha = 0.14f))
                drawPath(path, color, style = stroke)
            }
            RadioUsageKind.Fh -> {
                val radius = minSide * 0.42f
                val center = Offset(width / 2f, height / 2f)
                drawCircle(color = color, radius = radius, center = center)
                drawLine(
                    color = Color.White.copy(alpha = 0.95f),
                    start = Offset(center.x - radius * 0.48f, center.y - radius * 0.48f),
                    end = Offset(center.x + radius * 0.48f, center.y + radius * 0.48f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
            RadioUsageKind.Other -> {
                val topLeft = Offset(pad, pad)
                val rectSize = Size(width - pad * 2f, height - pad * 2f)
                val path = Path().apply {
                    moveTo(pad, height - pad)
                    lineTo(pad, pad)
                    lineTo(width - pad, pad)
                    close()
                }
                drawPath(path, color)
                drawLine(
                    color = color,
                    start = Offset(pad, height - pad),
                    end = Offset(width - pad, pad),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawRect(
                    color = outlineColor,
                    topLeft = topLeft,
                    size = rectSize,
                    style = Stroke(width = strokeWidth * 0.72f)
                )
            }
        }
    }
}

fun primaryRadioUsageKindFor(serviceMask: Int, systemMask: Int): RadioUsageKind {
    return radioUsageKindsFor(serviceMask, systemMask).first()
}

fun radioUsageKindsFor(serviceMask: Int, systemMask: Int): List<RadioUsageKind> {
    val kinds = mutableListOf<RadioUsageKind>()
    if ((systemMask and RadioSystemMasks.TV) != 0) {
        kinds += RadioUsageKind.Tv
    }
    if ((systemMask and RadioSystemMasks.RADIO) != 0) {
        kinds += RadioUsageKind.Radio
    }
    if ((serviceMask and (RadioServiceMasks.PRIVATE or RadioServiceMasks.RAIL or RadioServiceMasks.TRANSPORT)) != 0) {
        kinds += RadioUsageKind.PrivateMobile
    }
    if ((serviceMask and RadioServiceMasks.FH) != 0) {
        kinds += RadioUsageKind.Fh
    }
    if ((serviceMask and (RadioServiceMasks.SATELLITE or RadioServiceMasks.RADAR or RadioServiceMasks.OTHER)) != 0 || kinds.isEmpty()) {
        kinds += RadioUsageKind.Other
    }
    return kinds.distinct()
}

fun radioUsageColor(kind: RadioUsageKind): Color {
    return when (kind) {
        RadioUsageKind.Tv -> Color(0xFF8BC34A)
        RadioUsageKind.Radio -> Color(0xFFFDD835)
        RadioUsageKind.PrivateMobile -> Color(0xFF006D77)
        RadioUsageKind.Fh -> Color(0xFF0D47A1)
        RadioUsageKind.Other -> Color(0xFF111111)
    }
}
