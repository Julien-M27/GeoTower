package fr.geotower.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geotower.R
import fr.geotower.data.api.ApiEndpoints
import fr.geotower.data.api.ApiServer
import fr.geotower.data.api.ApiServerMode
import fr.geotower.data.api.ServerPingResult
import fr.geotower.data.api.ServerPingSample
import fr.geotower.data.api.ServerReachability
import fr.geotower.ui.theme.LocalGeoTowerUiSizing
import fr.geotower.utils.PowerProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Choix du serveur GeoTower (principal / miroir), partagé par la section « Données et mises à jour »
 * des Réglages (Base de données) et par la page Diagnostic.
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
    onSelect: (ApiServerMode) -> Unit,
    onOpenPing: (() -> Unit)? = null
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
        },
        dismissButton = onOpenPing?.let { openPing ->
            {
                TextButton(onClick = openPing) {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = null,
                        modifier = Modifier.size(sizing.component(18.dp))
                    )
                    Spacer(modifier = Modifier.width(sizing.spacing(4.dp)))
                    Text(stringResource(R.string.appstrings_diagnostic_action_ping_api_server))
                }
            }
        }
    )
}

/** État du test de latence vers un serveur GeoTower. */
sealed interface ApiServerPingState {
    object Idle : ApiServerPingState
    data class Running(
        val server: ApiServer,
        val passes: Int,
        val completedPasses: Int,
        val samples: List<ServerPingSample>
    ) : ApiServerPingState
    data class Completed(val result: ServerPingResult) : ApiServerPingState
    data class Stopped(
        val server: ApiServer,
        val passes: Int,
        val completedPasses: Int,
        val samples: List<ServerPingSample>
    ) : ApiServerPingState
    object Failed : ApiServerPingState
}

typealias DiagnosticPingState = ApiServerPingState

/**
 * Dialogue de test de latence applicatif (ping HTTPS) vers l'un des serveurs GeoTower.
 * Cette surcharge gère son propre cycle de vie et sa coroutine de test.
 */
@Composable
fun ApiServerPingDialog(
    initialServer: ApiServer = ApiEndpoints.active(),
    onDismiss: () -> Unit,
    onApplyServer: ((ApiServer) -> Unit)? = null
) {
    var selectedServer by remember(initialServer) { mutableStateOf(initialServer) }
    var selectedPasses by remember { mutableIntStateOf(ServerReachability.DEFAULT_PING_PASSES) }
    var state by remember { mutableStateOf<ApiServerPingState>(ApiServerPingState.Idle) }
    var pingJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    fun stopPing() {
        val current = state
        pingJob?.cancel()
        pingJob = null
        if (current is ApiServerPingState.Running) {
            state = ApiServerPingState.Stopped(
                server = current.server,
                passes = current.passes,
                completedPasses = current.completedPasses,
                samples = current.samples
            )
        }
    }

    fun runPing() {
        if (state is ApiServerPingState.Running) return
        val server = selectedServer
        val passes = selectedPasses
        val job = scope.launch {
            state = ApiServerPingState.Running(
                server = server,
                passes = passes,
                completedPasses = 0,
                samples = emptyList()
            )
            try {
                val result = ServerReachability.runPing(server, passes) { sample ->
                    val currentRunning = state as? ApiServerPingState.Running
                    if (currentRunning != null) {
                        state = currentRunning.copy(
                            completedPasses = sample.attempt,
                            samples = currentRunning.samples + sample
                        )
                    }
                }
                state = ApiServerPingState.Completed(result)
            } catch (e: CancellationException) {
                val currentRunning = state as? ApiServerPingState.Running
                if (currentRunning != null) {
                    state = ApiServerPingState.Stopped(
                        server = currentRunning.server,
                        passes = currentRunning.passes,
                        completedPasses = currentRunning.completedPasses,
                        samples = currentRunning.samples
                    )
                }
                throw e
            } catch (e: Exception) {
                state = ApiServerPingState.Failed
            } finally {
                if (pingJob == coroutineContext[Job]) {
                    pingJob = null
                }
            }
        }
        pingJob = job
    }

    ApiServerPingDialog(
        selectedServer = selectedServer,
        selectedPasses = selectedPasses,
        state = state,
        onServerSelected = { server ->
            if (selectedServer != server) {
                stopPing()
                selectedServer = server
                state = ApiServerPingState.Idle
            }
        },
        onPassesSelected = { passes ->
            if (selectedPasses != passes) {
                stopPing()
                selectedPasses = passes
                state = ApiServerPingState.Idle
            }
        },
        onRun = { runPing() },
        onStop = { stopPing() },
        onDismiss = {
            stopPing()
            onDismiss()
        },
        onApplyServer = onApplyServer
    )
}

