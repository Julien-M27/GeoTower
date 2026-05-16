package fr.geotower.utils

import androidx.annotation.DrawableRes
import fr.geotower.R

object AppLogoDrawingResources {
    const val PREF_KEY = "app_logo_drawing_choice"
    const val AUTO = "auto"

    private const val FAMILY_GEOTOWER = "geotower"
    private const val FAMILY_GEORADIO = "georadio"
    private const val FAMILY_FUN = "fun"

    private const val STYLE_COLOR_ON_DARK = "color_on_dark"
    private const val STYLE_COLOR_ON_LIGHT = "color_on_light"
    private const val STYLE_MONO_LIGHT = "mono_light"
    private const val STYLE_MONO_DARK = "mono_dark"
    private const val STYLE_MONO_MUTED = "mono_muted"

    val families = listOf(FAMILY_GEOTOWER, FAMILY_GEORADIO, FAMILY_FUN)
    val styles = listOf(STYLE_COLOR_ON_DARK, STYLE_COLOR_ON_LIGHT, STYLE_MONO_LIGHT, STYLE_MONO_DARK, STYLE_MONO_MUTED)
    val choices = listOf(AUTO) + families.flatMap { family -> styles.map { style -> "$family:$style" } }

    fun normalize(choice: String?): String {
        return choice?.takeIf { it in choices } ?: AUTO
    }

    fun family(choice: String): String? {
        return normalize(choice).takeIf { it != AUTO }?.substringBefore(":")
    }

    fun style(choice: String): String? {
        return normalize(choice).takeIf { it != AUTO }?.substringAfter(":")
    }

    fun activeFamily(activeIconRes: Int): String {
        return when (activeIconRes) {
            R.mipmap.ic_launcher_georadio -> FAMILY_GEORADIO
            R.mipmap.ic_launcher_funny -> FAMILY_FUN
            else -> FAMILY_GEOTOWER
        }
    }

    @DrawableRes
    fun resolve(choice: String, activeIconRes: Int, isDark: Boolean): Int {
        val normalized = normalize(choice)
        val resolvedFamily = if (normalized == AUTO) activeFamily(activeIconRes) else normalized.substringBefore(":")
        val resolvedStyle = if (normalized == AUTO) {
            if (isDark) STYLE_COLOR_ON_DARK else STYLE_COLOR_ON_LIGHT
        } else {
            normalized.substringAfter(":")
        }
        return drawableFor(resolvedFamily, resolvedStyle)
    }

    @DrawableRes
    fun drawableFor(family: String, style: String): Int {
        return when (family) {
            FAMILY_GEORADIO -> when (style) {
                STYLE_COLOR_ON_LIGHT -> R.drawable.logo_georadio_color_on_light
                STYLE_MONO_LIGHT -> R.drawable.logo_georadio_mono_light
                STYLE_MONO_DARK -> R.drawable.logo_georadio_mono_dark
                STYLE_MONO_MUTED -> R.drawable.logo_georadio_mono_muted
                else -> R.drawable.logo_georadio_color_on_dark
            }
            FAMILY_FUN -> when (style) {
                STYLE_COLOR_ON_LIGHT -> R.drawable.logo_fun_color_on_light
                STYLE_MONO_LIGHT -> R.drawable.logo_fun_mono_light
                STYLE_MONO_DARK -> R.drawable.logo_fun_mono_dark
                STYLE_MONO_MUTED -> R.drawable.logo_fun_mono_muted
                else -> R.drawable.logo_fun_color_on_dark
            }
            else -> when (style) {
                STYLE_COLOR_ON_LIGHT -> R.drawable.logo_geotower_color_on_light
                STYLE_MONO_LIGHT -> R.drawable.logo_geotower_mono_light
                STYLE_MONO_DARK -> R.drawable.logo_geotower_mono_dark
                STYLE_MONO_MUTED -> R.drawable.logo_geotower_mono_muted
                else -> R.drawable.logo_geotower_color_on_dark
            }
        }
    }
}
