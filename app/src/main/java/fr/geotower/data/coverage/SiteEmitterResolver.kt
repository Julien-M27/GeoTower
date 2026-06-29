package fr.geotower.data.coverage

import fr.geotower.data.models.AntenneDbEntity

/**
 * Transforme les antennes brutes d'un site (table `antenne`) en [AntennaSpec] prêtes au calcul.
 *
 * - Exclut les faisceaux hertziens (`is_fh != 0`).
 * - Ignore les antennes sans azimut (sauf omni) ou sans hauteur.
 * - Classe omni vs sectorielle via le libellé `ref_type_antenne` (heuristique documentée, ajustable).
 *   ANFR ne portant pas la bande au niveau antenne, la fréquence appliquée est celle choisie par
 *   l'utilisateur ([defaultFrequencyMHz]) — approximation pour Fresnel / plafond.
 */
object SiteEmitterResolver {
    /** Libellés (minuscule, sans accent simplifié) trahissant une antenne omnidirectionnelle. */
    private val OMNI_HINTS = listOf(
        "omni", "dipole", "dipôle", "doublet", "colineaire", "colinéaire",
        "fouet", "fuseau", "gonio", "discone", "ground plane"
    )

    fun isOmni(typeLabel: String?): Boolean {
        val l = typeLabel?.lowercase() ?: return false
        return OMNI_HINTS.any { l.contains(it) }
    }

    fun resolve(
        antennas: List<AntenneDbEntity>,
        typeLabels: Map<Int, String>,
        operator: String?,
        defaultFrequencyMHz: Int?,
        viewshed: ViewshedParams,
        fallbackHeightM: Double = 15.0
    ): List<AntennaSpec> {
        val sectorHalfBeam = CoverageGeo.horizontalHalfWidthDeg(
            viewshed.theta3dbDeg, viewshed.patternAmDb, viewshed.gainThresholdDb
        )
        return antennas.mapNotNull { a ->
            if (a.isFh != 0) return@mapNotNull null
            val label = a.taeId?.let { typeLabels[it] }
            val omni = isOmni(label)
            // hauteur_bas parfois absente en base : on replie plutôt que de perdre tout le secteur.
            val height = a.hauteurBas?.takeIf { it > 0.0 } ?: fallbackHeightM
            val azimut = a.azimut?.toDouble() ?: (if (omni) 0.0 else return@mapNotNull null)
            AntennaSpec(
                aerId = a.aerId,
                azimutDeg = azimut,
                txHeightM = height,
                omni = omni,
                halfBeamDeg = if (omni) 180.0 else sectorHalfBeam,
                frequencyMHz = defaultFrequencyMHz,
                operator = operator,
                typeLabel = label
            )
        }
    }
}
