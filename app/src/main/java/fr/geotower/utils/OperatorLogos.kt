package fr.geotower.utils

import fr.geotower.R

object OperatorLogos {
    fun drawableRes(raw: String?): Int? {
        val normalized = raw?.uppercase()?.trim().orEmpty()
        if (normalized.contains("TDF") ||
            normalized.contains("TELEDIFFUSION") ||
            normalized.contains("TÉLÉDIFFUSION")
        ) {
            return R.drawable.logo_tdf
        }
        if (normalized.contains("TOWERCAST") || normalized.contains("TOWER CAST")) {
            return R.drawable.logo_towercast
        }
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
            OperatorColors.ROGERS_KEY -> R.drawable.logo_rogers
            OperatorColors.BELL_KEY -> R.drawable.logo_bell
            OperatorColors.TELUS_KEY -> R.drawable.logo_telus
            OperatorColors.VIDEOTRON_KEY -> R.drawable.logo_videotron
            OperatorColors.FREEDOM_MOBILE_KEY -> R.drawable.logo_freedom
            OperatorColors.SASKTEL_KEY -> R.drawable.logo_sasktel
            OperatorColors.XPLORE_KEY -> R.drawable.logo_xplore
            OperatorColors.EASTLINK_KEY -> R.drawable.logo_eastlink
            OperatorColors.TBAYTEL_KEY -> R.drawable.logo_tbaytel
            OperatorColors.SSI_MICRO_KEY -> R.drawable.logo_ssi_micro
            OperatorColors.ECOTEL_KEY -> R.drawable.logo_ecotel
            OperatorColors.COGECO_KEY -> R.drawable.logo_cogeco
            OperatorColors.SOGETEL_KEY -> R.drawable.logo_sogetel
            OperatorColors.ICE_WIRELESS_KEY -> R.drawable.logo_ice_wireless
            else -> null
        }
    }

    fun homeLogoChoiceRes(choice: String, appLogoRes: Int): Int {
        return drawableRes(choice) ?: appLogoRes
    }
}
