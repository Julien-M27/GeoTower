package fr.geotower.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.R
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.TechniqueEntity // ✅ NOUVEL IMPORT
import fr.geotower.utils.formatDateToFrench // ✅ Formatage localisé des dates
import fr.geotower.utils.AppConfig
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.OperatorLogos
import androidx.compose.ui.res.stringResource

@Composable
fun OperatorsListSection(
    antennas: List<LocalisationEntity>,
    techniques: Map<String, TechniqueEntity>,
    hsDataMap: Map<String, fr.geotower.data.models.SiteHsEntity> = emptyMap(), // 🚨 Changé ici
    cardBgColor: Color,
    blockShape: Shape,
    useOneUi: Boolean,
    priorityOperatorKey: String? = null,
    onAntennaClick: (String) -> Unit
) {
    // ✅ 1. LECTURE DES PARAMÈTRES SPÉCIFIQUES AU DÉTAIL DU SITE
    val s2G = AppConfig.siteShowTechno2G.value && (AppConfig.siteF2G_900.value || AppConfig.siteF2G_1800.value)
    val s3G = AppConfig.siteShowTechno3G.value && (AppConfig.siteF3G_900.value || AppConfig.siteF3G_2100.value)
    val s4G = AppConfig.siteShowTechno4G.value && (AppConfig.siteF4G_700.value || AppConfig.siteF4G_800.value || AppConfig.siteF4G_900.value || AppConfig.siteF4G_1800.value || AppConfig.siteF4G_2100.value || AppConfig.siteF4G_2600.value)
    val s5G = AppConfig.siteShowTechno5G.value && (AppConfig.siteF5G_700.value || AppConfig.siteF5G_2100.value || AppConfig.siteF5G_3500.value || AppConfig.siteF5G_26000.value)
    val sFh = AppConfig.siteShowTechnoFH.value

    // ✅ 2. ON FILTRE LES OPÉRATEURS POUR CACHER CEUX SANS TECHNO ACTIVE
    val filteredAntennas = antennas.filter { antenna ->
        val tech = techniques[antenna.idAnfr]
        val rawTechs = (tech?.technologies?.takeIf { it.isNotBlank() } ?: antenna.frequences ?: "").uppercase()

        val has2G = rawTechs.contains("2G")
        val has3G = rawTechs.contains("3G")
        val has4G = rawTechs.contains("4G")
        val has5G = rawTechs.contains("5G")
        val hasFH = rawTechs.contains("FH")

        val hasAnyKnown = has2G || has3G || has4G || has5G || hasFH

        if (!hasAnyKnown) {
            true // On le garde par sécurité si la donnée est inconnue
        } else {
            (has2G && s2G) || (has3G && s3G) || (has4G && s4G) || (has5G && s5G) || (hasFH && sFh)
        }
    }

    // Si on a tout masqué, on ne dessine rien du tout
    if (filteredAntennas.isEmpty()) return

    val defaultOperatorKey = OperatorColors.keyFor(AppConfig.defaultOperator.value)
    val hasPriorityMatch = priorityOperatorKey != null && filteredAntennas.any { antenna ->
        priorityOperatorKey in OperatorColors.keysFor(antenna.operateur)
    }
    val sortedAntennas = filteredAntennas.sortedBy { antenna ->
        val operatorKeys = OperatorColors.keysFor(antenna.operateur)
        when {
            priorityOperatorKey != null && priorityOperatorKey in operatorKeys -> 0
            defaultOperatorKey != null && defaultOperatorKey in operatorKeys -> 1
            else -> 2
        }
    }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.operator_count, filteredAntennas.size), // ✅ Utilise la taille filtrée
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (!useOneUi) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        }
        sortedAntennas.forEach { antenna ->
            val operatorKeys = OperatorColors.keysFor(antenna.operateur)
            OperatorDetailItem(
                antenna = antenna,
                technique = techniques[antenna.idAnfr],
                hsEntity = hsDataMap[antenna.idAnfr], // 🚨 Changé ici
                cardBgColor = cardBgColor,
                blockShape = blockShape,
                useOneUi = useOneUi,
                isMuted = hasPriorityMatch && priorityOperatorKey !in operatorKeys,
                onClick = { onAntennaClick(antenna.idAnfr) }
            )
            if (!useOneUi) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun OperatorDetailItem(
    antenna: LocalisationEntity,
    technique: TechniqueEntity?,
    hsEntity: fr.geotower.data.models.SiteHsEntity? = null, // 🚨 Changé ici
    cardBgColor: Color,
    blockShape: Shape,
    useOneUi: Boolean,
    isMuted: Boolean = false,
    onClick: () -> Unit
) {
    val modifier = if (useOneUi) {
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(blockShape).background(cardBgColor).clickable(onClick = onClick).padding(16.dp)
    } else {
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp)
    }

    Column(modifier = modifier.alpha(if (isMuted) 0.42f else 1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val opName = antenna.operateur ?: stringResource(R.string.appstrings_unknown)
            val logoRes = getLocalLogoRes(opName)

            if (logoRes != null) {
                Image(painter = painterResource(id = logoRes), contentDescription = null, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)))
            } else {
                Box(modifier = Modifier.size(60.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = opName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)

                // ✅ LECTURE DES FILTRES SPÉCIFIQUES AU DÉTAIL DU SITE
                val s2G = AppConfig.siteShowTechno2G.value && (AppConfig.siteF2G_900.value || AppConfig.siteF2G_1800.value)
                val s3G = AppConfig.siteShowTechno3G.value && (AppConfig.siteF3G_900.value || AppConfig.siteF3G_2100.value)
                val s4G = AppConfig.siteShowTechno4G.value && (AppConfig.siteF4G_700.value || AppConfig.siteF4G_800.value || AppConfig.siteF4G_900.value || AppConfig.siteF4G_1800.value || AppConfig.siteF4G_2100.value || AppConfig.siteF4G_2600.value)
                val s5G = AppConfig.siteShowTechno5G.value && (AppConfig.siteF5G_700.value || AppConfig.siteF5G_2100.value || AppConfig.siteF5G_3500.value || AppConfig.siteF5G_26000.value)
                val sFh = AppConfig.siteShowTechnoFH.value

                // ✅ AFFICHAGE DES VRAIES TECHNOLOGIES FILTRÉES
                val rawTechs = technique?.technologies?.takeIf { it.isNotBlank() } ?: antenna.frequences
                // ✅ On envoie les filtres au formateur
                val realTechs = formatSiteTechnologies(rawTechs, stringResource(R.string.appstrings_unknown), s2G, s3G, s4G, s5G, sFh)
                Text(text = realTechs, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)

            } // <-- Fin de la Column(weight = 1f)

            // La flèche reste toute seule à droite
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } // <-- Fin de la Row contenant le logo, le titre et la flèche

        // 🚨 NOUVEAU PLACEMENT : Sous le logo complet, et au-dessus des dates (Implémentation)
        if (hsEntity != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 12.dp) // Espace avec le logo au-dessus
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Warning,
                    contentDescription = stringResource(R.string.appstrings_outage_attention_desc),
                    tint = Color(0xFFE53935),
                    modifier = Modifier.size(16.dp).padding(end = 6.dp)
                )
                Text(
                    text = formatOutageDetails(hsEntity),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE53935),
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ✅ AFFICHAGE DES DATES AVEC FORMATAGE FRANÇAIS
        val dateImp = technique?.dateImplantation?.let { formatDateToFrench(it) } ?: "-"
        val dateSer = technique?.dateService?.let { formatDateToFrench(it) } ?: "-"
        val dateMod = technique?.dateModif?.let { formatDateToFrench(it) } ?: "-"

        // ✅ On utilise la ressource Android officielle
        val txtModif = stringResource(R.string.appstrings_last_modification)

        // ✅ PLUS DE " : " EN TROP !
        DateLine(stringResource(R.string.appstrings_implementation), dateImp)
        DateLine(stringResource(R.string.appstrings_activated_on), dateSer)

        if (dateMod != "-") {
            DateLine(txtModif, dateMod)
        }
    }
}

