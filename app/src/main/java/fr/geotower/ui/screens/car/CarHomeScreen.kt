package fr.geotower.ui.screens.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.utils.AppLogger

class CarHomeScreen(
    carContext: CarContext,
    private val repository: AnfrRepository
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        AppLogger.i(TAG, "Rendering Android Auto home template")
        val screenManager = carContext.getCarService(ScreenManager::class.java)
        return MessageTemplate.Builder(carContext.getString(R.string.car_connected))
            .setTitle(carContext.getString(R.string.app_name))
            .setHeaderAction(Action.APP_ICON)
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.car_nearby_sites))
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
