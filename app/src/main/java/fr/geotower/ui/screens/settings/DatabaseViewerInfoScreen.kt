package fr.geotower.ui.screens.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.TableView
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.geotower.R
import fr.geotower.data.db.DatabaseColumnGlossary
import fr.geotower.data.db.DatabaseColumnInfo
import fr.geotower.data.db.DatabaseInspector
import fr.geotower.data.db.DatabaseNotAvailableException
import fr.geotower.data.db.InspectableDatabase
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DatabaseViewerInfoScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val safeBack = rememberSafeBackNavigation(navController, fallbackRoute = "database_viewer")
    val inspector = remember(context) { DatabaseInspector(context) }
    val scrollState = rememberScrollState()
    var selectedDatabase by remember { mutableStateOf(InspectableDatabase.MOBILE) }
    var tables by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedTable by remember { mutableStateOf<String?>(null) }
    var columns by remember { mutableStateOf<List<DatabaseColumnInfo>>(emptyList()) }
    var isLoadingTables by remember { mutableStateOf(false) }
    var isLoadingColumns by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val language = context.resources.configuration.locales[0]?.language.orEmpty()

    BackHandler(enabled = !safeBack.isLocked) { safeBack.navigateBack() }

    LaunchedEffect(selectedDatabase) {
        isLoadingTables = true
        isLoadingColumns = false
        tables = emptyList()
        columns = emptyList()
        selectedTable = null
        errorMessage = null
        try {
            val discoveredTables = withContext(Dispatchers.IO) {
                inspector.listTableNames(selectedDatabase)
            }
            tables = discoveredTables
            selectedTable = discoveredTables.firstOrNull()
            if (discoveredTables.isEmpty()) {
                errorMessage = context.getString(R.string.database_viewer_info_no_tables)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            errorMessage = context.getString(
                if (error is DatabaseNotAvailableException) R.string.database_viewer_missing_database
                else R.string.database_viewer_read_error,
            )
        }
        isLoadingTables = false
    }

    LaunchedEffect(selectedDatabase, selectedTable) {
        val table = selectedTable ?: return@LaunchedEffect
        isLoadingColumns = true
        columns = emptyList()
        errorMessage = null
        try {
            columns = withContext(Dispatchers.IO) {
                inspector.readTableColumns(selectedDatabase, table)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            errorMessage = context.getString(R.string.database_viewer_read_error)
        }
        isLoadingColumns = false
    }

    Scaffold(
        containerColor = uiStyle.backgroundColor,
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.database_viewer_info_title),
                onBack = { safeBack.navigateBack() },
                backEnabled = !safeBack.isLocked,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .geoTowerFadingEdge(scrollState)
                .verticalScroll(scrollState)
                .padding(horizontal = sizing.spacing(16.dp)),
            verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp)),
        ) {
            Spacer(Modifier.height(sizing.spacing(4.dp)))
            Text(
                text = stringResource(R.string.database_viewer_info_intro),
                style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DatabaseChoiceMenu(
                selected = selectedDatabase,
                onSelected = { database ->
                    if (database != selectedDatabase) {
                        selectedTable = null
                        tables = emptyList()
                        columns = emptyList()
                        isLoadingTables = true
                        isLoadingColumns = false
                        errorMessage = null
                        selectedDatabase = database
                    }
                },
            )

            DatabaseInfoCard(
                title = stringResource(
                    when (selectedDatabase) {
                        InspectableDatabase.MOBILE -> R.string.database_viewer_mobile
                        InspectableDatabase.RADIO -> R.string.database_viewer_radio
                        InspectableDatabase.ENB -> R.string.database_viewer_enb
                    },
                ),
                body = stringResource(
                    when (selectedDatabase) {
                        InspectableDatabase.MOBILE -> R.string.database_viewer_info_mobile
                        InspectableDatabase.RADIO -> R.string.database_viewer_info_radio
                        InspectableDatabase.ENB -> R.string.database_viewer_info_enb
                    },
                ),
            )

            if (isLoadingTables) {
                LoadingLine()
            } else if (tables.isNotEmpty()) {
                TableChoiceMenu(
                    tables = tables,
                    selected = selectedTable,
                    onSelected = { selectedTable = it },
                )
            }

            selectedTable?.let { table ->
                Text(
                    text = stringResource(R.string.database_viewer_info_columns_title, table),
                    style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.database_viewer_info_columns_help),
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                )
            }

            if (isLoadingColumns) {
                LoadingLine()
            } else {
                columns.forEach { column ->
                    DatabaseColumnCard(
                        column = column,
                        meaning = DatabaseColumnGlossary.description(
                            database = selectedDatabase,
                            table = selectedTable.orEmpty(),
                            column = column.name,
                            language = language,
                        ) ?: stringResource(R.string.database_viewer_column_unknown_meaning, column.name),
                    )
                }
            }

            DatabaseInfoCard(
                title = stringResource(R.string.database_viewer_info_reading_title),
                body = stringResource(R.string.database_viewer_info_reading),
            )
            Spacer(Modifier.height(sizing.spacing(20.dp)))
        }
    }
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
            androidx.compose.material3.Icon(Icons.Outlined.TableView, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(label, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            androidx.compose.material3.Icon(Icons.Default.ArrowDropDown, contentDescription = null)
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
                            },
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
    tables: List<String>,
    selected: String?,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = selected ?: stringResource(R.string.database_viewer_choose_table),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            androidx.compose.material3.Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            tables.forEach { table ->
                DropdownMenuItem(
                    text = { Text(table) },
                    onClick = {
                        expanded = false
                        onSelected(table)
                    },
                )
            }
        }
    }
}

@Composable
private fun DatabaseColumnCard(column: DatabaseColumnInfo, meaning: String) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(sizing.spacing(14.dp)),
            verticalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp)),
        ) {
            Text(
                text = column.name,
                style = sizing.textStyle(MaterialTheme.typography.titleSmall),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = meaning,
                style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    R.string.database_viewer_column_sqlite_type,
                    column.type.ifBlank { "—" },
                ),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(sizing.spacing(6.dp)),
            ) {
                if (column.primaryKeyPosition > 0) {
                    ColumnMetaBadge(
                        text = stringResource(
                            R.string.database_viewer_column_primary_key_order,
                            column.primaryKeyPosition,
                        ),
                    )
                }
                ColumnMetaBadge(
                    text = stringResource(
                        if (column.notNull || column.primaryKeyPosition > 0) {
                            R.string.database_viewer_column_required
                        } else {
                            R.string.database_viewer_column_nullable
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun ColumnMetaBadge(text: String) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = sizing.spacing(8.dp), vertical = sizing.spacing(4.dp)),
            style = sizing.textStyle(MaterialTheme.typography.labelSmall),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun DatabaseInfoCard(title: String, body: String) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(sizing.spacing(16.dp)),
            verticalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp)),
        ) {
            Text(
                text = title,
                style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = body,
                style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
