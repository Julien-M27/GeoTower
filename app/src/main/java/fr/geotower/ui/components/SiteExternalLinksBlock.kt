package fr.geotower.ui.components

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.geotower.R
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.utils.AppStrings

@Composable
fun SiteExternalLinksBlock(
    info: LocalisationEntity,
    cardBgColor: Color,
    blockShape: Shape,
    buttonShape: Shape,
    isSignalQuestInstalled: Boolean,
    onShowCellularFr: () -> Unit,
    onShowRnc: () -> Unit,
    onShowEnb: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val txtExternalLinks = AppStrings.externalLinks
    val txtOpenApp = AppStrings.openApp
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

    var lastClickTime by remember { mutableLongStateOf(0L) }
    val debounceTime = 700L

    fun safeClick(action: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > debounceTime) {
            lastClickTime = currentTime
            action()
        }
    }

    val externalLinksOrder by remember { mutableStateOf(prefs.getString("external_links_order", "cartoradio,cellularfr,signalquest,rncmobile,enbanalytics,anfr")!!.split(",")) }

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
                            if (showCartoradio) {
                                CommunityLinkRow("Cartoradio", txtOpenApp, R.drawable.logo_cartoradio, buttonShape) {
                                    safeClick {
                                        // ✅ Lien Cartoradio avec les coordonnées (Longitude d'abord, puis Latitude)
                                        uriHandler.openUri("https://cartoradio.fr/index.html#/cartographie/lonlat/${info.longitude}/${info.latitude}")
                                    }
                                }
                            }
                        }
                        "cellularfr" -> {
                            if (showCellularFr && info.operateur?.contains("ORANGE", true) == true) {
                                CommunityLinkRow("CellularFR", txtOpenApp, R.drawable.logo_cellularfr, buttonShape) { safeClick { onShowCellularFr() } }
                            }
                        }
                        "rncmobile" -> {
                            if (showRncMobile && info.operateur?.contains("FREE", true) == true) {
                                CommunityLinkRow("RNC Mobile", txtOpenApp, R.drawable.logo_rncmobile, buttonShape) { safeClick { onShowRnc() } }
                            }
                        }
                        "signalquest" -> {
                            if (showSignalQuest && (info.operateur?.contains("SFR", true) == true || info.operateur?.contains("BOUYGUES", true) == true)) {
                                CommunityLinkRow("Signal Quest", txtOpenApp, R.drawable.logo_signalquest, buttonShape) {
                                    safeClick {
                                        if (isSignalQuestInstalled) {
                                            context.packageManager.getLaunchIntentForPackage("com.sfrmap.android")?.let { context.startActivity(it) }
                                        } else {
                                            uriHandler.openUri("https://play.google.com/store/apps/details?id=com.sfrmap.android")
                                        }
                                    }
                                }
                            }
                        }
                        "enbanalytics" -> {
                            if (showEnbAnalytics) {
                                CommunityLinkRow("eNB-Analytics", txtOpenApp, R.drawable.logo_enbanalytics, buttonShape) { safeClick { onShowEnb() } }
                            }
                        }
                        "anfr" -> {
                            if (showAnfr) {
                                CommunityLinkRow("data.gouv.fr", txtOpenApp, R.drawable.logo_anfr, buttonShape) {
                                    safeClick {
                                        // ✅ Lien data.gouv.fr (ANFR) avec zoom 17, Latitude puis Longitude
                                        uriHandler.openUri("https://data.anfr.fr/visualisation/map/?id=observatoire_2g_3g_4g&location=17,${info.latitude},${info.longitude}")
                                    }
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