package fr.geotower.data.models

import com.google.gson.annotations.SerializedName

data class OfflineMapDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("map_url") val mapUrl: String,
    @SerializedName("estimated_size_mb") val estimatedSizeMb: Int,
    @SerializedName("map_filename") val mapFilename: String
)