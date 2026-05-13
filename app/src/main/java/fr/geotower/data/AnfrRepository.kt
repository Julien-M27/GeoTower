package fr.geotower.data

import android.content.Context
import fr.geotower.data.api.AnfrService
import fr.geotower.data.db.AppDatabase
import fr.geotower.data.db.GeoTowerDao
import fr.geotower.data.models.DbCluster
import fr.geotower.data.models.FaisceauxEntity
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.data.api.SignalQuestClient
import fr.geotower.data.models.SiteHsEntity
import fr.geotower.utils.AppLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AnfrRepository(
    private val api: AnfrService,
    private val context: Context // ✅ NOUVEAU : On passe le context
) {

    // ✅ NOUVEAU : On récupère toujours le DAO depuis l'instance active (qui se recréera si elle a été fermée)
    private val dao: GeoTowerDao
        get() = AppDatabase.getDatabase(context).geoTowerDao()

    private suspend fun <T> queryLocalDatabase(defaultValue: T, block: suspend GeoTowerDao.() -> T): T {
        return try {
            dao.block()
        } catch (e: Exception) {
            AppLogger.w(TAG_DB, "Local database query failed", e)
            defaultValue
        }
    }

    // =================================================================
    // 1. POUR LA CARTE (Affichage ultra-rapide des points)
    // =================================================================
    suspend fun getAntennasInBox(latNorth: Double, lonEast: Double, latSouth: Double, lonWest: Double): List<LocalisationEntity> {
        return queryLocalDatabase(emptyList()) {
            getLocalisationsInBox(
                minLat = latSouth,
                maxLat = latNorth,
                minLon = lonWest,
                maxLon = lonEast
            )
        }
    }

    suspend fun getNearest100(lat: Double, lon: Double): List<LocalisationEntity> {
        return queryLocalDatabase(emptyList()) {
            val radii = listOf(0.03, 0.08, 0.18, 0.45, 1.0, 2.5, 5.0)
            var bestResult = emptyList<LocalisationEntity>()

            for (radius in radii) {
                val nearest = getNearestWithinRadius(
                    lat = lat,
                    lon = lon,
                    minLat = lat - radius,
                    maxLat = lat + radius,
                    minLon = lon - radius,
                    maxLon = lon + radius,
                    maxDistanceSquared = radius * radius,
                    limit = 100
                )

                if (nearest.size > bestResult.size) bestResult = nearest
                if (nearest.size >= 100) return@queryLocalDatabase nearest
            }

            bestResult.ifEmpty { getNearest100(lat, lon) }
        }
    }

    // =================================================================
    // 1.5 POUR LA CARTE (Mode Macro : Clustering progressif à 5 niveaux)
    // =================================================================
    suspend fun getClusteredAntennas(zoom: Double, latNorth: Double, lonEast: Double, latSouth: Double, lonWest: Double): List<DbCluster> {
        return queryLocalDatabase(emptyList()) {
            when {
                zoom < 6.5 -> getL1Clusters(minLat = latSouth, maxLat = latNorth, minLon = lonWest, maxLon = lonEast)
                zoom < 8.0 -> getL2Clusters(minLat = latSouth, maxLat = latNorth, minLon = lonWest, maxLon = lonEast)
                zoom < 9.5 -> getL3Clusters(minLat = latSouth, maxLat = latNorth, minLon = lonWest, maxLon = lonEast)
                zoom < 10.5 -> getL4Clusters(minLat = latSouth, maxLat = latNorth, minLon = lonWest, maxLon = lonEast)
                zoom < 11.5 -> getL5Clusters(minLat = latSouth, maxLat = latNorth, minLon = lonWest, maxLon = lonEast)
                zoom < 12.5 -> getL6Clusters(minLat = latSouth, maxLat = latNorth, minLon = lonWest, maxLon = lonEast)
                else -> getL7Clusters(minLat = latSouth, maxLat = latNorth, minLon = lonWest, maxLon = lonEast)
            }
        }
    }

    // =================================================================
    // 2. POUR LES DÉTAILS (Quand on clique sur une antenne)
    // =================================================================
    suspend fun getTechniqueDetails(idAnfr: String): TechniqueEntity? {
        return queryLocalDatabase(null) {
            getTechniqueDetails(idAnfr)
        }
    }

    suspend fun getPhysiqueDetails(idAnfr: String): List<PhysiqueEntity> {
        return queryLocalDatabase(emptyList()) {
            getPhysiqueDetails(idAnfr)
        }
    }

    suspend fun getTechniqueDetailsByIds(idAnfrs: List<String>): Map<String, TechniqueEntity> {
        val distinctIds = idAnfrs.filter { it.isNotBlank() }.distinct()
        if (distinctIds.isEmpty()) return emptyMap()

        return distinctIds.chunked(SQLITE_IN_CLAUSE_BATCH_SIZE)
            .flatMap { chunk ->
                queryLocalDatabase(emptyList<TechniqueEntity>()) {
                    getTechniqueDetailsByIds(chunk)
                }
            }
            .associateBy { it.idAnfr }
    }

    suspend fun getTechniqueSummariesByIds(idAnfrs: List<String>): Map<String, TechniqueEntity> {
        val distinctIds = idAnfrs.filter { it.isNotBlank() }.distinct()
        if (distinctIds.isEmpty()) return emptyMap()

        return distinctIds.chunked(SQLITE_IN_CLAUSE_BATCH_SIZE)
            .flatMap { chunk ->
                queryLocalDatabase(emptyList<TechniqueEntity>()) {
                    getTechniqueSummariesByIds(chunk)
                }
            }
            .associateBy { it.idAnfr }
    }

    suspend fun getPhysiqueDetailsByIds(idAnfrs: List<String>): Map<String, List<PhysiqueEntity>> {
        val distinctIds = idAnfrs.filter { it.isNotBlank() }.distinct()
        if (distinctIds.isEmpty()) return emptyMap()

        return distinctIds.chunked(SQLITE_IN_CLAUSE_BATCH_SIZE)
            .flatMap { chunk ->
                queryLocalDatabase(emptyList<PhysiqueEntity>()) {
                    getPhysiqueDetailsByIds(chunk)
                }
            }
            .groupBy { it.idAnfr }
    }

    suspend fun getPhysiqueSummariesByIds(idAnfrs: List<String>): Map<String, List<PhysiqueEntity>> {
        val distinctIds = idAnfrs.filter { it.isNotBlank() }.distinct()
        if (distinctIds.isEmpty()) return emptyMap()

        return distinctIds.chunked(SQLITE_IN_CLAUSE_BATCH_SIZE)
            .flatMap { chunk ->
                queryLocalDatabase(emptyList<PhysiqueEntity>()) {
                    getPhysiqueSummariesByIds(chunk)
                }
            }
            .groupBy { it.idAnfr }
    }

    suspend fun getFaisceauxDetails(idAnfr: String): List<FaisceauxEntity> {
        return queryLocalDatabase(emptyList()) {
            getFaisceauxDetails(idAnfr)
        }
    }

    suspend fun getPhysiqueByAnfr(idAnfr: String): List<PhysiqueEntity> {
        return queryLocalDatabase(emptyList()) {
            getPhysiqueByAnfr(idAnfr)
        }
    }

    suspend fun getTechniqueByAnfr(idAnfr: String): List<TechniqueEntity> {
        return queryLocalDatabase(emptyList()) {
            getTechniqueByAnfr(idAnfr)
        }
    }

    suspend fun searchAntennasById(query: String): List<LocalisationEntity> {
        return queryLocalDatabase(emptyList()) {
            searchAntennasById(query)
        }
    }

    suspend fun searchAntennasByText(query: String, limit: Int = 200): List<LocalisationEntity> {
        return queryLocalDatabase(emptyList()) {
            searchAntennasByText(query, limit)
        }
    }

    suspend fun searchAntennasByAddress(query: String, limit: Int = 5000): List<LocalisationEntity> {
        return queryLocalDatabase(emptyList()) {
            searchAntennasByAddress(query, limit)
        }
    }

    suspend fun getAntennasByExactId(exactId: String): List<LocalisationEntity> {
        return queryLocalDatabase(emptyList()) {
            getAntennasByExactId(exactId)
        }
    }

    suspend fun getUniqueSupportCountByOperator(operatorName: String): Int {
        return queryLocalDatabase(0) {
            getUniqueSupportCountByOperator(operatorName)
        }
    }

    suspend fun get4GSupportCountByOperator(operatorName: String): Int {
        return queryLocalDatabase(0) {
            get4GSupportCountByOperator(operatorName)
        }
    }

    suspend fun get5GSupportCountByOperator(operatorName: String): Int {
        return queryLocalDatabase(0) {
            get5GSupportCountByOperator(operatorName)
        }
    }

    // =================================================================
    // 3. PANNES RÉSEAU (Nouveau système GeoJSON GeoTower)
    // =================================================================
    suspend fun getSitesHs(): List<SiteHsEntity> {
        return try {
            // 1. On télécharge le fichier brut depuis ton serveur
            val response = api.getSitesHsGeoJson()
            val jsonString = response.string()

            // 2. On lit la structure GeoJSON
            val jsonObject = org.json.JSONObject(jsonString)
            val features = jsonObject.getJSONArray("features")

            val hsList = mutableListOf<SiteHsEntity>()

            // 3. On extrait chaque point (Version blindée anti-crash)
            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val properties = feature.optJSONObject("properties") ?: org.json.JSONObject()

                // 🚨 CORRECTION : L'ARCEP publie parfois des pannes SANS coordonnées GPS !
                // optJSONObject évite que l'application ne crashe si la géométrie est absente.
                val geometry = feature.optJSONObject("geometry")
                val coordinates = geometry?.optJSONArray("coordinates")

                // GeoJSON range toujours [Longitude, Latitude]
                val lon = coordinates?.optDouble(0, 0.0) ?: 0.0
                val lat = coordinates?.optDouble(1, 0.0) ?: 0.0

                // 1. Extraction de toutes les propriétés du JSON
                val stationAnfr = properties.optString("station_anfr", "")
                val operateurStr = properties.optString("operateur", "")

                // Détail technique des pannes par technologie
                val v2g = properties.optNullableString("voix2g")
                val v3g = properties.optNullableString("voix3g")
                val v4g = properties.optNullableString("voix4g")

                val d3g = properties.optNullableString("data3g")
                val d4g = properties.optNullableString("data4g")
                val d5g = properties.optNullableString("data5g")

                // Infos de localisation
                val dept = properties.optNullableString("departement")
                val cp = properties.optNullableString("code_postal")
                val insee = properties.optNullableString("code_insee")
                val com = properties.optNullableString("commune")

                // 2. Création de l'objet complet
                val site = SiteHsEntity(
                    idAnfr = stationAnfr,
                    operateur = operateurStr,
                    latitude = lat,
                    longitude = lon,

                    // Localisation
                    departement = dept,
                    codePostal = cp,
                    codeInsee = insee,
                    commune = com,

                    // Voix
                    voix2g = v2g,
                    voix3g = v3g,
                    voix4g = v4g,

                    // Data
                    data3g = d3g,
                    data4g = d4g,
                    data5g = d5g,

                    // Global et Détails
                    voixGlobal = properties.optNullableString("voix"),
                    dataGlobal = properties.optNullableString("data"),
                    raison = properties.optNullableString("raison"),
                    detail = properties.optNullableString("detail"),
                    propre = properties.optInt("propre", 0),

                    // Dates
                    debutVoix = properties.optNullableString("debut_voix"),
                    finVoix = properties.optNullableString("fin_voix"),
                    debutData = properties.optNullableString("debut_data"),
                    finData = properties.optNullableString("fin_data"),
                    dateDebut = properties.optNullableString("debut"),
                    dateFin = properties.optNullableString("fin")
                )
                hsList.add(site)
            }

            // 🚨 AJOUT : Sauvegarde de la date du jour (Dernière vérification réussie)
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val currentDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            prefs.edit().putString("last_hs_update", currentDate).apply()

            hsList
        } catch (e: Exception) {
            AppLogger.w(TAG_MAP, "Outage data request failed", e)
            emptyList()
        }
    }

    private fun org.json.JSONObject.optNullableString(name: String): String? {
        return if (isNull(name)) null else optString(name)
    }

    private companion object {
        const val TAG_DB = "GeoTowerDb"
        const val TAG_MAP = "GeoTowerMap"
        const val SQLITE_IN_CLAUSE_BATCH_SIZE = 900
    }
}
