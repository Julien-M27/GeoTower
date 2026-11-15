package fr.geotower.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
 * Attribution cliquable des partenaires eNB-Analytics et RNC Mobile (pour Free Mobile) : les
 * identifiants eNB/gNB ne viennent pas de l'ANFR, la source doit donc etre creditee partout ou la
 * donnee est lue ou telechargee.
 *
 * Le clic ouvre [EnbAboutDialog] ou [RncMobileAboutDialog] (description du projet + lien vers le
 * site). Utilise par la fiche site ([SiteNetworkIdsBlock]) et par la carte de telechargement
 * ([EnbDatabaseDownloadCard]).
 */
@Composable
fun EnbSourceAttribution(
    dialogShape: Shape,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.appstrings_site_network_ids_source),
    includeRncMobile: Boolean = false,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.End,
    textStyle: TextStyle = LocalGeoTowerUiStyle.current.sizing.textStyle(MaterialTheme.typography.labelSmall),
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    var showEnbAbout by remember { mutableStateOf(false) }
    var showRncAbout by remember { mutableStateOf(false) }

    if (includeRncMobile) {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = horizontalArrangement,
            verticalArrangement = Arrangement.spacedBy(sizing.spacing(4.dp))
        ) {
            AttributionLabelRow(
                label = label,
                contentDescription = stringResource(R.string.appstrings_site_network_ids_about_action),
                textStyle = textStyle,
                onClick = { showEnbAbout = true }
            )
            Text(
                text = "  •  ",
                style = textStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AttributionLabelRow(
                label = RNC_MOBILE_PROJECT_NAME,
                contentDescription = stringResource(R.string.appstrings_site_network_ids_rnc_about_action),
                textStyle = textStyle,
                onClick = { showRncAbout = true }
            )
        }
    } else {
        Row(
            modifier = modifier.clickable { showEnbAbout = true },
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
    }

    if (showEnbAbout) {
        EnbAboutDialog(shape = dialogShape, onDismiss = { showEnbAbout = false })
    }
    if (showRncAbout) {
        RncMobileAboutDialog(shape = dialogShape, onDismiss = { showRncAbout = false })
    }
}

@Composable
private fun AttributionLabelRow(
    label: String,
    contentDescription: String,
    textStyle: TextStyle,
    onClick: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Row(
        modifier = Modifier.clickable(onClick = onClick),
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
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(sizing.component(16.dp))
        )
    }
}

/** Qui est eNB Analytics, et pourquoi GeoTower peut redistribuer ses identifiants. */
@Composable
fun EnbAboutDialog(shape: Shape, onDismiss: () -> Unit) {
    PartnerAboutDialog(
        shape = shape,
        title = ENB_PROJECT_NAME,
        description = stringResource(R.string.appstrings_site_network_ids_about),
        websiteLabel = ENB_WEBSITE_LABEL,
        websiteUrl = ENB_WEBSITE_URL,
        onDismiss = onDismiss
    )
}

/** Presentation de RNC Mobile, d'ou provient une grande partie des identifiants Free Mobile. */
@Composable
fun RncMobileAboutDialog(shape: Shape, onDismiss: () -> Unit) {
    PartnerAboutDialog(
        shape = shape,
        title = RNC_MOBILE_PROJECT_NAME,
        description = stringResource(R.string.appstrings_site_network_ids_rnc_about),
        websiteLabel = RNC_MOBILE_WEBSITE_LABEL,
        websiteUrl = RNC_MOBILE_WEBSITE_URL,
        onDismiss = onDismiss
    )
}

@Composable
private fun PartnerAboutDialog(
    shape: Shape,
    title: String,
    description: String,
    websiteLabel: String,
    websiteUrl: String,
    onDismiss: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
                TextButton(onClick = { uriHandler.openUri(websiteUrl) }) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(sizing.component(18.dp))
                    )
                    Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                    Text(text = websiteLabel, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            DialogNeutralButton(text = stringResource(R.string.appstrings_close), onClick = onDismiss)
        }
    )
}

// Noms des partenaires et leurs sites, volontairement hors traduction.
private const val ENB_PROJECT_NAME = "eNB Analytics"
private const val ENB_WEBSITE_LABEL = "enb-analytics.fr"
private const val ENB_WEBSITE_URL = "https://enb-analytics.fr"
private const val RNC_MOBILE_PROJECT_NAME = "RNC Mobile"
private const val RNC_MOBILE_WEBSITE_LABEL = "rncmobile.net"
private const val RNC_MOBILE_WEBSITE_URL = "https://rncmobile.net/"
