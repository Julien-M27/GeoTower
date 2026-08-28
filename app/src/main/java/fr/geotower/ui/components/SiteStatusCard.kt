package fr.geotower.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.geotower.ui.theme.LocalGeoTowerUiSizing
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.ui.res.stringResource
import fr.geotower.R
import fr.geotower.data.models.SiteHsEntity
import fr.geotower.data.outages.OutageStatusCodes
import fr.geotower.ui.theme.LocalGeoTowerUiStyle

/** Couleur d'un « ? » quand l'état précis de la génération n'est pas publié. */
enum class UncertainServiceColor {
    Green,
    Red
}

// 1. Structure pour gérer l'état de chaque case de la grille
data class ServiceStatus(
    val isVoixOk: Boolean? = null,
    val isInternetOk: Boolean? = null,
    val isProject: Boolean = false,
    val isVoixProject: Boolean = false,
    val isInternetProject: Boolean = false,
    /** Couleur du « ? » quand la voix n'est pas publiée pour cette génération. */
    val voixUncertainColor: UncertainServiceColor? = null,
    /** Couleur du « ? » quand la data n'est pas publiée pour cette génération. */
    val internetUncertainColor: UncertainServiceColor? = null
) {
    val isVoixUncertain: Boolean get() = voixUncertainColor != null
    val isInternetUncertain: Boolean get() = internetUncertainColor != null
    val isVoixUncertainGreen: Boolean get() = voixUncertainColor == UncertainServiceColor.Green
    val isInternetUncertainGreen: Boolean get() = internetUncertainColor == UncertainServiceColor.Green
}

fun serviceAvailabilityFromOutageCode(
    hasTechnology: Boolean,
    outageCode: String?,
    isOutage: Boolean
): Boolean? {
    if (!hasTechnology) return null
    if (!isOutage) return true

    // Lecture partagée avec le décompte par génération des réglages (OutageTechBreakdown) : une case
    // rouge ici et une panne comptée là-bas doivent toujours désigner le même code.
    return when {
        OutageStatusCodes.isUp(outageCode) -> true
        OutageStatusCodes.isDown(outageCode) -> false
        else -> null
    }
}

/**
 * Vrai quand l'opérateur ne publie AUCUN code pour ce service à cette génération alors que la panne
 * qu'il déclare peut l'emporter : la case affiche alors « ? », en vert si l'état connu de la
 * génération est fonctionnel, en rouge si elle peut être touchée par la panne.
 *
 * Les relevés sont souvent partiels — une panne déclarée « Data : HS » sans détail par génération, ou
 * une data 4G HS avec la voix 4G laissée vide (aucun des quatre opérateurs ne publie de colonne voix
 * 5G, et Orange n'en publie pas pour la 4G). Le tiret « rien de publié » y était trompeur : il se lit
 * comme une absence de technologie alors que l'en-tête de la carte annonce déjà une panne.
 *
 * Une couleur rouge vient d'un code global du MÊME service ou du code de l'autre service sur la
 * génération ; une couleur verte vient d'un code `OK` connu sur la génération ou, à défaut, au
 * niveau global.
 *
 * Un code global à `OK` ne referme PAS ce doute : il n'agrège que les générations que l'opérateur
 * renseigne vraiment. Cas réel massif chez Orange — 2G et 3G décommissionnées donc déclarées `NE`,
 * pas de colonne voix 4G, d'où un « voix : OK » de façade sur un site dont la data 4G est HS, c'est-à-
 * dire dont la VoLTE (seule voix possible) est nécessairement tombée avec elle.
 *
 * Un état connu `OK` sur l'autre service de la même génération ouvre aussi un « ? » vert : la
 * génération fonctionne, mais l'état précis du service manquant n'est pas communiqué. Un code publié
 * pour la case, `NE` (non équipé) compris, ferme le doute : l'opérateur a répondu pour cette case.
 */
