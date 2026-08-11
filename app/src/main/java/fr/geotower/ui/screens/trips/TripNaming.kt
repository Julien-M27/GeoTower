package fr.geotower.ui.screens.trips

import android.content.Context
import fr.geotower.R
import fr.geotower.data.trip.TripPlan
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Le nom proposé par l'app pour une tournée : « Tournée du 12 août 2026 ».
 *
 * Un seul endroit le construit — la création d'un trajet et la pose d'une date doivent produire
 * exactement le même texte, sinon fixer une date renommerait la tournée d'une façon qui ne
 * ressemble pas à ce qui avait été proposé au départ.
 */
fun defaultTripName(context: Context, millis: Long, locale: Locale): String = context.getString(
    R.string.trips_default_name_pattern,
    DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(millis))
)

/**
 * Le nom que le trajet doit porter après un changement de date.
 *
 * Le titre ne suit la date que tant qu'il est **automatique** : dès que l'utilisateur a renommé sa
 * tournée, on n'y touche plus. Sans date, il retombe sur le jour de création — le nom reste ainsi
 * cohérent avec ce que le trajet annonce.
 */
fun tripNameAfterScheduling(
    context: Context,
    plan: TripPlan,
    plannedAtMillis: Long?,
    locale: Locale
): String = if (plan.autoNamed) {
    defaultTripName(context, plannedAtMillis ?: plan.createdAtMillis, locale)
} else {
    plan.name
}

/**
 * Ce que devient un trajet quand on lui fixe (ou lui retire) une date. Écrit une seule fois : la
 * liste et la carte proposent toutes deux ce réglage, et deux règles divergentes finiraient par
 * produire deux comportements différents pour le même geste.
 *
 * Le **statut suit la date** — c'est lui qui alimente le filtre « à venir », et une tournée datée
 * restée « brouillon » n'y apparaîtrait jamais. Une tournée déjà terminée ou archivée garde le
 * sien : la dater à nouveau ne la ramène pas dans les tournées à faire.
 */
fun TripPlan.withSchedule(
    context: Context,
    plannedAtMillis: Long?,
    reminderOffsetsMinutes: List<Int>,
    stopDurationMinutes: Int,
    locale: Locale
): TripPlan = copy(
    name = tripNameAfterScheduling(context, this, plannedAtMillis, locale),
    plannedAtMillis = plannedAtMillis,
    reminderOffsetsMinutes = reminderOffsetsMinutes,
    stopDurationMinutes = stopDurationMinutes,
    status = statusAfterScheduling(plannedAtMillis)
)
