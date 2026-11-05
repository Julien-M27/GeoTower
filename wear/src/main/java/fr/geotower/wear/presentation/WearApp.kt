package fr.geotower.wear.presentation

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
import fr.geotower.wear.data.model.WearScreen
import fr.geotower.wear.data.model.WearTower
import fr.geotower.wear.data.repository.WearTowerRepository
import fr.geotower.wear.presentation.screens.HomeScreen
import fr.geotower.wear.presentation.screens.RadarCompassScreen
import fr.geotower.wear.presentation.screens.SettingsScreen
import fr.geotower.wear.presentation.screens.TowerDetailScreen
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
        val towers by repository.towers.collectAsStateWithLifecycle()
        val selectedTowerId by repository.selectedTowerId.collectAsStateWithLifecycle()
        val operatorFilter by repository.operatorFilter.collectAsStateWithLifecycle()
        val isLoading by repository.isLoading.collectAsStateWithLifecycle()
        val errorMessage by repository.errorMessage.collectAsStateWithLifecycle()

        // Page d'accueil par défaut (Menu principal en liste comme sur Garmin)
        var currentScreen by remember { mutableStateOf(WearScreen.HOME) }
        var inspectedTower by remember { mutableStateOf<WearTower?>(null) }

        // Démarre l'écoute du GPS matériel interne de la montre
        DisposableEffect(Unit) {
            gpsEngine.startListening()
            onDispose {
                gpsEngine.stopListening()
            }
        }

        // Chargement automatique dès que le fix GPS de la montre est obtenu
        LaunchedEffect(gpsStatus) {
            val status = gpsStatus
            if (status is GpsFixStatus.Fixed && towers.isEmpty() && !isLoading) {
                repository.loadTowers(status.latitude, status.longitude)
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

        // Gestion du bouton retour physique et geste swipe Wear OS
        BackHandler(enabled = currentScreen != WearScreen.HOME) {
            when (currentScreen) {
                WearScreen.DETAIL -> currentScreen = WearScreen.LIST
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
                            selectedTower = selectedTower,
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            onTowerSelected = { tower ->
                                repository.selectTower(tower.id)
                                currentScreen = WearScreen.RADAR // Verrouille et affiche la boussole
                            },
                            onOpenDetails = { tower ->
                                inspectedTower = tower
                                currentScreen = WearScreen.DETAIL
                            },
                            onRefresh = {
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
                                currentScreen = WearScreen.DETAIL
                            },
                            onNavigateToList = {
                                currentScreen = WearScreen.LIST
                            }
                        )
                    }

                    WearScreen.SETTINGS -> {
                        SettingsScreen(
                            currentSource = dataSourceMode,
                            operatorFilter = operatorFilter,
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
                            onOperatorFilterChanged = { filter ->
                                repository.setOperatorFilter(filter)
                            },
                            onForceRefresh = {
                                val status = gpsStatus
                                if (status is GpsFixStatus.Fixed) {
                                    scope.launch {
                                        repository.loadTowers(status.latitude, status.longitude)
                                    }
                                } else {
                                    gpsEngine.startListening()
                                }
                            }
                        )
                    }

                    WearScreen.DETAIL -> {
                        val towerToShow = inspectedTower ?: selectedTower
                        if (towerToShow != null) {
                            TowerDetailScreen(
                                tower = towerToShow,
                                isTarget = selectedTower?.id == towerToShow.id,
                                onLockTarget = {
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
                }
            }
        }
    }
}
