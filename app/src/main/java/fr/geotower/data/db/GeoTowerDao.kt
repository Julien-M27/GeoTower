package fr.geotower.data.db

import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Query
import fr.geotower.data.models.DbCluster
import fr.geotower.data.models.FaisceauxEntity
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.TechniqueEntity

data class SupportRadioStatsRow(
    @ColumnInfo(name = "id_support") val idSupport: String,
    @ColumnInfo(name = "tech_mask") val techMask: Int,
    @ColumnInfo(name = "band_mask") val bandMask: Int,
    @ColumnInfo(name = "statut") val statut: String?,
    @ColumnInfo(name = "has_active") val hasActive: Int,
    @ColumnInfo(name = "details_frequences") val encodedDetailsFrequences: String?
)

data class RadioStatRow(
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "item_key") val itemKey: String,
    @ColumnInfo(name = "label") val label: String?,
    @ColumnInfo(name = "total_count") val totalCount: Int,
    @ColumnInfo(name = "active_count") val activeCount: Int
)

data class WeeklyRadioStatRow(
    @ColumnInfo(name = "week_key") val weekKey: String,
    @ColumnInfo(name = "week_start") val weekStart: String?,
    @ColumnInfo(name = "source_date") val sourceDate: String?,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "item_key") val itemKey: String,
    @ColumnInfo(name = "label") val label: String?,
    @ColumnInfo(name = "total_count") val totalCount: Int,
    @ColumnInfo(name = "active_count") val activeCount: Int
)

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
            l.band_mask,
            l.arcep_nidt,
            l.is_zb,
            COALESCE(st.libelle, '') AS statut,
            COALESCE(t.has_active, 0) AS has_active,
            CASE WHEN EXISTS (
                SELECT 1
                FROM support underground_support
                LEFT JOIN ref_nature underground_nature ON underground_support.nat_id = underground_nature.nat_id
                WHERE underground_support.id_anfr = l.id_anfr
                AND underground_nature.libelle = 'Intérieur sous-terrain'
            ) THEN 1 ELSE 0 END AS has_underground_support
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        LEFT JOIN technique t ON l.id_anfr = t.id_anfr
        LEFT JOIN ref_statut st ON t.statut_id = st.id
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
            l.band_mask,
            l.arcep_nidt,
            l.is_zb,
            COALESCE(st.libelle, '') AS statut,
            COALESCE(t.has_active, 0) AS has_active,
            CASE WHEN EXISTS (
                SELECT 1
                FROM support underground_support
                LEFT JOIN ref_nature underground_nature ON underground_support.nat_id = underground_nature.nat_id
                WHERE underground_support.id_anfr = l.id_anfr
                AND underground_nature.libelle = 'Intérieur sous-terrain'
            ) THEN 1 ELSE 0 END AS has_underground_support
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
            l.band_mask,
            l.arcep_nidt,
            l.is_zb,
            COALESCE(st.libelle, '') AS statut,
            COALESCE(t.has_active, 0) AS has_active,
            CASE WHEN EXISTS (
                SELECT 1
                FROM support underground_support
                LEFT JOIN ref_nature underground_nature ON underground_support.nat_id = underground_nature.nat_id
                WHERE underground_support.id_anfr = l.id_anfr
                AND underground_nature.libelle = 'Intérieur sous-terrain'
            ) THEN 1 ELSE 0 END AS has_underground_support
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        LEFT JOIN technique t ON l.id_anfr = t.id_anfr
        LEFT JOIN ref_statut st ON t.statut_id = st.id
        WHERE UPPER(COALESCE(o.libelle, '')) LIKE '%' || UPPER(:operatorName) || '%'
        AND (
            COALESCE(st.libelle, '') IN ('En service', 'Techniquement opÃ©rationnel', 'Techniquement operationnel')
            OR COALESCE(t.has_active, 0) = 1
        )
        ORDER BY (
            (l.latitude - :lat) * (l.latitude - :lat) +
            (CASE
                WHEN ABS(l.longitude - :lon) > 180 THEN 360 - ABS(l.longitude - :lon)
                ELSE ABS(l.longitude - :lon)
            END) *
            (CASE
                WHEN ABS(l.longitude - :lon) > 180 THEN 360 - ABS(l.longitude - :lon)
                ELSE ABS(l.longitude - :lon)
            END)
        ) ASC
        LIMIT :limit
    """)
    suspend fun getNearestActiveLocalisationsByOperator(
        operatorName: String,
        lat: Double,
        lon: Double,
        limit: Int
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
            l.band_mask,
            l.arcep_nidt,
            l.is_zb,
            NULL AS statut,
            0 AS has_active,
            CASE WHEN EXISTS (
                SELECT 1
                FROM support underground_support
                LEFT JOIN ref_nature underground_nature ON underground_support.nat_id = underground_nature.nat_id
                WHERE underground_support.id_anfr = l.id_anfr
                AND underground_nature.libelle = 'Intérieur sous-terrain'
            ) THEN 1 ELSE 0 END AS has_underground_support
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        ORDER BY (
            (l.latitude - :lat) * (l.latitude - :lat) +
            (CASE
                WHEN ABS(l.longitude - :lon) > 180 THEN 360 - ABS(l.longitude - :lon)
                ELSE ABS(l.longitude - :lon)
            END) *
            (CASE
                WHEN ABS(l.longitude - :lon) > 180 THEN 360 - ABS(l.longitude - :lon)
                ELSE ABS(l.longitude - :lon)
            END)
        ) ASC
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
            l.band_mask,
            l.arcep_nidt,
            l.is_zb,
            NULL AS statut,
            0 AS has_active,
            CASE WHEN EXISTS (
                SELECT 1
                FROM support underground_support
                LEFT JOIN ref_nature underground_nature ON underground_support.nat_id = underground_nature.nat_id
                WHERE underground_support.id_anfr = l.id_anfr
                AND underground_nature.libelle = 'Intérieur sous-terrain'
            ) THEN 1 ELSE 0 END AS has_underground_support
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        ORDER BY (
            (l.latitude - :lat) * (l.latitude - :lat) +
            (CASE
                WHEN ABS(l.longitude - :lon) > 180 THEN 360 - ABS(l.longitude - :lon)
                ELSE ABS(l.longitude - :lon)
            END) *
            (CASE
                WHEN ABS(l.longitude - :lon) > 180 THEN 360 - ABS(l.longitude - :lon)
                ELSE ABS(l.longitude - :lon)
            END)
        ) ASC
        LIMIT :limit
    """)
    suspend fun getNearest(
        lat: Double,
        lon: Double,
        limit: Int
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
            l.band_mask,
            l.arcep_nidt,
            l.is_zb,
            NULL AS statut,
            0 AS has_active,
            CASE WHEN EXISTS (
                SELECT 1
                FROM support underground_support
                LEFT JOIN ref_nature underground_nature ON underground_support.nat_id = underground_nature.nat_id
                WHERE underground_support.id_anfr = l.id_anfr
                AND underground_nature.libelle = 'IntÃ©rieur sous-terrain'
            ) THEN 1 ELSE 0 END AS has_underground_support
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        WHERE l.is_zb = 1
        ORDER BY (
            (l.latitude - :lat) * (l.latitude - :lat) +
            (CASE
                WHEN ABS(l.longitude - :lon) > 180 THEN 360 - ABS(l.longitude - :lon)
                ELSE ABS(l.longitude - :lon)
            END) *
            (CASE
                WHEN ABS(l.longitude - :lon) > 180 THEN 360 - ABS(l.longitude - :lon)
                ELSE ABS(l.longitude - :lon)
            END)
        ) ASC
        LIMIT :limit
    """)
    suspend fun getNearestZb(
        lat: Double,
        lon: Double,
        limit: Int
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
            l.band_mask,
            l.arcep_nidt,
            l.is_zb,
            NULL AS statut,
            0 AS has_active,
            CASE WHEN EXISTS (
                SELECT 1
                FROM support underground_support
                LEFT JOIN ref_nature underground_nature ON underground_support.nat_id = underground_nature.nat_id
                WHERE underground_support.id_anfr = l.id_anfr
                AND underground_nature.libelle = 'Intérieur sous-terrain'
            ) THEN 1 ELSE 0 END AS has_underground_support
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
            COALESCE(e.libelle, CASE WHEN t.adm_id IS NULL THEN NULL ELSE 'Code Exploitant ' || CAST(t.adm_id AS TEXT) END) AS exploitant,
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
        LEFT JOIN technique t ON s.id_anfr = t.id_anfr
        LEFT JOIN ref_nature n ON s.nat_id = n.nat_id
        LEFT JOIN ref_proprietaire p ON s.tpo_id = p.tpo_id
        LEFT JOIN ref_exploitant e ON t.adm_id = e.adm_id
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
            COALESCE(e.libelle, CASE WHEN t.adm_id IS NULL THEN NULL ELSE 'Code Exploitant ' || CAST(t.adm_id AS TEXT) END) AS exploitant,
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
        LEFT JOIN technique t ON s.id_anfr = t.id_anfr
        LEFT JOIN ref_nature n ON s.nat_id = n.nat_id
        LEFT JOIN ref_proprietaire p ON s.tpo_id = p.tpo_id
        LEFT JOIN ref_exploitant e ON t.adm_id = e.adm_id
        WHERE s.id_anfr IN (:idAnfrs)
    """)
    suspend fun getPhysiqueDetailsByIds(idAnfrs: List<String>): List<PhysiqueEntity>

    @Query("""
        SELECT
            s.id_anfr,
            s.id_support,
            COALESCE(n.libelle, CASE WHEN s.nat_id IS NULL THEN NULL ELSE 'Code Nature ' || CAST(s.nat_id AS TEXT) END) AS nature_support,
            COALESCE(p.libelle, 'Inconnu') AS proprietaire,
            COALESCE(e.libelle, CASE WHEN t.adm_id IS NULL THEN NULL ELSE 'Code Exploitant ' || CAST(t.adm_id AS TEXT) END) AS exploitant,
            s.hauteur,
            NULL AS azimuts_et_types
        FROM support s
        LEFT JOIN technique t ON s.id_anfr = t.id_anfr
        LEFT JOIN ref_nature n ON s.nat_id = n.nat_id
        LEFT JOIN ref_proprietaire p ON s.tpo_id = p.tpo_id
        LEFT JOIN ref_exploitant e ON t.adm_id = e.adm_id
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

    @Query("""
        SELECT AVG(l.latitude) AS centerLat, AVG(l.longitude) AS centerLon, COUNT(*) AS count, GROUP_CONCAT(DISTINCT COALESCE(o.libelle, 'Inconnu')) AS operators
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        WHERE l.latitude BETWEEN :minLat AND :maxLat
        AND l.longitude BETWEEN :minLon AND :maxLon
        AND (:showOnlyZbSites = 0 OR l.is_zb = 1)
        AND (:hideUndergroundSites = 0 OR NOT EXISTS (
            SELECT 1
            FROM support underground_support
            LEFT JOIN ref_nature underground_nature ON underground_support.nat_id = underground_nature.nat_id
            WHERE underground_support.id_anfr = l.id_anfr
            AND underground_nature.libelle = 'Intérieur sous-terrain'
        ))
        GROUP BY ROUND(l.latitude / 2.5), ROUND(l.longitude / 3.0)
    """)
    suspend fun getL1Clusters(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, hideUndergroundSites: Boolean, showOnlyZbSites: Boolean): List<DbCluster>

    @Query("""
        SELECT AVG(l.latitude) AS centerLat, AVG(l.longitude) AS centerLon, COUNT(*) AS count, GROUP_CONCAT(DISTINCT COALESCE(o.libelle, 'Inconnu')) AS operators
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        WHERE l.latitude BETWEEN :minLat AND :maxLat
        AND l.longitude BETWEEN :minLon AND :maxLon
        AND (:showOnlyZbSites = 0 OR l.is_zb = 1)
        AND (:hideUndergroundSites = 0 OR NOT EXISTS (
            SELECT 1
            FROM support underground_support
            LEFT JOIN ref_nature underground_nature ON underground_support.nat_id = underground_nature.nat_id
            WHERE underground_support.id_anfr = l.id_anfr
            AND underground_nature.libelle = 'Intérieur sous-terrain'
        ))
        GROUP BY ROUND(l.latitude / 1.0), ROUND(l.longitude / 1.2)
    """)
    suspend fun getL2Clusters(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, hideUndergroundSites: Boolean, showOnlyZbSites: Boolean): List<DbCluster>

    @Query("""
        SELECT AVG(l.latitude) AS centerLat, AVG(l.longitude) AS centerLon, COUNT(*) AS count, GROUP_CONCAT(DISTINCT COALESCE(o.libelle, 'Inconnu')) AS operators
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        WHERE l.latitude BETWEEN :minLat AND :maxLat
        AND l.longitude BETWEEN :minLon AND :maxLon
        AND (:showOnlyZbSites = 0 OR l.is_zb = 1)
        AND (:hideUndergroundSites = 0 OR NOT EXISTS (
            SELECT 1
            FROM support underground_support
            LEFT JOIN ref_nature underground_nature ON underground_support.nat_id = underground_nature.nat_id
            WHERE underground_support.id_anfr = l.id_anfr
            AND underground_nature.libelle = 'Intérieur sous-terrain'
        ))
        GROUP BY ROUND(l.latitude / 0.4), ROUND(l.longitude / 0.5)
    """)
    suspend fun getL3Clusters(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, hideUndergroundSites: Boolean, showOnlyZbSites: Boolean): List<DbCluster>

    @Query("""
        SELECT AVG(l.latitude) AS centerLat, AVG(l.longitude) AS centerLon, COUNT(*) AS count, GROUP_CONCAT(DISTINCT COALESCE(o.libelle, 'Inconnu')) AS operators
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        WHERE l.latitude BETWEEN :minLat AND :maxLat
        AND l.longitude BETWEEN :minLon AND :maxLon
        AND (:showOnlyZbSites = 0 OR l.is_zb = 1)
        AND (:hideUndergroundSites = 0 OR NOT EXISTS (
            SELECT 1
            FROM support underground_support
            LEFT JOIN ref_nature underground_nature ON underground_support.nat_id = underground_nature.nat_id
            WHERE underground_support.id_anfr = l.id_anfr
            AND underground_nature.libelle = 'Intérieur sous-terrain'
        ))
        GROUP BY ROUND(l.latitude / 0.15), ROUND(l.longitude / 0.2)
    """)
    suspend fun getL4Clusters(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, hideUndergroundSites: Boolean, showOnlyZbSites: Boolean): List<DbCluster>

    @Query("""
        SELECT AVG(l.latitude) AS centerLat, AVG(l.longitude) AS centerLon, COUNT(*) AS count, GROUP_CONCAT(DISTINCT COALESCE(o.libelle, 'Inconnu')) AS operators
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        WHERE l.latitude BETWEEN :minLat AND :maxLat
        AND l.longitude BETWEEN :minLon AND :maxLon
        AND (:showOnlyZbSites = 0 OR l.is_zb = 1)
        AND (:hideUndergroundSites = 0 OR NOT EXISTS (
            SELECT 1
            FROM support underground_support
            LEFT JOIN ref_nature underground_nature ON underground_support.nat_id = underground_nature.nat_id
            WHERE underground_support.id_anfr = l.id_anfr
            AND underground_nature.libelle = 'Intérieur sous-terrain'
        ))
        GROUP BY ROUND(l.latitude, 1), ROUND(l.longitude, 1)
    """)
    suspend fun getL5Clusters(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, hideUndergroundSites: Boolean, showOnlyZbSites: Boolean): List<DbCluster>

    @Query("""
        SELECT AVG(l.latitude) AS centerLat, AVG(l.longitude) AS centerLon, COUNT(*) AS count, GROUP_CONCAT(DISTINCT COALESCE(o.libelle, 'Inconnu')) AS operators
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        WHERE l.latitude BETWEEN :minLat AND :maxLat
        AND l.longitude BETWEEN :minLon AND :maxLon
        AND (:showOnlyZbSites = 0 OR l.is_zb = 1)
        AND (:hideUndergroundSites = 0 OR NOT EXISTS (
            SELECT 1
            FROM support underground_support
            LEFT JOIN ref_nature underground_nature ON underground_support.nat_id = underground_nature.nat_id
            WHERE underground_support.id_anfr = l.id_anfr
            AND underground_nature.libelle = 'Intérieur sous-terrain'
        ))
        GROUP BY ROUND(l.latitude / 0.05), ROUND(l.longitude / 0.06)
    """)
    suspend fun getL6Clusters(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, hideUndergroundSites: Boolean, showOnlyZbSites: Boolean): List<DbCluster>

    @Query("""
        SELECT AVG(l.latitude) AS centerLat, AVG(l.longitude) AS centerLon, COUNT(*) AS count, GROUP_CONCAT(DISTINCT COALESCE(o.libelle, 'Inconnu')) AS operators
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        WHERE l.latitude BETWEEN :minLat AND :maxLat
        AND l.longitude BETWEEN :minLon AND :maxLon
        AND (:showOnlyZbSites = 0 OR l.is_zb = 1)
        AND (:hideUndergroundSites = 0 OR NOT EXISTS (
            SELECT 1
            FROM support underground_support
            LEFT JOIN ref_nature underground_nature ON underground_support.nat_id = underground_nature.nat_id
            WHERE underground_support.id_anfr = l.id_anfr
            AND underground_nature.libelle = 'Intérieur sous-terrain'
        ))
        GROUP BY ROUND(l.latitude / 0.02), ROUND(l.longitude / 0.025)
    """)
    suspend fun getL7Clusters(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, hideUndergroundSites: Boolean, showOnlyZbSites: Boolean): List<DbCluster>

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
            l.band_mask,
            l.arcep_nidt,
            l.is_zb,
            COALESCE(st.libelle, '') AS statut,
            COALESCE(t.has_active, 0) AS has_active,
            CASE WHEN EXISTS (
                SELECT 1
                FROM support underground_support
                LEFT JOIN ref_nature underground_nature ON underground_support.nat_id = underground_nature.nat_id
                WHERE underground_support.id_anfr = l.id_anfr
                AND underground_nature.libelle = 'Intérieur sous-terrain'
            ) THEN 1 ELSE 0 END AS has_underground_support
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        LEFT JOIN technique t ON l.id_anfr = t.id_anfr
        LEFT JOIN ref_statut st ON t.statut_id = st.id
        WHERE l.id_anfr LIKE '%' || :query || '%'
        OR UPPER(COALESCE(l.arcep_nidt, '')) LIKE '%' || UPPER(:query) || '%'
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
            l.band_mask,
            l.arcep_nidt,
            l.is_zb,
            COALESCE(st.libelle, '') AS statut,
            COALESCE(t.has_active, 0) AS has_active,
            CASE WHEN EXISTS (
                SELECT 1
                FROM support underground_support
                LEFT JOIN ref_nature underground_nature ON underground_support.nat_id = underground_nature.nat_id
                WHERE underground_support.id_anfr = l.id_anfr
                AND underground_nature.libelle = 'Intérieur sous-terrain'
            ) THEN 1 ELSE 0 END AS has_underground_support
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        LEFT JOIN technique t ON l.id_anfr = t.id_anfr
        LEFT JOIN ref_statut st ON t.statut_id = st.id
        LEFT JOIN support s ON l.id_anfr = s.id_anfr
        LEFT JOIN ref_nature n ON s.nat_id = n.nat_id
        LEFT JOIN ref_proprietaire p ON s.tpo_id = p.tpo_id
        LEFT JOIN ref_commune c ON l.code_insee = c.code_insee
        WHERE l.id_anfr LIKE '%' || :query || '%'
           OR UPPER(COALESCE(o.libelle, '')) LIKE '%' || UPPER(:query) || '%'
           OR UPPER(COALESCE(l.arcep_nidt, '')) LIKE '%' || UPPER(:query) || '%'
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
            l.band_mask,
            l.arcep_nidt,
            l.is_zb,
            COALESCE(st.libelle, '') AS statut,
            COALESCE(t.has_active, 0) AS has_active,
            CASE WHEN EXISTS (
                SELECT 1
                FROM support underground_support
                LEFT JOIN ref_nature underground_nature ON underground_support.nat_id = underground_nature.nat_id
                WHERE underground_support.id_anfr = l.id_anfr
                AND underground_nature.libelle = 'Intérieur sous-terrain'
            ) THEN 1 ELSE 0 END AS has_underground_support
        FROM localisation l
        INNER JOIN technique t ON l.id_anfr = t.id_anfr
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        LEFT JOIN ref_statut st ON t.statut_id = st.id
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
            l.band_mask,
            l.arcep_nidt,
            l.is_zb,
            COALESCE(st.libelle, '') AS statut,
            COALESCE(t.has_active, 0) AS has_active,
            CASE WHEN EXISTS (
                SELECT 1
                FROM support underground_support
                LEFT JOIN ref_nature underground_nature ON underground_support.nat_id = underground_nature.nat_id
                WHERE underground_support.id_anfr = l.id_anfr
                AND underground_nature.libelle = 'Intérieur sous-terrain'
            ) THEN 1 ELSE 0 END AS has_underground_support
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        LEFT JOIN technique t ON l.id_anfr = t.id_anfr
        LEFT JOIN ref_statut st ON t.statut_id = st.id
        WHERE l.id_anfr = :exactId
        OR (
            :exactId != ''
            AND
            :exactId NOT GLOB '*[^0-9]*'
            AND l.id_anfr = printf('%010d', CAST(:exactId AS INTEGER))
        )
        OR l.id_anfr IN (
            SELECT id_anfr
            FROM support
            WHERE id_support = :exactId
            OR (
                :exactId != ''
                AND
                :exactId NOT GLOB '*[^0-9]*'
                AND CAST(id_support AS INTEGER) = CAST(:exactId AS INTEGER)
            )
        )
    """)
    suspend fun getAntennasByExactId(exactId: String): List<LocalisationEntity>

    @Query("""
        SELECT COUNT(DISTINCT s.id_support)
        FROM support s
        INNER JOIN localisation l ON s.id_anfr = l.id_anfr
        INNER JOIN ref_operateur o ON l.operateur_id = o.id
        WHERE UPPER(TRIM(o.libelle)) IN (:operatorNames)
    """)
    suspend fun getUniqueSupportCountByOperator(operatorNames: List<String>): Int

    @Query("""
        SELECT COUNT(DISTINCT s.id_support)
        FROM support s
        INNER JOIN localisation l ON s.id_anfr = l.id_anfr
        INNER JOIN ref_operateur o ON l.operateur_id = o.id
        LEFT JOIN technique t ON l.id_anfr = t.id_anfr
        LEFT JOIN ref_statut st ON t.statut_id = st.id
        WHERE UPPER(TRIM(o.libelle)) IN (:operatorNames)
        AND (
            COALESCE(t.has_active, 0) = 1
            OR COALESCE(st.libelle, '') IN ('En service', 'Techniquement opÃ©rationnel', 'Techniquement operationnel')
        )
    """)
    suspend fun getActiveUniqueSupportCountByOperator(operatorNames: List<String>): Int

    @Query("""
        SELECT COUNT(DISTINCT s.id_support)
        FROM support s
        INNER JOIN localisation l ON s.id_anfr = l.id_anfr
        INNER JOIN ref_operateur o ON l.operateur_id = o.id
        WHERE UPPER(TRIM(o.libelle)) IN (:operatorNames)
        AND (l.tech_mask & 1) != 0
    """)
    suspend fun get2GSupportCountByOperator(operatorNames: List<String>): Int

    @Query("""
        SELECT COUNT(DISTINCT s.id_support)
        FROM support s
        INNER JOIN localisation l ON s.id_anfr = l.id_anfr
        INNER JOIN ref_operateur o ON l.operateur_id = o.id
        WHERE UPPER(TRIM(o.libelle)) IN (:operatorNames)
        AND (l.tech_mask & 2) != 0
    """)
    suspend fun get3GSupportCountByOperator(operatorNames: List<String>): Int

    @Query("""
        SELECT COUNT(DISTINCT s.id_support)
        FROM support s
        INNER JOIN localisation l ON s.id_anfr = l.id_anfr
        INNER JOIN ref_operateur o ON l.operateur_id = o.id
        WHERE UPPER(TRIM(o.libelle)) IN (:operatorNames)
        AND (l.tech_mask & 4) != 0
    """)
    suspend fun get4GSupportCountByOperator(operatorNames: List<String>): Int

    @Query("""
        SELECT COUNT(DISTINCT s.id_support)
        FROM support s
        INNER JOIN localisation l ON s.id_anfr = l.id_anfr
        INNER JOIN ref_operateur o ON l.operateur_id = o.id
        WHERE UPPER(TRIM(o.libelle)) IN (:operatorNames)
        AND (l.tech_mask & 8) != 0
    """)
    suspend fun get5GSupportCountByOperator(operatorNames: List<String>): Int

    @Query("""
        SELECT COUNT(DISTINCT s.id_support)
        FROM support s
        INNER JOIN localisation l ON s.id_anfr = l.id_anfr
        INNER JOIN ref_operateur o ON l.operateur_id = o.id
        WHERE UPPER(TRIM(o.libelle)) IN (:operatorNames)
        AND (l.band_mask & :bandMask) != 0
    """)
    suspend fun getSupportCountByOperatorAndBand(operatorNames: List<String>, bandMask: Int): Int

    @Query("""
        SELECT DISTINCT
            s.id_support,
            l.tech_mask,
            l.band_mask,
            COALESCE(st.libelle, '') AS statut,
            COALESCE(t.has_active, 0) AS has_active,
            t.details_frequences AS details_frequences
        FROM support s
        INNER JOIN localisation l ON s.id_anfr = l.id_anfr
        INNER JOIN ref_operateur o ON l.operateur_id = o.id
        LEFT JOIN technique t ON l.id_anfr = t.id_anfr
        LEFT JOIN ref_statut st ON t.statut_id = st.id
        WHERE UPPER(TRIM(o.libelle)) IN (:operatorNames)
    """)
    suspend fun getSupportRadioStatsRowsByOperator(operatorNames: List<String>): List<SupportRadioStatsRow>

    @Query("""
        SELECT
            category,
            item_key,
            COALESCE(MAX(label), '') AS label,
            CAST(SUM(total_count) AS INTEGER) AS total_count,
            CAST(SUM(active_count) AS INTEGER) AS active_count
        FROM radio_stat_current
        WHERE operator_name IN (:operatorNames)
        GROUP BY category, item_key
    """)
    suspend fun getCurrentRadioStatsByOperator(operatorNames: List<String>): List<RadioStatRow>

    @Query("""
        SELECT
            week_key,
            week_start,
            source_date,
            category,
            item_key,
            COALESCE(MAX(label), '') AS label,
            CAST(SUM(total_count) AS INTEGER) AS total_count,
            CAST(SUM(active_count) AS INTEGER) AS active_count
        FROM radio_stat_weekly
        WHERE operator_name IN (:operatorNames)
        GROUP BY week_key, week_start, source_date, category, item_key
        ORDER BY COALESCE(week_start, source_date, week_key) ASC, week_key ASC
    """)
    suspend fun getWeeklyRadioStatsByOperator(operatorNames: List<String>): List<WeeklyRadioStatRow>

    @Query("""
        SELECT
            s.id_anfr,
            s.id_support,
            COALESCE(n.libelle, CASE WHEN s.nat_id IS NULL THEN NULL ELSE 'Code Nature ' || CAST(s.nat_id AS TEXT) END) AS nature_support,
            COALESCE(p.libelle, 'Inconnu') AS proprietaire,
            COALESCE(e.libelle, CASE WHEN t.adm_id IS NULL THEN NULL ELSE 'Code Exploitant ' || CAST(t.adm_id AS TEXT) END) AS exploitant,
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
        LEFT JOIN technique t ON s.id_anfr = t.id_anfr
        LEFT JOIN ref_nature n ON s.nat_id = n.nat_id
        LEFT JOIN ref_proprietaire p ON s.tpo_id = p.tpo_id
        LEFT JOIN ref_exploitant e ON t.adm_id = e.adm_id
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
