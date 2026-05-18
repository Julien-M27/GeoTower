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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
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
import fr.geotower.data.api.RetrofitClient
import fr.geotower.data.db.AppDatabase
import fr.geotower.data.db.GeoTowerDao
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.utils.AppLogger
import fr.geotower.utils.DeviceProfile
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.OperatorLogos
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.Request

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
    private val liveSitePhotoCacheLock = Any()
    private val liveSitePhotoCache = LinkedHashMap<String, Bitmap?>()
    @Volatile
    private var liveSitePhotoLoadingKey: String? = null
    private var liveSitePhotoJob: Job? = null

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
                contentText = getString(R.string.live_tracking_searching),
                progress = 0,
                operator = defaultOp,
                antLoc = null,
                address = "",
                sitePhotoBitmap = null
            )
        } else {
            buildNotification(
                contentText = getString(R.string.live_tracking_searching),
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
            AppLogger.w(TAG_LOCATION, "Location updates could not start", e)
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
                        text = applicationContext.getString(R.string.live_tracking_no_antenna_found, defaultOp),
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
                val sitePhotoId = repository.getPhysiqueDetails(closestAntenna.idAnfr)
                    .firstOrNull()
                    ?.idSupport
                    ?.takeIf { it.isNotBlank() }
                    ?: closestAntenna.idAnfr
                val fullAddress = technique?.adresse ?: ""
                val notificationText = applicationContext.getString(
                    R.string.live_tracking_antenna_distance,
                    defaultOp,
                    distanceWithDirectionStr
                )
                val photoCacheKey = liveSitePhotoCacheKey(defaultOp, sitePhotoId)
                val liveSitePhotoBitmap = cachedLiveSitePhotoBitmap(photoCacheKey)

                updateNotification(
                    text = notificationText,
                    userLoc = location,
                    antLoc = closestAntenna,
                    operator = defaultOp,
                    progress = progress,
                    address = fullAddress,
                    sitePhotoBitmap = liveSitePhotoBitmap
                )

                requestLiveSitePhotoBitmapIfNeeded(
                    cacheKey = photoCacheKey,
                    siteId = sitePhotoId,
                    operator = defaultOp,
                    text = notificationText,
                    userLoc = location,
                    antLoc = closestAntenna,
                    progress = progress,
                    address = fullAddress
                )
            } catch (_: CancellationException) {
                // Newer GPS updates replace older calculations.
            } catch (e: Exception) {
                AppLogger.w(TAG_LOCATION, "Live tracking update failed", e)
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
            val operatorQueryName = OperatorColors.specForKey(operator)?.aliases?.firstOrNull() ?: operator
            val candidates = dao.getActiveLocalisationsInBoxByOperator(
                operatorName = operatorQueryName,
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
        address: String = "",
        sitePhotoBitmap: Bitmap? = null
    ) {
        if (!LiveTrackingController.hasPostNotificationsPermission(this)) {
            stopTrackingAndSelf()
            return
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = if (supportsProgressStyle()) {
            buildLiveNotification(text, progress, operator, antLoc, address, sitePhotoBitmap)
        } else {
            buildNotification(text, userLoc, antLoc, operator, progress, address)
        }

        manager.notify(notificationId, notification)
    }

    private fun requestLiveSitePhotoBitmapIfNeeded(
        cacheKey: String,
        siteId: String,
        operator: String,
        text: String,
        userLoc: Location?,
        antLoc: LocalisationEntity,
        progress: Int,
        address: String
    ) {
        if (!supportsProgressStyle() || siteId.isBlank() || hasLiveSitePhotoCacheEntry(cacheKey)) return
        if (liveSitePhotoLoadingKey == cacheKey) return

        liveSitePhotoJob?.cancel()
        liveSitePhotoLoadingKey = cacheKey
        liveSitePhotoJob = serviceScope.launch {
            try {
                val bitmap = loadLiveSitePhotoBitmap(siteId, operator)
                putLiveSitePhotoBitmap(cacheKey, bitmap)

                if (
                    bitmap != null &&
                    lockedAntennaId == antLoc.idAnfr &&
                    currentOperator == operator
                ) {
                    updateNotification(
                        text = text,
                        userLoc = userLoc,
                        antLoc = antLoc,
                        operator = operator,
                        progress = progress,
                        address = address,
                        sitePhotoBitmap = bitmap
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                putLiveSitePhotoBitmap(cacheKey, null)
                AppLogger.w(TAG_LOCATION, "Live site photo request failed", e)
            } finally {
                if (liveSitePhotoLoadingKey == cacheKey) {
                    liveSitePhotoLoadingKey = null
                }
            }
        }
    }

    private suspend fun loadLiveSitePhotoBitmap(siteId: String, operator: String): Bitmap? {
        val candidate = LiveSitePhotoSelector.firstCandidate(applicationContext, siteId, operator) ?: return null
        return downloadLiveSitePhotoBitmap(candidate.url, candidate.maxBytes)
    }

    private fun downloadLiveSitePhotoBitmap(url: String, maxBytes: Int): Bitmap? {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return RetrofitClient.currentClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body ?: return@use null
            val contentLength = body.contentLength()
            if (contentLength > maxBytes) return@use null

            val bytes = body.byteStream().use { stream ->
                readLimitedBytes(stream, maxBytes)
            }
            decodeLiveSitePhotoBitmap(bytes)
        }
    }

    private fun readLimitedBytes(input: InputStream, maxBytes: Int): ByteArray? {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val output = ByteArrayOutputStream()
        var total = 0

        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) return null
            output.write(buffer, 0, read)
        }

        return output.toByteArray()
    }

    private fun decodeLiveSitePhotoBitmap(bytes: ByteArray?): Bitmap? {
        if (bytes == null || bytes.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val targetSize = liveSitePhotoIconSizePx()
        val options = BitmapFactory.Options().apply {
            inSampleSize = liveSitePhotoInSampleSize(bounds.outWidth, bounds.outHeight, targetSize)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        return centerCropLiveSitePhoto(decoded, targetSize)
    }

    private fun liveSitePhotoInSampleSize(width: Int, height: Int, targetSize: Int): Int {
        var inSampleSize = 1
        while (width / inSampleSize > targetSize * 2 || height / inSampleSize > targetSize * 2) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun centerCropLiveSitePhoto(source: Bitmap, targetSize: Int): Bitmap {
        val side = min(source.width, source.height)
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2
        val cropped = Bitmap.createBitmap(source, left, top, side, side)
        val scaled = if (cropped.width == targetSize && cropped.height == targetSize) {
            cropped
        } else {
            Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
        }

        if (scaled !== cropped) cropped.recycle()
        if (cropped !== source && !source.isRecycled) source.recycle()
        return roundLiveIconCorners(scaled)
    }

    private fun roundedDrawableBitmap(resId: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(this, resId)?.mutate() ?: return null
        val targetSize = liveSitePhotoIconSizePx()
        val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, targetSize, targetSize)
        drawable.draw(canvas)
        return roundLiveIconCorners(bitmap)
    }

    private fun roundLiveIconCorners(source: Bitmap): Bitmap {
        val rounded = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val radius = resources.displayMetrics.density * LIVE_SITE_PHOTO_CORNER_RADIUS_DP
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        Canvas(rounded).drawRoundRect(
            RectF(0f, 0f, source.width.toFloat(), source.height.toFloat()),
            radius,
            radius,
            paint
        )
        if (!source.isRecycled) source.recycle()
        return rounded
    }

    private fun liveSitePhotoIconSizePx(): Int {
        return (resources.displayMetrics.density * LIVE_SITE_PHOTO_ICON_DP)
            .toInt()
            .coerceAtLeast(MIN_LIVE_SITE_PHOTO_ICON_PX)
    }

    private fun liveSitePhotoCacheKey(operator: String, siteId: String): String {
        return LiveSitePhotoSelector.cacheKey(applicationContext, operator, siteId)
    }

    private fun hasLiveSitePhotoCacheEntry(cacheKey: String): Boolean {
        return synchronized(liveSitePhotoCacheLock) {
            liveSitePhotoCache.containsKey(cacheKey)
        }
    }

    private fun cachedLiveSitePhotoBitmap(cacheKey: String): Bitmap? {
        return synchronized(liveSitePhotoCacheLock) {
            liveSitePhotoCache[cacheKey]
        }
    }

    private fun putLiveSitePhotoBitmap(cacheKey: String, bitmap: Bitmap?) {
        synchronized(liveSitePhotoCacheLock) {
            if (!liveSitePhotoCache.containsKey(cacheKey) &&
                liveSitePhotoCache.size >= MAX_LIVE_SITE_PHOTO_CACHE_ENTRIES
            ) {
                liveSitePhotoCache.entries.iterator().let { iterator ->
                    if (iterator.hasNext()) {
                        iterator.next()
                        iterator.remove()
                    }
                }
            }
            liveSitePhotoCache[cacheKey] = bitmap
        }
    }

    private fun clearLiveSitePhotoCache() {
        synchronized(liveSitePhotoCacheLock) {
            liveSitePhotoCache.values.filterNotNull().forEach { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            liveSitePhotoCache.clear()
        }
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun buildLiveNotification(
        contentText: String,
        progress: Int,
        operator: String,
        antLoc: LocalisationEntity?,
        address: String,
        sitePhotoBitmap: Bitmap?
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

        progressEndIcon(operator, sitePhotoBitmap)?.let(progressStyle::setProgressEndIcon)

        val hasAddress = address.isNotBlank()
        val liveTitle = if (hasAddress) contentText else getString(R.string.live_tracking_title)
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
                    IconCompat.createWithResource(this, R.drawable.ic_notification_action_transparent).toIcon(this),
                    getString(R.string.live_tracking_stop_action),
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
                    shortCriticalText = shortCriticalText,
                    sitePhotoBitmap = sitePhotoBitmap
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
            .setContentTitle(getString(R.string.live_tracking_title))
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
            .addAction(0, getString(R.string.live_tracking_stop_action), stopPendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))

        return builder.build().apply {
            extras.putAll(
                samsungOngoingActivityExtras(
                    operator = operator,
                    progress = progress,
                    primaryInfo = contentText,
                    secondaryInfo = expandedText,
                    shortCriticalText = extractShortCriticalText(contentText),
                    sitePhotoBitmap = null
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
        shortCriticalText: String?,
        sitePhotoBitmap: Bitmap?
    ): Bundle {
        if (!DeviceProfile.supportsSamsungOngoingActivity) return Bundle.EMPTY

        val chipText = samsungChipText(shortCriticalText, primaryInfo)
        val drawerPrimaryInfo = samsungDrawerPrimaryInfo(chipText)
        val compactSecondary = secondaryInfo
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_SAMSUNG_NOW_BAR_TEXT_LENGTH)
        val operatorIcon = roundedOperatorLogoIcon(operator)
        val expandedIcon = sitePhotoBitmap?.let { bitmap ->
            IconCompat.createWithBitmap(bitmap).toIcon(this)
        } ?: operatorIcon
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
            }
            expandedIcon?.let { icon ->
                putParcelable("android.ongoingActivityNoti.secondIcon", icon)
            }
        }
    }

    private fun supportsProgressStyle(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
    }

    private fun samsungChipText(shortCriticalText: String?, fallback: String): String {
        return (shortCriticalText ?: fallback)
            .take(MAX_SAMSUNG_CHIP_TEXT_LENGTH)
    }

    private fun samsungDrawerPrimaryInfo(chipText: String): String {
        return getString(R.string.live_tracking_drawer_primary, getString(R.string.live_tracking_title), chipText)
            .take(MAX_SAMSUNG_DRAWER_TITLE_LENGTH)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                liveTrackingChannelV3,
                getString(R.string.live_tracking_channel_name),
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
            AppLogger.w(TAG_LOCATION, "Live tracking foreground service failed", e)
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
        liveSitePhotoJob?.cancel()
        liveSitePhotoLoadingKey = null
        stopLocationUpdates()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
        return START_NOT_STICKY
    }

    private fun resetIfOperatorChanged(operator: String) {
        if (lockedOperator != null && lockedOperator != operator) {
            processingJob?.cancel()
            liveSitePhotoJob?.cancel()
            liveSitePhotoLoadingKey = null
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
            val rawOp = prefs.getString("default_operator", "Aucun") ?: "Aucun"
            return OperatorColors.keyFor(rawOp) ?: "AUCUN"
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
        return OperatorColors.colorInt(operator)
    }

    private fun progressEndIcon(operator: String, sitePhotoBitmap: Bitmap?) =
        sitePhotoBitmap?.let { IconCompat.createWithBitmap(it).toIcon(this) }
            ?: roundedOperatorLogoIcon(operator)

    private fun roundedOperatorLogoIcon(operator: String) =
        operatorLogo(operator)
            ?.let(::roundedDrawableBitmap)
            ?.let { bitmap -> IconCompat.createWithBitmap(bitmap).toIcon(this) }

    private fun operatorLogo(operator: String): Int? {
        return OperatorLogos.drawableRes(operator)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        processingJob?.cancel()
        liveSitePhotoJob?.cancel()
        liveSitePhotoLoadingKey = null
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopLocationUpdates()
        clearLiveSitePhotoCache()
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
        private const val LIVE_SITE_PHOTO_ICON_DP = 64
        private const val LIVE_SITE_PHOTO_CORNER_RADIUS_DP = 10
        private const val MIN_LIVE_SITE_PHOTO_ICON_PX = 96
        private const val MAX_LIVE_SITE_PHOTO_CACHE_ENTRIES = 12
        private const val TAG_LOCATION = "GeoTowerLocation"
        private val SEARCH_RADII_KM = doubleArrayOf(5.0, 10.0, 25.0, 50.0, 100.0)
    }
}
