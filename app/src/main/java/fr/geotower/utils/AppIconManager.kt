package fr.geotower.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableIntStateOf

object AppIconManager {
    private const val COMPONENT_DEFAULT = "fr.geotower.MainActivityDefault"
    private const val COMPONENT_ALT = "fr.geotower.MainActivityAlt"
    private const val COMPONENT_ALT2 = "fr.geotower.MainActivityAlt2" // NOUVEAU : Le 3ème alias

    // État réactif global
    var currentIconRes = mutableIntStateOf(0)

    fun setIcon(context: Context, iconIndex: Int) {
        val packageManager = context.packageManager
        val packageName = context.packageName

        // On identifie quel alias doit être allumé
        val componentToEnable = when (iconIndex) {
            1 -> COMPONENT_ALT
            2 -> COMPONENT_ALT2
            else -> COMPONENT_DEFAULT
        }

        // On liste tous les alias existants
        val allComponents = listOf(COMPONENT_DEFAULT, COMPONENT_ALT, COMPONENT_ALT2)

        try {
            // On boucle sur les 3 : on active le bon, on désactive les autres !
            for (component in allComponents) {
                val state = if (component == componentToEnable) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }

                packageManager.setComponentEnabledSetting(
                    ComponentName(packageName, component),
                    state,
                    PackageManager.DONT_KILL_APP
                )
            }

            // MISE À JOUR DE L'ÉTAT IMMÉDIATE
            currentIconRes.intValue = when (iconIndex) {
                1 -> fr.geotower.R.mipmap.ic_launcher_georadio
                2 -> fr.geotower.R.mipmap.ic_launcher_funny // Le nouveau logo !
                else -> fr.geotower.R.mipmap.ic_launcher_geotower
            }

        } catch (e: Exception) {
            e.printStackTrace()
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
}