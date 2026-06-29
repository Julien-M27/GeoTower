package fr.geotower.data.coverage

import kotlin.math.floor

/** Coordonnée géographique simple (degrés). Découplée d'osmdroid pour rester testable hors Android. */
data class LatLon(val latitude: Double, val longitude: Double)

/**
 * Une antenne émettrice réelle (hors faisceau hertzien), prête pour le calcul de visibilité.
 * `azimut`/`hauteur` sont issus d'ANFR ; `omni`/ouverture proviennent d'un modèle paramétrique
 * (ANFR ne fournit pas le lobe constructeur).
 */
data class AntennaSpec(
    val aerId: String,
    val azimutDeg: Double,
    val txHeightM: Double,
    val omni: Boolean,
    val halfBeamDeg: Double,
    val frequencyMHz: Int?,
    val operator: String?,
    val typeLabel: String?
)

/** Paramètres physiques/diagramme du calcul de visibilité (hypothèses réglables — voir disclaimer). */
data class ViewshedParams(
    val maxRadiusM: Double,
    val curvature: Boolean = true,
    val fresnel: Boolean = false,
    val frequencyMHz: Int? = null,
    val tiltDeg: Double = 5.0,
    val verticalBeamwidthDeg: Double = 10.0,
    val theta3dbDeg: Double = 65.0,
    val patternAmDb: Double = 30.0,
    val gainThresholdDb: Double = -10.0,
    val receiverHeightM: Double = 1.5
)

/** Requête complète : grille terrain (mutualisée) + paramètres de visibilité (appliqués par antenne). */
data class CoverageRequest(
    val maxRadiusM: Double,
    val angularStepDeg: Double,
    val sampleStepM: Double,
    val includeObstacles: Boolean,
    val viewshed: ViewshedParams
)

/** Un relèvement terrain : distances croissantes (m) et altitude de surface (m, sol ± toit). */
class TerrainRay(
    val bearingDeg: Double,
    val distances: DoubleArray,
    val ground: DoubleArray
)

/** Grille radiale d'altitudes autour du site, mutualisée par toutes les antennes. */
class TerrainField(
    val siteLat: Double,
    val siteLon: Double,
    val siteGroundM: Double,
    val rays: List<TerrainRay>,
    val sampleStepM: Double,
    val maxRadiusM: Double,
    val obstaclesIncluded: Boolean,
    /** Fraction des points dont l'altitude a été obtenue (1.0 = parfait ; bas = requêtes IGN en échec). */
    val validPointFraction: Double = 1.0,
    val failedChunks: Int = 0,
    val totalChunks: Int = 0,
    val sampleError: String? = null
)

/** Portée visible (m) pour un relèvement, + gain relatif hors-axe (dB, ≤ 0). */
data class SectorRay(
    val bearingDeg: Double,
    val maxVisibleM: Double,
    val relGainDb: Double
)

/** Visibilité d'une antenne (secteur orienté ou omni). */
data class SectorViewshed(
    val antenna: AntennaSpec,
    val rays: List<SectorRay>
) {
    /**
     * Contour fermé du secteur en coordonnées géographiques : pour un secteur, apex au site + arc ;
     * pour une omni, simple anneau. Vide si aucun rayon visible.
     */
    fun outline(siteLat: Double, siteLon: Double): List<LatLon> {
        if (rays.isEmpty()) return emptyList()
        // Trier par écart SIGNÉ à l'azimut (et non par relèvement absolu) : sinon un secteur qui
        // chevauche le 0°/360° (orienté nord) est coupé en deux paquets → polygone auto-sécant qui
        // s'annule en règle non-zéro (secteur « mangé »).
        val sorted = if (antenna.omni) {
            rays.sortedBy { it.bearingDeg }
        } else {
            rays.sortedBy { CoverageGeo.signedDeltaDeg(it.bearingDeg, antenna.azimutDeg) }
        }
        val pts = ArrayList<LatLon>(sorted.size + 2)
        if (!antenna.omni) pts.add(LatLon(siteLat, siteLon))
        for (r in sorted) {
            val ll = CoverageGeo.destinationPoint(siteLat, siteLon, r.bearingDeg, r.maxVisibleM)
            pts.add(LatLon(ll[0], ll[1]))
        }
        if (!antenna.omni) pts.add(LatLon(siteLat, siteLon))
        return pts
    }
}

