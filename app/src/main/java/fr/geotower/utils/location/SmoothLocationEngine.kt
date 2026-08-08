package fr.geotower.utils.location

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Un relevé GPS brut, réduit aux seuls champs utiles au lissage.
 *
 * Volontairement sans type Android : le moteur reste du Kotlin pur, donc testable en JVM sans
 * Robolectric. La conversion depuis [android.location.Location] se fait au point d'entrée.
 *
 * [timeMs] doit venir d'une horloge MONOTONE (`SystemClock.elapsedRealtime`, soit
 * `Location.getElapsedRealtimeNanos() / 1_000_000`) : l'heure système peut sauter en arrière, ce qui
 * ferait diverger l'extrapolation.
 */
data class RawFix(
    val latitude: Double,
    val longitude: Double,
    val timeMs: Long,
    val speedMps: Float? = null,
    val bearingDeg: Float? = null,
    val accuracyM: Float? = null
)

/** Position affichable produite par le moteur, à un instant donné. */
data class SmoothedPosition(
    val latitude: Double,
    val longitude: Double,
    /** Vitesse retenue pour l'extrapolation (m/s), 0 si le moteur considère l'utilisateur à l'arrêt. */
    val speedMps: Float,
    /** true quand la position doit son avancée à l'estime piétonne et non au GPS. */
    val deadReckoned: Boolean
)

/**
 * Rend le déplacement du repère de position continu, là où le GPS ne fournit qu'un point par
 * seconde.
 *
 * Le principe est celui de la prédiction avec correction douce, empruntée au réseau des jeux :
 *
 *  - on ANCRE sur le dernier relevé et on avance en ligne droite à la vitesse et au cap qu'il
 *    annonce (extrapolation) — le repère est donc à l'heure, pas en retard d'un relevé ;
 *  - l'écart entre ce que l'on affichait et ce que le nouveau relevé annonce est mémorisé comme un
 *    OFFSET que l'on fait décroître exponentiellement — le repère rejoint la vérité en glissant au
 *    lieu de sauter ;
 *  - au-delà de [MAX_EXTRAPOLATION_S] sans relevé, on cesse d'avancer à l'aveugle : soit l'estime
 *    piétonne prend le relais ([PedestrianDeadReckoning]), soit le repère se fige.
 *
 * Garde-fous : un écart supérieur à [SNAP_DISTANCE_M] est un vrai téléport (premier point, sortie de
 * tunnel, retour de veille) et se fait sèchement — glisser sur 200 m serait absurde. En dessous de
 * [STATIONARY_SPEED_MPS] la vitesse est forcée à zéro, sinon le bruit GPS à l'arrêt ferait dériver
 * le repère en continu.
 *
 * Tout se passe sur le thread principal (relevés postés sur le Looper principal, échantillonnage
 * depuis la phase de dessin) ; l'ancre reste néanmoins @Volatile par sécurité.
 */
