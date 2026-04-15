@file:OptIn(ExperimentalMaterial3Api::class)
package fr.geotower.ui.screens.about

import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppIconManager
import fr.geotower.utils.AppStrings
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.NewReleases as FilledNewReleases

@Composable
fun AboutScreen(navController: NavController) {

    // --- SÉCURITÉ ANTI-SPAM ---
    var lastClickTime by remember { mutableLongStateOf(0L) }
    val debounceTime = 700L

    fun safeClick(action: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > debounceTime) {
            lastClickTime = currentTime
            action()
        }
    }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // --- 1. LECTURE RÉACTIVE DU THÈME (Comme sur l'accueil) ---
    val themeMode by AppConfig.themeMode
    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)

    // --- 2. CONFIGURATION ONE UI DYNAMIQUE ---
    val useOneUi = AppConfig.useOneUiDesign
    val isOled by AppConfig.isOledMode // "by" permet de réagir au changement instantanément

    val cardShape = if (useOneUi) RoundedCornerShape(28.dp) else RoundedCornerShape(12.dp)

    // Couleur de fond dynamique pour l'écran (gère le mode OLED)
    val mainBgColor = if (isDark && isOled) Color.Black else MaterialTheme.colorScheme.background

    // Couleur des bulles/cartes selon le mode (One UI utilise un gris anthracite en sombre)
    val bubbleBaseColor = if (useOneUi) {
        if (isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) // Style classique
    }

    // --- LECTURE DU MODE DE NAVIGATION DEPUIS AppConfig ---
    val navMode = AppConfig.navMode.intValue

    // --- RÉCUPÉRATION DE VERSION ---
    val appVersion = remember {
        try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
            "Version ${packageInfo.versionName}"
        } catch (e: Exception) {
            "Version Inconnue"
        }
    }

    val appTitle = "GeoTower"
    val logoResId by AppIconManager.currentIconRes
    val isWideScreen = configuration.screenWidthDp >= 600

    var activeSectionIndex by remember { mutableIntStateOf(0) }

    val menuItems = listOf(
        Triple(AppStrings.aboutPresentation, Icons.Outlined.Info, 0),
        Triple(AppStrings.aboutNew, Icons.Outlined.NewReleases, 1),
        Triple(AppStrings.privacyCategory, Icons.Outlined.Lock, 2), // <-- NOUVEL ONGLET
        Triple(AppStrings.aboutSources, Icons.Outlined.Folder, 3),  // Décalé à 3
        Triple(AppStrings.aboutDev, Icons.Default.EditNote, 4)      // Décalé à 4
    )

    // --- LOGIQUE DE SYNCHRONISATION FLUIDE ---
    if (isWideScreen && navMode == 0) {
        LaunchedEffect(scrollState.value, scrollState.maxValue) {
            val maxScroll = scrollState.maxValue.toFloat()
            val currentScroll = scrollState.value.toFloat()

            if (maxScroll == 0f) {
                activeSectionIndex = 0
            } else {
                val ratio = currentScroll / maxScroll

                activeSectionIndex = when {
                    ratio >= 0.98f -> 4
                    ratio < 0.20f -> 0
                    ratio < 0.45f -> 1
                    ratio < 0.70f -> 2
                    ratio < 0.95f -> 3
                    else -> 4
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (logoResId == 0) AppIconManager.getLogoResId(context)
    }

    Scaffold(
        containerColor = mainBgColor, // Utilisation de la couleur de fond dynamique
        topBar = {
            if (!isWideScreen) {
                AboutTopBar { safeClick { navController.popBackStack() } }
            }
        }
    ) { innerPadding ->
        // 🚀 NOUVEL AFFICHAGE QUI UTILISE LE COMPOSANT COMMUN
        fr.geotower.ui.components.ResponsiveDualPaneLayout(
            modifier = Modifier.padding(innerPadding),
            // ✅ AJOUT : onCloseSidebar
            sidebar = { width, onCloseSidebar ->
                Row(modifier = Modifier.width(width).fillMaxHeight().background(mainBgColor)) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(top = 16.dp, bottom = 24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { safeClick { navController.popBackStack() } }, modifier = Modifier.padding(start = 8.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            // ✅ RETOUR DU BOUTON MENU
                            IconButton(onClick = onCloseSidebar, modifier = Modifier.padding(end = 8.dp)) {
                                Icon(Icons.Default.Menu, contentDescription = "Fermer Menu", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        menuItems.forEach { (title, icon, index) ->
                            AboutNavigationMenuItem(
                                title = title,
                                icon = icon,
                                isSelected = activeSectionIndex == index,
                                isDark = isDark,
                                useOneUi = useOneUi,
                                onClick = {
                                    activeSectionIndex = index
                                    if (navMode == 0) {
                                        val targetScroll = when(index) {
                                            0 -> 0
                                            1 -> 350
                                            2 -> 1000
                                            else -> 1450
                                        }
                                        scope.launch { scrollState.animateScrollTo(targetScroll) }
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        AboutNavigationMenuItem(
                            title = AppStrings.settingsTitle,
                            icon = Icons.Outlined.Settings,
                            isSelected = false,
                            isDark = isDark,
                            useOneUi = useOneUi,
                            onClick = {
                                safeClick {
                                    try {
                                        navController.navigate("settings") {
                                            launchSingleTop = true
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                        }
                                    } catch (e: IllegalArgumentException) {
                                        e.printStackTrace()
                                        navController.popBackStack()
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        Text(
                            text = appVersion,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            },
            content = { isExpanded, isSidebarVisible, onToggleSidebar ->
                Column(modifier = Modifier.fillMaxSize().background(mainBgColor)) {

                    // --- EN-TÊTE TABLETTE ---
                    if (isExpanded) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(
                                visible = !isSidebarVisible,
                                enter = fadeIn(animationSpec = tween(300)) + expandHorizontally(),
                                exit = fadeOut(animationSpec = tween(200)) + shrinkHorizontally()
                            ) {
                                IconButton(
                                    onClick = onToggleSidebar,
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = "Ouvrir Menu", tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }

                            Text(
                                text = AppStrings.about,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )

                            AnimatedVisibility(
                                visible = !isSidebarVisible,
                                enter = fadeIn(animationSpec = tween(300)) + expandHorizontally(),
                                exit = fadeOut(animationSpec = tween(200)) + shrinkHorizontally()
                            ) {
                                Spacer(modifier = Modifier.width(56.dp))
                            }
                        }
                    }

                    // --- CONTENU DE LA PAGE ---
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (navMode == 0 || !isExpanded) Modifier.aboutFadingEdge(scrollState) else Modifier)
                            .then(if (navMode == 0 || !isExpanded) Modifier.verticalScroll(scrollState) else Modifier)
                            .padding(horizontal = if (isExpanded) 48.dp else 24.dp)
                    ) {
                        if (navMode == 0 || !isExpanded) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AllAboutContent(appTitle, appVersion, logoResId, cardShape, bubbleBaseColor)
                                Spacer(modifier = Modifier.height(60.dp))
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                when(activeSectionIndex) {
                                    0 -> SectionPresentation(appTitle, appVersion, logoResId)
                                    1 -> SectionNouveautes(appVersion, cardShape, bubbleBaseColor)
                                    2 -> SectionConfidentialite(cardShape, bubbleBaseColor)
                                    3 -> SectionSources(cardShape, bubbleBaseColor)
                                    4 -> SectionDeveloppement()
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

// ============================================================
// COMPOSANT MENU DE NAVIGATION (FOLD)
// ============================================================

@Composable
fun AboutNavigationMenuItem(title: String, icon: ImageVector, isSelected: Boolean, isDark: Boolean, useOneUi: Boolean, onClick: () -> Unit) {
    // Si One UI est activé, on utilise la couleur primaire en fond comme pour les switchs
    val bgColor = if (isSelected) {
        if (useOneUi) MaterialTheme.colorScheme.primaryContainer else {
            if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)
        }
    } else Color.Transparent

    val contentColor = if (isSelected) {
        if (useOneUi) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
    } else {
        if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f)
    }

    val shape = if (useOneUi) RoundedCornerShape(16.dp) else RoundedCornerShape(12.dp)

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = shape,
        color = bgColor
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = contentColor)
        }
    }
}

@Composable
fun AboutTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.Transparent).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
        Text(AppStrings.about, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Spacer(Modifier.width(48.dp))
    }
}

// ============================================================
// CONTENU DES SECTIONS À PROPOS
// ============================================================

@Composable
fun AllAboutContent(appTitle: String, appVersion: String, logoResId: Int, cardShape: Shape, bubbleColor: Color) {
    SectionPresentation(appTitle, appVersion, logoResId)
    Spacer(modifier = Modifier.height(48.dp))
    SectionNouveautes(appVersion, cardShape, bubbleColor)
    Spacer(modifier = Modifier.height(48.dp))

    // --- NOUVEAU ---
    SectionConfidentialite(cardShape, bubbleColor)
    Spacer(modifier = Modifier.height(48.dp))
    // ---------------

    SectionSources(cardShape, bubbleColor)
    Spacer(modifier = Modifier.height(48.dp))
    SectionDeveloppement()
}

@Composable
fun SectionPresentation(appTitle: String, appVersion: String, logoResId: Int) {
    AboutDrawableImage(resId = logoResId, modifier = Modifier.size(150.dp).padding(top = 16.dp))
    Spacer(modifier = Modifier.height(16.dp))
    Text(appTitle, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Text(appVersion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(32.dp))
    Text(
        text = AppStrings.aboutIntro,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
fun SectionNouveautes(appVersion: String, cardShape: Shape, bubbleColor: Color) {
    val releaseNotes = mapOf(
        "Interface & Design" to listOf(
            "Carte des Antennes :" to listOf(
                "Ajout des cartes Hors-Ligne",
                "Modification des clusters"
            ),
            "Détail des sites :" to listOf(
                "Ajout du statut des antennes"
            )
        )
    )

    SectionTitle("Nouveautés ($appVersion)")
    Card(
        colors = CardDefaults.cardColors(containerColor = if (bubbleColor == Color.Transparent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else bubbleColor),
        shape = cardShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FilledNewReleases, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Dernières modifications", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))

            releaseNotes.forEach { (category, notes) ->
                Text(category, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                notes.forEach { note ->
                    when (note) {
                        is Pair<*, *> -> {
                            Text(note.first.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 2.dp))
                            (note.second as? List<*>)?.forEach { subItem ->
                                Text("• $subItem", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 20.dp, top = 1.dp, bottom = 1.dp))
                            }
                        }
                        is String -> Text("• $note", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

            }
        }
    }
}

@Composable
fun SectionSources(cardShape: Shape, bubbleColor: Color) {
    SectionTitle(AppStrings.aboutSources)
    val cardColor = if (bubbleColor == Color.Transparent) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else bubbleColor

    // ✅ On initialise le gestionnaire de liens
    val uriHandler = LocalUriHandler.current

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = cardShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 1. ANFR (Données)
            CreditItem(AppStrings.srcAntennas, AppStrings.srcAntennasDesc)

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // 2. IGN (Cliquable)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri("https://geoservices.ign.fr/") }
            ) {
                CreditItem(AppStrings.srcIgn, AppStrings.srcIgnDesc)
                Text("geoservices.ign.fr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // 3. OpenStreetMap (Cliquable)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri("https://www.openstreetmap.org/copyright") }
            ) {
                CreditItem(AppStrings.srcOsm, AppStrings.srcOsmDesc)
                Text("www.openstreetmap.org", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // 4. OpenAndroMaps (Cliquable)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri("https://www.openandromaps.org/") }
            ) {
                CreditItem(AppStrings.openAndroMapsTitle, AppStrings.openAndroMapsDesc)
                Text("www.openandromaps.org", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // 5. Inspiration
            CreditItem(AppStrings.srcInspo, AppStrings.srcInspoDesc)
        }
    }
}

@Composable
fun SectionDeveloppement() {
    SectionTitle(AppStrings.aboutDev) // <-- MODIFIÉ
    ListItem(
        headlineContent = { Text(AppStrings.devCredit) }, // <-- MODIFIÉ
        leadingContent = { Icon(imageVector = Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

// ============================================================
// UTILITAIRES
// ============================================================

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
}

@Composable
private fun CreditItem(title: String, description: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun Modifier.aboutFadingEdge(scrollState: androidx.compose.foundation.ScrollState): Modifier {
    if (!AppConfig.isBlurEnabled.value) return this
    val fadeHeight = 80.dp
    return this.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        val heightPx = fadeHeight.toPx()
        val topAlpha = (scrollState.value / heightPx).coerceIn(0f, 1f)
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 1f - topAlpha), Color.Black), 0f, heightPx), blendMode = BlendMode.DstIn)
        val remainingScroll = scrollState.maxValue - scrollState.value
        val bottomAlpha = (remainingScroll / heightPx).coerceIn(0f, 1f)
        drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 1f - bottomAlpha)), size.height - heightPx, size.height), blendMode = BlendMode.DstIn)
    }
}

@Composable
private fun AboutDrawableImage(resId: Int, modifier: Modifier = Modifier, contentDescription: String? = null) {
    AndroidView<ImageView>(
        modifier = modifier,
        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { imageView ->
            if (resId != 0) imageView.setImageResource(resId)
            imageView.contentDescription = contentDescription
        }
    )
}

@Composable
fun SectionConfidentialite(cardShape: Shape, bubbleColor: Color) {
    SectionTitle(AppStrings.privacyCategory)
    val cardColor = if (bubbleColor == Color.Transparent) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else bubbleColor

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = cardShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(AppStrings.yourDataTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(AppStrings.yourDataDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}