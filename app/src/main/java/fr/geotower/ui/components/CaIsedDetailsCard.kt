package fr.geotower.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import fr.geotower.R
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import fr.geotower.data.CaIsedSectorDetail
import fr.geotower.data.CaIsedSiteDetails
import fr.geotower.utils.AppConfig
import fr.geotower.utils.ScreenPageTarget
import fr.geotower.utils.TechFrequencyOrder
import java.util.Locale

@Composable
fun CaIsedDetailsCard(
    details: CaIsedSiteDetails,
    cardBgColor: Color,
    blockShape: Shape
) {
    val freqOrder = AppConfig.resolveOrderForScreen(ScreenPageTarget.SITE_DETAIL)
    val sortedSectors = remember(details.sectors, freqOrder) {
        sortCaSectors(details.sectors, freqOrder)
    }
    var showAllSectors by remember { mutableStateOf(false) }
    val visibleSectors = remember(sortedSectors, showAllSectors) {
        if (showAllSectors || sortedSectors.size <= 6) sortedSectors else sortedSectors.take(6)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = blockShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CellTower,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.ca_ised_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            val lblRecordId = stringResource(R.string.ca_ised_record_id)
            val lblLicenceArea = stringResource(R.string.ca_ised_licence_area)
            val lblProvince = stringResource(R.string.ca_ised_province)
            val lblSiteType = stringResource(R.string.ca_ised_site_type)
            val lblOutdoor = stringResource(R.string.ca_ised_site_type_outdoor)
            val lblIndoor = stringResource(R.string.ca_ised_site_type_indoor)
            val lblAccount = stringResource(R.string.ca_ised_account)
            val lblImport = stringResource(R.string.ca_ised_import)
            val metaLines = buildList {
                details.recordId?.let { add(lblRecordId to it) }
                details.areaName?.let { area ->
                    val idSuffix = details.areaId?.let { " ($it)" }.orEmpty()
                    add(lblLicenceArea to "$area$idSuffix")
                }
                details.provinceCode?.let { add(lblProvince to it) }
                details.siteType?.let { st ->
                    val label = when (st.uppercase(Locale.US)) {
                        "O" -> lblOutdoor
                        "I" -> lblIndoor
                        else -> st
                    }
                    add(lblSiteType to label)
                }
                details.accountNumber?.let { add(lblAccount to it) }
                details.uploadDate?.let { add(lblImport to it) }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                metaLines.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (details.sectors.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = stringResource(R.string.ca_ised_sectors_title, details.sectors.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                visibleSectors.forEach { sector ->
                    CaIsedSectorItem(sector)
                }

                if (details.sectors.size > 6) {
                    TextButton(
                        onClick = { showAllSectors = !showAllSectors },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = if (showAllSectors) {
                                stringResource(R.string.ca_ised_collapse)
                            } else {
                                stringResource(R.string.ca_ised_show_all_sectors, details.sectors.size)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaIsedSectorItem(sector: CaIsedSectorDetail) {
    val (techLabel, techBg, techFg) = resolveTechBadgeStyle(sector.technology)
    val bandLabel = resolveCanadianBandName(sector.txFrequency, sector.technology)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // En-tête : badges Techno / Bande / Largeur / PCI
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (techLabel != null) {
                        SectorBadge(text = techLabel, containerColor = techBg, contentColor = techFg)
                    }
                    if (bandLabel != null) {
                        SectorBadge(
                            text = bandLabel,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    sector.bandwidth?.let { bw ->
                        SectorBadge(
                            text = String.format(Locale.US, "%.0f MHz", bw),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                sector.physicalId?.takeIf { it.isNotBlank() }?.let { pci ->
                    Spacer(modifier = Modifier.width(6.dp))
                    SectorBadge(
                        text = "PCI $pci",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Fréquences TX / RX
            val freqText = buildList {
                sector.txFrequency?.let { add(String.format(Locale.US, "TX %.2f MHz", it)) }
                sector.rxFrequency?.let { add(String.format(Locale.US, "RX %.2f MHz", it)) }
            }.joinToString("  •  ")
            if (freqText.isNotBlank()) {
                Text(
                    text = freqText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Géométrie RF
            val lblAzimuth = stringResource(R.string.ca_ised_azimuth)
            val lblHeight = stringResource(R.string.ca_ised_height)
            val lblTilt = stringResource(R.string.ca_ised_tilt)
            val lblBeamwidth = stringResource(R.string.ca_ised_beamwidth)
            val lblEirp = stringResource(R.string.ca_ised_eirp)
            val lblType = stringResource(R.string.ca_ised_type)
            val lblAntenna = stringResource(R.string.ca_ised_antenna)
            val lblRadio = stringResource(R.string.ca_ised_radio)
            val rfParts = buildList {
                sector.txAntAzimuth?.let { add(lblAzimuth to "${it}°") }
                sector.txAntHeight?.let { add(lblHeight to String.format(Locale.US, "%.1f m", it)) }
                sector.txAntElevationAngle?.takeIf { it != 0.0 }?.let {
                    add(lblTilt to String.format(Locale.US, "%.1f°", it))
                }
                if (sector.txAntHorizBeamwidth != null && sector.txAntVertBeamwidth != null) {
                    add(
                        lblBeamwidth to String.format(
                            Locale.US,
                            "%.0f° × %.1f°",
                            sector.txAntHorizBeamwidth,
                            sector.txAntVertBeamwidth
                        )
                    )
                }
            }
            if (rfParts.isNotEmpty()) {
                LabeledInlineLine(rfParts)
            }

            // Puissance, PIRE & MIMO
            val powerParts = buildList {
                sector.txPower?.let { add("TX" to String.format(Locale.US, "%.1f W", it)) }
                sector.txAntGain?.let { add("Gain" to String.format(Locale.US, "%.1f dBi", it)) }
                sector.eirpWatts?.let { w ->
                    val dbm = sector.eirpDbm
                    if (dbm != null) {
                        add(lblEirp to String.format(Locale.US, "%.0f W (%.1f dBm)", w, dbm))
                    }
                }
                sector.downlinkAllocation?.takeIf { it < 100.0 }?.let {
                    add("DL" to String.format(Locale.US, "%.0f%%", it))
                }
                if (sector.txNumberAntennas != null && sector.rxNumberAntennas != null) {
                    val typeSuffix = sector.txAntType?.let { " ($it)" }.orEmpty()
                    add("MIMO" to "${sector.txNumberAntennas}T${sector.rxNumberAntennas}R$typeSuffix")
                } else {
                    sector.txAntType?.let { add(lblType to it) }
                }
            }
            if (powerParts.isNotEmpty()) {
                LabeledInlineLine(powerParts)
            }

            // Matériel antenne / radio
            val ant = listOfNotNull(sector.txAntManufacturer, sector.txAntModelNo).joinToString(" ").trim()
            val radio = listOfNotNull(sector.txRadioManufacturerCode, sector.txRadioModelNumber).joinToString(" ").trim()
            val hwParts = buildList {
                if (ant.isNotBlank()) add(lblAntenna to ant)
                if (radio.isNotBlank()) add(lblRadio to radio)
            }
            if (hwParts.isNotEmpty()) {
                LabeledInlineLine(hwParts)
            }

            // Identifiants & Licence
            val idsLine = buildList {
                sector.cellId?.takeIf { it.isNotBlank() }?.let { add("Cell $it") }
                sector.licenceNumber?.takeIf { it.isNotBlank() }?.let { lic ->
                    val cat = sector.licenceCategory?.let { " ($it)" }.orEmpty()
                    add("Lic. $lic$cat")
                }
            }.joinToString("  •  ")
            if (idsLine.isNotBlank()) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = idsLine,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SectorBadge(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun LabeledInlineLine(items: List<Pair<String, String>>) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = MaterialTheme.colorScheme.onSurface
    Text(
        text = buildAnnotatedString {
            items.forEachIndexed { index, (label, value) ->
                if (index > 0) append("  •  ")
                withStyle(SpanStyle(color = labelColor)) { append("$label : ") }
                withStyle(SpanStyle(color = valueColor, fontWeight = FontWeight.Medium)) { append(value) }
            }
        },
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun resolveTechBadgeStyle(rawTech: String?): Triple<String?, Color, Color> {
    val upper = rawTech?.trim()?.uppercase(Locale.US).orEmpty()
    return when {
        upper == "5GNR" -> Triple("5G NR", Color(0xFF6A1B9A), Color.White)
        upper == "5GDSS" -> Triple("5G DSS", Color(0xFF7B1FA2), Color.White)
        upper.contains("5G") || upper.contains("NR") -> Triple("5G", Color(0xFF6A1B9A), Color.White)
        upper == "LTE" -> Triple("4G LTE", Color(0xFF1565C0), Color.White)
        upper in setOf("HSPA", "HSPA+", "UMTS", "WCDMA") -> Triple("3G $upper", Color(0xFF00838F), Color.White)
        upper in setOf("GSM", "EDGE", "GPRS") -> Triple("2G $upper", Color(0xFF546E7A), Color.White)
        upper.isNotBlank() -> Triple(rawTech, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
        else -> Triple(null, Color.Transparent, Color.Transparent)
    }
}

private fun resolveCanadianBandName(txFreqMhz: Double?, rawTech: String?): String? {
    val f = txFreqMhz ?: return null
    val is5g = rawTech?.uppercase(Locale.US)?.let { it.contains("5G") || it.contains("NR") } == true
    val is3g = rawTech?.uppercase(Locale.US)?.let { it.contains("HSPA") || it.contains("UMTS") } == true
    return when {
        f in 600.0..697.99 -> if (is5g) "600 MHz (n71)" else "600 MHz (B71)"
        f in 698.0..805.99 -> "700 MHz (B12/B13/B17)"
        f in 806.0..901.99 -> when {
            is5g -> "850 MHz (n5)"
            is3g -> "850 MHz (B5)"
            else -> "850 MHz (B5)"
        }
        f in 1710.0..1784.99 || f in 2110.0..2199.99 -> if (is5g) "2100 AWS (n66)" else "2100 AWS (B4/B66)"
        f in 1850.0..1999.99 -> if (is5g) "1900 PCS (n2/n25)" else "1900 PCS (B2/B25)"
        f in 2300.0..2399.99 -> "2300 WCS (B30)"
        f in 2500.0..2699.99 -> if (is5g) "2600 BRS (n7/n41)" else "2600 BRS (B7/B41)"
        f in 3400.0..3999.99 -> if (is5g) "3500/3800 (n78/n77)" else "3500 MHz (B42/B43)"
        f >= 24000.0 -> "${(f / 1000.0).toInt()} GHz mmWave"
        else -> "${f.toInt()} MHz"
    }
}

private fun sortCaSectors(
    sectors: List<CaIsedSectorDetail>,
    order: TechFrequencyOrder
): List<CaIsedSectorDetail> {
    fun techRank(rawTech: String?): Int {
        val upper = rawTech?.trim()?.uppercase(Locale.US).orEmpty()
        val gen = when {
            upper.contains("5G") || upper.contains("NR") -> 5
            upper.contains("LTE") -> 4
            upper.contains("HSPA") || upper.contains("UMTS") || upper.contains("WCDMA") -> 3
            upper.contains("GSM") || upper.contains("EDGE") || upper.contains("GPRS") -> 2
            else -> 0
        }
        return if (order == TechFrequencyOrder.DESCENDING) -gen else gen
    }

    return sectors.sortedWith(
        compareBy<CaIsedSectorDetail>(
            { techRank(it.technology) },
            {
                val f = it.txFrequency ?: 0.0
                if (order == TechFrequencyOrder.DESCENDING) -f else f
            },
            { it.txAntAzimuth ?: Int.MAX_VALUE }
        )
    )
}
