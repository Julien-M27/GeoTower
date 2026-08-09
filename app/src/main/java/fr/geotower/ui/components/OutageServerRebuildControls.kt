package fr.geotower.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.data.outages.ServerOutageRebuildMonitor
import fr.geotower.data.outages.ServerOutageRebuildRefusal
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import kotlinx.coroutines.delay
import kotlin.math.ceil

/** Battement du compte à rebours du quota : « réessayez dans 12 min » doit descendre tout seul. */
private const val COUNTDOWN_TICK_MS = 30_000L

/**
 * Demande de régénération du fichier national des pannes, adressée au SERVEUR.
 *
 * Le relevé serveur est refait périodiquement par le serveur lui-même ; ce bouton sert au cas où
 * l'utilisateur constate une panne plus récente que le dernier relevé. Le serveur n'accepte que
 * deux générations par heure, tous appareils confondus : le quota restant est annoncé AVANT
 * l'appui, et un refus affiche le délai plutôt qu'une erreur.
 *
 * L'avancement est lu sur [ServerOutageRebuildMonitor], pas sur la composition : la génération
 * survit à une sortie de la page, et le nouveau relevé est téléchargé dès qu'il est prêt.
 *
 * Pendant de [OutageLocalGenerationControls], qui pilote la génération quand les pannes sont
 * préparées sur l'appareil.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OutageServerRebuildControls(
    repository: AnfrRepository,
    enabled: Boolean,
    onOutagesRefreshed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val state by ServerOutageRebuildMonitor.state.collectAsState()

    // Quota relu à l'arrivée sur la page et à chaque retour : les créneaux se libèrent tout seuls.
    LifecycleResumeEffect(enabled) {
        if (enabled) ServerOutageRebuildMonitor.refreshQuota(repository)
        onPauseOrDispose { }
    }

    // La copie conservée sur l'appareil vient de changer : la carte doit relire son résumé.
    LaunchedEffect(state.phase) {
        if (state.phase == ServerOutageRebuildMonitor.Phase.DONE) onOutagesRefreshed()
    }

    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.retryAtMillis, tick) {
        if (state.retryAtMillis > System.currentTimeMillis()) {
            delay(COUNTDOWN_TICK_MS)
            tick++
        }
    }
    val retryMinutes = remainingMinutes(state.retryAtMillis, tick)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp)),
    ) {
        Text(
            text = stringResource(R.string.outage_rebuild_desc),
            style = sizing.textStyle(MaterialTheme.typography.bodySmall),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
            onClick = { ServerOutageRebuildMonitor.request(repository) },
            enabled = enabled && !state.busy && retryMinutes == 0,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = sizing.component(50.dp)),
            shape = RoundedCornerShape(sizing.component(12.dp)),
        ) {
            Icon(
                Icons.Default.CloudSync,
                contentDescription = null,
                modifier = Modifier.size(sizing.component(24.dp)),
            )
            Spacer(Modifier.width(sizing.spacing(8.dp)))
            Text(
                text = stringResource(R.string.outage_rebuild_button),
                fontWeight = FontWeight.Bold,
                style = sizing.textStyle(MaterialTheme.typography.labelLarge),
                textAlign = TextAlign.Center,
            )
        }

        if (state.busy) {
            LinearWavyProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(sizing.component(6.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        rebuildStatusLine(state, retryMinutes)?.let { (text, color) ->
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Minutes restantes avant le prochain créneau, 0 si un créneau est libre. */
private fun remainingMinutes(retryAtMillis: Long, @Suppress("UNUSED_PARAMETER") tick: Int): Int {
    if (retryAtMillis <= 0L) return 0
    val remaining = retryAtMillis - System.currentTimeMillis()
    if (remaining <= 0L) return 0
    return ceil(remaining / 60_000.0).toInt().coerceAtLeast(1)
}

/** Texte d'état sous le bouton, et sa couleur. Null quand il n'y a rien à dire. */
@Composable
private fun rebuildStatusLine(
    state: ServerOutageRebuildMonitor.UiState,
    retryMinutes: Int,
): Pair<String, Color>? = when (state.phase) {
    ServerOutageRebuildMonitor.Phase.REQUESTING ->
        stringResource(R.string.outage_rebuild_requesting) to MaterialTheme.colorScheme.primary

    ServerOutageRebuildMonitor.Phase.RUNNING ->
        stringResource(R.string.outage_rebuild_running) to MaterialTheme.colorScheme.primary

    ServerOutageRebuildMonitor.Phase.DOWNLOADING ->
        stringResource(R.string.outage_rebuild_downloading) to MaterialTheme.colorScheme.primary

    ServerOutageRebuildMonitor.Phase.DONE ->
        stringResource(R.string.outage_rebuild_done) to MaterialTheme.colorScheme.primary

    ServerOutageRebuildMonitor.Phase.REFUSED ->
        refusalMessage(state.refusal, retryMinutes) to MaterialTheme.colorScheme.onSurfaceVariant

    ServerOutageRebuildMonitor.Phase.FAILED -> {
        val detail = state.serverError?.takeIf { it.isNotBlank() }
        val message = if (detail != null) {
            stringResource(R.string.outage_rebuild_failed, detail)
        } else {
            stringResource(R.string.outage_rebuild_failed_generic)
        }
        message to MaterialTheme.colorScheme.error
    }

    // Au repos, la seule chose utile à dire est ce qu'il reste de créneaux dans l'heure — ou le
    // délai, quand le serveur a déjà annoncé qu'il refuserait (créneaux pris par d'autres).
    ServerOutageRebuildMonitor.Phase.IDLE -> when {
        retryMinutes > 0 ->
            refusalMessage(state.refusal, retryMinutes) to MaterialTheme.colorScheme.onSurfaceVariant

        state.quotaPerHour > 0 && state.remaining >= 0 -> stringResource(
            R.string.outage_rebuild_quota,
            state.quotaPerHour,
            state.remaining,
        ) to MaterialTheme.colorScheme.onSurfaceVariant

        else -> null
    }
}

/** « Réessayez dans X min », en disant si c'est le quota du serveur ou celui de cet appareil. */
@Composable
private fun refusalMessage(refusal: ServerOutageRebuildRefusal, retryMinutes: Int): String =
    if (refusal == ServerOutageRebuildRefusal.CLIENT_QUOTA) {
        stringResource(R.string.outage_rebuild_refused_client, retryMinutes)
    } else {
        stringResource(R.string.outage_rebuild_refused_global, retryMinutes)
    }
