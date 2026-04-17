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

    // =================================================================
    // 1. POUR LA CARTE (Affichage ultra-rapide des points)
    // =================================================================
    suspend fun getAntennasInBox(latNorth: Double, lonEast: Double, latSouth: Double, lonWest: Double): List<LocalisationEntity> {
        return try {
            dao.getLocalisationsInBox(
                minLat = latSouth,
                maxLat = latNorth,
                minLon = lonWest,
                maxLon = lonEast
            )
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getNearest100(lat: Double, lon: Double): List<LocalisationEntity> {
        return dao.getNearest100(lat, lon)
    }

    // =================================================================
    // 1.5 POUR LA CARTE (Mode Macro : Clustering progressif à 5 niveaux)
    // =================================================================
    suspend fun getClusteredAntennas(zoom: Double, latNorth: Double, lonEast: Double, latSouth: Double, lonWest: Double): List<DbCluster> {
        return try {
            when {
                zoom < 6.5 -> dao.getL1Clusters(minLat = latSouth, maxLat = latNorth, minLon = lonWest, maxLon = lonEast)
                zoom < 8.0 -> dao.getL2Clusters(minLat = latSouth, maxLat = latNorth, minLon = lonWest, maxLon = lonEast)
                zoom < 9.5 -> dao.getL3Clusters(minLat = latSouth, maxLat = latNorth, minLon = lonWest, maxLon = lonEast)
                zoom < 10.5 -> dao.getL4Clusters(minLat = latSouth, maxLat = latNorth, minLon = lonWest, maxLon = lonEast)
                zoom < 11.5 -> dao.getL5Clusters(minLat = latSouth, maxLat = latNorth, minLon = lonWest, maxLon = lonEast)
                zoom < 12.5 -> dao.getL6Clusters(minLat = latSouth, maxLat = latNorth, minLon = lonWest, maxLon = lonEast)
                else -> dao.getL7Clusters(minLat = latSouth, maxLat = latNorth, minLon = lonWest, maxLon = lonEast)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // =================================================================
    // 2. POUR LES DÉTAILS (Quand on clique sur une antenne)
    // =================================================================
    suspend fun getTechniqueDetails(idAnfr: String): TechniqueEntity? {
        return dao.getTechniqueDetails(idAnfr)
    }

    suspend fun getPhysiqueDetails(idAnfr: String): List<PhysiqueEntity> {
        return dao.getPhysiqueDetails(idAnfr)
    }

    suspend fun getFaisceauxDetails(idAnfr: String): List<FaisceauxEntity> {
        return dao.getFaisceauxDetails(idAnfr)
    }

    suspend fun getPhysiqueByAnfr(idAnfr: String): List<PhysiqueEntity> {
        return dao.getPhysiqueByAnfr(idAnfr)
    }

    suspend fun getTechniqueByAnfr(idAnfr: String): List<TechniqueEntity> {
        return dao.getTechniqueByAnfr(idAnfr)
    }

    suspend fun searchAntennasById(query: String): List<LocalisationEntity> {
        return dao.searchAntennasById(query)
    }

    suspend fun getAntennasByExactId(exactId: String): List<LocalisationEntity> {
        return dao.getAntennasByExactId(exactId)
    }

    suspend fun getUniqueSupportCountByOperator(operatorName: String): Int {
        return try {
            dao.getUniqueSupportCountByOperator(operatorName)
        } catch (e: Exception) {
            0
        }
    }

    suspend fun get4GSupportCountByOperator(operatorName: String): Int {
        return try {
            dao.get4GSupportCountByOperator(operatorName)
        } catch (e: Exception) {
            0
        }
    }

    suspend fun get5GSupportCountByOperator(operatorName: String): Int {
        return try {
            dao.get5GSupportCountByOperator(operatorName)
        } catch (e: Exception) {
            0
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

            // 3. On extrait chaque point
            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val properties = feature.getJSONObject("properties")
                val geometry = feature.getJSONObject("geometry")
                val coordinates = geometry.getJSONArray("coordinates")

                // GeoJSON range toujours [Longitude, Latitude]
                val lon = coordinates.getDouble(0)
                val lat = coordinates.getDouble(1)

                // 1. Extraction de toutes les propriétés du JSON
                val stationAnfr = properties.optString("station_anfr", "")
                val operateurStr = properties.optString("operateur", "")

                // Détail technique des pannes par technologie
                val v2g = properties.optString("voix2g", null)
                val v3g = properties.optString("voix3g", null)
                val v4g = properties.optString("voix4g", null)

                val d3g = properties.optString("data3g", null)
                val d4g = properties.optString("data4g", null)
                val d5g = properties.optString("data5g", null)

                // Infos de localisation
                val dept = properties.optString("departement", null)
                val cp = properties.optString("code_postal", null)
                val insee = properties.optString("code_insee", null)
                val com = properties.optString("commune", null)

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
                    voixGlobal = properties.optString("voix", null),
                    dataGlobal = properties.optString("data", null),
                    raison = properties.optString("raison", null),
                    detail = properties.optString("detail", null),
                    propre = properties.optInt("propre", 0),

                    // Dates
                    debutVoix = properties.optString("debut_voix", null),
                    finVoix = properties.optString("fin_voix", null),
                    debutData = properties.optString("debut_data", null),
                    finData = properties.optString("fin_data", null),
                    dateDebut = properties.optString("debut", null),
                    dateFin = properties.optString("fin", null)
                )
                hsList.add(site)
            }

            // 🚨 AJOUT : Sauvegarde de la date du jour (Dernière vérification réussie)
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val currentDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            prefs.edit().putString("last_hs_update", currentDate).apply()

            hsList
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}