package fr.geotower.ui.screens.diagnostic

import android.Manifest
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.geotower.data.share.ShareHistoryStore
import fr.geotower.ui.theme.LocalGeoTowerUiSizing
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import fr.geotower.R
import fr.geotower.data.api.ApiEndpoints
import fr.geotower.data.api.ApiServer
import fr.geotower.data.api.ServerReachability
import fr.geotower.data.api.ServerStatus
import fr.geotower.data.build.LocalBuildReportStore
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.db.GeoTowerDatabaseValidator
import fr.geotower.data.db.LocalDbBuildStatus
import fr.geotower.data.db.RadioDatabaseValidator
import fr.geotower.data.workers.OfflineMapDownloadValidator
import fr.geotower.ui.components.ApiServerModeDialog
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.GeoTowerLoadingMessage
import fr.geotower.ui.components.PageScrollEdgeButtons
import fr.geotower.ui.components.apiServerModeLabelRes
import fr.geotower.ui.components.applyApiServerMode
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.components.pageScrollbar
import fr.geotower.utils.PageScrollPrefs
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppFileLog
import fr.geotower.utils.LiveTrackingPrefs
import fr.geotower.utils.PowerProfile
import fr.geotower.utils.PreferenceStores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SETTINGS_DATABASE_ROUTE = "settings?section=database"
private const val SETTINGS_OFFLINE_MAPS_ROUTE = "settings?section=offline_maps"

// --- Animation d'analyse ------------------------------------------------------------------------
// Une analyse ne dure souvent que quelques dizaines de millisecondes : sans durée plancher,
// l'animation clignoterait sans qu'on la voie. Les anciens résultats restent affichés (estompés)
// jusqu'à ce que les nouveaux soient prêts, pour ne jamais laisser l'écran vide.
private const val SCAN_MIN_ANIMATION_MS = 700L
private const val SCAN_STALE_CONTENT_ALPHA = 0.45f
private const val SCAN_SPIN_PERIOD_MS = 1300
private const val SCAN_PULSE_PERIOD_MS = 900

@Composable
fun DiagnosticScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "settings")
    val liveFallbackEnabled = RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.LIVE_API_FR)
    var state by remember { mutableStateOf<DiagnosticState?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showApiServerDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = LocalGeoTowerUiSizing.current

    fun refresh() {
        if (isRefreshing) return
        scope.launch {
            isRefreshing = true
            val startedAt = SystemClock.elapsedRealtime()
            try {
                // Les anciennes valeurs restent à l'écran : `state` n'est remplacé qu'une fois la
                // nouvelle analyse terminée (et l'animation vue).
                val next = buildDiagnosticState(context.applicationContext, liveFallbackEnabled)
                if (PowerProfile.richAnimations) {
                    val elapsed = SystemClock.elapsedRealtime() - startedAt
                    if (elapsed < SCAN_MIN_ANIMATION_MS) delay(SCAN_MIN_ANIMATION_MS - elapsed)
                }
                state = next
            } finally {
                isRefreshing = false
            }
        }
    }

    BackHandler(enabled = !safeBackNavigation.isLocked) {
        safeBackNavigation.navigateBack()
    }

    LaunchedEffect(liveFallbackEnabled) {
        refresh()
    }

    Scaffold(
        containerColor = uiStyle.backgroundColor,
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.appstrings_diagnostic_title),
                onBack = { safeBackNavigation.navigateBack() },
                backEnabled = !safeBackNavigation.isLocked,
                actionsWidth = 56.dp,
                actions = {
                    val refreshLabel = stringResource(R.string.appstrings_diagnostic_refresh)
                    val scanningLabel = stringResource(R.string.appstrings_diagnostic_scanning)
                    IconButton(
                        onClick = { refresh() },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            DiagnosticScanningRefreshIcon(contentDescription = scanningLabel)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = refreshLabel
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        val currentState = state
        if (currentState == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(uiStyle.backgroundColor)
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                GeoTowerLoadingMessage(
                    title = stringResource(R.string.appstrings_diagnostic_loading_title),
                    detail = stringResource(R.string.appstrings_diagnostic_loading_desc)
                )
            }
        } else {
            // Pendant une analyse les résultats précédents restent lisibles, simplement estompés.
            val staleAlpha by animateFloatAsState(
                targetValue = if (isRefreshing) SCAN_STALE_CONTENT_ALPHA else 1f,
                animationSpec = tween(durationMillis = if (PowerProfile.richAnimations) 320 else 0),
                label = "diagnostic-stale-alpha"
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(uiStyle.backgroundColor)
                    .padding(innerPadding)
            ) {
                DiagnosticScanProgressBar(visible = isRefreshing)

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .geoTowerFadingEdge(scrollState, fadeHeight = uiStyle.sizing.component(72.dp))
                        .pageScrollbar(PageScrollPrefs.DIAGNOSTIC, scrollState)
                        .verticalScroll(scrollState)
                        .navigationBarsPadding()
                        .padding(
                            horizontal = uiStyle.sizing.spacing(18.dp),
                            vertical = uiStyle.sizing.spacing(12.dp)
                        ),
                    verticalArrangement = Arrangement.spacedBy(uiStyle.sizing.spacing(14.dp))
                ) {
                    DiagnosticGlobalCard(
                        state = currentState,
                        isRefreshing = isRefreshing,
                        onRefresh = { refresh() },
                        onCopyReport = { copyDiagnosticReport(context, currentState.report) }
                    )

                    Column(
                        modifier = Modifier.alpha(staleAlpha),
                        verticalArrangement = Arrangement.spacedBy(uiStyle.sizing.spacing(14.dp))
                    ) {
                        DiagnosticStatusGrid(items = currentState.items)

                        currentState.items.forEach { item ->
                            DiagnosticItemCard(
                                item = item,
                                onAction = { action ->
                                    handleDiagnosticAction(context, navController, action) {
                                        showApiServerDialog = true
                                    }
                                }
                            )
                        }
                    }

                    Button(
                        onClick = { copyDiagnosticReport(context, currentState.report) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(sizing.component(18.dp)))
                        Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                        Text(stringResource(R.string.appstrings_diagnostic_copy_report))
                    }

                    // Journal disque (AppFileLog) : la seule trace exploitable pour les chemins
                    // qu'on ne peut pas observer avec un PC branché (Android Auto, workers, crash
                    // au démarrage). `logSize` est relu à chaque analyse pour refléter l'effacement.
                    var logSize by remember(currentState.generatedAt) {
                        mutableStateOf(AppFileLog.sizeBytes(context))
                    }
                    Text(
                        text = stringResource(
                            R.string.appstrings_diagnostic_log_size,
                            formatBytes(context, logSize)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { shareDebugLog(context) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(sizing.component(18.dp)))
                            Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                            Text(stringResource(R.string.appstrings_diagnostic_share_log))
                        }
                        TextButton(
                            onClick = {
                                AppFileLog.clear(context)
                                logSize = AppFileLog.sizeBytes(context)
                                Toast.makeText(context, R.string.appstrings_diagnostic_log_cleared, Toast.LENGTH_SHORT).show()
                            },
                            enabled = logSize > 0L
                        ) {
                            Text(stringResource(R.string.appstrings_diagnostic_clear_log))
                        }
                    }
                }
                PageScrollEdgeButtons(PageScrollPrefs.DIAGNOSTIC, scrollState)
                }
            }
        }
    }

    if (showApiServerDialog) {
        // Même dialogue que la carte « Serveur GeoTower » des Réglages (Système) : le réglage n'a
        // qu'un seul état, les deux pages ne peuvent donc pas diverger.
        ApiServerModeDialog(
            currentMode = ApiEndpoints.mode.value,
            onDismiss = { showApiServerDialog = false },
            onSelect = { selectedMode ->
                showApiServerDialog = false
                // Le scan est rejoué après la sonde forcée, sinon la carte afficherait encore le
                // verdict de l'ancien serveur.
                applyApiServerMode(context, scope, selectedMode) { refresh() }
            }
        )
    }
}

