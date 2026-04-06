package fr.geotower.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import fr.geotower.R
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings

// ✅ CLASSE DE DONNÉES COMPLÈTE
data class OperatorStat(
    val name: String,
    val count: Int,
    val logoRes: Int,
    val color: Color,
    val groupedFreqs: Map<String, List<Pair<String, Int>>>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityStatsDetailSheet(
    antennas: List<LocalisationEntity>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val defaultOp = AppConfig.defaultOperator.value
    val txtOthers = AppStrings.others

    // LECTURE DIRECTE DEPUIS LA COLONNE "FILTRES"
    val stats = remember<List<OperatorStat>>(antennas, defaultOp) {
        val normalizeInsee: (String?) -> String? = { code ->
            when {
                code == null -> null
                code.startsWith("751") && code.length == 5 -> "75056"
                code.startsWith("132") && code.length == 5 -> "13055"
                code.startsWith("6938") && code.length == 5 -> "69123"
                else -> code
            }
        }

        val targetInsee = antennas.mapNotNull { normalizeInsee(it.codeInsee)?.takeIf { c -> c.isNotBlank() } }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

        val cityAntennas = if (targetInsee != null) antennas.filter { normalizeInsee(it.codeInsee) == targetInsee } else antennas

        val regexFiltre = Regex("^(2G|3G|4G|5G)(\\d{3,4})$")

        val rawList = listOf(
            Triple("ORANGE", R.drawable.logo_orange, Color(0xFFFF6600)),
            Triple("BOUYGUES", R.drawable.logo_bouygues, Color(0xFF00295F)),
            Triple("SFR", R.drawable.logo_sfr, Color(0xFFE2001A)),
            Triple("FREE", R.drawable.logo_free, Color(0xFF757575))
        ).map { (opKey, logo, color) ->

            val opAnts = cityAntennas.filter { it.operateur?.contains(opKey, true) == true }

            val opAntennasGrouped = opAnts.groupBy { "${Math.round(it.latitude * 10000.0)}_${Math.round(it.longitude * 10000.0)}" }
            val siteCount = opAntennasGrouped.size

            val counts = mutableMapOf<String, Int>()

            opAntennasGrouped.values.forEach { siteAntennas ->
                val siteSystems = siteAntennas.flatMap { ant ->
                    val filtresStr = ant.filtres ?: ""
                    filtresStr.split(Regex("\\s+")).mapNotNull { token ->
                        val match = regexFiltre.find(token.trim().uppercase())
                        if (match != null) {
                            val tech = match.groupValues[1]
                            val freq = match.groupValues[2]
                            "$tech|$freq"
                        } else null
                    }
                }.distinct()

                siteSystems.forEach { sys ->
                    counts[sys] = (counts[sys] ?: 0) + 1
                }
            }

            val groupedByTech = counts.toList().groupBy { (sys, _) ->
                sys.substringBefore("|")
            }.mapValues { (_, items) ->
                items.map { (sys, count) ->
                    Pair(sys.substringAfter("|"), count)
                }
            }.toSortedMap(compareBy { tech ->
                val idx = AppConfig.siteTechnoOrder.value.indexOf(tech)
                if (idx == -1) 99 else idx
            })

            val displayOpName = when(opKey) {
                "ORANGE" -> "Orange"
                "BOUYGUES" -> "Bouygues"
                "SFR" -> "SFR"
                "FREE" -> "Free"
                else -> opKey
            }

            OperatorStat(displayOpName, siteCount, logo, color, groupedByTech)
        }

        rawList.sortedWith { a, b ->
            if (a.name == b.name) 0
            else if (a.name.equals(defaultOp, ignoreCase = true)) -1
            else if (b.name.equals(defaultOp, ignoreCase = true)) 1
            else b.count.compareTo(a.count)
        }
    }

    var expandedOps by remember { mutableStateOf(setOf<String>()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = AppStrings.operatorDetailsTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(24.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(stats) { stat ->
                    val isExpanded = expandedOps.contains(stat.name)
                    val arrowRotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "arrowAnim")

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        // ✅ BANDEAU OPÉRATEUR : alpha = 0.5f
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedOps = if (isExpanded) expandedOps - stat.name else expandedOps + stat.name
                            }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = painterResource(id = stat.logoRes),
                                    contentDescription = stat.name,
                                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = stat.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(text = AppStrings.sitesCount(stat.count), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Box(modifier = Modifier.background(stat.color, CircleShape).padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(text = stat.count.toString(), fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.rotate(arrowRotation))
                            }

                            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), thickness = 0.5.dp)

                                    CartoradioGroupedTable(groupedData = stat.groupedFreqs, brandColor = stat.color)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// LE TABLEAU
@Composable
fun CartoradioGroupedTable(
    groupedData: Map<String, List<Pair<String, Int>>>,
    brandColor: Color
) {
    val tableBgColor = MaterialTheme.colorScheme.surface

    if (groupedData.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().background(tableBgColor).padding(16.dp), contentAlignment = Alignment.Center) {
            Text("Données techniques non disponibles", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // ✅ CHANGEMENT ICI : En-tête du tableau mis à 0.5f pour matcher avec le reste
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(AppStrings.frequenciesAndTechs, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(AppStrings.sitesLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        groupedData.forEach { (tech, items) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // ✅ COLONNE TECHNOLOGIE : Déjà à 0.5f, donc elle matche avec l'en-tête et le bandeau
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            ) {
                // COLONNE GAUCHE
                Box(modifier = Modifier.weight(0.25f).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Text(text = tech, fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // COLONNE DROITE
                Column(modifier = Modifier.weight(0.75f).background(tableBgColor)) {
                    val sortedItems = items.sortedWith(compareBy { pair ->
                        val freqValue = Regex("\\d+").findAll(pair.first).map { it.value }.lastOrNull() ?: ""
                        val orderList = when(tech) {
                            "5G" -> AppConfig.siteFreqOrder5G.value
                            "4G" -> AppConfig.siteFreqOrder4G.value
                            "3G" -> AppConfig.siteFreqOrder3G.value
                            "2G" -> AppConfig.siteFreqOrder2G.value
                            else -> emptyList()
                        }
                        val idx = orderList.indexOf(freqValue)
                        if (idx == -1) 999 else idx
                    })

                    sortedItems.forEachIndexed { index, (sys, count) ->
                        val isAlternate = index % 2 != 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isAlternate) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (sys.contains("MHz", true)) sys else "$sys MHz",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = count.toString(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = brandColor
                            )
                        }
                    }
                }
            }
        }
    }
}