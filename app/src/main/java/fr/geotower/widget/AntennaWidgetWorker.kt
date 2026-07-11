package fr.geotower.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.gson.Gson
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import fr.geotower.GeoTowerApp
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.SiteHsEntity

// ⚠️ Assure-toi que cet import correspond au nom exact de ta base de données locale
import fr.geotower.data.db.AppDatabase
import fr.geotower.R
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.PreferenceStores

data class WidgetSiteData(val id: String, val operateur: String, val distance: String, val adresse: String, val colorHex: String)

private data class WidgetMapUpdate(
    val wideImagePath: String,
    val squareImagePath: String,
    val wideExpandedImagePath: String,
    val squareExpandedImagePath: String,
    val siteCount: Int,
    val centerLat: Double,
    val centerLon: Double
)

class AntennaWidgetWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (
            !RemoteFeatureFlags.isPlatformEnabled(RemoteFeatureFlags.Platform.WIDGETS) ||
            !RemoteFeatureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.WIDGET_UPDATE)
        ) {
            return Result.success()
        }
        // Aucun widget posé : on n'interroge PAS la localisation et on annule la tâche périodique
        // (couvre tous les chemins de planification, y compris un reset des réglages).
        if (!WidgetUpdateScheduler.hasAnyWidget(context)) {
            WidgetUpdateScheduler.cancelPeriodicUpdateIfNoWidgetsRemain(context)
            return Result.success()
        }
        val prefs = context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
        val timeFormat = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())

        suspend fun updateUiAndFinish(
            isSuccess: Boolean,
            jsonToSave: String? = null,
            mapUpdate: WidgetMapUpdate? = null,
            clearMap: Boolean = false
        ) {
            val currentTime = timeFormat.format(java.util.Date())
            val timeString = currentTime

            val editor = prefs.edit().putString("widget_last_update", timeString)
            if (jsonToSave != null) {
                editor.putString("widget_data_api", jsonToSave)
            }
            if (clearMap) {
                editor
                    .remove(PREF_WIDGET_MAP_IMAGE_PATH)
                    .remove(PREF_WIDGET_MAP_IMAGE_WIDE_PATH)
                    .remove(PREF_WIDGET_MAP_IMAGE_SQUARE_PATH)
                    .remove(PREF_WIDGET_MAP_IMAGE_WIDE_EXPANDED_PATH)
                    .remove(PREF_WIDGET_MAP_IMAGE_SQUARE_EXPANDED_PATH)
                    .remove(PREF_WIDGET_MAP_CENTER_LAT)
                    .remove(PREF_WIDGET_MAP_CENTER_LON)
                    .putInt(PREF_WIDGET_MAP_SITE_COUNT, 0)
            }
            if (mapUpdate != null) {
                editor
                    .putString(PREF_WIDGET_MAP_IMAGE_PATH, mapUpdate.wideImagePath)
                    .putString(PREF_WIDGET_MAP_IMAGE_WIDE_PATH, mapUpdate.wideImagePath)
                    .putString(PREF_WIDGET_MAP_IMAGE_SQUARE_PATH, mapUpdate.squareImagePath)
                    .putString(PREF_WIDGET_MAP_IMAGE_WIDE_EXPANDED_PATH, mapUpdate.wideExpandedImagePath)
                    .putString(PREF_WIDGET_MAP_IMAGE_SQUARE_EXPANDED_PATH, mapUpdate.squareExpandedImagePath)
                    .putInt(PREF_WIDGET_MAP_SITE_COUNT, mapUpdate.siteCount)
                    .putFloat(PREF_WIDGET_MAP_CENTER_LAT, mapUpdate.centerLat.toFloat())
                    .putFloat(PREF_WIDGET_MAP_CENTER_LON, mapUpdate.centerLon.toFloat())
            }
            editor.apply()

            AntennaWidget().updateAll(context)
            AntennaMapWidget().updateAll(context)
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            updateUiAndFinish(false)
            return Result.failure()
        }

        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

            // 🚀 REQUÊTE GPS (On augmente à 20 secondes pour laisser le temps au capteur de s'allumer)
            // Mode faible conso : BALANCED au lieu de HIGH_ACCURACY (widget périodique en arrière-plan).
            val widgetGpsPriority = if (fr.geotower.utils.PowerProfile.gpsBalanced) Priority.PRIORITY_BALANCED_POWER_ACCURACY else Priority.PRIORITY_HIGH_ACCURACY
            var location: Location? = withTimeoutOrNull(20000L) {
                fusedLocationClient.getCurrentLocation(
                    widgetGpsPriority,
                    CancellationTokenSource().token
                ).await()
            }

            // ✅ PLAN B : Si le capteur n'a pas accroché de satellite, on prend la dernière position en mémoire !
            if (location == null) {
                location = fusedLocationClient.lastLocation.await()
            }

            // Si VRAIMENT on a rien (même en mémoire), on abandonne
            if (location == null) {
                updateUiAndFinish(false)
                return Result.failure()
            }

            val lat = location.latitude
            val lon = location.longitude

            // RECHERCHE DANS LA BASE DE DONNÉES LOCALE
            val dao = AppDatabase.getDatabase(context).geoTowerDao()

            val offsetLat = 0.045
            val offsetLon = 0.045 / Math.cos(Math.toRadians(lat))

            val antennasInBox = dao.getLocalisationsInBox(
                minLat = lat - offsetLat,
                maxLat = lat + offsetLat,
                minLon = lon - offsetLon,
                maxLon = lon + offsetLon
            )
            val antennas = antennasInBox.ifEmpty {
                dao.getNearest100(lat, lon)
            }

            if (antennas.isEmpty()) {
                updateUiAndFinish(true, "[]", clearMap = true)
                return Result.success()
            }

            // REGROUPEMENT ET CALCUL DE DISTANCE
            val groupedSites = antennas.groupBy { antenna ->
                "${String.format(Locale.US, "%.4f", antenna.latitude)}_${String.format(Locale.US, "%.4f", antenna.longitude)}"
            }.values

            val closestSites = groupedSites.map { siteAntennas ->
                val main = siteAntennas.first()
                val results = FloatArray(1)
                Location.distanceBetween(lat, lon, main.latitude, main.longitude, results)
                siteAntennas to results[0]
            }.sortedBy { it.second }.take(10)

            val widgetSites = closestSites.map { (siteAntennas, distance) ->
                val main = siteAntennas.first()

                // ✅ NOUVEAU : Le Worker lit enfin la préférence (avec notre sécurité anti-crash)
                val isMi = try {
                    prefs.getInt("distance_unit", 0) == 1
                } catch (e: Exception) {
                    try {
                        prefs.getBoolean("distance_unit", false)
                    } catch (e2: Exception) {
                        false
                    }
                }

                // ✅ NOUVEAU : Il applique la bonne unité (Miles ou Km)
                val distStr = if (isMi) {
                    val distMiles = distance / 1609.34f
                    if (distMiles < 0.1f) {
                        "${(distance * 3.28084f).toInt()} ft"
                    } else {
                        String.format(Locale.US, "%.2f mi", distMiles)
                    }
                } else {
                    if (distance >= 1000) {
                        String.format(Locale.US, "%.1f km", distance / 1000f)
                    } else {
                        "${distance.toInt()} m"
                    }
                }

                val ops = siteAntennas.mapNotNull { it.operateur }
                    .flatMap { OperatorColors.keysFor(it) }
                    .distinct()
                    .sortedBy { op -> OperatorColors.orderedKeys.indexOf(op).takeIf { it >= 0 } ?: 99 }

                val operateurText = ops.joinToString(" • ")

                val colorHex = if (ops.size == 1) {
                    OperatorColors.colorHex(ops[0], fallback = "#FFFFFF")
                } else {
                    "#MULTI"
                }

                val technique = dao.getTechniqueDetails(main.idAnfr.toString())
                val adresseAffichee = technique?.adresse ?: context.getString(R.string.site_anfr_title, main.idAnfr.toString())

                WidgetSiteData(
                    id = main.idAnfr.toString(),
                    operateur = operateurText,
                    distance = distStr,
                    adresse = adresseAffichee,
                    colorHex = colorHex
                )
            }

            val mapIsMi = readWidgetUsesMiles(prefs)
            val hsOperatorMap = loadHsOperatorMap()
            val mapSites = closestSites.map { (siteAntennas, distance) ->
                val main = siteAntennas.first()
                val ops = siteAntennas.mapNotNull { it.operateur }
                    .flatMap { OperatorColors.keysFor(it) }
                    .distinct()
                    .sortedBy { op -> OperatorColors.orderedKeys.indexOf(op).takeIf { it >= 0 } ?: 99 }

                WidgetMapSiteData(
                    id = main.idAnfr.toString(),
                    operatorKeys = ops,
                    distanceMeters = distance,
                    distanceLabel = formatWidgetDistance(distance, mapIsMi),
                    latitude = main.latitude,
                    longitude = main.longitude,
                    hasOutage = hasWidgetHsOperator(siteAntennas, hsOperatorMap),
                    antennas = siteAntennas.map { antenna ->
                        WidgetMapAntennaData(
                            id = antenna.idAnfr.toString(),
                            operatorName = antenna.operateur,
                            azimuts = antenna.azimuts,
                            azimutsFh = antenna.azimutsFh
                        )
                    }
                )
            }

            val mapUpdate = if (mapSites.isNotEmpty()) {
                val mapData = WidgetMapData(
                    userLat = lat,
                    userLon = lon,
                    sites = mapSites
                )
                runCatching {
                    val imagePaths = AntennaMapWidgetRenderer.renderAndSaveVariants(
                        context = context,
                        data = mapData,
                        mapProvider = prefs.getInt("map_provider", 1),
                        ignStyle = prefs.getInt("ign_style", 0),
                        options = WidgetMapRenderOptions(
                            defaultOperator = prefs.getString("default_operator", "Aucun") ?: "Aucun",
                            showAzimuths = prefs.getBoolean(
                                AppConfig.PREF_SHOW_AZIMUTH_LINES,
                                AppConfig.DEFAULT_SHOW_AZIMUTH_LINES
                            ),
                            showAzimuthCones = prefs.getBoolean(
                                AppConfig.PREF_SHOW_AZIMUTH_CONES,
                                AppConfig.DEFAULT_SHOW_AZIMUTH_CONES
                            ),
                            showTechnoFh = prefs.getBoolean("show_techno_fh", true)
                        )
                    )
                    WidgetMapUpdate(
                        wideImagePath = imagePaths.widePath,
                        squareImagePath = imagePaths.squarePath,
                        wideExpandedImagePath = imagePaths.wideExpandedPath,
                        squareExpandedImagePath = imagePaths.squareExpandedPath,
                        siteCount = mapSites.size,
                        centerLat = lat,
                        centerLon = lon
                    )
                }.onFailure { error ->
                    AppLogger.w(TAG, "Map widget render failed", error)
                }.getOrNull()
            } else {
                null
            }

            val json = Gson().toJson(widgetSites)

            // Tout s'est bien passé, on sauvegarde !
            updateUiAndFinish(true, json, mapUpdate = mapUpdate)
            return Result.success()

        } catch (e: Exception) {
            AppLogger.w(TAG, "Widget worker failed", e)
            updateUiAndFinish(false)
            return Result.retry()
        }
    }

    private fun readWidgetUsesMiles(prefs: android.content.SharedPreferences): Boolean {
        return try {
            prefs.getInt("distance_unit", 0) == 1
        } catch (e: Exception) {
            try {
                prefs.getBoolean("distance_unit", false)
            } catch (e2: Exception) {
                false
            }
        }
    }

    private suspend fun loadHsOperatorMap(): Map<String, Set<String>> {
        val sitesHs = runCatching {
            (context.applicationContext as? GeoTowerApp)
                ?.repository
                ?.getSitesHs()
                .orEmpty()
        }.onFailure { error ->
            AppLogger.w(TAG, "Map widget outage data failed", error)
        }.getOrDefault(emptyList())

        return buildWidgetHsOperatorMap(sitesHs)
    }

    private fun buildWidgetHsOperatorMap(sitesHs: List<SiteHsEntity>): Map<String, Set<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()

        sitesHs.forEach { hs ->
            val id = normalizedWidgetAnfrId(hs.idAnfr)
            if (id.isBlank()) return@forEach

            val parsedOperators = OperatorColors.keysFor(hs.operateur)
            val operators = if (parsedOperators.isEmpty()) {
                listOf(WIDGET_HS_OPERATOR_WILDCARD)
            } else {
                parsedOperators
            }
            result.getOrPut(id) { mutableSetOf() }.addAll(operators)
        }

        return result
    }

    private fun hasWidgetHsOperator(
        siteAntennas: List<LocalisationEntity>,
        hsOperatorMap: Map<String, Set<String>>
    ): Boolean {
        return siteAntennas.any { antenna ->
            val hsOperators = hsOperatorMap[normalizedWidgetAnfrId(antenna.idAnfr)] ?: return@any false
            OperatorColors.keysFor(antenna.operateur).any { operatorKey ->
                WIDGET_HS_OPERATOR_WILDCARD in hsOperators || operatorKey in hsOperators
            }
        }
    }

    private fun normalizedWidgetAnfrId(value: String): String {
        val trimmed = value.trim()
        return trimmed.toLongOrNull()?.toString() ?: trimmed
    }

    private fun formatWidgetDistance(distanceMeters: Float, useMiles: Boolean): String {
        return if (useMiles) {
            val distMiles = distanceMeters / 1609.34f
            if (distMiles < 0.1f) {
                "${(distanceMeters * 3.28084f).toInt()} ft"
            } else {
                String.format(Locale.US, "%.2f mi", distMiles)
            }
        } else {
            if (distanceMeters >= 1000f) {
                String.format(Locale.US, "%.1f km", distanceMeters / 1000f)
            } else {
                "${distanceMeters.toInt()} m"
            }
        }
    }

    private companion object {
        private const val TAG = "GeoTower"
        private const val WIDGET_HS_OPERATOR_WILDCARD = "*"
    }
}
