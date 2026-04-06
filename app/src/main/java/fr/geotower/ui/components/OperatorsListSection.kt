package fr.geotower.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import fr.geotower.ui.screens.emitters.formatDateToFrench // ✅ IMPORT DU FORMATAGE DES DATES
import fr.geotower.utils.AppStrings
import fr.geotower.utils.AppConfig

@Composable
fun OperatorsListSection(
    antennas: List<LocalisationEntity>,
    techniques: Map<String, TechniqueEntity>,
    cardBgColor: Color,
    blockShape: Shape,
    useOneUi: Boolean,
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

    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = AppStrings.operatorCount(filteredAntennas.size), // ✅ Utilise la taille filtrée
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        filteredAntennas.forEach { antenna ->
            OperatorDetailItem(
                antenna = antenna,
                technique = techniques[antenna.idAnfr],
                cardBgColor = cardBgColor,
                blockShape = blockShape,
                useOneUi = useOneUi,
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
    technique: TechniqueEntity?, // ✅ NOUVEAU PARAMÈTRE
    cardBgColor: Color,
    blockShape: Shape,
    useOneUi: Boolean,
    onClick: () -> Unit
) {
    val modifier = if (useOneUi) {
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(blockShape).background(cardBgColor).clickable(onClick = onClick).padding(16.dp)
    } else {
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp)
    }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val opName = antenna.operateur ?: "Inconnu"
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
                val realTechs = formatSiteTechnologies(rawTechs, AppStrings.unknown, s2G, s3G, s4G, s5G, sFh)
                Text(text = realTechs, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ✅ AFFICHAGE DES DATES AVEC FORMATAGE FRANÇAIS
        val dateImp = technique?.dateImplantation?.let { formatDateToFrench(it) } ?: "-"
        val dateSer = technique?.dateService?.let { formatDateToFrench(it) } ?: "-"
        val dateMod = technique?.dateModif?.let { formatDateToFrench(it) } ?: "-"

        // ✅ On utilise la traduction officielle d'AppStrings
        val txtModif = AppStrings.lastModification

        // ✅ PLUS DE " : " EN TROP !
        DateLine(AppStrings.implementation, dateImp)
        DateLine(AppStrings.activatedOn, dateSer)

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
    if (filtered.isEmpty()) return AppStrings.notSpecified

    return filtered.sortedDescending().joinToString(" - ")
}

private fun getLocalLogoRes(opName: String): Int? {
    return when {
        opName.contains("ORANGE", true) -> R.drawable.logo_orange
        opName.contains("SFR", true) -> R.drawable.logo_sfr
        opName.contains("BOUYGUES", true) -> R.drawable.logo_bouygues
        opName.contains("FREE", true) -> R.drawable.logo_free
        else -> null
    }
}