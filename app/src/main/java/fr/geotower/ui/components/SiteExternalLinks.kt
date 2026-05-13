package fr.geotower.ui.components

import android.content.SharedPreferences
import fr.geotower.R

const val SITE_EXTERNAL_LINK_ORDER_PREF_KEY = "external_links_order"
private const val LEGACY_SITE_EXTERNAL_LINK_ORDER_PREF_KEY = "page_site_external_links_order"

data class SiteExternalLinkDefinition(
    val id: String,
    val label: String,
    val logoRes: Int,
    val prefKey: String,
    val defaultEnabled: Boolean = true
)

val SiteExternalLinkDefinitions = listOf(
    SiteExternalLinkDefinition("cartoradio", "Cartoradio", R.drawable.logo_cartoradio, "link_cartoradio"),
    SiteExternalLinkDefinition("cellularfr", "CellularFR", R.drawable.logo_cellularfr, "link_cellularfr"),
    SiteExternalLinkDefinition("signalquest", "Signal Quest", R.drawable.logo_signalquest, "link_signalquest"),
    SiteExternalLinkDefinition("rncmobile", "RNC Mobile", R.drawable.logo_rncmobile, "link_rncmobile"),
    SiteExternalLinkDefinition("enbanalytics", "eNB-Analytics", R.drawable.logo_enbanalytics, "link_enbanalytics"),
    SiteExternalLinkDefinition("anfr", "data.gouv.fr", R.drawable.logo_anfr, "show_anfr")
)

fun siteExternalLinkById(id: String): SiteExternalLinkDefinition? {
    return SiteExternalLinkDefinitions.firstOrNull { it.id == id }
}

fun defaultSiteExternalLinkOrder(): List<String> {
    return SiteExternalLinkDefinitions.map { it.id }
}

fun readSiteExternalLinkOrder(prefs: SharedPreferences): List<String> {
    val rawOrder = prefs.getString(SITE_EXTERNAL_LINK_ORDER_PREF_KEY, null)
        ?: prefs.getString(LEGACY_SITE_EXTERNAL_LINK_ORDER_PREF_KEY, null)
        ?: defaultSiteExternalLinkOrder().joinToString(",")

    return normalizeSiteExternalLinkOrder(rawOrder.split(","))
}

fun writeSiteExternalLinkOrder(prefs: SharedPreferences, order: List<String>) {
    val normalized = normalizeSiteExternalLinkOrder(order).joinToString(",")
    prefs.edit()
        .putString(SITE_EXTERNAL_LINK_ORDER_PREF_KEY, normalized)
        .putString(LEGACY_SITE_EXTERNAL_LINK_ORDER_PREF_KEY, normalized)
        .apply()
}

fun resetSiteExternalLinks(prefs: SharedPreferences) {
    val editor = prefs.edit()
    SiteExternalLinkDefinitions.forEach { link ->
        editor.putBoolean(link.prefKey, link.defaultEnabled)
    }
    val defaultOrder = defaultSiteExternalLinkOrder().joinToString(",")
    editor
        .putString(SITE_EXTERNAL_LINK_ORDER_PREF_KEY, defaultOrder)
        .putString(LEGACY_SITE_EXTERNAL_LINK_ORDER_PREF_KEY, defaultOrder)
        .apply()
}

private fun normalizeSiteExternalLinkOrder(order: List<String>): List<String> {
    val knownIds = SiteExternalLinkDefinitions.map { it.id }
    val cleaned = order.map { it.trim() }
        .filter { it in knownIds }
        .distinct()
    return cleaned + knownIds.filterNot { it in cleaned }
}
