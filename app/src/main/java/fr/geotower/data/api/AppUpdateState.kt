package fr.geotower.data.api

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import fr.geotower.BuildConfig
import fr.geotower.utils.PreferenceStores
import kotlinx.coroutines.sync.Mutex

/**
 * Dernière version connue de l'application, lisible par l'interface.
 *
 * [fr.geotower.data.workers.AppUpdateNotifier] ne mémorise que « j'ai déjà notifié pour cette
 * release » : couper les notifications de mise à jour suffisait à ce que plus rien ne sache qu'une
 * version existe, et le worker n'est alors même plus planifié (cf.
 * [fr.geotower.data.workers.UpdateCheckScheduler]). La connaissance de la dernière version et la
 * décision de notifier sont donc séparées ici.
 */
object AppUpdateState {
    private const val PREF_VERSION = "known_latest_app_version"
    private const val PREF_URL = "known_latest_app_url"
    private const val PREF_CHECKED_AT = "known_latest_app_checked_at"

    /** Au-delà, une ouverture du tiroir déclenche une vérification. */
    private const val STALE_AFTER_MILLIS = 6L * 60L * 60L * 1000L

    private val refreshMutex = Mutex()
    private val current = mutableStateOf<KnownRelease?>(null)

    /** Version connue plus récente que celle installée, ou `null` si aucune. */
    val newerRelease: State<KnownRelease?> get() = current

    data class KnownRelease(val versionName: String, val downloadUrl: String)

    /** À appeler au démarrage du process : restaure ce qu'on savait au lancement précédent. */
    fun loadCached(context: Context) {
        val prefs = prefs(context)
        // Garde-fou : une installation Play ne doit jamais afficher de bandeau « nouvelle version »,
        // quelle que soit la façon dont les préférences ont été remplies (cf. AppDistribution).
        if (fr.geotower.utils.AppDistribution.isPlayInstall) {
            current.value = null
            prefs.edit().remove(PREF_VERSION).remove(PREF_URL).apply()
            return
        }
        val versionName = prefs.getString(PREF_VERSION, null)?.takeIf { it.isNotBlank() }
        val downloadUrl = prefs.getString(PREF_URL, null)?.takeIf { it.isNotBlank() }

        if (versionName != null && downloadUrl != null && isNewer(versionName)) {
            current.value = KnownRelease(versionName, downloadUrl)
            return
        }

        // L'utilisateur a pu installer cette version entre-temps : on ne garde pas un faux bandeau.
        current.value = null
        if (versionName != null || downloadUrl != null) {
            prefs.edit().remove(PREF_VERSION).remove(PREF_URL).apply()
        }
    }

    /** Mémorise une release déjà récupérée, sans appel réseau supplémentaire. */
    fun record(context: Context, release: AppReleaseInfo?) {
        // Échec réseau : on garde ce qu'on savait plutôt que d'effacer le bandeau.
        if (release == null) return

        val editor = prefs(context).edit().putLong(PREF_CHECKED_AT, System.currentTimeMillis())
        if (isNewer(release.versionName) && release.downloadUrl.isNotBlank()) {
            editor.putString(PREF_VERSION, release.versionName)
                .putString(PREF_URL, release.downloadUrl)
            current.value = KnownRelease(release.versionName, release.downloadUrl)
        } else {
            editor.remove(PREF_VERSION).remove(PREF_URL)
            current.value = null
        }
        editor.apply()
    }

    /**
     * Rafraîchit si la dernière vérification date trop.
     *
     * Nécessaire parce que le worker quotidien n'est pas planifié quand les notifications de mise à
     * jour sont coupées. [AppUpdateChecker.getLatestRelease] rend déjà la main sans réseau quand la
     * vérification est désactivée (kill-switch distant ou traitement local « autonomie maximale »).
     */
    suspend fun refreshIfStale(context: Context) {
        val appContext = context.applicationContext
        val checkedAt = prefs(appContext).getLong(PREF_CHECKED_AT, 0L)
        if (System.currentTimeMillis() - checkedAt < STALE_AFTER_MILLIS) return
        if (!refreshMutex.tryLock()) return

        try {
            record(appContext, AppUpdateChecker.getLatestRelease())
        } finally {
            refreshMutex.unlock()
        }
    }

    private fun isNewer(remoteVersionName: String): Boolean =
        AppUpdateChecker.isRemoteVersionNewer(remoteVersionName, BuildConfig.VERSION_NAME)

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
}
