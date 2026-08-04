package fr.geotower.ui.screens.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geotower.R
import fr.geotower.data.build.LocalBuildCapability
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.db.LocalDbProvenance
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Panneau des 5 crans du « traitement local des données », partagé par [LocalModeScreen]
 * (Réglages) et l'étape correspondante du premier lancement.
 *
 * Il porte le choix ET tout ce qui l'explique (kill-switch distant, appareil inéligible, écart
 * entre le cran et la base réellement installée, services tiers non concernés) : deux copies de
 * cette liste finiraient par diverger, et c'est justement l'écran où une phrase qui ment sur ce
 * qui tourne où se paie cher.
 *
 * Ce qui n'est PAS ici : les réglages propres à un cran (génération de la base, fréquence des
 * pannes). Ils dépendent de l'écran hôte — les Réglages les affichent juste en dessous, le
 * premier lancement enchaîne sur sa page « Base de données », qui les porte déjà.
 */
@Composable
fun LocalModeLevelControls(
    useOneUi: Boolean,
    modifier: Modifier = Modifier,
    showIntro: Boolean = true,
) {
    val context = LocalContext.current
    val sizing = LocalGeoTowerUiStyle.current.sizing

    // Niveau choisi par l'utilisateur (observable). Le kill-switch distant peut forcer le niveau effectif à 0.
    val level = AppConfig.localModeLevel.intValue
    val remoteEnabled = RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.LOCAL_MODE_ENABLED)
    val eligibility = remember { LocalBuildCapability.evaluate(context) }
    // Un appareil qui ne peut pas générer la base (RAM/stockage) ne doit pas pouvoir choisir un
    // cran dont c'est le seul apport : « Base en local » ne ferait rien du tout, et « Base + sites »
    // se comporterait exactement comme « Sites en panne en local » sous un nom qui promet plus.
    // « Autonomie maximale » reste ouvert : ses coupures, elles, s'appliquent de toute façon.
    val canPickDbLevels = eligibility.eligible

    // Provenance réelle de la base installée. La génération est aussi accessible depuis Réglages ▸
    // Base de données et la première ouverture : un utilisateur peut donc tourner sur une base
    // générée ailleurs alors que son cran dit « Serveur ». On le lui signale plutôt que de le
    // laisser devant une page qui affirme le contraire de ce qu'il vit.
    var dbLocallyBuilt by remember { mutableStateOf(false) }
    LaunchedEffect(level) {
        dbLocallyBuilt = withContext(Dispatchers.IO) {
            LocalDbProvenance.readMobile(context).locallyBuilt
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp)),
    ) {
        if (showIntro) {
            Text(
                text = stringResource(R.string.local_mode_intro),
                style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

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

        // Appareil inéligible : annoncé AVANT la liste, et sous forme de carte. En petit texte gris
        // sous cinq options dont deux grisées, l'information arrivait après la question qu'elle
        // répond. Les chiffres mesurés valent mieux que le seuil seul : « 4 Go / 6 Go requis » se
        // vérifie, « appareil incompatible » se subit.
        if (!canPickDbLevels) {
            LocalBuildIneligibleCard(eligibility = eligibility, useOneUi = useOneUi)
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

        // Cran maximal sur un appareil qui ne peut pas générer sa base : l'autonomie est réelle
        // partout ailleurs, mais la base continue de venir du serveur — sans quoi l'application
        // n'aurait aucune donnée (cf. [AppConfig.blockServerDatabase]). Le dire ici est le prix à
        // payer pour laisser ce cran ouvert à tous.
        if (level == AppConfig.LOCAL_MODE_MAX && !canPickDbLevels) {
            Text(
                text = stringResource(R.string.local_mode_max_partial_on_this_device),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Text(
            text = stringResource(R.string.local_mode_maps_note),
            style = sizing.textStyle(MaterialTheme.typography.bodySmall),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Carte « cet appareil ne peut pas générer la base », avec les chiffres mesurés en face des seuils.
 *
 * [LocalBuildCapability.Eligibility.reason] n'est pas affichable : c'est du texte français en dur,
 * destiné aux journaux. Les tailles passent par `Formatter.formatShortFileSize`, qui les rend dans
 * la langue et les unités du système.
 */
@Composable
private fun LocalBuildIneligibleCard(
    eligibility: LocalBuildCapability.Eligibility,
    useOneUi: Boolean,
) {
    val context = LocalContext.current
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val shape = if (useOneUi) RoundedCornerShape(sizing.component(22.dp)) else RoundedCornerShape(sizing.component(12.dp))

    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(sizing.spacing(16.dp)),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(sizing.component(24.dp)),
            )
            Spacer(Modifier.width(sizing.spacing(12.dp)))
            Column {
                Text(
                    text = stringResource(R.string.local_mode_db_ineligible_title),
                    style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(sizing.spacing(4.dp)))
                Text(
                    text = stringResource(
                        R.string.local_mode_db_ineligible_measured,
                        size(LocalBuildCapability.MIN_TOTAL_RAM_GIB * 1024L * 1024L * 1024L),
                        size(LocalBuildCapability.MIN_FREE_STORAGE_BYTES),
                        size(eligibility.totalRamBytes),
                        size(eligibility.freeStorageBytes),
                    ),
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(sizing.spacing(8.dp)))
                Text(
                    text = stringResource(R.string.local_mode_db_ineligible),
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
