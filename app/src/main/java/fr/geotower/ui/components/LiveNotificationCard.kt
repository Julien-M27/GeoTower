package fr.geotower.ui.components

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import fr.geotower.services.LiveTrackingService
import fr.geotower.utils.AppStrings

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

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var canPostPromoted by remember {
        mutableStateOf(LiveTrackingService.canPostPromotedNotifications(context))
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canPostPromoted = LiveTrackingService.canPostPromotedNotifications(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onCheckedChange(true)
            LiveTrackingService.start(context)
        } else {
            onCheckedChange(false)
        }
    }

    val handleToggle: (Boolean) -> Unit = { isChecked ->
        if (enabled) {
            if (isChecked) {
                if (hasNotificationPermission()) {
                    onCheckedChange(true)
                    LiveTrackingService.start(context)
                } else {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                onCheckedChange(false)
                LiveTrackingService.stop(context)
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
                if (
                    enabled &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
                    !canPostPromoted
                ) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        AppStrings.liveNotificationPromotedDisabled,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(
                        onClick = {
                            try {
                                context.startActivity(LiveTrackingService.promotedSettingsIntent(context))
                            } catch (_: ActivityNotFoundException) {
                                context.startActivity(LiveTrackingService.appNotificationSettingsIntent(context))
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                    ) {
                        Text(AppStrings.liveNotificationPromotedSettings)
                    }
                }
            }

            if (useOneUi) {
                fr.geotower.ui.components.OneUiSwitch(
                    checked = checked,
                    onCheckedChange = handleToggle
                )
            } else {
                Switch(
                    checked = checked,
                    onCheckedChange = handleToggle,
                    enabled = enabled,
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
