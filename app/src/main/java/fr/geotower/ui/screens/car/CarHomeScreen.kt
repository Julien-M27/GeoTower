package fr.geotower.ui.screens.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
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
                    .setTitle(carContext.getString(R.string.car_sites_around_me))
                    .setText(carContext.getString(R.string.car_menu_nearby_description))
                    .setImage(carMenuIcon(carContext, R.drawable.ic_place), GridItem.IMAGE_TYPE_ICON)
                    .setOnClickListener {
                        screenManager.push(CarNearbySitesScreen(carContext, repository))
                    }
                    .build()
            )
            .addItem(
                GridItem.Builder()
                    .setTitle(carContext.getString(R.string.car_menu_map))
                    .setText(carContext.getString(R.string.car_menu_map_description))
                    .setImage(carMenuIcon(carContext, R.drawable.ic_car_map), GridItem.IMAGE_TYPE_ICON)
                    .setOnClickListener {
                        screenManager.push(CarAntennaMapScreen(carContext, repository))
                    }
                    .build()
            )
            .addItem(
                GridItem.Builder()
                    .setTitle(carContext.getString(R.string.car_menu_settings))
                    .setText(carContext.getString(R.string.car_menu_settings_description))
                    .setImage(carMenuIcon(carContext, R.drawable.ic_car_map_layers), GridItem.IMAGE_TYPE_ICON)
                    .setOnClickListener {
                        screenManager.push(CarMapSettingsScreen(carContext))
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

private fun carMenuIcon(carContext: CarContext, drawableRes: Int): CarIcon {
    return CarIcon.Builder(IconCompat.createWithResource(carContext, drawableRes))
        .setTint(CarColor.PRIMARY)
        .build()
}
