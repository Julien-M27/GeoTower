@file:OptIn(ExperimentalMaterial3Api::class)
package fr.geotower.ui.screens.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.data.build.LocalBuildCapability
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.db.GeoTowerDatabaseValidator
import fr.geotower.data.workers.DatabaseDownloadWorker
import fr.geotower.data.workers.LocalDbBuildWorker
import fr.geotower.ui.components.SafeClick
import fr.geotower.ui.components.colorPaletteFadingEdge
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.screens.settings.PreferenceProfilesSheet
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.services.LiveTrackingController
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLocale
import fr.geotower.utils.AppUiMode
import fr.geotower.utils.OperatorLogos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.collectAsState

@Composable
fun FirstStartScreen(
    repository: AnfrRepository,
    onFinished: () -> Unit
) {
    val safeClick = rememberSafeClick()

    val totalSteps = 8

    // 1. La mémoire : Quelle est la page maximale atteinte ?
    var maxUnlockedStep by remember { mutableIntStateOf(0) }

    // 2. Le Pager : Il s'adapte au nombre de pages débloquées.
    val pagerState = rememberPagerState(pageCount = { maxUnlockedStep + 1 })

    // 3. Un CoroutineScope pour animer le Pager lors du clic sur le bouton
    val coroutineScope = rememberCoroutineScope()
    var pendingStep by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(maxUnlockedStep, pendingStep) {
        val targetStep = pendingStep
        if (targetStep != null && maxUnlockedStep >= targetStep) {
            pagerState.animateScrollToPage(targetStep)
            pendingStep = null
        }
    }

    // 4. On garde une variable "currentStep" dérivée du pager pour le reste de ton code (Boutons, Indicateurs)
    val currentStep = pagerState.currentPage

    fun goToStep(step: Int) {
        val targetStep = step.coerceIn(0, totalSteps - 1)
        if (maxUnlockedStep < targetStep) {
            maxUnlockedStep = targetStep
            pendingStep = targetStep
        } else {
            coroutineScope.launch { pagerState.animateScrollToPage(targetStep) }
        }
    }

    fun goToNextStep() {
        if (currentStep < totalSteps - 1) {
            goToStep(currentStep + 1)
        } else {
            onFinished()
        }
    }

    var showLocationPermissionDialog by remember { mutableStateOf(false) }
    var showNotificationPermissionDialog by remember { mutableStateOf(false) }
    var isLocationPermissionHandled by remember { mutableStateOf(false) }
    var isNotificationPermissionHandled by remember { mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isLocationGranted =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (isLocationGranted) {
            isLocationPermissionHandled = true
        } else {
            showLocationPermissionDialog = true
        }
    }

    // Étape 2 : cette permission ne concerne QUE l'affichage des notifications (téléchargements,
    // mises à jour, rapports…). Un refus ici ne coupe plus le suivi live de l'étape 3, qui a sa
    // propre demande et ne dépend que de la localisation.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isNotificationPermissionHandled = true
        } else {
            showNotificationPermissionDialog = true
        }
    }

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE) }
    val defaultOperator by AppConfig.defaultOperator // Récupération de l'opérateur actuel

    LaunchedEffect(Unit) {
        AppConfig.localDatabaseState.value = withContext(Dispatchers.IO) {
            GeoTowerDatabaseValidator.getInstalledDatabaseStatus(context).state
        }
    }

    val useOneUi = AppConfig.useOneUiDesign
    var showOperatorSheet by remember { mutableStateOf(false) }
    var showWarningDialog by remember { mutableStateOf(false) } // État du pop-up d'avertissement
    var showUnitSheet by remember { mutableStateOf(false) }
    var showPreferenceProfilesSheet by remember { mutableStateOf(false) }

    // ✅ NOUVEAU : Variables pour gérer le pop-up de succès de fin de téléchargement
    var showSuccessDialog by remember { mutableStateOf(false) }
    var wasSyncing by remember { mutableStateOf(false) }

    val workManager = remember { androidx.work.WorkManager.getInstance(context) }
    val workInfos by workManager.getWorkInfosForUniqueWorkFlow(DatabaseDownloadWorker.UNIQUE_WORK_NAME).collectAsState(initial = emptyList())
    val currentWork = workInfos.firstOrNull()
    val isDownloading = currentWork?.state == androidx.work.WorkInfo.State.RUNNING || currentWork?.state == androidx.work.WorkInfo.State.ENQUEUED
    // La base peut aussi arriver par génération locale (étape 6) : on surveille les deux chemins,
    // sinon le pop-up de succès ne s'afficherait jamais pour un utilisateur qui génère au lieu de
    // télécharger.
    val buildInfos by workManager.getWorkInfosForUniqueWorkFlow(LocalDbBuildWorker.UNIQUE_WORK_NAME).collectAsState(initial = emptyList())
    val currentBuild = buildInfos.firstOrNull()
    val isBuilding = currentBuild?.state == androidx.work.WorkInfo.State.RUNNING || currentBuild?.state == androidx.work.WorkInfo.State.ENQUEUED
    val isSyncing = isDownloading || isBuilding

    // ✅ NOUVEAU : On détecte le passage exact de "En téléchargement" à "Terminé"
    LaunchedEffect(isSyncing) {
        if (wasSyncing && !isSyncing) {
            // Le téléchargement vient de s'arrêter. Vérifions le vrai schema local.
            val dbState = withContext(Dispatchers.IO) {
                GeoTowerDatabaseValidator.getInstalledDatabaseStatus(context).state
            }
            AppConfig.localDatabaseState.value = dbState
            if (dbState == GeoTowerDatabaseValidator.LocalDatabaseState.VALID) {
                showSuccessDialog = true
            }
        }
        wasSyncing = isSyncing
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var appLanguage by remember { mutableStateOf(prefs.getString("app_language", AppLocale.LANGUAGE_SYSTEM) ?: AppLocale.LANGUAGE_SYSTEM) }
    var showLanguageSheet by remember { mutableStateOf(false) }

    // --- LECTURE RÉACTIVE DU THÈME ---
    val themeMode by AppConfig.themeMode
    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)
    val isOled by AppConfig.isOledMode
    val sizing = LocalGeoTowerUiStyle.current.sizing

    val cardShape = if (useOneUi) RoundedCornerShape(28.dp) else RoundedCornerShape(12.dp)
    val cardBorder = if (useOneUi) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    val bubbleColor = if (useOneUi) {
        if (isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    } else {
        Color.Transparent
    }
    val mainBgColor = if (isDark && isOled) Color.Black else MaterialTheme.colorScheme.background

    Scaffold(
        containerColor = mainBgColor,

    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = sizing.spacing(24.dp)),
        ) {

            PagerIndicator(totalSteps = totalSteps, currentStep = currentStep)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // Permet un espace entre les pages lors du swipe pour un meilleur rendu
                    pageSpacing = sizing.spacing(16.dp),
                    // Rend le swipe inactif vers l'avant si la page n'est pas débloquée
                    userScrollEnabled = true,
                    verticalAlignment = Alignment.Top
                ) { page ->
                    val pageScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (page == 4) Modifier.colorPaletteFadingEdge(pageScrollState) else Modifier)
                            .verticalScroll(pageScrollState),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (page) {
                            0 -> StepWelcomeDesign(
                                useOneUi = useOneUi,
                                cardShape = cardShape,
                                cardBorder = cardBorder,
                                bubbleColor = bubbleColor,
                                onOpenLanguageSheet = { showLanguageSheet = true },
                                onSafeClick = safeClick
                            )
                            1 -> StepLocationPermissionDesign()
                            2 -> StepNotificationsPermissionDesign()
                            // Le suivi live ne nécessite ni opérateur ni permission de notification :
                            // il vient après l'étape des notifications, mais n'en dépend pas.
                            3 -> StepLiveTrackingDesign(
                                shape = cardShape,
                                border = cardBorder,
                                bubbleColor = bubbleColor,
                                useOneUi = useOneUi,
                                defaultOperator = defaultOperator,
                                onOpenOperatorSheet = { showOperatorSheet = true }
                            )
                            4 -> StepThemeDesign(useOneUi, cardShape, cardBorder, bubbleColor, onSafeClick = safeClick)
                            5 -> StepMapDesign(useOneUi, bubbleColor, onSafeClick = safeClick)
                            6 -> StepDatabaseDesign(useOneUi, cardShape, cardBorder, bubbleColor, onSafeClick = safeClick)
                            7 -> StepPreferencesDesign(
                                useOneUi = useOneUi,
                                cardShape = cardShape,
                                cardBorder = cardBorder,
                                bubbleColor = bubbleColor,
                                onOpenOperatorSheet = { showOperatorSheet = true },
                                onOpenUnitSheet = { showUnitSheet = true },
                                onOpenPreferenceProfilesSheet = { showPreferenceProfilesSheet = true },
                                onSafeClick = safeClick
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))

            val hasLocationPermission =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val isLocationStepReady = hasLocationPermission || isLocationPermissionHandled
            val hasNotificationPermission =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val isNotificationStepReady = hasNotificationPermission || isNotificationPermissionHandled

            val buttonText = when(currentStep) {
                0 -> stringResource(R.string.onboarding_start_configuration)
                1 -> if (isLocationStepReady) stringResource(R.string.common_next) else stringResource(R.string.common_authorize)
                2 -> if (isNotificationStepReady) stringResource(R.string.common_next) else stringResource(R.string.common_authorize)
                totalSteps - 1 -> stringResource(R.string.onboarding_lets_go)
                else -> stringResource(R.string.common_next)
            }
            Button(
                onClick = {
                    safeClick("onboarding_primary_$currentStep") {
                        when(currentStep) {
                            0 -> {
                                goToNextStep()
                            }

                            1 -> {
                                if (isLocationStepReady) {
                                    goToNextStep()
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            }

                            2 -> {
                                if (isNotificationStepReady) {
                                    goToNextStep()
                                } else {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }

                            totalSteps - 1 -> {
                                // FIN DE L'ONBOARDING : Vérification de l'opérateur
                                if (defaultOperator == "Aucun") {
                                    showWarningDialog = true
                                } else {
                                    onFinished()
                                }
                            }

                            else -> {
                                if (currentStep == 6 && AppConfig.localDatabaseState.value == null) {
                                    coroutineScope.launch {
                                        val dbState = withContext(Dispatchers.IO) {
                                            GeoTowerDatabaseValidator.getInstalledDatabaseStatus(context).state
                                        }
                                        AppConfig.localDatabaseState.value = dbState
                                    }
                                }
                                goToNextStep()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sizing.component(56.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(text = buttonText, fontSize = sizing.text(18.sp))
                Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                if (currentStep == totalSteps - 1) {
                    Icon(Icons.Default.Check, contentDescription = null)
                } else if (currentStep > 0) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }

    // --- POP-UP D'AVERTISSEMENT SI UNE AUTORISATION EST REFUSÉE ---
    if (showLocationPermissionDialog) {
        OnboardingPermissionDeniedDialog(
            title = { Text(text = stringResource(R.string.onboarding_location_denied_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.onboarding_location_denied_desc)) },
            shape = cardShape,
            onContinue = {
                showLocationPermissionDialog = false
                isLocationPermissionHandled = true
            },
            onRetry = {
                showLocationPermissionDialog = false
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        )
    }

    if (showNotificationPermissionDialog) {
        OnboardingPermissionDeniedDialog(
            title = { Text(text = stringResource(R.string.onboarding_notifications_denied_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.onboarding_notifications_denied_desc)) },
            shape = cardShape,
            onContinue = {
                showNotificationPermissionDialog = false
                isNotificationPermissionHandled = true
            },
            onRetry = {
                showNotificationPermissionDialog = false
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        )
    }

    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
            title = { Text(text = stringResource(R.string.onboarding_warning_no_operator_title), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(stringResource(R.string.onboarding_warning_no_operator_desc))
                }
            },
            shape = cardShape,
            containerColor = MaterialTheme.colorScheme.surface,
            confirmButton = {
                TextButton(
                    onClick = {
                        showWarningDialog = false
                        onFinished() // L'utilisateur force la continuation
                    }
                ) {
                    Text(stringResource(R.string.common_continue_anyway), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showWarningDialog = false
                        showOperatorSheet = true // Ouvre la sélection
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.onboarding_choose_operator), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // --- POP-UP DE SUCCÈS FIN DE TÉLÉCHARGEMENT ---
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                // ✅ NOUVEAU : On désactive aussi le pop-up global si l'utilisateur clique à côté
                fr.geotower.AppGlobalState.showDbSuccessPopup.value = false
            },
            title = { Text(text = stringResource(R.string.onboarding_database_success_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
            text = { Text(stringResource(R.string.onboarding_database_success_desc)) },
            shape = cardShape,
            containerColor = MaterialTheme.colorScheme.surface,
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        // ✅ NOUVEAU : On désactive le pop-up global pour qu'il n'apparaisse pas sur l'accueil
                        fr.geotower.AppGlobalState.showDbSuccessPopup.value = false

                        // On n'avance automatiquement que si l'utilisateur est encore sur l'étape de
                        // téléchargement (6). Le téléchargement tourne en arrière-plan : s'il se termine
                        // alors que l'utilisateur a déjà glissé plus loin (ex. dernière étape), appeler
                        // goToNextStep() finirait l'onboarding et renverrait vers l'accueil sans attendre.
                        // Dans ce cas on se contente de fermer le pop-up.
                        if (currentStep == 6) {
                            goToNextStep()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.common_continue), fontWeight = FontWeight.Bold)
                }
            }
        )
    }
    // --- BOTTOM SHEET DE SÉLECTION OPÉRATEUR ---
    if (showOperatorSheet) {
        fr.geotower.ui.components.OperatorSheet(
            current = defaultOperator,
            onSelect = { selectedOperator ->
                AppConfig.defaultOperator.value = selectedOperator
                prefs.edit().putString("default_operator", selectedOperator).apply()
                // Le suivi se relance pour viser le nouvel opérateur — ou l'antenne la plus proche
                // si « Aucun », il n'exige plus d'opérateur. On n'ouvre SURTOUT PAS l'écran système
                // des notifications promues ici : tant que l'app-op n'est pas accordé, la condition
                // reste vraie, et cette feuille est atteinte à la fin du tuto (carte « Opérateur »
                // et dialogue « C'est parti » sans opérateur) — l'écran système se rouvrait à chaque
                // choix. Il appartient au moment où l'on allume le suivi, pas à ce moment-ci.
                if (AppConfig.enableLiveTracking.value) {
                    LiveTrackingController.startIfEligible(context)
                }
            },
            onDismiss = { showOperatorSheet = false },
            sheetState = sheetState,
            useOneUi = useOneUi,
            bubbleColor = bubbleColor
        )
    }

    // --- BOTTOM SHEET DE SÉLECTION LANGUE ---
    if (showLanguageSheet) {
        fr.geotower.ui.components.LanguageSheet(
            current = appLanguage,
            onSelect = { nouvelleLangue ->
                appLanguage = nouvelleLangue
                AppConfig.appLanguage.value = nouvelleLangue
                AppLocale.applyApplicationLocale(context, nouvelleLangue)
                prefs.edit().putString("app_language", nouvelleLangue).apply()
            },
            onDismiss = { showLanguageSheet = false },
            sheetState = sheetState,
            useOneUi = useOneUi,
            bubbleColor = bubbleColor
        )
    }
    if (showUnitSheet) {
        fr.geotower.ui.components.UnitSettingsSheet(
            onDismiss = { showUnitSheet = false },
            sheetState = sheetState,
            useOneUi = useOneUi,
            bubbleColor = bubbleColor
        )
    }
    if (showPreferenceProfilesSheet) {
        PreferenceProfilesSheet(
            onDismiss = { showPreferenceProfilesSheet = false },
            sheetState = sheetState,
            useOneUi = useOneUi
        )
    }
}

// ==========================================
// --- ÉTAPE 4 : PRÉFÉRENCES (DESIGN PARAMÈTRES) ---
// ==========================================
@Composable
fun StepPreferencesDesign(
    useOneUi: Boolean,
    cardShape: Shape,
    cardBorder: BorderStroke?,
    bubbleColor: Color,
    onOpenOperatorSheet: () -> Unit,
    onOpenUnitSheet: () -> Unit,
    onOpenPreferenceProfilesSheet: () -> Unit,
    onSafeClick: SafeClick
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val sizing = LocalGeoTowerUiStyle.current.sizing

    var defaultOperator by AppConfig.defaultOperator

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))
        Icon(Icons.Outlined.Tune, null, modifier = Modifier.size(sizing.component(80.dp)), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))

        Text(stringResource(R.string.settings_section_preferences), style = sizing.textStyle(MaterialTheme.typography.headlineMedium), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
        Text(stringResource(R.string.onboarding_preferences_desc), textAlign = TextAlign.Center, style = sizing.textStyle(MaterialTheme.typography.bodyLarge), color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(sizing.spacing(32.dp)))

        // --- CARTE PROFILS DE PRÉFÉRENCES ---
        Surface(onClick = { onSafeClick("onboarding_preference_profiles") { onOpenPreferenceProfilesSheet() } }, color = if (useOneUi) bubbleColor else Color.Transparent, border = cardBorder, shape = cardShape, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(sizing.spacing(16.dp)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.preference_profiles_title), style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.preference_profiles_card_desc), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))

        Surface(onClick = { onSafeClick("onboarding_operator") { onOpenOperatorSheet() } }, color = if (useOneUi) bubbleColor else Color.Transparent, border = cardBorder, shape = cardShape, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(sizing.spacing(16.dp)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_default_operator), style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                    Text(if (defaultOperator == "Aucun") stringResource(R.string.common_select) else stringResource(R.string.common_current_value, defaultOperator), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val operatorLogoRes = OperatorLogos.drawableRes(defaultOperator)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (operatorLogoRes != null) { Image(painter = painterResource(id = operatorLogoRes), contentDescription = null, modifier = Modifier.size(sizing.component(32.dp)).clip(RoundedCornerShape(6.dp))); Spacer(modifier = Modifier.width(sizing.spacing(12.dp))) }
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.common_select), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))
        // --- CARTE UNITÉS ---
        Surface(onClick = { onSafeClick("onboarding_unit") { onOpenUnitSheet() } }, color = if (useOneUi) bubbleColor else Color.Transparent, border = cardBorder, shape = cardShape, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(sizing.spacing(16.dp)), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_units_title), style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                    val unitDesc = if(AppConfig.distanceUnit.intValue == 0) "km, km/h" else "mi, mph"
                    Text(unitDesc, style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(sizing.component(32.dp)), contentAlignment = Alignment.Center) { Text(text = "📏", fontSize = sizing.text(24.sp)) }
                    Spacer(Modifier.width(sizing.spacing(8.dp)))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // --- CARTE MODE SIMPLIFIÉ ---
        // Proposé dès le tuto : c'est le moment où le choix coûte le moins cher à l'utilisateur.
        if (RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SIMPLE_MODE_ENABLED)) {
            val simpleMode by AppConfig.simpleMode
            Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))
            Surface(color = if (useOneUi) bubbleColor else Color.Transparent, border = cardBorder, shape = cardShape, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(sizing.spacing(16.dp)), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_simple_mode_title), style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.settings_simple_mode_desc), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(sizing.spacing(12.dp)))
                    fr.geotower.ui.components.GeoTowerSwitch(
                        checked = simpleMode,
                        onCheckedChange = { AppConfig.setSimpleMode(context, it) },
                        useOneUi = useOneUi,
                        checkedColor = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPermissionDeniedDialog(
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    shape: Shape,
    onContinue: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = title,
        text = text,
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surface,
        confirmButton = {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.common_try_again), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onContinue) {
                Text(stringResource(R.string.common_continue_anyway), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun StepWelcomeDesign(
    useOneUi: Boolean,
    cardShape: Shape,
    cardBorder: BorderStroke?,
    bubbleColor: Color,
    onOpenLanguageSheet: () -> Unit,
    onSafeClick: SafeClick
) {
    // --- NOUVEAU : On récupère dynamiquement l'icône actuelle ! ---
    val context = LocalContext.current
    val currentIconRes by fr.geotower.utils.AppIconManager.currentIconRes
    val displayIcon = if (currentIconRes != 0) currentIconRes else fr.geotower.utils.AppIconManager.getLogoResId(context)

    val appLanguage by AppConfig.appLanguage
    val sizing = LocalGeoTowerUiStyle.current.sizing

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))

        // On remplace le R.mipmap... par notre variable dynamique
        DrawableImage(resId = displayIcon, modifier = Modifier.size(sizing.component(150.dp)))

        Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))

        Text(stringResource(R.string.brand_geotower), style = sizing.textStyle(MaterialTheme.typography.headlineMedium), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
        Text(
            text = stringResource(R.string.onboarding_welcome_desc),
            textAlign = TextAlign.Center,
            style = sizing.textStyle(MaterialTheme.typography.bodyLarge),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

        // --- CARTE LANGUE (déplacée ici pour le choix dès le 1er écran) ---
        val flag = AppLocale.languageFlag(appLanguage)
        val displayLanguage = stringResource(AppLocale.languageDisplayNameRes(appLanguage))
        Surface(onClick = { onSafeClick("onboarding_language") { onOpenLanguageSheet() } }, color = if (useOneUi) bubbleColor else Color.Transparent, border = cardBorder, shape = cardShape, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(sizing.spacing(16.dp)), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_app_language), style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.common_current_value, displayLanguage), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(sizing.component(32.dp)), contentAlignment = Alignment.Center) { Text(text = flag, fontSize = sizing.text(24.sp)) }
                    Spacer(Modifier.width(sizing.spacing(8.dp)))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
        ) {
            PermissionDetailItem(
                icon = Icons.Default.LocationOn,
                title = stringResource(R.string.onboarding_welcome_nearby_title),
                description = stringResource(R.string.onboarding_welcome_nearby_desc)
            )
            PermissionDetailItem(
                icon = Icons.Default.Map,
                title = stringResource(R.string.onboarding_welcome_map_title),
                description = stringResource(R.string.onboarding_welcome_map_desc)
            )
            PermissionDetailItem(
                icon = Icons.Default.Check,
                title = stringResource(R.string.onboarding_welcome_tools_title),
                description = stringResource(R.string.onboarding_welcome_tools_desc)
            )
        }
    }
}

