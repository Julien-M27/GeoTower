package fr.geotower.ui.components

import android.Manifest
import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import fr.geotower.BuildConfig
import fr.geotower.R
import fr.geotower.data.api.DatabaseDownloader
import fr.geotower.data.api.ServerStatus
import fr.geotower.data.build.LocalDbRebuildOffer
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.config.RemoteHomeAnnouncement
import fr.geotower.data.db.GeoTowerDatabaseValidator
import fr.geotower.data.workers.AppUpdateNotifier
import fr.geotower.data.workers.DatabaseBulkUpdate
import fr.geotower.data.workers.DatabaseDownloadWorker
import fr.geotower.data.workers.EnbDatabaseDownloadWorker
import fr.geotower.data.workers.LocalDbBuildWorker
import fr.geotower.data.workers.OperationPauseStore
import fr.geotower.data.workers.RadioDatabaseDownloadWorker
import fr.geotower.ui.theme.LocalGeoTowerUiSizing
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import fr.geotower.utils.LocationReadiness
import fr.geotower.utils.PreferenceStores
import fr.geotower.utils.locationReadiness
import fr.geotower.utils.openAppLocationSettings
import fr.geotower.utils.openLocationSourceSettings
import fr.geotower.utils.rememberLocationReadinessState
import fr.geotower.utils.rememberNetworkConnectivityState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG_BANNERS = "GeoTowerStatusBanners"
private const val PREF_HOME_ANNOUNCEMENT_DISMISSED = "home_announcement_dismissed_key"

private fun databaseProgressKey(target: DatabaseBulkUpdate.Target): String = when (target) {
    DatabaseBulkUpdate.Target.MOBILE -> DatabaseDownloadWorker.KEY_PROGRESS
    DatabaseBulkUpdate.Target.RADIO -> RadioDatabaseDownloadWorker.KEY_PROGRESS
    DatabaseBulkUpdate.Target.ENB -> EnbDatabaseDownloadWorker.KEY_PROGRESS
}

private fun databasePauseKey(target: DatabaseBulkUpdate.Target): String = when (target) {
    DatabaseBulkUpdate.Target.MOBILE -> OperationPauseStore.MOBILE_DB_DOWNLOAD
    DatabaseBulkUpdate.Target.RADIO -> OperationPauseStore.RADIO_DB_DOWNLOAD
    DatabaseBulkUpdate.Target.ENB -> OperationPauseStore.ENB_DB_DOWNLOAD
}

private fun databaseSettingsSection(target: DatabaseBulkUpdate.Target): String = when (target) {
    DatabaseBulkUpdate.Target.MOBILE -> "db_mobile"
    DatabaseBulkUpdate.Target.RADIO -> "db_radio"
    DatabaseBulkUpdate.Target.ENB -> "db_enb"
}

/**
 * Bloc des bandeaux de statut GeoTower :
 * - Annonce distante (message administrateur / release / info)
 * - Hors-ligne (perte de connexion réseau)
 * - Serveur injoignable (réseau OK mais API muette)
 * - Localisation désactivée (permission refusée ou GPS éteint)
 * - Base de données (manquante, invalide, mise à jour disponible, téléchargement ou génération en cours)
 */
