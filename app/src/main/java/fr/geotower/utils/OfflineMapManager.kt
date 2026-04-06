package fr.geotower.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

class OfflineMapManager(private val context: Context) {

    private val mapFileName = "france.map"
    // URL exemple (à remplacer par ton lien direct vers le fichier .map)
    private val mapUrl = "https://download.mapsforge.org/maps/v5/europe/france.map"

    fun isMapDownloaded(): Boolean {
        val file = File(getMapDirectory(), mapFileName)
        return file.exists() && file.length() > 0
    }

    fun getMapFile(): File {
        return File(getMapDirectory(), mapFileName)
    }

    fun downloadMap() {
        val request = DownloadManager.Request(Uri.parse(mapUrl))
            .setTitle("Téléchargement Carte France")
            .setDescription("Récupération des données cartographiques...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, mapFileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
    }

    private fun getMapDirectory(): File? {
        // On stocke ça dans le dossier accessible par OSMDroid
        return context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    }
}