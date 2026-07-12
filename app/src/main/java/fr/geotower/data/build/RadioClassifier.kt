package fr.geotower.data.build

import fr.geotower.data.models.RadioServiceMasks
import fr.geotower.data.models.RadioSystemMasks

/**
 * Classification des emetteurs ANFR pour la base radio non-mobile, portee a l'identique depuis
 * `docs/server/fr_radio_db_builder.py` (`is_mobile_like_system`, `is_public_mobile_operator`,
 * `system_key`, `service_for`, `SERVICE_LABELS`, `PUBLIC_MOBILE_OPERATOR_MARKERS`).
 *
 * Les bits reutilisent [RadioServiceMasks] / [RadioSystemMasks] (verifies bit a bit face aux
 * constantes serveur), afin que la base generee localement soit lue exactement comme celle du serveur.
 */
object RadioClassifier {

    /** Marqueurs de libelle exploitant identifiant un operateur mobile grand public. */
    private val PUBLIC_MOBILE_OPERATOR_MARKERS = listOf(
        "BOUYGUES",
        "ORANGE",
        "SFR",
        "SOCIETE FRANCAISE DU RADIOTELEPHONE",
        "FREE MOBILE",
        "FREE CARAIB",
        "DIGICEL",
        "OUTREMER",
        "SRR",
        "ZEOP",
        "MAORE",
        "DAUPHIN",
        "TELCO OI",
        "VITI",
        "PMT",
        "VODAFONE",
        "SPM TELECOM",
        "ONATI",
        "GLOBALTEL",
        "UTS CARAIBE",
        "OPT",
    )

    /** Python `is_mobile_like_system` : GSM public / UMTS / 5G NR / LTE (hors "LTE P..." prive). */
    fun isMobileLikeSystem(system: String): Boolean {
        val upper = system.uppercase()
        if (upper.startsWith("GSM 900") || upper.startsWith("GSM 1800") ||
            upper.startsWith("UMTS") || upper.startsWith("5G NR")
        ) {
            return true
        }
        if (upper.startsWith("LTE") && !upper.contains(" P")) return true
        return false
    }

    /** Python `is_public_mobile_operator` : le libelle exploitant contient un marqueur. */
    fun isPublicMobileOperator(label: String): Boolean {
        val upper = label.uppercase()
        return PUBLIC_MOBILE_OPERATOR_MARKERS.any { upper.contains(it) }
    }

    /** Python `system_key` : prefixe `EMR_LB_SYSTEME` -> cle systeme (clef de [systemBit]). */
    fun systemKey(system: String): String {
        val upper = system.uppercase()
        return when {
            upper.startsWith("FM") -> "FM"
            upper.startsWith("RDF DVB") -> "DVB_T"
            upper.startsWith("RDF T-DAB") -> "DAB"
            upper.startsWith("RDF AM") -> "AM"
            upper.startsWith("FH") -> "FH"
            upper.startsWith("GSM R") -> "GSM_R"
            upper.startsWith("PMR") -> "PMR"
            upper.startsWith("TETRA") || upper.startsWith("TETRAPOL") -> "TETRA"
            upper.startsWith("RMU") -> "POCSAG"
            upper.startsWith("COM TER") -> "COM_TER"
            upper.startsWith("COM MAR") -> "COM_MAR"
            upper.startsWith("AIS") -> "AIS"
            upper.startsWith("SAT") -> "SAT"
            upper.startsWith("RDR") -> "RADAR"
            upper.startsWith("BLR") -> "BLR"
            upper.startsWith("LTE") -> "LTE_PRIVATE"
            upper.startsWith("5G BROADCAST") -> "BROADCAST_5G"
            upper == "RS" -> "METEO_RS"
            upper.startsWith("TELEM") || upper.startsWith("TELECD") -> "TELEMETRY"
            else -> "OTHER"
        }
    }

