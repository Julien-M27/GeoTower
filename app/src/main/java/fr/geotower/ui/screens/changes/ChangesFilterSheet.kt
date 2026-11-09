package fr.geotower.ui.screens.changes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.data.models.ChangeMapPoint
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.OperatorColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangesFilterSheet(
    currentFilter: ChangesFilterState,
    allPoints: List<ChangeMapPoint>,
    onApply: (ChangesFilterState) -> Unit,
    onDismiss: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filter by remember { mutableStateOf(currentFilter) }

    // États de repli/dépli pour chaque section
    var techExpanded by remember { mutableStateOf(true) }
    var freqExpanded by remember { mutableStateOf(true) }
    var opExpanded by remember { mutableStateOf(true) }
    var metroExpanded by remember { mutableStateOf(true) }
    var dromExpanded by remember { mutableStateOf(true) }
    var actionsExpanded by remember { mutableStateOf(true) }
    var siteTypeExpanded by remember { mutableStateOf(true) }

    // Calcul du nombre de points correspondants en direct
    val matchingCount = remember(filter, allPoints) {
        allPoints.count { ChangesFilterHelper.matches(it, filter) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
        ) {
            // En-tête
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sizing.spacing(20.dp), vertical = sizing.spacing(12.dp)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Filtres",
                        style = sizing.textStyle(MaterialTheme.typography.titleLarge),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!filter.isDefault) {
                        Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                        TextButton(
                            onClick = { filter = ChangesFilterState() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Réinitialiser", fontSize = 12.sp)
                        }
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Fermer")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Contenu défilant
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = sizing.spacing(16.dp))
            ) {
                // 1. TECHNOLOGIES
                val allTechCodes = ChangesFilterState.ALL_TECHS.map { it.first }.toSet()
                val allTechsSelected = filter.techs.containsAll(allTechCodes)
                FilterSectionHeader(
                    title = "Technologies",
                    isExpanded = techExpanded,
                    onToggleExpand = { techExpanded = !techExpanded },
                    actionLabel = if (allTechsSelected) "Tout décocher" else "Tout cocher",
                    onAction = {
                        filter = filter.copy(techs = if (allTechsSelected) emptySet() else allTechCodes)
                    }
                )
                AnimatedVisibility(
                    visible = techExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        ChangesFilterState.ALL_TECHS.forEach { (code, label) ->
                            val checked = filter.techs.contains(code)
                            FilterCheckboxRow(
                                label = label,
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    filter = filter.copy(
                                        techs = if (isChecked) filter.techs + code else filter.techs - code
                                    )
                                }
                            )
                        }
                    }
                }

                // 2. FRÉQUENCES
                val allFreqCodes = ChangesFilterState.ALL_FREQS.map { it.first }.toSet()
                val allFreqsSelected = filter.freqs.containsAll(allFreqCodes)
                FilterSectionHeader(
                    title = "Fréquences",
                    isExpanded = freqExpanded,
                    onToggleExpand = { freqExpanded = !freqExpanded },
                    actionLabel = if (allFreqsSelected) "Tout décocher" else "Tout cocher",
                    onAction = {
                        filter = filter.copy(freqs = if (allFreqsSelected) emptySet() else allFreqCodes)
                    }
                )
                AnimatedVisibility(
                    visible = freqExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        ChangesFilterState.ALL_FREQS.forEach { (freq, label) ->
                            val checked = filter.freqs.contains(freq)
                            FilterCheckboxRow(
                                label = label,
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    filter = filter.copy(
                                        freqs = if (isChecked) filter.freqs + freq else filter.freqs - freq
                                    )
                                }
                            )
                        }
                    }
                }

                // 3. OPÉRATEURS
                val allOps = ChangesFilterState.defaultAllOperators()
                val allOpsSelected = filter.operators.containsAll(allOps)
                FilterSectionHeader(
                    title = "Opérateurs",
                    isExpanded = opExpanded,
                    onToggleExpand = { opExpanded = !opExpanded },
                    actionLabel = if (allOpsSelected) "Tout décocher" else "Tout cocher",
                    onAction = {
                        filter = filter.copy(operators = if (allOpsSelected) emptySet() else allOps)
                    }
                )
                AnimatedVisibility(
                    visible = opExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        // France Métropolitaine
                        val metroKeys = ChangesFilterState.METRO_OP_KEYS.map { it.first }.toSet()
                        val allMetroSelected = filter.operators.containsAll(metroKeys)
                        FilterSubSectionHeader(
                            title = "France Métropolitaine",
                            isExpanded = metroExpanded,
                            onToggleExpand = { metroExpanded = !metroExpanded },
                            actionLabel = if (allMetroSelected) "Tout décocher" else "Tout cocher",
                            onAction = {
                                filter = filter.copy(
                                    operators = if (allMetroSelected) filter.operators - metroKeys else filter.operators + metroKeys
                                )
                            }
                        )
                        AnimatedVisibility(
                            visible = metroExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                ChangesFilterState.METRO_OP_KEYS.forEach { (key, label) ->
                                    val checked = filter.operators.contains(key)
                                    val spec = OperatorColors.specForKey(key)
                                    val color = spec?.let { Color(it.colorArgb) }
                                    FilterCheckboxRow(
                                        label = label,
                                        checked = checked,
                                        dotColor = color,
                                        onCheckedChange = { isChecked ->
                                            filter = filter.copy(
                                                operators = if (isChecked) filter.operators + key else filter.operators - key
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        // DROM/COM
                        val dromKeys = ChangesFilterState.DROM_OP_KEYS.map { it.first }.toSet()
                        val allDromSelected = filter.operators.containsAll(dromKeys)
                        FilterSubSectionHeader(
                            title = "DROM/COM",
                            isExpanded = dromExpanded,
                            onToggleExpand = { dromExpanded = !dromExpanded },
                            actionLabel = if (allDromSelected) "Tout décocher" else "Tout cocher",
                            onAction = {
                                filter = filter.copy(
                                    operators = if (allDromSelected) filter.operators - dromKeys else filter.operators + dromKeys
                                )
                            }
                        )
                        AnimatedVisibility(
                            visible = dromExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                ChangesFilterState.DROM_OP_KEYS.forEach { (key, label) ->
                                    val checked = filter.operators.contains(key)
                                    val spec = OperatorColors.specForKey(key)
                                    val color = spec?.let { Color(it.colorArgb) }
                                    FilterCheckboxRow(
                                        label = label,
                                        checked = checked,
                                        dotColor = color,
                                        onCheckedChange = { isChecked ->
                                            filter = filter.copy(
                                                operators = if (isChecked) filter.operators + key else filter.operators - key
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. ACTIONS
                val allActions = ChangeActionType.values().toSet()
                val allActionsSelected = filter.actions.containsAll(allActions)
                FilterSectionHeader(
                    title = "Actions",
                    isExpanded = actionsExpanded,
                    onToggleExpand = { actionsExpanded = !actionsExpanded },
                    actionLabel = if (allActionsSelected) "Tout décocher" else "Tout cocher",
                    onAction = {
                        filter = filter.copy(actions = if (allActionsSelected) emptySet() else allActions)
                    }
                )
                AnimatedVisibility(
                    visible = actionsExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        ChangeActionType.values().forEach { action ->
                            val checked = filter.actions.contains(action)
                            val (iconRes, iconTint) = when (action) {
                                ChangeActionType.SYSTEM_ADDED -> Pair(fr.geotower.R.drawable.ic_change_ajout, Color.Black)
                                ChangeActionType.ACTIVATION -> Pair(fr.geotower.R.drawable.ic_change_activation, Color.Black)
                                ChangeActionType.DEACTIVATION -> Pair(fr.geotower.R.drawable.ic_change_extinction, Color.Black)
                                ChangeActionType.SYSTEM_REMOVED -> Pair(fr.geotower.R.drawable.ic_change_extinction, Color.Black)
                                ChangeActionType.SITE_ADDED -> Pair(fr.geotower.R.drawable.ic_change_ajout, Color.Black)
                                ChangeActionType.SITE_REMOVED -> Pair(fr.geotower.R.drawable.ic_change_suppression, Color.Black)
                                else -> Pair(null, null)
                            }
                            FilterCheckboxRow(
                                label = action.label,
                                checked = checked,
                                iconRes = iconRes,
                                iconTint = iconTint,
                                onCheckedChange = { isChecked ->
                                    filter = filter.copy(
                                        actions = if (isChecked) filter.actions + action else filter.actions - action
                                    )
                                }
                            )
                        }
                    }
                }

                // 5. SITES NEUFS / EXISTANTS
                FilterSectionHeader(
                    title = "Sites Neufs",
                    isExpanded = siteTypeExpanded,
                    onToggleExpand = { siteTypeExpanded = !siteTypeExpanded },
                    actionLabel = null,
                    onAction = null
                )
                AnimatedVisibility(
                    visible = siteTypeExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        SiteTypeOption.values().forEach { option ->
                            val selected = filter.siteType == option
                            FilterRadioRow(
                                label = option.label,
                                selected = selected,
                                onSelect = { filter = filter.copy(siteType = option) }
                            )
                        }
                    }
                }
            }

            // Barre inférieure fixe avec bouton d'application
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = sizing.spacing(20.dp), vertical = sizing.spacing(12.dp)),
                    horizontalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Annuler")
                    }

                    Button(
                        onClick = {
                            onApply(filter)
                            onDismiss()
                        },
                        modifier = Modifier.weight(2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        val label = if (matchingCount > 1) "$matchingCount résultats" else "$matchingCount résultat"
                        Text("Afficher ($label)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSectionHeader(
    title: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    actionLabel: String?,
    onAction: (() -> Unit)?
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val rotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "arrowRotation")

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(6.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Replier" else "Déplier",
                    modifier = Modifier
                        .size(sizing.component(20.dp))
                        .rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = title,
                    style = sizing.textStyle(MaterialTheme.typography.titleSmall),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (actionLabel != null && onAction != null) {
                OutlinedButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(28.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Text(text = actionLabel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun FilterSubSectionHeader(
    title: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    actionLabel: String,
    onAction: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val rotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "subArrowRotation")

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = sizing.spacing(20.dp), end = sizing.spacing(16.dp), top = sizing.spacing(4.dp), bottom = sizing.spacing(4.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(sizing.spacing(6.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Replier" else "Déplier",
                    modifier = Modifier
                        .size(sizing.component(18.dp))
                        .rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = title,
                    style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(26.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Text(text = actionLabel, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FilterCheckboxRow(
    label: String,
    checked: Boolean,
    dotColor: Color? = null,
    iconRes: Int? = null,
    iconTint: Color? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(6.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
        }
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(sizing.component(18.dp))
            )
            Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
        }
        Text(
            text = label,
            style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FilterRadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(6.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
        Text(
            text = label,
            style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
