package fr.geotower.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.data.models.LocalisationEntity // ✅ NOUVEL IMPORT
import fr.geotower.utils.AppConfig
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import fr.geotower.R

@Composable
fun SitePanelHeightsBlock(
    info: LocalisationEntity, // ✅ NOUVEAU TYPE
    cardBgColor: Color,
    blockShape: Shape
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val txtPanelHeightsTitle = stringResource(R.string.appstrings_panel_heights_title)

    val distanceUnit = AppConfig.distanceUnit.intValue
    val locale = LocalConfiguration.current.locales[0]
    val formattedHeights = remember(info.azimuts, distanceUnit, locale) {
        if (info.azimuts.isNullOrBlank()) ""
        else {
            val heights = info.azimuts?.split(",")
                ?.mapNotNull { it.substringAfter("(", "").substringBefore("m", "").trim().toFloatOrNull() }
                ?.filter { it > 0f }?.distinct()?.sorted() ?: emptyList()
            if (heights.isNotEmpty()) {
                heights.joinToString(" - ") { formatPanelHeightForUnit(it.toDouble(), distanceUnit, locale) }
            } else ""
        }
    }

    if (formattedHeights.isNotEmpty()) {
        Card(shape = blockShape, colors = CardDefaults.cardColors(containerColor = cardBgColor), elevation = CardDefaults.cardElevation(0.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(sizing.spacing(16.dp)).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FormatLineSpacing, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                    Text(text = txtPanelHeightsTitle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = sizing.spacing(12.dp)), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(text = formattedHeights, style = sizing.textStyle(MaterialTheme.typography.bodyLarge), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/**
 * Partage avec la ligne « Emetteur a ... » des cartes operateur du support.
 *
 * `locale` vient de la configuration de la composition (donc de la langue FORCEE dans l'app, pas de
 * celle du telephone) : « 28,9 m » en francais, « 28.9 m » en anglais, comme l'ANFR l'ecrit.
 */
internal fun formatPanelHeightForUnit(heightMeters: Double, distanceUnit: Int, locale: Locale): String {
    return if (distanceUnit == 1) {
        "${(heightMeters * 3.28084).roundToInt()} ft"
    } else {
        if (heightMeters % 1.0 == 0.0) "${heightMeters.toInt()} m" else String.format(locale, "%.1f m", heightMeters)
    }
}
