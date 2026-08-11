package fr.geotower.data.trip

import kotlin.math.roundToLong

/**
 * Encodage compact d'une géométrie de trajet, au format « encoded polyline » (précision 5, celui
 * de Google et d'OSRM).
 *
 * Pourquoi ne pas stocker les points tels quels : un segment routier de 50 km revient de la
 * Géoplateforme avec plusieurs milliers de points, et une tournée en compte une quinzaine. En
 * tableaux de doubles JSON, le fichier des trajets atteindrait vite plusieurs mégaoctets, relus
 * intégralement à chaque ouverture de la liste et réécrits à chaque case « visité » cochée. Encodé,
 * le même tracé tient en quelques kilo-octets.
 *
 * La précision 5 arrondit à ~1,1 m : sans effet sur un tracé qu'on suit à la voiture ou à pied,
 * alors que la précision 6 gonfle la chaîne d'un cinquième pour rien.
 */
object TripGeometryCodec {
    private const val SCALE = 1e5

    /** Points en `[latitude, longitude]`, dans l'ordre du tracé — même convention que `RoutePathResult`. */
    fun encode(points: List<DoubleArray>): String {
        if (points.isEmpty()) return ""

        val builder = StringBuilder(points.size * 6)
        var previousLatitude = 0L
        var previousLongitude = 0L
        for (point in points) {
            if (point.size < 2) continue
            val latitude = (point[0] * SCALE).roundToLong()
            val longitude = (point[1] * SCALE).roundToLong()
            builder.appendValue(latitude - previousLatitude)
            builder.appendValue(longitude - previousLongitude)
            previousLatitude = latitude
            previousLongitude = longitude
        }
        return builder.toString()
    }

    /**
     * Relit une chaîne produite par [encode]. Tolérante : une chaîne tronquée ou abîmée rend les
     * points déjà lus plutôt que de lever — un fichier de trajets légèrement corrompu doit coûter
     * un tracé approximatif, pas la perte de la tournée.
     */
    fun decode(encoded: String): List<DoubleArray> {
        if (encoded.isEmpty()) return emptyList()

        val points = ArrayList<DoubleArray>(encoded.length / 4 + 1)
        var index = 0
        var latitude = 0L
        var longitude = 0L
        while (index < encoded.length) {
            val latitudeDelta = encoded.readValue(index) ?: break
            index = latitudeDelta.second
            val longitudeDelta = encoded.readValue(index) ?: break
            index = longitudeDelta.second
            latitude += latitudeDelta.first
            longitude += longitudeDelta.first
            points += doubleArrayOf(latitude / SCALE, longitude / SCALE)
        }
        return points
    }

    private fun StringBuilder.appendValue(delta: Long) {
        // Zigzag : le signe passe dans le bit de poids faible, pour que les petites valeurs
        // négatives restent courtes une fois découpées en groupes de 5 bits.
        var value = if (delta < 0) (delta shl 1).inv() else (delta shl 1)
        while (value >= 0x20) {
            append((((value and 0x1f) or 0x20) + 63).toInt().toChar())
            value = value shr 5
        }
        append((value + 63).toInt().toChar())
    }

    /** Rend la valeur décodée et l'index suivant, ou `null` si la chaîne se termine en plein nombre. */
    private fun String.readValue(start: Int): Pair<Long, Int>? {
        var index = start
        var shift = 0
        var result = 0L
        while (index < length) {
            val chunk = (this[index].code - 63).toLong()
            if (chunk < 0) return null
            index++
            result = result or ((chunk and 0x1f) shl shift)
            if (chunk < 0x20) {
                val value = if (result and 1L != 0L) (result shr 1).inv() else (result shr 1)
                return value to index
            }
            shift += 5
            if (shift > 60) return null
        }
        return null
    }
}
