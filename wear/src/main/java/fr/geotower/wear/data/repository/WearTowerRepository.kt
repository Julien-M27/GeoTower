package fr.geotower.wear.data.repository

import android.content.Context
import android.util.Log
import fr.geotower.wear.data.model.DataSourceMode
import fr.geotower.wear.data.model.WearTower
import fr.geotower.wear.data.network.WearWebApiClient
import fr.geotower.wear.data.preferences.WearPreferences
import fr.geotower.wear.data.wearable.WearDataLayerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class WearTowerRepository(context: Context) {

    companion object {
        private const val TAG = "WearTowerRepository"
    }

    private val preferences = WearPreferences(context)
    private val dataLayerClient = WearDataLayerClient(context)

    val dataSourceMode: StateFlow<DataSourceMode> = preferences.dataSourceMode
    val isPhoneAppInstalled: StateFlow<Boolean> = dataLayerClient.isPhoneAppInstalled
    val gpsRefreshInterval: StateFlow<fr.geotower.wear.data.model.GpsRefreshInterval> = preferences.gpsRefreshInterval
    val mapTheme: StateFlow<fr.geotower.wear.data.model.WearMapTheme> = preferences.mapTheme
    val language: StateFlow<fr.geotower.wear.data.model.WearLanguage> = preferences.language
    val distanceUnit: StateFlow<fr.geotower.wear.data.model.DistanceUnit> = preferences.distanceUnit

    private val _towers = MutableStateFlow<List<WearTower>>(emptyList())
    val towers: StateFlow<List<WearTower>> = _towers.asStateFlow()

    private val _selectedTowerId = MutableStateFlow<String?>(null)
    val selectedTowerId: StateFlow<String?> = _selectedTowerId.asStateFlow()

    private val _operatorFilter = MutableStateFlow<String?>(null) // null = Tous
    val operatorFilter: StateFlow<String?> = _operatorFilter.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun selectTower(id: String) {
        _selectedTowerId.value = id
    }

    fun setOperatorFilter(filter: String?) {
        _operatorFilter.value = filter
    }

    suspend fun checkPhoneAppInstalled(): Boolean {
        val installed = dataLayerClient.checkPhoneAppInstalled()
        if (!installed && preferences.dataSourceMode.value == DataSourceMode.PHONE_LOCAL) {
            Log.w(TAG, "Application smartphone GeoTower non détectée : repli automatique sur AUTO")
            preferences.setDataSourceMode(DataSourceMode.AUTO)
        }
        return installed
    }

    fun setDataSourceMode(mode: DataSourceMode) {
        if (mode == DataSourceMode.PHONE_LOCAL && !isPhoneAppInstalled.value) {
            Log.w(TAG, "Refus de verrouiller sur PHONE_LOCAL : application smartphone non installée")
            preferences.setDataSourceMode(DataSourceMode.AUTO)
            return
        }
        preferences.setDataSourceMode(mode)
    }

    fun setGpsRefreshInterval(interval: fr.geotower.wear.data.model.GpsRefreshInterval) {
        preferences.setGpsRefreshInterval(interval)
    }

    fun setMapTheme(theme: fr.geotower.wear.data.model.WearMapTheme) {
        preferences.setMapTheme(theme)
    }

    fun setLanguage(language: fr.geotower.wear.data.model.WearLanguage) {
        preferences.setLanguage(language)
    }

    fun setDistanceUnit(unit: fr.geotower.wear.data.model.DistanceUnit) {
        preferences.setDistanceUnit(unit)
    }

    suspend fun loadTowers(latitude: Double, longitude: Double) = withContext(Dispatchers.IO) {
        _isLoading.value = true
        _errorMessage.value = null

        val currentMode = preferences.dataSourceMode.value
        Log.i(TAG, "Chargement des antennes pour lat=$latitude, lon=$longitude en mode $currentMode")

        try {
            val results = when (currentMode) {
                DataSourceMode.AUTO -> {
                    // Stratégie Auto : interroge d'abord la base locale du téléphone via Bluetooth
                    try {
                        Log.d(TAG, "Mode AUTO : essai via smartphone Bluetooth...")
                        val phoneResults = dataLayerClient.fetchNearbyFromPhone(lat = latitude, lon = longitude)
                        if (phoneResults.isNotEmpty()) {
                            Log.i(TAG, "Mode AUTO : ${phoneResults.size} antennes reçues du smartphone")
                            phoneResults
                        } else {
                            Log.d(TAG, "Mode AUTO : smartphone n'a pas renvoyé d'antennes, bascule sur API Web...")
                            WearWebApiClient.fetchNearbyTowers(lat = latitude, lon = longitude)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Mode AUTO : échec smartphone (${e.message}), bascule sur API Web...", e)
                        WearWebApiClient.fetchNearbyTowers(lat = latitude, lon = longitude)
                    }
                }
                DataSourceMode.PHONE_LOCAL -> {
                    Log.d(TAG, "Mode PHONE_LOCAL : interrogation smartphone...")
                    dataLayerClient.fetchNearbyFromPhone(lat = latitude, lon = longitude)
                }
                DataSourceMode.WEB_API -> {
                    Log.d(TAG, "Mode WEB_API : appel direct API Web...")
                    WearWebApiClient.fetchNearbyTowers(lat = latitude, lon = longitude)
                }
            }
            _towers.value = results
            if (results.isNotEmpty() && (_selectedTowerId.value == null || results.none { it.id == _selectedTowerId.value })) {
                _selectedTowerId.value = results.first().id
            }
            if (results.isEmpty()) {
                _errorMessage.value = "Aucune antenne dans un rayon de 5 km"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du chargement des sites", e)
            _towers.value = emptyList()
            val msg = e.localizedMessage?.ifBlank { null } ?: e.javaClass.simpleName
            _errorMessage.value = "Erreur de chargement : $msg"
        } finally {
            _isLoading.value = false
        }
    }
}
