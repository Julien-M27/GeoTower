package fr.geotower.data.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.Locale

data class AppReleaseInfo(
    val releaseId: String,
    val versionName: String,
    val publishedAt: String?,
    val downloadUrl: String,
    val fileName: String?,
    val notes: String?,
    val notesTranslations: Map<String, String> = emptyMap()
) {
    fun localizedNotes(languageTag: String?): String? {
        val normalizedTag = languageTag
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.replace("_", "-")
            .orEmpty()
        val languageOnly = normalizedTag.substringBefore("-")
        return notesTranslations[normalizedTag]
            ?: notesTranslations[languageOnly]
            ?: notesTranslations["fr"]
            ?: notesTranslations["en"]
            ?: notesTranslations.values.firstOrNull { it.isNotBlank() }
            ?: notes
    }
}

object AppUpdateChecker {
    private const val LATEST_APP_RELEASE_URL = "https://api.cajejuma.fr/api/v2/app/latest"
    private val VERSION_NUMBER_REGEX = Regex("\\d+")

    suspend fun getLatestRelease(): AppReleaseInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(LATEST_APP_RELEASE_URL)
                    .header("Accept", "application/json")
                    .build()

                val response = RetrofitClient.currentClient.newCall(request).execute()
                response.use {
                    if (!it.isSuccessful) return@withContext null
                    val bodyString = it.body?.string() ?: return@withContext null
                    parseRelease(bodyString)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    internal fun parseRelease(rawJson: String): AppReleaseInfo? {
        val json = JsonParser.parseString(rawJson).asJsonObject
        val releaseId = json.stringOrBlank("releaseId")
        val versionName = json.stringOrBlank("versionName")
        val downloadUrl = json.stringOrBlank("downloadUrl")

        if (releaseId.isBlank() || versionName.isBlank() || !downloadUrl.isWebUrl()) {
            return null
        }

        return AppReleaseInfo(
            releaseId = releaseId,
            versionName = versionName,
            publishedAt = json.stringOrBlank("publishedAt").takeIf { it.isNotBlank() },
            downloadUrl = downloadUrl,
            fileName = json.stringOrBlank("fileName").takeIf { it.isNotBlank() },
            notes = json.stringOrBlank("notes").takeIf { it.isNotBlank() },
            notesTranslations = json.stringMap("notesTranslations")
        )
    }

    fun isRemoteVersionNewer(remoteVersionName: String, installedVersionName: String): Boolean {
        val remoteParts = remoteVersionName.versionParts()
        val installedParts = installedVersionName.versionParts()
        if (remoteParts.isEmpty() || installedParts.isEmpty()) return false

        val maxSize = maxOf(remoteParts.size, installedParts.size)
        for (index in 0 until maxSize) {
            val remotePart = remoteParts.getOrElse(index) { 0 }
            val installedPart = installedParts.getOrElse(index) { 0 }
            if (remotePart != installedPart) return remotePart > installedPart
        }

        return false
    }

    private fun JsonObject.stringOrBlank(memberName: String): String {
        return get(memberName)
            ?.takeIf { !it.isJsonNull }
            ?.asString
            ?.trim()
            .orEmpty()
    }

    private fun JsonObject.stringMap(memberName: String): Map<String, String> {
        val obj = get(memberName)
            ?.takeIf { !it.isJsonNull && it.isJsonObject }
            ?.asJsonObject
            ?: return emptyMap()

        return obj.entrySet().mapNotNull { (rawKey, rawValue) ->
            val key = rawKey.normalizeLanguageKey()
            val value = rawValue
                ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
                ?.let { runCatching { it.asString.trim().take(2000) }.getOrNull() }
                .orEmpty()
            if (key.isBlank() || value.isBlank()) {
                null
            } else {
                key to value
            }
        }.toMap()
    }

    private fun String.isWebUrl(): Boolean {
        return startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)
    }

    private fun String.versionParts(): List<Int> {
        return VERSION_NUMBER_REGEX.findAll(this)
            .mapNotNull { it.value.toIntOrNull() }
            .toList()
    }

    private fun String.normalizeLanguageKey(): String {
        val normalized = trim()
            .lowercase(Locale.ROOT)
            .replace("_", "-")
            .take(20)
        return normalized.takeIf { key ->
            key.isNotBlank() && key.all { it.isLetterOrDigit() || it == '-' }
        }.orEmpty()
    }
}
