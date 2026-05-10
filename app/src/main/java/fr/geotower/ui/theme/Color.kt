package fr.geotower.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import fr.geotower.utils.OperatorColors

// --- Palettes Material 3 de l'application ---
// Ces couleurs pilotent uniquement le thème global de l'interface.
enum class AppColorPalette(val storageKey: String) {
    Dynamic("dynamic"),
    Baseline("baseline"),
    Red("red"),
    Green("green"),
    Blue("blue"),
    Cyan("cyan"),
    Teal("teal"),
    Indigo("indigo"),
    Rose("rose"),
    Amber("amber"),
    Graphite("graphite");

    companion object {
        fun fromKey(key: String?): AppColorPalette {
            if (key == "canada") return Red
            return values().firstOrNull { it.storageKey == key } ?: Dynamic
        }
    }
}

val AppColorPaletteOptions = listOf(
    AppColorPalette.Dynamic,
    AppColorPalette.Baseline,
    AppColorPalette.Red,
    AppColorPalette.Green,
    AppColorPalette.Blue,
    AppColorPalette.Cyan,
    AppColorPalette.Teal,
    AppColorPalette.Indigo,
    AppColorPalette.Rose,
    AppColorPalette.Amber,
    AppColorPalette.Graphite
)

fun appPalettePreviewColors(palette: AppColorPalette): List<Color> = when (palette) {
    AppColorPalette.Dynamic -> listOf(Color(0xFF5267A3), Color(0xFF586275), Color(0xFF7B5D79), Color(0xFFE2E2EC))
    AppColorPalette.Baseline -> listOf(Color(0xFF6750A4), Color(0xFF57438B), Color(0xFF3E2B66), Color(0xFFE6E0F8))
    AppColorPalette.Red -> listOf(Color(0xFFBA1A1A), Color(0xFF9C1B2B), Color(0xFF7A1020), Color(0xFFFFDAD6))
    AppColorPalette.Green -> listOf(Color(0xFF006E1C), Color(0xFF42683F), Color(0xFF1F4F26), Color(0xFFD8EFD1))
    AppColorPalette.Blue -> listOf(Color(0xFF006A9E), Color(0xFF00577D), Color(0xFF004B6A), Color(0xFFC8E6FF))
    AppColorPalette.Cyan -> listOf(Color(0xFF006A6A), Color(0xFF4A6363), Color(0xFF005050), Color(0xFFCFF1F1))
    AppColorPalette.Teal -> listOf(Color(0xFF006B5F), Color(0xFF00534A), Color(0xFF003D37), Color(0xFFCEEDE6))
    AppColorPalette.Indigo -> listOf(Color(0xFF4D5BB7), Color(0xFF555E91), Color(0xFF303B91), Color(0xFFE0E0FF))
    AppColorPalette.Rose -> listOf(Color(0xFF984061), Color(0xFF7B2949), Color(0xFF5E1533), Color(0xFFFFD8E5))
    AppColorPalette.Amber -> listOf(Color(0xFF7C5900), Color(0xFF624500), Color(0xFF4A3300), Color(0xFFF4E1BC))
    AppColorPalette.Graphite -> listOf(Color(0xFF5E6068), Color(0xFF575C63), Color(0xFF3F424A), Color(0xFFE3E4EC))
}

fun appStaticColorScheme(palette: AppColorPalette, darkTheme: Boolean): ColorScheme {
    // Le mode dynamique est résolu dans le thème avec les APIs Android 12+.
    // Ici, il retombe sur Baseline pour garder un rendu stable sur les anciens appareils.
    val resolvedPalette = if (palette == AppColorPalette.Dynamic) AppColorPalette.Baseline else palette
    return when (resolvedPalette) {
        AppColorPalette.Dynamic,
        AppColorPalette.Baseline -> if (darkTheme) BaselineDarkColorScheme else BaselineLightColorScheme
        AppColorPalette.Red -> if (darkTheme) RedDarkColorScheme else RedLightColorScheme
        AppColorPalette.Green -> if (darkTheme) GreenDarkColorScheme else GreenLightColorScheme
        AppColorPalette.Blue -> if (darkTheme) BlueDarkColorScheme else BlueLightColorScheme
        AppColorPalette.Cyan -> if (darkTheme) CyanDarkColorScheme else CyanLightColorScheme
        AppColorPalette.Teal -> if (darkTheme) TealDarkColorScheme else TealLightColorScheme
        AppColorPalette.Indigo -> if (darkTheme) IndigoDarkColorScheme else IndigoLightColorScheme
        AppColorPalette.Rose -> if (darkTheme) RoseDarkColorScheme else RoseLightColorScheme
        AppColorPalette.Amber -> if (darkTheme) AmberDarkColorScheme else AmberLightColorScheme
        AppColorPalette.Graphite -> if (darkTheme) GraphiteDarkColorScheme else GraphiteLightColorScheme
    }
}

