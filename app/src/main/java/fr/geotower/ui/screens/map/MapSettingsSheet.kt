package fr.geotower.ui.screens.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings
import android.content.SharedPreferences
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.MutableState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MapSettingsSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    // ✅ AJOUT : Pour pouvoir sauvegarder le paramètre
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", android.content.Context.MODE_PRIVATE)

    // Variables Opérateurs
    var showOrange by AppConfig.showOrange
    var showSfr by AppConfig.showSfr
    var showBouygues by AppConfig.showBouygues
    var showFree by AppConfig.showFree

    // Variables Azimuts
    var showAzimuths by AppConfig.showAzimuths

    // Variables Technos
    var show2G by AppConfig.showTechno2G
    var show3G by AppConfig.showTechno3G
    var show4G by AppConfig.showTechno4G
    var show5G by AppConfig.showTechno5G
    var showFH by AppConfig.showTechnoFH

    // Variables Fréquences
    // 2G
    var f2G_900 by AppConfig.f2G_900
    var f2G_1800 by AppConfig.f2G_1800
    // 3G
    var f3G_900 by AppConfig.f3G_900
    var f3G_2100 by AppConfig.f3G_2100
    // 4G
    var f4G_700 by AppConfig.f4G_700
    var f4G_800 by AppConfig.f4G_800
    var f4G_900 by AppConfig.f4G_900
    var f4G_1800 by AppConfig.f4G_1800
    var f4G_2100 by AppConfig.f4G_2100
    var f4G_2600 by AppConfig.f4G_2600
    // 5G
    var f5G_700 by AppConfig.f5G_700
    var f5G_2100 by AppConfig.f5G_2100
    var f5G_3500 by AppConfig.f5G_3500
    var f5G_26000 by AppConfig.f5G_26000


    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Transparent,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 64.dp)
        ) {

            // 1. OPÉRATEURS
            SectionTitle(AppStrings.operatorsTitle)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Orange reste en Orange
                    SelectableButton(
                        "Orange",
                        showOrange,
                        Modifier.weight(1f),
                        selectedColor = Color(0xFFFF7900)
                    ) { showOrange = it }
                    // SFR reste en Rouge
                    SelectableButton(
                        "SFR",
                        showSfr,
                        Modifier.weight(1f),
                        selectedColor = Color(0xFFE2001A)
                    ) { showSfr = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Bouygues reste en Bleu foncé
                    SelectableButton(
                        "Bouygues",
                        showBouygues,
                        Modifier.weight(1f),
                        selectedColor = Color(0xFF00295F)
                    ) { showBouygues = it }
                    // Free passe en Gris
                    SelectableButton(
                        "Free",
                        showFree,
                        Modifier.weight(1f),
                        selectedColor = Color(0xFF757575)
                    ) { showFree = it }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ==========================================
            // 2. AZIMUTS
            // ==========================================
            SectionTitle(AppStrings.azimuthsTitle)
            SelectableButton(
                label = AppStrings.showAzimuthsLabel.replace(" (", "\n("),
                isSelected = showAzimuths,
                modifier = Modifier.fillMaxWidth()
            ) {
                showAzimuths = it
                prefs.edit().putBoolean("show_azimuths", it).apply()
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 2. TECHNOLOGIES
            SectionTitle(AppStrings.technologiesTitle)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectableButton("2G", show2G, Modifier.weight(1f)) {
                    show2G = it; prefs.edit().putBoolean("show_techno_2g", it).apply()
                }
                SelectableButton("3G", show3G, Modifier.weight(1f)) {
                    show3G = it; prefs.edit().putBoolean("show_techno_3g", it).apply()
                }
                SelectableButton("4G", show4G, Modifier.weight(1f)) {
                    show4G = it; prefs.edit().putBoolean("show_techno_4g", it).apply()
                }
                SelectableButton("5G", show5G, Modifier.weight(1f)) {
                    show5G = it; prefs.edit().putBoolean("show_techno_5g", it).apply()
                }
                SelectableButton("FH", showFH, Modifier.weight(1f)) {
                    showFH = it; prefs.edit().putBoolean("show_techno_fh", it).apply()
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 3. FRÉQUENCES
            SectionTitle(AppStrings.frequenciesTitle)

            // Ligne 2G
            AnimatedVisibility(visible = show2G) {
                Column {
                    FreqRow("2G") {
                        FilterToggleButton("900 MHz", "f2g_900", AppConfig.f2G_900, prefs)
                        FilterToggleButton("1800 MHz", "f2g_1800", AppConfig.f2G_1800, prefs)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            // Ligne 3G
            AnimatedVisibility(visible = show3G) {
                Column {
                    FreqRow("3G") {
                        FilterToggleButton("900 MHz", "f3g_900", AppConfig.f3G_900, prefs)
                        FilterToggleButton("2100 MHz", "f3g_2100", AppConfig.f3G_2100, prefs)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            // Ligne 4G
            AnimatedVisibility(visible = show4G) {
                Column {
                    FreqRow("4G") {
                        FilterToggleButton("700 MHz", "f4g_700", AppConfig.f4G_700, prefs)
                        FilterToggleButton("800 MHz", "f4g_800", AppConfig.f4G_800, prefs)
                        FilterToggleButton("900 MHz", "f4g_900", AppConfig.f4G_900, prefs)
                        FilterToggleButton("1800 MHz", "f4g_1800", AppConfig.f4G_1800, prefs)
                        FilterToggleButton("2100 MHz", "f4g_2100", AppConfig.f4G_2100, prefs)
                        FilterToggleButton("2600 MHz", "f4g_2600", AppConfig.f4G_2600, prefs)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            // Ligne 5G
            AnimatedVisibility(visible = show5G) {
                Column {
                    FreqRow("5G") {
                        FilterToggleButton("700 MHz", "f5g_700", AppConfig.f5G_700, prefs)
                        FilterToggleButton("2100 MHz", "f5g_2100", AppConfig.f5G_2100, prefs)
                        FilterToggleButton("3500 MHz", "f5g_3500", AppConfig.f5G_3500, prefs)
                        //FilterToggleButton("26 GHz", "f5g_26000", AppConfig.f5G_26000, prefs)
                    }
                }
            }
        }
    }
}

// --- COMPOSANTS UI ---

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 16.dp)
    )
}

@Composable
fun SelectableButton(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    selectedColor: Color? = null,
    onClick: (Boolean) -> Unit
) {
    val activeColor = selectedColor ?: MaterialTheme.colorScheme.primary

    // Le fond reste légèrement teinté, mais le texte ne change plus de couleur
    val containerColor = if (isSelected) activeColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor = MaterialTheme.colorScheme.onSurface // Couleur fixe pour l'écriture
    val border = if (isSelected) BorderStroke(1.dp, activeColor) else null // Bordure plus fine

    Surface(
        onClick = { onClick(!isSelected) },
        // ✅ CORRECTION : heightIn(min = 56.dp) permet au bouton de s'agrandir s'il y a 2 lignes
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
        border = border
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 8.dp) // Un peu d'espace en haut et en bas
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center, // ✅ CORRECTION : Centre le texte
                lineHeight = 20.sp // Rapproche joliment les deux lignes
            )
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FreqRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            modifier = Modifier.width(50.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) { content() }
    }
}

@Composable
fun FreqButton(
    label: String,
    isSelected: Boolean,
    onClick: (Boolean) -> Unit
) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = { onClick(!isSelected) },
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.size(width = 86.dp, height = 40.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
fun FilterToggleButton(
    label: String,
    prefKey: String,
    state: MutableState<Boolean>,
    prefs: SharedPreferences
) {
    // On utilise ton design de bouton existant
    FreqButton(
        label = label,
        isSelected = state.value,
        onClick = { newState ->
            state.value = newState
            prefs.edit().putBoolean(prefKey, newState).apply()
        }
    )
}