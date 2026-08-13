package fr.geotower.ui.screens.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import fr.geotower.ui.theme.LocalGeoTowerUiSizing
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppIconManager
import fr.geotower.utils.HomePrefs
import fr.geotower.utils.LocationReadiness
import fr.geotower.utils.locationReadiness
import fr.geotower.utils.openAppLocationSettings
import fr.geotower.utils.openLocationSourceSettings
import fr.geotower.utils.rememberLocationReadinessState
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.ui.zIndex
import fr.geotower.BuildConfig
import fr.geotower.R
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.config.RemoteHomeAnnouncement
import fr.geotower.data.db.DatabaseVersionPolicy
import fr.geotower.data.notifications.NotificationHistoryStore
import fr.geotower.data.db.GeoTowerDatabaseValidator
import fr.geotower.services.LiveTrackingController
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Memory
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.geotower.data.build.LocalDbRebuildOffer
import fr.geotower.data.workers.AppUpdateNotifier
import fr.geotower.data.workers.DatabaseDownloadWorker
import fr.geotower.data.workers.LocalDbBuildWorker
import fr.geotower.data.api.ServerStatus
import fr.geotower.ui.components.LocationUnavailableBanner
import fr.geotower.ui.components.PageScrollEdgeButtons
import fr.geotower.ui.components.ServerUnreachableBanner
import fr.geotower.ui.components.rememberLocalDbBuildStatus
import fr.geotower.ui.components.rememberServerReachabilityState
import fr.geotower.ui.components.pageScrollbar
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.utils.PageScrollPrefs
import fr.geotower.ui.theme.GeoTowerUiSizing
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppLogoDrawingResources
import fr.geotower.utils.AppLogger
import fr.geotower.utils.OperatorLogos
import kotlinx.coroutines.launch

private const val TAG_HOME = "GeoTowerDb"
private const val PREF_HOME_ANNOUNCEMENT_DISMISSED = "home_announcement_dismissed_key"

