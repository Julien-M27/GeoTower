package fr.geotower.ui.screens.changes

import fr.geotower.data.models.ChangeDetailItem
import fr.geotower.data.models.ChangeMapPoint
import fr.geotower.utils.OperatorColors

enum class SiteTypeOption(val label: String) {
    ALL("Tous"),
    NEW("Site neuf"),
    EXISTING("Site existant")
}

enum class ChangeActionType(val label: String) {
    SYSTEM_ADDED("Ajout fréquence"),
    ACTIVATION("Activation fréquence"),
    DEACTIVATION("Extinction fréquence"),
    SYSTEM_REMOVED("Suppression fréquence"),
    SITE_ADDED("Nouveaux sites"),
    SITE_REMOVED("Site démantelé"),
    MOVED("Déplacement géographique")
}

data class ChangesFilterState(
    val techs: Set<String> = setOf("2G", "3G", "4G", "5G"),
    val freqs: Set<Int> = setOf(700, 800, 900, 1800, 2100, 2600, 3500),
    val operators: Set<String> = defaultAllOperators(),
    val actions: Set<ChangeActionType> = ChangeActionType.values().toSet(),
    val siteType: SiteTypeOption = SiteTypeOption.ALL
) {
    val isDefault: Boolean
        get() = techs.size == 4 &&
                freqs.size == 7 &&
                operators.size == defaultAllOperators().size &&
                actions.size == ChangeActionType.values().size &&
                siteType == SiteTypeOption.ALL

    val activeCount: Int
        get() {
            var count = 0
            if (techs.size < 4) count += (4 - techs.size)
            if (freqs.size < 7) count += (7 - freqs.size)
            if (operators.size < defaultAllOperators().size) count += 1
            if (actions.size < ChangeActionType.values().size) count += (ChangeActionType.values().size - actions.size)
            if (siteType != SiteTypeOption.ALL) count += 1
            return count
        }

    companion object {
        val ALL_TECHS = listOf(
            "2G" to "GSM (2G)",
            "3G" to "UMTS (3G)",
            "4G" to "LTE (4G)",
            "5G" to "NR (5G)"
        )

        val ALL_FREQS = listOf(
            700 to "700 MHz",
            800 to "800 MHz",
            900 to "900 MHz",
            1800 to "1800 MHz",
            2100 to "2100 MHz",
            2600 to "2600 MHz",
            3500 to "3500 MHz"
        )

        val METRO_OP_KEYS = listOf(
            OperatorColors.BOUYGUES_KEY to "BOUYGUES TELECOM",
            OperatorColors.FREE_KEY to "FREE MOBILE",
            OperatorColors.ORANGE_KEY to "ORANGE",
            OperatorColors.SFR_KEY to "SFR"
        )

        val DROM_OP_KEYS = listOf(
            OperatorColors.DIGICEL_KEY to "DIGICEL",
            OperatorColors.FREE_CARAIBE_KEY to "FREE CARAIBES",
            OperatorColors.OPT_NC_KEY to "Gouv Nelle Calédonie (OPT)",
            OperatorColors.ONATI_KEY to "ONATI",
            OperatorColors.OUTREMER_TELECOM_KEY to "OUTREMER TELECOM",
            OperatorColors.SRR_KEY to "SRR",
            OperatorColors.TELCO_OI_KEY to "TELCO OI",
            OperatorColors.VITI_KEY to "VITI SAS",
            OperatorColors.ZEOP_KEY to "ZEOP",
            OperatorColors.DAUPHIN_TELECOM_KEY to "DAUPHIN TELECOM",
            OperatorColors.UTS_CARAIBE_KEY to "UTS CARAIBES",
            OperatorColors.MAORE_MOBILE_KEY to "MAORE MOBILE",
            OperatorColors.SPM_TELECOM_KEY to "SPM TELECOM"
        )

        fun defaultAllOperators(): Set<String> {
            return (METRO_OP_KEYS.map { it.first } + DROM_OP_KEYS.map { it.first }).toSet()
        }
    }
}

object ChangesFilterHelper {

    fun extractTech(system: String): String {
        val u = system.uppercase()
        return when {
            u.contains("5G") || u.contains("NR") -> "5G"
            u.contains("4G") || u.contains("LTE") -> "4G"
            u.contains("3G") || u.contains("UMTS") -> "3G"
            u.contains("2G") || u.contains("GSM") -> "2G"
            else -> ""
        }
    }

