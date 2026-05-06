package fr.geotower.ui.screens.car

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import fr.geotower.data.AnfrRepository
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.utils.LocationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CarNearbySitesScreen(
    carContext: CarContext,
    private val repository: AnfrRepository
) : Screen(carContext) {

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var state: NearbySitesState = NearbySitesState.Loading

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                screenScope.cancel()
            }
        })
        loadNearbySites()
    }

    override fun onGetTemplate(): Template {
        return when (val currentState = state) {
            NearbySitesState.Loading -> loadingTemplate()
            NearbySitesState.MissingLocationPermission -> missingPermissionTemplate()
            NearbySitesState.Empty -> messageTemplate(
                title = "Sites proches",
                message = "Aucun site trouve autour de votre position.",
                actionTitle = "Reessayer",
                action = ::loadNearbySites
            )
            is NearbySitesState.Error -> messageTemplate(
                title = "Sites proches",
                message = currentState.message,
                actionTitle = "Reessayer",
                action = ::loadNearbySites
            )
            is NearbySitesState.Loaded -> loadedTemplate(currentState.sites)
        }
    }

    private fun loadNearbySites() {
        state = NearbySitesState.Loading
        invalidate()

        screenScope.launch {
            if (!hasLocationPermission()) {
                state = NearbySitesState.MissingLocationPermission
                invalidate()
                return@launch
            }

            val location = getCarLocation()
            if (location == null) {
                state = NearbySitesState.Error("Position indisponible pour le moment.")
                invalidate()
                return@launch
            }

            val sites = withContext(Dispatchers.IO) {
                val antennas = repository.getNearest100(location.latitude, location.longitude)
                antennas.toCarSiteListItems(location).take(25)
            }

            state = if (sites.isEmpty()) NearbySitesState.Empty else NearbySitesState.Loaded(sites)
            invalidate()
        }
    }

    private fun loadedTemplate(sites: List<CarSiteListItem>): Template {
        val screenManager = carContext.getCarService(ScreenManager::class.java)
        val itemListBuilder = ItemList.Builder()

        sites.forEach { site ->
            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle(site.title)
                    .addText("${formatCarDistance(site.distanceMeters)} - ${site.operators}")
                    .addText(site.subtitle)
                    .setOnClickListener {
                        screenManager.push(CarSiteDetailScreen(carContext, site))
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(itemListBuilder.build())
            .setTitle("Sites autour de moi")
            .setHeaderAction(Action.BACK)
            .build()
    }

    private fun loadingTemplate(): Template {
        return messageTemplate(
            title = "Sites autour de moi",
            message = "Recherche des sites proches...",
            actionTitle = null,
            action = null
        )
    }

    private fun missingPermissionTemplate(): Template {
        return MessageTemplate.Builder("Autorisez la localisation dans GeoTower sur le telephone.")
            .setTitle("Localisation requise")
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle("Ouvrir l'app")
                    .setOnClickListener {
                        carContext.getCarService(ScreenManager::class.java)
                            .push(CarPermissionScreen(carContext))
                    }
                    .build()
            )
            .build()
    }

    private fun messageTemplate(
        title: String,
        message: String,
        actionTitle: String?,
        action: (() -> Unit)?
    ): Template {
        val builder = MessageTemplate.Builder(message)
            .setTitle(title)
            .setHeaderAction(Action.BACK)

        if (actionTitle != null && action != null) {
            builder.addAction(
                Action.Builder()
                    .setTitle(actionTitle)
                    .setOnClickListener(action)
                    .build()
            )
        }

        return builder.build()
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(carContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(carContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCarLocation(): Location? {
        return LocationHelper(carContext).getCurrentLocation() ?: getLastKnownLocation()
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation(): Location? {
        val locationManager = carContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.getProviders(true)
            .mapNotNull { provider -> locationManager.getLastKnownLocation(provider) }
            .maxByOrNull { it.time }
    }

    private suspend fun List<LocalisationEntity>.toCarSiteListItems(location: Location): List<CarSiteListItem> {
        return groupBy {
            "${java.lang.String.format(java.util.Locale.US, "%.4f", it.latitude)}_${java.lang.String.format(java.util.Locale.US, "%.4f", it.longitude)}"
        }
            .map { (_, antennas) ->
                val main = antennas.first()
                val distance = calculateCarDistance(
                    location.latitude,
                    location.longitude,
                    main.latitude,
                    main.longitude
                )
                val technique = repository.getTechniqueDetails(main.idAnfr)
                val fullAddress = technique?.adresse?.takeIf { it.isNotBlank() } ?: "Site ANFR ${main.idAnfr}"
                val splitIndex = fullAddress.lastIndexOf(",")
                val title = if (splitIndex > 0) fullAddress.substring(0, splitIndex).trim() else fullAddress
                val subtitle = if (splitIndex > 0) fullAddress.substring(splitIndex + 1).trim() else "Site ANFR ${main.idAnfr}"
                val operators = antennas
                    .flatMap { it.operatorSummary().split(", ") }
                    .distinct()
                    .joinToString(", ")

                CarSiteListItem(
                    idAnfr = main.idAnfr,
                    title = title,
                    subtitle = subtitle,
                    operators = operators,
                    distanceMeters = distance,
                    latitude = main.latitude,
                    longitude = main.longitude
                )
            }
            .sortedBy { it.distanceMeters }
    }
}

private sealed interface NearbySitesState {
    data object Loading : NearbySitesState
    data object MissingLocationPermission : NearbySitesState
    data object Empty : NearbySitesState
    data class Error(val message: String) : NearbySitesState
    data class Loaded(val sites: List<CarSiteListItem>) : NearbySitesState
}