class SmoothLocationEngine(
    private val deadReckoning: PedestrianDeadReckoning? = null
) {

    private class Anchor(
        val latitude: Double,
        val longitude: Double,
        val timeMs: Long,
        /** Vitesse décomposée en m/s dans le repère local est/nord. */
        val velocityEast: Double,
        val velocityNorth: Double,
        /** Écart visuel à résorber, en mètres, dans le même repère. */
        val offsetEast: Double,
        val offsetNorth: Double,
        /** Mètres par degré de longitude à cette latitude (le méridien, lui, ne varie pas). */
        val metersPerDegLon: Double,
        val speedMps: Float
    )

    @Volatile
    private var anchor: Anchor? = null
    private var previousFix: RawFix? = null

    /** true dès qu'un premier relevé est arrivé : sans ça il n'y a rien à dessiner. */
    val hasFix: Boolean get() = anchor != null

    fun reset() {
        anchor = null
        previousFix = null
        deadReckoning?.reset()
    }

    /** Enregistre un relevé GPS. À appeler à chaque arrivée de point, dans l'ordre chronologique. */
    fun onFix(fix: RawFix) {
        val previous = anchor
        // Là où le repère se trouve visuellement à l'instant du nouveau relevé : c'est de CE point
        // que le glissement doit repartir, pas du relevé précédent.
        val displayed = previous?.let { sampleFrom(it, fix.timeMs) }

        val metersPerDegLon = metersPerDegreeLongitude(fix.latitude)
        var offsetEast = 0.0
        var offsetNorth = 0.0
        if (displayed != null) {
            offsetEast = (displayed.longitude - fix.longitude) * metersPerDegLon
            offsetNorth = (displayed.latitude - fix.latitude) * METERS_PER_DEG_LAT
            if (hypot(offsetEast, offsetNorth) > SNAP_DISTANCE_M) {
                offsetEast = 0.0
                offsetNorth = 0.0
            }
        }

        val (velocityEast, velocityNorth, speed) = velocityFor(fix, previousFix)

        deadReckoning?.onGpsFix(
            timeMs = fix.timeMs,
            speedMps = speed,
            previous = previousFix,
            current = fix
        )

        anchor = Anchor(
            latitude = fix.latitude,
            longitude = fix.longitude,
            timeMs = fix.timeMs,
            velocityEast = velocityEast,
            velocityNorth = velocityNorth,
            offsetEast = offsetEast,
            offsetNorth = offsetNorth,
            metersPerDegLon = metersPerDegLon,
            speedMps = speed
        )
        previousFix = fix
    }

    /** Position à afficher à l'instant [nowMs] (même horloge monotone que les relevés). */
    fun sample(nowMs: Long): SmoothedPosition? = anchor?.let { sampleFrom(it, nowMs) }

    /**
     * true quand le repère a rejoint le dernier relevé ET n'avance plus : le rendu peut s'endormir
     * jusqu'au prochain point.
     */
    fun isIdle(nowMs: Long): Boolean {
        val current = anchor ?: return true
        if (deadReckoning?.hasPendingMotion(nowMs) == true) return false

        val elapsedMs = nowMs - current.timeMs
        // Tant que l'extrapolation avance, il y a quelque chose à animer. Passé l'horizon, le repère
        // est figé faute de relevé : inutile de continuer à réveiller le rendu.
        if (current.speedMps > 0f && elapsedMs < (MAX_EXTRAPOLATION_S * 1000).toLong()) return false

        val remaining = hypot(current.offsetEast, current.offsetNorth) * decayFactor(elapsedMs)
        return remaining < SETTLED_EPSILON_M
    }

    private fun sampleFrom(from: Anchor, nowMs: Long): SmoothedPosition {
        val elapsedS = ((nowMs - from.timeMs).coerceAtLeast(0L)) / 1000.0
        val extrapolatedS = min(elapsedS, MAX_EXTRAPOLATION_S)

        var east = from.velocityEast * extrapolatedS
        var north = from.velocityNorth * extrapolatedS

        // Passé l'horizon d'extrapolation, l'estime piétonne prend le relais si elle a vu des pas.
        var deadReckoned = false
        if (elapsedS > MAX_EXTRAPOLATION_S && deadReckoning != null) {
            val since = from.timeMs + (MAX_EXTRAPOLATION_S * 1000).toLong()
            val walked = deadReckoning.displacementSince(since, nowMs)
            if (walked.east != 0.0 || walked.north != 0.0) {
                east += walked.east
                north += walked.north
                deadReckoned = true
            }
        }

        val decay = decayFactor(nowMs - from.timeMs)
        east += from.offsetEast * decay
        north += from.offsetNorth * decay

        return SmoothedPosition(
            latitude = from.latitude + north / METERS_PER_DEG_LAT,
            longitude = from.longitude + east / from.metersPerDegLon,
            speedMps = from.speedMps,
            deadReckoned = deadReckoned
        )
    }

    private fun decayFactor(elapsedMs: Long): Double =
        exp(-(elapsedMs.coerceAtLeast(0L) / 1000.0) / OFFSET_TAU_S)

    /**
     * Vitesse retenue pour l'extrapolation : celle annoncée par le relevé si elle est exploitable,
     * sinon celle déduite du relevé précédent, sinon rien.
     */
    private fun velocityFor(fix: RawFix, previous: RawFix?): Triple<Double, Double, Float> {
        val reportedSpeed = fix.speedMps
        if (reportedSpeed != null) {
            // Le relevé annonce l'arrêt : on le croit sur parole. Reconstruire une vitesse à partir
            // de l'écart avec le point précédent ferait galoper le repère sur du simple bruit — deux
            // points distants de 10 m à l'arrêt donneraient 10 m/s.
            if (reportedSpeed < STATIONARY_SPEED_MPS) return Triple(0.0, 0.0, 0f)

            val reportedBearing = fix.bearingDeg
            if (reportedBearing != null) {
                val speed = min(reportedSpeed, MAX_SPEED_MPS)
                val rad = reportedBearing * PI / 180.0
                return Triple(speed * sin(rad), speed * cos(rad), speed)
            }
            // Vitesse sans cap : la direction se reconstruit ci-dessous.
        }

        // Pas de vitesse utilisable dans le relevé : on la reconstruit à partir du point précédent.
        if (previous != null) {
            val dtS = (fix.timeMs - previous.timeMs) / 1000.0
            if (dtS in MIN_DERIVED_INTERVAL_S..MAX_DERIVED_INTERVAL_S) {
                val metersPerDegLon = metersPerDegreeLongitude(fix.latitude)
                val east = (fix.longitude - previous.longitude) * metersPerDegLon
                val north = (fix.latitude - previous.latitude) * METERS_PER_DEG_LAT
                val speed = hypot(east, north) / dtS
                if (speed >= STATIONARY_SPEED_MPS && speed <= MAX_SPEED_MPS) {
                    return Triple(east / dtS, north / dtS, speed.toFloat())
                }
            }
        }

        // À l'arrêt : surtout ne pas extrapoler, le bruit GPS ferait vagabonder le repère.
        return Triple(0.0, 0.0, 0f)
    }

    companion object {
        /** Un degré de latitude vaut ~111,32 km partout ; la longitude, elle, se resserre aux pôles. */
        const val METERS_PER_DEG_LAT = 111_320.0

        /** Au-delà, on cesse d'avancer à l'aveugle (s). */
        const val MAX_EXTRAPOLATION_S = 1.6

        /** Constante de temps de résorption de l'écart (s) : plus c'est petit, plus ça rattrape sec. */
        const val OFFSET_TAU_S = 0.45

        /** Écart au-delà duquel on considère un vrai saut de position et non une correction (m). */
        const val SNAP_DISTANCE_M = 60.0

        /** Sous ce seuil, l'utilisateur est réputé immobile (m/s). */
        const val STATIONARY_SPEED_MPS = 0.5f

        /** Plafond de vraisemblance, contre les vitesses aberrantes (m/s, ~250 km/h). */
        const val MAX_SPEED_MPS = 70f

        private const val MIN_DERIVED_INTERVAL_S = 0.2
        private const val MAX_DERIVED_INTERVAL_S = 5.0
        private const val SETTLED_EPSILON_M = 0.15

        fun metersPerDegreeLongitude(latitude: Double): Double =
            (METERS_PER_DEG_LAT * cos(latitude * PI / 180.0)).let {
                // Aux pôles le cosinus tend vers 0 : on plafonne pour ne jamais diviser par ~zéro.
                if (abs(it) < 1.0) 1.0 else it
            }
    }
}
