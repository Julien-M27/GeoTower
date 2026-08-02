package fr.geotower.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geotower.R
import fr.geotower.ui.theme.LocalGeoTowerUiStyle

/**
 * Attribution cliquable du partenaire eNB-Analytics : les identifiants eNB/gNB ne viennent pas de
 * l'ANFR, la source doit donc etre creditee partout ou la donnee est lue ou telechargee.
 *
 * Le clic ouvre [EnbAboutDialog] (description du projet + lien vers le site). Utilise par la fiche
 * site ([SiteNetworkIdsBlock]) et par la carte de telechargement ([EnbDatabaseDownloadCard]).
 */
@Composable
fun EnbSourceAttribution(
    dialogShape: Shape,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.appstrings_site_network_ids_source),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.End,
    textStyle: TextStyle = LocalGeoTowerUiStyle.current.sizing.textStyle(MaterialTheme.typography.labelSmall),
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    var showAbout by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.clickable { showAbout = true },
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(sizing.spacing(4.dp)))
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = stringResource(R.string.appstrings_site_network_ids_about_action),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(sizing.component(16.dp))
        )
    }

    if (showAbout) {
        EnbAboutDialog(shape = dialogShape, onDismiss = { showAbout = false })
    }
}

/** Qui est eNB Analytics, et pourquoi GeoTower peut redistribuer ses identifiants. */
@Composable
fun EnbAboutDialog(shape: Shape, onDismiss: () -> Unit) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(text = ENB_PROJECT_NAME, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.appstrings_site_network_ids_about),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
                TextButton(onClick = { uriHandler.openUri(ENB_WEBSITE_URL) }) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(sizing.component(18.dp))
                    )
                    Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                    Text(text = ENB_WEBSITE_LABEL, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            DialogNeutralButton(text = stringResource(R.string.appstrings_close), onClick = onDismiss)
        }
    )
}

// Nom du partenaire et son site, volontairement hors traduction.
private const val ENB_PROJECT_NAME = "eNB Analytics"
private const val ENB_WEBSITE_LABEL = "enb-analytics.fr"
private const val ENB_WEBSITE_URL = "https://enb-analytics.fr"
