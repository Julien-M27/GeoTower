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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.res.pluralStringResource
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
import fr.geotower.data.workers.DatabaseBulkUpdate
import fr.geotower.data.workers.DatabaseDownloadWorker
import fr.geotower.data.workers.EnbDatabaseDownloadWorker
import fr.geotower.data.workers.LocalDbBuildWorker
import fr.geotower.data.workers.OperationPauseStore
import fr.geotower.data.workers.RadioDatabaseDownloadWorker
import fr.geotower.data.api.ServerStatus
import fr.geotower.ui.components.LocationUnavailableBanner
import fr.geotower.ui.components.PageScrollEdgeButtons
import fr.geotower.ui.components.ServerUnreachableBanner
import fr.geotower.ui.components.AppLogoImage
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
    LaunchedEffect(Unit) {
        if (AppConfig.isBottomNavActive()) {
            navController.navigate("map") {
                popUpTo("home") { inclusive = true }
            }
        }
    }
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

    var hideHomeLogo by remember { mutableStateOf(false) }
    var hasHomeBannerFromComponent by remember { mutableStateOf(false) }

    // ---> 1. LECTURE DU CHOIX DU LOGO D'ACCUEIL <---
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val homeLogoChoice = prefs.getString("home_logo_choice", "app") ?: "app"
    val featureFlags by RemoteFeatureFlags.config

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

    val lifecycleOwner = LocalLifecycleOwner.current

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
            fr.geotower.ui.components.HomeStatusBanners(
                navController = navController,
                onBannerVisibilityChange = { hideLogo, hasBanner ->
                    hideHomeLogo = hideLogo
                    hasHomeBannerFromComponent = hasBanner
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
                val isSideBySide = isLandscape || isExpanded
                val isCompactSideBySide = isCompactLandscape || isCompactExpanded
                // Hauteur minimale pour forcer l'espace entre le titre, les boutons et le "À propos"
                val minHeight = maxHeight
                val hasHomeBanner = !isOnline || hideHomeLogo || hasHomeBannerFromComponent
                val sideBySideFallbackMinHeight = sizing.component(if (isCompactSideBySide) 520.dp else 680.dp)
                val sideBySideMenuUsesGrid = hasHomeBanner && minHeight < sideBySideFallbackMinHeight
                val showHelpButton = prefs.getBoolean("show_home_help", true)
                // Le lien « À propos » est un élément du menu (voir MenuButtonsList) sauf en
                // disposition côte à côte (paysage, tablettes et folds), où il garde sa place
                // sous le titre : la disposition n'y a pas de pied de page. Le réglage vaut pour les deux.
                val showAboutLink = HomePrefs.showAboutLink.read(prefs) &&
                    featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.ABOUT)
                val helpButtonPosition by AppConfig.homeHelpPosition
                val helpButtonAlignment = homeHelpAlignment(helpButtonPosition)
                val isHelpButtonAtBottom = helpButtonPosition.startsWith("bottom")
                // Le même écart que les cibles du glissé (voir HomeHelpCornerTargets) : le bouton
                // atterrit exactement là où la pastille l'annonçait.
                val helpButtonPadding = homeHelpCornerPadding(helpButtonPosition, sizing)
                // Le bouton « Aides » se déplace aussi par appui long, entre ses quatre coins. Pas
                // en côte à côte : cette disposition a son propre bouton d'aide, sous le titre.
                val helpDragState = rememberHomeHelpDragState { corner ->
                    AppConfig.setHomeHelpPosition(prefs, corner)
                }
                val canDragHelpButton = AppConfig.homeLongPressReorder.value && !isSideBySide

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { helpDragState.onContentPositioned(it) }
                ) {
                    // Les quatre cibles en premier : elles se dessinent sous le reste de la page,
                    // donc sous le bouton qu'on déplace au-dessus d'elles.
                    HomeHelpCornerTargets(helpDragState)

                    // Deux états de défilement : côte à côte (paysage, tablettes, folds) ou smartphone portrait.
                    val menuScrollState = rememberScrollState()
                    val phoneScrollState = rememberScrollState()

                    val showQuitButton by AppConfig.homeShowQuitButton
                    if (showQuitButton) {
                        HomeQuitButton(
                            onClick = onQuit,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = sizing.spacing(12.dp), end = sizing.spacing(12.dp))
                                .zIndex(3f)
                        )
                    }

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

                    // Disposition côte à côte : paysage ainsi que folds et tablettes (vertical comme horizontal).
                    // Le logo, le titre, le lien « À propos », l'aide et la version sont à gauche ; le menu est à droite.
                    if (isSideBySide) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    horizontal = if (isCompactSideBySide) 28.dp else 48.dp,
                                    vertical = if (isCompactSideBySide) 12.dp else 24.dp
                                )
                                .navigationBarsPadding(),
                            horizontalArrangement = Arrangement.spacedBy(if (isCompactSideBySide) 24.dp else 40.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(if (isCompactSideBySide) 0.9f else 1f)
                                    .fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                DrawableImage(
                                    resId = displayLogoResId,
                                    modifier = Modifier
                                        .size(sizing.component(if (isCompactSideBySide) 128.dp else 220.dp))
                                        .clip(RoundedCornerShape(if (isCompactSideBySide) 24.dp else 36.dp))
                                )
                                Spacer(modifier = Modifier.height(sizing.spacing(if (isCompactSideBySide) 14.dp else 24.dp)))
                                Text(
                                    text = "GeoTower",
                                    style = sizing.textStyle(MaterialTheme.typography.displayLarge),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = sizing.text(if (isCompactSideBySide) 44.sp else 64.sp),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(sizing.spacing(if (isCompactSideBySide) 10.dp else 18.dp)))
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
                                    .weight(if (isCompactSideBySide) 1.35f else 1.25f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier
                                        .widthIn(max = if (isCompactSideBySide) 620.dp else 700.dp)
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
                                        isGrid = sideBySideMenuUsesGrid,
                                        compact = isCompactSideBySide,
                                        // Le lien « À propos » est déjà sous le titre, à gauche.
                                        includeAbout = false
                                    )
                                }
                            }
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
                            Spacer(modifier = Modifier.height(sizing.spacing(24.dp) + androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
                        }
                    }

                    // Les quatre coins sont posés sur la page, y compris ceux du bas : dans le pied
                    // de page ils suivaient le défilement et se retrouvaient à moitié sous le bord
                    // bas dès que l'accueil dépassait d'un cheveu la hauteur de l'écran.
                    // En mode côte à côte, seuls les coins du haut : cette disposition a déjà son bouton
                    // d'aide sous le titre, et son pied de page tient sur une ligne.
                    if (showHelpButton && !(isSideBySide && isHelpButtonAtBottom)) {
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
                        scrollState = if (isSideBySide) menuScrollState else phoneScrollState
                    )
                }
            }
        }
    }
}

// --- SOUS-COMPOSANTS ---

@Composable
internal fun HomeQuitButton(
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
internal fun HomeNotificationsButton(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BadgedBox(
        modifier = modifier,
        badge = {
            if (unreadCount > 0) {
                Badge(
                    modifier = Modifier.offset(x = (-4).dp, y = 6.dp)
                ) {
                    // Au-delà de 99 la pastille déborderait du bouton.
                    Text(
                        text = notificationBadgeText(unreadCount),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    ) {
        IconButton(onClick = onClick) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = stringResource(R.string.notification_history_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun notificationBadgeText(unreadCount: Int): String =
    if (unreadCount > 99) "99+" else unreadCount.toString()

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
internal fun HomeAboutLink(fillWidth: Boolean = false, onClick: () -> Unit) {
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
    AppLogoImage(resId = resId, modifier = modifier)
}