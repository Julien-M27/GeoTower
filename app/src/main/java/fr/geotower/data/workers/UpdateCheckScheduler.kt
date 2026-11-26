package fr.geotower.data.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import fr.geotower.data.config.RemoteFeatureFlags
import java.util.Calendar
import java.util.concurrent.TimeUnit

object UpdateCheckScheduler {
    const val DAILY_WORK_NAME = "DailyUpdateCheckAt20"
    const val LEGACY_PERIODIC_WORK_NAME = "PeriodicUpdateCheck"

    private const val PREFS_NAME = "GeoTowerPrefs"
    private const val UPDATE_NOTIFICATIONS_KEY = "enable_update_notifications"
    private const val DEFAULT_UPDATE_NOTIFICATIONS_ENABLED = true
    private val REGULAR_TARGET_HOURS = listOf(8, 20)
    private val THURSDAY_TARGET_HOURS = listOf(8, 15, 16, 17, 18, 19, 20, 21, 22, 23)
    private val FRIDAY_TARGET_HOURS = listOf(0, 8, 20)

    fun reconcile(context: Context) {
        cancelLegacyPeriodicCheck(context)

        if (areUpdateNotificationsEnabled(context) && canScheduleAnyUpdateCheck()) {
            enqueueNextCheck(context, ExistingWorkPolicy.KEEP)
        } else {
            cancelDailyCheck(context)
        }
    }

    fun onNotificationsPreferenceChanged(context: Context, enabled: Boolean) {
        cancelLegacyPeriodicCheck(context)

        if (enabled && canScheduleAnyUpdateCheck()) {
            enqueueNextCheck(context, ExistingWorkPolicy.REPLACE)
        } else {
            cancelDailyCheck(context)
        }
    }

    fun scheduleNextAfterSuccessfulRun(context: Context) {
        if (areUpdateNotificationsEnabled(context) && canScheduleAnyUpdateCheck()) {
            enqueueNextCheck(context, ExistingWorkPolicy.APPEND_OR_REPLACE)
        } else {
            cancelDailyCheck(context)
        }
    }

    fun cancelDailyCheck(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(DAILY_WORK_NAME)
    }

    private fun cancelLegacyPeriodicCheck(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(LEGACY_PERIODIC_WORK_NAME)
    }

    private fun enqueueNextCheck(context: Context, policy: ExistingWorkPolicy) {
        val appContext = context.applicationContext
        val delayMillis = delayUntilNextTargetMillis()
        val request = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            DAILY_WORK_NAME,
            policy,
            request
        )
    }

    internal fun delayUntilNextTargetMillis(
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: java.util.TimeZone = java.util.TimeZone.getDefault()
    ): Long {
        val now = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
        }

        for (dayOffset in 0..1) {
            val day = Calendar.getInstance(timeZone).apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            for (hour in targetHoursForDay(day.get(Calendar.DAY_OF_WEEK))) {
                day.set(Calendar.HOUR_OF_DAY, hour)
                if (day.after(now)) {
                    return (day.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
                }
            }
        }

        return TimeUnit.HOURS.toMillis(12)
    }

    private fun targetHoursForDay(dayOfWeek: Int): List<Int> = when (dayOfWeek) {
        Calendar.THURSDAY -> THURSDAY_TARGET_HOURS
        Calendar.FRIDAY -> FRIDAY_TARGET_HOURS
        else -> REGULAR_TARGET_HOURS
    }

    fun areUpdateNotificationsEnabled(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(UPDATE_NOTIFICATIONS_KEY, DEFAULT_UPDATE_NOTIFICATIONS_ENABLED)
    }

    private fun canScheduleAnyUpdateCheck(): Boolean {
        val canCheckDatabase = RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.DATABASE_UPDATE_CHECK) &&
            RemoteFeatureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.DATABASE_UPDATE_CHECK)
        val canCheckApp = RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.APP_UPDATE_CHECK) &&
            RemoteFeatureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.APP_UPDATE_CHECK)
        return canCheckDatabase || canCheckApp
    }
}
