package fr.geotower.ui.screens.coverage

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import fr.geotower.ui.components.oneUiActionButtonShape
import fr.geotower.ui.components.GeoTowerBreadcrumbItem
import fr.geotower.ui.components.GeoTowerNavigationBreadcrumbBar
import fr.geotower.ui.components.geoTowerFadingEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import fr.geotower.data.models.RadioFilterMasks
import fr.geotower.ui.components.GeoTowerSwitch
import android.content.Context
import androidx.compose.material3.IconButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.Settings
import fr.geotower.ui.screens.settings.COVERAGE_DEFAULTS_PREFS
import fr.geotower.ui.screens.settings.COVERAGE_PREF_OBSTACLES
import fr.geotower.ui.screens.settings.COVERAGE_PREF_QUALITY
import fr.geotower.ui.screens.settings.COVERAGE_PREF_TILT
import fr.geotower.ui.screens.settings.CoverageSettingsSheet
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.ui.components.SecureScreenEffect
import fr.geotower.data.coverage.CoverageComputer
import fr.geotower.data.coverage.CoverageRequest
import fr.geotower.data.coverage.SiteCoverage
import fr.geotower.data.coverage.SiteEmitterResolver
import fr.geotower.data.coverage.ViewshedParams
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.SharedMiniMapCard
import fr.geotower.ui.components.TheoreticalCoverageShareGenerator
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.screens.map.TheoreticalCoverageOverlay
import fr.geotower.utils.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.views.MapView
import kotlin.math.ceil
import kotlin.math.roundToInt

private data class CoverageAntennaRow(
    val azimut: Int?,
    val heightM: Double?,
    val typeLabel: String?,
    val omni: Boolean
)

