package fr.geotower.ui.screens.settings

import android.content.Context
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fr.geotower.R
import fr.geotower.data.community.CommunityDataPreferences
import fr.geotower.ui.components.MiniMapViewMode
import fr.geotower.utils.AppConfig
import fr.geotower.utils.PreferenceStores
import fr.geotower.utils.SitePagePrefs

/**
 * Compteur d'écritures des clés de visibilité « _simple ».
 *
 * Un pylône affiche autant de sections opérateur que d'opérateurs, chacune rendue par son propre
 * `SiteDetailScreen(embedded = true)`, et le panneau de réglage peut désormais s'ouvrir d'ailleurs
 * que depuis une section (barre du haut de la fiche pylône, réglages). Sans ce compteur, les
 * sections déjà dépliées resteraient sur la valeur lue à leur composition.
 */
object EmbeddedSiteBlocks {
    val revision = mutableIntStateOf(0)

    fun bumpRevision() {
        revision.intValue++
    }
}

/**
 * Panneau « Sections opérateur » : les mêmes blocs que la fiche site, mais écrits sur les clés
 * suffixées `_simple` qu'utilisent les sections dépliées du mode simplifié.
 *
 * Autonome (il porte ses propres sous-panneaux) parce qu'il s'ouvre depuis trois endroits sans
 * rapport entre eux : le pied d'une section dépliée, la barre du haut de la fiche pylône et
 * Réglages > Personnalisation des pages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbeddedSiteBlocksSettingsSheet(
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    sheetState: SheetState,
    useOneUi: Boolean,
    bubbleColor: Color,
    highlightBlockId: String? = null
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE) }

    // Le panneau principal s'efface le temps d'un sous-panneau : sans ça, ouvrir « Photos » depuis
    // une ligne fermerait tout, puisque la feuille appelle onDismiss() avant de demander l'ouverture.
    var mainVisible by remember { mutableStateOf(true) }
    var subSheet by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(mainVisible, subSheet) {
        if (!mainVisible && subSheet == null) onDismiss()
    }

    fun write(prefKey: String, value: Boolean) {
        prefs.edit().putBoolean(SitePagePrefs.embeddedKey(prefKey), value).apply()
        EmbeddedSiteBlocks.bumpRevision()
    }

    fun read(blockId: String, prefKey: String, standaloneValue: Boolean): Boolean =
        SitePagePrefs.readEmbedded(prefs, blockId, prefKey, standaloneValue)

    // L'ordre des blocs est commun aux deux modes : seule la visibilité est propre aux sections.
    var order by remember { mutableStateOf(SitePagePrefs.order(prefs)) }
    var miniMapMode by remember {
        mutableStateOf(MiniMapViewMode.fromStorageKey(prefs.getString(SitePagePrefs.MINI_MAP_MODE, null)))
    }

    var showOperator by remember { mutableStateOf(read("operator", SitePagePrefs.operator.key, SitePagePrefs.operator.read(prefs))) }
    var showBearing by remember { mutableStateOf(read("bearing", SitePagePrefs.bearing.key, SitePagePrefs.read(prefs, SitePagePrefs.bearing))) }
    var showHeight by remember { mutableStateOf(read("height", SitePagePrefs.height.key, SitePagePrefs.read(prefs, SitePagePrefs.height))) }
    var showMap by remember { mutableStateOf(read("map", SitePagePrefs.map.key, SitePagePrefs.map.read(prefs))) }
    var showSupportDetails by remember { mutableStateOf(read("support_details", SitePagePrefs.supportDetails.key, SitePagePrefs.supportDetails.read(prefs))) }
    var showPanelHeights by remember { mutableStateOf(read("panel_heights", SitePagePrefs.panelHeights.key, SitePagePrefs.panelHeights.read(prefs))) }
    var showIds by remember { mutableStateOf(read("ids", SitePagePrefs.ids.key, SitePagePrefs.ids.read(prefs))) }
    var showNetworkIds by remember { mutableStateOf(read("network_ids", SitePagePrefs.networkIds.key, SitePagePrefs.networkIds.read(prefs))) }
    var showOpenMap by remember { mutableStateOf(read("open_map", SitePagePrefs.openMap.key, SitePagePrefs.openMap.read(prefs))) }
    var showElevationProfile by remember { mutableStateOf(read("elevation_profile", SitePagePrefs.elevationProfile.key, SitePagePrefs.elevationProfile.read(prefs))) }
    var showTheoreticalCoverage by remember { mutableStateOf(read("theoretical_coverage", SitePagePrefs.theoreticalCoverage.key, SitePagePrefs.theoreticalCoverage.read(prefs))) }
    var showThroughputCalculator by remember { mutableStateOf(read("throughput_calculator", SitePagePrefs.throughputCalculator.key, SitePagePrefs.throughputCalculator.read(prefs))) }
    var showNav by remember { mutableStateOf(read("nav", SitePagePrefs.nav.key, SitePagePrefs.nav.read(prefs))) }
    var showShare by remember { mutableStateOf(read("share", SitePagePrefs.share.key, SitePagePrefs.share.read(prefs))) }
    var showDates by remember { mutableStateOf(read("dates", SitePagePrefs.dates.key, SitePagePrefs.dates.read(prefs))) }
    var showAddress by remember { mutableStateOf(read("address", SitePagePrefs.address.key, SitePagePrefs.address.read(prefs))) }
    var showFreqs by remember { mutableStateOf(read("freqs", SitePagePrefs.freqs.key, SitePagePrefs.freqs.read(prefs))) }
    var showLinks by remember { mutableStateOf(read("links", SitePagePrefs.links.key, SitePagePrefs.links.read(prefs))) }
    // Photos, statut et speedtest sont des états globaux d'AppConfig sur la fiche autonome : ici ils
    // restent locaux à la section, sinon les masquer déréglerait le partage et le rapport PDF.
    var showPhotos by remember { mutableStateOf(read("photos", "site_show_photos", AppConfig.siteShowPhotos.value)) }
    var showStatus by remember { mutableStateOf(read("status", "site_show_status", AppConfig.siteShowStatus.value)) }
    var showSpeedtest by remember { mutableStateOf(read("speedtest", "site_show_speedtest", AppConfig.siteShowSpeedtest.value)) }

    if (mainVisible) {
        SiteSettingsSheet(
            title = stringResource(R.string.appstrings_page_site_embedded_settings),
            siteOrder = order,
            onOrderChange = {
                order = SitePagePrefs.normalizeOrder(it)
                SitePagePrefs.saveOrder(prefs, order)
                EmbeddedSiteBlocks.bumpRevision()
            },
            showOperator = showOperator,
            onOperatorChange = { showOperator = it; write(SitePagePrefs.operator.key, it) },
            showBearing = showBearing,
            onBearingChange = { showBearing = it; write(SitePagePrefs.bearing.key, it) },
            showHeight = showHeight,
            onHeightChange = { showHeight = it; write(SitePagePrefs.height.key, it) },
            showMap = showMap,
            onMapChange = { showMap = it; write(SitePagePrefs.map.key, it) },
            showSupportDetails = showSupportDetails,
            onSupportDetailsChange = { showSupportDetails = it; write(SitePagePrefs.supportDetails.key, it) },
            showPhotos = showPhotos,
            onPhotosChange = { showPhotos = it; write("site_show_photos", it) },
            showPanelHeights = showPanelHeights,
            onPanelHeightsChange = { showPanelHeights = it; write(SitePagePrefs.panelHeights.key, it) },
            showIds = showIds,
            onIdsChange = { showIds = it; write(SitePagePrefs.ids.key, it) },
            showNetworkIds = showNetworkIds,
            onNetworkIdsChange = { showNetworkIds = it; write(SitePagePrefs.networkIds.key, it) },
            showOpenMap = showOpenMap,
            onOpenMapChange = { showOpenMap = it; write(SitePagePrefs.openMap.key, it) },
            showElevationProfile = showElevationProfile,
            onElevationProfileChange = { showElevationProfile = it; write(SitePagePrefs.elevationProfile.key, it) },
            showThroughputCalculator = showThroughputCalculator,
            onThroughputCalculatorChange = { showThroughputCalculator = it; write(SitePagePrefs.throughputCalculator.key, it) },
            showTheoreticalCoverage = showTheoreticalCoverage,
            onTheoreticalCoverageChange = { showTheoreticalCoverage = it; write(SitePagePrefs.theoreticalCoverage.key, it) },
            showNav = showNav,
            onNavChange = { showNav = it; write(SitePagePrefs.nav.key, it) },
            showShare = showShare,
            onShareChange = { showShare = it; write(SitePagePrefs.share.key, it) },
            showDates = showDates,
            onDatesChange = { showDates = it; write(SitePagePrefs.dates.key, it) },
            showAddress = showAddress,
            onAddressChange = { showAddress = it; write(SitePagePrefs.address.key, it) },
            showStatus = showStatus,
            onStatusChange = { showStatus = it; write("site_show_status", it) },
            showSpeedtest = showSpeedtest,
            onSpeedtestChange = { showSpeedtest = it; write("site_show_speedtest", it) },
            showFreqs = showFreqs,
            onFreqsChange = { showFreqs = it; write(SitePagePrefs.freqs.key, it) },
            showLinks = showLinks,
            onLinksChange = { showLinks = it; write(SitePagePrefs.links.key, it) },
            onOpenMiniMapSettings = { mainVisible = false; subSheet = SUB_SHEET_MINI_MAP },
            onOpenFrequencies = { mainVisible = false; subSheet = SUB_SHEET_FREQUENCIES },
            onOpenPhotosSettings = { mainVisible = false; subSheet = SUB_SHEET_PHOTOS },
            onOpenSpeedtestSettings = { mainVisible = false; subSheet = SUB_SHEET_SPEEDTEST },
            onDismiss = { mainVisible = false },
            onBack = onBack,
            sheetState = sheetState,
            useOneUi = useOneUi,
            bubbleColor = bubbleColor,
            highlightBlockId = highlightBlockId
        )
    }

    when (subSheet) {
        SUB_SHEET_MINI_MAP -> MiniMapSettingsSheet(
            selectedMode = miniMapMode,
            onModeChange = {
                miniMapMode = it
                prefs.edit().putString(SitePagePrefs.MINI_MAP_MODE, it.storageKey).apply()
                EmbeddedSiteBlocks.bumpRevision()
            },
            onDismiss = { subSheet = null },
            onBack = { subSheet = null; mainVisible = true },
            sheetState = sheetState,
            useOneUi = useOneUi,
            bubbleColor = bubbleColor
        )

        SUB_SHEET_FREQUENCIES -> SiteFreqFiltersSheet(
            onDismiss = { subSheet = null },
            onBack = { subSheet = null; mainVisible = true }
        )

        SUB_SHEET_PHOTOS -> SitePhotosSettingsSheet(
            onDismiss = { subSheet = null },
            onBack = { subSheet = null; mainVisible = true },
            photosVisible = showPhotos,
            onPhotosVisibilityChange = { showPhotos = it; write("site_show_photos", it) },
            onOpenCommunityDataSettings = { subSheet = SUB_SHEET_COMMUNITY_PHOTOS }
        )

        SUB_SHEET_COMMUNITY_PHOTOS, SUB_SHEET_SPEEDTEST -> CommunityDataSettingsSheet(
            onDismiss = { subSheet = null },
            sheetState = sheetState,
            useOneUi = useOneUi,
            featureId = if (subSheet == SUB_SHEET_SPEEDTEST) {
                CommunityDataPreferences.FEATURE_SPEEDTEST
            } else {
                CommunityDataPreferences.FEATURE_PHOTOS
            }
        )
    }
}

private const val SUB_SHEET_MINI_MAP = "mini_map"
private const val SUB_SHEET_FREQUENCIES = "frequencies"
private const val SUB_SHEET_PHOTOS = "photos"
private const val SUB_SHEET_SPEEDTEST = "speedtest"
private const val SUB_SHEET_COMMUNITY_PHOTOS = "community_photos"