fun serviceUncertaintyColorFromOutageCode(
    hasTechnology: Boolean,
    outageCode: String?,
    serviceGlobalCode: String?,
    siblingOutageCode: String?,
    isOutage: Boolean,
    siblingGlobalCode: String? = null
): UncertainServiceColor? {
    if (!hasTechnology || !isOutage) return null
    // Un code publié se suffit : il vaut vert, rouge ou tiret (« NE »), jamais un doute.
    if (OutageStatusCodes.clean(outageCode) != null) return null

    // Le détail de l'autre service pour cette génération est prioritaire sur son code global : le
    // global peut agréger des générations différentes de celle affichée.
    val siblingStatusPublished = OutageStatusCodes.clean(siblingOutageCode) != null
    val hasDownStatus = OutageStatusCodes.isDown(serviceGlobalCode) ||
        OutageStatusCodes.isDown(siblingOutageCode) ||
        (!siblingStatusPublished && OutageStatusCodes.isDown(siblingGlobalCode))
    if (hasDownStatus) return UncertainServiceColor.Red

    val hasUpStatus = OutageStatusCodes.isUp(serviceGlobalCode) ||
        OutageStatusCodes.isUp(siblingOutageCode) ||
        (!siblingStatusPublished && OutageStatusCodes.isUp(siblingGlobalCode))
    return if (hasUpStatus) UncertainServiceColor.Green else null
}

fun serviceUncertaintyFromOutageCode(
    hasTechnology: Boolean,
    outageCode: String?,
    serviceGlobalCode: String?,
    siblingOutageCode: String?,
    isOutage: Boolean
): Boolean = serviceUncertaintyColorFromOutageCode(
    hasTechnology = hasTechnology,
    outageCode = outageCode,
    serviceGlobalCode = serviceGlobalCode,
    siblingOutageCode = siblingOutageCode,
    isOutage = isOutage
) != null

/**
 * Construit les quatre colonnes de la grille voix/data à partir du relevé de panne de l'opérateur
 * ([hsEntity], null quand aucune panne n'est déclarée).
 *
 * Partagée par la fiche site, le partage image et le rapport PDF : les trois recopiaient la même
 * lecture, et la case « incertaine » ([serviceUncertaintyFromOutageCode]) doit y apparaître à
 * l'identique. Les `has*` disent quelles générations sont physiquement sur l'antenne et les
 * `is*Project` lesquelles ne sont qu'un projet : cette part vient de la base ANFR
 * (`details_frequences`), donc de l'appelant, pas du relevé de panne.
 */