private val BaselineLightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFFD0BCFF)
)

private val BaselineDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF313033),
    inversePrimary = Color(0xFF6750A4)
)

private val RedLightColorScheme = lightColorScheme(
    primary = Color(0xFFBA1A1A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF775652),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD6),
    onSecondaryContainer = Color(0xFF2C1512),
    tertiary = Color(0xFF745B2E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF281900),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF201A19),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF201A19),
    surfaceVariant = Color(0xFFF5DDDA),
    onSurfaceVariant = Color(0xFF534341),
    outline = Color(0xFF857370),
    outlineVariant = Color(0xFFD8C2BE),
    inverseSurface = Color(0xFF362F2E),
    inverseOnSurface = Color(0xFFFBEEEC),
    inversePrimary = Color(0xFFFFB4AB)
)

private val RedDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFE7BDB7),
    onSecondary = Color(0xFF442925),
    secondaryContainer = Color(0xFF5D3F3B),
    onSecondaryContainer = Color(0xFFFFDAD6),
    tertiary = Color(0xFFE3C28C),
    onTertiary = Color(0xFF412D04),
    tertiaryContainer = Color(0xFF5A4319),
    onTertiaryContainer = Color(0xFFFFDEA6),
    background = Color(0xFF201A19),
    onBackground = Color(0xFFEDE0DE),
    surface = Color(0xFF201A19),
    onSurface = Color(0xFFEDE0DE),
    surfaceVariant = Color(0xFF534341),
    onSurfaceVariant = Color(0xFFD8C2BE),
    outline = Color(0xFFA08C89),
    outlineVariant = Color(0xFF534341),
    inverseSurface = Color(0xFFEDE0DE),
    inverseOnSurface = Color(0xFF362F2E),
    inversePrimary = Color(0xFFBA1A1A)
)

private val BlueLightColorScheme = lightColorScheme(
    primary = Color(0xFF006A9E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCBE6FF),
    onPrimaryContainer = Color(0xFF001E31),
    secondary = Color(0xFF50606E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E4F5),
    onSecondaryContainer = Color(0xFF0C1D29),
    tertiary = Color(0xFF66587B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDDCFF),
    onTertiaryContainer = Color(0xFF211634),
    background = Color(0xFFFCFCFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFCFCFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDEE3EB),
    onSurfaceVariant = Color(0xFF42474E),
    outline = Color(0xFF72787E),
    outlineVariant = Color(0xFFC2C7CE),
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF0F0F4),
    inversePrimary = Color(0xFF8DCDFF)
)

private val BlueDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8DCDFF),
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF004B70),
    onPrimaryContainer = Color(0xFFCBE6FF),
    secondary = Color(0xFFB7C8D9),
    onSecondary = Color(0xFF22323F),
    secondaryContainer = Color(0xFF394956),
    onSecondaryContainer = Color(0xFFD3E4F5),
    tertiary = Color(0xFFD3BFE8),
    onTertiary = Color(0xFF372A4A),
    tertiaryContainer = Color(0xFF4E4162),
    onTertiaryContainer = Color(0xFFEDDCFF),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CE),
    outline = Color(0xFF8C9198),
    outlineVariant = Color(0xFF42474E),
    inverseSurface = Color(0xFFE2E2E6),
    inverseOnSurface = Color(0xFF2F3033),
    inversePrimary = Color(0xFF006A9E)
)

private val TealLightColorScheme = lightColorScheme(
    primary = Color(0xFF006B5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF76F8E5),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E1),
    onSecondaryContainer = Color(0xFF06201B),
    tertiary = Color(0xFF456179),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCBE6FF),
    onTertiaryContainer = Color(0xFF001E31),
    background = Color(0xFFFAFDFA),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFFAFDFA),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDAE5E1),
    onSurfaceVariant = Color(0xFF3F4946),
    outline = Color(0xFF6F7976),
    outlineVariant = Color(0xFFBEC9C5),
    inverseSurface = Color(0xFF2E3130),
    inverseOnSurface = Color(0xFFEFF1EF),
    inversePrimary = Color(0xFF55DBC9)
)

