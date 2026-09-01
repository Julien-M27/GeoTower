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
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapController
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
    private var siteListExpanded = true
    private var templateRequestCount = 0L
    private var lifecycleEventCount = 0L

    init {
        carLog("Carte: création de CarAntennaMapScreen (${mapDiagnosticState()})")
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                carLog("Carte: onStart #${++lifecycleEventCount} (${mapDiagnosticState()})")
                registerMapSurfaceIfSupported()
                if (state == CarSitesLoadResult.MissingLocationPermission && hasCarLocationPermission(carContext)) {
                    carLog("Carte: permission retrouvée pendant onStart, rechargement des sites")
                    loadSites()
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                carLog("Carte: onStop #${++lifecycleEventCount} (${mapDiagnosticState()})")
                unregisterMapSurface()
                mapSurfaceCallback.detachSurface()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                carLog("Carte: onDestroy #${++lifecycleEventCount} (${mapDiagnosticState()})")
                unregisterMapSurface()
                mapSurfaceCallback.close()
                screenScope.cancel()
            }
        })
        loadSites()
    }

    override fun onGetTemplate(): Template = carTemplateOrError(carContext, "CarAntennaMapScreen") {
        carLog(
            "Carte: onGetTemplate #${++templateRequestCount}, " +
                "état=${state.javaClass.simpleName}, ${mapDiagnosticState()}"
        )
        when (val currentState = state) {
            CarSitesLoadResult.Loading -> loadingTemplate()
            is CarSitesLoadResult.Loaded -> {
                val validSites = currentState.sites.filter(::hasValidCoordinates)
                carLog("Carte: données chargées=${currentState.sites.size}, coordonnées valides=${validSites.size}")
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
        val startedAt = android.os.SystemClock.elapsedRealtime()
        carLog("Carte: début chargement des sites (${mapDiagnosticState()})")
        state = CarSitesLoadResult.Loading
        invalidate()

        screenScope.launch {
            try {
                state = sitesLoader.load()
                carLog(
                    "Carte: fin chargement des sites en " +
                        "${android.os.SystemClock.elapsedRealtime() - startedAt} ms, " +
                        "résultat=${state.javaClass.simpleName}"
                )
                invalidate()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AppFileLog.e(CAR_LOG_TAG, "Echec du chargement de la carte voiture", error)
                carLog(
                    "Carte: chargement en échec après " +
                        "${android.os.SystemClock.elapsedRealtime() - startedAt} ms, " +
                        "${error.javaClass.simpleName}"
                )
                state = CarSitesLoadResult.Error(
                    "${error.javaClass.simpleName} : ${error.message ?: "-"}".take(200)
                )
                invalidate()
            }
        }
    }

    private fun loadedTemplate(sites: List<CarSiteListItem>): Template {
        val customSurfaceAvailable = registerMapSurfaceIfSupported()
        carLog(
            "Carte: sélection du template, surface personnalisée=$customSurfaceAvailable, " +
                "sites=${sites.size}, ${mapDiagnosticState()}"
        )
        if (customSurfaceAvailable) {
            val customTemplate = runCatching { customMapTemplate(sites) }
                .onFailure {
                    AppFileLog.e(CAR_LOG_TAG, "Echec de construction de la carte applicative", it)
                    unregisterMapSurface()
                    mapSurfaceCallback.detachSurface()
                }
                .getOrNull()
            if (customTemplate != null) return customTemplate
            carLog("Carte: construction personnalisée nulle, bascule vers le template hôte")
        }

        carLog("Carte: utilisation de PlaceListMapTemplate (carte fournie par l'hôte)")
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
        carLog(
            "Carte applicative: sites=${sites.size}, lignes=${shownSites.size}, limiteHôte=$hostLimit, " +
                "listeDépliée=$siteListExpanded, provider=${AppConfig.mapProvider.intValue}, " +
                "ignStyle=${AppConfig.ignStyle.intValue}, azimuts=${AppConfig.showAzimuths.value}, " +
                "cônes=${AppConfig.showAzimuthsCone.value}, FH=${AppConfig.showTechnoFH.value}"
        )
        mapSurfaceCallback.updateSites(sites)

        val items = ItemList.Builder()
        shownSites.forEach { site ->
            items.addItem(
                Row.Builder()
                    .setImage(carOperatorGridIcon(carContext, site.operators), Row.IMAGE_TYPE_LARGE)
                    .setTitle(site.title)
                    .addText(formatCarDistance(site.distanceMeters))
                    .addText(carSiteDescriptionLine(site))
                    .setOnClickListener {
                        screenManager.push(CarSiteDetailScreen(carContext, site))
                    }
                    .build()
            )
        }

        val contentTemplate: Template = if (siteListExpanded) {
            ListTemplate.Builder()
                .setTitle(carContext.getString(R.string.car_map_title))
                .setHeaderAction(carHeaderAction())
                .setSingleList(items.build())
                .build()
        } else {
            // MapWithContentTemplate impose toujours un content template. AndroidX Car App
            // n'autorise un Pane sans ligne que lorsqu'il est explicitement en chargement :
            // Pane.Builder().build() lève sinon une IllegalStateException dans Pane.Builder.
            // Cette variante conserve le volet replié très compact, sans faux message visible,
            // et laisse l'icône de l'ActionStrip comme seul contrôle de réouverture.
            PaneTemplate.Builder(Pane.Builder().setLoading(true).build())
                .setTitle(carContext.getString(R.string.car_map_title))
                .setHeaderAction(carHeaderAction())
                .build()
        }

        return MapWithContentTemplate.Builder()
            .setContentTemplate(contentTemplate)
            .setMapController(
                MapController.Builder()
                    .setMapActionStrip(
                        ActionStrip.Builder()
                            .addAction(Action.PAN)
                            .addAction(mapZoomAction(R.drawable.ic_car_map_zoom_in) { mapSurfaceCallback.zoomIn() })
                            .addAction(mapZoomAction(R.drawable.ic_car_map_zoom_out) { mapSurfaceCallback.zoomOut() })
                            .addAction(mapZoomAction(R.drawable.ic_car_map_recenter) { mapSurfaceCallback.recenter() })
                            .build()
                    )
                    .setPanModeListener { mapSurfaceCallback.setPanMode(it) }
                    .build()
            )
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(toggleSiteListAction())
                    .addAction(mapSettingsAction())
                    .build()
            )
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
        carLog(
            "Carte hôte: sites=${sites.size}, marqueurs=${shownSites.size}, limiteHôte=$hostLimit, " +
                "provider configuré=${AppConfig.mapProvider.intValue} (ignoré par PlaceListMapTemplate)"
        )

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
                .addText(carSiteDescriptionLine(site))
                .setMetadata(Metadata.Builder().setPlace(place).build())
                .setOnClickListener {
                    screenManager.push(CarSiteDetailScreen(carContext, site))
                }
            items.addItem(row.build())
        }

        return runCatching {
            PlaceListMapTemplate.Builder()
                .setTitle(carContext.getString(R.string.car_map_title))
                .setHeaderAction(carHeaderAction())
                // Le host peut reconstruire le template après un changement de permission. Ne
                // jamais activer cette option si l'autorisation a été retirée entre deux rendus.
                .setCurrentLocationEnabled(hasCarLocationPermission(carContext))
                .setItemList(items.build())
                .setActionStrip(ActionStrip.Builder().addAction(mapSettingsAction()).build())
                .build()
        }.onFailure {
            AppFileLog.e(CAR_LOG_TAG, "Echec de construction du template carte", it)
        }.getOrElse {
            // Une ancienne version d'hôte ou une contrainte inattendue ne doit pas faire tomber
            // toute la session : la liste reste une représentation sûre des mêmes sites.
            fallbackListTemplate(shownSites)
        }
    }

    private fun mapSettingsAction(): Action {
        val screenManager = carContext.getCarService(ScreenManager::class.java)
        return Action.Builder()
            .setTitle(carContext.getString(R.string.car_map_settings_action))
            .setOnClickListener {
                carLog("Carte: ouverture des réglages, surfaceEnregistrée=$mapSurfaceRegistered")
                screenManager.push(
                    CarMapSettingsScreen(carContext) {
                        carLog("Carte: retour des réglages, rafraîchissement demandé")
                        mapSurfaceCallback.refresh()
                    }
                )
            }
            .build()
    }

    private fun toggleSiteListAction(): Action {
        return Action.Builder()
            // Sans titre, Android Auto rend l'action comme un contrôle circulaire d'icône, ce qui
            // évite le large bouton « Masquer/Afficher la liste » visible dans la vidéo.
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_map_list)).build())
            .setOnClickListener {
                carLog("Carte: clic liste, avant=$siteListExpanded, surfaceEnregistrée=$mapSurfaceRegistered")
                siteListExpanded = !siteListExpanded
                carLog("Carte: clic liste, après=$siteListExpanded, invalidation du template")
                invalidate()
            }
            .build()
    }

    private fun mapZoomAction(iconRes: Int, action: () -> Unit): Action {
        return Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, iconRes)).build())
            .setOnClickListener(action)
            .build()
    }

    private fun registerMapSurfaceIfSupported(): Boolean {
        val apiLevel = runCatching { carContext.getCarAppApiLevel() }
            .onFailure { AppFileLog.e(CAR_LOG_TAG, "Carte: lecture de l'API Car App impossible", it) }
            .getOrNull()
        carLog("Carte: tentative d'enregistrement de surface, api=$apiLevel, ${mapDiagnosticState()}")
        if (apiLevel == null || apiLevel < 7) {
            carLog("Carte: fallback hôte, raison=API Car App < 7 ou inconnue")
            return false
        }
        if (mapSurfaceRegistered) {
            carLog("Carte: surface déjà enregistrée")
            return true
        }

        return runCatching {
            carContext.getCarService(AppManager::class.java)
                .setSurfaceCallback(mapSurfaceCallback)
            mapSurfaceRegistered = true
            carLog("Carte: setSurfaceCallback réussi, surface personnalisée activée")
            true
        }.onFailure {
            AppFileLog.e(CAR_LOG_TAG, "La surface de carte n'est pas disponible sur cet hôte", it)
            carLog(
                "Carte: fallback hôte, setSurfaceCallback en échec=" +
                    "${it.javaClass.simpleName}: ${it.message ?: "-"}"
            )
        }.getOrDefault(false)
    }

    private fun unregisterMapSurface() {
        if (!mapSurfaceRegistered) {
            carLog("Carte: aucune surface à désenregistrer")
            return
        }
        carLog("Carte: désenregistrement de la surface")
        runCatching {
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
        }.onFailure {
            AppFileLog.e(CAR_LOG_TAG, "Impossible de libérer la surface de carte", it)
        }
        mapSurfaceRegistered = false
    }

    private fun mapDiagnosticState(): String {
        val apiLevel = runCatching { carContext.getCarAppApiLevel() }.getOrElse { -1 }
        fun permission(permission: String): String = runCatching {
            if (carContext.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                "accordée"
            } else {
                "refusée/non accordée"
            }
        }.getOrElse { "erreur:${it.javaClass.simpleName}" }

        return "api=$apiLevel, surface=$mapSurfaceRegistered, " +
            "ACCESS_SURFACE=${permission("androidx.car.app.ACCESS_SURFACE")}, " +
            "MAP_TEMPLATES=${permission("androidx.car.app.MAP_TEMPLATES")}, " +
            "INTERNET=${permission("android.permission.INTERNET")}, " +
            "localisation=${hasCarLocationPermission(carContext)}, liste=$siteListExpanded"
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
                    .addText(carSiteDescriptionLine(site))
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
