package fr.geotower.services

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import fr.geotower.utils.AppLogger

class GeoTowerCarAppService : CarAppService() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.i(TAG, "Android Auto service created")
    }

    override fun createHostValidator(): HostValidator {
        AppLogger.i(TAG, "Creating Android Auto host validator")
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        AppLogger.i(TAG, "Creating Android Auto session for $sessionInfo")
        return GeoTowerCarSession()
    }

    override fun onCreateSession(): Session {
        AppLogger.i(TAG, "Creating Android Auto session")
        return GeoTowerCarSession()
    }

    private companion object {
        private const val TAG = "GeoTowerCar"
    }
}
