package fr.geotower.utils

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import fr.geotower.data.config.RemoteFeatureFlags

/**
 * Interrupteur maître des notifications de GeoTower, indépendant du suivi live.
 *
 * Historiquement le seul réglage qui ouvrait la porte aux notifications était « Notification live »,
 * qui démarre aussi le suivi GPS : impossible d'avoir les notifications de l'app sans le tracking.
 * Ce réglage-ci porte donc la partie « notifications » (demande de permission incluse) et pilote
 * toutes celles que l'app choisit d'envoyer : base téléchargée ou à jour, mise à jour de l'app,
 * pannes récupérées, rapport PDF prêt, envoi de photos terminé, carte hors ligne téléchargée,
 * génération locale terminée. Le suivi live garde son propre interrupteur.
 *
 * Ne concerne PAS les notifications de progression imposées par Android pendant qu'un travail de
 * premier plan tourne (téléchargement, génération, envoi) : elles accompagnent le service, ne sont
 * pas décidées par l'app et disparaissent avec lui.
 */
object AppNotifications {
    const val PREF_ENABLED = "enable_app_notifications"
    const val DEFAULT_ENABLED = true

    fun enabled(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_ENABLED, DEFAULT_ENABLED)

    fun enabled(context: Context): Boolean = enabled(appPrefs(context))

    /**
     * Vrai si l'app peut réellement poster une notification informative : réglage de l'utilisateur,
     * kill-switch distant et permission système. À appeler juste avant chaque `notify()`.
     */
    fun canPost(context: Context): Boolean {
        if (!RemoteFeatureFlags.isPlatformEnabled(RemoteFeatureFlags.Platform.NOTIFICATIONS)) return false
        if (!enabled(context)) return false
        return hasPermission(context)
    }

    fun hasPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    /** Écrit le réglage et met à jour l'état miroité en mémoire. */
    fun setEnabled(context: Context, enabled: Boolean) {
        AppConfig.enableAppNotifications.value = enabled
        appPrefs(context).edit().putBoolean(PREF_ENABLED, enabled).apply()
    }

    private fun appPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
    }
}
