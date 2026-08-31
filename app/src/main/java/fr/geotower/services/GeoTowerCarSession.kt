package fr.geotower.services

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import fr.geotower.GeoTowerApp
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.ui.screens.car.CarErrorScreen
import fr.geotower.ui.screens.car.CarHomeScreen
import fr.geotower.ui.screens.car.CarUnavailableScreen
import fr.geotower.utils.AppFileLog
import fr.geotower.utils.AppLogger

class GeoTowerCarSession : Session() {
    /** L'accueil voiture propose les deux parcours adaptés à Android Auto. */
    override fun onCreateScreen(intent: Intent): Screen {
        AppLogger.i(TAG, "Creating car root screen")
        AppFileLog.i(TAG, "Création de l'écran racine (action=${intent.action ?: "-"})")
        return try {
            if (!RemoteFeatureFlags.isPlatformEnabled(RemoteFeatureFlags.Platform.ANDROID_AUTO)) {
                AppFileLog.i(TAG, "Plateforme voiture désactivée par le kill-switch distant")
                return CarUnavailableScreen(carContext)
            }
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
