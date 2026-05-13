package fr.geotower.utils

import fr.geotower.R

object OperatorLogos {
    fun drawableRes(raw: String?): Int? {
        return when (OperatorColors.keyFor(raw)) {
            OperatorColors.ORANGE_KEY -> R.drawable.logo_orange
            OperatorColors.BOUYGUES_KEY -> R.drawable.logo_bouygues
            OperatorColors.SFR_KEY -> R.drawable.logo_sfr
            OperatorColors.FREE_KEY -> R.drawable.logo_free
            else -> null
        }
    }

    fun homeLogoChoiceRes(choice: String, appLogoRes: Int): Int {
        return when (choice.lowercase()) {
            "orange" -> R.drawable.logo_orange
            "bouygues" -> R.drawable.logo_bouygues
            "sfr" -> R.drawable.logo_sfr
            "free" -> R.drawable.logo_free
            else -> appLogoRes
        }
    }
}
