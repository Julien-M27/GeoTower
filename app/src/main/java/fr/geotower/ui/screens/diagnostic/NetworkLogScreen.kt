@file:OptIn(ExperimentalMaterial3Api::class)
package fr.geotower.ui.screens.diagnostic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import fr.geotower.R
import fr.geotower.data.netlog.NetworkLogStatusFilter
import fr.geotower.data.netlog.NetworkLogStore
import fr.geotower.data.netlog.NetworkRequestEntry
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.geoTowerLazyListFadingEdge
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NetworkLogScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val safeBack = rememberSafeBackNavigation(navController)

    BackHandler { safeBack.navigateBack() }

    var requests by remember { mutableStateOf<List<NetworkRequestEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(NetworkLogStatusFilter.ALL) }
    var selectedRequestForDetail by remember { mutableStateOf<NetworkRequestEntry?>(null) }
    var showClearConfirmation by remember { mutableStateOf(false) }

    val retentionDays = remember { NetworkLogStore.getRetentionDays(context) }
    val retentionLabel = remember(retentionDays) {
        when (retentionDays) {
            1 -> context.getString(R.string.network_log_duration_1_day)
            7 -> context.getString(R.string.network_log_duration_7_days)
            15 -> context.getString(R.string.network_log_duration_15_days)
            30 -> context.getString(R.string.network_log_duration_30_days)
            90 -> context.getString(R.string.network_log_duration_3_months)
            180 -> context.getString(R.string.network_log_duration_6_months)
            365 -> context.getString(R.string.network_log_duration_12_months)
            else -> context.getString(R.string.network_log_duration_always)
        }
    }

    fun loadData() {
        scope.launch {
            isLoading = true
            val data = withContext(Dispatchers.IO) {
                NetworkLogStore.getRequests(
                    context = context,
                    limit = 500,
                    searchQuery = searchQuery,
                    statusFilter = selectedFilter
                )
            }
            requests = data
            isLoading = false
        }
    }

    LaunchedEffect(searchQuery, selectedFilter) {
        loadData()
    }

    Scaffold(
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.network_log_screen_title),
                onBack = { safeBack.navigateBack() },
                actions = {
                    IconButton(
                        onClick = {
                            val ok = NetworkLogStore.shareLog(
                                context,
                                context.getString(R.string.network_log_share)
                            )
                            if (!ok) {
                                Toast.makeText(context, R.string.network_log_empty, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = requests.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = stringResource(R.string.network_log_share),
                            tint = if (requests.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                    IconButton(
                        onClick = { showClearConfirmation = true },
                        enabled = requests.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = stringResource(R.string.network_log_clear),
                            tint = if (requests.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error.copy(alpha = 0.38f)
                        )
                    }
                }
            )
        },
        containerColor = uiStyle.backgroundColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Bandeau d'en-tête : recherche et filtres
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp)),
                verticalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp))
            ) {
                // Info rétention & nombre
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.network_log_requests_count, requests.size),
                        style = sizing.textStyle(MaterialTheme.typography.labelMedium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.network_log_retention_badge, retentionLabel),
                        style = sizing.textStyle(MaterialTheme.typography.labelMedium),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Champ de recherche
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            stringResource(R.string.network_log_search_placeholder),
                            style = sizing.textStyle(MaterialTheme.typography.bodySmall)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(sizing.component(18.dp))
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = null,
                                    modifier = Modifier.size(sizing.component(18.dp))
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(sizing.component(12.dp)),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Filtres de statut
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp))
                ) {
                    FilterChip(
                        selected = selectedFilter == NetworkLogStatusFilter.ALL,
                        onClick = { selectedFilter = NetworkLogStatusFilter.ALL },
                        label = { Text(stringResource(R.string.network_log_filter_all)) }
                    )
                    FilterChip(
                        selected = selectedFilter == NetworkLogStatusFilter.SUCCESS_2XX,
                        onClick = { selectedFilter = NetworkLogStatusFilter.SUCCESS_2XX },
                        label = { Text(stringResource(R.string.network_log_filter_success)) }
                    )
                    FilterChip(
                        selected = selectedFilter == NetworkLogStatusFilter.ERRORS,
                        onClick = { selectedFilter = NetworkLogStatusFilter.ERRORS },
                        label = { Text(stringResource(R.string.network_log_filter_errors)) }
                    )
                }
            }

            // Liste des requêtes
            if (requests.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(sizing.spacing(32.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp))
                    ) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(sizing.component(48.dp)),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = stringResource(R.string.network_log_empty),
                            style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.network_log_empty_desc),
                            style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .geoTowerLazyListFadingEdge(listState)
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(
                        horizontal = sizing.spacing(16.dp),
                        vertical = sizing.spacing(8.dp)
                    ),
                    verticalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp))
                ) {
                    items(requests, key = { it.id }) { item ->
                        NetworkRequestRowItem(
                            item = item,
                            onClick = { selectedRequestForDetail = item }
                        )
                    }
                }
            }
        }
    }

    // Dialogue de confirmation de vidage
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(stringResource(R.string.network_log_clear_confirm_title)) },
            text = { Text(stringResource(R.string.network_log_clear_confirm_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        NetworkLogStore.clear(context)
                        loadData()
                        Toast.makeText(context, R.string.network_log_cleared, Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(
                        stringResource(R.string.network_log_clear),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Dialogue de détail de la requête
    selectedRequestForDetail?.let { req ->
        NetworkRequestDetailDialog(
            entry = req,
            onDismiss = { selectedRequestForDetail = null }
        )
    }
}

@Composable
private fun NetworkRequestRowItem(
    item: NetworkRequestEntry,
    onClick: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val timeStr = remember(item.timestamp) { timeFormat.format(Date(item.timestamp)) }

    val statusColor = when {
        item.isSuccess -> Color(0xFF2E7D32) // Vert
        item.isRedirect -> Color(0xFF1976D2) // Bleu
        item.isClientError -> Color(0xFFE65100) // Orange
        else -> Color(0xFFC62828) // Rouge (5xx ou erreur réseau)
    }

    val statusBg = when {
        item.isSuccess -> Color(0xFFE8F5E9)
        item.isRedirect -> Color(0xFFE3F2FD)
        item.isClientError -> Color(0xFFFFF3E0)
        else -> Color(0xFFFFEBEE)
    }

    val statusLabel = if (item.statusCode > 0) "${item.statusCode}" else "ERR"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(sizing.component(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            sizing.component(1.dp),
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(sizing.spacing(12.dp)),
            verticalArrangement = Arrangement.spacedBy(sizing.spacing(6.dp))
        ) {
            // Ligne 1 : Méthode, Code statut, Durée, Horodatage
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge Méthode (GET, POST...)
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(sizing.component(4.dp))
                ) {
                    Text(
                        text = item.method,
                        modifier = Modifier.padding(horizontal = sizing.spacing(6.dp), vertical = sizing.spacing(2.dp)),
                        style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(sizing.spacing(6.dp)))

                // Badge Statut (200, 404, 500...)
                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(sizing.component(4.dp))
                ) {
                    Text(
                        text = statusLabel,
                        modifier = Modifier.padding(horizontal = sizing.spacing(6.dp), vertical = sizing.spacing(2.dp)),
                        style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))

                // Durée (latence)
                Text(
                    text = "${item.durationMs} ms",
                    style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                    color = if (item.durationMs > 1000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (item.responseSizeBytes > 0) {
                    Spacer(modifier = Modifier.width(sizing.spacing(6.dp)))
                    Text(
                        text = "· ${Formatter.formatShortFileSize(LocalContext.current, item.responseSizeBytes)}",
                        style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Heure
                Text(
                    text = timeStr,
                    style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Ligne 2 : Fonctionnalité / Rôle de la requête
            Text(
                text = item.displayFeature,
                style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Ligne 3 : Chemin & Domaine
            Text(
                text = item.path.ifBlank { "/" },
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = item.host,
                style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Ligne 4 (si erreur) : message d'erreur
            if (!item.errorMessage.isNullOrBlank()) {
                Text(
                    text = item.errorMessage,
                    style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NetworkRequestDetailDialog(
    entry: NetworkRequestEntry,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US) }
    val dateStr = remember(entry.timestamp) { dateFormat.format(Date(entry.timestamp)) }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, R.string.network_log_copied, Toast.LENGTH_SHORT).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.network_log_detail_title),
                style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = sizing.spacing(4.dp)),
                verticalArrangement = Arrangement.spacedBy(sizing.spacing(10.dp))
            ) {
                // Méthode et Statut
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            stringResource(R.string.network_log_detail_method),
                            style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            entry.method,
                            style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(R.string.network_log_detail_status),
                            style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (entry.statusCode > 0) "${entry.statusCode}" else "ERR",
                            style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                            fontWeight = FontWeight.Bold,
                            color = if (entry.isSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Fonctionnalité / Rôle
                Column {
                    Text(
                        stringResource(R.string.network_log_detail_feature),
                        style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        entry.displayFeature,
                        style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Date et heure
                Column {
                    Text(
                        stringResource(R.string.network_log_detail_date),
                        style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        dateStr,
                        style = sizing.textStyle(MaterialTheme.typography.bodySmall)
                    )
                }

                // Durée et Taille
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            stringResource(R.string.network_log_detail_duration),
                            style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${entry.durationMs} ms",
                            style = sizing.textStyle(MaterialTheme.typography.bodyMedium)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(R.string.network_log_detail_size),
                            style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (entry.responseSizeBytes >= 0) Formatter.formatShortFileSize(context, entry.responseSizeBytes) else "-",
                            style = sizing.textStyle(MaterialTheme.typography.bodyMedium)
                        )
                    }
                }

                // URL
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.network_log_detail_url),
                            style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = { copyToClipboard("URL", entry.url) },
                            modifier = Modifier.size(sizing.component(24.dp))
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.network_log_copy_url),
                                modifier = Modifier.size(sizing.component(16.dp))
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(sizing.component(8.dp))
                    ) {
                        Text(
                            text = entry.url,
                            modifier = Modifier.padding(sizing.spacing(8.dp)),
                            style = sizing.textStyle(MaterialTheme.typography.bodySmall).copy(fontFamily = FontFamily.Monospace),
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Message d'erreur éventuel
                if (!entry.errorMessage.isNullOrBlank()) {
                    Column {
                        Text(
                            stringResource(R.string.network_log_detail_error),
                            style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                            color = MaterialTheme.colorScheme.error
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(sizing.component(8.dp))
                        ) {
                            Text(
                                text = entry.errorMessage,
                                modifier = Modifier.padding(sizing.spacing(8.dp)),
                                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val fullDetails = buildString {
                        append("Feature: ${entry.displayFeature}\n")
                        append("URL: ${entry.url}\n")
                        append("Method: ${entry.method}\n")
                        append("Status: ${entry.statusCode}\n")
                        append("Duration: ${entry.durationMs} ms\n")
                        append("Date: $dateStr\n")
                        if (entry.responseSizeBytes >= 0) append("Size: ${entry.responseSizeBytes} B\n")
                        if (!entry.errorMessage.isNullOrBlank()) append("Error: ${entry.errorMessage}\n")
                    }
                    copyToClipboard("Request details", fullDetails)
                }
            ) {
                Text(stringResource(R.string.network_log_copy_details))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.appstrings_close))
            }
        }
    )
}
