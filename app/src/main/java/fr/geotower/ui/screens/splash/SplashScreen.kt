package fr.geotower.ui.screens.splash

import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import fr.geotower.ui.components.SequentialWavyLoader
import fr.geotower.utils.AppIconManager
import fr.geotower.utils.AppStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SplashScreen(
    navController: NavController,
    nextDestination: String
) {
    val context = LocalContext.current
    val scale = remember { Animatable(0f) }

    // --- 1. LOGIQUE RÉACTIVE (Titre & Logo) ---
    val appTitle = "GeoTower"
    val logoResId by AppIconManager.currentIconRes

    LaunchedEffect(key1 = true) {
        if (logoResId == 0) AppIconManager.getLogoResId(context)

        // ✅ CORRECTION : Vérification des mises à jour en accès direct à la BDD
        launch(Dispatchers.IO) {
            try {
                // 1. On récupère la version sur le serveur
                val latestVersion = fr.geotower.data.api.DatabaseDownloader.getLatestDatabaseVersion()

                // 2. On lit le fichier SQLite local SANS utiliser Room (Anti-Crash)
                var currentVersion: String? = null
                val dbPath = context.getDatabasePath("geotower.db")

                if (dbPath.exists()) {
                    val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                        dbPath.absolutePath,
                        null,
                        android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                    )
                    val cursor = db.rawQuery("SELECT version FROM metadata LIMIT 1", null)
                    if (cursor.moveToFirst()) {
                        currentVersion = cursor.getString(0)
                    }
                    cursor.close()
                    db.close()
                }

                // 3. Si les versions sont différentes, on affiche la bannière
                if (latestVersion != null && latestVersion != currentVersion) {
                    withContext(Dispatchers.Main) {
                        fr.geotower.utils.AppConfig.isDbUpdateAvailable.value = true
                    }
                }
            } catch (e: Exception) {
                // Pas d'internet, BDD corrompue ou serveur HS : On ignore pour laisser l'app s'ouvrir
                e.printStackTrace()
            }
        }

        // Animation d'entrée
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 800,
                easing = { OvershootInterpolator(2f).getInterpolation(it) }
            )
        )

        // On patiente un peu pour que l'utilisateur voie bien le logo
        delay(1200L)

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
            // Utilisation de logoResId (réactif)
            DrawableImage(
                resId = logoResId,
                contentDescription = "Logo",
                modifier = Modifier
                    .size(150.dp)
                    .scale(scale.value)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // TITRE FIXE
            Text(
                text = appTitle,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                modifier = Modifier.scale(scale.value)
            )

            Spacer(modifier = Modifier.height(40.dp))

            SequentialWavyLoader(
                modifier = Modifier.size(42.dp),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = AppStrings.loadingApp,
                style = MaterialTheme.typography.bodySmall,
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