private val TealDarkColorScheme = darkColorScheme(
    primary = Color(0xFF55DBC9),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFF76F8E5),
    secondary = Color(0xFFB1CCC5),
    onSecondary = Color(0xFF1C3530),
    secondaryContainer = Color(0xFF334B46),
    onSecondaryContainer = Color(0xFFCCE8E1),
    tertiary = Color(0xFFADCAE6),
    onTertiary = Color(0xFF153349),
    tertiaryContainer = Color(0xFF2D4960),
    onTertiaryContainer = Color(0xFFCBE6FF),
    background = Color(0xFF191C1B),
    onBackground = Color(0xFFE1E3E1),
    surface = Color(0xFF191C1B),
    onSurface = Color(0xFFE1E3E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C5),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4946),
    inverseSurface = Color(0xFFE1E3E1),
    inverseOnSurface = Color(0xFF2E3130),
    inversePrimary = Color(0xFF006B5F)
)

private val RoseLightColorScheme = lightColorScheme(
    primary = Color(0xFF984061),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD8E5),
    onPrimaryContainer = Color(0xFF3E001D),
    secondary = Color(0xFF74565F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF2B151C),
    tertiary = Color(0xFF7E5530),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC2),
    onTertiaryContainer = Color(0xFF2F1500),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF201A1B),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF201A1B),
    surfaceVariant = Color(0xFFF2DDE2),
    onSurfaceVariant = Color(0xFF514348),
    outline = Color(0xFF837378),
    outlineVariant = Color(0xFFD5C2C7),
    inverseSurface = Color(0xFF352F30),
    inverseOnSurface = Color(0xFFFAEEEF),
    inversePrimary = Color(0xFFFFB0CB)
)

private val RoseDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB0CB),
    onPrimary = Color(0xFF5E1132),
    primaryContainer = Color(0xFF7B2949),
    onPrimaryContainer = Color(0xFFFFD8E5),
    secondary = Color(0xFFE2BDC6),
    onSecondary = Color(0xFF422931),
    secondaryContainer = Color(0xFF5A3F47),
    onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFFF0BD93),
    onTertiary = Color(0xFF48290A),
    tertiaryContainer = Color(0xFF633F1F),
    onTertiaryContainer = Color(0xFFFFDCC2),
    background = Color(0xFF201A1B),
    onBackground = Color(0xFFEBE0E1),
    surface = Color(0xFF201A1B),
    onSurface = Color(0xFFEBE0E1),
    surfaceVariant = Color(0xFF514348),
    onSurfaceVariant = Color(0xFFD5C2C7),
    outline = Color(0xFF9E8C91),
    outlineVariant = Color(0xFF514348),
    inverseSurface = Color(0xFFEBE0E1),
    inverseOnSurface = Color(0xFF352F30),
    inversePrimary = Color(0xFF984061)
)

private val AmberLightColorScheme = lightColorScheme(
    primary = Color(0xFF7C5900),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDEA6),
    onPrimaryContainer = Color(0xFF271900),
    secondary = Color(0xFF6D5C3F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF7DFBB),
    onSecondaryContainer = Color(0xFF261A04),
    tertiary = Color(0xFF4D6544),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCFEBC1),
    onTertiaryContainer = Color(0xFF0B2007),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1E1B16),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFEDE1CF),
    onSurfaceVariant = Color(0xFF4D4639),
    outline = Color(0xFF7F7667),
    outlineVariant = Color(0xFFD1C5B4),
    inverseSurface = Color(0xFF33302A),
    inverseOnSurface = Color(0xFFF7EFE7),
    inversePrimary = Color(0xFFFABD2F)
)

private val AmberDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFABD2F),
    onPrimary = Color(0xFF412D00),
    primaryContainer = Color(0xFF5E4200),
    onPrimaryContainer = Color(0xFFFFDEA6),
    secondary = Color(0xFFD9C3A0),
    onSecondary = Color(0xFF3C2F15),
    secondaryContainer = Color(0xFF54452A),
    onSecondaryContainer = Color(0xFFF7DFBB),
    tertiary = Color(0xFFB3CEA6),
    onTertiary = Color(0xFF203619),
    tertiaryContainer = Color(0xFF364D2E),
    onTertiaryContainer = Color(0xFFCFEBC1),
    background = Color(0xFF1E1B16),
    onBackground = Color(0xFFE9E1D9),
    surface = Color(0xFF1E1B16),
    onSurface = Color(0xFFE9E1D9),
    surfaceVariant = Color(0xFF4D4639),
    onSurfaceVariant = Color(0xFFD1C5B4),
    outline = Color(0xFF9A8F80),
    outlineVariant = Color(0xFF4D4639),
    inverseSurface = Color(0xFFE9E1D9),
    inverseOnSurface = Color(0xFF33302A),
    inversePrimary = Color(0xFF7C5900)
)

