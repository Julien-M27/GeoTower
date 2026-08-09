package fr.geotower.data.outages

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import fr.geotower.data.api.ServerDetailDto
import fr.geotower.data.api.SitesHsRebuildDto
import fr.geotower.utils.LocalizedDateLabels

/** Refus de quota du serveur : deux générations par heure, pas une de plus. */
const val HTTP_TOO_MANY_REQUESTS = 429

/** Où en est la génération du fichier national, côté serveur. */
enum class ServerOutageRebuildState {
    /** Aucune génération connue (serveur redémarré, fichier d'état neuf). */
    IDLE,
    RUNNING,
    DONE,
    FAILED;

    companion object {
        fun fromWire(value: String?): ServerOutageRebuildState = when (value?.trim()?.lowercase()) {
            "running" -> RUNNING
            "done" -> DONE
            "failed" -> FAILED
            else -> IDLE
        }
    }
}

/** Pourquoi le serveur a refusé la demande. Le quota est la seule raison prévue. */
enum class ServerOutageRebuildRefusal {
    NONE,

    /** Les créneaux de l'heure sont déjà pris, tous appareils confondus. */
    GLOBAL_QUOTA,

    /** Cet appareil a déjà consommé sa demande de l'heure. */
    CLIENT_QUOTA;

    companion object {
        fun fromWire(value: String?): ServerOutageRebuildRefusal = when (value?.trim()?.lowercase()) {
            "quota_global" -> GLOBAL_QUOTA
            "quota_client" -> CLIENT_QUOTA
            else -> NONE
        }
    }
}

/**
 * Ce que le serveur répond quand on lui demande de régénérer les pannes, ou qu'on l'interroge sur
 * l'état de la génération en cours.
 *
 * Un refus de quota n'est PAS une erreur : le serveur n'accepte que deux générations par heure, et
 * cette réponse-là porte le délai avant le prochain créneau. C'est pour ça que l'appelant reçoit un
 * statut plutôt qu'une exception ; seules les vraies pannes (réseau, 500) remontent en exception.
 */
data class ServerOutageRebuildStatus(
    val state: ServerOutageRebuildState = ServerOutageRebuildState.IDLE,
    /** true seulement si la demande vient de déclencher une génération (202 du serveur). */
    val startedByThisRequest: Boolean = false,
    /** true si le serveur a refusé faute de créneau (429). */
    val rateLimited: Boolean = false,
    val refusal: ServerOutageRebuildRefusal = ServerOutageRebuildRefusal.NONE,
    /** Générations autorisées par heure, telles que le serveur les annonce. */
    val quotaPerHour: Int = 0,
    /** Créneaux encore libres dans l'heure, -1 si le serveur ne plafonne pas. */
    val remaining: Int = -1,
    /** Secondes avant le prochain créneau, 0 si un créneau est libre. */
    val retryAfterSeconds: Int = 0,
    /** Motif d'échec renvoyé par le serveur, non traduit : affiché tel quel, comme un détail. */
    val serverError: String? = null,
    /** Instant de production du fichier actuellement servi, 0 si inconnu. */
    val fileUpdatedAtMillis: Long = 0L,
) {
    val running: Boolean get() = state == ServerOutageRebuildState.RUNNING
}

private val rebuildGson = Gson()

/** Convertit la réponse réseau, en tenant compte du code HTTP : 429 = refus de quota. */
fun SitesHsRebuildDto.toRebuildStatus(httpCode: Int): ServerOutageRebuildStatus =
    ServerOutageRebuildStatus(
        state = ServerOutageRebuildState.fromWire(state),
        startedByThisRequest = started,
        rateLimited = httpCode == HTTP_TOO_MANY_REQUESTS,
        refusal = ServerOutageRebuildRefusal.fromWire(reason),
        quotaPerHour = quotaPerHour.coerceAtLeast(0),
        remaining = remaining,
        retryAfterSeconds = retryAfterSeconds.coerceAtLeast(0),
        serverError = error?.takeIf { it.isNotBlank() },
        fileUpdatedAtMillis = LocalizedDateLabels.isoInstantMillis(fileUpdatedAt),
    )

/** Corps d'un 429 : le serveur y met le même JSON que pour une demande acceptée. */
fun parseSitesHsRebuildBody(raw: String?): SitesHsRebuildDto? {
    val json = raw?.takeIf { it.isNotBlank() } ?: return null
    return try {
        rebuildGson.fromJson(json, SitesHsRebuildDto::class.java)
    } catch (_: JsonSyntaxException) {
        null
    }
}

/** Message d'erreur d'une réponse FastAPI (`{"detail": "…"}`), null si le corps n'en porte pas. */
fun parseServerDetail(raw: String?): String? {
    val json = raw?.takeIf { it.isNotBlank() } ?: return null
    return try {
        rebuildGson.fromJson(json, ServerDetailDto::class.java)?.detail?.takeIf { it.isNotBlank() }
    } catch (_: JsonSyntaxException) {
        null
    }
}
