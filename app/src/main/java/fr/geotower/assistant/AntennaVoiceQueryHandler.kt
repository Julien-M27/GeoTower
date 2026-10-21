package fr.geotower.assistant

import android.location.Location
import fr.geotower.data.AnfrRepository
import fr.geotower.data.models.LocalisationEntity
import kotlin.math.roundToInt

class AntennaVoiceQueryHandler(
    private val repository: AnfrRepository,
    private val locationHelper: DeviceLocationHelper
) {

    /**
     * Recherche les antennes les plus proches et prépare le texte parlé pour l'assistant vocal.
     */
    suspend fun queryNearestAntennas(
        operatorFilter: String? = null,
        limit: Int = 1
    ): Pair<List<AntennaQueryResult>, String> {
        val userLocation = locationHelper.getLastKnownLocation()
        if (userLocation == null) {
            val missingLocationMsg = "Position géographique indisponible. Vérifiez que la localisation est activée."
            return emptyList<AntennaQueryResult>() to missingLocationMsg
        }

        val cleanedOp = cleanOperatorFilter(operatorFilter)
        val safeLimit = limit.coerceAtLeast(1)
        val entities = fetchSites(cleanedOp, userLocation.latitude, userLocation.longitude, safeLimit)

        if (entities.isEmpty()) {
            val emptyMsg = if (cleanedOp != null) {
                "Aucune antenne $cleanedOp active trouvée à proximité dans la base locale GeoTower."
            } else {
                "Aucune antenne active trouvée à proximité dans la base locale GeoTower."
            }
            return emptyList<AntennaQueryResult>() to emptyMsg
        }

        val results = entities.map { entity ->
            val distanceMeters = computeDistanceMeters(
                userLocation.latitude, userLocation.longitude,
                entity.latitude, entity.longitude
            )
            val bearing = computeBearing(
                userLocation.latitude, userLocation.longitude,
                entity.latitude, entity.longitude
            )
            val directionLabel = getCardinalDirection(bearing)
            val techs = extractTechnologies(entity.techMask)
            val opName = entity.operateur?.ifBlank { "Opérateur inconnu" } ?: "Opérateur inconnu"

            val sentence = buildSpokenSentence(
                operatorName = opName,
                distanceMeters = distanceMeters,
                directionLabel = directionLabel,
                technologies = techs
            )

            AntennaQueryResult(
                idAnfr = entity.idAnfr,
                operator = opName,
                distanceMeters = distanceMeters,
                directionLabel = directionLabel,
                technologies = techs,
                latitude = entity.latitude,
                longitude = entity.longitude,
                spokenSentence = sentence
            )
        }

        val primaryResponse = results.first().spokenSentence
        return results to primaryResponse
    }

    private suspend fun fetchSites(
        operatorFilter: String?,
        lat: Double,
        lon: Double,
        limit: Int
    ): List<LocalisationEntity> {
        return if (!operatorFilter.isNullOrBlank()) {
            repository.getNearestActiveByOperator(
                operatorName = operatorFilter,
                lat = lat,
                lon = lon,
                limit = limit
            )
        } else {
            repository.getNearest(
                lat = lat,
                lon = lon,
                limit = limit
            )
        }
    }

    companion object {
        fun cleanOperatorFilter(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val lower = raw.trim().lowercase()
            return when {
                lower.contains("free") -> "Free"
                lower.contains("orange") -> "Orange"
                lower.contains("sfr") -> "SFR"
                lower.contains("bouygues") -> "Bouygues"
                lower.contains("digicel") -> "Digicel"
                lower.contains("outremer") -> "Outremer"
                // Si la phrase contient "antenne", "relais", etc. sans nom d'opérateur spécifique,
                // on renvoie null pour chercher tous les opérateurs confondus.
                else -> null
            }
        }

        fun computeDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
            val results = FloatArray(1)
            Location.distanceBetween(lat1, lon1, lat2, lon2, results)
            return results[0].roundToInt()
        }

        fun computeBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val loc1 = Location("").apply {
                latitude = lat1
                longitude = lon1
            }
            val loc2 = Location("").apply {
                latitude = lat2
                longitude = lon2
            }
            return loc1.bearingTo(loc2)
        }

        fun getCardinalDirection(bearingDegrees: Float): String {
            val normalized = (bearingDegrees % 360f + 360f) % 360f
            return when {
                normalized >= 337.5f || normalized < 22.5f -> "au nord"
                normalized < 67.5f -> "au nord-est"
                normalized < 112.5f -> "à l'est"
                normalized < 157.5f -> "au sud-est"
                normalized < 202.5f -> "au sud"
                normalized < 247.5f -> "au sud-ouest"
                normalized < 292.5f -> "à l'ouest"
                else -> "au nord-ouest"
            }
        }

        fun extractTechnologies(techMask: Int): List<String> = buildList {
            if ((techMask and 1) != 0) add("2G")
            if ((techMask and 2) != 0) add("3G")
            if ((techMask and 4) != 0) add("4G")
            if ((techMask and 8) != 0) add("5G")
        }

        fun buildSpokenSentence(
            operatorName: String,
            distanceMeters: Int,
            directionLabel: String,
            technologies: List<String>
        ): String {
            val distanceStr = if (distanceMeters < 1000) {
                "$distanceMeters mètres"
            } else {
                val km = String.format(java.util.Locale.FRANCE, "%.1f", distanceMeters / 1000.0)
                "$km kilomètres"
            }

            val techStr = if (technologies.isNotEmpty()) {
                " en " + technologies.joinToString(" et ")
            } else {
                ""
            }

            return "L'antenne $operatorName la plus proche se trouve à $distanceStr $directionLabel$techStr."
        }
    }
}
