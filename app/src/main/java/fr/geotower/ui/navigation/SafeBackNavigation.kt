package fr.geotower.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.navigation.NavController

@Stable
class SafeBackNavigation internal constructor(
    val isLocked: Boolean,
    val navigateBack: () -> Unit
)

@Composable
fun rememberSafeBackNavigation(
    navController: NavController,
    fallbackRoute: String? = "home"
): SafeBackNavigation {
    var isLocked by remember { mutableStateOf(false) }
    val latestFallbackRoute by rememberUpdatedState(fallbackRoute)

    val navigateBack = remember(navController) {
        {
            if (!isLocked) {
                isLocked = true
                val didNavigate = runCatching {
                    if (!navController.popBackStack()) {
                        val fallback = latestFallbackRoute
                        if (fallback != null) {
                            navController.navigate(fallback) {
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
