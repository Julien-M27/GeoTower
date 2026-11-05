package fr.geotower.services.garmin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
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
import com.garmin.monkeybrains.serialization.Serializer
import fr.geotower.data.AnfrRepository
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.utils.AppFileLog
import fr.geotower.utils.AppLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
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
 *
 * Supporte la segmentation multi-trames (chunking) pour acheminer jusqu'à
 * 50 sites de manière fluide sans saturer le buffer radio BLE (8 Ko max).
 */
object GarminCompanionManager {

    const val GARMIN_APP_UUID = "4d29f8a3-9b1c-4f76-8f3e-72bc31920e40"

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _lastReceivedPayload = MutableStateFlow<String?>("Aucun message reçu")
    val lastReceivedPayload: StateFlow<String?> = _lastReceivedPayload.asStateFlow()

    fun log(tag: String, msg: String, tr: Throwable? = null) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.FRANCE).format(Date())
        val line = if (tr != null) {
            "[$time] [$tag] ⚠️ $msg : ${tr.message ?: tr::class.java.simpleName}\n${tr.stackTraceToString()}"
        } else {
            "[$time] [$tag] $msg"
        }
        if (tr != null) {
            _lastError.value = "[$time] $msg : ${tr.message ?: tr::class.java.simpleName}"
            AppFileLog.e(tag, msg, tr)
        } else {
            AppFileLog.i(tag, msg)
        }
        _logs.update { current ->
            (current + line).takeLast(60)
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
        _lastError.value = null
    }

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        log("GarminBridge", "Exception non capturée dans coroutine Garmin", throwable)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)
    private var appContext: Context? = null
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

    const val PREF_GARMIN_BACKGROUND_SERVICE = "garmin_background_service_enabled"
    const val PREF_GARMIN_BACKGROUND_DURATION = "garmin_background_duration_ms"
    const val PREF_GARMIN_SERVICE_START_TIME = "garmin_service_start_time"

    val DURATION_CHOICES = listOf(
        0L to "Illimité",
        1 * 3600 * 1000L to "1h",
        2 * 3600 * 1000L to "2h",
        4 * 3600 * 1000L to "4h",
        8 * 3600 * 1000L to "8h"
    )

    private val _isBackgroundServiceEnabled = MutableStateFlow(false)
    val isBackgroundServiceEnabled: StateFlow<Boolean> = _isBackgroundServiceEnabled.asStateFlow()

    private val _backgroundDurationMs = MutableStateFlow(0L)
    val backgroundDurationMs: StateFlow<Long> = _backgroundDurationMs.asStateFlow()

    private val _serviceStartTimeMs = MutableStateFlow(0L)
    val serviceStartTimeMs: StateFlow<Long> = _serviceStartTimeMs.asStateFlow()

    fun setBackgroundServiceEnabled(context: Context, enabled: Boolean) {
        val appCtx = context.applicationContext
        _isBackgroundServiceEnabled.value = enabled
        val now = if (enabled) System.currentTimeMillis() else 0L
        _serviceStartTimeMs.value = now
        val prefs = appCtx.getSharedPreferences("geotower_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(PREF_GARMIN_BACKGROUND_SERVICE, enabled)
            .putLong(PREF_GARMIN_SERVICE_START_TIME, now)
            .apply()
        log("GarminBridge", "Liaison montre écran éteint configurée : $enabled (start: $now)")
        if (enabled) {
            GarminCompanionService.start(appCtx)
        } else {
            GarminCompanionService.stop(appCtx)
        }
    }

    fun setBackgroundDuration(context: Context, durationMs: Long) {
        val appCtx = context.applicationContext
        _backgroundDurationMs.value = durationMs
        val prefs = appCtx.getSharedPreferences("geotower_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong(PREF_GARMIN_BACKGROUND_DURATION, durationMs).apply()
        log("GarminBridge", "Durée liaison montre configurée : $durationMs ms")
        if (_isBackgroundServiceEnabled.value) {
            // Relancer pour mettre à jour la notification et le timer d'arrêt automatique
            GarminCompanionService.start(appCtx)
        }
    }

    private val deviceEventListener: IQDeviceEventListener = IQDeviceEventListener { device, newStatus ->
        runCatching {
            val name = device?.friendlyName ?: "Inconnue"
            log("GarminBridge", "Montre $name statut: $newStatus")
            if (device != null && newStatus == IQDevice.IQDeviceStatus.CONNECTED) {
                connectIQ?.let { ciq ->
                    try {
                        ciq.registerForEvents(device, deviceEventListener, iqApp, appEventListener)
                    } catch (e: Exception) {
                        log("GarminBridge", "Erreur auto-reconnect $name", e)
                    }
                }
            }
            refreshConnectedDevices()
        }.onFailure { e ->
            log("GarminBridge", "Erreur deviceEventListener", e)
        }
    }

    private val appEventListener: IQApplicationEventListener = IQApplicationEventListener { device, app, messageData, status ->
        runCatching {
            val name = device?.friendlyName ?: "Inconnue"
            if (status == IQMessageStatus.SUCCESS && messageData != null && device != null && app != null) {
                log("GarminBridge", "Message reçu de la montre $name : ${messageData.size} élément(s)")
                for (item in messageData) {
                    if (item is Map<*, *>) {
                        handleWatchMessage(device, app, item)
                    } else {
                        log("GarminBridge", "Élément message inattendu (non Map): ${item?.javaClass?.simpleName}")
                    }
                }
            } else {
                log("GarminBridge", "Statut réception message montre $name: $status")
            }
        }.onFailure { e ->
            log("GarminBridge", "Erreur appEventListener", e)
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
        val appCtx = context.applicationContext
        appContext = appCtx
        if (!isGarminConnectInstalled(appCtx)) {
            _isSdkReady.value = false
            _lastStatusMessage.value = "Garmin Connect non installé"
            log("GarminBridge", "Garmin Connect Mobile non détecté sur le téléphone")
            return
        }
        if (connectIQ != null) return
        repository = anfrRepo

        val prefs = appCtx.getSharedPreferences("geotower_prefs", Context.MODE_PRIVATE)
        val bgEnabled = prefs.getBoolean(PREF_GARMIN_BACKGROUND_SERVICE, false)
        val durationMs = prefs.getLong(PREF_GARMIN_BACKGROUND_DURATION, 0L)
        val startTime = prefs.getLong(PREF_GARMIN_SERVICE_START_TIME, 0L)
        _backgroundDurationMs.value = durationMs
        _serviceStartTimeMs.value = startTime

        if (bgEnabled && durationMs > 0L && startTime > 0L && (System.currentTimeMillis() - startTime) >= durationMs) {
            _isBackgroundServiceEnabled.value = false
            _serviceStartTimeMs.value = 0L
            prefs.edit().putBoolean(PREF_GARMIN_BACKGROUND_SERVICE, false).putLong(PREF_GARMIN_SERVICE_START_TIME, 0L).apply()
            log("GarminBridge", "Durée liaison expirée pendant l'arrêt de l'application")
        } else {
            _isBackgroundServiceEnabled.value = bgEnabled
            if (bgEnabled) {
                GarminCompanionService.start(appCtx)
            }
        }

        log("GarminBridge", "Initialisation du SDK Connect IQ...")

        try {
            val ciq = ConnectIQ.getInstance(appCtx, IQConnectType.WIRELESS)
            connectIQ = ciq
            ciq.initialize(appCtx, false, object : ConnectIQListener {
                override fun onSdkReady() {
                    _isSdkReady.value = true
                    _lastStatusMessage.value = "Prêt (Garmin Connect actif)"
                    log("GarminBridge", "SDK Connect IQ prêt et opérationnel")

                    registerWithConnectedDevices()
                }

                override fun onInitializeError(errStatus: IQSdkErrorStatus) {
                    _isSdkReady.value = false
                    _lastStatusMessage.value = "Erreur initialisation: $errStatus"
                    log("GarminBridge", "Erreur initialisation SDK: $errStatus")
                }

                override fun onSdkShutDown() {
                    _isSdkReady.value = false
                    _lastStatusMessage.value = "SDK arrêté"
                    _connectedWatches.value = emptyList()
                    log("GarminBridge", "SDK Connect IQ arrêté")
                }
            })

            // Récepteur direct BroadcastReceiver pour garantir la réception même si le SDK filtre
            try {
                val filter = IntentFilter("com.garmin.android.connectiq.INCOMING_MESSAGE")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appCtx.registerReceiver(rawBroadcastReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    appCtx.registerReceiver(rawBroadcastReceiver, filter)
                }
                log("GarminBridge", "Récepteur broadcast direct enregistré")
            } catch (e: Exception) {
                log("GarminBridge", "Erreur enregistrement récepteur direct", e)
            }
        } catch (e: Exception) {
            _lastStatusMessage.value = "Exception: ${e.message}"
            log("GarminBridge", "Impossible d'initialiser ConnectIQ", e)
        }
    }

    private val rawBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val action = intent.action ?: return
            if (action == "com.garmin.android.connectiq.INCOMING_MESSAGE") {
                try {
                    val dev = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra("com.garmin.android.connectiq.EXTRA_REMOTE_DEVICE", IQDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra("com.garmin.android.connectiq.EXTRA_REMOTE_DEVICE")
                    }
                    val app = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra("com.garmin.android.connectiq.EXTRA_REMOTE_APPLICATION", IQApp::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra("com.garmin.android.connectiq.EXTRA_REMOTE_APPLICATION")
                    }
                    val payloadBytes = intent.getByteArrayExtra("com.garmin.android.connectiq.EXTRA_PAYLOAD")
                    if (dev != null && app != null && payloadBytes != null) {
                        val deserialized = Serializer.deserialize(payloadBytes)
                        log("GarminBridge", "Broadcast direct reçu de ${dev.friendlyName}: ${deserialized.size} élément(s)")
                        for (item in deserialized) {
                            if (item is Map<*, *>) {
                                handleWatchMessage(dev, app, item)
                            }
                        }
                    }
                } catch (e: Throwable) {
                    log("GarminBridge", "Erreur traitement broadcast direct", e)
                }
            }
        }
    }

    private fun registerWithConnectedDevices() {
        val ciq = connectIQ ?: return
        try {
            val connected = runCatching { ciq.connectedDevices }.getOrNull() ?: emptyList()
            val known = runCatching { ciq.knownDevices }.getOrNull() ?: emptyList()
            val allDevices = (connected + known).distinctBy { it.deviceIdentifier }
            log("GarminBridge", "Montres détectées (${connected.size} connectée(s), ${known.size} connue(s))")

            for (dev in allDevices) {
                try {
                    ciq.registerForDeviceEvents(dev, deviceEventListener)
                    ciq.registerForAppEvents(dev, iqApp, appEventListener)
                    log("GarminBridge", "Enregistré pour événements sur: ${dev.friendlyName} (id=${dev.deviceIdentifier})")
                } catch (e: Exception) {
                    log("GarminBridge", "Impossible d'enregistrer ${dev.friendlyName}", e)
                }
            }

            refreshConnectedDevices()
        } catch (e: Exception) {
            log("GarminBridge", "Erreur lors de l'enregistrement des montres", e)
        }
    }

    fun refreshConnectedDevices() {
        val ciq = connectIQ ?: return
        if (!_isSdkReady.value) return
        try {
            val connected = runCatching { ciq.connectedDevices }.getOrNull() ?: emptyList()
            val known = runCatching { ciq.knownDevices }.getOrNull() ?: emptyList()
            val allDevices = (connected + known).distinctBy { it.deviceIdentifier }
            _connectedWatches.value = allDevices.map {
                GarminConnectedWatch(
                    id = it.deviceIdentifier,
                    name = it.friendlyName ?: "Montre Garmin",
                    status = it.status ?: IQDevice.IQDeviceStatus.UNKNOWN
                )
            }
        } catch (e: Exception) {
            log("GarminBridge", "Erreur rafraîchissement des montres", e)
        }
    }

    fun pingConnectedWatches() {
        val ciq = connectIQ
        if (ciq == null) {
            log("GarminBridge", "Ping impossible : ConnectIQ non initialisé")
            return
        }
        val connected = runCatching { ciq.connectedDevices }.getOrNull() ?: emptyList()
        val known = runCatching { ciq.knownDevices }.getOrNull() ?: emptyList()
        val allDevices = (connected + known).distinctBy { it.deviceIdentifier }
        log("GarminBridge", "Envoi d'un ping test vers ${allDevices.size} montre(s)...")
        if (allDevices.isEmpty()) {
            log("GarminBridge", "Aucune montre Garmin détectée par ConnectIQ.")
            return
        }
        for (dev in allDevices) {
            log("GarminBridge", "Ping vers ${dev.friendlyName} (id=${dev.deviceIdentifier})...")
            sendToWatch(dev, iqApp, mapOf("cmd" to "ping", "time" to System.currentTimeMillis()))
        }
    }

    private var lastHandledPayload: String? = null
    private var lastHandledTimestamp: Long = 0L

    private fun handleWatchMessage(device: IQDevice, app: IQApp, data: Map<*, *>) {
        val payloadKey = "${device.deviceIdentifier}_$data"
        val now = System.currentTimeMillis()
        if (payloadKey == lastHandledPayload && now - lastHandledTimestamp < 1500L) {
            return
        }
        lastHandledPayload = payloadKey
        lastHandledTimestamp = now

        runCatching {
            val cmd = data["cmd"]?.toString() ?: ""
            _lastReceivedPayload.value = "Cmd '$cmd' de ${device.friendlyName} ($data)"
            log("GarminBridge", "Commande reçue: '$cmd' de ${device.friendlyName}")

            when (cmd) {
                "ping" -> {
                    log("GarminBridge", "Réponse 'pong' immédiate envoyée")
                    sendToWatch(device, app, mapOf("cmd" to "pong", "time" to System.currentTimeMillis()))
                }
                "nearby" -> {
                    val lat = (data["lat"] as? Number)?.toDouble()
                        ?: data["lat"]?.toString()?.toDoubleOrNull()
                    val lon = (data["lon"] as? Number)?.toDouble()
                        ?: data["lon"]?.toString()?.toDoubleOrNull()
                    val radiusKm = (data["radius_km"] as? Number)?.toDouble()
                        ?: data["radius_km"]?.toString()?.toDoubleOrNull()
                        ?: 10.0
                    val limit = (data["limit"] as? Number)?.toInt()
                        ?: data["limit"]?.toString()?.toIntOrNull()?.coerceIn(1, 50)
                        ?: 10

                    if (lat == null || lon == null) {
                        log("GarminBridge", "Coordonnées invalides reçues de la montre: lat=$lat, lon=$lon")
                        return
                    }

                    log("GarminBridge", "Requête 'nearby' : lat=$lat, lon=$lon, rayon=${radiusKm}km, max=$limit sites")
                    val powerManager = appContext?.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                    val wakeLock = powerManager?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "geotower:garmin_bridge")
                    wakeLock?.acquire(15_000L)

                    scope.launch {
                        try {
                            processNearbyRequest(device, app, lat, lon, radiusKm, limit)
                        } catch (t: Throwable) {
                            log("GarminBridge", "Erreur critique dans processNearbyRequest", t)
                        } finally {
                            if (wakeLock?.isHeld == true) {
                                runCatching { wakeLock.release() }
                            }
                        }
                    }
                }
                else -> {
                    log("GarminBridge", "Commande non reconnue: '$cmd'")
                }
            }
        }.onFailure { e ->
            log("GarminBridge", "Erreur dans handleWatchMessage", e)
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
        val repo = repository
        if (repo == null) {
            log("GarminBridge", "Erreur: AnfrRepository non injecté !")
            return
        }
        try {
            val effectiveLimit = limit.coerceIn(1, 50)
            val candidateCount = (effectiveLimit * 6).coerceIn(25, 300)
            log("GarminBridge", "Recherche des $candidateCount candidats les plus proches dans Room...")
            val antennas = repo.getNearest(lat, lon, candidateCount)
            log("GarminBridge", "${antennas.size} antenne(s) brute(s) trouvée(s)")

            if (antennas.isEmpty()) {
                log("GarminBridge", "Aucune antenne trouvée dans le périmètre. Envoi liste vide.")
                sendToWatchAsync(
                    device,
                    app,
                    mapOf("sites" to emptyList<Map<String, Any>>(), "chunk" to 0, "total_chunks" to 1)
                )
                return
            }

            val anfrIds = antennas.map { it.idAnfr }.distinct()
            val techniqueById = runCatching { repo.getTechniqueSummariesByIds(anfrIds) }.getOrDefault(emptyMap())
            val physiqueById = runCatching { repo.getPhysiqueSummariesByIds(anfrIds) }.getOrDefault(emptyMap())

            // Regroupement par site physique réel (coordonnées arrondies à ~11 mètres)
            val groups = antennas.groupBy {
                String.format(Locale.US, "%.4f_%.4f", it.latitude, it.longitude)
            }
            log("GarminBridge", "${groups.size} site(s) physique(s) distinct(s) identifié(s)")

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
                siteMap["id"] = main.idAnfr.toIntOrNull() ?: 0
                siteMap["d"] = distMeters
                siteMap["az"] = bearingDeg
                siteMap["lat"] = main.latitude
                siteMap["lon"] = main.longitude
                siteMap["sup"] = supCode
                siteMap["support_name"] = supName.take(35)
                siteMap["h"] = height
                siteMap["loc"] = locationName.take(30)
                siteMap["addr"] = (if (fullAddress.isNotBlank()) fullAddress else locationName).take(45)
                siteMap["ops"] = ops

                sitesList.add(siteMap)
            }

            // Trier par distance et limiter au nombre demandé (jusqu'à 50)
            sitesList.sortBy { (it["d"] as? Int) ?: 0 }
            val finalSites = sitesList.take(effectiveLimit)
            log("GarminBridge", "${finalSites.size} site(s) sélectionné(s) (limite=$effectiveLimit)")

            if (finalSites.isEmpty()) {
                log("GarminBridge", "Aucun site dans le rayon de ${radiusKm}km. Envoi liste vide.")
                sendToWatchAsync(
                    device,
                    app,
                    mapOf("sites" to emptyList<Map<String, Any>>(), "chunk" to 0, "total_chunks" to 1)
                )
                return
            }

            // Découpage en trames BLE sécurisées de 15 sites max (~5 Ko par trame)
            val chunkSize = 15
            val chunks = finalSites.chunked(chunkSize)
            log("GarminBridge", "Envoi BLE progressif de ${finalSites.size} site(s) en ${chunks.size} trame(s)")

            for ((index, chunk) in chunks.withIndex()) {
                val chunkPayload = mapOf(
                    "sites" to chunk,
                    "chunk" to index,
                    "total_chunks" to chunks.size
                )
                log("GarminBridge", "Envoi trame ${index + 1}/${chunks.size} (${chunk.size} sites)...")
                val success = sendToWatchAsync(device, app, chunkPayload)
                log("GarminBridge", "Trame ${index + 1}/${chunks.size} -> ${if (success) "Reçue par la montre (SUCCESS)" else "ÉCHEC / Timeout"}")
                if (!success) {
                    log("GarminBridge", "Arrêt envoi trames : échec de transmission à la trame ${index + 1}")
                    break
                }
                if (index < chunks.size - 1) {
                    delay(120L)
                }
            }

            _lastSyncTimestamp.value = System.currentTimeMillis()
        } catch (e: Exception) {
            log("GarminBridge", "Erreur lors du traitement nearby pour la montre", e)
            runCatching {
                sendToWatchAsync(
                    device,
                    app,
                    mapOf("sites" to emptyList<Map<String, Any>>(), "chunk" to 0, "total_chunks" to 1)
                )
            }
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
            opMap["bands"] = bandsStr.take(35)
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

    private suspend fun sendToWatchAsync(device: IQDevice?, app: IQApp?, payload: Any): Boolean {
        val ciq = connectIQ ?: return false
        if (device == null || app == null) return false
        return try {
            val deferred = CompletableDeferred<Boolean>()
            ciq.sendMessage(device, app, payload, object : IQSendMessageListener {
                override fun onMessageStatus(dev: IQDevice?, a: IQApp?, status: IQMessageStatus?) {
                    runCatching {
                        val name = dev?.friendlyName ?: "Montre"
                        AppFileLog.i("GarminBridge", "Statut envoi trame vers $name: $status")
                        deferred.complete(status == IQMessageStatus.SUCCESS)
                    }.onFailure {
                        deferred.complete(false)
                    }
                }
            })
            withTimeoutOrNull(3000L) { deferred.await() } ?: false
        } catch (t: Throwable) {
            AppFileLog.e("GarminBridge", "Exception sendToWatchAsync", t)
            false
        }
    }

    private fun sendToWatch(device: IQDevice?, app: IQApp?, payload: Any) {
        val ciq = connectIQ ?: return
        if (device == null || app == null) return
        try {
            ciq.sendMessage(device, app, payload, object : IQSendMessageListener {
                override fun onMessageStatus(dev: IQDevice?, a: IQApp?, status: IQMessageStatus?) {
                    runCatching {
                        val name = dev?.friendlyName ?: "Montre"
                        AppFileLog.i("GarminBridge", "Statut envoi message vers $name: $status")
                    }
                }
            })
        } catch (t: Throwable) {
            AppFileLog.e("GarminBridge", "Erreur lors de l'envoi du message à la montre", t)
        }
    }
}
