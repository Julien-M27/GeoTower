package fr.geotower.ui.screens.car

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import fr.geotower.MainActivity
import fr.geotower.utils.AppStrings

class CarPermissionScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder(
            AppStrings.carPermissionExplanation(carContext)
        )
            .setTitle(AppStrings.carLocationRequired(carContext))
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle(AppStrings.carOpenApp(carContext))
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