/**
 * Écran-outil dédié : choisir les paramètres (qualité, obstacles, fréquence, down-tilt) puis calculer
 * et visualiser la couverture théorique d'un site sur une mini-carte. Entrée principale de la
 * fonctionnalité (le calcul reste séquentiel — voir le rate-limit IGN documenté).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TheoreticalCoverageScreen(
    navController: NavController,
    repository: AnfrRepository,
    idAnfr: String
) {
    SecureScreenEffect(RemoteFeatureFlags.SecureScreens.THEORETICAL_COVERAGE)
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(COVERAGE_DEFAULTS_PREFS, Context.MODE_PRIVATE)
    val scope = rememberCoroutineScope()
    val safeBack = rememberSafeBackNavigation(navController, fallbackRoute = "site_detail/$idAnfr")

    val themeMode by fr.geotower.utils.AppConfig.themeMode
    val isOledMode by fr.geotower.utils.AppConfig.isOledMode
    val useOneUi = fr.geotower.utils.AppConfig.useOneUiDesign
    val isDark = (themeMode == 2) || (themeMode == 0 && androidx.compose.foundation.isSystemInDarkTheme())
    val mainBgColor = if (isDark && isOledMode) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.background
    val cardBgColor = if (useOneUi) {
        if (isDark) androidx.compose.ui.graphics.Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val blockShape = RoundedCornerShape(if (useOneUi) 24.dp else 12.dp)

    var site by remember { mutableStateOf<LocalisationEntity?>(null) }
    var antennaRows by remember { mutableStateOf<List<CoverageAntennaRow>>(emptyList()) }

    var quality by remember { mutableIntStateOf(prefs.getInt(COVERAGE_PREF_QUALITY, 1)) }
    var includeObstacles by remember { mutableStateOf(prefs.getBoolean(COVERAGE_PREF_OBSTACLES, true)) }
    var frequencyMHz by remember { mutableIntStateOf(3500) }
    var tiltDeg by remember { mutableFloatStateOf(prefs.getInt(COVERAGE_PREF_TILT, 5).toFloat()) }
    var showSettings by remember { mutableStateOf(false) }
    val settingsSheetState = rememberModalBottomSheetState()

    var coverage by remember { mutableStateOf<SiteCoverage?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var computeJob by remember { mutableStateOf<Job?>(null) }

    val overlay = remember { TheoreticalCoverageOverlay(context) }
    var mapRef by remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(idAnfr) {
        val loaded = withContext(Dispatchers.IO) {
            // Le VRAI LocalisationEntity (champ `azimuts` peuplé) → la mini-carte dessine les azimuts du site.
            val s = repository.getAntennasByExactId(idAnfr).firstOrNull()
            val raw = repository.getAntennesForCoverage(idAnfr).filter { it.isFh == 0 }
            val labels = repository.getAntennaTypes().associate { it.taeId to it.libelle }
            val rows = raw.map { a ->
                val label = a.taeId?.let { labels[it] }
                CoverageAntennaRow(a.azimut, a.hauteurBas, label, SiteEmitterResolver.isOmni(label))
            }.sortedBy { it.azimut ?: Int.MAX_VALUE }
            s to rows
        }
        site = loaded.first
        antennaRows = loaded.second
    }

    // Garantit que l'overlay reste attaché à la carte et reflète la dernière couverture
    // (SharedMiniMapCard gère ses propres overlays — on (re)pose le nôtre puis on rafraîchit).
    LaunchedEffect(coverage, mapRef) {
        val mv = mapRef ?: return@LaunchedEffect
        if (!mv.overlays.contains(overlay)) {
            mv.overlays.add(0, overlay)
        }
        overlay.setCoverage(coverage)
        mv.invalidate()
    }

    // Seulement les fréquences réellement présentes sur le site (dérivées du band_mask).
    val frequencies = remember(site) { siteFrequencies(site?.bandMask ?: 0) }
    val estimatedRequests = estimateRequests(quality)
    LaunchedEffect(frequencies) {
        if (frequencyMHz !in frequencies) {
            frequencyMHz = if (3500 in frequencies) 3500 else frequencies.lastOrNull() ?: 3500
        }
    }

    fun launchCompute() {
        if (site == null) return
        computeJob?.cancel()
        isLoading = true
        progress = null
        computeJob = scope.launch {
            try {
                // Mode faible conso (Éco+) : force l'aperçu + coupe les obstacles → bien moins de requêtes IGN.
                val ecoPreview = fr.geotower.utils.PowerProfile.coverageQualityPreview
                val effectiveQuality = if (ecoPreview) 0 else quality
                val effectiveObstacles = includeObstacles && !ecoPreview
                val request = buildRequest(effectiveQuality, effectiveObstacles, frequencyMHz, tiltDeg.toDouble())
                val result = CoverageComputer.compute(
                    repository = repository,
                    idAnfr = idAnfr,
                    request = request,
                    maxPointsPerRequest = RemoteFeatureFlags.limitOrDefault(RemoteFeatureFlags.Limits.COVERAGE_MAX_POINTS_PER_REQUEST, 300),
                    maxConcurrentRequests = RemoteFeatureFlags.limitOrDefault(RemoteFeatureFlags.Limits.COVERAGE_MAX_CONCURRENT_REQUESTS, 1)
                ) { done, total -> progress = done to total }
                coverage = result
                overlay.setCoverage(result)
                mapRef?.invalidate()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w("TheoreticalCoverage", "compute failed", e)
            } finally {
                isLoading = false
                progress = null
            }
        }
    }

    fun shareCoverage() {
        val mv = mapRef ?: return
        val cov = coverage ?: return
        if (mv.width <= 0 || mv.height <= 0) return
        // Capture de la MapView sur le thread principal (obligatoire : c'est une View), puis composition
        // de l'image + compression PNG + écriture disque sur IO (lourd), enfin le chooser de retour sur Main.
        scope.launch {
            runCatching {
                val mapBitmap = android.graphics.Bitmap.createBitmap(mv.width, mv.height, android.graphics.Bitmap.Config.ARGB_8888)
                mv.draw(android.graphics.Canvas(mapBitmap))
                val (radius, angular, sample) = qualityParams(quality)
                val params = "≈ ${(radius / 1000).toInt()} km · ${angular.toInt()}° · ${sample.toInt()} m · $frequencyMHz MHz · tilt ${tiltDeg.roundToInt()}°"
                val uri = withContext(Dispatchers.IO) {
                    val image = TheoreticalCoverageShareGenerator.create(
                        title = context.getString(R.string.appstrings_coverage_button),
                        subtitle = "ANFR ${cov.idAnfr}" + (cov.operator?.let { " · $it" } ?: ""),
                        paramsLine = params,
                        disclaimer = context.getString(R.string.appstrings_coverage_disclaimer_short),
                        source = "Source : IGN RGE ALTI / BD TOPO · © OpenStreetMap",
                        mapBitmap = mapBitmap,
                        forceDark = false
                    )
                    val imagesDir = java.io.File(context.cacheDir, "images").apply { mkdirs() }
                    val file = java.io.File(imagesDir, "coverage_${cov.idAnfr}.png")
                    file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, it) }
                    androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                // FLAG_ACTIVITY_NEW_TASK requis : contexte localisé (LocaleProvider), pas une Activity → sinon crash OnePlus.
                context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.appstrings_coverage_share)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure {
                AppLogger.w("TheoreticalCoverage", "share failed", it)
                android.widget.Toast.makeText(context, context.getString(R.string.appstrings_error), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        containerColor = mainBgColor,
        topBar = {
            Column(modifier = Modifier.background(mainBgColor)) {
                GeoTowerBackTopBar(
                    title = stringResource(R.string.appstrings_coverage_button),
                    onBack = { safeBack.navigateBack() },
                    backgroundColor = mainBgColor,
                    backEnabled = !safeBack.isLocked,
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.appstrings_coverage_settings_title)
                            )
                        }
                    }
                )
                GeoTowerNavigationBreadcrumbBar(
                    navController = navController,
                    currentItem = GeoTowerBreadcrumbItem(
                        label = stringResource(R.string.appstrings_coverage_button),
                        icon = Icons.Default.Map,
                        key = "theoretical_coverage"
                    ),
                    currentRouteKeys = setOf("theoretical_coverage"),
                    backgroundColor = if (useOneUi) cardBgColor else MaterialTheme.colorScheme.surfaceContainer
                )
            }
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .geoTowerFadingEdge(scrollState)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // La mini-carte n'est créée QU'UNE FOIS le site chargé : sinon son AndroidView est créé
            // avec un centre placeholder (milieu de la France) et ne se recentre jamais → site/résultat hors écran.
            val loadedSite = site
            if (loadedSite == null) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else {
                SharedMiniMapCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    centerLat = loadedSite.latitude,
                    centerLon = loadedSite.longitude,
                    mappedAntennas = listOf(loadedSite),
                    blockShape = blockShape,
                    cardBorder = null,
                    onMapReady = { mv ->
                        mapRef = mv
                        mv.overlays.add(0, overlay)
                        overlay.setCoverage(coverage)
                        mv.invalidate()
                    },
                    initialZoom = 12.0,
                    allowGestures = true
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = blockShape,
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = cardBgColor)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "${stringResource(R.string.appstrings_coverage_quality)} · ≈ $estimatedRequests req",
                        fontWeight = FontWeight.Bold
                    )
                    CoverageSlider(
                        value = quality.toFloat(),
                        valueRange = 0f..3f,
                        steps = 2,
                        useOneUi = useOneUi,
                        enabled = !isLoading,
                        onValueChange = { quality = it.roundToInt() }
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GeoTowerSwitch(
                            checked = includeObstacles,
                            onCheckedChange = { includeObstacles = it },
                            enabled = !isLoading,
                            useOneUi = useOneUi
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.appstrings_coverage_obstacles))
                    }

                    Text(stringResource(R.string.appstrings_coverage_frequency), fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        frequencies.forEach { f ->
                            FilterChip(
                                selected = f == frequencyMHz,
                                onClick = { frequencyMHz = f },
                                label = { Text("$f MHz") }
                            )
                        }
                    }

                    Text(
                        "${stringResource(R.string.appstrings_coverage_tilt)} : ${tiltDeg.roundToInt()}°",
                        fontWeight = FontWeight.Bold
                    )
                    CoverageSlider(
                        value = tiltDeg,
                        valueRange = 0f..15f,
                        steps = 14,
                        useOneUi = useOneUi,
                        enabled = !isLoading,
                        onValueChange = { tiltDeg = it }
                    )

                    Button(
                        onClick = { launchCompute() },
                        enabled = !isLoading && site != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = oneUiActionButtonShape(useOneUi),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(stringResource(R.string.appstrings_coverage_compute))
                    }

                    if (isLoading) {
                        val p = progress
                        val fraction = if (p != null && p.second > 0) {
                            (p.first.toFloat() / p.second).coerceIn(0f, 1f)
                        } else {
                            null
                        }
                        if (fraction != null) {
                            androidx.compose.material3.LinearWavyProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            androidx.compose.material3.LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            if (p != null) {
                                "${stringResource(R.string.appstrings_coverage_computing)} ${p.first}/${p.second}"
                            } else {
                                stringResource(R.string.appstrings_coverage_computing)
                            }
                        )
                        TextButton(onClick = {
                            computeJob?.cancel()
                            isLoading = false
                            progress = null
                        }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = blockShape,
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = cardBgColor)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "${stringResource(R.string.appstrings_coverage_antennas_detected)} (${antennaRows.size})",
                        fontWeight = FontWeight.Bold
                    )
                    if (antennaRows.isEmpty()) {
                        Text(stringResource(R.string.appstrings_coverage_empty))
                    } else {
                        antennaRows.forEach { row ->
                            val type = if (row.omni) "omni" else "sect."
                            val az = row.azimut?.let { "$it°" } ?: "—"
                            val h = row.heightM?.let { "${it.roundToInt()} m" } ?: "—"
                            val suffix = row.typeLabel?.let { " · $it" } ?: ""
                            Text("• $type · azimut $az · $h$suffix")
                        }
                    }
                }
            }

            val computed = coverage
            if (computed != null && !computed.isEmpty) {
                Button(
                    onClick = { shareCoverage() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = oneUiActionButtonShape(useOneUi),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.appstrings_coverage_share), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                stringResource(R.string.appstrings_coverage_disclaimer_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showSettings) {
        CoverageSettingsSheet(
            onDismiss = { showSettings = false },
            sheetState = settingsSheetState,
            useOneUi = useOneUi
        )
    }
}

private fun estimateRequests(quality: Int): Int {
    val (radius, angular, sample) = qualityParams(quality)
    val rays = (360.0 / angular).toInt()
    val samples = (radius / sample).toInt()
    return ceil(rays.toDouble() * samples / 300.0).toInt()
}

/** [radius m, pas angulaire deg, sampling m]. Plus la qualité monte, plus il y a de requêtes (séquentielles). */
private fun qualityParams(quality: Int): Triple<Double, Double, Double> = when (quality) {
    0 -> Triple(3000.0, 8.0, 80.0)   // aperçu
    1 -> Triple(4000.0, 6.0, 70.0)   // équilibré (défaut)
    2 -> Triple(5000.0, 4.0, 50.0)   // précis
    else -> Triple(6000.0, 3.0, 40.0) // max (long sur réseau)
}

