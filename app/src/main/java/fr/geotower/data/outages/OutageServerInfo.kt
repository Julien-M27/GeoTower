package fr.geotower.data.outages

import android.content.SharedPreferences
import fr.geotower.data.models.SiteHsEntity
import fr.geotower.utils.LocalizedDateLabels

import fr.geotower.utils.AppConfig

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

    private fun keyFor(baseKey: String, mode: OutageSourceMode): String =
        if (mode == OutageSourceMode.OPERATORS) baseKey else "${baseKey}_${mode.key}"

    /** Date de la donnée serveur, « - » tant qu'aucun téléchargement n'a abouti. */
    fun lastUpdate(prefs: SharedPreferences, mode: OutageSourceMode = AppConfig.outageSourceMode.value): String =
        prefs.getString(keyFor(KEY_LAST_UPDATE, mode), null)?.takeIf { it.isNotBlank() } ?: "-"

    /** Instant (ms) de production du fichier par le serveur, 0 si le serveur ne le publie pas. */
    fun generatedAtMillis(prefs: SharedPreferences, mode: OutageSourceMode = AppConfig.outageSourceMode.value): Long =
        prefs.getLong(keyFor(KEY_GENERATED_AT, mode), 0L)

    /** Instant (ms) du dernier téléchargement réussi, 0 si aucun. */
    fun downloadedAtMillis(prefs: SharedPreferences, mode: OutageSourceMode = AppConfig.outageSourceMode.value): Long =
        prefs.getLong(keyFor(KEY_DOWNLOADED_AT, mode), 0L)

    /** Nombre de pannes du dernier téléchargement réussi, -1 si inconnu. */
    fun count(prefs: SharedPreferences, mode: OutageSourceMode = AppConfig.outageSourceMode.value): Int =
        prefs.getInt(keyFor(KEY_COUNT, mode), -1)

    /** Répartition par opérateur du dernier téléchargement réussi, vide si inconnue. */
    fun breakdown(prefs: SharedPreferences, mode: OutageSourceMode = AppConfig.outageSourceMode.value): List<Pair<String, Int>> =
        OutageLocalConfig.decodeBreakdown(prefs.getString(keyFor(KEY_BREAKDOWN, mode), null))

    /** Détail par génération (2G/3G/4G/5G) du dernier téléchargement réussi, vide si inconnu. */
    fun techBreakdown(prefs: SharedPreferences, mode: OutageSourceMode = AppConfig.outageSourceMode.value): List<OutageTechRow> =
        OutageTechBreakdown.decode(prefs.getString(keyFor(KEY_TECH_BREAKDOWN, mode), null))

    /**
     * Range un détail par génération recalculé après coup, pour une copie téléchargée AVANT que le
     * résumé ne le retienne : sans ça, le tableau des réglages resterait vide jusqu'au prochain
     * téléchargement alors que la donnée est déjà sur l'appareil.
     */
    fun recordTechBreakdown(
        prefs: SharedPreferences,
        rows: List<OutageTechRow>,
        mode: OutageSourceMode = AppConfig.outageSourceMode.value,
    ) {
        prefs.edit().putString(keyFor(KEY_TECH_BREAKDOWN, mode), OutageTechBreakdown.encode(rows)).apply()
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
        mode: OutageSourceMode = AppConfig.outageSourceMode.value,
    ): Long {
        val generatedAt = LocalizedDateLabels.isoInstantMillis(generatedAtIso)
        val genKey = keyFor(KEY_GENERATED_AT, mode)
        val editor = prefs.edit()
            .putString(keyFor(KEY_LAST_UPDATE, mode), lastUpdate)
            .putLong(keyFor(KEY_DOWNLOADED_AT, mode), downloadedAtMillis)
            .putInt(keyFor(KEY_COUNT, mode), sites.size)
            .putString(keyFor(KEY_BREAKDOWN, mode), OutageLocalConfig.encodeBreakdown(OutageLocalConfig.breakdownOf(sites)))
            .putString(keyFor(KEY_TECH_BREAKDOWN, mode), OutageTechBreakdown.encode(OutageTechBreakdown.of(sites)))
        if (generatedAt > 0L) {
            editor.putLong(genKey, generatedAt)
        } else {
            editor.remove(genKey)
        }
        editor.apply()
        return generatedAt
    }

    /** Oublie tout du fichier serveur (suppression de la copie depuis les réglages). */
    fun clear(prefs: SharedPreferences, mode: OutageSourceMode = AppConfig.outageSourceMode.value) {
        val genKey = keyFor(KEY_GENERATED_AT, mode)
        prefs.edit()
            .remove(keyFor(KEY_LAST_UPDATE, mode))
            .remove(genKey)
            .remove(keyFor(KEY_DOWNLOADED_AT, mode))
            .remove(keyFor(KEY_COUNT, mode))
            .remove(keyFor(KEY_BREAKDOWN, mode))
            .remove(keyFor(KEY_TECH_BREAKDOWN, mode))
            .apply()
    }
}