private data class HomeMenuButtonMetrics(
    val width: Dp,
    val height: Dp,
    val oneUiCornerRadius: Dp,
    val classicCornerRadius: Dp,
    val iconSize: Dp,
    val textSize: TextUnit,
    val horizontalPadding: Dp,
    val iconSpacing: Dp
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun HomeScreen(navController: NavController) {
    val sizing = LocalGeoTowerUiSizing.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope() // ✅ AJOUT CRUCIAL ICI
    val logoResId by AppIconManager.currentIconRes
    val safeClick = rememberSafeClick()

    // ---> BOUTON QUITTER : on arrête le suivi en direct (donc plus de notif live) puis on ferme l'app <---
    // NB : on récupère l'Activity via LocalActivity et non via un cast de LocalContext, car le
    // GeoTowerLocaleProvider remplace LocalContext par un createConfigurationContext() qui ne
    // contient plus l'Activity dans sa chaîne (le cast renverrait null dès qu'une langue est forcée).
    val activity = LocalActivity.current
    val onQuit: () -> Unit = {
        safeClick {
            LiveTrackingController.stop(context)
            activity?.finishAndRemoveTask()
        }
    }

    // ---> DÉTECTION DE LA DISPONIBILITÉ DE LA LOCALISATION <---
    // Réévaluée en continu : permission coupée OU localisation (GPS) désactivée au niveau système.
    // Si l'un des deux manque, on affiche un bandeau d'alerte en haut de l'accueil.
    val locationReadinessState = rememberLocationReadinessState()
    val readiness by locationReadinessState
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        // Recalcule tout de suite le verdict complet (permission + services).
        locationReadinessState.value = locationReadiness(context)
        if (!granted) {
            // Refus définitif (« Ne plus demander ») : la boîte système ne s'affiche plus,
            // on renvoie donc vers les réglages de l'app.
            val canAskAgain = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_COARSE_LOCATION)
            } ?: false
            if (!canAskAgain) openAppLocationSettings(context)
        }
    }
    // Action du bouton : selon la cause, on demande la permission OU on ouvre les réglages de localisation.
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

    // ---> 1. LECTURE DU CHOIX DU LOGO D'ACCUEIL <---
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val homeLogoChoice = prefs.getString("home_logo_choice", "app") ?: "app"
    val featureFlags by RemoteFeatureFlags.config
    val canStartDatabaseDownload =
        featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.DATABASE_DOWNLOAD) &&
            featureFlags.isActionEnabled(RemoteFeatureFlags.Actions.START_DATABASE_DOWNLOAD) &&
            featureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.DATABASE_DOWNLOAD)
    var dismissedHomeAnnouncementKey by remember {
        mutableStateOf(prefs.getString(PREF_HOME_ANNOUNCEMENT_DISMISSED, "") ?: "")
    }


    // --- 1. LECTURE RÉACTIVE DU THÈME ET DESIGN ---
    val uiStyle = LocalGeoTowerUiStyle.current

    val isDark = uiStyle.isDark

    val useOneUi = uiStyle.useOneUi

    // On force la couleur "Pâle" peu importe le thème
    val paleColor = if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    val onPaleColor = if (isDark) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer

    // Couleur de fond de l'écran
    val mainBgColor = uiStyle.backgroundColor

    // Couleur des boutons inactifs (Gris One UI ou Classique)
    val buttonBgColor = if (useOneUi) uiStyle.bubbleColor else MaterialTheme.colorScheme.surfaceVariant

    val appLogoDrawingChoice by AppConfig.appLogoDrawingChoice
    val appLogoDrawingResId = AppLogoDrawingResources.resolve(appLogoDrawingChoice, logoResId, isDark)
    val displayLogoResId = if (homeLogoChoice == "app") {
        appLogoDrawingResId
    } else {
        OperatorLogos.homeLogoChoiceRes(homeLogoChoice, appLogoDrawingResId)
    }

    // ---> 1. AJOUT : ON ÉCOUTE LE RÉSEAU ICI <---
    val isOnline by fr.geotower.utils.rememberNetworkConnectivityState()

    // ---> ÉTAT DU SERVEUR GEOTOWER (réseau présent mais API muette : maintenance, panne, blocage) <---
    // Distinct du bandeau hors-ligne : on ne l'affiche que si l'appareil a bien du réseau.
    val serverStatus by rememberServerReachabilityState(isOnline)
    val isServerUnreachable = isOnline && serverStatus == ServerStatus.UNREACHABLE

    LaunchedEffect(Unit) {
        if (logoResId == 0) AppIconManager.getLogoResId(context)
    }
    // ---> LOGIQUE DE VÉRIFICATION DE LA BASE DE DONNÉES <---
    val workManager = remember { androidx.work.WorkManager.getInstance(context) }

    // ✅ NOUVEAU : On écoute si un téléchargement est déjà en cours
    val workInfos by workManager.getWorkInfosForUniqueWorkFlow(DatabaseDownloadWorker.UNIQUE_WORK_NAME).collectAsState(initial = emptyList())
    val currentWork = workInfos.firstOrNull()
    val isSyncing = currentWork?.state == androidx.work.WorkInfo.State.RUNNING || currentWork?.state == androidx.work.WorkInfo.State.ENQUEUED
    // ✅ NOUVEAU : On récupère la progression de 0 à 100
    val downloadProgress = currentWork?.progress?.getInt(DatabaseDownloadWorker.KEY_PROGRESS, 0) ?: 0

    // ✅ Retour visuel immédiat au clic « Télécharger » : WorkManager met un court instant à passer
    // le worker à ENQUEUED/RUNNING. Ce latch bascule le bandeau en mode « téléchargement » dès le
    // clic, puis est relâché quand isSyncing prend le relais (voir LaunchedEffect plus bas). Sans lui,
    // le bandeau clignoterait (disparition « Mise à jour disponible » → réapparition « Téléchargement en cours »).
    var isDownloadStarting by remember { mutableStateOf(false) }
    val isDownloading = isSyncing || isDownloadStarting

    // Même latch, côté génération locale : le bandeau bascule sur « génération en cours » dès le
    // clic sur « Régénérer », sans attendre que WorkManager passe le worker à ENQUEUED.
    var isRebuildStarting by remember { mutableStateOf(false) }

    // Génération locale en cours : la base mobile est construite dans un fichier à part et n'est
    // installée qu'à la toute fin. Pendant ce temps elle est bel et bien absente du disque — le
    // bandeau doit dire « génération en cours », surtout pas « base manquante ».
    val localBuild = rememberLocalDbBuildStatus()
    val isGeneratingDb = localBuild.mobileRunning || isRebuildStarting

    val localDbState by AppConfig.localDatabaseState
    var wasSyncing by remember { mutableStateOf(isSyncing) }
    var isUpdateAvailable by remember { mutableStateOf(false) }
    // La base installée a été GÉNÉRÉE sur l'appareil : une nouvelle version se propose alors en
    // régénération, pas en téléchargement (vérifié en même temps que la version distante).
    var isRebuildOffer by remember { mutableStateOf(false) }
    val isDbChecked = localDbState != null
    val isDbMissing = localDbState == GeoTowerDatabaseValidator.LocalDatabaseState.MISSING
    val isDbInvalid = localDbState == GeoTowerDatabaseValidator.LocalDatabaseState.INVALID

    val lifecycleOwner = LocalLifecycleOwner.current // À ajouter avant les Effects

    // Pastille du bouton « Notifications ». Relue à chaque ON_RESUME, ce qui couvre les deux cas :
    // le retour du journal (l'ouvrir vaut lecture, la pastille doit retomber) et le retour
    // d'arrière-plan, où un worker a pu notifier pendant ce temps. Dans un NavHost, le
    // LocalLifecycleOwner est celui de la destination, donc l'événement arrive bien dans les deux.
    var unreadNotifications by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    // Lecture d'un fichier JSON : jamais sur le fil principal.
                    val count = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        NotificationHistoryStore.unreadCount(context)
                    }
                    unreadNotifications = count
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ✅ BLOC 1 : On réutilise l'état validé au splash/onboarding et on ne rescane qu'en fallback ou après téléchargement.
    LaunchedEffect(isOnline) {
        if (isOnline) {
            AppUpdateNotifier.checkAndNotify(context.applicationContext)
        }
    }

    DisposableEffect(lifecycleOwner, isOnline) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && isOnline) {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    AppUpdateNotifier.checkAndNotify(context.applicationContext)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(localBuild.mobileRunning) {
        // Idem côté génération : le worker tourne, le latch a fait son office.
        if (localBuild.mobileRunning) isRebuildStarting = false
    }

    LaunchedEffect(isSyncing) {
        // Le worker a démarré (ENQUEUED/RUNNING) : on relâche le latch de retour visuel immédiat.
        if (isSyncing) isDownloadStarting = false
        if (!isSyncing && (AppConfig.localDatabaseState.value == null || wasSyncing)) {
            AppConfig.localDatabaseState.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                GeoTowerDatabaseValidator.getInstalledDatabaseStatus(context).state
            }
        }
        wasSyncing = isSyncing
    }

    // ✅ BLOC 2 : Vérification réseau à CHAQUE retour sur l'écran (ON_RESUME)
    DisposableEffect(lifecycleOwner, isOnline, isSyncing, localDbState) {
        val observer = LifecycleEventObserver { _, event ->
            if (
                event == Lifecycle.Event.ON_RESUME &&
                isOnline &&
                !isSyncing &&
                localDbState == GeoTowerDatabaseValidator.LocalDatabaseState.VALID
            ) {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val remoteVersion = fr.geotower.data.api.DatabaseDownloader.getLatestDatabaseVersion()

                        val localVersion = GeoTowerDatabaseValidator.getInstalledDatabaseVersion(context)
                        val hasRemoteUpdate = DatabaseVersionPolicy.isRemoteNewer(remoteVersion, localVersion)
                        // Base générée sur l'appareil : la nouvelle version se propose en
                        // régénération, la même donnée ANFR étant à portée de build local.
                        val rebuildOffer = hasRemoteUpdate && LocalDbRebuildOffer.forMobile(context)

                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            isRebuildOffer = rebuildOffer
                            if (hasRemoteUpdate) {
                                isUpdateAvailable = true
                                fr.geotower.utils.AppConfig.isDbUpdateAvailable.value = true
                            } else {
                                isUpdateAvailable = false
                                fr.geotower.utils.AppConfig.isDbUpdateAvailable.value = false
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.w(TAG_HOME, "Database update check failed", e)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 1. On récupère juste le chiffre de la version dans le try/catch
    val rawVersion = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.XX"
        } catch (_: Exception) {
            "1.XX"
        }
    }
    // 2. On récupère le mot "Version" traduit
    val versionText = stringResource(R.string.common_version)
    // 3. On assemble les deux !
    val appVersion = "$versionText $rawVersion"

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = mainBgColor // Applique le fond dynamique
    ) {
        // NOUVEAU : Une colonne principale qui contient le bandeau EN HAUT, puis le reste
        Column(modifier = Modifier.fillMaxSize()) {
            HomeAnnouncementBanner(
                announcement = featureFlags.homeAnnouncement,
                dismissedKey = dismissedHomeAnnouncementKey,
                onDismiss = { dismissKey ->
                    dismissedHomeAnnouncementKey = dismissKey
                    prefs.edit().putString(PREF_HOME_ANNOUNCEMENT_DISMISSED, dismissKey).apply()
                }
            )

            // ---> 1. LE BANDEAU HORS-LIGNE (Pousse le contenu vers le bas au lieu de le cacher) <---
            androidx.compose.animation.AnimatedVisibility(
                visible = !isOnline,
                // On utilise expand/shrink pour que ça écarte la page doucement
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
                modifier = Modifier.fillMaxWidth().padding(top = sizing.spacing(16.dp), start = sizing.spacing(16.dp), end = sizing.spacing(16.dp))
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(8.dp)) // Petite marge sous le bandeau
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

            // ---> LE BANDEAU « SERVEUR INJOIGNABLE » (réseau OK, mais l'API GeoTower ne répond pas) <---
            ServerUnreachableBanner(visible = isServerUnreachable)

            // ---> LE BANDEAU DE LOCALISATION DÉSACTIVÉE (permission coupée OU GPS éteint) <---
            LocationUnavailableBanner(
                readiness = readiness,
                onFixClick = onFixLocation
            )

            // ---> 2. LE BANDEAU DE BASE DE DONNÉES (S'affiche juste en dessous si besoin) <---
            val isDbUnavailable = isDbChecked && localDbState != GeoTowerDatabaseValidator.LocalDatabaseState.VALID
            val isDbBannerVisible = isDbUnavailable || isUpdateAvailable || isDownloading || isGeneratingDb
            // Un bandeau occupe le haut de l'écran : on masque le logo (et on passe en grille sur
            // grand écran) comme le font déjà le hors-ligne et le bandeau base de données.
            val hideHomeLogo = isDbBannerVisible || isServerUnreachable

            // Une base générée sur l'appareil se met à jour en la régénérant : le bandeau propose
            // « Régénérer » au lieu de « Télécharger ». Seulement quand la base installée est
            // valide (mise à jour disponible) — manquante ou invalide, on repart du téléchargement.
            val isRebuildBanner = isRebuildOffer && isUpdateAvailable && !isDbMissing && !isDbInvalid

            DatabaseWarningBanner(
                isMissing = isDbMissing,
                isInvalid = isDbInvalid,
                isUpdateAvailable = isUpdateAvailable,
                isDownloading = isDownloading,
                isGenerating = isGeneratingDb,
                isRebuildOffer = isRebuildBanner,
                downloadProgress = if (isGeneratingDb) localBuild.progress else downloadProgress,
                onDownloadClick = {
                    // On cache le bandeau et on lance le téléchargement (ou la régénération) manuel
                    isUpdateAvailable = false
                    AppConfig.isDbUpdateAvailable.value = false

                    if (isRebuildBanner) {
                        // Régénération de la SEULE base annoncée par le bandeau (la base mobile) :
                        // les packs radio se pilotent depuis la carte de génération des réglages.
                        isRebuildStarting = true
                        LocalDbBuildWorker.enqueue(
                            workManager,
                            mobile = true,
                            radioBroadcast = false,
                            nonMobileTech = false
                        )
                    } else if (canStartDatabaseDownload &&
                        RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.DATABASE_DOWNLOAD) &&
                        RemoteFeatureFlags.isActionEnabled(RemoteFeatureFlags.Actions.START_DATABASE_DOWNLOAD) &&
                        RemoteFeatureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.DATABASE_DOWNLOAD)
                    ) {
                        // Retour visuel immédiat : on bascule le bandeau en « Téléchargement en cours »
                        // sans attendre que WorkManager passe le worker à ENQUEUED.
                        isDownloadStarting = true
                        DatabaseDownloadWorker.enqueue(workManager)
                    }
                }
            )

            // ---> 2. LE RESTE DE LA PAGE <---
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val isExpanded = minOf(maxWidth, maxHeight) >= 600.dp
                val isLandscape = maxWidth > maxHeight
                val screenHeightDp = LocalConfiguration.current.screenHeightDp
                val isCompactLandscape = isLandscape && (maxHeight < 700.dp || screenHeightDp < 700)
                // Un Fold déplié en portrait passe le seuil « expanded » (600.dp) mais reste bien
                // plus petit qu'une vraie tablette (~674x830.dp contre ~800x1280.dp). Sans ça il
                // héritait des métriques tablette (titre 64.sp, logo 280.dp, boutons pleine taille),
                // que le facteur d'échelle de largeur (plafonné à 1,15) agrandit encore : l'accueil
                // arrivait « zoomé » et ne tenait plus dans la hauteur. On garde donc les métriques
                // compactes, comme le fait déjà le paysage court.
                val isCompactExpanded = !isLandscape && (maxWidth < 720.dp || maxHeight < 900.dp)
                // Hauteur minimale pour forcer l'espace entre le titre, les boutons et le "À propos"
                val minHeight = maxHeight
                val showHelpButton = prefs.getBoolean("show_home_help", true)
                // Le lien « À propos » est un élément du menu (voir MenuButtonsList) sauf en
                // paysage, où il garde sa place sous le titre : la disposition n'y a pas de pied de
                // page. Le réglage vaut pour les deux.
                val showAboutLink = HomePrefs.showAboutLink.read(prefs) &&
                    featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.ABOUT)
                val helpButtonPosition by AppConfig.homeHelpPosition
                val helpButtonAlignment = homeHelpAlignment(helpButtonPosition)
                val isHelpButtonAtBottom = helpButtonPosition.startsWith("bottom")
                // Le même écart que les cibles du glissé (voir HomeHelpCornerTargets) : le bouton
                // atterrit exactement là où la pastille l'annonçait.
                val helpButtonPadding = homeHelpCornerPadding(helpButtonPosition, sizing)
                // Le bouton « Aides » se déplace aussi par appui long, entre ses quatre coins. Pas
                // en paysage : cette disposition a son propre bouton d'aide, sous le titre.
                val helpDragState = rememberHomeHelpDragState { corner ->
                    AppConfig.setHomeHelpPosition(prefs, corner)
                }
                val canDragHelpButton = AppConfig.homeLongPressReorder.value && !isLandscape

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { helpDragState.onContentPositioned(it) }
                ) {
                    // Les quatre cibles en premier : elles se dessinent sous le reste de la page,
                    // donc sous le bouton qu'on déplace au-dessus d'elles.
                    HomeHelpCornerTargets(helpDragState)

                    // Une seule disposition est composée à la fois ; les trois états sont
                    // déclarés ici pour que la pastille « haut / bas » sache laquelle piloter.
                    val menuScrollState = rememberScrollState()
                    val foldScrollState = rememberScrollState()
                    val phoneScrollState = rememberScrollState()

                    HomeQuitButton(
                        onClick = onQuit,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = sizing.spacing(12.dp), end = sizing.spacing(12.dp))
                            .zIndex(3f)
                    )

                    // Symétrique du bouton « Quitter », dans le coin opposé.
                    HomeNotificationsButton(
                        unreadCount = unreadNotifications,
                        onClick = {
                            safeClick {
                                navController.navigate("notification_history") { launchSingleTop = true }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = sizing.spacing(12.dp), start = sizing.spacing(12.dp))
                            .zIndex(3f)
                    )

                    if (isLandscape) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    horizontal = if (isCompactLandscape) 28.dp else 48.dp,
                                    vertical = if (isCompactLandscape) 12.dp else 24.dp
                                )
                                .navigationBarsPadding(),
                            horizontalArrangement = Arrangement.spacedBy(if (isCompactLandscape) 24.dp else 40.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(if (isCompactLandscape) 0.9f else 1f)
                                    .fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                DrawableImage(
                                    resId = displayLogoResId,
                                    modifier = Modifier
                                        .size(sizing.component(if (isCompactLandscape) 128.dp else 220.dp))
                                        .clip(RoundedCornerShape(if (isCompactLandscape) 24.dp else 36.dp))
                                )
                                Spacer(modifier = Modifier.height(sizing.spacing(if (isCompactLandscape) 14.dp else 24.dp)))
                                Text(
                                    text = "GeoTower",
                                    style = sizing.textStyle(MaterialTheme.typography.displayLarge),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = sizing.text(if (isCompactLandscape) 44.sp else 64.sp),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(sizing.spacing(if (isCompactLandscape) 10.dp else 18.dp)))
                                LandscapeHomeInfoActions(
                                    navController = navController,
                                    version = appVersion,
                                    showAboutLink = showAboutLink,
                                    showHelpButton = showHelpButton,
                                    onHelpClick = { safeClick { navController.navigate("help") { launchSingleTop = true } } }
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(if (isCompactLandscape) 1.35f else 1.25f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier
                                        .widthIn(max = if (isCompactLandscape) 620.dp else 700.dp)
                                        .pageScrollbar(PageScrollPrefs.HOME, menuScrollState)
                                        .verticalScroll(menuScrollState),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    MenuButtonsList(
                                        navController = navController,
                                        useOneUi = useOneUi,
                                        buttonBgColor = buttonBgColor,
                                        paleColor = paleColor,
                                        onPaleColor = onPaleColor,
                                        isOnline = isOnline && !hideHomeLogo,
                                        logoResId = displayLogoResId,
                                        isExpanded = true,
                                        isGrid = true,
                                        compact = true,
                                        // Le lien « À propos » est déjà sous le titre, à gauche.
                                        includeAbout = false
                                    )

                                }
                            }
                        }
                    } else if (isExpanded) {
                        // --- DISPOSITION FOLD (TABLETTE) ---
                        val prefsFold = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
                        val showLogo = prefsFold.getBoolean("show_home_logo", true)

                        // ✅ CORRECTION : Si pas de logo OU pas de réseau OU BANDEAU AFFICHE, on passe en mode Grille
                        val isGrid = !showLogo || !isOnline || hideHomeLogo

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = minHeight)
                                .pageScrollbar(PageScrollPrefs.HOME, foldScrollState)
                                .verticalScroll(foldScrollState)
                                .padding(
                                    horizontal = sizing.spacing(if (isCompactExpanded) 24.dp else 32.dp),
                                    vertical = sizing.spacing(if (isCompactExpanded) 12.dp else 24.dp)
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.weight(1f))

                            // 📦 BLOC CENTRAL "SOUDÉ" (Titre + Menu)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "GeoTower",
                                    style = sizing.textStyle(MaterialTheme.typography.displayLarge),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = sizing.text(if (isCompactExpanded) 46.sp else 64.sp),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = sizing.spacing(if (isCompactExpanded) 20.dp else 32.dp))
                                )

                                if (isGrid) {
                                    // --- DISPOSITION EN GRILLE ---
                                    MenuButtonsList(navController, useOneUi, buttonBgColor, paleColor, onPaleColor, isOnline && !hideHomeLogo, displayLogoResId, isExpanded, isGrid = true, compact = isCompactExpanded)
                                } else {
                                    // --- DISPOSITION CÔTE À CÔTE (Classique) ---
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        // Sur Fold, les colonnes ne sont pas à 50/50 : les boutons ont une
                                        // largeur fixe (métriques compactes) qui ne rentre pas dans la moitié
                                        // d'un écran de ~674.dp, on donne donc la part large au menu.
                                        Column(modifier = Modifier.weight(if (isCompactExpanded) 0.75f else 1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            DrawableImage(
                                                resId = displayLogoResId,
                                                modifier = Modifier
                                                    .size(sizing.component(if (isCompactExpanded) 190.dp else 280.dp))
                                                    .clip(RoundedCornerShape(if (isCompactExpanded) 24.dp else 32.dp))
                                            )
                                        }
                                        Column(modifier = Modifier.weight(if (isCompactExpanded) 1.25f else 1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            // ✅ CORRECTION : On passe "isOnline && !hideHomeLogo" pour forcer le masquage du logo sur tous les appareils si besoin
                                            MenuButtonsList(navController, useOneUi, buttonBgColor, paleColor, onPaleColor, isOnline && !hideHomeLogo, displayLogoResId, isExpanded, isGrid = false, compact = isCompactExpanded)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Spacer(modifier = Modifier.height(sizing.spacing(if (isCompactExpanded) 16.dp else 32.dp)))
                            AboutSection(version = appVersion)
                        }
                    } else {
                        // --- DISPOSITION SMARTPHONE ---
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = minHeight)
                                .pageScrollbar(PageScrollPrefs.HOME, phoneScrollState)
                                .verticalScroll(phoneScrollState)
                                .padding(horizontal = sizing.spacing(24.dp), vertical = sizing.spacing(8.dp)),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.weight(1f))

                            // 📦 BLOC CENTRAL "SOUDÉ" (Titre + Menu)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "GeoTower",
                                    style = sizing.textStyle(MaterialTheme.typography.displayLarge),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = sizing.text(48.sp),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(32.dp))
                                )
                                // ✅ CORRECTION : On passe la condition ici aussi pour le téléphone
                                MenuButtonsList(navController, useOneUi, buttonBgColor, paleColor, onPaleColor, isOnline && !hideHomeLogo, displayLogoResId, isExpanded, isGrid = false)
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            AboutSection(version = appVersion)
                            Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))
                        }
                    }

                    // Les quatre coins sont posés sur la page, y compris ceux du bas : dans le pied
                    // de page ils suivaient le défilement et se retrouvaient à moitié sous le bord
                    // bas dès que l'accueil dépassait d'un cheveu la hauteur de l'écran.
                    // En paysage, seuls les coins du haut : cette disposition a déjà son bouton
                    // d'aide sous le titre, et son pied de page tient sur une ligne.
                    if (showHelpButton && !(isLandscape && isHelpButtonAtBottom)) {
                        HomeHelpFab(
                            state = helpDragState,
                            draggable = canDragHelpButton,
                            containerColor = paleColor,
                            contentColor = onPaleColor,
                            onClick = { safeClick { navController.navigate("help") { launchSingleTop = true } } },
                            modifier = Modifier
                                .align(helpButtonAlignment)
                                .padding(helpButtonPadding)
                                .zIndex(2f)
                        )
                    }

                    PageScrollEdgeButtons(
                        page = PageScrollPrefs.HOME,
                        scrollState = when {
                            isLandscape -> menuScrollState
                            isExpanded -> foldScrollState
                            else -> phoneScrollState
                        }
                    )
                }
            }
        }
    }
}

