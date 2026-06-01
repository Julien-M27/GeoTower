package fr.geotower.ui.screens.home

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
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppIconManager
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.ui.zIndex
import fr.geotower.BuildConfig
import fr.geotower.R
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.config.RemoteHomeAnnouncement
import fr.geotower.data.db.DatabaseVersionPolicy
import fr.geotower.data.db.GeoTowerDatabaseValidator
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.geotower.data.workers.AppUpdateNotifier
import fr.geotower.data.workers.DatabaseDownloadWorker
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppLogoDrawingResources
import fr.geotower.utils.AppLogger
import fr.geotower.utils.OperatorLogos
import kotlinx.coroutines.launch

private const val TAG_HOME = "GeoTowerDb"
private const val PREF_HOME_ANNOUNCEMENT_DISMISSED = "home_announcement_dismissed_key"

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope() // ✅ AJOUT CRUCIAL ICI
    val logoResId by AppIconManager.currentIconRes
    val safeClick = rememberSafeClick()

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

    val localDbState by AppConfig.localDatabaseState
    var wasSyncing by remember { mutableStateOf(isSyncing) }
    var isUpdateAvailable by remember { mutableStateOf(false) }
    val isDbChecked = localDbState != null
    val isDbMissing = localDbState == GeoTowerDatabaseValidator.LocalDatabaseState.MISSING
    val isDbInvalid = localDbState == GeoTowerDatabaseValidator.LocalDatabaseState.INVALID

    val lifecycleOwner = LocalLifecycleOwner.current // À ajouter avant les Effects

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

    LaunchedEffect(isSyncing) {
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

                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
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
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 16.dp, end = 16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp) // Petite marge sous le bandeau
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.appstrings_offline_message),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ---> 2. LE BANDEAU DE BASE DE DONNÉES (S'affiche juste en dessous si besoin) <---
            val isDbUnavailable = isDbChecked && localDbState != GeoTowerDatabaseValidator.LocalDatabaseState.VALID
            val isDbBannerVisible = isDbUnavailable || isUpdateAvailable || isSyncing
            val isDbReady = localDbState != GeoTowerDatabaseValidator.LocalDatabaseState.MISSING &&
                localDbState != GeoTowerDatabaseValidator.LocalDatabaseState.INVALID &&
                !isSyncing

            DatabaseWarningBanner(
                isMissing = isDbMissing,
                isInvalid = isDbInvalid,
                isUpdateAvailable = isUpdateAvailable,
                isDownloading = isSyncing,
                downloadProgress = downloadProgress,
                onDownloadClick = {
                    // On cache le bandeau et on lance le téléchargement manuel
                    isUpdateAvailable = false
                    AppConfig.isDbUpdateAvailable.value = false

                    if (canStartDatabaseDownload &&
                        RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.DATABASE_DOWNLOAD) &&
                        RemoteFeatureFlags.isActionEnabled(RemoteFeatureFlags.Actions.START_DATABASE_DOWNLOAD) &&
                        RemoteFeatureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.DATABASE_DOWNLOAD)
                    ) {
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
                // Hauteur minimale pour forcer l'espace entre le titre, les boutons et le "À propos"
                val minHeight = maxHeight
                val showHelpButton = prefs.getBoolean("show_home_help", true)
                val helpButtonPosition = prefs.getString("home_help_position", "bottom_end") ?: "bottom_end"
                val helpButtonAlignment = when (helpButtonPosition) {
                    "top_start" -> Alignment.TopStart
                    "top_end" -> Alignment.TopEnd
                    "bottom_start" -> Alignment.BottomStart
                    else -> Alignment.BottomEnd
                }
                val isHelpButtonAtBottom = helpButtonPosition.startsWith("bottom")
                val showBottomHelpButton = showHelpButton && isHelpButtonAtBottom
                val isBottomHelpButtonStart = helpButtonPosition == "bottom_start"
                val helpButtonPadding = if (isHelpButtonAtBottom) {
                    PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 2.dp)
                } else {
                    PaddingValues(20.dp)
                }

                Box(modifier = Modifier.fillMaxSize()) {
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
                                        .size(if (isCompactLandscape) 128.dp else 220.dp)
                                        .clip(RoundedCornerShape(if (isCompactLandscape) 24.dp else 36.dp))
                                )
                                Spacer(modifier = Modifier.height(if (isCompactLandscape) 14.dp else 24.dp))
                                Text(
                                    text = "GeoTower",
                                    style = MaterialTheme.typography.displayLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = if (isCompactLandscape) 44.sp else 64.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(if (isCompactLandscape) 10.dp else 18.dp))
                                LandscapeHomeInfoActions(
                                    navController = navController,
                                    version = appVersion,
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
                                        .verticalScroll(rememberScrollState()),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    MenuButtonsList(
                                        navController = navController,
                                        useOneUi = useOneUi,
                                        buttonBgColor = buttonBgColor,
                                        paleColor = paleColor,
                                        onPaleColor = onPaleColor,
                                        isOnline = isOnline && !isDbBannerVisible,
                                        logoResId = displayLogoResId,
                                        isExpanded = true,
                                        isDbReady = isDbReady,
                                        isGrid = true,
                                        compact = true
                                    )

                                }
                            }
                        }
                    } else if (isExpanded) {
                        // --- DISPOSITION FOLD (TABLETTE) ---
                        val prefsFold = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
                        val showLogo = prefsFold.getBoolean("show_home_logo", true)

                        // ✅ CORRECTION : Si pas de logo OU pas de réseau OU BANDEAU AFFICHE, on passe en mode Grille
                        val isGrid = !showLogo || !isOnline || isDbBannerVisible

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = minHeight)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 32.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.weight(1f))

                            // 📦 BLOC CENTRAL "SOUDÉ" (Titre + Menu)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "GeoTower",
                                    style = MaterialTheme.typography.displayLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 64.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
                                )

                                if (isGrid) {
                                    // --- DISPOSITION EN GRILLE ---
                                    MenuButtonsList(navController, useOneUi, buttonBgColor, paleColor, onPaleColor, isOnline && !isDbBannerVisible, displayLogoResId, isExpanded, isDbReady = isDbReady, isGrid = true)
                                } else {
                                    // --- DISPOSITION CÔTE À CÔTE (Classique) ---
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            DrawableImage(
                                                resId = displayLogoResId,
                                                modifier = Modifier.size(280.dp).clip(RoundedCornerShape(32.dp))
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            // ✅ CORRECTION : On passe "isOnline && !isDbBannerVisible" pour forcer le masquage du logo sur tous les appareils si besoin
                                            MenuButtonsList(navController, useOneUi, buttonBgColor, paleColor, onPaleColor, isOnline && !isDbBannerVisible, displayLogoResId, isExpanded, isDbReady = isDbReady, isGrid = false)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Spacer(modifier = Modifier.height(32.dp))
                            AboutSection(
                                navController = navController,
                                version = appVersion,
                                paleColor = paleColor,
                                showBottomHelpButton = showBottomHelpButton,
                                alignHelpStart = isBottomHelpButtonStart,
                                helpContentColor = onPaleColor,
                                onHelpClick = { safeClick { navController.navigate("help") { launchSingleTop = true } } }
                            )
                        }
                    } else {
                        // --- DISPOSITION SMARTPHONE ---
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = minHeight)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.weight(1f))

                            // 📦 BLOC CENTRAL "SOUDÉ" (Titre + Menu)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "GeoTower",
                                    style = MaterialTheme.typography.displayLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 48.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
                                )
                                // ✅ CORRECTION : On passe la condition ici aussi pour le téléphone
                                MenuButtonsList(navController, useOneUi, buttonBgColor, paleColor, onPaleColor, isOnline && !isDbBannerVisible, displayLogoResId, isExpanded, isDbReady = isDbReady, isGrid = false)
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            AboutSection(
                                navController = navController,
                                version = appVersion,
                                paleColor = paleColor,
                                showBottomHelpButton = showBottomHelpButton,
                                alignHelpStart = isBottomHelpButtonStart,
                                helpContentColor = onPaleColor,
                                onHelpClick = { safeClick { navController.navigate("help") { launchSingleTop = true } } }
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    if (showHelpButton && !isHelpButtonAtBottom) {
                        FloatingActionButton(
                            onClick = { safeClick { navController.navigate("help") { launchSingleTop = true } } },
                            containerColor = paleColor,
                            contentColor = onPaleColor,
                            modifier = Modifier
                                .align(helpButtonAlignment)
                                .padding(helpButtonPadding)
                                .then(if (isHelpButtonAtBottom) Modifier else Modifier.navigationBarsPadding())
                                .zIndex(2f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Help, contentDescription = stringResource(R.string.appstrings_home_help_settings))
                        }
                    }
                }
            }
        }
    }
}

