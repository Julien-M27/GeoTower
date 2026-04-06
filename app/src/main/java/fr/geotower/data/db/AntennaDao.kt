package fr.geotower.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import fr.geotower.data.models.Antenna
import fr.geotower.data.models.AntennaMap
import kotlinx.coroutines.flow.Flow

@Dao
interface AntennaDao {

    @Query("SELECT * FROM antennas")
    fun getAllAntennas(): Flow<List<Antenna>>

    @Query("""
        SELECT 
            MIN(localId) AS localId,
            idAnfr,
            MAX(idSupport) AS idSupport,
            operatorName,
            latitude,
            longitude,
            address,
            zipCode,
            MAX(codeInsee) AS codeInsee,
            city,
            supportType,
            height,
            azimuts,
            GROUP_CONCAT(DISTINCT technology) AS technology,
            GROUP_CONCAT(
                CASE 
                    WHEN frequencies LIKE '%|%|%' THEN frequencies 
                    ELSE frequencies || '|' || IFNULL(statut, 'Inconnu') || '|' || IFNULL(implementationDate, '') 
                END, 
            ';;') AS frequencies,
            GROUP_CONCAT(DISTINCT statut) AS statut,
            MIN(implementationDate) AS implementationDate, 
            MAX(activationDate) AS activationDate,
            MAX(modificationDate) AS modificationDate,
            MAX(proprietaire) AS proprietaire,
            MAX(type_antenne) AS type_antenne
        FROM antennas
        WHERE latitude = (SELECT latitude FROM antennas WHERE idAnfr = :siteId LIMIT 1) 
        AND longitude = (SELECT longitude FROM antennas WHERE idAnfr = :siteId LIMIT 1)
        GROUP BY operatorName
    """)
    suspend fun getAntennasBySiteId(siteId: Long): List<Antenna>

    @Query("""
        SELECT 
            MIN(localId) AS localId,
            idAnfr,
            MAX(idSupport) AS idSupport,
            operatorName,
            latitude,
            longitude,
            address,
            zipCode,
            MAX(codeInsee) AS codeInsee,
            city,
            supportType,
            height,
            azimuts,
            GROUP_CONCAT(DISTINCT technology) AS technology,
            GROUP_CONCAT(
                CASE 
                    WHEN frequencies LIKE '%|%|%' THEN frequencies 
                    ELSE frequencies || '|' || IFNULL(statut, 'Inconnu') || '|' || IFNULL(implementationDate, '') 
                END, 
            ';;') AS frequencies,
            GROUP_CONCAT(DISTINCT statut) AS statut,
            MIN(implementationDate) AS implementationDate, 
            MAX(activationDate) AS activationDate,
            MAX(modificationDate) AS modificationDate,
            MAX(proprietaire) AS proprietaire,
            MAX(type_antenne) AS type_antenne
        FROM antennas 
        WHERE idAnfr = :id 
        GROUP BY operatorName
        LIMIT 1
    """)
    suspend fun getAntennaById(id: Long): Antenna?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(antennas: List<Antenna>)

    @Query("DELETE FROM antennas")
    suspend fun clearAll()
    // NOUVEAU : On fait correspondre les colonnes SQL avec les variables de AntennaMap
    @Query("""
        SELECT 
            idAnfr AS id, 
            operatorName AS operateur, 
            latitude, 
            longitude, 
            technology AS technologie, 
            frequencies AS frequences, 
            azimuts 
        FROM antennas 
        WHERE codeInsee = :insee
    """)
    suspend fun getAntennasByInsee(insee: String): List<AntennaMap>
}