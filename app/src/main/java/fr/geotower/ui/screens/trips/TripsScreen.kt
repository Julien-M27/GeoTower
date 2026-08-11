package fr.geotower.ui.screens.trips

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import fr.geotower.R
import fr.geotower.data.api.RouteApi
import fr.geotower.data.workers.TripReminderScheduler
import fr.geotower.data.trip.TripPlan
import fr.geotower.data.trip.TripPlanStore
import fr.geotower.data.trip.TripSharing
import fr.geotower.data.trip.formatTripDuration
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.PageScrollEdgeButtons
import fr.geotower.ui.components.geoTowerLazyListFadingEdge
import fr.geotower.ui.components.pageScrollbar
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.PageScrollPrefs
import fr.geotower.utils.formatSiteDistanceMeters
import java.text.DateFormat
import java.util.Date

/** Filtres proposés en tête de liste. `null` = tout afficher. */
private enum class TripsFilter(val status: String?) {
    ALL(null),
    UPCOMING(TripPlan.STATUS_PLANNED),
    DRAFT(TripPlan.STATUS_DRAFT),
    DONE(TripPlan.STATUS_DONE),
    ARCHIVED(TripPlan.STATUS_ARCHIVED)
}

/**
 * Liste des trajets enregistrés — la page d'accueil de la fonctionnalité.
 *
 * Tout y est enregistré immédiatement : il n'y a pas de bouton « enregistrer ». Renommer, dupliquer
 * ou inverser écrit dans [TripPlanStore] et recharge la liste, parce qu'une tournée saisie sur le
 * terrain ne doit jamais dépendre d'un geste de confirmation qu'on oublie.
 */
