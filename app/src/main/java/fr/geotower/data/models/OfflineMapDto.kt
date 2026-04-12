package fr.geotower.data.models

import com.google.gson.annotations.SerializedName

data class OfflineMapDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("zip_url") val zipUrl: String,
    @SerializedName("estimated_zip_size_mb") val estimatedZipSizeMb: Int,
    @SerializedName("map_filename") val mapFilename: String
)