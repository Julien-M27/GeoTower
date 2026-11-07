package fr.geotower.services.wear

import android.location.Location
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import fr.geotower.data.db.AppDatabase
import fr.geotower.data.models.WearNearbySiteRawRow
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
        const val PATH_PING_PHONE = "/geotower/ping_phone"
        const val PATH_PONG_PHONE = "/geotower/pong_phone"
        private const val KM_PER_LAT_DEGREE = 111.0
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val senderNodeId = messageEvent.sourceNodeId

        if (messageEvent.path == PATH_PING_PHONE) {
            Log.d(TAG, "Ping reçu de la montre ($senderNodeId), réponse pong envoyée")
            serviceScope.launch {
                runCatching {
                    Wearable.getMessageClient(this@WearablePhoneListenerService)
                        .sendMessage(senderNodeId, PATH_PONG_PHONE, byteArrayOf(1))
                        .await()
                }
            }
            return
        }

        if (messageEvent.path != PATH_REQUEST_NEARBY) {
            super.onMessageReceived(messageEvent)
            return
        }

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

        val rawRows = dao.getWearNearbySiteRows(
            minLat = centerLat - offsetLat,
            maxLat = centerLat + offsetLat,
            minLon = centerLon - offsetLon,
            maxLon = centerLon + offsetLon
        )

        val resultsArray = FloatArray(2)

        data class RowWithDistance(
            val row: WearNearbySiteRawRow,
            val distance: Float,
            val bearing: Float
        )

        val candidates = rawRows.mapNotNull { row ->
            val lat = row.latitude
            val lon = row.longitude
            Location.distanceBetween(centerLat, centerLon, lat, lon, resultsArray)
            val distance = resultsArray[0]
            val bearing = (resultsArray[1] + 360f) % 360f
            if (distance <= radiusKm * 1000.0) {
                RowWithDistance(row, distance, bearing)
            } else {
                null
            }
        }

        // Regroupement par support physique :
        // 1. id_support si présent et non vide
        // 2. sinon regroupement géographique très fin par coordonnées arrondies (~11 mètres)
        val groupedSupports = candidates.groupBy { item ->
            val idSup = item.row.idSupport?.trim()
            if (!idSup.isNullOrEmpty()) {
                "SUP_$idSup"
            } else {
                val latRound = Math.round(item.row.latitude * 10000.0)
                val lonRound = Math.round(item.row.longitude * 10000.0)
                "GEO_${latRound}_${lonRound}"
            }
        }

        val supportItems = groupedSupports.values.map { group ->
            val first = group.first()
            val minDistance = group.minOf { it.distance }
            val bearing = first.bearing

            // Adresse réelle : technique.adresse et ref_commune.nom (jamais "Code Insee")
            val street = group.mapNotNull { it.row.adresse?.trim() }.firstOrNull { it.isNotBlank() }
            val city = group.mapNotNull { it.row.commune?.trim() }.firstOrNull { it.isNotBlank() }
            val resolvedAddress = when {
                street != null && city != null -> {
                    if (street.contains(city, ignoreCase = true)) street else "$street, $city"
                }
                street != null -> street
                city != null -> city
                else -> "Site télécom"
            }

            // Type de support physique réel (ref_nature)
            val resolvedNature = group.mapNotNull { it.row.natureSupport?.trim() }
                .firstOrNull { it.isNotBlank() && !it.startsWith("Code Nature") }
                ?: group.mapNotNull { it.row.natureSupport?.trim() }.firstOrNull { it.isNotBlank() }
                ?: "Pylône autostable"

            // Hauteur du support réel
            val resolvedHeight = group.mapNotNull { it.row.hauteur?.toInt() }.firstOrNull { it > 0 } ?: 30

            // Regroupement des opérateurs présents sur ce support
            val opGroups = group.groupBy { it.row.operateur?.trim() ?: "Inconnu" }
            val operatorsList = opGroups.map { (opName, opRows) ->
                var combinedTech = 0
                var combinedBands = 0
                val azimuthsSet = linkedSetOf<Int>()

                for (opRow in opRows) {
                    combinedTech = combinedTech or (opRow.row.techMask ?: 0)
                    combinedBands = combinedBands or (opRow.row.bandMask ?: 0)
                    opRow.row.azimuts?.split(",", ";")?.forEach { azStr ->
                        azStr.replace("°", "").trim().toIntOrNull()?.let { azimuthsSet.add(it) }
                    }
                }

                val liveOrder = fr.geotower.utils.AppConfig.resolveOrderForScreen(fr.geotower.utils.ScreenPageTarget.LIVE_MONITOR)
                val techString = fr.geotower.utils.BandFormatter.formatTechnologies(combinedTech, liveOrder)
                val bandsString = fr.geotower.utils.BandFormatter.formatBands(combinedBands, combinedTech, liveOrder)
                val azSorted = azimuthsSet.sorted()

                OperatorNearbyItem(
                    name = opName,
                    technologies = techString,
                    bands = if (bandsString.isNotBlank()) bandsString else "4G / 5G",
                    azimuths = azSorted
                )
            }.sortedBy { getOperatorPriority(it.name) }

            val primaryOp = operatorsList.firstOrNull()?.name ?: "Inconnu"
            val allTechs = operatorsList.flatMap { it.technologies.split("/", ",") }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(", ")
                .ifEmpty { "4G / 5G" }

            val supportId = group.mapNotNull { it.row.idSupport?.trim() }.firstOrNull { it.isNotBlank() }
                ?: first.row.idAnfr

            TowerNearbyItem(
                id = supportId,
                operator = primaryOp,
                distanceMeters = minDistance.toInt(),
                bearingDegrees = bearing.toInt(),
                technologies = allTechs,
                address = resolvedAddress,
                latitude = first.row.latitude,
                longitude = first.row.longitude,
                supportType = resolvedNature,
                supportHeight = resolvedHeight,
                operators = operatorsList
            )
        }.sortedBy { it.distanceMeters }.take(limit)

        val jsonArray = JSONArray()
        for (item in supportItems) {
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

                val opsArr = JSONArray()
                for (op in item.operators) {
                    val opObj = JSONObject().apply {
                        put("name", op.name)
                        put("tech", op.technologies)
                        put("bands", op.bands)
                        put("az", op.azimuths.joinToString(","))
                        val azArr = JSONArray()
                        op.azimuths.forEach { azArr.put(it) }
                        put("azimuths", azArr)
                    }
                    opsArr.put(opObj)
                }
                put("operators", opsArr)
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

    private fun formatBandsString(bandMask: Int, techMask: Int): String {
        if (bandMask == 0) {
            val parts = mutableListOf<String>()
            if (techMask and 8 != 0) parts.add("5G: 3500")
            if (techMask and 4 != 0) parts.add("4G: 700, 1800, 2100")
            if (techMask and 2 != 0) parts.add("3G: 900")
            if (techMask and 1 != 0) parts.add("2G: 900")
            return if (parts.isNotEmpty()) parts.joinToString(" • ") + " MHz" else "4G: 800, 1800, 2100 MHz"
        }

        val f5g = mutableListOf<String>()
        if (bandMask and (1 shl 12) != 0) f5g.add("3500")
        if (bandMask and (1 shl 10) != 0) f5g.add("700")
        if (bandMask and (1 shl 11) != 0) f5g.add("2100")
        if (bandMask and (1 shl 21) != 0) f5g.add("1400")
        if (bandMask and (1 shl 13) != 0) f5g.add("26G")

        val f4g = mutableListOf<String>()
        if (bandMask and (1 shl 4) != 0) f4g.add("700")
        if (bandMask and (1 shl 5) != 0) f4g.add("800")
        if (bandMask and (1 shl 6) != 0) f4g.add("900")
        if (bandMask and (1 shl 7) != 0) f4g.add("1800")
        if (bandMask and (1 shl 8) != 0) f4g.add("2100")
        if (bandMask and (1 shl 9) != 0) f4g.add("2600")

        val f3g = mutableListOf<String>()
        if (bandMask and (1 shl 2) != 0) f3g.add("900")
        if (bandMask and (1 shl 3) != 0) f3g.add("2100")

        val f2g = mutableListOf<String>()
        if (bandMask and (1 shl 0) != 0) f2g.add("900")
        if (bandMask and (1 shl 1) != 0) f2g.add("1800")

        val groups = mutableListOf<String>()
        if (f5g.isNotEmpty()) groups.add("5G: ${f5g.joinToString(", ")}")
        if (f4g.isNotEmpty()) groups.add("4G: ${f4g.joinToString(", ")}")
        if (f3g.isNotEmpty()) groups.add("3G: ${f3g.joinToString(", ")}")
        if (f2g.isNotEmpty()) groups.add("2G: ${f2g.joinToString(", ")}")

        return if (groups.isNotEmpty()) groups.joinToString(" • ") + " MHz" else "4G: 800, 1800, 2100 MHz"
    }

    private fun getOperatorPriority(name: String): Int {
        val upper = name.uppercase(java.util.Locale.ROOT)
        return when {
            upper.contains("ORANGE") -> 1
            upper.contains("SFR") -> 2
            upper.contains("BOUYGUES") -> 3
            upper.contains("FREE") -> 4
            else -> 5
        }
    }

    private data class OperatorNearbyItem(
        val name: String,
        val technologies: String,
        val bands: String,
        val azimuths: List<Int>
    )

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
        val supportHeight: Int?,
        val operators: List<OperatorNearbyItem>
    )
}
