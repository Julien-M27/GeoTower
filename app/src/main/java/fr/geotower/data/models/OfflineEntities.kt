package fr.geotower.data.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.InflaterInputStream

object RadioFilterMasks {
    const val TECH_2G = 1 shl 0
    const val TECH_3G = 1 shl 1
    const val TECH_4G = 1 shl 2
    const val TECH_5G = 1 shl 3
    const val TECH_FH = 1 shl 4

    const val BAND_2G_900 = 1 shl 0
    const val BAND_2G_1800 = 1 shl 1
    const val BAND_3G_900 = 1 shl 2
    const val BAND_3G_2100 = 1 shl 3
    const val BAND_4G_700 = 1 shl 4
    const val BAND_4G_800 = 1 shl 5
    const val BAND_4G_900 = 1 shl 6
    const val BAND_4G_1800 = 1 shl 7
    const val BAND_4G_2100 = 1 shl 8
    const val BAND_4G_2600 = 1 shl 9
    const val BAND_5G_700 = 1 shl 10
    const val BAND_5G_2100 = 1 shl 11
    const val BAND_5G_3500 = 1 shl 12
    const val BAND_5G_26000 = 1 shl 13
    const val BAND_FH = 1 shl 14

    fun bandMaskToFilterString(mask: Int): String {
        if (mask == 0) return ""
        return buildList {
            if (mask has BAND_2G_900) add("2G900")
            if (mask has BAND_2G_1800) add("2G1800")
            if (mask has BAND_3G_900) add("3G900")
            if (mask has BAND_3G_2100) add("3G2100")
            if (mask has BAND_4G_700) add("4G700")
            if (mask has BAND_4G_800) add("4G800")
            if (mask has BAND_4G_900) add("4G900")
            if (mask has BAND_4G_1800) add("4G1800")
            if (mask has BAND_4G_2100) add("4G2100")
            if (mask has BAND_4G_2600) add("4G2600")
            if (mask has BAND_5G_700) add("5G700")
            if (mask has BAND_5G_2100) add("5G2100")
            if (mask has BAND_5G_3500) add("5G3500")
            if (mask has BAND_5G_26000) add("5G26000")
            if (mask has BAND_FH) add("FH")
        }.joinToString(" ")
    }

    fun techMaskToString(mask: Int): String {
        if (mask == 0) return ""
        return buildList {
            if (mask has TECH_2G) add("2G")
            if (mask has TECH_3G) add("3G")
            if (mask has TECH_4G) add("4G")
            if (mask has TECH_5G) add("5G")
            if (mask has TECH_FH) add("FH")
        }.joinToString(", ")
    }

    private infix fun Int.has(bit: Int): Boolean = (this and bit) != 0
}

object FrequencyDetailsCodec {
    private const val COMPRESSED_PREFIX = "Z1:"

    private val base64DecodeTable = IntArray(256) { -1 }.also { table ->
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".forEachIndexed { index, char ->
            table[char.code] = index
        }
        table['-'.code] = 62
        table['_'.code] = 63
    }

    fun decode(value: String?): String? {
        if (value.isNullOrEmpty() || !value.startsWith(COMPRESSED_PREFIX)) return value
        return try {
            val compressed = decodeBase64(value.substring(COMPRESSED_PREFIX.length))
            ByteArrayInputStream(compressed).use { input ->
                InflaterInputStream(input).use { inflater ->
                    inflater.readBytes().toString(Charsets.UTF_8)
                }
            }
        } catch (_: Exception) {
            value
        }
    }

    private fun decodeBase64(value: String): ByteArray {
        val output = ByteArrayOutputStream(value.length * 3 / 4)
        var buffer = 0
        var bits = 0

        loop@ for (char in value) {
            if (char == '=') break@loop
            if (char.isWhitespace()) continue

            val decoded = if (char.code < base64DecodeTable.size) base64DecodeTable[char.code] else -1
            if (decoded < 0) continue

            buffer = (buffer shl 6) or decoded
            bits += 6
            while (bits >= 8) {
                bits -= 8
                output.write((buffer shr bits) and 0xFF)
                buffer = if (bits == 0) 0 else buffer and ((1 shl bits) - 1)
            }
        }

        return output.toByteArray()
    }
}

@Entity(tableName = "localisation")
data class LocalisationDbEntity(
    @PrimaryKey
    @ColumnInfo(name = "id_anfr") val idAnfr: String,
    @ColumnInfo(name = "operateur_id") val operateurId: Int?,
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "azimuts") val azimuts: String?,
    @ColumnInfo(name = "code_insee") val codeInsee: String?,
    @ColumnInfo(name = "azimuts_fh") val azimutsFh: String?,
    @ColumnInfo(name = "tech_mask") val techMask: Int,
    @ColumnInfo(name = "band_mask") val bandMask: Int
)

@Entity(tableName = "technique")
data class TechniqueDbEntity(
    @PrimaryKey
    @ColumnInfo(name = "id_anfr") val idAnfr: String,
    @ColumnInfo(name = "statut_id") val statutId: Int?,
    @ColumnInfo(name = "date_implantation") val dateImplantation: String?,
    @ColumnInfo(name = "date_service") val dateService: String?,
    @ColumnInfo(name = "date_modif") val dateModif: String?,
    @ColumnInfo(name = "details_frequences") val detailsFrequences: String?,
    @ColumnInfo(name = "adresse") val adresse: String?,
    @ColumnInfo(name = "has_active") val hasActive: Int
)

