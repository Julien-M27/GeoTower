package fr.geotower.services

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import fr.geotower.BuildConfig
import fr.geotower.utils.AppFileLog
import fr.geotower.utils.AppLogger

class GeoTowerCarAppService : CarAppService() {
    override fun onCreate() {
        super.onCreate()
        // L'USB étant pris par la voiture, logcat n'est pas branchable : tout le chemin Auto est
        // doublé dans le journal disque, relisible depuis la page Diagnostic.
        AppFileLog.init(applicationContext)
        AppFileLog.i(TAG, "Service Android Auto créé")
        AppLogger.i(TAG, "Android Auto service created")
    }

    override fun createHostValidator(): HostValidator {
        AppLogger.i(TAG, "Creating Android Auto host validator")
        // Debug keeps local/test hosts flexible; release only accepts Android Auto hosts.
        return if (BuildConfig.DEBUG) {
            AppFileLog.i(TAG, "Validateur d'hôte : tous les hôtes acceptés (build debug)")
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            AppFileLog.i(TAG, "Validateur d'hôte : liste Android Auto uniquement (build release)")
            HostValidator.Builder(this)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        AppLogger.i(TAG, "Creating Android Auto session for $sessionInfo")
        AppFileLog.i(TAG, "Création de la session Android Auto ($sessionInfo)")
        return GeoTowerCarSession()
    }

    override fun onCreateSession(): Session {
        AppLogger.i(TAG, "Creating Android Auto session")
        AppFileLog.i(TAG, "Création de la session Android Auto (sans SessionInfo)")
        return GeoTowerCarSession()
    }

    override fun onDestroy() {
        AppFileLog.i(TAG, "Service Android Auto détruit")
        super.onDestroy()
    }

    private companion object {
        private const val TAG = "GeoTowerCar"
    }
}
