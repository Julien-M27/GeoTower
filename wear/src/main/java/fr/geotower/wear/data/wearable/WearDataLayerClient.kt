package fr.geotower.wear.data.wearable

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import fr.geotower.wear.data.model.WearTower
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * Client Wearable Data Layer communicant avec le smartphone via Bluetooth / Wi-Fi local.
 */
class WearDataLayerClient(private val context: Context) {

    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    companion object {
        private const val TAG = "WearDataLayerClient"
        const val PATH_REQUEST_NEARBY = "/geotower/request_nearby"
        const val PATH_RESPONSE_NEARBY = "/geotower/response_nearby"
        // 12 secondes pour laisser le temps au smartphone de se réveiller et d'interroger SQLite
        private const val TIMEOUT_MS = 12000L
    }

    suspend fun fetchNearbyFromPhone(lat: Double, lon: Double, radiusKm: Double = 5.0, limit: Int = 15): List<WearTower> = withContext(Dispatchers.IO) {
        val nodes = nodeClient.connectedNodes.await()
        if (nodes.isEmpty()) {
            throw RuntimeException("Aucun smartphone connecté en Bluetooth")
        }

        // On prend le nœud de proximité directe en priorité (le smartphone appairé)
        val phoneNode = nodes.find { it.isNearby } ?: nodes.first()
        val requestJson = JSONObject().apply {
            put("lat", lat)
            put("lon", lon)
            put("radiusKm", radiusKm)
            put("limit", limit)
        }.toString()

        val responseDeferred = CompletableDeferred<String>()

        val listener = object : MessageClient.OnMessageReceivedListener {
            override fun onMessageReceived(event: MessageEvent) {
                if (event.path == PATH_RESPONSE_NEARBY) {
                    val body = String(event.data, Charsets.UTF_8)
                    Log.d(TAG, "Réponse brute reçue du téléphone (${body.length} caractères)")
                    responseDeferred.complete(body)
                }
            }
        }

        try {
            messageClient.addListener(listener).await()
            messageClient.sendMessage(phoneNode.id, PATH_REQUEST_NEARBY, requestJson.toByteArray(Charsets.UTF_8)).await()
            Log.d(TAG, "Requête envoyée au téléphone (${phoneNode.displayName}, id=${phoneNode.id})")

            val rawResponse = try {
                withTimeout(TIMEOUT_MS) {
                    responseDeferred.await()
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                throw RuntimeException("Le smartphone ne répond pas. Assurez-vous que GeoTower y est installé et à jour.")
            }

            return@withContext parsePhoneResponse(rawResponse)
        } finally {
            try {
                messageClient.removeListener(listener)
            } catch (e: Exception) {
                Log.w(TAG, "Erreur suppression listener", e)
            }
        }
    }

    private fun parsePhoneResponse(rawJson: String): List<WearTower> {
        val root = JSONObject(rawJson)
        if (root.has("error")) {
            throw RuntimeException(root.getString("error"))
        }

        val towersArray = root.getJSONArray("towers")
        val towers = mutableListOf<WearTower>()

        for (i in 0 until towersArray.length()) {
            val obj = towersArray.getJSONObject(i)
            val opName = obj.optString("operator", "Inconnu")
            val techStr = obj.optString("technologies", "Mobile")

            // Support multi-opérateur si transmis par le smartphone
            val opList = mutableListOf<fr.geotower.wear.data.model.WearOperatorSite>()
            if (obj.has("operators")) {
                val arr = obj.getJSONArray("operators")
                for (j in 0 until arr.length()) {
                    val o = arr.getJSONObject(j)
                    opList.add(
                        fr.geotower.wear.data.model.WearOperatorSite(
                            operator = o.optString("name", opName),
                            technologies = o.optString("tech", techStr),
                            bands = o.optString("bands", "700, 1800, 2100 MHz")
                        )
                    )
                }
            } else {
                opList.add(
                    fr.geotower.wear.data.model.WearOperatorSite(
                        operator = opName,
                        technologies = techStr
                    )
                )
            }

            towers.add(
                WearTower(
                    id = obj.getString("id"),
                    distanceMeters = obj.getInt("distanceMeters"),
                    bearingDegrees = obj.getInt("bearingDegrees"),
                    address = obj.optString("address", ""),
                    latitude = obj.optDouble("latitude", 0.0),
                    longitude = obj.optDouble("longitude", 0.0),
                    supportType = obj.optString("supportType", "Pylône autostable"),
                    supportHeight = if (obj.has("supportHeight") && !obj.isNull("supportHeight")) obj.getInt("supportHeight") else 35,
                    operators = opList,
                    source = "phone_db"
                )
            )
        }
        return towers
    }
}
