package fr.geotower.data.coverage

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.cos
import kotlin.math.max

/**
 * Charge la grille radiale d'altitudes autour d'un site, **une seule fois** (mutualisée par toutes
 * les antennes du site). Le réseau est injecté ([getElevations], [fetchBuildings]) pour rester
 * testable hors Android.
 *
 * Budget réseau ≈ `ceil(nbRelèvements × nbÉchantillons / [maxPointsPerRequest])` requêtes batch
 * (GET, ~300 points/requête) + 1 requête WFS (si obstacles). Le calcul par antenne ensuite est gratuit (CPU).
 */
class TerrainFieldLoader(
    private val getElevations: suspend (List<DoubleArray>) -> List<Double>,
    private val fetchBuildings: suspend (minLat: Double, minLon: Double, maxLat: Double, maxLon: Double) -> List<BuildingObstacle>,
    private val maxPointsPerRequest: Int = 300,
    private val maxConcurrentRequests: Int = 6
) {
    suspend fun load(
        siteLat: Double,
        siteLon: Double,
        maxRadiusM: Double,
        angularStepDeg: Double,
        sampleStepM: Double,
        includeObstacles: Boolean,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): TerrainField {
        val step = max(1.0, sampleStepM)
        val angStep = max(0.1, angularStepDeg)

        // 1) Relèvements 0..360 + distances croissantes par rayon.
        val bearings = ArrayList<Double>()
        run {
            var b = 0.0
            while (b < 360.0 - 1e-9) {
                bearings.add(b)
                b += angStep
            }
        }
        val distancesPerRay = ArrayList<Double>()
        run {
            var d = step
            while (d <= maxRadiusM + 1e-6) {
                distancesPerRay.add(d)
                d += step
            }
        }

        // 2) Liste plate de tous les points en [lon, lat] : index 0 = site, puis chaque rayon.
        val allPoints = ArrayList<DoubleArray>(1 + bearings.size * distancesPerRay.size)
        allPoints.add(doubleArrayOf(siteLon, siteLat))
        for (bearing in bearings) {
            for (d in distancesPerRay) {
                val ll = CoverageGeo.destinationPoint(siteLat, siteLon, bearing, d) // [lat, lon]
                allPoints.add(doubleArrayOf(ll[1], ll[0]))
            }
        }

        // 3) Altitudes terrain en batch (GET chunké, concurrence bornée, retry sur échec réseau).
        val fetch = fetchElevationsChunked(allPoints, onProgress)
        val z = fetch.z
        val validFraction = if (z.isEmpty()) 0.0 else z.count { !it.isNaN() }.toDouble() / z.size

        // 4) Obstacles bâtis : UNE passe WFS sur la bbox du disque, puis index spatial.
        val index: BuildingIndex? = if (includeObstacles) {
            val latDelta = maxRadiusM / 111_320.0
            val lonDelta = maxRadiusM / (111_320.0 * cos(Math.toRadians(siteLat)).coerceAtLeast(1e-6))
            val buildings = runCatching {
                fetchBuildings(siteLat - latDelta, siteLon - lonDelta, siteLat + latDelta, siteLon + lonDelta)
            }.getOrDefault(emptyList())
            if (buildings.isEmpty()) null else BuildingIndex(buildings)
        } else {
            null
        }

        // 5) Assemblage des rayons : sol terrain (+ toit si bâtiment couvre le point).
        val siteGround = z.getOrElse(0) { Double.NaN }
        val rays = ArrayList<TerrainRay>(bearings.size)
        var cursor = 1
        for (bearing in bearings) {
            val n = distancesPerRay.size
            val dist = DoubleArray(n)
            val ground = DoubleArray(n)
            for (i in 0 until n) {
                val pIdx = cursor + i
                val lon = allPoints[pIdx][0]
                val lat = allPoints[pIdx][1]
                val terrain = z.getOrElse(pIdx) { Double.NaN }
                dist[i] = distancesPerRay[i]
                ground[i] = if (terrain.isNaN() || index == null) {
                    terrain
                } else {
                    index.surfaceAltitude(lon, lat, terrain)
                }
            }
            cursor += n
            rays.add(TerrainRay(bearing, dist, ground))
        }

        return TerrainField(
            siteLat = siteLat,
            siteLon = siteLon,
            siteGroundM = if (siteGround.isNaN()) 0.0 else siteGround,
            rays = rays,
            sampleStepM = step,
            maxRadiusM = maxRadiusM,
            obstaclesIncluded = includeObstacles && index != null && !index.isEmpty,
            validPointFraction = validFraction,
            failedChunks = fetch.failed,
            totalChunks = fetch.total,
            sampleError = fetch.sampleError
        )
    }

    private class FetchResult(
        val z: DoubleArray,
        val failed: Int,
        val total: Int,
        val sampleError: String?
    )

    private suspend fun fetchElevationsChunked(
        points: List<DoubleArray>,
        onProgress: (Int, Int) -> Unit
    ): FetchResult = coroutineScope {
        val chunkSize = maxPointsPerRequest.coerceAtLeast(1)
        val chunks = points.indices.chunked(chunkSize)
        val total = chunks.size
        val result = DoubleArray(points.size) { Double.NaN }
        val done = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val sampleError = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val sem = Semaphore(maxConcurrentRequests.coerceAtLeast(1))
        onProgress(0, total)
        chunks.map { idxList ->
            async {
                sem.withPermit {
                    currentCoroutineContext().ensureActive()
                    val sub = idxList.map { points[it] }
                    val (zs, error) = fetchChunkWithRetry(sub)
                    if (zs != null) {
                        for (k in idxList.indices) {
                            result[idxList[k]] = zs.getOrElse(k) { Double.NaN }
                        }
                    } else {
                        failed.incrementAndGet()
                        if (error != null) sampleError.compareAndSet(null, error)
                    }
                    onProgress(done.incrementAndGet(), total)
                }
            }
        }.awaitAll()
        FetchResult(result, failed.get(), total, sampleError.get())
    }

    /** Une requête d'altitudes, avec quelques tentatives + backoff. Renvoie (résultat?, dernier message d'erreur?). */
    private suspend fun fetchChunkWithRetry(sub: List<DoubleArray>): Pair<List<Double>?, String?> {
        var attempt = 0
        var lastError: String? = null
        while (attempt < MAX_ATTEMPTS) {
            currentCoroutineContext().ensureActive()
            val outcome = runCatching { getElevations(sub) }
            outcome.getOrNull()?.let { return it to null }
            lastError = outcome.exceptionOrNull()?.let { "${it::class.simpleName}: ${it.message}" }
            attempt++
            if (attempt < MAX_ATTEMPTS) {
                // 429 = rate-limit IGN : backoff long pour laisser le quota se reconstituer.
                val rateLimited = lastError?.contains("429") == true
                delay(if (rateLimited) 1500L + attempt * 1000L else 300L * attempt)
            }
        }
        return null to lastError
    }

    private companion object {
        private const val MAX_ATTEMPTS = 5
    }
}
