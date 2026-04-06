package fr.geotower

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.work.*
import fr.geotower.data.workers.SignalQuestUploadWorker
import fr.geotower.ui.components.GlobalUploadOverlay
import fr.geotower.widget.AntennaWidgetWorker
import android.content.pm.PackageManager
import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow

// --- DONNÉES ---
import fr.geotower.data.AnfrRepository
import fr.geotower.data.db.AppDatabase
import fr.geotower.utils.AppConfig
import fr.geotower.data.api.RetrofitClient


// --- ÉCRANS ---
import fr.geotower.ui.screens.splash.SplashScreen
import fr.geotower.ui.screens.onboarding.FirstStartScreen
import fr.geotower.ui.screens.home.HomeScreen
import fr.geotower.ui.screens.settings.SettingsScreen
import fr.geotower.ui.screens.emitters.NearEmittersScreen
import fr.geotower.ui.screens.emitters.SupportDetailScreen
import fr.geotower.ui.screens.emitters.SiteDetailScreen
import fr.geotower.ui.screens.stats.StatisticsScreen
import fr.geotower.ui.screens.about.AboutScreen
import fr.geotower.ui.screens.compass.CompassScreen
import fr.geotower.ui.screens.emitters.SignalQuestUploadScreen
import android.net.Uri
import androidx.compose.ui.zIndex

// --- IMPORT MAP ---
import fr.geotower.ui.screens.map.MapScreen
import fr.geotower.ui.screens.map.MapViewModel
import fr.geotower.ui.screens.map.MapViewModelFactory
import java.io.File
import androidx.compose.runtime.collectAsState
import androidx.navigation.navDeepLink

// ✅ ÉTAPE 1 : État global pour afficher le popup de la BDD depuis n'importe où
object AppGlobalState {
    val showDbSuccessPopup = androidx.compose.runtime.mutableStateOf(false)
}

class MainActivity : ComponentActivity() {

    // 🌟 1. LE CANAL POUR LA NOTIFICATION
    private val navigateToSiteFlow = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    // 🌟 2. ATTRAPER LE CLIC QUAND L'APP EST EN ARRIÈRE-PLAN
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // ✅ Crucial : on met à jour l'intent de l'activité

        // ✅ On laisse le NavController gérer l'URL (QR Code ou Notification) tout seul
        // Cela résout le bug de "mémoire" car il extrait l'ID tout seul de l'URL
        intent.data?.let { uri ->
            if (uri.scheme == "geotower") {
                // On récupère le navController depuis notre NavHost (via navigateToSiteFlow ou accès direct)
                navigateToSiteFlow.tryEmit(uri.toString())
            }
        }

        // On garde la gestion des anciens Extras si jamais l'URL est absente
        val siteIdFromNotif = intent.getStringExtra("TARGET_SITE_ID")
        if (siteIdFromNotif != null && intent.data == null) {
            navigateToSiteFlow.tryEmit("support_detail/$siteIdFromNotif")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ✅ ÉTAPE 2 : Vérifie si on a cliqué sur la notif quand l'app était fermée
        if (intent?.getBooleanExtra("SHOW_DB_SUCCESS_POPUP", false) == true) {
            AppGlobalState.showDbSuccessPopup.value = true
        }

        // --- CORRECTION 1 : Initialisation du Repository ---
        // On lui donne le context pour qu'il aille chercher la BDD lui-même quand il en a besoin
        val repository = AnfrRepository(
            api = RetrofitClient.apiService,
            context = applicationContext
        )

        // Vérification premier lancement (CORRIGÉ : on utilise le fichier global)
        val sharedPref = getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
        val isFirstRun = sharedPref.getBoolean("isFirstRun", true)

        // --- CONFIGURATION OSMDROID (SÉCURITÉ ANTI-403) ---
        val sharedPrefs = getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        val osmdroidConfig = org.osmdroid.config.Configuration.getInstance()

        // 1. On charge la config
        osmdroidConfig.load(this, sharedPrefs)

        // 2. IMPORTANT : On définit un User-Agent unique et descriptif.
        // C'est ce qui évite que les serveurs d'OSM te bloquent.
        osmdroidConfig.userAgentValue = "$packageName (GeoTower App; Android)"

        // 3. On force le dossier de cache dans le stockage interne de l'app.
        // Cela garantit que les tuiles sont bien sauvegardées et pas retéléchargées à chaque fois.
        val basePath = File(cacheDir, "osmdroid")
        osmdroidConfig.osmdroidBasePath = basePath
        osmdroidConfig.osmdroidTileCache = File(basePath, "tiles")

        // ========================================================
        // NOUVEAU : LECTURE DE LA DEMANDE DU WIDGET & ASSISTANT
        // ========================================================
        // ========================================================
        // NOUVEAU : MISE À JOUR DU WIDGET À L'OUVERTURE DE L'APP
        // ========================================================
        val updateWidgetRequest = OneTimeWorkRequestBuilder<AntennaWidgetWorker>().build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "WidgetUpdateOnOpen",
            ExistingWorkPolicy.REPLACE,
            updateWidgetRequest
        )
        val widgetDest = intent.getStringExtra("widget_dest")

