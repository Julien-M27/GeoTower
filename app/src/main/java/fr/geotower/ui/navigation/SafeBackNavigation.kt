package fr.geotower.ui.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import fr.geotower.utils.AppConfig

/** Repli « racine de l'app » : à résoudre via [AppConfig.homeRoute], jamais en dur. */
const val ROOT_FALLBACK_ROUTE: String = "home"

/**
 * Route sans ses arguments : la carte est déclarée « map?photoDraftId={photoDraftId} », il faut
 * donc comparer les bases pour savoir si on est déjà sur la racine.
 */
private fun String.routeBase(): String = substringBefore('?')

@Stable
class SafeBackNavigation internal constructor(
    val isLocked: Boolean,
    val navigateBack: () -> Unit
)

@Composable
fun rememberSafeBackNavigation(
    navController: NavController,
    fallbackRoute: String? = ROOT_FALLBACK_ROUTE
): SafeBackNavigation {
    var isLocked by remember { mutableStateOf(false) }
    // Le mode simplifié n'a pas d'accueil : la carte en tient lieu. Un repli codé en dur sur
    // « home » y ressuscitait la page d'accueil dès que la pile était vide (retour depuis la
    // carte racine, par exemple).
    val wantsRootFallback = fallbackRoute == ROOT_FALLBACK_ROUTE
    val resolvedFallback = if (wantsRootFallback) AppConfig.homeRoute() else fallbackRoute
    val latestFallbackRoute by rememberUpdatedState(resolvedFallback)
    val latestWantsRootFallback by rememberUpdatedState(wantsRootFallback)
    // NB : on passe par LocalActivity et non par un cast de LocalContext (le GeoTowerLocaleProvider
    // remplace LocalContext par un createConfigurationContext() qui ne contient plus l'Activity).
    val latestActivity by rememberUpdatedState(LocalActivity.current)

    val navigateBack = remember(navController) {
        {
            if (!isLocked) {
                isLocked = true
                // Relevé AVANT le pop : une fois la pile vidée, currentDestination est nul.
                val routeBeforePop = navController.currentDestination?.route
                val didNavigate = runCatching {
                    if (!navController.popBackStack()) {
                        val fallback = latestFallbackRoute
                        val wasAtRoot = latestWantsRootFallback &&
                            fallback != null &&
                            routeBeforePop?.routeBase() == fallback.routeBase()
                        when {
                            // On était déjà sur la racine de l'app : le retour doit quitter,
                            // exactement comme le retour système depuis l'accueil.
                            wasAtRoot -> latestActivity?.finish()
                            fallback != null -> navController.navigate(fallback) {
                                popUpTo(0)
                                launchSingleTop = true
                            }
                        }
                    }
                }.isSuccess

                if (!didNavigate) {
                    isLocked = false
                }
            }
        }
    }

    return SafeBackNavigation(
        isLocked = isLocked,
        navigateBack = navigateBack
    )
}
