package fr.geotower.data.api

import com.google.gson.JsonParser
import fr.geotower.BuildConfig
import fr.geotower.utils.FrenchAdminAreas
import fr.geotower.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/** Données de cadrage d'une commune ou d'une zone administrative utilisées pour les ratios. */
data class GeoAreaReference(
    val codeInsee: String,
    val name: String?,
    val areaKm2: Double?,
    val population: Int?,
    /** L'API ne fournit pas le millésime dans cette réponse. */
    val populationYear: String? = null
)

/** Compatibilité source pour les appels qui concernent encore explicitement une commune. */
typealias CommuneReference = GeoAreaReference

/**
 * Référentiel public et léger de l'administration française.
 *
 * `surface` est exprimée en hectares par geo.api.gouv.fr, comme dans le builder local des
 * statistiques départementales ; elle est convertie en km² avant d'être exposée à l'UI.
 */
object CommuneReferenceApi {
    private const val TAG = "GeoTowerCommuneReference"
    private const val BASE_URL = "https://geo.api.gouv.fr/"
    private val userAgent = "GeoTower/${BuildConfig.VERSION_NAME} (Android)"
    private val memoryCache = ConcurrentHashMap<String, CommuneReference>()

    fun get(codeInsee: String): CommuneReference? {
        val normalizedCode = codeInsee.trim().uppercase()
        if (normalizedCode.isBlank()) return null
        memoryCache[normalizedCode]?.let { return it }

        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("communes")
            .addPathSegment(normalizedCode)
            .addQueryParameter("fields", "nom,code,population,surface")
            .addQueryParameter("format", "json")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .get()
            .build()

        return try {
            RetrofitClient.currentClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w(TAG, "Commune reference request failed code=${response.code}")
                    return@use null
                }
                val body = response.body?.string() ?: return@use null
                parse(body, normalizedCode)?.also { memoryCache[normalizedCode] = it }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Commune reference request failed", e)
            null
        }
    }

    internal fun parse(json: String, requestedCode: String): CommuneReference? {
        return runCatching {
            val objectValue = JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject
                ?: return@runCatching null
            val code = objectValue.get("code")?.takeIf { !it.isJsonNull }?.asString
                ?.trim()
                ?.uppercase()
                ?.takeIf { it.isNotBlank() }
                ?: requestedCode
            val name = objectValue.get("nom")?.takeIf { !it.isJsonNull }?.asString
                ?.takeIf { it.isNotBlank() }
            val population = objectValue.get("population")?.takeIf { !it.isJsonNull }
                ?.asInt
                ?.takeIf { it >= 0 }
            val surfaceHectares = objectValue.get("surface")?.takeIf { !it.isJsonNull }
                ?.asDouble
                ?.takeIf { it >= 0.0 }

            CommuneReference(
                codeInsee = code,
                name = name,
                areaKm2 = surfaceHectares?.div(100.0),
                population = population
            )
        }.getOrNull()
    }
}

/**
 * Référentiel public des départements et régions.
 *
 * L'API expose parfois directement population/surface sur l'objet département ou région. Quand
 * ces champs ne sont pas présents, on retombe sur les communes des départements couverts ; cette
 * seconde voie est plus lente, mais elle fonctionne également pour les régions et les collectivités
 * dont la fiche agrégée est incomplète.
 */
object AdministrativeAreaReferenceApi {
    private const val TAG = "GeoTowerAdministrativeReference"
    private const val BASE_URL = "https://geo.api.gouv.fr/"
    private val userAgent = "GeoTower/${BuildConfig.VERSION_NAME} (Android)"
    private val memoryCache = ConcurrentHashMap<String, GeoAreaReference>()

