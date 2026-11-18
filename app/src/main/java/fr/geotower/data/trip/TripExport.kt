package fr.geotower.data.trip

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import fr.geotower.data.api.RouteApi
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory

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

    /** Relit un export JSON GeoTower, en écartant les trajets invalides ou vides. */
    fun parseJson(content: String): List<TripPlan> {
        val type = object : TypeToken<List<TripPlan>>() {}.type
        val plans: List<TripPlan>? = gson.fromJson(content, type)
        return plans.orEmpty().mapNotNull { it.sanitized() }.filterNot { it.isEmptyDraft() }
    }

    /** Détecte automatiquement JSON ou GPX et retourne les trajets valides. */
    fun parseAny(content: String, nowMillis: Long = System.currentTimeMillis()): List<TripPlan> {
        val trimmed = content.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        return if (trimmed.startsWith("<") && trimmed.contains("<gpx", ignoreCase = true)) {
            parseGpx(content, nowMillis)
        } else {
            parseJson(content)
        }
    }

    /**
     * Relit un fichier GPX 1.1 (`<wpt>` et/ou `<trk>` / `<rte>`) et construit un ou plusieurs [TripPlan].
     * Les `<wpt>` deviennent des étapes ; à défaut, les `<rtept>` ou points de `<trkseg>` (départ/arrivée
     * ou points échantillonnés) sont utilisés.
     */
    fun parseGpx(content: String, nowMillis: Long = System.currentTimeMillis()): List<TripPlan> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)))
        val root = doc.documentElement ?: return emptyList()
        if (!root.tagName.equals("gpx", ignoreCase = true)) return emptyList()

        fun Element.childText(tag: String): String? {
            val nodes = getElementsByTagName(tag)
            return if (nodes.length > 0) nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() } else null
        }

        fun Element.toStep(): TripStep? {
            val lat = getAttribute("lat").toDoubleOrNull() ?: return null
            val lon = getAttribute("lon").toDoubleOrNull() ?: return null
            val label = childText("name") ?: childText("desc") ?: ""
            return TripStep(
                latitude = lat,
                longitude = lon,
                label = label,
                kind = TripStep.KIND_MANUAL,
                supportId = null,
                visitedAtMillis = null,
                note = childText("cmt"),
                profileToNext = null,
                photosSentCount = 0
            ).sanitized()
        }

        val waypoints = mutableListOf<TripStep>()
        val wptNodes = root.getElementsByTagName("wpt")
        for (i in 0 until wptNodes.length) {
            val el = wptNodes.item(i) as? Element ?: continue
            el.toStep()?.let { waypoints += it }
        }

        val trkNodes = root.getElementsByTagName("trk")
        val rteNodes = root.getElementsByTagName("rte")
        val metadataName = (root.getElementsByTagName("metadata").item(0) as? Element)?.childText("name")

        val plans = mutableListOf<TripPlan>()
        if (waypoints.isNotEmpty()) {
            val trkName = (trkNodes.item(0) as? Element)?.childText("name")
            val name = trkName ?: metadataName ?: "GPX"
            TripPlan(
                id = UUID.randomUUID().toString(),
                schemaVersion = TripPlanStore.SCHEMA_VERSION,
                name = name,
                createdAtMillis = nowMillis,
                updatedAtMillis = nowMillis,
                profile = RouteApi.PROFILE_CAR,
                returnToStart = false,
                steps = waypoints,
                legs = emptyList(),
                plannedAtMillis = null,
                reminderOffsetsMinutes = TripPlan.DEFAULT_REMINDER_OFFSETS_MINUTES,
                stopDurationMinutes = 10,
                status = TripPlan.STATUS_DRAFT
            ).sanitized()?.takeIf { !it.isEmptyDraft() }?.let { plans += it }
        } else if (rteNodes.length > 0) {
            for (i in 0 until rteNodes.length) {
                val rte = rteNodes.item(i) as? Element ?: continue
                val pts = rte.getElementsByTagName("rtept")
                val steps = (0 until pts.length).mapNotNull { (pts.item(it) as? Element)?.toStep() }
                if (steps.isEmpty()) continue
                TripPlan(
                    id = UUID.randomUUID().toString(),
                    schemaVersion = TripPlanStore.SCHEMA_VERSION,
                    name = rte.childText("name") ?: metadataName ?: "GPX ${i + 1}",
                    createdAtMillis = nowMillis,
                    updatedAtMillis = nowMillis,
                    profile = RouteApi.PROFILE_CAR,
                    returnToStart = false,
                    steps = steps,
                    legs = emptyList(),
                    plannedAtMillis = null,
                    reminderOffsetsMinutes = TripPlan.DEFAULT_REMINDER_OFFSETS_MINUTES,
                    stopDurationMinutes = 10,
                    status = TripPlan.STATUS_DRAFT
                ).sanitized()?.takeIf { !it.isEmptyDraft() }?.let { plans += it }
            }
        } else if (trkNodes.length > 0) {
            for (i in 0 until trkNodes.length) {
                val trk = trkNodes.item(i) as? Element ?: continue
                val pts = trk.getElementsByTagName("trkpt")
                val allPts = (0 until pts.length).mapNotNull { (pts.item(it) as? Element)?.toStep() }
                if (allPts.isEmpty()) continue
                val sampled = if (allPts.size <= 25) {
                    allPts
                } else {
                    val step = (allPts.size - 1) / 24.0
                    (0..24).map { idx -> allPts[(idx * step).toInt().coerceAtMost(allPts.lastIndex)] }
                        .distinctBy { "${it.latitude}_${it.longitude}" }
                }
                TripPlan(
                    id = UUID.randomUUID().toString(),
                    schemaVersion = TripPlanStore.SCHEMA_VERSION,
                    name = trk.childText("name") ?: metadataName ?: "GPX ${i + 1}",
                    createdAtMillis = nowMillis,
                    updatedAtMillis = nowMillis,
                    profile = RouteApi.PROFILE_CAR,
                    returnToStart = false,
                    steps = sampled,
                    legs = emptyList(),
                    plannedAtMillis = null,
                    reminderOffsetsMinutes = TripPlan.DEFAULT_REMINDER_OFFSETS_MINUTES,
                    stopDurationMinutes = 10,
                    status = TripPlan.STATUS_DRAFT
                ).sanitized()?.takeIf { !it.isEmptyDraft() }?.let { plans += it }
            }
        }
        return plans
    }

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
