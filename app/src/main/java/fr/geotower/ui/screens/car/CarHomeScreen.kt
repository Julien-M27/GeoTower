package fr.geotower.ui.screens.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import fr.geotower.data.AnfrRepository
import fr.geotower.utils.AppLogger
import fr.geotower.utils.AppStrings

class CarHomeScreen(
    carContext: CarContext,
    private val repository: AnfrRepository
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        AppLogger.i(TAG, "Rendering Android Auto home template")
        val screenManager = carContext.getCarService(ScreenManager::class.java)
        return MessageTemplate.Builder(AppStrings.carConnected(carContext))
            .setTitle("GeoTower")
            .setHeaderAction(Action.APP_ICON)
            .addAction(
                Action.Builder()
                    .setTitle(AppStrings.carNearbySites(carContext))
                    .setOnClickListener {
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
