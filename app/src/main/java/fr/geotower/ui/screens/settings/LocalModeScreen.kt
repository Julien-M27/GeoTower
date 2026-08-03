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
import androidx.compose.runtime.remember
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
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.LocalDbBuildCard
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig

/**
 * Page « Traitement local des données » (ouverte depuis Réglages ▸ au-dessus de la source des pannes).
 *
 * Propose 4 niveaux cumulatifs (0 Serveur / 1 Sites en panne en local / 2 Base + sites en local /
 * 3 Autonomie maximale) qui décident de ce qui est traité sur l'appareil plutôt que fourni par le
 * backend GeoTower. Le rafraîchissement des feature-flags reste actif à tous les niveaux (contrôle
 * distant), et un kill-switch distant ([RemoteFeatureFlags.Features.LOCAL_MODE_ENABLED]) peut forcer
 * le niveau effectif à 0. Les services tiers (carto, altimétrie, recherche) ne sont jamais touchés.
 *
 * Chaque palier affiche SES réglages juste sous la liste, au lieu de les disperser dans d'autres
 * pages : niveau ≥ 1 → [OutageLocalControls] (fréquence, arrière-plan, mise à jour immédiate des
 * pannes), niveau ≥ 2 → [LocalDbBuildCard] (génération de la base). Le niveau est ainsi la seule
 * vérité, et l'ancienne page « Source des pannes » n'existe plus.
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

            NavigationModeOption(
                title = stringResource(R.string.local_mode_level_off_title),
                desc = stringResource(R.string.local_mode_level_off_desc),
                isSelected = level == 0,
                useOneUi = useOneUi,
                onClick = { AppConfig.setLocalModeLevel(context, 0) },
            )
            NavigationModeOption(
                title = stringResource(R.string.local_mode_level_outages_title),
                desc = stringResource(R.string.local_mode_level_outages_desc),
                isSelected = level == 1,
                useOneUi = useOneUi,
                onClick = { AppConfig.setLocalModeLevel(context, 1) },
            )
            NavigationModeOption(
                title = stringResource(R.string.local_mode_level_data_title),
                desc = stringResource(R.string.local_mode_level_data_desc),
                isSelected = level == 2,
                useOneUi = useOneUi,
                onClick = { AppConfig.setLocalModeLevel(context, 2) },
            )
            NavigationModeOption(
                title = stringResource(R.string.local_mode_level_full_title),
                desc = stringResource(R.string.local_mode_level_full_desc),
                isSelected = level == 3,
                useOneUi = useOneUi,
                onClick = { AppConfig.setLocalModeLevel(context, 3) },
            )

            Text(
                text = stringResource(R.string.local_mode_maps_note),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Niveau ≥ 1 : les pannes sont récupérées ici, donc leurs réglages s'affichent ici.
            if (level >= 1) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                OutageLocalControls()
            }

            // Niveau ≥ 2 : la base doit être générée sur l'appareil (si éligible) au lieu d'être téléchargée.
            if (level >= 2) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Text(
                    text = stringResource(
                        if (eligibility.eligible) R.string.local_mode_db_eligible else R.string.local_mode_db_ineligible,
                    ),
                    style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                    fontWeight = FontWeight.Bold,
                    color = if (eligibility.eligible) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (eligibility.eligible) {
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
            }
        }
    }
}
