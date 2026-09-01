@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package fr.geotower.ui.screens.emitters

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import fr.geotower.R
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.PageCustomizationFooter
import fr.geotower.ui.components.PageCustomizationHint
import fr.geotower.ui.components.PageScrollEdgeButtons
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.components.pageScrollbar
import fr.geotower.ui.navigation.ROOT_FALLBACK_ROUTE
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.screens.settings.FrequencyReferencePagePreferences
import fr.geotower.ui.screens.settings.FrequencyReferenceSettingsSheet
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.PageScrollPrefs
import fr.geotower.utils.PreferenceStores
import androidx.compose.ui.res.stringResource

const val FREQUENCY_REFERENCE_ROUTE = "frequency_reference"

private enum class FrequencyReferenceScope {
    FRANCE,
    INTERNATIONAL
}

enum class FrequencyReferenceTechnology(
    val id: String,
    val titleRes: Int,
    val descriptionRes: Int
) {
    GSM_2G("2G", R.string.appstrings_frequency_reference_2g, R.string.appstrings_frequency_reference_2g_desc),
    UMTS_3G("3G", R.string.appstrings_frequency_reference_3g, R.string.appstrings_frequency_reference_3g_desc),
    LTE_4G("4G", R.string.appstrings_frequency_reference_4g, R.string.appstrings_frequency_reference_4g_desc),
    NR_5G("5G", R.string.appstrings_frequency_reference_5g, R.string.appstrings_frequency_reference_5g_desc)
}

private enum class FrequencyReferenceDuplex {
    FDD,
    TDD,
    SDL
}

private data class FrequencyReferenceBand(
    val technology: FrequencyReferenceTechnology,
    val name: String,
    val duplex: FrequencyReferenceDuplex,
    val uplink: String? = null,
    val downlink: String? = null,
    val sharedRange: String? = null
)

private val frequencyNumberRegex = Regex(
    """(\d+(?:[.,]\d+)?)\s*(MHz|GHz)""",
    RegexOption.IGNORE_CASE
)
private val bandNameRegex = Regex("""\b(?:B|N)\s*(\d+)\b""", RegexOption.IGNORE_CASE)

/** Retourne le début de plage en MHz, quelle que soit l'unité utilisée dans le libellé. */
private fun FrequencyReferenceBand.frequencySortKey(): Double {
    return listOfNotNull(uplink, downlink, sharedRange)
        .mapNotNull { range ->
            frequencyNumberRegex.find(range)?.let { match ->
                match.groupValues[1]
                    .replace(',', '.')
                    .toDoubleOrNull()
                    ?.let { value ->
                        if (match.groupValues[2].equals("GHz", ignoreCase = true)) {
                            value * 1000.0
                        } else {
                            value
                        }
                    }
            }
        }
        .minOrNull() ?: Double.MAX_VALUE
}

/** Clé numérique pour obtenir B1, B2, B3… (et n1, n2, n3…) plutôt qu'un tri alphabétique. */
private fun FrequencyReferenceBand.bandNameSortNumber(): Int =
    bandNameRegex.find(name)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE

private fun FrequencyReferenceBand.bandNameSortGroup(): Int =
    if (bandNameRegex.containsMatchIn(name)) 0 else 1

private fun fdd(
    technology: FrequencyReferenceTechnology,
    name: String,
    uplink: String,
    downlink: String
) = FrequencyReferenceBand(technology, name, FrequencyReferenceDuplex.FDD, uplink, downlink)

private fun tdd(
    technology: FrequencyReferenceTechnology,
    name: String,
    range: String
) = FrequencyReferenceBand(technology, name, FrequencyReferenceDuplex.TDD, sharedRange = range)

private fun sdl(
    technology: FrequencyReferenceTechnology,
    name: String,
    downlink: String
) = FrequencyReferenceBand(technology, name, FrequencyReferenceDuplex.SDL, downlink = downlink)

/**
 * Référentiel embarqué des bandes mobiles. Les plages sont les plages radio normalisées ; elles
 * ne préjugent pas de l'opérateur qui détient une autorisation ni des bandes réellement actives
 * sur un site donné.
 */