@Composable
fun StepLocationPermissionDesign() {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))
        Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(sizing.component(80.dp)), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))

        Text(stringResource(R.string.onboarding_location_title), style = sizing.textStyle(MaterialTheme.typography.headlineMedium), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
        Text(
            text = stringResource(R.string.onboarding_location_desc),
            textAlign = TextAlign.Center,
            style = sizing.textStyle(MaterialTheme.typography.bodyLarge),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
        ) {
            PermissionDetailItem(
                icon = Icons.Default.LocationOn,
                title = stringResource(R.string.onboarding_location_nearby_title),
                description = stringResource(R.string.onboarding_location_nearby_desc)
            )
            PermissionDetailItem(
                icon = Icons.Default.Map,
                title = stringResource(R.string.onboarding_location_map_title),
                description = stringResource(R.string.onboarding_location_map_desc)
            )
            PermissionDetailItem(
                icon = Icons.Default.Check,
                title = stringResource(R.string.onboarding_location_privacy_title),
                description = stringResource(R.string.onboarding_location_privacy_desc)
            )
        }
    }
}

@Composable
fun StepNotificationsPermissionDesign() {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))
        Icon(Icons.Default.Notifications, null, modifier = Modifier.size(sizing.component(80.dp)), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))

        Text(stringResource(R.string.onboarding_notifications_title), style = sizing.textStyle(MaterialTheme.typography.headlineMedium), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
        Text(
            text = stringResource(R.string.onboarding_notifications_desc),
            textAlign = TextAlign.Center,
            style = sizing.textStyle(MaterialTheme.typography.bodyLarge),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
        ) {
            PermissionDetailItem(
                icon = Icons.Default.Notifications,
                title = stringResource(R.string.onboarding_notifications_download_title),
                description = stringResource(R.string.onboarding_notifications_download_desc)
            )
            PermissionDetailItem(
                icon = Icons.Default.Map,
                title = stringResource(R.string.onboarding_notifications_update_title),
                description = stringResource(R.string.onboarding_notifications_update_desc)
            )
            PermissionDetailItem(
                icon = Icons.Default.Check,
                title = stringResource(R.string.onboarding_notifications_control_title),
                description = stringResource(R.string.onboarding_notifications_control_desc)
            )
        }
    }
}