private fun buildRequest(quality: Int, obstacles: Boolean, frequencyMHz: Int, tiltDeg: Double): CoverageRequest {
    val (radius, angular, sample) = qualityParams(quality)
    return CoverageRequest(
        maxRadiusM = radius,
        angularStepDeg = angular,
        sampleStepM = sample,
        includeObstacles = obstacles,
        viewshed = ViewshedParams(
            maxRadiusM = radius,
            curvature = true,
            fresnel = false,
            frequencyMHz = frequencyMHz,
            tiltDeg = tiltDeg,
            verticalBeamwidthDeg = 10.0
        )
    )
}

/** Fréquences (MHz) réellement présentes sur le site, dérivées du band_mask. Défaut 3500 si vide. */
private fun siteFrequencies(bandMask: Int): List<Int> {
    val mapping = listOf(
        RadioFilterMasks.BAND_4G_700 to 700, RadioFilterMasks.BAND_5G_700 to 700,
        RadioFilterMasks.BAND_4G_800 to 800,
        RadioFilterMasks.BAND_2G_900 to 900, RadioFilterMasks.BAND_3G_900 to 900, RadioFilterMasks.BAND_4G_900 to 900,
        RadioFilterMasks.BAND_5G_1400 to 1400,
        RadioFilterMasks.BAND_2G_1800 to 1800, RadioFilterMasks.BAND_4G_1800 to 1800,
        RadioFilterMasks.BAND_3G_2100 to 2100, RadioFilterMasks.BAND_4G_2100 to 2100, RadioFilterMasks.BAND_5G_2100 to 2100,
        RadioFilterMasks.BAND_4G_2600 to 2600,
        RadioFilterMasks.BAND_5G_3500 to 3500,
        RadioFilterMasks.BAND_5G_4200 to 4200,
        RadioFilterMasks.BAND_5G_26000 to 26000
    )
    val present = mapping.filter { (flag, _) -> bandMask and flag != 0 }.map { it.second }.distinct().sorted()
    return present.ifEmpty { listOf(3500) }
}

/** Curseur stylé OneUI (rail épais arrondi + pastilles + pouce cerclé), repli sur le Slider standard sinon. */
@Composable
private fun CoverageSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    useOneUi: Boolean,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    if (!useOneUi) {
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps, enabled = enabled)
        return
    }
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val tickCount = steps.coerceAtLeast(0) + 2
    val inactiveTrack = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val activeTrack = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
    val dotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        thumb = {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )
        },
        track = {
            Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                val centerY = size.height / 2
                drawLine(inactiveTrack, Offset(0f, centerY), Offset(size.width, centerY), 10.dp.toPx(), StrokeCap.Round)
                drawLine(activeTrack, Offset(0f, centerY), Offset(size.width * fraction, centerY), 10.dp.toPx(), StrokeCap.Round)
                val radius = if (tickCount > 52) 0.85.dp.toPx() else 1.15.dp.toPx()
                val lastIndex = (tickCount - 1).coerceAtLeast(1)
                for (i in 0 until tickCount) {
                    drawCircle(dotColor, radius, Offset(size.width * (i.toFloat() / lastIndex.toFloat()), centerY))
                }
            }
        }
    )
}