private object FrequencyReferenceCatalog {
    val franceMetropolitan: List<FrequencyReferenceBand> = listOf(
        fdd(FrequencyReferenceTechnology.GSM_2G, "GSM 900", "880–915 MHz", "925–960 MHz"),
        fdd(FrequencyReferenceTechnology.GSM_2G, "DCS 1800", "1710–1785 MHz", "1805–1880 MHz"),

        fdd(FrequencyReferenceTechnology.UMTS_3G, "B8 · 900 MHz", "880–915 MHz", "925–960 MHz"),
        fdd(FrequencyReferenceTechnology.UMTS_3G, "B1 · 2100 MHz", "1920–1980 MHz", "2110–2170 MHz"),

        fdd(FrequencyReferenceTechnology.LTE_4G, "B28 · 700 MHz", "703–733 MHz", "758–788 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B20 · 800 MHz", "832–862 MHz", "791–821 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B8 · 900 MHz", "880–915 MHz", "925–960 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B3 · 1800 MHz", "1710–1785 MHz", "1805–1880 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B1 · 2100 MHz", "1920–1980 MHz", "2110–2170 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B7 · 2600 MHz", "2500–2570 MHz", "2620–2690 MHz"),

        fdd(FrequencyReferenceTechnology.NR_5G, "n28 · 700 MHz", "703–733 MHz", "758–788 MHz"),
        fdd(FrequencyReferenceTechnology.NR_5G, "n3 · 1800 MHz", "1710–1785 MHz", "1805–1880 MHz"),
        fdd(FrequencyReferenceTechnology.NR_5G, "n1 · 2100 MHz", "1920–1980 MHz", "2110–2170 MHz"),
        tdd(FrequencyReferenceTechnology.NR_5G, "n78 · 3500 MHz", "3490–3800 MHz")
    )

