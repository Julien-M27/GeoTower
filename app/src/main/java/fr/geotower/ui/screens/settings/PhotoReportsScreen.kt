package fr.geotower.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import fr.geotower.ui.components.PageScrollEdgeButtons
import fr.geotower.ui.components.pageScrollbar
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.utils.PageScrollPrefs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.data.api.SignalQuestOperators
import fr.geotower.data.api.SignalQuestPhotoReportReasons
import fr.geotower.data.community.PhotoReportHistoryEntry
import fr.geotower.data.community.PhotoReportHistoryStore
import fr.geotower.utils.PreferenceStores
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.LocalizedDateLabels
import fr.geotower.utils.OperatorColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Page « Mes signalements » : ce que l'utilisateur a signalé à SignalQuest, et où ça en est.
 *
 * Deux états seulement, et c'est une limite de l'API, pas un raccourci : elle est en écriture seule.
 * On sait qu'un signalement est **parti**, et on voit qu'une photo a fini par **disparaître** des
 * photos publiées. Un refus, lui, ressemble en tout point à une attente — la page ne prétend donc
 * jamais dire « refusé ».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoReportsScreen(
    navController: NavController,
    repository: AnfrRepository? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val scrollState = rememberScrollState()
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "settings")
    val safeClick = rememberSafeClick()

    val prefs = remember(context) {
        context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
    }
    var showSettingsSheet by remember { mutableStateOf(false) }
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showCounter by remember { mutableStateOf(HistoryPagePreferences.read(prefs, HistoryPagePreferences.REPORT_COUNTER)) }
    var showIntro by remember { mutableStateOf(HistoryPagePreferences.read(prefs, HistoryPagePreferences.REPORT_INTRO)) }
    var showStatus by remember { mutableStateOf(HistoryPagePreferences.read(prefs, HistoryPagePreferences.REPORT_STATUS)) }
    var showAddress by remember { mutableStateOf(HistoryPagePreferences.read(prefs, HistoryPagePreferences.REPORT_ADDRESS)) }
    var showDetails by remember { mutableStateOf(HistoryPagePreferences.read(prefs, HistoryPagePreferences.REPORT_DETAILS)) }

    var entries by remember { mutableStateOf<List<PhotoReportHistoryEntry>>(emptyList()) }
    var resolvedSites by remember { mutableStateOf<Map<String, ResolvedPhotoReportSite>>(emptyMap()) }
    var pendingDeletion by remember { mutableStateOf<PhotoReportHistoryEntry?>(null) }
    var reloadTick by remember { mutableStateOf(0) }

    BackHandler(enabled = !safeBackNavigation.isLocked) {
        if (showSettingsSheet) {
            showSettingsSheet = false
        } else {
            safeBackNavigation.navigateBack()
        }
    }

    LaunchedEffect(reloadTick) {
        entries = PhotoReportHistoryStore.read(context)
    }

    LaunchedEffect(entries, repository) {
        if (repository == null) return@LaunchedEffect
        if (entries.isEmpty()) {
            resolvedSites = emptyMap()
            return@LaunchedEffect
        }
        resolvedSites = withContext(Dispatchers.IO) {
            entries.associate { entry ->
                entry.id to resolveReportSite(
                    repository = repository,
                    siteId = entry.siteId,
                    operatorLabel = entry.operatorLabel
                )
            }
        }
    }

    Scaffold(
        containerColor = uiStyle.backgroundColor,
        // Les routes du NavHost padent déjà avec l'innerPadding racine.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.photo_reports_title),
                onBack = { safeBackNavigation.navigateBack() },
                backEnabled = !safeBackNavigation.isLocked,
                backgroundColor = uiStyle.backgroundColor,
                actions = {
                    IconButton(
                        onClick = { safeClick("photo_reports_settings") { showSettingsSheet = true } }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.appstrings_settings_title),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .geoTowerFadingEdge(scrollState, fadeHeight = sizing.component(72.dp))
                    .pageScrollbar(PageScrollPrefs.PHOTO_REPORTS, scrollState)
                    .verticalScroll(scrollState)
                    .navigationBarsPadding()
                    .padding(horizontal = sizing.spacing(16.dp)),
                verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
            ) {
                Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))

                if (showIntro) {
                    Text(
                        text = stringResource(R.string.photo_reports_intro),
                        style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (showCounter && entries.isNotEmpty()) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.photo_reports_recorded,
                            entries.size,
                            entries.size
                        ),
                        style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = sizing.spacing(48.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.photo_reports_empty),
                            style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                entries.forEach { entry ->
                    val siteInfo = resolvedSites[entry.id]
                    PhotoReportRow(
                        entry = entry,
                        siteInfo = siteInfo,
                        showStatus = showStatus,
                        showAddress = showAddress,
                        showDetails = showDetails,
                        onOpenSite = {
                            openReportedSite(
                                context = context,
                                navController = navController,
                                repository = repository,
                                entry = entry,
                                siteInfo = siteInfo,
                                coroutineScope = coroutineScope
                            )
                        },
                        onDelete = { pendingDeletion = entry }
                    )
                }

                Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))
            }

            PageScrollEdgeButtons(PageScrollPrefs.PHOTO_REPORTS, scrollState)
        }
    }

    if (showSettingsSheet) {
        HistoryPageSettingsSheet(
            title = stringResource(R.string.photo_reports_settings_title),
            page = PageScrollPrefs.PHOTO_REPORTS,
            options = listOf(
                HistoryPageOption(
                    title = stringResource(R.string.photo_reports_option_counter),
                    checked = showCounter,
                    onCheckedChange = {
                        showCounter = it
                        HistoryPagePreferences.write(prefs, HistoryPagePreferences.REPORT_COUNTER, it)
                    }
                ),
                HistoryPageOption(
                    title = stringResource(R.string.photo_reports_option_intro),
                    checked = showIntro,
                    onCheckedChange = {
                        showIntro = it
                        HistoryPagePreferences.write(prefs, HistoryPagePreferences.REPORT_INTRO, it)
                    }
                ),
                HistoryPageOption(
                    title = stringResource(R.string.photo_reports_option_status),
                    checked = showStatus,
                    onCheckedChange = {
                        showStatus = it
                        HistoryPagePreferences.write(prefs, HistoryPagePreferences.REPORT_STATUS, it)
                    }
                ),
                HistoryPageOption(
                    title = stringResource(R.string.photo_reports_option_address),
                    checked = showAddress,
                    onCheckedChange = {
                        showAddress = it
                        HistoryPagePreferences.write(prefs, HistoryPagePreferences.REPORT_ADDRESS, it)
                    }
                ),
                HistoryPageOption(
                    title = stringResource(R.string.photo_reports_option_details),
                    checked = showDetails,
                    onCheckedChange = {
                        showDetails = it
                        HistoryPagePreferences.write(prefs, HistoryPagePreferences.REPORT_DETAILS, it)
                    }
                ),
            ),
            onReset = {
                showCounter = HistoryPagePreferences.DEFAULT_ENABLED
                showIntro = HistoryPagePreferences.DEFAULT_ENABLED
                showStatus = HistoryPagePreferences.DEFAULT_ENABLED
                showAddress = HistoryPagePreferences.DEFAULT_ENABLED
                showDetails = HistoryPagePreferences.DEFAULT_ENABLED
                HistoryPagePreferences.write(prefs, HistoryPagePreferences.REPORT_COUNTER, HistoryPagePreferences.DEFAULT_ENABLED)
                HistoryPagePreferences.write(prefs, HistoryPagePreferences.REPORT_INTRO, HistoryPagePreferences.DEFAULT_ENABLED)
                HistoryPagePreferences.write(prefs, HistoryPagePreferences.REPORT_STATUS, HistoryPagePreferences.DEFAULT_ENABLED)
                HistoryPagePreferences.write(prefs, HistoryPagePreferences.REPORT_ADDRESS, HistoryPagePreferences.DEFAULT_ENABLED)
                HistoryPagePreferences.write(prefs, HistoryPagePreferences.REPORT_DETAILS, HistoryPagePreferences.DEFAULT_ENABLED)
            },
            onDismiss = { showSettingsSheet = false },
            onBack = { showSettingsSheet = false },
            sheetState = settingsSheetState,
            useOneUi = uiStyle.useOneUi,
            bubbleColor = uiStyle.bubbleColor
        )
    }

    pendingDeletion?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text(stringResource(R.string.photo_reports_delete_title)) },
            text = { Text(stringResource(R.string.photo_reports_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    PhotoReportHistoryStore.remove(context, listOf(entry.id))
                    pendingDeletion = null
                    reloadTick++
                }) {
                    Text(stringResource(R.string.photo_reports_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text(stringResource(R.string.appstrings_cancel))
                }
            }
        )
    }
}

