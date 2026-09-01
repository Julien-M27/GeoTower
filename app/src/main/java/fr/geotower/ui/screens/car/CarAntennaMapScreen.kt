package fr.geotower.ui.screens.car

import android.text.Spannable
import android.text.SpannableString
import androidx.car.app.CarContext
import androidx.car.app.AppManager
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarLocation
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Metadata
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.utils.AppFileLog
import fr.geotower.utils.AppConfig
import fr.geotower.utils.MapUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val DEFAULT_MAP_PLACE_LIMIT = 6

/** Carte Android Auto : surface GeoTower sur les hôtes récents, repli hôte sur les anciens. */
class CarAntennaMapScreen(
    carContext: CarContext,
    private val repository: AnfrRepository
) : Screen(carContext) {

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sitesLoader = CarNearbySitesLoader(carContext, repository)
    private val mapSurfaceCallback = CarAntennaMapSurfaceCallback(carContext)
    private var state: CarSitesLoadResult = CarSitesLoadResult.Loading
    private var mapSurfaceRegistered = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                registerMapSurfaceIfSupported()
                if (state == CarSitesLoadResult.MissingLocationPermission && hasCarLocationPermission(carContext)) {
                    loadSites()
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                unregisterMapSurface()
                mapSurfaceCallback.detachSurface()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                unregisterMapSurface()
                mapSurfaceCallback.close()
                screenScope.cancel()
            }
        })
        loadSites()
    }

    override fun onGetTemplate(): Template = carTemplateOrError(carContext, "CarAntennaMapScreen") {
        when (val currentState = state) {
            CarSitesLoadResult.Loading -> loadingTemplate()
            is CarSitesLoadResult.Loaded -> {
                val validSites = currentState.sites.filter(::hasValidCoordinates)
                if (validSites.isEmpty()) {
                    messageTemplate(
                        title = carContext.getString(R.string.car_map_title),
                        message = carContext.getString(R.string.car_no_sites_nearby),
                        actionTitle = carContext.getString(R.string.common_try_again),
                        action = ::loadSites
                    )
                } else {
                    loadedTemplate(validSites)
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
        if (registerMapSurfaceIfSupported()) {
            val customTemplate = runCatching { customMapTemplate(sites) }
                .onFailure {
                    AppFileLog.e(CAR_LOG_TAG, "Echec de construction de la carte applicative", it)
                    unregisterMapSurface()
                    mapSurfaceCallback.detachSurface()
                }
                .getOrNull()
            if (customTemplate != null) return customTemplate
        }

        return placeListMapTemplate(sites)
    }

    /** Carte applicative : logos dans les lignes, dessin des antennes directement sur la surface. */
    private fun customMapTemplate(sites: List<CarSiteListItem>): Template {
        val screenManager = carContext.getCarService(ScreenManager::class.java)
        val hostLimit = runCatching {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        }.getOrElse { DEFAULT_MAP_PLACE_LIMIT }.coerceAtLeast(1)
        val shownSites = sites.take(hostLimit)
        carLog("Carte applicative : ${sites.size} site(s) trouvé(s), ${shownSites.size} ligne(s) affichée(s)")
        mapSurfaceCallback.updateSites(sites)

        val items = ItemList.Builder()
        shownSites.forEach { site ->
            items.addItem(
                Row.Builder()
                    .setImage(carOperatorGridIcon(carContext, site.operators), Row.IMAGE_TYPE_LARGE)
                    .setTitle(site.title)
                    .addText(formatCarDistance(site.distanceMeters))
                    .addText(site.subtitle)
                    .setOnClickListener {
                        screenManager.push(CarSiteDetailScreen(carContext, site))
                    }
                    .build()
            )
        }

        return MapWithContentTemplate.Builder()
            .setContentTemplate(
                ListTemplate.Builder()
                    .setTitle(carContext.getString(R.string.car_map_title))
                    .setHeaderAction(carHeaderAction())
                    .setSingleList(items.build())
                    .build()
            )
            .setActionStrip(ActionStrip.Builder().addAction(mapSwitchAction()).build())
            .build()
    }

    /** Repli hôte : la carte reste utilisable même si MapWithContentTemplate n'est pas disponible. */
    private fun placeListMapTemplate(sites: List<CarSiteListItem>): Template {
        val screenManager = carContext.getCarService(ScreenManager::class.java)
        val hostLimit = runCatching {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST)
        }.getOrElse { DEFAULT_MAP_PLACE_LIMIT }.coerceAtLeast(1)
        val shownSites = sites.take(hostLimit)
        carLog("Carte : ${sites.size} site(s) trouvé(s), ${shownSites.size} marqueur(s) affiché(s)")

        val items = ItemList.Builder()
        shownSites.forEachIndexed { index, site ->
            // PlaceListMapTemplate réutilise le PlaceMarker comme repère de la carte ET de la
            // liste. L'API interdit donc d'ajouter une image d'opérateurs à cette même Row. Le
            // chemin MapWithContentTemplate ci-dessus est utilisé sur les hôtes compatibles.
            val place = Place.Builder(CarLocation.create(site.latitude, site.longitude))
                .setMarker(antennaPlaceMarker(site, index))
                .build()
            val row = Row.Builder()
                .setTitle(site.title)
                .addText(distanceLineWithSpan(site))
                .addText(site.subtitle)
                .setMetadata(Metadata.Builder().setPlace(place).build())
                .setOnClickListener {
                    screenManager.push(CarSiteDetailScreen(carContext, site))
                }
            items.addItem(row.build())
        }

        val mapSwitch = Action.Builder()
            .setTitle(carContext.getString(R.string.car_menu_nearby))
            .setOnClickListener {
                screenManager.popToRoot()
                screenManager.push(CarNearbySitesScreen(carContext, repository))
            }
            .build()

        return runCatching {
            PlaceListMapTemplate.Builder()
                .setTitle(carContext.getString(R.string.car_map_title))
                .setHeaderAction(carHeaderAction())
                // Le host peut reconstruire le template après un changement de permission. Ne
                // jamais activer cette option si l'autorisation a été retirée entre deux rendus.
                .setCurrentLocationEnabled(hasCarLocationPermission(carContext))
                .setItemList(items.build())
                .setActionStrip(ActionStrip.Builder().addAction(mapSwitch).build())
                .build()
        }.onFailure {
            AppFileLog.e(CAR_LOG_TAG, "Echec de construction du template carte", it)
        }.getOrElse {
            // Une ancienne version d'hôte ou une contrainte inattendue ne doit pas faire tomber
            // toute la session : la liste reste une représentation sûre des mêmes sites.
            fallbackListTemplate(shownSites)
        }
    }

    private fun mapSwitchAction(): Action {
        val screenManager = carContext.getCarService(ScreenManager::class.java)
        return Action.Builder()
            .setTitle(carContext.getString(R.string.car_menu_nearby))
            .setOnClickListener {
                screenManager.popToRoot()
                screenManager.push(CarNearbySitesScreen(carContext, repository))
            }
            .build()
    }

    private fun registerMapSurfaceIfSupported(): Boolean {
        if (!runCatching { carContext.getCarAppApiLevel() >= 7 }.getOrDefault(false)) return false
        if (mapSurfaceRegistered) return true

        return runCatching {
            carContext.getCarService(AppManager::class.java)
                .setSurfaceCallback(mapSurfaceCallback)
            mapSurfaceRegistered = true
            true
        }.onFailure {
            AppFileLog.e(CAR_LOG_TAG, "La surface de carte n'est pas disponible sur cet hôte", it)
        }.getOrDefault(false)
    }

    private fun unregisterMapSurface() {
        if (!mapSurfaceRegistered) return
        runCatching {
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
        }.onFailure {
            AppFileLog.e(CAR_LOG_TAG, "Impossible de libérer la surface de carte", it)
        }
        mapSurfaceRegistered = false
    }

    private fun fallbackListTemplate(sites: List<CarSiteListItem>): Template {
        val screenManager = carContext.getCarService(ScreenManager::class.java)
        val hostLimit = runCatching {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        }.getOrElse { DEFAULT_MAP_PLACE_LIMIT }.coerceAtLeast(1)

        val items = ItemList.Builder()
        sites.take(hostLimit).forEach { site ->
            val row = Row.Builder()
                    .setImage(carOperatorGridIcon(carContext, site.operators), Row.IMAGE_TYPE_LARGE)
                    .setTitle(site.title)
                    .addText(formatCarDistance(site.distanceMeters))
                    .addText(site.subtitle)
                    .setOnClickListener {
                        screenManager.push(CarSiteDetailScreen(carContext, site))
                    }
            items.addItem(row.build())
        }

        return ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.car_map_title))
            .setHeaderAction(carHeaderAction())
            .setSingleList(items.build())
            .build()
    }

    private fun distanceLineWithSpan(site: CarSiteListItem): CharSequence {
        val distance = SpannableString(" ")
        distance.setSpan(
            DistanceSpan.create(distanceForTemplate(site.distanceMeters)),
            0,
            1,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )
        return distance
    }

    /** Réutilise le dessin des marqueurs de la carte téléphone, y compris les secteurs azimutés. */
    private fun antennaPlaceMarker(site: CarSiteListItem, index: Int): PlaceMarker {
        if (site.antennas.isEmpty()) {
            return PlaceMarker.Builder().setLabel((index + 1).toString()).build()
        }

        return runCatching {
            val markerDrawable = MapUtils.createAdaptiveMarker(
                context = carContext,
                siteAntennas = site.antennas,
                showAzimuths = true,
                defaultOp = AppConfig.defaultOperator.value
            )
            val markerIcon = CarIcon.Builder(
                IconCompat.createWithBitmap(markerDrawable.bitmap)
            ).build()
            // TYPE_IMAGE est rendu par l'hôte dans un cartouche blanc avec pointe (visible sur
            // Android Auto). Le bitmap est déjà transparent autour du dessin : TYPE_ICON permet
            // à l'hôte de conserver l'antenne directement comme contenu du marqueur.
            PlaceMarker.Builder()
                .setIcon(markerIcon, PlaceMarker.TYPE_ICON)
                .build()
        }.onFailure {
            AppFileLog.e(CAR_LOG_TAG, "Impossible de dessiner le marqueur antenne ${site.idAnfr}", it)
        }.getOrElse {
            PlaceMarker.Builder().setLabel((index + 1).toString()).build()
        }
    }

    private fun distanceForTemplate(distanceMeters: Float): Distance {
        val meters = distanceMeters.toDouble().coerceAtLeast(0.0)
        return if (AppConfig.distanceUnit.intValue == 1) {
            val miles = meters / 1_609.344
            if (miles < 0.1) {
                Distance.create(meters * 3.28084, Distance.UNIT_FEET)
            } else {
                Distance.create(miles, Distance.UNIT_MILES)
            }
        } else if (meters >= 1_000.0) {
            Distance.create(meters / 1_000.0, Distance.UNIT_KILOMETERS)
        } else {
            Distance.create(meters, Distance.UNIT_METERS)
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
            carContext.requestPermissions(CAR_LOCATION_PERMISSIONS) { granted, _ ->
                if (granted.isEmpty()) {
                    state = CarSitesLoadResult.MissingLocationPermission
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

    private fun hasValidCoordinates(site: CarSiteListItem): Boolean {
        return site.latitude.isFinite() &&
            site.longitude.isFinite() &&
            site.userLatitude.isFinite() &&
            site.userLongitude.isFinite() &&
            site.distanceMeters.isFinite() &&
            site.distanceMeters >= 0f &&
            site.latitude in -90.0..90.0 &&
            site.longitude in -180.0..180.0 &&
            site.userLatitude in -90.0..90.0 &&
            site.userLongitude in -180.0..180.0
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
