package fr.geotower.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fr.geotower.ui.theme.ColorBouygues
import fr.geotower.ui.theme.ColorFree
import fr.geotower.ui.theme.ColorOrange
import fr.geotower.ui.theme.ColorSfr
import fr.geotower.utils.Constants
import fr.geotower.utils.FilterState

@Composable
fun MapFilterMenu(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // En-tête
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filtres Carte",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Section Opérateurs
                Text("Opérateurs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                FilterCheckbox(label = "Orange", color = ColorOrange, key = Constants.OP_ORANGE)
                FilterCheckbox(label = "SFR", color = ColorSfr, key = Constants.OP_SFR)
                FilterCheckbox(label = "Bouygues", color = ColorBouygues, key = Constants.OP_BOUYGUES)
                FilterCheckbox(label = "Free Mobile", color = ColorFree, key = Constants.OP_FREE)

                Spacer(modifier = Modifier.height(16.dp))

                // Section Technologies
                Text("Technologies", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                TechCheckbox("5G")
                TechCheckbox("4G")
                TechCheckbox("3G")
                TechCheckbox("2G")

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Appliquer")
                }
            }
        }
    }
}

@Composable
fun FilterCheckbox(label: String, color: Color, key: String) {
    val isChecked = FilterState.activeOperators.contains(key)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = { FilterState.toggleOperator(key) },
            colors = CheckboxDefaults.colors(checkedColor = color)
        )
        Text(text = label)
    }
}

@Composable
fun TechCheckbox(label: String) {
    val isChecked = FilterState.activeTechnos.contains(label)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = { FilterState.toggleTechno(label) }
        )
        Text(text = label)
    }
}
