package fr.geotower.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import fr.geotower.data.db.AppDatabase
import fr.geotower.data.models.FrequencyDetailsCodec
import kotlin.math.log10
import kotlin.math.pow

data class CaIsedSiteDetails(
    @SerializedName("record_id") val recordId: String? = null,
    @SerializedName("account_number") val accountNumber: String? = null,
    @SerializedName("licensee_name") val licenseeName: String? = null,
    @SerializedName("province_code") val provinceCode: String? = null,
    @SerializedName("site_type") val siteType: String? = null,
    @SerializedName("structure_type") val structureType: String? = null,
    @SerializedName("area_id") val areaId: String? = null,
    @SerializedName("area_name") val areaName: String? = null,
    @SerializedName("upload_date") val uploadDate: String? = null,
    @SerializedName("sectors") val sectors: List<CaIsedSectorDetail> = emptyList()
)

data class CaIsedSectorDetail(
    @SerializedName("licence_number") val licenceNumber: String? = null,
    @SerializedName("licence_category") val licenceCategory: String? = null,
    @SerializedName("subservice_id") val subserviceId: String? = null,
    @SerializedName("station_type") val stationType: String? = null,
    @SerializedName("technology") val technology: String? = null,
    @SerializedName("cell_id") val cellId: String? = null,
    @SerializedName("physical_id") val physicalId: String? = null,
    @SerializedName("tx_frequency") val txFrequency: Double? = null,
    @SerializedName("rx_frequency") val rxFrequency: Double? = null,
    @SerializedName("bandwidth") val bandwidth: Double? = null,
    @SerializedName("class_emission") val classEmission: String? = null,
    @SerializedName("tx_power") val txPower: Double? = null,
    @SerializedName("downlink_allocation") val downlinkAllocation: Double? = null,
    @SerializedName("tx_ant_type") val txAntType: String? = null,
    @SerializedName("tx_number_antennas") val txNumberAntennas: Int? = null,
    @SerializedName("rx_number_antennas") val rxNumberAntennas: Int? = null,
    @SerializedName("tx_ant_model_no") val txAntModelNo: String? = null,
    @SerializedName("tx_ant_manufacturer") val txAntManufacturer: String? = null,
    @SerializedName("tx_radio_model_number") val txRadioModelNumber: String? = null,
    @SerializedName("tx_radio_manufacturer_code") val txRadioManufacturerCode: String? = null,
    @SerializedName("tx_ant_height") val txAntHeight: Double? = null,
    @SerializedName("tx_ant_horiz_beamwidth") val txAntHorizBeamwidth: Double? = null,
    @SerializedName("tx_ant_vert_beamwidth") val txAntVertBeamwidth: Double? = null,
    @SerializedName("tx_ant_azimuth") val txAntAzimuth: Int? = null,
    @SerializedName("tx_ant_elevation_angle") val txAntElevationAngle: Double? = null,
    @SerializedName("tx_ant_gain") val txAntGain: Double? = null,
    @SerializedName("tx_line_loss") val txLineLoss: Double? = null,
    @SerializedName("date_last_changed") val dateLastChanged: String? = null
) {
    /** PIRE / EIRP en dBm si tx_power (>0) et tx_ant_gain sont presents. */
    val eirpDbm: Double?
        get() {
            val p = txPower?.takeIf { it > 0.0 } ?: return null
            val g = txAntGain ?: return null
            val loss = txLineLoss ?: 0.0
            return 10.0 * log10(p * 1000.0) + g - loss
        }

    /** PIRE / EIRP en Watts. */
    val eirpWatts: Double?
        get() = eirpDbm?.let { 10.0.pow((it - 30.0) / 10.0) }
}

object CaIsedSiteDetailsRepository {
    private val gson = Gson()
    private val liveCache = java.util.concurrent.ConcurrentHashMap<String, CaIsedSiteDetails>()

    fun cacheLiveDetails(idAnfr: String, details: CaIsedSiteDetails?) {
        if (idAnfr.startsWith("CA-", ignoreCase = true) && details != null) {
            liveCache[idAnfr] = details
        }
    }

    fun loadForSite(context: Context, idAnfr: String): CaIsedSiteDetails? {
        if (!idAnfr.startsWith("CA-", ignoreCase = true)) return null
        liveCache[idAnfr]?.let { return it }
        return try {
            val db = AppDatabase.getDatabase(context, fr.geotower.data.db.DatabaseCountry.CA).openHelper.readableDatabase
            val hasTable = db.query(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'ca_ised_site_details' LIMIT 1"
            ).use { it.moveToFirst() }
            if (!hasTable) return null

            val encoded = db.query(
                "SELECT payload FROM ca_ised_site_details WHERE id_anfr = ? LIMIT 1",
                arrayOf(idAnfr)
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: return null

            val json = FrequencyDetailsCodec.decode(encoded) ?: return null
            gson.fromJson(json, CaIsedSiteDetails::class.java)
        } catch (_: Exception) {
            null
        }
    }
}
