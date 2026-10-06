package fr.geotower.data.outages

import fr.geotower.data.models.SiteHsEntity
import org.json.JSONObject

/**
 * Parseur GeoJSON unifié pour les sites en panne (utilisé pour le fichier serveur GeoTower
 * et pour le fichier journalier officiel Arcep / data.gouv.fr).
 */
object SitesHsGeoJsonParser {

    data class ParseResult(
        val sites: List<SiteHsEntity>,
        val generatedAtIso: String? = null,
    )

    fun parse(jsonString: String, sourceLastUpdate: String? = null): ParseResult {
        val jsonObject = JSONObject(jsonString)
        val features = jsonObject.optJSONArray("features") ?: return ParseResult(emptyList())
        val generatedAtIso = jsonObject.optJSONObject("metadata")?.optNullableString("generated_at")

        val hsList = ArrayList<SiteHsEntity>(features.length())

        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val properties = feature.optJSONObject("properties") ?: JSONObject()

            val geometry = feature.optJSONObject("geometry")
            val coordinates = geometry?.optJSONArray("coordinates")
            val geometryType = geometry?.optNullableString("type")

            val lon = coordinates?.optDouble(0, 0.0) ?: 0.0
            val lat = coordinates?.optDouble(1, 0.0) ?: 0.0

            val stationAnfr = properties.optString("station_anfr", "").trim()
            val operateurStr = properties.optString("operateur", "").trim()
            if (operateurStr.isEmpty()) continue

            val site = SiteHsEntity(
                idAnfr = stationAnfr,
                operateur = operateurStr,
                latitude = lat,
                longitude = lon,
                geometryType = geometryType,

                departement = properties.optNullableString("departement"),
                codePostal = properties.optNullableString("code_postal"),
                codeInsee = properties.optNullableString("code_insee"),
                commune = properties.optNullableString("commune"),

                voix2g = properties.optNullableString("voix2g"),
                voix3g = properties.optNullableString("voix3g"),
                voix4g = properties.optNullableString("voix4g"),
                voix5g = properties.optNullableString("voix5g"),

                data2g = properties.optNullableString("data2g"),
                data3g = properties.optNullableString("data3g"),
                data4g = properties.optNullableString("data4g"),
                data5g = properties.optNullableString("data5g"),

                voixGlobal = properties.optNullableString("voix"),
                dataGlobal = properties.optNullableString("data"),
                raison = properties.optNullableString("raison"),
                detail = properties.optNullableString("detail"),
                propre = properties.optInt("propre", 0),

                debutVoix = properties.optNullableString("debut_voix"),
                finVoix = properties.optNullableString("fin_voix"),
                debutData = properties.optNullableString("debut_data"),
                finData = properties.optNullableString("fin_data"),
                dateDebut = properties.optNullableString("debut"),
                dateFin = properties.optNullableString("fin"),
                sourceLastUpdate = sourceLastUpdate
            )
            hsList.add(site)
        }

        return ParseResult(hsList, generatedAtIso)
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (isNull(name)) return null
        val str = optString(name).trim()
        return str.ifEmpty { null }
    }
}
