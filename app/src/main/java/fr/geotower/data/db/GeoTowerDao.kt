package fr.geotower.data.db

import androidx.room.Dao
import androidx.room.Query
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.FaisceauxEntity
import fr.geotower.data.models.DbCluster // ✅ NOUVEL IMPORT

@Dao
interface GeoTowerDao {

    // 1. Pour la carte : on charge tout super vite (ou juste une zone)
    @Query("SELECT * FROM localisation")
    suspend fun getAllLocalisations(): List<LocalisationEntity>

    // TRÈS UTILE pour ta fonction "getAntennasInBox" de ton Repository :
    @Query("""
        SELECT * FROM localisation 
        WHERE latitude BETWEEN :minLat AND :maxLat 
        AND longitude BETWEEN :minLon AND :maxLon
    """)
    suspend fun getLocalisationsInBox(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<LocalisationEntity>

    // 2. Pour le tiroir de détails quand on clique sur un point (rapide, sans jointure)
    @Query("SELECT * FROM technique WHERE id_anfr = :idAnfr")
    suspend fun getTechniqueDetails(idAnfr: String): TechniqueEntity?

    @Query("SELECT * FROM physique WHERE id_anfr = :idAnfr")
    suspend fun getPhysiqueDetails(idAnfr: String): List<PhysiqueEntity>

    @Query("SELECT * FROM faisceaux_hertziens WHERE id_anfr = :idAnfr")
    suspend fun getFaisceauxDetails(idAnfr: String): List<FaisceauxEntity>

    @Query("SELECT * FROM physique WHERE id_anfr = :idAnfr")
    suspend fun getPhysiqueByAnfr(idAnfr: String): List<PhysiqueEntity>

    @Query("SELECT * FROM technique WHERE id_anfr = :idAnfr")
    suspend fun getTechniqueByAnfr(idAnfr: String): List<TechniqueEntity>

    // 🚀 La requête magique qui regroupe les points (pour le dézoom)
    @Query("""
        SELECT 
            ROUND(latitude, 1) as centerLat, 
            ROUND(longitude, 1) as centerLon, 
            COUNT(*) as count 
        FROM localisation 
        WHERE latitude BETWEEN :minLat AND :maxLat 
        AND longitude BETWEEN :minLon AND :maxLon
        GROUP BY ROUND(latitude, 1), ROUND(longitude, 1)
    """)
    suspend fun getClusteredLocalisations(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<DbCluster>

    // 🌍 NIVEAU 1 : National (Zoom < 6.5) -> Blocs de ~250km
    @Query("""
        SELECT AVG(latitude) as centerLat, AVG(longitude) as centerLon, COUNT(*) as count 
        FROM localisation WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon
        GROUP BY ROUND(latitude / 2.5), ROUND(longitude / 3.0)
    """)
    suspend fun getL1Clusters(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<DbCluster>

    // 🗺️ NIVEAU 2 : Régional (Zoom < 8.0) -> Blocs de ~100km
    @Query("""
        SELECT AVG(latitude) as centerLat, AVG(longitude) as centerLon, COUNT(*) as count 
        FROM localisation WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon
        GROUP BY ROUND(latitude / 1.0), ROUND(longitude / 1.2)
    """)
    suspend fun getL2Clusters(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<DbCluster>

    // 📍 NIVEAU 3 : Départemental (Zoom < 9.5) -> Blocs de ~40km
    @Query("""
        SELECT AVG(latitude) as centerLat, AVG(longitude) as centerLon, COUNT(*) as count 
        FROM localisation WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon
        GROUP BY ROUND(latitude / 0.4), ROUND(longitude / 0.5)
    """)
    suspend fun getL3Clusters(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<DbCluster>

    // 🏙️ NIVEAU 4 : Agglomération (Zoom < 10.5) -> Blocs de ~15km
    @Query("""
        SELECT AVG(latitude) as centerLat, AVG(longitude) as centerLon, COUNT(*) as count 
        FROM localisation WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon
        GROUP BY ROUND(latitude / 0.15), ROUND(longitude / 0.2)
    """)
    suspend fun getL4Clusters(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<DbCluster>

    // 🏘️ NIVEAU 5 : Commune (Zoom < 11.5) -> Blocs de ~11km
    @Query("""
        SELECT AVG(latitude) as centerLat, AVG(longitude) as centerLon, COUNT(*) as count 
        FROM localisation WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon
        GROUP BY ROUND(latitude, 1), ROUND(longitude, 1)
    """)
    suspend fun getL5Clusters(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<DbCluster>
    // 🏘️ NIVEAU 6 : Quartier (Zoom < 12.0) -> Blocs de ~5.5km
    @Query("""
        SELECT AVG(latitude) as centerLat, AVG(longitude) as centerLon, COUNT(*) as count 
        FROM localisation WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon
        GROUP BY ROUND(latitude / 0.05), ROUND(longitude / 0.06)
    """)
    suspend fun getL6Clusters(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<DbCluster>

    // 🏢 NIVEAU 7 : Hyper-centre (Zoom < 13.0) -> Blocs de ~2.2km
    @Query("""
        SELECT AVG(latitude) as centerLat, AVG(longitude) as centerLon, COUNT(*) as count 
        FROM localisation WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon
        GROUP BY ROUND(latitude / 0.02), ROUND(longitude / 0.025)
    """)
    suspend fun getL7Clusters(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<DbCluster>

    @androidx.room.Query("SELECT version FROM metadata LIMIT 1")
    suspend fun getDatabaseVersion(): String?

    // Cherche dans les ID ANFR, ou cherche l'ID ANFR correspondant à un ID Support
    @Query("SELECT * FROM localisation WHERE id_anfr LIKE '%' || :query || '%' OR id_anfr IN (SELECT id_anfr FROM physique WHERE id_support LIKE '%' || :query || '%') LIMIT 50")
    suspend fun searchAntennasById(query: String): List<LocalisationEntity>

    // ✅ CORRECTION : On compare les valeurs numériquement pour ignorer les zéros au début !
    @Query("SELECT * FROM localisation WHERE CAST(id_anfr AS INTEGER) = CAST(:exactId AS INTEGER) OR id_anfr IN (SELECT id_anfr FROM physique WHERE CAST(id_support AS INTEGER) = CAST(:exactId AS INTEGER))")
    suspend fun getAntennasByExactId(exactId: String): List<LocalisationEntity>

    // =================================================================
    // 📊 REQUÊTES STATISTIQUES (Croisées avec la table physique pour éviter les doublons
    // et avec la table technique pour ne compter que les sites actifs)
    // =================================================================

    // 1. Compte le nombre de supports (pylônes physiques) uniques EN SERVICE par opérateur
    @Query("""
        SELECT COUNT(DISTINCT p.id_support) 
        FROM physique p 
        INNER JOIN localisation l ON p.id_anfr = l.id_anfr 
        INNER JOIN technique t ON l.id_anfr = t.id_anfr
        WHERE l.operateur LIKE '%' || :operatorName || '%'
        AND (t.statut = 'En service' OR t.statut = 'Techniquement opérationnel')
    """)
    suspend fun getUniqueSupportCountByOperator(operatorName: String): Int

    // 2. Compte le nombre de supports uniques équipés en 4G EN SERVICE
    @Query("""
        SELECT COUNT(DISTINCT p.id_support) 
        FROM physique p 
        INNER JOIN localisation l ON p.id_anfr = l.id_anfr 
        INNER JOIN technique t ON l.id_anfr = t.id_anfr
        WHERE l.operateur LIKE '%' || :operatorName || '%' 
        AND l.filtres LIKE '%4G%'
        AND (t.statut = 'En service' OR t.statut = 'Techniquement opérationnel')
    """)
    suspend fun get4GSupportCountByOperator(operatorName: String): Int

    // 3. Compte le nombre de supports uniques équipés en 5G EN SERVICE
    @Query("""
        SELECT COUNT(DISTINCT p.id_support) 
        FROM physique p 
        INNER JOIN localisation l ON p.id_anfr = l.id_anfr 
        INNER JOIN technique t ON l.id_anfr = t.id_anfr
        WHERE l.operateur LIKE '%' || :operatorName || '%' 
        AND l.filtres LIKE '%5G%'
        AND (t.statut = 'En service' OR t.statut = 'Techniquement opérationnel')
    """)
    suspend fun get5GSupportCountByOperator(operatorName: String): Int

}