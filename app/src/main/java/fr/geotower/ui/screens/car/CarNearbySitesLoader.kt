package fr.geotower.ui.screens.car

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.car.app.CarContext
import androidx.core.content.ContextCompat
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.data.db.GeoTowerDatabaseValidator
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.utils.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val LOCATION_TIMEOUT_MS = 8_000L
private const val MAX_SITES = 25

/** Résultat commun aux vues « proximité » et « carte » d'Android Auto. */
internal sealed interface CarSitesLoadResult {
    data object Loading : CarSitesLoadResult
    data object MissingLocationPermission : CarSitesLoadResult
    data object Empty : CarSitesLoadResult
    data object DatabaseMissing : CarSitesLoadResult
    data class Error(val message: String) : CarSitesLoadResult
    data class Loaded(val sites: List<CarSiteListItem>) : CarSitesLoadResult
}

/** Charge les sites autour de la voiture et construit le modèle léger utilisé par les templates. */
internal class CarNearbySitesLoader(
    private val carContext: CarContext,
    private val repository: AnfrRepository
) {

    suspend fun load(): CarSitesLoadResult {
        if (!hasCarLocationPermission(carContext)) {
            carLog("Sites proches : permission de localisation absente")
            return CarSitesLoadResult.MissingLocationPermission
        }

        val location = getCarLocation()
            ?: return CarSitesLoadResult.Error(carContext.getString(R.string.car_location_unavailable))

        val sites = withContext(Dispatchers.IO) {
            repository.getNearest100(location.latitude, location.longitude)
                .toCarSiteListItems(location)
                .take(MAX_SITES)
        }

        carLog("Sites proches : ${sites.size} site(s) prêts à être affichés")
        return when {
            sites.isNotEmpty() -> CarSitesLoadResult.Loaded(sites)
            !hasUsableDatabase() -> CarSitesLoadResult.DatabaseMissing
            else -> CarSitesLoadResult.Empty
        }
    }

    private suspend fun hasUsableDatabase(): Boolean = withContext(Dispatchers.IO) {
        GeoTowerDatabaseValidator.getInstalledDatabaseStatus(carContext).state ==
            GeoTowerDatabaseValidator.LocalDatabaseState.VALID
    }

    /** Attend brièvement un point frais, puis utilise la dernière position connue si nécessaire. */
    @SuppressLint("MissingPermission")
    private suspend fun getCarLocation(): Location? {
        val startedAt = SystemClock.elapsedRealtime()
        val fresh = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            LocationHelper(carContext).getCurrentLocation()
        }
        if (fresh != null) return fresh

        carLog("Position : aucun point frais en ${SystemClock.elapsedRealtime() - startedAt} ms, repli sur la dernière connue")
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

internal fun hasCarLocationPermission(carContext: CarContext): Boolean {
    return ContextCompat.checkSelfPermission(carContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(carContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
