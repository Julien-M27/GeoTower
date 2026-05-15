package fr.geotower.utils

import android.os.Build

object DeviceProfile {
    val manufacturer: String
        get() = Build.MANUFACTURER.orEmpty()

    val model: String
        get() = Build.MODEL.orEmpty()

    val device: String
        get() = Build.DEVICE.orEmpty()

    val isSamsungDevice: Boolean
        get() = manufacturer.equals("samsung", ignoreCase = true)

    val supportsSamsungOngoingActivity: Boolean
        get() = isSamsungDevice

    val prefersSplitDisplay: Boolean
        get() = isGalaxyZFold || isGoogleFold

    private val isGalaxyZFold: Boolean
        get() = model.startsWith("SM-F9", ignoreCase = true)

    private val isGoogleFold: Boolean
        get() {
            val pixelFoldModels = setOf("GQK96", "GGH2X", "G9FNL")
            val pixelFoldDevices = setOf("felix", "comet")

            return model.contains("Fold", ignoreCase = true) ||
                model.uppercase() in pixelFoldModels ||
                device.lowercase() in pixelFoldDevices
        }
}
