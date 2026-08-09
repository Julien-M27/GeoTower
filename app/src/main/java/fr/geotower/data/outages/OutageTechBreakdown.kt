package fr.geotower.data.outages

import fr.geotower.data.models.SiteHsEntity
import java.util.Locale

/**
 * Codes d'état d'un service (voix ou data) tels que les opérateurs les publient dans leurs relevés.
 *
 * Vérité unique de l'app : la fiche site
 * ([fr.geotower.ui.components.serviceAvailabilityFromOutageCode]) et le décompte par génération
 * ([OutageTechBreakdown]) lisent le MÊME code de la MÊME façon, sinon un site rouge dans la fiche
 * pourrait ne pas être compté dans le tableau des réglages.
 */
object OutageStatusCodes {

    private val DOWN = setOf("HS", "DE")
    private val UP = setOf("OK")

    /** Valeur exploitable, ou null quand l'opérateur n'a rien renseigné pour ce service. */
    fun clean(value: String?): String? = value
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != "-" && !it.equals("null", ignoreCase = true) }

    fun isDown(value: String?): Boolean = normalized(value) in DOWN

    fun isUp(value: String?): Boolean = normalized(value) in UP

    private fun normalized(value: String?): String? = clean(value)?.uppercase(Locale.ROOT)
}

/** Les quatre générations suivies par les relevés opérateurs, dans l'ordre d'affichage. */
enum class OutageTechnology(val label: String) {
    G2("2G"),
    G3("3G"),
    G4("4G"),
    G5("5G"),
}

/**
 * Une ligne du tableau « générations hors service par opérateur » : un compte par génération, dans
 * l'ordre de [OutageTechnology.entries].
 *
 * Un compte vaut [OutageTechBreakdown.UNPUBLISHED] quand l'opérateur ne renseigne AUCUN état pour
 * cette génération : afficher « 0 » laisserait croire que rien n'est en panne alors que la donnée
 * n'existe simplement pas (les quatre relevés ne détaillent pas les mêmes services).
 */
data class OutageTechRow(
    val operateur: String,
    val counts: List<Int>,
) {
    fun countFor(technology: OutageTechnology): Int =
        counts.getOrElse(technology.ordinal) { OutageTechBreakdown.UNPUBLISHED }

    val hasUnpublished: Boolean get() = counts.any { it == OutageTechBreakdown.UNPUBLISHED }
}

/**
 * Détail par génération des pannes, calculé une seule fois à la récupération (téléchargement serveur
 * ou génération locale) et rangé dans les prefs comme la répartition par opérateur : les réglages
 * affichent le tableau sans avoir à reparser le fichier national.
 */
object OutageTechBreakdown {

    /** Compte d'une génération que l'opérateur ne renseigne pas du tout. */
    const val UNPUBLISHED = -1

    /**
     * Une génération est comptée hors service dès que la voix OU la data l'est : c'est la lecture
     * de la fiche site, où une case rouge suffit à dire « cette génération ne passe plus ».
     * Ordre identique à [OutageLocalConfig.breakdownOf] pour que les deux blocs se lisent en vis-à-vis.
     */
    fun of(sites: List<SiteHsEntity>): List<OutageTechRow> =
        sites.groupBy { it.operateur }
            .entries
            .sortedByDescending { it.value.size }
            .map { (operateur, operatorSites) ->
                OutageTechRow(
                    operateur = operateur,
                    counts = OutageTechnology.entries.map { technology -> countDown(operatorSites, technology) },
                )
            }

    /** Cumul par génération, [UNPUBLISHED] tant qu'aucun opérateur ne publie la génération. */
    fun totalsOf(rows: List<OutageTechRow>): List<Int> = OutageTechnology.entries.map { technology ->
        val published = rows.map { it.countFor(technology) }.filter { it != UNPUBLISHED }
        if (published.isEmpty()) UNPUBLISHED else published.sum()
    }

    /** Encodage « opérateur<TAB>2G<TAB>3G<TAB>4G<TAB>5G » par ligne, comme [OutageLocalConfig.encodeBreakdown]. */
    fun encode(rows: List<OutageTechRow>): String = rows.joinToString("\n") { row ->
        val operateur = row.operateur.replace('\t', ' ').replace('\n', ' ')
        (listOf(operateur) + row.counts.map(Int::toString)).joinToString("\t")
    }

    fun decode(encoded: String?): List<OutageTechRow> = encoded
        ?.lineSequence()
        ?.mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != OutageTechnology.entries.size + 1) return@mapNotNull null
            val operateur = parts[0].takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val counts = parts.drop(1).map { it.trim().toIntOrNull() }
            // Une ligne à moitié lisible vaudrait un compte faux : on la jette entière.
            if (counts.any { it == null }) return@mapNotNull null
            OutageTechRow(operateur, counts.filterNotNull())
        }
        ?.toList()
        .orEmpty()

    private fun countDown(sites: List<SiteHsEntity>, technology: OutageTechnology): Int {
        var down = 0
        var published = false
        sites.forEach { site ->
            val voice = site.voiceStatus(technology)
            val data = site.dataStatus(technology)
            if (OutageStatusCodes.clean(voice) != null || OutageStatusCodes.clean(data) != null) {
                published = true
            }
            if (OutageStatusCodes.isDown(voice) || OutageStatusCodes.isDown(data)) down++
        }
        return if (published) down else UNPUBLISHED
    }
}

private fun SiteHsEntity.voiceStatus(technology: OutageTechnology): String? = when (technology) {
    OutageTechnology.G2 -> voix2g
    OutageTechnology.G3 -> voix3g
    OutageTechnology.G4 -> voix4g
    OutageTechnology.G5 -> voix5g
}

private fun SiteHsEntity.dataStatus(technology: OutageTechnology): String? = when (technology) {
    OutageTechnology.G2 -> data2g
    OutageTechnology.G3 -> data3g
    OutageTechnology.G4 -> data4g
    OutageTechnology.G5 -> data5g
}
