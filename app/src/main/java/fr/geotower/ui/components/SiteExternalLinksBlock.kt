package fr.geotower.ui.components

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.geotower.R
import fr.geotower.data.api.SignalQuestOperators
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.models.LocalisationEntity

@Composable
fun SiteExternalLinksBlock(
    info: LocalisationEntity,
    cardBgColor: Color,
    blockShape: Shape,
    buttonShape: Shape,
    onShowCartoradio: () -> Unit,
    onShowCellularFr: () -> Unit,
    onShowSignalQuest: () -> Unit,
    onShowRnc: () -> Unit,
    onShowEnb: () -> Unit,
    onShowAnfr: () -> Unit
) {
    val context = LocalContext.current
    val txtExternalLinks = stringResource(R.string.appstrings_external_links)
    val txtOpenApp = stringResource(R.string.appstrings_open)
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val featureFlags by RemoteFeatureFlags.config

    val safeClick = rememberSafeClick()

    val externalLinksOrder by remember(featureFlags) { mutableStateOf(readSiteExternalLinkOrder(prefs)) }

    val showCartoradio by remember { mutableStateOf(prefs.getBoolean("link_cartoradio", true)) }
    val showAnfr by remember { mutableStateOf(prefs.getBoolean("show_anfr", true)) }
    val showCellularFr by remember { mutableStateOf(prefs.getBoolean("link_cellularfr", true)) }
    val showRncMobile by remember { mutableStateOf(prefs.getBoolean("link_rncmobile", true)) }
    val showSignalQuest by remember { mutableStateOf(prefs.getBoolean("link_signalquest", true)) }
    val showEnbAnalytics by remember { mutableStateOf(prefs.getBoolean("link_enbanalytics", true)) }

    Card(shape = blockShape, colors = CardDefaults.cardColors(containerColor = cardBgColor), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = txtExternalLinks, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                externalLinksOrder.forEach { block ->
                    when (block) {
                        "cartoradio" -> {
                            if (
                                showCartoradio &&
                                featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.EXTERNAL_LINKS_CARTORADIO) &&
                                featureFlags.isProviderEnabled(RemoteFeatureFlags.Providers.CARTORADIO) &&
                                featureFlags.isActionEnabled(RemoteFeatureFlags.Actions.OPEN_EXTERNAL_LINK)
                            ) {
                                CommunityLinkRow(siteExternalLinkById(block)?.label ?: "Cartoradio", txtOpenApp, siteExternalLinkById(block)?.logoRes ?: R.drawable.logo_cartoradio, buttonShape) {
                                    safeClick { onShowCartoradio() }
                                }
                            }
                        }
                        "cellularfr" -> {
                            if (
                                showCellularFr &&
                                featureFlags.isSiteExternalLinkEnabled("cellularfr") &&
                                featureFlags.isProviderEnabled(RemoteFeatureFlags.Providers.CELLULARFR) &&
                                featureFlags.isActionEnabled(RemoteFeatureFlags.Actions.OPEN_EXTERNAL_LINK) &&
                                info.operateur?.contains("ORANGE", true) == true
                            ) {
                                CommunityLinkRow(siteExternalLinkById(block)?.label ?: "CellularFR", txtOpenApp, siteExternalLinkById(block)?.logoRes ?: R.drawable.logo_cellularfr, buttonShape) { safeClick { onShowCellularFr() } }
                            }
                        }
                        "rncmobile" -> {
                            if (
                                showRncMobile &&
                                featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.EXTERNAL_LINKS_RNC_MOBILE) &&
                                featureFlags.isProviderEnabled(RemoteFeatureFlags.Providers.RNC_MOBILE) &&
                                featureFlags.isActionEnabled(RemoteFeatureFlags.Actions.OPEN_EXTERNAL_LINK) &&
                                info.operateur?.contains("FREE", true) == true
                            ) {
                                CommunityLinkRow(siteExternalLinkById(block)?.label ?: "RNC Mobile", txtOpenApp, siteExternalLinkById(block)?.logoRes ?: R.drawable.logo_rncmobile, buttonShape) { safeClick { onShowRnc() } }
                            }
                        }
                        "signalquest" -> {
                            val signalQuestOperator = SignalQuestOperators.operatorParamFor(info.operateur)
                            if (
                                showSignalQuest &&
                                featureFlags.isSiteExternalLinkEnabled("signalquest") &&
                                featureFlags.isProviderEnabled(RemoteFeatureFlags.Providers.SIGNALQUEST) &&
                                featureFlags.isActionEnabled(RemoteFeatureFlags.Actions.OPEN_EXTERNAL_LINK) &&
                                signalQuestOperator != null
                            ) {
                                CommunityLinkRow(siteExternalLinkById(block)?.label ?: "Signal Quest", txtOpenApp, siteExternalLinkById(block)?.logoRes ?: R.drawable.logo_signalquest, buttonShape) { safeClick { onShowSignalQuest() } }
                            }
                        }
                        "enbanalytics" -> {
                            if (
                                showEnbAnalytics &&
                                featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.EXTERNAL_LINKS_ENB_ANALYTICS) &&
                                featureFlags.isProviderEnabled(RemoteFeatureFlags.Providers.ENB_ANALYTICS) &&
                                featureFlags.isActionEnabled(RemoteFeatureFlags.Actions.OPEN_EXTERNAL_LINK)
                            ) {
                                CommunityLinkRow(siteExternalLinkById(block)?.label ?: "eNB-Analytics", txtOpenApp, siteExternalLinkById(block)?.logoRes ?: R.drawable.logo_enbanalytics, buttonShape) { safeClick { onShowEnb() } }
                            }
                        }
                        "anfr" -> {
                            if (
                                showAnfr &&
                                featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.EXTERNAL_LINKS_ANFR) &&
                                featureFlags.isProviderEnabled(RemoteFeatureFlags.Providers.ANFR) &&
                                featureFlags.isActionEnabled(RemoteFeatureFlags.Actions.OPEN_EXTERNAL_LINK)
                            ) {
                                CommunityLinkRow(siteExternalLinkById(block)?.label ?: "data.gouv.fr", txtOpenApp, siteExternalLinkById(block)?.logoRes ?: R.drawable.logo_anfr, buttonShape) {
                                    safeClick { onShowAnfr() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityLinkRow(btnText: String, txtOpen: String, logoRes: Int, buttonShape: Shape, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Image(painter = painterResource(id = logoRes), contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(modifier = Modifier.width(16.dp))
        Button(onClick = onClick, modifier = Modifier.weight(1f), shape = buttonShape) {
            Text(text = "$txtOpen $btnText", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}