    /** Bandes 3GPP supplémentaires couramment utilisées hors de France. */
    val international: List<FrequencyReferenceBand> = franceMetropolitan + listOf(
        fdd(FrequencyReferenceTechnology.GSM_2G, "GSM 450", "450.4–457.6 MHz", "460.4–467.6 MHz"),
        fdd(FrequencyReferenceTechnology.GSM_2G, "GSM 850", "824–849 MHz", "869–894 MHz"),
        fdd(FrequencyReferenceTechnology.GSM_2G, "PCS 1900", "1850–1910 MHz", "1930–1990 MHz"),

        fdd(FrequencyReferenceTechnology.UMTS_3G, "B2 · 1900 MHz", "1850–1910 MHz", "1930–1990 MHz"),
        fdd(FrequencyReferenceTechnology.UMTS_3G, "B3 · 1800 MHz", "1710–1785 MHz", "1805–1880 MHz"),
        fdd(FrequencyReferenceTechnology.UMTS_3G, "B4 · AWS", "1710–1755 MHz", "2110–2155 MHz"),
        fdd(FrequencyReferenceTechnology.UMTS_3G, "B5 · 850 MHz", "824–849 MHz", "869–894 MHz"),
        fdd(FrequencyReferenceTechnology.UMTS_3G, "B7 · 2600 MHz", "2500–2570 MHz", "2620–2690 MHz"),
        fdd(FrequencyReferenceTechnology.UMTS_3G, "B9 · 1700 MHz", "1750–1785 MHz", "1845–1880 MHz"),
        fdd(FrequencyReferenceTechnology.UMTS_3G, "B11 · 1500 MHz", "1427.9–1452.9 MHz", "1475.9–1500.9 MHz"),
        fdd(FrequencyReferenceTechnology.UMTS_3G, "B12 · 700 MHz", "698–716 MHz", "728–746 MHz"),
        fdd(FrequencyReferenceTechnology.UMTS_3G, "B13 · 700 MHz", "777–787 MHz", "746–756 MHz"),
        fdd(FrequencyReferenceTechnology.UMTS_3G, "B14 · 700 MHz", "788–798 MHz", "758–768 MHz"),
        fdd(FrequencyReferenceTechnology.UMTS_3G, "B19 · 850 MHz", "830–845 MHz", "875–890 MHz"),
        fdd(FrequencyReferenceTechnology.UMTS_3G, "B20 · 800 MHz", "832–862 MHz", "791–821 MHz"),
        fdd(FrequencyReferenceTechnology.UMTS_3G, "B25 · 1900 MHz", "1850–1915 MHz", "1930–1995 MHz"),
        fdd(FrequencyReferenceTechnology.UMTS_3G, "B26 · 850 MHz", "814–849 MHz", "859–894 MHz"),

        fdd(FrequencyReferenceTechnology.LTE_4G, "B2 · 1900 MHz", "1850–1910 MHz", "1930–1990 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B4 · AWS", "1710–1755 MHz", "2110–2155 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B5 · 850 MHz", "824–849 MHz", "869–894 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B12 · 700 MHz", "699–716 MHz", "729–746 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B13 · 700 MHz", "777–787 MHz", "746–756 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B14 · 700 MHz", "788–798 MHz", "758–768 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B17 · 700 MHz", "704–716 MHz", "734–746 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B18 · 800 MHz", "815–830 MHz", "860–875 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B19 · 850 MHz", "830–845 MHz", "875–890 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B25 · 1900 MHz", "1850–1915 MHz", "1930–1995 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B26 · 850 MHz", "814–849 MHz", "859–894 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B27 · 800 MHz", "807–824 MHz", "852–869 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B28 · 700 MHz", "703–748 MHz", "758–803 MHz"),
        sdl(FrequencyReferenceTechnology.LTE_4G, "B29 · 700 MHz", "717–728 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B30 · 2300 MHz", "2305–2315 MHz", "2350–2360 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B31 · 450 MHz", "452.5–457.5 MHz", "462.5–467.5 MHz"),
        sdl(FrequencyReferenceTechnology.LTE_4G, "B32 · 1500 MHz", "1452–1496 MHz"),
        tdd(FrequencyReferenceTechnology.LTE_4G, "B33 · 1900 MHz", "1900–1920 MHz"),
        tdd(FrequencyReferenceTechnology.LTE_4G, "B34 · 2000 MHz", "2010–2025 MHz"),
        tdd(FrequencyReferenceTechnology.LTE_4G, "B35 · 1900 MHz", "1850–1910 MHz"),
        tdd(FrequencyReferenceTechnology.LTE_4G, "B36 · 1900 MHz", "1930–1990 MHz"),
        tdd(FrequencyReferenceTechnology.LTE_4G, "B37 · 1900 MHz", "1910–1930 MHz"),
        tdd(FrequencyReferenceTechnology.LTE_4G, "B38 · 2600 MHz", "2570–2620 MHz"),
        tdd(FrequencyReferenceTechnology.LTE_4G, "B39 · 1900 MHz", "1880–1920 MHz"),
        tdd(FrequencyReferenceTechnology.LTE_4G, "B40 · 2300 MHz", "2300–2400 MHz"),
        tdd(FrequencyReferenceTechnology.LTE_4G, "B41 · 2500 MHz", "2496–2690 MHz"),
        tdd(FrequencyReferenceTechnology.LTE_4G, "B42 · 3500 MHz", "3400–3600 MHz"),
        tdd(FrequencyReferenceTechnology.LTE_4G, "B43 · 3700 MHz", "3600–3800 MHz"),
        tdd(FrequencyReferenceTechnology.LTE_4G, "B46 · 5 GHz", "5150–5925 MHz"),
        tdd(FrequencyReferenceTechnology.LTE_4G, "B47 · 5 GHz", "5855–5925 MHz"),
        tdd(FrequencyReferenceTechnology.LTE_4G, "B48 · CBRS", "3550–3700 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B65 · 2100 MHz", "1920–2010 MHz", "2110–2200 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B66 · AWS-3", "1710–1780 MHz", "2110–2200 MHz"),
        sdl(FrequencyReferenceTechnology.LTE_4G, "B67 · 700 MHz", "738–758 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B68 · 700 MHz", "698–728 MHz", "753–783 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B70 · 1700 MHz", "1695–1710 MHz", "1995–2020 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B71 · 600 MHz", "663–698 MHz", "617–652 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B72 · 450 MHz", "451.3–455.9 MHz", "461.3–465.9 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B73 · 450 MHz", "450–455 MHz", "460–465 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B74 · 1500 MHz", "1427–1470 MHz", "1475–1518 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B85 · 700 MHz", "698–716 MHz", "728–746 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B87 · 400 MHz", "410–415 MHz", "420–425 MHz"),
        fdd(FrequencyReferenceTechnology.LTE_4G, "B88 · 400 MHz", "412–417 MHz", "422–427 MHz"),

        fdd(FrequencyReferenceTechnology.NR_5G, "n2 · 1900 MHz", "1850–1910 MHz", "1930–1990 MHz"),
        fdd(FrequencyReferenceTechnology.NR_5G, "n5 · 850 MHz", "824–849 MHz", "869–894 MHz"),
        fdd(FrequencyReferenceTechnology.NR_5G, "n7 · 2600 MHz", "2500–2570 MHz", "2620–2690 MHz"),
        fdd(FrequencyReferenceTechnology.NR_5G, "n8 · 900 MHz", "880–915 MHz", "925–960 MHz"),
        fdd(FrequencyReferenceTechnology.NR_5G, "n12 · 700 MHz", "699–716 MHz", "729–746 MHz"),
        fdd(FrequencyReferenceTechnology.NR_5G, "n13 · 700 MHz", "777–787 MHz", "746–756 MHz"),
        fdd(FrequencyReferenceTechnology.NR_5G, "n14 · 700 MHz", "788–798 MHz", "758–768 MHz"),
        fdd(FrequencyReferenceTechnology.NR_5G, "n20 · 800 MHz", "832–862 MHz", "791–821 MHz"),
        fdd(FrequencyReferenceTechnology.NR_5G, "n25 · 1900 MHz", "1850–1915 MHz", "1930–1995 MHz"),
        fdd(FrequencyReferenceTechnology.NR_5G, "n26 · 850 MHz", "814–849 MHz", "859–894 MHz"),
        fdd(FrequencyReferenceTechnology.NR_5G, "n66 · AWS", "1710–1780 MHz", "2110–2200 MHz"),
        sdl(FrequencyReferenceTechnology.NR_5G, "n29 · 700 MHz", "717–728 MHz"),
        tdd(FrequencyReferenceTechnology.NR_5G, "n40 · 2300 MHz", "2300–2400 MHz"),
        tdd(FrequencyReferenceTechnology.NR_5G, "n41 · 2500 MHz", "2496–2690 MHz"),
        tdd(FrequencyReferenceTechnology.NR_5G, "n46 · 5 GHz", "5150–5925 MHz"),
        tdd(FrequencyReferenceTechnology.NR_5G, "n48 · CBRS", "3550–3700 MHz"),
        tdd(FrequencyReferenceTechnology.NR_5G, "n77 · 3700 MHz", "3300–4200 MHz"),
        tdd(FrequencyReferenceTechnology.NR_5G, "n79 · 4500 MHz", "4400–5000 MHz"),
        sdl(FrequencyReferenceTechnology.NR_5G, "n75 · 1500 MHz", "1432–1517 MHz"),
        sdl(FrequencyReferenceTechnology.NR_5G, "n76 · 1500 MHz", "1427–1432 MHz"),
        tdd(FrequencyReferenceTechnology.NR_5G, "n90 · 2600 MHz", "2496–2690 MHz"),
        tdd(FrequencyReferenceTechnology.NR_5G, "n257 · 28 GHz", "26.5–29.5 GHz"),
        tdd(FrequencyReferenceTechnology.NR_5G, "n258 · 26 GHz", "24.25–27.5 GHz"),
        tdd(FrequencyReferenceTechnology.NR_5G, "n260 · 39 GHz", "37–40 GHz"),
        tdd(FrequencyReferenceTechnology.NR_5G, "n261 · 28 GHz", "27.5–28.35 GHz")
    )
}

