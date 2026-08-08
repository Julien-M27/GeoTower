package fr.geotower.ui.screens.car

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.constraints.ConstraintManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.data.db.GeoTowerDatabaseValidator
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.utils.AppFileLog
import fr.geotower.utils.LocationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Repli si l'hôte ne répond pas : valeur d'Android Auto (`content_limit_list` de car-app). */
private const val DEFAULT_LIST_CONTENT_LIMIT = 6

/**
 * Attente maximale d'un point GPS frais avant de se rabattre sur la dernière position connue.
 *
 * Assez long pour laisser un récepteur de voiture accrocher au démarrage, assez court pour qu'un
 * échec ne bloque pas l'écran racine.
 */
private const val LOCATION_TIMEOUT_MS = 8_000L

class CarNearbySitesScreen(
    carContext: CarContext,
    private val repository: AnfrRepository
) : Screen(carContext) {

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var state: NearbySitesState = NearbySitesState.Loading

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            // Revenir au premier plan doit relancer la recherche si la localisation vient d'être
            // accordée ailleurs — typiquement dans les réglages du téléphone pendant une session
            // Android Auto. Sans ça, l'écran reste bloqué sur « autorisation absente ».
            override fun onStart(owner: LifecycleOwner) {
                if (state == NearbySitesState.MissingLocationPermission && hasLocationPermission()) {
                    loadNearbySites()
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                screenScope.cancel()
            }
        })
        loadNearbySites()
    }

    override fun onGetTemplate(): Template = carTemplateOrError(carContext, "CarNearbySitesScreen") {
        when (val currentState = state) {
            NearbySitesState.Loading -> loadingTemplate()
            NearbySitesState.MissingLocationPermission -> missingPermissionTemplate()
            NearbySitesState.Empty -> messageTemplate(
                title = carContext.getString(R.string.car_nearby_sites),
                message = carContext.getString(R.string.car_no_sites_nearby),
                actionTitle = carContext.getString(R.string.common_try_again),
                action = ::loadNearbySites
            )
            NearbySitesState.DatabaseMissing -> messageTemplate(
                title = carContext.getString(R.string.car_nearby_sites),
                message = carContext.getString(R.string.car_database_missing),
                actionTitle = carContext.getString(R.string.common_try_again),
                action = ::loadNearbySites
            )
            is NearbySitesState.Error -> messageTemplate(
                title = carContext.getString(R.string.car_nearby_sites),
                message = currentState.message,
                actionTitle = carContext.getString(R.string.common_try_again),
                action = ::loadNearbySites
            )
            is NearbySitesState.Loaded -> loadedTemplate(currentState.sites)
        }
    }

    private fun loadNearbySites() {
        state = NearbySitesState.Loading
        invalidate()

        screenScope.launch {
            try {
                if (!hasLocationPermission()) {
                    carLog("Sites proches : permission de localisation absente")
                    state = NearbySitesState.MissingLocationPermission
                    invalidate()
                    return@launch
                }

                val location = getCarLocation()
                if (location == null) {
                    carLog("Sites proches : aucune position disponible")
                    state = NearbySitesState.Error(carContext.getString(R.string.car_location_unavailable))
                    invalidate()
                    return@launch
                }

                val sites = withContext(Dispatchers.IO) {
                    val antennas = repository.getNearest100(location.latitude, location.longitude)
                    antennas.toCarSiteListItems(location).take(25)
                }

                carLog("Sites proches : ${sites.size} site(s) prêts à être affichés")
                // Liste vide : dire POURQUOI. Sans base installée, le dépôt renvoie une liste vide
                // sans lever d'erreur, et « aucun site autour de vous » était faux et sans issue —
                // c'est l'état que rencontre toute première utilisation, la seule qui compte ici.
                // La vérification n'a lieu que sur liste vide : le repli API live sert des résultats
                // sans base valide, et l'annoncer manquante serait tout aussi faux.
                state = when {
                    sites.isNotEmpty() -> NearbySitesState.Loaded(sites)
                    !hasUsableDatabase() -> NearbySitesState.DatabaseMissing
                    else -> NearbySitesState.Empty
                }
                invalidate()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                // Sans ce filet, l'exception traverse le scope de l'écran et l'hôte n'affiche que
                // son bandeau générique — l'utilisateur ne saurait pas si c'est la base, le GPS ou
                // le réseau qui a lâché.
                AppFileLog.e(CAR_LOG_TAG, "Echec du chargement des sites proches", error)
                state = NearbySitesState.Error(
                    "${error.javaClass.simpleName} : ${error.message ?: "-"}".take(200)
                )
                invalidate()
            }
        }
    }

    private fun loadedTemplate(sites: List<CarSiteListItem>): Template {
        val screenManager = carContext.getCarService(ScreenManager::class.java)
        // L'hôte REFUSE un ListTemplate qui dépasse sa limite de contenu (6 lignes sur Android
        // Auto) et affiche alors son écran d'erreur générique. Cette limite dépend de l'hôte et de
        // l'état de conduite : elle se lit à l'exécution, elle ne peut pas être codée en dur.
        val hostLimit = runCatching {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        }.getOrElse { DEFAULT_LIST_CONTENT_LIMIT }.coerceAtLeast(1)
        val shownSites = sites.take(hostLimit)
        carLog("Sites proches : ${sites.size} trouvé(s), ${shownSites.size} affiché(s) (limite hôte = $hostLimit)")
        val itemListBuilder = ItemList.Builder()

        shownSites.forEach { site ->
            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle(site.title)
                    .addText(
                        carContext.getString(
                            R.string.car_site_distance_operators,
                            formatCarDistance(site.distanceMeters),
                            site.operators
                        )
                    )
                    .addText(site.subtitle)
                    .setOnClickListener {
                        screenManager.push(CarSiteDetailScreen(carContext, site))
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(itemListBuilder.build())
            .setTitle(carContext.getString(R.string.car_sites_around_me))
            .setHeaderAction(carHeaderAction())
            .build()
    }

    private fun loadingTemplate(): Template {
        return messageTemplate(
            title = carContext.getString(R.string.car_sites_around_me),
            message = carContext.getString(R.string.car_search_nearby),
            actionTitle = null,
            action = null
        )
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

    /**
     * Demande la localisation sur place, sans écran intermédiaire.
     *
     * Le bouton renvoyait vers un écran qui répétait la même phrase avant de lancer MainActivity sur
     * le téléphone : deux appuis, et une UI téléphone projetée sur l'écran du véhicule. Ici l'hôte
     * pose la demande là où il faut — sur le téléphone en projection, sur l'écran de la voiture en
     * Android Automotive OS.
     */
    private fun requestLocationPermission() {
        val permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        // L'hôte refuse la demande dans certains états de conduite : sans garde, l'exception
        // remonterait jusqu'à lui et on retomberait sur son écran d'erreur générique.
        runCatching {
            carContext.requestPermissions(permissions) { granted, _ ->
                if (granted.isEmpty()) {
                    carLog("Sites proches : localisation refusée")
                    state = NearbySitesState.Error(carContext.getString(R.string.car_permission_denied))
                    invalidate()
                } else {
                    carLog("Sites proches : localisation accordée")
                    loadNearbySites()
                }
            }
        }.onFailure {
            AppFileLog.e(CAR_LOG_TAG, "Echec de la demande de localisation depuis la voiture", it)
            state = NearbySitesState.Error(carContext.getString(R.string.car_permission_denied))
            invalidate()
        }
    }

    private fun messageTemplate(
        title: String,
        message: String,
        actionTitle: String?,
        action: (() -> Unit)?
    ): Template {
        val builder = MessageTemplate.Builder(message)
            .setTitle(title)
            .setHeaderAction(carHeaderAction())

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

    private suspend fun hasUsableDatabase(): Boolean = withContext(Dispatchers.IO) {
        GeoTowerDatabaseValidator.getInstalledDatabaseStatus(carContext).state ==
            GeoTowerDatabaseValidator.LocalDatabaseState.VALID
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(carContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(carContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Position de référence, avec une attente bornée.
     *
     * `getCurrentLocation()` demande un point frais en haute précision et ne passe aucun jeton
     * d'annulation : rien ne le borne côté client. Tant que cette recherche partait d'un appui
     * volontaire, l'attente restait le problème de l'utilisateur ; depuis que la liste est l'écran
     * racine, elle démarre à l'ouverture de la session — un démarrage à froid sans fix (parking
     * couvert, voiture qui vient d'être démarrée) laissait l'accueil sur « recherche en cours »
     * sans limite ni moyen d'en sortir.
     *
     * Passé [LOCATION_TIMEOUT_MS], on se rabat sur la dernière position connue ; s'il n'y en a pas,
     * l'écran bascule sur « position indisponible », qui propose au moins de réessayer.
     */
    @SuppressLint("MissingPermission")
    private suspend fun getCarLocation(): Location? {
        val startedAt = SystemClock.elapsedRealtime()
        val fresh = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            LocationHelper(carContext).getCurrentLocation()
        }
        if (fresh != null) return fresh

        carLog("Position : aucun point frais en ${SystemClock.elapsedRealtime() - startedAt} ms, repli sur la dernière connue")
        // Repli hors thread principal : getProviders/getLastKnownLocation sont des appels binder,
        // et ce chemin s'exécute désormais pendant la création de la session.
        val known = withContext(Dispatchers.IO) { getLastKnownLocation() }
        if (known != null) {
            val ageMinutes = (System.currentTimeMillis() - known.time) / 60_000
            carLog("Position : dernière position connue retenue (ancienneté $ageMinutes min)")
        }
        return known
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
                val siteTitle = carContext.getString(R.string.site_anfr_title, main.idAnfr)
                val fullAddress = technique?.adresse?.takeIf { it.isNotBlank() } ?: siteTitle
                val splitIndex = fullAddress.lastIndexOf(",")
                val title = if (splitIndex > 0) fullAddress.substring(0, splitIndex).trim() else fullAddress
                val subtitle = if (splitIndex > 0) fullAddress.substring(splitIndex + 1).trim() else siteTitle
                val operators = antennas
                    .flatMap { it.operatorSummary(carContext).split(", ") }
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
    data object DatabaseMissing : NearbySitesState
    data class Error(val message: String) : NearbySitesState
    data class Loaded(val sites: List<CarSiteListItem>) : NearbySitesState
}