fun siteServiceStatusGrid(
    hsEntity: SiteHsEntity?,
    has2G: Boolean,
    has3G: Boolean,
    has4G: Boolean,
    has5G: Boolean,
    is2gProject: Boolean,
    is3gProject: Boolean,
    is4gProject: Boolean,
    is5gProject: Boolean
): Map<String, ServiceStatus> {
    val isOutage = hsEntity != null

    fun availability(hasTech: Boolean, outageCode: String?, globalCode: String?): Boolean? {
        if (!hasTech || !isOutage) return if (hasTech) true else null

        // Un HS global s'applique à toutes les générations renseignées pour ce service. Une case
        // absente reste indéterminée afin d'afficher « ? » rouge plutôt qu'un faux « HS » précis.
        if (OutageStatusCodes.isHs(globalCode) && OutageStatusCodes.clean(outageCode) != null) {
            return when {
                OutageStatusCodes.isUp(outageCode) || OutageStatusCodes.isDown(outageCode) -> false
                else -> null
            }
        }
        return serviceAvailabilityFromOutageCode(hasTech, outageCode, isOutage)
    }

    fun voiceUncertainColor(hasTech: Boolean, voiceCode: String?, dataCode: String?): UncertainServiceColor? =
        serviceUncertaintyColorFromOutageCode(
            hasTechnology = hasTech,
            outageCode = voiceCode,
            serviceGlobalCode = hsEntity?.voixGlobal,
            siblingOutageCode = dataCode,
            isOutage = isOutage,
            siblingGlobalCode = hsEntity?.dataGlobal
        )

    fun dataUncertainColor(hasTech: Boolean, dataCode: String?, voiceCode: String?): UncertainServiceColor? =
        serviceUncertaintyColorFromOutageCode(
            hasTechnology = hasTech,
            outageCode = dataCode,
            serviceGlobalCode = hsEntity?.dataGlobal,
            siblingOutageCode = voiceCode,
            isOutage = isOutage,
            siblingGlobalCode = hsEntity?.voixGlobal
        )

    // La voix sur 5G (VoNR) n'est pas déployée : un « HS » déclaré dessus est présenté comme un projet
    // plutôt que comme une panne, tout comme une data 5G HS sur un site sans 5G en base.
    val is5gVoiceProject = is5gProject || OutageStatusCodes.isDown(hsEntity?.voix5g)
    val is5gDataProject = is5gProject || (!has5G && OutageStatusCodes.isDown(hsEntity?.data5g))

    return mapOf(
        "2G" to ServiceStatus(
            isVoixOk = availability(has2G, hsEntity?.voix2g, hsEntity?.voixGlobal),
            // La 2G est suivie uniquement pour la voix dans cette grille : la Data est non applicable.
            isInternetOk = null,
            isProject = is2gProject,
            voixUncertainColor = voiceUncertainColor(has2G, hsEntity?.voix2g, null),
            internetUncertainColor = null
        ),
        "3G" to ServiceStatus(
            isVoixOk = availability(has3G, hsEntity?.voix3g, hsEntity?.voixGlobal),
            isInternetOk = availability(has3G, hsEntity?.data3g, hsEntity?.dataGlobal),
            isProject = is3gProject,
            voixUncertainColor = voiceUncertainColor(has3G, hsEntity?.voix3g, hsEntity?.data3g),
            internetUncertainColor = dataUncertainColor(has3G, hsEntity?.data3g, hsEntity?.voix3g)
        ),
        "4G" to ServiceStatus(
            isVoixOk = availability(has4G, hsEntity?.voix4g, hsEntity?.voixGlobal),
            isInternetOk = availability(has4G, hsEntity?.data4g, hsEntity?.dataGlobal),
            isProject = is4gProject,
            voixUncertainColor = voiceUncertainColor(has4G, hsEntity?.voix4g, hsEntity?.data4g),
            internetUncertainColor = dataUncertainColor(has4G, hsEntity?.data4g, hsEntity?.voix4g)
        ),
        "5G" to ServiceStatus(
            isVoixOk = availability(has5G, hsEntity?.voix5g, hsEntity?.voixGlobal),
            isInternetOk = availability(has5G, hsEntity?.data5g, hsEntity?.dataGlobal),
            isProject = is5gProject,
            isVoixProject = is5gVoiceProject,
            isInternetProject = is5gDataProject,
            voixUncertainColor = voiceUncertainColor(has5G, hsEntity?.voix5g, hsEntity?.data5g),
            internetUncertainColor = dataUncertainColor(has5G, hsEntity?.data5g, hsEntity?.voix5g)
        )
    )
}

