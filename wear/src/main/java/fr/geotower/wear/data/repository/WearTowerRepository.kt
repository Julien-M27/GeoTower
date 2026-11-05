package fr.geotower.wear.data.repository

import android.content.Context
import fr.geotower.wear.data.model.DataSourceMode
import fr.geotower.wear.data.model.WearTower
import fr.geotower.wear.data.network.WearWebApiClient
import fr.geotower.wear.data.preferences.WearPreferences
import fr.geotower.wear.data.wearable.WearDataLayerClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WearTowerRepository(context: Context) {

    private val preferences = WearPreferences(context)
    private val dataLayerClient = WearDataLayerClient(context)

    val dataSourceMode: StateFlow<DataSourceMode> = preferences.dataSourceMode

    private val _towers = MutableStateFlow<List<WearTower>>(emptyList())
    val towers: StateFlow<List<WearTower>> = _towers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setDataSourceMode(mode: DataSourceMode) {
        preferences.setDataSourceMode(mode)
    }

    suspend fun loadTowers(latitude: Double, longitude: Double) {
        _isLoading.value = true
        _errorMessage.value = null

        val currentMode = preferences.dataSourceMode.value

        try {
            val results = when (currentMode) {
                DataSourceMode.PHONE_LOCAL -> {
                    dataLayerClient.fetchNearbyFromPhone(lat = latitude, lon = longitude)
                }
                DataSourceMode.WEB_API -> {
                    WearWebApiClient.fetchNearbyTowers(lat = latitude, lon = longitude)
                }
            }
            _towers.value = results
            if (results.isEmpty()) {
                _errorMessage.value = "Aucune antenne dans un rayon de 5 km"
            }
        } catch (e: Exception) {
            _towers.value = emptyList()
            _errorMessage.value = e.message ?: "Erreur de chargement"
        } finally {
            _isLoading.value = false
        }
    }
}
