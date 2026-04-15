package fr.geotower.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.utils.AppStrings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 1. Structure pour gérer l'état de chaque case de la grille
data class ServiceStatus(
    val isVoixOk: Boolean? = null,
    val isSmsOk: Boolean? = null,
    val isInternetOk: Boolean? = null
)

@Composable
fun SiteStatusCard(
    isOutage: Boolean,
    outageText: String?,
    cardBgColor: Color,
    blockShape: Shape,
    techStatus: Map<String, ServiceStatus> = emptyMap() // Pour le futur, si vous avez les vraies données
) {
    // Couleurs
    val colorOk = Color(0xFF4CAF50) // Vert
    val colorKo = Color(0xFFE53935) // Rouge
    val colorNeutral = Color.Gray.copy(alpha = 0.5f) // Gris

    Card(
        modifier = Modifier.fillMaxWidth(), // Pas de double marge !
        shape = blockShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // --- SECTION 1 : EN-TÊTE ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = AppStrings.statusTitle, // "Statut du Site"
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                val statusIcon = if (isOutage) Icons.Default.Warning else Icons.Default.CheckCircle
                val statusColor = if (isOutage) colorKo else colorOk

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isOutage) AppStrings.statusOutage else AppStrings.statusFunctional,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = statusColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Affichage du détail de la panne s'il existe
            if (isOutage && !outageText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = outageText,
                    fontSize = 13.sp,
                    color = colorKo,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // --- SECTION 2 : GRILLE DES SERVICES ---
            val technologies = listOf("2G", "3G", "4G", "5G")

            // En-têtes (2G, 3G, 4G, 5G)
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1.5f)) // Espace vide à gauche
                technologies.forEach { tech ->
                    Text(
                        text = tech,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Lignes de services
            ServiceRow(AppStrings.serviceVoice, technologies, techStatus) { _, it -> it.isVoixOk }
            Spacer(modifier = Modifier.height(10.dp))
            ServiceRow(AppStrings.serviceSms, technologies, techStatus) { _, it -> it.isSmsOk }
            Spacer(modifier = Modifier.height(10.dp))
            ServiceRow(AppStrings.serviceInternet, technologies, techStatus) { tech, it ->
                if (tech == "2G") null else it.isInternetOk // 🚨 ON FORCE LE "NULL" POUR LA 2G
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // --- SECTION 3 : PIED DE PAGE ---
            val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            Text(
                text = "${AppStrings.lastUpdatedText} $currentTime",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        }
    }
}
// 3. Composant réutilisable pour dessiner une ligne de la grille (Voix, SMS, etc.)
@Composable
private fun ServiceRow(
    serviceName: String,
    technologies: List<String>,
    techStatus: Map<String, ServiceStatus>,
    statusSelector: (String, ServiceStatus) -> Boolean? // 🚨 AJOUT DU STRING
) {
    val colorOk = Color(0xFF4CAF50)
    val colorKo = Color(0xFFE53935)
    val colorNeutral = Color.Gray.copy(alpha = 0.3f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Nom du service (Voix, SMS, Internet)
        Text(
            text = serviceName,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.5f)
        )

        // Icônes pour chaque technologie
        technologies.forEach { tech ->
            val status = techStatus[tech]?.let { statusSelector(tech, it) } // 🚨 ON TRANSMET LA TECHNO
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when (status) {
                    true -> Icon(Icons.Default.Check, null, tint = colorOk, modifier = Modifier.size(20.dp))
                    false -> Icon(Icons.Default.Close, null, tint = colorKo, modifier = Modifier.size(20.dp))
                    null -> Icon(Icons.Default.Remove, null, tint = colorNeutral, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}