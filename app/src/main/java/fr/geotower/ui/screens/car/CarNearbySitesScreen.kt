package fr.geotower.ui.screens.car

import android.Manifest
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

/** Repli si l'hôte ne répond pas : valeur d'Android Auto (`content_limit_list` de car-app). */
private const val DEFAULT_LIST_CONTENT_LIMIT = 6

class CarNearbySitesScreen(
    carContext: CarContext,
    private val repository: AnfrRepository
) : Screen(carContext) {

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sitesLoader = CarNearbySitesLoader(carContext, repository)
    private var state: NearbySitesState = NearbySitesState.Loading

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            // Revenir au premier plan doit relancer la recherche si la localisation vient d'être
            // accordée ailleurs — typiquement dans les réglages du téléphone pendant une session
            // Android Auto. Sans ça, l'écran reste bloqué sur « autorisation absente ».
            override fun onStart(owner: LifecycleOwner) {
                if (state == NearbySitesState.MissingLocationPermission && hasCarLocationPermission(carContext)) {
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
                state = when (val result = sitesLoader.load()) {
                    CarSitesLoadResult.Loading -> NearbySitesState.Loading
                    CarSitesLoadResult.MissingLocationPermission -> NearbySitesState.MissingLocationPermission
                    CarSitesLoadResult.Empty -> NearbySitesState.Empty
                    CarSitesLoadResult.DatabaseMissing -> NearbySitesState.DatabaseMissing
                    is CarSitesLoadResult.Error -> NearbySitesState.Error(result.message)
                    is CarSitesLoadResult.Loaded -> NearbySitesState.Loaded(result.sites)
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
                    .setImage(carOperatorGridIcon(carContext, site.operators))
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
                    state = NearbySitesState.MissingLocationPermission
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

}

private sealed interface NearbySitesState {
    data object Loading : NearbySitesState
    data object MissingLocationPermission : NearbySitesState
    data object Empty : NearbySitesState
    data object DatabaseMissing : NearbySitesState
    data class Error(val message: String) : NearbySitesState
    data class Loaded(val sites: List<CarSiteListItem>) : NearbySitesState
}
