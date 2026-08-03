package fr.geotower.ui.screens.car

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import fr.geotower.MainActivity
import fr.geotower.R
import fr.geotower.utils.AppFileLog

class CarPermissionScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = carTemplateOrError(carContext, "CarPermissionScreen") {
        MessageTemplate.Builder(
            carContext.getString(R.string.car_permission_explanation)
        )
            .setTitle(carContext.getString(R.string.car_location_required))
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.car_open_app))
                    .setOnClickListener { openPhoneApp() }
                    .build()
            )
            .build()
    }

    private fun openPhoneApp() {
        val intent = Intent(carContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        // Démarrer une Activity téléphone depuis la voiture est refusé selon l'état de conduite :
        // on trace le refus au lieu de laisser l'exception atteindre l'hôte.
        runCatching { carContext.startActivity(intent) }
            .onFailure { AppFileLog.e(CAR_LOG_TAG, "Echec de l'ouverture de l'app sur le téléphone", it) }
    }
}
