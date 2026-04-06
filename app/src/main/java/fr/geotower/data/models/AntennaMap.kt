package fr.geotower.data.models

import com.google.gson.annotations.SerializedName

// Ce modèle ne contient QUE ce qui est nécessaire pour afficher le point sur la carte
data class AntennaMap(
    val id: Long,

    @SerializedName("operateur")
    val operateur: String?, // "Orange", "SFR", etc.

    val latitude: Double,
    val longitude: Double,

    @SerializedName("technologie")
    val technologie: String?, // "4G / 5G"

    @SerializedName("frequences") // 700mHz / 800mHz...
    val frequences: String?,

    @SerializedName("azimuts")
    val azimuts: String? // Pour plus tard (dessiner les flèches)
)