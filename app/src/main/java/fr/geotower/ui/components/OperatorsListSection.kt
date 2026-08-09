package fr.geotower.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import androidx.compose.ui.unit.sp
import fr.geotower.R
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.TechniqueEntity // ✅ NOUVEL IMPORT
import fr.geotower.utils.emitterHeightsMeters // ✅ Hauteurs lues dans details_frequences
import fr.geotower.utils.formatDateToFrench // ✅ Formatage localisé des dates
import fr.geotower.utils.AppConfig
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.OperatorLogos
import androidx.compose.ui.res.pluralStringResource
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
    activeOperatorKeys: Set<String>? = null,
    // Mode simplifié : chaque ligne se déplie sur la fiche de l'opérateur au lieu d'ouvrir un
    // écran. Le contenu n'est composé que lorsqu'il est visible — sur un pylône mutualisé,
    // charger les quatre opérateurs d'un coup quadruplerait le temps d'ouverture de la fiche.
    expandable: Boolean = false,
    initialExpandedAntennaId: String? = null,
    expandedContent: @Composable (LocalisationEntity) -> Unit = {},
    // Lance l'export PDF multi-opérateurs de la fiche support (une page par station). Nul quand le
    // bloc « Partager » du support est masqué : sans lui, l'export n'existe pas.
    onShareAllOperators: (() -> Unit)? = null,
    onAntennaClick: (String) -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    // ✅ 1. LECTURE DES PARAMÈTRES SPÉCIFIQUES AU DÉTAIL DU SITE
    val s2G = AppConfig.siteShowTechno2G.value && (AppConfig.siteF2G_900.value || AppConfig.siteF2G_1800.value)
    val s3G = AppConfig.siteShowTechno3G.value && (AppConfig.siteF3G_900.value || AppConfig.siteF3G_2100.value)
    val s4G = AppConfig.siteShowTechno4G.value && (AppConfig.siteF4G_700.value || AppConfig.siteF4G_800.value || AppConfig.siteF4G_900.value || AppConfig.siteF4G_1800.value || AppConfig.siteF4G_2100.value || AppConfig.siteF4G_2600.value)
    val s5G = AppConfig.siteShowTechno5G.value && (AppConfig.siteF5G_700.value || AppConfig.siteF5G_1400.value || AppConfig.siteF5G_2100.value || AppConfig.siteF5G_3500.value || AppConfig.siteF5G_4200.value || AppConfig.siteF5G_26000.value)
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
    val activeKeys = activeOperatorKeys
    val hasPriorityMatch = priorityOperatorKey != null && filteredAntennas.any { antenna ->
        priorityOperatorKey in OperatorColors.keysFor(antenna.operateur)
    }
    val sortedAntennas = filteredAntennas.sortedBy { antenna ->
        val operatorKeys = OperatorColors.keysFor(antenna.operateur)
        when {
            activeKeys != null && operatorKeys.any { it in activeKeys } -> 0
            priorityOperatorKey != null && priorityOperatorKey in operatorKeys -> 0
            defaultOperatorKey != null && defaultOperatorKey in operatorKeys -> 1
            else -> 2
        }
    }

    // Tout est replié à l'ouverture : c'est l'utilisateur qui déplie ce qu'il veut voir, un
    // opérateur à la fois ou tout d'un coup avec la bascule ci-dessous. Seule exception, un
    // opérateur explicitement visé (notification, widget, lien profond, historique) s'ouvre seul.
    val targetedAntennaId = initialExpandedAntennaId
        ?.takeIf { id -> sortedAntennas.any { it.idAnfr == id } }
    // Plusieurs opérateurs peuvent rester ouverts en même temps : on garde une liste d'identifiants
    // et non un seul. La clé du remember la remet à zéro au changement de pylône, sinon on
    // garderait déplié un opérateur qui n'est plus dans la liste.
    var expandedAntennaIds by rememberSaveable(sortedAntennas.first().idAnfr) {
        mutableStateOf(listOfNotNull(targetedAntennaId))
    }
    val allExpanded = expandable && sortedAntennas.all { it.idAnfr in expandedAntennaIds }

    // La fiche insérée est un écran entier : la composer dans la même image que la fiche support
    // fait sauter l'ouverture. Utile uniquement quand un opérateur est déjà déplié à l'arrivée
    // (cas d'un opérateur explicitement visé) ; ensuite le drapeau reste vrai et les dépliages
    // manuels sont immédiats.
    var deferredContentReady by remember(sortedAntennas.first().idAnfr) { mutableStateOf(false) }
    LaunchedEffect(sortedAntennas.first().idAnfr) {
        withFrameNanos { }
        deferredContentReady = true
    }

    // En mode One UI, chaque opérateur est une carte : elle doit respirer comme les autres cartes
    // de la page (16 dp partout). En mode Material, c'est au contraire une liste compacte séparée
    // par des traits — coller les lignes entre elles est la mise en page voulue.
    val cardSpacing = if (useOneUi) sizing.spacing(16.dp) else 0.dp
    val listItemPadding = if (useOneUi) 0.dp else sizing.spacing(8.dp)

    Column(verticalArrangement = Arrangement.spacedBy(cardSpacing)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sizing.spacing(16.dp))
                .padding(vertical = listItemPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.operator_count, filteredAntennas.size), // ✅ Utilise la taille filtrée
                color = MaterialTheme.colorScheme.primary,
                fontSize = sizing.text(13.sp),
                modifier = Modifier.weight(1f)
            )
            // Bascule « tout ouvrir / tout fermer » : sur un pylône mutualisé, déplier les quatre
            // opérateurs un par un est fastidieux. Inutile s'il n'y en a qu'un.
            if (expandable && sortedAntennas.size > 1) {
                TextButton(
                    onClick = {
                        expandedAntennaIds = if (allExpanded) {
                            emptyList()
                        } else {
                            sortedAntennas.map { it.idAnfr }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = sizing.spacing(8.dp))
                ) {
                    Icon(
                        imageVector = if (allExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                        contentDescription = null,
                        modifier = Modifier.size(sizing.component(18.dp))
                    )
                    Spacer(modifier = Modifier.width(sizing.spacing(6.dp)))
                    Text(
                        text = if (allExpanded) {
                            stringResource(R.string.operators_collapse_all)
                        } else {
                            stringResource(R.string.operators_expand_all)
                        },
                        fontSize = sizing.text(13.sp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        // Partage de tous les opérateurs d'un coup : c'est l'export PDF paginé déjà utilisé par le
        // bloc « Partager » du support (une page par station), simplement rendu accessible ici.
        if (expandable && onShareAllOperators != null && sortedAntennas.size > 1) {
            Button(
                onClick = onShareAllOperators,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sizing.spacing(16.dp))
                    .padding(bottom = listItemPadding)
                    .height(sizing.component(52.dp)),
                shape = blockShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.size(sizing.component(20.dp))
                )
                Spacer(modifier = Modifier.width(sizing.spacing(10.dp)))
                Text(
                    text = stringResource(R.string.operators_share_all),
                    fontSize = sizing.text(15.sp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (!useOneUi) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        }
        sortedAntennas.forEach { antenna ->
            val operatorKeys = OperatorColors.keysFor(antenna.operateur)
            val isExpanded = expandable && antenna.idAnfr in expandedAntennaIds
            OperatorDetailItem(
                antenna = antenna,
                technique = techniques[antenna.idAnfr],
                hsEntity = hsDataMap[antenna.idAnfr], // 🚨 Changé ici
                cardBgColor = cardBgColor,
                blockShape = blockShape,
                useOneUi = useOneUi,
                isMuted = (activeKeys != null && operatorKeys.none { it in activeKeys }) ||
                    (activeKeys == null && hasPriorityMatch && priorityOperatorKey !in operatorKeys),
                expandable = expandable,
                isExpanded = isExpanded,
                onClick = {
                    if (expandable) {
                        // Refermer libère le contenu de cet opérateur ; les autres ne bougent pas.
                        expandedAntennaIds = if (isExpanded) {
                            expandedAntennaIds - antenna.idAnfr
                        } else {
                            expandedAntennaIds + antenna.idAnfr
                        }
                    } else {
                        onAntennaClick(antenna.idAnfr)
                    }
                }
            )
            // Pas d'AnimatedVisibility ici : l'animation de hauteur remesure tout le sous-arbre à
            // chaque image, et le sous-arbre est une fiche complète — c'est ce qui saccadait.
            if (expandable && isExpanded && deferredContentReady) {
                expandedContent(antenna)
            }
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
    expandable: Boolean = false,
    isExpanded: Boolean = false,
    onClick: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val modifier = if (useOneUi) {
        Modifier.fillMaxWidth().padding(horizontal = sizing.spacing(16.dp)).clip(blockShape).background(cardBgColor).clickable(onClick = onClick).padding(sizing.spacing(16.dp))
    } else {
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(sizing.spacing(16.dp))
    }

    Column(modifier = modifier.alpha(if (isMuted) 0.42f else 1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val opName = antenna.operateur ?: stringResource(R.string.appstrings_unknown)
            val logoRes = getLocalLogoRes(opName)

            if (logoRes != null) {
                Image(painter = painterResource(id = logoRes), contentDescription = null, modifier = Modifier.size(sizing.component(60.dp)).clip(RoundedCornerShape(8.dp)))
            } else {
                Box(modifier = Modifier.size(sizing.component(60.dp)).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)))
            }
            Spacer(modifier = Modifier.width(sizing.spacing(16.dp)))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = opName, fontWeight = FontWeight.Bold, fontSize = sizing.text(18.sp), color = MaterialTheme.colorScheme.onSurface)

                // ✅ LECTURE DES FILTRES SPÉCIFIQUES AU DÉTAIL DU SITE
                val s2G = AppConfig.siteShowTechno2G.value && (AppConfig.siteF2G_900.value || AppConfig.siteF2G_1800.value)
                val s3G = AppConfig.siteShowTechno3G.value && (AppConfig.siteF3G_900.value || AppConfig.siteF3G_2100.value)
                val s4G = AppConfig.siteShowTechno4G.value && (AppConfig.siteF4G_700.value || AppConfig.siteF4G_800.value || AppConfig.siteF4G_900.value || AppConfig.siteF4G_1800.value || AppConfig.siteF4G_2100.value || AppConfig.siteF4G_2600.value)
                val s5G = AppConfig.siteShowTechno5G.value && (AppConfig.siteF5G_700.value || AppConfig.siteF5G_1400.value || AppConfig.siteF5G_2100.value || AppConfig.siteF5G_3500.value || AppConfig.siteF5G_4200.value || AppConfig.siteF5G_26000.value)
                val sFh = AppConfig.siteShowTechnoFH.value

                // ✅ AFFICHAGE DES VRAIES TECHNOLOGIES FILTRÉES
                val rawTechs = technique?.technologies?.takeIf { it.isNotBlank() } ?: antenna.frequences
                // ✅ On envoie les filtres au formateur
                val realTechs = formatSiteTechnologies(rawTechs, stringResource(R.string.appstrings_unknown), s2G, s3G, s4G, s5G, sFh)
                Text(text = realTechs, fontSize = sizing.text(16.sp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)

            } // <-- Fin de la Column(weight = 1f)

            // La flèche reste toute seule à droite. Dépliable : chevron haut/bas, pour ne pas
            // promettre l'ouverture d'un écran alors que la fiche s'insère juste en dessous.
            val trailingIcon = when {
                !expandable -> Icons.AutoMirrored.Filled.KeyboardArrowRight
                isExpanded -> Icons.Default.ExpandLess
                else -> Icons.Default.ExpandMore
            }
            Icon(trailingIcon, null, modifier = Modifier.size(sizing.component(28.dp)), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } // <-- Fin de la Row contenant le logo, le titre et la flèche

        // 🚨 NOUVEAU PLACEMENT : Sous le logo complet, et au-dessus des dates (Implémentation)
        if (hsEntity != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = sizing.spacing(12.dp)) // Espace avec le logo au-dessus
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Warning,
                    contentDescription = stringResource(R.string.appstrings_outage_attention_desc),
                    tint = Color(0xFFE53935),
                    modifier = Modifier.size(sizing.component(16.dp)).padding(end = sizing.spacing(6.dp))
                )
                Text(
                    text = formatOutageDetails(hsEntity),
                    fontSize = sizing.text(12.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE53935),
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

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

        EmitterHeightsLine(technique)
    }
}

/**
 * « Émetteur à 28 m », « Émetteurs à 25 m et 30 m » : a quelle hauteur cet operateur emet depuis ce
 * pylone. L'information existe deja dans la fiche site mais enfouie dans le tableau des frequences,
 * une colonne par bande — alors qu'elle tient en une ligne et se lit d'un coup d'oeil ici.
 */
@Composable
private fun EmitterHeightsLine(technique: TechniqueEntity?) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    // `detailsFrequences` decompresse le blob `Z1:` a CHAQUE lecture : on ne le fait qu'une fois.
    val heights = remember(technique?.encodedDetailsFrequences) {
        emitterHeightsMeters(technique?.detailsFrequences)
    }
    if (heights.isEmpty()) return

    val distanceUnit = AppConfig.distanceUnit.intValue
    val locale = LocalConfiguration.current.locales[0]
    val formatted = heights.map { formatPanelHeightForUnit(it, distanceUnit, locale) }

    // Seules les hauteurs passent en gras, pas le reste de la phrase. On ne peut pas les retrouver
    // par indexOf dans le texte fini (« 3 m » est un morceau de « 33 m ») : on demande aux formats
    // traduits de poser un marqueur d'un caractere, puis on rebatit la chaine autour. Ca suit aussi
    // les langues qui rangent le trou ailleurs dans la phrase (« Sendeantennen in %1$s Höhe »).
    val heightsText = if (formatted.size == 1) {
        buildAnnotatedString { appendBoldHeight(formatted.first()) }
    } else {
        annotatedFromTemplate(
            template = stringResource(
                R.string.appstrings_list_and,
                MARK_LIST_HEAD.toString(),
                MARK_LIST_TAIL.toString()
            ),
            parts = mapOf(
                MARK_LIST_HEAD to {
                    formatted.dropLast(1).forEachIndexed { index, height ->
                        if (index > 0) append(", ")
                        appendBoldHeight(height)
                    }
                },
                MARK_LIST_TAIL to { appendBoldHeight(formatted.last()) }
            )
        )
    }

    Text(
        text = annotatedFromTemplate(
            template = pluralStringResource(
                R.plurals.emitter_heights,
                formatted.size,
                MARK_LIST_HEAD.toString()
            ),
            parts = mapOf(MARK_LIST_HEAD to { append(heightsText) })
        ),
        fontSize = sizing.text(13.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = sizing.spacing(4.dp))
    )
}

private const val MARK_LIST_HEAD = '\u0001'
private const val MARK_LIST_TAIL = '\u0002'

private fun AnnotatedString.Builder.appendBoldHeight(height: String) {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(height) }
}

/** Remplace chaque marqueur d'un caractere du libelle traduit par un fragment deja mis en forme. */
private fun annotatedFromTemplate(
    template: String,
    parts: Map<Char, AnnotatedString.Builder.() -> Unit>
): AnnotatedString = buildAnnotatedString {
    template.forEach { char ->
        val part = parts[char]
        if (part != null) part() else append(char)
    }
}

@Composable
fun DateLine(label: String, value: String) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Text(
        text = buildAnnotatedString {
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append(label) }
            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(value) }
        },
        fontSize = sizing.text(14.sp), modifier = Modifier.padding(vertical = sizing.spacing(2.dp))
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
    // 0. Panne déduite (propagation zone blanche) : pas de déclaration propre à cet opérateur,
    //    on affiche simplement « Potentiellement en panne » sans détail de service/raison.
    if (hsData.isPotential) {
        return stringResource(R.string.appstrings_outage_status_potential)
    }

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
