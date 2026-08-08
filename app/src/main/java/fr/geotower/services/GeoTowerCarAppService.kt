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
        // L'USB étant pris par la voiture, logcat n'est pas branchable : tout le chemin voiture est
        // doublé dans le journal disque, relisible depuis la page Diagnostic.
        AppFileLog.init(applicationContext)
        AppFileLog.i(TAG, "Service voiture créé")
        AppLogger.i(TAG, "Car app service created")
        logHostPackages()
    }

    override fun createHostValidator(): HostValidator {
        AppLogger.i(TAG, "Creating car host validator")
        // Debug keeps local/test hosts flexible; release only accepts the Google car hosts —
        // la liste de car-app couvre gearhead (Android Auto) ET l'hôte de templates (AAOS).
        return if (BuildConfig.DEBUG) {
            AppFileLog.i(TAG, "Validateur d'hôte : tous les hôtes acceptés (build debug)")
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            AppFileLog.i(TAG, "Validateur d'hôte : hôtes voiture Google uniquement (build release)")
            HostValidator.Builder(this)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        AppLogger.i(TAG, "Creating car session for $sessionInfo")
        AppFileLog.i(TAG, "Création de la session voiture ($sessionInfo)")
        return GeoTowerCarSession()
    }

    override fun onCreateSession(): Session {
        AppLogger.i(TAG, "Creating car session")
        AppFileLog.i(TAG, "Création de la session voiture (sans SessionInfo)")
        return GeoTowerCarSession()
    }

    override fun onDestroy() {
        AppFileLog.i(TAG, "Service voiture détruit")
        super.onDestroy()
    }

    /** Version de l'hôte présent sur l'appareil : certaines régressions sont propres à une version. */
    private fun logHostPackages() {
        HOST_PACKAGES.forEach { packageName ->
            val version = runCatching {
                packageManager.getPackageInfo(packageName, 0).versionName
            }.getOrNull()
            AppFileLog.i(TAG, "Hôte $packageName : ${version ?: "introuvable ou masqué"}")
        }
    }

    private companion object {
        private const val TAG = "GeoTowerCar"
        private val HOST_PACKAGES = listOf(
            // Android Auto (projection depuis le téléphone).
            "com.google.android.projection.gearhead",
            // Android Automotive OS (l'app tourne dans le véhicule).
            "com.google.android.apps.automotive.templates.host"
        )
    }
}
