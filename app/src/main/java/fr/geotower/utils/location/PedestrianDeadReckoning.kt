package fr.geotower.utils.location

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Navigation à l'estime piétonne : fait avancer le repère quand le GPS se tait (passage sous un
 * porche, rue en canyon urbain, tunnel piéton), en comptant les pas.
 *
 * Pourquoi les pas et pas l'accéléromètre « brut » : intégrer deux fois une accélération pour en
 * tirer une position dérive de plusieurs mètres en quelques secondes, c'est inexploitable. Compter
 * les pas et les multiplier par une longueur d'enjambée, en revanche, tient la route sur la
 * vingtaine de mètres qui nous intéresse ici.
 *
 * La détection se fait sur [android.hardware.Sensor.TYPE_ACCELEROMETER] et NON sur
 * `TYPE_STEP_DETECTOR`, qui exigerait la permission `ACTIVITY_RECOGNITION` depuis Android 10 — une
 * demande de permission disproportionnée pour du confort visuel.
 *
 * Classe volontairement sans type Android (elle reçoit des nombres) : le rattachement aux capteurs
 * se fait à l'appelant, et l'algorithme reste testable en JVM.
 *
 * Garde-fous : rien n'est enregistré hors régime piéton (au-delà de [PEDESTRIAN_SPEED_MAX_MPS], les
 * vibrations d'un véhicule seraient comptées comme des pas), et le déplacement cumulé est plafonné à
 * [MAX_DRIFT_M] — passé cette distance sans GPS, l'estime a trop dérivé pour qu'on lui fasse encore
 * confiance, le repère se fige.
 */
class PedestrianDeadReckoning {

    /** Déplacement cumulé, en mètres, dans le repère local est/nord. */
    data class Displacement(val east: Double, val north: Double)

    // --- Détection de pas -------------------------------------------------------
    private var lastSampleMs = 0L
    private var gravityMagnitude = Double.NaN
    private var filtered = 0.0
    private var armed = false
    private var peakValue = 0.0
    private var peakTimeMs = 0L
    private var lastStepMs = 0L

    // --- Contexte fourni de l'extérieur -----------------------------------------
    private var headingDeg: Float? = null
    private var lastGpsSpeedMps = 0f
    private var stepLengthFactor = 1.0

    // --- Historique des pas (tampon circulaire) ---------------------------------
    private val stepTimeMs = LongArray(STEP_HISTORY)
    private val stepEast = DoubleArray(STEP_HISTORY)
    private val stepNorth = DoubleArray(STEP_HISTORY)
    private val stepLength = DoubleArray(STEP_HISTORY)
    private var stepCount = 0

    fun reset() {
        lastSampleMs = 0L
        gravityMagnitude = Double.NaN
        filtered = 0.0
        armed = false
        lastStepMs = 0L
        stepCount = 0
        headingDeg = null
        lastGpsSpeedMps = 0f
    }

    /** Cap courant en degrés (0 = nord). Alimenté par la boussole déjà lissée de la carte. */
    fun setHeading(degrees: Float) {
        headingDeg = degrees
    }

    /**
     * Un échantillon d'accéléromètre. [timeMs] doit être `SystemClock.elapsedRealtime()` et NON
     * `SensorEvent.timestamp` : les pas datés ici sont comparés aux horodatages des relevés GPS, et
     * la base de temps des capteurs n'est pas la même sur tous les appareils.
     */
    fun onAccelerometerSample(timeMs: Long, x: Float, y: Float, z: Float) {
        val magnitude = sqrt((x * x + y * y + z * z).toDouble())

        if (gravityMagnitude.isNaN()) {
            gravityMagnitude = magnitude
            lastSampleMs = timeMs
            return
        }

        val dtS = ((timeMs - lastSampleMs).coerceIn(1L, 200L)) / 1000.0
        lastSampleMs = timeMs

        // Deux passe-bas exprimés en constantes de temps, pour rester justes quelle que soit la
        // cadence réellement servie par le capteur : le premier isole la pesanteur, le second
        // débruite ce qu'il en reste.
        gravityMagnitude += (magnitude - gravityMagnitude) * (1.0 - exp(-dtS / GRAVITY_TAU_S))
        val alternating = magnitude - gravityMagnitude
        filtered += (alternating - filtered) * (1.0 - exp(-dtS / SMOOTHING_TAU_S))

        // Détection du sommet d'oscillation : on s'arme au-dessus du seuil haut, on retient le
        // maximum, et on valide le pas en retombant sous le seuil bas (hystérésis = pas de
        // double-comptage sur une oscillation bruitée).
        if (!armed) {
            if (filtered > PEAK_THRESHOLD) {
                armed = true
                peakValue = filtered
                peakTimeMs = timeMs
            }
        } else {
            if (filtered > peakValue) {
                peakValue = filtered
                peakTimeMs = timeMs
            }
            if (filtered < RELEASE_THRESHOLD) {
                armed = false
                registerStep(peakTimeMs)
            }
        }
    }

