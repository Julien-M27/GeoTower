package fr.geotower.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Schedule
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
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.stringResource
import fr.geotower.R

// 1. Structure pour gérer l'état de chaque case de la grille
data class ServiceStatus(
    val isVoixOk: Boolean? = null,
    val isSmsOk: Boolean? = null,
    val isInternetOk: Boolean? = null,
    val isProject: Boolean = false // 🚨 AJOUT : Permet de cibler une techno spécifique "en projet"
)

@Composable
fun SiteStatusCard(
    isProjectSite: Boolean, // 🚨 AJOUT : Vrai si tout le site est un projet
    isOutage: Boolean,
    outageText: String?,
    cardBgColor: Color,
    blockShape: Shape,
    outageStartDate: String? = null,
    outageExpectedRestorationDate: String? = null,
    techStatus: Map<String, ServiceStatus> = emptyMap()
) {
    // Couleurs
    val colorOk = Color(0xFF4CAF50) // Vert
    val colorKo = Color(0xFFE53935) // Rouge
    val colorProject = Color(0xFFFFA000) // Jaune/Orange (Projet)
    val colorNeutral = Color.Gray.copy(alpha = 0.5f) // Gris

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    text = stringResource(R.string.appstrings_status_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // 🚨 NOUVEAU : Logique d'affichage de l'état global
                val statusIcon = when {
                    isProjectSite -> Icons.Default.Schedule
                    isOutage -> Icons.Default.Warning
                    else -> Icons.Default.CheckCircle
                }

                val statusColor = when {
                    isProjectSite -> colorProject
                    isOutage -> colorKo
                    else -> colorOk
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            isProjectSite -> stringResource(R.string.appstrings_status_project)
                            isOutage -> stringResource(R.string.appstrings_status_outage)
                            else -> stringResource(R.string.appstrings_status_functional)
                        },
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

            if (isOutage) {
                val formattedStartDate = formatOutageStatusDate(outageStartDate)
                val formattedRestorationDate = formatOutageStatusDate(outageExpectedRestorationDate)

                if (formattedStartDate != null || formattedRestorationDate != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        OutageDateLine(
                            label = stringResource(R.string.appstrings_outage_start_date),
                            value = formattedStartDate
                                ?: stringResource(R.string.appstrings_outage_date_unavailable),
                            color = colorKo
                        )
                        OutageDateLine(
                            label = stringResource(R.string.appstrings_outage_restore_forecast),
                            value = formattedRestorationDate
                                ?: stringResource(R.string.appstrings_outage_date_unavailable),
                            color = colorKo
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // --- SECTION 2 : GRILLE DES SERVICES ---
            val technologies = listOf("2G", "3G", "4G", "5G")

            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1.5f))
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

            ServiceRow(stringResource(R.string.appstrings_service_voice), technologies, techStatus) { _, it -> it.isVoixOk }
            Spacer(modifier = Modifier.height(10.dp))
            ServiceRow(stringResource(R.string.appstrings_service_sms), technologies, techStatus) { _, it -> it.isSmsOk }
            Spacer(modifier = Modifier.height(10.dp))
            ServiceRow(stringResource(R.string.appstrings_service_internet), technologies, techStatus) { tech, it ->
                if (tech == "2G") null else it.isInternetOk
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // --- SECTION 3 : PIED DE PAGE ---
            val currentTime = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date())
            Text(
                text = "${stringResource(R.string.appstrings_last_updated_text)} $currentTime",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun OutageDateLine(label: String, value: String, color: Color) {
    Text(
        text = "$label : $value",
        fontSize = 12.sp,
        color = color,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start
    )
}

private fun formatOutageStatusDate(rawDate: String?): String? {
    val cleanDate = rawDate
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != "-" && !it.equals("null", ignoreCase = true) }
        ?: return null

    val normalizedDate = cleanDate
        .replace('T', ' ')
        .substringBefore('+')
        .substringBefore('Z')
        .trim()

    val patterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd")
    patterns.forEach { pattern ->
        val candidate = normalizedDate.take(pattern.length)
        runCatching {
            val parsedDate = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
            }.parse(candidate)

            if (parsedDate != null) {
                val formatter = if (pattern.contains("HH")) {
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
                } else {
                    DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault())
                }
                return formatter.format(parsedDate)
            }
        }
    }

    return cleanDate
}

@Composable
private fun ServiceRow(
    serviceName: String,
    technologies: List<String>,
    techStatus: Map<String, ServiceStatus>,
    statusSelector: (String, ServiceStatus) -> Boolean?
) {
    val colorOk = Color(0xFF4CAF50)
    val colorKo = Color(0xFFE53935)
    val colorProject = Color(0xFFFFA000)
    val colorNeutral = Color.Gray.copy(alpha = 0.3f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = serviceName,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.5f)
        )

        technologies.forEach { tech ->
            val techItem = techStatus[tech]
            val status = techItem?.let { statusSelector(tech, it) }
            val isProj = techItem?.isProject == true // 🚨 NOUVEAU : On regarde si la techno est en projet

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // 🚨 NOUVEAU : Logique d'affichage de la case (Priorité au projet)
                when {
                    isProj && status != null -> Text("~", color = colorProject, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    status == true -> Icon(Icons.Default.Check, null, tint = colorOk, modifier = Modifier.size(20.dp))
                    status == false -> Icon(Icons.Default.Close, null, tint = colorKo, modifier = Modifier.size(20.dp))
                    else -> Icon(Icons.Default.Remove, null, tint = colorNeutral, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
