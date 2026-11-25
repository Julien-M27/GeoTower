package fr.geotower.data.speedtest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
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

            var rawOcrText: String? = null
            var parsed = ParsedSpeedtestData()

            runCatching {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath, options)
                    val inputImage = if (bitmap != null) {
                        InputImage.fromBitmap(bitmap, rotationDegrees)
                    } else {
                        InputImage.fromFilePath(context.applicationContext, imageFile.toUri())
                    }
                    val firstResult = recognizer.process(inputImage).await()
                    val firstText = firstResult.text
                    val firstBoxes = extractBoxedLines(firstResult, inputImage, rotationDegrees)
                    rawOcrText = firstText.takeIf { it.isNotBlank() }
                    if (firstText.isNotBlank()) {
                        parsed = SpeedtestOcrParser.parse(firstText, firstBoxes)
                    }

                    // Seconde passe à échelle réduite (0.5x) si des débits manquent :
                    // ML Kit rate parfois les très gros chiffres d'affichage (>150px de haut)
                    // comme sur SignalQuest ou Ookla.
                    if (bitmap != null &&
                        (parsed.downloadMbps == null || parsed.uploadMbps == null) &&
                        bitmap.width >= 600 && bitmap.height >= 600
                    ) {
                        val scaled = Bitmap.createScaledBitmap(
                            bitmap,
                            bitmap.width / 2,
                            bitmap.height / 2,
                            true
                        )
                        try {
                            val scaledInput = InputImage.fromBitmap(scaled, rotationDegrees)
                            val secondResult = recognizer.process(scaledInput).await()
                            val secondText = secondResult.text
                            if (secondText.isNotBlank()) {
                                val secondBoxes = extractBoxedLines(secondResult, scaledInput, rotationDegrees)
                                parsed = mergeParsed(parsed, SpeedtestOcrParser.parse(secondText, secondBoxes))
                                if (rawOcrText == null) {
                                    rawOcrText = secondText
                                }
                            }
                        } finally {
                            if (scaled !== bitmap) scaled.recycle()
                        }
                    }

                    // Troisième passe en niveaux de gris + contraste accentué si des métriques
                    // principales (DL/UL/Ping/Gigue) manquent encore (petits textes gris peu contrastés).
                    if (bitmap != null &&
                        (parsed.downloadMbps == null || parsed.uploadMbps == null ||
                            parsed.pingMs == null || parsed.jitterMs == null)
                    ) {
                        val contrastBitmap = createHighContrastGrayscaleBitmap(bitmap)
                        try {
                            val contrastInput = InputImage.fromBitmap(contrastBitmap, rotationDegrees)
                            val contrastResult = recognizer.process(contrastInput).await()
                            val contrastText = contrastResult.text
                            if (contrastText.isNotBlank()) {
                                val contrastBoxes = extractBoxedLines(contrastResult, contrastInput, rotationDegrees)
                                parsed = mergeParsed(parsed, SpeedtestOcrParser.parse(contrastText, contrastBoxes))
                                if (rawOcrText == null) {
                                    rawOcrText = contrastText
                                }
                            }
                        } finally {
                            contrastBitmap.recycle()
                        }
                    }
                } finally {
                    recognizer.close()
                }
            }.onFailure { error ->
                Log.e("SpeedtestOCR", "ML Kit OCR failed for ${imageFile.name}", error)
            }

            SpeedtestImageMetadata(
                testedAtMillis = testedAtMillis,
                latitude = latitude,
                longitude = longitude,
                parsed = parsed,
                rawOcrText = rawOcrText
            )
        }

    private fun mergeParsed(primary: ParsedSpeedtestData, secondary: ParsedSpeedtestData): ParsedSpeedtestData {
        return ParsedSpeedtestData(
            downloadMbps = primary.downloadMbps ?: secondary.downloadMbps,
            uploadMbps = primary.uploadMbps ?: secondary.uploadMbps,
            pingMs = primary.pingMs ?: secondary.pingMs,
            jitterMs = primary.jitterMs ?: secondary.jitterMs,
            operatorName = primary.operatorName ?: secondary.operatorName,
            networkName = primary.networkName ?: secondary.networkName,
            networkTech = primary.networkTech ?: secondary.networkTech,
            band = primary.band ?: secondary.band,
            cellIdentifier = primary.cellIdentifier ?: secondary.cellIdentifier
        )
    }

    private fun createHighContrastGrayscaleBitmap(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val grayMatrix = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.75f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        grayMatrix.postConcat(contrastMatrix)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(grayMatrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    private fun extractBoxedLines(
        visionText: com.google.mlkit.vision.text.Text,
        inputImage: InputImage,
        rotationDegrees: Int
    ): List<OcrBoxLine> {
        val rawW = inputImage.width.toFloat().coerceAtLeast(1f)
        val rawH = inputImage.height.toFloat().coerceAtLeast(1f)
        val w = if (rotationDegrees == 90 || rotationDegrees == 270) rawH else rawW
        val h = if (rotationDegrees == 90 || rotationDegrees == 270) rawW else rawH

        return visionText.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                val text = line.text.trim()
                if (text.isEmpty()) return@mapNotNull null
                OcrBoxLine(
                    text = text,
                    left = (box.left / w).coerceIn(0f, 1f),
                    top = (box.top / h).coerceIn(0f, 1f),
                    right = (box.right / w).coerceIn(0f, 1f),
                    bottom = (box.bottom / h).coerceIn(0f, 1f)
                )
            }
        }
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
