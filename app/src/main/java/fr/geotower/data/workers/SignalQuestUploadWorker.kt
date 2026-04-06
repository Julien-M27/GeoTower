package fr.geotower.data.workers

import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.*
import fr.geotower.R
import fr.geotower.data.api.SignalQuestClient
import fr.geotower.BuildConfig
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import androidx.work.workDataOf

class SignalQuestUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val siteId = inputData.getString("siteId") ?: return Result.failure()
        val operator = inputData.getString("operator") ?: ""
        val description = inputData.getString("description") ?: ""
        val urisStr = inputData.getString("uris") ?: ""
        val uris = urisStr.split(",").filter { it.isNotEmpty() }

        val total = uris.size
        var successCount = 0

        // --- 1. AJOUT : On informe l'interface que l'on commence à 0 ---
        setProgress(workDataOf("current" to 0, "total" to total))

        // On prépare la notification de départ avec traduction
        val startMsg = getLocalizedString(
            "Envoi en cours (0/$total)...",
            "Uploading (0/$total)...",
            "Enviando (0/$total)..."
        )
        showNotification("Signal Quest", startMsg, true)

        uris.forEachIndexed { index, uriStr ->
            try {
                val uri = Uri.parse(uriStr)
                val file = uriToFile(uri)

                if (file != null) {
                    val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                    val descBody = description.toRequestBody("text/plain".toMediaTypeOrNull())
                    val opBody = operator.toRequestBody("text/plain".toMediaTypeOrNull())

                    val response = SignalQuestClient.api.uploadSitePhoto(
                        authHeader = "Bearer ${BuildConfig.SQ_API_KEY}",
                        siteId = siteId,
                        file = body,
                        description = descBody,
                        operator = opBody
                    )

                    if (response.isSuccessful) {
                        successCount++
                    } else {
                        val errorCode = response.code()
                        val errorBody = response.errorBody()?.string()
                        android.util.Log.e("UploadSQ", "Erreur API $errorCode : $errorBody")
                    }
                    file.delete() // On nettoie le fichier temporaire
                }

                // --- 2. AJOUT : On met à jour la progression pour le Pop-Up de l'interface ! ---
                setProgress(workDataOf("current" to index + 1, "total" to total))

                // Mise à jour de la notification avec traduction
                val progressMsg = getLocalizedString(
                    "Envoi en cours (${index + 1}/$total)...",
                    "Uploading (${index + 1}/$total)...",
                    "Enviando (${index + 1}/$total)..."
                )
                showNotification("Signal Quest", progressMsg, true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Notification finale avec traduction
        if (successCount == total) {
            val successMsg = getLocalizedString(
                "$successCount/$total photos envoyées avec succès !",
                "$successCount/$total photos sent successfully!",
                "$successCount/$total fotos enviadas com sucesso!"
            )
            showNotification("Signal Quest", successMsg, false)
        } else {
            val failMsg = getLocalizedString(
                "$successCount/$total photos envoyées (échecs possibles)",
                "$successCount/$total photos sent (possible failures)",
                "$successCount/$total fotos enviadas (possíveis falhas)"
            )
            showNotification("Signal Quest", failMsg, false)
        }

        return Result.success()
    }

    // --- FONCTION DE TRADUCTION SANS COMPOSABLE ---
    private fun getLocalizedString(fr: String, en: String, pt: String): String {
        val prefs = applicationContext.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
        val currentLang = prefs.getString("app_language", "Système") ?: "Système"

        val langToCheck = if (currentLang == "Système") {
            java.util.Locale.getDefault().language
        } else {
            currentLang
        }

        return when {
            langToCheck == "Français" || langToCheck == "fr" -> fr
            langToCheck == "Português" || langToCheck == "pt" -> pt
            else -> en
        }
    }
    private fun showNotification(title: String, message: String, isProgress: Boolean) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "sq_upload_channel"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "Upload Signal Quest", android.app.NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }


        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.logo_signalquest)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isProgress)

        if (isProgress) builder.setProgress(0, 0, true)

        notificationManager.notify(99, builder.build())
    }

    // Fonction utilitaire pour convertir l'URI de la galerie en fichier réel pour l'upload
    // Fonction utilitaire pour convertir l'URI en VRAI fichier JPEG (Convertit les HEIC/PNG automatiquement)
    // Fonction utilitaire pour convertir l'URI en VRAI fichier JPEG ET CORRIGER L'ORIENTATION
    private fun uriToFile(uri: Uri): File? {
        return try {
            // --- 1. LIRE L'ORIENTATION CACHÉE (EXIF) ---
            var rotationDegrees = 0
            val inputStreamForExif = applicationContext.contentResolver.openInputStream(uri)
            if (inputStreamForExif != null) {
                // Nous utilisons androidx.exifinterface.media.ExifInterface pour lire les métadonnées
                val exif = androidx.exifinterface.media.ExifInterface(inputStreamForExif)
                val orientation = exif.getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                )
                inputStreamForExif.close()

                rotationDegrees = when (orientation) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }

            // --- 2. OUVRIR L'IMAGE (Bitmap) ---
            val inputStreamForBitmap = applicationContext.contentResolver.openInputStream(uri)
            var bitmap = android.graphics.BitmapFactory.decodeStream(inputStreamForBitmap)
            inputStreamForBitmap?.close()

            if (bitmap == null) return null

            // --- 3. APPLIQUER LA CORRECTION DE ROTATION ---
            if (rotationDegrees != 0) {
                val matrix = android.graphics.Matrix()
                matrix.postRotate(rotationDegrees.toFloat())

                // On crée un NOUVEAU bitmap tourné
                val rotatedBitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

                // On nettoie l'ancien pour libérer la mémoire vive
                bitmap.recycle()
                bitmap = rotatedBitmap // On utilise le nouveau bitmap tourné
            }

            // --- 4. PRÉPARER LE FICHIER TEMPORAIRE JPEG (Qualité 90%) ---
            val tempFile = File(applicationContext.cacheDir, "temp_upload_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(tempFile)

            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)

            outputStream.flush()
            outputStream.close()

            // On nettoie la mémoire vive finale
            bitmap.recycle()

            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}