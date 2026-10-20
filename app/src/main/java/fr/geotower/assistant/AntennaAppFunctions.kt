package fr.geotower.assistant

import fr.geotower.data.AnfrRepository

/**
 * Modèle pour l'exposition des App Functions Android 16+ (Gemini on-device tool calling).
 *
 * Lorsque la bibliothèque `androidx.appfunctions` est activée sur un appareil compatible,
 * cette classe peut être directement annotée avec `@AppFunction(isDescribedByKDoc = true)`
 * et chaque propriété retournée avec `@AppFunctionSerializable(isDescribedByKDoc = true)`.
 *
 * Gemini utilise la documentation KDoc ci-dessous comme schéma d'appel de fonction (Function Calling).
 */
class AntennaAppFunctions(
    private val repository: AnfrRepository,
    private val locationHelper: DeviceLocationHelper
) {
    private val queryHandler = AntennaVoiceQueryHandler(repository, locationHelper)

    /**
     * Recherche l'antenne relais de téléphonie mobile la plus proche de l'utilisateur,
     * en interrogeant la base locale Room de GeoTower.
     *
     * @param operatorName Nom éventuel de l'opérateur recherché (ex: Free, Orange, SFR, Bouygues).
     * @return Les détails de l'antenne trouvée ainsi que la phrase de réponse prête à être énoncée vocalement.
     */
    suspend fun getNearestAntenna(operatorName: String? = null): AntennaAppFunctionResult? {
        val (results, speechText) = queryHandler.queryNearestAntennas(
            operatorFilter = operatorName,
            limit = 1
        )
        val first = results.firstOrNull() ?: return null

        return AntennaAppFunctionResult(
            operator = first.operator,
            distanceMeters = first.distanceMeters,
            direction = first.directionLabel,
            technologies = first.technologies.joinToString(", "),
            latitude = first.latitude,
            longitude = first.longitude,
            spokenResponse = speechText
        )
    }
}

/**
 * Entité de retour pour l'App Function Gemini.
 */
data class AntennaAppFunctionResult(
    /** Nom de l'opérateur du relais mobile */
    val operator: String,
    /** Distance estimée en mètres */
    val distanceMeters: Int,
    /** Orientation cardinale relative (ex: 'au nord-est', 'au sud') */
    val direction: String,
    /** Technologies déployées (ex: '4G, 5G') */
    val technologies: String,
    /** Coordonnée de latitude */
    val latitude: Double,
    /** Coordonnée de longitude */
    val longitude: Double,
    /** Synthèse vocale prête pour l'assistant */
    val spokenResponse: String
)
