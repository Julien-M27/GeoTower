package fr.geotower.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.TableView
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.geotower.R
import fr.geotower.data.db.DatabaseInspector
import fr.geotower.data.db.DatabaseInspectorRules
import fr.geotower.data.db.DatabaseNotAvailableException
import fr.geotower.data.db.DatabaseSearchSuggestion
import fr.geotower.data.db.DatabaseTablePage
import fr.geotower.data.db.InspectableDatabase
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun DatabaseViewerScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val safeBack = rememberSafeBackNavigation(navController, fallbackRoute = "settings")
    val inspector = remember(context) { DatabaseInspector(context) }
    var selectedDatabase by remember { mutableStateOf(InspectableDatabase.MOBILE) }
    var tables by remember { mutableStateOf<List<fr.geotower.data.db.DatabaseTableInfo>>(emptyList()) }
    var selectedTable by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var dismissedSuggestionQuery by remember { mutableStateOf<String?>(null) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var tablePage by remember { mutableStateOf<DatabaseTablePage?>(null) }
    var isLoadingTables by remember { mutableStateOf(false) }
    var isLoadingPage by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val normalizedSearchQuery = searchQuery.trim()
    val searchSuggestions: List<DatabaseSearchSuggestion> =
        remember(tablePage, selectedTable, normalizedSearchQuery) {
            if (tablePage?.tableName == selectedTable) {
                DatabaseInspectorRules.searchSuggestions(tablePage, normalizedSearchQuery)
            } else {
                emptyList()
            }
        }
    val isSuggestionMenuExpanded =
        searchSuggestions.isNotEmpty() && dismissedSuggestionQuery != normalizedSearchQuery

    BackHandler(enabled = !safeBack.isLocked) { safeBack.navigateBack() }

    LaunchedEffect(selectedDatabase) {
        isLoadingTables = true
        isLoadingPage = false
        tablePage = null
        selectedTable = null
        searchQuery = ""
        dismissedSuggestionQuery = null
        pageIndex = 0
        errorMessage = null
        try {
            val discoveredTables = withContext(Dispatchers.IO) {
                inspector.listTables(selectedDatabase)
            }
            tables = discoveredTables
            selectedTable = discoveredTables.firstOrNull()?.name
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            tables = emptyList()
            errorMessage = if (error is DatabaseNotAvailableException) {
                context.getString(R.string.database_viewer_missing_database)
            } else {
                context.getString(R.string.database_viewer_read_error)
            }
        }
        isLoadingTables = false
    }

    LaunchedEffect(selectedDatabase, selectedTable, pageIndex, searchQuery) {
        val table = selectedTable ?: return@LaunchedEffect
        val normalizedQuery = searchQuery.trim()
        if (normalizedQuery.isNotEmpty()) delay(350)
        isLoadingPage = true
        errorMessage = null
        try {
            tablePage = withContext(Dispatchers.IO) {
                inspector.readPage(selectedDatabase, table, pageIndex, searchQuery = normalizedQuery)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            tablePage = null
            errorMessage = context.getString(R.string.database_viewer_read_error)
        }
        isLoadingPage = false
    }

    Scaffold(
        containerColor = uiStyle.backgroundColor,
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.database_viewer_title),
                onBack = { safeBack.navigateBack() },
                backEnabled = !safeBack.isLocked,
                actions = {
                    IconButton(onClick = { navController.navigate("database_viewer_info") }) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.database_viewer_info_title),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = sizing.spacing(16.dp)),
            verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp)),
        ) {
            Spacer(Modifier.height(sizing.spacing(4.dp)))
            Text(
                text = stringResource(R.string.database_viewer_desc),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DatabaseChoiceMenu(
                selected = selectedDatabase,
                onSelected = { selectedDatabase = it },
            )

            if (isLoadingTables) {
                LoadingLine()
            } else if (tables.isNotEmpty()) {
                TableChoiceMenu(
                    tables = tables,
                    selected = selectedTable,
                    onSelected = {
                        selectedTable = it
                        dismissedSuggestionQuery = null
                        pageIndex = 0
                    },
                )
            }

            if (selectedTable != null) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            dismissedSuggestionQuery = null
                            pageIndex = 0
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.database_viewer_search_label)) },
                        placeholder = { Text(stringResource(R.string.database_viewer_search_hint)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    dismissedSuggestionQuery = null
                                    pageIndex = 0
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.database_viewer_search_clear))
                                }
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = isSuggestionMenuExpanded,
                        onDismissRequest = { dismissedSuggestionQuery = normalizedSearchQuery },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.database_viewer_suggestions_title),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = sizing.textStyle(MaterialTheme.typography.labelLarge),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        searchSuggestions.forEach { suggestion ->
                            DatabaseSearchSuggestionItem(
                                suggestion = suggestion,
                                onClick = {
                                    searchQuery = suggestion.value
                                    dismissedSuggestionQuery = suggestion.value.trim()
                                    pageIndex = 0
                                },
                            )
                        }
                    }
                }
            }

            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                )
            }

            if (isLoadingPage) {
                LoadingLine()
            } else {
                tablePage?.let { loadedPage ->
                    DatabaseTableContent(
                        tablePage = loadedPage,
                        modifier = Modifier.weight(1f),
                    )
                    DatabasePageControls(
                        tablePage = loadedPage,
                        onPrevious = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                        onNext = { pageIndex += 1 },
                    )
                }
            }
        }
    }
}

