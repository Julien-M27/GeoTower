package fr.geotower.services

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import fr.geotower.BuildConfig
import fr.geotower.utils.AppFileLog
import fr.geotower.utils.AppLogger

class GeoTowerCarAppService : CarAppService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        // L'USB étant pris par la voiture, logcat n'est pas branchable : tout le chemin Auto est
        // doublé dans le journal disque, relisible depuis la page Diagnostic.
        AppFileLog.init(applicationContext)
        AppFileLog.i(TAG, "Service Android Auto créé")
        AppLogger.i(TAG, "Android Auto service created")
        logHostPackages()
        startConnectionProbe()
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
        mainHandler.removeCallbacksAndMessages(null)
        AppFileLog.i(TAG, "Service Android Auto détruit")
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

    /**
     * Le service est créé puis détruit ~5 s plus tard sans que la poignée de main aboutisse. Deux
     * causes possibles et une seule trace : soit le thread principal est saturé et l'app ne répond
     * pas à temps à `getAppInfo`, soit l'hôte ne nous adresse jamais la parole (refus de politique).
     * Ces battements les départagent : s'ils tombent à l'heure et que `hostInfo` reste vide, c'est
     * l'hôte qui refuse, pas nous qui sommes lents.
     */
    private fun startConnectionProbe() {
        val startedAt = SystemClock.elapsedRealtime()
        mainHandler.post {
            AppFileLog.i(TAG, "Thread principal disponible après ${SystemClock.elapsedRealtime() - startedAt} ms")
        }
        repeat(PROBE_TICKS) { index ->
            mainHandler.postDelayed({
                val host = runCatching { hostInfo?.packageName }.getOrNull()
                AppFileLog.i(
                    TAG,
                    "Sonde ${index + 1}/$PROBE_TICKS à ${SystemClock.elapsedRealtime() - startedAt} ms " +
                        "· hôte connecté = ${host ?: "aucun"}"
                )
            }, (index + 1) * PROBE_INTERVAL_MS)
        }
    }

    private companion object {
        private const val TAG = "GeoTowerCar"
        private const val PROBE_TICKS = 12
        private const val PROBE_INTERVAL_MS = 500L
        private val HOST_PACKAGES = listOf("com.google.android.projection.gearhead")
    }
}
