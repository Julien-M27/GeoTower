package fr.geotower.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.data.api.SqSpeedtestData
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings

import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpeedtestCard(
    operatorName: String?,
    speedtestData: SqSpeedtestData?,
    isLoading: Boolean,
    shape: Shape,
    bgColor: Color,
    contentColor: Color
) {
    // 1. Vérification du paramètre utilisateur (Affichage activé ?)
    if (!AppConfig.siteShowSpeedtest.value) return

    // 2. Vérification de l'opérateur (Seulement SFR et Bouygues)
    val isCompatible = operatorName?.contains("SFR", ignoreCase = true) == true ||
            operatorName?.contains("BOUYGUES", ignoreCase = true) == true

    // Si ce n'est pas SFR ou Bouygues, on ne dessine rien et le bloc disparaît
    if (!isCompatible) return

    // 3. Dessin de la carte
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = bgColor, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth() // Le padding extérieur sera géré par l'écran parent
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {

            // Titre du bloc
            Text(
                text = AppStrings.speedtestTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // État 1 : En cours de chargement (Appel API)
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(modifier = Modifier.size(32.dp))
                }
            }
            // État 2 : Aucun test de débit trouvé
            else if (speedtestData == null) {
                Text(
                    text = AppStrings.speedtestNoData,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            // État 3 : Données reçues avec succès !
            else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Colonne : Débit Descendant
                    SpeedStatColumn(
                        icon = Icons.Default.KeyboardArrowDown,
                        label = AppStrings.speedtestDownload,
                        value = if (speedtestData.downloadSpeed != null) String.format(Locale.US, "%.1f", speedtestData.downloadSpeed) else "--",
                        unit = "Mbps",
                        color = Color(0xFF4CAF50) // Vert
                    )

                    // Colonne : Débit Montant
                    SpeedStatColumn(
                        icon = Icons.Default.KeyboardArrowUp,
                        label = AppStrings.speedtestUpload,
                        value = if (speedtestData.uploadSpeed != null) String.format(Locale.US, "%.1f", speedtestData.uploadSpeed) else "--",
                        unit = "Mbps",
                        color = Color(0xFF2196F3) // Bleu
                    )

                    // Colonne : Ping (Latence)
                    SpeedStatColumn(
                        icon = Icons.Default.Timer,
                        label = AppStrings.speedtestPing,
                        value = if (speedtestData.ping != null) speedtestData.ping.toInt().toString() else "--",
                        unit = "ms",
                        color = Color(0xFFFF9800) // Orange
                    )
                }

                // Optionnel : Afficher la date du test si l'API la fournit
                if (!speedtestData.timestamp.isNullOrBlank()) {
                    val dateStr = speedtestData.timestamp.take(10) // YYYY-MM-DD
                    val parts = dateStr.split("-")
                    if (parts.size == 3) {
                        val year = parts[0]
                        val month = parts[1]
                        val day = parts[2].toInt().toString() // "07" -> "7"
                        val monthName = AppStrings.getMonthName(month)
                        
                        Text(
                            text = "$day $monthName $year",
                            fontSize = 12.sp,
                            color = contentColor.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 12.dp).align(Alignment.End)
                        )
                    }
                }
            }
        }
    }
}

// Petit sous-composant privé pour éviter de dupliquer le code des 3 colonnes
@Composable
private fun SpeedStatColumn(icon: ImageVector, label: String, value: String, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(text = unit, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}