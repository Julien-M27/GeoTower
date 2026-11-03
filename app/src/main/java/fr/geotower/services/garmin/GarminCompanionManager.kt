package fr.geotower.services.garmin

import android.content.Context
import android.location.Location
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.ConnectIQ.ConnectIQListener
import com.garmin.android.connectiq.ConnectIQ.IQApplicationEventListener
import com.garmin.android.connectiq.ConnectIQ.IQConnectType
import com.garmin.android.connectiq.ConnectIQ.IQDeviceEventListener
import com.garmin.android.connectiq.ConnectIQ.IQMessageStatus
import com.garmin.android.connectiq.ConnectIQ.IQSdkErrorStatus
import com.garmin.android.connectiq.ConnectIQ.IQSendMessageListener
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import fr.geotower.data.AnfrRepository
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.utils.AppFileLog
import fr.geotower.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

data class GarminConnectedWatch(
    val id: Long,
    val name: String,
    val status: IQDevice.IQDeviceStatus
)

/**
 * Gestionnaire compagnon Garmin Connect IQ pour GeoTower.
 *
 * Établit la liaison BLE avec l'application GeoTower sur la montre Garmin
 * (Forerunner 965, Forerunner 55, etc.) via le service Garmin Connect Mobile.
 *
 * Permet à la montre de recevoir les antennes relais à proximité directement
 * depuis la base de données locale (Room) du téléphone en BLE, sans nécessiter
 * de connexion Internet ni de serveur distant.
 */
object GarminCompanionManager {

    const val GARMIN_APP_UUID = "4d29f8a3-9b1c-4f76-8f3e-72bc31920e40"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connectIQ: ConnectIQ? = null
    private var repository: AnfrRepository? = null
    private val iqApp = IQApp(GARMIN_APP_UUID)

    private val _isSdkReady = MutableStateFlow(false)
    val isSdkReady: StateFlow<Boolean> = _isSdkReady.asStateFlow()

    private val _connectedWatches = MutableStateFlow<List<GarminConnectedWatch>>(emptyList())
    val connectedWatches: StateFlow<List<GarminConnectedWatch>> = _connectedWatches.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(0L)
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private val _lastStatusMessage = MutableStateFlow<String?>("Non initialisé")
    val lastStatusMessage: StateFlow<String?> = _lastStatusMessage.asStateFlow()

    private val deviceEventListener = IQDeviceEventListener { device, newStatus ->
        AppFileLog.i("GarminBridge", "Montre ${device.friendlyName} statut: $newStatus")
        refreshConnectedDevices()
    }

    private val appEventListener = IQApplicationEventListener { device, app, messageData, status ->
        if (status == IQMessageStatus.SUCCESS && messageData != null) {
            AppFileLog.i("GarminBridge", "Message reçu de la montre ${device.friendlyName} : ${messageData.size} élément(s)")
            for (item in messageData) {
                if (item is Map<*, *>) {
                    handleWatchMessage(device, app, item)
                }
            }
        } else {
            AppFileLog.i("GarminBridge", "Erreur réception message montre: $status")
        }
    }

    fun isGarminConnectInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.garmin.android.apps.connectmobile", 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun init(context: Context, anfrRepo: AnfrRepository) {
        val appContext = context.applicationContext
        if (!isGarminConnectInstalled(appContext)) {
            _isSdkReady.value = false
            _lastStatusMessage.value = "Garmin Connect non installé"
            return
        }
        if (connectIQ != null) return
        repository = anfrRepo

