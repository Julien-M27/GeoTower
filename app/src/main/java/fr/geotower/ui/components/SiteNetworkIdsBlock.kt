package fr.geotower.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geotower.R
import fr.geotower.data.EnbRepository
import fr.geotower.ui.theme.LocalGeoTowerUiStyle

/**
 * Bloc « Identifiants reseau » de la fiche site : eNB (4G) et gNB (5G) rattaches a ce pylone pour
 * cet operateur, lus dans la base optionnelle `geotower_fr_enb.db`.
 *
 * N'emet **rien** si aucun identifiant n'est connu - base absente, operateur non couvert, ou site
 * sans rattachement (majoritaire chez Free, ou 3 % seulement des identifiants portent un support).
 * Le parent utilisant `Arrangement.spacedBy`, ne rien emettre ne laisse aucun trou.
 *
 * Une station peut porter plusieurs identifiants par technologie (jusqu'a 3 eNB + 1 gNB observes),
 * d'ou l'affichage en liste et le libelle au pluriel.
 */
@Composable
fun SiteNetworkIdsBlock(
    identifiers: EnbRepository.SiteNetworkIds,
    cardBgColor: Color,
    blockShape: Shape
) {
    if (identifiers.isEmpty) return

    val sizing = LocalGeoTowerUiStyle.current.sizing
    val context = LocalContext.current
    val txtTitle = stringResource(R.string.appstrings_site_network_ids_title)
    val txtLte = pluralStringResource(R.plurals.site_network_ids_4g, identifiers.lte.size)
    val txtNr = pluralStringResource(R.plurals.site_network_ids_5g, identifiers.nr.size)
    val txtNotSpecified = stringResource(R.string.appstrings_not_specified)
    val txtCopied = stringResource(R.string.appstrings_id_copied)

    Card(
        shape = blockShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(sizing.spacing(16.dp)).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CellTower, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                Text(text = txtTitle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = sizing.spacing(12.dp)),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                NetworkIdColumn(
                    modifier = Modifier.weight(1f),
                    label = txtLte,
                    prefix = LTE_PREFIX,
                    values = identifiers.lte,
                    emptyText = txtNotSpecified,
                    onCopied = { copyToClipboard(context, it, txtCopied) }
                )
                Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
                NetworkIdColumn(
                    modifier = Modifier.weight(1f),
                    label = txtNr,
                    prefix = NR_PREFIX,
                    values = identifiers.nr,
                    emptyText = txtNotSpecified,
                    onCopied = { copyToClipboard(context, it, txtCopied) }
                )
            }

            // Attribution du partenaire : cette donnee n'est pas de l'ANFR, elle merite d'etre
            // creditee la ou elle est lue, et pas seulement dans les reglages.
            Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))
            EnbSourceAttribution(
                dialogShape = blockShape,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun NetworkIdColumn(
    modifier: Modifier,
    label: String,
    prefix: String,
    values: List<Long>,
    emptyText: String,
    onCopied: (String) -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val clickable = values.isNotEmpty()

    Column(
        modifier = modifier.then(
            if (clickable) {
                Modifier.clickable { onCopied(values.joinToString(", ") { "$prefix $it" }) }
            } else {
                Modifier
            }
        )
    ) {
        Text(
            text = label,
            style = sizing.textStyle(MaterialTheme.typography.labelMedium),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(sizing.spacing(6.dp)))
        if (values.isEmpty()) {
            Text(
                text = emptyText,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            values.forEach { value ->
                Text(
                    text = "$prefix $value",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, value: String, confirmation: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(value, value))
    Toast.makeText(context, confirmation, Toast.LENGTH_SHORT).show()
}

// Sigles techniques 3GPP, volontairement hors traduction. Le nom et le site du partenaire vivent
// desormais dans EnbSourceAttribution, partages avec la carte de telechargement.
private const val LTE_PREFIX = "eNB"
private const val NR_PREFIX = "gNB"
