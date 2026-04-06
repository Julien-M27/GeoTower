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
import fr.geotower.ui.screens.emitters.formatDateToFrench // ✅ IMPORT DE LA FONCTION DE FORMATAGE
import fr.geotower.utils.AppStrings

@Composable
fun SiteDatesBlock(
    info: LocalisationEntity,
    technique: TechniqueEntity?, // ✅ NOUVEAU PARAMÈTRE
    cardBgColor: Color,
    blockShape: Shape
) {
    val txtDates = AppStrings.dates
    val txtImplementation = AppStrings.implementation
    val txtActivatedOn = AppStrings.activatedOn
    val txtLastModification = AppStrings.lastModification // ✅ NOUVELLE TRADUCTION

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

            val dateImpStr = dateImp?.let { formatDateToFrench(it) } ?: "Non spécifiée"
            val dateSerStr = dateSer?.let { formatDateToFrench(it) } ?: "Non spécifiée"
            val dateModStr = dateMod?.let { formatDateToFrench(it) } ?: "-"

            // ✅ PLUS DE " : " EN TROP ICI NON PLUS !
            Text(text = "$txtImplementation$dateImpStr", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "$txtActivatedOn$dateSerStr", color = MaterialTheme.colorScheme.onSurfaceVariant)

            // ✅ CONDITION : On affiche la dernière modification seulement si elle existe
            if (dateModStr != "-" && dateModStr != "Non spécifiée") {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "$txtLastModification$dateModStr", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}