package fr.geotower.data.build

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import fr.geotower.utils.DepartmentCodes
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request

/**
 * Telechargement des sources ANFR officielles (HTTPS, hotes de [OfficialSources] uniquement) et
 * parsing des formats auxiliaires (communes JSON, ARCEP CSV).
 *
 * Le parsing ([parseCommunesJson], [parseArcepSites]) est pur et testable en JVM ; les methodes
 * reseau s'appuient sur OkHttp (lib JVM) avec allowlist d'hotes et plafond de taille.
 */
class RawSourceDownloader(private val client: OkHttpClient = defaultClient()) {

    /**
     * Ouvre `url` en flux et le confie a `block`, SANS le poser sur disque (indispensable pour
     * l'observatoire de ~500 Mo). La compression gzip est laissee transparente (OkHttp).
     */
    fun <T> withStream(url: String, block: (InputStream) -> T): T {
        require(OfficialSources.isAllowedHost(url)) { "Hote non autorise: $url" }
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} pour $url")
            val body = response.body ?: throw IOException("Reponse sans corps: $url")
            return body.byteStream().use { block(it) }
        }
    }

    /** Recupere un corps texte (JSON, listing), plafonne a `maxBytes`. */
    fun fetchText(url: String, maxBytes: Long): String {
        require(OfficialSources.isAllowedHost(url)) { "Hote non autorise: $url" }
        val request = Request.Builder().url(url).header("Accept-Encoding", "identity").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} pour $url")
            val body = response.body ?: throw IOException("Reponse sans corps: $url")
            return readCapped(body.byteStream(), maxBytes).toString(Charsets.UTF_8)
        }
    }

    /**
     * Telecharge `url` vers `dest` en flux, plafonne a `maxBytes`. `onProgress(copied, total)` est
     * notifie a chaque bloc ; `total` vaut la taille annoncee (Content-Length) ou -1 si inconnue.
     */
    fun downloadToFile(url: String, dest: File, maxBytes: Long, onProgress: ((copied: Long, total: Long) -> Unit)? = null) {
        require(OfficialSources.isAllowedHost(url)) { "Hote non autorise: $url" }
        val request = Request.Builder().url(url).header("Accept-Encoding", "identity").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} pour $url")
            val body = response.body ?: throw IOException("Reponse sans corps: $url")
            val total = body.contentLength()
            var copied = 0L
            dest.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                body.byteStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        copied += read
                        if (copied > maxBytes) throw IOException("Source trop volumineuse (> $maxBytes octets): $url")
                        output.write(buffer, 0, read)
                        onProgress?.invoke(copied, total)
                    }
                }
                output.flush()
            }
            // Detecte une coupure reseau (fichier tronque -> ZIP illisible ensuite).
            if (total > 0 && copied != total) {
                throw IOException("Telechargement incomplet: $copied/$total octets ($url)")
            }
        }
    }

    companion object {
        /**
         * Client aux timeouts genereux (sources ANFR volumineuses). **HTTP/1.1 force** : les gros
         * telechargements ANFR/CDN provoquent des « stream was reset: PROTOCOL_ERROR » en HTTP/2.
         */
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .retryOnConnectionFailure(true)
            .callTimeout(30, TimeUnit.MINUTES)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()

        private val OPERATOR_ALIASES = listOf("nom_op", "op_name", "operator", "operator_name")
        private val NIDT_ALIASES = listOf("num_site", "op_site_id", "nidt")
        private val STATION_ALIASES = listOf("id_station_anfr", "sta_nm_anfr", "id_anfr", "station_anfr")
        private val SITE_ZB_ALIASES = listOf("site_zb", "zb", "zone_blanche")
        private val SITE_DCC_ALIASES = listOf("site_dcc", "dcc")
        private val TRUE_VALUES = setOf("1", "true", "vrai", "oui", "yes", "y")

        /** Communes geo.api.gouv.fr : `[{"nom":..,"code":..}]` -> `code_insee -> NOM` (majuscules, comme le serveur). */
        fun parseCommunesJson(json: String): Map<String, String> {
            val array = try {
                JsonParser.parseString(json).asJsonArray
            } catch (_: Exception) {
                return emptyMap()
            }
            val result = LinkedHashMap<String, String>()
            for (element in array) {
                val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                val code = AnfrParsing.cleanText(obj.get("code").stringOrNull())
                val nom = AnfrParsing.cleanText(obj.get("nom").stringOrNull()).uppercase()
                if (code.isNotEmpty() && nom.isNotEmpty()) result[code] = nom
            }
            return result
        }

        /**
         * Referentiel departemental : noms depuis `/departements`, superficie et population
         * agregees depuis les communes — meme methode que `fetch_reference_from_api`
         * (docs/server/fr_dept_stats.py), y compris la surface en hectares a diviser par 100.
         *
         * Les COM (975, 977, 978, 986, 987, 988) ne sont ni dans `/departements` ni dans
         * `/communes` : elles n'auront ni nom ni ratios, seulement leurs compteurs ANFR. Le
         * serveur, lui, les interroge une par une ; sur l'appareil, ce serait 12 appels reseau
         * de plus dans un build deja long, pour six territoires.
         */
        fun parseDepartmentReference(
            departementsJson: String,
            communesJson: String,
            populationYear: String? = null,
        ): Map<String, DepartmentReferenceRow> {
            val surfaceHectares = HashMap<String, Double>()
            val population = HashMap<String, Int>()
            val communeCount = HashMap<String, Int>()

            val communes = try {
                JsonParser.parseString(communesJson).asJsonArray
            } catch (_: Exception) {
                null
            }
            communes?.forEach { element ->
                val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val code = AnfrParsing.cleanText(obj.get("codeDepartement").stringOrNull())
                    .ifEmpty { DepartmentCodes.fromInsee(obj.get("code").stringOrNull()).orEmpty() }
                if (code.isEmpty()) return@forEach
                surfaceHectares[code] = (surfaceHectares[code] ?: 0.0) + (obj.get("surface").doubleOrNull() ?: 0.0)
                population[code] = (population[code] ?: 0) + (obj.get("population").intOrNull() ?: 0)
                communeCount[code] = (communeCount[code] ?: 0) + 1
            }

            val departements = try {
                JsonParser.parseString(departementsJson).asJsonArray
            } catch (_: Exception) {
                return emptyMap()
            }
            val result = LinkedHashMap<String, DepartmentReferenceRow>()
            for (element in departements) {
                val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                val code = AnfrParsing.cleanText(obj.get("code").stringOrNull()).uppercase()
                if (code.isEmpty()) continue
                val hasCommunes = (communeCount[code] ?: 0) > 0
                result[code] = DepartmentReferenceRow(
                    code = code,
                    name = AnfrParsing.cleanText(obj.get("nom").stringOrNull()).ifEmpty { null },
                    regionCode = AnfrParsing.cleanText(obj.get("codeRegion").stringOrNull()).ifEmpty { null },
                    areaKm2 = if (hasCommunes) Math.round((surfaceHectares[code] ?: 0.0) / 100.0 * 1000.0) / 1000.0 else null,
                    // Population nulle plutot que zero : certaines communes (TAAF) n'ont pas de
                    // champ `population`, et « 0 habitant » s'afficherait comme une vraie valeur.
                    population = population[code]?.takeIf { hasCommunes && it > 0 },
                    populationYear = populationYear,
                )
            }
            return result
        }

        /**
         * Un departement interroge seul : `/departements/{code}` renvoie un objet (pas un tableau)
         * et ses communes viennent d'un second appel. Sert aux COM, absentes des listes globales.
         */
        fun parseSingleDepartmentReference(
            departementJson: String,
            communesJson: String,
            code: String,
            populationYear: String? = null,
        ): DepartmentReferenceRow? {
            val normalized = code.trim().uppercase()
            if (normalized.isEmpty()) return null
            val obj = try {
                JsonParser.parseString(departementJson).asJsonObject
            } catch (_: Exception) {
                return null
            }

            var surfaceHectares = 0.0
            var population = 0
            var communeCount = 0
            val communes = try {
                JsonParser.parseString(communesJson).asJsonArray
            } catch (_: Exception) {
                null
            }
            communes?.forEach { element ->
                val commune = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                surfaceHectares += commune.get("surface").doubleOrNull() ?: 0.0
                population += commune.get("population").intOrNull() ?: 0
                communeCount++
            }

            val name = AnfrParsing.cleanText(obj.get("nom").stringOrNull()).ifEmpty { null }
            if (name == null && communeCount == 0) return null
            return DepartmentReferenceRow(
                code = normalized,
                name = name,
                regionCode = AnfrParsing.cleanText(obj.get("codeRegion").stringOrNull()).ifEmpty { null },
                areaKm2 = if (communeCount > 0) Math.round(surfaceHectares / 100.0 * 1000.0) / 1000.0 else null,
                population = population.takeIf { communeCount > 0 && it > 0 },
                populationYear = populationYear,
            )
        }

        /**
         * ARCEP "sites" -> `(id_anfr, operateur majuscules) -> (nidt, is_zb)`. Port de
         * `load_arcep_site_metadata` : fusion sur nidt (garde le premier non vide) et is_zb (OU logique).
         */
        fun parseArcepSites(rows: Iterable<AnfrCsvRow>): Map<Pair<String, String>, ArcepSiteMeta> {
            val result = HashMap<Pair<String, String>, ArcepSiteMeta>()
            for (row in rows) {
                val idAnfr = AnfrParsing.normalizeIdAnfr(firstAlias(row, STATION_ALIASES))
                val operator = AnfrParsing.cleanText(firstAlias(row, OPERATOR_ALIASES)).uppercase()
                if (idAnfr.isEmpty() || operator.isEmpty()) continue
                val nidt = AnfrParsing.cleanText(firstAlias(row, NIDT_ALIASES)).ifEmpty { null }
                val zb = parseBool(firstAlias(row, SITE_ZB_ALIASES)) || parseBool(firstAlias(row, SITE_DCC_ALIASES))
                val key = idAnfr to operator
                val previous = result[key]
                result[key] = ArcepSiteMeta(
                    nidt = nidt ?: previous?.nidt,
                    isZb = if (zb || previous?.isZb == 1) 1 else 0,
                )
            }
            return result
        }

        private fun firstAlias(row: AnfrCsvRow, aliases: List<String>): String? =
            aliases.firstNotNullOfOrNull { row.get(it)?.takeIf { value -> value.isNotBlank() } }

        private fun parseBool(value: String?): Boolean =
            AnfrParsing.cleanText(value).lowercase() in TRUE_VALUES

        private fun readCapped(input: InputStream, maxBytes: Long): ByteArray {
            val buffer = ByteArray(64 * 1024)
            val output = java.io.ByteArrayOutputStream()
            var total = 0L
            input.use {
                while (true) {
                    val read = it.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) throw IOException("Reponse trop volumineuse (> $maxBytes octets)")
                    output.write(buffer, 0, read)
                }
            }
            return output.toByteArray()
        }

        private fun JsonElement?.stringOrNull(): String? =
            this?.takeIf { it.isJsonPrimitive }?.asString

        private fun JsonElement?.doubleOrNull(): Double? =
            this?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble }.getOrNull() }

        private fun JsonElement?.intOrNull(): Int? =
            this?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }
    }
}
