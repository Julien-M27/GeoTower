package fr.geotower.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.geotower.R
import fr.geotower.data.api.LiveDatabaseStatus
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.db.DatabaseCountryStore
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LiveDatabaseUsageWarningDialog(liveFeatureId: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val activeCountry by DatabaseCountryStore.activeCountryState
    val featureFlags by RemoteFeatureFlags.config
    var isUsingLiveForCountry by remember(liveFeatureId, activeCountry) { mutableStateOf<Boolean?>(null) }
    var showDialog by remember(liveFeatureId, activeCountry) { mutableStateOf(true) }

    LaunchedEffect(activeCountry, featureFlags) {
        isUsingLiveForCountry = withContext(Dispatchers.IO) {
            LiveDatabaseStatus.isInUse(context, activeCountry)
        }
    }

    DisposableEffect(lifecycleOwner, liveFeatureId, activeCountry) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                showDialog = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isUsingLiveDatabase =
        isUsingLiveForCountry == true &&
            featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.LIVE_API_FR) &&
            featureFlags.isFeatureEnabled(liveFeatureId)

    if (showDialog && isUsingLiveDatabase) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    stringResource(R.string.live_database_warning_title),
                    style = sizing.textStyle(MaterialTheme.typography.headlineSmall)
                )
            },
            text = {
                Text(
                    stringResource(R.string.live_database_warning_desc),
                    style = sizing.textStyle(MaterialTheme.typography.bodyMedium)
                )
            },
            confirmButton = {
                Button(
                    onClick = { showDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        stringResource(R.string.common_continue),
                        style = sizing.textStyle(MaterialTheme.typography.labelLarge)
                    )
                }
            }
        )
    }
}
