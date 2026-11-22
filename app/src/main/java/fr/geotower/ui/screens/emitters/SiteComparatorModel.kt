package fr.geotower.ui.screens.emitters

import fr.geotower.data.models.FaisceauxEntity
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.RadioFilterMasks
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.data.models.isDeclaredActive
import fr.geotower.utils.FreqBand
import fr.geotower.utils.TechFrequencyOrder
import fr.geotower.utils.parseAndSortFrequencies
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Résumé normalisé d'un site ANFR utilisé pour la comparaison côte à côte.
 */
data class ComparableSiteSummary(
    val idAnfr: String,
    val operatorName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val status: String?,
    val isActive: Boolean,
    val supportId: String?,
    val supportNature: String?,
    val supportOwner: String?,
    val maxSupportHeightMeters: Double?,
    val technologies: List<String>,
    val bandLabels: List<String>,
    val azimuths: List<Int>,
    val hasFh: Boolean,
    val fhAzimuthsCount: Int,
    val dateImplantation: String?,
    val dateService: String?,
    val dateModif: String?
)

/**
 * Résultat de la comparaison des bandes de fréquences entre deux sites.
 */
data class BandComparison(
    val common: List<String>,
    val onlyA: List<String>,
    val onlyB: List<String>
)

/**
 * Construit un [ComparableSiteSummary] à partir des entités ANFR locales ou Live.
 */
fun buildComparableSiteSummary(
    localisation: LocalisationEntity,
    technique: TechniqueEntity?,
    physique: List<PhysiqueEntity>,
    faisceaux: List<FaisceauxEntity>,
    order: TechFrequencyOrder = TechFrequencyOrder.DESCENDING
): ComparableSiteSummary {
    val parsedBands: List<FreqBand> = parseAndSortFrequencies(
        freqStr = technique?.detailsFrequences,
        txtUnknown = "?",
        txtAzimuthNotSpecified = "",
        order = order
    )
    val bandLabels = if (parsedBands.isNotEmpty()) {
        sortBandLabels(parsedBands.map { formatComparatorBandLabel(it.gen, it.value) }.distinct(), order)
    } else {
        formatBandTokensFromMask(localisation.bandMask, order)
    }

    val techRank = techRankMap(order)
    val technologies = if (parsedBands.isNotEmpty()) {
        parsedBands.map { techLabelForGen(it.gen) }.distinct().sortedBy { techRank[it] ?: 99 }
    } else {
        val fromTech = technique?.technologies
            ?.split(",", " ", ";", "/")
            ?.map { it.trim().uppercase(Locale.ROOT) }
            ?.filter { it in setOf("2G", "3G", "4G", "5G") }
            .orEmpty()
        if (fromTech.isNotEmpty()) {
            fromTech.distinct().sortedBy { techRank[it] ?: 99 }
        } else {
            RadioFilterMasks.techMaskToString(localisation.techMask)
                .split(",")
                .map { it.trim() }
                .filter { it in setOf("2G", "3G", "4G", "5G") }
                .distinct()
                .sortedBy { techRank[it] ?: 99 }
        }
    }

    val maxHeight = physique.mapNotNull { it.hauteur }.filter { it > 0.0 }.maxOrNull()
    val primarySupport = physique.maxByOrNull { it.hauteur ?: 0.0 }

    val azimuths = parseAzimuths(localisation.azimuts)
    val fhAzimuths = parseAzimuths(localisation.azimutsFh)
    val hasFh = fhAzimuths.isNotEmpty() || faisceaux.isNotEmpty() ||
        (localisation.techMask and RadioFilterMasks.TECH_FH) != 0

    val rawStatus = technique?.statut?.takeIf { it.isNotBlank() } ?: localisation.statut?.takeIf { it.isNotBlank() }

    return ComparableSiteSummary(
        idAnfr = localisation.idAnfr,
        operatorName = localisation.operateur?.takeIf { it.isNotBlank() } ?: "?",
        address = technique?.adresse?.takeIf { it.isNotBlank() } ?: "",
        latitude = localisation.latitude,
        longitude = localisation.longitude,
        status = rawStatus,
        isActive = localisation.isDeclaredActive(),
        supportId = primarySupport?.idSupport?.takeIf { it.isNotBlank() },
        supportNature = primarySupport?.natureSupport?.takeIf { it.isNotBlank() },
        supportOwner = primarySupport?.proprietaire?.takeIf { it.isNotBlank() },
        maxSupportHeightMeters = maxHeight,
        technologies = technologies,
        bandLabels = bandLabels,
        azimuths = azimuths,
        hasFh = hasFh,
        fhAzimuthsCount = maxOf(fhAzimuths.size, faisceaux.size),
        dateImplantation = technique?.dateImplantation?.takeIf { it.isNotBlank() },
        dateService = technique?.dateService?.takeIf { it.isNotBlank() } ?: localisation.dateService?.takeIf { it.isNotBlank() },
        dateModif = technique?.dateModif?.takeIf { it.isNotBlank() }
    )
}

/**
 * Compare les bandes de fréquences entre deux sites A et B, triées selon [order].
 */
