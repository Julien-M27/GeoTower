package fr.geotower.data.models

// L'objet PYLÔNE (Le parent)
data class RadioSite(
    val siteId: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val city: String,
    val height: Float,
    val typeSupport: String,
    // La liste des opérateurs présents sur ce pylône
    val operators: List<OperatorDetails>
)

// L'objet OPÉRATEUR (L'enfant)
data class OperatorDetails(
    val name: String,       // Orange, Free...
    val technologies: List<String>, // [4G, 5G]
    val frequencies: List<String>   // [700, 3500]
)