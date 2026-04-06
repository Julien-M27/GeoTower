package fr.geotower.ui.components.detail_parts

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geotower.ui.components.OutlinedCard

@Composable
fun SupportInfoBlock(
    address: String,
    city: String,
    height: Float,
    typeSupport: String
) {
    OutlinedCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Adresse
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Adresse",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(text = "$address, $city", style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // LIGNE CORRIGÉE : On utilise Divider classique
            Divider()

            Spacer(modifier = Modifier.height(16.dp))

            // Infos techniques (Hauteur & Type)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Hauteur
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Height,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "Hauteur", style = MaterialTheme.typography.labelSmall)
                        Text(text = "${height.toInt()} m", fontWeight = FontWeight.Bold)
                    }
                }

                // Type
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SettingsInputAntenna,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "Support", style = MaterialTheme.typography.labelSmall)
                        Text(text = typeSupport, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}