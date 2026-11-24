package fr.geotower.data.speedtest

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

data class SpeedtestImageMetadata(
    val testedAtMillis: Long,
    val latitude: Double?,
    val longitude: Double?,
    val parsed: ParsedSpeedtestData,
    val rawOcrText: String?
)

object SpeedtestImageAnalyzer {

    private const val DIR_NAME = "speedtests"

    fun speedtestsDir(context: Context): File {
        return File(context.applicationContext.filesDir, DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    fun imageFile(context: Context, imageFileName: String): File {
        return File(speedtestsDir(context), imageFileName)
    }

    suspend fun copyImageToInternalStorage(context: Context, sourceUri: Uri): String? =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val resolver = appContext.contentResolver
            val ext = resolveExtension(appContext, sourceUri)
            val fileName = "st_${UUID.randomUUID()}.$ext"
            val targetFile = imageFile(appContext, fileName)

            val effectiveUri = resolveOriginalMediaUri(appContext, sourceUri)
            val copied = runCatching {
                resolver.openInputStream(effectiveUri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                    true
                } ?: false
            }.getOrElse {
                if (effectiveUri != sourceUri) {
                    runCatching {
                        resolver.openInputStream(sourceUri)?.use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                            true
                        } ?: false
                    }.getOrDefault(false)
                } else {
                    false
                }
            }

            if (!copied || !targetFile.exists() || targetFile.length() == 0L) {
                runCatching { targetFile.delete() }
                null
            } else {
                fileName
            }
        }

    suspend fun analyzeStoredImage(context: Context, imageFile: File): SpeedtestImageMetadata =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            var testedAtMillis: Long? = null
            var latitude: Double? = null
            var longitude: Double? = null

            var rotationDegrees = 0
            runCatching {
                val exif = ExifInterface(imageFile.absolutePath)
                rotationDegrees = exif.rotationDegrees
                val latLong = exif.latLong
                if (latLong != null && latLong.size == 2 && latLong[0].isFinite() && latLong[1].isFinite()) {
                    latitude = latLong[0]
                    longitude = latLong[1]
                }
                val dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                if (!dateStr.isNullOrBlank()) {
                    testedAtMillis = parseExifDate(dateStr)
                }
            }

            if (testedAtMillis == null) {
                val mod = imageFile.lastModified()
                testedAtMillis = if (mod > 0L) mod else now
            }

            val rawOcrText = runCatching {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                    val inputImage = if (bitmap != null) {
                        InputImage.fromBitmap(bitmap, rotationDegrees)
                    } else {
                        InputImage.fromFilePath(context.applicationContext, imageFile.toUri())
                    }
                    recognizer.process(inputImage).await().text
                } finally {
                    recognizer.close()
                }
            }.onFailure { error ->
                Log.e("SpeedtestOCR", "ML Kit OCR failed for ${imageFile.name}", error)
            }.getOrNull()?.takeIf { it.isNotBlank() }

            val parsed = rawOcrText?.let { SpeedtestOcrParser.parse(it) } ?: ParsedSpeedtestData()

            SpeedtestImageMetadata(
                testedAtMillis = testedAtMillis ?: now,
                latitude = latitude,
                longitude = longitude,
                parsed = parsed,
                rawOcrText = rawOcrText
            )
        }

    private fun resolveOriginalMediaUri(context: Context, uri: Uri): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return uri
        return runCatching {
            MediaStore.setRequireOriginal(uri)
        }.getOrDefault(uri)
    }

    private fun parseExifDate(raw: String): Long? {
        val formats = listOf(
            "yyyy:MM:dd HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in formats) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(raw.trim())?.time
            }.getOrNull()
            if (parsed != null && parsed > 0L) return parsed
        }
        return null
    }

    private fun resolveExtension(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)
        val fromMime = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        if (!fromMime.isNullOrBlank()) return fromMime.lowercase(Locale.ROOT)

        val displayName = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        val fromName = displayName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
        if (!fromName.isNullOrBlank() && fromName.length <= 5) return fromName

        return "jpg"
    }
}
