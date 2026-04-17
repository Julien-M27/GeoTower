package fr.geotower.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.PhysiqueEntity // ✅ NOUVEAU
import fr.geotower.data.models.TechniqueEntity // ✅ NOUVEAU
import fr.geotower.ui.screens.emitters.formatDateToFrench
import fr.geotower.ui.screens.emitters.formatTechnologies
import fr.geotower.ui.screens.emitters.getDetailLogoRes
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import fr.geotower.ui.components.SiteStatusCard

private fun Int.dpToPx(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()

fun shareFullAntennaCapture(
    context: Context,
    currentView: View,
    info: LocalisationEntity,
    physique: PhysiqueEntity?,
    technique: TechniqueEntity?,
    hsDataMap: Map<String, fr.geotower.data.models.SiteHsEntity>, // 🚨 AJOUT
    distanceStr: String,
    bearingStr: String,
    forceDarkTheme: Boolean,
    txtTitle: String,
    txtAddressLabel: String,
    txtNotSpecified: String,
    txtGpsLabel: String,
    txtSupportHeight: String,
    txtDistanceLabel: String,
    txtFromMyPosition: String,
    txtBearingLabel: String,
    txtGeneratedBy: String,
    txtShareSiteVia: String,
    txtimplementation: String,
    txtLastModification: String,
    txtIdentifiers: String,
    txtIdNumber: String,
    txtFrequenciesTitle: String,
    txtBandsNotSpecified: String,
    txtInService: String,
    txtTechnically: String,
    txtUnknownStatus: String,
    txtAnfrStationNumber: String,
    txtDates: String,
    txtError: String,
    txtProjectApproved: String,
    txtActivatedOn: String,
    txtDateNotSpecifiedAnfr: String,
    txtPanelHeightsTitle: String,
    txtAzimuths: String,
    txtIdSupportLabel: String,
    txtSupportDetailsTitle: String,
    txtSupportNature: String,
    txtOwner: String,
    txtAntennaType: String,
    mapBitmap: Bitmap?,
    txtInitError: String,
    photoBitmaps: List<Bitmap>,
    txtCommunityPhotosTitle: String,
    incMap: Boolean,
    incSupport: Boolean,
    incHeights: Boolean,
    incIds: Boolean,
    incDates: Boolean,
    incAddress: Boolean,
    incFreqs: Boolean,
    incConfidential: Boolean,
    incQrCode: Boolean,
    shareOrder: List<String>
) {
    val composeView = ComposeView(context).apply {
        setViewTreeLifecycleOwner(currentView.findViewTreeLifecycleOwner())
        setViewTreeSavedStateRegistryOwner(currentView.findViewTreeSavedStateRegistryOwner())
        setViewTreeViewModelStoreOwner(currentView.findViewTreeViewModelStoreOwner())
        setContent {
            MaterialTheme(colorScheme = if (forceDarkTheme) darkColorScheme() else lightColorScheme()) {
                Surface(color = MaterialTheme.colorScheme.background) {

                    val formattedHeights = if (info.azimuts.isNullOrBlank()) ""
                    else {
                        val heights = info.azimuts?.split(",")
                            ?.mapNotNull { it.substringAfter("(", "").substringBefore("m", "").trim().toFloatOrNull() }
                            ?.filter { it > 0f }?.distinct()?.sorted() ?: emptyList()
                        if (heights.isNotEmpty()) heights.joinToString(" m - ") + " m" else ""
                    }

                    Column(
                        modifier = Modifier.width(400.dp).wrapContentHeight().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(txtTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                val logoRes = getDetailLogoRes(info.operateur ?: "")
                                if (logoRes != null) { Image(painter = painterResource(id = logoRes), contentDescription = null, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp))) }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(info.operateur ?: "Inconnu", fontWeight = FontWeight.Bold)
                                    val rawTechs =
                                        technique?.technologies?.takeIf { it.isNotBlank() }
                                            ?: info.frequences
                                    Text(
                                        formatTechnologies(rawTechs, AppStrings.unknown),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (incSupport) {
                            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (!incConfidential) {
                                    val rotation = bearingStr.replace("°", "").toFloatOrNull() ?: 0f
                                    Card(modifier = Modifier.weight(1f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                        Column(modifier = Modifier.padding(16.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                                            Text(txtBearingLabel.replace(" : ", ""), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                                            Spacer(Modifier.height(8.dp))
                                            Icon(Icons.Default.Navigation, null, Modifier.size(40.dp).rotate(rotation), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.height(8.dp))
                                            Text(bearingStr, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Card(modifier = Modifier.weight(1f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                                        Text(txtSupportHeight.replace(" : ", ""), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                                        Spacer(Modifier.height(8.dp))
                                        Icon(Icons.Default.VerticalAlignTop, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(8.dp))
                                        // ✅ ON UTILISE LA VRAIE HAUTEUR
                                        Text("${physique?.hauteur ?: "--"} m", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        shareOrder.forEach { block ->
                            when (block) {
                                "map" -> {
                                    if (incMap && mapBitmap != null) {
                                        Image(
                                            bitmap = mapBitmap.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                                "support" -> {
                                    if (incSupport) {
                                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(txtSupportDetailsTitle, fontWeight = FontWeight.Bold)
                                                }
                                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                                // ✅ ON UTILISE LA VRAIE NATURE ET PROPRIÉTAIRE
                                                val nature = physique?.natureSupport?.takeIf { it.isNotBlank() } ?: txtNotSpecified
                                                val proprietaire = physique?.proprietaire?.takeIf { it.isNotBlank() } ?: "Inconnu"
                                                Text("$txtSupportNature : $nature", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("$txtOwner : $proprietaire", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                                "ids" -> {
                                    if (incIds) {
                                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Tag, null, tint = MaterialTheme.colorScheme.primary)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(txtIdentifiers, fontWeight = FontWeight.Bold)
                                                }
                                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                                val idSupportValue = physique?.idSupport?.takeIf { it.isNotBlank() } ?: txtNotSpecified
                                                Text("$txtIdSupportLabel $idSupportValue", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("$txtAnfrStationNumber ${info.idAnfr}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                                "dates" -> {
                                    if (incDates) {
                                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.DateRange, null, tint = MaterialTheme.colorScheme.primary)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(txtDates, fontWeight = FontWeight.Bold)
                                                }
                                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                                // ✅ ON UTILISE LES VRAIES DATES
                                                val dateImpStr = technique?.dateImplantation?.takeIf { it.isNotBlank() }?.let { formatDateToFrench(it) } ?: "-"
                                                val dateSerStr = technique?.dateService?.takeIf { it.isNotBlank() }?.let { formatDateToFrench(it) } ?: "-"
                                                val dateModStr = technique?.dateModif?.takeIf { it.isNotBlank() }?.let { formatDateToFrench(it) } ?: "-"
                                                if (dateModStr != "-") {
                                                    Text("$txtLastModification $dateModStr", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }

                                                Text("$txtimplementation $dateImpStr", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("$txtActivatedOn $dateSerStr", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                if (dateModStr != "-") {
                                                    Text("$txtLastModification $dateModStr", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                                "address" -> {
                                    if (incAddress) {
                                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.primary)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(txtAddressLabel.replace(" : ", ""), fontWeight = FontWeight.Bold)
                                                }
                                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                                // ✅ ON UTILISE LA VRAIE ADRESSE
                                                val fullAddress = technique?.adresse?.takeIf { it.isNotBlank() } ?: "Adresse non spécifiée"
                                                Text(fullAddress, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text("$txtGpsLabel ${String.format(Locale.US, "%.5f, %.5f", info.latitude, info.longitude)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                if (incConfidential) {
                                                    Text(AppStrings.distanceHidden, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                } else {
                                                    Text("$txtDistanceLabel $distanceStr$txtFromMyPosition", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                                "status" -> {
                                    if (AppConfig.shareSiteStatus.value) {
                                        val hsEntity = hsDataMap[info.idAnfr]
                                        val isOutage = hsEntity != null
                                        val outageText = hsEntity?.let { fr.geotower.ui.components.formatOutageDetails(it) }

                                        val rawTechs = technique?.technologies?.takeIf { it.isNotBlank() } ?: info.frequences ?: ""
                                        val has2G = rawTechs.contains("2G", ignoreCase = true)
                                        val has3G = rawTechs.contains("3G", ignoreCase = true)
                                        val has4G = rawTechs.contains("4G", ignoreCase = true)
                                        val has5G = rawTechs.contains("5G", ignoreCase = true)

                                        val detailsStr = technique?.detailsFrequences ?: ""
                                        val globalStatut = technique?.statut ?: ""
                                        val globalIsProject = globalStatut.contains("Projet", ignoreCase = true)

                                        fun isTechPlanned(keywords: List<String>): Boolean {
                                            if (detailsStr.isBlank()) return globalIsProject
                                            val lines = detailsStr.split("\n").filter { line ->
                                                keywords.any { k -> line.contains(k, ignoreCase = true) }
                                            }
                                            if (lines.isEmpty()) return globalIsProject
                                            return lines.all { it.contains("Projet", ignoreCase = true) }
                                        }

                                        val is2gProject = has2G && isTechPlanned(listOf("GSM", "2G"))
                                        val is3gProject = has3G && isTechPlanned(listOf("UMTS", "3G"))
                                        val is4gProject = has4G && isTechPlanned(listOf("LTE", "4G"))
                                        val is5gProject = has5G && isTechPlanned(listOf("NR", "5G"))

                                        val totalTechs = listOf(has2G, has3G, has4G, has5G).count { it }
                                        val projectTechs = listOf(is2gProject, is3gProject, is4gProject, is5gProject).count { it }
                                        val isEntirelyProject = totalTechs > 0 && totalTechs == projectTechs

                                        val realTechStatus = mapOf(
                                            "2G" to fr.geotower.ui.components.ServiceStatus(
                                                isVoixOk = if (has2G) hsEntity?.let { it.voix2g != "HS" } ?: true else null,
                                                isSmsOk = if (has2G) hsEntity?.let { it.voix2g != "HS" } ?: true else null,
                                                isInternetOk = if (has2G) hsEntity?.let { it.data2g != "HS" } ?: true else null,
                                                isProject = is2gProject
                                            ),
                                            "3G" to fr.geotower.ui.components.ServiceStatus(
                                                isVoixOk = if (has3G) hsEntity?.let { it.voix3g != "HS" } ?: true else null,
                                                isSmsOk = if (has3G) hsEntity?.let { it.voix3g != "HS" } ?: true else null,
                                                isInternetOk = if (has3G) hsEntity?.let { it.data3g != "HS" } ?: true else null,
                                                isProject = is3gProject
                                            ),
                                            "4G" to fr.geotower.ui.components.ServiceStatus(
                                                isVoixOk = if (has4G) hsEntity?.let { it.voix4g != "HS" } ?: true else null,
                                                isSmsOk = if (has4G) hsEntity?.let { it.voix4g != "HS" } ?: true else null,
                                                isInternetOk = if (has4G) hsEntity?.let { it.data4g != "HS" } ?: true else null,
                                                isProject = is4gProject
                                            ),
                                            "5G" to fr.geotower.ui.components.ServiceStatus(
                                                isVoixOk = if (has5G) hsEntity?.let { it.voix5g != "HS" } ?: true else null,
                                                isSmsOk = if (has5G) hsEntity?.let { it.voix5g != "HS" } ?: true else null,
                                                isInternetOk = if (has5G) hsEntity?.let { it.data5g != "HS" } ?: true else null,
                                                isProject = is5gProject
                                            )
                                        )

                                        SiteStatusCard(
                                            isProjectSite = isEntirelyProject,
                                            isOutage = isOutage,
                                            outageText = outageText,
                                            cardBgColor = MaterialTheme.colorScheme.surfaceVariant,
                                            blockShape = RoundedCornerShape(12.dp),
                                            techStatus = realTechStatus
                                        )
                                    }
                                }
                                "freq" -> {
                                    if (incFreqs) {
                                        val rawFreqs = technique?.detailsFrequences ?: info.frequences
                                        // ✅ 1. CORRECTION : On passe les traductions à la fonction de parsing
                                        // ✅ ON APPLIQUE LE MÊME FILTRE QUE SUR L'ÉCRAN DE DÉTAILS
                                        val parsedBands = parseAndSortFrequencies(rawFreqs, AppStrings.unknown, AppStrings.azimuthNotSpecified).filter { band ->
                                            when (band.gen) {
                                                5 -> AppConfig.siteShowTechno5G.value && when (band.value) {
                                                    700 -> AppConfig.siteF5G_700.value
                                                    2100 -> AppConfig.siteF5G_2100.value
                                                    3500 -> AppConfig.siteF5G_3500.value
                                                    26000 -> AppConfig.siteF5G_26000.value
                                                    else -> true
                                                }
                                                4 -> AppConfig.siteShowTechno4G.value && when (band.value) {
                                                    700 -> AppConfig.siteF4G_700.value
                                                    800 -> AppConfig.siteF4G_800.value
                                                    900 -> AppConfig.siteF4G_900.value
                                                    1800 -> AppConfig.siteF4G_1800.value
                                                    2100 -> AppConfig.siteF4G_2100.value
                                                    2600 -> AppConfig.siteF4G_2600.value
                                                    else -> true
                                                }
                                                3 -> AppConfig.siteShowTechno3G.value && when (band.value) {
                                                    900 -> AppConfig.siteF3G_900.value
                                                    2100 -> AppConfig.siteF3G_2100.value
                                                    else -> true
                                                }
                                                2 -> AppConfig.siteShowTechno2G.value && when (band.value) {
                                                    900 -> AppConfig.siteF2G_900.value
                                                    1800 -> AppConfig.siteF2G_1800.value
                                                    else -> true
                                                }
                                                else -> AppConfig.siteShowTechnoFH.value
                                            }
                                        }

                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                                // --- EN-TÊTE DU BLOC ---
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.WifiTethering, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(txtFrequenciesTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                }
                                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                                if (parsedBands.isEmpty()) {
                                                    Text(txtBandsNotSpecified, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                } else {
                                                    // --- LISTE DES FRÉQUENCES ---
                                                    parsedBands.forEachIndexed { bandIndex, band ->
                                                        if (bandIndex > 0) {
                                                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                                        }

                                                        // 1. Nom du Système et Statut aligné à droite
                                                        val (statusColor, statusText) = when {
                                                            band.status.contains("En service", true) -> Pair(Color(0xFF4CAF50), txtInService)
                                                            band.status.contains("Techniquement", true) -> Pair(Color(0xFF4CAF50), txtTechnically)
                                                            band.status.contains("Approuvé", true) -> Pair(Color(0xFF2196F3), txtProjectApproved)
                                                            else -> Pair(Color.Gray, txtUnknownStatus)
                                                        }

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = band.rawFreq.substringBefore(":").trim(),
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                fontSize = 14.sp,
                                                                modifier = Modifier.weight(1f) // Pousse le statut tout à droite
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(
                                                                    text = statusText,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    fontWeight = FontWeight.Normal,
                                                                    fontSize = 12.sp
                                                                )
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Icon(Icons.Default.Circle, contentDescription = null, tint = statusColor, modifier = Modifier.size(10.dp))
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))

                                                        // 2. Fréquences détaillées et calculs
                                                        Column(modifier = Modifier.padding(start = 8.dp)) {
                                                            val preciseFreqs = band.rawFreq.substringAfter(":", "").trim()

                                                            // ✅ ON INTÈGRE LES CONDITIONS DU SPECTRE POUR LE PARTAGE D'IMAGE
                                                            if (AppConfig.siteShowSpectrum.value && preciseFreqs.isNotBlank() && preciseFreqs != band.rawFreq.trim()) {
                                                                var totalBandwidth = 0.0
                                                                var detectedUnit = "MHz"

                                                                val regex = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*-\s*([0-9]+(?:[.,][0-9]+)?)\s*([a-zA-Z]*Hz)?""")

                                                                val detailedFreqs = regex.replace(preciseFreqs) { match ->
                                                                    val n1 = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0
                                                                    val n2 = match.groupValues[2].replace(',', '.').toDoubleOrNull() ?: 0.0
                                                                    val unit = match.groupValues[3].takeIf { it.isNotBlank() } ?: "MHz"

                                                                    detectedUnit = unit

                                                                    val diff = kotlin.math.abs(n2 - n1)
                                                                    totalBandwidth += diff
                                                                    val diffStr = if (diff % 1.0 == 0.0) diff.toInt().toString() else String.format(java.util.Locale.US, "%.1f", diff)
                                                                    "${match.groupValues[1]}-${match.groupValues[2]} $unit [$diffStr $unit]".trim()
                                                                }

                                                                if (AppConfig.siteShowSpectrumBand.value) {
                                                                    Text(
                                                                        text = "${AppStrings.spectrumByBand} : $detailedFreqs",
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                        fontSize = 12.sp,
                                                                        fontWeight = FontWeight.Normal
                                                                    )
                                                                }

                                                                if (AppConfig.siteShowSpectrumTotal.value && totalBandwidth > 0) {
                                                                    val totalStr = if (totalBandwidth % 1.0 == 0.0) totalBandwidth.toInt().toString() else String.format(java.util.Locale.US, "%.1f", totalBandwidth)
                                                                    if (AppConfig.siteShowSpectrumBand.value) {
                                                                        Spacer(modifier = Modifier.height(2.dp))
                                                                    }
                                                                    Text(
                                                                        text = "${AppStrings.totalspectrum} : $totalStr $detectedUnit",
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                        fontSize = 12.sp,
                                                                        fontWeight = FontWeight.Medium
                                                                    )
                                                                }

                                                                if (AppConfig.siteShowSpectrumBand.value || (AppConfig.siteShowSpectrumTotal.value && totalBandwidth > 0)) {
                                                                    Spacer(modifier = Modifier.height(4.dp))
                                                                }
                                                            }

                                                            // 3. Date d'activation
                                                            val dateFormatted = formatDateToFrench(band.date)
                                                            if (dateFormatted.isNotBlank() && dateFormatted != "-") {
                                                                Text("$txtActivatedOn$dateFormatted", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                                            } else {
                                                                Text(txtDateNotSpecifiedAnfr, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                                            }

                                                            // 4. Détails Physiques
                                                            if (band.physDetails.isNotEmpty()) {
                                                                Spacer(modifier = Modifier.height(6.dp))
                                                                band.physDetails.forEach { physDetail ->
                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        modifier = Modifier.padding(top = 4.dp)
                                                                    ) {
                                                                        Icon(
                                                                            Icons.Default.Explore,
                                                                            contentDescription = null,
                                                                            tint = MaterialTheme.colorScheme.primary,
                                                                            modifier = Modifier.size(14.dp)
                                                                        )
                                                                        Spacer(modifier = Modifier.width(6.dp))

                                                                        // ✅ 4. SÉPARATION ET TRADUCTION DU TYPE D'ANTENNE
                                                                        val typePart = physDetail.substringBefore(" : ").trim()
                                                                        val restPart = physDetail.substringAfter(" : ", "").trim()
                                                                        val translatedType = AppStrings.translateAntennaType(typePart)
                                                                        val finalPhysText = if (restPart.isNotEmpty()) "$translatedType : $restPart" else translatedType

                                                                        Text(
                                                                            text = finalPhysText,
                                                                            color = MaterialTheme.colorScheme.onSurface,
                                                                            fontWeight = FontWeight.Medium,
                                                                            fontSize = 12.sp
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // ✅ NOUVEAU : Le pied de page avec le QR Code (Détail Antenne)
                        // ✅ NOUVEAU : On l'entoure avec la condition
                        if (incQrCode) {
                            val qrUri = "geotower://site/${info.idAnfr}"
                            val qrBitmap = remember(qrUri) { generateQrCodeBitmap(qrUri, 200) }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (qrBitmap != null) {
                                    Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(text = fr.geotower.utils.AppStrings.scanToOpen, fontSize = 11.sp, color = Color.Gray)
                                    Text(text = fr.geotower.utils.AppStrings.geoTowerApp, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        // ✅ RETOUR DU TEXTE "Généré via"
                        Text(text = txtGeneratedBy, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
        }
    }

    val rootView = currentView.rootView as ViewGroup
    composeView.translationX = 10000f

    try {
        if (composeView.parent != null) {
            (composeView.parent as ViewGroup).removeView(composeView)
        }
        rootView.addView(composeView, ViewGroup.LayoutParams(400.dpToPx(context), ViewGroup.LayoutParams.WRAP_CONTENT))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, txtInitError, Toast.LENGTH_SHORT).show()
        return
    }

    composeView.postDelayed({
        try {
            val widthSpec = View.MeasureSpec.makeMeasureSpec(400.dpToPx(context), View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            composeView.measure(widthSpec, heightSpec)
            composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)

            if (composeView.measuredWidth > 0 && composeView.measuredHeight > 0) {
                val bitmap = Bitmap.createBitmap(composeView.measuredWidth, composeView.measuredHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                composeView.draw(canvas)

                rootView.removeView(composeView)

                val imagesDir = File(context.cacheDir, "images")
                imagesDir.mkdirs()
                val file = File(imagesDir, "Geotower_site_${info.idAnfr}.png")

                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, "Capture", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(Intent.createChooser(intent, txtShareSiteVia))
            } else {
                rootView.removeView(composeView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (composeView.parent != null) rootView.removeView(composeView)
        }
    }, 500)
}

fun shareFullSiteCapture(
    context: Context,
    currentView: View,
    siteId: Long,
    mainInfo: LocalisationEntity,
    antennas: List<LocalisationEntity>,
    physique: PhysiqueEntity?,
    techniquesMap: Map<String, TechniqueEntity>,
    hsDataMap: Map<String, fr.geotower.data.models.SiteHsEntity>, // 🚨 AJOUT
    distanceStr: String,
    bearingStr: String,
    forceDarkTheme: Boolean,
    txtTitle: String,
    txtAddressLabel: String,
    txtNotSpecified: String,
    txtGpsLabel: String,
    txtSupportHeight: String,
    txtDistanceLabel: String,
    txtFromMyPosition: String,
    txtBearingLabel: String,
    txtOperatorsTitle: String,
    txtGeneratedBy: String,
    txtShareSiteVia: String,
    txtIdNumber: String,
    txtInitError: String,
    txtSupportNature: String,
    txtOwner: String,
    mapBitmap: Bitmap?,
    incMap: Boolean,
    incSupport: Boolean,
    incOperators: Boolean,
    incConfidential: Boolean,
    incQrCode: Boolean,
    shareOrder: List<String>
) {
    try {
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(currentView.findViewTreeLifecycleOwner())
            setViewTreeSavedStateRegistryOwner(currentView.findViewTreeSavedStateRegistryOwner())
            setViewTreeViewModelStoreOwner(currentView.findViewTreeViewModelStoreOwner())

            setContent {
                val colors = if (forceDarkTheme) darkColorScheme() else lightColorScheme()
                MaterialTheme(colorScheme = colors) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Column(
                            modifier = Modifier
                                .width(400.dp)
                                .wrapContentHeight()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(text = txtTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                            shareOrder.forEach { block ->
                                when (block) {
                                    "map" -> {
                                        if (incMap && mapBitmap != null) {
                                            Image(
                                                bitmap = mapBitmap.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                    "support" -> {
                                        if (incSupport) {
                                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(AppStrings.supportDetailsTitle, fontWeight = FontWeight.Bold)
                                                    }
                                                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                                    // ✅ ON UTILISE LES VRAIES DONNÉES DU PYLÔNE
                                                    val technique = techniquesMap[mainInfo.idAnfr]
                                                    val fullAddress = technique?.adresse?.takeIf { it.isNotBlank() } ?: "Adresse non spécifiée"
                                                    val nature = physique?.natureSupport?.takeIf { it.isNotBlank() } ?: txtNotSpecified
                                                    val proprietaire = physique?.proprietaire?.takeIf { it.isNotBlank() } ?: "Inconnu"
                                                    val hauteur = "${physique?.hauteur ?: "--"} m"
                                                    val idSupportValue = physique?.idSupport?.takeIf { it.isNotBlank() } ?: txtNotSpecified

                                                    Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append("$txtIdNumber ") }; withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(idSupportValue) } }, fontSize = 14.sp)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append("$txtAddressLabel ") }; withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(fullAddress) } }, fontSize = 14.sp)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append("$txtGpsLabel ") }; withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(String.format(Locale.US, "%.5f, %.5f", mainInfo.latitude, mainInfo.longitude)) } }, fontSize = 14.sp)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append("$txtSupportNature : ") }; withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(nature) } }, fontSize = 14.sp)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append("$txtOwner : ") }; withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(proprietaire) } }, fontSize = 14.sp)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append("$txtSupportHeight ") }; withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(hauteur) } }, fontSize = 14.sp)

                                                    if (incConfidential) {
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(AppStrings.distanceHidden, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                    } else {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append("$txtDistanceLabel ") }; withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("$distanceStr$txtFromMyPosition") } }, fontSize = 14.sp)
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append("$txtBearingLabel ") }; withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(bearingStr) } }, fontSize = 14.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "operators" -> {
                                        if (incOperators) {
                                            Column {
                                                Text(text = "$txtOperatorsTitle (${antennas.size})", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                antennas.forEach { antenna ->
                                                    OperatorDetailItem(
                                                        antenna = antenna,
                                                        technique = techniquesMap[antenna.idAnfr],
                                                        hsEntity = hsDataMap[antenna.idAnfr], // 🚨 PASSAGE DE LA PANNE ICI
                                                        cardBgColor = Color.Transparent,
                                                        blockShape = RoundedCornerShape(0.dp),
                                                        useOneUi = false,
                                                        onClick = {}
                                                    )
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // ✅ CORRECTION : On entoure avec if (incQrCode)
                            if (incQrCode) {
                                val qrUri = "geotower://support/$siteId"
                                val qrBitmap = remember(qrUri) { generateQrCodeBitmap(qrUri, 200) }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (qrBitmap != null) {
                                        Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
                                        Spacer(modifier = Modifier.width(16.dp))
                                    }
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text(text = fr.geotower.utils.AppStrings.scanToOpen, fontSize = 11.sp, color = Color.Gray)
                                        Text(text = fr.geotower.utils.AppStrings.geoTowerApp, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }

                            Text(text = txtGeneratedBy, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                    }
                }
            }
        }

        val rootView = currentView.rootView as? ViewGroup ?: return
        composeView.translationX = 10000f
        rootView.addView(composeView)

        composeView.post {
            try {
                val displayMetrics = context.resources.displayMetrics
                val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(displayMetrics.widthPixels, View.MeasureSpec.EXACTLY)
                val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

                composeView.measure(widthMeasureSpec, heightMeasureSpec)
                composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)

                val bitmap = Bitmap.createBitmap(composeView.measuredWidth, composeView.measuredHeight, Bitmap.Config.ARGB_8888)
                composeView.draw(Canvas(bitmap))
                rootView.removeView(composeView)

                val cachePath = File(context.cacheDir, "images")
                cachePath.mkdirs()
                val safeId = physique?.idSupport?.takeIf { it.isNotBlank() } ?: siteId.toString()
                val file = File(cachePath, "Geotower_support_$safeId.png")
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, uri)
                    type = "image/png"
                    clipData = ClipData.newUri(context.contentResolver, "Capture", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, txtShareSiteVia))
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, txtInitError, Toast.LENGTH_SHORT).show()
                rootView.removeView(composeView)
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
}

@Composable
fun ThemeOptionItem(
    iconVector: ImageVector? = null,
    iconRes: Int? = null,
    label: String,
    subLabel: String? = null,
    trailingIcon: ImageVector? = null,
    isSubItem: Boolean = false,
    useOneUi: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(if (useOneUi) RoundedCornerShape(16.dp) else RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (isSubItem) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconSize = 24.dp
        if (iconRes != null) {
            Image(painter = painterResource(id = iconRes), contentDescription = null, modifier = Modifier.size(iconSize).clip(RoundedCornerShape(4.dp)))
        } else if (iconVector != null) {
            Icon(imageVector = iconVector, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(iconSize))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontWeight = if (isSubItem) FontWeight.Medium else FontWeight.SemiBold, fontSize = if (isSubItem) 15.sp else 16.sp, color = MaterialTheme.colorScheme.onSurface)
            if (subLabel != null) { Text(text = subLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (trailingIcon != null) { Icon(trailingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntennaShareMenu(
    info: LocalisationEntity,
    physique: PhysiqueEntity?,
    technique: TechniqueEntity?,
    hsDataMap: Map<String, fr.geotower.data.models.SiteHsEntity>, // 🚨 AJOUT
    distanceStr: String,
    bearingStr: String,
    useOneUi: Boolean,
    buttonShape: Shape,
    globalMapRef: org.osmdroid.views.MapView?,
    communityPhotosSize: Int
) {
    val context = LocalContext.current
    val currentView = LocalView.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    var lastClickTime by remember { mutableLongStateOf(0L) }
    fun safeClick(action: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > 700L) {
            lastClickTime = currentTime
            action()
        }
    }

    var showShareSheet by remember { mutableStateOf(false) }
    var showSelectionSheet by remember { mutableStateOf(false) }
    var selectedShareTheme by remember { mutableStateOf(false) }

    var incMap by remember { mutableStateOf(prefs.getBoolean("share_map_enabled", true)) }
    var incSupport by remember { mutableStateOf(prefs.getBoolean("share_support_enabled", true)) }
    var incHeights by remember { mutableStateOf(prefs.getBoolean("share_heights_enabled", true)) }
    var incIds by remember { mutableStateOf(prefs.getBoolean("share_ids_enabled", true)) }
    var incDates by remember { mutableStateOf(prefs.getBoolean("share_dates_enabled", true)) }
    var incAddress by remember { mutableStateOf(prefs.getBoolean("share_address_enabled", true)) }
    var incFreqs by remember { mutableStateOf(prefs.getBoolean("share_freq_enabled", true)) }
    var incConfidential by remember { mutableStateOf(prefs.getBoolean("share_confidential_enabled", false)) }
    var incQrCode by remember { mutableStateOf(prefs.getBoolean("share_site_qr_enabled", true)) } // ✅ NOUVELLE VARIABLE
    var shareOrder by remember { mutableStateOf(prefs.getString("share_order", "map,support,ids,dates,address,status,freq")!!.split(",")) }

    // ✅ FORCE LE RECHARGEMENT DES PARAMÈTRES PAR DÉFAUT À CHAQUE OUVERTURE
    LaunchedEffect(showShareSheet) {
        if (showShareSheet) {
            incMap = prefs.getBoolean("share_map_enabled", true)
            incSupport = prefs.getBoolean("share_support_enabled", true)
            incIds = prefs.getBoolean("share_ids_enabled", true)
            incDates = prefs.getBoolean("share_dates_enabled", true)
            incAddress = prefs.getBoolean("share_address_enabled", true)
            incFreqs = prefs.getBoolean("share_freq_enabled", true)
            incConfidential = prefs.getBoolean("share_confidential_enabled", false)
            incQrCode = prefs.getBoolean("share_site_qr_enabled", true)
            shareOrder = prefs.getString("share_order", "map,support,ids,dates,address,status,freq")!!
                .split(",").filter { it != "heights" }
        }
    }

    val txtSiteDetailsTitle = AppStrings.siteDetailTitle
    val txtAddressLabel = AppStrings.addressLabel
    val txtNotSpecified = AppStrings.notSpecified
    val txtGpsLabel = AppStrings.gpsLabel
    val txtSupportHeight = AppStrings.supportHeight
    val txtDistanceLabel = AppStrings.distanceLabel
    val txtFromMyPosition = AppStrings.fromMyPosition
    val txtBearingLabel = AppStrings.bearingLabel
    val txtGeneratedBy = AppStrings.generatedBy
    val txtShareSiteVia = AppStrings.shareSiteVia
    val txtimplementation = AppStrings.implementation
    val txtLastModification = AppStrings.lastModification
    val txtIdentifiers = AppStrings.identifiers
    val txtIdNumber = AppStrings.idNumber
    val txtFrequenciesTitle = AppStrings.frequenciesTitle
    val txtBandsNotSpecified = AppStrings.bandsNotSpecified
    val txtInService = AppStrings.inService
    val txtTechnically = AppStrings.technically
    val txtUnknownStatus = AppStrings.unknownStatus
    val txtAnfrStationNumber = AppStrings.anfrStationNumber
    val txtDates = AppStrings.dates
    val txtError = AppStrings.error
    val txtProjectApproved = AppStrings.projectApproved
    val txtActivatedOn = AppStrings.activatedOn
    val txtDateNotSpecifiedAnfr = AppStrings.dateNotSpecifiedAnfr
    val txtPanelHeightsTitle = AppStrings.panelHeightsTitle
    val txtAzimuths = AppStrings.azimuthsLabel
    val txtIdSupportLabel = AppStrings.idSupportLabel
    val txtSupportDetailsTitle = AppStrings.supportDetailsTitle
    val txtSupportNature = AppStrings.supportNature
    val txtOwner = AppStrings.owner
    val txtAntennaType = AppStrings.antennaType
    val txtCommunityPhotosTitle = AppStrings.communityPhotosTitleShort(communityPhotosSize)
    val txtThemeLight = AppStrings.themeLight
    val txtLightModeDesc = AppStrings.lightModeDesc
    val txtThemeDark = AppStrings.themeDark
    val txtDarkModeDesc = AppStrings.darkModeDesc
    val txtShareSite = AppStrings.shareSite
    val txtShareAs = AppStrings.shareAs
    val txtImageContent = AppStrings.imageContent
    val txtShareConfidentialOption = AppStrings.shareConfidentialOption
    val txtShareConfidentialDesc = AppStrings.shareConfidentialDesc
    val txtGenerateImage = AppStrings.generateImage
    val txtMove = AppStrings.move
    val txtInitError = AppStrings.initError

    Button(
        onClick = { safeClick { showShareSheet = true } },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = buttonShape,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
    ) {
        Icon(Icons.Default.Share, null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(txtShareSite, fontWeight = FontWeight.Bold)
    }

    if (showShareSheet) {
        ModalBottomSheet(onDismissRequest = { showShareSheet = false }, sheetState = sheetState, containerColor = sheetBgColor) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp, start = 16.dp, end = 16.dp)) {
                Text(txtShareAs, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp))
                ThemeOptionItem(iconVector = Icons.Outlined.LightMode, label = txtThemeLight, subLabel = txtLightModeDesc, useOneUi = useOneUi) { safeClick { selectedShareTheme = false; showShareSheet = false; showSelectionSheet = true } }
                Spacer(modifier = Modifier.height(12.dp))
                ThemeOptionItem(iconVector = Icons.Outlined.DarkMode, label = txtThemeDark, subLabel = txtDarkModeDesc, useOneUi = useOneUi) { safeClick { selectedShareTheme = true; showShareSheet = false; showSelectionSheet = true } }
            }
        }
    }

    if (showSelectionSheet) {
        ModalBottomSheet(onDismissRequest = { showSelectionSheet = false }, sheetState = sheetState, containerColor = sheetBgColor) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp, start = 24.dp, end = 24.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { safeClick { showSelectionSheet = false; showShareSheet = true } }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    Text(text = txtImageContent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.width(48.dp))
                }

                val density = LocalDensity.current
                val itemHeight = 48.dp
                val stepPx = with(density) { itemHeight.toPx() }
                var draggedItem by remember { mutableStateOf<String?>(null) }
                var dragOffset by remember { mutableFloatStateOf(0f) }

                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    shareOrder.forEach { blockId ->
                        key(blockId) {
                            val isDragged = draggedItem == blockId
                            val dragModifier = Modifier.pointerInput(blockId) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggedItem = blockId; dragOffset = 0f },
                                    onDrag = { change, dragAmount ->
                                        change.consume(); dragOffset += dragAmount.y
                                        val currentIndex = shareOrder.indexOf(blockId)
                                        if (currentIndex < 0) return@detectDragGesturesAfterLongPress
                                        var newIndex = currentIndex
                                        while (dragOffset > stepPx * 0.5f && newIndex < shareOrder.size - 1) { dragOffset -= stepPx; newIndex++ }
                                        while (dragOffset < -stepPx * 0.5f && newIndex > 0) { dragOffset += stepPx; newIndex-- }
                                        if (newIndex != currentIndex) {
                                            val newList = shareOrder.toMutableList()
                                            val item = newList.removeAt(currentIndex)
                                            newList.add(newIndex, item)
                                            shareOrder = newList
                                            prefs.edit().putString("share_order", newList.joinToString(",")).apply()
                                        }
                                    },
                                    onDragEnd = { draggedItem = null; dragOffset = 0f },
                                    onDragCancel = { draggedItem = null; dragOffset = 0f }
                                )
                            }

                            // Dans AntennaShareMenu -> showSelectionSheet -> shareOrder.forEach
                            val (label, checked, onChecked) = when (blockId) {
                                // ✅ ON SUPPRIME prefs.edit() : les changements restent locaux au menu
                                "map" -> Triple(AppStrings.shareMapOption, incMap, { it: Boolean -> incMap = it })
                                "support" -> Triple(AppStrings.shareSupportOption, incSupport, { it: Boolean -> incSupport = it })
                                "ids" -> Triple(AppStrings.shareIdsOption, incIds, { it: Boolean -> incIds = it })
                                "dates" -> Triple(AppStrings.shareDatesOption, incDates, { it: Boolean -> incDates = it })
                                "address" -> Triple(AppStrings.shareAddressOption, incAddress, { it: Boolean -> incAddress = it })
                                "status" -> Triple(AppStrings.shareStatusOption, AppConfig.shareSiteStatus.value, { it: Boolean -> AppConfig.shareSiteStatus.value = it })
                                "freq" -> Triple(AppStrings.shareFreqOption, incFreqs, { it: Boolean -> incFreqs = it })
                                else -> Triple("", false, { _: Boolean -> })
                            }

                            if (label.toString().isNotEmpty()) {
                                @Suppress("UNCHECKED_CAST") val safeOnChecked = onChecked as (Boolean) -> Unit
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(itemHeight)
                                        .zIndex(if (isDragged) 10f else 0f)
                                        .graphicsLayer { translationY = if (isDragged) dragOffset else 0f; scaleX = if (isDragged) 1.02f else 1f; scaleY = if (isDragged) 1.02f else 1f; shadowElevation = if (isDragged) 8.dp.toPx() else 0f }
                                        .background(if (isDragged) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(8.dp))
                                        .then(dragModifier)
                                        .padding(start = 8.dp)
                                ) {
                                    Icon(Icons.Default.DragHandle, contentDescription = txtMove, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(label.toString(), modifier = Modifier.weight(1f))
                                    if (useOneUi) Box(modifier = Modifier.scale(0.85f)) { OneUiSwitch(checked as Boolean, safeOnChecked) }
                                    else Switch(checked = checked as Boolean, onCheckedChange = safeOnChecked, modifier = Modifier.scale(0.8f))
                                }
                            }
                        }
                    }

                    // ✅ AJOUT DU BOUTON RÉINITIALISER
                    TextButton(
                        onClick = {
                            shareOrder = listOf("map", "support", "ids", "dates", "address", "status", "freq")
                            prefs.edit().putString("share_order", shareOrder.joinToString(",")).apply()
                            incMap = true; incSupport = true; incIds = true; incDates = true; incAddress = true; incFreqs = true; incQrCode = true
                            AppConfig.shareSiteStatus.value = true
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(fr.geotower.utils.AppStrings.resetToDefault, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // ✅ NOUVEAU BOUTON : INTERRUPTEUR QR CODE
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("QR Code", modifier = Modifier.weight(1f))
                        if (useOneUi) Box(modifier = Modifier.scale(0.85f)) { OneUiSwitch(checked = incQrCode, onCheckedChange = { incQrCode = it }) }
                        else Switch(checked = incQrCode, onCheckedChange = { incQrCode = it }, modifier = Modifier.scale(0.8f))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(txtShareConfidentialOption, fontWeight = FontWeight.Bold, color = if(incConfidential) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text(txtShareConfidentialDesc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (useOneUi) Box(modifier = Modifier.scale(0.85f)) { OneUiSwitch(checked = incConfidential, onCheckedChange = { incConfidential = it }) }
                        else Switch(checked = incConfidential, onCheckedChange = { incConfidential = it }, modifier = Modifier.scale(0.8f))
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            showSelectionSheet = false
                            currentView.postDelayed({
                                val mapBmp = if (incMap) { globalMapRef?.let { map -> try { val bmp = Bitmap.createBitmap(map.width, map.height, Bitmap.Config.ARGB_8888); map.draw(Canvas(bmp)); bmp } catch (e: Exception) { null } } } else null
                                shareFullAntennaCapture(
                                    context, currentView, info,
                                    physique, technique,
                                    hsDataMap, // 🚨 AJOUT ICI : C'est le 6ème paramètre attendu
                                    distanceStr, bearingStr, selectedShareTheme,
                                    txtSiteDetailsTitle, txtAddressLabel, txtNotSpecified, txtGpsLabel, txtSupportHeight, txtDistanceLabel, txtFromMyPosition, txtBearingLabel, txtGeneratedBy, txtShareSiteVia, txtimplementation, txtLastModification, txtIdentifiers, txtIdNumber, txtFrequenciesTitle, txtBandsNotSpecified, txtInService, txtTechnically, txtUnknownStatus, txtAnfrStationNumber, txtDates, txtError, txtProjectApproved, txtActivatedOn, txtDateNotSpecifiedAnfr, txtPanelHeightsTitle, txtAzimuths, txtIdSupportLabel, txtSupportDetailsTitle, txtSupportNature, txtOwner, txtAntennaType,
                                    mapBmp, txtInitError, emptyList(), txtCommunityPhotosTitle,
                                    // ✅ CORRECTION DE L'APPEL : ON PASSE incQrCode JUSTE AVANT shareOrder !
                                    incMap, incSupport, incHeights, incIds, incDates, incAddress, incFreqs, incConfidential, incQrCode, shareOrder
                                )
                            }, 300)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp), shape = buttonShape
                    ) { Text(txtGenerateImage, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportShareMenu(
    siteId: Long,
    antennas: List<LocalisationEntity>,
    physique: PhysiqueEntity?,
    techniquesMap: Map<String, TechniqueEntity>,
    hsDataMap: Map<String, fr.geotower.data.models.SiteHsEntity>, // 🚨 AJOUT
    distanceStr: String,
    bearingStr: String,
    useOneUi: Boolean,
    buttonShape: Shape,
    globalMapRef: org.osmdroid.views.MapView?
) {
    val context = LocalContext.current
    val currentView = LocalView.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    var lastClickTime by remember { mutableLongStateOf(0L) }
    fun safeClick(action: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > 700L) {
            lastClickTime = currentTime
            action()
        }
    }

    var showShareSheet by remember { mutableStateOf(false) }
    var showSelectionSheet by remember { mutableStateOf(false) }
    var selectedShareTheme by remember { mutableStateOf(false) }

    var incMap by remember { mutableStateOf(prefs.getBoolean("share_sup_map_enabled", true)) }
    var incSupport by remember { mutableStateOf(prefs.getBoolean("share_sup_support_enabled", true)) }
    var incOperators by remember { mutableStateOf(prefs.getBoolean("share_sup_operators_enabled", true)) }
    var incConfidential by remember { mutableStateOf(prefs.getBoolean("share_sup_confidential_enabled", false)) }
    var incQrCode by remember { mutableStateOf(prefs.getBoolean("share_sup_qr_enabled", true)) } // ✅ NOUVELLE VARIABLE
    var shareOrder by remember { mutableStateOf(prefs.getString("share_sup_order", "map,support,operators")!!.split(",")) }

    // ✅ FORCE LE RECHARGEMENT À CHAQUE OUVERTURE
    LaunchedEffect(showShareSheet) {
        if (showShareSheet) {
            incMap = prefs.getBoolean("share_sup_map_enabled", true)
            incSupport = prefs.getBoolean("share_sup_support_enabled", true)
            incOperators = prefs.getBoolean("share_sup_operators_enabled", true)
            incConfidential = prefs.getBoolean("share_sup_confidential_enabled", false)
            incQrCode = prefs.getBoolean("share_sup_qr_enabled", true) // ✅ RECHARGEMENT
            shareOrder = prefs.getString("share_sup_order", "map,support,operators")!!.split(",")
        }
    }

    val txtSupportDetailTitle = AppStrings.supportDetailTitle
    val txtAddressLabel = AppStrings.addressLabel
    val txtNotSpecified = AppStrings.notSpecified
    val txtGpsLabel = AppStrings.gpsLabel
    val txtSupportHeight = AppStrings.supportHeight
    val txtDistanceLabel = AppStrings.distanceLabel
    val txtFromMyPosition = AppStrings.fromMyPosition
    val txtBearingLabel = AppStrings.bearingLabel
    val txtOperatorsTitle = AppStrings.operatorsTitle
    val txtGeneratedBy = AppStrings.generatedBy
    val txtShareSiteVia = AppStrings.shareSiteVia
    val txtThemeLight = AppStrings.themeLight
    val txtLightModeDesc = AppStrings.lightModeDesc
    val txtThemeDark = AppStrings.themeDark
    val txtDarkModeDesc = AppStrings.darkModeDesc
    val txtIdNumber = AppStrings.idNumber
    val txtSupportNature = AppStrings.supportNature
    val txtOwner = AppStrings.owner // ✅ AJOUT DE LA TRADUCTION
    val txtShareSite = AppStrings.shareSite
    val txtShareAs = AppStrings.shareAs
    val txtImageContent = AppStrings.imageContent
    val txtShareConfidentialOption = AppStrings.shareConfidentialOption
    val txtShareConfidentialDesc = AppStrings.shareConfidentialDesc
    val txtGenerateImage = AppStrings.generateImage
    val txtMove = AppStrings.move
    val txtInitError = AppStrings.initError

    Button(
        onClick = { safeClick { showShareSheet = true } },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = buttonShape,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(txtShareSite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }

    if (showShareSheet && antennas.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { showShareSheet = false }, sheetState = sheetState, containerColor = sheetBgColor) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp, start = 16.dp, end = 16.dp)) {
                Text(txtShareAs, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp))
                ThemeOptionItem(iconVector = Icons.Outlined.LightMode, label = txtThemeLight, subLabel = txtLightModeDesc, useOneUi = useOneUi) { safeClick { selectedShareTheme = false; showShareSheet = false; showSelectionSheet = true } }
                Spacer(modifier = Modifier.height(12.dp))
                ThemeOptionItem(iconVector = Icons.Outlined.DarkMode, label = txtThemeDark, subLabel = txtDarkModeDesc, useOneUi = useOneUi) { safeClick { selectedShareTheme = true; showShareSheet = false; showSelectionSheet = true } }
            }
        }
    }

    if (showSelectionSheet && antennas.isNotEmpty()) {
        val mainInfo = antennas.first()
        ModalBottomSheet(onDismissRequest = { showSelectionSheet = false }, sheetState = sheetState, containerColor = sheetBgColor) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp, start = 24.dp, end = 24.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { safeClick { showSelectionSheet = false; showShareSheet = true } }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    Text(text = txtImageContent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.width(48.dp))
                }

                val density = LocalDensity.current
                val itemHeight = 48.dp
                val stepPx = with(density) { itemHeight.toPx() }
                var draggedItem by remember { mutableStateOf<String?>(null) }
                var dragOffset by remember { mutableFloatStateOf(0f) }

                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    shareOrder.forEach { blockId ->
                        key(blockId) {
                            val isDragged = draggedItem == blockId
                            val dragModifier = Modifier.pointerInput(blockId) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggedItem = blockId; dragOffset = 0f },
                                    onDrag = { change, dragAmount ->
                                        change.consume(); dragOffset += dragAmount.y
                                        val currentIndex = shareOrder.indexOf(blockId)
                                        if (currentIndex < 0) return@detectDragGesturesAfterLongPress
                                        var newIndex = currentIndex
                                        while (dragOffset > stepPx * 0.5f && newIndex < shareOrder.size - 1) { dragOffset -= stepPx; newIndex++ }
                                        while (dragOffset < -stepPx * 0.5f && newIndex > 0) { dragOffset += stepPx; newIndex-- }
                                        if (newIndex != currentIndex) {
                                            val newList = shareOrder.toMutableList()
                                            val item = newList.removeAt(currentIndex)
                                            newList.add(newIndex, item)
                                            shareOrder = newList
                                            prefs.edit().putString("share_sup_order", newList.joinToString(",")).apply()
                                        }
                                    },
                                    onDragEnd = { draggedItem = null; dragOffset = 0f },
                                    onDragCancel = { draggedItem = null; dragOffset = 0f }
                                )
                            }

                            // Dans SupportShareMenu -> showSelectionSheet -> shareOrder.forEach
                            val (label, checked, onChecked) = when (blockId) {
                                "map" -> Triple(AppStrings.shareMapOption, incMap, { it: Boolean -> incMap = it })
                                "support" -> Triple(AppStrings.shareSupportOption, incSupport, { it: Boolean -> incSupport = it })
                                "operators" -> Triple(AppStrings.operatorsTitle, incOperators, { it: Boolean -> incOperators = it })
                                else -> Triple("", false, { _: Boolean -> })
                            }

                            if (label.toString().isNotEmpty()) {
                                @Suppress("UNCHECKED_CAST") val safeOnChecked = onChecked as (Boolean) -> Unit
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(itemHeight)
                                        .zIndex(if (isDragged) 10f else 0f)
                                        .graphicsLayer { translationY = if (isDragged) dragOffset else 0f; scaleX = if (isDragged) 1.02f else 1f; scaleY = if (isDragged) 1.02f else 1f; shadowElevation = if (isDragged) 8.dp.toPx() else 0f }
                                        .background(if (isDragged) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(8.dp))
                                        .then(dragModifier)
                                        .padding(start = 8.dp)
                                ) {
                                    Icon(Icons.Default.DragHandle, contentDescription = txtMove, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(label.toString(), modifier = Modifier.weight(1f))
                                    if (useOneUi) Box(modifier = Modifier.scale(0.85f)) { OneUiSwitch(checked as Boolean, safeOnChecked) }
                                    else Switch(checked = checked as Boolean, onCheckedChange = safeOnChecked, modifier = Modifier.scale(0.8f))
                                }
                            }
                        }
                    }

                    // ✅ AJOUT DU BOUTON RÉINITIALISER
                    TextButton(
                        onClick = {
                            shareOrder = listOf("map", "support", "operators")
                            prefs.edit().putString("share_sup_order", shareOrder.joinToString(",")).apply()
                            incMap = true; incSupport = true; incOperators = true; incQrCode = true
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(fr.geotower.utils.AppStrings.resetToDefault, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // ✅ NOUVEAU BOUTON : INTERRUPTEUR QR CODE
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("QR Code", modifier = Modifier.weight(1f))
                        if (useOneUi) Box(modifier = Modifier.scale(0.85f)) { OneUiSwitch(checked = incQrCode, onCheckedChange = { incQrCode = it }) }
                        else Switch(checked = incQrCode, onCheckedChange = { incQrCode = it }, modifier = Modifier.scale(0.8f))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(txtShareConfidentialOption, fontWeight = FontWeight.Bold, color = if(incConfidential) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text(txtShareConfidentialDesc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (useOneUi) Box(modifier = Modifier.scale(0.85f)) { OneUiSwitch(checked = incConfidential, onCheckedChange = { incConfidential = it }) }
                        else Switch(checked = incConfidential, onCheckedChange = { incConfidential = it }, modifier = Modifier.scale(0.8f))
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            showSelectionSheet = false
                            globalMapRef?.let { map -> map.controller.setZoom(17.5); map.controller.setCenter(org.osmdroid.util.GeoPoint(mainInfo.latitude, mainInfo.longitude)) }
                            currentView.postDelayed({
                                val mapBmp = if (incMap) { try { globalMapRef?.let { map -> val bmp = Bitmap.createBitmap(map.width, map.height, Bitmap.Config.ARGB_8888); map.draw(Canvas(bmp)); bmp } } catch (e: Exception) { null } } else null
                                shareFullSiteCapture(
                                    context, currentView, siteId, antennas.first(), antennas,
                                    physique, techniquesMap,
                                    hsDataMap,
                                    distanceStr, bearingStr, selectedShareTheme,
                                    txtSupportDetailTitle, txtAddressLabel, txtNotSpecified, txtGpsLabel, txtSupportHeight, txtDistanceLabel, txtFromMyPosition, txtBearingLabel, txtOperatorsTitle, txtGeneratedBy, txtShareSiteVia, txtIdNumber,
                                    txtInitError, txtSupportNature, txtOwner, mapBmp,
                                    incMap, incSupport, incOperators, incConfidential, incQrCode, shareOrder
                                )
                            }, 300)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = buttonShape
                    ) {
                        Text(txtGenerateImage, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

}

// ✅ NOUVEAU : Fonction pour générer le QR Code
fun generateQrCodeBitmap(text: String, size: Int): Bitmap? {
    return try {
        val hints = java.util.EnumMap<com.google.zxing.EncodeHintType, Any>(com.google.zxing.EncodeHintType::class.java)
        hints[com.google.zxing.EncodeHintType.MARGIN] = 1
        val bitMatrix = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}