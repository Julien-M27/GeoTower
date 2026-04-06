package fr.geotower

import android.app.Application
import androidx.preference.PreferenceManager
import fr.geotower.data.AnfrRepository
import fr.geotower.data.api.RetrofitClient
import org.osmdroid.config.Configuration

class GeoRadioApp : Application() {

    // 🗑️ Plus besoin de déclarer la "database" ici, le Repository s'en occupe tout seul !

    val repository by lazy {
        AnfrRepository(
            api = RetrofitClient.apiService,
            context = applicationContext // ✅ CORRECTION : on passe le context au lieu du dao
        )
    }

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        Configuration.getInstance().userAgentValue = packageName
    }
}