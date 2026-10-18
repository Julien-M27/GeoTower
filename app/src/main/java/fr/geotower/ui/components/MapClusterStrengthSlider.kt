package fr.geotower.ui.components

import android.content.Context
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geotower.R
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.PreferenceStores
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapClusterStrengthSlider(
    useOneUi: Boolean,
    modifier: Modifier = Modifier,
    showDescription: Boolean = true
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
    }
    val currentStrength = AppConfig.mapClusterStrength.intValue.coerceIn(0, 100)
    var sliderValue by remember(currentStrength) {
        mutableFloatStateOf(currentStrength.toFloat())
    }
    val displayedStrength = sliderValue.roundToInt().coerceIn(0, 100)

    fun updateStrength(value: Float) {
        sliderValue = value
        AppConfig.mapClusterStrength.intValue = value.roundToInt().coerceIn(0, 100)
    }

    fun persistStrength() {
        val savedStrength = sliderValue.roundToInt().coerceIn(0, 100)
        AppConfig.mapClusterStrength.intValue = savedStrength
        prefs.edit()
            .putInt(AppConfig.PREF_MAP_CLUSTER_STRENGTH, savedStrength)
            .apply()
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.map_clustering_strength_title),
                style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(sizing.spacing(4.dp))
            ) {
                if (displayedStrength != AppConfig.DEFAULT_MAP_CLUSTER_STRENGTH) {
                    IconButton(
                        onClick = {
                            updateStrength(AppConfig.DEFAULT_MAP_CLUSTER_STRENGTH.toFloat())
                            persistStrength()
                        },
                        modifier = Modifier.size(sizing.component(28.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.appstrings_reset_to_default),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(sizing.component(18.dp))
                        )
                    }
                }
                Text(
                    text = "$displayedStrength%",
                    style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (showDescription) {
            Text(
                text = stringResource(R.string.map_clustering_strength_desc),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = sizing.spacing(4.dp))
            )
        }

        Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
        if (useOneUi) {
            Slider(
                value = sliderValue,
                onValueChange = ::updateStrength,
                onValueChangeFinished = ::persistStrength,
                valueRange = 0f..100f,
                steps = 0,
                modifier = Modifier.fillMaxWidth(),
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(sizing.component(24.dp))
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .border(
                                sizing.component(3.dp),
                                MaterialTheme.colorScheme.primary,
                                CircleShape
                            )
                    )
                },
                track = { _ ->
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(sizing.component(14.dp))
                    ) {
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.3f),
                            start = Offset(0f, size.height / 2),
                            end = Offset(size.width, size.height / 2),
                            strokeWidth = sizing.component(14.dp).toPx(),
                            cap = StrokeCap.Round
                        )
                        // Repère du réglage d'usine (100 %)
                        val defaultRatio = AppConfig.DEFAULT_MAP_CLUSTER_STRENGTH / 100f
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
                onValueChange = ::updateStrength,
                onValueChangeFinished = ::persistStrength,
                valueRange = 0f..100f,
                steps = 0,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.map_clustering_strength_low),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.map_clustering_strength_high),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Carte de réglage de la densité de clustering (section Cartographie).
 */
@Composable
fun MapClusterStrengthCard(
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean,
    modifier: Modifier = Modifier,
    showDescription: Boolean = true
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent

    Surface(
        shape = shape,
        border = border,
        color = cardBg,
        modifier = modifier.fillMaxWidth()
    ) {
        MapClusterStrengthSlider(
            useOneUi = useOneUi,
            showDescription = showDescription,
            modifier = Modifier.padding(sizing.spacing(16.dp))
        )
    }
}
