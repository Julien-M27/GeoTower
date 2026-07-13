package fr.geotower.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import fr.geotower.R
import fr.geotower.data.db.DbOperationTimings
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import kotlinx.coroutines.delay

/**
 * Ligne « temps » d'une operation de base ([DbOperationTimings]) :
 *  - pendant l'operation ([running] vrai) : un chrono qui defile (rafraichi chaque seconde) ;
 *  - sinon : la duree de la derniere operation reussie, libellee « Genere en » ou « Telecharge en »
 *    selon [downloaded].
 *
 * N'affiche rien tant qu'il n'y a aucune duree a montrer (jamais d'operation). Le debut du chrono
 * est lu depuis les prefs a chaque tick : il reste donc fidele apres un aller-retour dans l'ecran.
 */
@Composable
fun DbOperationTimingText(
    timingKey: String,
    running: Boolean,
    downloaded: Boolean,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    val context = LocalContext.current
    val sizing = LocalGeoTowerUiStyle.current.sizing

    var elapsedMs by remember(timingKey) { mutableStateOf<Long?>(null) }
    var finalMs by remember(timingKey) { mutableStateOf<Long?>(null) }

    LaunchedEffect(timingKey, running) {
        if (running) {
            finalMs = null
            while (true) {
                val start = DbOperationTimings.readStartTime(context, timingKey)
                elapsedMs = if (start != null) (System.currentTimeMillis() - start).coerceAtLeast(0L) else 0L
                delay(1000)
            }
        } else {
            elapsedMs = null
            finalMs = DbOperationTimings.readDurationMs(context, timingKey)
        }
    }

    val label = when {
        running -> elapsedMs?.let {
            stringResource(R.string.appstrings_db_time_elapsed, DbOperationTimings.formatDuration(it))
        }
        finalMs != null -> {
            val duration = DbOperationTimings.formatDuration(finalMs!!)
            if (downloaded) {
                stringResource(R.string.appstrings_db_time_downloaded, duration)
            } else {
                stringResource(R.string.appstrings_db_time_generated, duration)
            }
        }
        else -> null
    } ?: return

    Text(
        text = label,
        modifier = modifier,
        fontSize = sizing.text(12.sp),
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
    )
}
