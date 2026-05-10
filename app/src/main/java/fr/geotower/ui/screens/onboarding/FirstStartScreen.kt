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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.data.db.GeoTowerDatabaseValidator
import fr.geotower.data.workers.DatabaseDownloadWorker
import fr.geotower.ui.components.colorPaletteFadingEdge
import fr.geotower.services.LiveTrackingController
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.collectAsState

@Composable
fun FirstStartScreen(
    repository: AnfrRepository,
    onFinished: () -> Unit
) {
    var lastClickTime by remember { mutableLongStateOf(0L) }
    val debounceTime = 700L

    fun safeClick(action: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > debounceTime) {
            lastClickTime = currentTime
            action()
        }
    }

    val totalSteps = 7

    // 1. La mémoire : Quelle est la page maximale atteinte ?
    var maxUnlockedStep by remember { mutableIntStateOf(0) }

    // 2. Le Pager : Il s'adapte au nombre de pages débloquées.
    val pagerState = rememberPagerState(pageCount = { maxUnlockedStep + 1 })

    // 3. Un CoroutineScope pour animer le Pager lors du clic sur le bouton
    val coroutineScope = rememberCoroutineScope()

    // 4. On garde une variable "currentStep" dérivée du pager pour le reste de ton code (Boutons, Indicateurs)
    val currentStep = pagerState.currentPage

    fun goToNextStep() {
        if (currentStep < totalSteps - 1) {
            if (maxUnlockedStep < currentStep + 1) maxUnlockedStep = currentStep + 1
            coroutineScope.launch { pagerState.animateScrollToPage(currentStep + 1) }
        } else {
            onFinished()
        }
    }

    var showLocationPermissionDialog by remember { mutableStateOf(false) }
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

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        isNotificationPermissionHandled = true
    }

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE) }
    var forceOneUi by AppConfig.forceOneUiTheme
    val defaultOperator by AppConfig.defaultOperator // Récupération de l'opérateur actuel
    val liveNotifsEnabled by AppConfig.enableLiveNotifications

    LaunchedEffect(Unit) {
        if (!prefs.contains("force_one_ui")) {
            val isSamsung = android.os.Build.MANUFACTURER.contains("samsung", ignoreCase = true)
            if (isSamsung) forceOneUi = true
        }
        AppConfig.localDatabaseState.value = withContext(Dispatchers.IO) {
            GeoTowerDatabaseValidator.getInstalledDatabaseStatus(context).state
        }
    }

    val useOneUi = forceOneUi
    var showOperatorSheet by remember { mutableStateOf(false) }
    var showWarningDialog by remember { mutableStateOf(false) } // État du pop-up d'avertissement
    var showDbWarning by remember { mutableStateOf(false) }
    var showUnitSheet by remember { mutableStateOf(false) }

    // ✅ NOUVEAU : Variables pour gérer le pop-up de succès de fin de téléchargement
    var showSuccessDialog by remember { mutableStateOf(false) }
    var wasSyncing by remember { mutableStateOf(false) }

    val workManager = remember { androidx.work.WorkManager.getInstance(context) }
    val workInfos by workManager.getWorkInfosForUniqueWorkFlow(DatabaseDownloadWorker.UNIQUE_WORK_NAME).collectAsState(initial = emptyList())
    val currentWork = workInfos.firstOrNull()
    val isSyncing = currentWork?.state == androidx.work.WorkInfo.State.RUNNING || currentWork?.state == androidx.work.WorkInfo.State.ENQUEUED

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
    var appLanguage by remember { mutableStateOf(prefs.getString("app_language", AppStrings.LANGUAGE_SYSTEM) ?: AppStrings.LANGUAGE_SYSTEM) }
    var showLanguageSheet by remember { mutableStateOf(false) }

    // --- LECTURE RÉACTIVE DU THÈME ---
    val themeMode by AppConfig.themeMode
    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)
    val isOled by AppConfig.isOledMode

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
                .padding(horizontal = 24.dp),
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
                    pageSpacing = 16.dp,
                    // Rend le swipe inactif vers l'avant si la page n'est pas débloquée
                    userScrollEnabled = true,
                    verticalAlignment = Alignment.Top
                ) { page ->
                    val pageScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (page == 3) Modifier.colorPaletteFadingEdge(pageScrollState) else Modifier)
                            .verticalScroll(pageScrollState),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (page) {
                            0 -> StepWelcomeDesign()
                            1 -> StepLocationPermissionDesign()
                            2 -> StepNotificationsPermissionDesign(
                                shape = cardShape,
                                border = cardBorder,
                                bubbleColor = bubbleColor,
                                useOneUi = useOneUi
                            )
                            // ✅ NOUVELLE PAGE INSÉRÉE ICI :
                            3 -> StepThemeDesign(useOneUi, cardShape, cardBorder, bubbleColor, onSafeClick = { action -> safeClick(action) })
                            // L'ancienne page 3 devient la 4 :
                            4 -> StepMapDesign(useOneUi, bubbleColor, onSafeClick = { action -> safeClick(action) })
                            5 -> StepDatabaseDesign(useOneUi, cardShape, cardBorder, bubbleColor, onSafeClick = { action -> safeClick(action) })
                            6 -> StepPreferencesDesign(
                                useOneUi = useOneUi,
                                cardShape = cardShape,
                                cardBorder = cardBorder,
                                bubbleColor = bubbleColor,
                                onOpenOperatorSheet = { showOperatorSheet = true },
                                onOpenLanguageSheet = { showLanguageSheet = true },
                                onOpenUnitSheet = { showUnitSheet = true },
                                onSafeClick = { action -> safeClick(action) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val hasLocationPermission =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val isLocationStepReady = hasLocationPermission || isLocationPermissionHandled
            val hasNotificationPermission =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val isNotificationStepReady = hasNotificationPermission || isNotificationPermissionHandled

            val buttonText = when(currentStep) {
                0 -> AppStrings.btnStartConfiguration
                1 -> if (isLocationStepReady) AppStrings.btnNext else AppStrings.btnAuthorize
                2 -> if (isNotificationStepReady) AppStrings.btnNext else AppStrings.btnAuthorize
                totalSteps - 1 -> AppStrings.btnLetsGo
                else -> AppStrings.btnNext
            }
            Button(
                onClick = {
                    safeClick {
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
                                if (currentStep == 5 && !isSyncing && AppConfig.localDatabaseState.value == null) {
                                    coroutineScope.launch {
                                        val dbState = withContext(Dispatchers.IO) {
                                            GeoTowerDatabaseValidator.getInstalledDatabaseStatus(context).state
                                        }
                                        AppConfig.localDatabaseState.value = dbState
                                        if (dbState == GeoTowerDatabaseValidator.LocalDatabaseState.VALID) {
                                            goToNextStep()
                                        } else {
                                            showDbWarning = true
                                        }
                                    }
                                    return@safeClick
                                }

                                val isDbDownloaded = AppConfig.localDatabaseState.value ==
                                    GeoTowerDatabaseValidator.LocalDatabaseState.VALID

                                // ✅ CORRECTION : On affiche l'avertissement UNIQUEMENT si ça ne télécharge pas ET que ce n'est pas installé
                                if (currentStep == 5 && !isSyncing && !isDbDownloaded) {
                                    // 🚨 Bloque sur la page 3 (Base de données) et affiche le pop-up
                                    showDbWarning = true
                                } else {
                                    // ➡️ Passe à la page suivante normalement
                                    if (maxUnlockedStep < currentStep + 1) {
                                        maxUnlockedStep = currentStep + 1
                                    }
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(currentStep + 1)
                                    }
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(text = buttonText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                if (currentStep == totalSteps - 1) {
                    Icon(Icons.Default.Check, contentDescription = null)
                } else if (currentStep > 0) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }

    // --- POP-UP D'AVERTISSEMENT SI AUCUN OPÉRATEUR ---
    if (showLocationPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showLocationPermissionDialog = false },
            title = { Text(text = AppStrings.onboardingLocationDisabledTitle, fontWeight = FontWeight.Bold) },
            text = { Text(AppStrings.onboardingLocationDisabledDesc) },
            shape = cardShape,
            containerColor = MaterialTheme.colorScheme.surface,
            confirmButton = {
                Button(
                    onClick = {
                        showLocationPermissionDialog = false
                        isLocationPermissionHandled = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(AppStrings.continueAnyway, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLocationPermissionDialog = false
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                ) {
                    Text(AppStrings.retry)
                }
            }
        )
    }

    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
            title = { Text(text = AppStrings.warningNoOpTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(AppStrings.warningNoOpDesc)
                    if (liveNotifsEnabled) {
                        Spacer(Modifier.height(8.dp))
                        val liveWarningLead = AppStrings.warningNoOpLiveNotificationsLead
                        val liveWarningLabel = AppStrings.warningNoOpLiveNotificationsLabel
                        val liveWarningMiddle = AppStrings.warningNoOpLiveNotificationsMiddle
                        val liveWarningHighlight = AppStrings.warningNoOpLiveNotificationsHighlight
                        val liveWarningSuffix = AppStrings.warningNoOpLiveNotificationsSuffix
                        Text(
                            buildAnnotatedString {
                                append(liveWarningLead)
                                withStyle(
                                    SpanStyle(
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                ) {
                                    append(liveWarningLabel)
                                }
                                append(liveWarningMiddle)
                                withStyle(
                                    SpanStyle(
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                ) {
                                    append(liveWarningHighlight)
                                }
                                append(liveWarningSuffix)
                            }
                        )
                    }
                }
            },
            shape = cardShape,
            containerColor = MaterialTheme.colorScheme.surface,
            confirmButton = {
                TextButton(
                    onClick = {
                        showWarningDialog = false
                        if (liveNotifsEnabled) {
                            AppConfig.enableLiveNotifications.value = false
                            prefs.edit().putBoolean("enable_live_notifications", false).apply()
                            LiveTrackingController.stop(context)
                        }
                        onFinished() // L'utilisateur force la continuation
                    }
                ) {
                    Text(AppStrings.warningContinue, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text(AppStrings.warningChooseOp, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // --- POP-UP D'AVERTISSEMENT SI BASE DE DONNÉES NON TÉLÉCHARGÉE ---
    if (showDbWarning) {
        AlertDialog(
            onDismissRequest = { showDbWarning = false },
            title = { Text(text = AppStrings.dbWarningTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(AppStrings.dbWarningDesc)
                    Spacer(Modifier.height(8.dp))
                    Text(AppStrings.dbWarningQuestion)
                }
            },
            shape = cardShape,
            containerColor = MaterialTheme.colorScheme.surface,
            confirmButton = {
                TextButton(
                    onClick = {
                        showDbWarning = false
                        // On force le passage à la page suivante !
                        if (maxUnlockedStep < currentStep + 1) {
                            maxUnlockedStep = currentStep + 1
                        }
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(currentStep + 1)
                        }
                    }
                ) {
                    Text(AppStrings.continueAnyway, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDbWarning = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(AppStrings.cancel, fontWeight = FontWeight.Bold)
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
            title = { Text(text = AppStrings.dbSuccessTitle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
            text = { Text(AppStrings.dbSuccessDesc) },
            shape = cardShape,
            containerColor = MaterialTheme.colorScheme.surface,
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        // ✅ NOUVEAU : On désactive le pop-up global pour qu'il n'apparaisse pas sur l'accueil
                        fr.geotower.AppGlobalState.showDbSuccessPopup.value = false

                        // On débloque et on fait glisser la page automatiquement vers l'étape suivante !
                        if (maxUnlockedStep < currentStep + 1) {
                            maxUnlockedStep = currentStep + 1
                        }
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(currentStep + 1)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(AppStrings.btnContinue, fontWeight = FontWeight.Bold)
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
                if (selectedOperator != "Aucun" && AppConfig.enableLiveNotifications.value) {
                    val eligibility = LiveTrackingController.startIfEligible(context)
                    if (
                        eligibility == LiveTrackingController.StartResult.Started &&
                        LiveTrackingController.shouldOpenPromotedNotificationSettings(context)
                    ) {
                        LiveTrackingController.openPromotedNotificationSettings(context)
                    }
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
    onOpenLanguageSheet: () -> Unit,
    onOpenUnitSheet: () -> Unit,
    onSafeClick: (() -> Unit) -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

    var defaultOperator by AppConfig.defaultOperator
    val appLanguage by AppConfig.appLanguage

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))
        Icon(Icons.Outlined.Tune, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))

        Text(AppStrings.preferences, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(AppStrings.prefDesc, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(32.dp))

        // --- CARTE OPÉRATEUR ---
        Surface(onClick = { onOpenOperatorSheet() }, color = if (useOneUi) bubbleColor else Color.Transparent, border = cardBorder, shape = cardShape, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(AppStrings.defaultOperator, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (defaultOperator == "Aucun") AppStrings.selectOperator else AppStrings.current(defaultOperator), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val operatorLogoRes = when (defaultOperator) { "Orange" -> R.drawable.logo_orange; "Bouygues", "Bouygues Telecom" -> R.drawable.logo_bouygues; "SFR" -> R.drawable.logo_sfr; "Free" -> R.drawable.logo_free; else -> null }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (operatorLogoRes != null) { Image(painter = painterResource(id = operatorLogoRes), contentDescription = null, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp))); Spacer(modifier = Modifier.width(12.dp)) }
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = AppStrings.select, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- CARTE LANGUE ---
        val flag = when (appLanguage) {
            AppStrings.LANGUAGE_FRENCH -> "🇫🇷"
            AppStrings.LANGUAGE_ENGLISH -> "🇬🇧"
            AppStrings.LANGUAGE_PORTUGUESE -> "🇵🇹"
            AppStrings.LANGUAGE_SYSTEM -> "📱"
            else -> "🌐"
        }
        val displayLanguage = AppStrings.languageDisplayName(appLanguage)

        Surface(onClick = { onOpenLanguageSheet() }, color = if (useOneUi) bubbleColor else Color.Transparent, border = cardBorder, shape = cardShape, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(AppStrings.appLanguageLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(AppStrings.current(displayLanguage), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) { Text(text = flag, fontSize = 24.sp) }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        // --- CARTE UNITÉS ---
        Surface(onClick = { onOpenUnitSheet() }, color = if (useOneUi) bubbleColor else Color.Transparent, border = cardBorder, shape = cardShape, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(AppStrings.unitSettingsTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val unitDesc = if(AppConfig.distanceUnit.intValue == 0) "km, km/h" else "mi, mph"
                    Text(unitDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) { Text(text = "📏", fontSize = 24.sp) }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun StepWelcomeDesign() {
    // --- NOUVEAU : On récupère dynamiquement l'icône actuelle ! ---
    val context = LocalContext.current
    val currentIconRes by fr.geotower.utils.AppIconManager.currentIconRes
    val displayIcon = if (currentIconRes != 0) currentIconRes else fr.geotower.utils.AppIconManager.getLogoResId(context)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))

        // On remplace le R.mipmap... par notre variable dynamique
        DrawableImage(resId = displayIcon, modifier = Modifier.size(150.dp))

        Spacer(modifier = Modifier.height(24.dp))

        Text(AppStrings.onboardingWelcomeTitle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = AppStrings.onboardingWelcomeDesc,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PermissionDetailItem(
                icon = Icons.Default.LocationOn,
                title = AppStrings.onboardingWelcomeNearbyTitle,
                description = AppStrings.onboardingWelcomeNearbyDesc
            )
            PermissionDetailItem(
                icon = Icons.Default.Map,
                title = AppStrings.onboardingWelcomeMapTitle,
                description = AppStrings.onboardingWelcomeMapDesc
            )
            PermissionDetailItem(
                icon = Icons.Default.Check,
                title = AppStrings.onboardingWelcomeToolsTitle,
                description = AppStrings.onboardingWelcomeToolsDesc
            )
        }
    }
}

@Composable
fun StepLocationPermissionDesign() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))
        Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))

        Text(AppStrings.onboardingLocationTitle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = AppStrings.onboardingLocationDesc,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PermissionDetailItem(
                icon = Icons.Default.LocationOn,
                title = AppStrings.onboardingLocationNearbyTitle,
                description = AppStrings.onboardingLocationNearbyDesc
            )
            PermissionDetailItem(
                icon = Icons.Default.Map,
                title = AppStrings.onboardingLocationMapTitle,
                description = AppStrings.onboardingLocationMapDesc
            )
            PermissionDetailItem(
                icon = Icons.Default.Check,
                title = AppStrings.onboardingLocationPrivacyTitle,
                description = AppStrings.onboardingLocationPrivacyDesc
            )
        }
    }
}

@Composable
fun StepNotificationsPermissionDesign(
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))
        Icon(Icons.Default.Notifications, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))

        Text(AppStrings.onboardingNotificationsTitle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = AppStrings.onboardingNotificationsDesc,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PermissionDetailItem(
                icon = Icons.Default.Notifications,
                title = AppStrings.onboardingNotificationsDownloadTitle,
                description = AppStrings.onboardingNotificationsDownloadDesc
            )
            PermissionDetailItem(
                icon = Icons.Default.Map,
                title = AppStrings.onboardingNotificationsUpdateTitle,
                description = AppStrings.onboardingNotificationsUpdateDesc
            )
            PermissionDetailItem(
                icon = Icons.Default.Check,
                title = AppStrings.onboardingNotificationsControlTitle,
                description = AppStrings.onboardingNotificationsControlDesc
            )
        }

        Spacer(Modifier.height(12.dp))

        // --- NOUVEAU : NOTIFICATION LIVE ---
        val liveNotifsEnabled by AppConfig.enableLiveNotifications

        fr.geotower.ui.components.LiveNotificationCard(
            title = AppStrings.liveNotificationTitle,
            desc = AppStrings.liveNotificationDesc,
            checked = liveNotifsEnabled,
            onCheckedChange = { isChecked ->
                if (isChecked) {
                    val eligibility = LiveTrackingController.eligibility(context)
                    if (
                        eligibility == LiveTrackingController.StartResult.MissingNotifications &&
                        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                    ) {
                        (context as? android.app.Activity)?.requestPermissions(
                            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                            1004
                        )
                    }

                    if (
                        eligibility == LiveTrackingController.StartResult.Started ||
                        eligibility == LiveTrackingController.StartResult.MissingOperator
                    ) {
                        AppConfig.enableLiveNotifications.value = true
                        prefs.edit().putBoolean("enable_live_notifications", true).apply()
                        if (eligibility == LiveTrackingController.StartResult.Started) {
                            if (LiveTrackingController.shouldOpenPromotedNotificationSettings(context)) {
                                LiveTrackingController.openPromotedNotificationSettings(context)
                            }
                            LiveTrackingController.startIfEligible(context)
                        } else {
                            LiveTrackingController.stop(context)
                        }
                    } else {
                        AppConfig.enableLiveNotifications.value = false
                        prefs.edit().putBoolean("enable_live_notifications", false).apply()
                        LiveTrackingController.stop(context)
                    }
                } else {
                    AppConfig.enableLiveNotifications.value = false
                    prefs.edit().putBoolean("enable_live_notifications", false).apply()
                    LiveTrackingController.stop(context)
                }
            },
            enabled = true,
            shape = shape,
            border = border,
            bubbleColor = bubbleColor,
            useOneUi = useOneUi
        )
    }
}

@Composable
fun StepThemeDesign(useOneUi: Boolean, cardShape: Shape, cardBorder: BorderStroke?, bubbleColor: Color, onSafeClick: (() -> Unit) -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

    var themeMode by AppConfig.themeMode
    var isOled by AppConfig.isOledMode
    var isBlurEnabled by AppConfig.isBlurEnabled
    var forceOneUi by AppConfig.forceOneUiTheme
    val menuSize by AppConfig.menuSize

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))
        Icon(Icons.Outlined.Smartphone, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))

        Text(AppStrings.appearance, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(AppStrings.themeDesc, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(32.dp))

        // --- APPEL AU COMPOSANT CENTRALISÉ ---
        fr.geotower.ui.components.AppearanceOptionsBlock(
            themeMode = themeMode, onThemeChange = { themeMode = it; prefs.edit().putInt("theme_mode", it).apply() },
            isOled = isOled, onOledChange = { isOled = it; prefs.edit().putBoolean("is_oled_mode", it).apply() },
            useOneUi = forceOneUi, onOneUiChange = { forceOneUi = it; prefs.edit().putBoolean("force_one_ui", it).apply() },
            isBlur = isBlurEnabled, onBlurChange = { isBlurEnabled = it; prefs.edit().putBoolean("is_blur_enabled", it).apply() },
            menuSize = menuSize,
            onMenuSizeChange = { newSize ->
                AppConfig.menuSize.value = newSize
                prefs.edit().putString("menuSize", newSize).apply()
            },
            appIconRes = null, // Pas de sélecteur d'icône au 1er lancement
            onAppIconClick = null,
            shape = cardShape, border = cardBorder, bubbleColor = bubbleColor, safeClick = onSafeClick
        )

        Spacer(modifier = Modifier.height(24.dp))

        fr.geotower.ui.components.ColorPalettePickerContent(
            useOneUi = useOneUi,
            bubbleColor = bubbleColor
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun StepMapDesign(useOneUi: Boolean, bubbleColor: Color, onSafeClick: (() -> Unit) -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

    var mapProvider by AppConfig.mapProvider
    var ignStyle by AppConfig.ignStyle

    val cardShape = if (useOneUi) RoundedCornerShape(28.dp) else RoundedCornerShape(12.dp)
    val cardBorder = if (useOneUi) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))
        Icon(Icons.Default.Map, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))

        Text(AppStrings.mapping, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(AppStrings.mapDesc, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(32.dp))

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
fun StepDatabaseDesign(useOneUi: Boolean, cardShape: Shape, cardBorder: BorderStroke?, bubbleColor: Color, onSafeClick: (() -> Unit) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))
        Icon(
            painter = painterResource(R.drawable.ic_material_database),
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(AppStrings.database, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(AppStrings.offlineDesc, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(32.dp))

        // 🚀 APPEL DU NOUVEAU COMPOSANT PARTAGÉ
        fr.geotower.ui.components.DatabaseDownloadCard(
            useOneUi = useOneUi,
            shape = cardShape,
            border = cardBorder,
            bubbleColor = bubbleColor,
            onSafeClick = onSafeClick
        )
    }
}
// --- COMPOSANTS UTILITAIRES ---

@Composable
fun PermissionDetailItem(icon: ImageVector, title: String, description: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PagerIndicator(totalSteps: Int, currentStep: Int) {
    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        repeat(totalSteps) { index ->
            val isActive = index == currentStep
            val color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            val width = if (isActive) 32.dp else 12.dp
            Box(modifier = Modifier.padding(horizontal = 4.dp).height(8.dp).width(width).clip(RoundedCornerShape(4.dp)).background(color))
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
