package fr.geotower.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import fr.geotower.utils.AppConfig
import fr.geotower.utils.DeviceProfile

@Immutable
data class GeoTowerUiStyle(
    val useOneUi: Boolean,
    val isSamsungDevice: Boolean,
    val isDark: Boolean,
    val isOled: Boolean,
    val backgroundColor: Color,
    val bubbleColor: Color,
    val cardColor: Color,
    val secondaryCardColor: Color,
    val cardShape: Shape,
    val blockShape: Shape,
    val actionButtonShape: Shape,
    val smallItemShape: Shape,
    val cardBorder: BorderStroke?,
    val subtleBorder: BorderStroke?
)

val LocalGeoTowerUiStyle = staticCompositionLocalOf<GeoTowerUiStyle> {
    error("LocalGeoTowerUiStyle is not provided")
}

@Composable
fun rememberGeoTowerUiStyle(): GeoTowerUiStyle {
    val themeMode by AppConfig.themeMode
    val isOled by AppConfig.isOledMode
    val uiMode by AppConfig.uiMode
    val isDark = themeMode == 2 || (themeMode == 0 && isSystemInDarkTheme())
    val useOneUi = uiMode.usesOneUi()
    val backgroundColor = if (isDark && isOled) Color.Black else MaterialTheme.colorScheme.background
    val bubbleColor = if (useOneUi) {
        if (isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    } else {
        Color.Transparent
    }
    val cardColor = if (useOneUi) {
        bubbleColor
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val secondaryCardColor = if (useOneUi) {
        if (isDark) Color(0xFF2B2B2B) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val cardBorder = if (useOneUi) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    val subtleBorder = if (useOneUi) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))

    return GeoTowerUiStyle(
        useOneUi = useOneUi,
        isSamsungDevice = DeviceProfile.isSamsungDevice,
        isDark = isDark,
        isOled = isOled,
        backgroundColor = backgroundColor,
        bubbleColor = bubbleColor,
        cardColor = cardColor,
        secondaryCardColor = secondaryCardColor,
        cardShape = if (useOneUi) RoundedCornerShape(28.dp) else RoundedCornerShape(12.dp),
        blockShape = if (useOneUi) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp),
        actionButtonShape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp),
        smallItemShape = if (useOneUi) RoundedCornerShape(16.dp) else RoundedCornerShape(12.dp),
        cardBorder = cardBorder,
        subtleBorder = subtleBorder
    )
}

@Composable
fun GeoTowerUiStyleProvider(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalGeoTowerUiStyle provides rememberGeoTowerUiStyle(),
        content = content
    )
}
