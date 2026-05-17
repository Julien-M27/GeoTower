package fr.geotower.ui.screens.help

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.geoTowerLazyListFadingEdge
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings
import kotlinx.coroutines.launch
import java.text.Normalizer

private data class HelpAction(
    val id: String,
    val title: String,
    val description: String
)

private data class HelpSection(
    val id: String,
    val title: String,
    val body: String,
    val actions: List<HelpAction> = emptyList(),
    val visual: HelpVisual? = null
)

private data class HelpTopic(
    val id: String,
    val title: String,
    val subtitle: String,
    val keywords: List<String>,
    val sections: List<HelpSection>
)

private data class HelpSectionSpec(
    val id: String,
    val actionIds: List<String> = emptyList(),
    val visual: HelpVisual? = null
)

private data class HelpTopicSpec(
    val id: String,
    val keywords: List<String>,
    val sections: List<HelpSectionSpec>
)

private data class HelpUiStyle(
    val useOneUi: Boolean,
    val backgroundColor: Color,
    val cardShape: Shape,
    val softShape: Shape,
    val chipShape: Shape,
    val cardColor: Color,
    val secondaryCardColor: Color,
    val heroColor: Color,
    val cardBorder: BorderStroke?
)

private enum class HelpVisual(val id: String) {
    Home("home"),
    Nearby("nearby"),
    Map("map"),
    Support("support"),
    Site("site"),
    Elevation("elevation"),
    Throughput("throughput"),
    Photos("photos"),
    Share("share"),
    Settings("settings"),
    Data("data"),
    About("about")
}

private enum class HelpVisualBlockType {
    TopBar,
    Search,
    Card,
    List,
    Button,
    Map,
    Marker,
    Circle,
    Fab,
    Graph,
    Preview,
    Panel
}

private data class HelpVisualBlock(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val type: HelpVisualBlockType,
    val marker: Int? = null
)

@Composable
fun HelpScreen(navController: NavController) {
    val topics = helpTopics()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedTopicId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastClickTime by remember { mutableLongStateOf(0L) }
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "home")
    val themeMode by AppConfig.themeMode
    val isOled by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val useOneUi = AppConfig.useOneUiDesign
    val helpStyle = HelpUiStyle(
        useOneUi = useOneUi,
        backgroundColor = if (isDark && isOled) Color.Black else MaterialTheme.colorScheme.background,
        cardShape = if (useOneUi) RoundedCornerShape(28.dp) else RoundedCornerShape(18.dp),
        softShape = if (useOneUi) RoundedCornerShape(24.dp) else RoundedCornerShape(18.dp),
        chipShape = if (useOneUi) RoundedCornerShape(18.dp) else RoundedCornerShape(12.dp),
        cardColor = if (useOneUi) {
            if (isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        secondaryCardColor = if (useOneUi) {
            if (isDark) Color(0xFF2B2B2B) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        heroColor = if (useOneUi) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.72f else 0.95f)
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        cardBorder = if (useOneUi) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
    )

    fun safeClick(action: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > 700L) {
            lastClickTime = currentTime
            action()
        }
    }

    fun navigateBack() {
        if (selectedTopicId != null) {
            selectedTopicId = null
        } else {
            safeBackNavigation.navigateBack()
        }
    }

    val selectedTopic = topics.firstOrNull { it.id == selectedTopicId }
    val filteredTopics = if (query.isBlank()) topics else topics.filter { it.matches(query) }

    BackHandler(enabled = !safeBackNavigation.isLocked) {
        safeClick { navigateBack() }
    }

    Scaffold(
        topBar = {
            HelpTopBar(
                onBack = { safeClick { navigateBack() } },
                backEnabled = !safeBackNavigation.isLocked,
                backgroundColor = helpStyle.backgroundColor
            )
        },
        containerColor = helpStyle.backgroundColor
    ) { padding ->
        if (selectedTopic == null) {
            HelpSummary(
                topics = filteredTopics,
                query = query,
                onQueryChange = { query = it },
                onClearQuery = { safeClick { query = "" } },
                onTopicClick = { topic -> selectedTopicId = topic.id },
                safeClick = ::safeClick,
                style = helpStyle,
                modifier = Modifier.padding(top = padding.calculateTopPadding())
            )
        } else {
            HelpTopicDetail(
                topic = selectedTopic,
                onBackToSummary = { safeClick { selectedTopicId = null } },
                safeClick = ::safeClick,
                style = helpStyle,
                modifier = Modifier.padding(top = padding.calculateTopPadding())
            )
        }
    }
}

