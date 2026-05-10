package fr.geotower.services

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import fr.geotower.GeoTowerApp
import fr.geotower.ui.screens.car.CarHomeScreen
import fr.geotower.utils.AppLogger

class GeoTowerCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        AppLogger.i(TAG, "Creating Android Auto root screen")
        val repository = (carContext.applicationContext as GeoTowerApp).repository
        return CarHomeScreen(carContext, repository)
    }

    private companion object {
        private const val TAG = "GeoTowerCar"
    }
}
