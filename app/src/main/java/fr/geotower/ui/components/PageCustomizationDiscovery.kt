package fr.geotower.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import fr.geotower.R
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.PageCustomizationPrefs
import fr.geotower.utils.PreferenceStores

/**
 * Les trois relais de découverte de « Personnalisation des pages ». Voir
 * [PageCustomizationPrefs] pour le pourquoi et la persistance.
 */

/**
 * Entoure la roue dentée d'une barre du haut et fait apparaître, une seule fois par page, une bulle
 * qui explique à quoi elle sert.
 *
 * La bulle est ancrée sous [anchor] plutôt que centrée à l'écran : l'intérêt est justement de
 * désigner l'icône, pas d'ouvrir une boîte de dialogue de plus.
 */
@Composable
fun PageCustomizationHint(
    page: String,
    onOpenSettings: () -> Unit,
    anchor: @Composable () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var visible by remember(page) { mutableStateOf(false) }

    LaunchedEffect(page) {
        val prefs = context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
        visible = PageCustomizationPrefs.registerVisit(prefs, page)
    }

    Box {
        anchor()
        if (visible) {
            val positionProvider = remember(density) {
                BelowAnchorPositionProvider(
                    gapPx = with(density) { 4.dp.roundToPx() },
                    marginPx = with(density) { 12.dp.roundToPx() }
                )
            }
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { visible = false },
                properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)
            ) {
                PageCustomizationHintBubble(
                    onDismiss = { visible = false },
                    onOpenSettings = {
                        visible = false
                        onOpenSettings()
                    }
                )
            }
        }
    }
}

@Composable
private fun PageCustomizationHintBubble(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val style = LocalGeoTowerUiStyle.current
    val sizing = style.sizing

    Surface(
        shape = style.cardShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = sizing.component(8.dp),
        modifier = Modifier
            .padding(horizontal = sizing.spacing(4.dp))
            .widthIn(max = sizing.component(320.dp))
    ) {
        Column(modifier = Modifier.padding(sizing.spacing(16.dp))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                Text(
                    text = stringResource(R.string.page_customization_hint_title),
                    style = sizing.textStyle(MaterialTheme.typography.titleSmall),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
            Text(
                text = stringResource(R.string.page_customization_hint_body),
                style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.page_customization_hint_dismiss),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                TextButton(onClick = onOpenSettings) {
                    Text(
                        text = stringResource(R.string.page_customization_hint_open),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

/**
 * Place la bulle juste sous l'ancre, alignée sur son bord de fin, et la ramène dans l'écran si elle
 * déborde — la roue dentée est collée au bord droit, une bulle large sortirait sinon du cadre.
 */
private class BelowAnchorPositionProvider(
    private val gapPx: Int,
    private val marginPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(marginPx, maxX)
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        val y = (anchorBounds.bottom + gapPx).coerceAtMost(maxY)
        return IntOffset(x, y)
    }
}

/**
 * Rappel discret en bas d'une page qui défile : ceux qui descendent jusque-là sont justement ceux
 * qui trouvent la page trop longue.
 *
 * N'émet rien si l'utilisateur a coupé le rappel ; le parent utilisant `Arrangement.spacedBy`, ne
 * rien émettre ne laisse aucun trou.
 */
@Composable
fun PageCustomizationFooter(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!PageCustomizationPrefs.footerEnabled.value) return

    val style = LocalGeoTowerUiStyle.current
    val sizing = style.sizing

    Surface(
        onClick = onClick,
        shape = style.blockShape,
        color = if (style.useOneUi) style.bubbleColor else Color.Transparent,
        border = if (style.useOneUi) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(12.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(sizing.component(20.dp))
            )
            Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
            Text(
                text = stringResource(R.string.page_customization_footer),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(sizing.component(20.dp))
            )
        }
    }
}

/**
 * Enveloppe un bloc de page pour que l'appui long ouvre son réglage — le geste que les gens tentent
 * spontanément quand ils veulent faire disparaître quelque chose.
 *
 * Se pose une seule fois autour du `when` qui distribue les blocs, sans toucher aux branches. Pour
 * que ce soit possible sans déformer la page, l'enveloppe porte elle-même l'espacement du parent :
 * elle ajoute [spacing] **sous** son contenu, et disparaît complètement (taille nulle, aucun
 * espacement) quand le bloc est masqué. Le parent passe donc de `Arrangement.spacedBy(spacing)` à
 * `Arrangement.Top`, et les distances rendues restent exactement les mêmes qu'avant — y compris
 * avant le pied de page, puisque l'espacement du dernier bloc visible reste le sien.
 *
 * [spacing] est aussi réappliqué à l'intérieur, pour les blocs qui émettent plusieurs cartes.
 */
@Composable
fun CustomizableBlock(
    blockId: String,
    onCustomize: ((String) -> Unit)?,
    spacing: Dp = 16.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val scaledSpacing = sizing.spacing(spacing)
    val gapPx = with(LocalDensity.current) { scaledSpacing.roundToPx() }

    Layout(
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(scaledSpacing),
                horizontalAlignment = horizontalAlignment,
                content = content
            )
        },
        modifier = Modifier.customizableBlock(blockId, onCustomize)
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints)
        // Un bloc masqué n'émet rien : sa colonne mesure zéro, on ne réserve alors pas l'espacement.
        if (placeable.height == 0) {
            layout(0, 0) {}
        } else {
            layout(placeable.width, placeable.height + gapPx) { placeable.place(0, 0) }
        }
    }
}

/**
 * Détection d'appui long en passe [PointerEventPass.Initial] : une détection classique ne verrait
 * jamais les blocs dont la racine est cliquable, puisque l'enfant consommerait l'appui avant nous.
 * Rien n'est consommé tant que l'appui long n'a pas abouti, donc le défilement et les clics normaux
 * restent intacts ; une fois abouti, la fin du geste est avalée pour que le relâchement ne soit pas
 * pris pour un clic par l'enfant.
 */
@Composable
fun Modifier.customizableBlock(
    blockId: String,
    onCustomize: ((String) -> Unit)?
): Modifier {
    if (onCustomize == null) return this
    val haptic = LocalHapticFeedback.current
    // Les pages passent une lambda recréée à chaque recomposition : la prendre comme clé
    // redémarrerait la détection en plein appui. On ne garde que l'identifiant du bloc.
    val currentOnCustomize by rememberUpdatedState(onCustomize)

    return this.pointerInput(blockId) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var longPressed = false
            try {
                withTimeout(viewConfiguration.longPressTimeoutMillis) {
                    var pointer = down
                    while (pointer.pressed) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        pointer = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeout
                        val travel = (pointer.position - down.position).getDistance()
                        if (travel > viewConfiguration.touchSlop) return@withTimeout
                    }
                }
            } catch (_: PointerEventTimeoutCancellationException) {
                longPressed = true
            }
            if (!longPressed) return@awaitEachGesture

            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            currentOnCustomize(blockId)

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) break
            }
        }
    }
}
