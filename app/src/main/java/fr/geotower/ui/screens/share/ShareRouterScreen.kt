package fr.geotower.ui.screens.share

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.geotower.R
import fr.geotower.data.speedtest.SpeedtestRepository
import fr.geotower.data.upload.SignalQuestUploadDraftStore
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import kotlinx.coroutines.launch

@Composable
fun ShareRouterScreen(
    draftId: String,
    onSendToSitePhoto: (String) -> Unit,
    onImportedToSpeedtests: (Int) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val scope = rememberCoroutineScope()

    val uriStrings = remember(draftId) { SignalQuestUploadDraftStore.peek(draftId) }
    val uris = remember(uriStrings) { uriStrings.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() } }

    var isImporting by remember { mutableStateOf(false) }

    LaunchedEffect(uris) {
        if (uris.isEmpty()) {
            SignalQuestUploadDraftStore.discard(draftId)
            onCancel()
        }
    }

    val handleCancel: () -> Unit = {
        if (!isImporting) {
            SignalQuestUploadDraftStore.discard(draftId)
            onCancel()
        }
    }

    BackHandler {
        handleCancel()
    }

    Scaffold(
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.share_router_title),
                onBack = handleCancel
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(sizing.spacing(16.dp)),
            verticalArrangement = Arrangement.spacedBy(sizing.spacing(16.dp))
        ) {
            Text(
                text = pluralStringResource(R.plurals.share_router_subtitle, uris.size, uris.size),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Aperçu miniature(s)
            if (uris.size == 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = uris.first(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else if (uris.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(sizing.spacing(10.dp)),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(uris) { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(130.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }
            }

            // Destination 1 : Photo de site SignalQuest
            ShareDestinationCard(
                title = stringResource(R.string.share_router_dest_site_photo_title),
                subtitle = stringResource(R.string.share_router_dest_site_photo_desc),
                icon = Icons.Default.AddAPhoto,
                enabled = !isImporting,
                onClick = { onSendToSitePhoto(draftId) }
            )

            // Destination 2 : Speedtests
            ShareDestinationCard(
                title = stringResource(R.string.share_router_dest_speedtest_title),
                subtitle = stringResource(R.string.share_router_dest_speedtest_desc),
                icon = Icons.Default.Speed,
                enabled = !isImporting,
                isLoading = isImporting,
                onClick = {
                    if (isImporting) return@ShareDestinationCard
                    isImporting = true
                    scope.launch {
                        val imported = SpeedtestRepository.importImages(context, uris)
                        SignalQuestUploadDraftStore.discard(draftId)
                        isImporting = false
                        onImportedToSpeedtests(imported.size)
                    }
                }
            )

            Spacer(modifier = Modifier.height(sizing.spacing(4.dp)))

            OutlinedButton(
                onClick = handleCancel,
                enabled = !isImporting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(text = stringResource(R.string.common_cancel))
            }
        }
    }
}

@Composable
private fun ShareDestinationCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(sizing.spacing(16.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(sizing.spacing(14.dp)))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
