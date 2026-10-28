package fr.geotower.ui.screens.more

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import fr.geotower.BuildConfig
import fr.geotower.R
import fr.geotower.data.notifications.NotificationHistoryStore
import fr.geotower.services.LiveTrackingController
import fr.geotower.ui.components.AppLogoImage
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.screens.home.AboutSection
import fr.geotower.ui.screens.home.HomeAboutLink
import fr.geotower.ui.screens.home.HomeHelpCornerTargets
import fr.geotower.ui.screens.home.HomeHelpFab
import fr.geotower.ui.screens.home.HomeMenuSlot
import fr.geotower.ui.screens.home.HomeNotificationsButton
import fr.geotower.ui.screens.home.HomeQuitButton
import fr.geotower.ui.screens.home.MenuButton
import fr.geotower.ui.screens.home.homeHelpAlignment
import fr.geotower.ui.screens.home.homeHelpCornerPadding
import fr.geotower.ui.screens.home.rememberHomeHelpDragState
import fr.geotower.ui.screens.home.rememberHomeMenuReorderState
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppIconManager
import fr.geotower.utils.AppLogoDrawingResources
import fr.geotower.utils.HomePrefs
import fr.geotower.utils.OperatorLogos
import fr.geotower.utils.PreferenceStores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class MoreMenuEntry(
    val id: String,
    val content: @Composable () -> Unit
)

