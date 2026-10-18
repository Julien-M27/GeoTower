package fr.geotower.ui.screens.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticEnvironmentDetailsTest {

    @Test
    fun formatDeviceLabelSameManufacturerAndBrandUsesManufacturerOnce() {
        val label = formatDeviceLabel(
            manufacturer = "Google",
            brand = "google",
            model = "Pixel 8 Pro",
            fallbackUnknown = "Inconnu"
        )
        assertEquals("Google Pixel 8 Pro", label)
    }

    @Test
    fun formatDeviceLabelDifferentBrandIncludesBrandInParentheses() {
        val label = formatDeviceLabel(
            manufacturer = "Xiaomi",
            brand = "POCO",
            model = "22011211G",
            fallbackUnknown = "Inconnu"
        )
        assertEquals("Xiaomi (POCO) 22011211G", label)
    }

    @Test
    fun formatDeviceLabelBlankManufacturerUsesModelOrFallback() {
        val labelWithModel = formatDeviceLabel(
            manufacturer = "",
            brand = "",
            model = "SM-A528B",
            fallbackUnknown = "Inconnu"
        )
        assertEquals("SM-A528B", labelWithModel)

        val labelBlank = formatDeviceLabel(
            manufacturer = "",
            brand = "",
            model = "",
            fallbackUnknown = "Inconnu"
        )
        assertEquals("Inconnu", labelBlank)
    }

    @Test
    fun formatCpuArchitectureCombinesSocAndAbis() {
        val formatted = formatCpuArchitecture(
            socModel = "Snapdragon 778G",
            supportedAbis = arrayOf("arm64-v8a", "armeabi-v7a")
        )
        assertEquals("Snapdragon 778G · arm64-v8a, armeabi-v7a", formatted)
    }

    @Test
    fun formatCpuArchitectureHandlesUnknownOrNullSoc() {
        val formattedNull = formatCpuArchitecture(
            socModel = null,
            supportedAbis = arrayOf("arm64-v8a")
        )
        assertEquals("arm64-v8a", formattedNull)

        val formattedUnknown = formatCpuArchitecture(
            socModel = "unknown",
            supportedAbis = arrayOf("arm64-v8a")
        )
        assertEquals("arm64-v8a", formattedUnknown)
    }

    @Test
    fun formatCpuArchitectureHandlesEmptyInputs() {
        val formattedEmpty = formatCpuArchitecture(
            socModel = "",
            supportedAbis = emptyArray()
        )
        assertEquals("", formattedEmpty)
    }
}