@Composable
private fun HelpTopBar(
    onBack: () -> Unit,
    backEnabled: Boolean,
    backgroundColor: Color
) {
    GeoTowerBackTopBar(
        title = AppStrings.helpTitle,
        onBack = onBack,
        backgroundColor = backgroundColor,
        backEnabled = backEnabled
    )
}

@Composable
private fun HelpSummary(
    topics: List<HelpTopic>,
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onTopicClick: (HelpTopic) -> Unit,
    safeClick: (() -> Unit) -> Unit,
    style: HelpUiStyle,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .geoTowerLazyListFadingEdge(listState),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 20.dp,
            end = 20.dp,
            bottom = 20.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                color = style.heroColor,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = style.cardShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = AppStrings.helpCenterTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = AppStrings.helpCenterIntro,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        item {
            HelpSearchField(
                query = query,
                onQueryChange = onQueryChange,
                onClearQuery = onClearQuery,
                style = style
            )
        }

        item {
            Text(
                text = if (query.isBlank()) AppStrings.helpContents else AppStrings.helpResults,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (topics.isEmpty()) {
            item {
                Text(
                    text = AppStrings.helpNoResults,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(topics, key = { it.id }) { topic ->
                HelpTopicCard(
                    topic = topic,
                    onClick = { onTopicClick(topic) },
                    safeClick = safeClick,
                    style = style
                )
            }
        }
    }
}

@Composable
private fun HelpSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    style: HelpUiStyle
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp),
        singleLine = true,
        shape = style.softShape,
        textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = if (style.useOneUi) style.cardColor else Color.Transparent,
            unfocusedContainerColor = if (style.useOneUi) style.cardColor else Color.Transparent,
            focusedBorderColor = if (style.useOneUi) Color.Transparent else MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = if (style.useOneUi) Color.Transparent else MaterialTheme.colorScheme.outline,
            disabledBorderColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(Icons.Default.Close, contentDescription = AppStrings.helpClearSearch)
                }
            }
        },
        placeholder = { Text(AppStrings.helpSearchPlaceholder, style = MaterialTheme.typography.bodyLarge) }
    )
}

@Composable
private fun HelpTopicCard(
    topic: HelpTopic,
    onClick: () -> Unit,
    safeClick: (() -> Unit) -> Unit,
    style: HelpUiStyle
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { safeClick { onClick() } },
        shape = style.cardShape,
        border = style.cardBorder,
        colors = CardDefaults.cardColors(containerColor = style.secondaryCardColor)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(topic.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                topic.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            AssistChip(
                onClick = { safeClick { onClick() } },
                shape = style.chipShape,
                label = { Text(AppStrings.helpSectionCount(topic.sections.size)) }
            )
        }
    }
}

