package fr.geotower.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import fr.geotower.utils.AppConfig
import kotlin.math.PI
import kotlin.math.sin

/** Glissement de la pastille : depart doux, arrivee amortie, sans rebond. */
private val SwitchGlideEasing = CubicBezierEasing(0.32f, 0f, 0.16f, 1f)
private const val SWITCH_GLIDE_MILLIS = 280

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
        GeoTowerMaterialSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            activeTrackColor = checkedColor
        )
    }
}

@Composable
private fun GeoTowerMaterialSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    // Une seule progression pilote tout (glissement, taille, couleurs, contour) : les
    // proprietes ne peuvent pas se desynchroniser en cours de route.
    // 1 = actif, rendu One UI (piste pleine, pastille blanche, sans contour).
    // 0 = eteint, rendu Material (piste vide, contour, petite pastille).
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = SWITCH_GLIDE_MILLIS, easing = SwitchGlideEasing),
        label = "materialSwitchProgress"
    )
    val enabledProgress by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
        label = "materialSwitchEnabled"
    )

    val trackColor = lerp(
        activeTrackColor.copy(alpha = 0f),
        lerp(activeTrackColor.copy(alpha = 0.38f), activeTrackColor, enabledProgress),
        progress
    )
    val borderColor = lerp(
        lerp(outlineColor.copy(alpha = 0.24f), outlineColor.copy(alpha = 0.7f), enabledProgress),
        Color.Transparent,
        progress
    )
    val thumbColor = lerp(
        lerp(onSurfaceColor.copy(alpha = 0.38f), outlineColor, enabledProgress),
        lerp(Color.White.copy(alpha = 0.7f), Color.White, enabledProgress),
        progress
    )

    val trackWidth = 52.dp
    val trackHeight = 32.dp
    val checkedThumbSize = 24.dp
    val uncheckedThumbSize = 16.dp
    val checkedPadding = (trackHeight - checkedThumbSize) / 2
    val uncheckedPadding = (trackHeight - uncheckedThumbSize) / 2

    val borderWidth = lerp(2.dp, 0.dp, progress)
    val thumbHeight = lerp(uncheckedThumbSize, checkedThumbSize, progress)
    val thumbElevation = lerp(0.dp, 1.dp, progress)
    // Etirement en pilule au milieu du trajet, nul aux deux extremites : c'est ce qui
    // donne la sensation de glissement plutot que de saut.
    val stretch = 3.dp * sin(progress * PI).toFloat()
    val thumbOffset = lerp(
        uncheckedPadding,
        trackWidth - checkedThumbSize - checkedPadding,
        progress
    ) - stretch / 2

    Box(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .clip(CircleShape)
            .background(trackColor)
            .border(borderWidth, borderColor, CircleShape),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(thumbHeight + stretch)
                .height(thumbHeight)
                .shadow(thumbElevation, CircleShape)
                .clip(CircleShape)
                .background(thumbColor)
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
