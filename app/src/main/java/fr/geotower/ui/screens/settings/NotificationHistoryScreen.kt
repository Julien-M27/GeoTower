package fr.geotower.ui.screens.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.geotower.R
import fr.geotower.data.notifications.NotificationHistoryEntry
import fr.geotower.data.notifications.NotificationHistoryStore
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.GeoTowerDateScrollbar
import fr.geotower.ui.components.PageScrollEdgeButtons
import fr.geotower.ui.components.formatHistoryDateTime
import fr.geotower.ui.components.formatHistoryStorageBytes
import fr.geotower.ui.components.geoTowerLazyListFadingEdge
import fr.geotower.ui.components.pageScrollbar
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppNotifications
import fr.geotower.utils.PageScrollPrefs
import fr.geotower.utils.PreferenceStores

/** Raccourci vers le journal des notifications, avec son compteur : pendant de [ShareHistoryShortcut]. */
@Composable
fun NotificationHistoryShortcut(
    cardShape: Shape,
    cardColor: Color,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
    val safeClick = rememberSafeClick()
    var historyCount by remember { mutableStateOf(0) }
    val sizing = LocalGeoTowerUiStyle.current.sizing

    LaunchedEffect(Unit) {
        historyCount = NotificationHistoryStore.read(context).size
    }

    Surface(
        onClick = { safeClick("notification_history_shortcut") { onOpenHistory() } },
        color = cardColor,
        shape = cardShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(sizing.spacing(16.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.notification_history_title),
                    style = sizing.textStyle(MaterialTheme.typography.titleSmall),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (historyCount == 0) {
                        stringResource(R.string.notification_history_none)
                    } else {
                        pluralStringResource(R.plurals.notification_history_recorded, historyCount, historyCount)
                    },
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Journal des notifications émises par l'application : téléchargements de bases, cartes hors ligne,
 * envois de photos, rappels de trajet, mises à jour. Chaque ligne rejoue au clic la destination que
 * portait la notification — la plupart des notifications de GeoTower partent alors que l'application
 * est fermée, et une notification balayée était jusqu'ici perdue.
 *
 * Les libellés sont reconstruits ici, à partir du type et du statut enregistrés : ce sont les mêmes
 * chaînes que celles des notifications, donc le journal se lit toujours dans la langue du moment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen(
    onNavigateBack: () -> Unit,
    onOpenEntry: (NotificationHistoryEntry) -> Unit
) {
    val safeClick = rememberSafeClick()
    val context = LocalContext.current
    val appContext = context.applicationContext
    val listState = rememberLazyListState()
    val prefs = remember(appContext) {
        appContext.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
    }
    var showSettingsSheet by remember { mutableStateOf(false) }
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCounter by remember { mutableStateOf(HistoryPagePreferences.read(prefs, HistoryPagePreferences.NOTIF_COUNTER)) }
    var showDetail by remember { mutableStateOf(HistoryPagePreferences.read(prefs, HistoryPagePreferences.NOTIF_DETAIL)) }
    var showDateBar by remember { mutableStateOf(HistoryPagePreferences.read(prefs, HistoryPagePreferences.NOTIF_DATE_BAR)) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var historyItems by remember { mutableStateOf<List<NotificationHistoryEntry>>(emptyList()) }
    var selectedIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    // Filtre par type : seuls les types réellement présents sont proposés. Rien de coché = tout
    // affiché ; le filtre ne survit pas volontairement à la sortie de la page.
    var typeFilter by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    // L'interrupteur maître peut être basculé pendant que la page est ouverte : on relit l'état à
    // chaque recomposition plutôt que de le figer.
    val notificationsMuted = !AppNotifications.canPost(appContext)

    val availableTypes = remember(historyItems) {
        val types = historyItems.map { it.type }.toSet()
        NOTIFICATION_HISTORY_TYPES.filter { it in types }
    }
    // Un type disparu de l'historique (tout supprimé) ne doit pas rester coché en douce.
    LaunchedEffect(availableTypes) {
        if (typeFilter.any { it !in availableTypes }) {
            typeFilter = typeFilter.filter { it in availableTypes }
        }
    }
    val visibleItems = remember(historyItems, typeFilter) {
        if (typeFilter.isEmpty()) historyItems else historyItems.filter { it.type in typeFilter }
    }
    val isFiltering = typeFilter.isNotEmpty()
    // Une entrée sélectionnée puis masquée par un changement de filtre partirait quand même à la
    // suppression, sans que rien ne l'annonce : on la retire de la sélection.
    LaunchedEffect(typeFilter) {
        val visibleIds = visibleItems.map { it.id }.toSet()
        if (selectedIds.any { it !in visibleIds }) {
            selectedIds = selectedIds.filter { it in visibleIds }
        }
    }
    val selectedIdSet = selectedIds.toSet()
    val isSelectionMode = selectedIds.isNotEmpty()
    val selectedItems = historyItems.filter { it.id in selectedIdSet }
    val selectedFreedBytes = NotificationHistoryStore.estimatedFreedBytes(selectedItems)
    val totalFreedBytes = NotificationHistoryStore.estimatedFreedBytes(historyItems)
    val isAllSelected = visibleItems.isNotEmpty() && visibleItems.all { it.id in selectedIdSet }

    fun reloadHistory() {
        val nextItems = NotificationHistoryStore.read(appContext)
        historyItems = nextItems
        val nextIds = nextItems.map { it.id }.toSet()
        selectedIds = selectedIds.filter { it in nextIds }
    }

    LaunchedEffect(appContext) {
        reloadHistory()
        // Ouvrir la page vaut lecture : la pastille du bouton d'accueil retombe à zéro. Marqué après
        // la lecture, pour que les entrées affichées soient bien celles qu'on déclare lues.
        NotificationHistoryStore.markAllRead(appContext)
    }

    BackHandler {
        if (isSelectionMode) {
            selectedIds = emptyList()
        } else {
            safeClick("notification_history_back") { onNavigateBack() }
        }
    }

    val themeMode by AppConfig.themeMode
    val isOled by AppConfig.isOledMode
    val isDark = themeMode == 2 || (themeMode == 0 && isSystemInDarkTheme())
    val pageColor = if (isDark && isOled) Color.Black else MaterialTheme.colorScheme.background
    val cardColor = if (AppConfig.useOneUiDesign) {
        if (isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    val cardShape = if (AppConfig.useOneUiDesign) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)
    val sizing = LocalGeoTowerUiStyle.current.sizing

    Scaffold(
        containerColor = pageColor,
        topBar = {
            if (isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(pageColor)
                        .padding(top = sizing.spacing(2.dp), bottom = sizing.spacing(6.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            safeClick("notification_history_toggle_all") {
                                // Sur la liste visible : sélectionner « tout » ne doit pas ramasser
                                // ce que le filtre cache.
                                selectedIds = if (isAllSelected) emptyList() else visibleItems.map { it.id }
                            }
                        },
                        modifier = Modifier.padding(start = sizing.spacing(4.dp))
                    ) {
                        Text(
                            if (isAllSelected) {
                                stringResource(R.string.appstrings_clear_all)
                            } else {
                                stringResource(R.string.appstrings_select_all)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { selectedIds = emptyList() },
                        modifier = Modifier.padding(end = sizing.spacing(4.dp))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.appstrings_cancel))
                    }
                }
            } else {
                GeoTowerBackTopBar(
                    title = stringResource(R.string.notification_history_title),
                    onBack = { safeClick("notification_history_back") { onNavigateBack() } },
                    backgroundColor = pageColor,
                    actions = {
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.appstrings_settings_title),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .geoTowerLazyListFadingEdge(listState)
                    .pageScrollbar(PageScrollPrefs.NOTIFICATION_HISTORY, listState),
                contentPadding = PaddingValues(
                    start = sizing.spacing(16.dp),
                    top = sizing.spacing(16.dp),
                    end = sizing.spacing(16.dp),
                    bottom = sizing.spacing(16.dp) +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                        if (isSelectionMode) sizing.spacing(76.dp) else 0.dp
                ),
                verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
            ) {
                // Notifications coupées : le journal continue de tout consigner, mais plus rien
                // n'apparaît dans la barre d'état. Le dire évite de croire l'app en panne.
                if (notificationsMuted) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ),
                            shape = cardShape,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(sizing.spacing(16.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.NotificationsOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(sizing.component(20.dp))
                                )
                                Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
                                Text(
                                    text = stringResource(R.string.notification_history_muted),
                                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }

                if (historyItems.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardColor),
                            shape = cardShape,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.notification_history_empty),
                                style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(sizing.spacing(16.dp))
                            )
                        }
                    }
                } else {
                    // Un seul type enregistré : la barre n'aurait rien à trier.
                    if (availableTypes.size > 1) {
                        item {
                            NotificationHistoryTypeFilterBar(
                                types = availableTypes,
                                selectedTypes = typeFilter,
                                onToggleType = { type ->
                                    typeFilter = if (type in typeFilter) {
                                        typeFilter.filterNot { it == type }
                                    } else {
                                        typeFilter + type
                                    }
                                },
                                onClear = { typeFilter = emptyList() }
                            )
                        }
                    }

                    if (showCounter) {
                        item {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.notification_history_recorded,
                                    visibleItems.size,
                                    visibleItems.size
                                ),
                                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(visibleItems, key = { it.id }) { item ->
                        val isSelected = item.id in selectedIdSet
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f)
                                } else {
                                    cardColor
                                }
                            ),
                            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            shape = cardShape,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            NotificationHistoryRow(
                                item = item,
                                showDetail = showDetail,
                                isSelectionMode = isSelectionMode,
                                onOpen = {
                                    safeClick("notification_history_open_${item.id}") { onOpenEntry(item) }
                                },
                                onSelect = {
                                    if (item.id !in selectedIds) selectedIds = selectedIds + item.id
                                },
                                onToggleSelection = {
                                    selectedIds = if (item.id in selectedIds) {
                                        selectedIds.filterNot { it == item.id }
                                    } else {
                                        selectedIds + item.id
                                    }
                                }
                            )
                        }
                    }

                    // Ce bouton vide TOUT l'historique : le laisser sous une liste filtrée ferait
                    // croire qu'il ne supprime que ce qui est affiché.
                    if (!isSelectionMode && !isFiltering) {
                        item {
                            TextButton(
                                onClick = {
                                    safeClick("notification_history_clear_open") { showClearDialog = true }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = sizing.spacing(8.dp), bottom = sizing.spacing(24.dp))
                            ) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(sizing.component(18.dp))
                                )
                                Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                                Text(
                                    "${stringResource(R.string.notification_history_clear)} " +
                                        "(${formatHistoryStorageBytes(totalFreedBytes)})"
                                )
                            }
                        }
                    }
                }
            }

            PageScrollEdgeButtons(PageScrollPrefs.NOTIFICATION_HISTORY, listState)

            GeoTowerDateScrollbar(
                listState = listState,
                timestamps = remember(visibleItems, availableTypes, showCounter, showDateBar, notificationsMuted) {
                    // Le bandeau, la barre de filtres et le compteur occupent les premiers index de
                    // la liste : on les aligne sur la première entrée pour que la bulle donne la
                    // bonne date dès le haut, sinon tout l'index est décalé.
                    val timestamps = visibleItems.map { it.createdAtMillis }
                    val leading = (if (notificationsMuted) 1 else 0) +
                        (if (availableTypes.size > 1) 1 else 0) +
                        (if (showCounter) 1 else 0)
                    when {
                        !showDateBar || timestamps.isEmpty() -> emptyList()
                        else -> List(leading) { timestamps.first() } + timestamps
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(
                        top = sizing.spacing(12.dp),
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                            sizing.spacing(12.dp)
                    )
            )

            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = sizing.spacing(16.dp),
                            end = sizing.spacing(16.dp),
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                sizing.spacing(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            safeClick("notification_history_delete_selected") {
                                NotificationHistoryStore.removeEntries(appContext, selectedIds)
                                selectedIds = emptyList()
                                reloadHistory()
                            }
                        },
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(sizing.component(18.dp))
                        )
                        Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                        Text(
                            "${stringResource(R.string.appstrings_delete)} (${selectedIds.size}) - " +
                                formatHistoryStorageBytes(selectedFreedBytes)
                        )
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        HistoryPageSettingsSheet(
            title = stringResource(R.string.notification_history_settings_title),
            page = PageScrollPrefs.NOTIFICATION_HISTORY,
            options = listOf(
                HistoryPageOption(
                    title = stringResource(R.string.notification_history_option_counter),
                    checked = showCounter,
                    onCheckedChange = {
                        showCounter = it
                        HistoryPagePreferences.write(prefs, HistoryPagePreferences.NOTIF_COUNTER, it)
                    }
                ),
                HistoryPageOption(
                    title = stringResource(R.string.notification_history_option_detail),
                    checked = showDetail,
                    onCheckedChange = {
                        showDetail = it
                        HistoryPagePreferences.write(prefs, HistoryPagePreferences.NOTIF_DETAIL, it)
                    }
                ),
                HistoryPageOption(
                    title = stringResource(R.string.history_option_date_bar),
                    checked = showDateBar,
                    onCheckedChange = {
                        showDateBar = it
                        HistoryPagePreferences.write(prefs, HistoryPagePreferences.NOTIF_DATE_BAR, it)
                    }
                )
            ),
            onReset = {
                showCounter = HistoryPagePreferences.DEFAULT_ENABLED
                showDetail = HistoryPagePreferences.DEFAULT_ENABLED
                showDateBar = HistoryPagePreferences.DEFAULT_ENABLED
                HistoryPagePreferences.write(prefs, HistoryPagePreferences.NOTIF_COUNTER, HistoryPagePreferences.DEFAULT_ENABLED)
                HistoryPagePreferences.write(prefs, HistoryPagePreferences.NOTIF_DETAIL, HistoryPagePreferences.DEFAULT_ENABLED)
                HistoryPagePreferences.write(prefs, HistoryPagePreferences.NOTIF_DATE_BAR, HistoryPagePreferences.DEFAULT_ENABLED)
            },
            onDismiss = { showSettingsSheet = false },
            onBack = { showSettingsSheet = false },
            sheetState = settingsSheetState,
            useOneUi = AppConfig.useOneUiDesign,
            bubbleColor = cardColor
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.notification_history_clear_title)) },
            text = { Text(stringResource(R.string.notification_history_clear_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        safeClick("notification_history_clear_confirm") {
                            NotificationHistoryStore.clear(appContext)
                            reloadHistory()
                            showClearDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.notification_history_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.appstrings_cancel))
                }
            }
        )
    }
}

