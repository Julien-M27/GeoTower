package fr.geotower.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.utils.AppStrings
import fr.geotower.utils.AppConfig
import fr.geotower.ui.screens.emitters.formatDateToFrench

@Composable
fun SiteFrequenciesBlock(
    info: LocalisationEntity,
    technique: TechniqueEntity?,
    formattedAzimuths: String,
    cardBgColor: Color,
    blockShape: Shape
) {
    val txtFrequenciesTitle = AppStrings.frequenciesTitle
    val txtBandsNotSpecified = AppStrings.bandsNotSpecified
    val txtInService = AppStrings.inService
    val txtTechnically = AppStrings.technically
    val txtUnknownStatus = AppStrings.unknownStatus
    val txtProjectApproved = AppStrings.projectApproved

    // ✅ NOUVELLES TRADUCTIONS RÉCUPÉRÉES
    val txtUnknown = AppStrings.unknown
    val txtActivatedOn = AppStrings.activatedOn
    val txtDateNotSpecifiedAnfr = AppStrings.dateNotSpecifiedAnfr
    val txtAzimuthNotSpecified = AppStrings.azimuthNotSpecified

    val rawFreqs = technique?.detailsFrequences ?: info.frequences

    // ✅ ON INTÈGRE NOS NOUVEAUX FILTRES DYNAMIQUES
    val parsedBands = remember(
        rawFreqs,
        AppConfig.siteShowTechno2G.value, AppConfig.siteF2G_900.value, AppConfig.siteF2G_1800.value,
        AppConfig.siteShowTechno3G.value, AppConfig.siteF3G_900.value, AppConfig.siteF3G_2100.value,
        AppConfig.siteShowTechno4G.value, AppConfig.siteF4G_700.value, AppConfig.siteF4G_800.value, AppConfig.siteF4G_900.value, AppConfig.siteF4G_1800.value, AppConfig.siteF4G_2100.value, AppConfig.siteF4G_2600.value,
        AppConfig.siteShowTechno5G.value, AppConfig.siteF5G_700.value, AppConfig.siteF5G_2100.value, AppConfig.siteF5G_3500.value, AppConfig.siteF5G_26000.value,
        AppConfig.siteShowTechnoFH.value
    ) {
        parseAndSortFrequencies(rawFreqs, txtUnknown, txtAzimuthNotSpecified).filter { band ->
            when (band.gen) {
                5 -> AppConfig.siteShowTechno5G.value && when (band.value) {
                    700 -> AppConfig.siteF5G_700.value
                    2100 -> AppConfig.siteF5G_2100.value
                    3500 -> AppConfig.siteF5G_3500.value
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
    }

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
                parsedBands.forEachIndexed { index, band ->
                    val (statusColor, statusText) = when {
                        band.status.contains("En service", true) -> Pair(Color(0xFF4CAF50), txtInService)
                        band.status.contains("Techniquement", true) -> Pair(Color(0xFF4CAF50), txtTechnically)
                        band.status.contains("Approuvé", true) -> Pair(Color(0xFF2196F3), txtProjectApproved)
                        else -> Pair(Color.Gray, txtUnknownStatus)
                    }

                    val dateFormatted = formatDateToFrench(band.date)
                    // ✅ TEXTES ENTIÈREMENT DYNAMIQUES
                    val dateDisplay = if (dateFormatted.isNotBlank() && dateFormatted != "-") {
                        "$txtActivatedOn$dateFormatted"
                    } else {
                        txtDateNotSpecifiedAnfr
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = if (index == parsedBands.lastIndex) 0.dp else 12.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "• ${band.rawFreq.substringBefore(":").trim()}",
                                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp, // ✅ Nom de la fréquence plus petit (avant: 14.sp)
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                                )
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
                                val preciseFreqs = band.rawFreq.substringAfter(":", "").trim()

                                // ✅ ÉTAPE 1 : On vérifie si le switch maître du spectre est activé
                                if (AppConfig.siteShowSpectrum.value && preciseFreqs.isNotBlank() && preciseFreqs != band.rawFreq.trim()) {

                                    var totalBandwidth = 0.0
                                    var detectedUnit = "MHz" // Unité par défaut

                                    val regex = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*-\s*([0-9]+(?:[.,][0-9]+)?)\s*([a-zA-Z]*Hz)?""")

                                    val detailedFreqs = regex.replace(preciseFreqs) { match ->
                                        val n1 = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0
                                        val n2 = match.groupValues[2].replace(',', '.').toDoubleOrNull() ?: 0.0
                                        val unit = match.groupValues[3].takeIf { it.isNotBlank() } ?: "MHz"

                                        detectedUnit = unit // On met à jour l'unité courante

                                        val diff = kotlin.math.abs(n2 - n1)
                                        totalBandwidth += diff

                                        val diffStr = if (diff % 1.0 == 0.0) diff.toInt().toString() else String.format(java.util.Locale.US, "%.1f", diff)

                                        "${match.groupValues[1]}-${match.groupValues[2]} $unit [$diffStr $unit]".trim()
                                    }

                                    // ✅ ÉTAPE 2 : On affiche le détail par bande uniquement si son switch est actif
                                    if (AppConfig.siteShowSpectrumBand.value) {
                                        Text(text = "${AppStrings.spectrumByBand} : $detailedFreqs", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }

                                    // ✅ ÉTAPE 3 : On affiche le spectre total uniquement si son switch est actif et qu'il y a une valeur
                                    if (AppConfig.siteShowSpectrumTotal.value && totalBandwidth > 0) {
                                        val totalStr = if (totalBandwidth % 1.0 == 0.0) totalBandwidth.toInt().toString() else String.format(java.util.Locale.US, "%.1f", totalBandwidth)

                                        // On ajoute un petit espace seulement si le texte du dessus est affiché
                                        if (AppConfig.siteShowSpectrumBand.value) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                        }

                                        Text(text = "${AppStrings.totalspectrum} : $totalStr $detectedUnit", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }

                                    // Espacement final uniquement si l'un des deux éléments a été affiché
                                    if (AppConfig.siteShowSpectrumBand.value || (AppConfig.siteShowSpectrumTotal.value && totalBandwidth > 0)) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }

                                Text(text = dateDisplay, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

                                // Affichage de tous les panneaux associés à cette fréquence
                                if (band.physDetails.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    band.physDetails.forEach { physDetail ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
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
                                            val typePart = if (physDetail.contains(":")) physDetail.substringBefore(":").trim() else physDetail.trim()
                                            val restPart = if (physDetail.contains(":")) physDetail.substringAfter(":").trim() else ""

                                            val translatedType = AppStrings.translateAntennaType(typePart)
                                            // ✅ SÉCURITÉ : Si la traduction renvoie du vide, on force l'affichage du mot original (ex: "Panneau")
                                            val safeType = if (translatedType.isNotBlank()) translatedType else typePart

                                            val finalPhysText = if (restPart.isNotEmpty()) "$safeType : $restPart" else safeType

                                            Text(
                                                text = finalPhysText, // Ex: "Panel : 120° (15.5m)"
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp
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

data class FreqBand(
    val rawFreq: String,
    val status: String,
    val date: String,
    val physDetails: List<String>,
    val gen: Int,
    val value: Int
)

// ✅ NOUVEAU : On passe les traductions en paramètres pour plus de sécurité
// ✅ NOUVEAU : On passe les traductions en paramètres
fun parseAndSortFrequencies(freqStr: String?, txtUnknown: String, txtAzimuthNotSpecified: String): List<FreqBand> {
    if (freqStr.isNullOrBlank()) return emptyList()

    val parsedLines = freqStr.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    val tempMap = mutableMapOf<String, Pair<FreqBand, MutableSet<String>>>()

    for (line in parsedLines) {
        val parts = line.split("|").map { it.trim() }
        val rawFrequencies = parts.getOrNull(0) ?: ""
        val status = parts.getOrNull(1) ?: txtUnknown // ✅ TRADUIT
        val dateStr = parts.getOrNull(2) ?: ""
        val phys = parts.getOrNull(3) ?: ""

        val systemName = rawFrequencies.substringBefore(":").trim().uppercase()

        if (!tempMap.containsKey(systemName)) {
            val g = when {
                systemName.contains("5G", true) || systemName.contains("NR", true) -> 5
                systemName.contains("4G", true) || systemName.contains("LTE", true) -> 4
                systemName.contains("3G", true) || systemName.contains("UMTS", true) -> 3
                systemName.contains("2G", true) || systemName.contains("GSM", true) -> 2
                else -> 0
            }
            val freqValue = Regex("\\d+").findAll(systemName).map { it.value.toInt() }.maxOrNull() ?: 0

            val band = FreqBand(rawFrequencies, status, dateStr, emptyList(), g, freqValue)
            tempMap[systemName] = Pair(band, mutableSetOf())
        }

        // ✅ TRADUIT
        if (phys.isNotBlank() && phys != "Azimut non spécifié" && phys != txtAzimuthNotSpecified) {
            tempMap[systemName]!!.second.add(phys)
        }
    }

    // ✅ TRI SELON L'ORDRE PERSONNALISÉ
    // ✅ APPLICATION DE L'ORDRE PERSONNALISÉ
    return tempMap.values.map { (band, physSet) ->
        band.copy(physDetails = physSet.toList().sorted())
    }.sortedWith(compareBy(
        // 1. On trie par Technologie (selon ton ordre personnalisé)
        { AppConfig.siteTechnoOrder.value.indexOf(if(it.gen == 5) "5G" else if(it.gen == 4) "4G" else if(it.gen == 3) "3G" else if(it.gen == 2) "2G" else "FH") },
        // 2. On trie par Fréquence à l'intérieur de la technologie
        { band ->
            val orderList = when(band.gen) {
                5 -> AppConfig.siteFreqOrder5G.value
                4 -> AppConfig.siteFreqOrder4G.value
                3 -> AppConfig.siteFreqOrder3G.value
                2 -> AppConfig.siteFreqOrder2G.value
                else -> emptyList()
            }
            val idx = orderList.indexOf(band.value.toString())
            if (idx == -1) 999 else idx // Si fréquence inconnue, on la met à la fin
        }
    ))
}