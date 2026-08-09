package fr.geotower.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.geotower.R
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.PowerProfile
import kotlinx.coroutines.delay

/** Identifiants des cartes de la section « Base de données » qui participent à l'actualisation. */
object DatabaseRefreshIds {
    const val MOBILE = "mobile"
    const val RADIO = "radio"
    const val ENB = "enb"
    const val OUTAGES = "outages"
    const val LOCAL_BUILD = "local_build"
}

/** Au-delà, une carte muette (réseau qui ne répond jamais) ne doit plus retenir l'indicateur. */
private const val REFRESH_TIMEOUT_MS = 25_000L

/** Queue d'animation : sans elle, une actualisation servie en 50 ms ne ferait que clignoter. */
private const val REFRESH_MIN_VISIBLE_MS = 350L

private const val REFRESH_SPIN_PERIOD_MS = 1300

/**
 * Actualisation de la section « Base de données » des réglages.
 *
 * Chaque carte (base mobile, base radio, base eNB/gNB, génération locale) relit la base installée
 * et réinterroge le manifeste à chaque changement de sa clé d'effet. Cet état partagé leur donne
 * une clé COMMUNE : la bouger relance les quatre d'un coup.
 *
 * L'indicateur ne s'éteint qu'une fois que toutes les cartes réellement composées ont rendu la
 * main, sinon le geste de tirage retomberait avant le retour des versions distantes. Chaque compte
 * rendu porte la clé sur laquelle la carte travaillait : la fin d'une actualisation annulée ne
 * clôt donc pas celle qui vient de démarrer.
 */
@Stable
class DatabaseRefreshState {
    var refreshKey by mutableIntStateOf(0)
        private set
    var isRefreshing by mutableStateOf(false)
        private set

    private val registered = mutableStateListOf<String>()
    private val completed = mutableStateListOf<String>()

    fun refresh() {
        if (isRefreshing) return
        completed.clear()
        refreshKey++
        isRefreshing = true
        settleIfComplete()
    }

    fun reportRefreshed(id: String, key: Int) {
        if (!isRefreshing || key != refreshKey) return
        if (id !in completed) completed.add(id)
        settleIfComplete()
    }

    fun settleNow() {
        isRefreshing = false
    }

    internal fun register(id: String) {
        if (id !in registered) registered.add(id)
    }

    internal fun unregister(id: String) {
        registered.remove(id)
        completed.remove(id)
        settleIfComplete()
    }

    private fun settleIfComplete() {
        if (isRefreshing && registered.all { it in completed }) isRefreshing = false
    }
}

@Composable
fun rememberDatabaseRefreshState(): DatabaseRefreshState = remember { DatabaseRefreshState() }

/**
 * Inscrit la carte auprès de l'actualisation partagée le temps de sa présence à l'écran : une
 * carte masquée (mode d'affichage, section refermée) ne doit pas retenir l'indicateur.
 */
@Composable
fun DatabaseRefreshMembership(state: DatabaseRefreshState?, id: String) {
    if (state == null) return
    DisposableEffect(state, id) {
        state.register(id)
        onDispose { state.unregister(id) }
    }
}

/** Filet de sécurité : coupe l'indicateur si une carte ne rend jamais la main. */
@Composable
fun DatabaseRefreshTimeout(state: DatabaseRefreshState) {
    LaunchedEffect(state.refreshKey, state.isRefreshing) {
        if (!state.isRefreshing) return@LaunchedEffect
        delay(REFRESH_TIMEOUT_MS)
        state.settleNow()
    }
}

/**
 * Indicateur *affiché* : reste visible un court instant après la fin de l'actualisation, pour que
 * les réponses instantanées ne se traduisent pas par un clignotement.
 */
@Composable
fun rememberDatabaseRefreshIndicator(state: DatabaseRefreshState): Boolean {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(state.isRefreshing) {
        if (state.isRefreshing) {
            visible = true
        } else if (visible) {
            delay(REFRESH_MIN_VISIBLE_MS)
            visible = false
        }
    }
    return visible
}

/**
 * Bouton d'actualisation de la section, pour les modes d'affichage où le tirage vers le bas n'est
 * pas utilisable (page unique : la section n'est pas en haut du défilement).
 *
 * [contentDescription] et [clickKey] sont ouverts pour les sections hors réglages qui relisent les
 * mêmes données (les versions de la page « À propos ») : même animation, même anti-clignotement,
 * mais une annonce d'accessibilité qui parle de la bonne section.
 */
@Composable
fun DatabaseSectionRefreshButton(
    state: DatabaseRefreshState,
    modifier: Modifier = Modifier,
    onSafeClick: SafeClick? = null,
    contentDescription: String = stringResource(R.string.settings_database_refresh),
    clickKey: String = "database_section_refresh"
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val safeClick = onSafeClick ?: rememberSafeClick()
    val spinning = rememberDatabaseRefreshIndicator(state)
    val rotation = if (spinning && PowerProfile.richAnimations) {
        val transition = rememberInfiniteTransition(label = "database-refresh-spin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = REFRESH_SPIN_PERIOD_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "database-refresh-spin-value"
        )
        angle
    } else {
        0f
    }

    IconButton(
        onClick = { safeClick(clickKey) { state.refresh() } },
        enabled = !spinning,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(sizing.component(22.dp))
                .graphicsLayer { rotationZ = rotation }
        )
    }
}
