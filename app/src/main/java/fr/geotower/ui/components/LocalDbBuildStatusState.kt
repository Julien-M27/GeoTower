package fr.geotower.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.work.WorkManager
import fr.geotower.data.db.LocalDbBuildStatus
import fr.geotower.data.workers.LocalDbBuildWorker

/**
 * Observe la generation locale de base en cours ([LocalDbBuildStatus]) : chaque ecran qui parle
 * d'une base « manquante » s'en sert pour dire « generation en cours » a la place.
 */
@Composable
fun rememberLocalDbBuildStatus(): LocalDbBuildStatus {
    val context = LocalContext.current
    val workManager = remember { WorkManager.getInstance(context) }
    val infos by workManager
        .getWorkInfosForUniqueWorkFlow(LocalDbBuildWorker.UNIQUE_WORK_NAME)
        .collectAsState(initial = emptyList())
    return LocalDbBuildStatus.fromWorkInfos(infos)
}
