package fr.geotower.data.upload

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.gson.Gson
import fr.geotower.R
import fr.geotower.data.api.SignalQuestOperators
import fr.geotower.data.community.CommunityDataPreferences
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.reflect.Modifier
import java.text.SimpleDateFormat
import java.util.Date
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
    val sourceSizeBytes: Long,
    val historyEntryId: String? = null,
    val status: String? = SignalQuestUploadFileStatus.PENDING,
    val remotePhotoId: String? = null,
    val remoteImageUrl: String? = null,
    val remoteUploadedAt: String? = null
)

object SignalQuestUploadFileStatus {
    const val PENDING = "pending"
    const val UPLOADED = "uploaded"
    const val AWAITING_VALIDATION = "awaiting_validation"
    const val FAILED_PERMANENT = "failed_permanent"
    const val RETRY = "retry"

    fun normalized(status: String?): String {
        return when (status) {
            UPLOADED -> UPLOADED
            AWAITING_VALIDATION -> AWAITING_VALIDATION
            FAILED_PERMANENT -> FAILED_PERMANENT
            RETRY -> RETRY
            else -> PENDING
        }
    }

    fun shouldUpload(status: String?): Boolean {
        return normalized(status) == PENDING || normalized(status) == RETRY
    }

    fun countsAsUploaded(status: String?): Boolean {
        return normalized(status) == UPLOADED || normalized(status) == AWAITING_VALIDATION
    }

    fun countsAsFinished(status: String?): Boolean {
        return countsAsUploaded(status) || normalized(status) == FAILED_PERMANENT
    }
}

data class SignalQuestUploadManifest(
    val uploadId: String,
    val siteId: String,
    val operator: String,
    val description: String,
    val createdAtMillis: Long,
    val files: List<SignalQuestUploadFile>,
    val stripExifBeforeUpload: Boolean = true,
    val anfrCode: String? = null,
    val nationalSiteCode: String? = null,
    val sourceCode: String? = null
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

    fun peek(draftId: String): List<String> {
        return drafts[draftId].orEmpty()
    }

    fun discard(draftId: String) {
        drafts.remove(draftId)
    }
}

class SignalQuestUploadQueueException(message: String, cause: Throwable? = null) : Exception(message, cause)
class SignalQuestInvalidPhotoException(message: String, cause: Throwable? = null) : Exception(message, cause)

object SignalQuestUploadQueue {
    const val INPUT_UPLOAD_ID = "uploadId"

    private val gson = Gson()
    private const val UPLOAD_DIR_NAME = "sq_upload"
    private const val CAMERA_DIR_NAME = "sq_camera"
    private const val PUBLIC_CAMERA_DIR_NAME = "Camera"
    private const val PUBLIC_FALLBACK_PHOTOS_DIR_NAME = "GeoTower Photos"
    private const val MANIFEST_FILE_NAME = "manifest.json"
    private const val STALE_CACHE_MAX_AGE_MS = 48L * 60L * 60L * 1000L
    private val exifAttributeTags: List<String> by lazy { discoverExifAttributeTags() }
    private val transferableExifAttributeTags: Set<String> by lazy {
        exifAttributeTags.filterNot { tag -> tag in nonTransferableExifTags }.toSet()
    }
    private val nonTransferableExifTags = setOf(
        ExifInterface.TAG_STRIP_OFFSETS,
        ExifInterface.TAG_STRIP_BYTE_COUNTS,
        ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT,
        ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH,
        ExifInterface.TAG_ORF_THUMBNAIL_IMAGE,
        ExifInterface.TAG_ORF_PREVIEW_IMAGE_START,
        ExifInterface.TAG_ORF_PREVIEW_IMAGE_LENGTH,
        ExifInterface.TAG_RW2_JPG_FROM_RAW
    )

    fun createCameraUri(context: Context): Uri {
        cleanupStaleFiles(context)
        return createCacheCameraUri(context)
    }

    fun completeCameraCapture(context: Context, uri: Uri, success: Boolean) {
        if (success) {
            if (isMediaStoreUri(uri)) {
                publishPendingMediaStoreImage(context, uri)
            } else {
                publishCachedCameraImage(context, uri)
            }
        } else {
            deleteCameraCapture(context, uri)
        }
    }