/**
 * Étape « suivi live » : elle allume le service de suivi GPS, pas les notifications de l'app.
 *
 * Les deux permissions y jouent des rôles distincts, d'où deux launchers :
 *  - la localisation précise **conditionne** le suivi (sans elle, il n'y a rien à suivre) ;
 *  - POST_NOTIFICATIONS ne décide que de la **visibilité** de la notification live. La refuser
 *    (à l'étape précédente ou ici) n'empêche plus le suivi de tourner.
 */
@Composable
fun StepLiveTrackingDesign(
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean,
    defaultOperator: String,
    onOpenOperatorSheet: () -> Unit
) {
    val context = LocalContext.current
    val liveTrackingEnabled by AppConfig.enableLiveTracking
    val hasOperator = defaultOperator != "Aucun"
    val sizing = LocalGeoTowerUiStyle.current.sizing

    var notificationPermissionMissing by remember {
        mutableStateOf(LiveTrackingController.notificationPermissionMissing(context))
    }
    var locationRefused by remember { mutableStateOf(false) }

    // Launchers plutôt qu'un cast de LocalContext en Activity : le contexte fourni ici est le
    // contexte localisé (GeoTowerLocaleProvider), le cast serait null.
    fun enableTracking() {
        val result = LiveTrackingController.setEnabled(context, true)
        locationRefused = result == LiveTrackingController.StartResult.MissingPreciseLocation
        if (
            result == LiveTrackingController.StartResult.Started &&
            LiveTrackingController.shouldOpenPromotedNotificationSettings(context)
        ) {
            LiveTrackingController.openPromotedNotificationSettings(context)
        }
    }

    // Accordée ou non, le suivi démarre : cette permission ne pilote que l'affichage.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notificationPermissionMissing = !isGranted
        enableTracking()
    }

    fun offerNotificationThenEnable() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            LiveTrackingController.notificationPermissionMissing(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enableTracking()
        }
    }

    // La localisation, elle, est bloquante : refusée à l'étape 1, on la redemande ici plutôt que
    // de laisser l'interrupteur retomber tout seul sans explication.
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            locationRefused = false
            offerNotificationThenEnable()
        } else {
            locationRefused = true
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))
        // Icône de position et non de notification : l'étape précédente portait les notifications,
        // celle-ci porte le suivi. Deux écrans identiques donnaient deux fois « les notifications ».
        Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(sizing.component(80.dp)), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))

        Text(stringResource(R.string.onboarding_live_notifications_title), style = sizing.textStyle(MaterialTheme.typography.headlineMedium), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
        Text(
            text = stringResource(R.string.onboarding_live_notifications_desc),
            textAlign = TextAlign.Center,
            style = sizing.textStyle(MaterialTheme.typography.bodyLarge),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(sizing.spacing(24.dp)))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
        ) {
            PermissionDetailItem(
                icon = Icons.Default.MyLocation,
                title = stringResource(R.string.onboarding_live_notifications_operator_title),
                description = stringResource(R.string.onboarding_live_notifications_operator_desc)
            )
            PermissionDetailItem(
                icon = Icons.Default.LocationOn,
                title = stringResource(R.string.onboarding_live_notifications_nearest_title),
                description = stringResource(R.string.onboarding_live_notifications_nearest_desc)
            )
            PermissionDetailItem(
                icon = Icons.Default.Check,
                title = stringResource(R.string.onboarding_live_notifications_control_title),
                description = stringResource(R.string.onboarding_live_notifications_control_desc)
            )
        }

        Spacer(Modifier.height(sizing.spacing(16.dp)))

        fr.geotower.ui.components.LiveNotificationCard(
            title = stringResource(R.string.onboarding_live_notifications_title),
            desc = if (hasOperator) {
                stringResource(R.string.onboarding_live_notifications_selected_operator, defaultOperator)
            } else {
                stringResource(R.string.onboarding_live_notifications_nearest_mode)
            },
            checked = liveTrackingEnabled,
            onCheckedChange = { isChecked ->
                if (!isChecked) {
                    LiveTrackingController.setEnabled(context, false)
                    locationRefused = false
                } else if (LiveTrackingController.hasPreciseLocationPermission(context)) {
                    offerNotificationThenEnable()
                } else {
                    // Localisation refusée à l'étape 1 : on la redemande, c'est elle qui manque.
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            enabled = true,
            shape = shape,
            border = border,
            bubbleColor = bubbleColor,
            useOneUi = useOneUi
        )

        // Deux messages distincts, pour deux causes distinctes.
        if (locationRefused) {
            Spacer(Modifier.height(sizing.spacing(8.dp)))
            Text(
                text = stringResource(R.string.onboarding_live_tracking_needs_location),
                textAlign = TextAlign.Center,
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.error
            )
        } else if (liveTrackingEnabled && notificationPermissionMissing) {
            Spacer(Modifier.height(sizing.spacing(8.dp)))
            Text(
                text = stringResource(R.string.onboarding_live_tracking_notification_hidden),
                textAlign = TextAlign.Center,
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StepThemeDesign(useOneUi: Boolean, cardShape: Shape, cardBorder: BorderStroke?, bubbleColor: Color, onSafeClick: SafeClick) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

    var themeMode by AppConfig.themeMode
    var isOled by AppConfig.isOledMode
    var isBlurEnabled by AppConfig.isBlurEnabled
    val uiScalePercent by AppConfig.uiScalePercent
    val sizing = LocalGeoTowerUiStyle.current.sizing

    fun updateOneUi(enabled: Boolean) {
        val mode = AppUiMode.fromOneUiEnabled(enabled)
        AppConfig.uiMode.value = mode
        prefs.edit().putString(AppConfig.PREF_UI_MODE, mode.storageKey).apply()
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))
        Icon(Icons.Outlined.Smartphone, null, modifier = Modifier.size(sizing.component(80.dp)), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))

        Text(stringResource(R.string.settings_section_appearance), style = sizing.textStyle(MaterialTheme.typography.headlineMedium), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
        Text(stringResource(R.string.onboarding_theme_desc), textAlign = TextAlign.Center, style = sizing.textStyle(MaterialTheme.typography.bodyLarge), color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(sizing.spacing(32.dp)))

        // --- APPEL AU COMPOSANT CENTRALISÉ ---
        fr.geotower.ui.components.AppearanceOptionsBlock(
            themeMode = themeMode, onThemeChange = { themeMode = it; prefs.edit().putInt("theme_mode", it).apply() },
            isOled = isOled, onOledChange = { isOled = it; prefs.edit().putBoolean("is_oled_mode", it).apply() },
            useOneUi = useOneUi, onOneUiChange = ::updateOneUi,
            isBlur = isBlurEnabled, onBlurChange = { isBlurEnabled = it; prefs.edit().putBoolean("is_blur_enabled", it).apply() },
            uiScalePercent = uiScalePercent,
            onUiScalePercentChange = { newPercent ->
                AppConfig.uiScalePercent.intValue = newPercent
                prefs.edit().putInt(AppConfig.PREF_UI_SCALE_PERCENT, newPercent).apply()
            },
            appIconRes = null, // Pas de sélecteur d'icône au 1er lancement
            onAppIconClick = null,
            shape = cardShape, border = cardBorder, bubbleColor = bubbleColor, safeClick = onSafeClick
        )

        Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))

        fr.geotower.ui.components.ColorPalettePickerContent(
            useOneUi = useOneUi,
            bubbleColor = bubbleColor
        )

        Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))
    }
}