@Composable
private fun HelpTopicDetail(
    topic: HelpTopic,
    onBackToSummary: () -> Unit,
    safeClick: (() -> Unit) -> Unit,
    style: HelpUiStyle,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val firstSectionIndex = 2

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .geoTowerLazyListFadingEdge(listState),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 20.dp,
            end = 20.dp,
            bottom = 20.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(topic.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(topic.subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            Card(
                shape = style.cardShape,
                border = style.cardBorder,
                colors = CardDefaults.cardColors(containerColor = style.secondaryCardColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(AppStrings.helpLocalContents, fontWeight = FontWeight.Bold)
                    topic.sections.forEachIndexed { index, section ->
                        TextButton(
                            onClick = {
                                safeClick {
                                    scope.launch { listState.animateScrollToItem(firstSectionIndex + index) }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${index + 1}. ${section.title}",
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        items(topic.sections, key = { it.id }) { section ->
            HelpSectionCard(section, style)
        }

        item {
            OutlinedButton(onClick = onBackToSummary, modifier = Modifier.fillMaxWidth(), shape = style.softShape) {
                Text(AppStrings.helpBackToContents)
            }
        }
    }
}

@Composable
private fun HelpSectionCard(section: HelpSection, style: HelpUiStyle) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = style.cardShape,
        border = style.cardBorder,
        colors = CardDefaults.cardColors(containerColor = style.cardColor)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(section.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            section.visual?.let { visual ->
                HelpVisualCard(visual = visual, style = style)
            }

            if (section.actions.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                section.actions.forEach { action ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(action.title, fontWeight = FontWeight.SemiBold)
                        Text(action.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpVisualCard(visual: HelpVisual, style: HelpUiStyle) {
    val visualBackground = if (style.useOneUi) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.48f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = style.softShape,
        color = visualBackground,
        border = if (style.useOneUi) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = AppStrings.helpVisualTitle(visual.id),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            HelpVisualMockup(visual = visual, style = style)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..3).forEach { index ->
                    HelpVisualLegendRow(index, AppStrings.helpVisualLabel(visual.id, index))
                }
            }
        }
    }
}

@Composable
private fun HelpVisualMockup(visual: HelpVisual, style: HelpUiStyle) {
    val mockupBackground = if (style.useOneUi) {
        MaterialTheme.colorScheme.background.copy(alpha = 0.62f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val isCompact = maxWidth < 420.dp
        val mockupHeight = helpVisualHeight(visual, isCompact)
        val contentWidth = maxWidth - 24.dp
        val contentHeight = mockupHeight - 24.dp
        val blocks = helpVisualBlocks(visual, compact = isCompact)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(mockupHeight)
                .clip(style.softShape)
                .background(mockupBackground)
                .padding(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                blocks.forEach { block ->
                    HelpVisualBlockView(
                        block = block,
                        parentWidth = contentWidth,
                        parentHeight = contentHeight,
                        style = style
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpVisualLegendRow(index: Int, label: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HelpVisualMarker(index, modifier = Modifier.size(20.dp), fontSize = 11)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HelpVisualBlockView(
    block: HelpVisualBlock,
    parentWidth: androidx.compose.ui.unit.Dp,
    parentHeight: androidx.compose.ui.unit.Dp,
    style: HelpUiStyle
) {
    val blockColor = helpVisualBlockColor(block.type)
    val shape = when (block.type) {
        HelpVisualBlockType.Marker,
        HelpVisualBlockType.Circle -> CircleShape
        HelpVisualBlockType.Button -> RoundedCornerShape(999.dp)
        HelpVisualBlockType.Fab -> RoundedCornerShape(12.dp)
        else -> if (style.useOneUi) RoundedCornerShape(18.dp) else RoundedCornerShape(10.dp)
    }

    Surface(
        modifier = Modifier
            .offset(x = parentWidth * block.x, y = parentHeight * block.y)
            .width(parentWidth * block.width)
            .height(parentHeight * block.height),
        shape = shape,
        color = blockColor,
        tonalElevation = if (style.useOneUi) 1.dp else 0.dp
    ) {
        block.marker?.let { marker ->
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                HelpVisualMarker(marker, modifier = Modifier.size(20.dp), fontSize = 11)
            }
        }
    }
}

@Composable
private fun HelpVisualMarker(number: Int, modifier: Modifier = Modifier.size(24.dp), fontSize: Int = 12) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = number.toString(),
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = fontSize.sp,
                lineHeight = fontSize.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall.copy(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )
        }
    }
}

@Composable
private fun helpVisualBlockColor(type: HelpVisualBlockType): Color {
    return when (type) {
        HelpVisualBlockType.TopBar -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
        HelpVisualBlockType.Search -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
        HelpVisualBlockType.Card -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
        HelpVisualBlockType.List -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f)
        HelpVisualBlockType.Button -> MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
        HelpVisualBlockType.Map -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.82f)
        HelpVisualBlockType.Marker -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.78f)
        HelpVisualBlockType.Circle -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f)
        HelpVisualBlockType.Fab -> MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
        HelpVisualBlockType.Graph -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.48f)
        HelpVisualBlockType.Preview -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
        HelpVisualBlockType.Panel -> MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
    }
}

private fun helpVisualHeight(visual: HelpVisual, compact: Boolean): androidx.compose.ui.unit.Dp {
    return when (visual) {
        HelpVisual.Home -> if (compact) 340.dp else 360.dp
        else -> if (compact) 240.dp else 260.dp
    }
}

private fun helpVisualBlocks(visual: HelpVisual, compact: Boolean): List<HelpVisualBlock> {
    return when (visual) {
        HelpVisual.Home -> listOf(
            HelpVisualBlock(0.08f, 0.03f, 0.84f, 0.07f, HelpVisualBlockType.TopBar, 1),
            HelpVisualBlock(0.12f, 0.16f, 0.76f, 0.09f, HelpVisualBlockType.Button, 2),
            HelpVisualBlock(0.12f, 0.29f, 0.76f, 0.09f, HelpVisualBlockType.Button),
            HelpVisualBlock(0.12f, 0.42f, 0.76f, 0.09f, HelpVisualBlockType.Button),
            HelpVisualBlock(0.12f, 0.55f, 0.76f, 0.09f, HelpVisualBlockType.Button),
            HelpVisualBlock(0.12f, 0.68f, 0.76f, 0.09f, HelpVisualBlockType.Button),
            HelpVisualBlock(0.12f, 0.81f, 0.76f, 0.09f, HelpVisualBlockType.Button),
            HelpVisualBlock(0.41f, 0.93f, 0.18f, 0.08f, HelpVisualBlockType.Circle),
            HelpVisualBlock(0.84f, 0.92f, 0.12f, 0.08f, HelpVisualBlockType.Fab, 3)
        )
        HelpVisual.Nearby -> listOf(
            HelpVisualBlock(0.06f, 0.06f, 0.88f, 0.14f, HelpVisualBlockType.Search, 1),
            HelpVisualBlock(0.1f, 0.28f, 0.16f, 0.1f, HelpVisualBlockType.Button, 2),
            HelpVisualBlock(0.32f, 0.28f, 0.16f, 0.1f, HelpVisualBlockType.Button),
            HelpVisualBlock(0.54f, 0.28f, 0.16f, 0.1f, HelpVisualBlockType.Button),
            HelpVisualBlock(0.76f, 0.28f, 0.14f, 0.1f, HelpVisualBlockType.Button),
            HelpVisualBlock(0.06f, 0.47f, 0.88f, 0.13f, HelpVisualBlockType.List, 3),
            HelpVisualBlock(0.06f, 0.66f, 0.88f, 0.13f, HelpVisualBlockType.List),
            HelpVisualBlock(0.06f, 0.85f, 0.88f, 0.13f, HelpVisualBlockType.List)
        )
        HelpVisual.Map -> listOf(
            HelpVisualBlock(0.04f, 0.04f, 0.92f, 0.88f, HelpVisualBlockType.Map),
            HelpVisualBlock(0.09f, 0.08f, 0.6f, 0.11f, HelpVisualBlockType.Search, 1),
            HelpVisualBlock(0.2f, 0.42f, 0.09f, 0.1f, HelpVisualBlockType.Marker, 2),
            HelpVisualBlock(0.58f, 0.55f, 0.09f, 0.1f, HelpVisualBlockType.Marker),
            HelpVisualBlock(0.78f, 0.18f, 0.12f, 0.12f, HelpVisualBlockType.Button, 3),
            HelpVisualBlock(0.78f, 0.35f, 0.12f, 0.12f, HelpVisualBlockType.Button)
        )
        HelpVisual.Support -> listOf(
            HelpVisualBlock(0.06f, 0.06f, 0.88f, 0.18f, HelpVisualBlockType.TopBar, 1),
            HelpVisualBlock(0.08f, 0.32f, 0.84f, 0.14f, HelpVisualBlockType.List, 2),
            HelpVisualBlock(0.08f, 0.52f, 0.84f, 0.14f, HelpVisualBlockType.List),
            HelpVisualBlock(0.12f, 0.76f, 0.16f, 0.12f, HelpVisualBlockType.Button, 3),
            HelpVisualBlock(0.36f, 0.76f, 0.16f, 0.12f, HelpVisualBlockType.Button),
            HelpVisualBlock(0.6f, 0.76f, 0.16f, 0.12f, HelpVisualBlockType.Button)
        )
        HelpVisual.Site -> listOf(
            HelpVisualBlock(0.06f, 0.05f, 0.88f, 0.16f, HelpVisualBlockType.TopBar, 1),
            HelpVisualBlock(0.08f, 0.29f, 0.84f, 0.14f, HelpVisualBlockType.Card),
            HelpVisualBlock(0.08f, 0.49f, 0.84f, 0.16f, HelpVisualBlockType.List, 2),
            HelpVisualBlock(0.1f, 0.76f, 0.16f, 0.12f, HelpVisualBlockType.Button, 3),
            HelpVisualBlock(0.33f, 0.76f, 0.16f, 0.12f, HelpVisualBlockType.Button),
            HelpVisualBlock(0.56f, 0.76f, 0.16f, 0.12f, HelpVisualBlockType.Button)
        )
        HelpVisual.Elevation -> listOf(
            HelpVisualBlock(0.06f, 0.06f, 0.88f, 0.12f, HelpVisualBlockType.TopBar, 1),
            HelpVisualBlock(0.08f, 0.34f, 0.84f, 0.28f, HelpVisualBlockType.Graph, 2),
            HelpVisualBlock(0.12f, 0.74f, 0.34f, 0.12f, HelpVisualBlockType.Button, 3),
            HelpVisualBlock(0.54f, 0.74f, 0.34f, 0.12f, HelpVisualBlockType.Button)
        )
        HelpVisual.Throughput -> listOf(
            HelpVisualBlock(0.06f, 0.06f, 0.88f, 0.13f, HelpVisualBlockType.TopBar, 1),
            HelpVisualBlock(0.08f, 0.27f, 0.23f, 0.1f, HelpVisualBlockType.Button, 2),
            HelpVisualBlock(0.36f, 0.27f, 0.23f, 0.1f, HelpVisualBlockType.Button),
            HelpVisualBlock(0.64f, 0.27f, 0.23f, 0.1f, HelpVisualBlockType.Button),
            HelpVisualBlock(0.08f, 0.48f, 0.39f, 0.3f, HelpVisualBlockType.List),
            HelpVisualBlock(0.54f, 0.48f, 0.36f, 0.3f, HelpVisualBlockType.Map, 3)
        )
        HelpVisual.Photos -> listOf(
            HelpVisualBlock(0.08f, 0.08f, 0.84f, 0.56f, HelpVisualBlockType.Preview, 1),
            HelpVisualBlock(0.16f, 0.72f, 0.18f, 0.12f, HelpVisualBlockType.Button, 2),
            HelpVisualBlock(0.48f, 0.72f, 0.36f, 0.12f, HelpVisualBlockType.Button, 3)
        )
        HelpVisual.Share -> listOf(
            HelpVisualBlock(0.06f, 0.06f, 0.36f, 0.78f, HelpVisualBlockType.List, 1),
            HelpVisualBlock(0.5f, 0.08f, 0.4f, 0.54f, HelpVisualBlockType.Preview, 2),
            HelpVisualBlock(0.54f, 0.72f, 0.32f, 0.12f, HelpVisualBlockType.Button, 3)
        )
        HelpVisual.Settings -> if (compact) {
            listOf(
                HelpVisualBlock(0.06f, 0.05f, 0.88f, 0.13f, HelpVisualBlockType.TopBar, 1),
                HelpVisualBlock(0.08f, 0.25f, 0.84f, 0.15f, HelpVisualBlockType.Card, 2),
                HelpVisualBlock(0.08f, 0.47f, 0.84f, 0.15f, HelpVisualBlockType.Card),
                HelpVisualBlock(0.08f, 0.7f, 0.84f, 0.16f, HelpVisualBlockType.List, 3)
            )
        } else {
            listOf(
                HelpVisualBlock(0.06f, 0.06f, 0.22f, 0.78f, HelpVisualBlockType.Panel, 1),
                HelpVisualBlock(0.36f, 0.08f, 0.56f, 0.16f, HelpVisualBlockType.Card, 2),
                HelpVisualBlock(0.36f, 0.32f, 0.56f, 0.16f, HelpVisualBlockType.Card),
                HelpVisualBlock(0.36f, 0.6f, 0.56f, 0.2f, HelpVisualBlockType.List, 3)
            )
        }
        HelpVisual.Data -> listOf(
            HelpVisualBlock(0.08f, 0.08f, 0.84f, 0.18f, HelpVisualBlockType.Card, 1),
            HelpVisualBlock(0.08f, 0.36f, 0.84f, 0.18f, HelpVisualBlockType.Card, 2),
            HelpVisualBlock(0.12f, 0.7f, 0.76f, 0.12f, HelpVisualBlockType.TopBar, 3)
        )
        HelpVisual.About -> if (compact) {
            listOf(
                HelpVisualBlock(0.06f, 0.05f, 0.88f, 0.13f, HelpVisualBlockType.TopBar, 1),
                HelpVisualBlock(0.08f, 0.25f, 0.84f, 0.16f, HelpVisualBlockType.Card, 2),
                HelpVisualBlock(0.08f, 0.49f, 0.84f, 0.16f, HelpVisualBlockType.Card, 3),
                HelpVisualBlock(0.08f, 0.72f, 0.84f, 0.14f, HelpVisualBlockType.Card)
            )
        } else {
            listOf(
                HelpVisualBlock(0.06f, 0.06f, 0.26f, 0.78f, HelpVisualBlockType.Panel, 1),
                HelpVisualBlock(0.4f, 0.08f, 0.5f, 0.18f, HelpVisualBlockType.TopBar, 2),
                HelpVisualBlock(0.4f, 0.36f, 0.5f, 0.18f, HelpVisualBlockType.Card, 3),
                HelpVisualBlock(0.4f, 0.63f, 0.5f, 0.16f, HelpVisualBlockType.Card)
            )
        }
    }
}

private fun HelpTopic.matches(query: String): Boolean {
    val needle = query.helpSearchKey()
    val haystack = buildString {
        append(title).append(' ')
        append(subtitle).append(' ')
        keywords.forEach { append(it).append(' ') }
        sections.forEach { section ->
            append(section.title).append(' ')
            append(section.body).append(' ')
            section.actions.forEach {
                append(it.title).append(' ')
                append(it.description).append(' ')
            }
        }
    }.helpSearchKey()
    return haystack.contains(needle)
}

private fun String.helpSearchKey(): String {
    return Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .lowercase()
}

@Composable
private fun helpTopics(): List<HelpTopic> {
    return helpTopicSpecs().map { topicSpec ->
        HelpTopic(
            id = topicSpec.id,
            title = AppStrings.helpTopicTitle(topicSpec.id),
            subtitle = AppStrings.helpTopicSubtitle(topicSpec.id),
            keywords = topicSpec.keywords,
            sections = topicSpec.sections.map { sectionSpec ->
                HelpSection(
                    id = sectionSpec.id,
                    title = AppStrings.helpSectionTitle(sectionSpec.id),
                    body = AppStrings.helpSectionBody(sectionSpec.id),
                    actions = sectionSpec.actionIds.map { actionId ->
                        HelpAction(
                            id = actionId,
                            title = AppStrings.helpActionTitle(actionId),
                            description = AppStrings.helpActionDesc(actionId)
                        )
                    },
                    visual = sectionSpec.visual
                )
            }
        )
    }
}

private fun helpTopicSpecs(): List<HelpTopicSpec> {
    return listOf(
        HelpTopicSpec(
            id = "start",
            keywords = listOf("debut", "configuration", "base", "gps", "operateur"),
            sections = listOf(
                HelpSectionSpec("start_prepare"),
                HelpSectionSpec("start_search"),
                HelpSectionSpec("start_cards")
            )
        ),
        HelpTopicSpec(
            id = "home",
            keywords = listOf("home", "accueil", "aides", "download", "stats"),
            sections = listOf(
                HelpSectionSpec(
                    "home_buttons",
                    listOf("home_nearby", "home_map", "home_compass", "home_settings", "home_about", "home_help"),
                    visual = HelpVisual.Home
                ),
                HelpSectionSpec("home_banners")
            )
        ),
        HelpTopicSpec(
            id = "nearby",
            keywords = listOf("nearby", "recherche", "ville", "adresse", "cp", "gps", "anfr", "support", "tech"),
            sections = listOf(
                HelpSectionSpec("nearby_search", listOf("search_field", "clear", "quick_suggestions", "info"), visual = HelpVisual.Nearby),
                HelpSectionSpec("nearby_codes"),
                HelpSectionSpec("nearby_list", listOf("load_more", "expand_area"))
            )
        ),
        HelpTopicSpec(
            id = "map",
            keywords = listOf("map", "carte", "gps", "zoom", "filtre", "offline", "hors ligne", "toolbox"),
            sections = listOf(
                HelpSectionSpec("map_controls", listOf("location", "zoom", "map_compass", "scale"), visual = HelpVisual.Map),
                HelpSectionSpec("map_filters"),
                HelpSectionSpec("map_offline")
            )
        ),
        HelpTopicSpec(
            id = "compass",
            keywords = listOf("boussole", "compass", "orientation", "azimut", "cap"),
            sections = listOf(
                HelpSectionSpec("compass_use"),
                HelpSectionSpec("buttons", listOf("live_search", "quit"))
            )
        ),
        HelpTopicSpec(
            id = "support",
            keywords = listOf("support", "pylone", "toit", "operateurs", "split"),
            sections = listOf(
                HelpSectionSpec("support_understand", visual = HelpVisual.Support),
                HelpSectionSpec("actions", listOf("open_map", "navigate", "share", "photos", "operator_site"))
            )
        ),
        HelpTopicSpec(
            id = "site",
            keywords = listOf("site", "frequences", "azimut", "hauteur", "debit", "profil", "speedtest"),
            sections = listOf(
                HelpSectionSpec("site_info", visual = HelpVisual.Site),
                HelpSectionSpec("site_tools", listOf("open_map", "navigate", "share", "elevation_profile", "throughput", "settings_gear"))
            )
        ),
        HelpTopicSpec(
            id = "elevation",
            keywords = listOf("profil", "altimetrique", "fresnel", "relief", "recalcul"),
            sections = listOf(
                HelpSectionSpec("elevation_use", visual = HelpVisual.Elevation),
                HelpSectionSpec("buttons", listOf("recalculate", "calculate_later"))
            )
        ),
        HelpTopicSpec(
            id = "throughput",
            keywords = listOf("debit", "throughput", "qam", "mimo", "4g", "5g", "distance", "cone"),
            sections = listOf(
                HelpSectionSpec("throughput_assumptions", visual = HelpVisual.Throughput),
                HelpSectionSpec("throughput_controls", listOf("custom", "optimal_distance", "mini_map"))
            )
        ),
        HelpTopicSpec(
            id = "photos",
            keywords = listOf("photos", "signalquest", "upload", "carousel", "plein ecran"),
            sections = listOf(
                HelpSectionSpec("photos_view", visual = HelpVisual.Photos),
                HelpSectionSpec("photos_upload")
            )
        ),
        HelpTopicSpec(
            id = "share",
            keywords = listOf("partage", "share", "image", "speedtest", "frequences"),
            sections = listOf(
                HelpSectionSpec("share_create", visual = HelpVisual.Share),
                HelpSectionSpec("share_options")
            )
        ),
        HelpTopicSpec(
            id = "settings",
            keywords = listOf("parametres", "settings", "one ui", "imperial", "split", "personnalisation"),
            sections = listOf(
                HelpSectionSpec("settings_general", visual = HelpVisual.Settings),
                HelpSectionSpec("settings_split"),
                HelpSectionSpec("settings_pages")
            )
        ),
        HelpTopicSpec(
            id = "data",
            keywords = listOf("base", "database", "telechargement", "hors ligne", "offline", "notification"),
            sections = listOf(
                HelpSectionSpec("data_database", visual = HelpVisual.Data),
                HelpSectionSpec("data_maps")
            )
        ),
        HelpTopicSpec(
            id = "about",
            keywords = listOf("about", "version", "sources", "developpement"),
            sections = listOf(
                HelpSectionSpec("about_sections", visual = HelpVisual.About),
                HelpSectionSpec("about_loops")
            )
        ),
        HelpTopicSpec(
            id = "glossary",
            keywords = listOf("glossaire", "icone", "bug", "gps", "erreur", "depannage"),
            sections = listOf(
                HelpSectionSpec("glossary_icons", listOf("back_arrow", "magnifier", "x_icon", "gear", "location", "share_icon", "download", "refresh")),
                HelpSectionSpec("glossary_issues")
            )
        )
    )
}
