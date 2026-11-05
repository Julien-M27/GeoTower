package fr.geotower.wear.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import fr.geotower.wear.data.model.DataSourceMode

@Composable
fun SourceToggleChip(
    currentMode: DataSourceMode,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Chip(
        onClick = onToggle,
        label = {
            Text(
                text = "Source : ${currentMode.shortLabel}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        secondaryLabel = {
            Text(
                text = when (currentMode) {
                    DataSourceMode.AUTO -> "Auto (Tél. puis Web)"
                    DataSourceMode.PHONE_LOCAL -> "Room SQLite hors-ligne"
                    DataSourceMode.WEB_API -> "API Web HTTPS directe"
                },
                fontSize = 9.sp,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = modifier.fillMaxWidth()
    )
}
