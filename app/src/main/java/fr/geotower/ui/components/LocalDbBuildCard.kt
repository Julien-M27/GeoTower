package fr.geotower.ui.components

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
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

    // Packs a generer : Mobile coche par defaut (cas le plus courant + le plus utile).
    var packMobile by remember { mutableStateOf(true) }
    var packRadioBroadcast by remember { mutableStateOf(false) }
    var packNonMobileTech by remember { mutableStateOf(false) }

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
                    Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))
                    Button(
                        onClick = {
                            safeClick("local_build_start") {
                                LocalDbBuildWorker.enqueue(workManager, packMobile, packRadioBroadcast, packNonMobileTech)
                            }
                        },
                        enabled = !isDownloading && (packMobile || packRadioBroadcast || packNonMobileTech),
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
    Surface(
        color = if (checked) accent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(sizing.component(12.dp)),
        border = if (checked) BorderStroke(1.5.dp, accent.copy(alpha = 0.6f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = sizing.spacing(4.dp))
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
