package fr.geotower.data.outages

import fr.geotower.data.AnfrRepository
import fr.geotower.utils.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Suivi d'une régénération demandée au SERVEUR : demande, attente, puis retéléchargement du
 * nouveau relevé.
 *
 * Porté par un objet de process, et non par la composition de la carte des réglages : la génération
 * dure une poignée de minutes côté serveur, l'utilisateur ne va pas rester sur la page à la
 * regarder. En quittant les réglages puis en revenant, il retrouve l'avancement au lieu d'un bouton
 * qui a l'air de n'avoir rien fait.
 *
 * Pas de WorkManager pour autant : rien n'est à reprendre après la mort du process, la génération
 * se poursuit sur le serveur et le prochain téléchargement automatique ramènera le fichier.
 */
object ServerOutageRebuildMonitor {

    private const val TAG = "GeoTowerOutages"

    /** Rythme d'interrogation du serveur pendant la génération. */
    private const val POLL_INTERVAL_MS = 5_000L

    /** Au-delà, on cesse d'attendre : le serveur coupe lui-même sa génération bien avant. */
    private const val MAX_WAIT_MS = 15 * 60_000L

    enum class Phase {
        IDLE,

        /** Demande envoyée, réponse du serveur pas encore reçue. */
        REQUESTING,

        /** Le serveur fabrique le fichier. */
        RUNNING,

        /** Fichier prêt : on récupère le nouveau relevé. */
        DOWNLOADING,

        /** Relevé régénéré ET téléchargé. */
        DONE,

        /** Quota de l'heure épuisé : voir [UiState.retryAtMillis]. */
        REFUSED,

        FAILED,
    }

    data class UiState(
        val phase: Phase = Phase.IDLE,
        /** Générations par heure annoncées par le serveur, 0 tant qu'il ne l'a pas dit. */
        val quotaPerHour: Int = 0,
        /** Créneaux libres dans l'heure, -1 tant que le serveur ne l'a pas dit. */
        val remaining: Int = -1,
        val refusal: ServerOutageRebuildRefusal = ServerOutageRebuildRefusal.NONE,
        /** Instant à partir duquel une nouvelle demande a une chance de passer, 0 si maintenant. */
        val retryAtMillis: Long = 0L,
        /** Motif d'échec renvoyé par le serveur (non traduit), null si l'échec est côté app. */
        val serverError: String? = null,
    ) {
        val busy: Boolean
            get() = phase == Phase.REQUESTING || phase == Phase.RUNNING || phase == Phase.DOWNLOADING
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * Relit le quota sans rien déclencher, pour que le bouton annonce ce qui reste avant qu'on
     * l'appuie. Ne touche pas à une demande en cours.
     */
    fun refreshQuota(repository: AnfrRepository) {
        if (_state.value.busy) return
        forgetSettledResult()
        scope.launch {
            val status = runCatching { repository.getServerSitesHsRebuildStatus() }.getOrNull()
                ?: return@launch
            if (_state.value.busy) return@launch
            _state.value = _state.value.copy(
                quotaPerHour = status.quotaPerHour,
                remaining = status.remaining,
                // Le serveur dit aussi ce qu'il refuserait MAINTENANT : le bouton peut donc être
                // grisé avant l'appui plutôt que de laisser l'utilisateur récolter un refus.
                refusal = status.refusal,
                retryAtMillis = retryDeadline(status),
            )
        }
    }

    /** Demande une régénération, puis suit la génération jusqu'au retéléchargement du relevé. */
    fun request(repository: AnfrRepository) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UiState(phase = Phase.REQUESTING, quotaPerHour = _state.value.quotaPerHour)

            val status = try {
                repository.requestServerSitesHsRebuild()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "Sites HS rebuild request failed", e)
                _state.value = _state.value.copy(phase = Phase.FAILED, serverError = e.message)
                return@launch
            }

            applyQuota(status)
            if (status.rateLimited) {
                _state.value = _state.value.copy(
                    phase = Phase.REFUSED,
                    refusal = status.refusal,
                    retryAtMillis = retryDeadline(status),
                )
                return@launch
            }

            follow(repository)
        }
    }

    /** Le serveur a pris la demande : on attend la fin, puis on va chercher le fichier. */
    private suspend fun follow(repository: AnfrRepository) {
        _state.value = _state.value.copy(phase = Phase.RUNNING)

        val deadline = System.currentTimeMillis() + MAX_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            // Un trou réseau pendant l'attente n'annule pas la génération : on réessaiera au tour
            // suivant, elle continue sur le serveur.
            val status = runCatching { repository.getServerSitesHsRebuildStatus() }.getOrNull()
                ?: continue
            applyQuota(status)
            when (status.state) {
                ServerOutageRebuildState.RUNNING -> continue
                ServerOutageRebuildState.FAILED -> {
                    _state.value = _state.value.copy(
                        phase = Phase.FAILED,
                        serverError = status.serverError,
                    )
                    return
                }
                // DONE, et IDLE (état perdu par un redémarrage du serveur) : dans les deux cas le
                // fichier servi est le plus récent que le serveur ait, autant aller le chercher.
                ServerOutageRebuildState.DONE, ServerOutageRebuildState.IDLE -> {
                    download(repository)
                    return
                }
            }
        }
        _state.value = _state.value.copy(phase = Phase.FAILED)
    }

    private suspend fun download(repository: AnfrRepository) {
        _state.value = _state.value.copy(phase = Phase.DOWNLOADING)
        val downloaded = runCatching { repository.downloadServerSitesHs() }
        _state.value = _state.value.copy(
            phase = if (downloaded.isSuccess) Phase.DONE else Phase.FAILED,
            serverError = downloaded.exceptionOrNull()?.message,
        )
    }

    /**
     * Une nouvelle visite de la page n'a pas à porter le résultat de la précédente : « relevé refait »
     * ou un échec redeviennent le compteur de créneaux. Un refus, lui, tient tant que son délai
     * court : c'est ce qui garde le bouton grisé.
     */
    private fun forgetSettledResult() {
        val current = _state.value
        val settled = current.phase == Phase.DONE ||
            current.phase == Phase.FAILED ||
            (current.phase == Phase.REFUSED && current.retryAtMillis <= System.currentTimeMillis())
        if (!settled) return
        _state.value = current.copy(
            phase = Phase.IDLE,
            refusal = ServerOutageRebuildRefusal.NONE,
            retryAtMillis = 0L,
            serverError = null,
        )
    }

    private fun applyQuota(status: ServerOutageRebuildStatus) {
        _state.value = _state.value.copy(
            quotaPerHour = status.quotaPerHour,
            remaining = status.remaining,
        )
    }

    private fun retryDeadline(status: ServerOutageRebuildStatus): Long =
        if (status.retryAfterSeconds > 0) {
            System.currentTimeMillis() + status.retryAfterSeconds * 1_000L
        } else {
            0L
        }
}
