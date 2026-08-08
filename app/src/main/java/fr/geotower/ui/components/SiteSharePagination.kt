package fr.geotower.ui.components

import fr.geotower.utils.FreqBand
import fr.geotower.utils.formatSpectrumDisplayDetails
import kotlin.math.ceil
import kotlin.math.max

/**
 * Tranche de bandes publiée sur une image de partage. Les tranches se suivent sans trou :
 * `to` d'une page est le `from` de la suivante.
 */
internal data class SiteShareFreqPage(val from: Int, val to: Int)

// Gabarit d'une image de partage : 400 dp de large (cf. SITE_SHARE_COLUMN_WIDTH_DP). Les
// messageries redimensionnent sur la plus grande dimension : plus l'image est haute, plus la
// largeur utile s'effondre et plus le texte devient illisible. On vise donc un rapport ~1:2,75.
internal const val SITE_SHARE_PAGE_HEIGHT_BUDGET_DP = 1100f

/** Titre, carte opérateur, cadre de la carte « Fréquences », QR code et pied de page. */
internal const val SITE_SHARE_PAGE_CHROME_DP = 380f

/** Carte « Identifiants », publiée sur la première image de fréquences seulement. */
internal const val SITE_SHARE_IDS_CARD_DP = 157f

/** Séparateur dessiné entre deux bandes d'une même carte. */
internal const val SITE_SHARE_BAND_SEPARATOR_DP = 25f

/**
 * Plafond d'images de fréquences. Au-delà, mieux vaut une dernière image trop haute que des
 * bandes perdues : envoyer dix pièces jointes n'a plus de sens, le rapport PDF prend le relais.
 */
internal const val SITE_SHARE_MAX_FREQ_PAGES = 4

// Hauteurs relevées sur la maquette du partage (ShareSiteFrequencyDetailCard), volontairement
// majorées : une page un peu courte est sans conséquence, une page trop longue casse le rapport.
private const val BAND_HEADER_DP = 24f
private const val BAND_DATE_DP = 16f
private const val TEXT_LINE_DP = 16f
private const val BLOCK_SPACING_DP = 6f
private const val PHYS_LINE_DP = 20f

/** Nombre de caractères tenant sur une ligne de détail (12 sp dans une carte de 400 dp). */
private const val DETAIL_LINE_CHARS = 44

/**
 * Hauteur estimée d'une bande dans la carte « Fréquences » du partage. Reprend exactement les
 * blocs dessinés par [ShareSiteFrequencyDetailCard] : en-tête, spectre, largeur de bande totale,
 * date, puis une ligne par panneau.
 */
internal fun siteShareBandHeightDp(
    band: FreqBand,
    showSpectrumRanges: Boolean,
    showBandwidthPerRange: Boolean,
    showBandwidthTotal: Boolean
): Float {
    var height = BAND_HEADER_DP + BAND_DATE_DP

    val preciseFreqs = band.rawFreq.substringAfter(":", "").trim()
    if (preciseFreqs.isNotBlank() && preciseFreqs != band.rawFreq.trim()) {
        val spectrum = formatSpectrumDisplayDetails(preciseFreqs)
        var detailHeight = 0f
        if (showSpectrumRanges || showBandwidthPerRange) {
            val details = spectrum.detailedFrequencies(showSpectrumRanges, showBandwidthPerRange)
            // « Spectre : » puis une ligne vide, avant les plages elles-mêmes.
            detailHeight += (2 + countWrappedLines(details)) * TEXT_LINE_DP
        }
        if (showBandwidthTotal && spectrum.hasTotal) {
            detailHeight += TEXT_LINE_DP
        }
        if (detailHeight > 0f) height += detailHeight + BLOCK_SPACING_DP
    }

    if (band.physDetails.isNotEmpty()) {
        height += BLOCK_SPACING_DP + band.physDetails.size * PHYS_LINE_DP
    }
    return height
}

/** Lignes réellement occupées par un texte, retours à la ligne automatiques compris. */
private fun countWrappedLines(text: String): Int {
    if (text.isEmpty()) return 0
    return text.split('\n').sumOf { line ->
        max(1, ceil(line.length / DETAIL_LINE_CHARS.toFloat()).toInt())
    }
}

/**
 * Découpe les bandes d'une station en images de partage. Chaque image reprend là où la
 * précédente s'est arrêtée ; une bande n'est jamais coupée en deux, quitte à dépasser le budget
 * quand elle ne tient pas seule sur une page.
 */
internal fun planSiteShareFrequencyPages(
    bandHeightsDp: List<Float>,
    firstPageSpaceDp: Float,
    nextPageSpaceDp: Float,
    maxPages: Int = SITE_SHARE_MAX_FREQ_PAGES
): List<SiteShareFreqPage> {
    // Station sans bande publiable : l'image existe quand même, avec « bandes non spécifiées ».
    if (bandHeightsDp.isEmpty()) return listOf(SiteShareFreqPage(0, 0))

    val pages = mutableListOf<SiteShareFreqPage>()
    var index = 0
    while (index < bandHeightsDp.size) {
        if (pages.size == maxPages - 1) {
            // Plafond atteint : la dernière image prend tout le reste plutôt que de perdre des bandes.
            pages += SiteShareFreqPage(index, bandHeightsDp.size)
            break
        }

        val space = if (pages.isEmpty()) firstPageSpaceDp else nextPageSpaceDp
        var used = 0f
        var end = index
        while (end < bandHeightsDp.size) {
            val cost = bandHeightsDp[end] + if (end > index) SITE_SHARE_BAND_SEPARATOR_DP else 0f
            // La première bande est toujours placée : sinon une bande plus haute qu'une page
            // ferait tourner la boucle indéfiniment.
            if (end > index && used + cost > space) break
            used += cost
            end++
        }
        pages += SiteShareFreqPage(index, end)
        index = end
    }
    return pages
}

/** Place disponible pour les bandes sur la première image de fréquences. */
internal fun siteShareFirstPageSpaceDp(includeIdentifiers: Boolean): Float {
    val chrome = SITE_SHARE_PAGE_CHROME_DP + if (includeIdentifiers) SITE_SHARE_IDS_CARD_DP else 0f
    return max(0f, SITE_SHARE_PAGE_HEIGHT_BUDGET_DP - chrome)
}

/** Place disponible sur les images de fréquences suivantes. */
internal fun siteShareNextPageSpaceDp(): Float =
    max(0f, SITE_SHARE_PAGE_HEIGHT_BUDGET_DP - SITE_SHARE_PAGE_CHROME_DP)