// --- SOUS-COMPOSANTS ---

@Composable
private fun HomeQuitButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ExitToApp,
            contentDescription = stringResource(R.string.appstrings_home_quit_app),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Journal des notifications reçues, en haut à gauche. La pastille compte les entrées arrivées depuis
 * la dernière ouverture de la page : sans elle, un bouton d'icône dans un coin n'attire jamais l'œil
 * et le journal ne serait jamais consulté.
 */
@Composable
private fun HomeNotificationsButton(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge {
                        // Au-delà de 99 la pastille déborderait du bouton.
                        Text(if (unreadCount > 99) "99+" else unreadCount.toString())
                    }
                }
            }
        ) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = stringResource(R.string.notification_history_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HomeAnnouncementBanner(
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

    androidx.compose.animation.AnimatedVisibility(
        visible = isVisible,
        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
        modifier = Modifier.fillMaxWidth().padding(top = sizing.spacing(16.dp), start = sizing.spacing(16.dp), end = sizing.spacing(16.dp))
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
            modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(8.dp))
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

@Composable
private fun LandscapeHomeInfoActions(
    navController: NavController,
    version: String,
    showAboutLink: Boolean,
    showHelpButton: Boolean,
    onHelpClick: () -> Unit
) {
    val sizing = LocalGeoTowerUiSizing.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showAboutLink) {
                TextButton(onClick = { navController.navigate("about") }) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(sizing.component(18.dp)))
                    Spacer(modifier = Modifier.width(sizing.spacing(6.dp)))
                    Text(
                        text = stringResource(R.string.nav_about),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = sizing.text(14.sp)
                    )
                }
            }

            if (showHelpButton) {
                TextButton(onClick = onHelpClick) {
                    Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, modifier = Modifier.size(sizing.component(18.dp)))
                    Spacer(modifier = Modifier.width(sizing.spacing(6.dp)))
                    Text(
                        text = stringResource(R.string.nav_help),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = sizing.text(14.sp)
                    )
                }
            }
        }

        Text(
            text = version,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            style = sizing.textStyle(MaterialTheme.typography.labelSmall),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun MenuButtonsList(
    navController: NavController,
    useOneUi: Boolean,
    buttonBgColor: Color,
    paleColor: Color,
    onPaleColor: Color,
    isOnline: Boolean,
    logoResId: Int,
    isExpanded: Boolean,
    isGrid: Boolean = false,
    compact: Boolean = false,
    // Faux en paysage seulement : cette disposition garde le lien « À propos » sous le titre.
    includeAbout: Boolean = true
) {
    val sizing = LocalGeoTowerUiSizing.current
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val showLogo = prefs.getBoolean("show_home_logo", true)
    val featureFlags by RemoteFeatureFlags.config

    val showNearby = AppConfig.showNearbyPage.value && featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.NEARBY)
    val showMap = AppConfig.showMapPage.value && featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.MAP)
    // Lu dans les préférences comme « Trajets » : le miroir d'AppConfig n'est rafraîchi qu'à
    // l'ouverture des réglages, et la boussole étant désormais éteinte par défaut, ce décalage
    // afficherait le bouton tant qu'on n'y est pas passé.
    val showCompass = HomePrefs.showCompassPage.read(prefs) &&
        featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.COMPASS)
    val showStats = AppConfig.showStatsPage.value && featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.STATS)
    // Lu directement dans les préférences, comme le lien « À propos » : la liste des trajets est
    // arrivée après coup et n'a pas de miroir observable dans AppConfig.
    val showTrips = HomePrefs.showTripsPage.read(prefs) &&
        featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.TRIPS)
    val showAbout = includeAbout && HomePrefs.showAboutLink.read(prefs) &&
        featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.ABOUT)

    val pagesOrder by AppConfig.pagesOrder
    val nearAntennasLabel = stringResource(R.string.nav_near_antennas)
    val mapLabel = stringResource(R.string.nav_map)
    val compassLabel = stringResource(R.string.nav_compass)
    val statsLabel = stringResource(R.string.nav_statistics)
    val tripsLabel = stringResource(R.string.trips_title)
    val settingsLabel = stringResource(R.string.nav_settings)

    val buttons = mutableListOf<HomeMenuEntry>()

    pagesOrder.forEach { pageId ->
        when (pageId) {
            "logo" -> {
                if (showLogo && isOnline && !isExpanded) {
                    buttons.add(HomeMenuEntry(pageId) {
                        DrawableImage(
                            resId = logoResId,
                            modifier = Modifier.padding(vertical = sizing.spacing(8.dp)).size(sizing.component(150.dp)).clip(RoundedCornerShape(24.dp))
                        )
                    })
                }
            }
            "nearby" -> {
                if (showNearby) {
                    buttons.add(HomeMenuEntry(pageId) { MenuButton(nearAntennasLabel, Icons.Default.MyLocation, paleColor, onPaleColor, useOneUi, isGrid, compact = compact) { navController.navigate("emitters") } })
                }
            }
            "map" -> {
                if (showMap) {
                    buttons.add(HomeMenuEntry(pageId) { MenuButton(mapLabel, Icons.Default.Map, buttonBgColor, MaterialTheme.colorScheme.onSurfaceVariant, useOneUi, isGrid, compact = compact) { navController.navigate("map") } })
                }
            }
            "compass" -> {
                if (showCompass && AppConfig.hasCompass.value) {
                    buttons.add(HomeMenuEntry(pageId) { MenuButton(compassLabel, Icons.Default.Explore, buttonBgColor, MaterialTheme.colorScheme.onSurfaceVariant, useOneUi, isGrid, compact = compact) { navController.navigate("compass") } })
                }
            }
            "stats" -> {
                if (showStats) {
                    buttons.add(HomeMenuEntry(pageId) {
                        MenuButton(
                            text = statsLabel,
                            icon = Icons.Default.BarChart,
                            color = buttonBgColor,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            useOneUi = useOneUi,
                            fillWidth = isGrid,
                            compact = compact,
                            onClick = { navController.navigate("stats") }
                        )
                    })
                }
            }
            "trips" -> {
                if (showTrips) {
                    buttons.add(HomeMenuEntry(pageId) {
                        MenuButton(
                            text = tripsLabel,
                            icon = Icons.Default.Route,
                            color = buttonBgColor,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            useOneUi = useOneUi,
                            fillWidth = isGrid,
                            compact = compact,
                            onClick = { navController.navigate("trips") }
                        )
                    })
                }
            }
            "settings" -> {
                // ✅ PARAMÈTRES RESTE TOUJOURS CLIQUABLE (enabled = true)
                buttons.add(HomeMenuEntry(pageId) { MenuButton(settingsLabel, Icons.Default.Settings, buttonBgColor, MaterialTheme.colorScheme.onSurfaceVariant, useOneUi, isGrid, compact = compact, enabled = true) { navController.navigate("settings") } })
            }
            "about" -> {
                // Élément du menu comme les autres — il se déplace et se masque — mais rendu en
                // lien discret : c'est l'apparence qu'il a toujours eue en pied d'accueil.
                if (showAbout) {
                    buttons.add(HomeMenuEntry(pageId) {
                        HomeAboutLink(fillWidth = isGrid) { navController.navigate("about") }
                    })
                }
            }
        }
    }

    // Appui long = déplacement, sur la page elle-même. Les emplacements possibles sont ceux des
    // éléments affichés : ce qui est masqué (page désactivée, boussole absente) reste accroché à son
    // voisin et n'apparaît pas comme un trou où déposer (voir HomePrefs.reorderVisible).
    val reorderEnabled = AppConfig.homeLongPressReorder.value && buttons.size > 1
    val reorderState = rememberHomeMenuReorderState(buttons.map { it.id }) { visibleOrder ->
        AppConfig.setPagesOrder(prefs, HomePrefs.reorderVisible(pagesOrder, visibleOrder))
    }

    val spacing = if (compact) 10.dp else 16.dp

    // `key` par identifiant, dans les deux dispositions : sans lui, Compose réutilise les
    // composables par position et l'élément saisi changerait d'identité au premier déplacement —
    // son détecteur de geste serait relancé et le glissé s'arrêterait net.
    if (isGrid) {
        HomeMenuGrid(spacing = spacing, modifier = Modifier.widthIn(max = 800.dp)) {
            buttons.forEach { entry ->
                key(entry.id) {
                    HomeMenuSlot(reorderState, entry.id, reorderEnabled) { entry.content() }
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(spacing), horizontalAlignment = Alignment.CenterHorizontally) {
            buttons.forEach { entry ->
                key(entry.id) {
                    HomeMenuSlot(reorderState, entry.id, reorderEnabled) { entry.content() }
                }
            }
        }
    }
}

/** Un élément de l'accueil : son identifiant dans `pages_order` et ce qu'il affiche. */
private data class HomeMenuEntry(
    val id: String,
    val content: @Composable () -> Unit
)

/**
 * La grille des boutons : deux par rangée, un élément seul en fin de liste prenant toute la largeur.
 *
 * C'est une mise en page à plat plutôt qu'une colonne de rangées, et c'est ce qui rend le
 * déplacement possible : tous les éléments sont frères, donc Compose peut déplacer celui qu'on
 * glisse d'une rangée à l'autre sans le détruire, et le `zIndex` de l'élément saisi le fait bien
 * passer au-dessus de tous les autres — entre rangées sœurs, il ne serait monté qu'au-dessus de ses
 * voisins immédiats.
 */
@Composable
private fun HomeMenuGrid(
    spacing: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        // L'accueil ne défile qu'en hauteur : la largeur est bornée partout où la grille sert.
        // Si elle ne l'était pas, on ne pourrait pas la partager en deux — repli sur une colonne.
        val bounded = constraints.hasBoundedWidth
        val gridWidth = if (bounded) constraints.maxWidth else 0
        val cellWidth = ((gridWidth - gap) / 2).coerceAtLeast(0)

        val placeables = measurables.mapIndexed { index, measurable ->
            val alone = !bounded || (index == measurables.lastIndex && index % 2 == 0)
            val itemWidth = if (alone) gridWidth else cellWidth
            measurable.measure(
                if (bounded) {
                    constraints.copy(minWidth = itemWidth, maxWidth = itemWidth, minHeight = 0)
                } else {
                    constraints.copy(minWidth = 0, minHeight = 0)
                }
            )
        }

        val rows = if (bounded) placeables.chunked(2) else placeables.map { listOf(it) }
        val height = rows.sumOf { row -> row.maxOf { it.height } } +
            gap * (rows.size - 1).coerceAtLeast(0)
        val width = if (bounded) gridWidth else (placeables.maxOfOrNull { it.width } ?: 0)

        layout(constraints.constrainWidth(width), constraints.constrainHeight(height)) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                row.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + gap
                }
                y += row.maxOf { it.height } + gap
            }
        }
    }
}

