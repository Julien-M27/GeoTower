package fr.geotower.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitSettingsSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState,
    useOneUi: Boolean,
    bubbleColor: Color
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    var currentDistance by remember { mutableIntStateOf(AppConfig.distanceUnit.intValue) }
    var currentSpeed by remember { mutableIntStateOf(AppConfig.speedUnit.intValue) }

    fun saveUnit(key: String, stateVar: MutableIntState, value: Int) {
        stateVar.intValue = value
        prefs.edit().putInt(key, value).apply()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp, start = 24.dp, end = 24.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(text = AppStrings.unitSettingsTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }

            Text(text = AppStrings.distanceLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp, start = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitOptionItem(AppStrings.unitKm, currentDistance == 0, useOneUi, bubbleColor) { currentDistance = 0; saveUnit("distance_unit", AppConfig.distanceUnit, 0) }
                UnitOptionItem(AppStrings.unitMi, currentDistance == 1, useOneUi, bubbleColor) { currentDistance = 1; saveUnit("distance_unit", AppConfig.distanceUnit, 1) }
            }

            Spacer(Modifier.height(24.dp))

            Text(text = AppStrings.speedLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp, start = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitOptionItem(AppStrings.unitKmh, currentSpeed == 0, useOneUi, bubbleColor) { currentSpeed = 0; saveUnit("speed_unit", AppConfig.speedUnit, 0) }
                UnitOptionItem(AppStrings.unitMph, currentSpeed == 1, useOneUi, bubbleColor) { currentSpeed = 1; saveUnit("speed_unit", AppConfig.speedUnit, 1) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun UnitOptionItem(name: String, isSelected: Boolean, useOneUi: Boolean, bubbleColor: Color, onClick: () -> Unit) {
    val accentColor = MaterialTheme.colorScheme.primary
    val bgColor = if (isSelected) accentColor.copy(alpha = 0.1f) else if (useOneUi) bubbleColor else Color.Transparent
    val border = if (useOneUi) { if (isSelected) BorderStroke(2.dp, accentColor) else null } else { BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) }
    val shape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)

    Surface(shape = shape, border = border, color = bgColor, modifier = Modifier.fillMaxWidth().height(56.dp).clickable { onClick() }) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = name, style = MaterialTheme.typography.titleMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
            if (useOneUi) { OneUiRadioButton(isSelected, onClick) } else { RadioButton(selected = isSelected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = accentColor)) }
        }
    }
}