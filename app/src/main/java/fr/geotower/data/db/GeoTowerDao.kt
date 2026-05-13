package fr.geotower.data.db

import androidx.room.Dao
import androidx.room.Query
import fr.geotower.data.models.DbCluster
import fr.geotower.data.models.FaisceauxEntity
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.TechniqueEntity

@Dao
interface GeoTowerDao {

    @Query("""
        SELECT
            l.id_anfr,
            COALESCE(o.libelle, 'Inconnu') AS operateur,
            l.latitude,
            l.longitude,
            l.azimuts,
            l.code_insee,
            l.azimuts_fh,
            l.tech_mask,
            l.band_mask
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        WHERE l.latitude BETWEEN :minLat AND :maxLat
        AND l.longitude BETWEEN :minLon AND :maxLon
    """)
    suspend fun getLocalisationsInBox(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<LocalisationEntity>

    @Query("""
        SELECT
            l.id_anfr,
            COALESCE(o.libelle, 'Inconnu') AS operateur,
            l.latitude,
            l.longitude,
            l.azimuts,
            l.code_insee,
            l.azimuts_fh,
            l.tech_mask,
            l.band_mask
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        LEFT JOIN technique t ON l.id_anfr = t.id_anfr
        LEFT JOIN ref_statut st ON t.statut_id = st.id
        WHERE l.latitude BETWEEN :minLat AND :maxLat
        AND l.longitude BETWEEN :minLon AND :maxLon
        AND UPPER(COALESCE(o.libelle, '')) LIKE '%' || UPPER(:operatorName) || '%'
        AND (
            COALESCE(st.libelle, '') IN ('En service', 'Techniquement opérationnel', 'Techniquement operationnel')
            OR COALESCE(t.has_active, 0) = 1
        )
    """)
    suspend fun getActiveLocalisationsInBoxByOperator(
        operatorName: String,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): List<LocalisationEntity>

    @Query("""
        SELECT
            l.id_anfr,
            COALESCE(o.libelle, 'Inconnu') AS operateur,
            l.latitude,
            l.longitude,
            l.azimuts,
            l.code_insee,
            l.azimuts_fh,
            l.tech_mask,
            l.band_mask
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        ORDER BY ((l.latitude - :lat) * (l.latitude - :lat) + (l.longitude - :lon) * (l.longitude - :lon)) ASC
        LIMIT 100
    """)
    suspend fun getNearest100(lat: Double, lon: Double): List<LocalisationEntity>

    @Query("""
        SELECT
            l.id_anfr,
            COALESCE(o.libelle, 'Inconnu') AS operateur,
            l.latitude,
            l.longitude,
            l.azimuts,
            l.code_insee,
            l.azimuts_fh,
            l.tech_mask,
            l.band_mask
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        WHERE l.latitude BETWEEN :minLat AND :maxLat
        AND l.longitude BETWEEN :minLon AND :maxLon
        AND ((l.latitude - :lat) * (l.latitude - :lat) + (l.longitude - :lon) * (l.longitude - :lon)) <= :maxDistanceSquared
        ORDER BY ((l.latitude - :lat) * (l.latitude - :lat) + (l.longitude - :lon) * (l.longitude - :lon)) ASC
        LIMIT :limit
    """)
    suspend fun getNearestWithinRadius(
        lat: Double,
        lon: Double,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        maxDistanceSquared: Double,
        limit: Int
    ): List<LocalisationEntity>

    @Query("""
        SELECT
            t.id_anfr,
            RTRIM(
                CASE WHEN (l.tech_mask & 1) != 0 THEN '2G, ' ELSE '' END ||
                CASE WHEN (l.tech_mask & 2) != 0 THEN '3G, ' ELSE '' END ||
                CASE WHEN (l.tech_mask & 4) != 0 THEN '4G, ' ELSE '' END ||
                CASE WHEN (l.tech_mask & 8) != 0 THEN '5G, ' ELSE '' END ||
                CASE WHEN (l.tech_mask & 16) != 0 THEN 'FH, ' ELSE '' END,
                ', '
            ) AS technologies,
            COALESCE(st.libelle, '') AS statut,
            t.date_implantation,
            t.date_service,
            t.date_modif,
            t.details_frequences AS details_frequences,
            t.adresse
        FROM technique t
        INNER JOIN localisation l ON t.id_anfr = l.id_anfr
        LEFT JOIN ref_statut st ON t.statut_id = st.id
        WHERE t.id_anfr = :idAnfr
    """)
    suspend fun getTechniqueDetails(idAnfr: String): TechniqueEntity?

    @Query("""
        SELECT
            s.id_anfr,
            s.id_support,
            COALESCE(n.libelle, CASE WHEN s.nat_id IS NULL THEN NULL ELSE 'Code Nature ' || CAST(s.nat_id AS TEXT) END) AS nature_support,
            COALESCE(p.libelle, 'Inconnu') AS proprietaire,
            s.hauteur,
            (
                SELECT GROUP_CONCAT(antenna_line, char(10))
                FROM (
                    SELECT DISTINCT
                        COALESCE(ta.libelle, 'Type inconnu (' || COALESCE(CAST(a.tae_id AS TEXT), '') || ')') ||
                        ' : ' || COALESCE(CAST(a.azimut AS TEXT), 'N/A') ||
                        '° (' || COALESCE(CAST(a.hauteur_bas AS TEXT), 'N/A') || 'm)' AS antenna_line
                    FROM antenne a
                    LEFT JOIN ref_type_antenne ta ON a.tae_id = ta.tae_id
                    WHERE a.id_anfr = s.id_anfr
                    AND a.id_support = s.id_support
                    AND a.is_fh = 0
                    ORDER BY a.azimut
                )
            ) AS azimuts_et_types
        FROM support s
        LEFT JOIN ref_nature n ON s.nat_id = n.nat_id
        LEFT JOIN ref_proprietaire p ON s.tpo_id = p.tpo_id
        WHERE s.id_anfr = :idAnfr
    """)
    suspend fun getPhysiqueDetails(idAnfr: String): List<PhysiqueEntity>

    @Query("""
        SELECT
            t.id_anfr,
            RTRIM(
                CASE WHEN (l.tech_mask & 1) != 0 THEN '2G, ' ELSE '' END ||
                CASE WHEN (l.tech_mask & 2) != 0 THEN '3G, ' ELSE '' END ||
                CASE WHEN (l.tech_mask & 4) != 0 THEN '4G, ' ELSE '' END ||
                CASE WHEN (l.tech_mask & 8) != 0 THEN '5G, ' ELSE '' END ||
                CASE WHEN (l.tech_mask & 16) != 0 THEN 'FH, ' ELSE '' END,
                ', '
            ) AS technologies,
            COALESCE(st.libelle, '') AS statut,
            t.date_implantation,
            t.date_service,
            t.date_modif,
            t.details_frequences AS details_frequences,
            t.adresse
        FROM technique t
        INNER JOIN localisation l ON t.id_anfr = l.id_anfr
        LEFT JOIN ref_statut st ON t.statut_id = st.id
        WHERE t.id_anfr IN (:idAnfrs)
    """)
    suspend fun getTechniqueDetailsByIds(idAnfrs: List<String>): List<TechniqueEntity>

    @Query("""
        SELECT
            t.id_anfr,
            RTRIM(
                CASE WHEN (l.tech_mask & 1) != 0 THEN '2G, ' ELSE '' END ||
                CASE WHEN (l.tech_mask & 2) != 0 THEN '3G, ' ELSE '' END ||
                CASE WHEN (l.tech_mask & 4) != 0 THEN '4G, ' ELSE '' END ||
                CASE WHEN (l.tech_mask & 8) != 0 THEN '5G, ' ELSE '' END ||
                CASE WHEN (l.tech_mask & 16) != 0 THEN 'FH, ' ELSE '' END,
                ', '
            ) AS technologies,
            COALESCE(st.libelle, '') AS statut,
            t.date_implantation,
            t.date_service,
            t.date_modif,
            NULL AS details_frequences,
            t.adresse
        FROM technique t
        INNER JOIN localisation l ON t.id_anfr = l.id_anfr
        LEFT JOIN ref_statut st ON t.statut_id = st.id
        WHERE t.id_anfr IN (:idAnfrs)
    """)
    suspend fun getTechniqueSummariesByIds(idAnfrs: List<String>): List<TechniqueEntity>

    @Query("""
        SELECT
            s.id_anfr,
            s.id_support,
            COALESCE(n.libelle, CASE WHEN s.nat_id IS NULL THEN NULL ELSE 'Code Nature ' || CAST(s.nat_id AS TEXT) END) AS nature_support,
            COALESCE(p.libelle, 'Inconnu') AS proprietaire,
            s.hauteur,
            (
                SELECT GROUP_CONCAT(antenna_line, char(10))
                FROM (
                    SELECT DISTINCT
                        COALESCE(ta.libelle, 'Type inconnu (' || COALESCE(CAST(a.tae_id AS TEXT), '') || ')') ||
                        ' : ' || COALESCE(CAST(a.azimut AS TEXT), 'N/A') ||
                        '° (' || COALESCE(CAST(a.hauteur_bas AS TEXT), 'N/A') || 'm)' AS antenna_line
                    FROM antenne a
                    LEFT JOIN ref_type_antenne ta ON a.tae_id = ta.tae_id
                    WHERE a.id_anfr = s.id_anfr
                    AND a.id_support = s.id_support
                    AND a.is_fh = 0
                    ORDER BY a.azimut
                )
            ) AS azimuts_et_types
        FROM support s
        LEFT JOIN ref_nature n ON s.nat_id = n.nat_id
        LEFT JOIN ref_proprietaire p ON s.tpo_id = p.tpo_id
        WHERE s.id_anfr IN (:idAnfrs)
    """)
    suspend fun getPhysiqueDetailsByIds(idAnfrs: List<String>): List<PhysiqueEntity>

    @Query("""
        SELECT
            s.id_anfr,
            s.id_support,
            COALESCE(n.libelle, CASE WHEN s.nat_id IS NULL THEN NULL ELSE 'Code Nature ' || CAST(s.nat_id AS TEXT) END) AS nature_support,
            COALESCE(p.libelle, 'Inconnu') AS proprietaire,
            s.hauteur,
            NULL AS azimuts_et_types
        FROM support s
        LEFT JOIN ref_nature n ON s.nat_id = n.nat_id
        LEFT JOIN ref_proprietaire p ON s.tpo_id = p.tpo_id
        WHERE s.id_anfr IN (:idAnfrs)
    """)
    suspend fun getPhysiqueSummariesByIds(idAnfrs: List<String>): List<PhysiqueEntity>

    @Query("""
        SELECT
            s.id_anfr,
            s.id_support,
            'Antenne à faisceau orientable' AS type_fh,
            (
                SELECT GROUP_CONCAT(antenna_line, char(10))
                FROM (
                    SELECT DISTINCT
                        COALESCE(ta.libelle, 'Type inconnu (' || COALESCE(CAST(a.tae_id AS TEXT), '') || ')') ||
                        ' : ' || COALESCE(CAST(a.azimut AS TEXT), 'N/A') ||
                        '° (' || COALESCE(CAST(a.hauteur_bas AS TEXT), 'N/A') || 'm)' AS antenna_line
                    FROM antenne a
                    LEFT JOIN ref_type_antenne ta ON a.tae_id = ta.tae_id
                    WHERE a.id_anfr = s.id_anfr
                    AND a.id_support = s.id_support
                    AND a.is_fh = 1
                    ORDER BY a.azimut
                )
            ) AS azimuts_fh
        FROM support s
        WHERE s.id_anfr = :idAnfr
        AND EXISTS (
            SELECT 1 FROM antenne a
            WHERE a.id_anfr = s.id_anfr
            AND a.id_support = s.id_support
            AND a.is_fh = 1
        )
    """)
    suspend fun getFaisceauxDetails(idAnfr: String): List<FaisceauxEntity>

    @Query("SELECT AVG(latitude) AS centerLat, AVG(longitude) AS centerLon, COUNT(*) AS count FROM localisation WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon GROUP BY ROUND(latitude / 2.5), ROUND(longitude / 3.0)")
    suspend fun getL1Clusters(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<DbCluster>

    @Query("SELECT AVG(latitude) AS centerLat, AVG(longitude) AS centerLon, COUNT(*) AS count FROM localisation WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon GROUP BY ROUND(latitude / 1.0), ROUND(longitude / 1.2)")
    suspend fun getL2Clusters(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<DbCluster>

    @Query("SELECT AVG(latitude) AS centerLat, AVG(longitude) AS centerLon, COUNT(*) AS count FROM localisation WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon GROUP BY ROUND(latitude / 0.4), ROUND(longitude / 0.5)")
    suspend fun getL3Clusters(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<DbCluster>

    @Query("SELECT AVG(latitude) AS centerLat, AVG(longitude) AS centerLon, COUNT(*) AS count FROM localisation WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon GROUP BY ROUND(latitude / 0.15), ROUND(longitude / 0.2)")
    suspend fun getL4Clusters(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<DbCluster>

    @Query("SELECT AVG(latitude) AS centerLat, AVG(longitude) AS centerLon, COUNT(*) AS count FROM localisation WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon GROUP BY ROUND(latitude, 1), ROUND(longitude, 1)")
    suspend fun getL5Clusters(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<DbCluster>

    @Query("SELECT AVG(latitude) AS centerLat, AVG(longitude) AS centerLon, COUNT(*) AS count FROM localisation WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon GROUP BY ROUND(latitude / 0.05), ROUND(longitude / 0.06)")
    suspend fun getL6Clusters(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<DbCluster>

    @Query("SELECT AVG(latitude) AS centerLat, AVG(longitude) AS centerLon, COUNT(*) AS count FROM localisation WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon GROUP BY ROUND(latitude / 0.02), ROUND(longitude / 0.025)")
    suspend fun getL7Clusters(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<DbCluster>

    @Query("""
        SELECT
            l.id_anfr,
            COALESCE(o.libelle, 'Inconnu') AS operateur,
            l.latitude,
            l.longitude,
            l.azimuts,
            l.code_insee,
            l.azimuts_fh,
            l.tech_mask,
            l.band_mask
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        WHERE l.id_anfr LIKE '%' || :query || '%'
        OR l.id_anfr IN (SELECT id_anfr FROM support WHERE id_support LIKE '%' || :query || '%')
        LIMIT 50
    """)
    suspend fun searchAntennasById(query: String): List<LocalisationEntity>

    @Query("""
        SELECT DISTINCT
            l.id_anfr,
            COALESCE(o.libelle, 'Inconnu') AS operateur,
            l.latitude,
            l.longitude,
            l.azimuts,
            l.code_insee,
            l.azimuts_fh,
            l.tech_mask,
            l.band_mask
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        LEFT JOIN technique t ON l.id_anfr = t.id_anfr
        LEFT JOIN support s ON l.id_anfr = s.id_anfr
        LEFT JOIN ref_nature n ON s.nat_id = n.nat_id
        LEFT JOIN ref_proprietaire p ON s.tpo_id = p.tpo_id
        LEFT JOIN ref_commune c ON l.code_insee = c.code_insee
        WHERE l.id_anfr LIKE '%' || :query || '%'
           OR UPPER(COALESCE(o.libelle, '')) LIKE '%' || UPPER(:query) || '%'
           OR UPPER(COALESCE(l.code_insee, '')) LIKE '%' || UPPER(:query) || '%'
           OR UPPER(COALESCE(c.nom, '')) LIKE '%' || UPPER(:query) || '%'
           OR UPPER(COALESCE(t.adresse, '')) LIKE '%' || UPPER(:query) || '%'
           OR UPPER(COALESCE(s.id_support, '')) LIKE '%' || UPPER(:query) || '%'
           OR UPPER(COALESCE(n.libelle, '')) LIKE '%' || UPPER(:query) || '%'
           OR UPPER(COALESCE(p.libelle, '')) LIKE '%' || UPPER(:query) || '%'
           OR (UPPER(:query) LIKE '%2G%' AND (l.tech_mask & 1) != 0)
           OR (UPPER(:query) LIKE '%3G%' AND (l.tech_mask & 2) != 0)
           OR (UPPER(:query) LIKE '%4G%' AND (l.tech_mask & 4) != 0)
           OR (UPPER(:query) LIKE '%5G%' AND (l.tech_mask & 8) != 0)
           OR (UPPER(:query) LIKE '%FH%' AND (l.tech_mask & 16) != 0)
           OR (UPPER(:query) LIKE '%700%' AND (l.band_mask & 1040) != 0)
           OR (UPPER(:query) LIKE '%800%' AND (l.band_mask & 32) != 0)
           OR (UPPER(:query) LIKE '%900%' AND (l.band_mask & 69) != 0)
           OR (UPPER(:query) LIKE '%1800%' AND (l.band_mask & 130) != 0)
           OR (UPPER(:query) LIKE '%2100%' AND (l.band_mask & 2312) != 0)
           OR (UPPER(:query) LIKE '%2600%' AND (l.band_mask & 512) != 0)
           OR (UPPER(:query) LIKE '%3500%' AND (l.band_mask & 4096) != 0)
           OR EXISTS (
                SELECT 1
                FROM antenne a
                LEFT JOIN ref_type_antenne ta ON a.tae_id = ta.tae_id
                WHERE a.id_anfr = l.id_anfr
                AND UPPER(COALESCE(ta.libelle, '')) LIKE '%' || UPPER(:query) || '%'
                LIMIT 1
           )
           OR UPPER(COALESCE(t.details_frequences, '')) LIKE '%' || UPPER(:query) || '%'
        LIMIT :limit
    """)
    suspend fun searchAntennasByText(query: String, limit: Int): List<LocalisationEntity>

    @Query("""
        SELECT DISTINCT
            l.id_anfr,
            COALESCE(o.libelle, 'Inconnu') AS operateur,
            l.latitude,
            l.longitude,
            l.azimuts,
            l.code_insee,
            l.azimuts_fh,
            l.tech_mask,
            l.band_mask
        FROM localisation l
        INNER JOIN technique t ON l.id_anfr = t.id_anfr
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        WHERE UPPER(COALESCE(t.adresse, '')) LIKE '%' || UPPER(:query) || '%'
        LIMIT :limit
    """)
    suspend fun searchAntennasByAddress(query: String, limit: Int): List<LocalisationEntity>

    @Query("""
        SELECT
            l.id_anfr,
            COALESCE(o.libelle, 'Inconnu') AS operateur,
            l.latitude,
            l.longitude,
            l.azimuts,
            l.code_insee,
            l.azimuts_fh,
            l.tech_mask,
            l.band_mask
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        WHERE CAST(l.id_anfr AS INTEGER) = CAST(:exactId AS INTEGER)
        OR l.id_anfr IN (
            SELECT id_anfr FROM support WHERE CAST(id_support AS INTEGER) = CAST(:exactId AS INTEGER)
        )
    """)
    suspend fun getAntennasByExactId(exactId: String): List<LocalisationEntity>

    @Query("""
        SELECT COUNT(DISTINCT s.id_support)
        FROM support s
        INNER JOIN localisation l ON s.id_anfr = l.id_anfr
        INNER JOIN ref_operateur o ON l.operateur_id = o.id
        LEFT JOIN technique t ON l.id_anfr = t.id_anfr
        LEFT JOIN ref_statut st ON t.statut_id = st.id
        WHERE o.libelle LIKE '%' || :operatorName || '%'
        AND COALESCE(st.libelle, '') IN ('En service', 'Techniquement opérationnel', 'Techniquement operationnel')
    """)
    suspend fun getUniqueSupportCountByOperator(operatorName: String): Int

    @Query("""
        SELECT COUNT(DISTINCT s.id_support)
        FROM support s
        INNER JOIN localisation l ON s.id_anfr = l.id_anfr
        INNER JOIN ref_operateur o ON l.operateur_id = o.id
        LEFT JOIN technique t ON l.id_anfr = t.id_anfr
        LEFT JOIN ref_statut st ON t.statut_id = st.id
        WHERE o.libelle LIKE '%' || :operatorName || '%'
        AND (l.band_mask & 1008) != 0
        AND COALESCE(st.libelle, '') IN ('En service', 'Techniquement opérationnel', 'Techniquement operationnel')
    """)
    suspend fun get4GSupportCountByOperator(operatorName: String): Int

    @Query("""
        SELECT COUNT(DISTINCT s.id_support)
        FROM support s
        INNER JOIN localisation l ON s.id_anfr = l.id_anfr
        INNER JOIN ref_operateur o ON l.operateur_id = o.id
        LEFT JOIN technique t ON l.id_anfr = t.id_anfr
        LEFT JOIN ref_statut st ON t.statut_id = st.id
        WHERE o.libelle LIKE '%' || :operatorName || '%'
        AND (l.band_mask & 15360) != 0
        AND COALESCE(st.libelle, '') IN ('En service', 'Techniquement opérationnel', 'Techniquement operationnel')
    """)
    suspend fun get5GSupportCountByOperator(operatorName: String): Int

    @Query("""
        SELECT
            s.id_anfr,
            s.id_support,
            COALESCE(n.libelle, CASE WHEN s.nat_id IS NULL THEN NULL ELSE 'Code Nature ' || CAST(s.nat_id AS TEXT) END) AS nature_support,
            COALESCE(p.libelle, 'Inconnu') AS proprietaire,
            s.hauteur,
            (
                SELECT GROUP_CONCAT(antenna_line, char(10))
                FROM (
                    SELECT DISTINCT
                        COALESCE(ta.libelle, 'Type inconnu (' || COALESCE(CAST(a.tae_id AS TEXT), '') || ')') ||
                        ' : ' || COALESCE(CAST(a.azimut AS TEXT), 'N/A') ||
                        '° (' || COALESCE(CAST(a.hauteur_bas AS TEXT), 'N/A') || 'm)' AS antenna_line
                    FROM antenne a
                    LEFT JOIN ref_type_antenne ta ON a.tae_id = ta.tae_id
                    WHERE a.id_anfr = s.id_anfr
                    AND a.id_support = s.id_support
                    AND a.is_fh = 0
                    ORDER BY a.azimut
                )
            ) AS azimuts_et_types
        FROM support s
        LEFT JOIN ref_nature n ON s.nat_id = n.nat_id
        LEFT JOIN ref_proprietaire p ON s.tpo_id = p.tpo_id
        WHERE s.id_anfr = :idAnfr
    """)
    suspend fun getPhysiqueByAnfr(idAnfr: String): List<PhysiqueEntity>

    @Query("""
        SELECT
            t.id_anfr,
            RTRIM(
                CASE WHEN (l.tech_mask & 1) != 0 THEN '2G, ' ELSE '' END ||
                CASE WHEN (l.tech_mask & 2) != 0 THEN '3G, ' ELSE '' END ||
                CASE WHEN (l.tech_mask & 4) != 0 THEN '4G, ' ELSE '' END ||
                CASE WHEN (l.tech_mask & 8) != 0 THEN '5G, ' ELSE '' END ||
                CASE WHEN (l.tech_mask & 16) != 0 THEN 'FH, ' ELSE '' END,
                ', '
            ) AS technologies,
            COALESCE(st.libelle, '') AS statut,
            t.date_implantation,
            t.date_service,
            t.date_modif,
            t.details_frequences AS details_frequences,
            t.adresse
        FROM technique t
        INNER JOIN localisation l ON t.id_anfr = l.id_anfr
        LEFT JOIN ref_statut st ON t.statut_id = st.id
        WHERE t.id_anfr = :idAnfr
    """)
    suspend fun getTechniqueByAnfr(idAnfr: String): List<TechniqueEntity>
}