private val GreenLightColorScheme = lightColorScheme(
    primary = Color(0xFF006E1C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF94F990),
    onPrimaryContainer = Color(0xFF002204),
    secondary = Color(0xFF52634F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E8CF),
    onSecondaryContainer = Color(0xFF111F0F),
    tertiary = Color(0xFF38656A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBCEBF0),
    onTertiaryContainer = Color(0xFF002023),
    background = Color(0xFFFCFDF6),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFFCFDF6),
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFDEE5D8),
    onSurfaceVariant = Color(0xFF424940),
    outline = Color(0xFF72796F),
    outlineVariant = Color(0xFFC2C9BD),
    inverseSurface = Color(0xFF2F312D),
    inverseOnSurface = Color(0xFFF0F1EB),
    inversePrimary = Color(0xFF78DC77)
)

private val GreenDarkColorScheme = darkColorScheme(
    primary = Color(0xFF78DC77),
    onPrimary = Color(0xFF00390A),
    primaryContainer = Color(0xFF005313),
    onPrimaryContainer = Color(0xFF94F990),
    secondary = Color(0xFFB9CCB3),
    onSecondary = Color(0xFF253423),
    secondaryContainer = Color(0xFF3B4B38),
    onSecondaryContainer = Color(0xFFD5E8CF),
    tertiary = Color(0xFFA0CFD4),
    onTertiary = Color(0xFF00363A),
    tertiaryContainer = Color(0xFF1E4D52),
    onTertiaryContainer = Color(0xFFBCEBF0),
    background = Color(0xFF1A1C19),
    onBackground = Color(0xFFE2E3DD),
    surface = Color(0xFF1A1C19),
    onSurface = Color(0xFFE2E3DD),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BD),
    outline = Color(0xFF8C9388),
    outlineVariant = Color(0xFF424940),
    inverseSurface = Color(0xFFE2E3DD),
    inverseOnSurface = Color(0xFF2F312D),
    inversePrimary = Color(0xFF006E1C)
)

private val CyanLightColorScheme = lightColorScheme(
    primary = Color(0xFF006A6A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF1F1),
    onPrimaryContainer = Color(0xFF002020),
    secondary = Color(0xFF4A6363),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E8),
    onSecondaryContainer = Color(0xFF052020),
    tertiary = Color(0xFF4C5F7D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD4E3FF),
    onTertiaryContainer = Color(0xFF061C36),
    background = Color(0xFFFAFDFC),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFFAFDFC),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFDAE5E4),
    onSurfaceVariant = Color(0xFF3F4948),
    outline = Color(0xFF6F7978),
    outlineVariant = Color(0xFFBEC9C8),
    inverseSurface = Color(0xFF2E3131),
    inverseOnSurface = Color(0xFFEFF1F0),
    inversePrimary = Color(0xFF80D5D5)
)

private val CyanDarkColorScheme = darkColorScheme(
    primary = Color(0xFF80D5D5),
    onPrimary = Color(0xFF003737),
    primaryContainer = Color(0xFF005050),
    onPrimaryContainer = Color(0xFF9CF1F1),
    secondary = Color(0xFFB0CCCC),
    onSecondary = Color(0xFF1B3535),
    secondaryContainer = Color(0xFF334B4B),
    onSecondaryContainer = Color(0xFFCCE8E8),
    tertiary = Color(0xFFB4C7E9),
    onTertiary = Color(0xFF1D314D),
    tertiaryContainer = Color(0xFF344764),
    onTertiaryContainer = Color(0xFFD4E3FF),
    background = Color(0xFF191C1C),
    onBackground = Color(0xFFE1E3E2),
    surface = Color(0xFF191C1C),
    onSurface = Color(0xFFE1E3E2),
    surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFBEC9C8),
    outline = Color(0xFF899392),
    outlineVariant = Color(0xFF3F4948),
    inverseSurface = Color(0xFFE1E3E2),
    inverseOnSurface = Color(0xFF2E3131),
    inversePrimary = Color(0xFF006A6A)
)

