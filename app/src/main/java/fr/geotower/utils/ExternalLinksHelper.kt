package fr.geotower.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

object ExternalLinksHelper {

    // Ouvre le navigateur
    private fun openUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLogger.w(TAG, "External link could not be opened", e)
        }
    }

    // Lien Officiel ANFR (Pour tout le monde)
    fun openCartoradio(context: Context, siteId: String) {
        openUrl(context, "${Constants.CARTORADIO_BASE_URL}$siteId")
    }

    // Lien Expert : Orange -> Cellulafr
    fun openCellulafr(context: Context, siteId: String) {
        openUrl(context, "${Constants.CELLULAFR_BASE_URL}$siteId")
    }

    // Lien Expert : Free -> RNC Mobile
    fun openRncMobile(context: Context, siteId: String) {
        openUrl(context, "${Constants.RNC_MOBILE_BASE_URL}$siteId")
    }

    private const val TAG = "GeoTower"
}
