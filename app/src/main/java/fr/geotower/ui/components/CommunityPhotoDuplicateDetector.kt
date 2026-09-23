package fr.geotower.ui.screens.emitters

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.imageLoader
import coil.request.ImageRequest
import fr.geotower.utils.OperatorColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

private const val HASH_WIDTH = 9
private const val HASH_HEIGHT = 8
private const val MAX_HAMMING_DISTANCE = 6
private const val DUPLICATE_CACHE_KEY_PREFIX = "community_photo_duplicate_hash_v1_"

internal object CommunityPhotoDuplicateCache {
    fun getHash(prefs: SharedPreferences, url: String): Long? {
        return prefs.getString(cacheKey(url), null)?.toLongOrNull()
    }

    fun putHashes(prefs: SharedPreferences, hashesByUrl: Map<String, Long>) {
        if (hashesByUrl.isEmpty()) return

        prefs.edit().apply {
            hashesByUrl.forEach { (url, hash) ->
                putString(cacheKey(url), hash.toString())
            }
        }.apply()
    }

    private fun cacheKey(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return DUPLICATE_CACHE_KEY_PREFIX + digest
    }
}

internal fun readCachedCommunityPhotoHashes(
    prefs: SharedPreferences,
    photos: List<CommunityPhoto>
): Map<String, Long> {
    return photos.distinctBy { it.url }.mapNotNull { photo ->
        CommunityPhotoDuplicateCache.getHash(prefs, photo.url)?.let { hash ->
            photo.url to hash
        }
    }.toMap()
}

/**
 * Calcule une empreinte perceptuelle différentielle : les petites variations de compression,
 * taille ou luminosité changent peu le résultat, contrairement à une comparaison d'URL.
 */
internal fun perceptualPhotoHash(bitmap: Bitmap): Long {
    val scaled = Bitmap.createScaledBitmap(bitmap, HASH_WIDTH, HASH_HEIGHT, true)
    val pixels = IntArray(HASH_WIDTH * HASH_HEIGHT)
    scaled.getPixels(pixels, 0, HASH_WIDTH, 0, 0, HASH_WIDTH, HASH_HEIGHT)
    if (scaled !== bitmap) scaled.recycle()

    var hash = 0L
    var bit = 0
    for (row in 0 until HASH_HEIGHT) {
        for (column in 0 until HASH_WIDTH - 1) {
            val left = luminance(pixels[row * HASH_WIDTH + column])
            val right = luminance(pixels[row * HASH_WIDTH + column + 1])
            if (left > right) hash = hash or (1L shl bit)
            bit++
        }
    }
    return hash
}

internal fun photoHashesAreSimilar(first: Long, second: Long): Boolean {
    return java.lang.Long.bitCount(first xor second) <= MAX_HAMMING_DISTANCE
}

internal fun deduplicateCommunityPhotos(
    photos: List<CommunityPhoto>,
    perceptualHashesByUrl: Map<String, Long>
): List<CommunityPhoto> {
    val result = mutableListOf<CommunityPhoto>()

    photos.forEach { photo ->
        val duplicateIndex = result.indexOfFirst { kept ->
            kept.url == photo.url ||
                (perceptualHashesByUrl[kept.url]?.let { keptHash ->
                    perceptualHashesByUrl[photo.url]?.let { photoHash ->
                        photoHashesAreSimilar(keptHash, photoHash)
                    }
                } == true)
        }

        if (duplicateIndex < 0) {
            result += photo
        } else {
            val kept = result[duplicateIndex]
            val hiddenOperator = photo.operatorDisplayLabelForDuplicate()
            if (!hiddenOperator.isNullOrBlank() &&
                hiddenOperator !in kept.duplicateOperatorLabels &&
                hiddenOperator != kept.operatorDisplayLabelForDuplicate()
            ) {
                result[duplicateIndex] = kept.copy(
                    duplicateOperatorLabels = kept.duplicateOperatorLabels + hiddenOperator
                )
            }
        }
    }

    return result
}

internal suspend fun loadCommunityPhotoHashes(
    context: Context,
    photos: List<CommunityPhoto>,
    prefs: SharedPreferences,
    imageLoader: ImageLoader = context.imageLoader
): Map<String, Long> = withContext(Dispatchers.IO) {
    val uniquePhotos = photos.distinctBy { it.url }
    val cachedHashes = readCachedCommunityPhotoHashes(prefs, uniquePhotos)
    val downloadedHashes = buildMap {
        uniquePhotos
            .filterNot { it.url in cachedHashes }
            .forEach { photo ->
                try {
                    val request = ImageRequest.Builder(context)
                        .data(photo.url)
                        .size(HASH_WIDTH, HASH_HEIGHT)
                        .allowHardware(false)
                        .build()
                    imageLoader.execute(request).drawable?.let { drawable ->
                        val bitmap = (drawable as? BitmapDrawable)?.bitmap
                            ?: drawable.toBitmap(HASH_WIDTH, HASH_HEIGHT)
                        put(photo.url, perceptualPhotoHash(bitmap))
                    }
                } catch (_: Exception) {
                    // Une image indisponible reste affichée par Coil avec son fallback ; elle ne peut
                    // simplement pas participer à la détection visuelle de cette session.
                }
            }
    }
    CommunityPhotoDuplicateCache.putHashes(prefs, downloadedHashes)
    cachedHashes + downloadedHashes
}

private fun luminance(color: Int): Int {
    return (android.graphics.Color.red(color) * 299 +
        android.graphics.Color.green(color) * 587 +
        android.graphics.Color.blue(color) * 114) / 1000
}

internal fun CommunityPhoto.operatorDisplayLabelForDuplicate(): String? {
    return operatorLabel
        ?.takeIf { it.isNotBlank() }
        ?: operatorKey?.let { key -> OperatorColors.specForKey(key)?.label ?: key }
}

internal fun communityPhotoInfoOperatorLabels(photo: CommunityPhoto): List<String> {
    return buildList {
        photo.operatorDisplayLabelForDuplicate()?.let(::add)
        photo.duplicateOperatorLabels
            .filterNot { it in this }
            .forEach(::add)
    }
}
