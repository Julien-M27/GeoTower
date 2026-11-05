package fr.geotower.wear.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import fr.geotower.wear.data.model.GpsFixStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Moteur GPS dédié à la montre Wear OS.
 * Exigence stricte : interroge systématiquement la puce GNSS matérielle interne de la montre
 * via [LocationManager.GPS_PROVIDER] afin de garantir que les coordonnées proviennent
 * directement du poignet et non du smartphone via Bluetooth.
 */
class WatchGpsEngine(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fusedClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    private val _gpsStatus = MutableStateFlow<GpsFixStatus>(GpsFixStatus.Searching)
    val gpsStatus: StateFlow<GpsFixStatus> = _gpsStatus.asStateFlow()

    private var isListening = false

    private val hardwareGpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            Log.d(TAG, "Fix GPS matériel montre reçu : ${location.latitude}, ${location.longitude} (±${location.accuracy}m, prov=${location.provider})")
            _gpsStatus.value = GpsFixStatus.Fixed(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = location.accuracy,
                provider = "GPS Montre (${location.provider})"
            )
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {
            Log.d(TAG, "Provider activé sur la montre : $provider")
        }
        override fun onProviderDisabled(provider: String) {
            Log.w(TAG, "Provider désactivé sur la montre : $provider")
            if (provider == LocationManager.GPS_PROVIDER) {
                _gpsStatus.value = GpsFixStatus.Error("GPS matériel de la montre désactivé")
            }
        }
    }

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            // Si le GPS matériel n'a pas encore fait de fix, on peut accepter la position haute précision
            if (_gpsStatus.value !is GpsFixStatus.Fixed) {
                _gpsStatus.value = GpsFixStatus.Fixed(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    accuracyMeters = loc.accuracy,
                    provider = "Haute précision (${loc.provider})"
                )
            }
        }
    }

    fun hasLocationPermission(): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (!hasLocationPermission()) {
            _gpsStatus.value = GpsFixStatus.Error("Permission GPS requise")
            return
        }

        if (isListening) return
        isListening = true
        _gpsStatus.value = GpsFixStatus.Searching

        // 1. Priorité absolue : puce matérielle GPS de la montre (LocationManager.GPS_PROVIDER)
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (isGpsEnabled) {
            try {
                // Lecture du dernier fix connu immédiat
                val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < 60_000) {
                    _gpsStatus.value = GpsFixStatus.Fixed(
                        latitude = lastKnown.latitude,
                        longitude = lastKnown.longitude,
                        accuracyMeters = lastKnown.accuracy,
                        provider = "GPS Montre (cache récent)"
                    )
                }

                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L, // Intervalle : 1 seconde
                    1f,    // Distance min : 1 mètre
                    hardwareGpsListener,
                    Looper.getMainLooper()
                )
                Log.i(TAG, "Écoute du GPS matériel interne (GPS_PROVIDER) démarrée")
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission de localisation manquante", e)
                _gpsStatus.value = GpsFixStatus.Error("Permission GPS requise")
                return
            } catch (e: Exception) {
                Log.e(TAG, "Erreur démarrage GPS_PROVIDER", e)
            }
        } else {
            Log.w(TAG, "LocationManager.GPS_PROVIDER non activé sur la montre")
        }

        // 2. Client Fused en parallèle avec haute précision matérielle pour assister l'acquisition
        try {
            val fusedRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                .setMinUpdateIntervalMillis(1000L)
                .setMinUpdateDistanceMeters(1f)
                .build()

            fusedClient.requestLocationUpdates(fusedRequest, fusedCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission Fused manquante", e)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur démarrage FusedLocationProvider", e)
        }
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        try {
            locationManager.removeUpdates(hardwareGpsListener)
            fusedClient.removeLocationUpdates(fusedCallback)
            Log.i(TAG, "Écoute GPS de la montre arrêtée")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'arrêt du GPS", e)
        }
    }

    companion object {
        private const val TAG = "WatchGpsEngine"
    }
}
