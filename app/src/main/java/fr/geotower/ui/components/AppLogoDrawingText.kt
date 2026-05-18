package fr.geotower.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.geotower.R
import fr.geotower.utils.AppLogoDrawingResources

@Composable
fun appLogoDrawingChoiceName(choice: String): String {
    val normalized = AppLogoDrawingResources.normalize(choice)
    if (normalized == AppLogoDrawingResources.AUTO) {
        return stringResource(R.string.appstrings_logo_drawing_auto)
    }

    val family = appLogoDrawingFamilyName(normalized.substringBefore(":"))
    val style = appLogoDrawingStyleName(normalized.substringAfter(":"))
    return "$family · $style"
}

@Composable
fun appLogoDrawingFamilyName(family: String): String = when (family) {
    "georadio" -> "GeoRadio"
    "fun" -> "Fun"
    else -> stringResource(R.string.brand_geotower)
}

@Composable
fun appLogoDrawingChoiceDescription(choice: String): String {
    val normalized = AppLogoDrawingResources.normalize(choice)
    return if (normalized == AppLogoDrawingResources.AUTO) {
        stringResource(R.string.appstrings_logo_drawing_auto_desc)
    } else {
        stringResource(R.string.appstrings_app_logo_drawing_subtitle)
    }
}

@Composable
fun appLogoDrawingStyleName(style: String): String = when (style) {
    "color_on_light" -> stringResource(R.string.appstrings_logo_drawing_color_on_light)
    "mono_light" -> stringResource(R.string.appstrings_logo_drawing_mono_light)
    "mono_dark" -> stringResource(R.string.appstrings_logo_drawing_mono_dark)
    "mono_muted" -> stringResource(R.string.appstrings_logo_drawing_mono_muted)
    else -> stringResource(R.string.appstrings_logo_drawing_color_on_dark)
}
