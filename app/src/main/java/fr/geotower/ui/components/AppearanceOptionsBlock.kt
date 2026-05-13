package fr.geotower.ui.components

import android.widget.ImageView // <-- AJOUT POUR L'ICÔNE
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView // <-- AJOUT POUR L'ICÔNE
import fr.geotower.utils.AppStrings
import android.os.Build

// Import des cartes depuis SettingsScreen
import fr.geotower.ui.screens.settings.PreferenceSwitchCard
import fr.geotower.ui.screens.settings.SettingsOptionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceOptionsBlock(
    themeMode: Int, onThemeChange: (Int) -> Unit,
    isOled: Boolean, onOledChange: (Boolean) -> Unit,
    useOneUi: Boolean, onOneUiChange: (Boolean) -> Unit,
    isBlur: Boolean, onBlurChange: (Boolean) -> Unit,
    menuSize: String, onMenuSizeChange: (String) -> Unit,
    appIconRes: Int? = null, onAppIconClick: (() -> Unit)? = null,
    onColorPaletteClick: (() -> Unit)? = null,
    shape: Shape, border: BorderStroke?, bubbleColor: Color, safeClick: SafeClick
) {
    // --- NOUVEAU : On cache le mode "Système" sur Android < 10 ---
    val showSystemTheme = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    // 1. Thème
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showSystemTheme) {
            SettingsOptionCard(AppStrings.system, Icons.Default.SettingsSuggest, themeMode == 0, { safeClick("appearance_theme_system") { onThemeChange(0) } }, Modifier.weight(1f), shape, border, bubbleColor, useOneUi)
        }
        SettingsOptionCard(AppStrings.themeLight, Icons.Default.LightMode, themeMode == 1 || (!showSystemTheme && themeMode == 0), { safeClick("appearance_theme_light") { onThemeChange(1) } }, Modifier.weight(1f), shape, border, bubbleColor, useOneUi)
        SettingsOptionCard(AppStrings.themeDark, Icons.Default.DarkMode, themeMode == 2, { safeClick("appearance_theme_dark") { onThemeChange(2) } }, Modifier.weight(1f), shape, border, bubbleColor, useOneUi)
    }
    Spacer(modifier = Modifier.height(12.dp))

    if (onColorPaletteClick != null) {
        ColorPaletteActionCard(
            shape = shape,
            border = border,
            bubbleColor = bubbleColor,
            useOneUi = useOneUi,
            onClick = { safeClick("appearance_color_palette") { onColorPaletteClick() } }
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    // 2. Mode OLED
    AnimatedVisibility(visible = themeMode == 2 || (themeMode == 0 && isSystemInDarkTheme())) {
        Column {
            PreferenceSwitchCard(AppStrings.oledTitle, AppStrings.oledSubtitle, isOled, onOledChange, shape, border, bubbleColor, useOneUi)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    // 3. Mode One UI
    PreferenceSwitchCard(AppStrings.oneUiInterface, AppStrings.oneUiSubtitle, useOneUi, onOneUiChange, shape, border, bubbleColor, useOneUi)
    Spacer(modifier = Modifier.height(12.dp))

    // 4. Flou de navigation
    PreferenceSwitchCard(AppStrings.blurTitle, AppStrings.blurSubtitle, isBlur, onBlurChange, shape, border, bubbleColor, useOneUi)
    Spacer(modifier = Modifier.height(12.dp))

    // 5. Icône de l'application
    if (onAppIconClick != null && appIconRes != null) {
        Surface(onClick = { safeClick("appearance_app_icon") { onAppIconClick() } }, shape = shape, border = border, color = if (useOneUi) bubbleColor else Color.Transparent, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {

                // Le texte prend tout l'espace, plus d'icône ni de sous-titre !
                Text(
                    text = AppStrings.appIcon,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // --- Icône mipmap actuelle ---
                AndroidView(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setImageResource(appIconRes)
                        }
                    },
                    update = { it.setImageResource(appIconRes) }
                )

                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    // 6. Curseur de taille du menu
    MenuSizeSelector(menuSize, onMenuSizeChange, shape, border, bubbleColor, useOneUi)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuSizeSelector(currentSize: String, onMenuSizeChange: (String) -> Unit, shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean) {
    var sliderPosition by remember(currentSize) { mutableFloatStateOf(when (currentSize) { "petit" -> 0f; "large" -> 2f; else -> 1f }) }
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent

    Surface(shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(AppStrings.menuSizeTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            if (useOneUi) {
                Slider(
                    value = sliderPosition, onValueChange = { sliderPosition = it },
                    onValueChangeFinished = { onMenuSizeChange(when (sliderPosition.toInt()) { 0 -> "petit"; 2 -> "large"; else -> "normal" }) },
                    valueRange = 0f..2f, steps = 1,
                    thumb = { Box(modifier = Modifier.size(24.dp).background(MaterialTheme.colorScheme.surface, CircleShape).border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)) },
                    track = { _ ->
                        Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
                            drawLine(color = Color.Gray.copy(alpha = 0.3f), start = Offset(0f, size.height / 2), end = Offset(size.width, size.height / 2), strokeWidth = 14.dp.toPx(), cap = StrokeCap.Round)
                            for (i in 0..2) drawCircle(color = Color.Gray.copy(alpha = 0.6f), radius = 4.dp.toPx(), center = Offset(i * (size.width / 2), size.height / 2))
                        }
                    }
                )
            } else {
                Slider(
                    value = sliderPosition, onValueChange = { sliderPosition = it },
                    onValueChangeFinished = { onMenuSizeChange(when (sliderPosition.toInt()) { 0 -> "petit"; 2 -> "large"; else -> "normal" }) },
                    valueRange = 0f..2f, steps = 1
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(AppStrings.menuSizeSmall, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(AppStrings.menuSizeNormal, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(AppStrings.menuSizeLarge, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