        try {
            val ciq = ConnectIQ.getInstance(appContext, IQConnectType.WIRELESS)
            connectIQ = ciq
            ciq.initialize(appContext, false, object : ConnectIQListener {
                override fun onSdkReady() {
                    _isSdkReady.value = true
                    _lastStatusMessage.value = "Prêt (Garmin Connect actif)"
                    AppFileLog.i("GarminBridge", "SDK Connect IQ prêt")
                    registerWithConnectedDevices()
                }

                override fun onInitializeError(errStatus: IQSdkErrorStatus) {
                    _isSdkReady.value = false
                    _lastStatusMessage.value = "Erreur initialisation: $errStatus"
                    AppFileLog.e("GarminBridge", "Erreur initialisation SDK: $errStatus")
                }

                override fun onSdkShutDown() {
                    _isSdkReady.value = false
                    _lastStatusMessage.value = "SDK arrêté"
                    _connectedWatches.value = emptyList()
                    AppFileLog.i("GarminBridge", "SDK Connect IQ arrêté")
                }
            })
        } catch (e: Exception) {
            _lastStatusMessage.value = "Exception: ${e.message}"
            AppFileLog.e("GarminBridge", "Impossible d'initialiser ConnectIQ", e)
        }
    }

    private fun registerWithConnectedDevices() {
        val ciq = connectIQ ?: return
        try {
            val devices = ciq.connectedDevices ?: emptyList()
            AppFileLog.i("GarminBridge", "Montres connectées détectées: ${devices.size}")
            for (dev in devices) {
                ciq.registerForDeviceEvents(dev, deviceEventListener)
                ciq.registerForAppEvents(dev, iqApp, appEventListener)
                AppFileLog.i("GarminBridge", "Enregistré pour événements sur: ${dev.friendlyName}")
            }
            refreshConnectedDevices()
        } catch (e: Exception) {
            AppFileLog.e("GarminBridge", "Erreur lors de l'enregistrement des montres", e)
        }
    }

    fun refreshConnectedDevices() {
        val ciq = connectIQ ?: return
        if (!_isSdkReady.value) return
        try {
            val devices = ciq.connectedDevices ?: emptyList()
            _connectedWatches.value = devices.map {
                GarminConnectedWatch(
                    id = it.deviceIdentifier,
                    name = it.friendlyName ?: "Montre Garmin",
                    status = it.status ?: IQDevice.IQDeviceStatus.UNKNOWN
                )
            }
        } catch (e: Exception) {
            AppFileLog.e("GarminBridge", "Erreur rafraîchissement des montres", e)
        }
    }

    private fun handleWatchMessage(device: IQDevice, app: IQApp, data: Map<*, *>) {
        val cmd = data["cmd"]?.toString() ?: ""
        AppFileLog.i("GarminBridge", "Commande reçue: $cmd")

        when (cmd) {
            "ping" -> {
                sendToWatch(device, app, mapOf("cmd" to "pong", "time" to System.currentTimeMillis()))
            }
            "nearby" -> {
                val lat = (data["lat"] as? Number)?.toDouble() ?: return
                val lon = (data["lon"] as? Number)?.toDouble() ?: return
                val radiusKm = (data["radius_km"] as? Number)?.toDouble() ?: 10.0
                val limit = (data["limit"] as? Number)?.toInt()?.coerceIn(1, 50) ?: 10

                scope.launch {
                    processNearbyRequest(device, app, lat, lon, radiusKm, limit)
                }
            }
        }
    }

    private suspend fun processNearbyRequest(
        device: IQDevice,
        app: IQApp,
        lat: Double,
        lon: Double,
        radiusKm: Double,
        limit: Int
    ) {
        val repo = repository ?: return
        try {
            // Lecture des antennes les plus proches
            val candidateCount = (limit * 6).coerceIn(25, 300)
            val antennas = repo.getNearest(lat, lon, candidateCount)
            if (antennas.isEmpty()) {
                sendToWatch(device, app, mapOf("sites" to emptyList<Map<String, Any>>()))
                return
            }

            val anfrIds = antennas.map { it.idAnfr }.distinct()
            val techniqueById = repo.getTechniqueSummariesByIds(anfrIds)
            val physiqueById = repo.getPhysiqueSummariesByIds(anfrIds)

            // Regroupement par site physique réel (coordonnées arrondies à ~11 mètres)
            val groups = antennas.groupBy {
                String.format(Locale.US, "%.4f_%.4f", it.latitude, it.longitude)
            }

            val maxRadiusMeters = (radiusKm * 1000.0).roundToInt()
            val sitesList = mutableListOf<Map<String, Any>>()

            for ((_, group) in groups) {
                val main = group.first()
                val distMeters = calculateDistanceMeters(lat, lon, main.latitude, main.longitude)
                if (distMeters > maxRadiusMeters) continue

                val bearingDeg = calculateBearingDegrees(lat, lon, main.latitude, main.longitude)
                val physiques = group.flatMap { physiqueById[it.idAnfr].orEmpty() }
                val techniques = group.mapNotNull { techniqueById[it.idAnfr] }

                val supCode = getGarminSupportCode(physiques)
                val supName = physiques.firstOrNull { !it.natureSupport.isNullOrBlank() }?.natureSupport?.trim()
                    ?: "Pylône autostable"
                val height = physiques.mapNotNull { it.hauteur }.filter { it > 0.0 }.maxOrNull()?.roundToInt() ?: 0

                val fullAddress = techniques.firstOrNull { !it.adresse.isNullOrBlank() }?.adresse?.trim() ?: ""
                val locationName = main.codeInsee?.let { "Insee $it" } ?: ""

                val ops = buildGarminOperators(group, techniques, physiques)
                if (ops.isEmpty()) continue

                val siteMap = HashMap<String, Any>()
                siteMap["id"] = main.idAnfr.toLongOrNull() ?: 0L
                siteMap["d"] = distMeters
                siteMap["az"] = bearingDeg
                siteMap["lat"] = main.latitude
                siteMap["lon"] = main.longitude
                siteMap["sup"] = supCode
                siteMap["support_name"] = supName
                siteMap["h"] = height
                siteMap["loc"] = locationName
                siteMap["addr"] = if (fullAddress.isNotBlank()) fullAddress else locationName
                siteMap["ops"] = ops

                sitesList.add(siteMap)
            }

            // Trier par distance et limiter
            sitesList.sortBy { (it["d"] as? Int) ?: 0 }
            val finalSites = sitesList.take(limit)

            val payload = mapOf("sites" to finalSites)
            AppFileLog.i("GarminBridge", "Envoi de ${finalSites.size} site(s) BLE à la montre")
            sendToWatch(device, app, payload)
            _lastSyncTimestamp.value = System.currentTimeMillis()
        } catch (e: Exception) {
            AppFileLog.e("GarminBridge", "Erreur lors du traitement nearby pour la montre", e)
        }
    }

    private fun buildGarminOperators(
        antennas: List<LocalisationEntity>,
        techniques: List<TechniqueEntity>,
        physiques: List<PhysiqueEntity>
    ): List<Map<String, Any>> {
        val byOperator = antennas.groupBy { it.operateur?.uppercase()?.trim() ?: "" }
        val result = mutableListOf<Map<String, Any>>()

        for ((rawOp, opAntennas) in byOperator) {
            val opId = when {
                rawOp.contains("ORANGE") -> 1
                rawOp.contains("SFR") -> 2
                rawOp.contains("BOUYGUES") -> 3
                rawOp.contains("FREE") -> 4
                else -> 5
            }

            val techMask = opAntennas.fold(0) { acc, a -> acc or a.techMask }
            val bandMask = opAntennas.fold(0) { acc, a -> acc or a.bandMask }

            val techs = mutableListOf<String>()
            if (techMask and 1 != 0) techs.add("2G")
            if (techMask and 2 != 0) techs.add("3G")
            if (techMask and 4 != 0) techs.add("4G")
            if (techMask and 8 != 0) techs.add("5G")
            val techStr = if (techs.isNotEmpty()) techs.joinToString(" / ") else "4G"

            val bandsStr = formatBandsString(bandMask, techMask)
            val azimuths = opAntennas.flatMap { parseAzimuths(it.azimuts) }.distinct().sorted()

            val opMap = HashMap<String, Any>()
            opMap["id"] = opId
            opMap["t"] = techStr
            opMap["bands"] = bandsStr
            opMap["az"] = azimuths
            result.add(opMap)
        }

        // Tri standard opérateurs (Orange, SFR, Bouygues, Free, Autres)
        result.sortBy { (it["id"] as? Int) ?: 99 }
        return result
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

    private fun parseAzimuths(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.replace(";", ",").replace("-", ",").split(",")
            .mapNotNull { token ->
                token.trim().removeSuffix("°").toIntOrNull()?.let { (it % 360 + 360) % 360 }
            }
    }

    private fun getGarminSupportCode(physiques: List<PhysiqueEntity>): Int {
        for (p in physiques) {
            val s = p.natureSupport?.lowercase(Locale.ROOT) ?: continue
            if (s.contains("pylone") || s.contains("pylône") || s.contains("mât") || s.contains("mat")) return 1
            if (s.contains("chateau d'eau") || s.contains("château d'eau") || s.contains("reservoir") || s.contains("réservoir")) return 2
            if (s.contains("terrasse") || s.contains("toit") || s.contains("immeuble") || s.contains("batiment") || s.contains("bâtiment")) return 3
            if (s.contains("clocher") || s.contains("eglise") || s.contains("église") || s.contains("cathedrale") || s.contains("cathédrale")) return 4
            if (s.contains("eolienne") || s.contains("éolienne")) return 5
        }
        return 1
    }

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val res = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, res)
        return res[0].roundToInt()
    }

    private fun calculateBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val res = FloatArray(2)
        Location.distanceBetween(lat1, lon1, lat2, lon2, res)
        val bearing = res[1]
        return ((bearing % 360f + 360f) % 360f).roundToInt()
    }

    private fun sendToWatch(device: IQDevice, app: IQApp, payload: Any) {
        val ciq = connectIQ ?: return
        try {
            ciq.sendMessage(device, app, payload, object : IQSendMessageListener {
                override fun onMessageStatus(dev: IQDevice, a: IQApp, status: IQMessageStatus) {
                    AppFileLog.i("GarminBridge", "Statut envoi message vers ${dev.friendlyName}: $status")
                }
            })
        } catch (e: Exception) {
            AppFileLog.e("GarminBridge", "Erreur lors de l'envoi du message à la montre", e)
        }
    }
}