/** Types proposés au filtre, dans l'ordre d'affichage. */
private val NOTIFICATION_HISTORY_TYPES: List<String> = listOf(
    NotificationHistoryStore.TYPE_DB_MOBILE,
    NotificationHistoryStore.TYPE_DB_RADIO,
    NotificationHistoryStore.TYPE_DB_ENB,
    NotificationHistoryStore.TYPE_DB_LOCAL_BUILD,
    NotificationHistoryStore.TYPE_DB_UPDATE,
    NotificationHistoryStore.TYPE_APP_UPDATE,
    NotificationHistoryStore.TYPE_MAP_DOWNLOAD,
    NotificationHistoryStore.TYPE_OUTAGES,
    NotificationHistoryStore.TYPE_PHOTO_UPLOAD,
    NotificationHistoryStore.TYPE_PHOTO_REPORT,
    NotificationHistoryStore.TYPE_TRIP_REMINDER,
    NotificationHistoryStore.TYPE_TRIP_ARRIVAL,
    NotificationHistoryStore.TYPE_PDF_REPORT
)

/** Barre de puces en tête de liste : une par type présent, plus « Tout » pour revenir en arrière. */
@Composable
private fun NotificationHistoryTypeFilterBar(
    types: List<String>,
    selectedTypes: List<String>,
    onToggleType: (String) -> Unit,
    onClear: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val safeClick = rememberSafeClick()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedTypes.isEmpty(),
            onClick = { safeClick("notification_history_filter_all") { onClear() } },
            label = {
                Text(
                    text = stringResource(R.string.notification_history_filter_all),
                    style = sizing.textStyle(MaterialTheme.typography.labelLarge)
                )
            }
        )
        types.forEach { type ->
            FilterChip(
                selected = type in selectedTypes,
                onClick = { safeClick("notification_history_filter_$type") { onToggleType(type) } },
                leadingIcon = {
                    Icon(
                        notificationTypeIcon(type),
                        contentDescription = null,
                        modifier = Modifier.size(sizing.component(16.dp))
                    )
                },
                label = {
                    Text(
                        text = notificationTypeLabel(type),
                        style = sizing.textStyle(MaterialTheme.typography.labelLarge),
                        maxLines = 1
                    )
                }
            )
        }
    }
}

