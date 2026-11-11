package fr.geotower.data.trip

import fr.geotower.R
import fr.geotower.data.api.RouteApi
import fr.geotower.utils.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Résultat d'un calcul d'itinéraire sur un trajet. [failedRequests] compte les requêtes perdues :
 * l'appelant s'en sert pour prévenir, plutôt que de laisser croire à une tournée complète alors
 * qu'il manque des segments.
 */
data class TripRouteOutcome(
    val plan: TripPlan,
    val requestCount: Int,
    val failedRequests: Int,
    val lastError: Throwable? = null
) {
    val hasFailures: Boolean get() = failedRequests > 0
}

/**
 * Traduit une exception d'itinéraire en ressource de chaîne explicite pour l'utilisateur.
 */
fun tripRouteErrorMessageRes(error: Throwable?): Int? {
    if (error == null) return null
    val message = error.message.orEmpty().lowercase(Locale.ROOT)
    val causeMessage = error.cause?.message.orEmpty().lowercase(Locale.ROOT)
    val full = "$message $causeMessage"

    return when {
        error is java.net.UnknownHostException || error is java.net.ConnectException ->
            R.string.trips_route_error_network
        error is java.net.SocketTimeoutException || full.contains("timed out") || full.contains("timeout") ->
            if (full.contains("http 504") || full.contains("upstream")) {
                R.string.trips_route_error_server
            } else {
                R.string.trips_route_error_timeout
            }
        full.contains("http 5") || full.contains("bad gateway") || full.contains("gateway time-out") ||
            full.contains("service unavailable") || full.contains("internal server error") ->
            R.string.trips_route_error_server
        full.contains("outside the requested area") || full.contains("snapped outside") ->
            R.string.trips_route_error_coverage
        else -> R.string.trips_route_error_server
    }
}

/**
 * Calcule les segments manquants d'un trajet en appelant le service d'itinéraire.
 *
 * Trois partis pris :
 *
 * - **on ne recalcule que ce qui manque** ([TripRoutePlanner]), pour qu'éditer une étape au milieu
 *   d'une tournée de vingt sites ne relance pas tout ;
 * - **un échec est local** : la tranche perdue laisse ses segments absents, les autres sont
 *   enregistrées. Un segment absent se dessine en trait direct, ce qui est exactement l'information
 *   à donner — « je n'ai pas pu calculer ce bout » — plutôt qu'un faux tracé ;
 * - **rien n'est inventé** : aucun segment de repli à vol d'oiseau n'est enregistré, sinon la
 *   distance totale afficherait une valeur qui n'est pas celle de la route.
 */
object TripRouteCalculator {
    private const val TAG = "GeoTowerTrip"

    /**
     * @param force recalcule tous les segments, y compris ceux déjà connus. C'est ce qu'impose un
     *   changement de profil : un tracé voiture ne vaut rien pour un trajet à pied.
     * @param optimization « le plus rapide » ou « le plus court » (cf. `AppConfig.routeOptimization`).
     *   Les segments calculés dans l'autre mode sont refaits, sans quoi le trajet mélangerait les
     *   deux après un changement de réglage.
     */
    suspend fun computeRoute(
        plan: TripPlan,
        force: Boolean = false,
        optimization: String = RouteApi.OPTIMIZATION_FASTEST
    ): TripRouteOutcome {
        val requests = TripRoutePlanner.planRequests(plan, force, optimization)
        if (requests.isEmpty()) return TripRouteOutcome(plan, requestCount = 0, failedRequests = 0)

        val computed = ArrayList<TripLeg>()
        var failed = 0
        var lastThrownError: Throwable? = null
        for (request in requests) {
            val (legs, error) = runRequest(plan, request, optimization)
            if (legs == null) {
                failed++
                if (lastThrownError == null && error != null) lastThrownError = error
            } else {
                computed += legs
            }
        }

        // Les segments recalculés remplacent les anciens ; ceux qu'on n'a pas touchés survivent —
        // sauf en recalcul forcé, où les anciens sont périmés par construction (changement de
        // profil) et où en garder un seul afficherait un tracé voiture sur un trajet à pied.
        val previousLegs = if (force) emptyList() else plan.legs
        val keptLegs = previousLegs.filterNot { existing ->
            computed.any { it.fromIndex == existing.fromIndex && it.toIndex == existing.toIndex }
        }
        val validPairs = plan.legPairs().toSet()
        val nextLegs = (keptLegs + computed).filter { (it.fromIndex to it.toIndex) in validPairs }

        return TripRouteOutcome(
            plan = plan.copy(legs = nextLegs),
            requestCount = requests.size,
            failedRequests = failed,
            lastError = lastThrownError
        )
    }

    /** Rend les segments d'une tranche et l'erreur éventuelle si la requête a échoué. */
    private suspend fun runRequest(
        plan: TripPlan,
        stepIndices: List<Int>,
        optimization: String
    ): Pair<List<TripLeg>?, Throwable?> {
        val points = stepIndices.map { doubleArrayOf(plan.steps[it].latitude, plan.steps[it].longitude) }
        if (points.size < 2) return Pair(null, null)

        // Deux points trop éloignés ne partagent aucun réseau : inutile d'interroger le service, il
        // répondrait un rabattement absurde que l'ancrage rejetterait de toute façon.
        val tooFar = points.zipWithNext().any { (from, to) ->
            haversineMeters(from[0], from[1], to[0], to[1]) > RouteApi.MAX_ROUTABLE_DISTANCE_METERS
        }
        if (tooFar) return Pair(null, IllegalArgumentException("Points too far"))

        val portions = try {
            withContext(Dispatchers.IO) { RouteApi.getRoutePortions(points, plan.profile, optimization) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            AppLogger.w(TAG, "Trip route unavailable for ${stepIndices.size} points", error)
            return Pair(null, error)
        }

        val legs = portions.mapIndexed { index, portion ->
            TripLeg(
                fromIndex = stepIndices[index],
                toIndex = stepIndices[index + 1],
                profile = plan.profile,
                distanceMeters = portion.distanceMeters,
                durationSeconds = portion.durationSeconds,
                encodedGeometry = TripGeometryCodec.encode(portion.points),
                maneuvers = portion.maneuvers
                    .map {
                        TripManeuver(
                            type = it.type,
                            modifier = it.modifier,
                            roadName = it.roadName,
                            distanceMeters = it.distanceMeters,
                            durationSeconds = it.durationSeconds
                        )
                    }
                    .takeIf { it.isNotEmpty() },
                optimization = optimization
            )
        }
        return Pair(legs, null)
    }
}
