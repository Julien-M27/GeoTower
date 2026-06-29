package fr.geotower.data.coverage

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Helpers géodésiques et de diagramme d'antenne — code **pur** (aucun import Android), testable sur JVM.
 *
 * Le modèle est une estimation **géométrique de ligne de visée**, PAS une simulation de propagation RF.
 * Les diagrammes (horizontal/vertical) sont des modèles paramétriques génériques : ANFR ne fournit ni
 * gain, ni ouverture, ni tilt réels.
 */
object CoverageGeo {
    const val EARTH_RADIUS_M = 6_371_000.0

    /** Rayon terrestre effectif (k = 4/3) pour la réfraction radio standard. */
    const val EFFECTIVE_EARTH_RADIUS_M = 4.0 / 3.0 * EARTH_RADIUS_M

    /**
     * Point destination à [distanceM] mètres depuis ([latDeg], [lonDeg]) selon le relèvement
     * [bearingDeg] (0 = Nord, sens horaire). Renvoie `[lat, lon]` en degrés.
     */
    fun destinationPoint(latDeg: Double, lonDeg: Double, bearingDeg: Double, distanceM: Double): DoubleArray {
        val angular = distanceM / EARTH_RADIUS_M
        val brg = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(latDeg)
        val lon1 = Math.toRadians(lonDeg)
        val sinLat1 = sin(lat1)
        val cosLat1 = cos(lat1)
        val sinAng = sin(angular)
        val cosAng = cos(angular)
        val sinLat2 = (sinLat1 * cosAng + cosLat1 * sinAng * cos(brg)).coerceIn(-1.0, 1.0)
        val lat2 = asin(sinLat2)
        val y = sin(brg) * sinAng * cosLat1
        val x = cosAng - sinLat1 * sinLat2
        val lon2 = lon1 + atan2(y, x)
        return doubleArrayOf(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    /** Distance haversine (m) entre deux points. */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Ramène un relèvement dans [0, 360). */
    fun normalizeBearingDeg(deg: Double): Double {
        var d = deg % 360.0
        if (d < 0) d += 360.0
        return d
    }

    /** Écart angulaire absolu (0..180) entre deux relèvements. */
    fun angularDifferenceDeg(a: Double, b: Double): Double {
        var d = Math.abs(normalizeBearingDeg(a) - normalizeBearingDeg(b)) % 360.0
        if (d > 180.0) d = 360.0 - d
        return d
    }

    /** Écart SIGNÉ (deg) de [bearingDeg] par rapport à [referenceDeg], dans [-180, 180). */
    fun signedDeltaDeg(bearingDeg: Double, referenceDeg: Double): Double {
        return ((bearingDeg - referenceDeg + 540.0) % 360.0) - 180.0
    }

    /**
     * Diagramme horizontal 3GPP : `A_h(θ) = -min(12·(θ/θ3dB)², A_m)` en dB (≤ 0).
     * @param deltaDeg écart à l'axe (azimut) en degrés.
     */
    fun horizontalPatternDb(deltaDeg: Double, theta3dbDeg: Double, patternAmDb: Double): Double {
        if (theta3dbDeg <= 0) return 0.0
        val ratio = deltaDeg / theta3dbDeg
        return -minOf(12.0 * ratio * ratio, patternAmDb)
    }

    /**
     * Diagramme vertical 3GPP centré sur `-tilt` : `A_v(φ) = -min(12·((φ + tilt)/φ3dB)², A_m)` en dB (≤ 0).
     * @param elevationDeg angle d'élévation de la cible (négatif vers le bas).
     */
    fun verticalPatternDb(elevationDeg: Double, tiltDeg: Double, verticalBeamwidthDeg: Double, patternAmDb: Double): Double {
        if (verticalBeamwidthDeg <= 0) return 0.0
        val off = (elevationDeg + tiltDeg) / verticalBeamwidthDeg
        return -minOf(12.0 * off * off, patternAmDb)
    }

    /** Demi-largeur horizontale (deg) où le diagramme retombe au seuil [thresholdDb] (≤ 0). */
    fun horizontalHalfWidthDeg(theta3dbDeg: Double, patternAmDb: Double, thresholdDb: Double): Double {
        val cutoff = minOf(-thresholdDb, patternAmDb)
        if (cutoff <= 0 || theta3dbDeg <= 0) return 0.0
        return theta3dbDeg * sqrt(cutoff / 12.0)
    }

    /** Rayon de la 1ʳᵉ zone de Fresnel (m) à la position d1 d'un trajet (d1, d2) pour une fréquence MHz. */
    fun fresnelRadiusM(d1M: Double, d2M: Double, frequencyMHz: Int): Double {
        if (frequencyMHz <= 0 || d1M <= 0 || d2M <= 0) return 0.0
        val d1Km = d1M / 1000.0
        val d2Km = d2M / 1000.0
        val totalKm = d1Km + d2Km
        val freqGHz = frequencyMHz / 1000.0
        return 17.32 * sqrt((d1Km * d2Km) / (freqGHz * totalKm))
    }
}
