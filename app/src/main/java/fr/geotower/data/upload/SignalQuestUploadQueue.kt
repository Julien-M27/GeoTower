package fr.geotower.data.upload

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.gson.Gson
import fr.geotower.data.api.SignalQuestOperators
import fr.geotower.data.community.CommunityDataPreferences
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

object SignalQuestUploadRules {
    const val MAX_PHOTOS = 10
    const val MAX_DIMENSION_PX = 2560
    const val JPEG_QUALITY = 86
    const val MIN_JPEG_QUALITY = 70
    const val MAX_SOURCE_BYTES: Long = 20L * 1024L * 1024L
    const val MAX_OUTPUT_BYTES: Long = MAX_SOURCE_BYTES

    private val acceptedMimeTypes = setOf(
        "image/jpeg",
        "image/png",
        "image/heic",
        "image/heif",
        "image/webp"
    )

    fun isAcceptedMimeType(mimeType: String?): Boolean {
        return mimeType?.lowercase(Locale.US) in acceptedMimeTypes
    }

    fun isAcceptedSourceSize(sizeBytes: Long?): Boolean {
        return sizeBytes != null && sizeBytes in 1..MAX_SOURCE_BYTES
    }

    fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int = MAX_DIMENSION_PX): Int {
        if (width <= 0 || height <= 0 || maxDimension <= 0) return 1

        var sampleSize = 1
        val largestSide = max(width, height)
        while (largestSide / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }
}

data class SignalQuestUploadFile(
    val sourceFileName: String,
    val sourceMimeType: String,
    val sourceSizeBytes: Long
)

data class SignalQuestUploadManifest(
    val uploadId: String,
    val siteId: String,
    val operator: String,
    val description: String,
    val createdAtMillis: Long,
    val files: List<SignalQuestUploadFile>
)

object SignalQuestUploadManifestCodec {
    private val gson = Gson()

    fun encode(manifest: SignalQuestUploadManifest): String = gson.toJson(manifest)

    fun decode(json: String): SignalQuestUploadManifest {
        return gson.fromJson(json, SignalQuestUploadManifest::class.java)
    }
}

object SignalQuestUploadDraftStore {
    private val drafts = ConcurrentHashMap<String, List<String>>()

    fun put(uriStrings: List<String>): String {
        val draftId = UUID.randomUUID().toString()
        drafts[draftId] = uriStrings
        return draftId
    }

    fun take(draftId: String): List<String> {
        return drafts.remove(draftId).orEmpty()
    }
}

class SignalQuestUploadQueueException(message: String, cause: Throwable? = null) : Exception(message, cause)
class SignalQuestInvalidPhotoException(message: String, cause: Throwable? = null) : Exception(message, cause)

object SignalQuestUploadQueue {
    const val INPUT_UPLOAD_ID = "uploadId"

    private const val UPLOAD_DIR_NAME = "sq_upload"
    private const val CAMERA_DIR_NAME = "sq_camera"
    private const val MANIFEST_FILE_NAME = "manifest.json"
    private const val STALE_CACHE_MAX_AGE_MS = 48L * 60L * 60L * 1000L

    fun createCameraUri(context: Context): Uri {
        cleanupStaleFiles(context)
        val cameraDir = cameraRoot(context).apply { mkdirs() }
        val tempFile = File.createTempFile("sq_camera_", ".jpg", cameraDir)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
    }

    @Throws(SignalQuestUploadQueueException::class)
    fun createUpload(
        context: Context,
        siteId: String,
        operator: String,
        description: String,
        uriStrings: List<String>
    ): SignalQuestUploadManifest {
        cleanupStaleFiles(context)

        if (siteId.isBlank()) {
            throw SignalQuestUploadQueueException("Site SignalQuest invalide.")
        }
        if (uriStrings.isEmpty()) {
            throw SignalQuestUploadQueueException("Ajoute au moins une photo avant l'envoi.")
        }
        if (uriStrings.size > SignalQuestUploadRules.MAX_PHOTOS) {
            throw SignalQuestUploadQueueException("Maximum ${SignalQuestUploadRules.MAX_PHOTOS} photos par envoi.")
        }
        val signalQuestOperator = SignalQuestOperators.operatorParamFor(operator)
            ?: throw SignalQuestUploadQueueException("Operateur SignalQuest non pris en charge.")
        val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
        if (!CommunityDataPreferences.isSignalQuestPhotosEnabled(prefs, operator)) {
            throw SignalQuestUploadQueueException("Envoi SignalQuest desactive pour cet operateur.")
        }

        val uploadId = UUID.randomUUID().toString()
        val uploadDir = uploadDir(context, uploadId).apply { mkdirs() }
        val files = mutableListOf<SignalQuestUploadFile>()

        try {
            uriStrings.forEachIndexed { index, uriString ->
                val uri = Uri.parse(uriString)
                val mimeType = resolveMimeType(context, uri)
                if (!SignalQuestUploadRules.isAcceptedMimeType(mimeType)) {
                    throw SignalQuestUploadQueueException("Format de photo non pris en charge.")
                }

                val sourceSize = querySourceSize(context, uri)
                if (sourceSize != null && !SignalQuestUploadRules.isAcceptedSourceSize(sourceSize)) {
                    throw SignalQuestUploadQueueException("Une photo depasse la limite de 20 Mo.")
                }

                val extension = extensionForMimeType(mimeType!!)
                val targetFile = File.createTempFile("source_${index}_", ".$extension", uploadDir)
                val copiedBytes = copyUriToInternalFile(context, uri, targetFile)
                if (!SignalQuestUploadRules.isAcceptedSourceSize(copiedBytes)) {
                    targetFile.delete()
                    throw SignalQuestUploadQueueException("Une photo depasse la limite de 20 Mo.")
                }

                files += SignalQuestUploadFile(
                    sourceFileName = targetFile.name,
                    sourceMimeType = mimeType.lowercase(Locale.US),
                    sourceSizeBytes = copiedBytes
                )
            }

            val manifest = SignalQuestUploadManifest(
                uploadId = uploadId,
                siteId = siteId,
                operator = signalQuestOperator,
                description = description,
                createdAtMillis = System.currentTimeMillis(),
                files = files
            )
            manifestFile(uploadDir).writeText(SignalQuestUploadManifestCodec.encode(manifest))
            return manifest
        } catch (e: SignalQuestUploadQueueException) {
            deleteInsideRoot(uploadRoot(context), uploadDir)
            throw e
        } catch (e: Exception) {
            deleteInsideRoot(uploadRoot(context), uploadDir)
            throw SignalQuestUploadQueueException("Impossible de preparer les photos.", e)
        }
    }

