package fr.geotower.data.upload

import android.content.SharedPreferences
import fr.geotower.data.api.SignalQuestOperators
import fr.geotower.data.community.CommunityDataPreferences
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.utils.OperatorColors

/**
 * Un opérateur d'un support vers qui l'on peut envoyer des photos, avec ce qu'il faut pour bâtir
 * l'envoi : la cible SignalQuest, une station de référence pour la position, et les azimuts.
 */
data class SupportSharedPhotoUploadOperator(
    val key: String,
    val label: String,
    val uploadOperator: String,
    val antenna: LocalisationEntity,
    // Azimuts de TOUTES les stations de cet operateur sur le support : `antenna` n'en porte qu'une,
    // et la confirmation d'envoi doit montrer tout ce qui appartient a l'operateur.
    val azimuts: String
)

/**
 * Les opérateurs d'un support qui acceptent un envoi de photos.
 *
 * Partagé par la fiche support et par l'arrivée sur une étape de tournée : les règles sont trop
 * fines pour être réécrites deux fois. Un support mutualisé porte plusieurs opérateurs ; deux
 * opérateurs locaux peuvent viser la même cible SignalQuest (SRR et SFR…), auquel cas leurs azimuts
 * fusionnent ; et chaque opérateur peut être coupé individuellement dans les réglages.
 */
fun supportSharedPhotoUploadOperators(
    antennas: List<LocalisationEntity>,
    prefs: SharedPreferences
): List<SupportSharedPhotoUploadOperator> {
    val operatorsByKey = linkedMapOf<String, SupportSharedPhotoUploadOperator>()

    antennas.forEach { antenna ->
        OperatorColors.keysFor(antenna.operateur).forEach { key ->
            val known = operatorsByKey[key]
            if (known != null) {
                // Station supplementaire du meme operateur : on garde la premiere pour la position,
                // mais ses azimuts rejoignent ceux deja retenus.
                operatorsByKey[key] = known.copy(
                    azimuts = SignalQuestUploadTargets.mergeAzimuts(known.azimuts, antenna.azimuts)
                )
                return@forEach
            }

            val uploadOperator = SignalQuestOperators.operatorParamFor(key) ?: return@forEach
            if (!CommunityDataPreferences.isSignalQuestPhotosEnabled(prefs, key)) return@forEach

            operatorsByKey[key] = SupportSharedPhotoUploadOperator(
                key = key,
                label = OperatorColors.specForKey(key)?.label ?: uploadOperator,
                uploadOperator = uploadOperator,
                antenna = antenna,
                azimuts = SignalQuestUploadTargets.mergeAzimuts(antenna.azimuts)
            )
        }
    }

    return OperatorColors.orderedKeys.mapNotNull { operatorsByKey[it] } +
        operatorsByKey.values.filterNot { it.key in OperatorColors.orderedKeys }
}
