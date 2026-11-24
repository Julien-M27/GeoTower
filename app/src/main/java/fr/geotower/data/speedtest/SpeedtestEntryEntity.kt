package fr.geotower.data.speedtest

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entrée de journal d'un speedtest importé depuis une capture d'écran.
 *
 * [imageFileName] est un nom de fichier relatif stocké dans le dossier interne `speedtests/`
 * afin que les entrées restent portables lors d'une sauvegarde/restauration entre appareils.
 */
@Entity(tableName = "speedtests")
data class SpeedtestEntryEntity(
    @PrimaryKey
    val id: String,
    val imageFileName: String,
    val testedAtMillis: Long,
    val createdAtMillis: Long,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val pingMs: Double? = null,
    val jitterMs: Double? = null,
    val operatorName: String? = null,
    val networkName: String? = null,
    val networkTech: String? = null,
    val band: String? = null,
    val cellIdentifier: String? = null,
    val locationLabel: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val linkedSiteId: String? = null,
    val note: String? = null,
    val ocrRawText: String? = null
)
