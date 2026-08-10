package fr.geotower.ui.screens.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.geotower.R
import fr.geotower.data.api.ApiServer
import fr.geotower.data.api.CellularFrApi
import fr.geotower.data.outages.OperatorOutageSource

/**
 * Un hôte contacté par l'app, tel qu'affiché dans « Sources de données » (page À propos).
 *
 * [host] est l'hôte RÉELLEMENT joint par l'app (c'est l'information recherchée) ; [url] est la page
 * lisible à ouvrir au clic — souvent le portail ou la licence du fournisseur, pas l'hôte technique
 * lui-même. `null` quand il n'existe pas de page à montrer (API JSON, CSV qui se téléchargerait,
 * serveur de tuiles, hôte de redirection) : l'entrée reste affichée mais n'est pas cliquable.
 */
data class AboutSourceLink(
    val host: String,
    val url: String? = null,
    /** Ligne de titre au-dessus de l'hôte. `null` = l'hôte se rattache au libellé précédent. */
    val label: String? = null
)

/** Un bloc de la section « Sources de données » : un titre, une description, ses hôtes. */
data class AboutSourceGroup(
    val title: String,
    val description: String,
    val links: List<AboutSourceLink> = emptyList()
)

// Pages de présentation des jeux de données ANFR / ARCEP (les URLs de téléchargement, elles, sont
// résolues à l'exécution par fr.geotower.data.build.OfficialSources).
private const val ANFR_WEEKLY_DATA_URL = "https://data.anfr.fr/explore/dataset/observatoire_2g_3g_4g/"
private const val ANFR_MONTHLY_DATA_URL = "https://www.data.gouv.fr/datasets/donnees-sur-les-installations-radioelectriques-de-plus-de-5-watts-1/"
private const val ARCEP_QUARTERLY_DATA_URL = "https://data.arcep.fr/mobile/sites/"
private const val ARCEP_HS_DATA_URL = "https://www.data.gouv.fr/datasets/sites-indisponibles"

/**
 * Catalogue des sources de données et des API que l'app interroge, groupé par usage.
 *
 * À tenir à jour quand une source réseau apparaît ou disparaît. Les hôtes définis ailleurs sont
 * repris de leur source de vérité ([ApiServer], [OperatorOutageSource]) plutôt que recopiés ; les
 * autres sont écrits ici, en regard du fichier qui les utilise.
 */