    fun extractFreq(system: String): Int? {
        val digits = system.filter { it.isDigit() }.toIntOrNull() ?: return null
        return when {
            digits in 680..799 -> 700
            digits in 800..899 -> 800
            digits in 900..999 -> 900
            digits in 1700..1900 -> 1800
            digits in 2000..2200 -> 2100
            digits in 2500..2700 -> 2600
            digits in 3400..3800 -> 3500
            else -> digits
        }
    }

    fun classifyChange(item: ChangeDetailItem): ChangeActionType {
        val isActivation = item.code == "STATUS_CHANGED" && (
            item.nowStatus?.contains("service", ignoreCase = true) == true ||
            item.nowStatus?.contains("activ", ignoreCase = true) == true ||
            item.nowStatus?.contains("opérat", ignoreCase = true) == true ||
            item.nowStatus?.contains("operat", ignoreCase = true) == true ||
            item.nowStatus?.contains("opǸrat", ignoreCase = true) == true
        )
        val isDeactivation = item.code == "STATUS_CHANGED" && (
            item.nowStatus?.contains("arrêt", ignoreCase = true) == true ||
            item.nowStatus?.contains("arret", ignoreCase = true) == true ||
            item.nowStatus?.contains("hors service", ignoreCase = true) == true ||
            item.nowStatus?.contains("supprim", ignoreCase = true) == true ||
            item.nowStatus?.contains("désactiv", ignoreCase = true) == true ||
            item.nowStatus?.contains("desactiv", ignoreCase = true) == true
        )
        return when {
            item.code == "SITE_ADDED" -> ChangeActionType.SITE_ADDED
            isActivation -> ChangeActionType.ACTIVATION
            isDeactivation -> ChangeActionType.DEACTIVATION
            item.code == "SYSTEM_ADDED" -> ChangeActionType.SYSTEM_ADDED
            item.code == "SYSTEM_REMOVED" -> ChangeActionType.SYSTEM_REMOVED
            item.code == "SITE_REMOVED" -> ChangeActionType.SITE_REMOVED
            item.code == "MOVED" -> ChangeActionType.MOVED
            item.code == "OPERATOR_ADDED" -> ChangeActionType.SITE_ADDED
            item.code == "OPERATOR_REMOVED" -> ChangeActionType.SITE_REMOVED
            else -> ChangeActionType.SYSTEM_ADDED
        }
    }

    fun matches(point: ChangeMapPoint, filter: ChangesFilterState): Boolean {
        if (filter.isDefault) return true

        // 1. Type de site
        val isNewSite = point.changes.any { it.code == "SITE_ADDED" }
        when (filter.siteType) {
            SiteTypeOption.NEW -> if (!isNewSite) return false
            SiteTypeOption.EXISTING -> if (isNewSite) return false
            SiteTypeOption.ALL -> Unit
        }

        // 2. Opérateurs du point
        val pointOps = point.operators()
        val hasMatchingOp = if (pointOps.isEmpty()) {
            true
        } else {
            pointOps.any { op ->
                val key = OperatorColors.keyFor(op) ?: op
                filter.operators.contains(key)
            }
        }
        if (!hasMatchingOp) return false

        // 3. Évolutions spécifiques
        val validChanges = point.changes.filter { chg ->
            val action = classifyChange(chg)
            if (!filter.actions.contains(action)) return@filter false

            // Opérateur du changement
            val op = chg.op ?: chg.ops?.firstOrNull()
            if (!op.isNullOrBlank()) {
                val key = OperatorColors.keyFor(op) ?: op
                if (!filter.operators.contains(key)) return@filter false
            }

            // Systèmes
            val allSys = mutableListOf<String>()
            chg.system?.takeIf { it.isNotBlank() }?.let { allSys.add(it) }
            chg.systems?.forEach { s -> if (s.isNotBlank()) allSys.add(s) }

            if (allSys.isNotEmpty()) {
                val sysMatches = allSys.any { sys ->
                    val tech = extractTech(sys)
                    val freq = extractFreq(sys)
                    val techOk = tech.isEmpty() || filter.techs.contains(tech)
                    val freqOk = freq == null || filter.freqs.contains(freq)
                    techOk && freqOk
                }
                if (!sysMatches) return@filter false
            } else {
                // Pas de système attaché (ex: MOVED ou SITE_REMOVED global)
                // Si l'utilisateur a désélectionné toutes les technos ou fréquences, on filtre
                if (filter.techs.isEmpty() || filter.freqs.isEmpty()) return@filter false
            }

            true
        }

        return validChanges.isNotEmpty()
    }
}