    @Throws(SignalQuestUploadQueueException::class)
    fun loadManifest(context: Context, uploadId: String): SignalQuestUploadManifest {
        val uploadDir = uploadDir(context, uploadId)
        val file = manifestFile(uploadDir)
        if (!file.isFile) {
            throw SignalQuestUploadQueueException("Manifeste d'upload introuvable.")
        }
        return try {
            SignalQuestUploadManifestCodec.decode(file.readText())
        } catch (e: Exception) {
            throw SignalQuestUploadQueueException("Manifeste d'upload invalide.", e)
        }
    }

    fun sourceFile(context: Context, uploadId: String, uploadFile: SignalQuestUploadFile): File {
        return File(uploadDir(context, uploadId), uploadFile.sourceFileName)
    }

    fun cleanupUpload(context: Context, uploadId: String) {
        deleteInsideRoot(uploadRoot(context), uploadDir(context, uploadId))
    }

    fun cleanupStaleFiles(context: Context, maxAgeMillis: Long = STALE_CACHE_MAX_AGE_MS) {
        cleanupOldChildren(uploadRoot(context), maxAgeMillis)
        cleanupOldChildren(cameraRoot(context), maxAgeMillis)
    }

    @Throws(SignalQuestInvalidPhotoException::class)
    fun prepareJpegForUpload(context: Context, manifest: SignalQuestUploadManifest, uploadFile: SignalQuestUploadFile): File {
        if (!SignalQuestUploadRules.isAcceptedMimeType(uploadFile.sourceMimeType)) {
            throw SignalQuestInvalidPhotoException("Type MIME refuse.")
        }

        val source = sourceFile(context, manifest.uploadId, uploadFile)
        if (!source.isFile || !SignalQuestUploadRules.isAcceptedSourceSize(source.length())) {
            throw SignalQuestInvalidPhotoException("Fichier source invalide.")
        }

        val uploadDir = uploadDir(context, manifest.uploadId)
        val preparedFile = File(uploadDir, "prepared_${source.nameWithoutExtension}.jpg")
        if (preparedFile.isFile && preparedFile.length() in 1..SignalQuestUploadRules.MAX_OUTPUT_BYTES) {
            return preparedFile
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw SignalQuestInvalidPhotoException("Image illisible.")
        }

        var decoded: Bitmap? = null
        var transformed: Bitmap? = null
        var scaled: Bitmap? = null

        try {
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = SignalQuestUploadRules.calculateInSampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            decoded = BitmapFactory.decodeFile(source.absolutePath, decodeOptions)
                ?: throw SignalQuestInvalidPhotoException("Image illisible.")

            transformed = applyExifOrientation(source, decoded)
            if (transformed !== decoded) {
                decoded.recycle()
                decoded = null
            }

            scaled = scaleToMaxDimension(transformed, SignalQuestUploadRules.MAX_DIMENSION_PX)
            if (scaled !== transformed) {
                transformed.recycle()
                transformed = null
            }

            writeCompressedJpeg(scaled, preparedFile)
            return preparedFile
        } catch (e: SignalQuestInvalidPhotoException) {
            preparedFile.delete()
            throw e
        } catch (e: OutOfMemoryError) {
            preparedFile.delete()
            throw SignalQuestInvalidPhotoException("Image trop grande pour etre preparee.", e)
        } catch (e: Exception) {
            preparedFile.delete()
            throw SignalQuestInvalidPhotoException("Preparation de l'image impossible.", e)
        } finally {
            decoded?.recycle()
            transformed?.recycle()
            scaled?.recycle()
        }
    }

