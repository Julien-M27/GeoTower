package fr.geotower.wear.presentation

import android.location.Location
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import fr.geotower.wear.data.location.WatchGpsEngine
import fr.geotower.wear.data.model.GpsFixStatus
import fr.geotower.wear.data.model.WearOperatorSite
import fr.geotower.wear.data.model.WearScreen
import fr.geotower.wear.data.model.WearTower
import fr.geotower.wear.data.repository.WearTowerRepository
import fr.geotower.wear.presentation.screens.AboutScreen
import fr.geotower.wear.presentation.screens.HomeScreen
import fr.geotower.wear.presentation.screens.MapScreen
import fr.geotower.wear.presentation.screens.OperatorDetailScreen
import fr.geotower.wear.presentation.screens.RadarCompassScreen
import fr.geotower.wear.presentation.screens.SettingsScreen
import fr.geotower.wear.presentation.screens.SupportDetailScreen
import fr.geotower.wear.presentation.screens.TowerListScreen
import fr.geotower.wear.presentation.theme.GeoTowerWearTheme
import kotlinx.coroutines.launch

@Composable
fun WearApp(
    gpsEngine: WatchGpsEngine,
    repository: WearTowerRepository,
    modifier: Modifier = Modifier,
    onRequestPermissions: () -> Unit = {}
) {
    GeoTowerWearTheme {
        val scope = rememberCoroutineScope()

        val gpsStatus by gpsEngine.gpsStatus.collectAsStateWithLifecycle()
        val dataSourceMode by repository.dataSourceMode.collectAsStateWithLifecycle()
        val gpsRefreshInterval by repository.gpsRefreshInterval.collectAsStateWithLifecycle()
        val towers by repository.towers.collectAsStateWithLifecycle()
        val selectedTowerId by repository.selectedTowerId.collectAsStateWithLifecycle()
        val operatorFilter by repository.operatorFilter.collectAsStateWithLifecycle()
        val isLoading by repository.isLoading.collectAsStateWithLifecycle()
        val errorMessage by repository.errorMessage.collectAsStateWithLifecycle()

        // Page d'accueil par défaut (Menu principal en liste comme sur Garmin)
        var currentScreen by remember { mutableStateOf(WearScreen.HOME) }
        var inspectedTower by remember { mutableStateOf<WearTower?>(null) }
        var inspectedOperatorSite by remember { mutableStateOf<WearOperatorSite?>(null) }
        var lastLoadedCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }

        // Démarre l'écoute du GPS matériel interne de la montre avec la fréquence sélectionnée
        LaunchedEffect(gpsRefreshInterval) {
            gpsEngine.setRefreshInterval(gpsRefreshInterval)
        }

        DisposableEffect(Unit) {
            gpsEngine.startListening()
            onDispose {
                gpsEngine.stopListening()
            }
        }

        // Chargement automatique dès que le fix GPS de la montre est obtenu ou qu'on se déplace (> 50m)
        LaunchedEffect(gpsStatus) {
            val status = gpsStatus
            if (status is GpsFixStatus.Fixed) {
                val last = lastLoadedCoords
                val shouldReload = if (last == null) {
                    true
                } else {
                    val distResults = FloatArray(1)
                    Location.distanceBetween(last.first, last.second, status.latitude, status.longitude, distResults)
                    distResults[0] > 50f
                }
                if (shouldReload && !isLoading) {
                    lastLoadedCoords = Pair(status.latitude, status.longitude)
                    repository.loadTowers(status.latitude, status.longitude)
                }
            }
        }

        // Filtrage des antennes selon l'opérateur sélectionné
        val filteredTowers = remember(towers, operatorFilter) {
            val filter = operatorFilter
            if (filter == null) {
                towers
            } else {
                towers.filter { it.operator.uppercase().contains(filter.uppercase()) }
            }
        }

        // Antenne active / cible verrouillée sur le radar
        val selectedTower = remember(filteredTowers, selectedTowerId) {
            filteredTowers.find { it.id == selectedTowerId } ?: filteredTowers.firstOrNull()
        }

        // Gestion du bouton retour physique et geste swipe Wear OS (parcours Garmin)
        BackHandler(enabled = currentScreen != WearScreen.HOME) {
            when (currentScreen) {
                WearScreen.OPERATOR_DETAIL -> currentScreen = WearScreen.SUPPORT_DETAIL
                WearScreen.SUPPORT_DETAIL -> currentScreen = WearScreen.LIST
                WearScreen.ABOUT -> currentScreen = WearScreen.SETTINGS
                else -> currentScreen = WearScreen.HOME
            }
        }

        Scaffold(
            timeText = { TimeText() },
            modifier = modifier.fillMaxSize().background(Color.Black)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (currentScreen) {
                    WearScreen.HOME -> {
                        HomeScreen(
                            towersCount = filteredTowers.size,
                            gpsStatus = gpsStatus,
                            dataSourceMode = dataSourceMode,
                            onNavigate = { screen -> currentScreen = screen }
                        )
                    }

                    WearScreen.LIST -> {
                        TowerListScreen(
                            towers = filteredTowers,
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            onSupportSelected = { tower ->
                                inspectedTower = tower
                                currentScreen = WearScreen.SUPPORT_DETAIL
                            },
                            onRefresh = {
                                gpsEngine.forceRefreshLocation { loc ->
                                    lastLoadedCoords = Pair(loc.latitude, loc.longitude)
                                    scope.launch {
                                        repository.loadTowers(loc.latitude, loc.longitude)
                                    }
                                }
                                val status = gpsStatus
                                if (status is GpsFixStatus.Fixed) {
                                    scope.launch {
                                        repository.loadTowers(status.latitude, status.longitude)
                                    }
                                } else if (status is GpsFixStatus.Error && status.message.contains("Permission")) {
                                    onRequestPermissions()
                                } else {
                                    gpsEngine.startListening()
                                }
                            }
                        )
                    }

                    WearScreen.RADAR -> {
                        RadarCompassScreen(
                            towers = filteredTowers,
                            selectedTower = selectedTower,
                            gpsStatus = gpsStatus,
                            dataSourceMode = dataSourceMode,
                            onTowerSelected = { tower ->
                                repository.selectTower(tower.id)
                            },
                            onOpenDetails = { tower ->
                                inspectedTower = tower
                                currentScreen = WearScreen.SUPPORT_DETAIL
                            },
                            onNavigateToList = {
                                currentScreen = WearScreen.LIST
                            }
                        )
                    }

                    WearScreen.MAP -> {
                        MapScreen(
                            towers = filteredTowers,
                            selectedTower = selectedTower,
                            gpsStatus = gpsStatus,
                            onTowerSelected = { tower ->
                                repository.selectTower(tower.id)
                            },
                            onOpenDetails = { tower ->
                                inspectedTower = tower
                                currentScreen = WearScreen.SUPPORT_DETAIL
                            }
                        )
                    }

                    WearScreen.SETTINGS -> {
                        SettingsScreen(
                            currentSource = dataSourceMode,
                            gpsRefreshInterval = gpsRefreshInterval,
                            gpsStatus = gpsStatus,
                            onSourceChanged = { newSource ->
                                repository.setDataSourceMode(newSource)
                                val status = gpsStatus
                                if (status is GpsFixStatus.Fixed) {
                                    scope.launch {
                                        repository.loadTowers(status.latitude, status.longitude)
                                    }
                                }
                            },
                            onGpsRefreshIntervalChanged = { newInterval ->
                                repository.setGpsRefreshInterval(newInterval)
                            },
                            onForceRefresh = {
                                gpsEngine.forceRefreshLocation { loc ->
                                    lastLoadedCoords = Pair(loc.latitude, loc.longitude)
                                    scope.launch {
                                        repository.loadTowers(loc.latitude, loc.longitude)
                                    }
                                }
                                val status = gpsStatus
                                if (status is GpsFixStatus.Fixed) {
                                    scope.launch {
                                        repository.loadTowers(status.latitude, status.longitude)
                                    }
                                } else {
                                    gpsEngine.startListening()
                                }
                            },
                            onOpenAbout = {
                                currentScreen = WearScreen.ABOUT
                            }
                        )
                    }

                    WearScreen.ABOUT -> {
                        AboutScreen(
                            onBack = {
                                currentScreen = WearScreen.SETTINGS
                            }
                        )
                    }

                    WearScreen.SUPPORT_DETAIL -> {
                        val towerToShow = inspectedTower ?: selectedTower
                        if (towerToShow != null) {
                            SupportDetailScreen(
                                tower = towerToShow,
                                onOperatorClick = { opSite ->
                                    inspectedOperatorSite = opSite
                                    currentScreen = WearScreen.OPERATOR_DETAIL
                                },
                                onOpenCompass = {
                                    repository.selectTower(towerToShow.id)
                                    currentScreen = WearScreen.RADAR
                                },
                                onBack = {
                                    currentScreen = WearScreen.LIST
                                }
                            )
                        } else {
                            currentScreen = WearScreen.LIST
                        }
                    }

                    WearScreen.OPERATOR_DETAIL -> {
                        val towerToShow = inspectedTower ?: selectedTower
                        val opToShow = inspectedOperatorSite ?: towerToShow?.operators?.firstOrNull()
                        if (towerToShow != null && opToShow != null) {
                            val opIndex = (towerToShow.operators.indexOf(opToShow).takeIf { it >= 0 } ?: 0) + 1
                            OperatorDetailScreen(
                                tower = towerToShow,
                                operatorSite = opToShow,
                                operatorIndex = opIndex,
                                totalOperators = towerToShow.operators.size.coerceAtLeast(1),
                                onOpenCompass = {
                                    repository.selectTower(towerToShow.id)
                                    currentScreen = WearScreen.RADAR
                                },
                                onBack = {
                                    currentScreen = WearScreen.SUPPORT_DETAIL
                                }
                            )
                        } else {
                            currentScreen = WearScreen.SUPPORT_DETAIL
                        }
                    }
                }
            }
        }
    }
}
