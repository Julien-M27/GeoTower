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
        RetrofitClient.init(applicationContext)
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        Configuration.getInstance().userAgentValue = packageName
        RemoteFeatureFlags.loadCached(applicationContext)
        PreferenceProfileManager.install(applicationContext)
        fr.geotower.utils.SystemPower.init(applicationContext)
        // Niveau faible conso chargé tôt (avant tout service/worker) → PowerProfile fiable même sans UI lancée.
        val ecoPrefs = getSharedPreferences(fr.geotower.utils.PreferenceStores.APP, MODE_PRIVATE)
        fr.geotower.utils.AppConfig.lowPowerLevel.intValue = ecoPrefs.getInt(fr.geotower.utils.AppConfig.PREF_LOW_POWER_LEVEL, 0)
        fr.geotower.utils.AppConfig.lowPowerFollowSystem.value = ecoPrefs.getBoolean(fr.geotower.utils.AppConfig.PREF_LOW_POWER_FOLLOW_SYSTEM, false)
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
