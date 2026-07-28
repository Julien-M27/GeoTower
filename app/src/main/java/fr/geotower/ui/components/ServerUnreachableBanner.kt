package fr.geotower.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import fr.geotower.R
import fr.geotower.data.api.ServerReachability
import fr.geotower.data.api.ServerStatus
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import kotlinx.coroutines.delay

/** Rythme des nouvelles tentatives tant que le serveur ne répond pas. */
private const val SERVER_RETRY_INTERVAL_MS = 60_000L

/**
 * Suit l'état du serveur GeoTower pendant que l'écran est affiché.
 *
 * - sonde à l'arrivée sur l'écran et à chaque `ON_RESUME` (l'anti-rafale de [ServerReachability]
 *   évite les appels inutiles) ;
 * - réessaie toutes les minutes tant que le serveur ne répond pas, pour que le bandeau disparaisse
 *   tout seul quand il revient ;
 * - ne sonde pas si l'appareil n'a pas de réseau : c'est le bandeau « hors ligne » qui s'applique.
 */
@Composable
fun rememberServerReachabilityState(isOnline: Boolean): State<ServerStatus> {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner, isOnline) {
        if (!isOnline) {
            ServerReachability.reset()
            return@LaunchedEffect
        }
        // `repeatOnLifecycle` relance le bloc à chaque retour au premier plan et l'annule quand
        // l'écran repasse en arrière-plan : pas de sonde qui tourne dans le vide app fermée.
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var verdict = ServerReachability.refresh()
            while (verdict == ServerStatus.UNREACHABLE) {
                delay(SERVER_RETRY_INTERVAL_MS)
                verdict = ServerReachability.refresh(force = true)
            }
        }
    }

    return ServerReachability.status
}

/**
 * Bandeau « serveur injoignable » : l'appareil a du réseau mais l'API GeoTower ne répond pas.
 * Le bouton information détaille ce qui devient indisponible et ce qui continue de marcher.
 */
@Composable
fun ServerUnreachableBanner(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    var showDetails by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = sizing.spacing(16.dp), start = sizing.spacing(16.dp), end = sizing.spacing(16.dp))
    ) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(8.dp))
        ) {
            val contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            Row(
                modifier = Modifier.padding(sizing.spacing(16.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(sizing.component(24.dp))
                )
                Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.appstrings_server_unreachable_banner_title),
                        color = contentColor,
                        fontWeight = FontWeight.Bold,
                        style = sizing.textStyle(MaterialTheme.typography.titleSmall)
                    )
                    Text(
                        text = stringResource(R.string.appstrings_server_unreachable_banner_desc),
                        color = contentColor,
                        style = sizing.textStyle(MaterialTheme.typography.bodySmall)
                    )
                }
                Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                IconButton(onClick = { showDetails = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.appstrings_server_unreachable_info_button),
                        tint = contentColor,
                        modifier = Modifier.size(sizing.component(24.dp))
                    )
                }
            }
        }
    }

    if (showDetails) {
        ServerUnreachableDetailsDialog(onDismiss = { showDetails = false })
    }
}

@Composable
private fun ServerUnreachableDetailsDialog(onDismiss: () -> Unit) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(sizing.component(24.dp))
            )
        },
        title = {
            Text(
                text = stringResource(R.string.appstrings_server_unreachable_dialog_title),
                style = sizing.textStyle(MaterialTheme.typography.headlineSmall)
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
            ) {
                Text(
                    text = stringResource(R.string.appstrings_server_unreachable_dialog_intro),
                    style = sizing.textStyle(MaterialTheme.typography.bodyMedium)
                )
                ServerUnreachableSection(
                    title = stringResource(R.string.appstrings_server_unreachable_dialog_unavailable_title),
                    content = stringResource(R.string.appstrings_server_unreachable_dialog_unavailable_list),
                    titleColor = MaterialTheme.colorScheme.error
                )
                ServerUnreachableSection(
                    title = stringResource(R.string.appstrings_server_unreachable_dialog_available_title),
                    content = stringResource(R.string.appstrings_server_unreachable_dialog_available_list),
                    titleColor = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.appstrings_server_unreachable_dialog_retry_hint),
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.appstrings_close),
                    style = sizing.textStyle(MaterialTheme.typography.labelLarge)
                )
            }
        }
    )
}

@Composable
private fun ServerUnreachableSection(
    title: String,
    content: String,
    titleColor: Color
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Column(verticalArrangement = Arrangement.spacedBy(sizing.spacing(4.dp))) {
        Text(
            text = title,
            style = sizing.textStyle(MaterialTheme.typography.titleSmall),
            fontWeight = FontWeight.Bold,
            color = titleColor
        )
        Text(
            text = content,
            style = sizing.textStyle(MaterialTheme.typography.bodySmall),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
