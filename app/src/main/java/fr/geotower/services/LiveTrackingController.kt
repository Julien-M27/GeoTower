package fr.geotower.services

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.utils.AppLogger

object LiveTrackingController {

    enum class StartResult {
        Stopped,
        Started,
        MissingOperator,
        MissingPreciseLocation,
        MissingNotifications
    }

    fun startIfEligible(context: Context): StartResult {
        val appContext = context.applicationContext
        if (!RemoteFeatureFlags.isPlatformEnabled(RemoteFeatureFlags.Platform.LIVE_TRACKING)) {
            stop(appContext)
            return StartResult.Stopped
        }
        val eligibility = eligibility(appContext)
        if (eligibility != StartResult.Started) {
            stop(appContext)
            return eligibility
        }

        if (LiveTrackingService.isRunning) {
            refreshNotification(appContext)
            return StartResult.Started
        }

        val serviceIntent = Intent(appContext, LiveTrackingService::class.java)
        return startLiveTrackingService(appContext, serviceIntent)
    }

    fun startOnAppLaunchIfEnabled(context: Context): StartResult {
        if (!RemoteFeatureFlags.isPlatformEnabled(RemoteFeatureFlags.Platform.LIVE_TRACKING)) return StartResult.Stopped
        val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enable_live_notifications", false)) return StartResult.Stopped
        if (LiveTrackingService.isRunning) return StartResult.Started
        return startIfEligible(context)
    }

    fun stop(context: Context) {
        context.applicationContext.stopService(
            Intent(context.applicationContext, LiveTrackingService::class.java)
        )
    }

    fun refreshNotification(context: Context) {
        if (!LiveTrackingService.isRunning) return
        val appContext = context.applicationContext
        val serviceIntent = Intent(appContext, LiveTrackingService::class.java).apply {
            action = LiveTrackingService.ACTION_REFRESH_NOTIFICATION
        }
        try {
            appContext.startService(serviceIntent)
        } catch (e: SecurityException) {
            AppLogger.w(TAG, "Live tracking notification refresh blocked by permissions", e)
        } catch (e: IllegalStateException) {
            AppLogger.w(TAG, "Live tracking notification refresh blocked by app state", e)
        }
    }

    fun refreshLocationSettings(context: Context) {
        if (!LiveTrackingService.isRunning) return
        val appContext = context.applicationContext
        val serviceIntent = Intent(appContext, LiveTrackingService::class.java).apply {
            action = LiveTrackingService.ACTION_REFRESH_LOCATION_SETTINGS
        }
        try {
            appContext.startService(serviceIntent)
        } catch (e: SecurityException) {
            AppLogger.w(TAG, "Live tracking location settings refresh blocked by permissions", e)
        } catch (e: IllegalStateException) {
            AppLogger.w(TAG, "Live tracking location settings refresh blocked by app state", e)
        }
    }

    fun eligibility(context: Context): StartResult {
        // Plus d'exigence d'opérateur : sans opérateur choisi, le suivi se rabat sur
        // l'antenne la plus proche (tous opérateurs). On garde StartResult.MissingOperator
        // dans l'enum pour compatibilité, mais l'éligibilité ne le renvoie plus.
        return when {
            !RemoteFeatureFlags.isPlatformEnabled(RemoteFeatureFlags.Platform.LIVE_TRACKING) -> StartResult.Stopped
            !hasPreciseLocationPermission(context) -> StartResult.MissingPreciseLocation
            !hasPostNotificationsPermission(context) -> StartResult.MissingNotifications
            else -> StartResult.Started
        }
    }

    fun shouldOpenPromotedNotificationSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return false
        if (!RemoteFeatureFlags.isPlatformEnabled(RemoteFeatureFlags.Platform.PROMOTED_NOTIFICATIONS)) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return !manager.canPostPromotedNotifications()
    }

    fun openPromotedNotificationSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return
        if (!RemoteFeatureFlags.isPlatformEnabled(RemoteFeatureFlags.Platform.PROMOTED_NOTIFICATIONS)) return
        val appContext = context.applicationContext
        val promotedIntent = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            appContext.startActivity(promotedIntent)
        } catch (_: Exception) {
            val fallbackIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { appContext.startActivity(fallbackIntent) }
        }
    }

    /**
     * Vrai si l'app est exemptée des optimisations d'énergie (Doze / App Standby). Sans cette
     * exemption, les OEM agressifs (Samsung/One UI) gèlent le service de premier plan dès que
     * l'app quitte le premier plan → les positions GPS du suivi live s'arrêtent.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Ouvre la boîte de dialogue système demandant à ignorer les optimisations de batterie.
     * Action explicite déclenchée depuis une carte des réglages (pas un popup automatique).
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Certains appareils n'exposent pas l'écran direct : on retombe sur la liste générale.
            AppLogger.w(TAG, "Battery optimization request dialog unavailable, opening settings", e)
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallback) }
        }
    }

    fun hasPreciseLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasPostNotificationsPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startLiveTrackingService(context: Context, serviceIntent: Intent): StartResult {
        return try {
            ContextCompat.startForegroundService(context, serviceIntent)
            StartResult.Started
        } catch (e: SecurityException) {
            AppLogger.w(TAG, "Live tracking foreground service start blocked by permissions", e)
            stop(context)
            StartResult.Stopped
        } catch (e: IllegalStateException) {
            AppLogger.w(TAG, "Live tracking foreground service start blocked by app state", e)
            stop(context)
            StartResult.Stopped
        }
    }

    private const val TAG = "LiveTracking"
}