/**
 * Dialogue de test de latence applicatif (ping HTTPS) avec état piloté par l'appelant.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ApiServerPingDialog(
    selectedServer: ApiServer,
    selectedPasses: Int,
    state: ApiServerPingState,
    onServerSelected: (ApiServer) -> Unit,
    onPassesSelected: (Int) -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    onApplyServer: ((ApiServer) -> Unit)? = null
) {
    val sizing = LocalGeoTowerUiSizing.current
    val isRunning = state is ApiServerPingState.Running

    AlertDialog(
        onDismissRequest = {
            if (isRunning) onStop()
            onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            Text(
                text = stringResource(R.string.appstrings_diagnostic_ping_dialog_title),
                color = MaterialTheme.colorScheme.onSurface,
                style = sizing.textStyle(MaterialTheme.typography.headlineSmall),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(sizing.spacing(6.dp))
            ) {
                Text(
                    text = stringResource(R.string.appstrings_diagnostic_ping_dialog_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(sizing.spacing(4.dp)))
                Text(
                    text = stringResource(R.string.appstrings_diagnostic_ping_server_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                ApiServer.entries.forEach { server ->
                    val isSelected = server == selectedServer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isRunning) { onServerSelected(server) }
                            .padding(vertical = sizing.spacing(4.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = if (isRunning) null else { { onServerSelected(server) } }
                        )
                        Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                        Column {
                            Text(
                                text = "${if (isSelected) "> " else "  "}${server.host}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Text(
                                text = stringResource(
                                    when (server) {
                                        ApiServer.PRIMARY -> R.string.appstrings_diagnostic_api_mode_primary
                                        ApiServer.MIRROR -> R.string.appstrings_diagnostic_api_mode_mirror
                                    }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.appstrings_diagnostic_ping_passes_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                listOf(
                    ServerReachability.DEFAULT_PING_PASSES,
                    ServerReachability.EXTENDED_PING_PASSES
                ).forEach { passes ->
                    val isSelected = passes == selectedPasses
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isRunning) { onPassesSelected(passes) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = if (isRunning) null else { { onPassesSelected(passes) } }
                        )
                        Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                        Text(
                            text = "${if (isSelected) ">" else " "} " +
                                stringResource(R.string.appstrings_diagnostic_ping_passes_option, passes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                ApiPingTerminal(
                    state = state,
                    selectedServer = selectedServer,
                    selectedPasses = selectedPasses
                )
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onApplyServer != null) {
                    TextButton(
                        onClick = {
                            if (isRunning) onStop()
                            onApplyServer(selectedServer)
                            onDismiss()
                        },
                        enabled = !isRunning
                    ) {
                        Text(stringResource(R.string.appstrings_diagnostic_action_choose_api_server))
                    }
                }
                if (isRunning) {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(sizing.component(18.dp))
                        )
                        Spacer(modifier = Modifier.width(sizing.spacing(6.dp)))
                        Text(stringResource(R.string.appstrings_diagnostic_ping_stop))
                    }
                } else {
                    Button(onClick = onRun) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(sizing.component(18.dp))
                        )
                        Spacer(modifier = Modifier.width(sizing.spacing(6.dp)))
                        Text(stringResource(R.string.appstrings_diagnostic_ping_run))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (isRunning) onStop()
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.appstrings_close))
            }
        }
    )
}

@Composable
fun ApiPingTerminal(
    state: ApiServerPingState,
    selectedServer: ApiServer,
    selectedPasses: Int
) {
    val terminalTextStyle = MaterialTheme.typography.bodySmall
    val terminalPanelShape = RoundedCornerShape(14.dp)
    val terminalBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val terminalText = MaterialTheme.colorScheme.onSurface
    val terminalMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val terminalAccent = MaterialTheme.colorScheme.primary
    val terminalError = MaterialTheme.colorScheme.error
    val progressTarget = when (state) {
        ApiServerPingState.Idle -> 0f
        is ApiServerPingState.Running -> state.completedPasses.toFloat() / state.passes
        is ApiServerPingState.Completed -> 1f
        is ApiServerPingState.Stopped -> state.completedPasses.toFloat() / state.passes
        ApiServerPingState.Failed -> 0f
    }.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(
            durationMillis = if (PowerProfile.richAnimations) 420 else 0,
            easing = FastOutSlowInEasing
        ),
        label = "api-ping-terminal-progress"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = terminalBackground,
        contentColor = terminalText,
        shape = terminalPanelShape,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, terminalAccent.copy(alpha = 0.32f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "$ ping --count $selectedPasses ${selectedServer.host}",
                style = terminalTextStyle,
                color = terminalAccent,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (state is ApiServerPingState.Running || state is ApiServerPingState.Completed || state is ApiServerPingState.Stopped) {
                LinearWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = if (state is ApiServerPingState.Stopped) terminalMuted else terminalAccent,
                    trackColor = terminalMuted.copy(alpha = 0.22f)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            when (state) {
                ApiServerPingState.Idle -> TerminalPrompt(
                    text = stringResource(R.string.appstrings_diagnostic_ping_terminal_ready)
                )
                is ApiServerPingState.Running -> {
                    state.samples.forEach { sample ->
                        key(sample.attempt) {
                            AnimatedVisibility(visible = true) {
                                ApiPingTerminalLine(sample)
                            }
                        }
                    }
                    TerminalPrompt(
                        text = stringResource(
                            R.string.appstrings_diagnostic_ping_running,
                            state.completedPasses,
                            state.passes
                        )
                    )
                }
                is ApiServerPingState.Completed -> {
                    state.result.samples.forEach { sample ->
                        key(sample.attempt) {
                            AnimatedVisibility(visible = true) {
                                ApiPingTerminalLine(sample)
                            }
                        }
                    }
                    AnimatedVisibility(visible = true) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(
                                    R.string.appstrings_diagnostic_ping_result_summary,
                                    state.result.successfulPasses,
                                    state.result.requestedPasses
                                ),
                                style = terminalTextStyle,
                                color = terminalText,
                                fontWeight = FontWeight.Bold
                            )
                            state.result.averageMs?.let { average ->
                                Text(
                                    text = stringResource(
                                        R.string.appstrings_diagnostic_ping_result_stats,
                                        average,
                                        state.result.minimumMs ?: average,
                                        state.result.maximumMs ?: average
                                    ),
                                    style = terminalTextStyle,
                                    color = terminalAccent
                                )
                            }
                            if (state.result.failedPasses > 0) {
                                Text(
                                    text = stringResource(
                                        R.string.appstrings_diagnostic_ping_result_failures,
                                        state.result.failedPasses
                                    ),
                                    style = terminalTextStyle,
                                    color = terminalError
                                )
                            }
                        }
                    }
                }
                is ApiServerPingState.Stopped -> {
                    state.samples.forEach { sample ->
                        key(sample.attempt) {
                            AnimatedVisibility(visible = true) {
                                ApiPingTerminalLine(sample)
                            }
                        }
                    }
                    AnimatedVisibility(visible = true) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            TerminalPrompt(
                                text = stringResource(
                                    R.string.appstrings_diagnostic_ping_stopped_summary,
                                    state.completedPasses,
                                    state.passes
                                ),
                                color = terminalMuted
                            )
                            val successfulSamples = state.samples.mapNotNull { it.latencyMs }
                            if (successfulSamples.isNotEmpty()) {
                                val average = successfulSamples.average().toLong()
                                Text(
                                    text = stringResource(
                                        R.string.appstrings_diagnostic_ping_result_stats,
                                        average,
                                        successfulSamples.minOrNull() ?: average,
                                        successfulSamples.maxOrNull() ?: average
                                    ),
                                    style = terminalTextStyle,
                                    color = terminalAccent
                                )
                            }
                            val failedCount = state.samples.count { it.latencyMs == null }
                            if (failedCount > 0) {
                                Text(
                                    text = stringResource(
                                        R.string.appstrings_diagnostic_ping_result_failures,
                                        failedCount
                                    ),
                                    style = terminalTextStyle,
                                    color = terminalError
                                )
                            }
                        }
                    }
                }
                ApiServerPingState.Failed -> TerminalPrompt(
                    text = stringResource(R.string.appstrings_diagnostic_ping_failed),
                    color = terminalError
                )
            }
        }
    }
}

@Composable
fun TerminalPrompt(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(
        text = "> $text",
        style = MaterialTheme.typography.bodySmall,
        color = color
    )
}

@Composable
fun ApiPingTerminalLine(sample: ServerPingSample) {
    val textStyle = MaterialTheme.typography.bodySmall
    val (text, color) = if (sample.latencyMs != null) {
        stringResource(
            R.string.appstrings_diagnostic_ping_terminal_reply,
            sample.attempt,
            sample.latencyMs
        ) to MaterialTheme.colorScheme.onSurface
    } else {
        stringResource(R.string.appstrings_diagnostic_ping_terminal_timeout, sample.attempt) to MaterialTheme.colorScheme.error
    }
    Text(text = text, style = textStyle, color = color)
}