@Composable
fun TripsScreen(
    navController: NavController,
    onCreateTrip: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onFollowTrip: (String) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val safeClick = rememberSafeClick()
    val safeBackNavigation = rememberSafeBackNavigation(navController)
    val listState = rememberLazyListState()

    var reloadTick by remember { mutableIntStateOf(0) }
    var plans by remember { mutableStateOf<List<TripPlan>?>(null) }
    var filter by remember { mutableStateOf(TripsFilter.ALL) }
    var renaming by remember { mutableStateOf<TripPlan?>(null) }
    var deleting by remember { mutableStateOf<TripPlan?>(null) }
    var scheduling by remember { mutableStateOf<TripPlan?>(null) }

    BackHandler(enabled = !safeBackNavigation.isLocked) { safeBackNavigation.navigateBack() }

    LaunchedEffect(reloadTick) {
        // Un « nouveau trajet » ouvert puis quitté sans y poser la moindre étape ne doit pas rester
        // dans la liste : on le retire au retour ici plutôt qu'en quittant la carte.
        plans = TripPlanStore.purgeEmptyDrafts(context).sortedWith(
            // Les trajets datés d'abord, du plus proche au plus lointain ; les brouillons ensuite,
            // du plus récemment touché au plus ancien.
            compareBy<TripPlan> { it.plannedAtMillis == null }
                .thenBy { it.plannedAtMillis ?: Long.MAX_VALUE }
                .thenByDescending { it.updatedAtMillis }
        )
    }

    val stepFallback = stringResource(R.string.trips_step_fallback_pattern)
    val distanceUnit = AppConfig.distanceUnit.intValue

    Scaffold(
        containerColor = uiStyle.backgroundColor,
        // Les routes du NavHost padent déjà avec l'innerPadding racine : sans cela, l'encart est
        // compté deux fois et laisse un vide en haut sur grand écran.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.trips_title),
                onBack = { safeBackNavigation.navigateBack() },
                backEnabled = !safeBackNavigation.isLocked,
                // Une seule action, de la largeur du bouton retour : la barre réserve `actionsWidth`
                // en face de lui, et tout écart décentre le titre. « Nouveau trajet » part donc en
                // bouton flottant, sa place naturelle pour une liste.
                actions = {
                    val current = plans.orEmpty()
                    IconButton(
                        onClick = { safeClick("export_all") { shareAll(context, current, stepFallback) } },
                        enabled = current.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.trips_export_all)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { safeClick("new_trip", onCreateTrip) }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.trips_new)
                )
            }
        }
    ) { innerPadding ->
        val loaded = plans
        val visible = loaded.orEmpty().filter { filter.status == null || it.status == filter.status }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(uiStyle.backgroundColor)
                .padding(innerPadding)
        ) {
            if (!loaded.isNullOrEmpty()) {
                TripsFilterBar(
                    selected = filter,
                    onSelect = { filter = it },
                    modifier = Modifier.padding(
                        horizontal = sizing.spacing(12.dp),
                        vertical = sizing.spacing(8.dp)
                    )
                )
            }

            when {
                loaded == null -> Box(Modifier.fillMaxSize())

                visible.isEmpty() -> TripsEmptyState(
                    hasAnyTrip = !loaded.isEmpty(),
                    modifier = Modifier.fillMaxSize()
                )

                else -> Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .geoTowerLazyListFadingEdge(listState)
                        .pageScrollbar(PageScrollPrefs.TRIPS, listState),
                    contentPadding = PaddingValues(
                        start = sizing.spacing(12.dp),
                        end = sizing.spacing(12.dp),
                        // De quoi passer sous le bouton flottant, sinon la dernière carte se
                        // retrouve dessous et son menu devient inatteignable. Pas d'encart système
                        // à ajouter ici : la route du NavHost pade déjà avec l'innerPadding racine.
                        bottom = sizing.spacing(96.dp)
                    ),
                    verticalArrangement = Arrangement.spacedBy(sizing.spacing(10.dp))
                ) {
                    items(visible, key = { it.id }) { plan ->
                        TripCard(
                            plan = plan,
                            distanceUnit = distanceUnit,
                            dateFormat = remember(configuration) {
                                DateFormat.getDateTimeInstance(
                                    DateFormat.MEDIUM,
                                    DateFormat.SHORT,
                                    configuration.locales[0]
                                )
                            },
                            onOpen = { safeClick(plan.id) { onOpenTrip(plan.id) } },
                            onRename = { renaming = plan },
                            onSchedule = { scheduling = plan },
                            onFollow = { safeClick("follow_${plan.id}") { onFollowTrip(plan.id) } },
                            onDuplicate = {
                                TripPlanStore.save(
                                    context,
                                    TripPlanStore.duplicated(plan, copyName(context, plan.name))
                                )
                                reloadTick++
                            },
                            onReverse = {
                                TripPlanStore.save(context, TripPlanStore.reversed(plan))
                                reloadTick++
                            },
                            onExportGpx = {
                                TripSharing.shareGpx(context, listOf(plan)) { index ->
                                    String.format(stepFallback, index + 1)
                                }
                            },
                            onExportJson = { TripSharing.shareJson(context, listOf(plan)) },
                            onToggleArchive = {
                                val next = if (plan.status == TripPlan.STATUS_ARCHIVED) {
                                    TripPlan.STATUS_DRAFT
                                } else {
                                    TripPlan.STATUS_ARCHIVED
                                }
                                val archived = plan.copy(status = next)
                                TripPlanStore.save(context, archived)
                                // Archiver coupe les rappels, désarchiver les remet : la
                                // reprogrammation gère les deux sens.
                                TripReminderScheduler.reschedule(context, archived)
                                reloadTick++
                            },
                            onDelete = { deleting = plan }
                        )
                    }
                }

                PageScrollEdgeButtons(PageScrollPrefs.TRIPS, listState)
                }
            }
        }
    }

    renaming?.let { target ->
        TripRenameDialog(
            initialName = target.name,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                // Renommer à la main coupe définitivement le suivi automatique de la date.
                TripPlanStore.save(context, target.copy(name = name, autoNamed = false))
                renaming = null
                reloadTick++
            }
        )
    }

    scheduling?.let { target ->
        TripScheduleDialog(
            plan = target,
            onDismiss = { scheduling = null },
            onConfirm = { plannedAtMillis, reminderOffsets, stopMinutes ->
                val next = target.withSchedule(
                    context = context,
                    plannedAtMillis = plannedAtMillis,
                    reminderOffsetsMinutes = reminderOffsets,
                    stopDurationMinutes = stopMinutes,
                    locale = configuration.locales[0]
                )
                TripPlanStore.save(context, next)
                TripReminderScheduler.reschedule(context, next)
                scheduling = null
                reloadTick++
            }
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.trips_delete_title)) },
            text = { Text(stringResource(R.string.trips_delete_message, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    // Sans ça, le rappel d'une tournée supprimée réveillerait pour rien.
                    TripReminderScheduler.cancel(context, target.id)
                    TripPlanStore.delete(context, target.id)
                    deleting = null
                    reloadTick++
                }) {
                    Text(stringResource(R.string.trips_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.appstrings_cancel))
                }
            }
        )
    }
}