@Composable
fun DateLine(label: String, value: String) {
    Text(
        text = buildAnnotatedString {
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append(label) }
            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(value) }
        },
        fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
private fun formatSiteTechnologies(
    tech: String?,
    txtUnknown: String,
    s2G: Boolean, s3G: Boolean, s4G: Boolean, s5G: Boolean, sFh: Boolean
): String {
    if (tech.isNullOrBlank()) return txtUnknown
    val parts = tech.split(Regex("[/,\\-]")).map { it.trim().uppercase() }.filter { it.isNotEmpty() }

    // ✅ On efface le texte de la technologie si elle est décochée
    val filtered = parts.filter { t ->
        var keep = true
        if (t.contains("2G") && !s2G) keep = false
        if (t.contains("3G") && !s3G) keep = false
        if (t.contains("4G") && !s4G) keep = false
        if (t.contains("5G") && !s5G) keep = false
        if (t.contains("FH") && !sFh) keep = false
        keep
    }

    // Si tout a été masqué par les filtres, on affiche "Non spécifié"
    if (filtered.isEmpty()) return stringResource(R.string.appstrings_not_specified)

    return filtered.sortedDescending().joinToString(" - ")
}

private fun getLocalLogoRes(opName: String): Int? {
    return OperatorLogos.drawableRes(opName)
}

