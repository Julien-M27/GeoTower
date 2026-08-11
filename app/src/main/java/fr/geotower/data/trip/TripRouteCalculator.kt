package fr.geotower.data.trip

import fr.geotower.data.api.RouteApi
import fr.geotower.utils.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Résultat d'un calcul d'itinéraire sur un trajet. [failedRequests] compte les requêtes perdues :
 * l'appelant s'en sert pour prévenir, plutôt que de laisser croire à une tournée complète alors
 * qu'il manque des segments.
 */
data class TripRouteOutcome(
    val plan: TripPlan,
    val requestCount: Int,
    val failedRequests: Int
) {
    val hasFailures: Boolean get() = failedRequests > 0
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
     */
    suspend fun computeRoute(plan: TripPlan, force: Boolean = false): TripRouteOutcome {
        val requests = TripRoutePlanner.planRequests(plan, force)
        if (requests.isEmpty()) return TripRouteOutcome(plan, requestCount = 0, failedRequests = 0)

        val computed = ArrayList<TripLeg>()
        var failed = 0
        for (request in requests) {
            val legs = runRequest(plan, request)
            if (legs == null) failed++ else computed += legs
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
            failedRequests = failed
        )
    }

    /** Rend les segments d'une tranche, ou `null` si la requête a échoué en entier. */
    private suspend fun runRequest(plan: TripPlan, stepIndices: List<Int>): List<TripLeg>? {
        val points = stepIndices.map { doubleArrayOf(plan.steps[it].latitude, plan.steps[it].longitude) }
        if (points.size < 2) return null

        // Deux points trop éloignés ne partagent aucun réseau : inutile d'interroger le service, il
        // répondrait un rabattement absurde que l'ancrage rejetterait de toute façon.
        val tooFar = points.zipWithNext().any { (from, to) ->
            haversineMeters(from[0], from[1], to[0], to[1]) > RouteApi.MAX_ROUTABLE_DISTANCE_METERS
        }
        if (tooFar) return null

        val portions = try {
            withContext(Dispatchers.IO) { RouteApi.getRoutePortions(points, plan.profile) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            AppLogger.w(TAG, "Trip route unavailable for ${stepIndices.size} points", error)
            return null
        }

        return portions.mapIndexed { index, portion ->
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
                    .takeIf { it.isNotEmpty() }
            )
        }
    }
}
