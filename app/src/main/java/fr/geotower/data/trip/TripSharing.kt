package fr.geotower.data.trip

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import fr.geotower.data.share.ShareHistoryStore
import java.io.File

/**
 * Envoi d'un ou plusieurs trajets vers une autre application. Le contenu est produit par
 * [TripExport], qui reste pur ; ce qui suit n'est que la plomberie Android.
 */
object TripSharing {
    private const val EXPORT_DIRECTORY = "trip_exports"

    // `stepFallbackLabel` en dernier pour que l'appelant l'écrive en lambda finale ; `nowMillis`
    // avant lui, sinon c'est cette date que la lambda viendrait remplir.
    fun shareGpx(
        context: Context,
        plans: List<TripPlan>,
        nowMillis: Long = System.currentTimeMillis(),
        stepFallbackLabel: (Int) -> String
    ) {
        share(
            context = context,
            plans = plans,
            content = TripExport.buildGpx(plans, nowMillis, stepFallbackLabel),
            extension = TripExport.GPX_EXTENSION,
            mimeType = TripExport.GPX_MIME_TYPE,
            contentCode = ShareHistoryStore.CONTENT_GPX
        )
    }

    fun shareJson(context: Context, plans: List<TripPlan>) {
        share(
            context = context,
            plans = plans,
            content = TripExport.buildJson(plans),
            extension = TripExport.JSON_EXTENSION,
            mimeType = TripExport.JSON_MIME_TYPE,
            contentCode = ShareHistoryStore.CONTENT_JSON
        )
    }

    private fun share(
        context: Context,
        plans: List<TripPlan>,
        content: String,
        extension: String,
        mimeType: String,
        contentCode: String
    ) {
        if (plans.isEmpty()) return

        val fileName = "${TripExport.fileStem(plans)}.$extension"
        val uri = writeExport(context, content, fileName)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // FLAG_ACTIVITY_NEW_TASK : le Context d'un écran Compose localisé n'est pas l'Activity, et
        // OxygenOS refuse alors d'ouvrir le sélecteur (« Erreur d'initialisation »).
        context.startActivity(
            Intent.createChooser(intent, fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

        val single = plans.singleOrNull()
        ShareHistoryStore.record(
            context = context,
            kind = ShareHistoryStore.KIND_TRIP,
            destination = ShareHistoryStore.DEST_SHARE,
            label = single?.name.orEmpty(),
            latitude = single?.steps?.firstOrNull()?.latitude,
            longitude = single?.steps?.firstOrNull()?.longitude,
            itemCount = plans.size,
            contents = contentCode
        )
    }

    private fun writeExport(context: Context, content: String, fileName: String): Uri {
        val directory = File(context.cacheDir, EXPORT_DIRECTORY).apply { mkdirs() }
        val file = File(directory, fileName)
        file.writeText(content, Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
