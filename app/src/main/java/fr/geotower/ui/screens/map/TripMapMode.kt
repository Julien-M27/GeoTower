package fr.geotower.ui.screens.map

/**
 * Les trois façons d'ouvrir un trajet sur la carte. Des chaînes et non une énumération : c'est ce
 * qui transite dans l'argument de navigation `tripMode` et dans `rememberSaveable`.
 *
 * - [TRIP_MODE_VIEW] : on regarde la tournée. Rien ne s'ajoute en touchant la carte, et deux
 *   boutons proposent de la suivre ou de la modifier. C'est l'état par défaut, celui d'un clic sur
 *   une tournée déjà tracée.
 * - [TRIP_MODE_EDIT] : on la construit. C'est l'état d'un trajet qu'on vient de créer.
 * - [TRIP_MODE_FOLLOW] : on la parcourt sur le terrain.
 */
const val TRIP_MODE_VIEW = "view"
const val TRIP_MODE_EDIT = "edit"
const val TRIP_MODE_FOLLOW = "follow"

/** Rabat une valeur venue de la navigation sur un mode connu. */
fun tripMapModeOrDefault(raw: String?): String = when (raw) {
    TRIP_MODE_EDIT -> TRIP_MODE_EDIT
    TRIP_MODE_FOLLOW -> TRIP_MODE_FOLLOW
    else -> TRIP_MODE_VIEW
}
