package fr.geotower.ui.components

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import fr.geotower.R
import fr.geotower.data.api.AppUpdateState
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.services.LiveTrackingController
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppIconManager
import fr.geotower.utils.AppLogoDrawingResources
import kotlinx.coroutines.launch

/**
 * Tiroir latéral du mode simplifié.
 *
 * Il remplace l'accueil, qui n'existe plus dans ce mode : la carte est la racine du backstack et
 * ce tiroir est le seul chemin vers les autres pages. Il n'enveloppe donc QUE la carte — le poser
 * autour de tout le NavHost donnerait un tiroir sur des écrans qui ont déjà leur bouton retour.
 *
 * [gesturesEnabled] est volontairement faux : un balayage depuis le bord entre en conflit avec le
 * déplacement de la carte.
 */
/**
 * Largeur du tiroir, en proportion de l'écran plutôt qu'en valeur fixe : sur un grand écran une
 * largeur fixe paraît étriquée, sur un petit elle ne laisse plus voir la carte derrière.
 *
 * Le plancher vaut 300dp — la largeur pour laquelle le titre « GeoTower » tient sur une ligne même
 * avec le curseur de taille d'interface au maximum — et le plafond reprend les 360dp de Material.
 * Sur le plus petit téléphone courant (360dp), le plancher laisse encore 60dp de carte visible,
 * au-dessus des 56dp minimum recommandés.
 */
private const val SIMPLE_MODE_DRAWER_WIDTH_RATIO = 0.75f
private val SIMPLE_MODE_DRAWER_MIN_WIDTH = 300.dp
private val SIMPLE_MODE_DRAWER_MAX_WIDTH = 360.dp

@Composable
private fun rememberSimpleModeDrawerWidth(): Dp {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    return (screenWidthDp * SIMPLE_MODE_DRAWER_WIDTH_RATIO).dp
        .coerceIn(SIMPLE_MODE_DRAWER_MIN_WIDTH, SIMPLE_MODE_DRAWER_MAX_WIDTH)
}

@Composable
fun SimpleModeDrawer(
    navController: NavController,
    drawerState: DrawerState,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = LocalGeoTowerUiStyle.current.isDark
    val isOledMode by AppConfig.isOledMode
    val sheetColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    // Le worker quotidien n'est pas planifié quand les notifications de mise à jour sont coupées :
    // l'ouverture du tiroir sert de déclencheur de secours (TTL géré par AppUpdateState).
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            AppUpdateState.refreshIfStale(context)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Fermé : aucun geste, un balayage depuis le bord entrerait en conflit avec le déplacement
        // de la carte. Ouvert : gestes actifs — c'est ce qui rend le voile cliquable, Material3
        // ne branche la fermeture au clic sur le voile que si gesturesEnabled est vrai.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = sheetColor,
                modifier = Modifier.width(rememberSimpleModeDrawerWidth())
            ) {
                SimpleModeDrawerContent(
                    navController = navController,
                    onCloseDrawer = { scope.launch { drawerState.close() } }
                )
            }
        },
        content = content
    )
}

