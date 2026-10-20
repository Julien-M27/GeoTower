package fr.geotower.assistant

/**
 * Résultat structuré représentant une antenne relais trouvée pour l'assistant vocal.
 */
data class AntennaQueryResult(
    val idAnfr: String,
    val operator: String,
    val distanceMeters: Int,
    val directionLabel: String,
    val technologies: List<String>,
    val latitude: Double,
    val longitude: Double,
    val spokenSentence: String
)
