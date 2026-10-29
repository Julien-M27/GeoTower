package fr.geotower.ui.components

import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Configure la fenêtre parente d'un [androidx.compose.ui.window.Dialog] pour qu'elle occupe
 * toute la surface de l'écran en largeur et en hauteur (MATCH_PARENT).
 *
 * Par défaut sous Android et Compose, `usePlatformDefaultWidth = false` force la largeur à
 * MATCH_PARENT mais laisse la hauteur à WRAP_CONTENT, ce qui provoque un centrage vertical
 * avec des bandes visibles en haut et en bas sur les grands écrans (tablettes, pliables) dès que
 * le contenu (ex: photo) ne remplit pas l'écran.
 */
@Composable
fun FullScreenDialogWindow() {
    val view = LocalView.current
    SideEffect {
        findDialogWindow(view)?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
}

private fun findDialogWindow(view: View): Window? {
    var parent = view.parent
    while (parent != null) {
        if (parent is DialogWindowProvider) {
            return parent.window
        }
        parent = parent.parent
    }
    return null
}
