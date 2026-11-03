package fr.geotower.ui.screens.about

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.geotower.R
import android.content.Context
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.PageScrollEdgeButtons
import fr.geotower.ui.components.geoTowerLazyListFadingEdge
import fr.geotower.ui.components.pageScrollbar
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.screens.settings.ChangelogPageSettingsSheet
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.ChangelogPagePrefs
import fr.geotower.utils.PageScrollPrefs
import fr.geotower.utils.PreferenceStores

private enum class ChangelogCategoryFilter {
    ALL,
    V2,
    V1,
    MAJOR
}

/**
 * Écran retraçant l'évolution complète et tous les changelogs de GeoTower depuis sa création.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogHistoryScreen(navController: NavController) {
    val context = LocalContext.current
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "about")

    BackHandler(enabled = !safeBackNavigation.isLocked) {
        safeBackNavigation.navigateBack()
    }

    val prefs = remember(context) { context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE) }
    var showHeaderStats by remember { mutableStateOf(ChangelogPagePrefs.showHeaderStats.read(prefs)) }
    var showDates by remember { mutableStateOf(ChangelogPagePrefs.showDates.read(prefs)) }
    var showBadges by remember { mutableStateOf(ChangelogPagePrefs.showBadges.read(prefs)) }
    val defaultExpandAll = remember { ChangelogPagePrefs.expandAllByDefault.read(prefs) }

    val allMilestones = remember { AppChangelogRepository.milestones }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ChangelogCategoryFilter.ALL) }
    var areAllExpanded by remember { mutableStateOf(defaultExpandAll) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Map mémorisant l'état ouvert/fermé de chaque version.
    // Par défaut, si l'option tout déplier est activée, toutes sont ouvertes, sinon seule la dernière.
    val expandedStates = remember {
        mutableStateMapOf<String, Boolean>().apply {
            if (defaultExpandAll) {
                allMilestones.forEach { put(it.versionName, true) }
            } else {
                allMilestones.firstOrNull()?.let { put(it.versionName, true) }
            }
        }
    }

    // Filtrage des étapes selon la catégorie et le mot-clé recherché
    val filteredMilestones by remember(searchQuery, selectedFilter, allMilestones) {
        derivedStateOf {
            val query = searchQuery.trim().lowercase()
            allMilestones.filter { milestone ->
                val categoryMatch = when (selectedFilter) {
                    ChangelogCategoryFilter.ALL -> true
                    ChangelogCategoryFilter.V2 -> milestone.versionName.startsWith("2.")
                    ChangelogCategoryFilter.V1 -> milestone.versionName.startsWith("1.")
                    ChangelogCategoryFilter.MAJOR -> milestone.isMajor
                }
                if (!categoryMatch) return@filter false

                if (query.isEmpty()) {
                    true
                } else {
                    // Vérification sur le numéro de version, le résumé ou les éléments
                    val versionMatch = milestone.versionName.lowercase().contains(query)
                    val summaryMatch = milestone.summary.lowercase().contains(query)
                    val directItemsMatch = milestone.directItems.any { it.lowercase().contains(query) }
                    val resItemsMatch = milestone.itemResIds.any { resId ->
                        context.getString(resId).lowercase().contains(query)
                    }
                    versionMatch || summaryMatch || directItemsMatch || resItemsMatch
                }
            }
        }
    }

    val lazyListState = rememberLazyListState()

    Scaffold(
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.appstrings_changelogs_history_title),
                onBack = { safeBackNavigation.navigateBack() },
                backEnabled = !safeBackNavigation.isLocked,
                actions = {
                    IconButton(
                        onClick = {
                            areAllExpanded = !areAllExpanded
                            filteredMilestones.forEach { m ->
                                expandedStates[m.versionName] = areAllExpanded
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (areAllExpanded) Icons.Outlined.UnfoldLess else Icons.Outlined.UnfoldMore,
                            contentDescription = if (areAllExpanded) {
                                stringResource(R.string.appstrings_changelogs_collapse_all)
                            } else {
                                stringResource(R.string.appstrings_changelogs_expand_all)
                            },
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { showSettingsSheet = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.appstrings_settings_title),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .geoTowerLazyListFadingEdge(lazyListState)
                    .pageScrollbar(PageScrollPrefs.CHANGELOGS, lazyListState),
                contentPadding = PaddingValues(
                    start = sizing.spacing(16.dp),
                    end = sizing.spacing(16.dp),
                    top = sizing.spacing(8.dp),
                    bottom = sizing.spacing(80.dp)
                ),
                verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
            ) {
                // En-tête : Métriques globales et évolution
                item(key = "header_summary") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (showHeaderStats) {
                            ChangelogStatsHeader(
                                totalVersions = allMilestones.size,
                                latestVersion = allMilestones.firstOrNull()?.versionName ?: "2.0.99"
                            )
                            Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))
                        }

                        // Champ de recherche dynamique
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.appstrings_changelogs_search_placeholder),
                                    style = sizing.textStyle(MaterialTheme.typography.bodyMedium)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = null
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(sizing.spacing(16.dp)),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))

                        // Filtres rapides (Chips)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp))
                        ) {
                            FilterChip(
                                selected = selectedFilter == ChangelogCategoryFilter.ALL,
                                onClick = { selectedFilter = ChangelogCategoryFilter.ALL },
                                label = { Text(stringResource(R.string.appstrings_changelogs_filter_all)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                            FilterChip(
                                selected = selectedFilter == ChangelogCategoryFilter.V2,
                                onClick = { selectedFilter = ChangelogCategoryFilter.V2 },
                                label = { Text(stringResource(R.string.appstrings_changelogs_filter_v2)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                            FilterChip(
                                selected = selectedFilter == ChangelogCategoryFilter.V1,
                                onClick = { selectedFilter = ChangelogCategoryFilter.V1 },
                                label = { Text(stringResource(R.string.appstrings_changelogs_filter_v1)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                            FilterChip(
                                selected = selectedFilter == ChangelogCategoryFilter.MAJOR,
                                onClick = { selectedFilter = ChangelogCategoryFilter.MAJOR },
                                label = { Text(stringResource(R.string.appstrings_changelogs_filter_major)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(sizing.spacing(4.dp)))
                        Text(
                            text = stringResource(R.string.appstrings_changelogs_versions_count, filteredMilestones.size),
                            style = sizing.textStyle(MaterialTheme.typography.labelMedium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = sizing.spacing(4.dp))
                        )
                    }
                }

                if (filteredMilestones.isEmpty()) {
                    item(key = "empty_state") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(sizing.spacing(16.dp)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = sizing.spacing(24.dp))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(sizing.spacing(24.dp)),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.SearchOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(sizing.component(48.dp))
                                )
                                Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))
                                Text(
                                    text = stringResource(R.string.appstrings_changelogs_empty_search),
                                    style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = filteredMilestones,
                        key = { it.versionName }
                    ) { milestone ->
                        // Si une recherche est active, on déplie automatiquement les résultats correspondants
                        val isExpanded = if (searchQuery.isNotBlank()) {
                            true
                        } else {
                            expandedStates[milestone.versionName] ?: false
                        }

                        ChangelogMilestoneCard(
                            milestone = milestone,
                            isExpanded = isExpanded,
                            showDates = showDates,
                            showBadges = showBadges,
                            onToggleExpand = {
                                expandedStates[milestone.versionName] = !isExpanded
                            }
                        )
                    }
                }
            }

            PageScrollEdgeButtons(
                page = PageScrollPrefs.CHANGELOGS,
                listState = lazyListState
            )
        }
    }

    if (showSettingsSheet) {
        ChangelogPageSettingsSheet(
            onDismiss = {
                showSettingsSheet = false
                showHeaderStats = ChangelogPagePrefs.showHeaderStats.read(prefs)
                showDates = ChangelogPagePrefs.showDates.read(prefs)
                showBadges = ChangelogPagePrefs.showBadges.read(prefs)
            },
            onBack = {
                showSettingsSheet = false
                showHeaderStats = ChangelogPagePrefs.showHeaderStats.read(prefs)
                showDates = ChangelogPagePrefs.showDates.read(prefs)
                showBadges = ChangelogPagePrefs.showBadges.read(prefs)
            },
            sheetState = settingsSheetState,
            useOneUi = AppConfig.useOneUiDesign,
            bubbleColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    }
}

/**
 * Carte de statistiques résumant le parcours de l'application.
 */
