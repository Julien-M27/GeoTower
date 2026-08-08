package fr.geotower.ui.screens.car

import android.content.Context
import android.location.Location
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import fr.geotower.R
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.utils.AppFileLog
import java.util.Locale

internal const val CAR_LOG_TAG = "GeoTowerCar"

/** Un template ne doit pas dépasser la largeur d'un écran de voiture, message d'erreur compris. */
private const val CAR_ERROR_DETAIL_MAX_CHARS = 240

internal fun carLog(message: String) = AppFileLog.i(CAR_LOG_TAG, message)

/**
 * Une exception levée dans `onGetTemplate` n'atteint jamais l'app : l'hôte Android Auto l'avale et
 * affiche son écran générique « a rencontré une erreur inattendue », sans rien laisser derrière.
 * On l'attrape donc ici pour l'écrire dans [AppFileLog] et la montrer telle quelle sur l'écran de
 * la voiture, ce qui rend le diagnostic possible sans PC branché.
 */
internal fun carTemplateOrError(
    carContext: CarContext,
    where: String,
    block: () -> Template
): Template {
    return try {
        block()
    } catch (error: Throwable) {
        AppFileLog.e(CAR_LOG_TAG, "Echec du rendu de $where", error)
        carErrorTemplate(carContext, where, error)
    }
}

internal fun carErrorTemplate(carContext: CarContext, where: String, error: Throwable): Template {
    val detail = buildString {
        append(error.javaClass.simpleName)
        error.message?.takeIf { it.isNotBlank() }?.let { append(" : ").append(it) }
    }.take(CAR_ERROR_DETAIL_MAX_CHARS)
    return MessageTemplate.Builder(carContext.getString(R.string.car_error_detail, where, detail))
        .setTitle(carContext.getString(R.string.car_error_title))
        .setHeaderAction(Action.APP_ICON)
        .build()
}

/**
 * Action d'en-tête d'un écran qui peut se retrouver à la racine comme empilé.
 *
 * Un `Action.BACK` posé sur la racine affiche une flèche retour qui ferme l'app — l'hôte n'a rien
 * derrière où revenir. L'icône de l'app est la convention voiture pour un écran racine.
 */
internal fun Screen.carHeaderAction(): Action =
    if (screenManager.stackSize <= 1) Action.APP_ICON else Action.BACK

internal fun formatCarDistance(distanceMeters: Float): String {
    return if (distanceMeters >= 1000f) {
        String.format(Locale.FRANCE, "%.1f km", distanceMeters / 1000f)
    } else {
        "${distanceMeters.toInt()} m"
    }
}

internal fun calculateCarDistance(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double
): Float {
    val result = FloatArray(1)
    Location.distanceBetween(fromLatitude, fromLongitude, toLatitude, toLongitude, result)
    return result[0]
}

internal fun LocalisationEntity.operatorSummary(context: Context): String {
    return operateur
        ?.split(Regex("[/,\\-]"))
        ?.map { it.trim().uppercase(Locale.FRANCE) }
        ?.filter { it.isNotEmpty() }
        ?.distinct()
        ?.joinToString(", ")
        ?.takeIf { it.isNotBlank() }
        ?: context.getString(R.string.appstrings_operator_unknown)
}