@Composable
private fun SimpleModeDrawerContent(
    navController: NavController,
    onCloseDrawer: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()
    val safeClick = rememberSafeClick()

    val hasCompass by AppConfig.hasCompass
    val newerRelease by AppUpdateState.newerRelease

    // Quitter : on coupe le suivi en direct (donc la notification live) avant de fermer l'app.
    // L'Activity vient de LocalActivity et non d'un cast de LocalContext : le GeoTowerLocaleProvider
    // remplace LocalContext par un createConfigurationContext() qui n'a plus l'Activity dans sa
    // chaîne — le cast renverrait null dès qu'une langue est forcée.
    val activity = LocalActivity.current
    val onQuit: () -> Unit = {
        LiveTrackingController.stop(context)
        activity?.finishAndRemoveTask()
    }

    val entries = remember(hasCompass) {
        buildList {
            add(DrawerEntry("emitters", RemoteFeatureFlags.Screens.NEARBY, Icons.Default.MyLocation, R.string.nav_near_antennas))
            add(DrawerEntry("map", RemoteFeatureFlags.Screens.MAP, Icons.Default.Map, R.string.nav_map))
            if (hasCompass) {
                add(DrawerEntry("compass", RemoteFeatureFlags.Screens.COMPASS, Icons.Default.Explore, R.string.nav_compass))
            }
            add(DrawerEntry("stats", RemoteFeatureFlags.Screens.STATS, Icons.Default.BarChart, R.string.nav_statistics))
            add(DrawerEntry("settings", RemoteFeatureFlags.Screens.SETTINGS, Icons.Default.Settings, R.string.nav_settings))
            // « À propos » n'est pas dans cette liste : comme sur l'accueil, il vit dans le pied,
            // entre le logo et la version.
            add(DrawerEntry("help", RemoteFeatureFlags.Screens.HELP, Icons.AutoMirrored.Filled.Help, R.string.nav_help))
        }
    }

    // Trois zones franches : en-tête fixe en haut, pages centrées dans l'espace restant, pied
    // ancré en bas. Sans le centrage, la liste se collait sous le titre et laissait un grand vide
    // au-dessus du pied — c'est le trou qui rendait le tiroir bancal.
    Column(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // --- L'espace du haut dégage la barre d'état et fait descendre l'ensemble du tiroir. ---
        Spacer(Modifier.height(sizing.spacing(32.dp)))

        // --- Bandeau de mise à jour : une alerte, elle passe avant tout le reste. ---
        newerRelease?.let { release ->
            Surface(
                onClick = {
                    safeClick {
                        onCloseDrawer()
                        uriHandler.openUri(release.downloadUrl)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sizing.spacing(12.dp), vertical = sizing.spacing(8.dp)),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(sizing.spacing(16.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdateAlt,
                        contentDescription = null,
                        modifier = Modifier.size(sizing.component(24.dp))
                    )
                    Spacer(Modifier.width(sizing.spacing(12.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.simple_mode_update_banner_title),
                            style = sizing.textStyle(MaterialTheme.typography.titleSmall),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.simple_mode_update_banner_desc, release.versionName),
                            style = sizing.textStyle(MaterialTheme.typography.bodySmall)
                        )
                    }
                }
            }
            Spacer(Modifier.height(sizing.spacing(8.dp)))
        }

        // --- En-tête : le nom en grand, même style que l'accueil (displayLarge, gras, primaire).
        // 52sp et non les 64sp de l'accueil : le tiroir est plus étroit que l'écran, et `maxLines`
        // garantit que « GeoTower » ne passe jamais à la ligne, même à 120 % de taille d'interface.
        Text(
            text = "GeoTower",
            style = sizing.textStyle(MaterialTheme.typography.displayLarge),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            // 46sp : « GeoTower » doit tenir sur une ligne dans les 300dp du tiroir, même avec le
            // curseur de taille d'interface poussé au maximum.
            fontSize = sizing.text(46.sp),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(horizontal = sizing.spacing(8.dp))
        )
    }

    // --- Pages, centrées dans l'espace laissé entre l'en-tête et le pied ---
    // `heightIn(min = maxHeight)` : la colonne occupe au moins la hauteur visible, sinon
    // `Arrangement.Center` n'aurait aucun effet dans un contenu défilant (l'arrangement porte sur
    // la hauteur du contenu, pas sur celle de la fenêtre). Au-delà, elle défile normalement.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
        val availableHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = availableHeight)
                // Flou au défilement, comme tout conteneur défilant de l'app. Posé AVANT
                // verticalScroll : il délave la bande haute/basse du hublot, pas le contenu.
                .geoTowerFadingEdge(scrollState, requireScrollableContent = true)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.Center
        ) {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            entries.forEach { entry ->
                if (!RemoteFeatureFlags.isScreenEnabled(entry.screenId)) return@forEach
                // La carte est la page courante : l'entrée sert de repère, elle referme simplement.
                val isCurrent = entry.route == "map" || currentRoute?.startsWith(entry.route) == true
                NavigationDrawerItem(
                    icon = { Icon(entry.icon, contentDescription = null) },
                    label = { Text(stringResource(entry.labelRes)) },
                    selected = isCurrent,
                    onClick = {
                        safeClick {
                            onCloseDrawer()
                            if (!isCurrent) {
                                navController.navigate(entry.route) { launchSingleTop = true }
                            }
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }

            // --- Quitter : le tiroir remplace l'accueil, il doit reprendre son bouton de sortie.
            // Détaché de la liste, ce n'est pas une page.
            Spacer(Modifier.height(sizing.spacing(12.dp)))
            NavigationDrawerItem(
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.appstrings_home_quit_app),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                selected = false,
                onClick = { safeClick { onQuit() } },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
    }

    // --- Pied ancré en bas du tiroir : logo puis version ---
    val logoResId by AppIconManager.currentIconRes
    val logoDrawingChoice by AppConfig.appLogoDrawingChoice
    val isDark = LocalGeoTowerUiStyle.current.isDark
    val displayLogoResId = AppLogoDrawingResources.resolve(logoDrawingChoice, logoResId, isDark)
    val txtVersion = stringResource(R.string.appstrings_version)
    val txtUnknown = stringResource(R.string.appstrings_unknown)
    val appVersion = remember(txtVersion, txtUnknown) {
        runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "$txtVersion ${packageInfo.versionName}"
        }.getOrDefault("$txtVersion $txtUnknown")
    }

    LaunchedEffect(Unit) {
        if (logoResId == 0) AppIconManager.getLogoResId(context)
    }

    // Séparateur en retrait, aligné sur les pastilles des pages : pleine largeur, il coupait le
    // tiroir en deux alors que rien d'autre ne touche les bords.
    HorizontalDivider(
        modifier = Modifier
            .padding(horizontal = sizing.spacing(28.dp))
            .padding(bottom = sizing.spacing(20.dp))
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sizing.spacing(24.dp))
            .padding(bottom = sizing.spacing(16.dp))
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (displayLogoResId != 0) {
            Image(
                painter = painterResource(id = displayLogoResId),
                contentDescription = null,
                modifier = Modifier
                    .size(sizing.component(180.dp))
                    .clip(RoundedCornerShape(sizing.component(36.dp)))
            )
            Spacer(Modifier.height(sizing.spacing(10.dp)))
        }

        // « À propos » entre le logo et la version, comme sur l'accueil (cf. AboutSection).
        if (RemoteFeatureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.ABOUT)) {
            TextButton(
                onClick = {
                    safeClick {
                        onCloseDrawer()
                        navController.navigate("about") { launchSingleTop = true }
                    }
                }
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(sizing.spacing(8.dp)))
                Text(
                    text = stringResource(R.string.nav_about),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = sizing.text(18.sp)
                )
            }
        }

        // Le nom n'est plus répété ici : il est en grand en tête du tiroir.
        Text(
            text = appVersion,
            style = sizing.textStyle(MaterialTheme.typography.labelSmall),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
    }
}

private data class DrawerEntry(
    val route: String,
    val screenId: String,
    val icon: ImageVector,
    val labelRes: Int
)
