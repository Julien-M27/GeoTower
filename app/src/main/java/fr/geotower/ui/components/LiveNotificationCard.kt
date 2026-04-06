package fr.geotower.ui.components

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LiveNotificationCard(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean
) {
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent
    val contentAlpha = if (enabled) 1f else 0.5f // Grisé si désactivé

    // ✅ 1. NOUVEAU : On récupère le contexte Android pour gérer le service
    val context = LocalContext.current

    // ✅ 2. NOUVEAU : Fonction centralisée qui réagit au clic
    val handleToggle: (Boolean) -> Unit = { isChecked ->
        if (enabled) {
            // A. On prévient l'interface graphique du changement
            onCheckedChange(isChecked)

            // B. On démarre ou on tue le service Android immédiatement !
            val serviceIntent = Intent(context, fr.geotower.services.LiveTrackingService::class.java)
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                context.stopService(serviceIntent) // ❌ Coupe le GPS et détruit la notification
            }
        }
    }

    Surface(
        shape = shape,
        border = border,
        color = cardBg,
        modifier = Modifier.fillMaxWidth().alpha(contentAlpha)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (useOneUi) {
                fr.geotower.ui.components.OneUiSwitch(
                    checked = checked,
                    onCheckedChange = handleToggle // ✅ MODIFIÉ : On utilise notre nouvelle fonction
                )
            } else {
                Switch(
                    checked = checked,
                    onCheckedChange = handleToggle, // ✅ MODIFIÉ : On utilise notre nouvelle fonction
                    enabled = enabled,
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}