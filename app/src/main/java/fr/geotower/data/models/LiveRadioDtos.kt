package fr.geotower.data.models

import com.google.gson.annotations.SerializedName

data class LiveRadioMarkersResponseDto(
    @SerializedName("country_code") val countryCode: String? = null,
    @SerializedName("markers") val markers: List<LiveRadioMarkerDto> = emptyList()
)

data class LiveRadioMarkerResponseDto(
    @SerializedName("country_code") val countryCode: String? = null,
    @SerializedName("marker") val marker: LiveRadioMarkerDto? = null
)

data class LiveRadioMarkerDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("service_mask") val serviceMask: Int? = null,
    @SerializedName("system_mask") val systemMask: Int? = null,
    @SerializedName("actor_label") val actorLabel: String? = null,
    @SerializedName("emitter_count") val emitterCount: Int? = null,
    @SerializedName("antenna_count") val antennaCount: Int? = null,
    @SerializedName("min_freq_khz") val minFreqKhz: Int? = null,
    @SerializedName("max_freq_khz") val maxFreqKhz: Int? = null,
    @SerializedName("cluster_count") val clusterCount: Int? = null,
    @SerializedName("detail_text") val detailText: String? = null,
    @SerializedName("station_id") val stationId: String? = null,
    @SerializedName("support_id") val supportId: String? = null
) {
    fun toRadioMapMarker(): RadioMapMarker? {
        val safeId = id?.takeIf { it.isNotBlank() } ?: return null
        val safeLat = latitude ?: return null
        val safeLon = longitude ?: return null
        return RadioMapMarker(
            id = safeId,
            latitude = safeLat,
            longitude = safeLon,
            serviceMask = serviceMask ?: 0,
            systemMask = systemMask ?: 0,
            actorLabel = actorLabel,
            emitterCount = emitterCount ?: 0,
            antennaCount = antennaCount ?: 0,
            minFreqKhz = minFreqKhz,
            maxFreqKhz = maxFreqKhz,
            clusterCount = (clusterCount ?: 1).coerceAtLeast(1),
            detailText = detailText,
            stationId = stationId.orEmpty(),
            supportId = supportId.orEmpty()
        )
    }
}
