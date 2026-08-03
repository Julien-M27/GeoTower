package fr.geotower.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.geotower.R
import fr.geotower.data.api.ApiEndpoints
import fr.geotower.data.api.ApiServer
import fr.geotower.data.api.ApiServerMode
import fr.geotower.data.api.ServerReachability
import fr.geotower.ui.theme.LocalGeoTowerUiSizing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Choix du serveur GeoTower (principal / miroir), partagé par la carte « Serveur GeoTower » de la
 * page Diagnostic et par celle de Réglages > Système.
 *
 * Le réglage n'a qu'un seul état — [ApiEndpoints.mode], persisté dans les préférences — et un seul
 * chemin d'application : les deux pages montrent donc toujours la même chose, quelle que soit celle
 * par laquelle l'utilisateur est passé.
 */

@StringRes
fun apiServerModeLabelRes(mode: ApiServerMode): Int = when (mode) {
    ApiServerMode.AUTO -> R.string.appstrings_diagnostic_api_mode_auto
    ApiServerMode.FORCE_PRIMARY -> R.string.appstrings_diagnostic_api_mode_primary
    ApiServerMode.FORCE_MIRROR -> R.string.appstrings_diagnostic_api_mode_mirror
}

/** Hôte(s) visé(s) par un mode, affiché sous son libellé. */
fun apiServerModeHosts(mode: ApiServerMode): String = when (mode) {
    ApiServerMode.AUTO -> "${ApiServer.PRIMARY.host} → ${ApiServer.MIRROR.host}"
    ApiServerMode.FORCE_PRIMARY -> ApiServer.PRIMARY.host
    ApiServerMode.FORCE_MIRROR -> ApiServer.MIRROR.host
}

/**
 * Enregistre le choix et éprouve tout de suite le serveur retenu : sans cette sonde forcée,
 * l'anti-rafale de [ServerReachability] laisserait afficher le verdict de l'ancien serveur pendant
 * une demi-minute. [onProbed] permet à l'appelant de rafraîchir son propre affichage ensuite.
 */
fun applyApiServerMode(
    context: Context,
    scope: CoroutineScope,
    mode: ApiServerMode,
    onProbed: () -> Unit = {}
) {
    ApiEndpoints.setMode(context, mode)
    scope.launch {
        ServerReachability.refresh(force = true)
        onProbed()
    }
}

/** Dialogue de choix : bascule automatique, ou serveur imposé (principal / miroir). */
@Composable
fun ApiServerModeDialog(
    currentMode: ApiServerMode,
    onDismiss: () -> Unit,
    onSelect: (ApiServerMode) -> Unit
) {
    val sizing = LocalGeoTowerUiSizing.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.appstrings_diagnostic_api_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(sizing.spacing(4.dp))) {
                Text(
                    text = stringResource(R.string.appstrings_diagnostic_api_dialog_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
                ApiServerMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = sizing.spacing(6.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mode == currentMode, onClick = { onSelect(mode) })
                        Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                        Column {
                            Text(
                                text = stringResource(apiServerModeLabelRes(mode)),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = apiServerModeHosts(mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.appstrings_close)) }
        }
    )
}