    /** Contexte GPS : régime de marche et recalage de la longueur d'enjambée. */
    fun onGpsFix(timeMs: Long, speedMps: Float, previous: RawFix?, current: RawFix) {
        lastGpsSpeedMps = speedMps
        calibrate(previous, current)
        // Un GPS franchement en mouvement rend l'estime inutile : on repart d'une page blanche pour
        // que le prochain décrochage ne traîne pas des pas périmés.
        if (speedMps > PEDESTRIAN_SPEED_MAX_MPS) {
            stepCount = 0
            armed = false
        }
    }

    /**
     * Déplacement accumulé par les pas postérieurs à [fromMs], vu à l'instant [nowMs].
     *
     * Chaque pas est étalé sur [STEP_RAMP_MS] au lieu d'être ajouté d'un bloc : sans cela le repère
     * avancerait par à-coups de 70 cm, exactement le défaut que l'on cherche à supprimer.
     */
    fun displacementSince(fromMs: Long, nowMs: Long): Displacement {
        var east = 0.0
        var north = 0.0
        val available = min(stepCount, STEP_HISTORY)
        for (offset in 0 until available) {
            val index = ((stepCount - 1 - offset) % STEP_HISTORY + STEP_HISTORY) % STEP_HISTORY
            val time = stepTimeMs[index]
            if (time <= fromMs) break // le tampon est chronologique : plus rien à trouver au-delà
            if (time > nowMs) continue
            val ramp = ((nowMs - time).toDouble() / STEP_RAMP_MS).coerceIn(0.0, 1.0)
            east += stepEast[index] * ramp
            north += stepNorth[index] * ramp
        }

        val distance = hypot(east, north)
        if (distance > MAX_DRIFT_M) {
            val scale = MAX_DRIFT_M / distance
            return Displacement(east * scale, north * scale)
        }
        return Displacement(east, north)
    }

    /** true si un pas est encore en train d'être étalé : le rendu ne doit pas s'endormir. */
    fun hasPendingMotion(nowMs: Long): Boolean =
        stepCount > 0 && (nowMs - lastStepMs) < STEP_RAMP_MS

    private fun registerStep(timeMs: Long) {
        val intervalMs = timeMs - lastStepMs
        if (lastStepMs != 0L && intervalMs < MIN_STEP_INTERVAL_MS) return // cadence impossible
        lastStepMs = timeMs

        // Hors régime piéton (voiture, train), les vibrations passeraient pour des pas : on garde la
        // cadence à jour mais on n'enregistre aucun déplacement.
        if (lastGpsSpeedMps > PEDESTRIAN_SPEED_MAX_MPS) return
        val heading = headingDeg ?: return

        val cadenceHz = if (intervalMs in MIN_STEP_INTERVAL_MS..MAX_STEP_INTERVAL_MS) {
            1000.0 / intervalMs
        } else {
            DEFAULT_CADENCE_HZ
        }

        val length = (stepLengthForCadence(cadenceHz) * stepLengthFactor)
            .coerceIn(MIN_STEP_LENGTH_M, MAX_STEP_LENGTH_M)
        val rad = heading * PI / 180.0

        val index = stepCount % STEP_HISTORY
        stepTimeMs[index] = timeMs
        stepEast[index] = length * sin(rad)
        stepNorth[index] = length * cos(rad)
        stepLength[index] = length
        stepCount++
    }

