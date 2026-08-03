package fr.geotower.services

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import fr.geotower.GeoTowerApp
import fr.geotower.ui.screens.car.CarErrorScreen
import fr.geotower.ui.screens.car.CarHomeScreen
import fr.geotower.utils.AppFileLog
import fr.geotower.utils.AppLogger

class GeoTowerCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        AppLogger.i(TAG, "Creating Android Auto root screen")
        AppFileLog.i(TAG, "Création de l'écran racine (action=${intent.action ?: "-"})")
        return try {
            val repository = (carContext.applicationContext as GeoTowerApp).repository
            CarHomeScreen(carContext, repository)
        } catch (error: Throwable) {
            AppFileLog.e(TAG, "Echec de la création de l'écran racine", error)
            CarErrorScreen(carContext, "onCreateScreen", error)
        }
    }

    private companion object {
        private const val TAG = "GeoTowerCar"
    }
}
