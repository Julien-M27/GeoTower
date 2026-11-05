package fr.geotower.services.wear

import android.location.Location
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import fr.geotower.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

/**
 * Service d'arrière-plan écoutant les requêtes provenant de la montre Wear OS.
 * Lorsque la montre demande les antennes à proximité en mode "Base locale téléphone",
 * ce service interroge la base Room (SQLite) ANFR et lui renvoie un JSON compact.
 */
class WearablePhoneListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "WearablePhoneListener"
        const val PATH_REQUEST_NEARBY = "/geotower/request_nearby"
        const val PATH_RESPONSE_NEARBY = "/geotower/response_nearby"
        private const val KM_PER_LAT_DEGREE = 111.0
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != PATH_REQUEST_NEARBY) {
            super.onMessageReceived(messageEvent)
            return
        }

        val senderNodeId = messageEvent.sourceNodeId
        val payload = String(messageEvent.data, Charsets.UTF_8)
        Log.d(TAG, "Requête reçue de la montre ($senderNodeId) : $payload")

        serviceScope.launch {
            try {
                val jsonReq = JSONObject(payload)
                val lat = jsonReq.getDouble("lat")
                val lon = jsonReq.getDouble("lon")
                val radiusKm = jsonReq.optDouble("radiusKm", 5.0)
                val limit = jsonReq.optInt("limit", 15)

                val resultsJson = queryNearbyTowers(lat, lon, radiusKm, limit)
                val responseBytes = resultsJson.toByteArray(Charsets.UTF_8)

                Wearable.getMessageClient(this@WearablePhoneListenerService)
                    .sendMessage(senderNodeId, PATH_RESPONSE_NEARBY, responseBytes)
                    .await()

                Log.d(TAG, "Réponse envoyée à la montre : ${resultsJson.length} octets")
            } catch (e: Exception) {
                Log.e(TAG, "Erreur lors du traitement de la requête Wearable", e)
                try {
                    val errorJson = JSONObject().apply {
                        put("error", e.message ?: "Erreur interne")
                    }.toString()
                    Wearable.getMessageClient(this@WearablePhoneListenerService)
                        .sendMessage(senderNodeId, PATH_RESPONSE_NEARBY, errorJson.toByteArray(Charsets.UTF_8))
                        .await()
                } catch (sendErr: Exception) {
                    Log.e(TAG, "Échec de l'envoi du message d'erreur", sendErr)
                }
            }
        }
    }

    private suspend fun queryNearbyTowers(
        centerLat: Double,
        centerLon: Double,
        radiusKm: Double,
        limit: Int
    ): String {
        val dao = AppDatabase.getDatabase(applicationContext).geoTowerDao()
        val safeCos = max(0.1, abs(cos(Math.toRadians(centerLat))))
        val offsetLat = radiusKm / KM_PER_LAT_DEGREE
        val offsetLon = offsetLat / safeCos

        val candidates = dao.getActiveLocalisationsInBox(
            minLat = centerLat - offsetLat,
            maxLat = centerLat + offsetLat,
            minLon = centerLon - offsetLon,
            maxLon = centerLon + offsetLon
        )

        val resultsArray = FloatArray(2)
        val sortedTowers = candidates.mapNotNull { loc ->
            val lat = loc.latitude ?: return@mapNotNull null
            val lon = loc.longitude ?: return@mapNotNull null
            Location.distanceBetween(centerLat, centerLon, lat, lon, resultsArray)
            val distance = resultsArray[0]
            val bearing = (resultsArray[1] + 360f) % 360f

            if (distance <= radiusKm * 1000.0) {
                TowerNearbyItem(
                    id = loc.idAnfr.toString(),
                    operator = loc.operateur ?: "Inconnu",
                    distanceMeters = distance.toInt(),
                    bearingDegrees = bearing.toInt(),
                    technologies = parseTechnologies(loc.techMask ?: 0),
                    address = loc.codeInsee?.let { "Code Insee: $it" } ?: "",
                    latitude = lat,
                    longitude = lon,
                    supportType = if (loc.hasUndergroundSupport == 1) "Intérieur sous-terrain" else "Pylône / Support télécom",
                    supportHeight = null
                )
            } else {
                null
            }
        }.sortedBy { it.distanceMeters }.take(limit)

        val jsonArray = JSONArray()
        for (item in sortedTowers) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("operator", item.operator)
                put("distanceMeters", item.distanceMeters)
                put("bearingDegrees", item.bearingDegrees)
                put("technologies", item.technologies)
                put("address", item.address)
                put("latitude", item.latitude)
                put("longitude", item.longitude)
                put("supportType", item.supportType)
                put("supportHeight", item.supportHeight)
            }
            jsonArray.put(obj)
        }

        return JSONObject().apply {
            put("source", "phone_db")
            put("towers", jsonArray)
        }.toString()
    }

    private fun parseTechnologies(techMask: Int): String {
        val techs = mutableListOf<String>()
        if (techMask and 1 != 0) techs.add("2G")
        if (techMask and 2 != 0) techs.add("3G")
        if (techMask and 4 != 0) techs.add("4G")
        if (techMask and 8 != 0) techs.add("5G")
        return if (techs.isEmpty()) "Mobile" else techs.joinToString(", ")
    }

    private data class TowerNearbyItem(
        val id: String,
        val operator: String,
        val distanceMeters: Int,
        val bearingDegrees: Int,
        val technologies: String,
        val address: String,
        val latitude: Double,
        val longitude: Double,
        val supportType: String,
        val supportHeight: Int?
    )
}
