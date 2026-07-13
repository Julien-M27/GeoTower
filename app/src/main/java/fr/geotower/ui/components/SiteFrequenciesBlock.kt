package fr.geotower.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.utils.AnfrDisplayText
import fr.geotower.utils.AppConfig
import fr.geotower.utils.FrequencyFilterSelection
import fr.geotower.utils.FrequencyStatusType
import fr.geotower.utils.FreqBand
import fr.geotower.utils.addMicrowaveFallbackBands
import fr.geotower.utils.classifyFrequencyStatus
import fr.geotower.utils.formatSpectrumDisplayDetails
import fr.geotower.utils.formatDateToFrench
import fr.geotower.utils.parseAndSortFrequencies
import fr.geotower.utils.radioBandCode
import fr.geotower.utils.radioTechnologyFrequencyLabel
import kotlin.math.roundToInt
import java.util.Locale
import androidx.compose.ui.res.stringResource
import fr.geotower.R
import fr.geotower.utils.ThroughputDisplayText

@Composable
fun SiteFrequenciesBlock(
    info: LocalisationEntity,
    technique: TechniqueEntity?,
    formattedAzimuths: String,
    cardBgColor: Color,
    blockShape: Shape,
    applyMapFilters: Boolean = false,
    forceGridDisplay: Boolean = false,
    showAntennaTypeTable: Boolean = false
) {
    val context = LocalContext.current
    val txtFrequenciesTitle = stringResource(R.string.appstrings_frequencies_title)
    val txtBandsNotSpecified = stringResource(R.string.appstrings_bands_not_specified)
    val txtInService = stringResource(R.string.appstrings_in_service)
    val txtTechnically = stringResource(R.string.appstrings_technically)
    val txtUnknownStatus = stringResource(R.string.appstrings_unknown_status)
    val txtProjectApproved = stringResource(R.string.appstrings_project_approved)
    val txtMicrowaveLinks = ThroughputDisplayText.backhaulLabel("radio")

    // ✅ NOUVELLES TRADUCTIONS RÉCUPÉRÉES
    val txtUnknown = stringResource(R.string.appstrings_unknown)
    val txtActivatedOn = stringResource(R.string.appstrings_activated_on)
    val txtDateNotSpecifiedAnfr = stringResource(R.string.appstrings_date_not_specified_anfr)
    val txtAzimuthNotSpecified = stringResource(R.string.appstrings_azimuth_not_specified)
    val txtPanelIdentifier = stringResource(R.string.appstrings_panel_identifier)
    val txtPanelIdentifierCopied = stringResource(R.string.appstrings_panel_identifier_copied)
    val txtCopy = stringResource(R.string.appstrings_copy)

    val rawFreqs = technique?.detailsFrequences ?: info.frequences
    val mapFrequencyFilter = if (applyMapFilters) FrequencyFilterSelection.fromMapConfig() else null

    // ✅ ON INTÈGRE NOS NOUVEAUX FILTRES DYNAMIQUES
    val parsedBands = remember(
        rawFreqs,
        info.azimutsFh,
        info.techMask,
        info.bandMask,
        info.statut,
        technique?.technologies,
        technique?.statut,
        technique?.dateService,
        technique?.dateImplantation,
        technique?.dateModif,
        AppConfig.siteShowTechno2G.value, AppConfig.siteF2G_900.value, AppConfig.siteF2G_1800.value,
        AppConfig.siteShowTechno3G.value, AppConfig.siteF3G_900.value, AppConfig.siteF3G_2100.value,
        AppConfig.siteShowTechno4G.value, AppConfig.siteF4G_700.value, AppConfig.siteF4G_800.value, AppConfig.siteF4G_900.value, AppConfig.siteF4G_1800.value, AppConfig.siteF4G_2100.value, AppConfig.siteF4G_2600.value,
        AppConfig.siteShowTechno5G.value, AppConfig.siteF5G_700.value, AppConfig.siteF5G_1400.value, AppConfig.siteF5G_2100.value, AppConfig.siteF5G_3500.value, AppConfig.siteF5G_4200.value, AppConfig.siteF5G_26000.value,
        AppConfig.siteShowTechnoFH.value
    ) {
        addMicrowaveFallbackBands(
            bands = parseAndSortFrequencies(rawFreqs, txtUnknown, txtAzimuthNotSpecified),
            info = info,
            technique = technique,
            rawFreqs = rawFreqs,
            txtUnknown = txtUnknown
        ).filter { band ->
            shouldDisplayFrequencyBand(band)
        }
    }

    val mobileBands = remember(parsedBands) { parsedBands.filter { it.gen in 2..5 } }
    val fhBands = remember(parsedBands) { parsedBands.filter { it.gen !in 2..5 } }

    Card(
        shape = blockShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WifiTethering, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = txtFrequenciesTitle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (parsedBands.isEmpty()) {
                Text(text = txtBandsNotSpecified, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                // ✅ NOUVEAU : On vérifie l'option d'affichage
                if (forceGridDisplay || AppConfig.siteFreqGridDisplay.value) {
                    FrequenciesGridView(
                        parsedBands = parsedBands,
                        txtUnknown = txtUnknown,
                        txtDateNotSpecifiedAnfr = txtDateNotSpecifiedAnfr,
                        txtInService = txtInService,
                        txtTechnically = txtTechnically,
                        txtProjectApproved = txtProjectApproved,
                        txtUnknownStatus = txtUnknownStatus,
                        mapFrequencyFilter = mapFrequencyFilter,
                        txtPanelIdentifier = txtPanelIdentifier,
                        showAntennaTypeTable = showAntennaTypeTable
                    )
                } else {
                    // 👇 TON CODE ACTUEL COMMENCE ICI 👇
                    val sectionedBands = mobileBands + fhBands
                    sectionedBands.forEachIndexed { index, band ->
                        if (index == mobileBands.size && fhBands.isNotEmpty()) {
                            if (mobileBands.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                text = txtMicrowaveLinks,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }
                        val (statusColor, statusText) = when (classifyFrequencyStatus(band.status)) {
                            FrequencyStatusType.InService -> Pair(Color(0xFF4CAF50), txtInService)
                            FrequencyStatusType.TechnicallyOperational -> Pair(Color(0xFF4CAF50), txtTechnically)
                            FrequencyStatusType.Approved -> Pair(Color(0xFF2196F3), txtProjectApproved)
                            FrequencyStatusType.Unknown -> Pair(Color.Gray, txtUnknownStatus)
                        }

                    val dateFormatted = formatDateToFrench(band.date)
                    // ✅ TEXTES ENTIÈREMENT DYNAMIQUES
                    val dateDisplay = if (dateFormatted.isNotBlank() && dateFormatted != "-") {
                        "$txtActivatedOn$dateFormatted"
                    } else {
                        txtDateNotSpecifiedAnfr
                    }
                    val isMutedByMapFilter = mapFrequencyFilter?.let { filter ->
                        !filter.isFullyEnabled && !filter.matchesBand(band.gen, band.value)
                    } ?: false

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (isMutedByMapFilter) 0.42f else 1f)
                            .padding(bottom = if (index == sectionedBands.lastIndex) 0.dp else 12.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                // ✅ NOUVEAU : Formatage propre "4G 700" ici aussi
                                val bandCode = radioBandCode(band.gen, band.value)
                                val mainBandLabel = if (band.gen in 2..5 && band.value > 0) {
                                    radioTechnologyFrequencyLabel(band.gen, band.value)
                                } else {
                                    band.rawFreq.substringBefore(":").trim().ifBlank { band.rawFreq }
                                }

                                if (band.gen in 2..5 && band.value > 0) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "• ",
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                    Column {
                                        Text(
                                            text = mainBandLabel,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        if (bandCode != null) {
                                            Text(
                                                text = "($bandCode)",
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp,
                                                lineHeight = 14.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                                } else {
                                    Text(
                                        text = "• ${band.rawFreq.substringBefore(":").trim().ifBlank { band.rawFreq }}",
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = statusText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal,
                                        fontSize = 10.sp, // ✅ État plus petit (avant: 12.sp)
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                                val preciseFreqs = band.spectrumLines
                                    .ifEmpty { listOf(band.rawFreq.substringAfter(":", "").trim()) }
                                    .filter { it.isNotBlank() }
                                    .joinToString("\n")

                                // ✅ ÉTAPE 1 : On vérifie si le switch maître du spectre est activé
                                if (AppConfig.siteShowSpectrum.value && preciseFreqs.isNotBlank() && preciseFreqs != band.rawFreq.trim()) {
                                    val spectrumDisplay = formatSpectrumDisplayDetails(preciseFreqs)

                                    // ✅ ÉTAPE 2 : On affiche le détail par bande uniquement si son switch est actif
                                    if (AppConfig.siteShowSpectrumBand.value) {
                                        Text(
                                            text = "${stringResource(R.string.appstrings_spectrum_by_band)} :\n\n${spectrumDisplay.detailedFrequencies}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            lineHeight = 16.sp
                                        )
                                    }

                                    // ✅ ÉTAPE 3 : On affiche le spectre total uniquement si son switch est actif et qu'il y a une valeur
                                    if (AppConfig.siteShowSpectrumTotal.value && spectrumDisplay.hasTotal) {
                                        // On ajoute un petit espace seulement si le texte du dessus est affiché
                                        if (AppConfig.siteShowSpectrumBand.value) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                        }

                                        Text(text = "${stringResource(R.string.appstrings_totalspectrum)} : ${spectrumDisplay.totalBandwidth} ${spectrumDisplay.totalUnit}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(R.string.appstrings_total_spectrum_warning),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                            fontSize = 11.sp,
                                            lineHeight = 13.sp
                                        )
                                    }

                                    // Espacement final uniquement si l'un des deux éléments a été affiché
                                    if (AppConfig.siteShowSpectrumBand.value || (AppConfig.siteShowSpectrumTotal.value && spectrumDisplay.hasTotal)) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }

                                Text(text = dateDisplay, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

                                // Affichage de tous les panneaux associés à cette fréquence
                                if (band.physDetails.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    band.physDetails.forEach { physDetail ->
                                        Row(
                                            verticalAlignment = Alignment.Top,
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Explore,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))

                                            // ✅ SÉPARATION ET TRADUCTION DU TYPE D'ANTENNE (Version Ultra-Robuste)
                                            val panelId = extractFrequencyPanelId(physDetail)
                                            val displayPhysDetail = removeFrequencyPanelIdForDisplay(physDetail)
                                            val typePart =
                                                if (displayPhysDetail.contains(":")) displayPhysDetail.substringBefore(
                                                    ":"
                                                ).trim() else displayPhysDetail.trim()
                                            val restPart =
                                                if (displayPhysDetail.contains(":")) displayPhysDetail.substringAfter(
                                                    ":"
                                                ).trim() else ""

                                            val translatedType =
                                                AnfrDisplayText.antennaType(typePart)
                                            // ✅ SÉCURITÉ : Si la traduction renvoie du vide, on force l'affichage du mot original (ex: "Panneau")
                                            val safeType =
                                                if (translatedType.isNotBlank()) translatedType else typePart

                                            val finalPhysText =
                                                if (restPart.isNotEmpty()) "$safeType : $restPart" else safeType

                                            Column {
                                                Text(
                                                    text = formatFrequencyPhysicalDetailsForUnit(finalPhysText), // Ex: "Panel : 120° (15.5m)"
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 12.sp
                                                )
                                                panelId?.let { id ->
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "$txtPanelIdentifier : $id",
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontWeight = FontWeight.Medium,
                                                            fontSize = 12.sp
                                                        )
                                                        IconButton(
                                                            onClick = {
                                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                                clipboard.setPrimaryClip(ClipData.newPlainText(txtPanelIdentifier, id))
                                                                Toast.makeText(context, txtPanelIdentifierCopied, Toast.LENGTH_SHORT).show()
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.ContentCopy,
                                                                contentDescription = txtCopy,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ✅ NOUVEAU : On passe les traductions en paramètres pour plus de sécurité
// ✅ NOUVEAU : On passe les traductions en paramètres
private fun shouldDisplayFrequencyBand(band: FreqBand): Boolean {
    return when (band.gen) {
        5 -> AppConfig.siteShowTechno5G.value && when (band.value) {
            700 -> AppConfig.siteF5G_700.value
            1400 -> AppConfig.siteF5G_1400.value
            2100 -> AppConfig.siteF5G_2100.value
            3500 -> AppConfig.siteF5G_3500.value
            4200 -> AppConfig.siteF5G_4200.value
            26000 -> AppConfig.siteF5G_26000.value
            else -> true
        }
        4 -> AppConfig.siteShowTechno4G.value && when (band.value) {
            700 -> AppConfig.siteF4G_700.value
            800 -> AppConfig.siteF4G_800.value
            900 -> AppConfig.siteF4G_900.value
            1800 -> AppConfig.siteF4G_1800.value
            2100 -> AppConfig.siteF4G_2100.value
            2600 -> AppConfig.siteF4G_2600.value
            else -> true
        }
        3 -> AppConfig.siteShowTechno3G.value && when (band.value) {
            900 -> AppConfig.siteF3G_900.value
            2100 -> AppConfig.siteF3G_2100.value
            else -> true
        }
        2 -> AppConfig.siteShowTechno2G.value && when (band.value) {
            900 -> AppConfig.siteF2G_900.value
            1800 -> AppConfig.siteF2G_1800.value
            else -> true
        }
        else -> AppConfig.siteShowTechnoFH.value
    }
}

private fun displayFrequencyBandLabel(band: FreqBand): String {
    if (band.gen in 2..5 && band.value > 0) {
        val base = radioTechnologyFrequencyLabel(band.gen, band.value)
        return radioBandCode(band.gen, band.value)?.let { "$base ($it)" } ?: base
    }
    return band.rawFreq.substringBefore(":").trim().ifBlank { band.rawFreq }
}

private fun formatFrequencyPhysicalDetailsForUnit(text: String): String {
    val displayText = removeFrequencyPanelIdForDisplay(text)
    if (AppConfig.distanceUnit.intValue != 1) return displayText
    return frequencyHeightMetersRegex.replace(displayText) { match ->
        val meters = match.groupValues[1].replace(',', '.').toDoubleOrNull()
        "(${formatFrequencyHeightMeters(meters)})"
    }
}

private fun removeFrequencyPanelIdForDisplay(text: String): String {
    return frequencyPanelIdRegex.replace(text, "").trim()
}

private fun formatFrequencyHeightForUnit(rawHeight: String): String {
    if (AppConfig.distanceUnit.intValue != 1) return rawHeight
    val trimmed = rawHeight.trim()
    if (trimmed == "-" || trimmed.contains("ft", ignoreCase = true)) return rawHeight
    val meters = Regex("""[0-9]+(?:[.,][0-9]+)?""").find(trimmed)?.value?.replace(',', '.')?.toDoubleOrNull()
        ?: return rawHeight
    return formatFrequencyHeightMeters(meters)
}

private fun formatFrequencyHeightMeters(meters: Double?): String {
    if (meters == null) return "--"
    return "${(meters * 3.28084).roundToInt()} ft"
}

private fun extractFrequencyPanelId(text: String): String? {
    return frequencyPanelIdRegex.find(text)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
}

private val frequencyPanelIdRegex = Regex("""\[AER_ID:\s*([^\]\s]+)\]""")
private val frequencyHeightMetersRegex = Regex("""\(([0-9]+(?:[.,][0-9]+)?)\s*m\)""", RegexOption.IGNORE_CASE)

// ==========================================
// 🚀 NOUVEAU DESIGN EN GRILLE (EXPERT)
// ==========================================
@Composable
fun FrequenciesGridView(
    parsedBands: List<FreqBand>,
    txtUnknown: String,
    txtDateNotSpecifiedAnfr: String,
    txtInService: String,
    txtTechnically: String,
    txtProjectApproved: String,
    txtUnknownStatus: String,
    mapFrequencyFilter: FrequencyFilterSelection?,
    txtPanelIdentifier: String,
    showAntennaTypeTable: Boolean = false
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val headerBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
    val subHeaderBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    // --- TABLEAU 1 : ÉMETTEURS (Techno / Date / État) ---
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Titre du tableau 1
            Box(
                modifier = Modifier.fillMaxWidth().background(headerBgColor).padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.appstrings_emitters_table_title),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider(color = borderColor)

            // En-têtes des colonnes
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                    .background(subHeaderBgColor), verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.appstrings_col_techno),
                    modifier = Modifier.weight(1.3f).padding(8.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                VerticalDivider(color = borderColor)
                Text(
                    stringResource(R.string.appstrings_col_service),
                    modifier = Modifier.weight(1f).padding(8.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                VerticalDivider(color = borderColor)
                Text(
                    stringResource(R.string.appstrings_col_state),
                    modifier = Modifier.weight(1.1f).padding(8.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
            HorizontalDivider(color = borderColor)

            // Lignes de données
            parsedBands.forEachIndexed { index, band ->
                val technoName = displayFrequencyBandLabel(band)
                val dateFormatted = formatDateToFrench(band.date)
                val dateDisplay =
                    if (dateFormatted.isNotBlank() && dateFormatted != "-") dateFormatted else txtDateNotSpecifiedAnfr

                val statusType = classifyFrequencyStatus(band.status)
                val statusColor = when (statusType) {
                    FrequencyStatusType.InService -> Color(0xFF4CAF50)
                    FrequencyStatusType.TechnicallyOperational -> MaterialTheme.colorScheme.primary
                    FrequencyStatusType.Approved -> Color(0xFF2196F3)
                    FrequencyStatusType.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val statusText = when (statusType) {
                    FrequencyStatusType.InService -> txtInService
                    FrequencyStatusType.TechnicallyOperational -> txtTechnically
                    FrequencyStatusType.Approved -> txtProjectApproved
                    FrequencyStatusType.Unknown -> txtUnknownStatus
                }
                val isMutedByMapFilter = mapFrequencyFilter?.let { filter ->
                    !filter.isFullyEnabled && !filter.matchesBand(band.gen, band.value)
                } ?: false

                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                        .alpha(if (isMutedByMapFilter) 0.42f else 1f)
                        .background(MaterialTheme.colorScheme.surface),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        technoName,
                        modifier = Modifier.weight(1.3f).padding(8.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    VerticalDivider(color = borderColor)
                    Text(
                        dateDisplay,
                        modifier = Modifier.weight(1f).padding(8.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    VerticalDivider(color = borderColor)
                    Text(
                        statusText,
                        modifier = Modifier.weight(1.1f).padding(8.dp),
                        fontSize = 12.sp,
                        color = statusColor,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
                if (index < parsedBands.lastIndex) HorizontalDivider(color = borderColor)
            }
        }
    }

    // --- TABLEAU 2 : ANTENNES (Azimut / Hauteur / Techno / Fréquence) ---
    // Regroupement des données par Azimut, PUIS par Hauteur
    data class AntennaRow(
        val techno: String,
        val bandEquivalent: String?,
        val freqs: String,
        val gen: Int,
        val value: Int,
        val panelId: String?,
        val isMuted: Boolean
    )
    val groupedAntennas = mutableMapOf<String, MutableMap<String, MutableList<AntennaRow>>>()

    parsedBands.forEach { band ->
        // ✅ NOUVEAU : Formatage propre "4G 700"
        val technoName = displayFrequencyBandLabel(band)
        val bandEquivalent = null

        val freqs = band.spectrumLines
            .ifEmpty { listOf(band.rawFreq.substringAfter(":", "").trim()) }
            .filter { it.isNotBlank() }
            .joinToString("\n")

        // Normalisation des plages de fréquences pour un rendu compact.
        val displayFreqs = if (freqs.isNotBlank()) {
            Regex("""([0-9]+(?:[.,][0-9]+)?)\s*-\s*([0-9]+(?:[.,][0-9]+)?)\s*([a-zA-Z]*Hz)?""")
                .replace(freqs) { match ->
                    val unit = match.groupValues[3].takeIf { it.isNotBlank() }
                    buildString {
                        append(match.groupValues[1])
                        append(" - ")
                        append(match.groupValues[2])
                        if (unit != null) append(" ").append(unit)
                    }
                }
                .replace(Regex("""\s*,\s*"""), ", ")
                .replace(Regex("""[ \t]+"""), " ")
                .trim()
        } else {
            "-"
        }

        val isMutedByMapFilter = mapFrequencyFilter?.let { filter ->
            !filter.isFullyEnabled && !filter.matchesBand(band.gen, band.value)
        } ?: false

        if (band.physDetails.isEmpty()) {
            groupedAntennas.getOrPut("-") { mutableMapOf() }
                .getOrPut("-") { mutableListOf() }
                .add(AntennaRow(technoName, bandEquivalent, displayFreqs, band.gen, band.value, null, isMutedByMapFilter))
        } else {
            band.physDetails.forEach { phys ->
                // Séparation robuste: "Panneau : 60° (28.9m)" -> On récupère "60" et "28.9m"
                val details =
                    if (phys.contains(":")) phys.substringAfter(":").trim() else phys.trim()
                val azimut =
                    details.substringBefore("(").replace("°", "").trim().takeIf { it.isNotBlank() }
                        ?: "-"
                val rawHauteur =
                    if (details.contains("(")) details.substringAfter("(").substringBefore(")")
                        .trim() else "-"
                val hauteur = formatFrequencyHeightForUnit(rawHauteur)

                groupedAntennas.getOrPut(azimut) { mutableMapOf() }
                    .getOrPut(hauteur) { mutableListOf() }
                    .add(AntennaRow(technoName, bandEquivalent, displayFreqs, band.gen, band.value, extractFrequencyPanelId(phys), isMutedByMapFilter))
            }
        }
    }

    // Tri des azimuts (croissant)
    val sortedAzimuts = groupedAntennas.entries.sortedBy {
        it.key.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 9999
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Titre du tableau 2
            Box(
                modifier = Modifier.fillMaxWidth().background(headerBgColor).padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.appstrings_antennas_table_title),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider(color = borderColor)

            // En-têtes des colonnes
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                    .background(subHeaderBgColor), verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.appstrings_col_azimuth),
                    modifier = Modifier.weight(0.8f).padding(6.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                VerticalDivider(color = borderColor)
                Text(
                    stringResource(R.string.appstrings_col_height),
                    modifier = Modifier.weight(1f).padding(6.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                VerticalDivider(color = borderColor)
                Text(
                    stringResource(R.string.appstrings_col_band),
                    modifier = Modifier.weight(1f).padding(6.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                VerticalDivider(color = borderColor)
                Text(
                    stringResource(R.string.appstrings_col_freqs),
                    modifier = Modifier.weight(1.6f).padding(6.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
            HorizontalDivider(color = borderColor)

            // Lignes groupées avec double "IntrinsicSize.Min" pour fusionner verticalement
            sortedAzimuts.forEachIndexed { azimutIndex, azimutEntry ->
                val azimut = azimutEntry.key
                val azimutDisplay = if (azimut == "-") "-" else "$azimut°"
                // Tri optionnel des hauteurs (par exemple par ordre décroissant)
                val sortedHauteurs = azimutEntry.value.entries.sortedByDescending {
                    it.key.replace(
                        Regex("[^0-9.]"),
                        ""
                    ).toFloatOrNull() ?: 0f
                }
                val panelIds = azimutEntry.value.values
                    .flatten()
                    .mapNotNull { it.panelId }
                    .distinct()
                    .sorted()

                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    // 1. Cellule fusionnée : Azimut (englobe toutes les hauteurs)
                    Box(
                        modifier = Modifier.weight(0.8f).fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Text(
                                azimutDisplay,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                            // Les identifiants sont déportés dans le tableau « type de panneau » quand il est actif.
                            if (!showAntennaTypeTable && panelIds.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = txtPanelIdentifier,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 10.sp
                                )
                                Text(
                                    text = panelIds.joinToString("\n"),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 10.sp
                                )
                            }
                        }
                    }
                    VerticalDivider(color = borderColor)

                    // 2. Colonne des hauteurs
                    Column(modifier = Modifier.weight(3.6f)) { // 1.0 + 1.0 + 1.6 = 3.6
                        sortedHauteurs.forEachIndexed { hauteurIndex, hauteurEntry ->
                            val hauteur = hauteurEntry.key
                            val rows = hauteurEntry.value

                            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                // 2A. Cellule fusionnée : Hauteur (englobe toutes les technos pour cette hauteur)
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        hauteur,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        modifier = Modifier.padding(4.dp)
                                    )
                                }
                                VerticalDivider(color = borderColor)

                                // 2B. Sous-tableau : Technologies et Fréquences
                                Column(modifier = Modifier.weight(2.6f)) { // 1.0 + 1.6 = 2.6
                                    rows.forEachIndexed { rowIndex, rowItem ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth()
                                                .height(IntrinsicSize.Min)
                                                .alpha(if (rowItem.isMuted) 0.42f else 1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(1f)
                                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = rowItem.techno,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    textAlign = TextAlign.Center
                                                )
                                                rowItem.bandEquivalent?.let { equivalent ->
                                                    Text(
                                                        text = "($equivalent)",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        textAlign = TextAlign.Center,
                                                        lineHeight = 12.sp
                                                    )
                                                }
                                            }
                                            VerticalDivider(color = borderColor)
                                            Text(
                                                text = rowItem.freqs,
                                                modifier = Modifier.weight(1.6f)
                                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                lineHeight = 16.sp
                                            )
                                        }
                                        if (rowIndex < rows.lastIndex) HorizontalDivider(color = borderColor)
                                    }
                                }
                            }
                            if (hauteurIndex < sortedHauteurs.lastIndex) HorizontalDivider(color = borderColor)
                        }
                    }
                }
                if (azimutIndex < sortedAzimuts.lastIndex) HorizontalDivider(color = borderColor)
            }
        }
    }

    // --- TABLEAU 3 : TYPE DE PANNEAU PAR AZIMUT ---
    // Parité avec le tableau « Identifiant panneau (AER ID) » du rapport PDF (GeoTowerReportPdf) :
    // uniquement les panneaux porteurs d'un identifiant AER, regroupés par azimut.
    if (showAntennaTypeTable) {
        val antennaTypeRows = buildAntennaTypeRows(parsedBands)
        AntennaTypeGridTable(
            rows = antennaTypeRows,
            borderColor = borderColor,
            headerBgColor = headerBgColor,
            subHeaderBgColor = subHeaderBgColor
        )
    }
}

// ==========================================
// 🚀 TABLEAU 3 : TYPE DE PANNEAU PAR AZIMUT (parité PDF)
// ==========================================
internal data class AntennaTypeGridRow(
    val azimut: String,
    val hauteur: String,
    val rawType: String,
    val ids: List<String>
)

private val antennaAzimuthNumberRegex = Regex("""-?\d+(?:[.,]\d+)?""")

/**
 * Construit les lignes du tableau « type de panneau par azimut » à partir des bandes déjà parsées.
 * Reprend la logique du rapport PDF (buildPdfAntennaIdRows) : seuls les panneaux porteurs d'un
 * identifiant AER sont retenus, dédupliqués et regroupés par (azimut, hauteur, type).
 */
internal fun buildAntennaTypeRows(bands: List<FreqBand>): List<AntennaTypeGridRow> {
    data class Key(val azimut: String, val hauteur: String, val rawType: String)
    val grouped = linkedMapOf<Key, LinkedHashSet<String>>()
    bands.asSequence().flatMap { it.physDetails.asSequence() }.forEach { phys ->
        val id = extractFrequencyPanelId(phys) ?: return@forEach
        val display = removeFrequencyPanelIdForDisplay(phys)
        val hasColon = display.contains(":")
        val geometry = if (hasColon) display.substringAfter(":").trim() else display.trim()
        val rawType = if (hasColon) display.substringBefore(":").trim() else ""
        val azimutNumber = antennaAzimuthNumberRegex
            .find(geometry.substringBefore("("))
            ?.value
            ?.let { formatAntennaAzimuthNumber(it) }
            ?: return@forEach
        val rawHauteur = if (geometry.contains("(")) {
            geometry.substringAfter("(").substringBefore(")").trim()
        } else {
            "-"
        }
        val hauteur = formatFrequencyHeightForUnit(rawHauteur.ifBlank { "-" })
        grouped
            .getOrPut(Key("$azimutNumber°", hauteur, rawType)) { linkedSetOf() }
            .add(id)
    }
    return grouped.entries
        .sortedWith(
            compareBy<Map.Entry<Key, LinkedHashSet<String>>> { antennaAzimuthSortKey(it.key.azimut) }
                .thenByDescending { antennaHeightSortValue(it.key.hauteur) }
        )
        .map { (key, ids) ->
            AntennaTypeGridRow(
                azimut = key.azimut,
                hauteur = key.hauteur,
                rawType = key.rawType,
                ids = ids.toList()
            )
        }
}

private fun formatAntennaAzimuthNumber(value: String): String {
    val number = value.replace(',', '.').toDoubleOrNull() ?: return value.trim()
    return if (number % 1.0 == 0.0) {
        number.toInt().toString()
    } else {
        "%.1f".format(Locale.US, number).trimEnd('0').trimEnd('.')
    }
}

private fun antennaAzimuthSortKey(azimut: String): Int {
    return antennaAzimuthNumberRegex.find(azimut)?.value?.replace(',', '.')?.toDoubleOrNull()?.roundToInt() ?: 9999
}

private fun antennaHeightSortValue(height: String): Float {
    return height.replace(',', '.').replace(Regex("[^0-9.-]"), "").toFloatOrNull() ?: -1f
}

@Composable
private fun antennaTypeShortLabel(rawType: String): String {
    if (rawType.isBlank() || rawType == "-") return "-"
    val normalized = rawType.lowercase(Locale.ROOT)
    return when {
        normalized.contains("tout en 1") || normalized.contains("tout en un") ->
            stringResource(R.string.appstrings_antenna_type_all_in_one_short)
        normalized.contains("parabol") ->
            stringResource(R.string.appstrings_antenna_type_dish_short)
        else -> AnfrDisplayText.antennaType(rawType)
    }
}

@Composable
private fun AntennaTypeGridTable(
    rows: List<AntennaTypeGridRow>,
    borderColor: Color,
    headerBgColor: Color,
    subHeaderBgColor: Color
) {
    if (rows.isEmpty()) return
    val groupedByAzimut = rows.groupBy { it.azimut }.entries.toList()
    val context = LocalContext.current
    val txtPanelIdentifier = stringResource(R.string.appstrings_panel_identifier)
    val txtPanelIdentifierCopied = stringResource(R.string.appstrings_panel_identifier_copied)
    val txtCopy = stringResource(R.string.appstrings_copy)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Titre du tableau 3
            Box(
                modifier = Modifier.fillMaxWidth().background(headerBgColor).padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.appstrings_aer_id_title),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider(color = borderColor)

            // En-têtes des colonnes
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(subHeaderBgColor),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.appstrings_col_azimuth),
                    modifier = Modifier.weight(0.8f).padding(6.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                VerticalDivider(color = borderColor)
                Text(
                    stringResource(R.string.appstrings_col_height),
                    modifier = Modifier.weight(0.9f).padding(6.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                VerticalDivider(color = borderColor)
                Text(
                    stringResource(R.string.appstrings_col_type),
                    modifier = Modifier.weight(1.2f).padding(6.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                VerticalDivider(color = borderColor)
                Text(
                    stringResource(R.string.appstrings_col_id),
                    modifier = Modifier.weight(1.3f).padding(6.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
            HorizontalDivider(color = borderColor)

            // Lignes groupées par azimut (cellule azimut fusionnée)
            groupedByAzimut.forEachIndexed { azimutIndex, (azimut, azimutRows) ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier.weight(0.8f).fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            azimut,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                    VerticalDivider(color = borderColor)

                    Column(modifier = Modifier.weight(3.4f)) { // 0.9 + 1.2 + 1.3 = 3.4
                        azimutRows.forEachIndexed { rowIndex, row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    row.hauteur,
                                    modifier = Modifier.weight(0.9f).padding(vertical = 8.dp, horizontal = 4.dp),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                                VerticalDivider(color = borderColor)
                                Text(
                                    antennaTypeShortLabel(row.rawType),
                                    modifier = Modifier.weight(1.2f).padding(vertical = 8.dp, horizontal = 4.dp),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                VerticalDivider(color = borderColor)
                                Column(
                                    modifier = Modifier.weight(1.3f).padding(vertical = 4.dp, horizontal = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    row.ids.forEach { id ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = id,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                lineHeight = 14.sp
                                            )
                                            IconButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText(txtPanelIdentifier, id))
                                                    Toast.makeText(context, txtPanelIdentifierCopied, Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = txtCopy,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (rowIndex < azimutRows.lastIndex) HorizontalDivider(color = borderColor)
                        }
                    }
                }
                if (azimutIndex < groupedByAzimut.lastIndex) HorizontalDivider(color = borderColor)
            }
        }
    }
}