    /** Bit `system_mask` pour une cle systeme (defaut OTHER si non mappee). */
    fun systemBit(key: String): Int = when (key) {
        "FM" -> RadioSystemMasks.FM
        "DVB_T" -> RadioSystemMasks.DVB_T
        "DAB" -> RadioSystemMasks.DAB
        "AM" -> RadioSystemMasks.AM
        "FH" -> RadioSystemMasks.FH
        "GSM_R" -> RadioSystemMasks.GSM_R
        "PMR" -> RadioSystemMasks.PMR
        "TETRA" -> RadioSystemMasks.TETRA
        "POCSAG" -> RadioSystemMasks.POCSAG
        "COM_TER" -> RadioSystemMasks.COM_TER
        "COM_MAR" -> RadioSystemMasks.COM_MAR
        "AIS" -> RadioSystemMasks.AIS
        "SAT" -> RadioSystemMasks.SAT
        "RADAR" -> RadioSystemMasks.RADAR
        "BLR" -> RadioSystemMasks.BLR
        "LTE_PRIVATE" -> RadioSystemMasks.LTE_PRIVATE
        "BROADCAST_5G" -> RadioSystemMasks.BROADCAST_5G
        "METEO_RS" -> RadioSystemMasks.METEO_RS
        "TELEMETRY" -> RadioSystemMasks.TELEMETRY
        else -> RadioSystemMasks.OTHER
    }

    /** Python `service_for` : famille de service d'un emetteur (ordre d'evaluation preserve). */
    fun serviceFor(system: String, actorLabel: String): Int {
        val upper = system.uppercase()
        val actorUpper = actorLabel.uppercase()
        return when {
            upper.startsWith("FH") -> RadioServiceMasks.FH
            upper.startsWith("FM") || upper.startsWith("RDF DVB") || upper.startsWith("RDF T-DAB") ||
                upper.startsWith("RDF AM") || upper.startsWith("5G BROADCAST") -> RadioServiceMasks.BROADCAST
            actorUpper.contains("SNCF") || upper.startsWith("GSM R") -> RadioServiceMasks.RAIL
            upper.startsWith("COM TER") || upper.startsWith("COM MAR") || upper.startsWith("AIS") ||
                upper.startsWith("COM AERTER") -> RadioServiceMasks.TRANSPORT
            upper.startsWith("SAT") || upper.startsWith("GPS") -> RadioServiceMasks.SATELLITE
            upper.startsWith("RDR") || upper.startsWith("GONIO") -> RadioServiceMasks.RADAR
            upper.startsWith("PMR") || upper.startsWith("TETRA") || upper.startsWith("TETRAPOL") ||
                upper.startsWith("RMU") || upper.startsWith("EM/REC") || upper.startsWith("REC") ||
                upper.startsWith("TELEM") || upper.startsWith("TELECD") || upper.startsWith("ANM") ||
                upper.startsWith("LTE") -> RadioServiceMasks.PRIVATE
            else -> RadioServiceMasks.OTHER
        }
    }

    /**
     * Python `service_names` : libelles des services presents dans `mask`, dans l'ordre des bits
     * (BROADCAST, PRIVATE, RAIL, TRANSPORT, FH, SATELLITE, RADAR, OTHER), joints par ", ".
     * Ces libelles sont ecrits dans la ligne `Familles:` du blob detail (parite serveur).
     */
    fun serviceNames(mask: Int): String {
        val parts = ArrayList<String>(SERVICE_LABELS_ORDERED.size)
        for ((bit, label) in SERVICE_LABELS_ORDERED) {
            if (mask and bit != 0) parts.add(label)
        }
        return parts.joinToString(", ")
    }

    private val SERVICE_LABELS_ORDERED = listOf(
        RadioServiceMasks.BROADCAST to "Radio/TV",
        RadioServiceMasks.PRIVATE to "Reseaux prives",
        RadioServiceMasks.RAIL to "Ferroviaire",
        RadioServiceMasks.TRANSPORT to "Transport",
        RadioServiceMasks.FH to "FH",
        RadioServiceMasks.SATELLITE to "Satellite",
        RadioServiceMasks.RADAR to "Radar",
        RadioServiceMasks.OTHER to "Autres",
    )
}