@Composable
private fun DatabaseSearchSuggestionItem(
    suggestion: DatabaseSearchSuggestion,
    onClick: () -> Unit,
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    DropdownMenuItem(
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(sizing.spacing(2.dp))) {
                Text(
                    text = suggestion.value,
                    style = sizing.textStyle(MaterialTheme.typography.bodyLarge),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = suggestion.columnName,
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun DatabaseChoiceMenu(
    selected: InspectableDatabase,
    onSelected: (InspectableDatabase) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (selected) {
        InspectableDatabase.MOBILE -> stringResource(R.string.database_viewer_mobile)
        InspectableDatabase.RADIO -> stringResource(R.string.database_viewer_radio)
        InspectableDatabase.ENB -> stringResource(R.string.database_viewer_enb)
    }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.TableView, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(label, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            InspectableDatabase.entries.forEach { database ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (database) {
                                InspectableDatabase.MOBILE -> stringResource(R.string.database_viewer_mobile)
                                InspectableDatabase.RADIO -> stringResource(R.string.database_viewer_radio)
                                InspectableDatabase.ENB -> stringResource(R.string.database_viewer_enb)
                            }
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(database)
                    },
                )
            }
        }
    }
}

@Composable
private fun TableChoiceMenu(
    tables: List<fr.geotower.data.db.DatabaseTableInfo>,
    selected: String?,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = selected ?: stringResource(R.string.database_viewer_choose_table),
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            tables.forEach { table ->
                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.database_viewer_table_item, table.name, table.rowCount.toString()))
                    },
                    onClick = {
                        expanded = false
                        onSelected(table.name)
                    },
                )
            }
        }
    }
}

@Composable
private fun DatabaseTableContent(tablePage: DatabaseTablePage, modifier: Modifier = Modifier) {
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    val sizing = LocalGeoTowerUiStyle.current.sizing
    var expandedCell by remember(tablePage.tableName, tablePage.page) { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        val summary = if (tablePage.searchQuery.isEmpty()) {
            stringResource(
                R.string.database_viewer_table_summary,
                tablePage.tableName,
                tablePage.totalRows.toString(),
            )
        } else {
            stringResource(
                R.string.database_viewer_search_summary,
                tablePage.tableName,
                tablePage.totalRows.toString(),
                tablePage.searchQuery,
            )
        }
        Text(
            text = summary,
            style = sizing.textStyle(MaterialTheme.typography.titleSmall),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(sizing.spacing(8.dp)))
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalScroll)
                .verticalScroll(verticalScroll),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(modifier = Modifier.padding(1.dp)) {
                Row {
                    tablePage.columns.forEach { column ->
                        DatabaseCell(
                            text = buildString {
                                append(column.name)
                                if (column.type.isNotBlank()) append("\n${column.type}")
                            },
                            header = true,
                        )
                    }
                }
                tablePage.rows.forEach { row ->
                    Row {
                        row.forEach { value ->
                            DatabaseCell(
                                text = value ?: "NULL",
                                onClick = { expandedCell = value ?: "NULL" },
                            )
                        }
                    }
                }
                if (tablePage.rows.isEmpty()) {
                    Text(
                        text = stringResource(
                            if (tablePage.searchQuery.isEmpty()) R.string.database_viewer_empty_table
                            else R.string.database_viewer_search_empty,
                        ),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    expandedCell?.let { value ->
        AlertDialog(
            onDismissRequest = { expandedCell = null },
            title = { Text(stringResource(R.string.database_viewer_cell_title)) },
            text = {
                Text(
                    text = value,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { expandedCell = null }) {
                    Text(stringResource(R.string.appstrings_close))
                }
            },
        )
    }
}

@Composable
private fun DatabaseCell(text: String, header: Boolean = false, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier
            .width(180.dp)
            .then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier),
        color = if (header) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
            style = LocalGeoTowerUiStyle.current.sizing.textStyle(MaterialTheme.typography.bodySmall),
            maxLines = if (header) 2 else 5,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DatabasePageControls(
    tablePage: DatabaseTablePage,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val firstRow = tablePage.page * tablePage.pageSize + 1
    val lastRow = (firstRow + tablePage.rows.size - 1).coerceAtLeast(0)
    val hasPrevious = tablePage.page > 0
    val hasNext = lastRow < tablePage.totalRows
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Button(onClick = onPrevious, enabled = hasPrevious) {
            Icon(Icons.Default.ChevronLeft, contentDescription = null)
            Text(stringResource(R.string.database_viewer_previous))
        }
        Text(
            text = stringResource(R.string.database_viewer_rows, firstRow.coerceAtMost(tablePage.totalRows.toInt()), lastRow),
            style = LocalGeoTowerUiStyle.current.sizing.textStyle(MaterialTheme.typography.labelSmall),
        )
        Button(onClick = onNext, enabled = hasNext) {
            Text(stringResource(R.string.database_viewer_next))
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun LoadingLine() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator()
    }
}
