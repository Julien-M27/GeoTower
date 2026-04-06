package fr.geotower.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import fr.geotower.utils.AppStrings

// On réutilise la carte d'option définie dans SettingsScreen
import fr.geotower.ui.screens.settings.SettingsOptionCard

@Composable
fun MappingOptionsBlock(
    mapProvider: Int, onMapProviderChange: (Int) -> Unit,
    ignStyle: Int, onIgnStyleChange: (Int) -> Unit,
    shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean, safeClick: (() -> Unit) -> Unit
) {
    // 1. Les 4 fournisseurs répartis sur 2 lignes pour ne pas écraser l'interface
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Si on passe sur OSM et qu'on était en Satellite (2), on repasse en Clair (0) automatiquement
            SettingsOptionCard(AppStrings.mapOsm, Icons.Default.Public, mapProvider == 1, { safeClick { onMapProviderChange(1); if (ignStyle == 2) onIgnStyleChange(0) } }, Modifier.weight(1f), shape, border, bubbleColor, useOneUi)
            SettingsOptionCard(AppStrings.mapIgn, Icons.Default.Layers, mapProvider == 0, { safeClick { onMapProviderChange(0) } }, Modifier.weight(1f), shape, border, bubbleColor, useOneUi)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SettingsOptionCard(AppStrings.mapMapLibre, Icons.Default.Explore, mapProvider == 2, { safeClick { onMapProviderChange(2) } }, Modifier.weight(1f), shape, border, bubbleColor, useOneUi)
            SettingsOptionCard(AppStrings.mapTopo, Icons.Default.Terrain, mapProvider == 3, { safeClick { onMapProviderChange(3) } }, Modifier.weight(1f), shape, border, bubbleColor, useOneUi)
        }
    }

    // 2. Sous-options (Clair / Sombre / Sat)
    // S'affiche pour l'IGN (0) ET pour OSM (1)
    AnimatedVisibility(
        visible = mapProvider == 0 || mapProvider == 1 || mapProvider == 2, // <-- MODIFIÉ ICI
        enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }) + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }) + shrinkVertically(shrinkTowards = Alignment.Top)
    ) {
        Column {
            Spacer(Modifier.height(16.dp))
            Text(AppStrings.mapStyle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                SettingsOptionCard(AppStrings.themeLight, Icons.Default.WbSunny, ignStyle == 0, { safeClick { onIgnStyleChange(0) } }, Modifier.weight(1f), shape, border, bubbleColor, useOneUi)
                SettingsOptionCard(AppStrings.themeDark, Icons.Default.NightsStay, ignStyle == 1, { safeClick { onIgnStyleChange(1) } }, Modifier.weight(1f), shape, border, bubbleColor, useOneUi)

                // Le Satellite ne s'affiche QUE si on est sur l'IGN (mapProvider == 0)
                if (mapProvider == 0) {
                    SettingsOptionCard(AppStrings.mapSat, Icons.Default.Image, ignStyle == 2, { safeClick { onIgnStyleChange(2) } }, Modifier.weight(1f), shape, border, bubbleColor, useOneUi)
                }
            }
        }
    }
}