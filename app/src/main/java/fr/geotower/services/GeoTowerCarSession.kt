package fr.geotower.services

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import fr.geotower.GeoTowerApp
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.ui.screens.car.CarErrorScreen
import fr.geotower.ui.screens.car.CarNearbySitesScreen
import fr.geotower.ui.screens.car.CarUnavailableScreen
import fr.geotower.utils.AppFileLog
import fr.geotower.utils.AppLogger

class GeoTowerCarSession : Session() {
    /**
     * La racine est directement la liste des sites proches.
     *
     * Elle passait auparavant par un écran d'accueil qui n'affichait qu'une phrase et un bouton :
     * sur un écran de voiture (1024x768 et plus), cela laissait la quasi-totalité de la surface
     * vide et coûtait un appui avant d'atteindre la seule fonction embarquée. Un conducteur ouvre
     * GeoTower pour voir ce qui l'entoure — autant le lui montrer.
     */
    override fun onCreateScreen(intent: Intent): Screen {
        AppLogger.i(TAG, "Creating car root screen")
        AppFileLog.i(TAG, "Création de l'écran racine (action=${intent.action ?: "-"})")
        return try {
            if (!RemoteFeatureFlags.isPlatformEnabled(RemoteFeatureFlags.Platform.ANDROID_AUTO)) {
                AppFileLog.i(TAG, "Plateforme voiture désactivée par le kill-switch distant")
                return CarUnavailableScreen(carContext)
            }
            val repository = (carContext.applicationContext as GeoTowerApp).repository
            CarNearbySitesScreen(carContext, repository)
        } catch (error: Throwable) {
            AppFileLog.e(TAG, "Echec de la création de l'écran racine", error)
            CarErrorScreen(carContext, "onCreateScreen", error)
        }
    }

    private companion object {
        private const val TAG = "GeoTowerCar"
    }
}
