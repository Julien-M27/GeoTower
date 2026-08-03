package fr.geotower.ui.screens.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.utils.AppLogger

class CarHomeScreen(
    carContext: CarContext,
    private val repository: AnfrRepository
) : Screen(carContext) {

    override fun onGetTemplate(): Template = carTemplateOrError(carContext, "CarHomeScreen") {
        AppLogger.i(TAG, "Rendering Android Auto home template")
        carLog("Rendu du template d'accueil")
        if (!RemoteFeatureFlags.isPlatformEnabled(RemoteFeatureFlags.Platform.ANDROID_AUTO)) {
            carLog("Android Auto désactivé par le kill-switch distant")
            return@carTemplateOrError MessageTemplate.Builder(carContext.getString(R.string.appstrings_unavailable))
                .setTitle(carContext.getString(R.string.app_name))
                .setHeaderAction(Action.APP_ICON)
                .build()
        }
        val screenManager = carContext.getCarService(ScreenManager::class.java)
        MessageTemplate.Builder(carContext.getString(R.string.car_connected))
            .setTitle(carContext.getString(R.string.app_name))
            .setHeaderAction(Action.APP_ICON)
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.car_nearby_sites))
                    .setOnClickListener {
                        carLog("Ouverture de l'écran des sites proches")
                        screenManager.push(CarNearbySitesScreen(carContext, repository))
                    }
                    .build()
            )
            .build()
    }

    private companion object {
        private const val TAG = "GeoTowerCar"
    }
}