        // ========================================================
        // NOUVEAU : VÉRIFICATION PÉRIODIQUE DES MISES À JOUR (TOUS LES 3 JOURS)
        // ========================================================
        val updateCheckRequest = androidx.work.PeriodicWorkRequestBuilder<fr.geotower.data.workers.UpdateCheckWorker>(
            6, java.util.concurrent.TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "PeriodicUpdateCheck",
            ExistingPeriodicWorkPolicy.KEEP, // KEEP = Ne remet pas le compteur à 0 à chaque ouverture de l'app
            updateCheckRequest
        )

        val widgetSiteId = intent.getStringExtra("widget_site_id")

        // 🌟 3. LECTURE DE L'ID SI L'APP ÉTAIT TOTALEMENT FERMÉE
        val notifSiteId = intent.getStringExtra("TARGET_SITE_ID")

        // 🌟 3. DÉTECTION DU LIEN (QR CODE OU NOTIFICATION)
        val deepLinkUri = intent.data
        val isDeepLink = deepLinkUri != null && deepLinkUri.scheme == "geotower"

        // 🎙️ Lecture de la commande vocale de Google Assistant (Défini dans shortcuts.xml)
        val assistantFeature = intent.getStringExtra("app_feature")

        // --- NOUVEAU : Lecture de la page de démarrage choisie par l'utilisateur ---
        val savedStartupPage = sharedPref.getString("startup_page", "home") ?: "home"

        // On détermine la destination finale en fonction de la demande
        val destinationApresSplash = if (isFirstRun) {
            "first_start"
        } else if (isDeepLink) {
            "home" // ✅ On laisse le NavHost gérer la navigation profonde
        } else if (notifSiteId != null) {
            "support_detail/$notifSiteId" // 🌟 Ouvre le détail si on vient de la notif
        } else if (widgetDest == "nearby") {
            "emitters" // Ouvre la page NearEmittersScreen
        } else if (widgetDest == "detail" && widgetSiteId != null) {
            "support_detail/$widgetSiteId" // Ouvre la page SupportDetailScreen du pylône sélectionné
        } else if (assistantFeature != null) {
            "map" // 🗺️ Téléportation directe sur la carte si demandé par la voix !
        } else {
            // Lancement normal : on utilise le choix de l'utilisateur
            when (savedStartupPage) {
                "home" -> "home"
                "nearby" -> "emitters"
                "map" -> "map"
                "compass" -> "compass"
                else -> "home"
            }
        }

        // 🌟 Si on vient du widget, de l'assistant, de la notification OU d'un QR CODE, on saute le Splash Screen !
        val startRoute =
            if ((widgetDest != null || assistantFeature != null || notifSiteId != null || isDeepLink) && !isFirstRun) destinationApresSplash else "splash"
        // ========================================================

        // --- LECTURE ET DÉTECTION DE LA LANGUE DÈS LE LANCEMENT ---
        val appPrefs = getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

