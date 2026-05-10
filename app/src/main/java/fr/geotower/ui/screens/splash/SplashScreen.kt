package fr.geotower.ui.screens.splash

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import fr.geotower.utils.AppLogger
import fr.geotower.utils.AppIconManager
import fr.geotower.utils.AppStrings
import fr.geotower.utils.AppConfig
import kotlinx.coroutines.*

private const val TAG_SPLASH = "GeoTowerDb"

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SplashScreen(
    navController: NavController,
    nextDestination: String
) {
    val context = LocalContext.current
    val appTitle = "GeoTower"
    val logoResId by AppIconManager.currentIconRes

    LaunchedEffect(Unit) {
        if (logoResId == 0) AppIconManager.getLogoResId(context)

        // Vérification des mises à jour en tâche de fond (accès direct SQLite pour éviter Room au démarrage)
        launch(Dispatchers.IO) {
            try {
                val localStatus = fr.geotower.data.db.GeoTowerDatabaseValidator.getInstalledDatabaseStatus(context)
                withContext(Dispatchers.Main) {
                    AppConfig.localDatabaseState.value = localStatus.state
                }

                val currentVersion = if (localStatus.state == fr.geotower.data.db.GeoTowerDatabaseValidator.LocalDatabaseState.VALID) {
                    fr.geotower.data.db.GeoTowerDatabaseValidator.getInstalledDatabaseVersion(context)
                } else {
                    null
                }

                val latestVersion = fr.geotower.data.api.DatabaseDownloader.getLatestDatabaseVersion()
                val updateAvailable = fr.geotower.data.db.DatabaseVersionPolicy.isRemoteNewer(latestVersion, currentVersion)
                withContext(Dispatchers.Main) {
                    AppConfig.isDbUpdateAvailable.value = updateAvailable
                }
            } catch (e: Exception) {
                AppLogger.w(TAG_SPLASH, "Startup database update check failed", e)
            }
        }

        // Temps d'affichage pour laisser le LoadingIndicator s'animer
        delay(2200L)

        navController.navigate(nextDestination) {
            popUpTo("splash") { inclusive = true }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            DrawableImage(
                resId = logoResId,
                contentDescription = "Logo",
                modifier = Modifier.size(150.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = appTitle,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp
            )

            Spacer(modifier = Modifier.height(60.dp))

            // ✅ NOUVEAU : Loading Indicator Expressive de Material 3
            LoadingIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = AppStrings.loadingApp,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun DrawableImage(
    resId: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                if (resId != 0) setImageResource(resId)
            }
        },
        update = { imageView ->
            if (resId != 0) imageView.setImageResource(resId)
            imageView.contentDescription = contentDescription
        }
    )
}
