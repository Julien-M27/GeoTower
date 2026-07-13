package fr.geotower.ui.screens.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.geotower.R
import fr.geotower.ui.components.settingsPopupFadingEdge
import fr.geotower.ui.screens.map.MapFiltersControls
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.MapFilterDefaults
import fr.geotower.utils.PreferenceStores
import kotlinx.coroutines.flow.drop

/**
 * Feuille de réglages des filtres carte par DÉFAUT.
 *
 * Elle réutilise exactement la disposition du panneau de filtres de la carte
 * ([MapFiltersControls]). Chaque filtre modifié ici :
 *  - s'applique immédiatement à la carte (écriture directe dans AppConfig + prefs) ;
 *  - devient le nouveau « défaut de référence » du bandeau « Filtres actifs » (via
 *    [MapFilterDefaults.captureFromCurrent]), qui ne s'affichera donc plus tant que la
 *    carte reste sur ces réglages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapFiltersDefaultsSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val bg = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val scrollState = rememberScrollState()
    val sizing = LocalGeoTowerUiStyle.current.sizing

    // Tout changement de filtre effectué dans cette page devient le nouveau défaut.
    // drop(1) ignore l'état initial : ouvrir puis fermer sans rien changer ne fige rien.
    LaunchedEffect(Unit) {
        snapshotFlow { MapFilterDefaults.currentSignature() }
            .drop(1)
            .collect { MapFilterDefaults.captureFromCurrent(prefs) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = bg) {
        if (onBack != null) BackHandler(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .settingsPopupFadingEdge(scrollState)
                .verticalScroll(scrollState)
                .padding(start = sizing.spacing(24.dp), end = sizing.spacing(24.dp), bottom = sizing.spacing(48.dp))
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                } else {
                    Spacer(Modifier.width(sizing.spacing(48.dp)))
                }
                Text(
                    text = stringResource(R.string.appstrings_map_filters_defaults_title),
                    style = sizing.textStyle(MaterialTheme.typography.titleLarge),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(sizing.spacing(48.dp)))
            }

            Text(
                text = stringResource(R.string.appstrings_map_filters_defaults_desc),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = sizing.spacing(4.dp), bottom = sizing.spacing(20.dp)),
                textAlign = TextAlign.Center
            )

            // La disposition complète du panneau de filtres de la carte, réutilisée telle quelle.
            MapFiltersControls(modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(sizing.spacing(24.dp)))
            TextButton(
                onClick = { MapFilterDefaults.resetToFactory(prefs) },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(sizing.spacing(8.dp)))
                Text(
                    text = stringResource(R.string.appstrings_reset_to_default),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(sizing.spacing(16.dp)).navigationBarsPadding())
        }
    }
}
