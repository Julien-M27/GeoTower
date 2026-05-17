package fr.geotower.data.upload

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

data class ExternalPhotoUploadHistoryEntry(
    val id: String,
    val uploadId: String,
    val sourceName: String,
    val supportId: String,
    val operator: String,
    val createdAtMillis: Long,
    val thumbnailPath: String?,
    val status: String,
    val stripExifBeforeUpload: Boolean,
    val remotePhotoId: String? = null,
    val remoteImageUrl: String? = null,
    val remoteUploadedAt: String? = null,
    val lastValidationCheckAtMillis: Long? = null,
    val validationCheckCount: Int = 0
)

object ExternalPhotoUploadHistoryStore {
    const val SOURCE_SIGNALQUEST = "SignalQuest"
    const val STATUS_PENDING = "pending"
    const val STATUS_AWAITING_VALIDATION = "awaiting_validation"
    const val STATUS_SUCCESS = "success"
    const val STATUS_FAILED = "failed"
    const val STATUS_RETRY = "retry"

    private const val HISTORY_FILE_NAME = "external_photo_upload_history.json"
    private const val THUMB_DIR_NAME = "external_photo_upload_history_thumbs"
    private const val MAX_HISTORY_ITEMS = 200
    private const val THUMB_MAX_DIMENSION_PX = 144
    private const val THUMB_QUALITY = 42

    private val gson = Gson()

    @Synchronized
    fun addPendingPhoto(
        context: Context,
        uploadId: String,
        sourceName: String,
        supportId: String,
        operator: String,
        createdAtMillis: Long,
        sourceFile: File,
        stripExifBeforeUpload: Boolean
    ): String {
        val id = UUID.randomUUID().toString()
        val entry = ExternalPhotoUploadHistoryEntry(
            id = id,
            uploadId = uploadId,
            sourceName = sourceName,
            supportId = supportId,
            operator = operator,
            createdAtMillis = createdAtMillis,
            thumbnailPath = createThumbnail(context, sourceFile, id),
            status = STATUS_PENDING,
            stripExifBeforeUpload = stripExifBeforeUpload
        )

        val nextEntries = (readInternal(context) + entry)
            .sortedByDescending { it.createdAtMillis }
            .take(MAX_HISTORY_ITEMS)
        saveInternal(context, nextEntries)
        cleanupOrphanThumbnails(context, nextEntries)
        return id
    }

    @Synchronized
    fun updateStatus(context: Context, entryId: String?, status: String) {
        if (entryId.isNullOrBlank()) return
        val nextEntries = readInternal(context).map { entry ->
            if (entry.id == entryId) entry.copy(status = status) else entry
        }
        saveInternal(context, nextEntries)
    }

    @Synchronized
    fun updateUploadResult(
        context: Context,
        entryId: String?,
        status: String,
        remotePhotoId: String? = null,
        remoteImageUrl: String? = null,
        remoteUploadedAt: String? = null
    ) {
        if (entryId.isNullOrBlank()) return
        val nextEntries = readInternal(context).map { entry ->
            if (entry.id == entryId) {
                entry.copy(
                    status = status,
                    remotePhotoId = remotePhotoId ?: entry.remotePhotoId,
                    remoteImageUrl = remoteImageUrl ?: entry.remoteImageUrl,
                    remoteUploadedAt = remoteUploadedAt ?: entry.remoteUploadedAt,
                    lastValidationCheckAtMillis = if (status == STATUS_AWAITING_VALIDATION) null else entry.lastValidationCheckAtMillis,
                    validationCheckCount = if (status == STATUS_AWAITING_VALIDATION) 0 else entry.validationCheckCount
                )
            } else {
                entry
            }
        }
        saveInternal(context, nextEntries)
    }

    @Synchronized
    fun markValidated(
        context: Context,
        entryId: String,
        remotePhotoId: String? = null,
        remoteImageUrl: String? = null,
        remoteUploadedAt: String? = null
    ) {
        val nextEntries = readInternal(context).map { entry ->
            if (entry.id == entryId) {
                entry.copy(
                    status = STATUS_SUCCESS,
                    remotePhotoId = remotePhotoId ?: entry.remotePhotoId,
                    remoteImageUrl = remoteImageUrl ?: entry.remoteImageUrl,
                    remoteUploadedAt = remoteUploadedAt ?: entry.remoteUploadedAt,
                    lastValidationCheckAtMillis = System.currentTimeMillis()
                )
            } else {
                entry
            }
        }
        saveInternal(context, nextEntries)
    }

