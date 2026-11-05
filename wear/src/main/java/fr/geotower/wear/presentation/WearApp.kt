package fr.geotower.wear.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import fr.geotower.wear.data.location.WatchGpsEngine
import fr.geotower.wear.data.model.GpsFixStatus
import fr.geotower.wear.data.model.WearScreen
import fr.geotower.wear.data.model.WearTower
import fr.geotower.wear.data.repository.WearTowerRepository
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

        var currentScreen by remember { mutableStateOf(WearScreen.RADAR) }
        var inspectedTower by remember { mutableStateOf<WearTower?>(null) }

        // Démarre l'écoute du GPS matériel interne de la montre
        DisposableEffect(Unit) {
            gpsEngine.startListening()
            onDispose {
                gpsEngine.stopListening()
            }
        }

        // Chargement automatique lors du premier fix GPS
        LaunchedEffect(gpsStatus) {
            val status = gpsStatus
            if (status is GpsFixStatus.Fixed && towers.isEmpty() && !isLoading) {
                repository.loadTowers(status.latitude, status.longitude)
            }
        }

        // Filtrage des antennes selon le choix utilisateur (Tous / Orange / SFR / Bouygues / Free)
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

        // Gestion du bouton retour physique / swipe
        BackHandler(enabled = currentScreen != WearScreen.RADAR) {
            if (currentScreen == WearScreen.DETAIL) {
                currentScreen = WearScreen.LIST
            } else {
                currentScreen = WearScreen.RADAR
            }
        }

        Scaffold(
            timeText = { TimeText() },
            modifier = modifier.fillMaxSize().background(Color.Black)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Affichage de l'écran actif
                when (currentScreen) {
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

                    WearScreen.LIST -> {
                        TowerListScreen(
                            towers = filteredTowers,
                            selectedTower = selectedTower,
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            onTowerSelected = { tower ->
                                repository.selectTower(tower.id)
                                currentScreen = WearScreen.RADAR // Verrouille et bascule directement sur le radar
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

                // Barre de menu de navigation rapide (identique à Garmin : Radar / Liste / Réglages)
                if (currentScreen != WearScreen.DETAIL) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 2.dp)
                    ) {
                        QuickNavBar(
                            currentScreen = currentScreen,
                            onNavigate = { screen -> currentScreen = screen }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickNavBar(
    currentScreen: WearScreen,
    onNavigate: (WearScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1C1C1E).copy(alpha = 0.95f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        NavTabItem(
            label = "🧭 Radar",
            isSelected = currentScreen == WearScreen.RADAR,
            onClick = { onNavigate(WearScreen.RADAR) }
        )
        NavTabItem(
            label = "📋 Liste",
            isSelected = currentScreen == WearScreen.LIST,
            onClick = { onNavigate(WearScreen.LIST) }
        )
        NavTabItem(
            label = "⚙️ Réglages",
            isSelected = currentScreen == WearScreen.SETTINGS,
            onClick = { onNavigate(WearScreen.SETTINGS) }
        )
    }
}

@Composable
private fun NavTabItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) MaterialTheme.colors.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else MaterialTheme.colors.onSurfaceVariant
        )
    }
}