/**
 * Une ligne du journal : icône du type, titre, phrase de la notification d'origine, date. L'appui
 * long ouvre la sélection multiple, comme sur les autres historiques.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationHistoryRow(
    item: NotificationHistoryEntry,
    showDetail: Boolean,
    isSelectionMode: Boolean,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onToggleSelection: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val statusColor = when (item.status) {
        NotificationHistoryStore.STATUS_ERROR -> MaterialTheme.colorScheme.error
        NotificationHistoryStore.STATUS_SUCCESS -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelection() else onOpen() },
                onLongClick = onSelect
            )
            .padding(sizing.spacing(16.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(sizing.component(40.dp))
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                notificationTypeIcon(item.type),
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(sizing.component(22.dp))
            )
        }
        Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = notificationTypeLabel(item.type),
                style = sizing.textStyle(MaterialTheme.typography.titleSmall),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = notificationEntryDescription(item),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatHistoryDateTime(item.createdAtMillis),
                    style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Événement consigné alors que la notification n'a pas pu partir : c'est justement
                // celui que l'utilisateur n'a jamais vu, il mérite d'être signalé comme tel.
                if (showDetail && !item.posted) {
                    Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                    Icon(
                        Icons.Default.NotificationsOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(sizing.component(12.dp))
                    )
                    Spacer(modifier = Modifier.width(sizing.spacing(4.dp)))
                    Text(
                        text = stringResource(R.string.notification_history_not_shown),
                        style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun notificationTypeIcon(type: String): ImageVector = when (type) {
    NotificationHistoryStore.TYPE_DB_MOBILE -> Icons.Default.Storage
    NotificationHistoryStore.TYPE_DB_RADIO -> Icons.Default.Radio
    NotificationHistoryStore.TYPE_DB_ENB -> Icons.Default.Tag
    NotificationHistoryStore.TYPE_DB_LOCAL_BUILD -> Icons.Default.Build
    NotificationHistoryStore.TYPE_DB_UPDATE -> Icons.Default.Download
    NotificationHistoryStore.TYPE_APP_UPDATE -> Icons.Default.SystemUpdate
    NotificationHistoryStore.TYPE_MAP_DOWNLOAD -> Icons.Default.Map
    NotificationHistoryStore.TYPE_OUTAGES -> Icons.Default.Warning
    NotificationHistoryStore.TYPE_PHOTO_UPLOAD -> Icons.Default.PhotoLibrary
    NotificationHistoryStore.TYPE_PHOTO_REPORT -> Icons.Default.Flag
    NotificationHistoryStore.TYPE_TRIP_REMINDER -> Icons.Default.Route
    NotificationHistoryStore.TYPE_TRIP_ARRIVAL -> Icons.Default.AddAPhoto
    NotificationHistoryStore.TYPE_PDF_REPORT -> Icons.Default.PictureAsPdf
    else -> Icons.Default.Notifications
}

@Composable
private fun notificationTypeLabel(type: String): String = when (type) {
    NotificationHistoryStore.TYPE_DB_MOBILE -> stringResource(R.string.notification_history_type_db_mobile)
    NotificationHistoryStore.TYPE_DB_RADIO -> stringResource(R.string.notification_history_type_db_radio)
    NotificationHistoryStore.TYPE_DB_ENB -> stringResource(R.string.notification_history_type_db_enb)
    NotificationHistoryStore.TYPE_DB_LOCAL_BUILD -> stringResource(R.string.notification_history_type_db_local_build)
    NotificationHistoryStore.TYPE_DB_UPDATE -> stringResource(R.string.notification_history_type_db_update)
    NotificationHistoryStore.TYPE_APP_UPDATE -> stringResource(R.string.notification_history_type_app_update)
    NotificationHistoryStore.TYPE_MAP_DOWNLOAD -> stringResource(R.string.notification_history_type_map_download)
    NotificationHistoryStore.TYPE_OUTAGES -> stringResource(R.string.notification_history_type_outages)
    NotificationHistoryStore.TYPE_PHOTO_UPLOAD -> stringResource(R.string.notification_history_type_photo_upload)
    NotificationHistoryStore.TYPE_PHOTO_REPORT -> stringResource(R.string.notification_history_type_photo_report)
    NotificationHistoryStore.TYPE_TRIP_REMINDER -> stringResource(R.string.notification_history_type_trip_reminder)
    NotificationHistoryStore.TYPE_TRIP_ARRIVAL -> stringResource(R.string.notification_history_type_trip_arrival)
    NotificationHistoryStore.TYPE_PDF_REPORT -> stringResource(R.string.notification_history_type_pdf_report)
    else -> type
}

/**
 * La phrase affichée sous le titre : ce sont les chaînes des notifications elles-mêmes, réassemblées
 * à partir des paramètres bruts enregistrés. Le journal dit donc mot pour mot ce que disait la
 * notification, dans la langue en cours et non dans celle du jour où elle est partie.
 */