    @Throws(SignalQuestUploadQueueException::class)
    fun createUpload(
        context: Context,
        siteId: String,
        operator: String,
        description: String,
        uriStrings: List<String>,
        stripExifBeforeUpload: Boolean = true
    ): SignalQuestUploadManifest {
        cleanupStaleFiles(context)

        val normalizedSiteId = siteId.trim()
        if (normalizedSiteId.isBlank()) {
            throw SignalQuestUploadQueueException(context.getString(R.string.signalquest_invalid_site))
        }
        if (uriStrings.isEmpty()) {
            throw SignalQuestUploadQueueException(context.getString(R.string.signalquest_photo_required))
        }
        if (uriStrings.size > SignalQuestUploadRules.MAX_PHOTOS) {
            throw SignalQuestUploadQueueException(
                context.getString(R.string.signalquest_max_photos, SignalQuestUploadRules.MAX_PHOTOS)
            )
        }
        val signalQuestOperator = SignalQuestOperators.operatorParamFor(operator)
            ?: throw SignalQuestUploadQueueException(context.getString(R.string.signalquest_unsupported_operator))
        val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
        if (!CommunityDataPreferences.isSignalQuestPhotosEnabled(prefs, operator)) {
            throw SignalQuestUploadQueueException(context.getString(R.string.signalquest_upload_disabled_for_operator))
        }

        val uploadId = UUID.randomUUID().toString()
        val createdAtMillis = System.currentTimeMillis()
        val uploadDir = uploadDir(context, uploadId).apply { mkdirs() }
        val files = mutableListOf<SignalQuestUploadFile>()

        try {
            uriStrings.forEachIndexed { index, uriString ->
                val uri = Uri.parse(uriString)
                val mimeType = resolveMimeType(context, uri)
                if (!SignalQuestUploadRules.isAcceptedMimeType(mimeType)) {
                    throw SignalQuestUploadQueueException(context.getString(R.string.signalquest_unsupported_photo_format))
                }

                val sourceSize = querySourceSize(context, uri)
                if (sourceSize != null && sourceSize > SignalQuestUploadRules.MAX_SOURCE_BYTES) {
                    throw SignalQuestUploadQueueException(context.getString(R.string.signalquest_photo_too_large))
                }

                val extension = extensionForMimeType(mimeType!!)
                val targetFile = File.createTempFile("source_${index}_", ".$extension", uploadDir)
                val copiedBytes = copyUriToInternalFile(context, uri, targetFile)
                if (!SignalQuestUploadRules.isAcceptedSourceSize(copiedBytes)) {
                    targetFile.delete()
                    throw SignalQuestUploadQueueException(context.getString(R.string.signalquest_photo_too_large))
                }

                val historyEntryId = ExternalPhotoUploadHistoryStore.addPendingPhoto(
                    context = context,
                    uploadId = uploadId,
                    sourceName = ExternalPhotoUploadHistoryStore.SOURCE_SIGNALQUEST,
                    supportId = normalizedSiteId,
                    operator = signalQuestOperator,
                    createdAtMillis = createdAtMillis,
                    sourceFile = targetFile,
                    stripExifBeforeUpload = stripExifBeforeUpload
                )

                files += SignalQuestUploadFile(
                    sourceFileName = targetFile.name,
                    sourceMimeType = mimeType.lowercase(Locale.US),
                    sourceSizeBytes = copiedBytes,
                    historyEntryId = historyEntryId
                )
            }

            val manifest = SignalQuestUploadManifest(
                uploadId = uploadId,
                siteId = normalizedSiteId,
                operator = signalQuestOperator,
                description = description,
                createdAtMillis = createdAtMillis,
                files = files,
                stripExifBeforeUpload = stripExifBeforeUpload
            )
            manifestFile(uploadDir).writeText(SignalQuestUploadManifestCodec.encode(manifest))
            return manifest
        } catch (e: SignalQuestUploadQueueException) {
            ExternalPhotoUploadHistoryStore.removeUpload(context, uploadId)
            deleteInsideRoot(uploadRoot(context), uploadDir)
            throw e
        } catch (e: Exception) {
            ExternalPhotoUploadHistoryStore.removeUpload(context, uploadId)
            deleteInsideRoot(uploadRoot(context), uploadDir)
            throw SignalQuestUploadQueueException(context.getString(R.string.signalquest_prepare_photos_failed), e)
        }
    }

