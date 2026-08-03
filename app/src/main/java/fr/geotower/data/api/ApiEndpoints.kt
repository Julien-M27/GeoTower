package fr.geotower.data.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import fr.geotower.utils.AppLogger
import fr.geotower.utils.PreferenceStores
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Serveurs API GeoTower. Le miroir sert **le même code** (`docs/server/`) : mêmes routes
 * `/api/v2/...`, même manifeste signé par la même clé. C'est un second hébergement, pas une autre
 * API. Toute URL de l'app visant l'un ou l'autre est réécrite à la volée vers le serveur actif par
 * l'intercepteur de [RetrofitClient].
 */
enum class ApiServer(val host: String) {
    /** Serveur principal. */
    PRIMARY("api.geotower.fr"),

    /** Miroir de secours, utilisé quand le principal ne répond plus. */
    MIRROR("api.cajejuma.fr");

    val baseUrl: String get() = "https://$host/"
}

/**
 * Une valeur et **le serveur qui l'a servie**. Indispensable pour les téléchargements vérifiés :
 * les deux serveurs peuvent servir des fichiers construits séparément, donc de SHA-256 différents.
 * Le manifeste lu sur un serveur n'engage que ce serveur — télécharger le fichier sur l'autre ferait
 * échouer le contrôle d'intégrité après 145 Mo.
 */
data class ServedFrom<T>(val value: T, val host: String)

/** Choix de l'utilisateur (page Diagnostic) : bascule automatique, ou serveur imposé. */
enum class ApiServerMode {
    /** Principal tant qu'il répond, miroir sinon, retour automatique au principal. */
    AUTO,

    /** Toujours `api.geotower.fr`, même injoignable (utile pour reproduire une panne). */
    FORCE_PRIMARY,

    /** Toujours `api.cajejuma.fr`. */
    FORCE_MIRROR
}

/**
 * Aiguillage principal / miroir : serveur actif, réglage utilisateur et date de dernière bascule.
 *
 * Les décisions réseau sont prises sur des champs `@Volatile` (l'intercepteur tourne sur un thread
 * d'E/S) ; les [State] Compose n'en sont qu'un reflet, publié sur le thread principal pour l'UI.
 * Volontairement sans dépendance à `AppConfig`, comme le reste de la couche réseau.
 */
object ApiEndpoints {
    private const val TAG = "GeoTowerApi"

    const val PREF_MODE = "api_server_mode"

    /**
     * En-tête interne : la requête vise **cet** hôte précis et ne doit être ni réécrite ni
     * rebasculée. Posé par la sonde [ServerReachability], retiré par l'intercepteur avant l'envoi.
     */
    const val PIN_HOST_HEADER = "X-GeoTower-Pin-Host"

    /** En mode AUTO, on retente le principal au bout de ce délai passé sur le miroir. */
    private const val PRIMARY_RETRY_INTERVAL_MS = 10 * 60 * 1000L

    @Volatile
    private var currentMode = ApiServerMode.AUTO

    @Volatile
    private var autoServer = ApiServer.PRIMARY

    @Volatile
    private var mirrorSince = 0L

    private val modeState = mutableStateOf(ApiServerMode.AUTO)

    /** Réglage utilisateur courant. */
    val mode: State<ApiServerMode> get() = modeState

    private val activeServerState = mutableStateOf(ApiServer.PRIMARY)

    /** Serveur réellement utilisé par les requêtes (réglage + bascule automatique). */
    val activeServer: State<ApiServer> get() = activeServerState

    private val lastSwitchAtState = mutableStateOf(0L)

    /** Date (epoch ms) de la dernière bascule automatique, 0 si aucune depuis le lancement. */
    val lastSwitchAt: State<Long> get() = lastSwitchAtState

    /** À appeler tôt (Application.onCreate) : relit le réglage persisté. */
    fun init(context: Context) {
        val stored = context.applicationContext
            .getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
            .getString(PREF_MODE, null)
        currentMode = stored
            ?.let { name -> runCatching { ApiServerMode.valueOf(name) }.getOrNull() }
            ?: ApiServerMode.AUTO
        publishState()
    }

    fun setMode(context: Context, newMode: ApiServerMode) {
        currentMode = newMode
        context.applicationContext
            .getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_MODE, newMode.name)
            .apply()
        // Changer de réglage repart d'une ardoise propre : on ne traîne pas une bascule décidée
        // sous l'ancien mode.
        if (newMode != ApiServerMode.AUTO) {
            autoServer = ApiServer.PRIMARY
            mirrorSince = 0L
        }
        publishState()
    }

    /** Serveur à utiliser pour la prochaine requête. */
    fun active(): ApiServer = when (currentMode) {
        ApiServerMode.FORCE_PRIMARY -> ApiServer.PRIMARY
        ApiServerMode.FORCE_MIRROR -> ApiServer.MIRROR
        ApiServerMode.AUTO -> autoServer
    }

    /** Hôte connu de l'API GeoTower (principal ou miroir) : sert aussi aux contrôles de sécurité. */
    fun isOfficialApiHost(host: String?): Boolean = serverForHost(host) != null

    fun serverForHost(host: String?): ApiServer? {
        val normalized = host?.trim().orEmpty()
        return ApiServer.entries.firstOrNull { normalized.equals(it.host, ignoreCase = true) }
    }

    /**
     * Même URL, mais sur [host]. Utilisé pour épingler un téléchargement au serveur qui a servi son
     * manifeste (à poser avec [PIN_HOST_HEADER], qui interdit en plus tout rejeu sur l'autre serveur).
     */
    fun urlOnHost(url: String, host: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        if (parsed.host.equals(host, ignoreCase = true)) return url
        return parsed.newBuilder().host(host).build().toString()
    }

    /** L'autre serveur, ou `null` si la bascule est interdite (mode forcé). */
    fun failoverTarget(server: ApiServer): ApiServer? {
        if (currentMode != ApiServerMode.AUTO) return null
        return if (server == ApiServer.PRIMARY) ApiServer.MIRROR else ApiServer.PRIMARY
    }

    /** true si, après un moment passé sur le miroir, il est temps de retenter le principal. */
    fun shouldRetryPrimary(): Boolean {
        if (currentMode != ApiServerMode.AUTO) return false
        if (autoServer != ApiServer.MIRROR) return false
        return System.currentTimeMillis() - mirrorSince >= PRIMARY_RETRY_INTERVAL_MS
    }

    /**
     * Bascule sur [server], qui vient de répondre. Sans effet en mode forcé, ou si c'est déjà le
     * serveur actif. Un échec ne bascule jamais tout seul : l'appelant doit d'abord constater que
     * l'autre serveur répond, sinon l'app tournerait entre deux serveurs morts.
     */
    fun switchTo(server: ApiServer) {
        if (currentMode != ApiServerMode.AUTO || autoServer == server) return
        AppLogger.i(TAG, "API server switch: ${autoServer.host} -> ${server.host}")
        autoServer = server
        mirrorSince = if (server == ApiServer.MIRROR) System.currentTimeMillis() else 0L
        publishState(switchedAt = System.currentTimeMillis())
    }

    private fun publishState(switchedAt: Long? = null) {
        val resolvedMode = currentMode
        val resolvedServer = active()
        val publish = Runnable {
            modeState.value = resolvedMode
            activeServerState.value = resolvedServer
            switchedAt?.let { lastSwitchAtState.value = it }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            publish.run()
        } else {
            Handler(Looper.getMainLooper()).post(publish)
        }
    }
}
