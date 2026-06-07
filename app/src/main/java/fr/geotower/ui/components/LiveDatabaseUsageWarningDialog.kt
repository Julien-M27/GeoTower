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
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.db.GeoTowerDatabaseValidator
import fr.geotower.utils.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LiveDatabaseUsageWarningDialog(liveFeatureId: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val localDbState by AppConfig.localDatabaseState
    val featureFlags by RemoteFeatureFlags.config
    var showDialog by remember(liveFeatureId) { mutableStateOf(true) }

    LaunchedEffect(localDbState) {
        if (localDbState == null) {
            AppConfig.localDatabaseState.value = withContext(Dispatchers.IO) {
                GeoTowerDatabaseValidator.getInstalledDatabaseStatus(context).state
            }
        }
    }

    DisposableEffect(lifecycleOwner, liveFeatureId) {
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
        localDbState != null &&
            localDbState != GeoTowerDatabaseValidator.LocalDatabaseState.VALID &&
            featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.LIVE_API_FR) &&
            featureFlags.isFeatureEnabled(liveFeatureId)

    if (showDialog && isUsingLiveDatabase) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.live_database_warning_title)) },
            text = { Text(stringResource(R.string.live_database_warning_desc)) },
            confirmButton = {
                Button(
                    onClick = { showDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(stringResource(R.string.common_continue))
                }
            }
        )
    }
}
