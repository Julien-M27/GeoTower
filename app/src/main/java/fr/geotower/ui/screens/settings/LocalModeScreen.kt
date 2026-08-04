package fr.geotower.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.data.build.LocalBuildCapability
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.db.LocalDbProvenance
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.LocalDbBuildCard
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * Chaque cran affiche SES réglages juste sous la liste, au lieu de les disperser dans d'autres
 * pages : pannes en local → [OutageLocalControls] (fréquence, arrière-plan, mise à jour immédiate),
 * base en local → [LocalDbBuildCard] (génération de la base). Le niveau est ainsi la seule vérité,
 * et l'ancienne page « Source des pannes » n'existe plus.
 */
@Composable
fun LocalModeScreen(
    navController: NavController,
    repository: AnfrRepository,
) {
    val context = LocalContext.current
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val useOneUi = uiStyle.useOneUi
    val shape = uiStyle.cardShape
    val border = uiStyle.cardBorder
    val bubbleColor = uiStyle.bubbleColor
    val safeBack = rememberSafeBackNavigation(navController, fallbackRoute = "settings")

    BackHandler(enabled = !safeBack.isLocked) { safeBack.navigateBack() }

    // Niveau choisi par l'utilisateur (observable). Le kill-switch distant peut forcer le niveau effectif à 0.
    val level = AppConfig.localModeLevel.intValue
    val remoteEnabled = RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.LOCAL_MODE_ENABLED)
    val eligibility = remember { LocalBuildCapability.evaluate(context) }
    // Un appareil qui ne peut pas générer la base (RAM/stockage) ne doit pas pouvoir choisir un
    // cran dont c'est le seul apport : « Base en local » ne ferait rien du tout, et « Base + sites »
    // se comporterait exactement comme « Sites en panne en local » sous un nom qui promet plus.
    // « Autonomie maximale » reste ouvert : ses coupures, elles, s'appliquent de toute façon.
    val canPickDbLevels = eligibility.eligible
    val scrollState = rememberScrollState()

    // Provenance réelle de la base installée. La génération est aussi accessible depuis Réglages ▸
    // Base de données et la première ouverture : un utilisateur peut donc tourner sur une base
    // générée ici alors que son cran dit « Serveur ». On le lui signale plutôt que de le laisser
    // devant une page qui affirme le contraire de ce qu'il vit.
    var dbLocallyBuilt by remember { mutableStateOf(false) }
    LaunchedEffect(level) {
        dbLocallyBuilt = withContext(Dispatchers.IO) {
            LocalDbProvenance.readMobile(context).locallyBuilt
        }
    }

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
            Text(
                text = stringResource(R.string.local_mode_intro),
                style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!remoteEnabled) {
                Text(
                    text = stringResource(R.string.local_mode_remote_disabled),
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Écart entre l'état réel et le cran choisi : la base tourne en local sans que le cran
            // le dise. Simple constat, aucune bascule automatique — le cran reste un choix.
            if (dbLocallyBuilt && !AppConfig.levelBuildsDbLocally(level)) {
                Text(
                    text = stringResource(R.string.local_mode_db_actually_local),
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            NavigationModeOption(
                title = stringResource(R.string.local_mode_level_off_title),
                desc = stringResource(R.string.local_mode_level_off_desc),
                isSelected = level == AppConfig.LOCAL_MODE_SERVER,
                useOneUi = useOneUi,
                onClick = { AppConfig.setLocalModeLevel(context, AppConfig.LOCAL_MODE_SERVER) },
            )
            NavigationModeOption(
                title = stringResource(R.string.local_mode_level_db_title),
                desc = stringResource(R.string.local_mode_level_db_desc),
                isSelected = level == AppConfig.LOCAL_MODE_DB,
                useOneUi = useOneUi,
                enabled = canPickDbLevels,
                onClick = { AppConfig.setLocalModeLevel(context, AppConfig.LOCAL_MODE_DB) },
            )
            NavigationModeOption(
                title = stringResource(R.string.local_mode_level_outages_title),
                desc = stringResource(R.string.local_mode_level_outages_desc),
                isSelected = level == AppConfig.LOCAL_MODE_OUTAGES,
                useOneUi = useOneUi,
                onClick = { AppConfig.setLocalModeLevel(context, AppConfig.LOCAL_MODE_OUTAGES) },
            )
            NavigationModeOption(
                title = stringResource(R.string.local_mode_level_data_title),
                desc = stringResource(R.string.local_mode_level_data_desc),
                isSelected = level == AppConfig.LOCAL_MODE_BOTH,
                useOneUi = useOneUi,
                enabled = canPickDbLevels,
                onClick = { AppConfig.setLocalModeLevel(context, AppConfig.LOCAL_MODE_BOTH) },
            )
            NavigationModeOption(
                title = stringResource(R.string.local_mode_level_full_title),
                desc = stringResource(R.string.local_mode_level_full_desc),
                isSelected = level == AppConfig.LOCAL_MODE_MAX,
                useOneUi = useOneUi,
                onClick = { AppConfig.setLocalModeLevel(context, AppConfig.LOCAL_MODE_MAX) },
            )

            // Deux options grisées sans un mot d'explication seraient pires que pas d'options.
            if (!canPickDbLevels) {
                Text(
                    text = stringResource(R.string.local_mode_db_ineligible),
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = stringResource(R.string.local_mode_maps_note),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Crans « base en local » : la base est générée sur l'appareil au lieu d'être
            // téléchargée. Affiché avant les pannes, comme dans la liste ci-dessus. Le cas
            // inéligible est déjà expliqué sous la liste (et ne reste atteignable qu'au cran
            // maximal, ou par un cran choisi avant que le stockage ne tombe sous le seuil).
            if (AppConfig.levelBuildsDbLocally(level) && eligibility.eligible) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Text(
                    text = stringResource(R.string.local_mode_db_eligible),
                    style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.local_mode_build_hint),
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LocalDbBuildCard(
                    useOneUi = useOneUi,
                    shape = shape,
                    border = border,
                    bubbleColor = bubbleColor,
                )
            }

            // Crans « pannes en local » : les sites en panne sont récupérés ici, donc leurs
            // réglages (fréquence, arrière-plan, mise à jour immédiate) s'affichent ici.
            if (AppConfig.levelFetchesOutagesLocally(level)) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                OutageLocalControls()
            }
        }
    }
}
