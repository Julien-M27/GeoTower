package fr.geotower.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import fr.geotower.R
import fr.geotower.data.build.BuildPhase
import fr.geotower.data.build.labelRes
import fr.geotower.data.build.LocalBuildCapability
import fr.geotower.data.workers.DatabaseDownloadWorker
import fr.geotower.data.workers.LocalDbBuildWorker
import fr.geotower.ui.theme.LocalGeoTowerUiStyle

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
) {
    val context = LocalContext.current
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val workManager = remember { WorkManager.getInstance(context) }
    val safeClick = onSafeClick ?: rememberSafeClick()

    val buildInfos by workManager
        .getWorkInfosForUniqueWorkFlow(LocalDbBuildWorker.UNIQUE_WORK_NAME)
        .collectAsState(initial = emptyList())
    val downloadInfos by workManager
        .getWorkInfosForUniqueWorkFlow(DatabaseDownloadWorker.UNIQUE_WORK_NAME)
        .collectAsState(initial = emptyList())

    val currentBuild = buildInfos.firstOrNull()
    val isBuilding = currentBuild?.state == WorkInfo.State.RUNNING || currentBuild?.state == WorkInfo.State.ENQUEUED
    val downloadState = downloadInfos.firstOrNull()?.state
    val isDownloading = downloadState == WorkInfo.State.RUNNING || downloadState == WorkInfo.State.ENQUEUED
    val progress = (currentBuild?.progress?.getInt(LocalDbBuildWorker.KEY_PROGRESS, 0) ?: 0) / 100f

    val eligibility = remember { LocalBuildCapability.evaluate(context) }

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
                !eligibility.eligible -> {
                    Text(
                        text = stringResource(R.string.appstrings_local_build_unavailable),
                        style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                isBuilding -> {
                    LinearWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(sizing.component(8.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
                    val phaseOrdinal = currentBuild?.progress?.getInt(LocalDbBuildWorker.KEY_PHASE, -1) ?: -1
                    val phaseText = BuildPhase.values().getOrNull(phaseOrdinal)
                        ?.let { stringResource(it.labelRes()) }
                        ?: stringResource(R.string.appstrings_local_build_running)
                    Text(
                        text = "$phaseText ${(progress * 100).toInt()}%",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    // Detail « live » de l'etape en cours (compteur de lignes) : evite l'impression de
                    // blocage quand le % de phase reste fige pendant un long calcul.
                    val phaseDetail = currentBuild?.progress?.getString(LocalDbBuildWorker.KEY_DETAIL).orEmpty()
                    if (phaseDetail.isNotBlank()) {
                        Text(
                            text = phaseDetail,
                            style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                    Button(
                        onClick = {
                            safeClick("local_build_start") {
                                LocalDbBuildWorker.enqueue(workManager)
                            }
                        },
                        enabled = !isDownloading,
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
                            text = stringResource(R.string.appstrings_local_build_action),
                            fontWeight = FontWeight.Bold,
                            style = sizing.textStyle(MaterialTheme.typography.labelLarge),
                        )
                    }
                }
            }
        }
    }
}
