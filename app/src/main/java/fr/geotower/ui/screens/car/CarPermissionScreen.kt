package fr.geotower.ui.screens.car

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import fr.geotower.MainActivity

class CarPermissionScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder(
            "GeoTower a besoin de la localisation pour afficher les sites autour de vous."
        )
            .setTitle("Localisation requise")
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle("Ouvrir l'app")
                    .setOnClickListener { openPhoneApp() }
                    .build()
            )
            .build()
    }

    private fun openPhoneApp() {
        val intent = Intent(carContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        carContext.startActivity(intent)
    }
}
