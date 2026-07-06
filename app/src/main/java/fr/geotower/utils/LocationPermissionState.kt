package fr.geotower.utils

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val TAG_LOCATION_PERMISSION = "GeoTowerLocation"

/**
 * État de disponibilité de la localisation, du point de vue de l'app.
 *
 * Deux causes distinctes empêchent de géolocaliser l'utilisateur, avec deux corrections différentes :
 * - [PermissionMissing] : l'autorisation n'est pas accordée → il faut la demander (ou ouvrir les réglages app).
 * - [ServicesOff] : l'autorisation est là mais la localisation (GPS) est coupée au niveau système → il faut
 *   l'activer dans les réglages de localisation.
 */
enum class LocationReadiness { Ready, PermissionMissing, ServicesOff }

/** Renvoie true si l'utilisateur a accordé une permission de localisation (précise ou approximative). */
fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

/** Renvoie true si la localisation (GPS/services de localisation) est activée au niveau système. */
fun isLocationServicesEnabled(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        lm.isLocationEnabled
    } else {
        @Suppress("DEPRECATION")
        runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ||
            runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
    }
}

/** Combine permission + services système en un seul verdict (la permission prime). */
fun locationReadiness(context: Context): LocationReadiness = when {
    !hasLocationPermission(context) -> LocationReadiness.PermissionMissing
    !isLocationServicesEnabled(context) -> LocationReadiness.ServicesOff
    else -> LocationReadiness.Ready
}

/**
 * État réactif de la disponibilité de la localisation (permission + services système).
 *
 * Ré-évalué silencieusement :
 * - à chaque retour au premier plan (ON_RESUME de l'Activity) → couvre les changements faits dans les
 *   réglages système (permission coupée, localisation désactivée) puis retour dans l'app ;
 * - **en temps réel** via un BroadcastReceiver sur PROVIDERS_CHANGED / MODE_CHANGED → couvre le bascule
 *   du GPS depuis les réglages rapides (le volet ne déclenche pas d'ON_RESUME car l'app reste au premier plan).
 *
 * Renvoie un [MutableState] pour que l'écran puisse aussi rafraîchir immédiatement depuis le callback de
 * demande de permission (retour instantané au moment de l'octroi).
 */
@Composable
fun rememberLocationReadinessState(): MutableState<LocationReadiness> {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val fallbackOwner = LocalLifecycleOwner.current
    val readiness = remember { mutableStateOf(locationReadiness(context)) }

    DisposableEffect(activity, fallbackOwner) {
        val lifecycle = (activity as? LifecycleOwner)?.lifecycle ?: fallbackOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                readiness.value = locationReadiness(context)
            }
        }
        // addObserver rejoue les événements jusqu'à l'état courant : si l'écran est déjà RESUMED
        // (retour par navigation), ON_RESUME est dispatché tout de suite → re-check immédiat.
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                readiness.value = locationReadiness(context)
            }
        }
        val filter = IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        // Broadcasts système protégés → RECEIVER_NOT_EXPORTED (recommandé pour un simple écouteur système).
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    return readiness
}

/**
 * Ouvre la page de réglages système de l'app.
 *
 * Sert de repli lorsque la localisation a été refusée définitivement (« Ne plus demander ») :
 * la boîte de dialogue système ne s'affiche plus, il faut donc renvoyer l'utilisateur vers les
 * réglages pour réactiver l'autorisation.
 */
fun openAppLocationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        AppLogger.w(TAG_LOCATION_PERMISSION, "Unable to open app settings for location permission", e)
    }
}

/** Ouvre les réglages système de localisation (pour réactiver le GPS quand les services sont coupés). */
fun openLocationSourceSettings(context: Context) {
    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        AppLogger.w(TAG_LOCATION_PERMISSION, "Unable to open location source settings", e)
        openAppLocationSettings(context)
    }
}
