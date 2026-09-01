package fr.geotower.ui.screens.car

import fr.geotower.data.models.LocalisationEntity

data class CarSiteListItem(
    val idAnfr: String,
    val title: String,
    val subtitle: String,
    val operators: String,
    val distanceMeters: Float,
    val latitude: Double,
    val longitude: Double,
    /** Position de la voiture utilisée pour cadrer la carte applicative. */
    val userLatitude: Double,
    val userLongitude: Double,
    /** Antennes du site, conservées pour construire le même marqueur que sur la carte téléphone. */
    val antennas: List<LocalisationEntity> = emptyList(),
    /** Natures de supports affichées entre l'adresse et la commune sur l'écran téléphone. */
    val supportTypes: List<String> = emptyList(),
    /** Informations physiques détaillées, affichées uniquement dans la fiche du site. */
    val supportDetails: List<CarSupportInfo> = emptyList(),
    /** Technologies déduites des masques ANFR et des résumés techniques. */
    val technologies: List<String> = emptyList(),
    val statuses: List<String> = emptyList(),
    val anfrIds: List<String> = emptyList(),
    val supportIds: List<String> = emptyList(),
    val azimuths: List<String> = emptyList(),
    val frequencyDetails: List<String> = emptyList(),
    val isZoneBlanche: Boolean = false,
    val hasUndergroundSupport: Boolean = false,
    val isEntirelyProject: Boolean = false
)

data class CarSupportInfo(
    val idSupport: String,
    val nature: String?,
    val owner: String?,
    val operator: String?,
    val heightMeters: Double?,
    val azimuthsAndTypes: String?
)