@Composable
private fun notificationEntryDescription(item: NotificationHistoryEntry): String {
    val isError = item.status == NotificationHistoryStore.STATUS_ERROR
    return when (item.type) {
        NotificationHistoryStore.TYPE_DB_MOBILE,
        NotificationHistoryStore.TYPE_DB_RADIO,
        NotificationHistoryStore.TYPE_DB_ENB ->
            if (isError) {
                stringResource(R.string.notification_database_download_failed_content)
            } else {
                stringResource(R.string.notification_database_downloaded_content)
            }

        NotificationHistoryStore.TYPE_DB_LOCAL_BUILD ->
            if (isError) {
                stringResource(R.string.appstrings_local_build_notif_failed, item.detail.ifBlank { "?" })
            } else {
                stringResource(R.string.appstrings_local_build_notif_done)
            }

        NotificationHistoryStore.TYPE_DB_UPDATE ->
            if (item.detail == NotificationHistoryStore.DETAIL_DB_UPDATE_REBUILD) {
                stringResource(R.string.notification_db_update_available_desc_rebuild)
            } else {
                stringResource(R.string.notification_db_update_available_desc)
            }

        NotificationHistoryStore.TYPE_APP_UPDATE ->
            stringResource(R.string.notification_app_update_available_desc, item.label)

        NotificationHistoryStore.TYPE_MAP_DOWNLOAD ->
            stringResource(R.string.notification_map_downloaded_content, item.label)

        NotificationHistoryStore.TYPE_OUTAGES ->
            if (isError) {
                stringResource(R.string.outage_gen_notif_failed, item.detail.ifBlank { "?" })
            } else {
                stringResource(R.string.outage_gen_notif_done, item.itemCount)
            }

        NotificationHistoryStore.TYPE_PHOTO_UPLOAD -> {
            // `itemCount` = photos parties, `detail` = total brut. Les trois plurals sont ceux des
            // notifications d'envoi, et prennent tous (envoyées, total) avec le total en quantité.
            val total = item.detail.toIntOrNull() ?: item.itemCount
            val plural = when {
                item.status == NotificationHistoryStore.STATUS_INFO -> R.plurals.notification_upload_cancelled
                isError -> R.plurals.notification_upload_partial
                else -> R.plurals.notification_upload_success
            }
            val result = pluralStringResource(plural, total, item.itemCount, total)
            if (item.label.isBlank()) result else "${item.label} - $result"
        }

        NotificationHistoryStore.TYPE_PHOTO_REPORT ->
            if (item.status == NotificationHistoryStore.STATUS_INFO) {
                stringResource(R.string.notification_photo_report_removed_desc, item.label)
            } else {
                stringResource(R.string.notification_photo_report_sent_desc)
            }

        NotificationHistoryStore.TYPE_TRIP_REMINDER -> {
            // `label` = nom du trajet (vide si sans titre), `detail` = date de départ en millis.
            val departure = item.detail.toLongOrNull()
                ?.let { formatHistoryDateTime(it) }
                .orEmpty()
            val name = item.label.ifBlank { stringResource(R.string.trips_untitled) }
            val text = stringResource(R.string.trips_reminder_text, departure)
            "$name - $text"
        }

        NotificationHistoryStore.TYPE_TRIP_ARRIVAL -> {
            // `label` = nom de l'étape, déjà mis en forme au moment de la notification.
            val title = stringResource(R.string.trips_arrival_notification_title, item.label)
            "$title - ${stringResource(R.string.trips_arrival_notification_text)}"
        }

        NotificationHistoryStore.TYPE_PDF_REPORT ->
            stringResource(R.string.appstrings_pdf_downloaded_desc, item.label)

        else -> item.label
    }
}
