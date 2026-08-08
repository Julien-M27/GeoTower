package fr.geotower.ui.screens.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import fr.geotower.R

/**
 * Racine de repli quand le kill-switch distant coupe la plateforme voiture. Sans elle, couper le
 * drapeau laisserait l'hôte sur son écran d'erreur générique, sans rien dire à l'utilisateur.
 */
class CarUnavailableScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template = carTemplateOrError(carContext, "CarUnavailableScreen") {
        MessageTemplate.Builder(carContext.getString(R.string.appstrings_unavailable))
            .setTitle(carContext.getString(R.string.app_name))
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
