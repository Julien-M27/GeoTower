package fr.geotower.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.annotation.StringRes
import fr.geotower.R
import fr.geotower.data.build.BuildPhase
import fr.geotower.data.build.BuildImportType
import fr.geotower.data.build.labelRes
import fr.geotower.data.build.LocalBuildCapability
import fr.geotower.data.db.DbOperationTimings
import fr.geotower.data.db.LocalDbProvenance
import fr.geotower.data.workers.DatabaseDownloadWorker
import fr.geotower.data.workers.LocalDbBuildWorker
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Carte de reglage "avancee" : generation locale de la base (appareils performants). Opt-in,
 * visible pour tous mais action desactivee si l'appareil n'est pas eligible (RAM/stockage) ou
 * si un telechargement de base est en cours (exclusion mutuelle).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LocalDbBuildCard(
    useOneUi: Boolean,
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    onSafeClick: SafeClick? = null,
    refreshState: DatabaseRefreshState? = null,
) {
    val context = LocalContext.current
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val workManager = remember { WorkManager.getInstance(context) }
    val safeClick = onSafeClick ?: rememberSafeClick()

    val buildInfos by workManager
        .getWorkInfosForUniqueWorkFlow(LocalDbBuildWorker.UNIQUE_WORK_NAME)
        .collectAsState(initial = emptyList())
    val downloadInfos by workManager
        .getWorkInfosByTagFlow(DatabaseDownloadWorker.WORK_TAG)
        .collectAsState(initial = emptyList())

    val currentBuild = buildInfos.firstOrNull()
    val isBuilding = currentBuild?.state == WorkInfo.State.RUNNING || currentBuild?.state == WorkInfo.State.ENQUEUED
    val buildWasCancelled = currentBuild?.state == WorkInfo.State.CANCELLED
    val buildFailureReason = currentBuild
        ?.takeIf { it.state == WorkInfo.State.FAILED }
        ?.outputData
        ?.getString(LocalDbBuildWorker.KEY_ERROR)
        ?.takeIf { it.isNotBlank() }
    val downloadState = downloadInfos.firstOrNull { workInfo ->
        workInfo.state == WorkInfo.State.RUNNING ||
            workInfo.state == WorkInfo.State.ENQUEUED ||
            workInfo.state == WorkInfo.State.BLOCKED
    }?.state
    val isDownloading = downloadState != null
    val progress = (currentBuild?.progress?.getInt(LocalDbBuildWorker.KEY_PROGRESS, 0) ?: 0) / 100f
    val currentImport = currentBuild?.progress
        ?.getInt(LocalDbBuildWorker.KEY_IMPORT, -1)
        ?.let { BuildImportType.values().getOrNull(it) }
    val currentFileName = currentBuild?.progress
        ?.getString(LocalDbBuildWorker.KEY_FILE)
        ?.takeIf { it.isNotBlank() }
    val downloadedBytes = currentBuild?.progress
        ?.getLong(LocalDbBuildWorker.KEY_DOWNLOADED_BYTES, 0L)
        ?: 0L
    val totalBytes = currentBuild?.progress
        ?.getLong(LocalDbBuildWorker.KEY_TOTAL_BYTES, -1L)
        ?: -1L

    // Packs a generer : Mobile coche par defaut (cas le plus courant + le plus utile).
    var packMobile by remember { mutableStateOf(true) }
    var packRadioBroadcast by remember { mutableStateOf(false) }
    var packNonMobileTech by remember { mutableStateOf(false) }

    // L'eligibilite depend des packs COCHES : le stockage necessaire va du simple au double entre
    // « mobile seul » et « tout », alors que la memoire, elle, ne bouge presque pas. Un appareil
    // trop juste pour tout generer peut donc tres bien generer le pack mobile.
    val eligibility = remember(packMobile, packRadioBroadcast, packNonMobileTech) {
        LocalBuildCapability.evaluate(context, packMobile, packRadioBroadcast || packNonMobileTech)
    }
    // « Tenter quand meme » : un appareil sous les budgets mesures n'est pas forcement incapable —
    // ils portent une marge, et un echec ne coute que du temps et des donnees (la base installee
    // n'est jamais touchee, le build vit dans un fichier a part).
    var forceBuild by remember { mutableStateOf(false) }

    // Provenance des bases installees : distingue « generee sur l'appareil » de « telechargee » et
    // fournit l'horodatage (metadata.version) du dernier build local. Re-lue a la fin de chaque build.
    var mobileInfo by remember { mutableStateOf(LocalDbProvenance.Info.NONE) }
    var radioInfo by remember { mutableStateOf(LocalDbProvenance.Info.NONE) }
    // Actualisation de toute la section « Base de données » : clé partagée par les quatre cartes.
    val sectionRefreshKey = refreshState?.refreshKey ?: 0
    DatabaseRefreshMembership(refreshState, DatabaseRefreshIds.LOCAL_BUILD)

    LaunchedEffect(isBuilding, sectionRefreshKey) {
        withContext(Dispatchers.IO) {
            mobileInfo = LocalDbProvenance.readMobile(context)
            radioInfo = LocalDbProvenance.readRadio(context)
        }
        refreshState?.reportRefreshed(DatabaseRefreshIds.LOCAL_BUILD, sectionRefreshKey)
    }

    Surface(
        shape = shape,
        border = border,
        color = if (useOneUi) bubbleColor else Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(sizing.spacing(16.dp))) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(8.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(sizing.component(22.dp)),
                )
                Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
                Text(
                    text = stringResource(R.string.appstrings_local_build_title),
                    fontWeight = FontWeight.Bold,
                    style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                )
            }

            Text(
                text = stringResource(R.string.appstrings_local_build_desc),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))

            when {
                isBuilding -> {
                    Text(
                        text = stringResource(
                            R.string.appstrings_local_build_overall_progress,
                            (progress * 100).toInt().coerceIn(0, 100),
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(sizing.spacing(6.dp)))
                    LinearWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(sizing.component(8.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
                    val phaseOrdinal = currentBuild.progress.getInt(LocalDbBuildWorker.KEY_PHASE, -1)
                    val currentPhase = BuildPhase.values().getOrNull(phaseOrdinal)
                    val phaseText = currentPhase
                        ?.let { stringResource(it.labelRes()) }
                        ?: stringResource(R.string.appstrings_local_build_running)
                    Text(
                        text = stringResource(R.string.appstrings_local_build_current_step, phaseText),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    val phaseDetail = currentBuild.progress.getString(LocalDbBuildWorker.KEY_DETAIL).orEmpty()
                    if (currentImport != null) {
                        val fileProgress = if (totalBytes > 0L) {
                            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        Spacer(modifier = Modifier.height(sizing.spacing(10.dp)))
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(sizing.component(12.dp)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(sizing.spacing(12.dp))) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(sizing.component(22.dp)),
                                    )
                                    Spacer(modifier = Modifier.width(sizing.spacing(10.dp)))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.appstrings_local_build_download_in_progress),
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = stringResource(currentImport.labelRes()),
                                            fontWeight = FontWeight.Bold,
                                            style = sizing.textStyle(MaterialTheme.typography.bodyLarge),
                                        )
                                        currentFileName?.let { fileName ->
                                            Text(
                                                text = stringResource(R.string.appstrings_local_build_download_file, fileName),
                                                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(sizing.spacing(10.dp)))
                                if (totalBytes > 0L) {
                                    LinearWavyProgressIndicator(
                                        progress = { fileProgress },
                                        modifier = Modifier.fillMaxWidth().height(sizing.component(6.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(sizing.spacing(6.dp)))
                                    Text(
                                        text = stringResource(
                                            R.string.appstrings_local_build_download_size,
                                            Formatter.formatShortFileSize(context, downloadedBytes),
                                            Formatter.formatShortFileSize(context, totalBytes),
                                        ),
                                        style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    Text(
                                        text = stringResource(
                                            R.string.appstrings_local_build_download_size_unknown,
                                            Formatter.formatShortFileSize(context, downloadedBytes),
                                        ),
                                        style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else if (phaseDetail.isNotBlank()) {
                        // Detail « live » de l'etape en cours (compteur de lignes) : evite l'impression
                        // de blocage quand le % de phase reste fige pendant un long calcul.
                        Text(
                            text = phaseDetail,
                            style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LocalBuildStepList(
                        steps = localBuildSteps(currentBuild.tags),
                        currentPhase = currentPhase ?: BuildPhase.RESOLVING,
                        overallPercent = (progress * 100f).toInt().coerceIn(0, 100),
                        detail = phaseDetail.takeUnless { currentImport != null },
                    )
                    // Chrono live du temps de generation en cours.
                    DbOperationTimingText(
                        timingKey = DbOperationTimings.LOCAL_BUILD,
                        running = true,
                        downloaded = false,
                    )
                    Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))
                    OutlinedButton(
                        onClick = {
                            safeClick("local_build_cancel") {
                                workManager.cancelUniqueWork(LocalDbBuildWorker.UNIQUE_WORK_NAME)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = sizing.component(50.dp)),
                        shape = RoundedCornerShape(sizing.component(12.dp)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(sizing.component(22.dp)))
                        Spacer(Modifier.width(sizing.spacing(8.dp)))
                        Text(
                            text = stringResource(R.string.database_cancel_download),
                            fontWeight = FontWeight.Bold,
                            style = sizing.textStyle(MaterialTheme.typography.labelLarge),
                        )
                    }
                }

                else -> {
                    if (buildWasCancelled || currentBuild?.state == WorkInfo.State.FAILED) {
                        val lastPhase = currentBuild.progress
                            .getInt(LocalDbBuildWorker.KEY_PHASE, -1)
                            .let { BuildPhase.values().getOrNull(it) }
                        val lastDetail = currentBuild.progress
                            .getString(LocalDbBuildWorker.KEY_DETAIL)
                            ?.takeIf { it.isNotBlank() }
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(sizing.component(12.dp)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(12.dp)),
                        ) {
                            Column(modifier = Modifier.padding(sizing.spacing(12.dp))) {
                                Text(
                                    text = when {
                                        buildWasCancelled -> stringResource(R.string.appstrings_local_build_cancelled_state)
                                        buildFailureReason != null -> stringResource(
                                            R.string.appstrings_local_build_notif_failed,
                                            buildFailureReason,
                                        )
                                        else -> stringResource(R.string.appstrings_local_build_unexpected_stop)
                                    },
                                    fontWeight = FontWeight.Bold,
                                    style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                                    color = MaterialTheme.colorScheme.error,
                                )
                                lastPhase?.let { phase ->
                                    Text(
                                        text = stringResource(
                                            R.string.appstrings_local_build_last_step,
                                            stringResource(phase.labelRes()),
                                        ),
                                        style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                lastDetail?.let { detail ->
                                    Text(
                                        text = detail,
                                        style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    // Recap « genere sur ce telephone » : quelle base a ete generee localement, et quand
                    // (horodatage = metadata.version du dernier build local encore installe).
                    val generatedMobile = mobileInfo.takeIf { it.locallyBuilt }
                        ?.let { LocalDbProvenance.formatBuildTime(it.buildVersionRaw) }
                    val generatedRadio = radioInfo.takeIf { it.locallyBuilt }
                        ?.let { LocalDbProvenance.formatBuildTime(it.buildVersionRaw) }
                    if (generatedMobile != null || generatedRadio != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(sizing.component(12.dp)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(12.dp)),
                        ) {
                            Column(modifier = Modifier.padding(sizing.spacing(12.dp))) {
                                Text(
                                    text = stringResource(R.string.appstrings_local_build_generated_title),
                                    fontWeight = FontWeight.Bold,
                                    style = sizing.textStyle(MaterialTheme.typography.labelLarge),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (generatedMobile != null) {
                                    Text(
                                        text = stringResource(R.string.appstrings_local_build_generated_mobile, generatedMobile),
                                        style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (generatedRadio != null) {
                                    Text(
                                        text = stringResource(R.string.appstrings_local_build_generated_radio, generatedRadio),
                                        style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                // Duree de la derniere generation locale reussie.
                                DbOperationTimingText(
                                    timingKey = DbOperationTimings.LOCAL_BUILD,
                                    running = false,
                                    downloaded = false,
                                )
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.appstrings_local_build_packs_title),
                        fontWeight = FontWeight.Bold,
                        style = sizing.textStyle(MaterialTheme.typography.titleSmall),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = sizing.spacing(8.dp)),
                    )
                    PackOption(
                        checked = packMobile,
                        icon = Icons.Default.CellTower,
                        label = stringResource(R.string.appstrings_local_build_pack_mobile),
                        subtitle = stringResource(R.string.appstrings_local_build_pack_mobile_desc),
                    ) { packMobile = !packMobile }
                    PackOption(
                        checked = packRadioBroadcast,
                        icon = Icons.Default.Radio,
                        label = stringResource(R.string.appstrings_local_build_pack_radio),
                        subtitle = stringResource(R.string.appstrings_local_build_pack_radio_desc),
                    ) { packRadioBroadcast = !packRadioBroadcast }
                    PackOption(
                        checked = packNonMobileTech,
                        icon = Icons.Default.SettingsInputAntenna,
                        label = stringResource(R.string.appstrings_local_build_pack_nonmobile),
                        subtitle = stringResource(R.string.appstrings_local_build_pack_nonmobile_desc),
                    ) { packNonMobileTech = !packNonMobileTech }
                    Text(
                        text = stringResource(R.string.appstrings_local_build_packs_hint),
                        style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = sizing.spacing(8.dp)),
                    )
                    // Un pack coche dont la base est deja installee (telechargee OU generee) sera ecrase :
                    // le bouton previent alors du remplacement.
                    val willReplace = (packMobile && mobileInfo.installed) ||
                        ((packRadioBroadcast || packNonMobileTech) && radioInfo.installed)

                    Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))
                    if (eligibility.eligible) {
                        // Cout annonce AVANT de lancer : c'est un budget mesure, pas une estimation.
                        Text(
                            text = stringResource(
                                R.string.appstrings_local_build_cost,
                                Formatter.formatShortFileSize(context, eligibility.required.storageBytes),
                            ),
                            style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.appstrings_local_build_unavailable),
                            fontWeight = FontWeight.Bold,
                            style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                            color = MaterialTheme.colorScheme.error,
                        )
                        // Chiffres explicites : l'utilisateur voit ce qui manque, et qu'un pack de
                        // moins peut suffire.
                        if (eligibility.heapLimitBytes < eligibility.required.heapBytes) {
                            Text(
                                text = stringResource(
                                    R.string.appstrings_local_build_need_memory,
                                    Formatter.formatShortFileSize(context, eligibility.heapLimitBytes),
                                    Formatter.formatShortFileSize(context, eligibility.required.heapBytes),
                                ),
                                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (eligibility.freeStorageBytes < eligibility.required.storageBytes) {
                            Text(
                                text = stringResource(
                                    R.string.appstrings_local_build_need_storage,
                                    Formatter.formatShortFileSize(context, eligibility.freeStorageBytes),
                                    Formatter.formatShortFileSize(context, eligibility.required.storageBytes),
                                ),
                                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!forceBuild) {
                            TextButton(onClick = { safeClick("local_build_force") { forceBuild = true } }) {
                                Text(
                                    text = stringResource(R.string.appstrings_local_build_try_anyway),
                                    style = sizing.textStyle(MaterialTheme.typography.labelLarge),
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.appstrings_local_build_try_anyway_warning),
                                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = sizing.spacing(4.dp)),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))
                    Button(
                        onClick = {
                            safeClick("local_build_start") {
                                LocalDbBuildWorker.enqueue(
                                    workManager, packMobile, packRadioBroadcast, packNonMobileTech,
                                    force = forceBuild,
                                )
                            }
                        },
                        enabled = !isDownloading &&
                            (packMobile || packRadioBroadcast || packNonMobileTech) &&
                            (eligibility.eligible || forceBuild),
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = sizing.component(56.dp)),
                        shape = RoundedCornerShape(sizing.component(12.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(sizing.component(22.dp)))
                        Spacer(Modifier.width(sizing.spacing(8.dp)))
                        Text(
                            text = stringResource(
                                if (willReplace) R.string.appstrings_local_build_replace_action
                                else R.string.appstrings_local_build_action,
                            ),
                            fontWeight = FontWeight.Bold,
                            style = sizing.textStyle(MaterialTheme.typography.labelLarge),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Liste lisible du pipeline local. Le pourcentage principal reste celui du pipeline global ; les
 * lignes indiquent sans ambiguite ce qui est deja termine, ce qui tourne et ce qui attend. Les
 * trois passages historiquement regroupes sous READING_SUPPORTS sont separes ici, car ils n'ont
 * ni le meme cout ni le meme diagnostic (sources SUP, antennes, puis adresses des supports).
 */
@Composable
private fun LocalBuildStepList(
    steps: List<LocalBuildStepSpec>,
    currentPhase: BuildPhase,
    overallPercent: Int,
    detail: String?,
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val activeIndex = localBuildActiveStepIndex(steps, currentPhase, overallPercent)
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(sizing.component(12.dp)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(sizing.spacing(12.dp))) {
            Text(
                text = stringResource(R.string.appstrings_local_build_steps_title),
                fontWeight = FontWeight.Bold,
                style = sizing.textStyle(MaterialTheme.typography.labelLarge),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))

            steps.forEachIndexed { index, step ->
                val completed = index < activeIndex
                val active = index == activeIndex
                val icon = when {
                    completed -> Icons.Default.CheckCircle
                    active -> Icons.Default.PlayArrow
                    else -> Icons.Default.Circle
                }
                val iconTint = when {
                    completed -> MaterialTheme.colorScheme.tertiary
                    active -> accent
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = sizing.spacing(4.dp)),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(sizing.component(20.dp)),
                    )
                    Spacer(modifier = Modifier.width(sizing.spacing(10.dp)))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(step.labelRes),
                                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                color = if (active) accent else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                            Text(
                                text = when {
                                    completed -> stringResource(R.string.appstrings_local_build_step_done)
                                    active -> stringResource(R.string.appstrings_local_build_step_active)
                                    else -> stringResource(R.string.appstrings_local_build_step_pending)
                                },
                                style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                                color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (active && !detail.isNullOrBlank()) {
                            Text(
                                text = detail,
                                style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class LocalBuildStepSpec(
    val phase: BuildPhase,
    @get:StringRes val labelRes: Int,
)

private fun localBuildSteps(tags: Set<String>): List<LocalBuildStepSpec> {
    // WorkInfo tags identify the selected packs before the first progress update. Le repli mobile
    // conserve un affichage utile pour les anciens travaux créés avant l'ajout de ces tags.
    val hasKnownPackTag = tags.any {
        it == LocalDbBuildWorker.TAG_PACK_MOBILE || it == LocalDbBuildWorker.TAG_PACK_RADIO
    }
    val hasMobile = !hasKnownPackTag || LocalDbBuildWorker.TAG_PACK_MOBILE in tags
    val hasRadio = LocalDbBuildWorker.TAG_PACK_RADIO in tags
    return buildList {
        add(LocalBuildStepSpec(BuildPhase.RESOLVING, R.string.appstrings_local_build_phase_resolving))
        add(LocalBuildStepSpec(BuildPhase.DOWNLOADING, R.string.appstrings_local_build_phase_downloading))
        if (hasMobile) {
            add(LocalBuildStepSpec(BuildPhase.READING_STATIONS, R.string.appstrings_local_build_phase_reading))
            add(LocalBuildStepSpec(BuildPhase.READING_SUPPORTS, R.string.appstrings_local_build_step_reading_sup_sources))
            add(LocalBuildStepSpec(BuildPhase.COMPUTING_FREQUENCIES, R.string.appstrings_local_build_phase_processing))
            add(LocalBuildStepSpec(BuildPhase.READING_SUPPORTS, R.string.appstrings_local_build_step_reading_antenna_sources))
            add(LocalBuildStepSpec(BuildPhase.COMPUTING_ANTENNAS, R.string.appstrings_local_build_phase_antennas))
            add(LocalBuildStepSpec(BuildPhase.READING_SUPPORTS, R.string.appstrings_local_build_step_reading_support_sources))
            add(LocalBuildStepSpec(BuildPhase.BUILDING_DETAILS, R.string.appstrings_local_build_phase_details))
            add(LocalBuildStepSpec(BuildPhase.INSERTING, R.string.appstrings_local_build_phase_inserting))
            add(LocalBuildStepSpec(BuildPhase.COMPUTING_STATS, R.string.appstrings_local_build_phase_stats))
            add(LocalBuildStepSpec(BuildPhase.FINALIZING, R.string.appstrings_local_build_phase_finalizing))
        }
        if (hasMobile) {
            add(LocalBuildStepSpec(BuildPhase.INSTALLING, R.string.appstrings_local_build_phase_installing))
        }
        if (hasRadio) {
            add(LocalBuildStepSpec(BuildPhase.RADIO_BUILDING, R.string.appstrings_local_build_phase_radio))
        }
        // En mode radio seul, l'installation arrive après RADIO_BUILDING. En mode mobile, elle a
        // déjà été ajoutée juste avant la génération radio éventuelle.
        if (!hasMobile) {
            add(LocalBuildStepSpec(BuildPhase.INSTALLING, R.string.appstrings_local_build_phase_installing))
        }
    }
}

private fun localBuildActiveStepIndex(
    steps: List<LocalBuildStepSpec>,
    currentPhase: BuildPhase,
    overallPercent: Int,
): Int {
    val matching = steps.indices.filter { steps[it].phase == currentPhase }
    return matching.lastOrNull { index ->
        // Les occurrences répétées de READING_SUPPORTS sont désambiguïsées par la progression
        // globale : 1er passage avant 62 %, 2e avant 70 %, 3e avant BUILDING_DETAILS.
        when (matching.size) {
            1 -> true
            else -> when {
                overallPercent >= 70 -> index == matching.last()
                overallPercent >= 62 -> index == matching.getOrNull(1)
                else -> index == matching.first()
            }
        }
    } ?: steps.indexOfFirst { it.phase == currentPhase }.coerceAtLeast(0)
}

/** Option de pack : icone + libelle + sous-titre + case, dans une surface cliquable surlignee si cochee. */
@Composable
private fun PackOption(
    checked: Boolean,
    icon: ImageVector,
    label: String,
    subtitle: String,
    onToggle: () -> Unit,
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val accent = MaterialTheme.colorScheme.primary
    val optionShape = RoundedCornerShape(sizing.component(12.dp))
    Surface(
        color = if (checked) accent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = optionShape,
        border = if (checked) BorderStroke(1.5.dp, accent.copy(alpha = 0.6f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = sizing.spacing(4.dp))
            .clip(optionShape)
            .clickable { onToggle() },
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = sizing.spacing(12.dp),
                vertical = sizing.spacing(10.dp),
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(sizing.component(26.dp)),
            )
            Spacer(Modifier.width(sizing.spacing(12.dp)))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontWeight = FontWeight.SemiBold,
                    style = sizing.textStyle(MaterialTheme.typography.bodyLarge),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(sizing.spacing(8.dp)))
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}
