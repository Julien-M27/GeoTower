package fr.geotower.services

import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator

class GeoTowerCarAppService : CarAppService() {
    override fun onCreate() {
        super.onCreate()
        Log.i("GeoTowerCar", "Android Auto service created")
    }

    override fun createHostValidator(): HostValidator {
        Log.i("GeoTowerCar", "Creating Android Auto host validator")
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        Log.i("GeoTowerCar", "Creating Android Auto session for $sessionInfo")
        return GeoTowerCarSession()
    }

    override fun onCreateSession(): Session {
        Log.i("GeoTowerCar", "Creating Android Auto session")
        return GeoTowerCarSession()
    }
}
