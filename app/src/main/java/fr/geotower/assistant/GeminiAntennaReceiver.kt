package fr.geotower.assistant

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import fr.geotower.GeoTowerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver permettant à Gemini et Google Assistant d'interroger la base locale
 * d'antennes relais GeoTower de façon totalement transparente en tâche de fond.
 */
class GeminiAntennaReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext as GeoTowerApp

        // Récupération souple de l'opérateur (différentes clés selon Assistant / Gemini / BII)
        val rawOperator = intent?.getStringExtra(EXTRA_OPERATOR_NAME)
            ?: intent?.getStringExtra(EXTRA_QUERY)
            ?: intent?.getStringExtra(EXTRA_THING_NAME)

        receiverScope.launch {
            try {
                val locationHelper = DeviceLocationHelper(appContext)
                val handler = AntennaVoiceQueryHandler(appContext.repository, locationHelper)

                val (results, speechText) = handler.queryNearestAntennas(
                    operatorFilter = rawOperator,
                    limit = 1
                )

                Log.i(TAG, "Requête vocale traitée : op='$rawOperator', réponse='$speechText'")

                val resultBundle = Bundle().apply {
                    putString(KEY_SPEECH_RESPONSE, speechText)
                    if (results.isNotEmpty()) {
                        val first = results.first()
                        putString(KEY_OPERATOR, first.operator)
                        putInt(KEY_DISTANCE_METERS, first.distanceMeters)
                        putString(KEY_DIRECTION, first.directionLabel)
                        putStringArrayList(KEY_TECHNOLOGIES, ArrayList(first.technologies))
                        putDouble(KEY_LATITUDE, first.latitude)
                        putDouble(KEY_LONGITUDE, first.longitude)
                    }
                }

                pendingResult.setResult(
                    Activity.RESULT_OK,
                    speechText,
                    resultBundle
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Erreur lors du traitement de la requête vocale", t)
                pendingResult.setResult(
                    Activity.RESULT_CANCELED,
                    "Une erreur est survenue lors de la recherche d'antenne.",
                    null
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "GeminiAntennaReceiver"

        const val ACTION_VOICE_QUERY = "fr.geotower.action.VOICE_QUERY_ANTENNA"
        const val EXTRA_OPERATOR_NAME = "operator_name"
        const val EXTRA_QUERY = "query"
        const val EXTRA_THING_NAME = "thing.name"

        const val KEY_SPEECH_RESPONSE = "android.speech.extra.VOICE_SEARCH_RESULT"
        const val KEY_OPERATOR = "operator"
        const val KEY_DISTANCE_METERS = "distance_meters"
        const val KEY_DIRECTION = "direction"
        const val KEY_TECHNOLOGIES = "technologies"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
    }
}
