package fr.geotower.wear.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import fr.geotower.wear.BuildConfig
import fr.geotower.wear.data.model.DataSourceMode
import fr.geotower.wear.data.model.DistanceUnit
import fr.geotower.wear.data.model.GpsFixStatus
import fr.geotower.wear.data.model.GpsRefreshInterval
import fr.geotower.wear.data.model.WearLanguage
import fr.geotower.wear.data.model.WearMapTheme
import fr.geotower.wear.presentation.components.GpsStatusHeader

@Composable
fun SettingsScreen(
    currentSource: DataSourceMode,
    isPhoneAppInstalled: Boolean,
    gpsRefreshInterval: GpsRefreshInterval,
    gpsStatus: GpsFixStatus,
    mapTheme: WearMapTheme,
    currentLanguage: WearLanguage,
    distanceUnit: DistanceUnit,
    onSourceChanged: (DataSourceMode) -> Unit,
    onGpsRefreshIntervalChanged: (GpsRefreshInterval) -> Unit,
    onMapThemeChanged: (WearMapTheme) -> Unit,
    onLanguageChanged: (WearLanguage) -> Unit,
    onDistanceUnitChanged: (DistanceUnit) -> Unit,
    onForceRefresh: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()

    val sourceModes = listOf(
        DataSourceMode.AUTO to "Auto (Tél. puis Web)",
        DataSourceMode.PHONE_LOCAL to "Base locale tél. (Bluetooth)",
        DataSourceMode.WEB_API to "API Web directe (Wi-Fi/4G)"
    )

    ScalingLazyColumn(
        state = listState,
        autoCentering = AutoCenteringParams(itemIndex = 1),
        contentPadding = PaddingValues(start = 10.dp, top = 16.dp, end = 10.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxSize().background(Color.Black)
    ) {
        // En-tête compactée pour placer le bandeau de sélection directement au centre
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
            ) {
                Text(
                    text = "Paramètres",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "SOURCE DES DONNÉES",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }
        }

        // Section 1 : Source des données (avec Auto au centre de l'écran par défaut)
        items(sourceModes.size) { index ->
            val (mode, desc) = sourceModes[index]
            val isSelected = currentSource == mode
            val isAvailable = mode != DataSourceMode.PHONE_LOCAL || isPhoneAppInstalled
            val subtitle = if (mode == DataSourceMode.PHONE_LOCAL && !isPhoneAppInstalled) {
                "Non dispo (appli absente sur tél.)"
            } else {
                desc
            }

            Chip(
                onClick = {
                    if (isAvailable) {
                        onSourceChanged(mode)
                    }
                },
                enabled = isAvailable,
                label = {
                    Text(
                        text = if (isSelected) "✓ ${mode.label}" else mode.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            !isAvailable -> Color.Gray
                            isSelected -> Color.White
                            else -> MaterialTheme.colors.onSurfaceVariant
                        }
                    )
                },
                secondaryLabel = {
                    Text(
                        text = subtitle,
                        fontSize = 9.sp,
                        color = if (!isAvailable) Color(0xFFFF453A) else MaterialTheme.colors.onSurfaceVariant
                    )
                },
                colors = if (isSelected) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Section 2 : Thème de la carte (Clair ou Sombre)
        item {
            Text(
                text = "THÈME DE LA CARTE",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val isLight = mapTheme == WearMapTheme.LIGHT
                CompactChip(
                    onClick = { onMapThemeChanged(WearMapTheme.LIGHT) },
                    label = {
                        Text(
                            text = if (isLight) "✓ Clair" else "Clair",
                            fontSize = 11.sp,
                            fontWeight = if (isLight) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    colors = if (isLight) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.weight(1f)
                )

                val isDark = mapTheme == WearMapTheme.DARK
                CompactChip(
                    onClick = { onMapThemeChanged(WearMapTheme.DARK) },
                    label = {
                        Text(
                            text = if (isDark) "✓ Sombre" else "Sombre",
                            fontSize = 11.sp,
                            fontWeight = if (isDark) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    colors = if (isDark) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Section 3 : Unités de mesure (Métrique / Impérial)
        item {
            Text(
                text = "UNITÉS DE MESURE",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val isMetric = distanceUnit == DistanceUnit.METRIC
                CompactChip(
                    onClick = { onDistanceUnitChanged(DistanceUnit.METRIC) },
                    label = {
                        Text(
                            text = if (isMetric) "✓ Métrique" else "Métrique",
                            fontSize = 11.sp,
                            fontWeight = if (isMetric) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    colors = if (isMetric) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.weight(1f)
                )

                val isImperial = distanceUnit == DistanceUnit.IMPERIAL
                CompactChip(
                    onClick = { onDistanceUnitChanged(DistanceUnit.IMPERIAL) },
                    label = {
                        Text(
                            text = if (isImperial) "✓ Impérial" else "Impérial",
                            fontSize = 11.sp,
                            fontWeight = if (isImperial) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    colors = if (isImperial) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Section 4 : Langue de l'application (comme sur le smartphone)
        item {
            Text(
                text = "LANGUE",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        items(WearLanguage.values().size) { index ->
            val lang = WearLanguage.values()[index]
            val isSelected = currentLanguage == lang
            Chip(
                onClick = { onLanguageChanged(lang) },
                label = {
                    Text(
                        text = if (isSelected) "✓ ${lang.label}" else lang.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.White else MaterialTheme.colors.onSurfaceVariant
                    )
                },
                colors = if (isSelected) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Section 4 : Actualisation du GPS (comme sur Garmin)
        item {
            Text(
                text = "ACTUALISATION GPS (GARMIN)",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        item {
            Chip(
                onClick = { onGpsRefreshIntervalChanged(gpsRefreshInterval.next()) },
                label = {
                    Text(
                        text = gpsRefreshInterval.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                secondaryLabel = {
                    Text(
                        text = gpsRefreshInterval.description,
                        fontSize = 9.sp,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Section 3 : Statut GPS Matériel
        item {
            Text(
                text = "GPS MATÉRIEL MONTRE",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        item {
            GpsStatusHeader(gpsStatus = gpsStatus)
        }

        // Section 4 : À propos
        item {
            Chip(
                onClick = onOpenAbout,
                label = {
                    Text(
                        text = "À propos",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                },
                secondaryLabel = {
                    Text(
                        text = "GeoTower Wear OS v${BuildConfig.VERSION_NAME}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Bouton Rafraîchir
        item {
            Spacer(Modifier.height(4.dp))
            CompactChip(
                onClick = onForceRefresh,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualiser",
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    Text(
                        text = "Forcer actualisation",
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
