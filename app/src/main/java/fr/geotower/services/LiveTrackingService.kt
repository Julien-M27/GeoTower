package fr.geotower.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import fr.geotower.MainActivity
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.data.db.AppDatabase
import fr.geotower.data.db.GeoTowerDao
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.utils.AppStrings
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LiveTrackingService : Service() {

    private val liveTrackingChannelV3 = "live_tracking_channel_v3"
    private val notificationId = 1001

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var repository: AnfrRepository

    private var serviceStartTime: Long = 0L
    private var processingJob: Job? = null
    private var lastProcessedLocation: Location? = null
    private var lastProcessedAt: Long = 0L
    private var lockedOperator: String? = null
    private var lockedAntennaId: String? = null
    private var initialDistance: Float? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        repository = AnfrRepository(
            api = fr.geotower.data.api.RetrofitClient.apiService,
            context = applicationContext
        )
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            return stopTrackingAndSelf()
        }

        if (serviceStartTime == 0L) {
            serviceStartTime = System.currentTimeMillis()
        }

        val defaultOp = currentOperator
        if (
            defaultOp == "AUCUN" ||
            !LiveTrackingController.hasPreciseLocationPermission(this) ||
            !LiveTrackingController.hasPostNotificationsPermission(this)
        ) {
            return stopTrackingAndSelf()
        }

        resetIfOperatorChanged(defaultOp)

        val initialNotification = if (supportsProgressStyle()) {
            buildLiveNotification(
                contentText = AppStrings.searchInProgress(this),
                progress = 0,
                operator = defaultOp,
                antLoc = null,
                address = ""
            )
        } else {
            buildNotification(
                contentText = AppStrings.searchInProgress(this),
                userLoc = null,
                antLoc = null,
                operator = defaultOp,
                progress = 0,
                address = ""
            )
        }

        if (!startAsForeground(initialNotification)) {
            return START_NOT_STICKY
        }
        startLocationUpdates()

        return START_STICKY
    }

    private fun startLocationUpdates() {
        if (!hasFineLocationPermission()) {
            stopTrackingAndSelf()
            return
        }

        stopLocationUpdates()
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                processLocationUpdate(location)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            android.util.Log.w("LiveTracking", "Location permission revoked before updates could start: ${e.message}")
            stopTrackingAndSelf()
        }
    }

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun processLocationUpdate(location: Location) {
        val defaultOp = currentOperator
        if (defaultOp == "AUCUN") {
            stopTrackingAndSelf()
            return
        }

        if (!shouldProcessLocation(location)) return
        lastProcessedLocation = Location(location)
        lastProcessedAt = System.currentTimeMillis()

        processingJob?.cancel()

        processingJob = serviceScope.launch {
            try {
                val dao = AppDatabase.getDatabase(applicationContext).geoTowerDao()
                val opAntennas = findActiveAntennasByRadius(dao, location, defaultOp)

                if (opAntennas.isEmpty()) {
                    updateNotification(
                        text = AppStrings.noAntennaFound(applicationContext, defaultOp),
                        userLoc = null,
                        antLoc = null,
                        operator = defaultOp,
                        progress = 0,
                        address = ""
                    )
                    return@launch
                }

                if (lockedOperator != defaultOp) {
                    lockedOperator = defaultOp
                    lockedAntennaId = null
                    initialDistance = null
                }

                var minDistance = Float.MAX_VALUE
                var closestAntenna = opAntennas.first()
                var bearingToClosest = 0f

                opAntennas.forEach { ant ->
                    val results = FloatArray(2)
                    Location.distanceBetween(location.latitude, location.longitude, ant.latitude, ant.longitude, results)
                    if (results[0] < minDistance) {
                        minDistance = results[0]
                        closestAntenna = ant
                        bearingToClosest = results[1]
                    }
                }

                val currentId = closestAntenna.idAnfr
                if (lockedAntennaId != currentId) {
                    lockedAntennaId = currentId
                    initialDistance = minDistance
                }

                val progress = if (initialDistance != null && initialDistance!! > 10f) {
                    ((1f - (minDistance / initialDistance!!).coerceIn(0f, 1f)) * 100).toInt()
                } else if (minDistance <= 10f) {
                    100
                } else {
                    0
                }

                val baseDistanceStr = formatDistance(minDistance)
                var bearing = bearingToClosest
                if (bearing < 0) bearing += 360f
                val directions = arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
                val index = Math.round(bearing / 45.0).toInt() % 8
                val directionStr = directions[index]
                val degreeStr = "${Math.round(bearing)}°"
                val distanceWithDirectionStr = "$baseDistanceStr • $directionStr ($degreeStr)"

                val technique = repository.getTechniqueDetails(closestAntenna.idAnfr)
                val fullAddress = technique?.adresse ?: ""

                updateNotification(
                    text = AppStrings.antennaDistance(applicationContext, defaultOp, distanceWithDirectionStr),
                    userLoc = location,
                    antLoc = closestAntenna,
                    operator = defaultOp,
                    progress = progress,
                    address = fullAddress
                )
            } catch (_: CancellationException) {
                // Newer GPS updates replace older calculations.
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun shouldProcessLocation(location: Location): Boolean {
        val previous = lastProcessedLocation ?: return true
        val elapsedMs = System.currentTimeMillis() - lastProcessedAt
        return elapsedMs >= MIN_PROCESS_INTERVAL_MS ||
            previous.distanceTo(location) >= MIN_PROCESS_DISTANCE_METERS
    }

    private suspend fun findActiveAntennasByRadius(
        dao: GeoTowerDao,
        location: Location,
        operator: String
    ): List<LocalisationEntity> {
        val safeCos = max(0.1, abs(cos(Math.toRadians(location.latitude))))

        SEARCH_RADII_KM.forEach { radiusKm ->
            val radiusMeters = (radiusKm * 1000.0).toFloat()
            val offsetLat = radiusKm / KM_PER_LATITUDE_DEGREE
            val offsetLon = offsetLat / safeCos
            val candidates = dao.getActiveLocalisationsInBoxByOperator(
                operatorName = operator,
                minLat = location.latitude - offsetLat,
                maxLat = location.latitude + offsetLat,
                minLon = location.longitude - offsetLon,
                maxLon = location.longitude + offsetLon
            )

            val insideRadius = candidates.filter { antenna ->
                val results = FloatArray(1)
                Location.distanceBetween(
                    location.latitude,
                    location.longitude,
                    antenna.latitude,
                    antenna.longitude,
                    results
                )
                results[0] <= radiusMeters
            }
            if (insideRadius.isNotEmpty()) return insideRadius
        }

        return emptyList()
    }

    private fun updateNotification(
        text: String,
        userLoc: Location?,
        antLoc: LocalisationEntity?,
        operator: String,
        progress: Int = 0,
        address: String = ""
    ) {
        if (!LiveTrackingController.hasPostNotificationsPermission(this)) {
            stopTrackingAndSelf()
            return
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = if (supportsProgressStyle()) {
            buildLiveNotification(text, progress, operator, antLoc, address)
        } else {
            buildNotification(text, userLoc, antLoc, operator, progress, address)
        }

        manager.notify(notificationId, notification)
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun buildLiveNotification(
        contentText: String,
        progress: Int,
        operator: String,
        antLoc: LocalisationEntity?,
        address: String
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationRequestCode(antLoc),
            notificationIntent(antLoc),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LiveTrackingService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val progressStyle = Notification.ProgressStyle()
            .setProgress(progress)
            .setProgressTrackerIcon(
                IconCompat.createWithResource(this, R.drawable.ic_live_user_tracker).toIcon(this)
            )
            .setProgressSegments(
                listOf(Notification.ProgressStyle.Segment(100).setColor(operatorColor(operator)))
            )

        operatorLogo(operator)?.let { logoResId ->
            progressStyle.setProgressEndIcon(IconCompat.createWithResource(this, logoResId).toIcon(this))
        }

        val hasAddress = address.isNotBlank()
        val liveTitle = if (hasAddress) contentText else AppStrings.nearestAntennaTitle(this)
        val liveContentText = if (hasAddress) notificationAddressText(address) else contentText
        val shortCriticalText = extractShortCriticalText(contentText)
        val builder = Notification.Builder(this, liveTrackingChannelV3)
            .setContentTitle(liveTitle)
            .setContentText(liveContentText)
            .setSmallIcon(R.drawable.geotower_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setColor(operatorColor(operator))
            .setStyle(progressStyle)
            .addAction(
                Notification.Action.Builder(
                    IconCompat.createWithResource(this, R.drawable.ic_launcher_monochrome).toIcon(this),
                    AppStrings.quitAction(this),
                    stopPendingIntent
                ).build()
            )

        runCatching {
            Notification.Builder::class.java
                .getMethod("setForegroundServiceBehavior", Int::class.javaPrimitiveType)
                .invoke(builder, Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        runCatching {
            Notification.Builder::class.java
                .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                .invoke(builder, true)
        }
        shortCriticalText?.let { text ->
            runCatching {
                Notification.Builder::class.java
                    .getMethod("setShortCriticalText", String::class.java)
                    .invoke(builder, text)
            }
        }

        return builder.build().apply {
            extras.putBoolean("android.requestPromotedOngoing", true)
            shortCriticalText?.let { extras.putString("android.shortCriticalText", it) }
            extras.putAll(
                samsungOngoingActivityExtras(
                    operator = operator,
                    progress = progress,
                    primaryInfo = liveTitle,
                    secondaryInfo = liveContentText,
                    shortCriticalText = shortCriticalText
                )
            )
        }
    }

    private fun buildNotification(
        contentText: String,
        userLoc: Location?,
        antLoc: LocalisationEntity?,
        operator: String,
        progress: Int = 0,
        address: String = ""
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationRequestCode(antLoc),
            notificationIntent(antLoc),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LiveTrackingService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val expandedText = notificationContentText(contentText, address)

        val builder = NotificationCompat.Builder(this, liveTrackingChannelV3)
            .setContentTitle(AppStrings.nearestAntennaTitle(this))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.geotower_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setWhen(serviceStartTime)
            .setUsesChronometer(true)
            .setColor(operatorColor(operator))
            .setProgress(100, progress, false)
            .addAction(0, AppStrings.quitAction(this), stopPendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))

        return builder.build().apply {
            extras.putAll(
                samsungOngoingActivityExtras(
                    operator = operator,
                    progress = progress,
                    primaryInfo = contentText,
                    secondaryInfo = expandedText,
                    shortCriticalText = extractShortCriticalText(contentText)
                )
            )
        }
    }

    private fun notificationIntent(antLoc: LocalisationEntity?): Intent {
        return Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            val siteId = antLoc?.idAnfr?.takeIf { it.isNotBlank() }
            if (siteId != null) {
                data = Uri.parse("geotower://site/${Uri.encode(siteId)}")
                putExtra("TARGET_SITE_ID", siteId)
            } else {
                putExtra("widget_dest", "nearby")
            }
        }
    }

    private fun notificationRequestCode(antLoc: LocalisationEntity?): Int {
        return antLoc?.idAnfr?.hashCode() ?: notificationId
    }

    private fun notificationContentText(contentText: String, address: String): String {
        val cleanAddress = address.trim()
        return if (cleanAddress.isBlank()) {
            contentText
        } else {
            "$contentText\n${notificationAddressText(cleanAddress)}"
        }
    }

    private fun notificationAddressText(address: String): String {
        val parts = address
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return when {
            parts.isEmpty() -> address.trim()
            parts.size == 1 -> parts.first()
            else -> "${parts.dropLast(1).joinToString(", ")}\n${parts.last()}"
        }
    }

    private fun samsungOngoingActivityExtras(
        operator: String,
        progress: Int,
        primaryInfo: String,
        secondaryInfo: String,
        shortCriticalText: String?
    ): Bundle {
        if (!isSamsungDevice()) return Bundle.EMPTY

        val chipText = samsungChipText(shortCriticalText, primaryInfo)
        val drawerPrimaryInfo = samsungDrawerPrimaryInfo(chipText)
        val compactSecondary = secondaryInfo
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_SAMSUNG_NOW_BAR_TEXT_LENGTH)
        val operatorIcon = operatorLogo(operator)?.let { logoResId ->
            IconCompat.createWithResource(this, logoResId).toIcon(this)
        }
        val trackerIcon = IconCompat.createWithResource(this, R.drawable.ic_live_user_tracker).toIcon(this)
        val progressSegment = Bundle().apply {
            putFloat("android.ongoingActivityNoti.progressSegments.segmentStart", 0.0f)
            putInt("android.ongoingActivityNoti.progressSegments.segmentColor", operatorColor(operator))
        }

        return Bundle().apply {
            putInt("android.ongoingActivityNoti.style", 1)
            putString("android.ongoingActivityNoti.primaryInfo", drawerPrimaryInfo)
            putString("android.ongoingActivityNoti.secondaryInfo", compactSecondary)
            putInt("android.ongoingActivityNoti.progress", progress)
            putInt("android.ongoingActivityNoti.progressMax", 100)
            putParcelable("android.ongoingActivityNoti.progressSegments.icon", trackerIcon)
            putInt("android.ongoingActivityNoti.progressSegments.progressColor", operatorColor(operator))
            putParcelableArray("android.ongoingActivityNoti.progressSegments", arrayOf(progressSegment))
            putInt("android.ongoingActivityNoti.actionType", 1)
            putInt("android.ongoingActivityNoti.actionPrimarySet", 0)
            putString("android.ongoingActivityNoti.nowbarPrimaryInfo", chipText)
            putString("android.ongoingActivityNoti.nowbarSecondaryInfo", compactSecondary)
            putString("android.ongoingActivityNoti.chipExpandedText", chipText)
            putInt("android.ongoingActivityNoti.chipBgColor", operatorColor(operator))
            operatorIcon?.let { icon ->
                putParcelable("android.ongoingActivityNoti.chipIcon", icon)
                putParcelable("android.ongoingActivityNoti.nowbarIcon", icon)
                putParcelable("android.ongoingActivityNoti.secondIcon", icon)
            }
        }
    }

    private fun supportsProgressStyle(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
    }

    private fun isSamsungDevice(): Boolean {
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }

    private fun samsungChipText(shortCriticalText: String?, fallback: String): String {
        return (shortCriticalText ?: fallback)
            .take(MAX_SAMSUNG_CHIP_TEXT_LENGTH)
    }

    private fun samsungDrawerPrimaryInfo(chipText: String): String {
        return "${AppStrings.nearestAntennaTitle(this)} • $chipText"
            .take(MAX_SAMSUNG_DRAWER_TITLE_LENGTH)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                liveTrackingChannelV3,
                AppStrings.liveTrackingChannelDesc(this),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(null, null)
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                vibrationPattern = longArrayOf(0L)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startAsForeground(notification: Notification): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    notificationId,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(notificationId, notification)
            }
            true
        } catch (e: SecurityException) {
            android.util.Log.w("LiveNotif", "Impossible de demarrer le service de localisation: ${e.message}")
            stopTrackingAndSelf()
            false
        }
    }

    private fun stopLocationUpdates() {
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    private fun stopTrackingAndSelf(): Int {
        processingJob?.cancel()
        stopLocationUpdates()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
        return START_NOT_STICKY
    }

    private fun resetIfOperatorChanged(operator: String) {
        if (lockedOperator != null && lockedOperator != operator) {
            processingJob?.cancel()
            lastProcessedLocation = null
            lastProcessedAt = 0L
            lockedOperator = null
            lockedAntennaId = null
            initialDistance = null
        }
    }

    private fun isMiles(context: Context): Boolean {
        val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
        return try {
            prefs.getInt("distance_unit", 0) == 1
        } catch (_: Exception) {
            prefs.getString("distance_unit", "0") == "1"
        }
    }

    private val currentOperator: String
        get() {
            val prefs = getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
            val rawOp = prefs.getString("default_operator", "Aucun")?.uppercase() ?: "AUCUN"
            return when {
                rawOp.contains("ORANGE") -> "ORANGE"
                rawOp.contains("BOUYGUES") -> "BOUYGUES"
                rawOp.contains("SFR") -> "SFR"
                rawOp.contains("FREE") -> "FREE"
                else -> rawOp
            }
        }

    private fun formatDistance(distanceMeters: Float): String {
        return if (isMiles(applicationContext)) {
            String.format(Locale.US, "%.2f mi", distanceMeters / 1609.34f)
        } else {
            if (distanceMeters >= 1000) String.format(Locale.US, "%.1f km", distanceMeters / 1000f)
            else "${distanceMeters.toInt()} m"
        }
    }

    private fun extractShortCriticalText(contentText: String): String? {
        val distanceRegex = Regex("""\d+(?:[.,]\d+)?\s?(?:km|mi|m)""", RegexOption.IGNORE_CASE)
        return distanceRegex.find(contentText)
            ?.value
            ?.replace(" ", "")
            ?.take(7)
            ?.ifBlank { null }
    }

    private fun operatorColor(operator: String): Int {
        return when {
            operator.contains("ORANGE") -> android.graphics.Color.parseColor("#FF7900")
            operator.contains("BOUYGUES") -> android.graphics.Color.parseColor("#009FE3")
            operator.contains("SFR") -> android.graphics.Color.parseColor("#E2001A")
            operator.contains("FREE") -> android.graphics.Color.parseColor("#55565A")
            else -> android.graphics.Color.GRAY
        }
    }

    private fun operatorLogo(operator: String): Int? {
        return when {
            operator.contains("ORANGE") -> R.drawable.logo_orange
            operator.contains("BOUYGUES") -> R.drawable.logo_bouygues
            operator.contains("SFR") -> R.drawable.logo_sfr
            operator.contains("FREE") -> R.drawable.logo_free
            else -> null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        processingJob?.cancel()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopLocationUpdates()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile
        internal var isRunning: Boolean = false
            private set

        private const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        private const val MIN_PROCESS_INTERVAL_MS = 30_000L
        private const val MIN_PROCESS_DISTANCE_METERS = 15f
        private const val KM_PER_LATITUDE_DEGREE = 111.0
        private const val MAX_SAMSUNG_NOW_BAR_TEXT_LENGTH = 80
        private const val MAX_SAMSUNG_CHIP_TEXT_LENGTH = 24
        private const val MAX_SAMSUNG_DRAWER_TITLE_LENGTH = 36
        private val SEARCH_RADII_KM = doubleArrayOf(5.0, 10.0, 25.0, 50.0, 100.0)
    }
}