/**
 * Le lien « À propos » de l'accueil.
 *
 * C'est un élément de `pages_order` comme les boutons du menu — il se déplace et se masque de la
 * même façon — mais il garde son apparence de lien discret : il n'ouvre pas une page d'usage
 * courant, et le transformer en gros bouton changerait l'allure de l'accueil pour tout le monde.
 */
@Composable
private fun HomeAboutLink(fillWidth: Boolean, onClick: () -> Unit) {
    val sizing = LocalGeoTowerUiSizing.current
    TextButton(
        onClick = onClick,
        // En grille, la cellule impose sa largeur : sans ça le lien serait collé à gauche.
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier
    ) {
        // Utilise primary pour l'icône au lieu de paleColor
        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
        Text(text = stringResource(R.string.nav_about), color = MaterialTheme.colorScheme.onSurface, fontSize = sizing.text(18.sp))
    }
}

/**
 * Pied de page de l'accueil.
 *
 * Il ne porte plus que le numéro de version : « À propos » a rejoint le menu (voir [HomeAboutLink])
 * et le bouton « Aides » est posé sur la page, aux quatre coins, quelle que soit sa position (voir
 * [HomeHelpFab]). Tant qu'il vivait ici, il descendait avec le pied de page : sur un écran où
 * l'accueil déborde d'un cheveu, il partait sous le pli et n'apparaissait qu'à moitié.
 */