@Composable
fun StepMapDesign(useOneUi: Boolean, bubbleColor: Color, onSafeClick: SafeClick) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

    var mapProvider by AppConfig.mapProvider
    var ignStyle by AppConfig.ignStyle
    val sizing = LocalGeoTowerUiStyle.current.sizing

    val cardShape = if (useOneUi) RoundedCornerShape(28.dp) else RoundedCornerShape(12.dp)
    val cardBorder = if (useOneUi) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))
        Icon(Icons.Default.Map, null, modifier = Modifier.size(sizing.component(80.dp)), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))

        Text(stringResource(R.string.settings_section_mapping), style = sizing.textStyle(MaterialTheme.typography.headlineMedium), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
        Text(stringResource(R.string.onboarding_mapping_desc), textAlign = TextAlign.Center, style = sizing.textStyle(MaterialTheme.typography.bodyLarge), color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(sizing.spacing(32.dp)))

        // --- APPEL AU COMPOSANT CENTRALISÉ ---
        fr.geotower.ui.components.MappingOptionsBlock(
            mapProvider = mapProvider,
            onMapProviderChange = { mapProvider = it; prefs.edit().putInt("map_provider", it).apply() },
            ignStyle = ignStyle,
            onIgnStyleChange = { ignStyle = it; prefs.edit().putInt("ign_style", it).apply() },
            shape = cardShape,
            border = cardBorder,
            bubbleColor = bubbleColor,
            useOneUi = useOneUi,
            safeClick = onSafeClick
        )
    }
}
// ==========================================
// --- NOUVELLE ÉTAPE : BASE DE DONNÉES ---
// ==========================================
@Composable
fun StepDatabaseDesign(useOneUi: Boolean, cardShape: Shape, cardBorder: BorderStroke?, bubbleColor: Color, onSafeClick: SafeClick) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val context = LocalContext.current
    // Génération locale : réservée aux appareils éligibles (RAM/stockage). Dès le 1er lancement, un
    // message « non disponible sur cet appareil » n'apporterait rien : on masque la carte.
    val buildEligibility = remember { LocalBuildCapability.evaluate(context) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))
        Icon(
            painter = painterResource(R.drawable.ic_material_database),
            contentDescription = null,
            modifier = Modifier.size(sizing.component(80.dp)),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))

        Text(stringResource(R.string.settings_section_database), style = sizing.textStyle(MaterialTheme.typography.headlineMedium), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
        Text(stringResource(R.string.onboarding_offline_desc), textAlign = TextAlign.Center, style = sizing.textStyle(MaterialTheme.typography.bodyLarge), color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(sizing.spacing(32.dp)))

        // 🚀 APPEL DU NOUVEAU COMPOSANT PARTAGÉ
        fr.geotower.ui.components.DatabaseDownloadCard(
            useOneUi = useOneUi,
            shape = cardShape,
            border = cardBorder,
            bubbleColor = bubbleColor,
            title = stringResource(R.string.settings_section_database),
            onSafeClick = onSafeClick
        )

        Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

        fr.geotower.ui.components.RadioDatabaseDownloadCard(
            useOneUi = useOneUi,
            shape = cardShape,
            border = cardBorder,
            bubbleColor = bubbleColor,
            onSafeClick = onSafeClick
        )

        Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

        // Identifiants eNB/gNB : donnée du partenaire eNB-Analytics (pas de l'ANFR), d'où
        // l'attribution cliquable portée par la carte elle-même.
        fr.geotower.ui.components.EnbDatabaseDownloadCard(
            useOneUi = useOneUi,
            shape = cardShape,
            border = cardBorder,
            bubbleColor = bubbleColor,
            onSafeClick = onSafeClick
        )

        if (buildEligibility.eligible) {
            Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

            fr.geotower.ui.components.LocalDbBuildCard(
                useOneUi = useOneUi,
                shape = cardShape,
                border = cardBorder,
                bubbleColor = bubbleColor,
                onSafeClick = onSafeClick
            )
        }
    }
}
// --- COMPOSANTS UTILITAIRES ---

@Composable
fun PermissionDetailItem(icon: ImageVector, title: String, description: String) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(sizing.spacing(14.dp)),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(sizing.component(40.dp))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(sizing.component(20.dp))
                    )
                }
            }
            Spacer(modifier = Modifier.width(sizing.spacing(14.dp)))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = sizing.textStyle(MaterialTheme.typography.titleSmall), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(sizing.spacing(2.dp)))
                Text(text = description, style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PagerIndicator(totalSteps: Int, currentStep: Int) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        repeat(totalSteps) { index ->
            val isActive = index == currentStep
            val color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            val width = if (isActive) sizing.component(32.dp) else sizing.component(12.dp)
            Box(modifier = Modifier.padding(horizontal = sizing.spacing(4.dp)).height(sizing.component(8.dp)).width(width).clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}

@Composable
fun DrawableImage(resId: Int, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setImageResource(resId) } },
        modifier = modifier,
        update = { it.setImageResource(resId) }
    )
}
