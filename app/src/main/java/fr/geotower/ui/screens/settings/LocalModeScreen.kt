package fr.geotower.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.theme.LocalGeoTowerUiStyle

/**
 * Page « Traitement local des données » (ouverte depuis Réglages ▸ au-dessus de la source des pannes).
 *
 * Propose 5 crans (0 Serveur / 1 Base en local / 2 Sites en panne en local / 3 Base + sites en
 * local / 4 Autonomie maximale) qui décident de ce qui est traité sur l'appareil plutôt que fourni
 * par le backend GeoTower. Les deux traitements sont **indépendants** : l'échelle les combine au
 * lieu de les empiler, parce que générer sa base n'oblige à rien côté pannes — et parce que cet
 * état-là (base ici, pannes serveur) n'avait aucune case et retombait sur « Serveur », qui
 * affirmait exactement le contraire. Le rafraîchissement des feature-flags reste actif à tous les
 * crans (contrôle distant), et un kill-switch distant
 * ([RemoteFeatureFlags.Features.LOCAL_MODE_ENABLED]) peut forcer le niveau effectif à 0. Les
 * services tiers (carto, altimétrie, recherche) ne sont jamais touchés.
 *
 * La liste des crans elle-même vit dans [LocalModeLevelControls] : le premier lancement propose le
 * même choix, à la page qui précède « Base de données », et deux copies auraient fini par diverger.
 *
 * **Cette page ne porte que le choix.** Les commandes qu'un cran déclenche sont retournées dans la
 * section qu'elles concernent (Réglages ▸ Base de données) : génération de la base sur la carte
 * dédiée, réglages de récupération des pannes sous la carte des sites en panne. Les héberger ici
 * obligeait à chercher un même réglage à deux endroits selon le cran actif.
 */
@Composable
fun LocalModeScreen(
    navController: NavController,
    repository: AnfrRepository,
) {
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val useOneUi = uiStyle.useOneUi
    val safeBack = rememberSafeBackNavigation(navController, fallbackRoute = "settings")

    BackHandler(enabled = !safeBack.isLocked) { safeBack.navigateBack() }

    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = uiStyle.backgroundColor,
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.local_mode_settings_title),
                onBack = { safeBack.navigateBack() },
                backEnabled = !safeBack.isLocked,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                // Flou au défilement : posé AVANT verticalScroll pour délaver la bande haute/basse
                // du hublot, pas le contenu défilé (voir geoTowerFadingEdge).
                .geoTowerFadingEdge(scrollState, fadeHeight = sizing.component(72.dp))
                .verticalScroll(scrollState)
                .padding(sizing.spacing(16.dp)),
            verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp)),
        ) {
            // Cette page ne porte QUE le choix du cran. Les réglages qu'un cran déclenche vivent
            // dans la section qui les concerne — génération de la base et fréquence de
            // récupération des pannes sont dans Réglages ▸ Base de données, à côté des cartes
            // qu'elles pilotent. Une page « niveau » qui hébergeait aussi les commandes obligeait
            // à chercher le même réglage à deux endroits selon le cran actif.
            LocalModeLevelControls(useOneUi = useOneUi)
        }
    }
}
