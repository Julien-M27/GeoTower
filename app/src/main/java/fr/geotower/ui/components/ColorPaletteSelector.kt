package fr.geotower.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.geotower.ui.theme.AppColorPalette
import fr.geotower.ui.theme.AppColorPaletteOptions
import fr.geotower.ui.theme.appPalettePreviewColors
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings

fun saveColorPalette(context: Context, palette: AppColorPalette) {
    AppConfig.colorPalette.value = palette.storageKey
    context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
        .edit()
        .putString(AppConfig.PREF_COLOR_PALETTE, palette.storageKey)
        .apply()
}

@Composable
fun ColorPaletteTopBar(onBack: () -> Unit) {
    GeoTowerBackTopBar(
        title = AppStrings.colorPaletteTitle,
        onBack = onBack,
        backgroundColor = MaterialTheme.colorScheme.background
    )
}

@Composable
fun ColorPaletteActionCard(
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedPaletteKey by AppConfig.colorPalette
    val selectedPalette = AppColorPalette.fromKey(selectedPaletteKey)
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent

    Surface(
        onClick = onClick,
        shape = shape,
        border = border,
        color = cardBg,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = AppStrings.colorPaletteTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = AppStrings.current(AppStrings.colorPaletteName(selectedPalette.storageKey)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorPaletteSwatches(
                    colors = previewColorsFor(selectedPalette),
                    dotSize = 18.dp,
                    spacing = 4.dp
                )
                Spacer(Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ColorPalettePickerContent(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    showHeader: Boolean = true,
    useOneUi: Boolean,
    bubbleColor: Color
) {
    val context = LocalContext.current
    val selectedPaletteKey by AppConfig.colorPalette
    val selectedPalette = AppColorPalette.fromKey(selectedPaletteKey)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding)
    ) {
        if (showHeader) {
            Text(
                text = AppStrings.colorSourceTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = AppStrings.colorSourceDesc,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppColorPaletteOptions.forEach { palette ->
                ColorPaletteOptionCard(
                    palette = palette,
                    selected = selectedPalette == palette,
                    useOneUi = useOneUi,
                    bubbleColor = bubbleColor,
                    onClick = { saveColorPalette(context, palette) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColorPaletteOptionCard(
    palette: AppColorPalette,
    selected: Boolean,
    useOneUi: Boolean,
    bubbleColor: Color,
    onClick: () -> Unit
) {
    val shape = if (useOneUi) RoundedCornerShape(28.dp) else RoundedCornerShape(12.dp)
    val selectedColor = MaterialTheme.colorScheme.primary
    val cardColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (useOneUi) 0.24f else 0.34f)
        useOneUi -> bubbleColor
        else -> Color.Transparent
    }
    val cardBorder = when {
        selected -> BorderStroke(2.dp, selectedColor)
        useOneUi -> null
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Surface(
        shape = shape,
        color = cardColor,
        border = cardBorder,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (selected) selectedColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(50.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (palette == AppColorPalette.Dynamic) Icons.Filled.AutoAwesome else Icons.Outlined.Palette,
                            contentDescription = null,
                            tint = if (selected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = AppStrings.colorPaletteName(palette.storageKey),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = AppStrings.colorPaletteDescription(palette.storageKey),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (selected) {
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = selectedColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            ColorPaletteSwatches(
                colors = previewColorsFor(palette),
                dotSize = 24.dp,
                spacing = 10.dp
            )

        }
    }
}

@Composable
private fun previewColorsFor(palette: AppColorPalette): List<Color> {
    return if (palette == AppColorPalette.Dynamic) {
        // L'aperçu dynamique utilise la palette réellement active pour mieux refléter le téléphone.
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.surfaceVariant
        )
    } else {
        appPalettePreviewColors(palette)
    }
}

@Composable
private fun ColorPaletteSwatches(
    colors: List<Color>,
    dotSize: androidx.compose.ui.unit.Dp,
    spacing: androidx.compose.ui.unit.Dp
) {
    Row(horizontalArrangement = Arrangement.spacedBy(spacing), verticalAlignment = Alignment.CenterVertically) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f), CircleShape)
            )
        }
    }
}
