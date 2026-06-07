package fr.geotower.data.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.geotower.BuildConfig
import fr.geotower.data.models.OfflineMapDto
import okio.ByteString.Companion.decodeBase64
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Locale

data class DownloadManifest(
    val generatedAt: Long,
    val expiresAt: Long,
    val database: DownloadManifestDatabase?,
    val radioDatabase: DownloadManifestDatabase?,
    val maps: List<OfflineMapDto>
)

data class DownloadManifestDatabase(
    val filename: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    val schemaVersion: Int,
    val countryCode: String,
    val version: String?
)

object DownloadManifestVerifier {
    private const val EXPECTED_SIGNED_SCHEMA_VERSION = 1
    private const val EXPECTED_PAYLOAD_SCHEMA_VERSION = 1
    private const val EXPECTED_ALGORITHM = "SHA256withECDSA"

    fun verifyAndParse(
        rawJson: String,
        publicKeys: Map<String, String> = configuredPublicKeys(),
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000L
    ): DownloadManifest? {
        val signed = runCatching { JsonParser.parseString(rawJson).asJsonObject }.getOrNull() ?: return null
        if (signed.intOrNull("schemaVersion") != EXPECTED_SIGNED_SCHEMA_VERSION) return null
        if (signed.stringOrBlank("algorithm") != EXPECTED_ALGORITHM) return null

        val keyId = signed.stringOrBlank("keyId")
        val publicKeyBase64 = publicKeys[keyId]?.takeIf { it.isNotBlank() } ?: return null
        val payloadBytes = decodeBase64Url(signed.stringOrBlank("payload")) ?: return null
        val signatureBytes = decodeBase64Url(signed.stringOrBlank("signature")) ?: return null
        val publicKeyBytes = decodeBase64Strict(publicKeyBase64) ?: return null

        if (!verifySignature(publicKeyBytes, payloadBytes, signatureBytes)) return null

        val payload = runCatching {
            JsonParser.parseString(String(payloadBytes, StandardCharsets.UTF_8)).asJsonObject
        }.getOrNull() ?: return null

        if (payload.intOrNull("schemaVersion") != EXPECTED_PAYLOAD_SCHEMA_VERSION) return null
        val generatedAt = payload.longOrNull("generatedAt") ?: return null
        val expiresAt = payload.longOrNull("expiresAt") ?: return null
        if (expiresAt < nowEpochSeconds) return null

        return DownloadManifest(
            generatedAt = generatedAt,
            expiresAt = expiresAt,
            database = payload.get("db").asJsonObjectOrNull()?.toDatabase(),
            radioDatabase = payload.get("radio_db").asJsonObjectOrNull()?.toDatabase(),
            maps = payload.get("maps")
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
                ?.mapNotNull { it.asJsonObjectOrNull()?.toOfflineMap() }
                .orEmpty()
        )
    }

    internal fun configuredPublicKeys(config: String = BuildConfig.GEOTOWER_MANIFEST_PUBLIC_KEYS): Map<String, String> {
        return config.split(",")
            .mapNotNull { entry ->
                val separator = entry.indexOf(':')
                if (separator <= 0 || separator == entry.lastIndex) return@mapNotNull null
                val keyId = entry.substring(0, separator).trim()
                val publicKey = entry.substring(separator + 1).trim()
                if (keyId.isBlank() || publicKey.isBlank()) null else keyId to publicKey
            }
            .toMap()
    }

    private fun verifySignature(publicKeyBytes: ByteArray, payloadBytes: ByteArray, signatureBytes: ByteArray): Boolean {
        return runCatching {
            val publicKey = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(publicKeyBytes))
            val verifier = Signature.getInstance(EXPECTED_ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(payloadBytes)
            verifier.verify(signatureBytes)
        }.getOrDefault(false)
    }

    private fun JsonObject.toDatabase(): DownloadManifestDatabase? {
        return DownloadManifestDatabase(
            filename = stringOrBlank("filename"),
            url = stringOrBlank("url"),
            sizeBytes = longOrNull("size_bytes") ?: return null,
            sha256 = stringOrBlank("sha256"),
            schemaVersion = intOrNull("schema_version") ?: return null,
            countryCode = stringOrBlank("country_code").uppercase(Locale.ROOT),
            version = stringOrBlank("version").takeIf { it.isNotBlank() }
        )
    }

    private fun JsonObject.toOfflineMap(): OfflineMapDto? {
        return OfflineMapDto(
            id = stringOrBlank("id").takeIf { it.isNotBlank() } ?: return null,
            name = stringOrBlank("name"),
            description = stringOrBlank("description"),
            mapUrl = stringOrBlank("map_url"),
            estimatedSizeMb = intOrNull("estimated_size_mb") ?: return null,
            mapFilename = stringOrBlank("map_filename"),
            sha256 = stringOrBlank("sha256").takeIf { it.isNotBlank() }
        )
    }

    private fun JsonObject.stringOrBlank(memberName: String): String {
        return get(memberName)
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.let { runCatching { it.asString.trim() }.getOrNull() }
            .orEmpty()
    }

    private fun JsonObject.intOrNull(memberName: String): Int? {
        return get(memberName)
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.let { runCatching { it.asInt }.getOrNull() }
    }

    private fun JsonObject.longOrNull(memberName: String): Long? {
        return get(memberName)
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.let { runCatching { it.asLong }.getOrNull() }
    }

    private fun JsonElement?.asJsonObjectOrNull(): JsonObject? {
        return this?.takeIf { it.isJsonObject }?.asJsonObject
    }

    private fun decodeBase64Strict(value: String): ByteArray? {
        return value.decodeBase64()?.toByteArray()
    }

    private fun decodeBase64Url(value: String): ByteArray? {
        if (value.isBlank()) return null
        val standard = value.replace('-', '+').replace('_', '/')
        val padded = standard + "=".repeat((4 - standard.length % 4) % 4)
        return padded.decodeBase64()?.toByteArray()
    }
}
