package fr.geotower.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.decode.DataSource
import fr.geotower.utils.PowerProfile

// --- Animation de chargement des photos, dans l'esprit Material 3 Expressive ---
// 1. conteneur tonal parcouru par un balayage lumineux pendant la requête ;
// 2. LoadingIndicator M3 Expressive (la forme qui se déforme) au centre, dimensionné d'après le
//    conteneur pour rester lisible aussi bien sur une vignette que sur le plein écran ;
// 3. arrivée de la photo en ressort « spatial » expressif (léger dépassement) pendant que le
//    placeholder s'efface.
// En mode faible consommation ([PowerProfile.richAnimations] = false) : pas de balayage, indicateur
// circulaire classique et apparition instantanée.

/** Ressort spatial expressif : la photo dépasse un peu sa taille finale avant de se poser. */
private val PhotoAppearSpring = spring<Float>(
    dampingRatio = 0.55f,
    stiffness = Spring.StiffnessMediumLow
)

private const val PHOTO_APPEAR_START_SCALE = 0.92f
// Alpha de départ volontairement non nul : un calque à alpha 0 n'est jamais dessiné, or c'est le
// dessin qui donne à Coil la taille cible de la requête.
private const val PHOTO_APPEAR_START_ALPHA = 0.2f
private const val PHOTO_SHIMMER_PERIOD_MS = 1400
private const val PHOTO_SHIMMER_BAND_RATIO = 0.55f
private const val PHOTO_INDICATOR_RATIO = 0.30f
private val PHOTO_INDICATOR_MIN = 22.dp
private val PHOTO_INDICATOR_MAX = 48.dp
/** En dessous de cette taille de conteneur, l'indicateur mangerait toute la vignette. */
private val PHOTO_INDICATOR_HIDE_BELOW = 44.dp
private val PHOTO_INDICATOR_UNBOUNDED = 120.dp

/**
 * [Image] asynchrone destinée aux photos : affiche l'animation de chargement expressive tant que le
 * bitmap n'est pas prêt, puis révèle la photo.
 *
 * @param fallback dessin affiché si la requête échoue ou si [model] est nul (schéma de support…).
 * @param containerColor fond du placeholder ; [Color.Transparent] désactive le balayage (viewer plein écran).
 */
@Composable
fun PhotoAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: Painter? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null
) {
    val richAnimations = PowerProfile.richAnimations
    val painter = rememberAsyncImagePainter(
        model = model,
        contentScale = contentScale,
        onSuccess = onSuccess
    )
    val state = painter.state
    val success = state as? AsyncImagePainter.State.Success
    val failed = state is AsyncImagePainter.State.Error ||
        (model == null && state is AsyncImagePainter.State.Empty)

    val appear = remember(model) { Animatable(0f) }
    val revealed by remember(appear) { derivedStateOf { appear.value >= 0.999f } }

    LaunchedEffect(model, success != null, failed) {
        when {
            success != null -> {
                // Photo déjà en cache mémoire (retour en arrière, re-scroll) : pas d'animation,
                // elle doit rester là où l'utilisateur l'a laissée.
                val cached = success.result.dataSource == DataSource.MEMORY_CACHE
                if (cached || !richAnimations) appear.snapTo(1f) else appear.animateTo(1f, PhotoAppearSpring)
            }
            failed -> if (richAnimations) appear.animateTo(1f, tween(160)) else appear.snapTo(1f)
            else -> Unit
        }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val shortestSide = minOf(
            if (constraints.hasBoundedWidth) maxWidth else PHOTO_INDICATOR_UNBOUNDED,
            if (constraints.hasBoundedHeight) maxHeight else PHOTO_INDICATOR_UNBOUNDED
        )

        if (!failed) {
            Image(
                painter = painter,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = appear.value
                        alpha = lerp(PHOTO_APPEAR_START_ALPHA, 1f, progress).coerceIn(0f, 1f)
                        val scale = lerp(PHOTO_APPEAR_START_SCALE, 1f, progress)
                        scaleX = scale
                        scaleY = scale
                    }
            )
        } else if (fallback != null) {
            Image(
                painter = fallback,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
            )
        } else {
            Icon(
                imageVector = Icons.Default.BrokenImage,
                contentDescription = null,
                tint = indicatorColor.copy(alpha = 0.55f),
                modifier = Modifier
                    .size(photoIndicatorSize(shortestSide))
                    .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
            )
        }

        // Le placeholder reste au-dessus et s'efface : la photo est ainsi révélée par un fondu.
        if (!revealed) {
            PhotoLoadingPlaceholder(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = (1f - appear.value).coerceIn(0f, 1f) },
                containerColor = containerColor,
                indicatorColor = indicatorColor,
                indicatorSize = photoIndicatorSize(shortestSide),
                showIndicator = shortestSide >= PHOTO_INDICATOR_HIDE_BELOW,
                animated = richAnimations
            )
        }
    }
}

/**
 * Le placement d'attente d'une photo : conteneur tonal balayé + indicateur expressif.
 * Utilisable seul pour un « squelette » de photo (emplacement réservé avant même la requête).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PhotoLoadingPlaceholder(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    indicatorSize: Dp = 32.dp,
    showIndicator: Boolean = true,
    animated: Boolean = PowerProfile.richAnimations
) {
    // Sur fond transparent (viewer plein écran) le balayage n'aurait rien à éclairer.
    val shimmering = animated && containerColor.alpha > 0.05f
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    Box(
        modifier = modifier
            .background(containerColor)
            .then(if (shimmering) Modifier.photoShimmer(highlight) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (showIndicator) {
            if (animated) {
                LoadingIndicator(
                    modifier = Modifier.size(indicatorSize),
                    color = indicatorColor
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(indicatorSize),
                    color = indicatorColor
                )
            }
        }
    }
}

/** Indicateur proportionnel au conteneur : discret sur une vignette, généreux en plein écran. */
private fun photoIndicatorSize(shortestSide: Dp): Dp =
    (shortestSide.value * PHOTO_INDICATOR_RATIO).dp.coerceIn(PHOTO_INDICATOR_MIN, PHOTO_INDICATOR_MAX)

/** Balayage lumineux traversant le conteneur, redessiné sans recomposition. */
@Composable
private fun Modifier.photoShimmer(highlight: Color): Modifier {
    val transition = rememberInfiniteTransition(label = "photo-shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(PHOTO_SHIMMER_PERIOD_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "photo-shimmer-progress"
    )
    return this.drawWithCache {
        val bandWidth = (size.width * PHOTO_SHIMMER_BAND_RATIO).coerceAtLeast(1f)
        val brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.5f to highlight,
            1f to Color.Transparent,
            startX = 0f,
            endX = bandWidth
        )
        onDrawBehind {
            val travel = (size.width + bandWidth) * progress - bandWidth
            translate(left = travel) {
                drawRect(brush = brush, size = Size(bandWidth, size.height))
            }
        }
    }
}