@Composable
fun formatOutageDetails(hsData: fr.geotower.data.models.SiteHsEntity): String {
    // 1. Traduction du texte détaillé de l'API (ex: "Incident en cours")
    val detailTranslated = when (hsData.detail?.lowercase()) {
        "incident en cours" -> stringResource(R.string.appstrings_api_detail_incident)
        "travaux de maintenance" -> stringResource(R.string.appstrings_api_detail_maintenance)
        "intervention technique" -> stringResource(R.string.appstrings_outage_reason_technical)
        "null" -> null // 🚨 ON INTERCEPTE ET ON DÉTRUIT LE FAUX TEXTE "null"
        else -> hsData.detail
    }

    // 2. Traduction du code court ("INT", "MAINT")
    val reasonTranslated = when (hsData.raison?.uppercase()) {
        "MAINT" -> stringResource(R.string.appstrings_outage_reason_maintenance)
        "INT" -> stringResource(R.string.appstrings_outage_reason_incident)
        "NULL" -> null // 🚨 PAREIL ICI
        else -> hsData.raison
    }

    // 3. On choisit le détail en priorité, sinon le code court, sinon "Inconnu"
    val displayReason = detailTranslated?.takeIf { it.isNotBlank() && it.lowercase() != "null" }
        ?: reasonTranslated?.takeIf { it.isNotBlank() && it.lowercase() != "null" }
        ?: stringResource(R.string.appstrings_unknown_outage_reason)

    val statusDegraded = stringResource(R.string.appstrings_outage_status_degraded)
    val statusHs = stringResource(R.string.appstrings_outage_status_hs)
    val voiceLabel = stringResource(R.string.appstrings_outage_voice)
    val dataLabel = stringResource(R.string.appstrings_outage_data)

    // 2. Traduction simple (Uniquement pour ce qui est en panne)
    fun getStatusText(code: String?): String {
        return when (code?.uppercase()) {
            "DE" -> statusDegraded
            "HS" -> statusHs
            else -> code ?: "-"
        }
    }

    // 3. Filtrage : On ne garde que les services en panne (HS) ou dégradés (DE)
    val activeOutages = mutableListOf<String>()

    val voixCode = hsData.voixGlobal?.uppercase()
    if (voixCode == "HS" || voixCode == "DE") {
        activeOutages.add("$voiceLabel : ${getStatusText(voixCode)}")
    }

    val dataCode = hsData.dataGlobal?.uppercase()
    if (dataCode == "HS" || dataCode == "DE") {
        activeOutages.add("$dataLabel : ${getStatusText(dataCode)}")
    }

    // 4. Construction de la phrase finale (sans la date)
    val detailsStr = if (activeOutages.isNotEmpty()) {
        " (${activeOutages.joinToString(", ")})"
    } else {
        "" // Si aucun service spécifique n'est listé en panne, on n'affiche pas de parenthèses
    }

    return "$displayReason$detailsStr"
}
