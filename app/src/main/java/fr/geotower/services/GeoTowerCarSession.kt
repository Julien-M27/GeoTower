package fr.geotower.services

import android.content.Intent
import android.util.Log
import androidx.car.app.Screen
import androidx.car.app.Session
import fr.geotower.GeoRadioApp
import fr.geotower.ui.screens.car.CarHomeScreen

class GeoTowerCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        Log.i("GeoTowerCar", "Creating Android Auto root screen")
        val repository = (carContext.applicationContext as GeoRadioApp).repository
        return CarHomeScreen(carContext, repository)
    }
}