    private fun uploadRoot(context: Context): File = File(context.cacheDir, UPLOAD_DIR_NAME)

    private fun cameraRoot(context: Context): File = File(context.cacheDir, CAMERA_DIR_NAME)

    private fun uploadDir(context: Context, uploadId: String): File = File(uploadRoot(context), uploadId)

    private fun manifestFile(uploadDir: File): File = File(uploadDir, MANIFEST_FILE_NAME)

    private fun resolveMimeType(context: Context, uri: Uri): String? {
        val resolverMime = normalizeMimeType(context.contentResolver.getType(uri))
        if (SignalQuestUploadRules.isAcceptedMimeType(resolverMime)) return resolverMime

        val extension = resolveFileExtension(context, uri)
        val extensionMimeType = mimeTypeForExtension(extension)
        if (extensionMimeType != null && (resolverMime == null || isGenericMimeType(resolverMime))) {
            return extensionMimeType
        }
        return resolverMime ?: extensionMimeType
    }

    private fun normalizeMimeType(mimeType: String?): String? {
        return when (val normalized = mimeType?.lowercase(Locale.US)) {
            "image/jpg" -> "image/jpeg"
            else -> normalized
        }
    }

    private fun isGenericMimeType(mimeType: String): Boolean {
        return mimeType == "application/octet-stream" || mimeType == "image/*" || mimeType == "*/*"
    }

    private fun resolveFileExtension(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst() && !cursor.isNull(nameIndex)) {
                val displayName = cursor.getString(nameIndex)
                val displayExtension = displayName.substringAfterLast('.', missingDelimiterValue = "")
                    .lowercase(Locale.US)
                    .takeIf { it.isNotBlank() }
                if (displayExtension != null) return displayExtension
            }
        }

        return MimeTypeMap.getFileExtensionFromUrl(uri.toString())?.lowercase(Locale.US)
    }

    private fun mimeTypeForExtension(extension: String?): String? {
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "webp" -> "image/webp"
            else -> extension?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
        }
    }

    private fun extensionForMimeType(mimeType: String): String {
        return when (mimeType.lowercase(Locale.US)) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            "image/webp" -> "webp"
            else -> "img"
        }
    }

    private fun querySourceSize(context: Context, uri: Uri): Long? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && cursor.moveToFirst() && !cursor.isNull(sizeIndex)) {
                return cursor.getLong(sizeIndex)
            }
        }

        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            if (descriptor.length > 0L) return descriptor.length
        }

        if (uri.scheme == "file") {
            val path = uri.path
            if (path != null) return File(path).length()
        }

        return null
    }

    @Throws(IOException::class, SignalQuestUploadQueueException::class)
    private fun copyUriToInternalFile(context: Context, uri: Uri, targetFile: File): Long {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw SignalQuestUploadQueueException("Photo inaccessible.")

        var copiedBytes = 0L
        input.use { source ->
            FileOutputStream(targetFile).use { target ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = source.read(buffer)
                    if (read == -1) break
                    copiedBytes += read
                    if (copiedBytes > SignalQuestUploadRules.MAX_SOURCE_BYTES) {
                        throw SignalQuestUploadQueueException("Une photo depasse la limite de 20 Mo.")
                    }
                    target.write(buffer, 0, read)
                }
            }
        }
        return copiedBytes
    }

    private fun applyExifOrientation(source: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(source).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
        }

        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleToMaxDimension(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largestSide = max(bitmap.width, bitmap.height)
        if (largestSide <= maxDimension) return bitmap

        val ratio = maxDimension.toFloat() / largestSide.toFloat()
        val targetWidth = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    @Throws(SignalQuestInvalidPhotoException::class)
    private fun writeCompressedJpeg(bitmap: Bitmap, targetFile: File) {
        var quality = SignalQuestUploadRules.JPEG_QUALITY
        while (quality >= SignalQuestUploadRules.MIN_JPEG_QUALITY) {
            FileOutputStream(targetFile).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                    throw SignalQuestInvalidPhotoException("Compression JPEG impossible.")
                }
            }

            if (targetFile.length() in 1..SignalQuestUploadRules.MAX_OUTPUT_BYTES) {
                return
            }
            quality -= 6
        }
        targetFile.delete()
        throw SignalQuestInvalidPhotoException("Photo compressee trop volumineuse.")
    }

    private fun cleanupOldChildren(root: File, maxAgeMillis: Long) {
        if (!root.exists()) return
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        root.listFiles().orEmpty().forEach { child ->
            if (child.lastModified() < cutoff) {
                deleteInsideRoot(root, child)
            }
        }
    }

    private fun deleteInsideRoot(root: File, target: File) {
        runCatching {
            val canonicalRoot = root.canonicalFile
            val canonicalTarget = target.canonicalFile
            val rootPath = canonicalRoot.path
            val targetPath = canonicalTarget.path
            if (targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)) {
                canonicalTarget.deleteRecursively()
            }
        }
    }
}