@Composable
private fun TripsFilterBar(
    selected: TripsFilter,
    onSelect: (TripsFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp))
    ) {
        TripsFilter.entries.forEach { entry ->
            FilterChip(
                selected = selected == entry,
                onClick = { onSelect(entry) },
                label = { Text(stringResource(tripsFilterLabel(entry)), fontSize = sizing.text(13.sp)) }
            )
        }
    }
}

private fun tripsFilterLabel(filter: TripsFilter): Int = when (filter) {
    TripsFilter.ALL -> R.string.trips_filter_all
    TripsFilter.UPCOMING -> R.string.trips_filter_upcoming
    TripsFilter.DRAFT -> R.string.trips_filter_draft
    TripsFilter.DONE -> R.string.trips_filter_done
    TripsFilter.ARCHIVED -> R.string.trips_filter_archived
}

@Composable
private fun TripCard(
    plan: TripPlan,
    distanceUnit: Int,
    dateFormat: DateFormat,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onSchedule: () -> Unit,
    onFollow: () -> Unit,
    onDuplicate: () -> Unit,
    onReverse: () -> Unit,
    onExportGpx: () -> Unit,
    onExportJson: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(sizing.spacing(14.dp))) {
            if (plan.steps.size >= 2) {
                TripSparkline(
                    plan = plan,
                    modifier = Modifier.size(sizing.component(56.dp))
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (plan.steps.size >= 2) sizing.spacing(12.dp) else 0.dp)
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (plan.profile == RouteApi.PROFILE_PEDESTRIAN) {
                        Icons.Default.DirectionsWalk
                    } else {
                        Icons.Default.DirectionsCar
                    },
                    contentDescription = null,
                    modifier = Modifier.size(sizing.component(20.dp)),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = plan.name.ifBlank { stringResource(R.string.trips_untitled) },
                    fontSize = sizing.text(16.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = sizing.spacing(8.dp))
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.trips_actions)
                        )
                    }
                    TripCardMenu(
                        expanded = menuOpen,
                        archived = plan.status == TripPlan.STATUS_ARCHIVED,
                        onDismiss = { menuOpen = false },
                        onRename = { menuOpen = false; onRename() },
                        onSchedule = { menuOpen = false; onSchedule() },
                        onFollow = { menuOpen = false; onFollow() },
                        canFollow = plan.steps.isNotEmpty(),
                        onDuplicate = { menuOpen = false; onDuplicate() },
                        onReverse = { menuOpen = false; onReverse() },
                        onExportGpx = { menuOpen = false; onExportGpx() },
                        onExportJson = { menuOpen = false; onExportJson() },
                        onToggleArchive = { menuOpen = false; onToggleArchive() },
                        onDelete = { menuOpen = false; onDelete() }
                    )
                }
            }

            Text(
                text = tripSummaryLine(plan, distanceUnit),
                fontSize = sizing.text(13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = sizing.spacing(4.dp))
            )

            plan.plannedAtMillis?.let { millis ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = sizing.spacing(6.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(sizing.component(14.dp)),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dateFormat.format(Date(millis)),
                        fontSize = sizing.text(12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = sizing.spacing(6.dp))
                    )
                }
            }

            if (plan.visitedCount() > 0) {
                Text(
                    text = stringResource(
                        R.string.trips_progress,
                        plan.visitedCount(),
                        plan.relevantStepCount()
                    ),
                    fontSize = sizing.text(12.sp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = sizing.spacing(4.dp))
                )
            }

            if (plan.steps.size >= 2 && !plan.isRouteComplete()) {
                // Dire que le tracé est incomplet, sinon la distance affichée passerait pour la
                // distance réelle de la tournée alors qu'elle en oublie des bouts.
                Text(
                    text = stringResource(R.string.trips_route_incomplete),
                    fontSize = sizing.text(12.sp),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = sizing.spacing(4.dp))
                )
            }
            }
        }
    }
}

