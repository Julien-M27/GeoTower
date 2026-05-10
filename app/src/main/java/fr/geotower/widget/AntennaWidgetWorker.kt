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

// ⚠️ Assure-toi que cet import correspond au nom exact de ta base de données locale
import fr.geotower.data.db.AppDatabase
import fr.geotower.utils.AppLogger
import fr.geotower.utils.OperatorColors

data class WidgetSiteData(val id: String, val operateur: String, val distance: String, val adresse: String, val colorHex: String)

class AntennaWidgetWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
        val timeFormat = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())

        suspend fun updateUiAndFinish(isSuccess: Boolean, jsonToSave: String? = null) {
            val currentTime = timeFormat.format(java.util.Date())
            val timeString = if (isSuccess) currentTime else "$currentTime ⚠️"

            val editor = prefs.edit().putString("widget_last_update", timeString)
            if (jsonToSave != null) {
                editor.putString("widget_data_api", jsonToSave)
            }
            editor.apply()

            AntennaWidget().updateAll(context)
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            updateUiAndFinish(false)
            return Result.failure()
        }

        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

            // 🚀 REQUÊTE GPS (On augmente à 20 secondes pour laisser le temps au capteur de s'allumer)
            var location: Location? = withTimeoutOrNull(20000L) {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
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

            val antennas = dao.getLocalisationsInBox(
                minLat = lat - offsetLat,
                maxLat = lat + offsetLat,
                minLon = lon - offsetLon,
                maxLon = lon + offsetLon
            )

            if (antennas.isEmpty()) {
                updateUiAndFinish(true, "[]")
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

                val baseOrder = listOf("ORANGE", "BOUYGUES", "SFR", "FREE")

                val ops = siteAntennas.mapNotNull { it.operateur }
                    .flatMap { it.split(Regex("[/,\\-]")) }
                    .map { it.trim().uppercase() }
                    .filter { it.isNotEmpty() }
                    .sortedBy { op ->
                        val match = baseOrder.firstOrNull { op.contains(it) }
                        if (match != null) baseOrder.indexOf(match) else 99
                    }
                    .map { op ->
                        when {
                            op.contains("ORANGE") -> "ORANGE"
                            op.contains("BOUYGUES") -> "BOUYGUES"
                            op.contains("FREE") -> "FREE"
                            op.contains("SFR") -> "SFR"
                            else -> op
                        }
                    }
                    .distinct()

                val operateurText = ops.joinToString(" • ")

                val colorHex = if (ops.size == 1) {
                    OperatorColors.colorHex(ops[0], fallback = "#FFFFFF")
                } else {
                    "#MULTI"
                }

                val technique = dao.getTechniqueDetails(main.idAnfr.toString())
                val adresseAffichee = technique?.adresse ?: "Site N°${main.idAnfr}"

                WidgetSiteData(
                    id = main.idAnfr.toString(),
                    operateur = operateurText,
                    distance = distStr,
                    adresse = adresseAffichee,
                    colorHex = colorHex
                )
            }

            val json = Gson().toJson(widgetSites)

            // Tout s'est bien passé, on sauvegarde !
            updateUiAndFinish(true, json)
            return Result.success()

        } catch (e: Exception) {
            AppLogger.w(TAG, "Widget worker failed", e)
            updateUiAndFinish(false)
            return Result.retry()
        }
    }

    private companion object {
        private const val TAG = "GeoTower"
    }
}
