package fr.geotower.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.geotower.ui.theme.LocalGeoTowerUiStyle

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GeoTowerLoadingMessage(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    indicatorColor: Color = MaterialTheme.colorScheme.primary
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Column(
        modifier = modifier.padding(horizontal = sizing.spacing(32.dp)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LoadingIndicator(color = indicatorColor)
        Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))
        Text(
            text = title,
            style = sizing.textStyle(MaterialTheme.typography.titleSmall),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(sizing.spacing(4.dp)))
        Text(
            text = detail,
            style = sizing.textStyle(MaterialTheme.typography.bodySmall),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
