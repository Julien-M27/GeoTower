package fr.geotower.services

import android.graphics.Bitmap
import android.location.Location
import fr.geotower.data.models.LocalisationEntity

/** Snapshot of the last live-tracking content rendered in the foreground notification. */
internal data class LiveTrackingNotificationState(
    val contentText: String,
    val userLoc: Location? = null,
    val antLoc: LocalisationEntity? = null,
    val operator: String,
    val progress: Int = 0,
    val address: String = "",
    val sitePhotoBitmap: Bitmap? = null,
    val mirrorTrackerIcon: Boolean = false,
) {
    companion object {
        fun forPause(
            last: LiveTrackingNotificationState?,
            fallback: LiveTrackingNotificationState,
        ): LiveTrackingNotificationState = last ?: fallback
    }
}
