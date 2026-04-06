package fr.geotower.ui.screens.emitters

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.geotower.data.AnfrRepository
import fr.geotower.utils.AppConfig

@Composable
fun SupportSiteWrapperScreen(
    navController: NavController,
    repository: AnfrRepository,
    supportId: Long
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isReady by remember { mutableStateOf(false) } // ✅ NOUVEAU : État de chargement
    var selectedSiteId by remember { mutableStateOf<Long?>(null) }
    val displayStyle by AppConfig.displayStyle

    // ✅ LE FIX EST ICI : On force la mise à jour GPS en mémoire avant d'afficher la page
    LaunchedEffect(supportId) {
        isReady = false
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
                val savedLat = prefs.getFloat("clicked_lat", 0f).toDouble()
                val savedLon = prefs.getFloat("clicked_lon", 0f).toDouble()

                // On cherche l'antenne correspondant à l'ID scanné
                val antennas = repository.getAntennasByExactId(supportId.toString())
                if (antennas.isNotEmpty()) {
                    var site = antennas.find {
                        Math.abs(it.latitude - savedLat) < 0.005 && Math.abs(it.longitude - savedLon) < 0.005
                    }

                    if (site == null) {
                        // Récupération manuelle du GPS pour le Wrapper avec vérification des permissions
                        val locManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager

                        // ✅ AJOUT : Vérification explicite de la permission GPS
                        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.ACCESS_FINE_LOCATION
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                        // On demande la position uniquement si la permission est accordée
                        val userLoc = if (hasPermission) {
                            try { locManager.getProviders(true).mapNotNull { locManager.getLastKnownLocation(it) }.maxByOrNull { it.time } } catch (e: Exception) { null }
                        } else null

                        site = if (userLoc != null) {
                            antennas.minByOrNull {
                                val dLat = it.latitude - userLoc.latitude; val dLon = it.longitude - userLoc.longitude
                                (dLat * dLat) + (dLon * dLon)
                            }
                        } else {
                            antennas.first()
                        }
                    }

                    // On met à jour la mémoire instantanément !
                    prefs.edit()
                        .putFloat("clicked_lat", site!!.latitude.toFloat())
                        .putFloat("clicked_lon", site!!.longitude.toFloat())
                        .apply()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        isReady = true
    }

    // ✅ ÉCRAN DE CHARGEMENT : On bloque l'affichage tant que la position n'est pas à jour
    if (!isReady) {
        Box(modifier = Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
        }
        return
    }

    // Si le mode fractionné est activé (1) ET qu'un site est cliqué, on réduit la largeur à 50%
    val isSplitActive = displayStyle == 1 && selectedSiteId != null

    val supportWidthFraction by animateFloatAsState(
        targetValue = if (isSplitActive) 0.5f else 1f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "split_screen_anim"
    )

    Row(modifier = Modifier.fillMaxSize()) {
        // --- MOITIÉ GAUCHE (Ou plein écran) : DÉTAIL DU SUPPORT ---
        Box(modifier = Modifier.fillMaxWidth(supportWidthFraction)) {
            SupportDetailScreen(
                navController = navController,
                repository = repository,
                siteId = supportId,
                onAntennaClick = { id ->
                    if (displayStyle == 1) {
                        // Bascule de l'état d'ouverture/fermeture
                        if (selectedSiteId == id) {
                            selectedSiteId = null // Ferme si on clique sur le même site
                        } else {
                            selectedSiteId = id // Ouvre ou change de site
                        }
                    } else {
                        navController.navigate("site_detail/$id") // Navigation normale
                    }
                }
            )
        }

        // --- MOITIÉ DROITE : DÉTAIL DU SITE AVEC ANIMATION DE GLISSEMENT ---
        AnimatedVisibility(
            visible = isSplitActive,
            // ✅ MODIFICATION ICI : Glisse depuis la droite vers la gauche
            enter = slideInHorizontally(
                initialOffsetX = { it }, // 'it' est la largeur complète, donc commence juste hors écran à droite
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            ),
            // ✅ MODIFICATION ICI : Glisse vers la droite hors de l'écran
            exit = slideOutHorizontally(
                targetOffsetX = { it }, // 'it' est la largeur complète, donc va juste hors écran à droite
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            ),
            modifier = Modifier.weight(1f) // Prend la place restante (les 50%)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // --- BARRE DE SÉPARATION VERTICALE ---
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant)
                )

                Box(modifier = Modifier.weight(1f)) {
                    // Vérification de nullité indispensable car l'animation de sortie
                    // se joue alors que selectedSiteId est déjà redevenu null.
                    if (selectedSiteId != null) {
                        SiteDetailScreen(
                            navController = navController,
                            repository = repository,
                            antennaId = selectedSiteId!!,
                            isSplitScreen = true,
                            onCloseSplitScreen = { selectedSiteId = null } // Déclenche l'animation de fermeture
                        )
                    }
                }
            }
        }
    }
}