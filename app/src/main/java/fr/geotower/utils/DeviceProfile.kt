package fr.geotower.utils

import android.os.Build

object DeviceProfile {
    val manufacturer: String
        get() = Build.MANUFACTURER.orEmpty()

    val isSamsungDevice: Boolean
        get() = manufacturer.equals("samsung", ignoreCase = true)

    val supportsSamsungOngoingActivity: Boolean
        get() = isSamsungDevice

    // Plus de détection de fold par modèle : l'affichage fractionné se décide sur la taille réelle
    // de la fenêtre (AppConfig.splitDisplayEnabled), ce qui couvre tous les pliables et les
    // tablettes, et suit le pliage au lieu de figer un choix au démarrage.
}