@Composable
private fun ChangelogStatsHeader(
    totalVersions: Int,
    latestVersion: String
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(sizing.spacing(20.dp)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(sizing.spacing(16.dp))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(sizing.component(28.dp))
                )
                Spacer(modifier = Modifier.width(sizing.spacing(10.dp)))
                Column {
                    Text(
                        text = stringResource(R.string.appstrings_changelogs_history_title),
                        style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.appstrings_changelogs_history_desc),
                        style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(sizing.spacing(14.dp)))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(sizing.spacing(14.dp)))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatColumn(
                    value = "$totalVersions",
                    label = stringResource(R.string.appstrings_changelogs_summary_stat_versions)
                )
                StatColumn(
                    value = "06/04/26",
                    label = stringResource(R.string.appstrings_changelogs_summary_stat_start)
                )
                StatColumn(
                    value = "v$latestVersion",
                    label = stringResource(R.string.appstrings_changelogs_summary_stat_latest)
                )
            }
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = sizing.textStyle(MaterialTheme.typography.titleLarge),
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = sizing.textStyle(MaterialTheme.typography.labelSmall),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Carte individuelle pour une version spécifique dans l'historique.
 */
@Composable
private fun ChangelogMilestoneCard(
    milestone: ChangelogMilestone,
    isExpanded: Boolean,
    showDates: Boolean = true,
    showBadges: Boolean = true,
    onToggleExpand: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val context = LocalContext.current

    val containerColor = when {
        milestone.isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        milestone.badge == MilestoneBadge.PLAY_STORE -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
        milestone.isMajor -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(sizing.spacing(16.dp)),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(250))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(sizing.spacing(14.dp))
        ) {
            // Ligne du haut : Badge de version, Tags, Date et Flèche
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pastille de version (ex: v2.0.99)
                Surface(
                    color = if (milestone.isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else if (milestone.isMajor) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(sizing.spacing(8.dp))
                ) {
                    Text(
                        text = "v${milestone.versionName}",
                        style = sizing.textStyle(MaterialTheme.typography.labelLarge),
                        fontWeight = FontWeight.Bold,
                        color = if (milestone.isCurrent) {
                            MaterialTheme.colorScheme.onPrimary
                        } else if (milestone.isMajor) {
                            MaterialTheme.colorScheme.onSecondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = sizing.spacing(8.dp), vertical = sizing.spacing(4.dp))
                    )
                }

                if (showBadges) {
                    Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))

                    // Badges additionnels (Actuelle, Play Store, Majeure, Lancement)
                    when (milestone.badge) {
                        MilestoneBadge.CURRENT -> {
                            MilestonePill(
                                label = stringResource(R.string.appstrings_changelogs_badge_current),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary,
                                icon = Icons.Outlined.CheckCircle
                            )
                        }
                        MilestoneBadge.PLAY_STORE -> {
                            MilestonePill(
                                label = stringResource(R.string.appstrings_changelogs_badge_play_store),
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                icon = Icons.Outlined.RocketLaunch
                            )
                        }
                        MilestoneBadge.MAJOR -> {
                            MilestonePill(
                                label = stringResource(R.string.appstrings_changelogs_badge_major),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                icon = Icons.Outlined.Star
                            )
                        }
                        MilestoneBadge.FOUNDATION -> {
                            MilestonePill(
                                label = stringResource(R.string.appstrings_changelogs_badge_launch),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                icon = Icons.Outlined.NewReleases
                            )
                        }
                        MilestoneBadge.NONE -> Unit
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Date de la version
                if (showDates) {
                    Text(
                        text = milestone.releaseDate,
                        style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(sizing.spacing(4.dp)))
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(sizing.component(20.dp))
                )
            }

            Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))

            // Résumé de la version
            Text(
                text = milestone.summary,
                style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            // Contenu déplié : détail des modifications
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = sizing.spacing(10.dp))
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = sizing.spacing(8.dp))
                    )

                    // Entrées provenant des chaînes de ressources (traduites)
                    milestone.itemResIds.forEach { resId ->
                        ChangelogBulletItem(text = stringResource(resId))
                    }

                    // Entrées directes (étapes fondatrices)
                    milestone.directItems.forEach { text ->
                        ChangelogBulletItem(text = text)
                    }
                }
            }
        }
    }
}

@Composable
private fun MilestonePill(
    label: String,
    containerColor: Color,
    contentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Surface(
        color = containerColor,
        shape = CircleShape
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = sizing.spacing(6.dp), vertical = sizing.spacing(2.dp))
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(sizing.component(12.dp))
                )
                Spacer(modifier = Modifier.width(sizing.spacing(3.dp)))
            }
            Text(
                text = label,
                style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

@Composable
private fun ChangelogBulletItem(text: String) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = sizing.spacing(3.dp)),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = sizing.spacing(6.dp), end = sizing.spacing(8.dp))
                .size(sizing.component(5.dp))
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Text(
            text = text,
            style = sizing.textStyle(MaterialTheme.typography.bodySmall),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}