@Composable
private fun TripCardMenu(
    expanded: Boolean,
    archived: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onSchedule: () -> Unit,
    onFollow: () -> Unit,
    canFollow: Boolean,
    onDuplicate: () -> Unit,
    onReverse: () -> Unit,
    onExportGpx: () -> Unit,
    onExportJson: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        TripMenuEntry(R.string.trips_action_rename, Icons.Default.Edit, onRename)
        TripMenuEntry(R.string.trips_action_schedule, Icons.Default.Schedule, onSchedule)
        // Suivre une tournée vide n'aurait rien à annoncer.
        if (canFollow) {
            TripMenuEntry(R.string.trips_action_follow, Icons.Default.Navigation, onFollow)
        }
        TripMenuEntry(R.string.trips_action_duplicate, Icons.Default.ContentCopy, onDuplicate)
        TripMenuEntry(R.string.trips_action_reverse, Icons.Default.SwapVert, onReverse)
        TripMenuEntry(R.string.trips_action_export_gpx, Icons.Default.Share, onExportGpx)
        TripMenuEntry(R.string.trips_action_export_json, Icons.Default.Share, onExportJson)
        TripMenuEntry(
            labelRes = if (archived) R.string.trips_action_unarchive else R.string.trips_action_archive,
            icon = if (archived) Icons.Default.Unarchive else Icons.Default.Archive,
            onClick = onToggleArchive
        )
        TripMenuEntry(R.string.trips_action_delete, Icons.Default.Delete, onDelete)
    }
}

@Composable
private fun TripMenuEntry(
    labelRes: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    DropdownMenuItem(
        text = { Text(stringResource(labelRes), fontSize = sizing.text(14.sp)) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(sizing.component(18.dp)))
        },
        onClick = onClick
    )
}

@Composable
private fun TripRenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trips_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(80) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.appstrings_validate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.appstrings_cancel)) }
        }
    )
}

@Composable
private fun TripsEmptyState(hasAnyTrip: Boolean, modifier: Modifier = Modifier) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Column(
        modifier = modifier.padding(sizing.spacing(24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Route,
            contentDescription = null,
            modifier = Modifier.size(sizing.component(48.dp)),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(
                if (hasAnyTrip) R.string.trips_empty_filter_title else R.string.trips_empty_title
            ),
            fontSize = sizing.text(16.sp),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = sizing.spacing(12.dp))
        )
        if (!hasAnyTrip) {
            Text(
                text = stringResource(R.string.trips_empty_desc),
                fontSize = sizing.text(13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = sizing.spacing(6.dp))
            )
        }
    }
}

/** « 42,3 km - 1 h 05 - 8 étapes », en n'affichant que ce qui est connu. */
@Composable
private fun tripSummaryLine(plan: TripPlan, distanceUnit: Int): String {
    val parts = mutableListOf<String>()
    if (plan.steps.size >= 2) {
        parts += formatSiteDistanceMeters(plan.totalDistanceMeters(), distanceUnit)
        parts += formatTripDuration(
            seconds = plan.totalDurationWithStopsSeconds(),
            hourLabel = stringResource(R.string.trips_duration_hour_short),
            minuteLabel = stringResource(R.string.trips_duration_minute_short)
        )
    }
    parts += pluralStringResource(R.plurals.trips_steps, plan.steps.size, plan.steps.size)
    return parts.joinToString(" - ")
}

private fun shareAll(context: android.content.Context, plans: List<TripPlan>, stepFallback: String) {
    if (plans.isEmpty()) return
    TripSharing.shareGpx(context, plans) { index -> String.format(stepFallback, index + 1) }
}

private fun copyName(context: android.content.Context, name: String): String =
    context.getString(R.string.trips_copy_pattern, name)
