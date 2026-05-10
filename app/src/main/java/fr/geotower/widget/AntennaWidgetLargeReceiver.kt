package fr.geotower.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class AntennaWidgetLargeReceiver : GlanceAppWidgetReceiver() {

    // ✅ La magie opère ici : on renvoie toujours vers ton design responsive !
    override val glanceAppWidget = AntennaWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
        val freq = prefs.getInt("widget_sync_freq", 60).toLong()

        val periodicWork = PeriodicWorkRequestBuilder<AntennaWidgetWorker>(freq, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "WidgetPeriodicUpdate",
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWork
        )
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManager.getInstance(context).cancelUniqueWork("WidgetPeriodicUpdate")
    }
}
