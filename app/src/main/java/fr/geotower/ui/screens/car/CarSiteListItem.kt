package fr.geotower.ui.screens.car

data class CarSiteListItem(
    val idAnfr: String,
    val title: String,
    val subtitle: String,
    val operators: String,
    val distanceMeters: Float,
    val latitude: Double,
    val longitude: Double
)
