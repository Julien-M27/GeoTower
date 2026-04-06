package fr.geotower.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class AntennaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = AntennaWidget()

    // Se déclenche quand le widget est ajouté à l'écran d'accueil
    override fun onEnabled(context: Context) {
        super.onEnabled(context)

        // Récupère la fréquence sauvegardée (ou 30 min par défaut)
        val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
        val freq = prefs.getInt("widget_sync_freq", 30).toLong()

        val periodicWork = PeriodicWorkRequestBuilder<AntennaWidgetWorker>(
            freq, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "WidgetPeriodicUpdate",
            ExistingPeriodicWorkPolicy.UPDATE, // UPDATE remplace l'ancien timer proprement
            periodicWork
        )
    }

    // Se déclenche quand le widget est supprimé de l'écran
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // On annule la tâche pour ne pas vider la batterie pour rien !
        WorkManager.getInstance(context).cancelUniqueWork("WidgetPeriodicUpdate")
    }
}