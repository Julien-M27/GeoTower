package fr.geotower.ui.components

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * Keeps the navigation bar readable inside Material 3 modal sheets.
 *
 * Android 7-7.1 cannot draw dark navigation-bar icons. A light sheet therefore needs a dark
 * navigation-bar surface on those versions; newer versions can keep the sheet surface and switch
 * to dark icons instead.
 */
@Composable
fun ModalSheetSystemBars() {
    val view = LocalView.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val isLightSurface = surfaceColor.luminance() > 0.5f
    val canUseDarkIcons = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    val navigationBarColor = if (canUseDarkIcons || !isLightSurface) {
        surfaceColor
    } else {
        MaterialTheme.colorScheme.inverseSurface
    }
    val useDarkIcons = canUseDarkIcons && isLightSurface

    DisposableEffect(view, navigationBarColor, useDarkIcons) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            val previousColor = window.navigationBarColor
            val previousUseDarkIcons = controller.isAppearanceLightNavigationBars
            window.navigationBarColor = navigationBarColor.toArgb()
            controller.isAppearanceLightNavigationBars = useDarkIcons

            onDispose {
                window.navigationBarColor = previousColor
                controller.isAppearanceLightNavigationBars = previousUseDarkIcons
            }
        }
    }
}
