package fr.geotower.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class DownloadManifestVerifierTest {
    @Test
    fun verifyAndParse_acceptsValidSignedManifest() {
        val keyPair = generateKeyPair()
        val payload = validPayload()
        val signed = signedManifest(payload, keyPair)
        val publicKeys = mapOf("test-key" to Base64.getEncoder().encodeToString(keyPair.public.encoded))

        val manifest = DownloadManifestVerifier.verifyAndParse(
            rawJson = signed,
            publicKeys = publicKeys,
            nowEpochSeconds = 1_500L
        )

        assertNotNull(manifest)
        assertEquals("geotower_fr.db", manifest?.database?.filename)
        assertEquals("1.0.0", manifest?.database?.version)
        assertEquals("geotower_fr_radio.db", manifest?.radioDatabase?.filename)
        assertEquals("20260228-radio-v1", manifest?.radioDatabase?.version)
        assertEquals(1, manifest?.maps?.size)
        assertEquals("alsace.map", manifest?.maps?.single()?.mapFilename)
    }

    @Test
    fun verifyAndParse_rejectsTamperedSignatureUnknownKeyAndExpiredPayload() {
        val keyPair = generateKeyPair()
        val publicKeys = mapOf("test-key" to Base64.getEncoder().encodeToString(keyPair.public.encoded))

        val signed = signedManifest(validPayload(), keyPair)
        val tampered = signed.replace("SHA256withECDSA", "SHA256withRSA")

        assertNull(DownloadManifestVerifier.verifyAndParse(tampered, publicKeys, nowEpochSeconds = 1_500L))
        assertNull(DownloadManifestVerifier.verifyAndParse(signed, emptyMap(), nowEpochSeconds = 1_500L))
        assertNull(DownloadManifestVerifier.verifyAndParse(signed, publicKeys, nowEpochSeconds = 3_001L))
    }

    @Test
    fun configuredPublicKeys_parsesKeyIdPairs() {
        assertEquals(
            mapOf("prod" to "abc", "next" to "def"),
            DownloadManifestVerifier.configuredPublicKeys("prod:abc,next:def")
        )
    }

    private fun validPayload(): String {
        return """
            {
              "schemaVersion": 1,
              "generatedAt": 1000,
              "expiresAt": 3000,
              "db": {
                "filename": "geotower_fr.db",
                "url": "https://api.cajejuma.fr/api/v2/download/db",
                "size_bytes": 1024,
                "sha256": "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                "schema_version": 7,
                "country_code": "FR",
                "version": "1.0.0"
              },
              "radio_db": {
                "filename": "geotower_fr_radio.db",
                "url": "https://api.cajejuma.fr/api/v2/download/radio_db",
                "size_bytes": 2048,
                "sha256": "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                "schema_version": 1,
                "country_code": "FR",
                "version": "20260228-radio-v1"
              },
              "maps": [
                {
                  "id": "alsace",
                  "name": "Alsace",
                  "description": "Region Grand Est",
                  "map_url": "https://download.mapsforge.org/maps/v5/europe/france/alsace.map",
                  "estimated_size_mb": 85,
                  "map_filename": "alsace.map",
                  "sha256": "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
                }
              ]
            }
        """.trimIndent()
    }

    private fun signedManifest(payload: String, keyPair: KeyPair): String {
        val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(payloadBytes)
            sign()
        }
        val payloadBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes)
        val signatureBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
        return """
            {
              "schemaVersion": 1,
              "keyId": "test-key",
              "algorithm": "SHA256withECDSA",
              "payload": "$payloadBase64",
              "signature": "$signatureBase64"
            }
        """.trimIndent()
    }

    private fun generateKeyPair(): KeyPair {
        return KeyPairGenerator.getInstance("EC").apply {
            initialize(256)
        }.generateKeyPair()
    }
}