@Composable
private fun DiagnosticGlobalCard(
    state: DiagnosticState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onCopyReport: () -> Unit
) {
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val statusLabel = state.globalSeverity.localizedLabel()
    val scanningLabel = stringResource(R.string.appstrings_diagnostic_scanning)
    val contentDescription = buildString {
        append("${state.globalTitle}. $statusLabel. ${state.globalSummary}")
        if (isRefreshing) append(" $scanningLabel")
    }
    val generatedAtLabel = stringResource(R.string.appstrings_diagnostic_detail_generated_at, state.generatedAt)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (uiStyle.useOneUi) uiStyle.bubbleColor else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = uiStyle.cardShape,
        border = uiStyle.cardBorder,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription }
    ) {
        Column(modifier = Modifier.padding(sizing.spacing(18.dp))) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                DiagnosticStatusOrb(state.globalSeverity, scanning = isRefreshing)
                Spacer(modifier = Modifier.width(sizing.spacing(14.dp)))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.globalTitle,
                        style = sizing.textStyle(MaterialTheme.typography.headlineSmall),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(sizing.spacing(3.dp)))
                    Text(
                        text = state.globalSummary,
                        style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
                    DiagnosticStatusBadge(state.globalSeverity)
                }
            }

            Spacer(modifier = Modifier.height(sizing.spacing(14.dp)))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Spacer(modifier = Modifier.height(sizing.spacing(10.dp)))

            if (isRefreshing) {
                Text(
                    text = scanningLabel,
                    style = sizing.textStyle(MaterialTheme.typography.labelMedium),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(sizing.spacing(2.dp)))
                Text(
                    text = stringResource(R.string.appstrings_diagnostic_scanning_hint),
                    style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = generatedAtLabel,
                    style = sizing.textStyle(MaterialTheme.typography.labelMedium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(sizing.spacing(10.dp)))

            Row(horizontalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp))) {
                Button(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isRefreshing) {
                        DiagnosticScanningRefreshIcon(
                            contentDescription = null,
                            size = sizing.component(18.dp)
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(sizing.component(18.dp)))
                    }
                    Spacer(modifier = Modifier.width(sizing.spacing(6.dp)))
                    Text(
                        text = stringResource(R.string.appstrings_diagnostic_refresh),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onCopyReport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(sizing.component(18.dp)))
                    Spacer(modifier = Modifier.width(sizing.spacing(6.dp)))
                    Text(stringResource(R.string.appstrings_diagnostic_copy_report))
                }
            }
        }
    }
}

