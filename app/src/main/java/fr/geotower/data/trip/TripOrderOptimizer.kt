package fr.geotower.data.trip

/**
 * Résultat d'une réorganisation. Les longueurs sont **à vol d'oiseau** : elles servent à décider et
 * à donner un aperçu immédiat, jamais à afficher une distance de trajet — celle-ci vient de la
 * route, une fois les segments recalculés.
 */
data class TripOrderOutcome(
    val plan: TripPlan,
    val straightBeforeMeters: Double,
    val straightAfterMeters: Double
) {
    val changed: Boolean get() = straightAfterMeters < straightBeforeMeters - 1.0
}

/**
 * Réorganise les étapes d'une tournée pour raccourcir le parcours.
 *
 * **Ce n'est pas l'ordre optimal, c'est un bon ordre.** Le problème est celui du voyageur de
 * commerce : au-delà d'une poignée d'étapes, l'optimum exact n'est pas calculable sur un téléphone.
 * L'interface doit le dire, et ne jamais promettre « le plus court ».
 *
 * Le classement se fait sur les distances **à vol d'oiseau**, jamais sur des itinéraires réels :
 * une matrice de routes coûterait `n × (n-1)` requêtes, soit 132 appels pour douze étapes. Les
 * vraies routes ne sont calculées qu'une fois, sur l'ordre retenu.
 *
 * Deux règles :
 * - la **première étape ne bouge pas**, c'est le point de départ ;
 * - avec [TripPlan.returnToStart], le parcours est un **cycle** et la longueur du retour compte.
 *   Sans cela, l'optimisation aurait tendance à terminer la tournée très loin du départ.
 */
object TripOrderOptimizer {
    /**
     * Au-delà, on ne tente rien : le 2-opt est quadratique par passe, et une tournée de cette
     * taille relève de toute façon d'un autre outil.
     */
    const val MAX_OPTIMIZABLE_STEPS = 120

    /** Garde-fou : une passe qui n'améliore plus rien sort d'elle-même, ceci n'est qu'un filet. */
    private const val MAX_PASSES = 64

    fun optimize(plan: TripPlan): TripOrderOutcome {
        val steps = plan.steps
        if (steps.size < 3 || steps.size > MAX_OPTIMIZABLE_STEPS) {
            val length = straightLength(List(steps.size) { it }, distances(steps), plan.returnToStart)
            return TripOrderOutcome(plan, length, length)
        }

        val matrix = distances(steps)
        val initial = List(steps.size) { it }
        val order = twoOpt(nearestNeighbour(matrix), matrix, plan.returnToStart)

        val before = straightLength(initial, matrix, plan.returnToStart)
        val after = straightLength(order, matrix, plan.returnToStart)
        if (after >= before - 1.0) return TripOrderOutcome(plan, before, before)

        return TripOrderOutcome(
            // Les segments sont tous périmés : ils reliaient d'autres couples d'étapes.
            plan = plan.copy(steps = order.map { steps[it] }, legs = emptyList()),
            straightBeforeMeters = before,
            straightAfterMeters = after
        )
    }

    internal fun distances(steps: List<TripStep>): Array<DoubleArray> {
        val size = steps.size
        val matrix = Array(size) { DoubleArray(size) }
        for (from in 0 until size) {
            for (to in from + 1 until size) {
                val meters = haversineMeters(
                    steps[from].latitude,
                    steps[from].longitude,
                    steps[to].latitude,
                    steps[to].longitude
                )
                matrix[from][to] = meters
                matrix[to][from] = meters
            }
        }
        return matrix
    }

    /** Longueur du parcours dans cet ordre ; le retour au départ compte si [cycle]. */
    internal fun straightLength(order: List<Int>, matrix: Array<DoubleArray>, cycle: Boolean): Double {
        if (order.size < 2) return 0.0
        var total = 0.0
        for (index in 0 until order.lastIndex) {
            total += matrix[order[index]][order[index + 1]]
        }
        if (cycle) total += matrix[order.last()][order.first()]
        return total
    }

    /** Point de départ imposé, puis à chaque fois l'étape non visitée la plus proche. */
    internal fun nearestNeighbour(matrix: Array<DoubleArray>): List<Int> {
        val size = matrix.size
        val visited = BooleanArray(size)
        val order = ArrayList<Int>(size)
        var current = 0
        visited[0] = true
        order += 0
        repeat(size - 1) {
            var best = -1
            var bestDistance = Double.MAX_VALUE
            for (candidate in 1 until size) {
                if (visited[candidate]) continue
                val distance = matrix[current][candidate]
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = candidate
                }
            }
            if (best < 0) return@repeat
            visited[best] = true
            order += best
            current = best
        }
        return order
    }

    /**
     * Amélioration 2-opt : on renverse un morceau du parcours tant que ça raccourcit. L'indice de
     * départ commence à 1, jamais à 0 — la première étape est figée.
     */
    internal fun twoOpt(start: List<Int>, matrix: Array<DoubleArray>, cycle: Boolean): List<Int> {
        val order = start.toIntArray()
        val size = order.size
        if (size < 4) return order.toList()

        var pass = 0
        var improved = true
        while (improved && pass < MAX_PASSES) {
            improved = false
            pass++
            for (from in 1 until size - 1) {
                for (to in from + 1 until size) {
                    if (delta(order, matrix, cycle, from, to) < -1e-6) {
                        order.reverse(from, to)
                        improved = true
                    }
                }
            }
        }
        return order.toList()
    }

    /**
     * Variation de longueur si l'on renverse `order[from..to]`. Le renversement ne touche que les
     * deux arêtes qui bordent le morceau : celles à l'intérieur sont les mêmes, en sens inverse.
     */
    private fun delta(
        order: IntArray,
        matrix: Array<DoubleArray>,
        cycle: Boolean,
        from: Int,
        to: Int
    ): Double {
        val size = order.size
        val before = order[from - 1]
        val first = order[from]
        val last = order[to]
        // Un parcours ouvert dont on renverse la fin n'a pas d'arête après le morceau : la seule
        // arête modifiée est celle qui l'attaque.
        if (!cycle && to == size - 1) return matrix[before][last] - matrix[before][first]

        val after = order[(to + 1) % size]
        return (matrix[before][last] + matrix[first][after]) -
            (matrix[before][first] + matrix[last][after])
    }

    private fun IntArray.reverse(from: Int, to: Int) {
        var low = from
        var high = to
        while (low < high) {
            val swap = this[low]
            this[low] = this[high]
            this[high] = swap
            low++
            high--
        }
    }
}
