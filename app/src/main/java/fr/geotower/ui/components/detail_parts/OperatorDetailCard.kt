package fr.geotower.ui.components.detail_parts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geotower.data.models.OperatorDetails
import fr.geotower.ui.components.OutlinedCard

@Composable
fun OperatorDetailCard(
    details: OperatorDetails,
    color: Color,
    externalLinksContent: @Composable () -> Unit // Pour insérer les boutons après
) {
    var expanded by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier.padding(vertical = 4.dp),
        onClick = { expanded = !expanded }
    ) {
        // EN-TÊTE (Toujours visible)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Pastille de couleur
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = details.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
        }

        // CORPS DÉPLIABLE
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // Technologies
                Text("Technologies :", fontWeight = FontWeight.Bold)
                Text(details.technologies.joinToString(", "))

                Spacer(modifier = Modifier.height(8.dp))

                // Fréquences
                Text("Fréquences :", fontWeight = FontWeight.Bold)
                Text(details.frequencies.joinToString("\n"))

                Spacer(modifier = Modifier.height(16.dp))

                // Zone pour les boutons externes (Cellulafr, etc.)
                externalLinksContent()
            }
        }
    }
}