@Composable
private fun DiagnosticStatusGrid(items: List<DiagnosticItem>) {
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 620.dp) 3 else 2
        val rows = remember(items, columns) { items.chunked(columns) }

        Column(verticalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp))) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp))
                ) {
                    rowItems.forEach { item ->
                        DiagnosticStatusTile(
                            item = item,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticStatusTile(
    item: DiagnosticItem,
    modifier: Modifier = Modifier
) {
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val statusLabel = item.severity.localizedLabel()

    Surface(
        color = if (uiStyle.useOneUi) uiStyle.bubbleColor else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = uiStyle.smallItemShape,
        border = uiStyle.subtleBorder,
        modifier = modifier
            .semantics {
                contentDescription = "${item.title}. $statusLabel. ${item.summary}"
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(sizing.spacing(12.dp)),
            verticalArrangement = Arrangement.spacedBy(sizing.spacing(7.dp))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = item.severity.icon(),
                    contentDescription = null,
                    tint = item.severity.contentColor(),
                    modifier = Modifier.size(sizing.component(18.dp))
                )
                Spacer(modifier = Modifier.width(sizing.spacing(6.dp)))
                Text(
                    text = statusLabel,
                    style = sizing.textStyle(MaterialTheme.typography.labelMedium),
                    color = item.severity.contentColor(),
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
            Text(
                text = item.title,
                style = sizing.textStyle(MaterialTheme.typography.titleSmall),
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.summary,
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DiagnosticItemCard(
    item: DiagnosticItem,
    onAction: (DiagnosticAction) -> Unit
) {
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    var expanded by remember(item.id) { mutableStateOf(false) }
    val statusLabel = item.severity.localizedLabel()
    val detailsContentDescription = stringResource(R.string.appstrings_diagnostic_details_cd, item.title)
    val stateLabel = if (expanded) {
        stringResource(R.string.appstrings_diagnostic_details_open)
    } else {
        stringResource(R.string.appstrings_diagnostic_details_closed)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (uiStyle.useOneUi) uiStyle.bubbleColor else MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = uiStyle.blockShape,
        border = uiStyle.cardBorder,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${item.title}. $statusLabel. ${item.summary}"
                stateDescription = stateLabel
            }
    ) {
        Column(modifier = Modifier.padding(sizing.spacing(15.dp))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DiagnosticStatusBadge(item.severity)
                Spacer(modifier = Modifier.width(sizing.spacing(10.dp)))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.summary,
                        style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.details.isNotEmpty()) {
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.semantics {
                            contentDescription = detailsContentDescription
                            stateDescription = stateLabel
                        }
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
            }

            if (expanded && item.details.isNotEmpty()) {
                Spacer(modifier = Modifier.height(sizing.spacing(10.dp)))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(sizing.spacing(10.dp)))
                Column(verticalArrangement = Arrangement.spacedBy(sizing.spacing(5.dp))) {
                    item.details.forEach { detail ->
                        Text(
                            text = detail,
                            style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val action = item.action
            val label = item.actionLabel
            if (action != null && label != null) {
                Spacer(modifier = Modifier.height(sizing.spacing(10.dp)))
                TextButton(
                    onClick = { onAction(action) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticStatusOrb(severity: DiagnosticSeverity, scanning: Boolean) {
    val sizing = LocalGeoTowerUiSizing.current
    val orbSize = sizing.component(46.dp)
    // Anneau indéterminé autour de la pastille : le statut précédent reste visible pendant l'analyse.
    val ringSize = sizing.component(58.dp)

    Box(
        modifier = Modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        val pulse = if (scanning) diagnosticScanPulse() else 1f
        Surface(
            color = severity.containerColor(),
            contentColor = severity.contentColor(),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            }
        ) {
            Box(
                modifier = Modifier.size(orbSize),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = severity.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(sizing.component(26.dp))
                )
            }
        }
        if (scanning) {
            CircularProgressIndicator(
                modifier = Modifier.size(ringSize),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
                strokeWidth = sizing.component(3.dp)
            )
        }
    }
}

/** Battement discret de la pastille pendant l'analyse (neutralisé en mode faible consommation). */
@Composable
private fun diagnosticScanPulse(): Float {
    if (!PowerProfile.richAnimations) return 1f
    val transition = rememberInfiniteTransition(label = "diagnostic-scan-pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SCAN_PULSE_PERIOD_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "diagnostic-scan-pulse-value"
    )
    return pulse
}

/**
 * Icône « actualiser » qui tourne tant que l'analyse est en cours. N'est composée que pendant
 * l'analyse : la transition infinie s'arrête d'elle-même une fois les résultats affichés.
 */
@Composable
private fun DiagnosticScanningRefreshIcon(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp? = null
) {
    val rotation = if (PowerProfile.richAnimations) {
        val transition = rememberInfiniteTransition(label = "diagnostic-scan-spin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = SCAN_SPIN_PERIOD_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "diagnostic-scan-spin-value"
        )
        angle
    } else {
        0f
    }
    Icon(
        imageVector = Icons.Default.Refresh,
        contentDescription = contentDescription,
        tint = LocalContentColor.current,
        modifier = modifier
            .then(if (size != null) Modifier.size(size) else Modifier)
            .graphicsLayer { rotationZ = rotation }
    )
}

/** Fine barre indéterminée sous la barre de titre, visible uniquement pendant l'analyse. */
@Composable
private fun DiagnosticScanProgressBar(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun DiagnosticStatusBadge(severity: DiagnosticSeverity) {
    val sizing = LocalGeoTowerUiSizing.current
    Surface(
        color = severity.containerColor(),
        contentColor = severity.contentColor(),
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = sizing.spacing(10.dp), vertical = sizing.spacing(6.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = severity.icon(),
                contentDescription = null,
                modifier = Modifier.size(sizing.component(16.dp))
            )
            Spacer(modifier = Modifier.width(sizing.spacing(5.dp)))
            Text(
                text = severity.localizedLabel(),
                style = sizing.textStyle(MaterialTheme.typography.labelMedium),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DiagnosticSeverity.localizedLabel(): String {
    return when (this) {
        DiagnosticSeverity.Ok -> stringResource(R.string.appstrings_diagnostic_status_ok)
        DiagnosticSeverity.Info -> stringResource(R.string.appstrings_diagnostic_status_info)
        DiagnosticSeverity.Warning -> stringResource(R.string.appstrings_diagnostic_status_warning)
        DiagnosticSeverity.Error -> stringResource(R.string.appstrings_diagnostic_status_error)
        DiagnosticSeverity.Unknown -> stringResource(R.string.appstrings_diagnostic_status_unknown)
    }
}

@Composable
private fun DiagnosticSeverity.containerColor(): Color {
    return when (this) {
        DiagnosticSeverity.Ok -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f)
        DiagnosticSeverity.Info -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
        DiagnosticSeverity.Warning -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.78f)
        DiagnosticSeverity.Error -> MaterialTheme.colorScheme.errorContainer
        DiagnosticSeverity.Unknown -> MaterialTheme.colorScheme.surfaceVariant
    }
}

@Composable
private fun DiagnosticSeverity.contentColor(): Color {
    return when (this) {
        DiagnosticSeverity.Error -> MaterialTheme.colorScheme.onErrorContainer
        DiagnosticSeverity.Ok -> MaterialTheme.colorScheme.onPrimaryContainer
        DiagnosticSeverity.Info -> MaterialTheme.colorScheme.onSecondaryContainer
        DiagnosticSeverity.Warning -> MaterialTheme.colorScheme.onTertiaryContainer
        DiagnosticSeverity.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun DiagnosticSeverity.icon(): ImageVector {
    return when (this) {
        DiagnosticSeverity.Ok -> Icons.Default.CheckCircle
        DiagnosticSeverity.Info -> Icons.Default.Info
        DiagnosticSeverity.Warning -> Icons.Default.Warning
        DiagnosticSeverity.Error -> Icons.Default.Error
        DiagnosticSeverity.Unknown -> Icons.Default.Info
    }
}

private suspend fun buildDiagnosticState(
    context: Context,
    liveFallbackEnabled: Boolean
): DiagnosticState = withContext(Dispatchers.IO) {
    val prefs = context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
    val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    // Une base en cours de generation est absente du disque jusqu'a la fin du build : le diagnostic
    // doit dire « generation en cours », pas « base absente ».
    val localBuild = LocalDbBuildStatus.read(context)
    val antennaItem = buildAntennaDatabaseItem(context, liveFallbackEnabled, localBuild)
    val radioItem = buildRadioDatabaseItem(context, localBuild)
    val mapsItem = buildOfflineMapsItem(context)
    val permissionsItem = buildPermissionsItem(context, prefs.getBoolean("enable_live_notifications", false))
    val notificationsItem = buildNotificationsItem(context, prefs)
    val storageItem = buildStorageItem(context)
    // Sonde non forcée : le verdict de moins de 30 s est réutilisé, une analyse manuelle plus
    // tardive re-teste réellement les deux serveurs.
    ServerReachability.refresh()
    val apiServerItem = buildApiServerItem(context)
    val environmentItem = buildEnvironmentItem(context, generatedAt)
    // Absent tant qu'aucune generation locale n'a ete mesuree sur cet appareil.
    val localBuildMetricsItem = buildLocalBuildMetricsItem(context)
    val items = listOfNotNull(
        antennaItem,
        radioItem,
        mapsItem,
        permissionsItem,
        notificationsItem,
        apiServerItem,
        storageItem,
        localBuildMetricsItem,
        environmentItem
    )
    val globalSeverity = when {
        items.any { it.severity == DiagnosticSeverity.Error } -> DiagnosticSeverity.Error
        items.any { it.severity == DiagnosticSeverity.Warning } -> DiagnosticSeverity.Warning
        else -> DiagnosticSeverity.Ok
    }
    val globalTitle = when (globalSeverity) {
        DiagnosticSeverity.Error -> context.getString(R.string.appstrings_diagnostic_global_error)
        DiagnosticSeverity.Warning -> context.getString(R.string.appstrings_diagnostic_global_warning)
        else -> context.getString(R.string.appstrings_diagnostic_global_ok)
    }
    val globalSummary = when (globalSeverity) {
        DiagnosticSeverity.Error -> context.getString(R.string.appstrings_diagnostic_global_error_summary)
        DiagnosticSeverity.Warning -> context.getString(R.string.appstrings_diagnostic_global_warning_summary)
        else -> context.getString(R.string.appstrings_diagnostic_global_ok_summary)
    }

    DiagnosticState(
        globalTitle = globalTitle,
        globalSummary = globalSummary,
        globalSeverity = globalSeverity,
        generatedAt = generatedAt,
        report = buildReport(context, generatedAt, globalTitle, items),
        items = items
    )
}

private fun buildAntennaDatabaseItem(
    context: Context,
    liveFallbackEnabled: Boolean,
    localBuild: LocalDbBuildStatus
): DiagnosticItem {
    val dbFile = context.getDatabasePath(GeoTowerDatabaseValidator.DB_NAME)
    val status = GeoTowerDatabaseValidator.getInstalledDatabaseStatus(context)
    val version = GeoTowerDatabaseValidator.getInstalledDatabaseVersion(context)
    val sidecars = databaseSidecars(context, GeoTowerDatabaseValidator.DB_NAME)
    val downloadArtifact = sidecars.firstOrNull { it.name.endsWith(".download") && it.length() > 0L }
    // Base en cours de generation sur l'appareil : ce n'est pas une anomalie, juste une attente.
    val generating = localBuild.mobileRunning &&
        status.state != GeoTowerDatabaseValidator.LocalDatabaseState.VALID
    val severity = when {
        generating -> DiagnosticSeverity.Info
        downloadArtifact != null -> DiagnosticSeverity.Warning
        status.state == GeoTowerDatabaseValidator.LocalDatabaseState.VALID -> DiagnosticSeverity.Ok
        liveFallbackEnabled -> DiagnosticSeverity.Warning
        else -> DiagnosticSeverity.Error
    }
    val summary = when {
        generating -> context.getString(R.string.appstrings_diagnostic_summary_antennas_generating)
        downloadArtifact != null -> context.getString(R.string.appstrings_diagnostic_summary_antennas_incomplete)
        status.state == GeoTowerDatabaseValidator.LocalDatabaseState.VALID -> {
            context.getString(
                R.string.appstrings_diagnostic_summary_antennas_ok,
                version ?: context.getString(R.string.appstrings_diagnostic_value_unknown)
            )
        }
        status.state == GeoTowerDatabaseValidator.LocalDatabaseState.MISSING -> {
            context.getString(R.string.appstrings_diagnostic_summary_antennas_missing)
        }
        else -> context.getString(R.string.appstrings_diagnostic_summary_antennas_invalid)
    }
    val details = buildList {
        add(
            context.getString(
                if (dbFile.isFile) R.string.appstrings_diagnostic_detail_file_present else R.string.appstrings_diagnostic_detail_file_missing,
                GeoTowerDatabaseValidator.DB_NAME
            )
        )
        add(context.getString(R.string.appstrings_diagnostic_detail_size, formatBytes(context, dbFile.lengthOrZero())))
        add(context.getString(R.string.appstrings_diagnostic_detail_version, version ?: context.getString(R.string.appstrings_diagnostic_value_unknown)))
        add(context.getString(R.string.appstrings_diagnostic_detail_schema, GeoTowerDatabaseValidator.EXPECTED_SCHEMA_VERSION))
        status.reason?.let { add(context.getString(R.string.appstrings_diagnostic_detail_reason, it)) }
        if (sidecars.isNotEmpty()) {
            add(context.getString(R.string.appstrings_diagnostic_detail_sidecars, sidecars.joinToString { it.name }))
        }
        if (downloadArtifact != null) {
            add(context.getString(R.string.appstrings_diagnostic_detail_download_artifact, downloadArtifact.name))
        }
    }
    return DiagnosticItem(
        id = "antennas_db",
        title = context.getString(R.string.appstrings_diagnostic_section_antennas_db),
        summary = summary,
        severity = severity,
        details = details,
        actionLabel = context.getString(R.string.appstrings_diagnostic_action_open_database_settings),
        action = DiagnosticAction.Navigate(SETTINGS_DATABASE_ROUTE)
    )
}

private fun buildRadioDatabaseItem(context: Context, localBuild: LocalDbBuildStatus): DiagnosticItem {
    val dbFile = context.getDatabasePath(RadioDatabaseValidator.DB_NAME)
    val validation = if (dbFile.isFile) RadioDatabaseValidator.validateDatabaseFile(dbFile) else null
    val metadata = if (validation?.isValid == true) readRadioMetadata(dbFile) else RadioMetadata()
    // Base radio en cours de generation : on l'annonce plutot que « non installee » / « invalide ».
    val generating = localBuild.radioRunning && validation?.isValid != true
    val severity = when {
        generating -> DiagnosticSeverity.Info
        !dbFile.isFile || dbFile.length() <= 0L -> DiagnosticSeverity.Info
        validation?.isValid == true -> DiagnosticSeverity.Ok
        else -> DiagnosticSeverity.Warning
    }
    val summary = when {
        generating -> context.getString(R.string.appstrings_diagnostic_summary_radio_generating)
        severity == DiagnosticSeverity.Ok -> context.getString(
            R.string.appstrings_diagnostic_summary_radio_ok,
            metadata.version ?: context.getString(R.string.appstrings_diagnostic_value_unknown)
        )
        severity == DiagnosticSeverity.Info -> context.getString(R.string.appstrings_diagnostic_summary_radio_missing)
        else -> context.getString(R.string.appstrings_diagnostic_summary_radio_invalid)
    }
    val details = buildList {
        add(
            context.getString(
                if (dbFile.isFile) R.string.appstrings_diagnostic_detail_file_present else R.string.appstrings_diagnostic_detail_file_missing,
                RadioDatabaseValidator.DB_NAME
            )
        )
        add(context.getString(R.string.appstrings_diagnostic_detail_size, formatBytes(context, dbFile.lengthOrZero())))
        add(context.getString(R.string.appstrings_diagnostic_detail_schema, RadioDatabaseValidator.EXPECTED_SCHEMA_VERSION))
        metadata.version?.let { add(context.getString(R.string.appstrings_diagnostic_detail_version, it)) }
        metadata.anfrDate?.let { add(context.getString(R.string.appstrings_diagnostic_detail_radio_date, it)) }
        metadata.rowCount?.let { add(context.getString(R.string.appstrings_diagnostic_detail_radio_rows, it)) }
        validation?.reason?.let { add(context.getString(R.string.appstrings_diagnostic_detail_reason, it)) }
    }
    return DiagnosticItem(
        id = "radio_db",
        title = context.getString(R.string.appstrings_diagnostic_section_radio_db),
        summary = summary,
        severity = severity,
        details = details,
        actionLabel = context.getString(R.string.appstrings_diagnostic_action_open_database_settings),
        action = DiagnosticAction.Navigate(SETTINGS_DATABASE_ROUTE)
    )
}

private fun buildOfflineMapsItem(context: Context): DiagnosticItem {
    val mapsDir = context.getExternalFilesDir(null)?.let { File(it, "maps") }
    val files = mapsDir?.listFiles()?.toList().orEmpty()
    val mapFiles = mapsDir?.let { OfflineMapDownloadValidator.listSafeMapFiles(it) }.orEmpty()
    val zeroByteMaps = mapFiles.filter { it.length() <= 0L }
    val tempFiles = files.filter { it.isFile && (it.name.endsWith(".download") || it.name.endsWith(".tmp")) }
    val unknownFiles = files.filter { file ->
        file.isFile &&
            !file.name.endsWith(".map") &&
            !file.name.endsWith(".download") &&
            !file.name.endsWith(".backup") &&
            !file.name.endsWith(".tmp")
    }
    val totalSize = mapFiles.sumOf { it.lengthOrZero() }
    val latestModified = mapFiles.maxOfOrNull { it.lastModified() }?.takeIf { it > 0L }
    val severity = when {
        zeroByteMaps.isNotEmpty() || tempFiles.isNotEmpty() -> DiagnosticSeverity.Warning
        mapFiles.isEmpty() -> DiagnosticSeverity.Info
        else -> DiagnosticSeverity.Ok
    }
    val summary = when {
        zeroByteMaps.isNotEmpty() || tempFiles.isNotEmpty() -> context.getString(R.string.appstrings_diagnostic_summary_maps_warning)
        mapFiles.isEmpty() -> context.getString(R.string.appstrings_diagnostic_summary_maps_empty)
        else -> context.getString(R.string.appstrings_diagnostic_summary_maps_ok, mapFiles.size, formatBytes(context, totalSize))
    }
    val details = buildList {
        add(context.getString(R.string.appstrings_diagnostic_detail_maps_count, mapFiles.size))
        add(context.getString(R.string.appstrings_diagnostic_detail_maps_size, formatBytes(context, totalSize)))
        latestModified?.let { add(context.getString(R.string.appstrings_diagnostic_detail_maps_latest, formatDateTime(it))) }
        if (zeroByteMaps.isNotEmpty()) {
            add(context.getString(R.string.appstrings_diagnostic_detail_maps_zero, zeroByteMaps.joinToString { it.name }))
        }
        if (tempFiles.isNotEmpty()) {
            add(context.getString(R.string.appstrings_diagnostic_detail_maps_temp, tempFiles.joinToString { it.name }))
        }
        if (unknownFiles.isNotEmpty()) {
            add(context.getString(R.string.appstrings_diagnostic_detail_maps_unknown, unknownFiles.joinToString { it.name }))
        }
    }
    return DiagnosticItem(
        id = "offline_maps",
        title = context.getString(R.string.appstrings_diagnostic_section_offline_maps),
        summary = summary,
        severity = severity,
        details = details,
        actionLabel = context.getString(R.string.appstrings_diagnostic_action_open_offline_maps),
        action = DiagnosticAction.Navigate(SETTINGS_OFFLINE_MAPS_ROUTE)
    )
}

private fun buildPermissionsItem(context: Context, liveNotificationsEnabled: Boolean): DiagnosticItem {
    val precise = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val approximate = hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    val background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        hasPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
        true
    }
    val hasLocation = precise || approximate
    val severity = when {
        liveNotificationsEnabled && (!hasLocation || !background) -> DiagnosticSeverity.Error
        !hasLocation -> DiagnosticSeverity.Warning
        else -> DiagnosticSeverity.Ok
    }
    val summary = when (severity) {
        DiagnosticSeverity.Ok -> context.getString(R.string.appstrings_diagnostic_summary_permissions_ok)
        DiagnosticSeverity.Error -> context.getString(R.string.appstrings_diagnostic_summary_permissions_error)
        else -> context.getString(R.string.appstrings_diagnostic_summary_permissions_warning)
    }
    val details = listOf(
        context.getString(R.string.appstrings_diagnostic_detail_location_precise, yesNo(context, precise)),
        context.getString(R.string.appstrings_diagnostic_detail_location_approximate, yesNo(context, approximate)),
        context.getString(
            R.string.appstrings_diagnostic_detail_location_background,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) yesNo(context, background) else context.getString(R.string.appstrings_diagnostic_value_not_required)
        ),
        context.getString(
            R.string.appstrings_diagnostic_detail_notification_permission,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                yesNo(context, hasPermission(context, Manifest.permission.POST_NOTIFICATIONS))
            } else {
                context.getString(R.string.appstrings_diagnostic_value_not_required)
            }
        )
    )
    return DiagnosticItem(
        id = "permissions",
        title = context.getString(R.string.appstrings_diagnostic_section_permissions),
        summary = summary,
        severity = severity,
        details = details,
        actionLabel = context.getString(R.string.appstrings_diagnostic_action_open_app_settings),
        action = DiagnosticAction.OpenAppSettings
    )
}

private fun buildNotificationsItem(context: Context, prefs: android.content.SharedPreferences): DiagnosticItem {
    val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    // Interrupteur maître de l'app (indépendant du suivi live) puis ses sous-réglages.
    val appNotificationsEnabled = fr.geotower.utils.AppNotifications.enabled(prefs)
    val updateNotificationsEnabled = prefs.getBoolean("enable_update_notifications", true)
    val liveNotificationsEnabled = prefs.getBoolean("enable_live_notifications", false)
    val liveInterval = LiveTrackingPrefs.locationUpdateIntervalSeconds(prefs)
    val blockedChannels = blockedNotificationChannelNames(context)
    val severity = when {
        !notificationsEnabled && (appNotificationsEnabled || liveNotificationsEnabled) -> DiagnosticSeverity.Warning
        blockedChannels.isNotEmpty() -> DiagnosticSeverity.Warning
        !appNotificationsEnabled && !liveNotificationsEnabled -> DiagnosticSeverity.Info
        else -> DiagnosticSeverity.Ok
    }
    val summary = when {
        !notificationsEnabled -> context.getString(R.string.appstrings_diagnostic_summary_notifications_blocked)
        severity == DiagnosticSeverity.Info -> context.getString(R.string.appstrings_diagnostic_summary_notifications_info)
        else -> context.getString(R.string.appstrings_diagnostic_summary_notifications_ok)
    }
    val details = buildList {
        add(context.getString(R.string.appstrings_diagnostic_detail_notifications_android, yesNo(context, notificationsEnabled)))
        add(context.getString(R.string.appstrings_diagnostic_detail_notifications_app, enabledDisabled(context, appNotificationsEnabled)))
        add(context.getString(R.string.appstrings_diagnostic_detail_notifications_update, enabledDisabled(context, updateNotificationsEnabled)))
        add(context.getString(R.string.appstrings_diagnostic_detail_notifications_live, enabledDisabled(context, liveNotificationsEnabled)))
        add(context.getString(R.string.appstrings_diagnostic_detail_notifications_live_interval, liveInterval))
        if (blockedChannels.isNotEmpty()) {
            add(context.getString(R.string.appstrings_diagnostic_detail_notifications_channels_blocked, blockedChannels.joinToString()))
        }
    }
    return DiagnosticItem(
        id = "notifications",
        title = context.getString(R.string.appstrings_diagnostic_section_notifications),
        summary = summary,
        severity = severity,
        details = details,
        actionLabel = context.getString(R.string.appstrings_diagnostic_action_open_notification_settings),
        action = DiagnosticAction.OpenNotificationSettings
    )
}

private fun buildStorageItem(context: Context): DiagnosticItem {
    val antennaDb = context.getDatabasePath(GeoTowerDatabaseValidator.DB_NAME)
    val radioDb = context.getDatabasePath(RadioDatabaseValidator.DB_NAME)
    val enbDb = context.getDatabasePath(fr.geotower.data.db.EnbDatabaseValidator.DB_NAME)
    // Copies des pannes : celle du serveur et celle produite sur l'appareil, jamais les deux en usage.
    val outagesSize = listOf(
        fr.geotower.data.outages.ServerOutageCache.FILE_NAME,
        fr.geotower.data.outages.OutageLocalCache.FILE_NAME,
    ).sumOf { File(context.filesDir, it).lengthOrZero() }
    val mapsDir = context.getExternalFilesDir(null)?.let { File(it, "maps") }
    val mapFiles = mapsDir?.let { OfflineMapDownloadValidator.listSafeMapFiles(it) }.orEmpty()
    val mapsSize = mapFiles.sumOf { it.lengthOrZero() }
    val cacheSize = directorySizeCapped(context.cacheDir)
    val available = runCatching {
        val stat = StatFs(context.filesDir.path)
        stat.availableBytes
    }.getOrDefault(0L)
    val knownSize = antennaDb.lengthOrZero() + radioDb.lengthOrZero() + enbDb.lengthOrZero() +
        outagesSize + mapsSize + cacheSize
    val details = listOf(
        context.getString(R.string.appstrings_diagnostic_detail_storage_db_antennas, formatBytes(context, antennaDb.lengthOrZero())),
        context.getString(R.string.appstrings_diagnostic_detail_storage_db_radio, formatBytes(context, radioDb.lengthOrZero())),
        context.getString(R.string.appstrings_diagnostic_detail_storage_db_enb, formatBytes(context, enbDb.lengthOrZero())),
        context.getString(R.string.appstrings_diagnostic_detail_storage_outages, formatBytes(context, outagesSize)),
        context.getString(R.string.appstrings_diagnostic_detail_storage_maps, formatBytes(context, mapsSize)),
        context.getString(R.string.appstrings_diagnostic_detail_storage_cache, formatBytes(context, cacheSize)),
        context.getString(R.string.appstrings_diagnostic_detail_storage_available, formatBytes(context, available))
    )
    return DiagnosticItem(
        id = "storage",
        title = context.getString(R.string.appstrings_diagnostic_section_storage),
        summary = context.getString(R.string.appstrings_diagnostic_summary_storage, formatBytes(context, knownSize)),
        severity = DiagnosticSeverity.Info,
        details = details
    )
}

/**
 * Serveur GeoTower réellement utilisé : principal, ou miroir de secours après bascule. Le réglage
 * (auto / forcé) est modifiable depuis l'action de la carte.
 */
private fun buildApiServerItem(context: Context): DiagnosticItem {
    val server = ApiEndpoints.active()
    val mode = ApiEndpoints.mode.value
    val status = ServerReachability.status.value
    val onMirror = server == ApiServer.MIRROR
    val severity = when {
        status == ServerStatus.UNREACHABLE -> DiagnosticSeverity.Error
        onMirror -> DiagnosticSeverity.Warning
        status == ServerStatus.REACHABLE -> DiagnosticSeverity.Ok
        else -> DiagnosticSeverity.Info
    }
    val summary = when {
        status == ServerStatus.UNREACHABLE -> {
            context.getString(R.string.appstrings_diagnostic_summary_api_unreachable)
        }
        onMirror -> context.getString(R.string.appstrings_diagnostic_summary_api_mirror, server.host)
        else -> context.getString(R.string.appstrings_diagnostic_summary_api_primary, server.host)
    }
    val details = buildList {
        add(context.getString(R.string.appstrings_diagnostic_detail_api_host, server.host))
        add(context.getString(R.string.appstrings_diagnostic_detail_api_primary, ApiServer.PRIMARY.host))
        add(context.getString(R.string.appstrings_diagnostic_detail_api_mirror, ApiServer.MIRROR.host))
        add(context.getString(R.string.appstrings_diagnostic_detail_api_mode, context.getString(apiServerModeLabelRes(mode))))
        add(
            context.getString(
                R.string.appstrings_diagnostic_detail_api_status,
                context.getString(
                    when (status) {
                        ServerStatus.REACHABLE -> R.string.appstrings_diagnostic_api_status_reachable
                        ServerStatus.UNREACHABLE -> R.string.appstrings_diagnostic_api_status_unreachable
                        ServerStatus.UNKNOWN -> R.string.appstrings_diagnostic_api_status_unknown
                    }
                )
            )
        )
        val checkedAt = ServerReachability.lastCheckedAt
        add(
            context.getString(
                R.string.appstrings_diagnostic_detail_api_checked_at,
                if (checkedAt > 0L) {
                    formatDateTime(checkedAt)
                } else {
                    context.getString(R.string.appstrings_diagnostic_api_never)
                }
            )
        )
        val switchedAt = ApiEndpoints.lastSwitchAt.value
        if (switchedAt > 0L) {
            add(context.getString(R.string.appstrings_diagnostic_detail_api_switched_at, formatDateTime(switchedAt)))
        }
    }
    return DiagnosticItem(
        id = "api_server",
        title = context.getString(R.string.appstrings_diagnostic_section_api_server),
        summary = summary,
        severity = severity,
        details = details,
        actionLabel = context.getString(R.string.appstrings_diagnostic_action_choose_api_server),
        action = DiagnosticAction.ChooseApiServer
    )
}

private fun buildEnvironmentItem(context: Context, generatedAt: String): DiagnosticItem {
    val packageInfo = packageInfo(context)
    val versionName = packageInfo?.versionName ?: context.getString(R.string.appstrings_diagnostic_value_unknown)
    val versionCode = packageInfo?.let(::versionCodeCompat)?.toString()
        ?: context.getString(R.string.appstrings_diagnostic_value_unknown)
    val androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    val device = listOf(Build.MANUFACTURER, Build.MODEL)
        .joinToString(" ")
        .trim()
        .ifBlank { context.getString(R.string.appstrings_diagnostic_value_unknown) }
    val language = Locale.getDefault().toLanguageTag()
    val details = listOf(
        context.getString(R.string.appstrings_diagnostic_detail_app_version, versionName),
        context.getString(R.string.appstrings_diagnostic_detail_app_code, versionCode),
        context.getString(R.string.appstrings_diagnostic_detail_android, androidVersion),
        context.getString(R.string.appstrings_diagnostic_detail_device, device),
        context.getString(R.string.appstrings_diagnostic_detail_language, language),
        context.getString(R.string.appstrings_diagnostic_detail_generated_at, generatedAt)
    )
    return DiagnosticItem(
        id = "environment",
        title = context.getString(R.string.appstrings_diagnostic_section_environment),
        summary = context.getString(R.string.appstrings_diagnostic_summary_environment, versionName, androidVersion),
        severity = DiagnosticSeverity.Info,
        details = details
    )
}

/**
 * Mesures de la derniere generation locale (durees par phase, pics de tas Java / memoire native /
 * stockage, tailles produites), enregistrees par `LocalDbBuildPipeline`.
 *
 * C'est ce qui permet de savoir si la generation peut etre ouverte a un appareil donne : le
 * plafond reel n'est pas la RAM totale mais le tas Java du process. Absent tant qu'aucune
 * generation n'a tourne.
 */
private fun buildLocalBuildMetricsItem(context: Context): DiagnosticItem? {
    val details = LocalBuildReportStore.read(context)
    if (details.isEmpty()) return null
    val measuredAt = LocalBuildReportStore.readTimestamp(context)
        ?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it)) }
        ?: context.getString(R.string.appstrings_diagnostic_value_unknown)
    return DiagnosticItem(
        id = "local_build_metrics",
        title = context.getString(R.string.appstrings_diagnostic_section_local_build),
        summary = context.getString(R.string.appstrings_diagnostic_summary_local_build, measuredAt),
        severity = DiagnosticSeverity.Info,
        details = details
    )
}

private fun buildReport(
    context: Context,
    generatedAt: String,
    globalTitle: String,
    items: List<DiagnosticItem>
): String {
    val packageInfo = packageInfo(context)
    val appVersion = packageInfo?.versionName ?: context.getString(R.string.appstrings_diagnostic_value_unknown)
    val appCode = packageInfo?.let(::versionCodeCompat)?.toString()
        ?: context.getString(R.string.appstrings_diagnostic_value_unknown)
    return buildString {
        appendLine(context.getString(R.string.appstrings_diagnostic_report_title))
        appendLine(context.getString(R.string.appstrings_diagnostic_report_date, generatedAt))
        appendLine(context.getString(R.string.appstrings_diagnostic_report_app, appVersion, appCode))
        appendLine(context.getString(R.string.appstrings_diagnostic_report_android, Build.VERSION.RELEASE, Build.VERSION.SDK_INT))
        appendLine()
        appendLine(context.getString(R.string.appstrings_diagnostic_report_global, globalTitle))
        items.forEach { item ->
            appendLine("${item.title}: ${item.summary}")
        }
    }.trim()
}

private data class RadioMetadata(
    val version: String? = null,
    val anfrDate: String? = null,
    val rowCount: Long? = null
)

private fun readRadioMetadata(file: File): RadioMetadata {
    var db: SQLiteDatabase? = null
    return try {
        db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        db.rawQuery("SELECT version, date_maj_anfr, row_count FROM metadata LIMIT 1", null).use { cursor ->
            if (cursor.moveToFirst()) {
                RadioMetadata(
                    version = cursor.getString(0),
                    anfrDate = cursor.getString(1),
                    rowCount = cursor.getLong(2)
                )
            } else {
                RadioMetadata()
            }
        }
    } catch (e: Exception) {
        RadioMetadata()
    } finally {
        db?.close()
    }
}

private fun handleDiagnosticAction(
    context: Context,
    navController: NavController,
    action: DiagnosticAction,
    onChooseApiServer: () -> Unit
) {
    when (action) {
        is DiagnosticAction.Navigate -> navController.navigate(action.route)
        DiagnosticAction.OpenAppSettings -> openAppSettings(context)
        DiagnosticAction.OpenNotificationSettings -> openNotificationSettings(context)
        DiagnosticAction.ChooseApiServer -> onChooseApiServer()
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    context.startActivity(intent)
}

private fun copyDiagnosticReport(context: Context, report: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(
        ClipData.newPlainText(
            context.getString(R.string.appstrings_diagnostic_title),
            report
        )
    )
    ShareHistoryStore.record(
        context = context,
        kind = ShareHistoryStore.KIND_DIAGNOSTIC,
        destination = ShareHistoryStore.DEST_CLIPBOARD,
        contents = ShareHistoryStore.FIELD_REPORT
    )
    Toast.makeText(context, R.string.appstrings_diagnostic_report_copied, Toast.LENGTH_SHORT).show()
}

private fun shareDebugLog(context: Context) {
    val chooserTitle = context.getString(R.string.appstrings_diagnostic_share_log)
    if (!AppFileLog.share(context, chooserTitle)) {
        Toast.makeText(context, R.string.appstrings_diagnostic_log_empty, Toast.LENGTH_SHORT).show()
        return
    }
    // Enregistré seulement si le journal existait : sinon rien n'est parti.
    ShareHistoryStore.record(
        context = context,
        kind = ShareHistoryStore.KIND_DIAGNOSTIC,
        destination = ShareHistoryStore.DEST_SHARE
    )
}

private fun hasPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun yesNo(context: Context, value: Boolean): String {
    return context.getString(
        if (value) R.string.appstrings_diagnostic_value_yes else R.string.appstrings_diagnostic_value_no
    )
}

private fun enabledDisabled(context: Context, value: Boolean): String {
    return context.getString(
        if (value) R.string.appstrings_diagnostic_value_enabled else R.string.appstrings_diagnostic_value_disabled
    )
}

private fun blockedNotificationChannelNames(context: Context): List<String> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
    val manager = context.getSystemService(NotificationManager::class.java) ?: return emptyList()
    return manager.notificationChannels
        .filter { it.importance == NotificationManager.IMPORTANCE_NONE }
        .map { it.name?.toString().orEmpty().ifBlank { it.id } }
        .take(5)
}

private fun databaseSidecars(context: Context, dbName: String): List<File> {
    val candidates = listOf(
        "$dbName-wal",
        "$dbName-shm",
        "$dbName.download",
        "$dbName.backup"
    )
    return candidates
        .map { context.getDatabasePath(it) }
        .filter { it.exists() && it.length() > 0L }
}

private fun directorySizeCapped(root: File?, maxFiles: Int = 400, maxDepth: Int = 2): Long {
    if (root == null || !root.exists()) return 0L
    var total = 0L
    var visited = 0
    fun visit(file: File, depth: Int) {
        if (visited >= maxFiles || depth > maxDepth) return
        visited += 1
        if (file.isFile) {
            total += file.lengthOrZero()
        } else {
            file.listFiles()?.forEach { visit(it, depth + 1) }
        }
    }
    visit(root, 0)
    return total
}

private fun File.lengthOrZero(): Long = if (isFile) length().coerceAtLeast(0L) else 0L

private fun formatBytes(context: Context, bytes: Long): String {
    return Formatter.formatFileSize(context, bytes.coerceAtLeast(0L))
}

private fun formatDateTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun packageInfo(context: Context): PackageInfo? {
    return runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()
}

@Suppress("DEPRECATION")
private fun versionCodeCompat(packageInfo: PackageInfo): Long {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        packageInfo.versionCode.toLong()
    }
}
