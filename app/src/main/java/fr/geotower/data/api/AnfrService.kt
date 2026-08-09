package fr.geotower.data.api

import com.google.gson.annotations.SerializedName
import fr.geotower.data.models.OfflineMapDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST

data class SitesHsInfoDto(
    @SerializedName("last_update")
    val lastUpdate: String? = null
)

/**
 * Réponse des routes de régénération des pannes, servie telle quelle par le serveur qu'il accepte
 * la demande (202), qu'une génération soit déjà en cours (200) ou que le quota soit épuisé (429).
 * C'est pour ce dernier cas que l'appelant lit un [Response] : le corps d'un 429 porte le délai.
 */
data class SitesHsRebuildDto(
    /** `idle`, `running`, `done` ou `failed` : où en est la dernière génération connue du serveur. */
    val state: String? = null,
    val running: Boolean = false,
    /** true seulement si CETTE demande a déclenché une génération. */
    val started: Boolean = false,
    @SerializedName("quota_per_hour")
    val quotaPerHour: Int = 0,
    @SerializedName("used_last_hour")
    val usedLastHour: Int = 0,
    val remaining: Int = -1,
    @SerializedName("retry_after_seconds")
    val retryAfterSeconds: Int = 0,
    /** `quota_global` ou `quota_client` quand la demande est refusée, absent sinon. */
    val reason: String? = null,
    @SerializedName("started_at")
    val startedAt: String? = null,
    @SerializedName("finished_at")
    val finishedAt: String? = null,
    val error: String? = null,
    @SerializedName("last_update")
    val lastUpdate: String? = null,
    /** Date de production du fichier actuellement servi : sert à voir qu'il a bien été remplacé. */
    @SerializedName("file_updated_at")
    val fileUpdatedAt: String? = null
)

/** Corps d'erreur standard de l'API (`{"detail": "…"}`), lu quand la demande n'aboutit pas. */
data class ServerDetailDto(val detail: String? = null)

interface AnfrService {
    @GET("/api/v2/download/manifest")
    suspend fun getDownloadManifest(): okhttp3.ResponseBody

    @GET("/api/v2/maps/catalog")
    suspend fun getMapsCatalog(): List<OfflineMapDto>

    @GET("/api/v2/antennes/hs")
    suspend fun getSitesHsGeoJson(): okhttp3.ResponseBody

    @GET("/api/v2/antennes/hs/info")
    suspend fun getSitesHsInfo(): SitesHsInfoDto

    @GET("/api/v2/antennes/hs/rebuild")
    suspend fun getSitesHsRebuildStatus(): SitesHsRebuildDto

    /** Demande une régénération du fichier national. Sans corps : le serveur n'attend rien. */
    @POST("/api/v2/antennes/hs/rebuild")
    suspend fun requestSitesHsRebuild(): Response<SitesHsRebuildDto>
}
