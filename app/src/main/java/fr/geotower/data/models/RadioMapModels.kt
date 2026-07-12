package fr.geotower.data.models

import android.content.Context
import androidx.annotation.StringRes
import fr.geotower.R
import java.util.Locale

object RadioServiceMasks {
    const val BROADCAST = 1 shl 0
    const val PRIVATE = 1 shl 1
    const val RAIL = 1 shl 2
    const val TRANSPORT = 1 shl 3
    const val FH = 1 shl 4
    const val SATELLITE = 1 shl 5
    const val RADAR = 1 shl 6
    const val OTHER = 1 shl 7

    const val ALL = BROADCAST or PRIVATE or RAIL or TRANSPORT or FH or SATELLITE or RADAR or OTHER

    /** Tout le non-mobile SAUF la diffusion Radio/TV (faisceaux, reseaux prives, ferroviaire, transport, etc.). */
    const val NON_BROADCAST = PRIVATE or RAIL or TRANSPORT or FH or SATELLITE or RADAR or OTHER

    @StringRes
    fun labelRes(mask: Int): Int {
        return when {
            (mask and BROADCAST) != 0 -> R.string.appstrings_radio_service_broadcast
            (mask and RAIL) != 0 -> R.string.appstrings_radio_service_rail
            (mask and TRANSPORT) != 0 -> R.string.appstrings_radio_service_transport
            (mask and FH) != 0 -> R.string.appstrings_radio_service_fh
            (mask and SATELLITE) != 0 -> R.string.appstrings_radio_service_satellite
            (mask and RADAR) != 0 -> R.string.appstrings_radio_service_radar
            (mask and PRIVATE) != 0 -> R.string.appstrings_radio_service_private
            else -> R.string.appstrings_radio_service_other
        }
    }

    fun labelFor(context: Context, mask: Int): String = context.getString(labelRes(mask))
}

object RadioSystemMasks {
    const val FM = 1 shl 0
    const val DVB_T = 1 shl 1
    const val DAB = 1 shl 2
    const val AM = 1 shl 3
    const val FH = 1 shl 4
    const val GSM_R = 1 shl 5
    const val PMR = 1 shl 6
    const val TETRA = 1 shl 7
    const val POCSAG = 1 shl 8
    const val COM_TER = 1 shl 9
    const val COM_MAR = 1 shl 10
    const val AIS = 1 shl 11
    const val SAT = 1 shl 12
    const val RADAR = 1 shl 13
    const val BLR = 1 shl 14
    const val LTE_PRIVATE = 1 shl 15
    const val BROADCAST_5G = 1 shl 16
    const val METEO_RS = 1 shl 17
    const val TELEMETRY = 1 shl 18
    const val OTHER = 1 shl 19

    const val TV = DVB_T or BROADCAST_5G
    const val RADIO = FM or DAB or AM
}

object RadioMapCategoryMasks {
    const val TV = 1 shl 0
    const val RADIO = 1 shl 1
    const val PRIVATE_MOBILE = 1 shl 2
    const val FH = 1 shl 3
    const val OTHER = 1 shl 4

    const val ALL = TV or RADIO or PRIVATE_MOBILE or FH or OTHER
}

