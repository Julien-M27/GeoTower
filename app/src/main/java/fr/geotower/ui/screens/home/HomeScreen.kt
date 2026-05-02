package fr.geotower.ui.screens.home

import android.annotation.SuppressLint
import android.content.Context
import android.widget.ImageView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppIconManager
import fr.geotower.utils.AppStrings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.ui.zIndex
import fr.geotower.R
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope() // ✅ AJOUT CRUCIAL ICI
    val logoResId by AppIconManager.currentIconRes

    // ---> 1. LECTURE DU CHOIX DU LOGO D'ACCUEIL <---
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val homeLogoChoice = prefs.getString("home_logo_choice", "app") ?: "app"

    // On associe le choix à la bonne image
    val displayLogoResId = when (homeLogoChoice) {
        "orange" -> R.drawable.logo_orange
        "sfr" -> R.drawable.logo_sfr
        "bouygues" -> R.drawable.logo_bouygues
        "free" -> R.drawable.logo_free
        else -> logoResId // Si "app", on garde le logo actuel de l'application
    }

    // --- 1. LECTURE RÉACTIVE DU THÈME ET DESIGN ---
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val forceOneUi by AppConfig.forceOneUiTheme

    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)

    val useOneUi = forceOneUi
    val isOled = isOledMode

    // On force la couleur "Pâle" peu importe le thème
    val paleColor = if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    val onPaleColor = if (isDark) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer

    // Couleur de fond de l'écran
    val mainBgColor = if (isDark && isOled) Color.Black else MaterialTheme.colorScheme.background

    // Couleur des boutons inactifs (Gris One UI ou Classique)
    val buttonBgColor = if (useOneUi) {
        if (isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    // ---> 1. AJOUT : ON ÉCOUTE LE RÉSEAU ICI <---
    val isOnline by fr.geotower.utils.rememberNetworkConnectivityState()

    LaunchedEffect(Unit) {
        if (logoResId == 0) AppIconManager.getLogoResId(context)
    }
    // ---> LOGIQUE DE VÉRIFICATION DE LA BASE DE DONNÉES <---
    val workManager = remember { androidx.work.WorkManager.getInstance(context) }

    // ✅ NOUVEAU : On écoute si un téléchargement est déjà en cours
    val workInfos by workManager.getWorkInfosForUniqueWorkFlow("db_download").collectAsState(initial = emptyList())
    val currentWork = workInfos.firstOrNull()
    val isSyncing = currentWork?.state == androidx.work.WorkInfo.State.RUNNING || currentWork?.state == androidx.work.WorkInfo.State.ENQUEUED
    // ✅ NOUVEAU : On récupère la progression de 0 à 100
    val downloadProgress = currentWork?.progress?.getInt("progress", 0) ?: 0

    var isDbMissing by remember { mutableStateOf(false) }
    var isUpdateAvailable by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current // À ajouter avant les Effects

    // ✅ BLOC 1 : Vérification physique (Fichier présent ?)
    LaunchedEffect(isSyncing) {
        if (!isSyncing) {
            val dbFile = context.getDatabasePath("geotower.db")
            isDbMissing = !dbFile.exists() || dbFile.length() < 100 * 1024
        }
    }

    // ✅ BLOC 2 : Vérification réseau à CHAQUE retour sur l'écran (ON_RESUME)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && isOnline && !isSyncing && !isDbMissing) {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        // On récupère la taille distante
                        val remoteSize = fr.geotower.data.api.DatabaseDownloader.getDatabaseSize()

                        // On calcule la taille locale
                        val dbFile = context.getDatabasePath("geotower.db")
                        val localSize = if (dbFile.exists()) dbFile.length() / (1024.0 * 1024.0) else 0.0

                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            // Comparaison des tailles (marge d'erreur de 0.01 Mo)
                            if (remoteSize > 0.0 && Math.abs(remoteSize - localSize) > 0.01) {
                                isUpdateAvailable = true
                                fr.geotower.utils.AppConfig.isDbUpdateAvailable.value = true
                            } else {
                                isUpdateAvailable = false
                                fr.geotower.utils.AppConfig.isDbUpdateAvailable.value = false
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 1. On récupère juste le chiffre de la version dans le try/catch
    val rawVersion = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.XX"
        } catch (_: Exception) {
            "1.XX"
        }
    }
    // 2. On récupère le mot "Version" traduit
    val versionText = AppStrings.version
    // 3. On assemble les deux !
    val appVersion = "$versionText $rawVersion"

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = mainBgColor // Applique le fond dynamique
    ) {
        // NOUVEAU : Une colonne principale qui contient le bandeau EN HAUT, puis le reste
        Column(modifier = Modifier.fillMaxSize()) {

            // ---> 1. LE BANDEAU HORS-LIGNE (Pousse le contenu vers le bas au lieu de le cacher) <---
            androidx.compose.animation.AnimatedVisibility(
                visible = !isOnline,
                // On utilise expand/shrink pour que ça écarte la page doucement
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 16.dp, end = 16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp) // Petite marge sous le bandeau
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = AppStrings.offlineMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ---> 2. LE BANDEAU DE BASE DE DONNÉES (S'affiche juste en dessous si besoin) <---
            val isDbBannerVisible = isDbMissing || isUpdateAvailable || isSyncing
            val isDbReady = !isDbMissing && !isSyncing // ✅ NOUVEAU : On vérifie si la base est prête et dispo

            DatabaseWarningBanner(
                isMissing = isDbMissing,
                isUpdateAvailable = isUpdateAvailable,
                isDownloading = isSyncing,
                downloadProgress = downloadProgress,
                onDownloadClick = {
                    // On cache le bandeau et on lance le téléchargement manuel
                    isUpdateAvailable = false
                    AppConfig.isDbUpdateAvailable.value = false

                    val request = androidx.work.OneTimeWorkRequestBuilder<fr.geotower.data.workers.DatabaseDownloadWorker>().build()
                    workManager.enqueueUniqueWork("db_download", androidx.work.ExistingWorkPolicy.REPLACE, request)
                }
            )

            // ---> 2. LE RESTE DE LA PAGE <---
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val isExpanded = maxWidth >= 600.dp
                // Hauteur minimale pour forcer l'espace entre le titre, les boutons et le "À propos"
                val minHeight = maxHeight

                if (isExpanded) {
                    // --- DISPOSITION FOLD (TABLETTE) ---
                    val prefsFold = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
                    val showLogo = prefsFold.getBoolean("show_home_logo", true)

                    // ✅ CORRECTION : Si pas de logo OU pas de réseau OU BANDEAU AFFICHE, on passe en mode Grille
                    val isGrid = !showLogo || !isOnline || isDbBannerVisible

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = minHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 32.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.weight(1f))

                        // 📦 BLOC CENTRAL "SOUDÉ" (Titre + Menu)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "GeoTower",
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 64.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
                            )

                            if (isGrid) {
                                // --- DISPOSITION EN GRILLE ---
                                MenuButtonsList(navController, useOneUi, buttonBgColor, paleColor, onPaleColor, isOnline && !isDbBannerVisible, displayLogoResId, isExpanded, isDbReady = isDbReady, isGrid = true)
                            } else {
                                // --- DISPOSITION CÔTE À CÔTE (Classique) ---
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        DrawableImage(
                                            resId = displayLogoResId,
                                            modifier = Modifier.size(280.dp).clip(RoundedCornerShape(32.dp))
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        // ✅ CORRECTION : On passe "isOnline && !isDbBannerVisible" pour forcer le masquage du logo sur tous les appareils si besoin
                                        MenuButtonsList(navController, useOneUi, buttonBgColor, paleColor, onPaleColor, isOnline && !isDbBannerVisible, displayLogoResId, isExpanded, isDbReady = isDbReady, isGrid = false)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Spacer(modifier = Modifier.height(32.dp))
                        AboutSection(navController, appVersion, paleColor)
                    }
                } else {
                    // --- DISPOSITION SMARTPHONE ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = minHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.weight(1f))

                        // 📦 BLOC CENTRAL "SOUDÉ" (Titre + Menu)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "GeoTower",
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 48.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
                            )
                            // ✅ CORRECTION : On passe la condition ici aussi pour le téléphone
                            MenuButtonsList(navController, useOneUi, buttonBgColor, paleColor, onPaleColor, isOnline && !isDbBannerVisible, displayLogoResId, isExpanded, isDbReady = isDbReady, isGrid = false)
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        AboutSection(navController, appVersion, paleColor)
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

// --- SOUS-COMPOSANTS ---

@Composable
fun MenuButtonsList(
    navController: NavController,
    useOneUi: Boolean,
    buttonBgColor: Color,
    paleColor: Color,
    onPaleColor: Color,
    isOnline: Boolean,
    logoResId: Int,
    isExpanded: Boolean,
    isDbReady: Boolean = true, // ✅ NOUVEAU PARAMÈTRE
    isGrid: Boolean = false
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val menuSize = prefs.getString("menuSize", "normal") ?: "normal"
    val showLogo = prefs.getBoolean("show_home_logo", true)

    val showNearby by AppConfig.showNearbyPage
    val showMap by AppConfig.showMapPage
    val showCompass by AppConfig.showCompassPage
    val showStats by AppConfig.showStatsPage

    var savedOrderString = prefs.getString("pages_order", "nearby,map,compass,stats,settings,logo") ?: "nearby,map,compass,stats,settings,logo"
    if (!savedOrderString.contains("logo")) {
        savedOrderString = "$savedOrderString,logo"
        prefs.edit().putString("pages_order", savedOrderString).apply()
    }
    val pagesOrder = savedOrderString.split(",")

    val buttons = mutableListOf<@Composable () -> Unit>()

    pagesOrder.forEach { pageId ->
        when (pageId) {
            "logo" -> {
                if (showLogo && isOnline && !isExpanded) {
                    buttons.add {
                        DrawableImage(
                            resId = logoResId,
                            modifier = Modifier.padding(vertical = 8.dp).size(150.dp).clip(RoundedCornerShape(24.dp))
                        )
                    }
                }
            }
            "nearby" -> {
                if (showNearby) {
                    // ✅ On bloque avec isDbReady
                    buttons.add { MenuButton(AppStrings.nearAntennas, Icons.Default.MyLocation, paleColor, onPaleColor, useOneUi, menuSize, isGrid, enabled = isDbReady) { navController.navigate("emitters") } }
                }
            }
            "map" -> {
                if (showMap) {
                    // ✅ On bloque avec isDbReady
                    buttons.add { MenuButton(AppStrings.mapTitle, Icons.Default.Map, buttonBgColor, MaterialTheme.colorScheme.onSurfaceVariant, useOneUi, menuSize, isGrid, enabled = isDbReady) { navController.navigate("map") } }
                }
            }
            "compass" -> {
                if (showCompass && AppConfig.hasCompass.value) {
                    // ✅ On bloque avec isDbReady
                    buttons.add { MenuButton(AppStrings.pageCompass, Icons.Default.Explore, buttonBgColor, MaterialTheme.colorScheme.onSurfaceVariant, useOneUi, menuSize, isGrid, enabled = isDbReady) { navController.navigate("compass") } }
                }
            }
            "stats" -> {
                if (showStats) {
                    val isStatsEnabledInDev = false
                    val alpha = if (isStatsEnabledInDev && isDbReady) 1f else 0.5f

                    buttons.add {
                        MenuButton(
                            text = AppStrings.statsTitle,
                            icon = Icons.Default.BarChart,
                            color = buttonBgColor.copy(alpha = alpha),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                            useOneUi = useOneUi,
                            menuSize = menuSize,
                            fillWidth = isGrid,
                            enabled = isStatsEnabledInDev && isDbReady, // ✅ On bloque avec isDbReady
                            onClick = { navController.navigate("stats") }
                        )
                    }
                }
            }
            "settings" -> {
                // ✅ PARAMÈTRES RESTE TOUJOURS CLIQUABLE (enabled = true)
                buttons.add { MenuButton(AppStrings.settingsTitle, Icons.Default.Settings, buttonBgColor, MaterialTheme.colorScheme.onSurfaceVariant, useOneUi, menuSize, isGrid, enabled = true) { navController.navigate("settings") } }
            }
        }
    }

    if (isGrid) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.widthIn(max = 800.dp)) {
            for (i in buttons.indices step 2) {
                if (i + 1 < buttons.size) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.weight(1f)) { buttons[i]() }
                        Box(modifier = Modifier.weight(1f)) { buttons[i+1]() }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) { buttons[i]() }
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            buttons.forEach { it() }
        }
    }
}

