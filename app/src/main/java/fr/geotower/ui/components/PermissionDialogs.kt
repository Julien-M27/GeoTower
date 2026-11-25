package fr.geotower.ui.components

import android.Manifest
import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.app.ActivityCompat
import fr.geotower.R
import fr.geotower.utils.LocationReadiness
import fr.geotower.utils.locationReadiness
import fr.geotower.utils.openAppLocationSettings
import fr.geotower.utils.openLocationSourceSettings

/**
 * Pop-up explicatif affiché avant d'envoyer l'utilisateur vers la page des paramètres Android
 * de l'application lorsqu'une permission a été refusée définitivement.
 */
@Composable
fun PermissionSettingsDialog(
    title: String,
    message: String,
    icon: ImageVector,
    confirmLabel: String = stringResource(R.string.permission_open_settings),
    dismissLabel: String = stringResource(R.string.common_cancel),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(imageVector = icon, contentDescription = null) },
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissLabel)
            }
        }
    )
}

/**
 * Retourne l'action à déclencher sur les boutons de correction de localisation (bandeau accueil,
 * boussole, antennes à proximité, carte).
 *
 * Si la permission a été refusée définitivement (« Ne plus demander »), affiche un pop-up
 * explicatif avant d'ouvrir les paramètres Android de l'application au lieu d'y envoyer
 * directement.
 */
@Composable
fun rememberLocationFixAction(
    locationReadinessState: MutableState<LocationReadiness>,
    context: Context = LocalContext.current
): () -> Unit {
    val activity = LocalActivity.current
    var showSettingsDialog by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationReadinessState.value = locationReadiness(context)
        if (!granted) {
            val canAskAgain = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_COARSE_LOCATION)
            } ?: false
            if (!canAskAgain) {
                showSettingsDialog = true
            }
        }
    }

    if (showSettingsDialog) {
        PermissionSettingsDialog(
            title = stringResource(R.string.permission_location_settings_title),
            message = stringResource(R.string.permission_location_settings_desc),
            icon = Icons.Default.LocationOn,
            onConfirm = {
                showSettingsDialog = false
                openAppLocationSettings(context)
            },
            onDismiss = {
                showSettingsDialog = false
            }
        )
    }

    return remember(locationReadinessState, context) {
        {
            when (locationReadinessState.value) {
                LocationReadiness.PermissionMissing -> locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
                LocationReadiness.ServicesOff -> openLocationSourceSettings(context)
                LocationReadiness.Ready -> {}
            }
        }
    }
}
