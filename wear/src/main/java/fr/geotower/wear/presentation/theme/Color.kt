package fr.geotower.wear.presentation.theme

import androidx.compose.ui.graphics.Color

val WearBackground = Color(0xFF000000)
val WearSurface = Color(0xFF1C1C1E)
val WearPrimary = Color(0xFF4C8DFF)
val WearOnPrimary = Color(0xFFFFFFFF)
val WearSecondary = Color(0xFF30D158)
val WearTextPrimary = Color(0xFFFFFFFF)
val WearTextSecondary = Color(0xFF8E8E93)

// Couleurs des opérateurs télécom
val ColorOrange = Color(0xFFFF7900)
val ColorSfr = Color(0xFFE2001A)
val ColorBouygues = Color(0xFF009FE3)
val ColorFree = Color(0xFF757575)
val ColorUnknown = Color(0xFF8E8E93)

fun getOperatorColor(operatorName: String): Color {
    val upper = operatorName.uppercase()
    return when {
        upper.contains("ORANGE") -> ColorOrange
        upper.contains("SFR") -> ColorSfr
        upper.contains("BOUYGUES") -> ColorBouygues
        upper.contains("FREE") -> ColorFree
        else -> ColorUnknown
    }
}
