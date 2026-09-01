package fr.geotower.ui.screens.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import fr.geotower.R

private const val DEFAULT_TECHNICAL_DETAIL_LIMIT = 6
private const val MAX_DETAIL_TEXT_CHARS = 220

/** Détails techniques du même regroupement de site que celui présenté sur le téléphone. */
class CarSiteTechnicalDetailScreen(
    carContext: CarContext,
    private val site: CarSiteListItem
) : Screen(carContext) {

    override fun onGetTemplate(): Template = carTemplateOrError(carContext, "CarSiteTechnicalDetailScreen") {
        val rows = listOfNotNull(
            detailRow(
                R.string.car_technical_antenna_count,
                listOf("${site.antennas.size} • ${site.operators}")
            ),
            detailRow(R.string.car_technical_technologies, site.technologies),
            detailRow(R.string.car_technical_supports, formatSupports()),
            detailRow(R.string.car_technical_radio, site.azimuths),
            detailRow(R.string.car_technical_status, site.statuses),
            detailRow(R.string.car_technical_ids, site.anfrIds.ifEmpty { listOf(site.idAnfr) }),
            detailRow(R.string.car_technical_frequencies, site.frequencyDetails),
            detailRow(R.string.car_technical_flags, formatFlags())
        )
        val limit = runCatching {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        }.getOrElse { DEFAULT_TECHNICAL_DETAIL_LIMIT }.coerceAtLeast(1)
        val items = ItemList.Builder()
        rows.take(limit).forEach { (title, value) ->
            items.addItem(Row.Builder().setTitle(title).addText(value).build())
        }

        ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.car_site_more_details))
            .setHeaderAction(carHeaderAction())
            .setSingleList(items.build())
            .build()
    }

    private fun formatSupports(): List<String> {
        return site.supportDetails.map { support ->
            buildString {
                append(support.idSupport)
                support.nature?.let { append(" • ").append(it) }
                support.heightMeters?.let { append(" • ").append(formatCarDistance(it.toFloat())) }
                support.owner?.let { append(" • ").append(it) }
                support.operator?.let { append(" • ").append(it) }
            }
        }.ifEmpty { site.supportIds }
    }

    private fun formatFlags(): List<String> {
        return buildList {
            if (site.isZoneBlanche) add(carContext.getString(R.string.car_technical_zone_blanches))
            if (site.hasUndergroundSupport) add(carContext.getString(R.string.car_technical_underground))
            if (site.isEntirelyProject) add(carContext.getString(R.string.car_technical_project))
        }
    }

    private fun detailRow(titleRes: Int, values: List<String>): Pair<String, String>? {
        val value = values
            .map { it.trim() }
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(" • ")
            .take(MAX_DETAIL_TEXT_CHARS)
        if (value.isBlank()) return null
        return carContext.getString(titleRes) to value
    }
}
