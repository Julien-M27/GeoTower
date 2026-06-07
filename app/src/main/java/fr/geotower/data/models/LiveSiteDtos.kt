package fr.geotower.data.models

import com.google.gson.annotations.SerializedName

data class LiveSitesListResponseDto(
    @SerializedName("country_code") val countryCode: String? = null,
    @SerializedName("limit") val limit: Int? = null,
    @SerializedName("radius_km") val radiusKm: Double? = null,
    @SerializedName("sites") val sites: List<LiveSiteSummaryDto> = emptyList()
)

data class LiveSiteResponseDto(
    @SerializedName("country_code") val countryCode: String? = null,
    @SerializedName("summary") val summary: LiveSiteSummaryDto? = null,
    @SerializedName("matches") val matches: List<LiveSiteSummaryDto> = emptyList(),
    @SerializedName("detail") val detail: LiveSiteDetailDto? = null,
    @SerializedName("supports") val supports: List<LiveSiteSupportDto> = emptyList(),
    @SerializedName("antennas") val antennas: List<LiveSiteAntennaDto> = emptyList()
)

data class LiveSiteSummaryDto(
    @SerializedName("site_rowid") val siteRowId: Long? = null,
    @SerializedName("id_anfr") val idAnfr: String? = null,
    @SerializedName("operator_name") val operatorName: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("azimuts") val azimuts: String? = null,
    @SerializedName("azimuts_fh") val azimutsFh: String? = null,
    @SerializedName("code_insee") val codeInsee: String? = null,
    @SerializedName("commune") val commune: String? = null,
    @SerializedName("adresse") val adresse: String? = null,
    @SerializedName("tech_mask") val techMask: Int? = null,
    @SerializedName("band_mask") val bandMask: Int? = null,
    @SerializedName("statut") val statut: String? = null,
    @SerializedName("has_active") val hasActive: Int? = null,
    @SerializedName("has_underground_support") val hasUndergroundSupport: Int? = null,
    @SerializedName("arcep_nidt") val arcepNidt: String? = null,
    @SerializedName("is_zb") val isZb: Int? = null,
    @SerializedName("support_count") val supportCount: Int? = null,
    @SerializedName("support_ids") val supportIds: String? = null
)

data class LiveSiteDetailDto(
    @SerializedName("id_anfr") val idAnfr: String? = null,
    @SerializedName("technologies") val technologies: String? = null,
    @SerializedName("statut") val statut: String? = null,
    @SerializedName("date_implantation") val dateImplantation: String? = null,
    @SerializedName("date_service") val dateService: String? = null,
    @SerializedName("date_modif") val dateModif: String? = null,
    @SerializedName("details_frequences") val detailsFrequences: String? = null,
    @SerializedName("adresse") val adresse: String? = null,
    @SerializedName("operator_name") val operatorName: String? = null,
    @SerializedName("code_insee") val codeInsee: String? = null,
    @SerializedName("commune") val commune: String? = null,
    @SerializedName("arcep_nidt") val arcepNidt: String? = null,
    @SerializedName("is_zb") val isZb: Int? = null
)

data class LiveSiteSupportDto(
    @SerializedName("id_anfr") val idAnfr: String? = null,
    @SerializedName("id_support") val idSupport: String? = null,
    @SerializedName("nature_support") val natureSupport: String? = null,
    @SerializedName("proprietaire") val proprietaire: String? = null,
    @SerializedName("exploitant") val exploitant: String? = null,
    @SerializedName("hauteur") val hauteur: Double? = null,
    @SerializedName("azimuts_et_types") val azimutsEtTypes: String? = null
)

data class LiveSiteAntennaDto(
    @SerializedName("aer_id") val aerId: String? = null,
    @SerializedName("id_anfr") val idAnfr: String? = null,
    @SerializedName("id_support") val idSupport: String? = null,
    @SerializedName("type_antenne") val typeAntenne: String? = null,
    @SerializedName("azimut") val azimut: Int? = null,
    @SerializedName("hauteur_bas") val hauteurBas: Double? = null,
    @SerializedName("is_fh") val isFh: Int? = null
)

fun LiveSiteSummaryDto.toLocalisationEntity(): LocalisationEntity? {
    val safeId = idAnfr?.takeIf { it.isNotBlank() } ?: return null
    val safeLat = latitude ?: return null
    val safeLon = longitude ?: return null
    return LocalisationEntity(
        idAnfr = safeId,
        operateur = operatorName,
        latitude = safeLat,
        longitude = safeLon,
        azimuts = azimuts,
        codeInsee = codeInsee,
        azimutsFh = azimutsFh,
        techMask = techMask ?: 0,
        bandMask = bandMask ?: 0,
        arcepNidt = arcepNidt,
        isZb = isZb ?: 0,
        statut = statut,
        hasActive = hasActive ?: 0,
        hasUndergroundSupport = hasUndergroundSupport ?: 0
    )
}

fun LiveSiteDetailDto.toTechniqueEntity(): TechniqueEntity? {
    val safeId = idAnfr?.takeIf { it.isNotBlank() } ?: return null
    return TechniqueEntity(
        idAnfr = safeId,
        technologies = technologies,
        statut = statut,
        dateImplantation = dateImplantation,
        dateService = dateService,
        dateModif = dateModif,
        encodedDetailsFrequences = detailsFrequences,
        adresse = adresse
    )
}

fun LiveSiteSupportDto.toPhysiqueEntity(): PhysiqueEntity? {
    val safeId = idAnfr?.takeIf { it.isNotBlank() } ?: return null
    val safeSupportId = idSupport?.takeIf { it.isNotBlank() } ?: return null
    return PhysiqueEntity(
        idAnfr = safeId,
        idSupport = safeSupportId,
        natureSupport = natureSupport,
        proprietaire = proprietaire,
        exploitant = exploitant,
        hauteur = hauteur,
        azimutsEtTypes = azimutsEtTypes
    )
}
