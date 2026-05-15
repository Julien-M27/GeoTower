package fr.geotower.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.geotower.utils.AppConfig

@Composable
fun GeoTowerSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    useOneUi: Boolean = AppConfig.useOneUiDesign,
    checkedColor: Color = MaterialTheme.colorScheme.primary
) {
    if (useOneUi) {
        GeoTowerOneUiSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            activeTrackColor = checkedColor
        )
    } else {
        val checkedTrackAlpha = if (isSystemInDarkTheme()) 0.32f else 0.18f
        val outlineColor = MaterialTheme.colorScheme.outline
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            thumbContent = {},
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = checkedColor,
                checkedTrackColor = checkedColor.copy(alpha = checkedTrackAlpha),
                checkedBorderColor = checkedColor.copy(alpha = 0.58f),
                checkedIconColor = Color.Transparent,
                uncheckedThumbColor = outlineColor,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = outlineColor.copy(alpha = 0.7f),
                uncheckedIconColor = Color.Transparent,
                disabledCheckedThumbColor = checkedColor.copy(alpha = 0.38f),
                disabledCheckedTrackColor = checkedColor.copy(alpha = checkedTrackAlpha * 0.6f),
                disabledCheckedBorderColor = checkedColor.copy(alpha = 0.24f),
                disabledUncheckedThumbColor = onSurfaceColor.copy(alpha = 0.38f),
                disabledUncheckedTrackColor = Color.Transparent,
                disabledUncheckedBorderColor = outlineColor.copy(alpha = 0.24f),
                disabledUncheckedIconColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun GeoTowerOneUiSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary
) {
    val isSystemDark = isSystemInDarkTheme()
    val inactiveTrackColor = if (isSystemDark) Color(0xFF3E3E40) else Color(0xFFE5E5EA)

    val trackColor by animateColorAsState(
        targetValue = if (checked) activeTrackColor else inactiveTrackColor,
        animationSpec = tween(durationMillis = 250, easing = LinearOutSlowInEasing),
        label = "trackColor"
    )

    val trackWidth = 52.dp
    val trackHeight = 32.dp
    val thumbSize = 24.dp
    val padding = (trackHeight - thumbSize) / 2
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - padding else padding,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "thumbOffset"
    )

    Box(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(CircleShape)
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .shadow(1.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
