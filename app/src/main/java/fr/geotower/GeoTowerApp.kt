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
