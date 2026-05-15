package fr.geotower.data

import fr.geotower.data.models.DbCluster

internal object MacroClusterGrouper {
    private const val TERRITORY_CLUSTER_MAX_ZOOM = 6.5

    private val targetedTerritories = listOf(
        Territory("france_metropolitaine", 41.0, 51.6, -5.8, 10.1),
        Territory("guyane", 1.8, 6.2, -54.9, -51.2),
        Territory("antilles_francaises", 14.2, 18.3, -63.4, -60.6),
        Territory("saint_pierre_miquelon", 46.6, 47.2, -56.6, -55.8)
    )

    fun mergeTargetedTerritories(clusters: List<DbCluster>, zoom: Double): List<DbCluster> {
        if (zoom >= TERRITORY_CLUSTER_MAX_ZOOM || clusters.size < 2) return clusters

        val grouped = linkedMapOf<Territory, MutableList<DbCluster>>()
        val passthrough = mutableListOf<DbCluster>()

        clusters.forEach { cluster ->
            val territory = targetedTerritories.firstOrNull { it.contains(cluster.centerLat, cluster.centerLon) }
            if (territory == null) {
                passthrough += cluster
            } else {
                grouped.getOrPut(territory) { mutableListOf() } += cluster
            }
        }

        return buildList {
            addAll(grouped.values.map { mergeClusters(it) })
            addAll(passthrough)
        }
    }

    private fun mergeClusters(clusters: List<DbCluster>): DbCluster {
        if (clusters.size == 1) return clusters.first()

        val weightedCount = clusters.sumOf { it.count.coerceAtLeast(1) }
        val totalCount = clusters.sumOf { it.count.coerceAtLeast(0) }
        val centerLat = clusters.sumOf { it.centerLat * it.count.coerceAtLeast(1) } / weightedCount
        val centerLon = clusters.sumOf { it.centerLon * it.count.coerceAtLeast(1) } / weightedCount

        return DbCluster(
            centerLat = centerLat,
            centerLon = centerLon,
            count = totalCount,
            operators = mergeOperators(clusters)
        )
    }

    private fun mergeOperators(clusters: List<DbCluster>): String? {
        val operators = clusters
            .asSequence()
            .flatMap { cluster -> cluster.operators.orEmpty().split(",").asSequence() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()

        return operators.joinToString(", ").takeIf { it.isNotBlank() }
    }

    private data class Territory(
        val id: String,
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double
    ) {
        fun contains(lat: Double, lon: Double): Boolean {
            return lat in minLat..maxLat && lon in minLon..maxLon
        }
    }
}
