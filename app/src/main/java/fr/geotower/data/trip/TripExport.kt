package fr.geotower.data.trip

import com.google.gson.GsonBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Sérialisation des trajets vers l'extérieur.
 *
 * Deux formats, deux usages :
 *
 * - le **GPX** s'ouvre partout (Garmin, OsmAnd, Locus, Google Earth…). C'est lui qui répond au vrai
 *   besoin : ne pas perdre ses tournées si l'app disparaît. Il ne sait en revanche porter ni la date
 *   prévue, ni les rappels, ni l'état « visité » — d'où le second format ;
 * - le **JSON** est le trajet complet, tel que l'app le stocke, en vue d'un réimport.
 *
 * Les deux fonctions sont pures : ni `Context`, ni fichier, ni horloge. C'est ce qui les rend
 * testables, et l'horodatage se passe en paramètre.
 */
object TripExport {
    const val GPX_MIME_TYPE = "application/gpx+xml"
    const val JSON_MIME_TYPE = "application/json"

    const val GPX_EXTENSION = "gpx"
    const val JSON_EXTENSION = "json"

    private const val CREATOR = "GeoTower"

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun buildJson(plans: List<TripPlan>): String = gson.toJson(plans)

    /**
     * Un `<trk>` par trajet, un `<trkseg>` par segment, un `<wpt>` par étape.
     *
     * Deux partis pris à connaître :
     * - sur une boucle, le retour au départ est bien dans la trace, mais le point de départ n'est
     *   **pas** répété en `<wpt>` : ce serait une étape à relever deux fois ;
     * - un segment non calculé sort en trait direct de deux points. Le GPX n'a aucun moyen de dire
     *   « je ne sais pas », et une trace trouée se lit comme une perte de données, alors qu'une
     *   ligne droite se lit pour ce qu'elle est. C'est aussi ce que l'app dessine à l'écran.
     *
     * @param stepFallbackLabel libellé d'une étape sans nom, par indice — passé par l'appelant pour
     *   rester traduit.
     */
    fun buildGpx(
        plans: List<TripPlan>,
        nowMillis: Long,
        stepFallbackLabel: (Int) -> String = { "${it + 1}" }
    ): String {
        val builder = StringBuilder(1_024)
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        builder.append("<gpx version=\"1.1\" creator=\"").append(CREATOR).append("\" ")
        builder.append("xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        builder.append("  <metadata>\n")
        builder.append("    <name>").append(escapeXml(metadataName(plans))).append("</name>\n")
        builder.append("    <time>").append(formatInstant(nowMillis)).append("</time>\n")
        builder.append("  </metadata>\n")

        for (plan in plans) {
            plan.steps.forEachIndexed { index, step ->
                builder.append("  <wpt lat=\"").append(formatCoordinate(step.latitude))
                builder.append("\" lon=\"").append(formatCoordinate(step.longitude)).append("\">\n")
                val label = step.label.takeIf { it.isNotBlank() } ?: stepFallbackLabel(index)
                builder.append("    <name>").append(escapeXml(label)).append("</name>\n")
                builder.append("  </wpt>\n")
            }

            builder.append("  <trk>\n")
            builder.append("    <name>").append(escapeXml(plan.name)).append("</name>\n")
            for ((fromIndex, toIndex) in plan.legPairs()) {
                val points = plan.legBetween(fromIndex, toIndex)
                    ?.points()
                    ?.takeIf { it.size >= 2 }
                    ?: listOf(
                        doubleArrayOf(plan.steps[fromIndex].latitude, plan.steps[fromIndex].longitude),
                        doubleArrayOf(plan.steps[toIndex].latitude, plan.steps[toIndex].longitude)
                    )
                builder.append("    <trkseg>\n")
                for (point in points) {
                    builder.append("      <trkpt lat=\"").append(formatCoordinate(point[0]))
                    builder.append("\" lon=\"").append(formatCoordinate(point[1])).append("\"/>\n")
                }
                builder.append("    </trkseg>\n")
            }
            builder.append("  </trk>\n")
        }

        builder.append("</gpx>\n")
        return builder.toString()
    }

    /** Nom de fichier proposé, sans extension et sans caractère interdit. */
    fun fileStem(plans: List<TripPlan>): String {
        val stem = plans.singleOrNull()?.name?.takeIf { it.isNotBlank() } ?: "trajets-geotower"
        return stem.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '-' }
            .joinToString("")
            .replace(Regex("-{2,}"), "-")
            .trim('-')
            .take(60)
            .ifBlank { "trajets-geotower" }
    }

    private fun metadataName(plans: List<TripPlan>): String =
        plans.singleOrNull()?.name?.takeIf { it.isNotBlank() } ?: CREATOR

    private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.6f", value)

    /**
     * ISO 8601 en UTC. `SimpleDateFormat` et non `java.time` : le projet est en minSdk 24 sans
     * désucrage, et `Instant` planterait sur Android 7.
     */
    private fun formatInstant(millis: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(millis))
    }

    private fun escapeXml(value: String): String {
        val builder = StringBuilder(value.length + 16)
        for (character in value) {
            when (character) {
                '&' -> builder.append("&amp;")
                '<' -> builder.append("&lt;")
                '>' -> builder.append("&gt;")
                '"' -> builder.append("&quot;")
                '\'' -> builder.append("&apos;")
                else -> builder.append(character)
            }
        }
        return builder.toString()
    }
}
