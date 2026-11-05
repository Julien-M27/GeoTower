package fr.geotower.wear.data.preferences

import android.content.Context
import android.content.SharedPreferences
import fr.geotower.wear.data.model.DataSourceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WearPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("geotower_wear_prefs", Context.MODE_PRIVATE)

    private val _dataSourceMode = MutableStateFlow(loadDataSourceMode())
    val dataSourceMode: StateFlow<DataSourceMode> = _dataSourceMode.asStateFlow()

    private fun loadDataSourceMode(): DataSourceMode {
        val name = prefs.getString(KEY_DATA_SOURCE_MODE, DataSourceMode.PHONE_LOCAL.name)
        return try {
            DataSourceMode.valueOf(name ?: DataSourceMode.PHONE_LOCAL.name)
        } catch (e: Exception) {
            DataSourceMode.PHONE_LOCAL
        }
    }

    fun setDataSourceMode(mode: DataSourceMode) {
        prefs.edit().putString(KEY_DATA_SOURCE_MODE, mode.name).apply()
        _dataSourceMode.value = mode
    }

    companion object {
        private const val KEY_DATA_SOURCE_MODE = "pref_data_source_mode"
    }
}