data class RadioMapMarker(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val serviceMask: Int,
    val systemMask: Int,
    val actorLabel: String?,
    val emitterCount: Int,
    val antennaCount: Int,
    val minFreqKhz: Int?,
    val maxFreqKhz: Int?,
    val clusterCount: Int = 1,
    val detailText: String? = null,
    val stationId: String = "",
    val supportId: String = ""
) {
    val isCluster: Boolean
        get() = clusterCount > 1 || id.startsWith(RADIO_CLUSTER_ID_PREFIX)

    fun networkName(context: Context): String =
        actorLabel?.takeIf { it.isNotBlank() } ?: RadioServiceMasks.labelFor(context, serviceMask)

    val systemSummary: String?
        get() = detailValue("Systemes")

    val frequencySummary: String?
        get() = detailValue("Frequences") ?: frequencyLabel()

    val antennaSummary: String?
        get() = detailValue("Antennes")?.cleanRadioAntennaText()

    val antennaLines: List<String>
        get() = antennaSummary
            ?.split(";")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

    val broadcastPrograms: List<RadioBroadcastProgram>
        get() = detailValue("Programmes")
            ?.split(";")
            ?.mapNotNull { RadioBroadcastProgram.fromPacked(it) }
            .orEmpty()

    val broadcastProgramSummary: String?
        get() {
            val programs = broadcastPrograms
            if (programs.isEmpty()) return null
            val names = programs.map { it.serviceName }.distinct()
            val shown = names.take(3).joinToString(", ")
            return if (names.size > 3) "$shown, +${names.size - 3}" else shown
        }

    val azimuths: List<Float>
        get() = antennaLines
            .mapNotNull { line ->
                val normalized = line.replace("\u00C2\u00B0", "\u00B0")
                ANTENNA_AZIMUTH_REGEX.find(normalized)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.replace(',', '.')
                    ?.toFloatOrNull()
                    ?.takeIf { it in 0f..360f }
                    ?.let { if (it == 360f) 0f else it }
            }
            .distinct()

    val supportSummary: String?
        get() = detailValue("Support")

    val supportNatureSummary: String?
        get() = supportSummary
            ?.split(";")
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    val supportOwnerSummary: String?
        get() = supportSummary
            ?.split(";")
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("proprietaire ", ignoreCase = true) }
            ?.substringAfter(" ", "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    val supportHeightSummary: String?
        get() {
            val heightDm = Regex("""hauteur_dm=(\d+)""")
                .find(supportSummary.orEmpty())
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: return null
            val meters = heightDm / 10.0
            return if (heightDm % 10 == 0) {
                "${heightDm / 10} m"
            } else {
                String.format(Locale.US, "%.1f m", meters)
            }
        }

    val addressSummary: String?
        get() = detailValue("Adresse")

    fun title(context: Context): String {
        return if (isCluster) {
            context.getString(
                R.string.appstrings_radio_cluster_sites,
                clusterCount,
                RadioServiceMasks.labelFor(context, serviceMask)
            )
        } else {
            networkName(context)
        }
    }

    fun subtitle(context: Context): String {
        broadcastProgramSummary?.let { return it }
        val service = RadioServiceMasks.labelFor(context, serviceMask)
        val counts = buildList {
            if (emitterCount > 0) add(context.getString(R.string.appstrings_radio_emitters_count, emitterCount))
            if (antennaCount > 0) add(context.getString(R.string.appstrings_radio_antennas_count, antennaCount))
            frequencyLabel()?.let { add(it) }
        }
        return (listOf(service) + counts).joinToString(" - ")
    }

    private fun frequencyLabel(): String? {
        val min = minFreqKhz ?: return null
        val max = maxFreqKhz ?: return null
        if (min <= 0 || max <= 0) return null
        return "${formatFrequency(min)}-${formatFrequency(max)}"
    }

    private fun formatFrequency(khz: Int): String {
        return when {
            khz >= 1_000_000 -> "%.2f GHz".format(khz / 1_000_000.0)
            khz >= 1_000 -> "%.1f MHz".format(khz / 1_000.0)
            else -> "$khz kHz"
        }
    }

    private fun detailValue(label: String): String? {
        val prefix = "$label:"
        return detailText
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith(prefix, ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun String.cleanRadioAntennaText(): String {
        return replace(Regex("""\s*\[TAE_ID\s+\d+\]""", RegexOption.IGNORE_CASE), "")
            .replace(" deg", "°")
            .replace("Deg", "°")
            .trim()
    }

    companion object {
        const val RADIO_CLUSTER_ID_PREFIX = "RADIO_CLUSTER_"
        private val ANTENNA_AZIMUTH_REGEX = Regex(
            """([0-9]{1,3}(?:[.,][0-9]+)?)\s*(?:°|deg)""",
            RegexOption.IGNORE_CASE
        )
    }
}

/** Identifiants des champs du résumé partageable (rapport GeoTower). */
object RadioReportSummaryFields {
    const val NETWORK = "network"
    const val SYSTEMS = "systems"
    const val FREQUENCIES = "frequencies"
    const val SUPPORT_HEIGHT = "support_height"
}

data class RadioReportSummaryField(
    val id: String,
    val value: String
)

/**
 * Construit, à partir des données déjà chargées d'un marqueur, la liste ordonnée
 * des champs factuels du résumé du rapport. Le libellé réseau localisé est fourni
 * par l'appelant (via [networkName]) afin que cette fonction reste pure (sans
 * Compose ni ressources) et testable unitairement. Les champs vides sont omis.
 */
fun RadioMapMarker.reportSummaryFields(networkLabel: String): List<RadioReportSummaryField> {
    return listOfNotNull(
        networkLabel.takeIf { it.isNotBlank() }
            ?.let { RadioReportSummaryField(RadioReportSummaryFields.NETWORK, it) },
        systemSummary?.takeIf { it.isNotBlank() }
            ?.let { RadioReportSummaryField(RadioReportSummaryFields.SYSTEMS, it) },
        frequencySummary?.takeIf { it.isNotBlank() }
            ?.let { RadioReportSummaryField(RadioReportSummaryFields.FREQUENCIES, it) },
        supportHeightSummary?.takeIf { it.isNotBlank() }
            ?.let { RadioReportSummaryField(RadioReportSummaryFields.SUPPORT_HEIGHT, it) }
    )
}

data class RadioBroadcastProgram(
    val serviceName: String,
    val frequencyLabel: String?,
    val mode: String?,
    val category: String?
) {
    val detailLabel: String?
        get() = listOfNotNull(
            frequencyLabel?.takeIf { it.isNotBlank() },
            mode?.takeIf { it.isNotBlank() },
            category?.takeIf { it.isNotBlank() }
        ).joinToString(" - ").takeIf { it.isNotBlank() }

    companion object {
        fun fromPacked(raw: String): RadioBroadcastProgram? {
            val text = raw.trim()
            if (text.isBlank()) return null
            val parts = text.split("|").map { it.trim() }
            return if (parts.size >= 2) {
                val name = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
                RadioBroadcastProgram(
                    serviceName = name,
                    frequencyLabel = parts.getOrNull(1)?.takeIf { it.isNotBlank() },
                    mode = parts.getOrNull(2)?.takeIf { it.isNotBlank() },
                    category = parts.getOrNull(3)?.takeIf { it.isNotBlank() }
                )
            } else {
                RadioBroadcastProgram(
                    serviceName = text,
                    frequencyLabel = null,
                    mode = null,
                    category = null
                )
            }
        }
    }
}
