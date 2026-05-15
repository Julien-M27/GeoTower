package fr.geotower.ui.screens.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import fr.geotower.data.AnfrRepository
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings
import fr.geotower.utils.OperatorColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(navController: NavController, repository: AnfrRepository) {
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)
    val defaultOp = AppConfig.defaultOperator.value

    val mainBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.background
    val cardBgColor = MaterialTheme.colorScheme.surfaceVariant

    // --- ÉTAT DES DONNÉES ---
    var supportCounts by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var support4GCounts by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var support5GCounts by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) } // ✅ NOUVEAU
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "home")

    BackHandler(enabled = !safeBackNavigation.isLocked) {
        safeBackNavigation.navigateBack()
    }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            isLoading = true
            val totalMap = mutableMapOf<String, Int>()
            val counts4GMap = mutableMapOf<String, Int>()
            val counts5GMap = mutableMapOf<String, Int>() // ✅ NOUVEAU

            val operatorsToFetch = OperatorColors.all

            for (operator in operatorsToFetch) {
                val queryName = operator.aliases.firstOrNull() ?: operator.key
                totalMap[operator.key] = repository.getUniqueSupportCountByOperator(queryName)
                counts4GMap[operator.key] = repository.get4GSupportCountByOperator(queryName)
                counts5GMap[operator.key] = repository.get5GSupportCountByOperator(queryName) // ✅ NOUVEAU
            }

            val baseOrder = OperatorColors.orderedKeys
            val displayOrder = mutableListOf<String>()
            OperatorColors.keyFor(defaultOp)?.let { displayOrder.add(it) }
            baseOrder.forEach { if (!displayOrder.contains(it)) displayOrder.add(it) }

            // Fonction utilitaire pour transformer les Maps en listes triées
            fun formatData(map: Map<String, Int>): List<Pair<String, Int>> {
                val rows = displayOrder.map { op ->
                    val name = OperatorColors.specForKey(op)?.label ?: op
                    Pair(name, map[op] ?: 0)
                }
                return rows.filter { it.second > 0 }.ifEmpty { rows }
            }

            supportCounts = formatData(totalMap)
            support4GCounts = formatData(counts4GMap)
            support5GCounts = formatData(counts5GMap) // ✅ NOUVEAU
            isLoading = false
        }
    }

    Scaffold(
        containerColor = mainBgColor,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(mainBgColor).padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { safeBackNavigation.navigateBack() },
                    enabled = !safeBackNavigation.isLocked,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, AppStrings.back, tint = MaterialTheme.colorScheme.onSurface)
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(AppStrings.statsTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.width(48.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(mainBgColor).verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Graphique 1 : TOTAL
            StatCard(AppStrings.statsSupportsTitle, AppStrings.statsSupportsDesc, supportCounts, isLoading, cardBgColor)

            Spacer(modifier = Modifier.height(16.dp))

            // Graphique 2 : 5G
            StatCard(AppStrings.stats5GTitle, AppStrings.stats5GDesc, support5GCounts, isLoading, cardBgColor)

            Spacer(modifier = Modifier.height(16.dp))

            // ✅ Graphique 3 : 4G
            StatCard(AppStrings.stats4GTitle, AppStrings.stats4GDesc, support4GCounts, isLoading, cardBgColor)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun StatCard(title: String, desc: String, data: List<Pair<String, Int>>, isLoading: Boolean, bgColor: Color) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                SupportBarChart(data = data)
            }
        }
    }
}

// ==========================================
// 📊 COMPOSANT GRAPHIQUE À BARRES SUR MESURE
// ==========================================
@Composable
fun SupportBarChart(data: List<Pair<String, Int>>) {
    val maxCount = data.maxOfOrNull { it.second } ?: 0
    val yMax = if (maxCount > 0) ((maxCount / 5000) + 1) * 5000 else 5000
    val ySteps = (0..yMax step 5000).toList().reversed()

    val xLabelAreaHeight = 24.dp
    val barWidth = 36.dp

    Row(modifier = Modifier.height(250.dp).fillMaxWidth()) {

        // COLONNE DE GAUCHE : L'Axe Y
        Column(
            modifier = Modifier.fillMaxHeight().padding(bottom = xLabelAreaHeight),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            ySteps.forEach { step ->
                Box(
                    modifier = Modifier.height(1.dp).width(35.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = if (step == 0) "0" else "${step / 1000}k",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = false,
                        modifier = Modifier.wrapContentHeight(unbounded = true)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // ZONE DU GRAPHIQUE
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {

            // Lignes horizontales
            Column(
                modifier = Modifier.fillMaxSize().padding(bottom = xLabelAreaHeight),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                ySteps.forEach { _ ->
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        thickness = 1.dp
                    )
                }
            }

            // Barres et Noms
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                data.forEach { (opName, count) ->
                    val barColor = OperatorColors.keyFor(opName)
                        ?.let { Color(OperatorColors.colorArgbForKey(it)) }
                        ?: Color.Gray

                    val fraction = if (yMax > 0) count.toFloat() / yMax.toFloat() else 0f

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(barWidth + 8.dp).fillMaxHeight()
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (count > 0) {
                                    Text(
                                        text = count.toString(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 2.dp),
                                        softWrap = false
                                    )
                                }

                                if (fraction > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight(fraction)
                                            .width(barWidth)
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                            .background(barColor)
                                    )
                                } else {
                                    Box(modifier = Modifier.height(0.dp).width(barWidth))
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .height(xLabelAreaHeight)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = opName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                softWrap = false,
                                maxLines = 1,
                                onTextLayout = { textLayoutResult ->
                                    if (textLayoutResult.hasVisualOverflow) {
                                        // Ignore overflow graphique
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
