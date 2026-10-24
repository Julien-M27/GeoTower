package fr.geotower.ui.screens.more

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.geotower.BuildConfig
import fr.geotower.R
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.screens.home.MenuButton
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig

@Composable
fun MoreScreen(navController: NavController) {
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val safeBackNavigation = rememberSafeBackNavigation(navController)

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

    val buttonBgColor = if (uiStyle.useOneUi) {
        uiStyle.bubbleColor
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.bottom_nav_more),
                onBack = { safeBackNavigation.navigateBack() },
                showBackButton = false,
                backgroundColor = MaterialTheme.colorScheme.background
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        // On utilise un padding inférieur constant (espace réservé à la barre de navigation)
        // pour que la disposition reste parfaitement stable lors du clic et de la transition vers les sous-écrans.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding(), bottom = sizing.component(88.dp))
                .padding(horizontal = sizing.spacing(20.dp), vertical = sizing.spacing(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 500.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(sizing.spacing(16.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MenuButton(
                    text = stringResource(R.string.nav_settings),
                    icon = Icons.Default.Settings,
                    color = buttonBgColor,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    useOneUi = uiStyle.useOneUi,
                    fillWidth = true,
                    onClick = { navController.navigate("settings") }
                )

                MenuButton(
                    text = stringResource(R.string.nav_about),
                    icon = Icons.Default.Info,
                    color = buttonBgColor,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    useOneUi = uiStyle.useOneUi,
                    fillWidth = true,
                    onClick = { navController.navigate("about") }
                )

                MenuButton(
                    text = stringResource(R.string.appstrings_help_title),
                    icon = Icons.AutoMirrored.Filled.Help,
                    color = buttonBgColor,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    useOneUi = uiStyle.useOneUi,
                    fillWidth = true,
                    onClick = { navController.navigate("help") }
                )
            }

            Text(
                text = "GeoTower v${BuildConfig.VERSION_NAME}",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                style = sizing.textStyle(MaterialTheme.typography.labelMedium),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = sizing.spacing(8.dp))
            )
        }
    }
}
