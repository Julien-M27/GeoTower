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
    val rawFixLocation = rememberLocationFixAction(locationReadinessState)
    val onFixLocation: () -> Unit = { safeClick { rawFixLocation() } }

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
    var isFrUpdateAvailable by remember { mutableStateOf(false) }
    var isCaUpdateAvailable by remember { mutableStateOf(false) }
    var availableDatabaseUpdates by remember {
        mutableStateOf<List<DatabaseBulkUpdate.TargetAction>>(emptyList())
    }
    var isRebuildOffer by remember { mutableStateOf(false) }
    var frDbState by remember { mutableStateOf<GeoTowerDatabaseValidator.LocalDatabaseState?>(null) }
    var caDbState by remember { mutableStateOf<GeoTowerDatabaseValidator.LocalDatabaseState?>(null) }

    LaunchedEffect(localBuild.mobileRunning) {
        if (localBuild.mobileRunning) isRebuildStarting = false
    }

    LaunchedEffect(isSyncing) {
        if (isSyncing) isDownloadStarting = false
        val (frState, caState, activeState) = withContext(Dispatchers.IO) {
            Triple(
                GeoTowerDatabaseValidator.getInstalledDatabaseStatus(
                    context,
                    country = fr.geotower.data.db.DatabaseCountry.FR
                ).state,
                GeoTowerDatabaseValidator.getInstalledDatabaseStatus(
                    context,
                    country = fr.geotower.data.db.DatabaseCountry.CA
                ).state,
                GeoTowerDatabaseValidator.getInstalledDatabaseStatus(context).state
            )
        }
        frDbState = frState
        caDbState = caState
        if (!isSyncing && (AppConfig.localDatabaseState.value == null || wasSyncing)) {
            AppConfig.localDatabaseState.value = activeState
        }
        wasSyncing = isSyncing
    }

    // Vérification réseau à chaque retour sur l'écran (ON_RESUME)
    DisposableEffect(lifecycleOwner, isOnline, isSyncing, frDbState, caDbState) {
        val observer = LifecycleEventObserver { _, event ->
            if (
                event == Lifecycle.Event.ON_RESUME &&
                isOnline &&
                !isSyncing &&
                (frDbState == GeoTowerDatabaseValidator.LocalDatabaseState.VALID ||
                    caDbState == GeoTowerDatabaseValidator.LocalDatabaseState.VALID)
            ) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val frValid = frDbState == GeoTowerDatabaseValidator.LocalDatabaseState.VALID
                        val caValid = caDbState == GeoTowerDatabaseValidator.LocalDatabaseState.VALID

                        val hasFrUpdate = if (frValid) {
                            val remoteFr = DatabaseDownloader.getLatestDatabaseUpdateInfo(
                                country = fr.geotower.data.db.DatabaseCountry.FR
                            )
                            val localFrVersion = GeoTowerDatabaseValidator.getInstalledDatabaseVersion(
                                context,
                                fr.geotower.data.db.DatabaseCountry.FR
                            )
                            DatabaseDownloader.isRemoteDatabaseUpdateAvailable(context, remoteFr, localFrVersion)
                        } else {
                            false
                        }
                        val hasCaUpdate = if (caValid) {
                            val remoteCa = DatabaseDownloader.getLatestDatabaseUpdateInfo(
                                country = fr.geotower.data.db.DatabaseCountry.CA
                            )
                            val localCaVersion = GeoTowerDatabaseValidator.getInstalledDatabaseVersion(
                                context,
                                fr.geotower.data.db.DatabaseCountry.CA
                            )
                            DatabaseDownloader.isRemoteDatabaseUpdateAvailable(context, remoteCa, localCaVersion)
                        } else {
                            false
                        }

                        val rebuildOffer = hasFrUpdate && LocalDbRebuildOffer.forMobile(context)
                        val hasAnyMobileUpdate = hasFrUpdate || hasCaUpdate
                        val mobileUpdateActions = if (hasAnyMobileUpdate) {
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
                            isFrUpdateAvailable = hasFrUpdate
                            isCaUpdateAvailable = hasCaUpdate
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
                                    if (hasAnyMobileUpdate) {
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
                            if (hasAnyMobileUpdate) {
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

    val activeCountry by fr.geotower.data.db.DatabaseCountryStore.activeCountryState
    val isCanadaActive = activeCountry == fr.geotower.data.db.DatabaseCountry.CA
    val isDbChecked = frDbState != null && caDbState != null
    val isFrMissing = frDbState == GeoTowerDatabaseValidator.LocalDatabaseState.MISSING
    val isCaMissing = caDbState == GeoTowerDatabaseValidator.LocalDatabaseState.MISSING
    val isFrInvalid = frDbState == GeoTowerDatabaseValidator.LocalDatabaseState.INVALID
    val isCaInvalid = caDbState == GeoTowerDatabaseValidator.LocalDatabaseState.INVALID
    val effectiveDbMissing = isDbChecked && isFrMissing && isCaMissing
    val effectiveDbInvalid = isDbChecked && (isFrInvalid || isCaInvalid)
    val effectiveUpdateAvailable = isUpdateAvailable || isFrUpdateAvailable || isCaUpdateAvailable
    val isDbUnavailable = effectiveDbMissing || effectiveDbInvalid
    val isDbBannerVisible = isDbUnavailable || effectiveUpdateAvailable || isDownloading || isGeneratingDb
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

    val isCurrentMobileCaDownload = currentWorkTarget == DatabaseBulkUpdate.Target.MOBILE && (
        currentWork?.tags?.contains(DatabaseDownloadWorker.countryTag(fr.geotower.data.db.DatabaseCountry.CA)) == true ||
            (currentWork?.tags?.none { it.startsWith("database_download_country_") } == true && isCanadaActive)
        )
    val isRebuildBanner = isRebuildOffer && effectiveUpdateAvailable && !effectiveDbMissing && !effectiveDbInvalid
    val databaseBannerTargetSection = when {
        isGeneratingDb -> "db_local_build"
        isDownloading -> if (isCurrentMobileCaDownload) "db_mobile_ca" else (currentWorkTarget?.let(::databaseSettingsSection) ?: "database")
        isRebuildBanner || (!isCanadaActive && AppConfig.dbForcedLocal()) -> "db_local_build"
        (isCaInvalid || isCaUpdateAvailable) && !isFrInvalid && !isFrUpdateAvailable -> "db_mobile_ca"
        (isFrInvalid || isFrUpdateAvailable) && !isCaInvalid && !isCaUpdateAvailable -> "db_mobile"
        availableDatabaseUpdates.size == 1 ->
            databaseSettingsSection(availableDatabaseUpdates.first().target)
        else -> "database"
    }

    val mobileBaseName = stringResource(R.string.notification_history_type_db_mobile)
    val databaseNamesByTarget = mapOf(
        DatabaseBulkUpdate.Target.MOBILE to "$mobileBaseName (France)",
        DatabaseBulkUpdate.Target.RADIO to "${stringResource(R.string.notification_history_type_db_radio)} (France)",
        DatabaseBulkUpdate.Target.ENB to "${stringResource(R.string.notification_history_type_db_enb)} (France)"
    )
    val displayedDatabaseNames = buildList {
        if (effectiveDbMissing && !isGeneratingDb) {
            add(mobileBaseName)
        }
        if (isFrInvalid || isFrUpdateAvailable) {
            add("$mobileBaseName (France)")
        }
        if (isCaInvalid || isCaUpdateAvailable) {
            add("$mobileBaseName (Canada)")
        }
        if (isGeneratingDb) {
            if (localBuild.mobileRunning) {
                add(if (localBuild.countryCode == "CA") "$mobileBaseName (Canada)" else "$mobileBaseName (France)")
            }
            if (localBuild.radioRunning) add(databaseNamesByTarget.getValue(DatabaseBulkUpdate.Target.RADIO))
        }
        availableDatabaseUpdates.forEach { action ->
            if (action.target != DatabaseBulkUpdate.Target.MOBILE) {
                databaseNamesByTarget[action.target]?.let(::add)
            }
        }
        currentWorkTarget?.let { target ->
            if (isCurrentMobileCaDownload) {
                add("$mobileBaseName (Canada)")
            } else {
                databaseNamesByTarget[target]?.let(::add)
            }
        }
    }.distinct()

    val onDownloadClick: () -> Unit = {
        val updatesToProcess = availableDatabaseUpdates
        val frNeedsDownload = isFrInvalid || isFrUpdateAvailable
        val caNeedsDownload = isCaInvalid || isCaUpdateAvailable
        isUpdateAvailable = false
        isFrUpdateAvailable = false
        isCaUpdateAvailable = false
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
        } else if ((updatesToProcess.isNotEmpty() || frNeedsDownload || caNeedsDownload) && canStartDatabaseDownload &&
            RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.DATABASE_DOWNLOAD) &&
            RemoteFeatureFlags.isActionEnabled(RemoteFeatureFlags.Actions.START_DATABASE_DOWNLOAD) &&
            RemoteFeatureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.DATABASE_DOWNLOAD)
        ) {
            updatesToProcess.forEach { update ->
                OperationPauseStore.clear(context, databasePauseKey(update.target))
            }
            if (frNeedsDownload || caNeedsDownload) {
                OperationPauseStore.clear(context, OperationPauseStore.MOBILE_DB_DOWNLOAD)
            }
            isDownloadStarting = true
            if (frNeedsDownload) {
                DatabaseDownloadWorker.enqueue(
                    workManager,
                    country = fr.geotower.data.db.DatabaseCountry.FR
                )
            }
            if (caNeedsDownload) {
                DatabaseDownloadWorker.enqueue(
                    workManager,
                    country = fr.geotower.data.db.DatabaseCountry.CA
                )
            }
            val otherUpdates = updatesToProcess.filter {
                it.target != DatabaseBulkUpdate.Target.MOBILE
            }
            if (otherUpdates.isNotEmpty()) {
                DatabaseBulkUpdate.enqueueDetailed(workManager, otherUpdates)
            } else if (!frNeedsDownload && !caNeedsDownload && updatesToProcess.isNotEmpty()) {
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
            isMissing = effectiveDbMissing,
            isInvalid = effectiveDbInvalid,
            isUpdateAvailable = effectiveUpdateAvailable,
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
        !isDownloading && !isGenerating && isUpdateAvailable && databaseCount > 0 ->
            pluralStringResource(
                R.plurals.appstrings_update_db_banner_summary,
                databaseCount,
                databaseCount
            )
        !isDownloading && !isGenerating && (isMissing || isInvalid) && databaseCount > 0 ->
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
                    }
                    databaseNamesText.takeIf { it.isNotBlank() }?.let { names ->
                        Text(
                            text = names,
                            color = contentColor,
                            fontWeight = FontWeight.Medium,
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
