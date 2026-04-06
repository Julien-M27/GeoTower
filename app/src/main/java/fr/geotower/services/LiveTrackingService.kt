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
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.google.android.gms.location.*
import fr.geotower.MainActivity
import fr.geotower.R
import fr.geotower.data.db.AppDatabase
import fr.geotower.data.models.LocalisationEntity
import kotlinx.coroutines.*
import java.util.Locale
import fr.geotower.utils.AppStrings

class LiveTrackingService : Service() {

    private val live_tracking_channel_v3 = "live_tracking_channel_v3"
    private val NOTIFICATION_ID = 1001

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    // ✅ NOUVEAU : Repository pour aller chercher l'adresse dans la base de données
    private lateinit var repository: fr.geotower.data.AnfrRepository

    // On sauvegarde l'heure exacte du lancement du service
    private var serviceStartTime: Long = 0L

    // VARIABLES POUR L'ÉCHELLE DYNAMIQUE
    private var lockedAntennaId: String? = null
    private var initialDistance: Float? = null

    private fun isMiles(context: Context): Boolean {
        val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
        return try {
            prefs.getInt("distance_unit", 0) == 1
        } catch (e: Exception) {
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

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialisation du repository
        repository = fr.geotower.data.AnfrRepository(
            api = fr.geotower.data.api.RetrofitClient.apiService,
            context = applicationContext
        )

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_STOP_SERVICE") {
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
                    android.util.Log.d("LiveNotif", "Impossible d'ouvrir les paramètres Live: ${e.message}")
                }
            }
        }

        val defaultOp = currentOperator
        startForeground(NOTIFICATION_ID, buildNotification(AppStrings.searchInProgress(this), null, null, defaultOp, 0, ""))
        startLocationUpdates()

        return START_STICKY
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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
                    updateNotification(AppStrings.noAntennaFound(applicationContext, defaultOp), null, null, defaultOp, 0, "")
                    return@launch
                }

                var minDistance = Float.MAX_VALUE
                var closestAntenna = opAntennas.first()
                var bearingToClosest = 0f // ✅ On prépare la variable pour l'angle

                opAntennas.forEach { ant ->
                    val results = FloatArray(2) // ✅ On demande 2 valeurs au GPS (Distance ET Cap)
                    Location.distanceBetween(location.latitude, location.longitude, ant.latitude, ant.longitude, results)
                    if (results[0] < minDistance) {
                        minDistance = results[0]
                        closestAntenna = ant
                        bearingToClosest = results[1] // ✅ On enregistre le cap de l'antenne la plus proche
                    }
                }

                // --- CALCUL PROGRESSION ---
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

                val isMi = isMiles(applicationContext)
                val baseDistanceStr = if (isMi) {
                    String.format(Locale.US, "%.2f mi", minDistance / 1609.34f)
                } else {
                    if (minDistance >= 1000) String.format(Locale.US, "%.1f km", minDistance / 1000f)
                    else "${minDistance.toInt()} m"
                }

                // ✅ CONVERSION DE L'ANGLE EN POINT CARDINAL ET DEGRÉ EXACT
                var bearing = bearingToClosest
                if (bearing < 0) bearing += 360f // On s'assure d'être entre 0 et 360°
                val directions = arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
                val index = Math.round(bearing / 45.0).toInt() % 8
                val directionStr = directions[index]

                // On arrondit le degré pour un affichage propre (ex: 45°)
                val degreeStr = "${Math.round(bearing)}°"

                // On fusionne la distance, la direction et le degré (Ex: "850 m • NE (45°)")
                val distanceWithDirectionStr = "$baseDistanceStr • $directionStr ($degreeStr)"

                // ✅ RECHERCHE DE L'ADRESSE
                val technique = repository.getTechniqueDetails(closestAntenna.idAnfr)
                // On récupère l'adresse complète brute
                val fullAddress = technique?.adresse ?: ""

                updateNotification(
                    AppStrings.antennaDistance(applicationContext, defaultOp, distanceWithDirectionStr), // ✅ On envoie le nouveau texte fusionné
                    location,
                    closestAntenna,
                    defaultOp,
                    progress,
                    fullAddress // ✅ On passe l'adresse complète !
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
        address: String = "" // ✅ Nouveau paramètre
    ) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
            && manager.canPostPromotedNotifications()
        ) {
            buildLiveNotification(text, progress, operator, antLoc, address)
        } else {
            buildNotification(text, userLoc, antLoc, operator, progress, address)
        }

        manager.notify(NOTIFICATION_ID, notification)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun buildLiveNotification(
        contentText: String,
        progress: Int,
        operator: String,
        antLoc: LocalisationEntity?,
        address: String // ✅ Nouveau paramètre
    ): android.app.Notification {

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // 🌟 AJOUT : On crée une URI unique pour que le système ne confonde pas les Intents
            data = android.net.Uri.parse("geotower://site/${antLoc?.idAnfr}")
            if (antLoc != null) {
                putExtra("TARGET_SITE_ID", antLoc.idAnfr?.toString())
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            antLoc?.idAnfr?.hashCode() ?: 0, // 🌟 MODIFIÉ : On utilise un code unique par antenne
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, LiveTrackingService::class.java).apply { action = "ACTION_STOP_SERVICE" }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val operatorColor = when {
            operator.contains("ORANGE") -> android.graphics.Color.parseColor("#FF7900")
            operator.contains("BOUYGUES") -> android.graphics.Color.parseColor("#00295F")
            operator.contains("SFR") -> android.graphics.Color.parseColor("#E2001A")
            else -> android.graphics.Color.parseColor("#757575")
        }

        val progressStyle = Notification.ProgressStyle()
            .setProgress(progress)
            .setProgressSegments(
                listOf(Notification.ProgressStyle.Segment(100).setColor(operatorColor))
            )

        val logoResId = when {
            operator.contains("ORANGE") -> R.drawable.logo_orange
            operator.contains("BOUYGUES") -> R.drawable.logo_bouygues
            operator.contains("SFR") -> R.drawable.logo_sfr
            operator.contains("FREE") -> R.drawable.logo_free
            else -> null
        }
        if (logoResId != null) {
            progressStyle.setProgressTrackerIcon(IconCompat.createWithResource(this, logoResId).toIcon(this))
        }

        // ✅ FORMATAGE : On ajoute l'adresse avec un petit pin de localisation dans la Live Notification
        val finalContentText = if (address.isNotEmpty()) "$contentText\n📍 $address" else contentText

        return Notification.Builder(this, live_tracking_channel_v3)
            .setContentTitle(AppStrings.nearestAntennaTitle(this))
            .setContentText(finalContentText) // ✅ Affiche "Distance \n 📍 Adresse"
            .setSmallIcon(R.drawable.geotower_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setColor(operatorColor)
            .setColorized(true)
            .setStyle(progressStyle)
            .setOnlyAlertOnce(true)
            .setWhen(System.currentTimeMillis() + 60_000L)
            .addAction(Notification.Action.Builder(null, AppStrings.quitAction(this), stopPendingIntent).build())
            .build()
    }

    private fun buildNotification(
        contentText: String,
        userLoc: Location?,
        antLoc: LocalisationEntity?,
        operator: String,
        progress: Int = 0,
        address: String = "" // ✅ Nouveau paramètre
    ): android.app.Notification {

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // 🌟 AJOUT : URI unique ici aussi
            data = android.net.Uri.parse("geotower://site/${antLoc?.idAnfr}")
            if (antLoc != null) {
                putExtra("TARGET_SITE_ID", antLoc.idAnfr?.toString())
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            antLoc?.idAnfr?.hashCode() ?: 0, // 🌟 MODIFIÉ : Code unique ici aussi
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, LiveTrackingService::class.java).apply { action = "ACTION_STOP_SERVICE" }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val operatorColor = when {
            operator.contains("ORANGE") -> android.graphics.Color.parseColor("#FF7900")
            operator.contains("BOUYGUES") -> android.graphics.Color.parseColor("#00295F")
            operator.contains("SFR") -> android.graphics.Color.parseColor("#E2001A")
            operator.contains("FREE") -> android.graphics.Color.parseColor("#757575")
            else -> android.graphics.Color.GRAY
        }

        val expandedView = android.widget.RemoteViews(packageName, R.layout.notif_expanded_bar)
        expandedView.setTextViewText(R.id.notif_title, AppStrings.nearestAntennaTitle(this))
        expandedView.setTextViewText(R.id.notif_text, contentText)

        // ✅ GESTION DE L'AFFICHAGE DE L'ADRESSE
        if (address.isNotEmpty()) {
            expandedView.setViewVisibility(R.id.notif_address, android.view.View.VISIBLE)
            expandedView.setTextViewText(R.id.notif_address, "📍 $address")
        } else {
            expandedView.setViewVisibility(R.id.notif_address, android.view.View.GONE)
        }

        expandedView.setProgressBar(R.id.notif_progress, 100, progress, false)

        val builder = NotificationCompat.Builder(this, live_tracking_channel_v3)
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
            .setColor(operatorColor)
            .addAction(0, AppStrings.quitAction(this), stopPendingIntent)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomBigContentView(expandedView)

        val logoResId = when {
            operator.contains("ORANGE") -> R.drawable.logo_orange
            operator.contains("BOUYGUES") -> R.drawable.logo_bouygues
            operator.contains("SFR") -> R.drawable.logo_sfr
            operator.contains("FREE") -> R.drawable.logo_free
            else -> null
        }

        if (logoResId != null) {
            val largeIconBitmap = BitmapFactory.decodeResource(resources, logoResId)
            builder.setLargeIcon(largeIconBitmap)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                live_tracking_channel_v3,
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
        if (::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}