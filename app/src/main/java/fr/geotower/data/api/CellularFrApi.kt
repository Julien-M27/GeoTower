package fr.geotower.data.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.geotower.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

data class CellularFrPhoto(
    val url: String,
    val author: String?,
    val uploadedAt: String?
)

object CellularFrApi {
    private const val TAG = "GeoTowerCellularFR"
    private const val BASE_URL = "https://cellularfr.fr/"
    private const val HOST = "cellularfr.fr"

    suspend fun getCellularFrPhotos(siteId: String): List<CellularFrPhoto> = withContext(Dispatchers.IO) {
        if (siteId.isBlank()) return@withContext emptyList()

        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("api")
            .addPathSegment("photos")
            .addQueryParameter("siteId", siteId)
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            RetrofitClient.currentClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w(TAG, "CellularFR photos request failed code=${response.code}")
                    return@use emptyList()
                }
                val body = response.body?.string() ?: return@use emptyList()
                parseCellularFrPhotos(body)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "CellularFR photos request failed", e)
            emptyList()
        }
    }

    internal fun resolvePhotoUrl(relativeUrl: String): String? {
        val trimmed = relativeUrl.trim()
        if (!trimmed.startsWith("/") || trimmed.startsWith("//")) return null

        val resolved = BASE_URL.toHttpUrl().resolve(trimmed) ?: return null
        if (resolved.scheme != "https" || resolved.host != HOST) return null
        return resolved.toString()
    }
}

internal fun parseCellularFrPhotos(json: String): List<CellularFrPhoto> {
    return runCatching {
        val root = JsonParser.parseString(json).asJsonObjectOrNull() ?: return@runCatching emptyList()
        val photos = root.get("photos")?.takeIf { it.isJsonArray }?.asJsonArray ?: return@runCatching emptyList()
        buildList {
            for (index in 0 until photos.size()) {
                val photoObject = photos[index].asJsonObjectOrNull() ?: continue
                val url = CellularFrApi.resolvePhotoUrl(photoObject.getStringOrNull("url").orEmpty()) ?: continue
                add(
                    CellularFrPhoto(
                        url = url,
                        author = photoObject.getStringOrNull("nickname"),
                        uploadedAt = photoObject.getStringOrNull("uploadDate")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun JsonElement?.asJsonObjectOrNull(): JsonObject? {
    return this?.takeIf { it.isJsonObject }?.asJsonObject
}

private fun JsonObject.getStringOrNull(name: String): String? {
    val value = get(name) ?: return null
    if (value.isJsonNull) return null
    return value.asString.takeIf { it.isNotBlank() }
}