@Composable
fun AboutSection(navController: NavController, version: String, paleColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = { navController.navigate("about") }) {
            // Utilise primary pour l'icône au lieu de paleColor
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = AppStrings.about, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp)
        }
        Text(text = version, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun MenuButton(
    text: String,
    icon: ImageVector,
    color: Color,
    contentColor: Color,
    useOneUi: Boolean,
    menuSize: String = "normal",
    fillWidth: Boolean = false, // ✅ NOUVEAU
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val buttonWidth = when(menuSize) { "petit" -> 280.dp; "large" -> 330.dp; else -> 320.dp }
    val buttonHeight = when(menuSize) { "petit" -> 65.dp; "large" -> 80.dp; else -> 60.dp }
    val iconSize = when(menuSize) { "petit" -> 24.dp; "large" -> 28.dp; else -> 28.dp }
    val textSize = when(menuSize) { "petit" -> 16.sp; "large" -> 18.sp; else -> 18.sp }

    val buttonShape = if (useOneUi) RoundedCornerShape(28.dp) else RoundedCornerShape(20.dp)
    val buttonElevation = if (useOneUi) 0.dp else 2.dp

    // ✅ NOUVEAU : Si fillWidth est true (Grille), le bouton s'adapte !
    val widthModifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(buttonWidth)

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = widthModifier.height(buttonHeight),
        shape = buttonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = contentColor,
            // ✅ NOUVEAU : On applique 50% de transparence si le bouton est inactif
            disabledContainerColor = color.copy(alpha = 0.5f),
            disabledContentColor = contentColor.copy(alpha = 0.5f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = buttonElevation)
    ) {
        Row(
            // On centre le contenu du bouton quand il est très large dans la grille
            horizontalArrangement = if (fillWidth) Arrangement.Center else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(iconSize))
            Spacer(modifier = Modifier.width(20.dp))
            Text(text = text, fontSize = textSize, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun DrawableImage(resId: Int, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER; if (resId != 0) setImageResource(resId) } },
        update = { if (resId != 0) it.setImageResource(resId) }
    )
}

