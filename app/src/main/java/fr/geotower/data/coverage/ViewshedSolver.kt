package fr.geotower.data.coverage

import kotlin.math.atan2

/**
 * Calcule la visibilité (viewshed) d'**une** antenne sur une grille terrain déjà chargée — code pur, CPU.
 *
 * Modèle : ligne de visée géométrique non coupée par le terrain/bâti (angle vertical croissant),
 * + courbure terrestre (k = 4/3) + diagramme horizontal 3GPP + diagramme vertical (down-tilt supposé)
 * + dégagement de Fresnel optionnel. **V1 = distance jusqu'au premier blocage** par rayon.
 */
object ViewshedSolver {
    fun solveSector(
        field: TerrainField,
        ant: AntennaSpec,
        params: ViewshedParams
    ): SectorViewshed {
        val hTx = field.siteGroundM + ant.txHeightM
        val freq = params.frequencyMHz ?: ant.frequencyMHz
        val rays = ArrayList<SectorRay>()

        for (ray in field.rays) {
            val delta = CoverageGeo.angularDifferenceDeg(ray.bearingDeg, ant.azimutDeg)
            val aH = if (ant.omni) 0.0 else CoverageGeo.horizontalPatternDb(delta, params.theta3dbDeg, params.patternAmDb)
            if (!ant.omni && aH < params.gainThresholdDb) continue // entièrement hors azimut

            var alphaMax = Double.NEGATIVE_INFINITY
            var maxVisible = 0.0
            val n = ray.distances.size
            for (i in 0 until n) {
                val d = ray.distances[i]
                val g = ray.ground[i]
                if (g.isNaN()) break // donnée manquante (mer / hors couverture) : on ne voit pas au-delà

                val bulge = if (params.curvature) d * d / (2.0 * CoverageGeo.EFFECTIVE_EARTH_RADIUS_M) else 0.0
                val zTarget = g + params.receiverHeightM - bulge
                val alpha = atan2(zTarget - hTx, d)

                // (a) Ligne de visée : bloqué si la cible passe sous l'angle d'obstruction max rencontré.
                if (alpha < alphaMax) break

                // (b) Diagramme vertical (down-tilt) : on borne la portée quand la cible monte au-dessus
                //     du centre du lobe (champ lointain). Le creux sous-lobe en champ proche est ignoré (V1).
                val elevationDeg = Math.toDegrees(alpha)
                if (elevationDeg > -params.tiltDeg) {
                    val aV = CoverageGeo.verticalPatternDb(elevationDeg, params.tiltDeg, params.verticalBeamwidthDeg, params.patternAmDb)
                    if (aH + aV < params.gainThresholdDb) break
                }

                // (c) Mise à jour de l'angle d'obstruction (avec inflation Fresnel optionnelle).
                val fresnelInflation = if (params.fresnel && freq != null) {
                    0.6 * CoverageGeo.fresnelRadiusM(d, (field.maxRadiusM - d).coerceAtLeast(1.0), freq)
                } else {
                    0.0
                }
                val zBlock = (g - bulge) + fresnelInflation
                val alphaBlock = atan2(zBlock - hTx, d)
                if (alphaBlock > alphaMax) alphaMax = alphaBlock
                maxVisible = d
            }

            if (maxVisible > 0.0) rays.add(SectorRay(ray.bearingDeg, maxVisible, aH))
        }
        return SectorViewshed(ant, rays)
    }
}