@Entity(tableName = "support", primaryKeys = ["id_anfr", "id_support"])
data class SupportDbEntity(
    @ColumnInfo(name = "id_anfr") val idAnfr: String,
    @ColumnInfo(name = "id_support") val idSupport: String,
    @ColumnInfo(name = "nat_id") val natId: Int?,
    @ColumnInfo(name = "tpo_id") val tpoId: Int?,
    @ColumnInfo(name = "hauteur") val hauteur: Double?
)

@Entity(tableName = "antenne")
data class AntenneDbEntity(
    @PrimaryKey
    @ColumnInfo(name = "aer_id") val aerId: String,
    @ColumnInfo(name = "id_anfr") val idAnfr: String,
    @ColumnInfo(name = "id_support") val idSupport: String?,
    @ColumnInfo(name = "tae_id") val taeId: Int?,
    @ColumnInfo(name = "azimut") val azimut: Int?,
    @ColumnInfo(name = "hauteur_bas") val hauteurBas: Double?,
    @ColumnInfo(name = "is_fh") val isFh: Int
)

@Entity(tableName = "ref_operateur")
data class RefOperateurDbEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: Int,
    @ColumnInfo(name = "libelle") val libelle: String
)

@Entity(tableName = "ref_nature")
data class RefNatureDbEntity(
    @PrimaryKey
    @ColumnInfo(name = "nat_id") val natId: Int,
    @ColumnInfo(name = "libelle") val libelle: String
)

@Entity(tableName = "ref_proprietaire")
data class RefProprietaireDbEntity(
    @PrimaryKey
    @ColumnInfo(name = "tpo_id") val tpoId: Int,
    @ColumnInfo(name = "libelle") val libelle: String
)

@Entity(tableName = "ref_type_antenne")
data class RefTypeAntenneDbEntity(
    @PrimaryKey
    @ColumnInfo(name = "tae_id") val taeId: Int,
    @ColumnInfo(name = "libelle") val libelle: String
)

@Entity(tableName = "ref_systeme")
data class RefSystemeDbEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: Int,
    @ColumnInfo(name = "libelle") val libelle: String
)

@Entity(tableName = "ref_statut")
data class RefStatutDbEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: Int,
    @ColumnInfo(name = "libelle") val libelle: String
)

@Entity(tableName = "ref_commune")
data class RefCommuneDbEntity(
    @PrimaryKey
    @ColumnInfo(name = "code_insee") val codeInsee: String,
    @ColumnInfo(name = "nom") val nom: String
)

@Entity(tableName = "metadata")
data class MetadataDbEntity(
    @PrimaryKey
    @ColumnInfo(name = "version") val version: String,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    @ColumnInfo(name = "country_code") val countryCode: String,
    @ColumnInfo(name = "country_name") val countryName: String?,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "date_maj_anfr") val dateMajAnfr: String?,
    @ColumnInfo(name = "zip_version") val zipVersion: String?
)

data class LocalisationEntity(
    @ColumnInfo(name = "id_anfr") val idAnfr: String,
    @ColumnInfo(name = "operateur") val operateur: String?,
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "azimuts") val azimuts: String?,
    @ColumnInfo(name = "code_insee") val codeInsee: String?,
    @ColumnInfo(name = "azimuts_fh") val azimutsFh: String?,
    @ColumnInfo(name = "tech_mask") val techMask: Int = 0,
    @ColumnInfo(name = "band_mask") val bandMask: Int = 0
) {
    @androidx.room.Ignore
    var frequences: String? = null

    val filtres: String?
        get() = RadioFilterMasks.bandMaskToFilterString(bandMask).takeIf { it.isNotBlank() }
}

data class TechniqueEntity(
    @ColumnInfo(name = "id_anfr") val idAnfr: String,
    @ColumnInfo(name = "technologies") val technologies: String?,
    @ColumnInfo(name = "statut") val statut: String?,
    @ColumnInfo(name = "date_implantation") val dateImplantation: String?,
    @ColumnInfo(name = "date_service") val dateService: String?,
    @ColumnInfo(name = "date_modif") val dateModif: String?,
    @ColumnInfo(name = "details_frequences") val encodedDetailsFrequences: String?,
    @ColumnInfo(name = "adresse") val adresse: String?
) {
    val detailsFrequences: String?
        get() = FrequencyDetailsCodec.decode(encodedDetailsFrequences)
}

data class PhysiqueEntity(
    @ColumnInfo(name = "id_anfr") val idAnfr: String,
    @ColumnInfo(name = "id_support") val idSupport: String,
    @ColumnInfo(name = "nature_support") val natureSupport: String?,
    @ColumnInfo(name = "proprietaire") val proprietaire: String?,
    @ColumnInfo(name = "hauteur") val hauteur: Double?,
    @ColumnInfo(name = "azimuts_et_types") val azimutsEtTypes: String?
)

data class FaisceauxEntity(
    @ColumnInfo(name = "id_anfr") val idAnfr: String,
    @ColumnInfo(name = "id_support") val idSupport: String,
    @ColumnInfo(name = "type_fh") val typeFh: String?,
    @ColumnInfo(name = "azimuts_fh") val azimutsFh: String?
)

data class DbCluster(
    @ColumnInfo(name = "centerLat") val centerLat: Double,
    @ColumnInfo(name = "centerLon") val centerLon: Double,
    @ColumnInfo(name = "count") val count: Int,
    @ColumnInfo(name = "operators") val operators: String?
)