@Composable
fun HomeStatusBanners(
    navController: NavController,
    modifier: Modifier = Modifier,
    onBannerVisibilityChange: ((hideLogo: Boolean, hasBanner: Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val safeClick = rememberSafeClick()

    // 1. Détection de la disponibilité de la localisation
    val locationReadinessState = rememberLocationReadinessState()
    val readiness by locationReadinessState
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationReadinessState.value = locationReadiness(context)
        if (!granted) {
            val canAskAgain = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_COARSE_LOCATION)
            } ?: false
            if (!canAskAgain) openAppLocationSettings(context)
        }
    }
    val onFixLocation: () -> Unit = {
        safeClick {
            when (readiness) {
                LocationReadiness.PermissionMissing -> locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
                LocationReadiness.ServicesOff -> openLocationSourceSettings(context)
                LocationReadiness.Ready -> {}
            }
        }
    }

    // 2. Annonce distante
    val featureFlags by RemoteFeatureFlags.config
    val canStartDatabaseDownload =
        featureFlags.isDatabaseDownloadAllowed(RemoteFeatureFlags.DatabaseTarget.MOBILE)
    var dismissedHomeAnnouncementKey by remember {
        mutableStateOf(prefs.getString(PREF_HOME_ANNOUNCEMENT_DISMISSED, "") ?: "")
    }

    // 3. Connectivité réseau & serveur
    val isOnline by rememberNetworkConnectivityState()
    val serverStatus by rememberServerReachabilityState(isOnline)
    val isServerUnreachable = isOnline && serverStatus == ServerStatus.UNREACHABLE

    // 4. Notification de mise à jour d'application
    LaunchedEffect(isOnline) {
        if (isOnline) {
            AppUpdateNotifier.checkAndNotify(context.applicationContext)
        }
    }
    DisposableEffect(lifecycleOwner, isOnline) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && isOnline) {
                scope.launch(Dispatchers.IO) {
                    AppUpdateNotifier.checkAndNotify(context.applicationContext)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 5. WorkManager & État de la base de données
    val workManager = remember { WorkManager.getInstance(context) }
    val mobileWorkInfos by workManager
        .getWorkInfosByTagFlow(DatabaseDownloadWorker.WORK_TAG)
        .collectAsState(initial = emptyList())
    val radioWorkInfos by workManager
        .getWorkInfosByTagFlow(RadioDatabaseDownloadWorker.WORK_TAG)
        .collectAsState(initial = emptyList())
    val enbWorkInfos by workManager
        .getWorkInfosByTagFlow(EnbDatabaseDownloadWorker.WORK_TAG)
        .collectAsState(initial = emptyList())
    val activeDatabaseWork = listOf(
        mobileWorkInfos.map { workInfo -> workInfo to DatabaseBulkUpdate.Target.MOBILE },
        radioWorkInfos.map { workInfo -> workInfo to DatabaseBulkUpdate.Target.RADIO },
        enbWorkInfos.map { workInfo -> workInfo to DatabaseBulkUpdate.Target.ENB }
    ).flatten().firstOrNull { (workInfo, _) ->
        workInfo.state == WorkInfo.State.RUNNING ||
            workInfo.state == WorkInfo.State.ENQUEUED ||
            workInfo.state == WorkInfo.State.BLOCKED
    }
    val currentWork = activeDatabaseWork?.first
    val currentWorkTarget = activeDatabaseWork?.second
    val isSyncing = currentWork != null
    val downloadProgress = if (currentWork != null && currentWorkTarget != null) {
        currentWork.progress.getInt(databaseProgressKey(currentWorkTarget), 0)
    } else {
        0
    }

    var isDownloadStarting by remember { mutableStateOf(false) }
    val isDownloading = isSyncing || isDownloadStarting
    var isRebuildStarting by remember { mutableStateOf(false) }
    val localBuild = rememberLocalDbBuildStatus()
    val isGeneratingDb = localBuild.running || isRebuildStarting
    val localDbState by AppConfig.localDatabaseState
    var wasSyncing by remember { mutableStateOf(isSyncing) }
    var isUpdateAvailable by remember { mutableStateOf(false) }
    var availableDatabaseUpdates by remember {
        mutableStateOf<List<DatabaseBulkUpdate.TargetAction>>(emptyList())
    }
    var isRebuildOffer by remember { mutableStateOf(false) }
    val isDbChecked = localDbState != null
    val isDbMissing = localDbState == GeoTowerDatabaseValidator.LocalDatabaseState.MISSING
    val isDbInvalid = localDbState == GeoTowerDatabaseValidator.LocalDatabaseState.INVALID

    LaunchedEffect(localBuild.mobileRunning) {
        if (localBuild.mobileRunning) isRebuildStarting = false
    }

    LaunchedEffect(isSyncing) {
        if (isSyncing) isDownloadStarting = false
        if (!isSyncing && (AppConfig.localDatabaseState.value == null || wasSyncing)) {
            AppConfig.localDatabaseState.value = withContext(Dispatchers.IO) {
                GeoTowerDatabaseValidator.getInstalledDatabaseStatus(context).state
            }
        }
        wasSyncing = isSyncing
    }

    // Vérification réseau à chaque retour sur l'écran (ON_RESUME)
    DisposableEffect(lifecycleOwner, isOnline, isSyncing, localDbState) {
        val observer = LifecycleEventObserver { _, event ->
            if (
                event == Lifecycle.Event.ON_RESUME &&
                isOnline &&
                !isSyncing &&
                localDbState == GeoTowerDatabaseValidator.LocalDatabaseState.VALID
            ) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val remote = DatabaseDownloader.getLatestDatabaseUpdateInfo()
                        val localVersion = GeoTowerDatabaseValidator.getInstalledDatabaseVersion(context)
                        val hasRemoteUpdate = DatabaseDownloader
                            .isRemoteDatabaseUpdateAvailable(context, remote, localVersion)
                        val rebuildOffer = hasRemoteUpdate && LocalDbRebuildOffer.forMobile(context)
                        val mobileUpdateActions = if (hasRemoteUpdate) {
                            listOf(
                                DatabaseBulkUpdate.TargetAction(
                                    DatabaseBulkUpdate.Target.MOBILE,
                                    DatabaseBulkUpdate.Action.UPDATE
                                )
                            )
                        } else {
                            emptyList()
                        }
                        withContext(Dispatchers.Main) {
                            isRebuildOffer = rebuildOffer
                            availableDatabaseUpdates = mobileUpdateActions
                            isUpdateAvailable = mobileUpdateActions.isNotEmpty()
                            AppConfig.isDbUpdateAvailable.value = mobileUpdateActions.isNotEmpty()
                        }
                        val additionalUpdates = DatabaseBulkUpdate
                            .checkAvailableUpdates(context, workManager) { partialResult ->
                                val partialAdditionalUpdates = partialResult.targetActions.filter {
                                    it.action == DatabaseBulkUpdate.Action.UPDATE &&
                                        it.target != DatabaseBulkUpdate.Target.MOBILE
                                }
                                val partialUpdateActions = buildList {
                                    if (hasRemoteUpdate) {
                                        add(
                                            DatabaseBulkUpdate.TargetAction(
                                                DatabaseBulkUpdate.Target.MOBILE,
                                                DatabaseBulkUpdate.Action.UPDATE
                                            )
                                        )
                                    }
                                    addAll(partialAdditionalUpdates)
                                }.distinctBy { it.target }
                                withContext(Dispatchers.Main) {
                                    isRebuildOffer = rebuildOffer
                                    availableDatabaseUpdates = partialUpdateActions
                                    isUpdateAvailable = partialUpdateActions.isNotEmpty()
                                    AppConfig.isDbUpdateAvailable.value = partialUpdateActions.isNotEmpty()
                                }
                            }
                            .targetActions
                            .filter {
                                it.action == DatabaseBulkUpdate.Action.UPDATE &&
                                    it.target != DatabaseBulkUpdate.Target.MOBILE
                            }
                        val updateActions = buildList {
                            if (hasRemoteUpdate) {
                                add(
                                    DatabaseBulkUpdate.TargetAction(
                                        DatabaseBulkUpdate.Target.MOBILE,
                                        DatabaseBulkUpdate.Action.UPDATE
                                    )
                                )
                            }
                            addAll(additionalUpdates)
                        }.distinctBy { it.target }

                        withContext(Dispatchers.Main) {
                            isRebuildOffer = rebuildOffer
                            availableDatabaseUpdates = updateActions
                            isUpdateAvailable = updateActions.isNotEmpty()
                            AppConfig.isDbUpdateAvailable.value = updateActions.isNotEmpty()
                        }
                    } catch (e: Exception) {
                        AppLogger.w(TAG_BANNERS, "Database update check failed", e)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isDbUnavailable = isDbChecked && localDbState != GeoTowerDatabaseValidator.LocalDatabaseState.VALID
    val isDbBannerVisible = isDbUnavailable || isUpdateAvailable || isDownloading || isGeneratingDb
    val hideHomeLogo = isDbBannerVisible || isServerUnreachable

    val languageTag = LocalConfiguration.current.locales[0]?.toLanguageTag()
    val announcement = featureFlags.homeAnnouncement
    val localizedText = announcement.localizedText(languageTag)
    val currentDismissKey = announcement.dismissKey()
    val isAnnouncementVisible = announcement.enabled &&
        announcement.isVisibleForAppVersion(BuildConfig.VERSION_NAME) &&
        localizedText.hasContent() &&
        (!announcement.dismissible || dismissedHomeAnnouncementKey != currentDismissKey)

    val hasBanner = !isOnline || hideHomeLogo || (readiness != LocationReadiness.Ready) || isAnnouncementVisible

    LaunchedEffect(hideHomeLogo, hasBanner) {
        onBannerVisibilityChange?.invoke(hideHomeLogo, hasBanner)
    }

    val isRebuildBanner = isRebuildOffer && isUpdateAvailable && !isDbMissing && !isDbInvalid
    val databaseBannerTargetSection = when {
        isGeneratingDb -> "db_local_build"
        isDownloading -> currentWorkTarget?.let(::databaseSettingsSection) ?: "database"
        isRebuildBanner || AppConfig.dbForcedLocal() -> "db_local_build"
        availableDatabaseUpdates.size == 1 ->
            databaseSettingsSection(availableDatabaseUpdates.first().target)
        else -> "database"
    }

    val databaseNamesByTarget = mapOf(
        DatabaseBulkUpdate.Target.MOBILE to stringResource(R.string.notification_history_type_db_mobile),
        DatabaseBulkUpdate.Target.RADIO to stringResource(R.string.notification_history_type_db_radio),
        DatabaseBulkUpdate.Target.ENB to stringResource(R.string.notification_history_type_db_enb)
    )
    val displayedDatabaseTargets = buildList {
        if (isDbUnavailable) add(DatabaseBulkUpdate.Target.MOBILE)
        if (isGeneratingDb) {
            if (localBuild.mobileRunning) add(DatabaseBulkUpdate.Target.MOBILE)
            if (localBuild.radioRunning) add(DatabaseBulkUpdate.Target.RADIO)
        }
        addAll(availableDatabaseUpdates.map { it.target })
        currentWorkTarget?.let(::add)
    }.distinct()
    val displayedDatabaseNames = displayedDatabaseTargets.mapNotNull(databaseNamesByTarget::get)

    val onDownloadClick: () -> Unit = {
        val updatesToProcess = availableDatabaseUpdates
        isUpdateAvailable = false
        AppConfig.isDbUpdateAvailable.value = false

        if (isRebuildBanner) {
            isRebuildStarting = true
            OperationPauseStore.clear(context, OperationPauseStore.LOCAL_DB_BUILD)
            LocalDbBuildWorker.enqueue(
                workManager,
                mobile = true,
                radioBroadcast = false,
                nonMobileTech = false
            )
            val otherUpdates = updatesToProcess.filter {
                it.target != DatabaseBulkUpdate.Target.MOBILE
            }
            if (otherUpdates.isNotEmpty()) {
                otherUpdates.forEach { update ->
                    OperationPauseStore.clear(context, databasePauseKey(update.target))
                }
                DatabaseBulkUpdate.enqueueDetailed(workManager, otherUpdates)
            }
        } else if (updatesToProcess.isNotEmpty() && canStartDatabaseDownload &&
            RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.DATABASE_DOWNLOAD) &&
            RemoteFeatureFlags.isActionEnabled(RemoteFeatureFlags.Actions.START_DATABASE_DOWNLOAD) &&
            RemoteFeatureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.DATABASE_DOWNLOAD)
        ) {
            updatesToProcess.forEach { update ->
                OperationPauseStore.clear(context, databasePauseKey(update.target))
            }
            isDownloadStarting = true
            if (updatesToProcess.size == 1 &&
                updatesToProcess.first().target == DatabaseBulkUpdate.Target.MOBILE
            ) {
                DatabaseDownloadWorker.enqueue(workManager)
            } else {
                DatabaseBulkUpdate.enqueueDetailed(workManager, updatesToProcess)
            }
        } else if (canStartDatabaseDownload &&
            RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.DATABASE_DOWNLOAD) &&
            RemoteFeatureFlags.isActionEnabled(RemoteFeatureFlags.Actions.START_DATABASE_DOWNLOAD) &&
            RemoteFeatureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.DATABASE_DOWNLOAD)
        ) {
            isDownloadStarting = true
            OperationPauseStore.clear(context, OperationPauseStore.MOBILE_DB_DOWNLOAD)
            DatabaseDownloadWorker.enqueue(workManager)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HomeAnnouncementBanner(
            announcement = featureFlags.homeAnnouncement,
            dismissedKey = dismissedHomeAnnouncementKey,
            onDismiss = { dismissKey ->
                dismissedHomeAnnouncementKey = dismissKey
                prefs.edit().putString(PREF_HOME_ANNOUNCEMENT_DISMISSED, dismissKey).apply()
            }
        )

        AnimatedVisibility(
            visible = !isOnline,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = sizing.spacing(16.dp), start = sizing.spacing(16.dp), end = sizing.spacing(16.dp))
        ) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = sizing.spacing(8.dp))
            ) {
                Row(
                    modifier = Modifier.padding(sizing.spacing(16.dp)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
                    Text(
                        text = stringResource(R.string.appstrings_offline_message),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        ServerUnreachableBanner(visible = isServerUnreachable)

        LocationUnavailableBanner(
            readiness = readiness,
            onFixClick = onFixLocation
        )

        DatabaseWarningBanner(
            isMissing = isDbMissing,
            isInvalid = isDbInvalid,
            isUpdateAvailable = isUpdateAvailable,
            isDownloading = isDownloading,
            isGenerating = isGeneratingDb,
            isRebuildOffer = isRebuildBanner,
            databaseNames = displayedDatabaseNames,
            downloadProgress = if (isGeneratingDb) localBuild.progress else downloadProgress,
            onBannerClick = {
                safeClick("home_database_banner") {
                    navController.navigate("settings?section=$databaseBannerTargetSection") {
                        launchSingleTop = true
                    }
                }
            },
            onDownloadClick = onDownloadClick
        )
    }
}

@Composable
fun HomeAnnouncementBanner(
    announcement: RemoteHomeAnnouncement,
    dismissedKey: String,
    onDismiss: (String) -> Unit
) {
    val sizing = LocalGeoTowerUiSizing.current
    val languageTag = LocalConfiguration.current.locales[0]?.toLanguageTag()
    val localizedText = announcement.localizedText(languageTag)
    val currentDismissKey = announcement.dismissKey()
    val isVisible = announcement.enabled &&
        announcement.isVisibleForAppVersion(BuildConfig.VERSION_NAME) &&
        localizedText.hasContent() &&
        (!announcement.dismissible || dismissedKey != currentDismissKey)
    val actionUrl = announcement.httpActionUrlOrNull()
    val uriHandler = LocalUriHandler.current
    val closeLabel = stringResource(R.string.appstrings_close)
    val openLabel = localizedText.actionLabel.ifBlank { stringResource(R.string.appstrings_open) }

    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = sizing.spacing(16.dp), start = sizing.spacing(16.dp), end = sizing.spacing(16.dp))
    ) {
        val containerColor = when (announcement.severity) {
            "error" -> MaterialTheme.colorScheme.errorContainer
            "warning" -> MaterialTheme.colorScheme.tertiaryContainer
            "success" -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        }
        val contentColor = when (announcement.severity) {
            "error" -> MaterialTheme.colorScheme.onErrorContainer
            "warning" -> MaterialTheme.colorScheme.onTertiaryContainer
            "success" -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        }
        val icon = when (announcement.severity) {
            "error" -> Icons.Default.Error
            "warning" -> Icons.Default.Warning
            "success" -> Icons.Default.CheckCircle
            else -> Icons.Default.Info
        }

        Surface(
            color = containerColor,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = sizing.spacing(8.dp))
        ) {
            Row(
                modifier = Modifier.padding(sizing.spacing(16.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(sizing.component(24.dp))
                )
                Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
                Column(modifier = Modifier.weight(1f)) {
                    if (localizedText.title.isNotBlank()) {
                        Text(
                            text = localizedText.title,
                            color = contentColor,
                            fontWeight = FontWeight.Bold,
                            style = sizing.textStyle(MaterialTheme.typography.titleSmall)
                        )
                    }
                    if (localizedText.message.isNotBlank()) {
                        Text(
                            text = localizedText.message,
                            color = contentColor,
                            style = sizing.textStyle(MaterialTheme.typography.bodySmall)
                        )
                    }
                    if (actionUrl != null) {
                        TextButton(
                            onClick = { uriHandler.openUri(actionUrl) },
                            colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                            contentPadding = PaddingValues(horizontal = sizing.spacing(0.dp), vertical = sizing.spacing(4.dp))
                        ) {
                            Text(openLabel, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(sizing.spacing(6.dp)))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(sizing.component(16.dp))
                            )
                        }
                    }
                }
                if (announcement.dismissible) {
                    IconButton(onClick = { onDismiss(currentDismissKey) }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = closeLabel,
                            tint = contentColor
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DatabaseWarningBanner(
    isMissing: Boolean,
    isInvalid: Boolean,
    isUpdateAvailable: Boolean,
    isDownloading: Boolean,
    isGenerating: Boolean,
    isRebuildOffer: Boolean = false,
    databaseNames: List<String> = emptyList(),
    downloadProgress: Int,
    onBannerClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    val sizing = LocalGeoTowerUiSizing.current
    val normalizedDatabaseNames = databaseNames
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
    val databaseNamesText = normalizedDatabaseNames
        .joinToString(separator = " • ")
    val databaseCount = normalizedDatabaseNames.size
    val databaseSummary = when {
        !isDownloading && isUpdateAvailable && databaseCount > 0 ->
            pluralStringResource(
                R.plurals.appstrings_update_db_banner_summary,
                databaseCount,
                databaseCount
            )
        !isDownloading && (isMissing || isInvalid) && databaseCount > 0 ->
            pluralStringResource(
                R.plurals.appstrings_download_db_banner_summary,
                databaseCount,
                databaseCount
            )
        else -> null
    }
    AnimatedVisibility(
        visible = isMissing || isInvalid || isUpdateAvailable || isDownloading || isGenerating,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(6.dp))
    ) {
        val isProblem = (isMissing || isInvalid) && !isGenerating
        val containerColor = if (isProblem) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
        }
        val contentColor = if (isProblem) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        val accentColor = if (isProblem) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }
        val actionContentColor = if (isProblem) {
            MaterialTheme.colorScheme.onError
        } else {
            MaterialTheme.colorScheme.onPrimary
        }
        val icon = when {
            isGenerating || isRebuildOffer -> Icons.Default.Memory
            isProblem -> Icons.Default.Error
            else -> Icons.Default.CloudDownload
        }

        Surface(
            onClick = onBannerClick,
            color = containerColor,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(12.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = accentColor.copy(alpha = 0.14f),
                    contentColor = accentColor,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(sizing.component(40.dp))
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.padding(sizing.spacing(8.dp))
                    )
                }

                Spacer(Modifier.width(sizing.component(12.dp)))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = when {
                            isGenerating -> stringResource(R.string.appstrings_generating_db_banner_title)
                            isDownloading -> stringResource(R.string.appstrings_downloading_db_banner_title)
                            isInvalid -> stringResource(R.string.appstrings_invalid_db_banner_title)
                            isMissing -> stringResource(R.string.appstrings_missing_db_banner_title)
                            else -> if (databaseCount > 0) {
                                pluralStringResource(
                                    R.plurals.appstrings_update_db_banner_title_plural,
                                    databaseCount,
                                    databaseCount
                                )
                            } else {
                                stringResource(R.string.appstrings_update_db_banner_title)
                            }
                        },
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        style = MaterialTheme.typography.titleSmall
                    )

                    databaseSummary?.let { summary ->
                        Text(
                            text = summary,
                            color = contentColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } ?: databaseNamesText.takeIf { it.isNotBlank() }?.let { names ->
                        Text(
                            text = names,
                            color = contentColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (isGenerating) {
                        Text(
                            text = stringResource(R.string.appstrings_generating_db_banner_desc),
                            color = contentColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (isRebuildOffer && !isDownloading) {
                        Text(
                            text = stringResource(R.string.appstrings_update_db_banner_desc_rebuild),
                            color = contentColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if ((isMissing || isInvalid) && !isDownloading) {
                        Text(
                            text = if (isInvalid) {
                                stringResource(R.string.appstrings_invalid_db_banner_desc)
                            } else {
                                stringResource(R.string.appstrings_missing_db_banner_desc)
                            },
                            color = contentColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (isDownloading || isGenerating) {
                    Row(
                        modifier = Modifier
                            .height(sizing.component(36.dp))
                            .width(sizing.component(100.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(sizing.component(24.dp)),
                            color = contentColor
                        )
                        Spacer(Modifier.width(sizing.component(8.dp)))
                        Text(
                            text = "$downloadProgress %",
                            color = contentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = sizing.text(13.sp)
                        )
                    }
                } else {
                    Button(
                        onClick = onDownloadClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = actionContentColor
                        ),
                        contentPadding = PaddingValues(
                            horizontal = sizing.spacing(12.dp),
                            vertical = sizing.spacing(4.dp)
                        ),
                        modifier = Modifier.height(sizing.component(36.dp))
                    ) {
                        Text(
                            text = stringResource(
                                if (isRebuildOffer) R.string.appstrings_btn_rebuild_banner
                                else if (isUpdateAvailable) R.string.appstrings_btn_update_banner
                                else R.string.appstrings_btn_download_banner
                            ),
                            fontWeight = FontWeight.Bold,
                            fontSize = sizing.text(12.sp)
                        )
                    }
                }
            }
        }
    }
}
