package fr.geotower.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.utils.AppConfig
import kotlin.math.roundToInt

@Composable
fun MapSpeedometer(speedKmH: Int, modifier: Modifier = Modifier) {
    // 1. On lit le choix de l'utilisateur (0 = km/h, 1 = mph)
    val isMph = AppConfig.speedUnit.intValue == 1

    // 2. On convertit la vitesse si nécessaire (1 km/h ≈ 0.621371 mph)
    val displaySpeed = if (isMph) {
        (speedKmH * 0.621371).roundToInt()
    } else {
        speedKmH
    }

    // 3. On choisit le bon label
    val unitLabel = if (isMph) "mph" else "km/h"

    Surface(
        modifier = modifier.padding(bottom = 4.dp, start = 6.dp),
        color = Color.White.copy(alpha = 0.8f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = "$displaySpeed $unitLabel",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}