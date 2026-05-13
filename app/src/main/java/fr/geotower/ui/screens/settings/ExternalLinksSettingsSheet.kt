package fr.geotower.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fr.geotower.ui.components.SiteExternalLinkDefinitions
import fr.geotower.ui.components.readSiteExternalLinkOrder
import fr.geotower.ui.components.rememberReorderableDragState
import fr.geotower.ui.components.resetSiteExternalLinks
import fr.geotower.ui.components.siteExternalLinkById
import fr.geotower.ui.components.writeSiteExternalLinkOrder
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalLinksSettingsSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState,
    useOneUi: Boolean
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    // Design des petits blocs individuels
    val cardShape = if (useOneUi) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)
    val cardBorder = if (useOneUi) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    val bubbleColor = if (useOneUi) {
        if (isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    var linkEnabled by remember {
        mutableStateOf(
            SiteExternalLinkDefinitions.associate { link ->
                link.id to prefs.getBoolean(link.prefKey, link.defaultEnabled)
            }
        )
    }

    var linksOrder by remember {
        mutableStateOf(readSiteExternalLinkOrder(prefs))
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp, start = 24.dp, end = 24.dp)) {

            // --- EN-TÊTE AVEC BOUTON RETOUR ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Text(
                    text = AppStrings.externalLinksSettingsTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(48.dp))
            }

            val itemHeight = 64.dp
            val spacing = 10.dp
            val reorderState = rememberReorderableDragState(
                items = linksOrder,
                itemHeight = itemHeight,
                itemSpacing = spacing,
                onOrderChange = { newOrder ->
                    linksOrder = newOrder
                    writeSiteExternalLinkOrder(prefs, newOrder)
                }
            )

            // --- LISTE DES BLOCS INDIVIDUELS SANS CADRE EXTERIEUR ---
            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                linksOrder.forEach { linkId ->
                    key(linkId) {
                        val isDragged = reorderState.isDragged(linkId)
                        val dragModifier = reorderState.dragModifier(linkId)
                        val dragOffset = reorderState.offsetFor(linkId)

                        val link = siteExternalLinkById(linkId)

                        if (link != null) {
                            val checked = linkEnabled[link.id] ?: link.defaultEnabled
                            val onChecked = { isChecked: Boolean ->
                                linkEnabled = linkEnabled + (link.id to isChecked)
                                prefs.edit().putBoolean(link.prefKey, isChecked).apply()
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(itemHeight)
                                    .zIndex(if (isDragged) 10f else 0f)
                                    .graphicsLayer {
                                        translationY = if (isDragged) dragOffset else 0f
                                        scaleX = if (isDragged) 1.02f else 1f
                                        scaleY = if (isDragged) 1.02f else 1f
                                        shadowElevation = if (isDragged) 8.dp.toPx() else 0f
                                    }
                                    .then(dragModifier),
                                shape = cardShape,
                                border = cardBorder,
                                color = if (isDragged) MaterialTheme.colorScheme.surfaceVariant else bubbleColor
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                                ) {
                                    Icon(Icons.Default.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(16.dp))

                                    Image(painter = painterResource(id = link.logoRes), contentDescription = null, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)))
                                    Spacer(modifier = Modifier.width(16.dp))

                                    Text(link.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)

                                    if (useOneUi) Box(modifier = Modifier.scale(0.85f)) { fr.geotower.ui.components.OneUiSwitch(checked, onChecked) }
                                    else Switch(checked = checked, onCheckedChange = onChecked, modifier = Modifier.scale(0.8f))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = {
                    resetSiteExternalLinks(prefs)
                    linkEnabled = SiteExternalLinkDefinitions.associate { it.id to it.defaultEnabled }
                    linksOrder = readSiteExternalLinkOrder(prefs)
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = AppStrings.resetToDefault,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
        }
    }
}
