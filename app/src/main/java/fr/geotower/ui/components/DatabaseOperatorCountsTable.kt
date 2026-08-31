package fr.geotower.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.ui.theme.LocalGeoTowerUiStyle

data class DatabaseOperatorCountTableRow(
    val operator: String,
    val values: List<String>,
)

@Composable
fun DatabaseOperatorCountsTable(
    rows: List<DatabaseOperatorCountTableRow>,
    operatorHeader: String,
    valueHeaders: List<String>,
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val tableShape = RoundedCornerShape(sizing.component(8.dp))
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val headerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val valueWeight = 0.75f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(sizing.component(0.5.dp), borderColor, tableShape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerColor, tableShape)
                .padding(vertical = sizing.spacing(8.dp), horizontal = sizing.spacing(8.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DatabaseTableHeaderCell(
                text = operatorHeader,
                modifier = Modifier.weight(1.5f),
                textAlign = TextAlign.Start,
            )
            valueHeaders.forEach { header ->
                DatabaseTableHeaderCell(
                    text = header,
                    modifier = Modifier.weight(valueWeight),
                    textAlign = TextAlign.End,
                )
            }
        }
        HorizontalDivider(color = borderColor, thickness = sizing.component(0.5.dp))

        rows.forEachIndexed { index, row ->
            if (index > 0) {
                HorizontalDivider(color = borderColor, thickness = sizing.component(0.5.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (index % 2 == 1) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
                        else Color.Transparent,
                    )
                    .padding(vertical = sizing.spacing(8.dp), horizontal = sizing.spacing(8.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.operator,
                    modifier = Modifier.weight(1.5f),
                    fontSize = sizing.text(12.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                row.values.forEach { value ->
                    Text(
                        text = value,
                        modifier = Modifier.weight(valueWeight),
                        fontSize = sizing.text(12.sp),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun DatabaseTableHeaderCell(text: String, modifier: Modifier, textAlign: TextAlign) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Text(
        text = text,
        modifier = modifier,
        fontSize = sizing.text(11.sp),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
    )
}
