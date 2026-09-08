package fr.geotower.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.mutableIntStateOf

object AppIconManager {
    private const val COMPONENT_DEFAULT = "fr.geotower.MainActivityDefault"
    private const val COMPONENT_ALT = "fr.geotower.MainActivityAlt"
    private const val COMPONENT_ALT2 = "fr.geotower.MainActivityAlt2" // NOUVEAU : Le 3ème alias

    // État réactif global
    var currentIconRes = mutableIntStateOf(0)

    /**
     * Changes the launcher alias without restarting or killing the running activity.
     *
     * Android 13+ can apply all alias changes as one package-manager transaction. Older versions
     * enable the new alias before disabling the old ones, so the launcher never briefly loses its
     * only entry.
     */
    fun setIcon(context: Context, iconIndex: Int) {
        val packageManager = context.packageManager
        val packageName = context.packageName
        val normalizedIndex = iconIndex.coerceIn(0, 2)

        // On identifie quel alias doit être allumé
        val componentToEnable = when (normalizedIndex) {
            1 -> COMPONENT_ALT
            2 -> COMPONENT_ALT2
            else -> COMPONENT_DEFAULT
        }

        // On liste tous les alias existants
        val allComponents = listOf(COMPONENT_DEFAULT, COMPONENT_ALT, COMPONENT_ALT2)

        try {
            val flags = PackageManager.DONT_KILL_APP
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Un seul changement vu par le launcher : pas de phase intermédiaire avec zéro
                // icône active, ni de rafraîchissement trois fois de suite.
                packageManager.setComponentEnabledSettings(
                    allComponents.map { component ->
                        PackageManager.ComponentEnabledSetting(
                            ComponentName(packageName, component),
                            if (component == componentToEnable) {
                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            } else {
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            },
                            flags
                        )
                    }
                )
            } else {
                // Compatibilité Android 7 à 12 : activer avant de désactiver évite de supprimer
                // brièvement l'entrée du launcher pendant le changement.
                packageManager.setComponentEnabledSetting(
                    ComponentName(packageName, componentToEnable),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    flags
                )
                allComponents
                    .filter { it != componentToEnable }
                    .forEach { component ->
                        packageManager.setComponentEnabledSetting(
                            ComponentName(packageName, component),
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            flags
                        )
                    }
                }

            // MISE À JOUR DE L'ÉTAT IMMÉDIATE
            currentIconRes.intValue = when (normalizedIndex) {
                1 -> fr.geotower.R.mipmap.ic_launcher_georadio
                2 -> fr.geotower.R.mipmap.ic_launcher_funny // Le nouveau logo !
                else -> fr.geotower.R.mipmap.ic_launcher_geotower
            }

        } catch (e: Exception) {
            AppLogger.w(TAG, "App icon update failed", e)
        }
    }

    // Fonction qui renvoie 0, 1 ou 2 selon l'alias actuellement activé
    fun getActiveIconIndex(context: Context): Int {
        return try {
            val packageManager = context.packageManager

            val stateAlt = packageManager.getComponentEnabledSetting(ComponentName(context.packageName, COMPONENT_ALT))
            if (stateAlt == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return 1

            val stateAlt2 = packageManager.getComponentEnabledSetting(ComponentName(context.packageName, COMPONENT_ALT2))
            if (stateAlt2 == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return 2

            0 // Si les deux autres sont désactivés, c'est le Default (0)
        } catch (e: Exception) {
            0
        }
    }

    fun getLogoResId(context: Context): Int {
        val resId = when (getActiveIconIndex(context)) {
            1 -> fr.geotower.R.mipmap.ic_launcher_georadio
            2 -> fr.geotower.R.mipmap.ic_launcher_funny // Le nouveau logo !
            else -> fr.geotower.R.mipmap.ic_launcher_geotower
        }

        currentIconRes.intValue = resId
        return resId
    }

    private const val TAG = "GeoTower"
}
