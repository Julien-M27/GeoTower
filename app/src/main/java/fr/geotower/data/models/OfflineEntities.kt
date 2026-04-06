package fr.geotower.data.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

// 📍 Table 1 : Localisation (Très légère, juste pour afficher les points sur la carte)
@Entity(tableName = "localisation")
data class LocalisationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id_anfr") val idAnfr: String,
    @ColumnInfo(name = "operateur") val operateur: String?,
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "azimuts") val azimuts: String?,
    @ColumnInfo(name = "code_insee") val codeInsee: String?,
    @ColumnInfo(name = "azimuts_fh") val azimutsFh: String?,
    @ColumnInfo(name = "filtres") val filtres: String? // ✅ ON DÉCLARE LA NOUVELLE COLONNE !
) {
    @androidx.room.Ignore
    var frequences: String? = null
}

// ⚙️ Table 2 : Technique (Le réseau et les fréquences)
@Entity(tableName = "technique")
data class TechniqueEntity(
    @PrimaryKey
    @ColumnInfo(name = "id_anfr") val idAnfr: String,
    @ColumnInfo(name = "technologies") val technologies: String?,
    @ColumnInfo(name = "statut") val statut: String?,
    @ColumnInfo(name = "date_implantation") val dateImplantation: String?,
    @ColumnInfo(name = "date_service") val dateService: String?,
    @ColumnInfo(name = "date_modif") val dateModif: String?,
    @ColumnInfo(name = "details_frequences") val detailsFrequences: String?,
    @ColumnInfo(name = "adresse") val adresse: String?
)

// 🏗️ Table 3 : Physique (L'infrastructure, le pylône)
@Entity(tableName = "physique", primaryKeys = ["id_anfr", "id_support"])
data class PhysiqueEntity(
    @ColumnInfo(name = "id_anfr") val idAnfr: String,
    @ColumnInfo(name = "id_support") val idSupport: String,
    @ColumnInfo(name = "nature_support") val natureSupport: String?,
    @ColumnInfo(name = "proprietaire") val proprietaire: String?,
    @ColumnInfo(name = "hauteur") val hauteur: Double?, // ✅ LA NOUVELLE COLONNE
    @ColumnInfo(name = "azimuts_et_types") val azimutsEtTypes: String?
)

// 📡 Table 4 : Faisceaux Hertziens (Les paraboles)
@Entity(tableName = "faisceaux_hertziens", primaryKeys = ["id_anfr", "id_support"])
data class FaisceauxEntity(
    @ColumnInfo(name = "id_anfr") val idAnfr: String,
    @ColumnInfo(name = "id_support") val idSupport: String,
    @ColumnInfo(name = "type_fh") val typeFh: String?,
    @ColumnInfo(name = "azimuts_fh") val azimutsFh: String?
)

// 🌍 Le résultat du comptage SQL pour la carte (Vue éloignée)
data class DbCluster(
    @androidx.room.ColumnInfo(name = "centerLat") val centerLat: Double,
    @androidx.room.ColumnInfo(name = "centerLon") val centerLon: Double,
    @androidx.room.ColumnInfo(name = "count") val count: Int
)
// 🕒 Table 5 : Métadonnées (Pour la version de la base)
@Entity(tableName = "metadata")
data class MetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "version") val version: String,
    @ColumnInfo(name = "date_maj_anfr") val dateMajAnfr: String?
)