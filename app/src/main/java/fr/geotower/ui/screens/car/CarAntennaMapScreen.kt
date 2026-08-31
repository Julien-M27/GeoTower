package fr.geotower.ui.screens.car

import android.text.Spannable
import android.text.SpannableString
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarLocation
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.Metadata
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.utils.AppFileLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val DEFAULT_MAP_PLACE_LIMIT = 6

/** Carte Android Auto : le host dessine le fond et les marqueurs à partir des coordonnées ANFR. */
class CarAntennaMapScreen(
    carContext: CarContext,
    private val repository: AnfrRepository
) : Screen(carContext) {

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sitesLoader = CarNearbySitesLoader(carContext, repository)
    private var state: CarSitesLoadResult = CarSitesLoadResult.Loading

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (state == CarSitesLoadResult.MissingLocationPermission && hasCarLocationPermission(carContext)) {
                    loadSites()
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                screenScope.cancel()
            }
        })
        loadSites()
    }

    override fun onGetTemplate(): Template = carTemplateOrError(carContext, "CarAntennaMapScreen") {
        when (val currentState = state) {
            CarSitesLoadResult.Loading -> loadingTemplate()
            is CarSitesLoadResult.Loaded -> {
                if (currentState.sites.isEmpty()) {
                    messageTemplate(
                        title = carContext.getString(R.string.car_map_title),
                        message = carContext.getString(R.string.car_no_sites_nearby),
                        actionTitle = carContext.getString(R.string.common_try_again),
                        action = ::loadSites
                    )
                } else {
                    loadedTemplate(currentState.sites)
                }
            }
            CarSitesLoadResult.MissingLocationPermission -> missingPermissionTemplate()
            CarSitesLoadResult.Empty -> messageTemplate(
                title = carContext.getString(R.string.car_map_title),
                message = carContext.getString(R.string.car_no_sites_nearby),
                actionTitle = carContext.getString(R.string.common_try_again),
                action = ::loadSites
            )
            CarSitesLoadResult.DatabaseMissing -> messageTemplate(
                title = carContext.getString(R.string.car_map_title),
                message = carContext.getString(R.string.car_database_missing),
                actionTitle = carContext.getString(R.string.common_try_again),
                action = ::loadSites
            )
            is CarSitesLoadResult.Error -> messageTemplate(
                title = carContext.getString(R.string.car_map_title),
                message = currentState.message,
                actionTitle = carContext.getString(R.string.common_try_again),
                action = ::loadSites
            )
        }
    }

    private fun loadSites() {
        state = CarSitesLoadResult.Loading
        invalidate()

        screenScope.launch {
            try {
                state = sitesLoader.load()
                invalidate()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AppFileLog.e(CAR_LOG_TAG, "Echec du chargement de la carte voiture", error)
                state = CarSitesLoadResult.Error(
                    "${error.javaClass.simpleName} : ${error.message ?: "-"}".take(200)
                )
                invalidate()
            }
        }
    }

    private fun loadedTemplate(sites: List<CarSiteListItem>): Template {
        val screenManager = carContext.getCarService(ScreenManager::class.java)
        val hostLimit = runCatching {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST)
        }.getOrElse { DEFAULT_MAP_PLACE_LIMIT }.coerceAtLeast(1)
        val shownSites = sites.take(hostLimit)
        carLog("Carte : ${sites.size} site(s) trouvé(s), ${shownSites.size} marqueur(s) affiché(s)")

        val items = ItemList.Builder()
        shownSites.forEach { site ->
            val place = Place.Builder(CarLocation.create(site.latitude, site.longitude))
                .setMarker(PlaceMarker.Builder().setLabel("A").build())
                .build()
            val row = Row.Builder()
                .setTitle(titleWithDistance(site))
                .setMetadata(Metadata.Builder().setPlace(place).build())
                .setOnClickListener {
                    screenManager.push(CarSiteDetailScreen(carContext, site))
                }
            listOf(site.operators, site.subtitle)
                .filter { it.isNotBlank() }
                .take(2)
                .forEach(row::addText)
            items.addItem(row.build())
        }

        val mapSwitch = Action.Builder()
            .setTitle(carContext.getString(R.string.car_menu_nearby))
            .setOnClickListener {
                screenManager.popToRoot()
                screenManager.push(CarNearbySitesScreen(carContext, repository))
            }
            .build()

        return PlaceListMapTemplate.Builder()
            .setTitle(carContext.getString(R.string.car_map_title))
            .setHeaderAction(carHeaderAction())
            .setCurrentLocationEnabled(true)
            .setItemList(items.build())
            .setActionStrip(ActionStrip.Builder().addAction(mapSwitch).build())
            .build()
    }

    private fun titleWithDistance(site: CarSiteListItem): CharSequence {
        val title = SpannableString(" ${site.title}")
        title.setSpan(
            DistanceSpan.create(distanceForTemplate(site.distanceMeters)),
            0,
            1,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )
        return title
    }

    private fun distanceForTemplate(distanceMeters: Float): Distance {
        return if (distanceMeters >= 1_000f) {
            Distance.create(distanceMeters / 1_000.0, Distance.UNIT_KILOMETERS)
        } else {
            Distance.create(distanceMeters.toDouble().coerceAtLeast(0.0), Distance.UNIT_METERS)
        }
    }

    private fun loadingTemplate(): Template {
        return PlaceListMapTemplate.Builder()
            .setTitle(carContext.getString(R.string.car_map_title))
            .setHeaderAction(carHeaderAction())
            .setLoading(true)
            .build()
    }

    private fun missingPermissionTemplate(): Template {
        return MessageTemplate.Builder(carContext.getString(R.string.car_location_permission_message))
            .setTitle(carContext.getString(R.string.car_location_required))
            .setHeaderAction(carHeaderAction())
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.car_grant_permission))
                    .setOnClickListener { requestLocationPermission() }
                    .build()
            )
            .build()
    }

    private fun requestLocationPermission() {
        runCatching {
            carContext.requestPermissions(
                listOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            ) { granted, _ ->
                if (granted.isEmpty()) {
                    state = CarSitesLoadResult.Error(carContext.getString(R.string.car_permission_denied))
                } else {
                    loadSites()
                }
                invalidate()
            }
        }.onFailure {
            AppFileLog.e(CAR_LOG_TAG, "Echec de la demande de localisation pour la carte", it)
            state = CarSitesLoadResult.Error(carContext.getString(R.string.car_permission_denied))
            invalidate()
        }
    }

    private fun messageTemplate(
        title: String,
        message: String,
        actionTitle: String,
        action: () -> Unit
    ): Template {
        return MessageTemplate.Builder(message)
            .setTitle(title)
            .setHeaderAction(carHeaderAction())
            .addAction(
                Action.Builder()
                    .setTitle(actionTitle)
                    .setOnClickListener(action)
                    .build()
            )
            .build()
    }
}
