package fr.geotower.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geotower.R
import fr.geotower.data.outages.OutageSourceMode
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig

/**
 * Sélecteur à deux options pour la source des pannes (sites hors service) :
 * 1. Fichiers opérateurs (temps réel)
 * 2. Fichier journalier consolidé (Arcep / data.gouv.fr)
 */
@Composable
fun OutageSourceSelector(
    useOneUi: Boolean,
    modifier: Modifier = Modifier,
    onSourceChanged: (OutageSourceMode) -> Unit = {},
) {
    val context = LocalContext.current
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val currentMode = AppConfig.outageSourceMode.value

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp)),
    ) {
        Text(
            text = stringResource(R.string.outage_source_selection_title),
            style = sizing.textStyle(MaterialTheme.typography.titleSmall),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        OutageSourceItem(
            title = stringResource(R.string.outage_source_operators_title),
            desc = stringResource(R.string.outage_source_operators_desc),
            icon = Icons.Default.FlashOn,
            isSelected = currentMode == OutageSourceMode.OPERATORS,
            useOneUi = useOneUi,
            onClick = {
                AppConfig.setOutageSourceMode(context, OutageSourceMode.OPERATORS)
                onSourceChanged(OutageSourceMode.OPERATORS)
            },
        )

        OutageSourceItem(
            title = stringResource(R.string.outage_source_daily_title),
            desc = stringResource(R.string.outage_source_daily_desc),
            icon = Icons.Default.DateRange,
            isSelected = currentMode == OutageSourceMode.DAILY,
            useOneUi = useOneUi,
            onClick = {
                AppConfig.setOutageSourceMode(context, OutageSourceMode.DAILY)
                onSourceChanged(OutageSourceMode.DAILY)
            },
        )
    }
}

@Composable
private fun OutageSourceItem(
    title: String,
    desc: String,
    icon: ImageVector,
    isSelected: Boolean,
    useOneUi: Boolean,
    onClick: () -> Unit,
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        useOneUi -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        else -> Color.Transparent
    }
    val border = if (!useOneUi && isSelected) {
        BorderStroke(sizing.component(1.dp), MaterialTheme.colorScheme.primary)
    } else null
    val optionShape = if (useOneUi) RoundedCornerShape(sizing.component(16.dp)) else RoundedCornerShape(sizing.component(10.dp))
    val selectedTextColor = if (useOneUi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
    val selectedDescColor = if (useOneUi) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = optionShape,
        color = bgColor,
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(sizing.spacing(12.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) selectedTextColor else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(sizing.component(22.dp)),
            )
            Spacer(Modifier.padding(sizing.spacing(6.dp)))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) selectedTextColor else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(sizing.spacing(2.dp)))
                Text(
                    text = desc,
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = if (isSelected) selectedDescColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isSelected) {
                Spacer(Modifier.padding(sizing.spacing(4.dp)))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = selectedTextColor,
                    modifier = Modifier.size(sizing.component(20.dp)),
                )
            }
        }
    }
}
