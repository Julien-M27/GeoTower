package fr.geotower.data.models

import com.google.gson.annotations.SerializedName

/**
 * Métadonnées du jeu de données servi par la base EN LIGNE (`/api/v2/live/fr/status`).
 *
 * La base live est construite à partir du même fichier que la base téléchargeable : elle en garde la
 * version ([offlineDbVersion]) et les dates des sources ANFR ([dateMajAnfr], [zipVersion]). C'est ce
 * qui permet d'annoncer, quand aucune base locale n'est installée, exactement quelles données l'app
 * est en train de lire — et qu'elles viennent du serveur.
 */
data class LiveDbStatusDto(
    @SerializedName("country_code") val countryCode: String? = null,
    @SerializedName("country_name") val countryName: String? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("offline_db_version") val offlineDbVersion: String? = null,
    @SerializedName("offline_schema_version") val offlineSchemaVersion: Int? = null,
    @SerializedName("live_schema_version") val liveSchemaVersion: Int? = null,
    @SerializedName("date_maj_anfr") val dateMajAnfr: String? = null,
    @SerializedName("zip_version") val zipVersion: String? = null,
    /**
     * Version du fichier trimestriel ARCEP. Absente des bases live construites avant la reprise de
     * `source_versions` par le builder : le champ reste donc nullable, et la ligne correspondante
     * n'est pas affichée plutôt que montrée vide (la donnée ARCEP, elle, est bien servie).
     */
    @SerializedName("quarterly_version") val quarterlyVersion: String? = null,
    @SerializedName("generated_at") val generatedAt: String? = null,
    /** Nombre de lignes de `site_summary`, c'est-à-dire de stations publiées. */
    @SerializedName("row_count") val rowCount: Int? = null
)
