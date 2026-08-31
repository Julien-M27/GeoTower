package fr.geotower.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.geotower.data.AdminAreaExtent
import fr.geotower.data.AnfrRepository
import fr.geotower.data.RadioRepository
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.db.CommuneNameRow
import fr.geotower.data.trip.TRIP_STEP_SITE_RADIUS_METERS
import fr.geotower.data.trip.TripStepAntennaRef
import fr.geotower.data.trip.TripStepSupport
import fr.geotower.data.trip.groupTripStepSupports
import fr.geotower.data.trip.tripStepSearchBox
import fr.geotower.data.api.NominatimApi
import fr.geotower.data.api.AdministrativeAreaReferenceApi
import fr.geotower.data.api.CommuneReference
import fr.geotower.data.api.CommuneReferenceApi
import fr.geotower.data.api.SignalQuestClient
import fr.geotower.data.api.SignalQuestOperators
import fr.geotower.data.api.SqCoveragePointData
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.RadioMapMarker
import fr.geotower.data.models.TechniqueEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import fr.geotower.data.models.SiteHsEntity
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import fr.geotower.utils.DepartmentCodes
import fr.geotower.utils.FrequencyFilterSelection
import fr.geotower.utils.FrenchAdminAreas
import fr.geotower.utils.OperatorColors

/** Contour d'une zone administrative, associé au code qui l'a demandé pour éviter tout décalage. */
data class AdminAreaOutline(
    val areaCode: String,
    val polygons: List<List<GeoPoint>>
)

/** Zone administrative retenue par la recherche : codes INSEE de tri, et contour pour les regroupements. */
private data class AdminAreaFilter(
    val departmentCodes: Set<String>,
    val polygons: List<List<GeoPoint>>?
)

data class SignalQuestCoveragePoint(
    val id: String?,
    val latitude: Double,
    val longitude: Double,
    val operatorKey: String,
    val operatorLabel: String,
    val technology: String?,
    val networkType: String?,
    val signalStrength: Float?,
    val rsrq: Float?,
    val snr: Float?,
    val mcc: Int?,
    val mnc: Int?,
    val enb: String?,
    val gnb: String?,
    val cellId: String?,
    val pci: Int?,
    val timestamp: String?
)

private data class SignalQuestCoverageBounds(
    val north: Double,
    val east: Double,
    val south: Double,
    val west: Double
)

/**
 * Un support physique distinct présent à un point de la carte.
 * Sert à désambiguïser quand plusieurs supports (id_support différents) se retrouvent
 * exactement aux mêmes coordonnées GPS (souvent des erreurs de saisie ANFR).
 */
data class SupportChoice(
    val supportId: String,
    val representative: LocalisationEntity,
    val operatorKeys: List<String>,
    val nature: String?
)

/** Un support autour d'une étape, enrichi de ses stations et de son adresse. */
data class TripStepSupportDetails(
    val support: TripStepSupport,
    val antennas: List<LocalisationEntity>,
    val address: String
)