/** Couverture d'un site = union des secteurs de toutes ses antennes. */
data class SiteCoverage(
    val idAnfr: String,
    val siteLat: Double,
    val siteLon: Double,
    val operator: String?,
    val sectors: List<SectorViewshed>,
    val request: CoverageRequest,
    val computedAtMillis: Long,
    /** Fraction du terrain effectivement obtenue d'IGN (diagnostic réseau). */
    val terrainValidFraction: Double = 1.0,
    val terrainFailedChunks: Int = 0,
    val terrainTotalChunks: Int = 0,
    val terrainSampleError: String? = null
) {
    val isEmpty: Boolean get() = sectors.all { it.rays.isEmpty() }
}

/**
 * Obstacle bâti : test d'appartenance + altitude de toit. Interface découplée de BD TOPO
 * pour rester testable hors Android (les fakes l'implémentent ; [fr.geotower.data.api.BdTopoBuilding] aussi).
 */
interface BuildingObstacle {
    val minLon: Double
    val minLat: Double
    val maxLon: Double
    val maxLat: Double
    fun contains(longitude: Double, latitude: Double): Boolean
    fun topAltitude(terrainElevation: Double): Double
}

/**
 * Index spatial uniforme (grille) : ne teste qu'une poignée de bâtiments par point au lieu de tous
 * (en ville, jusqu'à 5000 bâtiments). Chaque bâtiment est inséré dans toutes les cellules que
 * recouvre sa boîte englobante ; une collision de cellule n'ajoute que des tests inutiles (jamais d'oubli).
 */
class BuildingIndex(buildings: List<BuildingObstacle>, private val cellDeg: Double = 0.003) {
    private val cells = HashMap<Long, MutableList<BuildingObstacle>>()
    private val oversize = ArrayList<BuildingObstacle>()
    val isEmpty: Boolean get() = cells.isEmpty() && oversize.isEmpty()

    init {
        for (b in buildings) {
            val xi0 = floor(b.minLon / cellDeg).toInt()
            val xi1 = floor(b.maxLon / cellDeg).toInt()
            val yi0 = floor(b.minLat / cellDeg).toInt()
            val yi1 = floor(b.maxLat / cellDeg).toInt()
            val span = (xi1 - xi0 + 1).toLong() * (yi1 - yi0 + 1).toLong()
            if (span <= 0L || span > MAX_CELLS_PER_BUILDING) {
                // bbox aberrante (géométrie BD TOPO malformée) : testée à chaque point, jamais griddée.
                oversize.add(b)
                continue
            }
            var xi = xi0
            while (xi <= xi1) {
                var yi = yi0
                while (yi <= yi1) {
                    cells.getOrPut(key(xi, yi)) { mutableListOf() }.add(b)
                    yi++
                }
                xi++
            }
        }
    }

    /** Altitude de surface au point (terrain, ou toit si un bâtiment le couvre). */
    fun surfaceAltitude(longitude: Double, latitude: Double, terrainElevation: Double): Double {
        var top = terrainElevation
        cells[key(floor(longitude / cellDeg).toInt(), floor(latitude / cellDeg).toInt())]?.let { list ->
            for (b in list) {
                if (b.contains(longitude, latitude)) {
                    val t = b.topAltitude(terrainElevation)
                    if (t > top) top = t
                }
            }
        }
        for (b in oversize) {
            if (b.contains(longitude, latitude)) {
                val t = b.topAltitude(terrainElevation)
                if (t > top) top = t
            }
        }
        return top
    }

    private fun key(xi: Int, yi: Int): Long = (xi.toLong() shl 32) xor (yi.toLong() and 0xffffffffL)

    companion object {
        /** Au-delà, la bbox est jugée aberrante (un bâtiment réel tient en quelques cellules). */
        private const val MAX_CELLS_PER_BUILDING = 4096L
    }
}
