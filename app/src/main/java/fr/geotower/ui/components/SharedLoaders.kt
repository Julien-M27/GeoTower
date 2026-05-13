package fr.geotower.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SequentialWavyLoader(
    modifier: Modifier = Modifier,
    color: Color
) {
    val progress = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(1000, easing = LinearEasing)
        )
        rotation.animateTo(
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing)
            )
        )
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val center = Offset(width / 2, height / 2)
        val radius = (width / 2) * 0.9f
        val waveAmplitude = 1.5.dp.toPx()
        val waveFrequency = 12

        val wavyPath = Path().apply {
            val steps = 360 * 2
            for (i in 0..steps) {
                val angleDeg = i.toDouble() / 2
                val angleRad = Math.toRadians(angleDeg - 90).toFloat()
                val waveAngle = Math.toRadians(angleDeg * waveFrequency)
                val currentRadius = radius + waveAmplitude * sin(waveAngle)
                val x = center.x + currentRadius.toFloat() * cos(angleRad)
                val y = center.y + currentRadius.toFloat() * sin(angleRad)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }

        rotate(degrees = rotation.value) {
            val pathMeasure = PathMeasure()
            pathMeasure.setPath(wavyPath, false)
            val segmentPath = Path()
            pathMeasure.getSegment(0f, pathMeasure.length * progress.value, segmentPath, true)
            drawPath(
                path = segmentPath,
                color = color,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}
