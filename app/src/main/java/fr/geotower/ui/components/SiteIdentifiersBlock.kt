package fr.geotower.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geotower.data.models.LocalisationEntity // ✅ NOUVEL IMPORT
import androidx.compose.ui.res.stringResource
import fr.geotower.R

@Composable
fun SiteIdentifiersBlock(
    info: LocalisationEntity,
    idSupport: String?, // ✅ NOUVEAU PARAMÈTRE
    cardBgColor: Color,
    blockShape: Shape
) {
    val context = LocalContext.current

    val txtIdentifiers = stringResource(R.string.appstrings_identifiers)
    val txtIdSupportLabel = stringResource(R.string.appstrings_id_support_label)
    val txtAnfrStationNumber = stringResource(R.string.appstrings_anfr_station_number)
    val txtNotSpecified = stringResource(R.string.appstrings_not_specified)
    val txtIdSupportCopy = stringResource(R.string.appstrings_id_support_copy)
    val txtIdCopied = stringResource(R.string.appstrings_id_copied)
    val txtIdUnavailable = stringResource(R.string.appstrings_id_unavailable)

    Card(shape = blockShape, colors = CardDefaults.cardColors(containerColor = cardBgColor), elevation = CardDefaults.cardElevation(0.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = txtIdentifiers, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            val idSupportValue = idSupport ?: txtNotSpecified // ✅ ON UTILISE LE VRAI ID SUPPORT
            InfoLine(
                label = txtIdSupportLabel,
                value = idSupportValue,
                onCopy = {
                    if (idSupportValue != txtNotSpecified) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(txtIdSupportCopy, idSupportValue))
                        Toast.makeText(context, txtIdCopied, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, txtIdUnavailable, Toast.LENGTH_SHORT).show()
                    }
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            val anfrLabelClean = txtAnfrStationNumber.replace(" : ", "")
            val idAnfrValue = info.idAnfr.takeIf { it.isNotBlank() } ?: txtNotSpecified // ✅ ON RÉCUPÈRE L'ID ANFR

            InfoLine(
                label = txtAnfrStationNumber,
                value = idAnfrValue, // ✅ ON UTILISE L'ID ANFR ICI
                onCopy = {
                    if (idAnfrValue != txtNotSpecified) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(anfrLabelClean, idAnfrValue))
                        Toast.makeText(context, txtIdCopied, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, txtIdUnavailable, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}