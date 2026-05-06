package fr.geotower.ui.screens.car

import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import java.util.Locale

class CarSiteDetailScreen(
    carContext: CarContext,
    private val site: CarSiteListItem
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle("Operateurs").addText(site.operators).build())
            .addRow(Row.Builder().setTitle("Distance").addText(formatCarDistance(site.distanceMeters)).build())
            .addRow(Row.Builder().setTitle("Adresse").addText(site.title).addText(site.subtitle).build())
            .addRow(Row.Builder().setTitle("Coordonnees").addText(formatCoordinates()).build())
            .addAction(
                Action.Builder()
                    .setTitle("Naviguer")
                    .setOnClickListener { startNavigation() }
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle("Site ANFR ${site.idAnfr}")
            .setHeaderAction(Action.BACK)
            .build()
    }

    private fun startNavigation() {
        val label = Uri.encode("GeoTower ${site.idAnfr}")
        val uri = Uri.parse("geo:${site.latitude},${site.longitude}?q=${site.latitude},${site.longitude}($label)")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        carContext.startCarApp(intent)
    }

    private fun formatCoordinates(): String {
        return String.format(Locale.US, "%.5f, %.5f", site.latitude, site.longitude)
    }
}
