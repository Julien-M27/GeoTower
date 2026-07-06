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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView // <-- AJOUT POUR L'ICÔNE
import fr.geotower.R
import android.os.Build
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.ui.theme.GEO_TOWER_UI_SCALE_PERCENT_MAX
import fr.geotower.ui.theme.GEO_TOWER_UI_SCALE_PERCENT_MIN
import kotlin.math.roundToInt

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
    uiScalePercent: Int, onUiScalePercentChange: (Int) -> Unit,
    appIconRes: Int? = null, onAppIconClick: (() -> Unit)? = null,
    appLogoDrawingChoice: String? = null,
    appLogoDrawingRes: Int? = null,
    onAppLogoDrawingClick: (() -> Unit)? = null,
    onColorPaletteClick: (() -> Unit)? = null,
    shape: Shape, border: BorderStroke?, bubbleColor: Color, safeClick: SafeClick
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    // --- NOUVEAU : On cache le mode "Système" sur Android < 10 ---
    val showSystemTheme = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    // 1. Thème
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))) {
        if (showSystemTheme) {
            SettingsOptionCard(stringResource(R.string.appearance_theme_system), Icons.Default.SettingsSuggest, themeMode == 0, { safeClick("appearance_theme_system") { onThemeChange(0) } }, Modifier.weight(1f), shape, border, bubbleColor, useOneUi)
        }
        SettingsOptionCard(stringResource(R.string.appearance_theme_light), Icons.Default.LightMode, themeMode == 1 || (!showSystemTheme && themeMode == 0), { safeClick("appearance_theme_light") { onThemeChange(1) } }, Modifier.weight(1f), shape, border, bubbleColor, useOneUi)
        SettingsOptionCard(stringResource(R.string.appearance_theme_dark), Icons.Default.DarkMode, themeMode == 2, { safeClick("appearance_theme_dark") { onThemeChange(2) } }, Modifier.weight(1f), shape, border, bubbleColor, useOneUi)
    }
    Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

    if (onColorPaletteClick != null) {
        ColorPaletteActionCard(
            shape = shape,
            border = border,
            bubbleColor = bubbleColor,
            useOneUi = useOneUi,
            onClick = { safeClick("appearance_color_palette") { onColorPaletteClick() } }
        )
        Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))
    }

    // 2. Mode OLED
    AnimatedVisibility(visible = themeMode == 2 || (themeMode == 0 && isSystemInDarkTheme())) {
        Column {
            PreferenceSwitchCard(stringResource(R.string.appearance_oled_title), stringResource(R.string.appearance_oled_desc), isOled, onOledChange, shape, border, bubbleColor, useOneUi)
            Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))
        }
    }

    // 3. Mode One UI
    PreferenceSwitchCard(stringResource(R.string.appearance_one_ui_title), stringResource(R.string.appearance_one_ui_desc), useOneUi, onOneUiChange, shape, border, bubbleColor, useOneUi)
    Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

    // 4. Flou de navigation
    PreferenceSwitchCard(stringResource(R.string.appearance_scroll_blur_title), stringResource(R.string.appearance_scroll_blur_desc), isBlur, onBlurChange, shape, border, bubbleColor, useOneUi)
    Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

    // 5. Icône de l'application
    if (onAppIconClick != null && appIconRes != null) {
        Surface(onClick = { safeClick("appearance_app_icon") { onAppIconClick() } }, shape = shape, border = border, color = if (useOneUi) bubbleColor else Color.Transparent, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(12.dp)), verticalAlignment = Alignment.CenterVertically) {

                // Le texte prend tout l'espace, plus d'icône ni de sous-titre !
                Text(
                    text = stringResource(R.string.appearance_app_icon_title),
                    style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // --- Icône mipmap actuelle ---
                Box(
                    modifier = Modifier.size(sizing.component(44.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        modifier = Modifier.size(sizing.component(36.dp)).clip(RoundedCornerShape(sizing.component(8.dp))),
                        factory = { ctx ->
                            ImageView(ctx).apply {
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                setImageResource(appIconRes)
                            }
                        },
                        update = { it.setImageResource(appIconRes) }
                    )
                }

                Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(sizing.component(24.dp)))
            }
        }
        Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))
    }

    if (onAppLogoDrawingClick != null && appLogoDrawingRes != null && appLogoDrawingChoice != null) {
        Surface(onClick = { safeClick("appearance_app_logo_drawing") { onAppLogoDrawingClick() } }, shape = shape, border = border, color = if (useOneUi) bubbleColor else Color.Transparent, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(12.dp)), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.appearance_in_app_logo_title),
                        style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(sizing.spacing(2.dp)))
                    Text(
                        text = appLogoDrawingChoiceName(appLogoDrawingChoice),
                        style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier.size(sizing.component(44.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        modifier = Modifier.size(sizing.component(34.dp)),
                        factory = { ctx ->
                            ImageView(ctx).apply {
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                setImageResource(appLogoDrawingRes)
                            }
                        },
                        update = { it.setImageResource(appLogoDrawingRes) }
                    )
                }

                Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(sizing.component(24.dp)))
            }
        }
        Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))
    }

    // 6. Curseur de taille de l'interface
    MenuSizeSelector(uiScalePercent, onUiScalePercentChange, shape, border, bubbleColor, useOneUi)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuSizeSelector(currentPercent: Int, onPercentChange: (Int) -> Unit, shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val minPercent = GEO_TOWER_UI_SCALE_PERCENT_MIN
    val maxPercent = GEO_TOWER_UI_SCALE_PERCENT_MAX
    var sliderValue by remember(currentPercent) { mutableFloatStateOf(currentPercent.toFloat()) }
    val displayPercent = sliderValue.roundToInt().coerceIn(minPercent, maxPercent)
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent

    Surface(shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(sizing.spacing(16.dp))) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.appearance_menu_size_title), style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                Text("$displayPercent %", style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))

            if (useOneUi) {
                Slider(
                    value = sliderValue, onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onPercentChange(sliderValue.roundToInt().coerceIn(minPercent, maxPercent)) },
                    valueRange = minPercent.toFloat()..maxPercent.toFloat(),
                    thumb = { Box(modifier = Modifier.size(sizing.component(24.dp)).background(MaterialTheme.colorScheme.surface, CircleShape).border(sizing.component(3.dp), MaterialTheme.colorScheme.primary, CircleShape)) },
                    track = { _ ->
                        Canvas(modifier = Modifier.fillMaxWidth().height(sizing.component(14.dp))) {
                            drawLine(color = Color.Gray.copy(alpha = 0.3f), start = Offset(0f, size.height / 2), end = Offset(size.width, size.height / 2), strokeWidth = sizing.component(14.dp).toPx(), cap = StrokeCap.Round)
                            // Repere du point neutre (100%), situe au centre de la plage 80-120.
                            drawCircle(color = Color.Gray.copy(alpha = 0.6f), radius = sizing.component(4.dp).toPx(), center = Offset(size.width / 2, size.height / 2))
                        }
                    }
                )
            } else {
                Slider(
                    value = sliderValue, onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onPercentChange(sliderValue.roundToInt().coerceIn(minPercent, maxPercent)) },
                    valueRange = minPercent.toFloat()..maxPercent.toFloat()
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.appearance_menu_size_small), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.appearance_menu_size_normal), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.appearance_menu_size_large), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
