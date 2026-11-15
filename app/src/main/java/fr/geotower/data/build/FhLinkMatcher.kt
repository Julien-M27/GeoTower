package fr.geotower.data.build

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Prototype d'appariement des faisceaux hertziens (FH) entre supports ANFR.
 *
 * Prend en entree des sites disposant d'azimuts FH et reconstitue les liaisons
 * point-a-point par recherche spatiale en grille + alignement geodesique reciproque.
 */
object FhLinkMatcher {

    private const val EARTH_RADIUS_KM = 6371.0
    private const val GRID_CELL_DEG = 0.2

    data class FhSite(
        val idAnfr: String,
        val lat: Double,
        val lon: Double,
        val groupKey: String,
        val azimuths: List<Double>,
    )

    data class FhLink(
        val idAnfrA: String,
        val latA: Double,
        val lonA: Double,
        val idAnfrB: String,
        val latB: Double,
        val lonB: Double,
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double,
        val groupKey: String,
        val bearingABDeg: Double,
        val distanceKm: Double,
        val angularErrorDeg: Double,
    )

    data class MatchConfig(
        val maxDistanceKm: Double = 120.0,
        val minDistanceKm: Double = 0.05,
        val angleToleranceDeg: Double = 2.5,
    )

    data class MatchResult(
        val links: List<FhLink>,
        val totalInputSites: Int,
        val totalInputAzimuths: Int,
        val matchedAzimuths: Int,
        val unmatchedAzimuths: Int,
        val elapsedMs: Long,
    )

