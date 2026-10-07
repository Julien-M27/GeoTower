package fr.geotower.utils

import fr.geotower.utils.PreferenceProfileManager.displayValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceProfileManagerTest {

    @Test
    fun diffValuesReturnsEmptyWhenValuesAreEqual() {
        val current = mapOf(
            "theme_mode" to PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 1),
            "is_oled_mode" to PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true),
            "app_language" to PreferenceProfileValue(PreferenceProfileValue.TYPE_STRING, "fr")
        )
        val target = mapOf(
            "theme_mode" to PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 1),
            "is_oled_mode" to PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true),
            "app_language" to PreferenceProfileValue(PreferenceProfileValue.TYPE_STRING, "fr")
        )

        val diff = PreferenceProfileManager.diffValues(current, target)
        assertTrue(diff.isEmpty())
    }

    @Test
    fun diffValuesIgnoresSettingsMatchingFactoryDefaultsWhenMissingFromBase() {
        // When base has no entries (clean install), but profile explicitly holds factory default values
        val base = emptyMap<String, PreferenceProfileValue>()
        val profile = mapOf(
            "map_provider" to PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 1), // OpenStreetMap (default)
            "theme_mode" to PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 0), // Système (default)
            "app_language" to PreferenceProfileValue(PreferenceProfileValue.TYPE_STRING, "system"), // Système (default)
            "is_oled_mode" to PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true) // Activé (default)
        )

        val diff = PreferenceProfileManager.diffValues(base, profile)
        assertTrue("Factory defaults should not be flagged as differences", diff.isEmpty())
    }

    @Test
    fun diffValuesShowsConcreteFactoryDefaultWhenOverridden() {
        val base = emptyMap<String, PreferenceProfileValue>()
        val profile = mapOf(
            "map_provider" to PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 2), // Google Maps
            "default_operator" to PreferenceProfileValue(PreferenceProfileValue.TYPE_STRING, "Orange")
        )

        val diff = PreferenceProfileManager.diffValues(base, profile)
        assertEquals(2, diff.size)

        val mapDiff = diff.firstOrNull { it.key == "map_provider" }
        assertNotNull(mapDiff)
        assertEquals("OpenStreetMap", mapDiff?.oldValue)
        assertEquals("Google Maps", mapDiff?.newValue)

        val opDiff = diff.firstOrNull { it.key == "default_operator" }
        assertNotNull(opDiff)
        assertEquals("Aucun", opDiff?.oldValue)
        assertEquals("Orange", opDiff?.newValue)
    }

    @Test
    fun diffValuesIdentifiesOnlyChangedPreferences() {
        val base = mapOf(
            "theme_mode" to PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 0),
            "is_oled_mode" to PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true),
            "map_provider" to PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 1),
            "nav_mode" to PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 0)
        )
        val profile = mapOf(
            "theme_mode" to PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 2), // Changed
            "is_oled_mode" to PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true), // Unchanged
            "map_provider" to PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 3), // Changed
            "nav_mode" to PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 0) // Unchanged
        )

        val diff = PreferenceProfileManager.diffValues(base, profile)
        assertEquals(2, diff.size)

        val themeDiff = diff.firstOrNull { it.key == "theme_mode" }
        assertNotNull(themeDiff)
        assertEquals("Système", themeDiff?.oldValue)
        assertEquals("Sombre", themeDiff?.newValue)

        val mapDiff = diff.firstOrNull { it.key == "map_provider" }
        assertNotNull(mapDiff)
        assertEquals("OpenStreetMap", mapDiff?.oldValue)
        assertEquals("IGN", mapDiff?.newValue)
    }

    @Test
    fun displayValueFormatsKnownPreferencesMeaningfully() {
        val darkVal = PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 2)
        val lightVal = PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 1)
        val autoVal = PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 0)
        val trueVal = PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
        val falseVal = PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, false)
        val osmVal = PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 1)
        val gmapsVal = PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 2)
        val ignVal = PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 3)

        assertEquals("Sombre", darkVal.displayValue("theme_mode"))
        assertEquals("Clair", lightVal.displayValue("theme_mode"))
        assertEquals("Système", autoVal.displayValue("theme_mode"))
        assertEquals("Activé", trueVal.displayValue("is_oled_mode"))
        assertEquals("Désactivé", falseVal.displayValue("is_oled_mode"))
        assertEquals("OpenStreetMap", osmVal.displayValue("map_provider"))
        assertEquals("Google Maps", gmapsVal.displayValue("map_provider"))
        assertEquals("IGN", ignVal.displayValue("map_provider"))
    }

    @Test
    fun base64UrlEncodeAndDecodeRoundTrip() {
        val testPayloads = listOf(
            byteArrayOf(),
            byteArrayOf(1),
            byteArrayOf(1, 2),
            byteArrayOf(1, 2, 3),
            byteArrayOf(1, 2, 3, 4),
            "Hello, GeoTower profiles and QR code sharing!".toByteArray(Charsets.UTF_8),
            ByteArray(256) { it.toByte() }
        )

        for (payload in testPayloads) {
            val encoded = PreferenceProfileManager.encodeBase64Url(payload)
            // Ensure URL-safe characters only
            assertTrue(encoded.none { it == '+' || it == '/' || it == '=' })
            val decoded = PreferenceProfileManager.decodeBase64Url(encoded)
            assertEquals(payload.toList(), decoded.toList())
        }
    }

    @Test
    fun gzipCompressAndDecompressRoundTrip() {
        val originalText = "{\"theme_mode\":{\"type\":\"int\",\"value\":2},\"name\":\"Mode Nuit\"}"
        val compressed = PreferenceProfileManager.compressGzip(originalText.toByteArray(Charsets.UTF_8))
        val decompressed = PreferenceProfileManager.decompressGzip(compressed)

        assertEquals(originalText, decompressed)
    }

    @Test
    fun qrProfileSerializationAndDeserializationRoundTripWithDiff() {
        val baseValues: Map<String, PreferenceProfileValue> = mapOf(
            "theme_mode" to PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 0),
            "is_oled_mode" to PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, false),
            "map_provider" to PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 1),
            "nav_mode" to PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 0),
            "app_language" to PreferenceProfileValue(PreferenceProfileValue.TYPE_STRING, "system")
        )
        val baseProfile = PreferenceProfile(
            id = "default",
            name = "Par défaut",
            colorArgb = 0xFF2563EB.toInt(),
            icon = "star",
            createdAt = 1000L,
            updatedAt = 1000L,
            values = baseValues
        )

        val customProfileValues: Map<String, PreferenceProfileValue> = baseValues.toMutableMap().apply {
            put("theme_mode", PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 2)) // Changed
            put("is_oled_mode", PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)) // Changed
        }
        val customProfile = PreferenceProfile(
            id = "custom-1",
            name = "Randonnée Nuit",
            colorArgb = 0xFF10B981.toInt(),
            icon = "map",
            createdAt = 2000L,
            updatedAt = 2000L,
            values = customProfileValues
        )

        // 1. Generate QR string using baseProfile diff
        val qrData = PreferenceProfileManager.generateProfileQrData(customProfile, baseProfile)
        assertTrue(qrData.isNotBlank())

        // 2. Decode QR data merging with base values
        val decoded = PreferenceProfileManager.decodeProfileFromQrData(
            rawData = qrData,
            baseValues = baseValues,
            existingNames = listOf("Randonnée Nuit")
        )

        assertNotNull(decoded)
        // Name collision handled
        assertEquals("Randonnée Nuit 2", decoded?.name)
        assertEquals(customProfile.colorArgb, decoded?.colorArgb)
        assertEquals(customProfile.icon, decoded?.icon)

        // The decoded profile has both changed values and unmodified base values
        assertEquals(PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 2), decoded?.values?.get("theme_mode"))
        assertEquals(PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true), decoded?.values?.get("is_oled_mode"))
        assertEquals(PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 1), decoded?.values?.get("map_provider"))
        assertEquals(PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 0), decoded?.values?.get("nav_mode"))
        assertEquals(PreferenceProfileValue(PreferenceProfileValue.TYPE_STRING, "system"), decoded?.values?.get("app_language"))
    }

    @Test
    fun personalizedNameRegexMatchesOnlyAutoGeneratedNames() {
        val regex = PreferenceProfileManager.personalizedNameRegex
        assertTrue(regex.matches("Personnalisé"))
        assertTrue(regex.matches("Personnalisé 1"))
        assertTrue(regex.matches("Personnalisé 2"))
        assertTrue(regex.matches("Personnalisé1"))
        assertTrue(regex.matches("personnalisé 3"))

        org.junit.Assert.assertFalse(regex.matches("Par défaut"))
        org.junit.Assert.assertFalse(regex.matches("Profil"))
        org.junit.Assert.assertFalse(regex.matches("Profil 1"))
        org.junit.Assert.assertFalse(regex.matches("Randonnée"))
        org.junit.Assert.assertFalse(regex.matches("Mon Personnalisé"))
    }

    @Test
    fun profileWithZeroDiffsProducesEmptyList() {
        val baseDefaults = PreferenceProfileManager.factoryDefaultValues()
        // Simulated profile where values match factory defaults (like map_provider=1, theme_mode=0)
        val profileValues = mapOf(
            "map_provider" to PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 1),
            "theme_mode" to PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 0),
            "app_language" to PreferenceProfileValue(PreferenceProfileValue.TYPE_STRING, "system"),
            "site_show_status" to PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
        )

        val diff = PreferenceProfileManager.diffValues(baseDefaults, profileValues)
        assertTrue("A profile containing only default settings must have zero diffs", diff.isEmpty())
    }

    @Test
    fun profileWithModifiedSettingsProducesDiff() {
        val baseDefaults = PreferenceProfileManager.factoryDefaultValues()
        val profileValues = mapOf(
            "theme_mode" to PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 2), // Dark
            "map_provider" to PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 3) // IGN
        )

        val diff = PreferenceProfileManager.diffValues(baseDefaults, profileValues)
        assertEquals(2, diff.size)
    }

    @Test
    fun qrCodeGeneratesAndEncodesEvenWithManyModifiedSettings() {
        val baseDefaults = PreferenceProfileManager.factoryDefaultValues()
        // Invert/modify all available factory default settings
        val modifiedValues = baseDefaults.mapValues { (key, value) ->
            when (value.type) {
                PreferenceProfileValue.TYPE_BOOLEAN -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, !(value.value as Boolean))
                PreferenceProfileValue.TYPE_INT -> PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, (value.value as Int) + 1)
                PreferenceProfileValue.TYPE_LONG -> PreferenceProfileValue(PreferenceProfileValue.TYPE_LONG, (value.value as Long) + 100L)
                PreferenceProfileValue.TYPE_FLOAT -> PreferenceProfileValue(PreferenceProfileValue.TYPE_FLOAT, (value.value as Float) + 0.5f)
                PreferenceProfileValue.TYPE_STRING -> PreferenceProfileValue(PreferenceProfileValue.TYPE_STRING, "custom_${value.value}")
                PreferenceProfileValue.TYPE_STRING_SET -> PreferenceProfileValue(PreferenceProfileValue.TYPE_STRING_SET, setOf("custom_item"))
                else -> value
            }
        }

        val baseProfile = PreferenceProfile(
            id = "default",
            name = "Par défaut",
            colorArgb = 0xFF2563EB.toInt(),
            icon = "star",
            createdAt = 1000L,
            updatedAt = 1000L,
            values = baseDefaults
        )

        val heavyProfile = PreferenceProfile(
            id = "heavy-custom",
            name = "Profil Très Personnalisé",
            colorArgb = 0xFFFF5722.toInt(),
            icon = "flash",
            createdAt = 2000L,
            updatedAt = 2000L,
            values = modifiedValues
        )

        // 1. Generate QR string
        val qrData = PreferenceProfileManager.generateProfileQrData(heavyProfile, baseProfile)
        assertTrue(qrData.isNotBlank())
        // The payload must be reasonably compact (typically under 1000 characters)
        assertTrue("QR payload length (${qrData.length}) must be compact to fit in QR Code", qrData.length < 1200)

        // 2. Deep link should encode into QR matrix via ZXing without throwing DataTooBigException
        val deepLink = "geotower://profile?data=$qrData"
        val hints = java.util.EnumMap<com.google.zxing.EncodeHintType, Any>(com.google.zxing.EncodeHintType::class.java).apply {
            put(com.google.zxing.EncodeHintType.MARGIN, 1)
            put(com.google.zxing.EncodeHintType.ERROR_CORRECTION, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M)
            put(com.google.zxing.EncodeHintType.CHARACTER_SET, "ISO-8859-1")
        }
        val bitMatrix = com.google.zxing.qrcode.QRCodeWriter().encode(
            deepLink,
            com.google.zxing.BarcodeFormat.QR_CODE,
            512,
            512,
            hints
        )
        assertNotNull(bitMatrix)
        assertTrue(bitMatrix.width > 0)

        // 3. Round-trip decode matches modified settings
        val decoded = PreferenceProfileManager.decodeProfileFromQrData(
            rawData = qrData,
            baseValues = baseDefaults,
            existingNames = emptyList()
        )
        assertNotNull(decoded)
        assertEquals(heavyProfile.name, decoded?.name)
        assertEquals(heavyProfile.colorArgb, decoded?.colorArgb)
        assertEquals(heavyProfile.icon, decoded?.icon)
        assertEquals(modifiedValues.size, decoded?.values?.size)
    }
}