class MapViewModel(
    private val repository: AnfrRepository,
    private val radioRepository: RadioRepository
) : ViewModel() {

    private val _antennas = MutableStateFlow<List<LocalisationEntity>>(emptyList())
    val antennas = _antennas.asStateFlow()

    private val _radioMarkers = MutableStateFlow<List<RadioMapMarker>>(emptyList())
    val radioMarkers = _radioMarkers.asStateFlow()

    private val _signalQuestCoveragePoints = MutableStateFlow<List<SignalQuestCoveragePoint>>(emptyList())
    val signalQuestCoveragePoints = _signalQuestCoveragePoints.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // Date de mise en service la plus ancienne, pour borner le slider temporel de la carte.
    private val _oldestServiceDate = MutableStateFlow<String?>(null)
    val oldestServiceDate = _oldestServiceDate.asStateFlow()

    private val _cityStatsTechniques = MutableStateFlow<Map<String, TechniqueEntity>>(emptyMap())
    val cityStatsTechniques = _cityStatsTechniques.asStateFlow()

    private val _isCityStatsTechniquesLoading = MutableStateFlow(false)
    val isCityStatsTechniquesLoading = _isCityStatsTechniquesLoading.asStateFlow()

    private val _cityStatsReference = MutableStateFlow<CommuneReference?>(null)
    val cityStatsReference = _cityStatsReference.asStateFlow()

    private val _adminAreaOutline = MutableStateFlow<AdminAreaOutline?>(null)
    val adminAreaOutline = _adminAreaOutline.asStateFlow()

    private val _adminAreaStatsAntennas = MutableStateFlow<List<LocalisationEntity>>(emptyList())
    val adminAreaStatsAntennas = _adminAreaStatsAntennas.asStateFlow()

    private val _isAdminAreaStatsLoading = MutableStateFlow(false)
    val isAdminAreaStatsLoading = _isAdminAreaStatsLoading.asStateFlow()

    private var searchJob: Job? = null
    private var adminAreaOutlineJob: Job? = null
    private var adminAreaStatsJob: Job? = null
    private var loadedAdminAreaStatsKey: String? = null
    private var adminAreaFilter: AdminAreaFilter? = null
    private var signalQuestCoverageJob: Job? = null
    private var lastSignalQuestCoverageRequestKey: String? = null
    private var cityStatsTechniquesJob: Job? = null
    private var cityStatsReferenceJob: Job? = null
    private var cityStatsReferenceCode: String? = null
    private var loadedCityStatsTechniqueIds: Set<String> = emptySet()
    private val mapAzimuthTechniqueCache = mutableMapOf<String, TechniqueEntity>()
    private var cityPolygons: List<List<GeoPoint>>? = null
    private var isCityLocked = false

    fun loadAntennasForCity(latNorth: Double, lonEast: Double, latSouth: Double, lonWest: Double, polygons: List<List<GeoPoint>>) {
        searchJob?.cancel()
        cityPolygons = polygons
        isCityLocked = true // ✅ ON VERROUILLE LE CHARGEMENT AUTOMATIQUE

        searchJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                val frequencyFilter = FrequencyFilterSelection.fromMapConfig()
                val apiMarkers = repository.getAntennasInBox(
                    latNorth,
                    lonEast,
                    latSouth,
                    lonWest,
                    detailBackedBandMask = frequencyFilter.detailBackedBandMaskForEnrichment()
                )
                val filteredMarkers = apiMarkers.filter { isPointInPolygon(it.latitude, it.longitude, polygons) }

                _antennas.value = filteredMarkers
                _radioMarkers.value = loadRadioMarkers(13.0, latNorth, lonEast, latSouth, lonWest)
                    .filter { isPointInPolygon(it.latitude, it.longitude, polygons) }
                AppLogger.d(TAG, "City map markers filtered=${filteredMarkers.size} total=${apiMarkers.size}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "City map request failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Recherche par département ou région.
     *
     * Contrairement au verrou de commune, ce filtre **n'immobilise pas la carte** : une région
     * compte jusqu'à 10 000 emplacements, bien au-delà de [fr.geotower.utils.PowerProfile.mapMarkerCap],
     * et un chargement figé en aurait laissé un tiers invisible pour toujours. Il s'applique donc au
     * chargement normal, qui reste borné par ce qu'on regarde : regroupements quand on est loin,
     * sites détaillés quand on zoome.
     */
    fun setAdminAreaFilter(departmentCodes: List<String>, polygons: List<List<GeoPoint>>?) {
        adminAreaFilter = AdminAreaFilter(
            departmentCodes = departmentCodes.toSet(),
            polygons = polygons?.takeIf { it.isNotEmpty() }
        )
    }

    fun clearAdminAreaFilter() {
        if (adminAreaFilter == null && loadedAdminAreaStatsKey == null) return
        adminAreaFilter = null
        adminAreaStatsJob?.cancel()
        loadedAdminAreaStatsKey = null
        _adminAreaStatsAntennas.value = emptyList()
        _isAdminAreaStatsLoading.value = false
    }

    /**
     * Charge toute la zone **hors carte**, uniquement pour les compteurs de la fiche de zone.
     *
     * La carte, elle, ne voit que ce qui est à l'écran : à l'échelle d'un département elle affiche
     * des regroupements, dont on ne peut rien compter de fiable.
     */
    fun loadAdminAreaStats(
        areaKey: String,
        departmentCodes: List<String>,
        fallbackExtent: AdminAreaExtent? = null
    ) {
        if (loadedAdminAreaStatsKey == areaKey) return

        loadedAdminAreaStatsKey = areaKey
        adminAreaStatsJob?.cancel()
        // Dispatchers.IO explicite : l'enrichissement des bandes 5G reparse les détails de fréquence
        // ligne par ligne, et une région en compte jusqu'à 18 000 — hors de question sur le thread UI.
        adminAreaStatsJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isAdminAreaStatsLoading.value = true
            try {
                _adminAreaStatsAntennas.value = repository.getAntennasInAdminArea(
                    departmentCodes = departmentCodes,
                    fallbackExtent = fallbackExtent
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "Admin area stats request failed", e)
                _adminAreaStatsAntennas.value = emptyList()
            } finally {
                _isAdminAreaStatsLoading.value = false
            }
        }
    }

    /** Tri exact, sur le code INSEE : c'est la même définition de département que partout ailleurs. */
    private fun filterDetailedToAdminArea(antennas: List<LocalisationEntity>): List<LocalisationEntity> {
        val codes = adminAreaFilter?.departmentCodes ?: return antennas
        return antennas.filter { DepartmentCodes.fromInsee(it.codeInsee) in codes }
    }

    /**
     * Les regroupements sont pré-agrégés en base et ne portent pas de code commune : on les trie sur
     * le contour de la zone, à défaut. Une cellule de regroupement couvre quelques kilomètres, donc
     * un compte peut déborder de la frontière — sans contour (hors réseau), on n'en trie aucun.
     */
    private fun filterClustersToAdminArea(clusters: List<LocalisationEntity>): List<LocalisationEntity> {
        val polygons = adminAreaFilter?.polygons ?: return clusters
        return clusters.filter { isPointInPolygon(it.latitude, it.longitude, polygons) }
    }

    fun loadAntennasInBox(zoom: Double, latNorth: Double, lonEast: Double, latSouth: Double, lonWest: Double) {
        if (isCityLocked) return // ✅ MAGIQUE : Si on regarde une ville, on ne recalcule RIEN quand on bouge la carte !

        searchJob?.cancel()

        searchJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isLoading.value = true
            try {
                // ✅ 2. ON EMPÊCHE LE CLUSTERING GLOBAL SI UNE VILLE EST RECHERCHÉE
                val showSitesInService = AppConfig.showSitesInService.value
                val showSitesOutOfService = AppConfig.showSitesOutOfService.value
                val showProjectSites = AppConfig.showProjectSites.value

                if (!showSitesInService && !showSitesOutOfService && !showProjectSites) {
                    _antennas.value = emptyList()
                    _radioMarkers.value = loadRadioMarkers(zoom, latNorth, lonEast, latSouth, lonWest)
                    return@launch
                }

                // « En service » décoché : ce qui reste (pannes déclarées, sites en projet) tient
                // dans des sous-ensembles bornés → on les charge directement au lieu de ramener
                // toute la zone pour la filtrer ensuite.
                val loadsStatusSubsetsOnly = !showSitesInService
                val hsOnlyIds = if (loadsStatusSubsetsOnly && showSitesOutOfService) {
                    sitesHsAnfrIds(_sitesHs.value)
                } else {
                    emptyList()
                }

                val frequencyFilter = FrequencyFilterSelection.fromMapConfig()
                // Le clustering est pré-agrégé en base : il ne sait pas distinguer les statuts,
                // donc tout décochage force le chargement détaillé.
                val hasSiteDisplayFilter = !showSitesInService || !showSitesOutOfService || !showProjectSites
                val hasFrequencyFilter = !frequencyFilter.isFullyEnabled

                // Les clusters sont pré-agrégés et ne peuvent pas soustraire les masquages locaux
                // opérateur par opérateur. Dès qu'il existe une préférence de masquage, on charge
                // les lignes détaillées afin que la carte reste cohérente avec tous les autres écrans.
                if (zoom < 13.0 && cityPolygons == null && !hasSiteDisplayFilter && !hasFrequencyFilter &&
                    !AppConfig.timeSliderActive.value && !repository.hasHiddenSites()
                ) {
                    val clusters = repository.getClusteredAntennas(zoom, latNorth, lonEast, latSouth, lonWest)
                    val clusterIsZb = if (AppConfig.showOnlyZbSites.value) 1 else 0

                    // On transforme ces DbCluster en fausses LocalisationEntity
                    val fakeAntennas = clusters.map { cluster ->
                        val singleAntennaId = cluster.singleIdAnfr
                            ?.takeIf { cluster.count == 1 && it.isNotBlank() }

                        LocalisationEntity(
                            idAnfr = singleAntennaId ?: "CLUSTER_${cluster.count}",
                            operateur = OperatorColors.keysFor(cluster.operators).joinToString(", "),
                            latitude = cluster.centerLat,
                            longitude = cluster.centerLon,
                            azimuts = null, codeInsee = null, azimutsFh = null,
                            techMask = 0,
                            bandMask = 0,
                            isZb = clusterIsZb,
                            // Un cluster agrège des sites de statuts mélangés : on le déclare
                            // actif pour qu'il ne tombe pas dans le statut « En projet ».
                            // Sans effet réel : dès qu'un statut est décoché, plus de clustering.
                            hasActive = 1
                        )
                    }
                    _antennas.value = filterClustersToAdminArea(fakeAntennas)
                } else {
                    // Si on a zoomé, on charge les vraies antennes détaillées de la zone
                    val detailBackedBandMask = frequencyFilter.detailBackedBandMaskForEnrichment()
                    val rawAntennas = if (loadsStatusSubsetsOnly) {
                        // Union des sous-ensembles cochés. Le filtre fréquences reste appliqué
                        // côté carte : inutile de passer par la variante SQL par bandes ici.
                        val hsAntennas = if (hsOnlyIds.isNotEmpty()) {
                            repository.getAntennasByIdsInBox(
                                hsOnlyIds,
                                latNorth,
                                lonEast,
                                latSouth,
                                lonWest,
                                detailBackedBandMask = detailBackedBandMask
                            )
                        } else {
                            emptyList()
                        }
                        val projectAntennas = if (showProjectSites) {
                            repository.getProjectAntennasInBox(
                                latNorth,
                                lonEast,
                                latSouth,
                                lonWest,
                                detailBackedBandMask = detailBackedBandMask
                            )
                        } else {
                            emptyList()
                        }
                        (hsAntennas + projectAntennas).distinctBy { it.idAnfr }
                    } else if (hasFrequencyFilter) {
                        repository.getAntennasInBoxForFrequencyFilter(
                            latNorth = latNorth,
                            lonEast = lonEast,
                            latSouth = latSouth,
                            lonWest = lonWest,
                            frequencyFilter = frequencyFilter
                        )
                    } else {
                        repository.getAntennasInBox(
                            latNorth,
                            lonEast,
                            latSouth,
                            lonWest,
                            detailBackedBandMask = detailBackedBandMask
                        )
                    }

                    val areaAntennas = filterDetailedToAdminArea(rawAntennas)

                    // ✅ 3. SI UNE VILLE EST CIBLÉE, ON LA GARDE STRICTEMENT FILTRÉE !
                    if (cityPolygons != null) {
                        _antennas.value = areaAntennas.filter { isPointInPolygon(it.latitude, it.longitude, cityPolygons!!) }
                    } else {
                        _antennas.value = areaAntennas
                    }
                }
                _radioMarkers.value = loadRadioMarkers(zoom, latNorth, lonEast, latSouth, lonWest)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "Map markers request failed", e)
                _antennas.value = emptyList()
                _radioMarkers.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Sites autour d'un point, **indépendamment de ce que la carte affiche**.
     *
     * Le suivi du site le plus proche (outil de mesure) ne peut pas se contenter de la liste à
     * l'écran : dès qu'on regarde ailleurs, qu'on dézoome — il n'y a alors plus que des
     * regroupements, qui ne sont pas des sites — ou qu'une commune est verrouillée, la cible n'y est
     * plus et le trait disparaît. On relit donc la base autour de la position, avec la requête de la
     * carte pour que [accept] (les filtres d'affichage) porte sur les mêmes colonnes.
     *
     * La boîte s'élargit par paliers, et un palier n'est retenu que si son site le plus proche tombe
     * dans le cercle **inscrit** : trouver quelque chose dans un coin ne prouve rien, un site plus
     * proche pouvant se tenir juste au-delà d'un bord. En ville le premier palier suffit, alors que
     * partir d'emblée sur le plus large ramènerait des dizaines de milliers de lignes pour n'en
     * garder qu'une.
     */
    suspend fun loadSitesAround(
        lat: Double,
        lon: Double,
        accept: (LocalisationEntity) -> Boolean
    ): List<LocalisationEntity> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val detailBackedBandMask = FrequencyFilterSelection.fromMapConfig()
                .detailBackedBandMaskForEnrichment()
            val center = GeoPoint(lat, lon)
            // Un degré de longitude rétrécit avec la latitude : c'est lui qui donne le plus petit
            // demi-côté, donc le rayon garanti par la boîte.
            val metersPerDegree = DEGREE_METERS *
                kotlin.math.cos(Math.toRadians(lat)).coerceAtLeast(0.05)
            var best = emptyList<LocalisationEntity>()

            for (radius in NEARBY_TRACKING_RADII_DEGREES) {
                val kept = try {
                    repository.getAntennasInBox(
                        latNorth = lat + radius,
                        lonEast = lon + radius,
                        latSouth = lat - radius,
                        lonWest = lon - radius,
                        detailBackedBandMask = detailBackedBandMask
                    ).filter { !it.idAnfr.startsWith("CLUSTER_") && accept(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Nearby tracking sites request failed", e)
                    return@withContext best
                }
                if (kept.isEmpty()) continue

                best = kept
                val nearestMeters = kept.minOf {
                    center.distanceToAsDouble(GeoPoint(it.latitude, it.longitude))
                }
                if (nearestMeters <= radius * metersPerDegree) return@withContext kept
            }
            best
        }
    }

    /** Charge (une seule fois) la date de mise en service la plus ancienne pour borner le slider temporel. */
    fun ensureOldestServiceDateLoaded() {
        if (_oldestServiceDate.value != null) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val date = runCatching { repository.getOldestServiceDate() }.getOrNull()
            if (date != null) _oldestServiceDate.value = date
        }
    }

    private suspend fun loadRadioMarkers(
        zoom: Double,
        latNorth: Double,
        lonEast: Double,
        latSouth: Double,
        lonWest: Double
    ): List<RadioMapMarker> {
        val categoryMask = AppConfig.radioMapCategoryMask()
        return if (categoryMask != 0) {
            radioRepository.getVisibleMarkers(
                zoom = zoom,
                latNorth = latNorth,
                lonEast = lonEast,
                latSouth = latSouth,
                lonWest = lonWest,
                categoryMask = categoryMask
            )
        } else {
            emptyList()
        }
    }

    fun loadSignalQuestCoveragePointsInBox(
        zoom: Double,
        latNorth: Double,
        lonEast: Double,
        latSouth: Double,
        lonWest: Double
    ) {
        val selectedOperatorKeys = AppConfig.selectedSignalQuestCoverageOperatorKeys.value
            .filter { it in AppConfig.signalQuestCoverageOperatorKeys }
            .toSet()
        val bounds = clippedMetropolitanCoverageBounds(latNorth, lonEast, latSouth, lonWest)

        if (
            !AppConfig.showSignalQuestCoveragePoints.value ||
            zoom < SIGNALQUEST_COVERAGE_MIN_ZOOM ||
            selectedOperatorKeys.isEmpty() ||
            bounds == null
        ) {
            clearSignalQuestCoveragePoints()
            return
        }

        val requestKey = listOf(
            selectedOperatorKeys.sorted().joinToString(","),
            bounds.north,
            bounds.east,
            bounds.south,
            bounds.west
        ).joinToString("|")
        if (requestKey == lastSignalQuestCoverageRequestKey) return
        lastSignalQuestCoverageRequestKey = requestKey

        signalQuestCoverageJob?.cancel()
        signalQuestCoverageJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val perOperatorLimit = ((SIGNALQUEST_COVERAGE_MAX_POINTS + selectedOperatorKeys.size - 1) /
                    selectedOperatorKeys.size).coerceAtLeast(1)
                val loadedPoints = mutableListOf<SignalQuestCoveragePoint>()

                selectedOperatorKeys.sorted().forEach { operatorKey ->
                    val operatorParam = SignalQuestOperators.operatorParamForKey(operatorKey) ?: return@forEach
                    val response = SignalQuestClient.api.getCoveragePoints(
                        market = "FR",
                        operator = operatorParam,
                        north = bounds.north,
                        south = bounds.south,
                        east = bounds.east,
                        west = bounds.west,
                        days = fr.geotower.utils.PowerProfile.coveragePointDays,
                        limit = perOperatorLimit
                    )

                    if (response.isSuccessful) {
                        loadedPoints += response.body()
                            ?.data
                            .orEmpty()
                            .mapNotNull { point ->
                                point.toCoveragePoint(
                                    fallbackOperatorKey = operatorKey,
                                    selectedOperatorKeys = selectedOperatorKeys,
                                    bounds = bounds
                                )
                            }
                    } else {
                        AppLogger.w(TAG, "SignalQuest coverage request failed code=${response.code()}")
                    }
                }

                _signalQuestCoveragePoints.value = loadedPoints
                    .distinctBy { point ->
                        point.id ?: "${point.operatorKey}:${point.latitude}:${point.longitude}:${point.timestamp.orEmpty()}"
                    }
                    .take(SIGNALQUEST_COVERAGE_MAX_POINTS)
                AppLogger.d(TAG, "SignalQuest coverage points loaded count=${_signalQuestCoveragePoints.value.size}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "SignalQuest coverage request failed", e)
                _signalQuestCoveragePoints.value = emptyList()
            }
        }
    }

    fun clearSignalQuestCoveragePoints() {
        signalQuestCoverageJob?.cancel()
        signalQuestCoverageJob = null
        lastSignalQuestCoverageRequestKey = null
        _signalQuestCoveragePoints.value = emptyList()
    }

    private fun sitesHsAnfrIds(sitesHs: List<SiteHsEntity>): List<String> {
        return sitesHs
            .asSequence()
            .filter { it.idAnfr.isNotBlank() }
            .map { it.idAnfr }
            .distinct()
            .toList()
    }

    // =================================================================
    // ✅ PANNES RÉSEAU (GeoTower GeoJSON)
    // =================================================================
    private val _sitesHs = MutableStateFlow<List<SiteHsEntity>>(emptyList())
    val sitesHs = _sitesHs.asStateFlow()

    init {
        fetchSitesHs()
    }

    fun fetchSitesHs() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // On télécharge toutes les pannes de tous les opérateurs en une seule fois !
                val allHs = repository.getSitesHs()
                _sitesHs.value = allHs
                AppLogger.d(TAG, "Outage markers loaded count=${allHs.size}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "Outage markers request failed", e)
            }
        }
    }

    fun loadCityStatsTechniques(idAnfrs: List<String>) {
        val requestedIds = idAnfrs.filter { it.isNotBlank() && !it.startsWith("CLUSTER_") }.toSet()
        if (requestedIds.isEmpty()) {
            clearCityStatsTechniques()
            return
        }
        val missingIds = requestedIds - loadedCityStatsTechniqueIds
        if (missingIds.isEmpty()) return

        cityStatsTechniquesJob?.cancel()
        cityStatsTechniquesJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isCityStatsTechniquesLoading.value = true
            try {
                val loadedTechniques = missingIds
                    .chunked(CITY_STATS_TECHNIQUE_BATCH_SIZE)
                    .fold(emptyMap<String, TechniqueEntity>()) { acc, chunk ->
                        acc + repository.getTechniqueDetailsByIds(chunk)
                    }
                _cityStatsTechniques.value = _cityStatsTechniques.value + loadedTechniques
                loadedCityStatsTechniqueIds = loadedCityStatsTechniqueIds + missingIds
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "City stats technique details request failed", e)
            } finally {
                _isCityStatsTechniquesLoading.value = false
            }
        }
    }

    /** Charge les données publiques de population/superficie d'une commune, sans dépendre de la carte. */
    fun loadCityStatsReference(codeInsee: String?) {
        val normalizedCode = codeInsee?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        if (normalizedCode == cityStatsReferenceCode) return

        cityStatsReferenceCode = normalizedCode
        cityStatsReferenceJob?.cancel()
        if (normalizedCode == null) {
            _cityStatsReference.value = null
            return
        }

        _cityStatsReference.value = null
        cityStatsReferenceJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val reference = CommuneReferenceApi.get(normalizedCode)
            if (cityStatsReferenceCode == normalizedCode) {
                _cityStatsReference.value = reference
            }
        }
    }

    /** Charge le référentiel public d'un département ou d'une région pour les ratios de la fiche. */
    fun loadAdminAreaStatsReference(area: FrenchAdminAreas.Area) {
        val normalizedCode = area.code.trim().uppercase()
        if (normalizedCode.isBlank()) return
        val referenceKey = "${area.kind.name}:$normalizedCode"
        if (referenceKey == cityStatsReferenceCode) return

        cityStatsReferenceCode = referenceKey
        cityStatsReferenceJob?.cancel()
        _cityStatsReference.value = null
        cityStatsReferenceJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val reference = AdministrativeAreaReferenceApi.get(area)
            if (cityStatsReferenceCode == referenceKey) {
                _cityStatsReference.value = reference
            }
        }
    }

    fun clearCityStatsReference() {
        cityStatsReferenceJob?.cancel()
        cityStatsReferenceJob = null
        cityStatsReferenceCode = null
        _cityStatsReference.value = null
    }

    fun clearCityStatsTechniques() {
        cityStatsTechniquesJob?.cancel()
        _cityStatsTechniques.value = emptyMap()
        _isCityStatsTechniquesLoading.value = false
        loadedCityStatsTechniqueIds = emptySet()
    }

    suspend fun getMapAzimuthTechniqueDetails(idAnfrs: List<String>): Map<String, TechniqueEntity> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val requestedIds = idAnfrs
                .filter { it.isNotBlank() && !it.startsWith("CLUSTER_") }
                .distinct()
            if (requestedIds.isEmpty()) return@withContext emptyMap()

            val missingIds = requestedIds.filterNot { mapAzimuthTechniqueCache.containsKey(it) }
            if (missingIds.isNotEmpty()) {
                missingIds
                    .chunked(MAP_AZIMUTH_TECHNIQUE_BATCH_SIZE)
                    .forEach { chunk ->
                        mapAzimuthTechniqueCache += repository.getTechniqueDetailsByIds(chunk)
                    }
            }

            requestedIds.mapNotNull { id ->
                mapAzimuthTechniqueCache[id]?.let { technique -> id to technique }
            }.toMap()
        }
    }

    /** Résout les adresses affichées dans les informations contextuelles de la carte. */
    suspend fun getMapTechniqueSummaries(idAnfrs: List<String>): Map<String, TechniqueEntity> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val requestedIds = idAnfrs.filter { it.isNotBlank() }.distinct()
            if (requestedIds.isEmpty()) emptyMap()
            else repository.getTechniqueSummariesByIds(requestedIds)
        }
    }

    /**
     * Regroupe les antennes situées à un même point par support physique
     * (id_support, avec repli sur id_anfr si absent), dans l'ordre rencontré.
     *
     * - Une seule entrée => point normal : un support unique, éventuellement
     *   plusieurs opérateurs (donc plusieurs id_anfr) — pas d'ambiguïté.
     * - Plusieurs entrées => plusieurs supports superposés à désambiguïser.
     */
    suspend fun resolveSupportChoices(antennas: List<LocalisationEntity>): List<SupportChoice> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val distinct = antennas.distinctBy { it.idAnfr }
            val grouped = LinkedHashMap<String, MutableList<LocalisationEntity>>()
            val natureByKey = HashMap<String, String?>()
            for (antenna in distinct) {
                val physique = repository.getPhysiqueByAnfr(antenna.idAnfr).firstOrNull()
                val key = physique?.idSupport?.trim()?.takeIf { it.isNotBlank() } ?: antenna.idAnfr
                grouped.getOrPut(key) { mutableListOf() }.add(antenna)
                if (!natureByKey.containsKey(key)) natureByKey[key] = physique?.natureSupport
            }
            grouped.map { (key, group) ->
                val operatorKeys = group
                    .mapNotNull { it.operateur }
                    .flatMap { OperatorColors.keysFor(it) }
                    .distinct()
                SupportChoice(
                    supportId = key,
                    representative = group.first(),
                    operatorKeys = operatorKeys,
                    nature = natureByKey[key]
                )
            }
        }
    }

    /**
     * Les supports présents autour d'une étape de tournée, du plus proche au plus lointain, avec
     * leurs stations et leur adresse : de quoi proposer un envoi de photos en arrivant sur place.
     *
     * La lecture est **volontairement non filtrée** : on est devant le pylône, ses opérateurs
     * masqués sur la carte n'en existent pas moins, et rien ne serait plus déroutant qu'un envoi
     * impossible parce qu'une case de filtre est décochée à l'autre bout des réglages.
     */
    suspend fun loadTripStepSupports(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double = TRIP_STEP_SITE_RADIUS_METERS
    ): List<TripStepSupportDetails> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val box = tripStepSearchBox(latitude, longitude, radiusMeters)
            val nearby = repository
                .getAntennasInBox(box[0], box[1], box[2], box[3])
                .distinctBy { it.idAnfr }
            if (nearby.isEmpty()) return@withContext emptyList()

            // Une seule requête pour tous les supports, puis une pour toutes les adresses : autour
            // d'une étape il y a rarement plus d'une poignée de stations, mais sur un site étendu
            // une requête par station se paierait à l'arrivée, montre en main.
            val physiqueByAnfr = repository.getPhysiqueDetailsByIds(nearby.map { it.idAnfr })
            val supports = groupTripStepSupports(
                antennas = nearby.map { antenna ->
                    TripStepAntennaRef(
                        idAnfr = antenna.idAnfr,
                        supportId = physiqueByAnfr[antenna.idAnfr]
                            ?.firstOrNull()
                            ?.idSupport
                            ?.trim()
                            .orEmpty(),
                        latitude = antenna.latitude,
                        longitude = antenna.longitude
                    )
                },
                stepLatitude = latitude,
                stepLongitude = longitude,
                radiusMeters = radiusMeters
            )
            if (supports.isEmpty()) return@withContext emptyList()

            val antennaById = nearby.associateBy { it.idAnfr }
            val techniques = repository.getTechniqueDetailsByIds(
                supports.flatMap { it.idAnfrs }.distinct()
            )
            supports.map { support ->
                TripStepSupportDetails(
                    support = support,
                    antennas = support.idAnfrs.mapNotNull { antennaById[it] },
                    address = support.idAnfrs
                        .firstNotNullOfOrNull { idAnfr ->
                            techniques[idAnfr]?.adresse?.trim()?.takeIf { it.isNotBlank() }
                        }
                        .orEmpty()
                )
            }
        }
    }

    // =================================================================

    fun clearCityFilterAndReload(zoom: Double, latNorth: Double, lonEast: Double, latSouth: Double, lonWest: Double) {
        isCityLocked = false // ✅ ON DÉVERROUILLE
        cityPolygons = null
        clearAdminAreaFilter()
        _antennas.value = emptyList()
        _radioMarkers.value = emptyList()
        loadAntennasInBox(zoom, latNorth, lonEast, latSouth, lonWest)
        loadSignalQuestCoveragePointsInBox(zoom, latNorth, lonEast, latSouth, lonWest)
    }

    fun resetCityLock() {
        searchJob?.cancel()
        clearSignalQuestCoveragePoints()
        isCityLocked = false // ✅ ON DÉVERROUILLE
        cityPolygons = null
        clearAdminAreaFilter()
        _antennas.value = emptyList()
        _radioMarkers.value = emptyList()
    }

    /**
     * Contour d'un département / d'une région, récupéré en ligne pour l'habillage de la carte
     * (masque + liseré rouge, comme une commune).
     *
     * Il ne sert JAMAIS à filtrer : le tri des sites passe par le code INSEE en SQL, donc la
     * recherche reste exacte et fonctionne hors réseau, contour ou pas. Gardé dans le ViewModel et
     * non dans l'état sauvegardé : un contour départemental, même simplifié, dépasse ce qu'on peut
     * raisonnablement mettre dans un Bundle.
     */
    fun loadAdminAreaOutline(areaCode: String, areaName: String, isRegion: Boolean) {
        if (_adminAreaOutline.value?.areaCode == areaCode) return

        adminAreaOutlineJob?.cancel()
        _adminAreaOutline.value = null
        adminAreaOutlineJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val area = NominatimApi.searchAdminAreaOutline(areaName, isRegion)
                val polygons = area?.polygons
                    ?.map { ring -> ring.map { GeoPoint(it.latitude, it.longitude) } }
                    ?.filter { it.size >= 3 }
                    .orEmpty()
                val trimmed = trimOutlineRings(polygons)
                if (trimmed.isNotEmpty()) {
                    _adminAreaOutline.value = AdminAreaOutline(areaCode, trimmed)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "Admin area outline failed", e)
            }
        }
    }

    fun clearAdminAreaOutline() {
        adminAreaOutlineJob?.cancel()
        _adminAreaOutline.value = null
    }

    /** Emprise d'un département / d'une région, ou null si la base locale ne sait rien en dire. */
    suspend fun findAdminAreaExtent(departmentCodes: List<String>): AdminAreaExtent? {
        return try {
            repository.getAdminAreaExtent(departmentCodes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "Admin area extent failed", e)
            null
        }
    }

    suspend fun searchSiteById(query: String): LocalisationEntity? {
        return try {
            val results = repository.searchAntennasById(query)
            results.firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Stations proposées pendant la frappe : mêmes résultats que [searchSiteById], mais dédoublonnés
     * par identifiant ANFR — une station porte une ligne par opérateur, et la liste de suggestions
     * n'a pas à les répéter.
     */
    suspend fun searchSiteSuggestions(query: String, limit: Int): List<LocalisationEntity> {
        if (limit <= 0) return emptyList()
        return try {
            repository.searchAntennasById(query)
                .distinctBy { it.idAnfr }
                .take(limit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Communes proposées pendant la frappe, depuis le référentiel embarqué dans la base. */
    suspend fun searchCommuneSuggestions(query: String, limit: Int): List<CommuneNameRow> {
        return try {
            repository.searchCommunesByName(query, limit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "Commune suggestions failed", e)
            emptyList()
        }
    }

    /** Emprise d'une commune, ou null si la base locale n'y connaît aucune station. */
    suspend fun findCommuneExtent(codeInsee: String): AdminAreaExtent? {
        return try {
            repository.getCommuneExtent(codeInsee)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "Commune extent failed", e)
            null
        }
    }

    /**
     * Dégraisse un contour administratif avant affichage.
     *
     * Un contour régional brut compte près de 8 000 points, dont l'essentiel vient de centaines
     * d'îlots : la simplification demandée au serveur n'y peut rien, chaque anneau garde un nombre
     * minimal de sommets. Or ce tracé est reconstruit à chaque image sous la carte. On ne garde
     * donc que les anneaux visibles à cette échelle (≥ ~1 km), dans un budget de points fixe, en
     * commençant par le plus gros — la partie continentale.
     */
    private fun trimOutlineRings(polygons: List<List<GeoPoint>>): List<List<GeoPoint>> {
        if (polygons.isEmpty()) return emptyList()

        val candidates = polygons
            .filter { ring -> ringSpanDegrees(ring) >= OUTLINE_MIN_RING_SPAN_DEGREES }
            .sortedByDescending { it.size }
            .ifEmpty { listOfNotNull(polygons.maxByOrNull { it.size }) }

        val kept = mutableListOf<List<GeoPoint>>()
        var used = 0
        candidates.forEachIndexed { index, ring ->
            // Le plus gros anneau passe toujours : sans lui il n'y aurait plus de contour du tout.
            if (index == 0 || used + ring.size <= OUTLINE_MAX_POINTS) {
                kept += ring
                used += ring.size
            }
        }
        return kept
    }

    /** Plus grande dimension de l'anneau, en degrés — assez pour juger s'il fera plus d'un pixel. */
    private fun ringSpanDegrees(ring: List<GeoPoint>): Double {
        if (ring.isEmpty()) return 0.0
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        ring.forEach { point ->
            if (point.latitude < minLat) minLat = point.latitude
            if (point.latitude > maxLat) maxLat = point.latitude
            if (point.longitude < minLon) minLon = point.longitude
            if (point.longitude > maxLon) maxLon = point.longitude
        }
        return maxOf(maxLat - minLat, maxLon - minLon)
    }

    private fun isPointInPolygon(lat: Double, lon: Double, polygons: List<List<GeoPoint>>): Boolean {
        var isInside = false
        for (polygon in polygons) {
            var j = polygon.size - 1
            var polyInside = false
            for (i in polygon.indices) {
                val pi = polygon[i]
                val pj = polygon[j]
                if (((pi.longitude > lon) != (pj.longitude > lon)) &&
                    (lat < (pj.latitude - pi.latitude) * (lon - pi.longitude) / (pj.longitude - pi.longitude) + pi.latitude)) {
                    polyInside = !polyInside
                }
                j = i
            }
            if (polyInside) isInside = true
        }
        return isInside
    }
}

private fun clippedMetropolitanCoverageBounds(
    latNorth: Double,
    lonEast: Double,
    latSouth: Double,
    lonWest: Double
): SignalQuestCoverageBounds? {
    val north = minOf(latNorth, SIGNALQUEST_COVERAGE_METRO_NORTH)
    val south = maxOf(latSouth, SIGNALQUEST_COVERAGE_METRO_SOUTH)
    val east = minOf(lonEast, SIGNALQUEST_COVERAGE_METRO_EAST)
    val west = maxOf(lonWest, SIGNALQUEST_COVERAGE_METRO_WEST)

    return if (north > south && east > west) {
        SignalQuestCoverageBounds(north = north, east = east, south = south, west = west)
    } else {
        null
    }
}

private fun SqCoveragePointData.toCoveragePoint(
    fallbackOperatorKey: String,
    selectedOperatorKeys: Set<String>,
    bounds: SignalQuestCoverageBounds
): SignalQuestCoveragePoint? {
    val pointCoordinates = coordinates ?: return null
    val latitude = pointCoordinates.lat ?: return null
    val longitude = pointCoordinates.lng ?: return null
    if (latitude !in bounds.south..bounds.north || longitude !in bounds.west..bounds.east) return null

    val operatorKey = OperatorColors.keyFor(mobileOperator) ?: fallbackOperatorKey
    if (operatorKey !in selectedOperatorKeys) return null
    val operatorLabel = OperatorColors.specForKey(operatorKey)?.label ?: mobileOperator ?: operatorKey

    return SignalQuestCoveragePoint(
        id = id,
        latitude = latitude,
        longitude = longitude,
        operatorKey = operatorKey,
        operatorLabel = operatorLabel,
        technology = technology,
        networkType = networkType,
        signalStrength = signalStrength,
        rsrq = rsrq,
        snr = snr,
        mcc = mcc,
        mnc = mnc,
        enb = radio?.enb,
        gnb = radio?.gnb,
        cellId = radio?.cellId,
        pci = radio?.pci,
        timestamp = timestamp
    )
}

class MapViewModelFactory(
    private val repository: AnfrRepository,
    private val radioRepository: RadioRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MapViewModel(repository, radioRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

private const val TAG = "GeoTowerMap"
private const val CITY_STATS_TECHNIQUE_BATCH_SIZE = 400
/** ~1 km : en dessous, un anneau ne fait même pas un pixel à l'échelle d'un département. */
private const val OUTLINE_MIN_RING_SPAN_DEGREES = 0.01
private const val OUTLINE_MAX_POINTS = 2500
private const val MAP_AZIMUTH_TECHNIQUE_BATCH_SIZE = 400
/**
 * Paliers d'élargissement du voisinage cherché par le suivi, en degrés (~2, 7, 20 puis 55 km).
 * Le dernier borne le coût : au-delà, la boîte pèserait bien plus que le service rendu.
 */
private val NEARBY_TRACKING_RADII_DEGREES = listOf(0.02, 0.06, 0.18, 0.5)
/** Longueur d'un degré de latitude, en mètres. */
private const val DEGREE_METERS = 111_320.0
private const val SIGNALQUEST_COVERAGE_MIN_ZOOM = 13.0
private const val SIGNALQUEST_COVERAGE_MAX_POINTS = 5000
private const val SIGNALQUEST_COVERAGE_DAYS = 365
private const val SIGNALQUEST_COVERAGE_METRO_SOUTH = 41.0
private const val SIGNALQUEST_COVERAGE_METRO_NORTH = 51.6
private const val SIGNALQUEST_COVERAGE_METRO_WEST = -5.8
private const val SIGNALQUEST_COVERAGE_METRO_EAST = 10.1