@Composable
fun aboutSourceGroups(): List<AboutSourceGroup> {
    val weeklyLabel = stringResource(R.string.appstrings_version_weekly_label).replace('\n', ' ')
    val monthlyLabel = stringResource(R.string.appstrings_version_monthly_label).replace('\n', ' ')
    val quarterlyLabel = stringResource(R.string.appstrings_version_quarterly_label).replace('\n', ' ')
    val hsLabel = stringResource(R.string.appstrings_version_hs_label).replace('\n', ' ')

    return buildList {
        // 1. Jeux de données publics ANFR / ARCEP (fr.geotower.data.build.OfficialSources).
        add(
            AboutSourceGroup(
                title = stringResource(R.string.appstrings_src_antennas),
                description = stringResource(R.string.appstrings_src_antennas_desc),
                links = listOf(
                    AboutSourceLink(
                        label = weeklyLabel,
                        host = "data.anfr.fr",
                        url = ANFR_WEEKLY_DATA_URL
                    ),
                    AboutSourceLink(
                        label = monthlyLabel,
                        host = "www.data.gouv.fr",
                        url = ANFR_MONTHLY_DATA_URL
                    ),
                    AboutSourceLink(
                        label = quarterlyLabel,
                        host = "data.arcep.fr",
                        url = ARCEP_QUARTERLY_DATA_URL
                    ),
                    AboutSourceLink(
                        label = hsLabel,
                        host = "www.data.gouv.fr",
                        url = ARCEP_HS_DATA_URL
                    ),
                    // Cibles des redirections 302 de data.gouv et du bucket ARCEP : ce sont elles
                    // qui servent les fichiers, l'app les joint donc vraiment (OfficialSources).
                    AboutSourceLink(
                        label = stringResource(R.string.appstrings_src_download_hosts),
                        host = "static.data.gouv.fr"
                    ),
                    AboutSourceLink(host = "object.files.data.gouv.fr"),
                    AboutSourceLink(host = "arcep.s3.rbx.io.cloud.ovh.net")
                )
            )
        )

        // 2. Référentiel communes / départements (population, superficie).
        add(
            AboutSourceGroup(
                title = stringResource(R.string.appstrings_src_reference),
                description = stringResource(R.string.appstrings_src_reference_desc),
                links = listOf(
                    AboutSourceLink(host = "geo.api.gouv.fr", url = "https://geo.api.gouv.fr/")
                )
            )
        )

        // 3. Fichiers de pannes publiés par les opérateurs : la liste vient de l'enum, pas d'une
        // copie. Non cliquables : l'URL est un CSV, l'ouvrir déclencherait un téléchargement.
        add(
            AboutSourceGroup(
                title = stringResource(R.string.appstrings_src_outages),
                description = stringResource(R.string.appstrings_src_outages_desc),
                links = OperatorOutageSource.entries.map { source ->
                    AboutSourceLink(
                        label = source.label,
                        host = source.url.substringAfter("//").substringBefore('/')
                    )
                }
            )
        )

        // 4. Serveur GeoTower et son miroir : mêmes routes /api/v2/… servies par le même code.
        add(
            AboutSourceGroup(
                title = stringResource(R.string.appstrings_src_server),
                description = stringResource(R.string.appstrings_src_server_desc),
                links = listOf(
                    AboutSourceLink(
                        label = stringResource(R.string.appstrings_src_server_primary),
                        host = ApiServer.PRIMARY.host
                    ),
                    AboutSourceLink(
                        label = stringResource(R.string.appstrings_src_server_mirror),
                        host = ApiServer.MIRROR.host
                    )
                )
            )
        )

        // 5. IGN : fonds de carte WMTS, altimétrie RGE ALTI, bâtiments BD TOPO (Géoplateforme).
        add(
            AboutSourceGroup(
                title = stringResource(R.string.appstrings_src_ign),
                description = stringResource(R.string.appstrings_src_ign_desc) + "\n" +
                    stringResource(R.string.appstrings_src_ign_services),
                links = listOf(
                    AboutSourceLink(host = "geoservices.ign.fr", url = "https://geoservices.ign.fr/"),
                    AboutSourceLink(host = "data.geopf.fr")
                )
            )
        )

        // 6. OpenStreetMap : la licence des données, et le serveur de tuiles du fond OSM.
        add(
            AboutSourceGroup(
                title = stringResource(R.string.appstrings_src_osm),
                description = stringResource(R.string.appstrings_src_osm_desc),
                links = listOf(
                    AboutSourceLink(
                        host = "www.openstreetmap.org",
                        url = "https://www.openstreetmap.org/copyright"
                    ),
                    AboutSourceLink(
                        label = stringResource(R.string.appstrings_src_tiles_label),
                        host = "tile.openstreetmap.org"
                    )
                )
            )
        )

        // 7. Autres serveurs de tuiles (MapUtils.MapAttribution) : l'hôte est celui des tuiles,
        // le lien mène à la page de licence du fournisseur.
        add(
            AboutSourceGroup(
                title = stringResource(R.string.appstrings_src_tiles),
                description = stringResource(R.string.appstrings_src_tiles_desc),
                links = listOf(
                    AboutSourceLink(
                        label = "CARTO",
                        host = "basemaps.cartocdn.com",
                        url = "https://carto.com/attributions"
                    ),
                    AboutSourceLink(
                        label = "OpenTopoMap",
                        host = "tile.opentopomap.org",
                        url = "https://opentopomap.org/about"
                    ),
                    AboutSourceLink(
                        label = "Esri World Imagery",
                        host = "server.arcgisonline.com",
                        url = "https://www.arcgis.com/home/item.html?id=10df2279f9684e4a9f6a7f08febac2a9"
                    )
                )
            )
        )

        // 8. Géocodage (NominatimApi).
        add(
            AboutSourceGroup(
                title = stringResource(R.string.appstrings_src_geocoding),
                description = stringResource(R.string.appstrings_src_geocoding_desc),
                links = listOf(
                    AboutSourceLink(
                        host = "nominatim.openstreetmap.org",
                        url = "https://nominatim.openstreetmap.org/"
                    )
                )
            )
        )

        // 9. Cartes hors ligne (OfflineMapCatalog).
        add(
            AboutSourceGroup(
                title = stringResource(R.string.appstrings_maps_forges_title),
                description = stringResource(R.string.appstrings_maps_forges_desc),
                links = listOf(
                    AboutSourceLink(host = "mapsforge.org", url = "https://mapsforge.org"),
                    AboutSourceLink(
                        label = stringResource(R.string.appstrings_src_offline_maps_label),
                        host = "download.mapsforge.org",
                        url = "https://download.mapsforge.org/maps/v5/europe/france/"
                    )
                )
            )
        )

        // 10. Services tiers ouverts depuis les fiches de site (SiteExternalLinkDefinitions).
        add(
            AboutSourceGroup(
                title = stringResource(R.string.appstrings_src_partners),
                description = stringResource(R.string.appstrings_src_partners_desc),
                links = buildList {
                    add(AboutSourceLink(label = "Cartoradio", host = "cartoradio.fr", url = "https://cartoradio.fr/"))
                    // CellularFR masqué — voir CellularFrApi.ENABLED
                    if (CellularFrApi.isEnabled) {
                        add(AboutSourceLink(label = "CellularFR", host = "cellularfr.fr", url = "https://cellularfr.fr/"))
                    }
                    add(AboutSourceLink(label = "Signal Quest", host = "signalquest.fr", url = "https://signalquest.fr/"))
                    add(AboutSourceLink(label = "CellMapper", host = "www.cellmapper.net", url = "https://www.cellmapper.net/"))
                    add(AboutSourceLink(label = "RNC Mobile", host = "rncmobile.net", url = "https://rncmobile.net/"))
                    add(AboutSourceLink(label = "eNB-Analytics", host = "enb-analytics.fr", url = "https://enb-analytics.fr/"))
                    add(AboutSourceLink(label = "J'alerte l'Arcep", host = "jalerte.arcep.fr", url = "https://jalerte.arcep.fr/"))
                }
            )
        )
    }
}
