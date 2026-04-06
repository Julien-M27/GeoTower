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
}