@Composable
fun SiteStatusCard(
    isProjectSite: Boolean, // 🚨 AJOUT : Vrai si tout le site est un projet
    isOutage: Boolean,
    outageText: String?,
    cardBgColor: Color,
    blockShape: Shape,
    outageStartDate: String? = null,
    outageExpectedRestorationDate: String? = null,
    techStatus: Map<String, ServiceStatus> = emptyMap(),
    outageDetails: SiteHsEntity? = null,
    onAlertArcep: (() -> Unit)? = null,
    showVoiceRow: Boolean = true,
    showDataRow: Boolean = true
) {
    val sizing = LocalGeoTowerUiSizing.current
    // Couleurs
    val colorOk = Color(0xFF4CAF50) // Vert
    val colorKo = Color(0xFFE53935) // Rouge
    val colorProject = Color(0xFFFFA000) // Jaune/Orange (Projet)
    val colorNeutral = Color.Gray.copy(alpha = 0.5f) // Gris
    var showLegendDialog by remember { mutableStateOf(false) }
    val hasKnownServiceState = techStatus.values.any { it.isVoixOk != null || it.isInternetOk != null }
    val hasNonProjectOutage = techStatus.values.any {
        (it.isVoixOk == false && !it.isProject && !it.isVoixProject) ||
            (it.isInternetOk == false && !it.isProject && !it.isInternetProject)
    }
    // Un relevé qui ne détaille aucune génération (« Data : HS » seul) ne noircit aucune case : sans
    // ce test, la carte se dirait « fonctionnelle » là où la grille affiche des « ? ».
    val hasUncertainOutage = techStatus.values.any {
        (it.isVoixUncertain && !it.isProject && !it.isVoixProject) ||
            (it.isInternetUncertain && !it.isProject && !it.isInternetProject)
    }
    // Panne déduite (propagation zone blanche) : même rouge que le HS confirmé, libellé différent.
    val isPotentialOutage = outageDetails?.isPotential == true
    val displayIsOutage = isOutage &&
        (isPotentialOutage || !hasKnownServiceState || hasNonProjectOutage || hasUncertainOutage)

    if (showLegendDialog) {
        StatusLegendDialog(onDismiss = { showLegendDialog = false })
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = blockShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor)
    ) {
        Column(modifier = Modifier.padding(sizing.spacing(16.dp))) {
            // --- SECTION 1 : EN-TÊTE ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.appstrings_status_title),
                    style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // 🚨 NOUVEAU : Logique d'affichage de l'état global
                val statusIcon = when {
                    isProjectSite -> Icons.Default.Schedule
                    displayIsOutage -> Icons.Default.Warning
                    else -> Icons.Default.CheckCircle
                }

                val statusColor = when {
                    isProjectSite -> colorProject
                    displayIsOutage -> colorKo
                    else -> colorOk
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            isProjectSite -> stringResource(R.string.appstrings_status_project)
                            displayIsOutage && isPotentialOutage -> stringResource(R.string.appstrings_outage_status_potential)
                            displayIsOutage -> stringResource(R.string.appstrings_status_outage)
                            else -> stringResource(R.string.appstrings_status_functional)
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = sizing.text(14.sp),
                        color = statusColor
                    )
                    Spacer(modifier = Modifier.width(sizing.spacing(6.dp)))
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(sizing.component(20.dp))
                    )
                }
            }

            // Affichage du détail de la panne s'il existe.
            // Pour une panne déduite (zone blanche), on remplace le libellé répété par une phrase explicative.
            val outageBodyText = if (isPotentialOutage) {
                stringResource(R.string.appstrings_outage_potential_explanation)
            } else {
                outageText
            }
            if (displayIsOutage && !outageBodyText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
                Text(
                    text = outageBodyText,
                    fontSize = sizing.text(13.sp),
                    color = colorKo,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }

            if (displayIsOutage) {
                val formattedStartDate = formatOutageStatusDate(outageStartDate)
                val formattedRestorationDate = formatOutageStatusDate(outageExpectedRestorationDate)

                if (formattedStartDate != null || formattedRestorationDate != null) {
                    Spacer(modifier = Modifier.height(sizing.spacing(6.dp)))
                    Column(verticalArrangement = Arrangement.spacedBy(sizing.spacing(2.dp))) {
                        OutageDateLine(
                            label = stringResource(R.string.appstrings_outage_start_date),
                            value = formattedStartDate
                                ?: stringResource(R.string.appstrings_outage_date_unavailable),
                            valueColor = MaterialTheme.colorScheme.primary
                        )
                        OutageDateLine(
                            label = stringResource(R.string.appstrings_outage_restore_forecast),
                            value = formattedRestorationDate
                                ?: stringResource(R.string.appstrings_outage_date_unavailable),
                            valueColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Une panne déduite (zone blanche) n'a pas de données déclarées détaillées → pas de section dépliable.
            if (isOutage && outageDetails != null && !isPotentialOutage) {
                OutageDetailsSection(outageDetails, techStatus)
            }

            if (onAlertArcep != null) {
                Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))
                OutlinedButton(
                    onClick = onAlertArcep,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Launch,
                        contentDescription = null,
                        modifier = Modifier.size(sizing.component(18.dp))
                    )
                    Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                    Text(
                        text = stringResource(R.string.appstrings_alert_arcep_action),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))

            // --- SECTION 2 : GRILLE DES SERVICES ---
            val technologies = listOf("2G", "3G", "4G", "5G")

            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.weight(1.5f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    IconButton(
                        onClick = { showLegendDialog = true },
                        modifier = Modifier.size(sizing.component(32.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.appstrings_status_legend_open_desc),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(sizing.component(20.dp))
                        )
                    }
                }
                technologies.forEach { tech ->
                    Text(
                        text = tech,
                        fontWeight = FontWeight.Bold,
                        fontSize = sizing.text(14.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

            if (showVoiceRow) {
                ServiceRow(
                    serviceName = stringResource(R.string.appstrings_service_voice),
                    technologies = technologies,
                    techStatus = techStatus,
                    statusSelector = { _, it -> it.isVoixOk },
                    projectSelector = { _, it -> it.isProject || it.isVoixProject },
                    uncertainSelector = { _, it -> it.isVoixUncertain },
                    uncertainGreenSelector = { _, it -> it.isVoixUncertainGreen }
                )
            }
            if (showVoiceRow && showDataRow) {
                Spacer(modifier = Modifier.height(sizing.spacing(10.dp)))
            }
            if (showDataRow) {
                ServiceRow(
                    serviceName = stringResource(R.string.appstrings_outage_data),
                    technologies = technologies,
                    techStatus = techStatus,
                    statusSelector = { tech, it -> if (tech == "2G") null else it.isInternetOk },
                    projectSelector = { tech, it -> tech != "2G" && (it.isProject || it.isInternetProject) },
                    uncertainSelector = { tech, it -> tech != "2G" && it.isInternetUncertain },
                    uncertainGreenSelector = { tech, it -> tech != "2G" && it.isInternetUncertainGreen }
                )
            }

            Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

            // --- SECTION 3 : PIED DE PAGE ---
            // La date de la donnée n'accompagne QUE les pannes réellement déclarées : un site en
            // service n'est porté par aucun enregistrement, et une panne déduite (zone blanche) est
            // fabriquée côté app. Dans ces deux cas la date n'est pas « inconnue », elle n'existe
            // pas — l'annoncer comme manquante laisserait croire à un téléchargement raté.
            val sourceDate = formatOutageStatusDate(outageDetails?.sourceLastUpdate)
            val hasDeclaredOutage = outageDetails != null && !isPotentialOutage
            val footerText = when {
                sourceDate != null -> "${stringResource(R.string.appstrings_outage_source_date)} $sourceDate"
                hasDeclaredOutage -> stringResource(R.string.appstrings_outage_source_date_unknown)
                else -> stringResource(R.string.appstrings_outage_none_declared)
            }
            Text(
                text = footerText,
                fontSize = sizing.text(12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        }
    }
}

private data class OutageDetailDisplayRow(
    val label: String,
    val value: String,
    val valueColor: Color? = null
)

@Composable
private fun StatusLegendDialog(onDismiss: () -> Unit) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val colorOk = Color(0xFF4CAF50)
    val colorKo = Color(0xFFE53935)
    val colorProject = Color(0xFFFFA000)
    val colorNeutral = Color.Gray.copy(alpha = 0.45f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.appstrings_status_legend_title),
                style = sizing.textStyle(MaterialTheme.typography.headlineSmall),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))) {
                StatusLegendRow(
                    symbol = {
                        Icon(Icons.Default.Check, null, tint = colorOk, modifier = Modifier.size(sizing.component(20.dp)))
                    },
                    text = stringResource(R.string.appstrings_status_legend_ok)
                )
                StatusLegendRow(
                    symbol = {
                        Icon(Icons.Default.Close, null, tint = colorKo, modifier = Modifier.size(sizing.component(20.dp)))
                    },
                    text = stringResource(R.string.appstrings_status_legend_outage)
                )
                StatusLegendRow(
                    symbol = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(sizing.spacing(3.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("?", color = colorOk, fontWeight = FontWeight.Bold, fontSize = sizing.text(14.sp))
                            Text("?", color = colorKo, fontWeight = FontWeight.Bold, fontSize = sizing.text(14.sp))
                        }
                    },
                    text = stringResource(R.string.appstrings_status_legend_uncertain)
                )
                StatusLegendRow(
                    symbol = {
                        Icon(Icons.Default.Remove, null, tint = colorNeutral, modifier = Modifier.size(sizing.component(20.dp)))
                    },
                    text = stringResource(R.string.appstrings_status_legend_unavailable)
                )
                StatusLegendRow(
                    symbol = {
                        Text("~", color = colorProject, fontWeight = FontWeight.Bold, fontSize = sizing.text(22.sp))
                    },
                    text = stringResource(R.string.appstrings_status_legend_project)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(
                    text = stringResource(R.string.appstrings_status_legend_codes_title),
                    fontSize = sizing.text(12.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusLegendRow(
                    symbol = { Text("OK", fontWeight = FontWeight.Bold, fontSize = sizing.text(12.sp)) },
                    text = stringResource(R.string.appstrings_status_code_ok)
                )
                StatusLegendRow(
                    symbol = { Text("DE", fontWeight = FontWeight.Bold, fontSize = sizing.text(12.sp)) },
                    text = stringResource(R.string.appstrings_status_code_de)
                )
                StatusLegendRow(
                    symbol = { Text("HS", fontWeight = FontWeight.Bold, fontSize = sizing.text(12.sp)) },
                    text = stringResource(R.string.appstrings_status_code_hs)
                )
                StatusLegendRow(
                    symbol = { Text("NE", fontWeight = FontWeight.Bold, fontSize = sizing.text(12.sp)) },
                    text = stringResource(R.string.appstrings_status_code_ne)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(
                    text = stringResource(R.string.appstrings_status_legend_reason_codes_title),
                    fontSize = sizing.text(12.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusLegendRow(
                    symbol = { Text("INT", fontWeight = FontWeight.Bold, fontSize = sizing.text(11.sp)) },
                    text = stringResource(R.string.appstrings_outage_reason_incident)
                )
                StatusLegendRow(
                    symbol = { Text("MAINT", fontWeight = FontWeight.Bold, fontSize = sizing.text(11.sp)) },
                    text = stringResource(R.string.appstrings_outage_reason_maintenance)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.appstrings_close), style = sizing.textStyle(MaterialTheme.typography.labelLarge))
            }
        }
    )
}

@Composable
private fun StatusLegendRow(
    symbol: @Composable BoxScope.() -> Unit,
    text: String
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(sizing.component(28.dp)),
            contentAlignment = Alignment.Center,
            content = symbol
        )
        Text(
            text = text,
            fontSize = sizing.text(13.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun OutageDetailsSection(
    details: SiteHsEntity,
    techStatus: Map<String, ServiceStatus>
) {
    val sizing = LocalGeoTowerUiSizing.current
    var expanded by remember(details.idAnfr, details.dateDebut, details.dateFin) {
        mutableStateOf(false)
    }

    Spacer(modifier = Modifier.height(sizing.spacing(10.dp)))
    TextButton(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = sizing.spacing(0.dp), vertical = sizing.spacing(4.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.appstrings_outage_details_toggle),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }

    if (!expanded) return

    val unavailableText = stringResource(R.string.appstrings_outage_date_unavailable)
    val outageColor = Color(0xFFE53935)

    fun displayValue(value: String?): String = cleanOutageValue(value) ?: unavailableText

    fun displayDate(value: String?): String {
        return formatOutageStatusDate(value) ?: unavailableText
    }

    fun detailRow(label: String, value: String?): OutageDetailDisplayRow {
        return OutageDetailDisplayRow(label, displayValue(value))
    }

    fun serviceDetailRow(label: String, value: String?, globalCode: String?): OutageDetailDisplayRow {
        val valueColor = if (OutageStatusCodes.isHs(globalCode) || OutageStatusCodes.isDown(value)) {
            outageColor
        } else {
            null
        }
        return OutageDetailDisplayRow(label, displayValue(value), valueColor)
    }

    val projectText = stringResource(R.string.appstrings_status_project)

    fun technologyStatusRow(
        label: String,
        value: String?,
        technology: String,
        globalCode: String?,
        isServiceProject: (ServiceStatus) -> Boolean
    ): OutageDetailDisplayRow {
        val status = techStatus[technology]
        return if (status != null && (status.isProject || isServiceProject(status))) {
            OutageDetailDisplayRow(label, projectText)
        } else {
            serviceDetailRow(label, value, globalCode)
        }
    }

    val ownSiteText = when (details.propre) {
        1 -> stringResource(R.string.common_yes)
        0 -> stringResource(R.string.common_no)
        else -> details.propre?.toString() ?: unavailableText
    }

    val identityRows = listOf(
        detailRow(stringResource(R.string.appstrings_outage_operator), details.operateur),
        detailRow(stringResource(R.string.appstrings_outage_station_anfr), details.idAnfr),
        detailRow(stringResource(R.string.appstrings_outage_city), details.commune),
        detailRow(stringResource(R.string.appstrings_outage_department), details.departement),
        detailRow(stringResource(R.string.appstrings_outage_own_site), ownSiteText)
    )

    val causeRows = listOf(
        detailRow(stringResource(R.string.appstrings_outage_reason_code), details.raison),
        detailRow(stringResource(R.string.appstrings_outage_detail), details.detail)
    )

    val dateRows = listOf(
        detailRow(stringResource(R.string.appstrings_outage_global_start), displayDate(details.dateDebut)),
        detailRow(stringResource(R.string.appstrings_outage_global_end), displayDate(details.dateFin)),
        detailRow(stringResource(R.string.appstrings_outage_voice_start), displayDate(details.debutVoix)),
        detailRow(stringResource(R.string.appstrings_outage_voice_end), displayDate(details.finVoix)),
        detailRow(stringResource(R.string.appstrings_outage_data_start), displayDate(details.debutData)),
        detailRow(stringResource(R.string.appstrings_outage_data_end), displayDate(details.finData))
    )

    val voiceLabel = stringResource(R.string.appstrings_outage_voice)
    val dataLabel = stringResource(R.string.appstrings_outage_data)
    val rawStatusRows = listOf(
        serviceDetailRow(voiceLabel, details.voixGlobal, details.voixGlobal),
        serviceDetailRow(dataLabel, details.dataGlobal, details.dataGlobal),
        technologyStatusRow("$voiceLabel 2G", details.voix2g, "2G", details.voixGlobal) { it.isVoixProject },
        technologyStatusRow("$voiceLabel 3G", details.voix3g, "3G", details.voixGlobal) { it.isVoixProject },
        technologyStatusRow("$voiceLabel 4G", details.voix4g, "4G", details.voixGlobal) { it.isVoixProject },
        technologyStatusRow("$voiceLabel 5G", details.voix5g, "5G", details.voixGlobal) { it.isVoixProject },
        technologyStatusRow("$dataLabel 3G", details.data3g, "3G", details.dataGlobal) { it.isInternetProject },
        technologyStatusRow("$dataLabel 4G", details.data4g, "4G", details.dataGlobal) { it.isInternetProject },
        technologyStatusRow("$dataLabel 5G", details.data5g, "5G", details.dataGlobal) { it.isInternetProject }
    )

    Spacer(modifier = Modifier.height(sizing.spacing(14.dp)))
    OutageDetailGroup(stringResource(R.string.appstrings_outage_site_section), identityRows)
    OutageDetailGroup(stringResource(R.string.appstrings_outage_incident_section), causeRows + dateRows)
    OutageDetailGroup(stringResource(R.string.appstrings_outage_services_section), rawStatusRows)
}

@Composable
private fun OutageDetailGroup(title: String, rows: List<OutageDetailDisplayRow>) {
    val sizing = LocalGeoTowerUiSizing.current
    if (rows.isEmpty()) return

    Text(
        text = title,
        fontSize = sizing.text(12.sp),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = sizing.spacing(8.dp), bottom = sizing.spacing(4.dp))
    )

    Column(verticalArrangement = Arrangement.spacedBy(sizing.spacing(3.dp))) {
        rows.forEach { row ->
            OutageDetailLine(label = row.label, value = row.value, valueColor = row.valueColor)
        }
    }
}

@Composable
private fun OutageDetailLine(label: String, value: String, valueColor: Color? = null) {
    val sizing = LocalGeoTowerUiSizing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp)),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = sizing.text(11.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.9f)
        )
        Text(
            text = value,
            fontSize = sizing.text(11.sp),
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.2f)
        )
    }
}