    @Throws(SignalQuestUploadQueueException::class)
    fun loadManifest(context: Context, uploadId: String): SignalQuestUploadManifest {
        val uploadDir = uploadDir(context, uploadId)
        val file = manifestFile(uploadDir)
        if (!file.isFile) {
            throw SignalQuestUploadQueueException(context.getString(R.string.signalquest_manifest_missing))
        }
        return try {
            SignalQuestUploadManifestCodec.decode(file.readText())
        } catch (e: Exception) {
            throw SignalQuestUploadQueueException(context.getString(R.string.signalquest_manifest_invalid), e)
        }
    }

    @Synchronized
    fun saveManifest(context: Context, manifest: SignalQuestUploadManifest) {
        val uploadDir = uploadDir(context, manifest.uploadId).apply { mkdirs() }
        manifestFile(uploadDir).writeText(SignalQuestUploadManifestCodec.encode(manifest))
    }

    fun filesInUploadOrder(files: List<SignalQuestUploadFile>): List<SignalQuestUploadFile> {
        return files.asReversed()
    }

    @Synchronized
    fun updateFileResult(
        context: Context,
        manifest: SignalQuestUploadManifest,
        uploadFile: SignalQuestUploadFile,
        status: String,
        remotePhotoId: String? = null,
        remoteImageUrl: String? = null,
        remoteUploadedAt: String? = null
    ): SignalQuestUploadManifest {
        val nextFiles = manifest.files.map { file ->
            if (file.sourceFileName == uploadFile.sourceFileName) {
                file.copy(
                    status = SignalQuestUploadFileStatus.normalized(status),
                    remotePhotoId = remotePhotoId ?: file.remotePhotoId,
                    remoteImageUrl = remoteImageUrl ?: file.remoteImageUrl,
                    remoteUploadedAt = remoteUploadedAt ?: file.remoteUploadedAt
                )
            } else {
                file
            }
        }
        val nextManifest = manifest.copy(files = nextFiles)
        saveManifest(context, nextManifest)
        return nextManifest
    }

    fun sourceFile(context: Context, uploadId: String, uploadFile: SignalQuestUploadFile): File {
        return File(uploadDir(context, uploadId), uploadFile.sourceFileName)
    }

    fun cleanupUpload(context: Context, uploadId: String) {
        deleteInsideRoot(uploadRoot(context), uploadDir(context, uploadId))
    }

    fun cleanupStaleFiles(context: Context, maxAgeMillis: Long = STALE_CACHE_MAX_AGE_MS) {
        cleanupOldUploadChildren(uploadRoot(context), maxAgeMillis)
        cleanupOldChildren(cameraRoot(context), maxAgeMillis)
    }

    fun exifMetadataJsonForUpload(
        context: Context,
        manifest: SignalQuestUploadManifest,
        uploadFile: SignalQuestUploadFile
    ): String? {
        if (manifest.stripExifBeforeUpload) return null

        val source = sourceFile(context, manifest.uploadId, uploadFile)
        if (!source.isFile) return null

        return runCatching {
            readExifAttributes(source)
                .takeIf { it.isNotEmpty() }
                ?.let { attributes -> gson.toJson(attributes) }
        }.getOrNull()
    }

    @Throws(SignalQuestInvalidPhotoException::class)
    fun prepareJpegForUpload(context: Context, manifest: SignalQuestUploadManifest, uploadFile: SignalQuestUploadFile): File {
        if (!SignalQuestUploadRules.isAcceptedMimeType(uploadFile.sourceMimeType)) {
            throw SignalQuestInvalidPhotoException(context.getString(R.string.signalquest_mime_refused))
        }

        val source = sourceFile(context, manifest.uploadId, uploadFile)
        if (!source.isFile || !SignalQuestUploadRules.isAcceptedSourceSize(source.length())) {
            throw SignalQuestInvalidPhotoException(context.getString(R.string.signalquest_invalid_source_file))
        }

        val uploadDir = uploadDir(context, manifest.uploadId)
        val preparedFile = File(uploadDir, "prepared_${source.nameWithoutExtension}.jpg")
        if (preparedFile.isFile) preparedFile.delete()

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw SignalQuestInvalidPhotoException(context.getString(R.string.signalquest_unreadable_image))
        }

