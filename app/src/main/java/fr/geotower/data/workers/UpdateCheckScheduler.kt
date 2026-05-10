package fr.geotower.data.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object UpdateCheckScheduler {
    const val DAILY_WORK_NAME = "DailyUpdateCheckAt20"
    const val LEGACY_PERIODIC_WORK_NAME = "PeriodicUpdateCheck"

    private const val PREFS_NAME = "GeoTowerPrefs"
    private const val UPDATE_NOTIFICATIONS_KEY = "enable_update_notifications"
    private const val DEFAULT_UPDATE_NOTIFICATIONS_ENABLED = true
    private const val TARGET_HOUR = 20
    private const val TARGET_MINUTE = 0

    fun reconcile(context: Context) {
        cancelLegacyPeriodicCheck(context)

        if (areUpdateNotificationsEnabled(context)) {
            enqueueNextCheck(context, ExistingWorkPolicy.KEEP)
        } else {
            cancelDailyCheck(context)
        }
    }

    fun onNotificationsPreferenceChanged(context: Context, enabled: Boolean) {
        cancelLegacyPeriodicCheck(context)

        if (enabled) {
            enqueueNextCheck(context, ExistingWorkPolicy.REPLACE)
        } else {
            cancelDailyCheck(context)
        }
    }

    fun scheduleNextAfterSuccessfulRun(context: Context) {
        if (areUpdateNotificationsEnabled(context)) {
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

    internal fun delayUntilNextTargetMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val now = Calendar.getInstance().apply {
            timeInMillis = nowMillis
        }
        val next = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, TARGET_HOUR)
            set(Calendar.MINUTE, TARGET_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (!next.after(now)) {
            next.add(Calendar.DAY_OF_YEAR, 1)
        }

        return (next.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
    }

    fun areUpdateNotificationsEnabled(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(UPDATE_NOTIFICATIONS_KEY, DEFAULT_UPDATE_NOTIFICATIONS_ENABLED)
    }
}