private val IndigoLightColorScheme = lightColorScheme(
    primary = Color(0xFF4D5BB7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = Color(0xFF050B55),
    secondary = Color(0xFF5B5D72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E1F9),
    onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFF77536D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD7F0),
    onTertiaryContainer = Color(0xFF2D1228),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFF47464F),
    outline = Color(0xFF787680),
    outlineVariant = Color(0xFFC8C5D0),
    inverseSurface = Color(0xFF303036),
    inverseOnSurface = Color(0xFFF2F0F7),
    inversePrimary = Color(0xFFBEC2FF)
)

private val IndigoDarkColorScheme = darkColorScheme(
    primary = Color(0xFFBEC2FF),
    onPrimary = Color(0xFF1B287A),
    primaryContainer = Color(0xFF35429E),
    onPrimaryContainer = Color(0xFFE0E0FF),
    secondary = Color(0xFFC4C5DD),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF444559),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFFE6BAD7),
    onTertiary = Color(0xFF45263E),
    tertiaryContainer = Color(0xFF5D3C55),
    onTertiaryContainer = Color(0xFFFFD7F0),
    background = Color(0xFF1B1B21),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF1B1B21),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC8C5D0),
    outline = Color(0xFF928F9A),
    outlineVariant = Color(0xFF47464F),
    inverseSurface = Color(0xFFE4E1E9),
    inverseOnSurface = Color(0xFF303036),
    inversePrimary = Color(0xFF4D5BB7)
)

private val GraphiteLightColorScheme = lightColorScheme(
    primary = Color(0xFF5E6068),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E4EC),
    onPrimaryContainer = Color(0xFF1B1B21),
    secondary = Color(0xFF5F5E68),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E2EC),
    onSecondaryContainer = Color(0xFF1C1B22),
    tertiary = Color(0xFF655C63),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFECDDE7),
    onTertiaryContainer = Color(0xFF211A20),
    background = Color(0xFFFCFBFF),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFCFBFF),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE4E2EA),
    onSurfaceVariant = Color(0xFF47464D),
    outline = Color(0xFF78767E),
    outlineVariant = Color(0xFFC8C5CE),
    inverseSurface = Color(0xFF303034),
    inverseOnSurface = Color(0xFFF3F0F5),
    inversePrimary = Color(0xFFC7C6D0)
)

private val GraphiteDarkColorScheme = darkColorScheme(
    primary = Color(0xFFC7C6D0),
    onPrimary = Color(0xFF303038),
    primaryContainer = Color(0xFF46464F),
    onPrimaryContainer = Color(0xFFE3E4EC),
    secondary = Color(0xFFC8C5D0),
    onSecondary = Color(0xFF303038),
    secondaryContainer = Color(0xFF47464F),
    onSecondaryContainer = Color(0xFFE5E2EC),
    tertiary = Color(0xFFD0C1CB),
    onTertiary = Color(0xFF362E35),
    tertiaryContainer = Color(0xFF4D444B),
    onTertiaryContainer = Color(0xFFECDDE7),
    background = Color(0xFF1B1B1F),
    onBackground = Color(0xFFE5E1E6),
    surface = Color(0xFF1B1B1F),
    onSurface = Color(0xFFE5E1E6),
    surfaceVariant = Color(0xFF47464D),
    onSurfaceVariant = Color(0xFFC8C5CE),
    outline = Color(0xFF928F98),
    outlineVariant = Color(0xFF47464D),
    inverseSurface = Color(0xFFE5E1E6),
    inverseOnSurface = Color(0xFF303034),
    inversePrimary = Color(0xFF5E6068)
)

// --- Couleurs de base historiques ---
val ColorBlue = Color(0xFF4285F4)
val ColorPurple = Color(0xFF6200EE)
val ColorPink = Color(0xFFE91E63)

// --- Couleurs des operateurs ---
// A garder separe des palettes Material : ces couleurs representent les marques.
val ColorOrange = Color(OperatorColors.ORANGE_ARGB)
val ColorSfr = Color(OperatorColors.SFR_ARGB)
val ColorBouygues = Color(OperatorColors.BOUYGUES_ARGB)
val ColorFree = Color(OperatorColors.FREE_ARGB)
val ColorUnknown = Color(OperatorColors.UNKNOWN_ARGB)

// --- Couleurs des technologies radio ---
val Color5G = Color(0xFF00C853)
val Color4G = Color(0xFFFFD600)
val Color3G = Color(0xFF2962FF)
val Color2G = Color(0xFF455A64)

// --- Couleurs Material Baseline conservees pour compatibilite ---
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650A4)
val PurpleGrey40 = Color(0xFF625B71)
val Pink40 = Color(0xFF7D5260)
