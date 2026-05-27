package fr.geotower.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.TechniqueEntity // ✅ NOUVEL IMPORT
import fr.geotower.utils.formatDateToFrench // ✅ Formatage localisé des dates
import androidx.compose.ui.res.stringResource
import fr.geotower.R

@Composable
fun SiteDatesBlock(
    info: LocalisationEntity,
    technique: TechniqueEntity?, // ✅ NOUVEAU PARAMÈTRE
    cardBgColor: Color,
    blockShape: Shape
) {
    val txtDates = stringResource(R.string.appstrings_dates)
    val txtImplementation = stringResource(R.string.appstrings_implementation)
    val txtActivatedOn = stringResource(R.string.appstrings_activated_on)
    val txtLastModification = stringResource(R.string.appstrings_last_modification) // ✅ NOUVELLE TRADUCTION

    Card(
        shape = blockShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = txtDates, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // ✅ ON RÉCUPÈRE ET ON FORMATE LES DATES
            val dateImp = technique?.dateImplantation?.takeIf { it.isNotBlank() }
            val dateSer = technique?.dateService?.takeIf { it.isNotBlank() }
            val dateMod = technique?.dateModif?.takeIf { it.isNotBlank() } // ✅ RÉCUPÉRATION DE LA MODIFICATION

            val dateImpStr = dateImp?.let { formatDateToFrench(it) } ?: stringResource(R.string.appstrings_not_specified)
            val dateSerStr = dateSer?.let { formatDateToFrench(it) } ?: stringResource(R.string.appstrings_not_specified)
            val dateModStr = dateMod?.let { formatDateToFrench(it) } ?: "-"

            // ✅ PLUS DE " : " EN TROP ICI NON PLUS !
            InfoLine(txtImplementation, dateImpStr)
            Spacer(modifier = Modifier.height(4.dp))
            InfoLine(txtActivatedOn, dateSerStr)

            // ✅ CONDITION : On affiche la dernière modification seulement si elle existe
            if (dateModStr != "-" && dateModStr != stringResource(R.string.appstrings_not_specified)) {
                Spacer(modifier = Modifier.height(4.dp))
                InfoLine(txtLastModification, dateModStr)
            }
        }
    }
}