@Composable
fun MoreScreen(navController: NavController) {
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
    val safeBackNavigation = rememberSafeBackNavigation(navController)
    val safeClick = rememberSafeClick()
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        if (!AppConfig.isBottomNavActive()) {
            navController.navigate("home") {
                popUpTo("more") { inclusive = true }
            }
        }
    }

    BackHandler(enabled = !safeBackNavigation.isLocked) {
        safeBackNavigation.navigateBack()
    }

    val onQuit: () -> Unit = {
        safeClick {
            LiveTrackingController.stop(context)
            activity?.finishAndRemoveTask()
        }
    }

    var unreadNotifications by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    val count = withContext(Dispatchers.IO) {
                        NotificationHistoryStore.unreadCount(context)
                    }
                    unreadNotifications = count
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val buttonBgColor = if (uiStyle.useOneUi) {
        uiStyle.bubbleColor
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val paleColor = if (uiStyle.isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    val onPaleColor = if (uiStyle.isDark) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer

    val moreOrder by AppConfig.moreOrder
    val showLogo by AppConfig.moreShowLogo
    val showAbout by AppConfig.moreShowAbout
    val showHelp by AppConfig.moreShowHelp
    val showCompass by AppConfig.moreShowCompass
    val showNearby by AppConfig.moreShowNearby
    val showMap by AppConfig.moreShowMap
    val showTrips by AppConfig.moreShowTrips
    val showStats by AppConfig.moreShowStats
    val reorderEnabled = AppConfig.moreLongPressReorder.value

    val helpButtonPosition by AppConfig.homeHelpPosition
    val helpButtonAlignment = homeHelpAlignment(helpButtonPosition)
    val helpButtonPadding = homeHelpCornerPadding(helpButtonPosition, sizing)
    val helpDragState = rememberHomeHelpDragState { corner ->
        AppConfig.setHomeHelpPosition(prefs, corner)
    }
    val canDragHelpButton = AppConfig.moreLongPressReorder.value

    val logoResId by AppIconManager.currentIconRes
    val homeLogoChoice = prefs.getString("home_logo_choice", "app") ?: "app"
    val appLogoDrawingChoice by AppConfig.appLogoDrawingChoice
    val appLogoDrawingResId = AppLogoDrawingResources.resolve(appLogoDrawingChoice, logoResId, uiStyle.isDark)
    val displayLogoResId = if (homeLogoChoice == "app") {
        appLogoDrawingResId
    } else {
        OperatorLogos.homeLogoChoiceRes(homeLogoChoice, appLogoDrawingResId)
    }

    val versionText = stringResource(R.string.common_version)
    val appVersion = "$versionText ${BuildConfig.VERSION_NAME}"

    val entries = mutableListOf<MoreMenuEntry>()

    moreOrder.forEach { pageId ->
        when (pageId) {
            "logo" -> {
                if (showLogo) {
                    entries.add(MoreMenuEntry(pageId) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = sizing.spacing(8.dp))
                        ) {
                            AppLogoImage(
                                resId = displayLogoResId,
                                modifier = Modifier
                                    .size(sizing.component(150.dp))
                                    .clip(RoundedCornerShape(24.dp))
                            )
                        }
                    })
                }
            }
            "settings" -> {
                entries.add(MoreMenuEntry(pageId) {
                    MenuButton(
                        text = stringResource(R.string.nav_settings),
                        icon = Icons.Default.Settings,
                        color = buttonBgColor,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        useOneUi = uiStyle.useOneUi,
                        fillWidth = true,
                        onClick = { navController.navigate("settings") }
                    )
                })
            }
            "about" -> {
                if (showAbout) {
                    entries.add(MoreMenuEntry(pageId) {
                        HomeAboutLink(fillWidth = false) {
                            navController.navigate("about")
                        }
                    })
                }
            }
            "compass" -> {
                if (showCompass && AppConfig.hasCompass.value) {
                    entries.add(MoreMenuEntry(pageId) {
                        MenuButton(
                            text = stringResource(R.string.nav_compass),
                            icon = Icons.Default.Explore,
                            color = buttonBgColor,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            useOneUi = uiStyle.useOneUi,
                            fillWidth = true,
                            onClick = { navController.navigate("compass") }
                        )
                    })
                }
            }
            "nearby" -> {
                if (showNearby) {
                    entries.add(MoreMenuEntry(pageId) {
                        MenuButton(
                            text = stringResource(R.string.bottom_nav_nearby),
                            icon = Icons.Default.MyLocation,
                            color = buttonBgColor,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            useOneUi = uiStyle.useOneUi,
                            fillWidth = true,
                            onClick = { navController.navigate("emitters") }
                        )
                    })
                }
            }
            "map" -> {
                if (showMap) {
                    entries.add(MoreMenuEntry(pageId) {
                        MenuButton(
                            text = stringResource(R.string.bottom_nav_map),
                            icon = Icons.Default.Map,
                            color = buttonBgColor,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            useOneUi = uiStyle.useOneUi,
                            fillWidth = true,
                            onClick = { navController.navigate("map") }
                        )
                    })
                }
            }
            "trips" -> {
                if (showTrips) {
                    entries.add(MoreMenuEntry(pageId) {
                        MenuButton(
                            text = stringResource(R.string.bottom_nav_trips),
                            icon = Icons.Default.Route,
                            color = buttonBgColor,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            useOneUi = uiStyle.useOneUi,
                            fillWidth = true,
                            onClick = { navController.navigate("trips") }
                        )
                    })
                }
            }
            "stats" -> {
                if (showStats) {
                    entries.add(MoreMenuEntry(pageId) {
                        MenuButton(
                            text = stringResource(R.string.bottom_nav_stats),
                            icon = Icons.Default.BarChart,
                            color = buttonBgColor,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            useOneUi = uiStyle.useOneUi,
                            fillWidth = true,
                            onClick = { navController.navigate("stats") }
                        )
                    })
                }
            }
        }
    }

    val reorderState = rememberHomeMenuReorderState(entries.map { it.id }) { visibleOrder ->
        AppConfig.setMoreOrder(prefs, HomePrefs.reorderVisible(moreOrder, visibleOrder))
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GeoTowerBackTopBar(
                modifier = Modifier.padding(top = sizing.spacing(4.dp)),
                title = stringResource(R.string.bottom_nav_more),
                onBack = { safeBackNavigation.navigateBack() },
                showBackButton = false,
                backgroundColor = MaterialTheme.colorScheme.background,
                navigationIcon = {
                    HomeNotificationsButton(
                        unreadCount = unreadNotifications,
                        onClick = {
                            safeClick {
                                navController.navigate("notification_history") { launchSingleTop = true }
                            }
                        }
                    )
                },
                actions = {
                    val showQuitButton by AppConfig.homeShowQuitButton
                    if (showQuitButton) {
                        HomeQuitButton(onClick = onQuit)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding(), bottom = sizing.component(88.dp))
        ) {
            fr.geotower.ui.components.HomeStatusBanners(navController = navController)

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { helpDragState.onContentPositioned(it) }
                    .padding(horizontal = sizing.spacing(20.dp), vertical = sizing.spacing(16.dp))
            ) {
                val minHeight = maxHeight
                HomeHelpCornerTargets(helpDragState)

                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minHeight)
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    Column(
                        modifier = Modifier
                            .widthIn(max = 500.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(sizing.spacing(16.dp)),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        entries.forEach { entry ->
                            key(entry.id) {
                                HomeMenuSlot(
                                    state = reorderState,
                                    id = entry.id,
                                    enabled = reorderEnabled && entries.size > 1
                                ) {
                                    entry.content()
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    AboutSection(version = appVersion)
                    Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
                }

                if (showHelp) {
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
            }
        }
    }
}