        if (!manifest.stripExifBeforeUpload && canUploadOriginalJpeg(uploadFile, source, bounds)) {
            return source
        }

        var decoded: Bitmap? = null
        var oriented: Bitmap? = null
        var scaled: Bitmap? = null

        try {
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = SignalQuestUploadRules.calculateInSampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            decoded = BitmapFactory.decodeFile(source.absolutePath, decodeOptions)
                ?: throw SignalQuestInvalidPhotoException(context.getString(R.string.signalquest_unreadable_image))

            val bitmapForScale = if (manifest.stripExifBeforeUpload) {
                val orientedBitmap = applyExifOrientation(source, decoded)
                if (orientedBitmap !== decoded) {
                    decoded.recycle()
                    decoded = null
                    oriented = orientedBitmap
                }
                orientedBitmap
            } else {
                decoded
            }

            scaled = scaleToMaxDimension(bitmapForScale, SignalQuestUploadRules.MAX_DIMENSION_PX)
            if (scaled !== bitmapForScale) {
                when (bitmapForScale) {
                    decoded -> {
                        decoded.recycle()
                        decoded = null
                    }
                    oriented -> {
                        oriented.recycle()
                        oriented = null
                    }
                }
            }

            writeCompressedJpeg(context, scaled, preparedFile) {
                if (!manifest.stripExifBeforeUpload) {
                    copyExifAttributes(source, preparedFile)
                }
            }
            return preparedFile
        } catch (e: SignalQuestInvalidPhotoException) {
            preparedFile.delete()
            throw e
        } catch (e: OutOfMemoryError) {
            preparedFile.delete()
            throw SignalQuestInvalidPhotoException(context.getString(R.string.signalquest_image_too_large_prepare), e)
        } catch (e: Exception) {
            preparedFile.delete()
            throw SignalQuestInvalidPhotoException(context.getString(R.string.signalquest_image_prepare_failed), e)
        } finally {
            if (scaled !== decoded && scaled !== oriented) {
                scaled?.recycle()
            }
            decoded?.recycle()
            oriented?.recycle()
        }
    }

    private fun uploadRoot(context: Context): File = File(context.cacheDir, UPLOAD_DIR_NAME)

    private fun cameraRoot(context: Context): File = File(context.cacheDir, CAMERA_DIR_NAME)

    private fun uploadDir(context: Context, uploadId: String): File = File(uploadRoot(context), uploadId)

    private fun manifestFile(uploadDir: File): File = File(uploadDir, MANIFEST_FILE_NAME)

    private fun createCacheCameraUri(context: Context): Uri {
        val cameraDir = cameraRoot(context).apply { mkdirs() }
        val tempFile = File.createTempFile("sq_camera_", ".jpg", cameraDir)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
    }

    private fun createPublicCameraUri(context: Context): Uri? {
        val displayName = createCameraDisplayName()
        val publicDirName = publicPhotoDirectoryName()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, publicPhotoRelativePath(publicDirName))
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val photoDir = publicPhotoDirectory(publicDirName)
                if (publicDirName != PUBLIC_CAMERA_DIR_NAME) {
                    photoDir.mkdirs()
                }
                put(MediaStore.Images.Media.DATA, File(photoDir, displayName).absolutePath)
            }
        }

        return runCatching {
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull()
    }

    private fun publishPendingMediaStoreImage(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }
        runCatching {
            context.contentResolver.update(uri, values, null, null)
        }
    }

    private fun publishCachedCameraImage(context: Context, sourceUri: Uri) {
        val targetUri = createPublicCameraUri(context) ?: return
        runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(targetUri, "w")?.use { output ->
                    input.copyTo(output)
                } ?: throw IOException("Destination camera inaccessible.")
            } ?: throw IOException("Photo camera inaccessible.")

            publishPendingMediaStoreImage(context, targetUri)
        }.onFailure {
            deleteCameraCapture(context, targetUri)
        }
    }

    private fun deleteCameraCapture(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.delete(uri, null, null)
        }
    }

    private fun isMediaStoreUri(uri: Uri): Boolean {
        return uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY
    }

    private fun publicPhotoDirectoryName(): String {
        return if (publicPhotoDirectory(PUBLIC_CAMERA_DIR_NAME).isDirectory) {
            PUBLIC_CAMERA_DIR_NAME
        } else {
            PUBLIC_FALLBACK_PHOTOS_DIR_NAME
        }
    }

    private fun publicPhotoDirectory(dirName: String): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            dirName
        )
    }

    private fun publicPhotoRelativePath(dirName: String): String {
        return "${Environment.DIRECTORY_DCIM}${File.separator}$dirName"
    }

    private fun createCameraDisplayName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        return "GeoTower_$timestamp.jpg"
    }

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
            ?: throw SignalQuestUploadQueueException(context.getString(R.string.signalquest_photo_inaccessible))

        var copiedBytes = 0L
        input.use { source ->
            FileOutputStream(targetFile).use { target ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = source.read(buffer)
                    if (read == -1) break
                    copiedBytes += read
                    if (copiedBytes > SignalQuestUploadRules.MAX_SOURCE_BYTES) {
                        throw SignalQuestUploadQueueException(context.getString(R.string.signalquest_photo_too_large))
                    }
                    target.write(buffer, 0, read)
                }
            }
        }
        return copiedBytes
    }

    private fun canUploadOriginalJpeg(
        uploadFile: SignalQuestUploadFile,
        source: File,
        bounds: BitmapFactory.Options
    ): Boolean {
        return uploadFile.sourceMimeType.equals("image/jpeg", ignoreCase = true) &&
            max(bounds.outWidth, bounds.outHeight) <= SignalQuestUploadRules.MAX_DIMENSION_PX &&
            source.length() in 1..SignalQuestUploadRules.MAX_OUTPUT_BYTES
    }

    private fun applyExifOrientation(source: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(source.absolutePath).getAttributeInt(
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
    private fun writeCompressedJpeg(context: Context, bitmap: Bitmap, targetFile: File, afterWrite: () -> Unit) {
        var quality = SignalQuestUploadRules.JPEG_QUALITY
        while (quality >= SignalQuestUploadRules.MIN_JPEG_QUALITY) {
            FileOutputStream(targetFile).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                    throw SignalQuestInvalidPhotoException(context.getString(R.string.signalquest_jpeg_compression_failed))
                }
            }

            afterWrite()
            if (targetFile.length() in 1..SignalQuestUploadRules.MAX_OUTPUT_BYTES) {
                return
            }
            quality -= 6
        }
        targetFile.delete()
        throw SignalQuestInvalidPhotoException(context.getString(R.string.signalquest_compressed_photo_too_large))
    }

    @Throws(IOException::class)
    private fun copyExifAttributes(source: File, target: File) {
        val targetExif = ExifInterface(target.absolutePath)
        var hasCopiedAttribute = false

        readExifAttributes(source)
            .filterKeys { tag -> tag in transferableExifAttributeTags }
            .forEach { (tag, value) ->
                targetExif.setAttribute(tag, value)
                hasCopiedAttribute = true
            }

        if (hasCopiedAttribute) {
            targetExif.saveAttributes()
        }
    }

    private fun readExifAttributes(source: File): Map<String, String> {
        val sourceExif = ExifInterface(source.absolutePath)
        return exifAttributeTags
            .mapNotNull { tag -> sourceExif.getAttribute(tag)?.let { value -> tag to value } }
            .toMap()
    }

    private fun discoverExifAttributeTags(): List<String> {
        return ExifInterface::class.java.fields
            .asSequence()
            .filter { field ->
                Modifier.isStatic(field.modifiers) &&
                    field.type == String::class.java &&
                    field.name.startsWith("TAG_")
            }
            .mapNotNull { field -> runCatching { field.get(null) as? String }.getOrNull() }
            .distinct()
            .toList()
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

    private fun cleanupOldUploadChildren(root: File, maxAgeMillis: Long) {
        if (!root.exists()) return
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        root.listFiles().orEmpty().forEach { child ->
            if (child.lastModified() < cutoff && !hasUploadWaitingForRetry(child)) {
                deleteInsideRoot(root, child)
            }
        }
    }

    private fun hasUploadWaitingForRetry(uploadDir: File): Boolean {
        val file = manifestFile(uploadDir)
        if (!file.isFile) return false
        return runCatching {
            SignalQuestUploadManifestCodec.decode(file.readText())
                .files
                .any { uploadFile -> SignalQuestUploadFileStatus.shouldUpload(uploadFile.status) }
        }.getOrDefault(false)
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