@Composable
fun AboutSection(version: String) {
    val sizing = LocalGeoTowerUiSizing.current
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(text = version, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), style = sizing.textStyle(MaterialTheme.typography.labelSmall))
    }
}

private fun homeMenuButtonMetrics(sizing: GeoTowerUiSizing, compact: Boolean): HomeMenuButtonMetrics {
    val baseWidth = if (compact) 300f else 330f
    val baseHeight = if (compact) 66f else 80f
    val baseOneUiCornerRadius = if (compact) 24f else 28f
    val baseClassicCornerRadius = 20f
    val baseIconSize = if (compact) 26f else 28f
    val baseTextSize = if (compact) 17f else 18f
    val baseHorizontalPadding = 12f
    val baseIconSpacing = if (compact) 14f else 20f

    return HomeMenuButtonMetrics(
        width = sizing.component(baseWidth.dp),
        height = sizing.component(baseHeight.dp),
        oneUiCornerRadius = sizing.component(baseOneUiCornerRadius.dp),
        classicCornerRadius = sizing.component(baseClassicCornerRadius.dp),
        iconSize = sizing.component(baseIconSize.dp),
        textSize = sizing.text(baseTextSize.sp),
        horizontalPadding = sizing.spacing(baseHorizontalPadding.dp),
        iconSpacing = sizing.spacing(baseIconSpacing.dp)
    )
}

