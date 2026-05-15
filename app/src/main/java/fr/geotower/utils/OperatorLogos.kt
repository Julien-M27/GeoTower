package fr.geotower.utils

import fr.geotower.R

object OperatorLogos {
    fun drawableRes(raw: String?): Int? {
        return when (OperatorColors.keyFor(raw)) {
            OperatorColors.ORANGE_KEY -> R.drawable.logo_orange
            OperatorColors.BOUYGUES_KEY -> R.drawable.logo_bouygues
            OperatorColors.SFR_KEY -> R.drawable.logo_sfr
            OperatorColors.FREE_KEY -> R.drawable.logo_free
            OperatorColors.SRR_KEY -> R.drawable.logo_sfr
            OperatorColors.DIGICEL_KEY -> R.drawable.logo_digicel
            OperatorColors.FREE_CARAIBE_KEY -> R.drawable.logo_free_caraibe
            OperatorColors.UTS_CARAIBE_KEY -> R.drawable.logo_uts_caraibe
            OperatorColors.DAUPHIN_TELECOM_KEY -> R.drawable.logo_dauphin_telecom
            OperatorColors.GLOBALTEL_KEY -> R.drawable.logo_globaltel
            OperatorColors.MAORE_MOBILE_KEY -> R.drawable.logo_maore_mobile
            OperatorColors.OUTREMER_TELECOM_KEY -> R.drawable.logo_outremer_telecom
            OperatorColors.SPM_TELECOM_KEY -> R.drawable.logo_spm_telecom
            OperatorColors.TELCO_OI_KEY -> R.drawable.logo_telco_oi
            OperatorColors.ZEOP_KEY -> R.drawable.logo_zeop
            OperatorColors.OPT_NC_KEY -> R.drawable.logo_opt_nouvelle_caledonie
            OperatorColors.ONATI_KEY -> R.drawable.logo_onati
            OperatorColors.PMT_VODAFONE_KEY -> R.drawable.logo_pmt
            OperatorColors.VITI_KEY -> R.drawable.logo_viti
            OperatorColors.SPT_KEY -> R.drawable.logo_spt
            else -> null
        }
    }

    fun homeLogoChoiceRes(choice: String, appLogoRes: Int): Int {
        return drawableRes(choice) ?: appLogoRes
    }
}
