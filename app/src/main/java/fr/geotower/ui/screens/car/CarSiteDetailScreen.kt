package fr.geotower.ui.screens.car

import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import fr.geotower.R
import fr.geotower.utils.AppFileLog
import java.util.Locale

class CarSiteDetailScreen(
    carContext: CarContext,
    private val site: CarSiteListItem
) : Screen(carContext) {

    override fun onGetTemplate(): Template = carTemplateOrError(carContext, "CarSiteDetailScreen") {
        carLog("Rendu de la fiche site ${site.idAnfr}")
        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle(carContext.getString(R.string.car_operators)).addText(site.operators).build())
            .addRow(Row.Builder().setTitle(carContext.getString(R.string.car_distance)).addText(formatCarDistance(site.distanceMeters)).build())
            .addRow(Row.Builder().setTitle(carContext.getString(R.string.car_address)).addText(site.title).addText(site.subtitle).build())
            .addRow(Row.Builder().setTitle(carContext.getString(R.string.car_coordinates)).addText(formatCoordinates()).build())
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.car_site_more_details))
                    .setOnClickListener {
                        carContext.getCarService(ScreenManager::class.java)
                            .push(CarSiteTechnicalDetailScreen(carContext, site))
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.car_navigate))
                    .setOnClickListener { startNavigation() }
                    .build()
            )
            .build()

        PaneTemplate.Builder(pane)
            .setTitle(carContext.getString(R.string.site_anfr_title, site.idAnfr))
            .setHeaderAction(Action.BACK)
            .build()
    }

    private fun startNavigation() {
        val label = Uri.encode("GeoTower ${site.idAnfr}")
        val uri = Uri.parse("geo:${site.latitude},${site.longitude}?q=${site.latitude},${site.longitude}($label)")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        // L'hôte refuse l'appel s'il n'a aucune app de navigation à qui le passer : sans garde,
        // l'exception remonte au host et on retombe sur son écran d'erreur générique.
        runCatching { carContext.startCarApp(intent) }
            .onFailure { AppFileLog.e(CAR_LOG_TAG, "Echec du lancement de la navigation vers ${site.idAnfr}", it) }
    }

    private fun formatCoordinates(): String {
        return String.format(Locale.US, "%.5f, %.5f", site.latitude, site.longitude)
    }
}