@Composable
fun FrequencyReferenceScreen(navController: NavController) {
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE) }
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = ROOT_FALLBACK_ROUTE)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var sortOrder by rememberSaveable {
        mutableStateOf(FrequencyReferencePagePreferences.read(prefs))
    }
    var technologyOrder by remember {
        mutableStateOf(FrequencyReferencePagePreferences.readTechnologyOrder(prefs))
    }
    var visibleTechnologies by remember {
        mutableStateOf(FrequencyReferencePagePreferences.readVisibleTechnologies(prefs))
    }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = if (selectedTab == 0) FrequencyReferenceScope.FRANCE else FrequencyReferenceScope.INTERNATIONAL
    val bands = when (scope) {
        FrequencyReferenceScope.FRANCE -> FrequencyReferenceCatalog.franceMetropolitan
        FrequencyReferenceScope.INTERNATIONAL -> FrequencyReferenceCatalog.international
    }.sortedWith(
        if (sortOrder == FrequencyReferencePagePreferences.SORT_BAND) {
            compareBy<FrequencyReferenceBand> { it.bandNameSortGroup() }
                .thenBy { it.bandNameSortNumber() }
                .thenBy { it.name }
        } else {
            compareBy<FrequencyReferenceBand> { it.frequencySortKey() }
                .thenBy { it.name }
        }
    )
    val scrollState = rememberScrollState()
    val mainBgColor = uiStyle.backgroundColor
    val cardBgColor = uiStyle.cardColor
    val blockShape = uiStyle.blockShape

    BackHandler(enabled = !safeBackNavigation.isLocked) {
        safeBackNavigation.navigateBack()
    }

    Scaffold(
        containerColor = mainBgColor,
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.appstrings_frequency_reference_title),
                onBack = { safeBackNavigation.navigateBack() },
                backgroundColor = mainBgColor,
                backEnabled = !safeBackNavigation.isLocked,
                actions = {
                    PageCustomizationHint(
                        page = PageScrollPrefs.FREQUENCY_REFERENCE,
                        onOpenSettings = { showSettingsSheet = true }
                    ) {
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.appstrings_settings_title),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(mainBgColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .geoTowerFadingEdge(scrollState, requireScrollableContent = true)
                    .pageScrollbar(PageScrollPrefs.FREQUENCY_REFERENCE, scrollState)
                    .verticalScroll(scrollState)
                    .navigationBarsPadding()
                    .padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(12.dp)),
                verticalArrangement = Arrangement.spacedBy(sizing.spacing(16.dp))
            ) {
                Card(
                    shape = blockShape,
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = uiStyle.cardBorder
                ) {
                    Row(
                        modifier = Modifier.padding(sizing.spacing(16.dp)),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(sizing.component(24.dp))
                        )
                        Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
                        Text(
                            text = stringResource(R.string.appstrings_frequency_reference_intro),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp))
                ) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text(stringResource(R.string.appstrings_frequency_reference_france_tab)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text(stringResource(R.string.appstrings_frequency_reference_international_tab)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = stringResource(
                        if (scope == FrequencyReferenceScope.FRANCE) {
                            R.string.appstrings_frequency_reference_france_heading
                        } else {
                            R.string.appstrings_frequency_reference_international_heading
                        }
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                    fontWeight = FontWeight.Bold
                )

                technologyOrder.forEach { technologyId ->
                    val technology = FrequencyReferenceTechnology.entries.firstOrNull { it.id == technologyId }
                        ?: return@forEach
                    val technologyBands = bands.filter { it.technology == technology }
                    if (technologyId in visibleTechnologies && technologyBands.isNotEmpty()) {
                        FrequencyTechnologyCard(
                            technology = technology,
                            bands = technologyBands,
                            cardBgColor = cardBgColor,
                            blockShape = blockShape
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.appstrings_frequency_reference_source),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall)
                )
                PageCustomizationFooter(onClick = { showSettingsSheet = true })
                Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
            }
            PageScrollEdgeButtons(PageScrollPrefs.FREQUENCY_REFERENCE, scrollState)
        }
    }

    if (showSettingsSheet) {
        FrequencyReferenceSettingsSheet(
            sortOrder = sortOrder,
            onSortOrderChange = {
                val normalized = FrequencyReferencePagePreferences.normalizeSortOrder(it)
                sortOrder = normalized
                FrequencyReferencePagePreferences.write(prefs, normalized)
            },
            onReset = {
                sortOrder = FrequencyReferencePagePreferences.DEFAULT_SORT_ORDER
                technologyOrder = FrequencyReferencePagePreferences.DEFAULT_TECHNOLOGY_ORDER
                visibleTechnologies = FrequencyReferencePagePreferences.TECHNOLOGY_IDS.toSet()
                FrequencyReferencePagePreferences.reset(prefs)
            },
            technologyOrder = technologyOrder,
            visibleTechnologies = visibleTechnologies,
            onTechnologyOrderChange = { newOrder ->
                val normalized = FrequencyReferencePagePreferences.normalizeTechnologyOrder(newOrder)
                technologyOrder = normalized
                FrequencyReferencePagePreferences.writeTechnologyOrder(prefs, normalized)
            },
            onTechnologyVisibilityChange = { technologyId, visible ->
                visibleTechnologies = visibleTechnologies.toMutableSet().apply {
                    if (visible) add(technologyId) else remove(technologyId)
                }
                FrequencyReferencePagePreferences.writeTechnologyVisibility(prefs, technologyId, visible)
            },
            onDismiss = { showSettingsSheet = false },
            onBack = { showSettingsSheet = false },
            sheetState = settingsSheetState,
            useOneUi = uiStyle.useOneUi,
            bubbleColor = uiStyle.bubbleColor
        )
    }
}

