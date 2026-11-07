package fr.geotower.wear.presentation.theme

import androidx.compose.ui.graphics.Color

val WearBackground = Color(0xFF000000)
val WearSurface = Color(0xFF1C1C1E)
val WearPrimary = Color(0xFF4C8DFF)
val WearOnPrimary = Color(0xFFFFFFFF)
val WearSecondary = Color(0xFF30D158)
val WearTextPrimary = Color(0xFFFFFFFF)
val WearTextSecondary = Color(0xFF8E8E93)

// Couleurs des 20 opérateurs télécom officiels GeoTower (Métropole + Outre-mer)
val ColorOrange = Color(0xFFFF7900)
val ColorBouygues = Color(0xFF009FE3)
val ColorSfr = Color(0xFFE2001A)
val ColorFree = Color(0xFF757575)
val ColorDigicel = Color(0xFFF4DA95)
val ColorFreeCaraibe = Color(0xFF757575)
val ColorOutremerTelecom = Color(0xFFF3A6C8)
val ColorUtsCaraibe = Color(0xFFF09B49)
val ColorDauphinTelecom = Color(0xFF51B2F8)
val ColorSrr = Color(0xFFE2001A)
val ColorTelcoOi = Color(0xFFC45A9E)
val ColorZeop = Color(0xFFF9C846)
val ColorMaoreMobile = Color(0xFF2FBF5B)
val ColorSpmTelecom = Color(0xFF875CEE)
val ColorGlobaltel = Color(0xFF59C28F)
val ColorOptNc = Color(0xFF005BAC)
val ColorOnati = Color(0xFF00A3E0)
val ColorPmtVodafone = Color(0xFFE60000)
val ColorViti = Color(0xFF005B2F)
val ColorSpt = Color(0xFF00806A)
val ColorUnknown = Color(0xFF8E8E93)

fun getOperatorColor(operatorName: String): Color {
    val upper = operatorName.uppercase()
    return when {
        upper.contains("FREE CARAIB") || upper.contains("FREE CARAÏB") -> ColorFreeCaraibe
        upper.contains("ORANGE") -> ColorOrange
        upper.contains("BOUYGUES") || upper.contains("BYTEL") -> ColorBouygues
        upper.contains("SRR") -> ColorSrr
        upper.contains("SFR") -> ColorSfr
        upper.contains("FREE") -> ColorFree
        upper.contains("DIGICEL") -> ColorDigicel
        upper.contains("OUTREMER") || upper.contains("OMT") -> ColorOutremerTelecom
        upper.contains("UTS") -> ColorUtsCaraibe
        upper.contains("DAUPHIN") -> ColorDauphinTelecom
        upper.contains("TELCO") || upper.contains("ONLY") -> ColorTelcoOi
        upper.contains("ZEOP") -> ColorZeop
        upper.contains("MAORE") || upper.contains("MAORÉ") -> ColorMaoreMobile
        upper.contains("SPM") -> ColorSpmTelecom
        upper.contains("GLOBALTEL") -> ColorGlobaltel
        upper.contains("OPT") || upper.contains("CALEDONIE") || upper.contains("CALÉDONIE") -> ColorOptNc
        upper.contains("ONATI") || upper.contains("VINI") -> ColorOnati
        upper.contains("VODAFONE") || upper.contains("PMT") -> ColorPmtVodafone
        upper.contains("VITI") || upper.contains("ORA") -> ColorViti
        upper.contains("SPT") -> ColorSpt
        else -> ColorUnknown
    }
}

/**
 * Nom court compact pour les puces et badges de la montre.
 */
fun getOperatorShortName(operatorName: String): String {
    val upper = operatorName.uppercase()
    return when {
        upper.contains("FREE CARAIB") || upper.contains("FREE CARAÏB") -> "FREE CAR."
        upper.contains("ORANGE") -> "ORANGE"
        upper.contains("BOUYGUES") || upper.contains("BYTEL") -> "BOUYGUES"
        upper.contains("SRR") -> "SRR"
        upper.contains("SFR") -> "SFR"
        upper.contains("FREE") -> "FREE"
        upper.contains("DIGICEL") -> "DIGICEL"
        upper.contains("OUTREMER") || upper.contains("OMT") -> "OUTREMER"
        upper.contains("UTS") -> "UTS"
        upper.contains("DAUPHIN") -> "DAUPHIN"
        upper.contains("TELCO") || upper.contains("ONLY") -> "TELCO OI"
        upper.contains("ZEOP") -> "ZEOP"
        upper.contains("MAORE") || upper.contains("MAORÉ") -> "MAORÉ"
        upper.contains("SPM") -> "SPM"
        upper.contains("GLOBALTEL") -> "GLOBALTEL"
        upper.contains("OPT") || upper.contains("CALEDONIE") || upper.contains("CALÉDONIE") -> "OPT NC"
        upper.contains("ONATI") || upper.contains("VINI") -> "ONATi"
        upper.contains("VODAFONE") || upper.contains("PMT") -> "VODAFONE"
        upper.contains("VITI") || upper.contains("ORA") -> "ORA"
        upper.contains("SPT") -> "SPT"
        else -> operatorName.trim().take(8).uppercase()
    }
}
