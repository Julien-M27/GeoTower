package fr.geotower.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.geotower.R
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.PageScrollEdgeButtons
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.components.pageScrollbar
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.screens.about.PhotoUploadHistoryShortcut
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.PageScrollPrefs

/**
 * Point d'entrée unique vers les journaux locaux de l'application : photos envoyées et sites ou
 * supports partagés. « À propos » comme les Réglages n'ont qu'un seul bouton « Historiques » à
 * proposer ; les deux pages ne sont plus listées séparément.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoriesScreen(
    onNavigateBack: () -> Unit,
    onOpenPhotoUploadHistory: () -> Unit,
    onOpenShareHistory: () -> Unit,
    onOpenNotificationHistory: () -> Unit
) {
    val safeClick = rememberSafeClick()
    val scrollState = rememberScrollState()
    var showSettingsSheet by remember { mutableStateOf(false) }
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    BackHandler { safeClick("histories_back") { onNavigateBack() } }

    val themeMode by AppConfig.themeMode
    val isOled by AppConfig.isOledMode
    val isDark = themeMode == 2 || (themeMode == 0 && isSystemInDarkTheme())
    val pageColor = if (isDark && isOled) Color.Black else MaterialTheme.colorScheme.background
    val cardColor = if (AppConfig.useOneUiDesign) {
        if (isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    val cardShape = if (AppConfig.useOneUiDesign) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)
    val sizing = LocalGeoTowerUiStyle.current.sizing

    Scaffold(
        containerColor = pageColor,
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.histories_title),
                onBack = { safeClick("histories_back") { onNavigateBack() } },
                backgroundColor = pageColor,
                actions = {
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.appstrings_settings_title),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .geoTowerFadingEdge(scrollState, fadeHeight = sizing.component(72.dp))
                    .pageScrollbar(PageScrollPrefs.HISTORIES, scrollState)
                    .verticalScroll(scrollState)
                    .padding(
                        PaddingValues(
                            start = sizing.spacing(16.dp),
                            top = sizing.spacing(16.dp),
                            end = sizing.spacing(16.dp),
                            bottom = sizing.spacing(16.dp) +
                                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        )
                    ),
                verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
            ) {
                Text(
                    text = stringResource(R.string.histories_desc),
                    style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                PhotoUploadHistoryShortcut(
                    cardShape = cardShape,
                    cardColor = cardColor,
                    onOpenHistory = onOpenPhotoUploadHistory
                )
                ShareHistoryShortcut(
                    cardShape = cardShape,
                    cardColor = cardColor,
                    onOpenHistory = onOpenShareHistory
                )
                NotificationHistoryShortcut(
                    cardShape = cardShape,
                    cardColor = cardColor,
                    onOpenHistory = onOpenNotificationHistory
                )
            }

            PageScrollEdgeButtons(PageScrollPrefs.HISTORIES, scrollState)
        }
    }

    if (showSettingsSheet) {
        HistoryPageSettingsSheet(
            title = stringResource(R.string.histories_settings_title),
            page = PageScrollPrefs.HISTORIES,
            options = emptyList(),
            onReset = {},
            onDismiss = { showSettingsSheet = false },
            onBack = { showSettingsSheet = false },
            sheetState = settingsSheetState,
            useOneUi = AppConfig.useOneUiDesign,
            bubbleColor = cardColor
        )
    }
}
