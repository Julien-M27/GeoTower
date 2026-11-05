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
    private var currentRefreshInterval: fr.geotower.wear.data.model.GpsRefreshInterval = fr.geotower.wear.data.model.GpsRefreshInterval.CONTINUOUS

    private val hardwareGpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            Log.d(TAG, "Fix GPS matériel montre reçu : ${location.latitude}, ${location.longitude} (±${location.accuracy}m, prov=${location.provider})")
            _gpsStatus.value = GpsFixStatus.Fixed(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = location.accuracy,
                provider = "GPS Montre (${location.provider})"
            )
            if (currentRefreshInterval == fr.geotower.wear.data.model.GpsRefreshInterval.ON_OPEN) {
                Log.d(TAG, "Mode 'À l'ouverture' : position acquise, coupure du GPS pour économiser la batterie")
                stopListening()
            }
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
            Log.d(TAG, "Fix GPS montre reçu (fused) : ${loc.latitude}, ${loc.longitude} (±${loc.accuracy}m)")
            _gpsStatus.value = GpsFixStatus.Fixed(
                latitude = loc.latitude,
                longitude = loc.longitude,
                accuracyMeters = loc.accuracy,
                provider = "GPS Montre"
            )
            if (currentRefreshInterval == fr.geotower.wear.data.model.GpsRefreshInterval.ON_OPEN) {
                Log.d(TAG, "Mode 'À l'ouverture' (fused) : position acquise, coupure du GPS")
                stopListening()
            }
        }
    }

    fun setRefreshInterval(interval: fr.geotower.wear.data.model.GpsRefreshInterval) {
        if (currentRefreshInterval != interval) {
            currentRefreshInterval = interval
            if (isListening) {
                stopListening()
                startListening()
            }
        }
    }

    /**
     * Force une réactualisation immédiate des coordonnées GPS de la montre,
     * qu'elle soit en veille ou en mode 'À l'ouverture'.
     */
    @SuppressLint("MissingPermission")
    fun forceRefreshLocation(onLocationReceived: ((Location) -> Unit)? = null) {
        if (!hasLocationPermission()) {
            _gpsStatus.value = GpsFixStatus.Error("Permission GPS requise")
            return
        }

        // 1. Tente d'abord de lire une position récente connue
        val lastGps = try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            null
        }

        if (lastGps != null) {
            _gpsStatus.value = GpsFixStatus.Fixed(
                latitude = lastGps.latitude,
                longitude = lastGps.longitude,
                accuracyMeters = lastGps.accuracy,
                provider = "GPS Montre"
            )
            onLocationReceived?.invoke(lastGps)
        }

        // 2. Demande une acquisition fraîche haute précision
        try {
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        _gpsStatus.value = GpsFixStatus.Fixed(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            accuracyMeters = loc.accuracy,
                            provider = "GPS Montre"
                        )
                        onLocationReceived?.invoke(loc)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "getCurrentLocation a échoué", e)
        }

        // 3. Relance l'écoute continue ou temporaire si nécessaire
        if (!isListening) {
            startListening()
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

                val gpsInterval = when (currentRefreshInterval) {
                    fr.geotower.wear.data.model.GpsRefreshInterval.ON_OPEN -> 1000L
                    fr.geotower.wear.data.model.GpsRefreshInterval.EVERY_1_MIN -> 60_000L
                    fr.geotower.wear.data.model.GpsRefreshInterval.CONTINUOUS -> 1000L
                }

                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    gpsInterval,
                    if (currentRefreshInterval == fr.geotower.wear.data.model.GpsRefreshInterval.CONTINUOUS) 1f else 5f,
                    hardwareGpsListener,
                    Looper.getMainLooper()
                )
                Log.i(TAG, "Écoute du GPS matériel interne (GPS_PROVIDER) démarrée (mode=$currentRefreshInterval, intervalle=${gpsInterval}ms)")
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
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && _gpsStatus.value !is GpsFixStatus.Fixed) {
                    Log.d(TAG, "Dernière position connue montre lue : ${loc.latitude}, ${loc.longitude}")
                    _gpsStatus.value = GpsFixStatus.Fixed(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracyMeters = loc.accuracy,
                        provider = "GPS Montre (récent)"
                    )
                }
            }

            val fusedInterval = when (currentRefreshInterval) {
                fr.geotower.wear.data.model.GpsRefreshInterval.ON_OPEN -> 2000L
                fr.geotower.wear.data.model.GpsRefreshInterval.EVERY_1_MIN -> 60_000L
                fr.geotower.wear.data.model.GpsRefreshInterval.CONTINUOUS -> 2000L
            }

            val fusedRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, fusedInterval)
                .setMinUpdateIntervalMillis(fusedInterval / 2)
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
