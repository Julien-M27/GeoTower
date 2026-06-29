package fr.geotower.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Icon
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
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.IconCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import fr.geotower.MainActivity
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.data.api.RetrofitClient
import fr.geotower.data.db.AppDatabase
import fr.geotower.data.db.GeoTowerDao
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.SiteHsEntity
import fr.geotower.utils.AppLogger
import fr.geotower.utils.DeviceProfile
import fr.geotower.utils.LiveTrackingPrefs
import fr.geotower.utils.NotificationIconResources
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.OperatorLogos
import fr.geotower.utils.PreferenceStores
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
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
    private var lastDistanceToLockedAntenna: Float? = null
    private val liveSitePhotoCacheLock = Any()
    private val liveSitePhotoCache = LinkedHashMap<String, Bitmap?>()
    private val liveSitePhotoHttpClient by lazy {
        RetrofitClient.currentClient.newBuilder()
            .connectTimeout(LIVE_SITE_PHOTO_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(LIVE_SITE_PHOTO_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(LIVE_SITE_PHOTO_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
    @Volatile
    private var liveSitePhotoLoadingKey: String? = null
    private var liveSitePhotoJob: Job? = null
    @Volatile
    private var liveOutageInfoMap: Map<String, List<LiveOutageInfo>> = emptyMap()
    @Volatile
    private var liveOutageFetchedAt: Long = 0L
    private var liveOutageRefreshJob: Job? = null

    private data class LiveOutageInfo(
        val operatorKeys: Set<String>
    )

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
            !LiveTrackingController.hasPreciseLocationPermission(this) ||
            !LiveTrackingController.hasPostNotificationsPermission(this)
        ) {
            return stopTrackingAndSelf()
        }

        val operatorChanged = resetIfOperatorChanged(defaultOp)

        if (intent?.action == ACTION_REFRESH_NOTIFICATION) {
            refreshFromLastProcessedLocation()
            return START_STICKY
        }

        if (intent?.action == ACTION_REFRESH_LOCATION_SETTINGS) {
            startLocationUpdates()
            refreshFromLastProcessedLocation()
            return START_STICKY
        }

        if (operatorChanged && refreshFromLastProcessedLocation()) {
            return START_STICKY
        }

        val initialNotification = if (supportsProgressStyle()) {
            buildLiveNotification(
                contentText = getString(R.string.live_tracking_searching),
                progress = 0,
                operator = defaultOp,
                antLoc = null,
                address = "",
                sitePhotoBitmap = null,
                mirrorTrackerIcon = false
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
        requestLiveOutageRefreshIfNeeded()
        startLocationUpdates()

        return START_STICKY
    }

    private fun startLocationUpdates() {
        if (!hasFineLocationPermission()) {
            stopTrackingAndSelf()
            return
        }

        stopLocationUpdates()
        val updateIntervalMs = liveLocationUpdateIntervalMillis()
        val locationRequest = LocationRequest.Builder(liveLocationPriority(), updateIntervalMs)
            .setMinUpdateIntervalMillis(updateIntervalMs)
            .build()

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

    private fun liveLocationUpdateIntervalMillis(): Long {
        val prefs = getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
        val seconds = LiveTrackingPrefs.locationUpdateIntervalSeconds(prefs)
        return TimeUnit.SECONDS.toMillis(seconds.toLong())
    }

    private fun liveLocationPriority(): Int {
        val prefs = getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
        return LiveTrackingPrefs.locationPriority(prefs)
    }

    private fun processLocationUpdate(location: Location) {
        val defaultOp = currentOperator

        if (!shouldProcessLocation(location)) return
        lastProcessedLocation = Location(location)
        lastProcessedAt = System.currentTimeMillis()

        processingJob?.cancel()

        processingJob = serviceScope.launch {
            try {
                val dao = AppDatabase.getDatabase(applicationContext).geoTowerDao()
                val opAntennas = findActiveAntennasByRadius(dao, location, defaultOp)

                if (opAntennas.isEmpty()) {
                    lastDistanceToLockedAntenna = null
                    val emptyText = if (defaultOp == "AUCUN") {
                        applicationContext.getString(R.string.live_tracking_no_antenna_found_nearest)
                    } else {
                        applicationContext.getString(R.string.live_tracking_no_antenna_found, defaultOp)
                    }
                    updateNotification(
                        text = emptyText,
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

                // En mode « aucun opérateur », on suit l'antenne la plus proche tous opérateurs
                // confondus : l'affichage (nom, couleur, logo, photo) reflète l'opérateur réel
                // de cette antenne. Quand un opérateur est choisi, on conserve son identité.
                val displayOperator = if (defaultOp == "AUCUN") {
                    OperatorColors.keyFor(closestAntenna.operateur) ?: closestAntenna.operateur ?: defaultOp
                } else {
                    defaultOp
                }

                val currentId = closestAntenna.idAnfr
                if (lockedAntennaId != currentId) {
                    lockedAntennaId = currentId
                    initialDistance = minDistance
                    lastDistanceToLockedAntenna = null
                }

                val previousDistance = lastDistanceToLockedAntenna
                val isMovingAwayFromAntenna = previousDistance != null &&
                    minDistance > previousDistance + MOVING_AWAY_DISTANCE_THRESHOLD_METERS
                lastDistanceToLockedAntenna = minDistance

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
                val directions = applicationContext.resources.getStringArray(R.array.live_cardinal_directions)
                val index = Math.round(bearing / 45.0).toInt() % 8
                val directionStr = directions.getOrElse(index) { "N" }
                val degreeStr = "${Math.round(bearing)}°"
                val distanceWithDirectionStr = "$baseDistanceStr • $directionStr ($degreeStr)"

                requestLiveOutageRefreshIfNeeded()
                val hsSuffix = liveOutageTitleSuffixFor(closestAntenna, displayOperator)

                val technique = repository.getTechniqueDetails(closestAntenna.idAnfr)
                val sitePhotoId = repository.getPhysiqueDetails(closestAntenna.idAnfr)
                    .firstOrNull()
                    ?.idSupport
                    ?.takeIf { it.isNotBlank() }
                    ?: closestAntenna.idAnfr
                val fullAddress = technique?.adresse ?: ""
                val notificationText = applicationContext.getString(
                    R.string.live_tracking_antenna_distance,
                    displayOperator,
                    distanceWithDirectionStr + hsSuffix
                )
                val photoCacheKey = liveSitePhotoCacheKey(displayOperator, sitePhotoId)
                val liveSitePhotoBitmap = cachedLiveSitePhotoBitmap(photoCacheKey)

                updateNotification(
                    text = notificationText,
                    userLoc = location,
                    antLoc = closestAntenna,
                    operator = displayOperator,
                    progress = progress,
                    address = fullAddress,
                    sitePhotoBitmap = liveSitePhotoBitmap,
                    mirrorTrackerIcon = isMovingAwayFromAntenna
                )

                requestLiveSitePhotoBitmapIfNeeded(
                    cacheKey = photoCacheKey,
                    siteId = sitePhotoId,
                    operator = displayOperator,
                    trackingOperator = defaultOp,
                    text = notificationText,
                    userLoc = location,
                    antLoc = closestAntenna,
                    progress = progress,
                    address = fullAddress,
                    mirrorTrackerIcon = isMovingAwayFromAntenna
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
        // « AUCUN » = aucun opérateur choisi → on cherche l'antenne active la plus proche,
        // tous opérateurs confondus, sans filtre par nom d'opérateur.
        val isNearestMode = operator == "AUCUN"
        val operatorQueryNames = if (isNearestMode) emptyList() else OperatorColors.searchLabelsFor(operator)

        SEARCH_RADII_KM.forEach { radiusKm ->
            val radiusMeters = (radiusKm * 1000.0).toFloat()
            val offsetLat = radiusKm / KM_PER_LATITUDE_DEGREE
            val offsetLon = offsetLat / safeCos
            val candidates = if (isNearestMode) {
                dao.getActiveLocalisationsInBox(
                    minLat = location.latitude - offsetLat,
                    maxLat = location.latitude + offsetLat,
                    minLon = location.longitude - offsetLon,
                    maxLon = location.longitude + offsetLon
                )
            } else {
                operatorQueryNames
                    .flatMap { operatorQueryName ->
                        dao.getActiveLocalisationsInBoxByOperator(
                            operatorName = operatorQueryName,
                            minLat = location.latitude - offsetLat,
                            maxLat = location.latitude + offsetLat,
                            minLon = location.longitude - offsetLon,
                            maxLon = location.longitude + offsetLon
                        )
                    }
                    .distinctBy { it.idAnfr }
            }

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

        return if (isNearestMode) {
            dao.getNearestActiveLocalisations(
                lat = location.latitude,
                lon = location.longitude,
                limit = GLOBAL_OPERATOR_FALLBACK_LIMIT
            )
        } else {
            operatorQueryNames
                .flatMap { operatorQueryName ->
                    dao.getNearestActiveLocalisationsByOperator(
                        operatorName = operatorQueryName,
                        lat = location.latitude,
                        lon = location.longitude,
                        limit = GLOBAL_OPERATOR_FALLBACK_LIMIT
                    )
                }
                .distinctBy { it.idAnfr }
        }
    }

    private fun requestLiveOutageRefreshIfNeeded(force: Boolean = false) {
        val now = System.currentTimeMillis()
        val hasFreshCache = liveOutageFetchedAt > 0L &&
            now - liveOutageFetchedAt < LIVE_OUTAGE_CACHE_TTL_MS
        if (!force && hasFreshCache) return
        if (liveOutageRefreshJob?.isActive == true) return

        liveOutageRefreshJob = serviceScope.launch {
            try {
                liveOutageInfoMap = buildLiveOutageInfoMap(repository.getSitesHs())
                liveOutageFetchedAt = System.currentTimeMillis()
                refreshFromLastProcessedLocation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG_LOCATION, "Live tracking outage data failed", e)
            }
        }
    }

    private fun buildLiveOutageInfoMap(sitesHs: List<SiteHsEntity>): Map<String, List<LiveOutageInfo>> {
        val result = mutableMapOf<String, MutableList<LiveOutageInfo>>()

        sitesHs.forEach { hs ->
            val id = normalizedLiveAnfrId(hs.idAnfr)
            if (id.isBlank()) return@forEach

            val parsedOperators = OperatorColors.keysFor(hs.operateur)
            val operators = if (parsedOperators.isEmpty()) {
                setOf(LIVE_HS_OPERATOR_WILDCARD)
            } else {
                parsedOperators.toSet()
            }
            result.getOrPut(id) { mutableListOf() }.add(
                LiveOutageInfo(operatorKeys = operators)
            )
        }

        return result
    }

    private fun liveOutageTitleSuffixFor(antenna: LocalisationEntity, operator: String): String {
        val outages = liveOutageInfoMap[normalizedLiveAnfrId(antenna.idAnfr)].orEmpty()
        if (outages.isEmpty()) return ""
        val operatorKeys = (OperatorColors.keysFor(antenna.operateur) + operator)
            .mapNotNull { OperatorColors.keyFor(it) }
            .distinct()
            .toSet()

        val matchingOutages = outages.filter { outage ->
            LIVE_HS_OPERATOR_WILDCARD in outage.operatorKeys ||
                outage.operatorKeys.any { it in operatorKeys }
        }
        if (matchingOutages.isEmpty()) return ""

        return "$LIVE_HS_TITLE_SEPARATOR$LIVE_HS_TITLE_LABEL"
    }

    private fun normalizedLiveAnfrId(value: String): String {
        val trimmed = value.trim()
        return trimmed.toLongOrNull()?.toString() ?: trimmed
    }

    private fun updateNotification(
        text: String,
        userLoc: Location?,
        antLoc: LocalisationEntity?,
        operator: String,
        progress: Int = 0,
        address: String = "",
        sitePhotoBitmap: Bitmap? = null,
        mirrorTrackerIcon: Boolean = false
    ) {
        if (!LiveTrackingController.hasPostNotificationsPermission(this)) {
            stopTrackingAndSelf()
            return
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = if (supportsProgressStyle()) {
            buildLiveNotification(text, progress, operator, antLoc, address, sitePhotoBitmap, mirrorTrackerIcon)
        } else {
            buildNotification(
                contentText = text,
                userLoc = userLoc,
                antLoc = antLoc,
                operator = operator,
                progress = progress,
                address = address,
                sitePhotoBitmap = sitePhotoBitmap,
                mirrorTrackerIcon = mirrorTrackerIcon
            )
        }

        manager.notify(notificationId, notification)
    }

    private fun requestLiveSitePhotoBitmapIfNeeded(
        cacheKey: String,
        siteId: String,
        operator: String,
        trackingOperator: String,
        text: String,
        userLoc: Location?,
        antLoc: LocalisationEntity,
        progress: Int,
        address: String,
        mirrorTrackerIcon: Boolean
    ) {
        if (siteId.isBlank() || hasLiveSitePhotoCacheEntry(cacheKey)) return
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
                    currentOperator == trackingOperator
                ) {
                    updateNotification(
                        text = text,
                        userLoc = userLoc,
                        antLoc = antLoc,
                        operator = operator,
                        progress = progress,
                        address = address,
                        sitePhotoBitmap = bitmap,
                        mirrorTrackerIcon = mirrorTrackerIcon
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

        return liveSitePhotoHttpClient.newCall(request).execute().use { response ->
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
        sitePhotoBitmap: Bitmap?,
        mirrorTrackerIcon: Boolean
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationRequestCode(antLoc),
            notificationIntent(antLoc),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_ACTION_REQUEST_CODE,
            Intent(this, LiveTrackingService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val progressStyle = Notification.ProgressStyle()
            .setProgress(progress)
            .setProgressTrackerIcon(liveTrackerIcon(mirrorTrackerIcon))
            .setProgressSegments(
                listOf(Notification.ProgressStyle.Segment(100).setColor(operatorColor(operator)))
            )

        val liveContentText = liveNotificationContentText(contentText, address)
        val shortCriticalText = extractShortCriticalText(contentText)
        val livePrimaryInfo = liveActivityPrimaryInfo(
            operator = operator,
            distanceText = shortCriticalText,
            fallbackTitle = getString(R.string.live_tracking_title),
            primaryInfo = contentText,
            secondaryInfo = liveContentText
        )
        val builder = Notification.Builder(this, liveTrackingChannelV3)
            .setContentTitle(livePrimaryInfo)
            .setContentText(liveContentText)
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
        NotificationIconResources.applyTo(builder, this)
        // Chip de la notif repliée : sur Samsung, le chip coloré rend bien le logo opérateur.
        // Sur les autres surfaces Android, la petite icône est tintée en monochrome → un logo
        // opérateur donnerait un carré plein moche, donc on garde l'icône monochrome de l'app.
        if (DeviceProfile.isSamsungDevice) {
            OperatorLogos.drawableRes(operator)?.let { builder.setSmallIcon(it) }
        }
        (sitePhotoBitmap ?: operatorLogoBitmap(operator))?.let { bitmap ->
            builder.setLargeIcon(Icon.createWithBitmap(bitmap))
        }

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

        // One UI 8.5 : les extras propriétaires android.ongoingActivityNoti.* (style legacy)
        // placent la notif en section TimeOrder et la rendent invisible dans la Now Bar.
        // On s'appuie sur l'API standard (ProgressStyle + setRequestPromotedOngoing) → section
        // OngoingActivity, affichage fiable sur 8.0 et 8.5.
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
        address: String = "",
        sitePhotoBitmap: Bitmap? = null,
        mirrorTrackerIcon: Boolean = false
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationRequestCode(antLoc),
            notificationIntent(antLoc),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_ACTION_REQUEST_CODE,
            Intent(this, LiveTrackingService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val expandedText = liveNotificationContentText(contentText, address)
        val shortCriticalText = extractShortCriticalText(contentText)
        val notificationTitle = liveActivityPrimaryInfo(
            operator = operator,
            distanceText = shortCriticalText,
            fallbackTitle = getString(R.string.live_tracking_title),
            primaryInfo = contentText,
            secondaryInfo = expandedText
        )
        val notificationText = address
            .takeIf { it.isNotBlank() }
            ?.let(::notificationAddressText)
            ?: contentText

        val builder = NotificationCompat.Builder(this, liveTrackingChannelV3)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setShowWhen(false)
            .setColor(operatorColor(operator))
            .setProgress(100, progress, false)
            .addAction(0, getString(R.string.live_tracking_stop_action), stopPendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
        (sitePhotoBitmap ?: operatorLogoBitmap(operator))?.let(builder::setLargeIcon)
        NotificationIconResources.applyTo(builder, this)

        return builder.build().apply {
            extras.putAll(
                samsungOngoingActivityExtras(
                    operator = operator,
                    progress = progress,
                    primaryInfo = notificationTitle,
                    secondaryInfo = expandedText,
                    shortCriticalText = shortCriticalText,
                    sitePhotoBitmap = sitePhotoBitmap,
                    mirrorTrackerIcon = mirrorTrackerIcon
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

    private fun liveNotificationContentText(
        contentText: String,
        address: String
    ): String {
        val cleanAddress = address.trim()
        return if (cleanAddress.isBlank()) {
            contentText
        } else {
            notificationAddressText(cleanAddress)
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
        sitePhotoBitmap: Bitmap?,
        mirrorTrackerIcon: Boolean
    ): Bundle {
        if (!DeviceProfile.supportsSamsungOngoingActivity) return Bundle.EMPTY

        val chipText = samsungChipText(shortCriticalText, primaryInfo)
        val drawerPrimaryInfo = liveActivityPrimaryInfo(
            operator = operator,
            distanceText = shortCriticalText,
            fallbackTitle = getString(R.string.live_tracking_title),
            primaryInfo = primaryInfo,
            secondaryInfo = secondaryInfo
        )
        val compactSecondary = secondaryInfo
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_SAMSUNG_NOW_BAR_TEXT_LENGTH)
        val operatorIcon = operatorLogoIcon(operator)
        val expandedIcon = sitePhotoBitmap?.let { bitmap ->
            IconCompat.createWithBitmap(bitmap).toIcon(this)
        } ?: operatorIcon
        val trackerIcon = liveTrackerIcon(mirrorTrackerIcon)
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

    private fun liveActivityPrimaryInfo(
        operator: String,
        distanceText: String?,
        fallbackTitle: String,
        primaryInfo: String,
        secondaryInfo: String
    ): String {
        val orientationText = liveOrientationText(primaryInfo)
            ?: liveOrientationText(secondaryInfo)

        if (!distanceText.isNullOrBlank() && !orientationText.isNullOrBlank()) {
            return listOf(liveOperatorName(operator), distanceText, orientationText)
                .joinToString(" \u2022 ")
                .take(MAX_SAMSUNG_DRAWER_TITLE_LENGTH)
        }

        if (!distanceText.isNullOrBlank()) {
            return getString(R.string.live_tracking_drawer_primary, fallbackTitle, distanceText)
                .take(MAX_SAMSUNG_DRAWER_TITLE_LENGTH)
        }

        return fallbackTitle.take(MAX_SAMSUNG_DRAWER_TITLE_LENGTH)
    }

    private fun liveOperatorName(operator: String): String {
        return OperatorColors.specForKey(operator)?.label?.takeIf { it.isNotBlank() }
            ?: operator.replace('_', ' ')
    }

    private fun liveOrientationText(text: String): String? {
        return Regex("""(?:^|[\s\u2022])([NSEOW]{1,2}\s*\(\s*\d{1,3}\s*\u00B0\s*\)(?:\s*\u2022\s*HS)?)""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(Regex("""\s+"""), " ")
            ?.replace("( ", "(")
            ?.replace(" )", ")")
            ?.takeIf { it.isNotBlank() }
    }

    private fun liveTrackerIcon(mirrored: Boolean): Icon {
        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_live_user_tracker)?.mutate()
            ?: return IconCompat.createWithResource(this, R.drawable.ic_live_user_tracker).toIcon(this)

        DrawableCompat.setTint(drawable, liveTrackerIconColor())
        val size = (resources.displayMetrics.density * LIVE_TRACKER_ICON_DP)
            .toInt()
            .coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        if (mirrored) {
            canvas.translate(size.toFloat(), 0f)
            canvas.scale(-1f, 1f)
        }
        drawable.draw(canvas)
        return Icon.createWithBitmap(bitmap)
    }

    private fun liveTrackerIconColor(): Int {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (nightMode == Configuration.UI_MODE_NIGHT_YES) Color.WHITE else Color.BLACK
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
        } catch (e: IllegalStateException) {
            AppLogger.w(TAG_LOCATION, "Live tracking foreground service could not enter foreground", e)
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
        liveOutageRefreshJob?.cancel()
        stopLocationUpdates()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
        return START_NOT_STICKY
    }

    private fun resetIfOperatorChanged(operator: String): Boolean {
        if (lockedOperator != null && lockedOperator != operator) {
            processingJob?.cancel()
            liveSitePhotoJob?.cancel()
            liveSitePhotoLoadingKey = null
            lastProcessedAt = 0L
            lockedOperator = null
            lockedAntennaId = null
            initialDistance = null
            lastDistanceToLockedAntenna = null
            return true
        }
        return false
    }

    private fun isMiles(context: Context): Boolean {
        val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
        return try {
            prefs.getInt("distance_unit", 0) == 1
        } catch (_: Exception) {
            prefs.getString("distance_unit", "0") == "1"
        }
    }

    private fun refreshFromLastProcessedLocation(): Boolean {
        val location = lastProcessedLocation?.let(::Location) ?: return false
        lastProcessedAt = 0L
        processLocationUpdate(location)
        return true
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
        val match = shortDistanceRegex.find(contentText) ?: return null
        val value = match.groupValues.getOrNull(1)
            ?.replace(',', '.')
            ?.toFloatOrNull()
            ?: return match.value.replace(" ", "").ifBlank { null }
        val unit = match.groupValues.getOrNull(2)?.lowercase(Locale.US).orEmpty()
        return formatShortCriticalDistance(value, unit).ifBlank { null }
    }

    private fun formatShortCriticalDistance(value: Float, unit: String): String {
        return when (unit) {
            "km" -> {
                if (value >= 100f) {
                    "${Math.round(value)}km"
                } else {
                    String.format(Locale.US, "%.1fkm", value)
                }
            }
            "mi" -> {
                if (value >= 100f) {
                    "${Math.round(value)}mi"
                } else {
                    String.format(Locale.US, "%.1fmi", value)
                }
            }
            else -> "${Math.round(value)}m"
        }
    }

    private fun operatorColor(operator: String): Int {
        return OperatorColors.colorInt(operator)
    }

    private fun operatorLogoIcon(operator: String): Icon? {
        return operatorLogoBitmap(operator)?.let { bitmap ->
            IconCompat.createWithBitmap(bitmap).toIcon(this)
        }
    }

    private fun operatorLogoBitmap(operator: String): Bitmap? {
        return OperatorLogos.drawableRes(operator)?.let(::roundedDrawableBitmap)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        processingJob?.cancel()
        liveSitePhotoJob?.cancel()
        liveSitePhotoLoadingKey = null
        liveOutageRefreshJob?.cancel()
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
        internal const val ACTION_REFRESH_NOTIFICATION = "ACTION_REFRESH_NOTIFICATION"
        internal const val ACTION_REFRESH_LOCATION_SETTINGS = "ACTION_REFRESH_LOCATION_SETTINGS"
        private const val STOP_ACTION_REQUEST_CODE = 1
        private const val MIN_PROCESS_INTERVAL_MS = 30_000L
        private const val MIN_PROCESS_DISTANCE_METERS = 15f
        private const val MOVING_AWAY_DISTANCE_THRESHOLD_METERS = 5f
        private const val KM_PER_LATITUDE_DEGREE = 111.0
        private const val MAX_SAMSUNG_NOW_BAR_TEXT_LENGTH = 80
        private const val MAX_SAMSUNG_CHIP_TEXT_LENGTH = 24
        private const val MAX_SAMSUNG_DRAWER_TITLE_LENGTH = 36
        private const val LIVE_TRACKER_ICON_DP = 24
        private const val LIVE_SITE_PHOTO_ICON_DP = 64
        private const val LIVE_SITE_PHOTO_CORNER_RADIUS_DP = 10
        private const val MIN_LIVE_SITE_PHOTO_ICON_PX = 96
        private const val MAX_LIVE_SITE_PHOTO_CACHE_ENTRIES = 12
        private const val LIVE_SITE_PHOTO_CONNECT_TIMEOUT_SECONDS = 4L
        private const val LIVE_SITE_PHOTO_READ_TIMEOUT_SECONDS = 6L
        private const val LIVE_SITE_PHOTO_CALL_TIMEOUT_SECONDS = 8L
        private const val LIVE_HS_OPERATOR_WILDCARD = "*"
        private const val LIVE_HS_TITLE_SEPARATOR = " \u2022 "
        private const val LIVE_HS_TITLE_LABEL = "HS"
        private const val GLOBAL_OPERATOR_FALLBACK_LIMIT = 100
        private const val TAG_LOCATION = "GeoTowerLocation"
        private val LIVE_OUTAGE_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(15)
        private val SEARCH_RADII_KM = doubleArrayOf(5.0, 10.0, 25.0, 50.0, 100.0)
        private val shortDistanceRegex =
            Regex("""(\d+(?:[.,]\d+)?)\s?(km|mi|m)""", RegexOption.IGNORE_CASE)
    }
}
