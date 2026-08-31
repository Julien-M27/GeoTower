package fr.geotower.ui.screens.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import fr.geotower.R
import fr.geotower.data.AnfrRepository

/** Menu d'accueil Android Auto, volontairement limité aux deux usages utiles en voiture. */
class CarHomeScreen(
    carContext: CarContext,
    private val repository: AnfrRepository
) : Screen(carContext) {

    override fun onGetTemplate(): Template = carTemplateOrError(carContext, "CarHomeScreen") {
        val screenManager = carContext.getCarService(ScreenManager::class.java)
        val items = ItemList.Builder()
            .addItem(
                GridItem.Builder()
                    .setTitle(carContext.getString(R.string.car_menu_map))
                    .setText(carContext.getString(R.string.car_menu_map_description))
                    .setImage(CarIcon.APP_ICON, GridItem.IMAGE_TYPE_ICON)
                    .setOnClickListener {
                        screenManager.push(CarAntennaMapScreen(carContext, repository))
                    }
                    .build()
            )
            .addItem(
                GridItem.Builder()
                    .setTitle(carContext.getString(R.string.car_menu_nearby))
                    .setText(carContext.getString(R.string.car_menu_nearby_description))
                    .setImage(CarIcon.APP_ICON, GridItem.IMAGE_TYPE_ICON)
                    .setOnClickListener {
                        screenManager.push(CarNearbySitesScreen(carContext, repository))
                    }
                    .build()
            )
            .build()

        GridTemplate.Builder()
            .setTitle(carContext.getString(R.string.car_menu_title))
            .setHeaderAction(carHeaderAction())
            .setSingleList(items)
            .build()
    }
}
