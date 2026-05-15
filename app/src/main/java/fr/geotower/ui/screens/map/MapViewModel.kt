package fr.geotower.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.geotower.data.AnfrRepository
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.TechniqueEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import fr.geotower.data.models.SiteHsEntity
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import fr.geotower.utils.OperatorColors

class MapViewModel(private val repository: AnfrRepository) : ViewModel() {

    private val _antennas = MutableStateFlow<List<LocalisationEntity>>(emptyList())
    val antennas = _antennas.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _cityStatsTechniques = MutableStateFlow<Map<String, TechniqueEntity>>(emptyMap())
    val cityStatsTechniques = _cityStatsTechniques.asStateFlow()

    private val _isCityStatsTechniquesLoading = MutableStateFlow(false)
    val isCityStatsTechniquesLoading = _isCityStatsTechniquesLoading.asStateFlow()

    private var searchJob: Job? = null
    private var cityStatsTechniquesJob: Job? = null
    private var loadedCityStatsTechniqueIds: Set<String> = emptySet()
    private var cityPolygons: List<List<GeoPoint>>? = null
    private var isCityLocked = false

    fun loadAntennasForCity(latNorth: Double, lonEast: Double, latSouth: Double, lonWest: Double, polygons: List<List<GeoPoint>>) {
        searchJob?.cancel()
        cityPolygons = polygons
        isCityLocked = true // ✅ ON VERROUILLE LE CHARGEMENT AUTOMATIQUE

        searchJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                val apiMarkers = repository.getAntennasInBox(latNorth, lonEast, latSouth, lonWest)
                val filteredMarkers = apiMarkers.filter { isPointInPolygon(it.latitude, it.longitude, polygons) }

                _antennas.value = filteredMarkers
                AppLogger.d(TAG, "City map markers filtered=${filteredMarkers.size} total=${apiMarkers.size}")
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
                val hasSiteDisplayFilter = !AppConfig.showSitesInService.value || !AppConfig.showSitesOutOfService.value

                if (zoom < 13.0 && cityPolygons == null && !hasSiteDisplayFilter) {
                    val clusters = repository.getClusteredAntennas(zoom, latNorth, lonEast, latSouth, lonWest)

                    // On transforme ces DbCluster en fausses LocalisationEntity
                    val fakeAntennas = clusters.map { cluster ->
                        LocalisationEntity(
                            idAnfr = "CLUSTER_${cluster.count}",
                            operateur = OperatorColors.keysFor(cluster.operators).joinToString(", "),
                            latitude = cluster.centerLat,
                            longitude = cluster.centerLon,
                            azimuts = null, codeInsee = null, azimutsFh = null,
                            techMask = 0,
                            bandMask = 0
                        )
                    }
                    _antennas.value = fakeAntennas
                } else {
                    // Si on a zoomé, on charge les vraies antennes détaillées de la zone
                    val rawAntennas = repository.getAntennasInBox(latNorth, lonEast, latSouth, lonWest)

                    // ✅ 3. SI UNE VILLE EST CIBLÉE, ON LA GARDE STRICTEMENT FILTRÉE !
                    if (cityPolygons != null) {
                        _antennas.value = rawAntennas.filter { isPointInPolygon(it.latitude, it.longitude, cityPolygons!!) }
                    } else {
                        _antennas.value = rawAntennas
                    }
                }
            } catch (e: Exception) {
                _antennas.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
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

    // =================================================================

    fun clearCityFilterAndReload(zoom: Double, latNorth: Double, lonEast: Double, latSouth: Double, lonWest: Double) {
        isCityLocked = false // ✅ ON DÉVERROUILLE
        cityPolygons = null
        _antennas.value = emptyList()
        loadAntennasInBox(zoom, latNorth, lonEast, latSouth, lonWest)
    }

    fun resetCityLock() {
        searchJob?.cancel()
        isCityLocked = false // ✅ ON DÉVERROUILLE
        cityPolygons = null
        _antennas.value = emptyList()
    }

    suspend fun searchSiteById(query: String): LocalisationEntity? {
        return try {
            val results = repository.searchAntennasById(query)
            results.firstOrNull()
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

class MapViewModelFactory(private val repository: AnfrRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MapViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

private const val TAG = "GeoTowerMap"
private const val CITY_STATS_TECHNIQUE_BATCH_SIZE = 400
