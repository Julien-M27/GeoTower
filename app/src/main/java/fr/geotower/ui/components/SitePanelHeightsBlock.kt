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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geotower.data.models.LocalisationEntity // ✅ NOUVEL IMPORT
import fr.geotower.utils.AppStrings

@Composable
fun SitePanelHeightsBlock(
    info: LocalisationEntity, // ✅ NOUVEAU TYPE
    cardBgColor: Color,
    blockShape: Shape
) {
    val txtPanelHeightsTitle = AppStrings.panelHeightsTitle

    val formattedHeights = remember(info.azimuts) {
        if (info.azimuts.isNullOrBlank()) ""
        else {
            val heights = info.azimuts?.split(",")
                ?.mapNotNull { it.substringAfter("(", "").substringBefore("m", "").trim().toFloatOrNull() }
                ?.filter { it > 0f }?.distinct()?.sorted() ?: emptyList()
            if (heights.isNotEmpty()) heights.joinToString(" m - ") + " m" else ""
        }
    }

    if (formattedHeights.isNotEmpty()) {
        Card(shape = blockShape, colors = CardDefaults.cardColors(containerColor = cardBgColor), elevation = CardDefaults.cardElevation(0.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FormatLineSpacing, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = txtPanelHeightsTitle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(text = formattedHeights, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}