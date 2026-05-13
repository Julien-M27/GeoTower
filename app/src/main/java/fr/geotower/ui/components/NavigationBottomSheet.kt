package fr.geotower.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.R
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationBottomSheet(
    latitude: Double,
    longitude: Double,
    onDismiss: () -> Unit,
    sheetState: SheetState,
    useOneUi: Boolean
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    var expandWebOptions by remember { mutableStateOf(false) }

    // --- NOUVEAU : On lit les textes @Composable ici, en dehors des clics ---
    val txtOpenRouteWith = AppStrings.openRouteWith
    val txtNoGpsApp = AppStrings.noGpsApp

    val safeClick = rememberSafeClick()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
        ) {
            Text(
                text = AppStrings.openRouteWith,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)
            )

            NavOptionItem(
                iconVector = Icons.Default.Smartphone,
                label = AppStrings.installedApp,
                subLabel = AppStrings.installedAppDesc,
                useOneUi = useOneUi,
                onClick = {
                    safeClick {
                        onDismiss()
                        val geoUri = Uri.parse("geo:0,0?q=${latitude},${longitude}")
                        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)

                        // MODIFIÉ ICI : Utilisation de la variable txtOpenRouteWith
                        val chooser = Intent.createChooser(mapIntent, txtOpenRouteWith)
                        try {
                            context.startActivity(chooser)
                        } catch (e: Exception) {
                            // MODIFIÉ ICI : Utilisation de la variable txtNoGpsApp
                            Toast.makeText(context, txtNoGpsApp, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            NavOptionItem(
                iconVector = Icons.Default.Language,
                label = AppStrings.onInternet,
                subLabel = AppStrings.onInternetDesc,
                trailingIcon = if (expandWebOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                useOneUi = useOneUi,
                onClick = { safeClick { expandWebOptions = !expandWebOptions } }
            )

            AnimatedVisibility(visible = expandWebOptions) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 8.dp)
                ) {
                    // --- 1. GOOGLE MAPS CLASSIQUE (Lien corrigé et propre) ---
                    NavOptionItem(
                        iconRes = R.drawable.logo_googlemap,
                        label = "Google Maps",
                        isSubItem = true,
                        useOneUi = useOneUi,
                        onClick = {
                            safeClick {
                                onDismiss()
                                uriHandler.openUri("https://www.google.com/maps/search/?api=1&query=${latitude},${longitude}")
                            }
                        }
                    )

                    // --- 2. NOUVEAU : GOOGLE STREET VIEW ---
                    NavOptionItem(
                        iconRes = R.drawable.logo_streetview,
                        label = "Google Street View",
                        isSubItem = true,
                        useOneUi = useOneUi,
                        onClick = {
                            safeClick {
                                onDismiss()
                                // L'URL officielle pour forcer l'ouverture en mode Street View
                                uriHandler.openUri("https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=${latitude},${longitude}")
                            }
                        }
                    )

                    // --- 3. APPLE PLANS ---
                    NavOptionItem(
                        iconRes = R.drawable.logo_appleplan,
                        label = "Apple Plans",
                        isSubItem = true,
                        useOneUi = useOneUi,
                        onClick = {
                            safeClick {
                                onDismiss()
                                uriHandler.openUri("https://maps.apple.com/?q=${latitude},${longitude}&ll=${latitude},${longitude}")
                            }
                        }
                    )

                    // --- 4. OPENSTREETMAP ---
                    NavOptionItem(
                        iconRes = R.drawable.logo_openstreetmap,
                        label = "OpenStreetMap",
                        isSubItem = true,
                        useOneUi = useOneUi,
                        onClick = {
                            safeClick {
                                onDismiss()
                                uriHandler.openUri("https://www.openstreetmap.org/?mlat=${latitude}&mlon=${longitude}#map=16/${latitude}/${longitude}")
                            }
                        }
                    )

                    // --- 5. GÉOPORTAIL IGN ---
                    NavOptionItem(
                        iconRes = R.drawable.logo_cartesign,
                        label = AppStrings.geoportailIgn,
                        isSubItem = true,
                        useOneUi = useOneUi,
                        onClick = {
                            safeClick {
                                onDismiss()
                                uriHandler.openUri("https://www.geoportail.gouv.fr/carte?c=${longitude},${latitude}&z=16")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavOptionItem(
    iconVector: ImageVector? = null,
    iconRes: Int? = null,
    label: String,
    subLabel: String? = null,
    trailingIcon: ImageVector? = null,
    isSubItem: Boolean = false,
    useOneUi: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(if (useOneUi) RoundedCornerShape(16.dp) else RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (isSubItem) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconSize = 24.dp
        if (iconRes != null) {
            Image(painter = painterResource(id = iconRes), contentDescription = null, modifier = Modifier.size(iconSize).clip(RoundedCornerShape(4.dp)))
        } else if (iconVector != null) {
            Icon(imageVector = iconVector, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(iconSize))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontWeight = if (isSubItem) FontWeight.Medium else FontWeight.SemiBold, fontSize = if (isSubItem) 15.sp else 16.sp, color = MaterialTheme.colorScheme.onSurface)
            if (subLabel != null) { Text(text = subLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (trailingIcon != null) { Icon(trailingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