    suspend fun get(area: FrenchAdminAreas.Area): GeoAreaReference? = withContext(Dispatchers.IO) {
        val normalizedCode = area.code.trim().uppercase()
        if (normalizedCode.isBlank()) return@withContext null
        val cacheKey = "${area.kind.name}:$normalizedCode"
        memoryCache[cacheKey]?.let { return@withContext it }

        val resource = if (area.kind == FrenchAdminAreas.Kind.REGION) "regions" else "departements"
        val direct = fetchJson("$resource/$normalizedCode")
            ?.let { parseAreaObject(it, normalizedCode, area.name) }

        // Les champs directs sont préférés : une seule requête et les valeurs sont celles du
        // référentiel lui-même. Les champs manquants sont complétés par l'agrégation ci-dessous.
        if (direct?.areaKm2 != null && direct.population != null) {
            memoryCache[cacheKey] = direct
            return@withContext direct
        }

        val departmentTotals = coroutineScope {
            area.departmentCodes
                .distinct()
                .map { departmentCode ->
                    async { fetchDepartmentTotals(departmentCode) }
                }
                .awaitAll()
                .filterNotNull()
        }
        val aggregatedSurfaceHectares = departmentTotals.sumOf { it.surfaceHectares }
        val aggregatedPopulation = departmentTotals.sumOf { it.population }
        val result = GeoAreaReference(
            codeInsee = direct?.codeInsee ?: normalizedCode,
            name = direct?.name ?: area.name,
            areaKm2 = direct?.areaKm2 ?: aggregatedSurfaceHectares
                .takeIf { it > 0.0 }
                ?.div(100.0),
            population = direct?.population ?: aggregatedPopulation.takeIf { it > 0 },
            populationYear = direct?.populationYear
        )

        if (result.areaKm2 != null || result.population != null) {
            memoryCache[cacheKey] = result
            result
        } else {
            null
        }
    }

    private suspend fun fetchDepartmentTotals(departmentCode: String): AreaTotals? {
        val json = fetchJson("departements/${departmentCode.trim().uppercase()}/communes") ?: return null
        return parseCommunesTotals(json)
    }

    private fun fetchJson(path: String): String? {
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegments(path)
            .addQueryParameter("fields", "nom,code,population,surface")
            .addQueryParameter("format", "json")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .get()
            .build()

        return try {
            RetrofitClient.currentClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w(TAG, "Administrative reference request failed code=${response.code}")
                    return@use null
                }
                response.body?.string()
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Administrative reference request failed", e)
            null
        }
    }

    private fun parseAreaObject(json: String, requestedCode: String, fallbackName: String): GeoAreaReference? {
        return runCatching {
            val objectValue = JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject
                ?: return@runCatching null
            val code = objectValue.get("code")?.takeIf { !it.isJsonNull }?.asString
                ?.trim()
                ?.uppercase()
                ?.takeIf { it.isNotBlank() }
                ?: requestedCode
            val name = objectValue.get("nom")?.takeIf { !it.isJsonNull }?.asString
                ?.takeIf { it.isNotBlank() }
                ?: fallbackName
            val population = objectValue.get("population")?.takeIf { !it.isJsonNull }
                ?.asDouble
                ?.toInt()
                ?.takeIf { it >= 0 }
            val surfaceHectares = objectValue.get("surface")?.takeIf { !it.isJsonNull }
                ?.asDouble
                ?.takeIf { it >= 0.0 }

            GeoAreaReference(
                codeInsee = code,
                name = name,
                areaKm2 = surfaceHectares?.div(100.0),
                population = population
            )
        }.getOrNull()
    }

    internal fun parseCommunesTotals(json: String): AreaTotals? {
        return runCatching {
            val communes = JsonParser.parseString(json).takeIf { it.isJsonArray }?.asJsonArray
                ?: return@runCatching null
            var population = 0
            var surfaceHectares = 0.0
            var count = 0
            communes.forEach { element ->
                val commune = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                population += commune.get("population")?.takeIf { !it.isJsonNull }
                    ?.asDouble
                    ?.toInt()
                    ?: 0
                surfaceHectares += commune.get("surface")?.takeIf { !it.isJsonNull }
                    ?.asDouble
                    ?: 0.0
                count++
            }
            if (count == 0) null else AreaTotals(population, surfaceHectares)
        }.getOrNull()
    }

    internal data class AreaTotals(
        val population: Int,
        val surfaceHectares: Double
    )
}
