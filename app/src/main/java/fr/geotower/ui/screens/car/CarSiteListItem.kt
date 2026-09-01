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
    val antennas: List<LocalisationEntity> = emptyList()
)