@Composable
private fun PhotoReportRow(
    entry: PhotoReportHistoryEntry,
    siteInfo: ResolvedPhotoReportSite?,
    showStatus: Boolean,
    showAddress: Boolean,
    showDetails: Boolean,
    onOpenSite: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val removed = entry.status == PhotoReportHistoryStore.STATUS_REMOVED
    val canOpen = entry.siteId.isNotBlank()

    Card(
        shape = uiStyle.cardShape,
        colors = CardDefaults.cardColors(containerColor = uiStyle.cardColor),
        border = uiStyle.cardBorder,
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (canOpen) {
                    Modifier.clickable(onClick = onOpenSite)
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(sizing.spacing(14.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (removed) Icons.Default.CheckCircle else Icons.Default.Schedule,
                contentDescription = null,
                tint = if (removed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(sizing.component(24.dp))
            )
            Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(sizing.spacing(2.dp))
            ) {
                Text(
                    text = stringResource(photoReportReasonLabelRes(entry.reason)),
                    style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (showStatus) {
                    Text(
                        text = stringResource(
                            if (removed) {
                                R.string.photo_reports_status_removed
                            } else {
                                R.string.photo_reports_status_sent
                            }
                        ),
                        style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val addressText = formatSiteAddress(siteInfo?.address, siteInfo?.commune)
                if (showAddress && !addressText.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = sizing.spacing(2.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(sizing.component(14.dp))
                        )
                        Spacer(modifier = Modifier.width(sizing.spacing(4.dp)))
                        Text(
                            text = addressText,
                            style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (showDetails) {
                    val codeLabel = formatSiteCodeLabel(entry.siteId, siteInfo)
                    val subtitleParts = listOfNotNull(
                        entry.operatorLabel?.takeIf { it.isNotBlank() },
                        codeLabel,
                        formatReportDate(context, entry.createdAtMillis, AppConfig.appLanguage.value)
                    )
                    if (subtitleParts.isNotEmpty()) {
                        Text(
                            text = subtitleParts.joinToString(" · "),
                            style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = sizing.spacing(2.dp))
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.photo_reports_delete_title),
                        modifier = Modifier.size(sizing.component(20.dp))
                    )
                }
                if (canOpen) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = stringResource(R.string.photo_reports_open_site),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(sizing.component(20.dp))
                    )
                }
            }
        }
    }
}

internal data class ResolvedPhotoReportSite(
    val siteId: String,
    val isSupportId: Boolean,
    val isAnfrId: Boolean,
    val targetAnfrId: String?,
    val targetSupportId: String?,
    val address: String?,
    val commune: String?,
    val latitude: Double?,
    val longitude: Double?
)

internal suspend fun resolveReportSite(
    repository: AnfrRepository,
    siteId: String,
    operatorLabel: String? = null
): ResolvedPhotoReportSite {
    val cleanId = siteId.trim()
    if (cleanId.isBlank()) {
        return ResolvedPhotoReportSite(
            siteId = siteId,
            isSupportId = false,
            isAnfrId = false,
            targetAnfrId = null,
            targetSupportId = null,
            address = null,
            commune = null,
            latitude = null,
            longitude = null
        )
    }

    val operatorKey = OperatorColors.keyFor(operatorLabel)

    val rows = try {
        repository.getFavoriteScopeSiteRows(cleanId)
    } catch (_: Exception) {
        emptyList()
    }

    if (rows.isNotEmpty()) {
        val matchedSupport = rows.any { it.idSupport == cleanId }
        val matchedAnfr = rows.any { it.idAnfr == cleanId }

        val operatorMatchingRow = if (!operatorKey.isNullOrBlank()) {
            rows.firstOrNull { row ->
                val rowOpKeys = OperatorColors.keysFor(row.operateur)
                val signalQuestOp = SignalQuestOperators.operatorParamFor(row.operateur)
                operatorKey in rowOpKeys || signalQuestOp.equals(operatorLabel, ignoreCase = true)
            }
        } else null

        // Si l'opérateur est spécifié, on cible exclusivement son antenne ; sinon première disponible
        val targetAnfrId = if (!operatorLabel.isNullOrBlank()) {
            operatorMatchingRow?.idAnfr?.takeIf(String::isNotBlank)
        } else {
            rows.firstNotNullOfOrNull { it.idAnfr.takeIf(String::isNotBlank) }
        }

        val targetSupportId = rows.firstNotNullOfOrNull { it.idSupport?.takeIf(String::isNotBlank) }
        val preferredRow = operatorMatchingRow ?: rows.first()
        val address = preferredRow.adresse?.trim()?.takeIf(String::isNotBlank)
            ?: rows.firstNotNullOfOrNull { it.adresse?.trim()?.takeIf(String::isNotBlank) }
        val commune = preferredRow.commune?.trim()?.takeIf(String::isNotBlank)
            ?: rows.firstNotNullOfOrNull { it.commune?.trim()?.takeIf(String::isNotBlank) }
        val latitude = preferredRow.latitude ?: rows.firstNotNullOfOrNull { it.latitude }
        val longitude = preferredRow.longitude ?: rows.firstNotNullOfOrNull { it.longitude }

        val isSupport = matchedSupport || (!matchedAnfr && targetSupportId == cleanId)
        val isAnfr = matchedAnfr || (!matchedSupport && targetAnfrId == cleanId)

        return ResolvedPhotoReportSite(
            siteId = siteId,
            isSupportId = isSupport,
            isAnfrId = isAnfr,
            targetAnfrId = targetAnfrId,
            targetSupportId = targetSupportId,
            address = address,
            commune = commune,
            latitude = latitude,
            longitude = longitude
        )
    }

    val antennas = try {
        repository.getAntennasByExactId(cleanId)
    } catch (_: Exception) {
        emptyList()
    }

    if (antennas.isNotEmpty()) {
        val matchedAnfr = antennas.any { it.idAnfr == cleanId }

        val operatorMatchingAntenna = if (!operatorKey.isNullOrBlank()) {
            antennas.firstOrNull { antenna ->
                val antennaKeys = OperatorColors.keysFor(antenna.operateur)
                val signalQuestOp = SignalQuestOperators.operatorParamFor(antenna.operateur)
                operatorKey in antennaKeys || signalQuestOp.equals(operatorLabel, ignoreCase = true)
            }
        } else null

        val selectedAntenna = if (!operatorLabel.isNullOrBlank()) {
            operatorMatchingAntenna
        } else {
            antennas.first()
        }

        val targetAnfrId = selectedAntenna?.idAnfr?.takeIf(String::isNotBlank)
        val latitude = selectedAntenna?.latitude ?: antennas.firstNotNullOfOrNull { it.latitude }
        val longitude = selectedAntenna?.longitude ?: antennas.firstNotNullOfOrNull { it.longitude }

        val techniques = targetAnfrId?.let { anfr ->
            runCatching { repository.getTechniqueByAnfr(anfr) }.getOrDefault(emptyList())
        }.orEmpty()
        val address = techniques.firstNotNullOfOrNull { it.adresse?.trim()?.takeIf(String::isNotBlank) }
        val commune: String? = null

        val targetSupportId = targetAnfrId?.let { anfr ->
            runCatching {
                repository.getPhysiqueByAnfr(anfr).firstNotNullOfOrNull { it.idSupport.takeIf(String::isNotBlank) }
            }.getOrNull()
        }
        val matchedSupport = targetSupportId == cleanId

        val isSupport = matchedSupport || (!matchedAnfr && targetSupportId != null)
        val isAnfr = matchedAnfr || (!matchedSupport && targetAnfrId != null)

        return ResolvedPhotoReportSite(
            siteId = siteId,
            isSupportId = isSupport,
            isAnfrId = isAnfr,
            targetAnfrId = targetAnfrId,
            targetSupportId = targetSupportId,
            address = address,
            commune = commune,
            latitude = latitude,
            longitude = longitude
        )
    }

    return ResolvedPhotoReportSite(
        siteId = siteId,
        isSupportId = false,
        isAnfrId = false,
        targetAnfrId = null,
        targetSupportId = null,
        address = null,
        commune = null,
        latitude = null,
        longitude = null
    )
}

private fun openReportedSite(
    context: Context,
    navController: NavController,
    repository: AnfrRepository?,
    entry: PhotoReportHistoryEntry,
    siteInfo: ResolvedPhotoReportSite?,
    coroutineScope: CoroutineScope
) {
    val siteId = entry.siteId.trim()
    if (siteId.isBlank()) return

    coroutineScope.launch {
        // 1. Positionner le repère cliqué si les coordonnées sont connues
        val lat = siteInfo?.latitude
        val lon = siteInfo?.longitude
        if (lat != null && lon != null) {
            context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
                .edit()
                .putFloat("clicked_lat", lat.toFloat())
                .putFloat("clicked_lon", lon.toFloat())
                .apply()
        }

        // 2. Si un ID ANFR cible est connu (et correspond à l'opérateur du signalement),
        // aller directement sur site_detail
        val targetAnfr = siteInfo?.targetAnfrId
        if (!targetAnfr.isNullOrBlank()) {
            navController.navigate("site_detail/${Uri.encode(targetAnfr)}")
            return@launch
        }

        // 3. Si aucun ID ANFR spécifique n'était résolu mais qu'un repository est disponible,
        // chercher une antenne correspondant à l'opérateur
        val targetAntenna = if (repository != null) {
            withContext(Dispatchers.IO) {
                val operatorKey = OperatorColors.keyFor(entry.operatorLabel)
                val antennas = runCatching { repository.getAntennasByExactId(siteId) }.getOrDefault(emptyList())
                if (!operatorKey.isNullOrBlank()) {
                    antennas.firstOrNull { antenna ->
                        val antennaKeys = OperatorColors.keysFor(antenna.operateur)
                        val signalQuestOperator = SignalQuestOperators.operatorParamFor(antenna.operateur)
                        operatorKey in antennaKeys || signalQuestOperator.equals(entry.operatorLabel, ignoreCase = true)
                    }
                } else {
                    antennas.firstOrNull()
                }
            }
        } else null

        if (targetAntenna != null) {
            context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
                .edit()
                .putFloat("clicked_lat", targetAntenna.latitude.toFloat())
                .putFloat("clicked_lon", targetAntenna.longitude.toFloat())
                .apply()
            navController.navigate("site_detail/${Uri.encode(targetAntenna.idAnfr)}")
        } else {
            // 4. Repli si aucune antenne spécifique n'a été trouvée pour cet opérateur :
            // Si c'est un support, ouvrir la fiche support avec le filtre opérateur
            if (siteInfo?.isSupportId == true) {
                val operatorParam = OperatorColors.keyFor(entry.operatorLabel)
                    ?.let { "?operator=${Uri.encode(it)}" }
                    .orEmpty()
                navController.navigate("support_detail/${Uri.encode(siteId)}$operatorParam")
            } else {
                navController.navigate("site_detail/${Uri.encode(siteId)}")
            }
        }
    }
}

internal fun formatSiteAddress(address: String?, commune: String?): String? {
    val cleanAddr = address?.trim()?.takeIf { it.isNotBlank() }
    val cleanCommune = commune?.trim()?.takeIf { it.isNotBlank() }
    return when {
        cleanAddr != null && cleanCommune != null -> {
            if (cleanAddr.contains(cleanCommune, ignoreCase = true)) {
                cleanAddr
            } else {
                "$cleanAddr, $cleanCommune"
            }
        }
        cleanAddr != null -> cleanAddr
        cleanCommune != null -> cleanCommune
        else -> null
    }
}

@Composable
private fun formatSiteCodeLabel(
    siteId: String,
    siteInfo: ResolvedPhotoReportSite?
): String? {
    if (siteId.isBlank()) return null
    return when {
        siteInfo?.isAnfrId == true -> {
            stringResource(R.string.photo_reports_code_anfr, siteId)
        }
        siteInfo?.isSupportId == true -> {
            val targetAnfr = siteInfo.targetAnfrId
            if (!targetAnfr.isNullOrBlank() && targetAnfr != siteId) {
                stringResource(R.string.photo_reports_code_support_with_anfr, siteId, targetAnfr)
            } else {
                stringResource(R.string.photo_reports_code_support, siteId)
            }
        }
        else -> {
            stringResource(R.string.photo_reports_code_site, siteId)
        }
    }
}

private fun formatReportDate(context: Context, millis: Long, languagePreference: String): String =
    LocalizedDateLabels.formatLongDate(context, millis, languagePreference)

private fun photoReportReasonLabelRes(reason: String): Int = when (reason) {
    SignalQuestPhotoReportReasons.WRONG_LOCATION -> R.string.appstrings_photo_report_reason_wrong_location
    SignalQuestPhotoReportReasons.QUALITY -> R.string.appstrings_photo_report_reason_quality
    SignalQuestPhotoReportReasons.INAPPROPRIATE -> R.string.appstrings_photo_report_reason_inappropriate
    SignalQuestPhotoReportReasons.COPYRIGHT -> R.string.appstrings_photo_report_reason_copyright
    SignalQuestPhotoReportReasons.SPAM -> R.string.appstrings_photo_report_reason_spam
    else -> R.string.appstrings_photo_report_reason_other
}
