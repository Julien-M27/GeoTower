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
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
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
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.utils.AppStrings
import java.util.Locale
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
    private var lockedAntennaId: String? = null
    private var initialDistance: Float? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        repository = AnfrRepository(
            api = fr.geotower.data.api.RetrofitClient.apiService,
            context = applicationContext
        )
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopLocationUpdates()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (serviceStartTime == 0L) {
            serviceStartTime = System.currentTimeMillis()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!manager.canPostPromotedNotifications()) {
                try {
                    val settingsIntent = Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS").also { i ->
                        i.data = android.net.Uri.parse("package:$packageName")
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(settingsIntent)
                } catch (e: Exception) {
                    android.util.Log.d("LiveNotif", "Impossible d'ouvrir les parametres Live: ${e.message}")
                }
            }
        }

        val defaultOp = currentOperator
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val initialNotification = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
            manager.canPostPromotedNotifications()
        ) {
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

        startForeground(notificationId, initialNotification)
        startLocationUpdates()

        return START_STICKY
    }

    private fun startLocationUpdates() {
        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                processLocationUpdate(location)
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun processLocationUpdate(location: Location) {
        val defaultOp = currentOperator
        if (defaultOp == "AUCUN") return

        serviceScope.launch {
            try {
                val dao = AppDatabase.getDatabase(applicationContext).geoTowerDao()
                val offsetLat = 0.05
                val offsetLon = 0.05 / kotlin.math.cos(Math.toRadians(location.latitude))

                val antennas = dao.getLocalisationsInBox(
                    minLat = location.latitude - offsetLat,
                    maxLat = location.latitude + offsetLat,
                    minLon = location.longitude - offsetLon,
                    maxLon = location.longitude + offsetLon
                )

                val opAntennas = antennas.filter { it.operateur?.uppercase()?.contains(defaultOp) == true }

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

                val currentId = closestAntenna.idAnfr?.toString() ?: ""
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateNotification(
        text: String,
        userLoc: Location?,
        antLoc: LocalisationEntity?,
        operator: String,
        progress: Int = 0,
        address: String = ""
    ) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
            manager.canPostPromotedNotifications()
        ) {
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
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = android.net.Uri.parse("geotower://site/${antLoc?.idAnfr}")
            antLoc?.idAnfr?.toString()?.let { putExtra("TARGET_SITE_ID", it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            antLoc?.idAnfr?.hashCode() ?: 0,
            intent,
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
            .setProgressSegments(
                listOf(Notification.ProgressStyle.Segment(100).setColor(operatorColor(operator)))
            )

        operatorLogo(operator)?.let { logoResId ->
            progressStyle.setProgressTrackerIcon(IconCompat.createWithResource(this, logoResId).toIcon(this))
        }

        val shortCriticalText = extractShortCriticalText(contentText)

        val builder = Notification.Builder(this, liveTrackingChannelV3)
            .setContentTitle(AppStrings.nearestAntennaTitle(this))
            .setContentText(contentText)
            .setSubText(address.takeIf { it.isNotBlank() })
            .setSmallIcon(R.drawable.geotower_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setColor(operatorColor(operator))
            .setStyle(progressStyle)
            .addAction(0, AppStrings.quitAction(this), stopPendingIntent)

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
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = android.net.Uri.parse("geotower://site/${antLoc?.idAnfr}")
            antLoc?.idAnfr?.toString()?.let { putExtra("TARGET_SITE_ID", it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            antLoc?.idAnfr?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LiveTrackingService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val expandedView = android.widget.RemoteViews(packageName, R.layout.notif_expanded_bar)
        expandedView.setTextViewText(R.id.notif_title, AppStrings.nearestAntennaTitle(this))
        expandedView.setTextViewText(R.id.notif_text, contentText)
        if (address.isNotEmpty()) {
            expandedView.setViewVisibility(R.id.notif_address, android.view.View.VISIBLE)
            expandedView.setTextViewText(R.id.notif_address, "📍 $address")
        } else {
            expandedView.setViewVisibility(R.id.notif_address, android.view.View.GONE)
        }
        expandedView.setProgressBar(R.id.notif_progress, 100, progress, false)

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
            .addAction(0, AppStrings.quitAction(this), stopPendingIntent)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomBigContentView(expandedView)

        operatorLogo(operator)?.let { logoResId ->
            val largeIconBitmap = BitmapFactory.decodeResource(resources, logoResId)
            builder.setLargeIcon(largeIconBitmap)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                liveTrackingChannelV3,
                AppStrings.liveTrackingChannelDesc(this),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(null, null)
                enableVibration(false)
                vibrationPattern = longArrayOf(0L)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopLocationUpdates() {
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
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
            operator.contains("BOUYGUES") -> android.graphics.Color.parseColor("#00295F")
            operator.contains("SFR") -> android.graphics.Color.parseColor("#E2001A")
            operator.contains("FREE") -> android.graphics.Color.parseColor("#757575")
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
        stopLocationUpdates()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
    }
}