@Composable
private fun FrequencyTechnologyCard(
    technology: FrequencyReferenceTechnology,
    bands: List<FrequencyReferenceBand>,
    cardBgColor: Color,
    blockShape: Shape
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Card(
        shape = blockShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = LocalGeoTowerUiStyle.current.cardBorder
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(
                    horizontal = sizing.spacing(16.dp),
                    vertical = sizing.spacing(14.dp)
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.WifiTethering,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(sizing.component(22.dp))
                )
                Spacer(modifier = Modifier.width(sizing.spacing(10.dp)))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(technology.titleRes),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = sizing.text(16.sp)
                    )
                    Text(
                        text = stringResource(technology.descriptionRes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = sizing.textStyle(MaterialTheme.typography.bodySmall)
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            bands.forEachIndexed { index, band ->
                FrequencyBandRow(band)
                if (index < bands.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = sizing.spacing(16.dp)),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FrequencyBandRow(band: FrequencyReferenceBand) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val mode = when (band.duplex) {
        FrequencyReferenceDuplex.FDD -> stringResource(R.string.appstrings_frequency_reference_fdd)
        FrequencyReferenceDuplex.TDD -> stringResource(R.string.appstrings_frequency_reference_tdd)
        FrequencyReferenceDuplex.SDL -> stringResource(R.string.appstrings_frequency_reference_sdl)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(12.dp)),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = band.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = sizing.text(14.sp)
            )
            Text(
                text = mode,
                color = MaterialTheme.colorScheme.primary,
                fontSize = sizing.text(11.sp),
                fontWeight = FontWeight.Medium
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            band.uplink?.let {
                FrequencyRangeText(stringResource(R.string.appstrings_frequency_reference_uplink), it)
            }
            band.downlink?.let {
                FrequencyRangeText(stringResource(R.string.appstrings_frequency_reference_downlink), it)
            }
            band.sharedRange?.let {
                FrequencyRangeText(stringResource(R.string.appstrings_frequency_reference_shared), it)
            }
        }
    }
}

@Composable
private fun FrequencyRangeText(label: String, range: String) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Text(
        text = "$label $range",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = sizing.text(11.sp),
        textAlign = TextAlign.End
    )
}
