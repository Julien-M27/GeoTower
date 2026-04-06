package fr.geotower.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "antennas")
data class Antenna(
    // ==========================================
    // CLÉ PRIMAIRE LOCALE (NOUVEAU)
    // ==========================================
    // Auto-générée par le téléphone. Empêche Room d'écraser
    // les opérateurs situés sur le même pylône !
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,

    // L'identifiant venant de l'API (anciennement "id")
    @SerializedName("id")
    val idAnfr: Long,

    @SerializedName("id_support")
    val idSupport: String? = null,

    // ==========================================
    // RÉSEAU & TECHNIQUE
    // ==========================================
    @SerializedName("operateur")
    val operatorName: String,

    @SerializedName("technologie")
    val technology: String? = null,

    @SerializedName("frequences")
    val frequencies: String? = "Non spécifié",

    @SerializedName("statut")
    val statut: String? = null,

    @SerializedName("azimuts")
    val azimuts: String? = null,


    // ==========================================
    // LOCALISATION (GPS)
    // ==========================================
    @SerializedName("latitude")
    val latitude: Double,

    @SerializedName("longitude")
    val longitude: Double,

    // ==========================================
    // ADRESSE POSTALE & DÉTAILS
    // ==========================================
    @SerializedName("adresse")
    val address: String? = null,

    @SerializedName("code_postal")
    val zipCode: String? = null,

    @SerializedName("code_insee")
    val codeInsee: String? = null,

    @SerializedName("ville")
    val city: String? = null,

    @SerializedName("hauteur")
    val height: Double? = 0.0,

    @SerializedName("nature_support")
    val supportType: String? = "Non spécifié",

    @SerializedName("proprietaire")
    val proprietaire: String? = "Non spécifié",

    @SerializedName("type_antenne")
    val type_antenne: String? = "Non spécifié",

    @SerializedName("date_implantation")
    val implementationDate: String? = "-",

    @SerializedName("date_service")
    val activationDate: String? = "-",

    @SerializedName("date_modif")
    val modificationDate: String? = "-"
)