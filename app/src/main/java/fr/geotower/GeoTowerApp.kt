package fr.geotower

import android.app.Application
import androidx.preference.PreferenceManager
import fr.geotower.data.AnfrRepository
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.api.RetrofitClient
import fr.geotower.data.db.AppDatabase
import fr.geotower.data.upload.SignalQuestUploadQueue
import fr.geotower.utils.PreferenceProfileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

class GeoTowerApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val repository by lazy {
        AnfrRepository(
            api = RetrofitClient.apiService,
            context = applicationContext
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Journal disque en premier : c'est le seul moyen de voir ce qui se passe là où logcat n'est
        // pas branchable (Android Auto), et le piège à exceptions doit couvrir tout le démarrage.
        fr.geotower.utils.AppFileLog.init(this)
        fr.geotower.utils.AppFileLog.installCrashHandler()
        RetrofitClient.init(applicationContext)
        // Choix principal / miroir : doit précéder la première requête, sinon un appareil réglé sur
        // le miroir repartirait sur le serveur principal le temps du démarrage.
        fr.geotower.data.api.ApiEndpoints.init(applicationContext)
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        Configuration.getInstance().userAgentValue = packageName
        RemoteFeatureFlags.loadCached(applicationContext)
        // Origine de l'installation : doit précéder AppUpdateState, qui s'en sert pour ne jamais
        // afficher de bandeau « nouvelle version » sur une installation venue du Play Store.
        fr.geotower.utils.AppDistribution.init(applicationContext)
        // Dernière version connue de l'app : le bandeau du tiroir doit être juste dès l'ouverture.
        fr.geotower.data.api.AppUpdateState.loadCached(applicationContext)
        PreferenceProfileManager.install(applicationContext)
        fr.geotower.utils.SystemPower.init(applicationContext)
        // Niveau faible conso chargé tôt (avant tout service/worker) → PowerProfile fiable même sans UI lancée.
        val ecoPrefs = getSharedPreferences(fr.geotower.utils.PreferenceStores.APP, MODE_PRIVATE)
        fr.geotower.utils.AppConfig.lowPowerLevel.intValue = ecoPrefs.getInt(fr.geotower.utils.AppConfig.PREF_LOW_POWER_LEVEL, 0)
        fr.geotower.utils.AppConfig.lowPowerFollowSystem.value = ecoPrefs.getBoolean(fr.geotower.utils.AppConfig.PREF_LOW_POWER_FOLLOW_SYSTEM, false)
        // Mode « traitement local » chargé tôt (avant tout worker) + éligibilité de génération DB.
        // La migration de l'ancien interrupteur « pannes en local » doit précéder la lecture.
        fr.geotower.utils.AppConfig.migrateLegacyOutageSourcePref(applicationContext)
        fr.geotower.utils.AppConfig.localModeLevel.intValue =
            ecoPrefs.getInt(fr.geotower.utils.AppConfig.PREF_LOCAL_MODE_LEVEL, 0).coerceIn(0, 3)
        fr.geotower.utils.AppConfig.localBuildEligible.value =
            fr.geotower.data.build.LocalBuildCapability.evaluate(applicationContext).eligible
        // Niveau 3 (« autonomie maximale ») : bloque les endpoints communautaires/MAJ/live du client partagé.
        RetrofitClient.communityEndpointBlocker = { fr.geotower.utils.AppConfig.blockCommunityAndUpdates() }
        appScope.launch {
            RemoteFeatureFlags.refreshIfNeeded(applicationContext, force = true)
        }
        appScope.launch(Dispatchers.IO) {
            // Pré-ouvre la base hors thread UI : crée au besoin les index de perf
            // (cf. GeoTowerDatabaseIndexes) et accélère la première requête carte.
            runCatching { AppDatabase.getDatabase(applicationContext) }
        }
        SignalQuestUploadQueue.cleanupStaleFiles(applicationContext)
    }
}
