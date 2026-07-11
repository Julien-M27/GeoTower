package fr.geotower.ui.components

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import fr.geotower.data.config.RemoteFeatureFlags

/**
 * Anti-capture d'écran pilotable à distance via `features.json` (section `secureScreens`).
 *
 * Déposé en une ligne au tout début du corps d'un écran, cet effet pose le flag
 * [WindowManager.LayoutParams.FLAG_SECURE] sur la fenêtre de l'Activity tant que l'écran est affiché
 * ET que le serveur a activé la clé correspondante. FLAG_SECURE bloque les captures d'écran,
 * l'enregistrement vidéo et la recopie/cast, et masque l'aperçu de l'app dans les applications récentes.
 *
 * Défaut = non sécurisé (captures autorisées) : tant que le flag serveur n'est pas à `true`
 * (ou hors-ligne / avant le premier fetch), rien n'est bloqué. Voir [RemoteFeatureFlags.SecureScreens]
 * pour les identifiants d'écran, et [RemoteFeatureFlags.isScreenSecure].
 *
 * Le flag est global à la fenêtre : un compteur de références garantit qu'il n'est retiré que lorsque
 * plus aucun écran sécurisé n'est affiché — robuste aux transitions où deux écrans sécurisés se
 * recouvrent brièvement, et à un changement de valeur du flag en cours d'affichage (le `DisposableEffect`
 * est réévalué quand `secure` change).
 *
 * @param screenId identifiant d'écran, à prendre dans [RemoteFeatureFlags.SecureScreens].
 */
@Composable
fun SecureScreenEffect(screenId: String) {
    val activity = LocalActivity.current
    val config by RemoteFeatureFlags.config
    val secure = activity != null && config.isScreenSecure(screenId)

    DisposableEffect(activity, secure) {
        val window = activity?.window
        if (secure && window != null) {
            secureWindowRefCount++
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (secure && window != null) {
                secureWindowRefCount--
                if (secureWindowRefCount <= 0) {
                    secureWindowRefCount = 0
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
    }
}

/**
 * Nombre d'écrans sécurisés actuellement affichés. Accédé uniquement sur le thread principal
 * (depuis un `DisposableEffect`), donc pas de synchronisation nécessaire.
 */
private var secureWindowRefCount = 0
