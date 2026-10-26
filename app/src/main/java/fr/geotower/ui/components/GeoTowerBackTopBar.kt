package fr.geotower.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import fr.geotower.R
import fr.geotower.ui.theme.LocalGeoTowerUiStyle

@Composable
fun GeoTowerBackTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    backEnabled: Boolean = true,
    showBackButton: Boolean = true,
    actionsWidth: Dp = 48.dp,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    GeoTowerBackTopBar(
        onBack = onBack,
        modifier = modifier,
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        backEnabled = backEnabled,
        showBackButton = showBackButton,
        actionsWidth = actionsWidth,
        navigationIcon = navigationIcon,
        actions = actions
    ) {
        Text(
            text = title,
            style = sizing.textStyle(MaterialTheme.typography.titleLarge),
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun GeoTowerBackTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    backEnabled: Boolean = true,
    showBackButton: Boolean = true,
    actionsWidth: Dp = 48.dp,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    titleContent: @Composable BoxScope.() -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val slotWidth = maxOf(actionsWidth, TOP_BAR_SLOT_MIN_WIDTH)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(vertical = sizing.spacing(2.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(slotWidth),
            contentAlignment = Alignment.CenterStart
        ) {
            if (navigationIcon != null) {
                navigationIcon()
            } else if (showBackButton) {
                IconButton(onClick = onBack, enabled = backEnabled) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.appstrings_back),
                        tint = contentColor
                    )
                }
            }
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
            content = titleContent
        )
        if (actions == null) {
            Spacer(Modifier.width(slotWidth))
        } else {
            Row(
                modifier = Modifier.width(slotWidth),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

/** Zone tactile d'un IconButton Material : plancher des deux reserves laterales de la barre. */
private val TOP_BAR_SLOT_MIN_WIDTH = 48.dp