// --- SOUS-COMPOSANTS ---

@Composable
private fun HomeAnnouncementBanner(
    announcement: RemoteHomeAnnouncement,
    dismissedKey: String,
    onDismiss: (String) -> Unit
) {
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
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 16.dp, end = 16.dp)
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
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (localizedText.title.isNotBlank()) {
                        Text(
                            text = localizedText.title,
                            color = contentColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    if (localizedText.message.isNotBlank()) {
                        Text(
                            text = localizedText.message,
                            color = contentColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (actionUrl != null) {
                        TextButton(
                            onClick = { uriHandler.openUri(actionUrl) },
                            colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                        ) {
                            Text(openLabel, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
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
    showHelpButton: Boolean,
    onHelpClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { navController.navigate("about") }) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.nav_about),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            if (showHelpButton) {
                TextButton(onClick = onHelpClick) {
                    Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.nav_help),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Text(
            text = version,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            style = MaterialTheme.typography.labelSmall,
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
    isDbReady: Boolean = true, // ✅ NOUVEAU PARAMÈTRE
    isGrid: Boolean = false,
    compact: Boolean = false
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val menuSize = prefs.getString("menuSize", "normal") ?: "normal"
    val showLogo = prefs.getBoolean("show_home_logo", true)
    val featureFlags by RemoteFeatureFlags.config

    val showNearby = AppConfig.showNearbyPage.value && featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.NEARBY)
    val showMap = AppConfig.showMapPage.value && featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.MAP)
    val showCompass = AppConfig.showCompassPage.value && featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.COMPASS)
    val showStats = AppConfig.showStatsPage.value && featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.STATS)

    var savedOrderString = prefs.getString("pages_order", "nearby,map,compass,stats,settings,logo") ?: "nearby,map,compass,stats,settings,logo"
    if (!savedOrderString.contains("logo")) {
        savedOrderString = "$savedOrderString,logo"
        prefs.edit().putString("pages_order", savedOrderString).apply()
    }
    val pagesOrder = savedOrderString.split(",")
    val nearAntennasLabel = stringResource(R.string.nav_near_antennas)
    val mapLabel = stringResource(R.string.nav_map)
    val compassLabel = stringResource(R.string.nav_compass)
    val statsLabel = stringResource(R.string.nav_statistics)
    val settingsLabel = stringResource(R.string.nav_settings)

    val buttons = mutableListOf<@Composable () -> Unit>()

    pagesOrder.forEach { pageId ->
        when (pageId) {
            "logo" -> {
                if (showLogo && isOnline && !isExpanded) {
                    buttons.add {
                        DrawableImage(
                            resId = logoResId,
                            modifier = Modifier.padding(vertical = 8.dp).size(150.dp).clip(RoundedCornerShape(24.dp))
                        )
                    }
                }
            }
            "nearby" -> {
                if (showNearby) {
                    // ✅ On bloque avec isDbReady
                    buttons.add { MenuButton(nearAntennasLabel, Icons.Default.MyLocation, paleColor, onPaleColor, useOneUi, menuSize, isGrid, compact = compact, enabled = isDbReady) { navController.navigate("emitters") } }
                }
            }
            "map" -> {
                if (showMap) {
                    // ✅ On bloque avec isDbReady
                    buttons.add { MenuButton(mapLabel, Icons.Default.Map, buttonBgColor, MaterialTheme.colorScheme.onSurfaceVariant, useOneUi, menuSize, isGrid, compact = compact, enabled = isDbReady) { navController.navigate("map") } }
                }
            }
            "compass" -> {
                if (showCompass && AppConfig.hasCompass.value) {
                    // ✅ On bloque avec isDbReady
                    buttons.add { MenuButton(compassLabel, Icons.Default.Explore, buttonBgColor, MaterialTheme.colorScheme.onSurfaceVariant, useOneUi, menuSize, isGrid, compact = compact, enabled = isDbReady) { navController.navigate("compass") } }
                }
            }
            "stats" -> {
                if (showStats) {
                    val isStatsEnabled = isDbReady
                    val alpha = if (isStatsEnabled) 1f else 0.5f

                    buttons.add {
                        MenuButton(
                            text = statsLabel,
                            icon = Icons.Default.BarChart,
                            color = buttonBgColor.copy(alpha = alpha),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                            useOneUi = useOneUi,
                            menuSize = menuSize,
                            fillWidth = isGrid,
                            compact = compact,
                            enabled = isStatsEnabled,
                            onClick = { navController.navigate("stats") }
                        )
                    }
                }
            }
            "settings" -> {
                // ✅ PARAMÈTRES RESTE TOUJOURS CLIQUABLE (enabled = true)
                buttons.add { MenuButton(settingsLabel, Icons.Default.Settings, buttonBgColor, MaterialTheme.colorScheme.onSurfaceVariant, useOneUi, menuSize, isGrid, compact = compact, enabled = true) { navController.navigate("settings") } }
            }
        }
    }

    if (isGrid) {
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp), modifier = Modifier.widthIn(max = 800.dp)) {
            for (i in buttons.indices step 2) {
                if (i + 1 < buttons.size) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
                        Box(modifier = Modifier.weight(1f)) { buttons[i]() }
                        Box(modifier = Modifier.weight(1f)) { buttons[i+1]() }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) { buttons[i]() }
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            buttons.forEach { it() }
        }
    }
}