@Composable
fun DatabaseWarningBanner(
    isMissing: Boolean,
    isUpdateAvailable: Boolean,
    isDownloading: Boolean,
    downloadProgress: Int, // ✅ NOUVEAU PARAMÈTRE
    onDownloadClick: () -> Unit
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = isMissing || isUpdateAvailable || isDownloading,
        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
        // ✅ AJOUT DES MARGES (Comme pour le bandeau hors-ligne)
        modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val containerColor = if (isMissing) {
            androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
        } else {
            androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
        }
        val contentColor = if (isMissing) {
            androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer
        } else {
            androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
        }
        val icon = if (isMissing) Icons.Default.Error else Icons.Default.CloudDownload

        androidx.compose.material3.Surface(
            color = containerColor,
            shape = RoundedCornerShape(16.dp), // ✅ AJOUT DE LA FORME ARRONDIE
            shadowElevation = 2.dp,            // ✅ AJOUT DE L'OMBRE
            modifier = androidx.compose.ui.Modifier.fillMaxWidth()
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor
                )
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(12.dp))
                androidx.compose.foundation.layout.Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                    androidx.compose.material3.Text(
                        text = if (isMissing) fr.geotower.utils.AppStrings.missingDbBannerTitle else fr.geotower.utils.AppStrings.updateDbBannerTitle,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = contentColor,
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall
                    )
                    if (isMissing) {
                        androidx.compose.material3.Text(
                            text = fr.geotower.utils.AppStrings.missingDbBannerDesc,
                            color = contentColor,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // ✅ NOUVEAU : Si ça télécharge, on affiche l'animation WavyLoader + LE POURCENTAGE
                if (isDownloading) {
                    androidx.compose.foundation.layout.Row(
                        modifier = androidx.compose.ui.Modifier.height(36.dp).width(100.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End // On aligne à droite
                    ) {
                        // ✅ CORRECTION : Utilisation de la version "Small"
                        fr.geotower.ui.components.SmallWavyLoader(
                            color = contentColor,
                            modifier = androidx.compose.ui.Modifier.size(24.dp)
                        )
                        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(8.dp))
                        androidx.compose.material3.Text(
                            text = "$downloadProgress %",
                            color = contentColor,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    // Sinon, on affiche le bouton normal
                    androidx.compose.material3.Button(
                        onClick = onDownloadClick,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = contentColor,
                            contentColor = containerColor
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = androidx.compose.ui.Modifier.height(36.dp)
                    ) {
                        androidx.compose.material3.Text(
                            text = fr.geotower.utils.AppStrings.btnDownloadBanner,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
