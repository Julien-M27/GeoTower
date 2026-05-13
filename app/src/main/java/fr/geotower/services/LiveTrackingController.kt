package fr.geotower.services

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

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
        val eligibility = eligibility(appContext)
        if (eligibility != StartResult.Started) {
            stop(appContext)
            return eligibility
        }

        val serviceIntent = Intent(appContext, LiveTrackingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(serviceIntent)
        } else {
            appContext.startService(serviceIntent)
        }
        return StartResult.Started
    }

    fun startOnAppLaunchIfEnabled(context: Context): StartResult {
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

    fun eligibility(context: Context): StartResult {
        return when {
            currentOperator(context) == "AUCUN" -> StartResult.MissingOperator
            !hasPreciseLocationPermission(context) -> StartResult.MissingPreciseLocation
            !hasPostNotificationsPermission(context) -> StartResult.MissingNotifications
            else -> StartResult.Started
        }
    }

    fun shouldOpenPromotedNotificationSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return !manager.canPostPromotedNotifications()
    }

    fun openPromotedNotificationSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return
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

    private fun currentOperator(context: Context): String {
        val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
        val rawOp = prefs.getString("default_operator", "Aucun")?.uppercase() ?: "AUCUN"
        return when {
            rawOp.contains("ORANGE") -> "ORANGE"
            rawOp.contains("BOUYGUES") -> "BOUYGUES"
            rawOp.contains("SFR") -> "SFR"
            rawOp.contains("FREE") -> "FREE"
            else -> rawOp
        }
    }
}
