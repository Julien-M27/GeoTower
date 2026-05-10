package fr.geotower.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.data.models.LocalisationEntity // ✅ NOUVEL IMPORT
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings
import java.util.Locale
import fr.geotower.data.models.TechniqueEntity
import kotlin.math.roundToInt

@Composable
fun SiteAddressBlock(
    info: LocalisationEntity,
    technique: TechniqueEntity?,
    distanceStr: String,
    cardBgColor: Color,
    blockShape: Shape
) {
    val context = LocalContext.current

    val txtAddressLabel = AppStrings.addressLabel
    val txtAddressCopy = AppStrings.addressCopy
    val txtAddressCopied = AppStrings.addressCopied
    val txtGpsLabel = AppStrings.gpsLabel
    val txtGpsCoordsCopy = AppStrings.gpsCoordsCopy
    val txtCoordsCopied = AppStrings.coordsCopied
    val txtDistanceLabel = AppStrings.distanceLabel
    val txtFromMyPosition = AppStrings.fromMyPosition

    Card(
        shape = blockShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = txtAddressLabel.replace(" : ", ""), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            val fullAddress = technique?.adresse?.takeIf { it.isNotBlank() } ?: AppStrings.notSpecified
            val gpsCoords = String.format(Locale.US, "%.5f, %.5f", info.latitude, info.longitude)

            InfoLine(
                label = txtAddressLabel,
                value = fullAddress,
                onCopy = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(txtAddressCopy, fullAddress))
                    Toast.makeText(context, txtAddressCopied, Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            InfoLine(
                label = txtGpsLabel,
                value = gpsCoords,
                onCopy = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(txtGpsCoordsCopy, gpsCoords))
                    Toast.makeText(context, txtCoordsCopied, Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            InfoLine(
                label = txtDistanceLabel,
                value = "${formatAddressDistanceForUnit(distanceStr)} $txtFromMyPosition"
            )
        }
    }
}

private fun formatAddressDistanceForUnit(distanceStr: String): String {
    if (AppConfig.distanceUnit.intValue != 1) return distanceStr
    val normalized = distanceStr.trim().replace(',', '.')
    val lower = normalized.lowercase(Locale.US)
    if (lower.contains("mi") || lower.contains("ft")) return distanceStr

    val value = Regex("""[0-9]+(?:\.[0-9]+)?""").find(normalized)?.value?.toDoubleOrNull()
        ?: return distanceStr
    val meters = when {
        lower.contains("km") -> value * 1000.0
        lower.endsWith("m") || lower.contains(" m") -> value
        else -> return distanceStr
    }
    val miles = meters / 1609.344
    return if (miles >= 0.1) String.format(Locale.US, "%.2f mi", miles) else "${(meters * 3.28084).roundToInt()} ft"
}
