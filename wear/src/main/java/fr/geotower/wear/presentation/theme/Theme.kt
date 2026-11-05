package fr.geotower.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

val WearColors = Colors(
    primary = WearPrimary,
    onPrimary = WearOnPrimary,
    secondary = WearSecondary,
    background = WearBackground,
    surface = WearSurface,
    onBackground = WearTextPrimary,
    onSurface = WearTextPrimary,
    onSurfaceVariant = WearTextSecondary
)

@Composable
fun GeoTowerWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = WearColors,
        content = content
    )
}
