package fr.geotower.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import fr.geotower.utils.AppConfig

// Thèmes de base (utilisés si le téléphone est vieux et n'a pas les couleurs dynamiques)
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun GeoRadioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val isOled = AppConfig.isOledMode.value
    val themeMode = AppConfig.themeMode.intValue // 0=Auto, 1=Clair, 2=Sombre

    // 1. On décide si on est en mode sombre
    val effectiveDarkTheme = when (themeMode) {
        1 -> false // Force Clair
        2 -> true  // Force Sombre
        else -> darkTheme // Auto
    }

    val context = LocalContext.current

    // 2. On choisit la palette de couleurs
    val baseScheme = when {
        // Si Android 12+ : On prend les couleurs du système
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (effectiveDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Sinon : Thèmes par défaut
        effectiveDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // 3. LA CORRECTION OLED EST ICI :
    // Si le mode OLED est actif ET qu'on est en thème sombre...
    // On prend la palette choisie juste avant, et on remplace JUSTE le fond par du Noir Pur.
    val finalScheme = if (isOled && effectiveDarkTheme) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            // On s'assure que les surfaces surélevées (cartes) restent noires ou très sombres
            surfaceContainer = Color.Black,
            surfaceContainerHigh = Color.Black
        )
    } else {
        baseScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // La barre d'état prend la couleur du fond (donc Noir si OLED)
            window.statusBarColor = finalScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !effectiveDarkTheme
        }
    }

    MaterialTheme(
        colorScheme = finalScheme,
        typography = Typography,
        content = content
    )
}