@Composable
fun MenuButton(
    text: String,
    icon: ImageVector,
    color: Color,
    contentColor: Color,
    useOneUi: Boolean,
    fillWidth: Boolean = false, // ✅ NOUVEAU
    compact: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val metrics = homeMenuButtonMetrics(sizing, compact)
    val buttonShape = if (useOneUi) RoundedCornerShape(metrics.oneUiCornerRadius) else RoundedCornerShape(metrics.classicCornerRadius)
    val buttonElevation = if (useOneUi) 0.dp else 2.dp

    // ✅ NOUVEAU : Si fillWidth est true (Grille), le bouton s'adapte !
    val widthModifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(metrics.width)

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = widthModifier.height(metrics.height),
        shape = buttonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = contentColor,
            // ✅ NOUVEAU : On applique 50% de transparence si le bouton est inactif
            disabledContainerColor = color.copy(alpha = 0.5f),
            disabledContentColor = contentColor.copy(alpha = 0.5f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = buttonElevation)
    ) {
        Row(
            // On centre le contenu du bouton quand il est très large dans la grille
            horizontalArrangement = if (fillWidth) Arrangement.Center else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = metrics.horizontalPadding)
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(metrics.iconSize))
            Spacer(modifier = Modifier.width(metrics.iconSpacing))
            Text(text = text, fontSize = metrics.textSize, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun DrawableImage(resId: Int, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER; if (resId != 0) setImageResource(resId) } },
        update = { if (resId != 0) it.setImageResource(resId) }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DatabaseWarningBanner(
    isMissing: Boolean,
    isInvalid: Boolean,
    isUpdateAvailable: Boolean,
    isDownloading: Boolean,
    // Génération locale de la base en cours : prime sur « manquante » / « invalide », puisque la
    // base absente est précisément celle en train d'être construite sur l'appareil.
    isGenerating: Boolean,
    // La base installée a été générée sur l'appareil : la mise à jour se propose en « Régénérer »
    // plutôt qu'en « Télécharger » (le bouton lance alors la génération locale).
    isRebuildOffer: Boolean = false,
    downloadProgress: Int, // ✅ NOUVEAU PARAMÈTRE (progression du téléchargement OU de la génération)
    onDownloadClick: () -> Unit
) {
    val sizing = LocalGeoTowerUiSizing.current
    androidx.compose.animation.AnimatedVisibility(
        visible = isMissing || isInvalid || isUpdateAvailable || isDownloading || isGenerating,
        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
        // ✅ AJOUT DES MARGES (Comme pour le bandeau hors-ligne)
        modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp))
    ) {
        // Une génération en cours n'est pas un problème : ni rouge, ni icône d'erreur.
        val isProblem = (isMissing || isInvalid) && !isGenerating
        val containerColor = if (isProblem) {
            androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
        } else {
            androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
        }
        val contentColor = if (isProblem) {
            androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer
        } else {
            androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
        }
        val icon = when {
            // La régénération proposée porte la même icône que la génération elle-même : rien ne
            // sera téléchargé depuis le serveur.
            isGenerating || isRebuildOffer -> Icons.Default.Memory
            isProblem -> Icons.Default.Error
            else -> Icons.Default.CloudDownload
        }

        androidx.compose.material3.Surface(
            color = containerColor,
            shape = RoundedCornerShape(16.dp), // ✅ AJOUT DE LA FORME ARRONDIE
            shadowElevation = 2.dp,            // ✅ AJOUT DE L'OMBRE
            modifier = androidx.compose.ui.Modifier.fillMaxWidth()
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(12.dp)),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor
                )
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(sizing.component(12.dp)))
                androidx.compose.foundation.layout.Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                    androidx.compose.material3.Text(
                        text = when {
                            isGenerating -> stringResource(R.string.appstrings_generating_db_banner_title)
                            isDownloading -> stringResource(R.string.appstrings_downloading_db_banner_title)
                            isInvalid -> stringResource(R.string.appstrings_invalid_db_banner_title)
                            isMissing -> stringResource(R.string.appstrings_missing_db_banner_title)
                            else -> stringResource(R.string.appstrings_update_db_banner_title)
                        },
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = contentColor,
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall
                    )
                    if (isGenerating) {
                        androidx.compose.material3.Text(
                            text = stringResource(R.string.appstrings_generating_db_banner_desc),
                            color = contentColor,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                    } else if (isRebuildOffer && !isDownloading) {
                        androidx.compose.material3.Text(
                            text = stringResource(R.string.appstrings_update_db_banner_desc_rebuild),
                            color = contentColor,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                    } else if ((isMissing || isInvalid) && !isDownloading) {
                        androidx.compose.material3.Text(
                            text = if (isInvalid) {
                                stringResource(R.string.appstrings_invalid_db_banner_desc)
                            } else {
                                stringResource(R.string.appstrings_missing_db_banner_desc)
                            },
                            color = contentColor,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // ✅ NOUVEAU : Si ça télécharge (ou génère), on affiche l'animation WavyLoader + LE POURCENTAGE
                if (isDownloading || isGenerating) {
                    androidx.compose.foundation.layout.Row(
                        modifier = androidx.compose.ui.Modifier.height(sizing.component(36.dp)).width(sizing.component(100.dp)),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End // On aligne à droite
                    ) {
                        // ✅ CORRECTION : Utilisation de CircularWavyProgressIndicator
                        CircularWavyProgressIndicator(
                            modifier = androidx.compose.ui.Modifier.size(sizing.component(24.dp)),
                            color = contentColor
                        )
                        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(sizing.component(8.dp)))
                        androidx.compose.material3.Text(
                            text = "$downloadProgress %",
                            color = contentColor,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontSize = sizing.text(13.sp)
                        )
                    }
                } else {
                    // Sinon, on affiche le bouton normal
                    androidx.compose.material3.Button(
                        onClick = onDownloadClick,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = contentColor,
                            contentColor = containerColor
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = sizing.spacing(12.dp), vertical = sizing.spacing(4.dp)),
                        modifier = androidx.compose.ui.Modifier.height(sizing.component(36.dp))
                    ) {
                        androidx.compose.material3.Text(
                            text = stringResource(
                                if (isRebuildOffer) R.string.appstrings_btn_rebuild_banner
                                else R.string.appstrings_btn_download_banner
                            ),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontSize = sizing.text(12.sp)
                        )
                    }
                }
            }
        }
    }
}
