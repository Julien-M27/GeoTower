package fr.geotower.ui.screens.car

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import fr.geotower.data.AnfrRepository

class CarHomeScreen(
    carContext: CarContext,
    private val repository: AnfrRepository
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        Log.i("GeoTowerCar", "Rendering Android Auto home template")
        val screenManager = carContext.getCarService(ScreenManager::class.java)
        return MessageTemplate.Builder("GeoTower est connecte a Android Auto.")
            .setTitle("GeoTower")
            .setHeaderAction(Action.APP_ICON)
            .addAction(
                Action.Builder()
                    .setTitle("Sites proches")
                    .setOnClickListener {
                        screenManager.push(CarNearbySitesScreen(carContext, repository))
                    }
                    .build()
            )
            .build()
    }
}
