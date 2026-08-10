package fr.geotower.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geotower.R
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import kotlin.math.roundToInt

/**
 * Traduit un niveau de zoom en repère parlant : « 16 » ne dit rien, « Rue » si.
 * Les paliers suivent ce que l'on voit réellement sur les fonds OSM / IGN.
 */
@Composable
internal fun zoomScaleLabel(zoom: Int): String = stringResource(
    when {
        zoom <= 11 -> R.string.settings_map_location_zoom_scale_region
        zoom <= 13 -> R.string.settings_map_location_zoom_scale_city
        zoom <= 15 -> R.string.settings_map_location_zoom_scale_district
        zoom <= 17 -> R.string.settings_map_location_zoom_scale_street
        else -> R.string.settings_map_location_zoom_scale_building
    }
)

/**
 * Curseur nu du zoom appliqué au recentrage sur la position (titre, valeur, piste, extrêmes) :
 * c'est à l'appelant de fournir le conteneur, les réglages et le panneau de la carte n'ayant pas
 * la même langue visuelle.
 *
 * @param showDescription la phrase d'explication, utile dans les réglages, superflue dans le
 * panneau de la carte où les autres entrées n'en ont pas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapLocationZoomSlider(
    zoomLevel: Int,
    onZoomLevelChange: (Int) -> Unit,
    useOneUi: Boolean,
    modifier: Modifier = Modifier,
    showDescription: Boolean = true
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val minZoom = AppConfig.MIN_MAP_LOCATION_ZOOM
    val maxZoom = AppConfig.MAX_MAP_LOCATION_ZOOM
    var sliderValue by remember(zoomLevel) { mutableFloatStateOf(zoomLevel.toFloat()) }
    val displayZoom = sliderValue.roundToInt().coerceIn(minZoom, maxZoom)
    // Un cran par niveau de zoom : entre deux niveaux entiers il n'y a rien à voir, le curseur
    // s'aimante donc plutôt que de laisser choisir un 16,4 qui serait arrondi en douce.
    val steps = (maxZoom - minZoom - 1).coerceAtLeast(0)
    val commit = { onZoomLevelChange(sliderValue.roundToInt().coerceIn(minZoom, maxZoom)) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.settings_map_location_zoom_title),
                style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$displayZoom · ${zoomScaleLabel(displayZoom)}",
                style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (showDescription) {
            Text(
                stringResource(R.string.settings_map_location_zoom_desc),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))

        if (useOneUi) {
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = commit,
                valueRange = minZoom.toFloat()..maxZoom.toFloat(),
                steps = steps,
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(sizing.component(24.dp))
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .border(sizing.component(3.dp), MaterialTheme.colorScheme.primary, CircleShape)
                    )
                },
                track = { _ ->
                    Canvas(modifier = Modifier.fillMaxWidth().height(sizing.component(14.dp))) {
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.3f),
                            start = Offset(0f, size.height / 2),
                            end = Offset(size.width, size.height / 2),
                            strokeWidth = sizing.component(14.dp).toPx(),
                            cap = StrokeCap.Round
                        )
                        // Repère du réglage d'usine, pour retrouver le zoom d'origine à l'œil.
                        val defaultRatio =
                            (AppConfig.DEFAULT_MAP_LOCATION_ZOOM - minZoom).toFloat() / (maxZoom - minZoom).toFloat()
                        drawCircle(
                            color = Color.Gray.copy(alpha = 0.6f),
                            radius = sizing.component(4.dp).toPx(),
                            center = Offset(size.width * defaultRatio, size.height / 2)
                        )
                    }
                }
            )
        } else {
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = commit,
                valueRange = minZoom.toFloat()..maxZoom.toFloat(),
                steps = steps
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                zoomScaleLabel(minZoom),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                zoomScaleLabel(maxZoom),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Le même curseur habillé en carte de réglages (section Cartographie).
 */
@Composable
fun MapLocationZoomCard(
    zoomLevel: Int,
    onZoomLevelChange: (Int) -> Unit,
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent

    Surface(shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
        MapLocationZoomSlider(
            zoomLevel = zoomLevel,
            onZoomLevelChange = onZoomLevelChange,
            useOneUi = useOneUi,
            modifier = Modifier.padding(sizing.spacing(16.dp))
        )
    }
}