fun compareSiteBands(
    bandsA: List<String>,
    bandsB: List<String>,
    order: TechFrequencyOrder = TechFrequencyOrder.DESCENDING
): BandComparison {
    val setA = bandsA.toSet()
    val setB = bandsB.toSet()
    return BandComparison(
        common = sortBandLabels(bandsA.filter { it in setB }.distinct(), order),
        onlyA = sortBandLabels(bandsA.filter { it !in setB }.distinct(), order),
        onlyB = sortBandLabels(bandsB.filter { it !in setA }.distinct(), order)
    )
}

/**
 * Formate et trie les bandes d'un `bandMask` (ex. "5G 3500", "4G 2600") selon [order].
 */
fun formatBandTokensFromMask(
    bandMask: Int,
    order: TechFrequencyOrder = TechFrequencyOrder.DESCENDING
): List<String> {
    if (bandMask == 0) return emptyList()
    val rawTokens = RadioFilterMasks.bandMaskToFilterString(bandMask)
        .split(" ")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { token ->
            val match = Regex("^([2345])G(\\d+)$").matchEntire(token)
            if (match != null) {
                val gen = match.groupValues[1].toIntOrNull() ?: 0
                val value = match.groupValues[2].toIntOrNull() ?: 0
                formatComparatorBandLabel(gen, value)
            } else {
                token
            }
        }
    return sortBandLabels(rawTokens, order)
}

/**
 * Trie des libellés de bandes (ex. "5G 3500", "4G 800", "FH") selon [order].
 */
fun sortBandLabels(
    labels: List<String>,
    order: TechFrequencyOrder
): List<String> {
    val techRank = techRankMap(order)
    return labels.distinct().sortedWith { a, b ->
        val (techA, freqA) = parseBandLabel(a)
        val (techB, freqB) = parseBandLabel(b)
        val rankCmp = (techRank[techA] ?: 99).compareTo(techRank[techB] ?: 99)
        if (rankCmp != 0) {
            rankCmp
        } else if (order == TechFrequencyOrder.DESCENDING) {
            freqB.compareTo(freqA)
        } else {
            freqA.compareTo(freqB)
        }
    }
}

private fun techLabelForGen(gen: Int): String = if (gen == 0) "FH" else "${gen}G"

private fun formatComparatorFreqUnit(value: Int): String =
    if (value >= 10_000 && value % 1000 == 0) "${value / 1000} GHz" else "$value MHz"

private fun formatComparatorBandLabel(gen: Int, value: Int): String {
    val tech = techLabelForGen(gen)
    return if (value > 0) "$tech ${formatComparatorFreqUnit(value)}" else tech
}

private fun techRankMap(order: TechFrequencyOrder): Map<String, Int> = when (order) {
    TechFrequencyOrder.DESCENDING -> mapOf("5G" to 0, "4G" to 1, "3G" to 2, "2G" to 3, "FH" to 4)
    TechFrequencyOrder.ASCENDING -> mapOf("2G" to 0, "3G" to 1, "4G" to 2, "5G" to 3, "FH" to 4)
}

private fun parseBandLabel(label: String): Pair<String, Int> {
    val trimmed = label.trim().uppercase(Locale.ROOT)
    val match = Regex("^(FH|[2345]G)\\s*(\\d+)(?:\\s*(GHZ|MHZ))?.*$").matchEntire(trimmed)
    return if (match != null) {
        val rawValue = match.groupValues[2].toIntOrNull() ?: 0
        val unit = match.groupValues[3]
        val mhzValue = if (unit == "GHZ") rawValue * 1000 else rawValue
        match.groupValues[1] to mhzValue
    } else if (trimmed.startsWith("FH")) {
        "FH" to 0
    } else {
        trimmed to 0
    }
}

/**
 * Calcule la distance géodésique en mètres entre deux points GPS (formule de Haversine).
 */
fun computeDistanceMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
): Double {
    val earthRadiusMeters = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val radLat1 = Math.toRadians(lat1)
    val radLat2 = Math.toRadians(lat2)

    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(radLat1) * cos(radLat2) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusMeters * c
}

/**
 * Calcule l'azimut en degrés (0..359) du point A vers le point B.
 */
fun computeBearingDegrees(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
): Int {
    val radLat1 = Math.toRadians(lat1)
    val radLat2 = Math.toRadians(lat2)
    val dLon = Math.toRadians(lon2 - lon1)

    val y = sin(dLon) * cos(radLat2)
    val x = cos(radLat1) * sin(radLat2) - sin(radLat1) * cos(radLat2) * cos(dLon)
    val deg = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    return deg.roundToInt() % 360
}

/**
 * Formate une distance en mètres ou kilomètres de manière lisible.
 */
fun formatComparatorDistance(meters: Double): String {
    return if (meters < 1000.0) {
        "${meters.roundToInt()} m"
    } else {
        String.format(Locale.getDefault(), "%.2f km", meters / 1000.0)
    }
}

internal fun parseAzimuths(raw: String?): List<Int> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(",", ";", " ", "/", "|")
        .mapNotNull { token ->
            val cleaned = token.trim().removeSuffix("°")
            cleaned.toDoubleOrNull()?.roundToInt()?.let { ((it % 360) + 360) % 360 }
        }
        .distinct()
        .sorted()
}