        if (!appPrefs.contains("app_language")) {
            // Premier lancement : On définit le choix sur "Système" par défaut
            appPrefs.edit().putString("app_language", "Système").apply()
            AppConfig.appLanguage.value = "Système"
        } else {
            // L'utilisateur a déjà une langue sauvegardée (ou "Système")
            AppConfig.appLanguage.value = appPrefs.getString("app_language", "Système") ?: "Système"
        }

        // ========================================================
        // NOUVEAU : CHARGEMENT DE TOUS LES AUTRES PARAMÈTRES
        // ========================================================
        if (!appPrefs.contains("theme_mode")) {
            // Premier lancement : Auto (0) pour Android 10+, Clair (1) pour les plus anciens
            val defaultTheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) 0 else 1
            appPrefs.edit().putInt("theme_mode", defaultTheme).apply()
            AppConfig.themeMode.intValue = defaultTheme
        } else {
            // Lancement classique : On charge ce que l'utilisateur avait choisi
            AppConfig.themeMode.intValue = appPrefs.getInt("theme_mode", 0)
        }
        AppConfig.isOledMode.value = appPrefs.getBoolean("is_oled_mode", true)
        AppConfig.isBlurEnabled.value = appPrefs.getBoolean("is_blur_enabled", true)
        AppConfig.forceOneUiTheme.value = appPrefs.getBoolean("force_one_ui", false)
        AppConfig.mapProvider.intValue = appPrefs.getInt("map_provider", 1)
        AppConfig.ignStyle.intValue = appPrefs.getInt("ign_style", 0)
        AppConfig.navMode.intValue = appPrefs.getInt("nav_mode", 0)
        AppConfig.defaultOperator.value = appPrefs.getString("default_operator", "Aucun") ?: "Aucun"
        AppConfig.loadSavedFilters(appPrefs)
        // ========================================================

        // --- VÉRIFICATION DU MAGNÉTOMÈTRE (BOUSSOLE) ---
        val sensorManager =
            getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val magneticSensor =
            sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_MAGNETIC_FIELD)
        val rotationSensor =
            sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR)

        // Si le téléphone n'a ni boussole magnétique, ni vecteur de rotation, on désactive la boussole
        if (magneticSensor == null && rotationSensor == null) {
            AppConfig.hasCompass.value = false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            if (checkSelfPermission("android.permission.POST_PROMOTED_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED
            ) {
                // Force l'affichage même si déjà refusée une fois
                requestPermissions(
                    arrayOf("android.permission.POST_PROMOTED_NOTIFICATIONS"),
                    1002
                )
                android.util.Log.d("LiveNotif", "Permission POST_PROMOTED demandée")
            } else {
                android.util.Log.d("LiveNotif", "Permission POST_PROMOTED déjà accordée")
            }
        }

        setContent {
            val themeMode by AppConfig.themeMode
            val isOled by AppConfig.isOledMode
            val context = LocalContext.current

            // ✅ NOUVEAU : On écoute la fin du téléchargement globalement
            val workManager = remember { androidx.work.WorkManager.getInstance(context) }
            val workInfos by workManager.getWorkInfosForUniqueWorkFlow("db_download").collectAsState(initial = emptyList())
            val currentWork = workInfos.firstOrNull()

            LaunchedEffect(currentWork?.state) {
                if (currentWork?.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                    AppGlobalState.showDbSuccessPopup.value = true // Affiche le Pop-up !
                    workManager.pruneWork() // 🧹 Nettoie l'historique du Worker pour que le pop-up ne revienne pas au prochain lancement de l'appli
                }
            }
            val isDark = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }

            val dynamicColors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (isDark) darkColorScheme() else lightColorScheme()
            }

            val colorScheme = if (isDark && isOled) {
                dynamicColors.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            } else {
                dynamicColors
            }

            MaterialTheme(colorScheme = colorScheme) {
                val navController = rememberNavController()

                // 🌟 4. ÉCOUTEUR : Naviguer instantanément (QR Code, Notif, Widget)
                LaunchedEffect(Unit) {
                    navigateToSiteFlow.collect { destination ->
                        if (destination.startsWith("geotower://")) {
                            // ✅ C'est un Deep Link (QR Code ou Notif moderne)
                            navController.handleDeepLink(Intent(Intent.ACTION_VIEW, Uri.parse(destination)))
                        } else {
                            // ✅ C'est une route interne (Widget ou ancien code)
                            navController.navigate(destination)
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->

                    val mapFactory = MapViewModelFactory(repository)
                    val sharedMapViewModel: MapViewModel = viewModel(factory = mapFactory)

                    // ---> 2. ON ENVELOPPE TOUT DANS UNE BOX POUR SUPERPOSER <---
                    Box(modifier = Modifier.fillMaxSize()) {

                        NavHost(
                            navController = navController,
                            startDestination = startRoute
                        ) {

                            // Splash
                            composable("splash") {
                                SplashScreen(
                                    navController = navController,
                                    nextDestination = destinationApresSplash // Utilise bien la destination finale
                                )
                            }

                            // Tuto
                            composable("first_start") {
                                Box(modifier = Modifier.padding(innerPadding)) {
                                    FirstStartScreen(
                                        repository = repository,
                                        onFinished = {
                                            sharedPref.edit().putBoolean("isFirstRun", false)
                                                .apply()
                                            navController.navigate("home") {
                                                popUpTo("first_start") { inclusive = true }
                                            }
                                        }
                                    )
                                }
                            }

                            // Home
                            composable("home") {
                                Box(modifier = Modifier.padding(innerPadding)) {
                                    HomeScreen(navController)
                                }
                            }

                            // Carte
                            composable("map") {
                                MapScreen(
                                    navController = navController,
                                    viewModel = sharedMapViewModel
                                )
                            }

                            // Boussole
                            composable("compass") {
                                Box(modifier = Modifier.padding(innerPadding)) {
                                    CompassScreen(
                                        navController = navController,
                                        viewModel = sharedMapViewModel
                                    )
                                }
                            }

                            // Statistiques
                            composable("stats") {
                                Box(modifier = Modifier.padding(innerPadding)) {
                                    // ✅ AJOUT DU REPOSITORY ICI 👇
                                    StatisticsScreen(navController = navController, repository = repository)
                                }
                            }

                            // Paramètres
                            composable("settings") {
                                Box(modifier = Modifier.padding(innerPadding)) {
                                    SettingsScreen(navController, repository)
                                }
                            }

                            // Emetteurs
                            composable("emitters") {
                                Box(modifier = Modifier.padding(innerPadding)) {
                                    NearEmittersScreen(navController, repository)
                                }
                            }

                            // À propos
                            composable("about") {
                                Box(modifier = Modifier.padding(innerPadding)) {
                                    AboutScreen(navController)
                                }
                            }

                            // --- 1. DÉTAIL DU SUPPORT (Le Pylône) ---
                            composable(
                                route = "support_detail/{id}",
                                arguments = listOf(navArgument("id") { type = NavType.StringType }), // ✅ String
                                deepLinks = listOf(navDeepLink { uriPattern = "geotower://support/{id}" })
                            ) { backStackEntry ->
                                val id = backStackEntry.arguments?.getString("id") ?: ""
                                // Convertir en Long si ta BDD utilise un Long, ou adapter la query
                                val idLong = id.toLongOrNull() ?: 0L
                                Box(modifier = Modifier.padding(innerPadding)) {
                                    fr.geotower.ui.screens.emitters.SupportSiteWrapperScreen(navController, repository, idLong)
                                }
                            }

                            // --- 2. DÉTAIL DU SITE (L'antenne spécifique d'un opérateur) ---
                            composable(
                                route = "site_detail/{id}",
                                arguments = listOf(navArgument("id") { type = NavType.StringType }), // ✅ String
                                deepLinks = listOf(navDeepLink { uriPattern = "geotower://site/{id}" })
                            ) { backStackEntry ->
                                val id = backStackEntry.arguments?.getString("id") ?: ""
                                val idLong = id.toLongOrNull() ?: 0L
                                Box(modifier = Modifier.padding(innerPadding)) {
                                    SiteDetailScreen(navController, repository, idLong)
                                }
                            }
                            // --- 3. ÉCRAN D'ENVOI SIGNAL QUEST ---
                            composable(
                                // ✅ AJOUT DE &azimuts={azimuts} À LA FIN
                                route = "sq_upload/{siteId}/{operatorName}?uris={uris}&lat={lat}&lon={lon}&azimuts={azimuts}",
                                arguments = listOf(
                                    navArgument("siteId") { type = NavType.StringType },
                                    navArgument("operatorName") { type = NavType.StringType },
                                    navArgument("uris") { type = NavType.StringType; defaultValue = "" },
                                    navArgument("lat") { type = NavType.StringType; defaultValue = "0.0" },
                                    navArgument("lon") { type = NavType.StringType; defaultValue = "0.0" },

                                    // ✅ NOUVEAU ARGUMENT
                                    navArgument("azimuts") { type = NavType.StringType; defaultValue = "" }
                                )
                            ) { backStackEntry ->
                                val siteId = backStackEntry.arguments?.getString("siteId") ?: ""
                                val operatorName = backStackEntry.arguments?.getString("operatorName") ?: ""
                                val urisStr = backStackEntry.arguments?.getString("uris") ?: ""
                                val lat = backStackEntry.arguments?.getString("lat")?.toDoubleOrNull() ?: 0.0
                                val lon = backStackEntry.arguments?.getString("lon")?.toDoubleOrNull() ?: 0.0

                                // ✅ NOUVEAU : On récupère et décode les azimuts
                                val azimutsStr = backStackEntry.arguments?.getString("azimuts") ?: ""
                                val decodedAzimuts = android.net.Uri.decode(azimutsStr)

                                val uris = urisStr.split(",").map { Uri.decode(it) }.filter { it.isNotEmpty() }

                                Box(modifier = Modifier.padding(innerPadding)) {
                                    SignalQuestUploadScreen(
                                        imageUris = uris,
                                        siteId = siteId,
                                        operatorName = operatorName,
                                        lat = lat,
                                        lon = lon,
                                        azimuts = decodedAzimuts,
                                        onNavigateBack = { navController.popBackStack() },
                                        onStartUpload = { finalUris, description ->
                                            val uploadData = workDataOf(
                                                "siteId" to siteId,
                                                "operator" to operatorName,
                                                "description" to description,
                                                "uris" to finalUris.joinToString(",")
                                            )

                                            val uploadRequest = OneTimeWorkRequestBuilder<SignalQuestUploadWorker>()
                                                .setInputData(uploadData)
                                                .addTag("sq_upload_${siteId}")
                                                .addTag("sq_upload_global")
                                                .setConstraints(
                                                    Constraints.Builder()
                                                        .setRequiredNetworkType(NetworkType.CONNECTED)
                                                        .build()
                                                )
                                                .build()

                                            WorkManager.getInstance(context).enqueueUniqueWork(
                                                "upload_sq_${System.currentTimeMillis()}",
                                                ExistingWorkPolicy.APPEND_OR_REPLACE,
                                                uploadRequest
                                            )

                                            navController.popBackStack()
                                        }
                                    )
                                }
                            }

                        }
                        fr.geotower.ui.components.GlobalUploadOverlay()

                        // ✅ NOUVEAU : On écoute la page sur laquelle se trouve l'utilisateur
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route

                        // ✅ ÉTAPE 3 : Le pop-up s'affiche PARTOUT, SAUF sur le splash et le tuto !
                        if (AppGlobalState.showDbSuccessPopup.value && currentRoute != "splash" && currentRoute != "first_start") {
                            AlertDialog(
                                onDismissRequest = {
                                    AppGlobalState.showDbSuccessPopup.value = false
                                },
                                title = {
                                    Text(
                                        text = fr.geotower.utils.AppStrings.notifDbDownloadSuccess,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                },
                                text = { Text(fr.geotower.utils.AppStrings.dbDownloadSuccessDesc) },
                                confirmButton = {
                                    Button(onClick = {
                                        // On ferme simplement le pop-up en douceur !
                                        AppGlobalState.showDbSuccessPopup.value = false
                                    }) {
                                        Text(fr.geotower.utils.AppStrings.dbDownloadTermine)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}