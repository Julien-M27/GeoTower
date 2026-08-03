package fr.geotower.ui.screens.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Template

/**
 * Écran de repli quand la construction du vrai écran échoue. Sans lui, l'hôte affiche son bandeau
 * générique et l'erreur est perdue ; ici elle reste lisible dans la voiture et dans [fr.geotower.utils.AppFileLog].
 */
class CarErrorScreen(
    carContext: CarContext,
    private val where: String,
    private val error: Throwable
) : Screen(carContext) {

    override fun onGetTemplate(): Template = carErrorTemplate(carContext, where, error)
}
