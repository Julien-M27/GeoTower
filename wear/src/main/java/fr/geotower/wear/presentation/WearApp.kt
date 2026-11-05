package fr.geotower.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import fr.geotower.wear.data.location.WatchGpsEngine
import fr.geotower.wear.data.model.DataSourceMode
import fr.geotower.wear.data.model.GpsFixStatus
import fr.geotower.wear.data.repository.WearTowerRepository
import fr.geotower.wear.presentation.components.GpsStatusHeader
import fr.geotower.wear.presentation.components.SourceToggleChip
import fr.geotower.wear.presentation.components.TowerCard
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
        val listState = rememberScalingLazyListState()
        val scope = rememberCoroutineScope()

        val gpsStatus by gpsEngine.gpsStatus.collectAsStateWithLifecycle()
        val dataSourceMode by repository.dataSourceMode.collectAsStateWithLifecycle()
        val towers by repository.towers.collectAsStateWithLifecycle()
        val isLoading by repository.isLoading.collectAsStateWithLifecycle()
        val errorMessage by repository.errorMessage.collectAsStateWithLifecycle()

        // Démarre l'écoute du GPS matériel interne de la montre
        DisposableEffect(Unit) {
            gpsEngine.startListening()
            onDispose {
                gpsEngine.stopListening()
            }
        }

        // Lorsque le GPS de la montre obtient un fix pour la première fois, charger les antennes
        LaunchedEffect(gpsStatus) {
            val status = gpsStatus
            if (status is GpsFixStatus.Fixed && towers.isEmpty() && !isLoading) {
                repository.loadTowers(status.latitude, status.longitude)
            }
        }

        Scaffold(
            timeText = { TimeText() },
            vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
            modifier = modifier.fillMaxSize().background(MaterialTheme.colors.background)
        ) {
            ScalingLazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Titre de l'application
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
                    ) {
                        Text(
                            text = "🗼 GeoTower",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.primary
                        )
                    }
                }

                // Statut GPS Montre (utilise systématiquement la puce de la montre)
                item {
                    GpsStatusHeader(gpsStatus = gpsStatus)
                }

                // Sélecteur de source de données (Téléphone Room vs Web API)
                item {
                    SourceToggleChip(
                        currentMode = dataSourceMode,
                        onToggle = {
                            val newMode = if (dataSourceMode == DataSourceMode.PHONE_LOCAL) {
                                DataSourceMode.WEB_API
                            } else {
                                DataSourceMode.PHONE_LOCAL
                            }
                            repository.setDataSourceMode(newMode)
                            val status = gpsStatus
                            if (status is GpsFixStatus.Fixed) {
                                scope.launch {
                                    repository.loadTowers(status.latitude, status.longitude)
                                }
                            }
                        }
                    )
                }

                // Indicateur de chargement
                if (isLoading) {
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }

                // Message d'erreur s'il y a lieu
                if (errorMessage != null && !isLoading) {
                    item {
                        Text(
                            text = errorMessage ?: "",
                            fontSize = 10.sp,
                            color = MaterialTheme.colors.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        )
                    }
                }

                // Liste des antennes relais
                if (towers.isNotEmpty()) {
                    item {
                        Text(
                            text = "Antennes proches (${towers.size}) :",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colors.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }

                    items(towers) { tower ->
                        TowerCard(tower = tower)
                    }
                }

                // Bouton Rafraîchir
                item {
                    Spacer(Modifier.height(4.dp))
                    CompactChip(
                        onClick = {
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
                        },
                        label = {
                            Text(
                                text = "🔄 Rafraîchir",
                                fontSize = 11.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