    @Synchronized
    fun recordValidationCheck(context: Context, entryId: String) {
        val checkedAt = System.currentTimeMillis()
        val nextEntries = readInternal(context).map { entry ->
            if (entry.id == entryId) {
                entry.copy(
                    lastValidationCheckAtMillis = checkedAt,
                    validationCheckCount = entry.validationCheckCount + 1
                )
            } else {
                entry
            }
        }
        saveInternal(context, nextEntries)
    }

    @Synchronized
    fun hasAwaitingValidation(context: Context): Boolean {
        return readInternal(context).any { it.status == STATUS_AWAITING_VALIDATION }
    }

    @Synchronized
    fun removeUpload(context: Context, uploadId: String) {
        if (uploadId.isBlank()) return
        val entries = readInternal(context)
        val removedEntries = entries.filter { it.uploadId == uploadId }
        if (removedEntries.isEmpty()) return

        removedEntries.forEach { entry ->
            entry.thumbnailPath?.let { File(it).delete() }
        }
        saveInternal(context, entries.filterNot { it.uploadId == uploadId })
    }

    @Synchronized
    fun removeEntries(context: Context, entryIds: Collection<String>) {
        val ids = entryIds.filter { it.isNotBlank() }.toSet()
        if (ids.isEmpty()) return

        val entries = readInternal(context)
        val removedEntries = entries.filter { it.id in ids }
        if (removedEntries.isEmpty()) return

        removedEntries.forEach { entry ->
            entry.thumbnailPath?.let { File(it).delete() }
        }
        val nextEntries = entries.filterNot { it.id in ids }
        saveInternal(context, nextEntries)
        cleanupOrphanThumbnails(context, nextEntries)
    }

    fun estimatedFreedBytes(entries: List<ExternalPhotoUploadHistoryEntry>): Long {
        return entries.sumOf { entry ->
            val thumbnailBytes = entry.thumbnailPath
                ?.let(::File)
                ?.takeIf { it.isFile }
                ?.length()
                ?: 0L
            thumbnailBytes + gson.toJson(entry).toByteArray().size.toLong()
        }
    }

    @Synchronized
    fun read(context: Context): List<ExternalPhotoUploadHistoryEntry> {
        return readInternal(context).sortedByDescending { it.createdAtMillis }
    }

    @Synchronized
    fun clear(context: Context) {
        historyFile(context).delete()
        thumbnailDir(context).deleteRecursively()
    }

    private fun createThumbnail(context: Context, sourceFile: File, id: String): String? {
        if (!sourceFile.isFile) return null

        var decoded: Bitmap? = null
        var oriented: Bitmap? = null
        var scaled: Bitmap? = null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val options = BitmapFactory.Options().apply {
                inSampleSize = SignalQuestUploadRules.calculateInSampleSize(
                    bounds.outWidth,
                    bounds.outHeight,
                    THUMB_MAX_DIMENSION_PX
                )
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            decoded = BitmapFactory.decodeFile(sourceFile.absolutePath, options) ?: return@runCatching null
            val orientedBitmap = applyExifOrientation(sourceFile, decoded!!)
            if (orientedBitmap !== decoded) {
                oriented = orientedBitmap
            }
            scaled = scaleToMaxDimension(orientedBitmap, THUMB_MAX_DIMENSION_PX)

            val targetFile = File(thumbnailDir(context).apply { mkdirs() }, "$id.jpg")
            FileOutputStream(targetFile).use { output ->
                scaled!!.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, output)
            }
            targetFile.absolutePath
        }.getOrNull().also {
            if (scaled !== decoded && scaled !== oriented) scaled?.recycle()
            oriented?.recycle()
            decoded?.recycle()
        }
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

    private fun readInternal(context: Context): List<ExternalPhotoUploadHistoryEntry> {
        val file = historyFile(context)
        if (!file.isFile) return emptyList()
        return runCatching {
            gson.fromJson(file.readText(), Array<ExternalPhotoUploadHistoryEntry>::class.java)
                ?.toList()
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun saveInternal(context: Context, entries: List<ExternalPhotoUploadHistoryEntry>) {
        historyFile(context).writeText(gson.toJson(entries))
    }

    private fun cleanupOrphanThumbnails(context: Context, entries: List<ExternalPhotoUploadHistoryEntry>) {
        val keepNames = entries.mapNotNull { entry ->
            entry.thumbnailPath?.let { File(it).name }
        }.toSet()
        thumbnailDir(context).listFiles()?.forEach { file ->
            if (file.name !in keepNames) file.delete()
        }
    }

    private fun historyFile(context: Context): File = File(context.filesDir, HISTORY_FILE_NAME)

    private fun thumbnailDir(context: Context): File = File(context.filesDir, THUMB_DIR_NAME)
}
