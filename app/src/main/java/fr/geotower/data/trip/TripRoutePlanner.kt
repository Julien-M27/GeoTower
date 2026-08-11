package fr.geotower.data.trip

import fr.geotower.data.api.RouteApi

/**
 * Décide **quelles requêtes d'itinéraire** lancer pour un trajet, sans rien appeler : toute la
 * logique de découpe est ici, en Kotlin pur, pour être testable sans réseau.
 *
 * Deux contraintes à concilier :
 *
 * - le service de la Géoplateforme accepte au plus [RouteApi.MAX_INTERMEDIATES] étapes
 *   intermédiaires, donc [MAX_POINTS_PER_REQUEST] points et `MAX_POINTS_PER_REQUEST - 1` segments
 *   par requête ;
 * - éditer une tournée ne doit pas tout recalculer. Déplacer une étape n'invalide que les deux
 *   segments qui la touchent : on ne relance une requête que sur les **suites contiguës** de
 *   segments manquants.
 */
object TripRoutePlanner {
    /** Départ + intermédiaires + arrivée. Le plafond appartient au service : voir [RouteApi]. */
    const val MAX_POINTS_PER_REQUEST = RouteApi.MAX_POINTS_PER_ROUTE_REQUEST

    /**
     * La suite d'étapes que l'itinéraire parcourt, par indice dans [TripPlan.steps]. Avec un retour
     * au départ, la première étape apparaît **deux fois** : au début et à la fin. C'est ce point en
     * double qui fait qu'une boucle ne tient que 16 étapes distinctes dans une requête, contre 17
     * pour un trajet ouvert.
     */
    fun routeSequence(stepCount: Int, returnToStart: Boolean): List<Int> {
        if (stepCount < 2) return emptyList()
        val sequence = ArrayList<Int>(stepCount + 1)
        for (index in 0 until stepCount) sequence += index
        if (returnToStart) sequence += 0
        return sequence
    }

    fun routeSequence(plan: TripPlan): List<Int> = routeSequence(plan.steps.size, plan.returnToStart)

    /**
     * Les groupes de points à envoyer au service pour compléter [plan], chacun de
     * [MAX_POINTS_PER_REQUEST] points au plus.
     *
     * Un trajet entièrement calculé rend une liste vide. Passer `force = true` recalcule tout —
     * c'est ce que fait un changement de profil, qui invalide tous les segments.
     */
    fun planRequests(plan: TripPlan, force: Boolean = false): List<List<Int>> {
        val sequence = routeSequence(plan)
        if (sequence.size < 2) return emptyList()

        val missing = BooleanArray(sequence.size - 1) { legIndex ->
            force || plan.legBetween(sequence[legIndex], sequence[legIndex + 1]) == null
        }
        return contiguousRuns(sequence, missing).flatMap { chunk(it) }
    }

    /**
     * Regroupe les segments manquants qui se suivent. Un segment d'indice `i` relie
     * `sequence[i]` à `sequence[i + 1]` : une suite de segments manquants `i..j` se traduit donc en
     * points `sequence[i]` à `sequence[j + 1]`.
     */
    internal fun contiguousRuns(sequence: List<Int>, missing: BooleanArray): List<List<Int>> {
        val runs = ArrayList<List<Int>>()
        var start = -1
        for (legIndex in missing.indices) {
            if (missing[legIndex]) {
                if (start < 0) start = legIndex
            } else if (start >= 0) {
                runs += sequence.subList(start, legIndex + 1).toList()
                start = -1
            }
        }
        if (start >= 0) runs += sequence.subList(start, missing.size + 1).toList()
        return runs
    }

    /**
     * Découpe une suite de points en tranches de [MAX_POINTS_PER_REQUEST] au plus, **à
     * recouvrement d'un point** : le dernier point d'une tranche est le premier de la suivante,
     * sans quoi le segment qui les relie ne serait jamais calculé.
     */
    internal fun chunk(points: List<Int>): List<List<Int>> {
        if (points.size <= MAX_POINTS_PER_REQUEST) return if (points.size >= 2) listOf(points) else emptyList()

        val chunks = ArrayList<List<Int>>()
        var start = 0
        while (start < points.lastIndex) {
            val end = minOf(start + MAX_POINTS_PER_REQUEST, points.size)
            chunks += points.subList(start, end).toList()
            start = end - 1
        }
        return chunks
    }
}
