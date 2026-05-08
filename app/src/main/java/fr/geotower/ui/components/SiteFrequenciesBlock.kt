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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.utils.AppStrings
import fr.geotower.utils.AppConfig
import fr.geotower.ui.screens.emitters.formatDateToFrench
import kotlin.math.roundToInt

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
                // ✅ NOUVEAU : On vérifie l'option d'affichage
                if (AppConfig.siteFreqGridDisplay.value) {
                    FrequenciesGridView(
                        parsedBands = parsedBands,
                        txtUnknown = txtUnknown,
                        txtDateNotSpecifiedAnfr = txtDateNotSpecifiedAnfr,
                        txtInService = txtInService,
                        txtTechnically = txtTechnically,
                        txtProjectApproved = txtProjectApproved,
                        txtUnknownStatus = txtUnknownStatus
                    )
                } else {
                    // 👇 TON CODE ACTUEL COMMENCE ICI 👇
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
                                // ✅ NOUVEAU : Formatage propre "4G 700" ici aussi
                                val technoName = displayFrequencyBandLabel(band)

                                Text(
                                    text = "• $technoName",
                                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
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
                                            val typePart =
                                                if (physDetail.contains(":")) physDetail.substringBefore(
                                                    ":"
                                                ).trim() else physDetail.trim()
                                            val restPart =
                                                if (physDetail.contains(":")) physDetail.substringAfter(
                                                    ":"
                                                ).trim() else ""

                                            val translatedType =
                                                AppStrings.translateAntennaType(typePart)
                                            // ✅ SÉCURITÉ : Si la traduction renvoie du vide, on force l'affichage du mot original (ex: "Panneau")
                                            val safeType =
                                                if (translatedType.isNotBlank()) translatedType else typePart

                                            val finalPhysText =
                                                if (restPart.isNotEmpty()) "$safeType : $restPart" else safeType

                                            Text(
                                                text = formatFrequencyPhysicalDetailsForUnit(finalPhysText), // Ex: "Panel : 120° (15.5m)"
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

private fun bandEquivalentLabel(gen: Int, value: Int): String? {
    return when (gen) {
        5 -> when (value) {
            700 -> "N28"
            800 -> "N20"
            900 -> "N8"
            1800 -> "N3"
            2100 -> "N1"
            2600 -> "N7"
            3500 -> "N78"
            26000 -> "N258"
            else -> null
        }
        4 -> when (value) {
            700 -> "B28"
            800 -> "B20"
            900 -> "B8"
            1800 -> "B3"
            2100 -> "B1"
            2600 -> "B7"
            3500 -> "B42"
            else -> null
        }
        3 -> when (value) {
            900 -> "B8"
            2100 -> "B1"
            else -> null
        }
        2 -> when (value) {
            900 -> "GSM 900"
            1800 -> "DCS 1800"
            else -> null
        }
        else -> null
    }
}

private fun displayFrequencyBandLabel(band: FreqBand): String {
    if (band.gen in 2..5 && band.value > 0) {
        val base = "${band.gen}G ${band.value} MHz"
        return bandEquivalentLabel(band.gen, band.value)?.let { "$base ($it)" } ?: base
    }
    return band.rawFreq.substringBefore(":").trim().ifBlank { band.rawFreq }
}

private fun formatFrequencyPhysicalDetailsForUnit(text: String): String {
    if (AppConfig.distanceUnit.intValue != 1) return text
    return frequencyHeightMetersRegex.replace(text) { match ->
        val meters = match.groupValues[1].replace(',', '.').toDoubleOrNull()
        "(${formatFrequencyHeightMeters(meters)})"
    }
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
    txtUnknownStatus: String
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
                    AppStrings.emittersTableTitle,
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
                    AppStrings.colTechno,
                    modifier = Modifier.weight(1.3f).padding(8.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                VerticalDivider(color = borderColor)
                Text(
                    AppStrings.colService,
                    modifier = Modifier.weight(1f).padding(8.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                VerticalDivider(color = borderColor)
                Text(
                    AppStrings.colState,
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
                val dateFormatted = fr.geotower.ui.screens.emitters.formatDateToFrench(band.date)
                val dateDisplay =
                    if (dateFormatted.isNotBlank() && dateFormatted != "-") dateFormatted else txtDateNotSpecifiedAnfr

                val statusColor = when {
                    band.status.contains("En service", true) -> Color(0xFF4CAF50) // Vert
                    band.status.contains(
                        "Techniquement",
                        true
                    ) -> MaterialTheme.colorScheme.primary // Bleu/Primaire
                    band.status.contains("Approuvé", true) -> Color(0xFF2196F3) // Bleu clair
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val statusText = when {
                    band.status.contains("En service", true) -> txtInService
                    band.status.contains("Techniquement", true) -> txtTechnically
                    band.status.contains("Approuvé", true) -> txtProjectApproved
                    else -> txtUnknownStatus
                }

                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
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
        val value: Int
    )
    val groupedAntennas = mutableMapOf<String, MutableMap<String, MutableList<AntennaRow>>>()

    parsedBands.forEach { band ->
        // ✅ NOUVEAU : Formatage propre "4G 700"
        val technoName = displayFrequencyBandLabel(band)
        val bandEquivalent = null

        val freqs = band.rawFreq.substringAfter(":", "").trim()

        // Remplacement élégant des tirets par des "à" comme sur l'image modèle
        val displayFreqs = if (freqs.isNotBlank()) {
            Regex("""([0-9]+(?:[.,][0-9]+)?)\s*-\s*([0-9]+(?:[.,][0-9]+)?)\s*([a-zA-Z]*Hz)?""")
                .replace(freqs) { match ->
                    val unit = match.groupValues[3].takeIf { it.isNotBlank() }
                    buildString {
                        append(match.groupValues[1])
                        append(" à ")
                        append(match.groupValues[2])
                        if (unit != null) append(" ").append(unit)
                    }
                }
                .replace(Regex("""\s*,\s*"""), ", ")
                .replace(Regex("""\s+"""), " ")
                .trim()
        } else {
            "-"
        }

        if (band.physDetails.isEmpty()) {
            groupedAntennas.getOrPut("-") { mutableMapOf() }
                .getOrPut("-") { mutableListOf() }
                .add(AntennaRow(technoName, bandEquivalent, displayFreqs, band.gen, band.value))
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
                    .add(AntennaRow(technoName, bandEquivalent, displayFreqs, band.gen, band.value))
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
                    AppStrings.antennasTableTitle,
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
                    AppStrings.colAzimuth,
                    modifier = Modifier.weight(0.8f).padding(6.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                VerticalDivider(color = borderColor)
                Text(
                    AppStrings.colHeight,
                    modifier = Modifier.weight(1f).padding(6.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                VerticalDivider(color = borderColor)
                Text(
                    AppStrings.colBand,
                    modifier = Modifier.weight(1f).padding(6.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                VerticalDivider(color = borderColor)
                Text(
                    AppStrings.colFreqs,
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

                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    // 1. Cellule fusionnée : Azimut (englobe toutes les hauteurs)
                    Box(
                        modifier = Modifier.weight(0.8f).fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            azimutDisplay,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.padding(4.dp)
                        )
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
                                                .height(IntrinsicSize.Min),
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
}