    /**
     * Recale la longueur d'enjambée sur le GPS quand celui-ci est fiable : chacun marche avec sa
     * foulée, et 20 % d'erreur sur 25 m se voient.
     */
    private fun calibrate(previous: RawFix?, current: RawFix) {
        if (previous == null) return
        val dtMs = current.timeMs - previous.timeMs
        if (dtMs < CALIBRATION_MIN_INTERVAL_MS || dtMs > CALIBRATION_MAX_INTERVAL_MS) return

        val accuracy = current.accuracyM ?: return
        val previousAccuracy = previous.accuracyM ?: return
        if (accuracy > CALIBRATION_MAX_ACCURACY_M || previousAccuracy > CALIBRATION_MAX_ACCURACY_M) return

        val metersPerDegLon = SmoothLocationEngine.metersPerDegreeLongitude(current.latitude)
        val east = (current.longitude - previous.longitude) * metersPerDegLon
        val north = (current.latitude - previous.latitude) * SmoothLocationEngine.METERS_PER_DEG_LAT
        val travelled = hypot(east, north)
        val speed = travelled / (dtMs / 1000.0)
        if (speed < CALIBRATION_MIN_SPEED_MPS || speed > PEDESTRIAN_SPEED_MAX_MPS) return

        var predicted = 0.0
        var counted = 0
        val available = min(stepCount, STEP_HISTORY)
        for (offset in 0 until available) {
            val index = ((stepCount - 1 - offset) % STEP_HISTORY + STEP_HISTORY) % STEP_HISTORY
            val time = stepTimeMs[index]
            if (time <= previous.timeMs) break
            if (time > current.timeMs) continue
            predicted += stepLength[index]
            counted++
        }
        if (counted < CALIBRATION_MIN_STEPS || predicted <= 0.0) return

        val ratio = travelled / predicted
        if (ratio < CALIBRATION_MIN_RATIO || ratio > CALIBRATION_MAX_RATIO) return
        stepLengthFactor = (stepLengthFactor + (ratio - stepLengthFactor) * CALIBRATION_GAIN)
            .coerceIn(MIN_LENGTH_FACTOR, MAX_LENGTH_FACTOR)
    }

    companion object {
        /** Modèle usuel : on allonge la foulée quand on accélère la cadence. */
        fun stepLengthForCadence(cadenceHz: Double): Double = 0.20 + 0.28 * cadenceHz

        private const val STEP_HISTORY = 128

        // Détection
        private const val GRAVITY_TAU_S = 0.5
        private const val SMOOTHING_TAU_S = 0.06
        private const val PEAK_THRESHOLD = 1.2      // m/s² au-dessus de la pesanteur
        private const val RELEASE_THRESHOLD = 0.3
        private const val MIN_STEP_INTERVAL_MS = 240L
        private const val MAX_STEP_INTERVAL_MS = 1500L
        private const val DEFAULT_CADENCE_HZ = 1.8

        // Enjambée
        private const val MIN_STEP_LENGTH_M = 0.40
        private const val MAX_STEP_LENGTH_M = 0.95
        private const val MIN_LENGTH_FACTOR = 0.70
        private const val MAX_LENGTH_FACTOR = 1.40

        // Étalement d'un pas dans le rendu
        private const val STEP_RAMP_MS = 400L

        /** Au-delà, on n'est plus à pied et la détection n'a plus de sens (m/s, ~9 km/h). */
        private const val PEDESTRIAN_SPEED_MAX_MPS = 2.6f

        /** Distance maximale que l'on accepte de parcourir sans GPS (m). */
        private const val MAX_DRIFT_M = 25.0

        // Recalage
        private const val CALIBRATION_MIN_INTERVAL_MS = 700L
        private const val CALIBRATION_MAX_INTERVAL_MS = 3000L
        private const val CALIBRATION_MAX_ACCURACY_M = 20f
        private const val CALIBRATION_MIN_SPEED_MPS = 0.6
        private const val CALIBRATION_MIN_STEPS = 2
        private const val CALIBRATION_MIN_RATIO = 0.5
        private const val CALIBRATION_MAX_RATIO = 2.0
        private const val CALIBRATION_GAIN = 0.12
    }
}
