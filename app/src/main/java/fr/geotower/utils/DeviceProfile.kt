package fr.geotower.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build

object DeviceProfile {
    val manufacturer: String
        get() = Build.MANUFACTURER.orEmpty()

    val isSamsungDevice: Boolean
        get() = manufacturer.equals("samsung", ignoreCase = true)

    val supportsSamsungOngoingActivity: Boolean
        get() = isSamsungDevice

    @Volatile
    private var glEs3Supported: Boolean? = null

    /**
     * Vérifie si l'appareil supporte matériellement OpenGL ES 3.0 ou supérieur,
     * requis pour le moteur 3D MapLibre.
     */
    fun supportsOpenGlEs3(context: Context): Boolean {
        glEs3Supported?.let { return it }
        val supported = try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val reqGlEsVersion = am?.deviceConfigurationInfo?.reqGlEsVersion ?: 0
            reqGlEsVersion >= 0x00030000
        } catch (_: Throwable) {
            false
        }
        glEs3Supported = supported
        return supported
    }

    // Plus de détection de fold par modèle : l'affichage fractionné se décide sur la taille réelle
    // de la fenêtre (AppConfig.splitDisplayEnabled), ce qui couvre tous les pliables et les
    // tablettes, et suit le pliage au lieu de figer un choix au démarrage.
}
