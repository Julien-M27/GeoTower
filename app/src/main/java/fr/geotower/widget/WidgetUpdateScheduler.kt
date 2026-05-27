package fr.geotower.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import fr.geotower.utils.PreferenceStores
import fr.geotower.utils.WidgetPrefs
import java.util.concurrent.TimeUnit

object WidgetUpdateScheduler {
    const val UNIQUE_WORK_NAME = "WidgetPeriodicUpdate"

    fun schedulePeriodicUpdate(context: Context) {
        val prefs = context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
        schedulePeriodicUpdate(context, WidgetPrefs.syncFrequencyMinutes(prefs))
    }

    fun schedulePeriodicUpdate(context: Context, requestedFrequencyMinutes: Int) {
        val freqMinutes = normalizedFrequencyMinutes(requestedFrequencyMinutes)
        val periodicWork = PeriodicWorkRequestBuilder<AntennaWidgetWorker>(
            freqMinutes,
            TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWork
        )
    }

    fun cancelPeriodicUpdateIfNoWidgetsRemain(context: Context) {
        val counts = activeWidgetCounts(context.applicationContext)
        if (shouldCancelPeriodicWork(counts)) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }

    internal fun normalizedFrequencyMinutes(rawMinutes: Int): Long {
        return rawMinutes.coerceAtLeast(WidgetPrefs.MIN_SYNC_MINUTES).toLong()
    }

    internal fun shouldCancelPeriodicWork(activeWidgetCounts: List<Int>): Boolean {
        return activeWidgetCounts.all { it <= 0 }
    }

    private fun activeWidgetCounts(context: Context): List<Int> {
        val manager = AppWidgetManager.getInstance(context)
        return receiverClasses.map { receiverClass ->
            runCatching {
                manager.getAppWidgetIds(ComponentName(context, receiverClass)).size
            }.getOrDefault(0)
        }
    }

    private val receiverClasses = listOf(
        AntennaWidgetReceiver::class.java,
        AntennaWidgetMediumReceiver::class.java,
        AntennaWidgetLargeReceiver::class.java,
        AntennaMapWidgetReceiver::class.java
    )
}
