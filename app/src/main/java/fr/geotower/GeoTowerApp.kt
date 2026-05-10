package fr.geotower

import android.app.Application
import androidx.preference.PreferenceManager
import fr.geotower.data.AnfrRepository
import fr.geotower.data.api.RetrofitClient
import fr.geotower.data.upload.SignalQuestUploadQueue
import org.osmdroid.config.Configuration

class GeoTowerApp : Application() {

    val repository by lazy {
        AnfrRepository(
            api = RetrofitClient.apiService,
            context = applicationContext
        )
    }

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        Configuration.getInstance().userAgentValue = packageName
        SignalQuestUploadQueue.cleanupStaleFiles(applicationContext)
    }
}
