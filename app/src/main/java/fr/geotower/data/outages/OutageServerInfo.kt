package fr.geotower.data.outages

import android.content.SharedPreferences
import fr.geotower.data.models.SiteHsEntity
import fr.geotower.utils.LocalizedDateLabels

/**
 * Ce que l'app retient de la source SERVEUR des pannes, dans les prefs partagées "settings" :
 *  - la date de la donnée (`last_update` de `/api/v2/antennes/hs/info`, connue au jour près) ;
 *  - l'instant où le serveur a produit le fichier (`metadata.generated_at` du GeoJSON, en UTC) ;
 *  - le résumé du dernier téléchargement (quand, combien de pannes, réparties comment).
 *
 * L'heure de génération est lue dans le fichier téléchargé lui-même : il n'y a rien de plus à
 * demander au serveur, et un fichier produit par un ancien builder (sans `generated_at`) retombe
 * simplement sur la date seule. Pendant de [OutageLocalConfig], qui joue le même rôle quand les
 * pannes sont produites sur l'appareil.
 *
 * Le résumé est ici, et non dans [ServerOutageCache], pour que la carte des réglages puisse
 * l'afficher sans relire ni reparser un fichier national de plusieurs mégaoctets.
 */
object OutageServerInfo {

    const val KEY_LAST_UPDATE = "last_hs_update"
    const val KEY_GENERATED_AT = "last_hs_generated_at"
    const val KEY_DOWNLOADED_AT = "last_hs_downloaded_at"
    const val KEY_COUNT = "last_hs_count"
    const val KEY_BREAKDOWN = "last_hs_breakdown"
    const val KEY_TECH_BREAKDOWN = "last_hs_tech_breakdown"

    /** Date de la donnée serveur, « - » tant qu'aucun téléchargement n'a abouti. */
    fun lastUpdate(prefs: SharedPreferences): String =
        prefs.getString(KEY_LAST_UPDATE, null)?.takeIf { it.isNotBlank() } ?: "-"

    /** Instant (ms) de production du fichier par le serveur, 0 si le serveur ne le publie pas. */
    fun generatedAtMillis(prefs: SharedPreferences): Long = prefs.getLong(KEY_GENERATED_AT, 0L)

    /** Instant (ms) du dernier téléchargement réussi, 0 si aucun. */
    fun downloadedAtMillis(prefs: SharedPreferences): Long = prefs.getLong(KEY_DOWNLOADED_AT, 0L)

    /** Nombre de pannes du dernier téléchargement réussi, -1 si inconnu. */
    fun count(prefs: SharedPreferences): Int = prefs.getInt(KEY_COUNT, -1)

    /** Répartition par opérateur du dernier téléchargement réussi, vide si inconnue. */
    fun breakdown(prefs: SharedPreferences): List<Pair<String, Int>> =
        OutageLocalConfig.decodeBreakdown(prefs.getString(KEY_BREAKDOWN, null))

    /** Détail par génération (2G/3G/4G/5G) du dernier téléchargement réussi, vide si inconnu. */
    fun techBreakdown(prefs: SharedPreferences): List<OutageTechRow> =
        OutageTechBreakdown.decode(prefs.getString(KEY_TECH_BREAKDOWN, null))

    /**
     * Range un détail par génération recalculé après coup, pour une copie téléchargée AVANT que le
     * résumé ne le retienne : sans ça, le tableau des réglages resterait vide jusqu'au prochain
     * téléchargement alors que la donnée est déjà sur l'appareil.
     */
    fun recordTechBreakdown(prefs: SharedPreferences, rows: List<OutageTechRow>) {
        prefs.edit().putString(KEY_TECH_BREAKDOWN, OutageTechBreakdown.encode(rows)).apply()
    }

    /**
     * Mémorise ce qu'un téléchargement réussi vient d'apporter et renvoie l'instant de production
     * du fichier par le serveur (0 s'il ne l'annonce pas), que l'appelant range dans sa copie.
     *
     * Un `generatedAtIso` absent ou illisible efface la clé : un horodatage périmé serait pire que
     * pas d'horodatage du tout.
     */
    fun recordDownload(
        prefs: SharedPreferences,
        lastUpdate: String,
        generatedAtIso: String?,
        sites: List<SiteHsEntity>,
        downloadedAtMillis: Long,
    ): Long {
        val generatedAt = LocalizedDateLabels.isoInstantMillis(generatedAtIso)
        val editor = prefs.edit()
            .putString(KEY_LAST_UPDATE, lastUpdate)
            .putLong(KEY_DOWNLOADED_AT, downloadedAtMillis)
            .putInt(KEY_COUNT, sites.size)
            .putString(KEY_BREAKDOWN, OutageLocalConfig.encodeBreakdown(OutageLocalConfig.breakdownOf(sites)))
            .putString(KEY_TECH_BREAKDOWN, OutageTechBreakdown.encode(OutageTechBreakdown.of(sites)))
        if (generatedAt > 0L) {
            editor.putLong(KEY_GENERATED_AT, generatedAt)
        } else {
            editor.remove(KEY_GENERATED_AT)
        }
        editor.apply()
        return generatedAt
    }

    /** Oublie tout du fichier serveur (suppression de la copie depuis les réglages). */
    fun clear(prefs: SharedPreferences) {
        prefs.edit()
            .remove(KEY_LAST_UPDATE)
            .remove(KEY_GENERATED_AT)
            .remove(KEY_DOWNLOADED_AT)
            .remove(KEY_COUNT)
            .remove(KEY_BREAKDOWN)
            .remove(KEY_TECH_BREAKDOWN)
            .apply()
    }
}
