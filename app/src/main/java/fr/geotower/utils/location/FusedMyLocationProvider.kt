package fr.geotower.utils.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import fr.geotower.utils.AppLogger
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.IMyLocationConsumer
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider

/**
 * Source de position de la carte, branchée sur le fused provider de Google.
 *
 * osmdroid fournit [GpsMyLocationProvider], qui écoute `LocationManager` en direct : des points
 * bruts, non fusionnés, plus nerveux, et dont la vitesse et le cap sont moins fiables — or ce sont
 * précisément ces deux champs qui alimentent l'extrapolation de [SmoothLocationEngine]. Le reste de
 * l'application (suivi live, `LocationHelper`) passe déjà par le fused provider ; cette classe met la
 * carte au même régime.
 *
 * [GpsMyLocationProvider] reste en repli si les services Google sont absents ou refusent la demande,
 * de sorte que la carte se localise toujours.
 */
class FusedMyLocationProvider(
    private val context: Context,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS
) : IMyLocationProvider {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    private var consumer: IMyLocationConsumer? = null
    private var fallback: GpsMyLocationProvider? = null

    @Volatile
    private var lastKnown: Location? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            lastKnown = location
            consumer?.onLocationChanged(location, this@FusedMyLocationProvider)
        }
    }

    @SuppressLint("MissingPermission")
    override fun startLocationProvider(myLocationConsumer: IMyLocationConsumer?): Boolean {
        consumer = myLocationConsumer
        if (!hasLocationPermission()) return false

        // osmdroid rappelle enableMyLocation() à chaque reprise : on repart toujours d'un abonnement
        // propre plutôt que d'en empiler plusieurs.
        stopUpdates()

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setWaitForAccurateLocation(false)
            .build()

        return try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { error ->
                    AppLogger.w(TAG, "Fused location updates refused, falling back", error)
                    startFallback(myLocationConsumer)
                }
            fusedClient.lastLocation.addOnSuccessListener { location ->
                // Sert d'amorce à runOnFirstFix : sans elle, le premier recentrage attend le premier
                // vrai point, soit plusieurs secondes à froid.
                if (location != null && lastKnown == null) {
                    lastKnown = location
                    consumer?.onLocationChanged(location, this)
                }
            }
            true
        } catch (e: SecurityException) {
            AppLogger.w(TAG, "Location permission revoked while starting", e)
            false
        } catch (e: Exception) {
            AppLogger.w(TAG, "Fused provider unavailable, falling back", e)
            startFallback(myLocationConsumer)
        }
    }

    override fun stopLocationProvider() {
        stopUpdates()
        fallback?.stopLocationProvider()
    }

    override fun getLastKnownLocation(): Location? = lastKnown ?: fallback?.lastKnownLocation

    override fun destroy() {
        stopLocationProvider()
        fallback?.destroy()
        fallback = null
        consumer = null
        lastKnown = null
    }

    private fun stopUpdates() {
        runCatching { fusedClient.removeLocationUpdates(callback) }
    }

    @SuppressLint("MissingPermission")
    private fun startFallback(myLocationConsumer: IMyLocationConsumer?): Boolean {
        val provider = fallback ?: GpsMyLocationProvider(context).also { fallback = it }
        return runCatching {
            provider.startLocationProvider { location, source ->
                if (location != null) lastKnown = location
                myLocationConsumer?.onLocationChanged(location, source)
            }
        }.getOrDefault(false)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        private const val TAG = "GeoTowerFusedProvider"

        /** Cadence historique de la carte : un point par seconde, comme le GPS brut d'osmdroid. */
        const val DEFAULT_INTERVAL_MS = 1000L
    }
}
