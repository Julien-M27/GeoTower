package fr.geotower.wear.data.preferences

import android.content.Context
import android.content.SharedPreferences
import fr.geotower.wear.data.model.DataSourceMode
import fr.geotower.wear.data.model.GpsRefreshInterval
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WearPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("geotower_wear_prefs", Context.MODE_PRIVATE)

    private val _dataSourceMode = MutableStateFlow(loadDataSourceMode())
    val dataSourceMode: StateFlow<DataSourceMode> = _dataSourceMode.asStateFlow()

    private val _gpsRefreshInterval = MutableStateFlow(loadGpsRefreshInterval())
    val gpsRefreshInterval: StateFlow<GpsRefreshInterval> = _gpsRefreshInterval.asStateFlow()

    private fun loadDataSourceMode(): DataSourceMode {
        val name = prefs.getString(KEY_DATA_SOURCE_MODE, DataSourceMode.AUTO.name)
        return try {
            DataSourceMode.valueOf(name ?: DataSourceMode.AUTO.name)
        } catch (e: Exception) {
            DataSourceMode.AUTO
        }
    }

    fun setDataSourceMode(mode: DataSourceMode) {
        prefs.edit().putString(KEY_DATA_SOURCE_MODE, mode.name).apply()
        _dataSourceMode.value = mode
    }

    private fun loadGpsRefreshInterval(): GpsRefreshInterval {
        val name = prefs.getString(KEY_GPS_REFRESH_INTERVAL, GpsRefreshInterval.CONTINUOUS.name)
        return try {
            GpsRefreshInterval.valueOf(name ?: GpsRefreshInterval.CONTINUOUS.name)
        } catch (e: Exception) {
            GpsRefreshInterval.CONTINUOUS
        }
    }

    fun setGpsRefreshInterval(interval: GpsRefreshInterval) {
        prefs.edit().putString(KEY_GPS_REFRESH_INTERVAL, interval.name).apply()
        _gpsRefreshInterval.value = interval
    }

    companion object {
        private const val KEY_DATA_SOURCE_MODE = "pref_data_source_mode"
        private const val KEY_GPS_REFRESH_INTERVAL = "pref_gps_refresh_interval"
    }
}