@Composable
fun AboutSection(
    navController: NavController,
    version: String,
    paleColor: Color,
    showBottomHelpButton: Boolean = false,
    alignHelpStart: Boolean = false,
    helpContentColor: Color = Color.Unspecified,
    onHelpClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TextButton(onClick = { navController.navigate("about") }) {
                // Utilise primary pour l'icône au lieu de paleColor
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.nav_about), color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp)
            }
            Text(text = version, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        }
        if (showBottomHelpButton && onHelpClick != null) {
            FloatingActionButton(
                onClick = onHelpClick,
                containerColor = paleColor,
                contentColor = if (helpContentColor == Color.Unspecified) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    helpContentColor
                },
                modifier = Modifier
                    .align(if (alignHelpStart) Alignment.TopStart else Alignment.TopEnd)
                    .padding(top = 12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Help, contentDescription = stringResource(R.string.appstrings_home_help_settings))
            }
        }
    }
}

@Composable
fun MenuButton(
    text: String,
    icon: ImageVector,
    color: Color,
    contentColor: Color,
    useOneUi: Boolean,
    menuSize: String = "normal",
    fillWidth: Boolean = false, // ✅ NOUVEAU
    compact: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val buttonWidth = if (compact) 300.dp else when(menuSize) { "petit" -> 280.dp; "large" -> 330.dp; else -> 320.dp }
    val buttonHeight = if (compact) 66.dp else when(menuSize) { "petit" -> 65.dp; "large" -> 80.dp; else -> 60.dp }
    val iconSize = if (compact) 26.dp else when(menuSize) { "petit" -> 24.dp; "large" -> 28.dp; else -> 28.dp }
    val textSize = if (compact) 17.sp else when(menuSize) { "petit" -> 16.sp; "large" -> 18.sp; else -> 18.sp }

    val buttonShape = if (useOneUi) RoundedCornerShape(if (compact) 24.dp else 28.dp) else RoundedCornerShape(20.dp)
    val buttonElevation = if (useOneUi) 0.dp else 2.dp

    // ✅ NOUVEAU : Si fillWidth est true (Grille), le bouton s'adapte !
    val widthModifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(buttonWidth)

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = widthModifier.height(buttonHeight),
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(iconSize))
            Spacer(modifier = Modifier.width(if (compact) 14.dp else 20.dp))
            Text(text = text, fontSize = textSize, fontWeight = FontWeight.SemiBold)
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
    downloadProgress: Int, // ✅ NOUVEAU PARAMÈTRE
    onDownloadClick: () -> Unit
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = isMissing || isInvalid || isUpdateAvailable || isDownloading,
        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
        // ✅ AJOUT DES MARGES (Comme pour le bandeau hors-ligne)
        modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val containerColor = if (isMissing || isInvalid) {
            androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
        } else {
            androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
        }
        val contentColor = if (isMissing || isInvalid) {
            androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer
        } else {
            androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
        }
        val icon = if (isMissing || isInvalid) Icons.Default.Error else Icons.Default.CloudDownload

        androidx.compose.material3.Surface(
            color = containerColor,
            shape = RoundedCornerShape(16.dp), // ✅ AJOUT DE LA FORME ARRONDIE
            shadowElevation = 2.dp,            // ✅ AJOUT DE L'OMBRE
            modifier = androidx.compose.ui.Modifier.fillMaxWidth()
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor
                )
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(12.dp))
                androidx.compose.foundation.layout.Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                    androidx.compose.material3.Text(
                        text = when {
                            isInvalid -> stringResource(R.string.appstrings_invalid_db_banner_title)
                            isMissing -> stringResource(R.string.appstrings_missing_db_banner_title)
                            else -> stringResource(R.string.appstrings_update_db_banner_title)
                        },
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = contentColor,
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall
                    )
                    if (isMissing || isInvalid) {
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

                // ✅ NOUVEAU : Si ça télécharge, on affiche l'animation WavyLoader + LE POURCENTAGE
                if (isDownloading) {
                    androidx.compose.foundation.layout.Row(
                        modifier = androidx.compose.ui.Modifier.height(36.dp).width(100.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End // On aligne à droite
                    ) {
                        // ✅ CORRECTION : Utilisation de CircularWavyProgressIndicator
                        CircularWavyProgressIndicator(
                            modifier = androidx.compose.ui.Modifier.size(24.dp),
                            color = contentColor
                        )
                        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(8.dp))
                        androidx.compose.material3.Text(
                            text = "$downloadProgress %",
                            color = contentColor,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontSize = 13.sp
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
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = androidx.compose.ui.Modifier.height(36.dp)
                    ) {
                        androidx.compose.material3.Text(
                            text = stringResource(R.string.appstrings_btn_download_banner),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
