package fr.geotower.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.geotower.data.AnfrRepository
import fr.geotower.data.RadioRepository
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.coverage.CoverageComputer
import fr.geotower.data.coverage.CoverageRequest
import fr.geotower.data.coverage.SiteCoverage
import fr.geotower.data.coverage.ViewshedParams
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
import fr.geotower.utils.FrequencyFilterSelection
import fr.geotower.utils.OperatorColors

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

/** Progression du calcul de couverture théorique (lots de requêtes terrain). */
data class TheoreticalCoverageProgress(val done: Int, val total: Int)

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

    private val _theoreticalCoverage = MutableStateFlow<SiteCoverage?>(null)
    val theoreticalCoverage = _theoreticalCoverage.asStateFlow()

    private val _theoreticalCoverageProgress = MutableStateFlow<TheoreticalCoverageProgress?>(null)
    val theoreticalCoverageProgress = _theoreticalCoverageProgress.asStateFlow()

    private val _theoreticalCoverageLoading = MutableStateFlow(false)
    val theoreticalCoverageLoading = _theoreticalCoverageLoading.asStateFlow()

    private var theoreticalCoverageJob: Job? = null
    private val theoreticalCoverageCache = mutableMapOf<String, SiteCoverage>()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // Date de mise en service la plus ancienne, pour borner le slider temporel de la carte.
    private val _oldestServiceDate = MutableStateFlow<String?>(null)
    val oldestServiceDate = _oldestServiceDate.asStateFlow()

    private val _cityStatsTechniques = MutableStateFlow<Map<String, TechniqueEntity>>(emptyMap())
    val cityStatsTechniques = _cityStatsTechniques.asStateFlow()

    private val _isCityStatsTechniquesLoading = MutableStateFlow(false)
    val isCityStatsTechniquesLoading = _isCityStatsTechniquesLoading.asStateFlow()

    private var searchJob: Job? = null
    private var signalQuestCoverageJob: Job? = null
    private var lastSignalQuestCoverageRequestKey: String? = null
    private var cityStatsTechniquesJob: Job? = null
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

    fun loadAntennasInBox(zoom: Double, latNorth: Double, lonEast: Double, latSouth: Double, lonWest: Double) {
        if (isCityLocked) return // ✅ MAGIQUE : Si on regarde une ville, on ne recalcule RIEN quand on bouge la carte !

        searchJob?.cancel()

        searchJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isLoading.value = true
            try {
                // ✅ 2. ON EMPÊCHE LE CLUSTERING GLOBAL SI UNE VILLE EST RECHERCHÉE
                val showSitesInService = AppConfig.showSitesInService.value
                val showSitesOutOfService = AppConfig.showSitesOutOfService.value
                val showOnlySitesOutOfService = !showSitesInService && showSitesOutOfService
                val hsOnlyIds = if (showOnlySitesOutOfService) {
                    sitesHsAnfrIds(_sitesHs.value)
                } else {
                    emptyList()
                }

                if (!showSitesInService && !showSitesOutOfService) {
                    _antennas.value = emptyList()
                    _radioMarkers.value = loadRadioMarkers(zoom, latNorth, lonEast, latSouth, lonWest)
                    return@launch
                }

                if (showOnlySitesOutOfService && hsOnlyIds.isEmpty()) {
                    _antennas.value = emptyList()
                    _radioMarkers.value = loadRadioMarkers(zoom, latNorth, lonEast, latSouth, lonWest)
                    return@launch
                }

                val frequencyFilter = FrequencyFilterSelection.fromMapConfig()
                val hasSiteDisplayFilter = !showSitesInService || !showSitesOutOfService
                val hasFrequencyFilter = !frequencyFilter.isFullyEnabled

                if (zoom < 13.0 && cityPolygons == null && !hasSiteDisplayFilter && !hasFrequencyFilter && !AppConfig.timeSliderActive.value) {
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
                            isZb = clusterIsZb
                        )
                    }
                    _antennas.value = fakeAntennas
                } else {
                    // Si on a zoomé, on charge les vraies antennes détaillées de la zone
                    val detailBackedBandMask = frequencyFilter.detailBackedBandMaskForEnrichment()
                    val rawAntennas = if (showOnlySitesOutOfService) {
                        repository.getAntennasByIdsInBox(
                            hsOnlyIds,
                            latNorth,
                            lonEast,
                            latSouth,
                            lonWest,
                            detailBackedBandMask = detailBackedBandMask
                        )
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

                    // ✅ 3. SI UNE VILLE EST CIBLÉE, ON LA GARDE STRICTEMENT FILTRÉE !
                    if (cityPolygons != null) {
                        _antennas.value = rawAntennas.filter { isPointInPolygon(it.latitude, it.longitude, cityPolygons!!) }
                    } else {
                        _antennas.value = rawAntennas
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
                        days = SIGNALQUEST_COVERAGE_DAYS,
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

    /**
     * Calcule (ou ressort du cache) la couverture théorique d'un site et la publie pour l'overlay carte.
     * Le terrain est mutualisé ; respecte les flags (provider IGN + feature) et les bornes (Limits).
     * Le calcul est annulable et reporte sa progression.
     */
    fun loadTheoreticalCoverageForSite(idAnfr: String) {
        if (idAnfr.isBlank()) return
        if (!RemoteFeatureFlags.isProviderEnabled(RemoteFeatureFlags.Providers.ELEVATION_IGN) ||
            !RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SITE_THEORETICAL_COVERAGE)
        ) {
            return
        }

        val request = defaultCoverageRequest()
        val cacheKey = listOf(
            idAnfr, request.maxRadiusM, request.angularStepDeg, request.sampleStepM,
            request.includeObstacles, request.viewshed.tiltDeg, request.viewshed.frequencyMHz
        ).joinToString("|")
        theoreticalCoverageCache[cacheKey]?.let { cached ->
            theoreticalCoverageJob?.cancel()
            _theoreticalCoverageLoading.value = false
            _theoreticalCoverageProgress.value = null
            _theoreticalCoverage.value = cached
            return
        }

        theoreticalCoverageJob?.cancel()
        _theoreticalCoverageLoading.value = true
        _theoreticalCoverageProgress.value = null
        theoreticalCoverageJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val coverage = CoverageComputer.compute(
                    repository = repository,
                    idAnfr = idAnfr,
                    request = request,
                    maxPointsPerRequest = RemoteFeatureFlags.limitOrDefault(RemoteFeatureFlags.Limits.COVERAGE_MAX_POINTS_PER_REQUEST, 300),
                    maxConcurrentRequests = RemoteFeatureFlags.limitOrDefault(RemoteFeatureFlags.Limits.COVERAGE_MAX_CONCURRENT_REQUESTS, 1)
                ) { done, total ->
                    _theoreticalCoverageProgress.value = TheoreticalCoverageProgress(done, total)
                }
                if (coverage == null) {
                    _theoreticalCoverage.value = null
                    return@launch
                }
                theoreticalCoverageCache[cacheKey] = coverage
                _theoreticalCoverage.value = coverage
                AppLogger.d(
                    TAG,
                    "Coverage[$idAnfr] terrain=${(coverage.terrainValidFraction * 100).toInt()}% " +
                        "KO=${coverage.terrainFailedChunks}/${coverage.terrainTotalChunks} sectors=${coverage.sectors.size}"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "Theoretical coverage failed", e)
                _theoreticalCoverage.value = null
            } finally {
                _theoreticalCoverageLoading.value = false
                _theoreticalCoverageProgress.value = null
            }
        }
    }

    fun clearTheoreticalCoverage() {
        theoreticalCoverageJob?.cancel()
        theoreticalCoverageJob = null
        _theoreticalCoverage.value = null
        _theoreticalCoverageProgress.value = null
        _theoreticalCoverageLoading.value = false
    }

    /** Profil « équilibré » par défaut (≈ 6 km / 3° / 30 m), borné par les flags. Lot 3 exposera le curseur. */
    private fun defaultCoverageRequest(): CoverageRequest {
        val maxRadiusM = RemoteFeatureFlags.limitOrDefault(RemoteFeatureFlags.Limits.COVERAGE_MAX_RADIUS_KM, 8) * 1000.0
        // Défaut volontairement léger : le réseau MOBILE s'effondre au-delà de ~30 requêtes IGN en rafale
        // (l'IGN lui-même encaisse, c'est l'appareil qui timeout). ~24 requêtes ici. Lot 3 = curseur qualité.
        val radius = minOf(4000.0, maxRadiusM)
        val minAngular = RemoteFeatureFlags.limitOrDefault(RemoteFeatureFlags.Limits.COVERAGE_MIN_ANGULAR_STEP_DEG, 2).toDouble()
        val minSample = RemoteFeatureFlags.limitOrDefault(RemoteFeatureFlags.Limits.COVERAGE_SAMPLE_STEP_M, 20).toDouble()
        val tilt = RemoteFeatureFlags.limitOrDefault(RemoteFeatureFlags.Limits.COVERAGE_DEFAULT_TILT_DEG, 5).toDouble()
        val vbeam = RemoteFeatureFlags.limitOrDefault(RemoteFeatureFlags.Limits.COVERAGE_DEFAULT_VBEAM_DEG, 10).toDouble()
        // Concurrence 1 (l'IGN rate-limite la concurrence) ⇒ on garde peu de requêtes pour rester ~15-20 s.
        return CoverageRequest(
            maxRadiusM = radius,
            angularStepDeg = maxOf(6.0, minAngular),
            sampleStepM = maxOf(70.0, minSample),
            includeObstacles = true,
            viewshed = ViewshedParams(
                maxRadiusM = radius,
                curvature = true,
                fresnel = false,
                frequencyMHz = 3500,
                tiltDeg = tilt,
                verticalBeamwidthDeg = vbeam
            )
        )
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

    // =================================================================

    fun clearCityFilterAndReload(zoom: Double, latNorth: Double, lonEast: Double, latSouth: Double, lonWest: Double) {
        isCityLocked = false // ✅ ON DÉVERROUILLE
        cityPolygons = null
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
        _antennas.value = emptyList()
        _radioMarkers.value = emptyList()
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
private const val MAP_AZIMUTH_TECHNIQUE_BATCH_SIZE = 400
private const val SIGNALQUEST_COVERAGE_MIN_ZOOM = 13.0
private const val SIGNALQUEST_COVERAGE_MAX_POINTS = 5000
private const val SIGNALQUEST_COVERAGE_DAYS = 365
private const val SIGNALQUEST_COVERAGE_METRO_SOUTH = 41.0
private const val SIGNALQUEST_COVERAGE_METRO_NORTH = 51.6
private const val SIGNALQUEST_COVERAGE_METRO_WEST = -5.8
private const val SIGNALQUEST_COVERAGE_METRO_EAST = 10.1
