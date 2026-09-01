package fr.geotower.ui.screens.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VerticalAlignTop
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
import fr.geotower.data.share.ShareHistoryEntry
import fr.geotower.data.share.ShareHistoryStore
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.PageScrollEdgeButtons
import fr.geotower.ui.components.formatHistoryDay
import fr.geotower.ui.components.formatHistoryDateTime
import fr.geotower.ui.components.formatHistoryStorageBytes
import fr.geotower.ui.components.geoTowerLazyListFadingEdge
import fr.geotower.ui.components.pageScrollbar
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.PageScrollPrefs
import fr.geotower.utils.PreferenceStores
import java.util.Locale

/** Raccourci vers l'historique des partages, avec son compteur : pendant de PhotoUploadHistoryShortcut. */
@Composable
fun ShareHistoryShortcut(
    cardShape: Shape,
    cardColor: Color,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
    val safeClick = rememberSafeClick()
    var historyCount by remember { mutableStateOf(0) }
    val sizing = LocalGeoTowerUiStyle.current.sizing

    LaunchedEffect(Unit) {
        historyCount = ShareHistoryStore.read(context).size
    }

    Surface(
        onClick = { safeClick("share_history_shortcut") { onOpenHistory() } },
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
            Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.share_history_title),
                    style = sizing.textStyle(MaterialTheme.typography.titleSmall),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (historyCount == 0) {
                        stringResource(R.string.share_history_none)
                    } else {
                        pluralStringResource(R.plurals.share_history_recorded, historyCount, historyCount)
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
 * Historique des sites et supports partagés ou exportés en PDF, sur le modèle de l'historique des
 * photos envoyées : chaque ligne annonce de quoi il s'agit (site mobile, support, radio) et le
 * clic rouvre la fiche correspondante avec toutes ses informations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareHistoryScreen(
    onNavigateBack: () -> Unit,
    onOpenEntry: (ShareHistoryEntry) -> Unit
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
    var showCounter by remember { mutableStateOf(HistoryPagePreferences.read(prefs, HistoryPagePreferences.SHARE_COUNTER)) }
    var showAddress by remember { mutableStateOf(HistoryPagePreferences.read(prefs, HistoryPagePreferences.SHARE_ADDRESS)) }
    var showContents by remember { mutableStateOf(HistoryPagePreferences.read(prefs, HistoryPagePreferences.SHARE_CONTENTS)) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var historyItems by remember { mutableStateOf<List<ShareHistoryEntry>>(emptyList()) }
    var selectedIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    // Filtre par sorte : les familles réellement présentes dans l'historique, cochées librement.
    // Rien de coché = tout affiché ; le filtre ne survit pas volontairement à la sortie de la page.
    var kindFilter by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val availableFamilies = remember(historyItems) {
        val kinds = historyItems.map { it.kind }.toSet()
        SHARE_HISTORY_FAMILIES.filter { family -> family.any { it in kinds } }
    }
    // Une famille disparue de l'historique (tout supprimé) ne doit pas rester cochée en douce.
    LaunchedEffect(availableFamilies) {
        val stillThere = availableFamilies.flatten().toSet()
        if (kindFilter.any { it !in stillThere }) {
            kindFilter = kindFilter.filter { it in stillThere }
        }
    }
    val visibleItems = remember(historyItems, kindFilter) {
        if (kindFilter.isEmpty()) historyItems else historyItems.filter { it.kind in kindFilter }
    }
    val isFiltering = kindFilter.isNotEmpty()
    // Une entrée sélectionnée puis masquée par un changement de filtre partirait quand même à la
    // suppression, sans que rien ne l'annonce : on la retire de la sélection.
    LaunchedEffect(kindFilter) {
        val visibleIds = visibleItems.map { it.id }.toSet()
        if (selectedIds.any { it !in visibleIds }) {
            selectedIds = selectedIds.filter { it in visibleIds }
        }
    }
    val selectedIdSet = selectedIds.toSet()
    val isSelectionMode = selectedIds.isNotEmpty()
    val selectedItems = historyItems.filter { it.id in selectedIdSet }
    val selectedFreedBytes = ShareHistoryStore.estimatedFreedBytes(selectedItems)
    val totalFreedBytes = ShareHistoryStore.estimatedFreedBytes(historyItems)
    val isAllSelected = visibleItems.isNotEmpty() && visibleItems.all { it.id in selectedIdSet }

    fun reloadHistory() {
        val nextItems = ShareHistoryStore.read(appContext)
        historyItems = nextItems
        val nextIds = nextItems.map { it.id }.toSet()
        selectedIds = selectedIds.filter { it in nextIds }
    }

    LaunchedEffect(appContext) { reloadHistory() }

    BackHandler {
        if (isSelectionMode) {
            selectedIds = emptyList()
        } else {
            safeClick("share_history_back") { onNavigateBack() }
        }
    }

    val themeMode by AppConfig.themeMode
    val isOled by AppConfig.isOledMode
    val appLanguage = AppConfig.appLanguage.value
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
                            safeClick("share_history_toggle_all") {
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
                    title = stringResource(R.string.share_history_title),
                    onBack = { safeClick("share_history_back") { onNavigateBack() } },
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
                    .pageScrollbar(PageScrollPrefs.SHARE_HISTORY, listState),
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
                if (historyItems.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardColor),
                            shape = cardShape,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.share_history_empty),
                                style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(sizing.spacing(16.dp))
                            )
                        }
                    }
                } else {
                    // Une seule famille enregistrée : la barre n'aurait rien à trier.
                    if (availableFamilies.size > 1) {
                        item {
                            ShareHistoryKindFilterBar(
                                families = availableFamilies,
                                selectedKinds = kindFilter,
                                onToggleFamily = { family ->
                                    kindFilter = if (family.any { it in kindFilter }) {
                                        kindFilter.filterNot { it in family }
                                    } else {
                                        kindFilter + family
                                    }
                                },
                                onClear = { kindFilter = emptyList() }
                            )
                        }
                    }

                    if (showCounter) {
                        item {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.share_history_recorded,
                                    visibleItems.size,
                                    visibleItems.size
                                ),
                                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    itemsIndexed(visibleItems, key = { _, item -> item.id }) { index, item ->
                        val day = formatHistoryDay(context, item.createdAtMillis, appLanguage)
                        val previousDay = visibleItems.getOrNull(index - 1)?.createdAtMillis?.let {
                            formatHistoryDay(context, it, appLanguage)
                        }
                        if (index == 0 || day != previousDay) {
                            Text(
                                text = day,
                                style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = sizing.spacing(2.dp), vertical = sizing.spacing(6.dp))
                            )
                        }

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
                            ShareHistoryRow(
                                item = item,
                                showAddress = showAddress,
                                showContents = showContents,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onOpen = {
                                    safeClick("share_history_open_${item.id}") { onOpenEntry(item) }
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
                                    safeClick("share_history_clear_open") { showClearDialog = true }
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
                                    "${stringResource(R.string.share_history_clear)} " +
                                        "(${formatHistoryStorageBytes(totalFreedBytes)})"
                                )
                            }
                        }
                    }
                }
            }

            PageScrollEdgeButtons(PageScrollPrefs.SHARE_HISTORY, listState)

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
                            safeClick("share_history_delete_selected") {
                                ShareHistoryStore.removeEntries(appContext, selectedIds)
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
            title = stringResource(R.string.share_history_settings_title),
            page = PageScrollPrefs.SHARE_HISTORY,
            options = listOf(
                HistoryPageOption(
                    title = stringResource(R.string.share_history_option_counter),
                    checked = showCounter,
                    onCheckedChange = {
                        showCounter = it
                        HistoryPagePreferences.write(prefs, HistoryPagePreferences.SHARE_COUNTER, it)
                    }
                ),
                HistoryPageOption(
                    title = stringResource(R.string.share_history_option_address),
                    checked = showAddress,
                    onCheckedChange = {
                        showAddress = it
                        HistoryPagePreferences.write(prefs, HistoryPagePreferences.SHARE_ADDRESS, it)
                    }
                ),
                HistoryPageOption(
                    title = stringResource(R.string.share_history_option_contents),
                    checked = showContents,
                    onCheckedChange = {
                        showContents = it
                        HistoryPagePreferences.write(prefs, HistoryPagePreferences.SHARE_CONTENTS, it)
                    }
                ),
            ),
            onReset = {
                showCounter = HistoryPagePreferences.DEFAULT_ENABLED
                showAddress = HistoryPagePreferences.DEFAULT_ENABLED
                showContents = HistoryPagePreferences.DEFAULT_ENABLED
                HistoryPagePreferences.write(prefs, HistoryPagePreferences.SHARE_COUNTER, HistoryPagePreferences.DEFAULT_ENABLED)
                HistoryPagePreferences.write(prefs, HistoryPagePreferences.SHARE_ADDRESS, HistoryPagePreferences.DEFAULT_ENABLED)
                HistoryPagePreferences.write(prefs, HistoryPagePreferences.SHARE_CONTENTS, HistoryPagePreferences.DEFAULT_ENABLED)
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
            title = { Text(stringResource(R.string.share_history_clear_title)) },
            text = { Text(stringResource(R.string.share_history_clear_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        safeClick("share_history_clear_confirm") {
                            ShareHistoryStore.clear(appContext)
                            reloadHistory()
                            showClearDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.share_history_clear))
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

/**
 * Familles de sortes proposées au filtre, dans l'ordre d'affichage. Une famille = une puce : les
 * deux sortes de copie partagent un libellé, donc une seule puce les couvre toutes les deux.
 */
private val SHARE_HISTORY_FAMILIES: List<List<String>> = listOf(
    listOf(ShareHistoryStore.KIND_MOBILE_SITE),
    listOf(ShareHistoryStore.KIND_MOBILE_SUPPORT),
    listOf(ShareHistoryStore.KIND_RADIO_SITE),
    listOf(ShareHistoryStore.KIND_RADIO_SUPPORT),
    listOf(ShareHistoryStore.KIND_MAP),
    listOf(
        ShareHistoryStore.KIND_FIELD_COPY,
        ShareHistoryStore.KIND_SUPPORT_FIELD_COPY,
        ShareHistoryStore.KIND_RADIO_FIELD_COPY
    ),
    listOf(ShareHistoryStore.KIND_PHOTO),
    listOf(ShareHistoryStore.KIND_TRIP),
    listOf(ShareHistoryStore.KIND_SETTINGS_PROFILE),
    listOf(ShareHistoryStore.KIND_DIAGNOSTIC)
)

/** Barre de puces en tête de liste : une par sorte présente, plus « Tout » pour revenir en arrière. */
@Composable
private fun ShareHistoryKindFilterBar(
    families: List<List<String>>,
    selectedKinds: List<String>,
    onToggleFamily: (List<String>) -> Unit,
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
            selected = selectedKinds.isEmpty(),
            onClick = { safeClick("share_history_filter_all") { onClear() } },
            label = {
                Text(
                    text = stringResource(R.string.share_history_filter_all),
                    style = sizing.textStyle(MaterialTheme.typography.labelLarge)
                )
            }
        )
        families.forEach { family ->
            val isSelected = family.any { it in selectedKinds }
            FilterChip(
                selected = isSelected,
                onClick = { safeClick("share_history_filter_${family.first()}") { onToggleFamily(family) } },
                leadingIcon = {
                    Icon(
                        shareHistoryKindIcon(family.first()),
                        contentDescription = null,
                        modifier = Modifier.size(sizing.component(16.dp))
                    )
                },
                label = {
                    Text(
                        text = shareHistoryKindLabel(family.first()),
                        style = sizing.textStyle(MaterialTheme.typography.labelLarge),
                        maxLines = 1
                    )
                }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ShareHistoryRow(
    item: ShareHistoryEntry,
    showAddress: Boolean,
    showContents: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onToggleSelection: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val reference = shareHistoryReference(item)
    val details = listOfNotNull(
        item.label.takeIf { it.isNotBlank() },
        item.address.takeIf { showAddress && it.isNotBlank() }
    ).joinToString(" - ")
    val contents = if (showContents) shareHistoryContents(item) else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelection() else onOpen() },
                onLongClick = onSelect
            )
            .padding(horizontal = sizing.spacing(14.dp), vertical = sizing.spacing(12.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            ShareHistorySelectionIndicator(isSelected)
            Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
        }
        Box(
            modifier = Modifier
                .size(sizing.component(48.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                shareHistoryKindIcon(item.kind),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(sizing.component(24.dp))
            )
        }
        Spacer(modifier = Modifier.width(sizing.spacing(14.dp)))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(sizing.spacing(3.dp))
        ) {
            Text(
                text = shareHistoryKindLabel(item.kind),
                style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (reference.isNotBlank()) {
                Text(
                    text = reference,
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (details.isNotBlank()) {
                Text(
                    text = details,
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (contents.isNotBlank()) {
                Text(
                    text = contents,
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = formatHistoryDateTime(item.createdAtMillis),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = sizing.spacing(8.dp),
                        vertical = sizing.spacing(3.dp)
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        shareHistoryDestinationIcon(item.destination),
                        contentDescription = null,
                        modifier = Modifier.size(sizing.component(14.dp))
                    )
                    Spacer(modifier = Modifier.width(sizing.spacing(4.dp)))
                    Text(
                        text = shareHistoryDestinationLabel(item.destination),
                        style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareHistorySelectionIndicator(isSelected: Boolean) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Surface(
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
        border = if (isSelected) null else BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.size(sizing.component(24.dp))
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(sizing.component(16.dp)))
            }
        }
    }
}

/** Référence lisible : station pour un site, support (et nombre de stations) pour un support. */
@Composable
private fun shareHistoryReference(item: ShareHistoryEntry): String {
    val parts = mutableListOf<String>()
    when (item.kind) {
        ShareHistoryStore.KIND_MAP -> {
            // Une carte n'a ni station ni support : son repère, c'est son cadrage.
            val lat = item.latitude
            val lon = item.longitude
            if (lat != null && lon != null) {
                parts += String.format(Locale.US, "%.5f, %.5f", lat, lon)
            }
            item.mapZoom?.let {
                parts += stringResource(R.string.share_history_map_ref, String.format(Locale.US, "%.1f", it))
            }
        }
        ShareHistoryStore.KIND_TRIP -> {
            // Un trajet n'a ni station ni support : son repère, c'est son nom (déjà en sujet) et,
            // pour un export groupé qui n'en porte aucun, le nombre de trajets envoyés.
            if (item.itemCount > 1) {
                parts += pluralStringResource(R.plurals.share_history_trips, item.itemCount, item.itemCount)
            }
        }
        ShareHistoryStore.KIND_MOBILE_SUPPORT,
        ShareHistoryStore.KIND_RADIO_SUPPORT,
        ShareHistoryStore.KIND_SUPPORT_FIELD_COPY -> {
            item.supportId.takeIf { it.isNotBlank() }?.let {
                parts += stringResource(R.string.share_history_support_ref, it)
            }
            if (item.itemCount > 1) {
                parts += pluralStringResource(R.plurals.share_history_stations, item.itemCount, item.itemCount)
            }
        }
        else -> {
            item.stationId.takeIf { it.isNotBlank() }?.let {
                parts += stringResource(R.string.share_history_station_ref, it)
            }
            item.supportId.takeIf { it.isNotBlank() }?.let {
                parts += stringResource(R.string.share_history_support_ref, it)
            }
        }
    }
    return parts.joinToString(" - ")
}

@Composable
private fun shareHistoryKindLabel(kind: String): String = when (kind) {
    ShareHistoryStore.KIND_MOBILE_SUPPORT -> stringResource(R.string.share_history_kind_mobile_support)
    ShareHistoryStore.KIND_RADIO_SITE -> stringResource(R.string.share_history_kind_radio_site)
    ShareHistoryStore.KIND_RADIO_SUPPORT -> stringResource(R.string.share_history_kind_radio_support)
    ShareHistoryStore.KIND_MAP -> stringResource(R.string.share_history_kind_map)
    ShareHistoryStore.KIND_FIELD_COPY,
    ShareHistoryStore.KIND_SUPPORT_FIELD_COPY,
    ShareHistoryStore.KIND_RADIO_FIELD_COPY ->
        stringResource(R.string.share_history_kind_field_copy)
    ShareHistoryStore.KIND_PHOTO -> stringResource(R.string.share_history_kind_photo)
    ShareHistoryStore.KIND_TRIP -> stringResource(R.string.share_history_kind_trip)
    ShareHistoryStore.KIND_SETTINGS_PROFILE -> stringResource(R.string.share_history_kind_settings_profile)
    ShareHistoryStore.KIND_DIAGNOSTIC -> stringResource(R.string.share_history_kind_diagnostic)
    else -> stringResource(R.string.share_history_kind_mobile_site)
}

private fun shareHistoryKindIcon(kind: String): ImageVector = when (kind) {
    ShareHistoryStore.KIND_MOBILE_SUPPORT -> Icons.Default.VerticalAlignTop
    ShareHistoryStore.KIND_RADIO_SITE -> Icons.Default.Radio
    ShareHistoryStore.KIND_RADIO_SUPPORT -> Icons.Default.SettingsInputAntenna
    ShareHistoryStore.KIND_MAP -> Icons.Default.Map
    ShareHistoryStore.KIND_FIELD_COPY,
    ShareHistoryStore.KIND_SUPPORT_FIELD_COPY,
    ShareHistoryStore.KIND_RADIO_FIELD_COPY -> Icons.Default.ContentCopy
    ShareHistoryStore.KIND_PHOTO -> Icons.Default.Photo
    ShareHistoryStore.KIND_TRIP -> Icons.Default.Route
    ShareHistoryStore.KIND_SETTINGS_PROFILE -> Icons.Default.Tune
    ShareHistoryStore.KIND_DIAGNOSTIC -> Icons.Default.BugReport
    else -> Icons.Default.Tag
}

/**
 * Contenu du partage : les blocs cochés dans le menu, avec les libellés de ce menu (jamais une
 * traduction parallèle). Un code inconnu -- entrée écrite par une version plus récente, bloc retiré
 * depuis -- est simplement ignoré plutôt que d'afficher un identifiant technique.
 */
@Composable
private fun shareHistoryContents(item: ShareHistoryEntry): String {
    val labels = ShareHistoryStore.contentCodes(item.contents).mapNotNull { code ->
        when (code) {
            ShareHistoryStore.CONTENT_MAP -> stringResource(R.string.appstrings_share_map_option)
            ShareHistoryStore.CONTENT_ELEVATION_PROFILE -> stringResource(R.string.appstrings_share_elevation_profile_option)
            ShareHistoryStore.CONTENT_SUPPORT -> stringResource(R.string.appstrings_share_support_option)
            ShareHistoryStore.CONTENT_PHOTOS -> stringResource(R.string.appstrings_share_photos_option)
            ShareHistoryStore.CONTENT_IDS -> stringResource(R.string.appstrings_share_ids_option)
            ShareHistoryStore.CONTENT_DATES -> stringResource(R.string.appstrings_share_dates_option)
            ShareHistoryStore.CONTENT_ADDRESS -> stringResource(R.string.appstrings_share_address_option)
            ShareHistoryStore.CONTENT_SPEEDTEST -> stringResource(R.string.appstrings_share_speedtest_option)
            ShareHistoryStore.CONTENT_THROUGHPUT -> stringResource(R.string.appstrings_share_throughput_option)
            ShareHistoryStore.CONTENT_FREQ -> stringResource(R.string.appstrings_share_freq_option)
            ShareHistoryStore.CONTENT_STATUS -> stringResource(R.string.appstrings_share_status_option)
            ShareHistoryStore.CONTENT_HEIGHTS -> stringResource(R.string.appstrings_panel_heights_title)
            ShareHistoryStore.CONTENT_OPERATORS -> stringResource(R.string.appstrings_operators_title)
            ShareHistoryStore.CONTENT_RADIO_ENTRIES -> stringResource(R.string.appstrings_radio_share_block_support_entries)
            ShareHistoryStore.CONTENT_QR_CODE -> stringResource(R.string.brand_qr_code)
            ShareHistoryStore.CONTENT_CONFIDENTIAL -> stringResource(R.string.appstrings_share_confidential_option)
            ShareHistoryStore.CONTENT_SPLIT_IMAGE -> stringResource(R.string.appstrings_split_share_image)
            ShareHistoryStore.CONTENT_AZIMUTHS -> stringResource(R.string.appstrings_share_map_azimuths_option)
            ShareHistoryStore.CONTENT_SPEEDOMETER -> stringResource(R.string.appstrings_share_map_speedometer_option)
            ShareHistoryStore.CONTENT_SCALE -> stringResource(R.string.appstrings_share_map_scale_option)
            ShareHistoryStore.CONTENT_ATTRIBUTION -> stringResource(R.string.appstrings_share_map_attribution_option)
            ShareHistoryStore.CONTENT_COVERAGE -> stringResource(R.string.appstrings_coverage_button)
            ShareHistoryStore.CONTENT_OBSTACLES -> stringResource(R.string.appstrings_coverage_obstacles)
            // Formats d'export d'un trajet : des noms propres, écrits pareil dans les 7 langues.
            ShareHistoryStore.CONTENT_GPX -> "GPX"
            ShareHistoryStore.CONTENT_JSON -> "JSON"
            ShareHistoryStore.CONTENT_SUMMARY -> stringResource(R.string.appstrings_radio_share_block_summary)
            ShareHistoryStore.CONTENT_SOURCE -> stringResource(R.string.appstrings_radio_share_block_source)
            ShareHistoryStore.CONTENT_BEARING_HEIGHT -> stringResource(R.string.appstrings_radio_share_block_bearing_height)
            ShareHistoryStore.CONTENT_RADIO_FREQ -> stringResource(R.string.appstrings_radio_share_block_radio_freqs)
            ShareHistoryStore.CONTENT_PROGRAMS -> stringResource(R.string.appstrings_radio_share_block_programs)
            ShareHistoryStore.CONTENT_EXTRA -> stringResource(R.string.appstrings_radio_share_block_extra)
            ShareHistoryStore.CONTENT_SUPPORT_ENTRIES -> stringResource(R.string.appstrings_radio_share_block_support_entries)
            // Champs copiés : mêmes libellés que les lignes des fiches, sans le « : » final.
            ShareHistoryStore.FIELD_ID_SUPPORT -> stringResource(R.string.appstrings_id_support_label).cleanFieldLabel()
            ShareHistoryStore.FIELD_ID_ANFR -> stringResource(R.string.appstrings_anfr_station_number).cleanFieldLabel()
            ShareHistoryStore.FIELD_ARCEP -> stringResource(R.string.appstrings_arcep_identifier_label).cleanFieldLabel()
            ShareHistoryStore.FIELD_ADDRESS -> stringResource(R.string.appstrings_address_label).cleanFieldLabel()
            ShareHistoryStore.FIELD_GPS -> stringResource(R.string.appstrings_gps_label).cleanFieldLabel()
            ShareHistoryStore.FIELD_PANEL -> stringResource(R.string.appstrings_panel_identifier).cleanFieldLabel()
            ShareHistoryStore.FIELD_NETWORK_ID -> stringResource(R.string.appstrings_site_network_ids_title).cleanFieldLabel()
            ShareHistoryStore.FIELD_REPORT -> stringResource(R.string.appstrings_diagnostic_title).cleanFieldLabel()
            else -> null
        }
    }
    // Le thème ferme la liste, nommé en entier : « Sombre » seul, en bout de blocs, ne dirait pas
    // de quoi -- et les libellés du menu de partage sont ici hors de leur contexte.
    val theme = item.darkTheme?.let { dark ->
        if (dark) {
            stringResource(R.string.share_history_theme_dark)
        } else {
            stringResource(R.string.share_history_theme_light)
        }
    }
    return (labels + listOfNotNull(theme)).joinToString(" · ")
}

@Composable
private fun shareHistoryDestinationLabel(destination: String): String = when (destination) {
    ShareHistoryStore.DEST_CLIPBOARD -> stringResource(R.string.share_history_dest_clipboard)
    ShareHistoryStore.DEST_PDF -> stringResource(R.string.share_history_dest_pdf)
    ShareHistoryStore.DEST_PDF_DOWNLOAD -> stringResource(R.string.share_history_dest_pdf_download)
    ShareHistoryStore.DEST_GALLERY -> stringResource(R.string.share_history_dest_gallery)
    else -> stringResource(R.string.share_history_dest_share)
}

private fun shareHistoryDestinationIcon(destination: String): ImageVector = when (destination) {
    ShareHistoryStore.DEST_CLIPBOARD -> Icons.Default.ContentCopy
    ShareHistoryStore.DEST_PDF -> Icons.Default.PictureAsPdf
    ShareHistoryStore.DEST_PDF_DOWNLOAD -> Icons.Default.Download
    ShareHistoryStore.DEST_GALLERY -> Icons.Default.PhotoLibrary
    else -> Icons.Default.Share
}

/** Les libellés de fiche finissent par « : » (convention strings.xml) : hors ligne, ça ne va pas. */
private fun String.cleanFieldLabel(): String = substringBefore(":").trim()
