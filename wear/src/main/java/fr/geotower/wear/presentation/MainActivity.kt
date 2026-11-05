package fr.geotower.wear.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import fr.geotower.wear.data.location.WatchGpsEngine
import fr.geotower.wear.data.repository.WearTowerRepository

class MainActivity : ComponentActivity() {

    private lateinit var gpsEngine: WatchGpsEngine
    private lateinit var repository: WearTowerRepository

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            gpsEngine.startListening()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gpsEngine = WatchGpsEngine(this)
        repository = WearTowerRepository(this)

        checkAndRequestLocationPermissions()

        setContent {
            WearApp(
                gpsEngine = gpsEngine,
                repository = repository
            )
        }
    }

    private fun checkAndRequestLocationPermissions() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
}
