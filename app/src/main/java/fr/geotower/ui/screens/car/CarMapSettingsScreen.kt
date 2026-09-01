package fr.geotower.ui.screens.car

import android.content.Context
import java.io.File
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import fr.geotower.R
import fr.geotower.utils.AppConfig
import fr.geotower.utils.PreferenceStores

private const val DEFAULT_MAP_SETTINGS_LIMIT = 5

/** Réglages de fond accessibles directement depuis l'interface Android Auto. */
class CarMapSettingsScreen(
    carContext: CarContext,
    private val onMapChanged: () -> Unit = {}
) : Screen(carContext) {

    override fun onGetTemplate(): Template = carTemplateOrError(carContext, "CarMapSettingsScreen") {
        val options = mapProviderOptions()
        val limit = runCatching {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        }.getOrElse { DEFAULT_MAP_SETTINGS_LIMIT }.coerceAtLeast(1)
        val currentProvider = AppConfig.mapProvider.intValue
        val items = ItemList.Builder()

        options.take(limit).forEach { option ->
            val isCurrent = option.value == currentProvider
            val description = buildString {
                append(carContext.getString(option.descriptionRes))
                if (isCurrent) {
                    append(" • ")
                    append(carContext.getString(R.string.car_map_settings_current))
                }
            }
            items.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(option.labelRes))
                    .addText(description)
                    .setOnClickListener { selectProvider(option.value) }
                    .build()
            )
        }

        ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.car_map_settings_title))
            .setHeaderAction(carHeaderAction())
            .setSingleList(items.build())
            .build()
    }

    private fun selectProvider(provider: Int) {
        val prefs = carContext.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
        AppConfig.mapProvider.intValue = provider
        prefs.edit().putInt("map_provider", provider).apply()

        // Le fond MapLibre/CARTO ne propose pas de satellite dans l'application téléphone. Le
        // même repli est appliqué ici pour éviter que le rendu voiture affiche un style incohérent.
        if (provider == 2 && AppConfig.ignStyle.intValue == 2) {
            AppConfig.ignStyle.intValue = 0
            prefs.edit().putInt("ign_style", 0).apply()
        }

        onMapChanged()
        runCatching {
            carContext.getCarService(ScreenManager::class.java).pop()
        }
    }

    private fun mapProviderOptions(): List<MapProviderOption> {
        return buildList {
            add(MapProviderOption(1, R.string.mapping_provider_osm, R.string.car_map_provider_osm))
            add(MapProviderOption(0, R.string.mapping_provider_ign, R.string.car_map_provider_ign))
            add(MapProviderOption(2, R.string.mapping_provider_maplibre, R.string.car_map_provider_maplibre))
            add(MapProviderOption(3, R.string.mapping_provider_topo, R.string.car_map_provider_topo))
            if (hasOfflineMaps()) {
                add(MapProviderOption(4, R.string.appstrings_offline_maps_title, R.string.car_map_provider_offline))
            }
        }
    }

    private fun hasOfflineMaps(): Boolean {
        val directory = File(carContext.getExternalFilesDir(null), "maps")
        return directory.listFiles { file -> file.extension == "map" && file.length() > 0L }
            ?.isNotEmpty() == true
    }
}

private data class MapProviderOption(
    val value: Int,
    val labelRes: Int,
    val descriptionRes: Int
)