    fun match(sites: List<FhSite>, config: MatchConfig = MatchConfig()): MatchResult {
        val startNs = System.nanoTime()
        if (sites.isEmpty()) {
            return MatchResult(emptyList(), 0, 0, 0, 0, 0L)
        }

        // Indexation spatiale par (groupKey, cellLat, cellLon)
        val grid = HashMap<GridKey, MutableList<FhSite>>(sites.size)
        var totalAzimuths = 0
        for (site in sites) {
            if (site.azimuths.isEmpty()) continue
            totalAzimuths += site.azimuths.size
            val key = GridKey(
                site.groupKey,
                cellIndex(site.lat),
                cellIndex(site.lon),
            )
            grid.getOrPut(key) { ArrayList(4) }.add(site)
        }

        // Collecte de toutes les paires candidates valides, puis appariement glouton
        // du plus proche au plus lointain pour eviter qu'un site lointain ne vole
        // l'azimut deja apparie d'un relais plus proche.
        val allCandidates = ArrayList<CandidateMatch>()

        for (siteA in sites) {
            if (siteA.azimuths.isEmpty()) continue

            val latDelta = config.maxDistanceKm / 111.0
            val cosLat = cos(toRad(siteA.lat)).coerceAtLeast(0.2)
            val lonDelta = config.maxDistanceKm / (111.0 * cosLat)

            val minCellLat = cellIndex(siteA.lat - latDelta)
            val maxCellLat = cellIndex(siteA.lat + latDelta)
            val minCellLon = cellIndex(siteA.lon - lonDelta)
            val maxCellLon = cellIndex(siteA.lon + lonDelta)

            val nearby = ArrayList<FhSite>()
            for (cLat in minCellLat..maxCellLat) {
                for (cLon in minCellLon..maxCellLon) {
                    val bucket = grid[GridKey(siteA.groupKey, cLat, cLon)] ?: continue
                    for (cand in bucket) {
                        if (cand.idAnfr > siteA.idAnfr) {
                            nearby.add(cand)
                        }
                    }
                }
            }
            if (nearby.isEmpty()) continue

            for (siteB in nearby) {
                val distKm = haversineKm(siteA.lat, siteA.lon, siteB.lat, siteB.lon)
                if (distKm < config.minDistanceKm || distKm > config.maxDistanceKm) continue

                val bearingAB = initialBearingDeg(siteA.lat, siteA.lon, siteB.lat, siteB.lon)
                val bearingBA = initialBearingDeg(siteB.lat, siteB.lon, siteA.lat, siteA.lon)

                var bestAzIdxA = -1
                var bestErrA = Double.MAX_VALUE
                for (i in siteA.azimuths.indices) {
                    val err = angularDiffDeg(bearingAB, siteA.azimuths[i])
                    if (err <= config.angleToleranceDeg && err < bestErrA) {
                        bestErrA = err
                        bestAzIdxA = i
                    }
                }
                if (bestAzIdxA == -1) continue

                var bestAzIdxB = -1
                var bestErrB = Double.MAX_VALUE
                for (j in siteB.azimuths.indices) {
                    val err = angularDiffDeg(bearingBA, siteB.azimuths[j])
                    if (err <= config.angleToleranceDeg && err < bestErrB) {
                        bestErrB = err
                        bestAzIdxB = j
                    }
                }
                if (bestAzIdxB == -1) continue

                allCandidates.add(
                    CandidateMatch(
                        siteA = siteA,
                        azIdxA = bestAzIdxA,
                        siteB = siteB,
                        azIdxB = bestAzIdxB,
                        bearingAB = bearingAB,
                        distKm = distKm,
                        totalErrDeg = bestErrA + bestErrB,
                    ),
                )
            }
        }

        allCandidates.sortWith(
            compareBy<CandidateMatch> { it.distKm }.thenBy { it.totalErrDeg },
        )

        val usedAzimuths = HashSet<AzimuthSlot>(allCandidates.size * 2)
        val deduplicated = LinkedHashMap<PairKey, FhLink>()
        var matchedAzimuths = 0

        for (cand in allCandidates) {
            val slotA = AzimuthSlot(cand.siteA.idAnfr, cand.azIdxA)
            val slotB = AzimuthSlot(cand.siteB.idAnfr, cand.azIdxB)
            if (slotA in usedAzimuths || slotB in usedAzimuths) continue

            val pairKey = PairKey(cand.siteA.idAnfr, cand.siteB.idAnfr, cand.siteA.groupKey)
            if (pairKey in deduplicated) continue

            usedAzimuths.add(slotA)
            usedAzimuths.add(slotB)
            matchedAzimuths += 2

            deduplicated[pairKey] = FhLink(
                idAnfrA = cand.siteA.idAnfr,
                latA = cand.siteA.lat,
                lonA = cand.siteA.lon,
                idAnfrB = cand.siteB.idAnfr,
                latB = cand.siteB.lat,
                lonB = cand.siteB.lon,
                minLat = min(cand.siteA.lat, cand.siteB.lat),
                maxLat = max(cand.siteA.lat, cand.siteB.lat),
                minLon = min(cand.siteA.lon, cand.siteB.lon),
                maxLon = max(cand.siteA.lon, cand.siteB.lon),
                groupKey = cand.siteA.groupKey,
                bearingABDeg = cand.bearingAB,
                distanceKm = cand.distKm,
                angularErrorDeg = cand.totalErrDeg,
            )
        }

        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
        return MatchResult(
            links = deduplicated.values.toList(),
            totalInputSites = sites.size,
            totalInputAzimuths = totalAzimuths,
            matchedAzimuths = matchedAzimuths,
            unmatchedAzimuths = totalAzimuths - matchedAzimuths,
            elapsedMs = elapsedMs,
        )
    }

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = toRad(lat2 - lat1)
        val dLon = toRad(lon2 - lon1)
        val rLat1 = toRad(lat1)
        val rLat2 = toRad(lat2)
        val sinLat = sin(dLat * 0.5)
        val sinLon = sin(dLon * 0.5)
        val a = sinLat * sinLat + cos(rLat1) * cos(rLat2) * sinLon * sinLon
        val c = 2.0 * atan2(sqrt(a), sqrt((1.0 - a).coerceAtLeast(0.0)))
        return EARTH_RADIUS_KM * c
    }

    fun initialBearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rLat1 = toRad(lat1)
        val rLat2 = toRad(lat2)
        val dLon = toRad(lon2 - lon1)
        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
        val deg = atan2(y, x) * 180.0 / PI
        return (deg + 360.0) % 360.0
    }

    fun angularDiffDeg(a: Double, b: Double): Double {
        val diff = ((a - b + 540.0) % 360.0) - 180.0
        return kotlin.math.abs(diff)
    }

    private fun cellIndex(deg: Double): Int = floor(deg / GRID_CELL_DEG).toInt()

    private fun toRad(deg: Double): Double = deg * PI / 180.0

    private data class GridKey(
        val groupKey: String,
        val cellLat: Int,
        val cellLon: Int,
    )

    private data class PairKey(
        val idA: String,
        val idB: String,
        val groupKey: String,
    )

    private data class AzimuthSlot(
        val idAnfr: String,
        val azIndex: Int,
    )

    private data class CandidateMatch(
        val siteA: FhSite,
        val azIdxA: Int,
        val siteB: FhSite,
        val azIdxB: Int,
        val bearingAB: Double,
        val distKm: Double,
        val totalErrDeg: Double,
    )
}