@Composable
private fun OutageDateLine(
    label: String,
    value: String,
    valueColor: Color
) {
    val sizing = LocalGeoTowerUiSizing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(sizing.spacing(6.dp)),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label :",
            fontSize = sizing.text(12.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.9f)
        )
        Text(
            text = value,
            fontSize = sizing.text(13.sp),
            fontWeight = FontWeight.Bold,
            color = valueColor,
            modifier = Modifier.weight(1.2f)
        )
    }
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

private fun cleanOutageValue(rawValue: String?): String? {
    return rawValue
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != "-" && !it.equals("null", ignoreCase = true) }
}

@Composable
private fun ServiceRow(
    serviceName: String,
    technologies: List<String>,
    techStatus: Map<String, ServiceStatus>,
    statusSelector: (String, ServiceStatus) -> Boolean?,
    projectSelector: (String, ServiceStatus) -> Boolean,
    uncertainSelector: (String, ServiceStatus) -> Boolean,
    uncertainGreenSelector: (String, ServiceStatus) -> Boolean
) {
    val sizing = LocalGeoTowerUiSizing.current
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
            fontSize = sizing.text(13.sp),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.5f)
        )

        technologies.forEach { tech ->
            val techItem = techStatus[tech]
            val status = techItem?.let { statusSelector(tech, it) }
            val isProj = techItem?.let { projectSelector(tech, it) } == true
            val isUncertain = techItem?.let { uncertainSelector(tech, it) } == true
            val isUncertainGreen = techItem?.let { uncertainGreenSelector(tech, it) } == true

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // 🚨 NOUVEAU : Logique d'affichage de la case (Priorité au projet)
                when {
                    isProj -> Text("~", color = colorProject, fontWeight = FontWeight.Bold, fontSize = sizing.text(22.sp))
                    status == true -> Icon(Icons.Default.Check, null, tint = colorOk, modifier = Modifier.size(sizing.component(20.dp)))
                    status == false -> Icon(Icons.Default.Close, null, tint = colorKo, modifier = Modifier.size(sizing.component(20.dp)))
                    // Panne déclarée mais service non détaillé : « ? » coloré selon l'état connu de
                    // l'autre service de la génération.
                    isUncertain -> Text(
                        "?",
                        color = if (isUncertainGreen) colorOk else colorKo,
                        fontWeight = FontWeight.Bold,
                        fontSize = sizing.text(16.sp)
                    )
                    else -> Icon(Icons.Default.Remove, null, tint = colorNeutral, modifier = Modifier.size(sizing.component(20.dp)))
                }
            }
        }
    }
}
