package fr.geotower.utils

import android.hardware.SensorManager

/**
 * Source de vérité unique du mode « faible consommation ».
 *
 * Trois niveaux : 0 Normal · 1 Éco (équilibré) · 2 Éco+ (agressif). Le niveau EFFECTIF ([level])
 * combine le réglage manuel ([AppConfig.lowPowerLevel]) et, si l'utilisateur l'a activé
 * ([AppConfig.lowPowerFollowSystem]), l'économie d'énergie d'Android ([SystemPower]) qui force au
 * moins le niveau Éco.
 *
 * Chaque curseur est exposé ici en valeur SÉMANTIQUE (ex. [mapReloadDebounceMs]) : les call sites
 * liront PowerProfile au lieu de constantes en dur, ce qui garde un seul point à faire évoluer et
 * permet d'ajuster/ajouter des niveaux sans les retoucher.
 *
 * NB : les optimisations de perf « gratuites » (couverture hors-thread, culling overlay, index bâti…)
 * sont TOUJOURS actives, indépendamment de ce mode — elles ne passent pas par PowerProfile.
 *
 * Lecture depuis un @Composable : les getters lisent des états Compose ([AppConfig], [SystemPower]),
 * donc l'UI se recompose quand le niveau change. Lecture depuis un service/worker : valeur ponctuelle.
 */
object PowerProfile {

    const val LEVEL_NORMAL = 0
    const val LEVEL_ECO = 1
    const val LEVEL_ECO_PLUS = 2

    /** Niveau effectif 0/1/2 = max(réglage manuel, niveau imposé par le système si suivi activé). */
    val level: Int
        get() {
            val manual = AppConfig.lowPowerLevel.intValue
            val auto = if (AppConfig.lowPowerFollowSystem.value && SystemPower.isSaveMode) LEVEL_ECO else LEVEL_NORMAL
            return maxOf(manual, auto)
        }

    val isEco: Boolean get() = level >= LEVEL_ECO
    val isEcoPlus: Boolean get() = level >= LEVEL_ECO_PLUS

    // --- Localisation / capteurs -------------------------------------------------
    /** GPS live en priorité « équilibrée » (BALANCED) plutôt que haute précision. */
    val gpsBalanced: Boolean get() = isEco
    /** Plancher d'intervalle (s) pour le suivi live (0 = ne rien forcer, garder le réglage utilisateur). */
    val liveIntervalFloorSeconds: Int get() = when {
        isEcoPlus -> 20
        isEco -> 15
        else -> 0
    }
    /** Cadence du capteur boussole. */
    val compassSensorDelay: Int get() = if (isEco) SensorManager.SENSOR_DELAY_UI else SensorManager.SENSOR_DELAY_GAME

    // --- Carte -------------------------------------------------------------------
    /** Débounce (ms) du rechargement des antennes au pan/zoom. */
    val mapReloadDebounceMs: Long get() = if (isEco) 450L else 180L
    /** Dessiner les cônes/lobes d'azimut (masqués dès le niveau Éco ; sinon suit le réglage utilisateur). */
    val drawAzimuthCones: Boolean get() = if (isEco) false else AppConfig.showAzimuthsCone.value
    /** Plafond de marqueurs affichés simultanément. */
    val mapMarkerCap: Int get() = if (isEco) 2000 else 6000
    /** Rotation continue du repère de position selon la boussole (figée en Éco). */
    val mapCompassRotation: Boolean get() = !isEco
    /** N'utiliser que les tuiles en cache/hors-ligne (zéro réseau tuiles) — Éco+ uniquement. */
    val mapTilesOfflineOnly: Boolean get() = isEcoPlus

    // --- Réseau / données --------------------------------------------------------
    /** Fenêtre (jours) des points de couverture communautaire SignalQuest. */
    val coveragePointDays: Int get() = if (isEco) 90 else 365
    /** Couverture théorique forcée en qualité « aperçu » + obstacles off — Éco+ uniquement. */
    val coverageQualityPreview: Boolean get() = isEcoPlus

    // --- Interface ---------------------------------------------------------------
    /** Animations riches (indicateurs de chargement expressifs, fondus de bord) — coupées dès Éco. */
    val richAnimations: Boolean get() = !isEco
    /** Transitions d'écran instantanées (plus de fondu) — Éco+ uniquement. */
    val instantScreenTransitions: Boolean get() = isEcoPlus
}
