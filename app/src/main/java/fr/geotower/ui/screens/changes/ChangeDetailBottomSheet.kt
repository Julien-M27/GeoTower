package fr.geotower.ui.screens.changes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.location.Geocoder
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.data.api.RetrofitClient
import fr.geotower.data.models.ChangeDetailItem
import fr.geotower.data.models.ChangeMapPoint
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.FrequencyStatusPalette
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.ScreenPageTarget
import fr.geotower.utils.TechFrequencyOrder
import fr.geotower.utils.TechFrequencyOrderHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeDetailBottomSheet(
    point: ChangeMapPoint,
    onDismiss: () -> Unit,
    onOpenSupportDetail: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val repository = remember { AnfrRepository(RetrofitClient.apiService, context.applicationContext) }
    var resolvedAddress by remember(point) { mutableStateOf(point.address) }

    LaunchedEffect(point) {
        if (resolvedAddress.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                val scopeId = point.supportId ?: point.changes.firstOrNull()?.stationId
                if (!scopeId.isNullOrBlank()) {
                    val rows = repository.getFavoriteScopeSiteRows(scopeId)
                    val fullAddr = rows.firstNotNullOfOrNull { row ->
                        val adr = row.adresse?.trim()?.takeIf { it.isNotBlank() }
                        val com = row.commune?.trim()?.takeIf { it.isNotBlank() }
                        when {
                            adr != null && com != null -> "$adr, $com"
                            adr != null -> adr
                            com != null -> com
                            else -> null
                        }
                    }
                    if (!fullAddr.isNullOrBlank()) {
                        resolvedAddress = fullAddr
                    }
                }

                // Si toujours non trouvé (ex. nouveau site récent), fallback Geocoder
                if (resolvedAddress.isNullOrBlank() && point.latitude != 0.0 && point.longitude != 0.0) {
                    try {
                        val geocoder = Geocoder(context, Locale.FRANCE)
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(point.latitude, point.longitude, 1)
                        val first = addresses?.firstOrNull()
                        if (first != null) {
                            val line = first.getAddressLine(0)
                            val locality = first.locality ?: first.subAdminArea
                            resolvedAddress = line ?: listOfNotNull(first.thoroughfare, locality).joinToString(", ")
                        }
                    } catch (_: Exception) {}
                }
            }
        }
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
                .padding(horizontal = sizing.spacing(20.dp))
                .padding(bottom = sizing.spacing(28.dp))
                .verticalScroll(rememberScrollState())
        ) {
            // En-tête avec titre et bouton fermer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (!point.supportId.isNullOrBlank()) {
                            "Support n° ${point.supportId}"
                        } else {
                            "Site n° ${point.changes.firstOrNull()?.stationId ?: "ANFR"}"
                        },
                        style = sizing.textStyle(MaterialTheme.typography.titleLarge),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!resolvedAddress.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(sizing.spacing(2.dp)))
                        Text(
                            text = resolvedAddress!!,
                            style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val locDesc = buildString {
                        if (!point.department.isNullOrBlank()) append("Dép. ${point.department} • ")
                        append(String.format(Locale.US, "%.5f, %.5f", point.latitude, point.longitude))
                    }
                    Spacer(modifier = Modifier.height(sizing.spacing(2.dp)))
                    Text(
                        text = locDesc,
                        style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Fermer")
                }
            }

            Spacer(modifier = Modifier.height(sizing.spacing(14.dp)))

            // Pastilles des opérateurs présents
            val operators = point.operators()
            if (operators.isNotEmpty()) {
                Text(
                    text = if (operators.size > 1) "Opérateurs concernés" else "Opérateur concerné",
                    style = sizing.textStyle(MaterialTheme.typography.labelMedium),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(sizing.spacing(6.dp)))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    operators.forEach { op ->
                        val key = OperatorColors.keyFor(op) ?: op
                        val spec = OperatorColors.specForKey(key)
                        val color = spec?.let { Color(it.colorArgb) } ?: Color(ChangesMarkerPainter.fallbackColorFor(op))
                        val label = spec?.label ?: op
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = color.copy(alpha = 0.15f),
                            border = null
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = sizing.spacing(10.dp), vertical = sizing.spacing(4.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(sizing.component(8.dp))
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Spacer(modifier = Modifier.width(sizing.spacing(6.dp)))
                                Text(
                                    text = label,
                                    style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))
            }

            // Liste de chaque changement (regroupés intelligemment par transition/opérateur pour éviter les doublons)
            val groupedChanges = groupChanges(point.changes)
            val headerText = if (groupedChanges.size == point.changes.size) {
                if (groupedChanges.size > 1) {
                    "Évolutions enregistrées (${groupedChanges.size})"
                } else {
                    "Évolution enregistrée (1)"
                }
            } else {
                val totalFreqs = groupedChanges.sumOf { it.systems.size.coerceAtLeast(1) }
                val evoPrefix = if (groupedChanges.size > 1) {
                    "Évolutions enregistrées (${groupedChanges.size}"
                } else {
                    "Évolution enregistrée (1"
                }
                val freqSuffix = if (totalFreqs > 1) "$totalFreqs fréquences" else "1 fréquence"
                "$evoPrefix • $freqSuffix)"
            }

            Text(
                text = headerText,
                style = sizing.textStyle(MaterialTheme.typography.labelMedium),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))

            Column(verticalArrangement = Arrangement.spacedBy(sizing.spacing(10.dp))) {
                groupedChanges.forEach { change ->
                    ChangeItemCard(change = change)
                }
            }

            Spacer(modifier = Modifier.height(sizing.spacing(20.dp)))

            // Boutons d'action : Copier coordonnées et ouvrir fiche si possible
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
            ) {
                OutlinedButton(
                    onClick = {
                        val coords = "${point.latitude},${point.longitude}"
                        val clip = ClipData.newPlainText("GPS", coords)
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(clip)
                        Toast.makeText(context, "Coordonnées copiées : $coords", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Coordonnées", fontSize = 13.sp)
                }

                if (!point.supportId.isNullOrBlank() && onOpenSupportDetail != null) {
                    Button(
                        onClick = {
                            onOpenSupportDetail(point.supportId)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Fiche support", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * Modèle regroupé pour présenter les évolutions de façon claire et sans doublons.
 */
data class GroupedChangeItem(
    val code: String,
    val op: String?,
    val ops: List<String>?,
    val wasStatus: String?,
    val nowStatus: String?,
    val systems: List<String>,
    val movedMeters: Double?,
    val rawCount: Int
)

/**
 * Regroupe les évolutions qui partagent le même code, le même opérateur et la même transition de statut.
 */
fun groupChanges(changes: List<ChangeDetailItem>): List<GroupedChangeItem> {
    val map = linkedMapOf<String, MutableList<ChangeDetailItem>>()
    changes.forEach { item ->
        val key = "${item.code}|${item.op.orEmpty()}|${item.wasStatus.orEmpty()}|${item.nowStatus.orEmpty()}|${item.movedMeters ?: 0.0}"
        map.getOrPut(key) { mutableListOf() }.add(item)
    }

    return map.values.map { list ->
        val first = list.first()
        val distinctSystems = mutableListOf<String>()
        list.forEach { item ->
            item.system?.trim()?.takeIf { it.isNotBlank() }?.let { if (!distinctSystems.contains(it)) distinctSystems.add(it) }
            item.systems?.forEach { s ->
                val trimmed = s.trim()
                if (trimmed.isNotBlank() && !distinctSystems.contains(trimmed)) distinctSystems.add(trimmed)
            }
        }

        val sortedSystems = sortSystemsByConfig(distinctSystems)

        GroupedChangeItem(
            code = first.code,
            op = first.op,
            ops = first.ops,
            wasStatus = first.wasStatus,
            nowStatus = first.nowStatus,
            systems = sortedSystems,
            movedMeters = first.movedMeters,
            rawCount = list.size
        )
    }
}

/**
 * Trie les fréquences selon les préférences de l'utilisateur (Technologies et Fréquences : 5G -> 2G ou 2G -> 5G).
 */
fun sortSystemsByConfig(systems: List<String>): List<String> {
    val order = AppConfig.resolveOrderForScreen(ScreenPageTarget.MAP)
    val technoOrder = AppConfig.siteTechnoOrder.value.ifEmpty {
        TechFrequencyOrderHelper.technologiesOrder(order)
    }
    val freqOrder5G = AppConfig.siteFreqOrder5G.value.ifEmpty {
        TechFrequencyOrderHelper.frequenciesOrder5G(order)
    }
    val freqOrder4G = AppConfig.siteFreqOrder4G.value.ifEmpty {
        TechFrequencyOrderHelper.frequenciesOrder4G(order)
    }
    val freqOrder3G = AppConfig.siteFreqOrder3G.value.ifEmpty {
        TechFrequencyOrderHelper.frequenciesOrder3G(order)
    }
    val freqOrder2G = AppConfig.siteFreqOrder2G.value.ifEmpty {
        TechFrequencyOrderHelper.frequenciesOrder2G(order)
    }

    fun extractTech(sys: String): String {
        val upper = sys.uppercase()
        return when {
            upper.contains("5G") || upper.contains("NR") -> "5G"
            upper.contains("4G") || upper.contains("LTE") -> "4G"
            upper.contains("3G") || upper.contains("UMTS") -> "3G"
            upper.contains("2G") || upper.contains("GSM") -> "2G"
            else -> "FH"
        }
    }

    fun extractFreqNum(sys: String): Int {
        val digits = sys.filter { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }

    return systems.sortedWith(
        compareBy(
            { sys ->
                val tech = extractTech(sys)
                val idx = technoOrder.indexOf(tech)
                if (idx != -1) idx else 999
            },
            { sys ->
                val tech = extractTech(sys)
                val freqDigits = sys.filter { it.isDigit() }
                val orderList = when (tech) {
                    "5G" -> freqOrder5G
                    "4G" -> freqOrder4G
                    "3G" -> freqOrder3G
                    "2G" -> freqOrder2G
                    else -> emptyList()
                }
                val idx = orderList.indexOf(freqDigits)
                if (idx != -1) idx else 999
            },
            { sys ->
                val num = extractFreqNum(sys)
                if (order == TechFrequencyOrder.DESCENDING) -num else num
            },
            { sys -> sys }
        )
    )
}

@Composable
private fun ChangeItemCard(change: GroupedChangeItem) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val opName = formatOpName(change.op)
    val opsNames = change.ops?.map { formatOpName(it) }?.joinToString(", ")

    val isActivation = change.code == "STATUS_CHANGED" && (
        change.nowStatus?.contains("service", ignoreCase = true) == true ||
        change.nowStatus?.contains("activ", ignoreCase = true) == true ||
        change.nowStatus?.contains("opérat", ignoreCase = true) == true ||
        change.nowStatus?.contains("operat", ignoreCase = true) == true ||
        change.nowStatus?.contains("opǸrat", ignoreCase = true) == true
    )
    val isDeactivation = change.code == "STATUS_CHANGED" && (
        change.nowStatus?.contains("arrêt", ignoreCase = true) == true ||
        change.nowStatus?.contains("arret", ignoreCase = true) == true ||
        change.nowStatus?.contains("hors service", ignoreCase = true) == true ||
        change.nowStatus?.contains("supprim", ignoreCase = true) == true ||
        change.nowStatus?.contains("désactiv", ignoreCase = true) == true ||
        change.nowStatus?.contains("desactiv", ignoreCase = true) == true
    )

    val (title, subtitle) = when {
        change.code == "SITE_ADDED" -> Pair(
            "Nouveau site créé",
            opsNames ?: (if (opName.isNotBlank()) opName else "Tous opérateurs")
        )
        isActivation -> Pair(
            "Activation",
            "${change.wasStatus ?: "Projet approuvé"} ➔ ${change.nowStatus}${if (opName.isNotBlank()) " • $opName" else ""}"
        )
        isDeactivation -> Pair(
            "Mise hors service",
            "${change.wasStatus ?: "?"} ➔ ${change.nowStatus}${if (opName.isNotBlank()) " • $opName" else ""}"
        )
        change.code == "STATUS_CHANGED" -> Pair(
            "Changement de statut",
            "${change.wasStatus ?: "?"} ➔ ${change.nowStatus ?: "En service"}${if (opName.isNotBlank()) " • $opName" else ""}"
        )
        change.code == "SYSTEM_ADDED" -> Pair(
            if (change.systems.size > 1) "Nouvelles fréquences (${change.systems.size})" else "Ajout de fréquence",
            if (opName.isNotBlank()) opName else if (change.systems.size > 1) "Fréquences ajoutées" else "Fréquence ajoutée"
        )
        change.code == "OPERATOR_ADDED" -> Pair(
            if (change.ops != null && change.ops.size > 1) "Arrivée d'opérateurs" else "Arrivée d'un opérateur",
            if (opName.isNotBlank()) "$opName sur ce support" else if (change.ops != null && change.ops.size > 1) "Opérateurs ajoutés sur ce support" else "Opérateur ajouté sur ce support"
        )
        change.code == "SITE_REMOVED" -> Pair(
            "Site démantelé / supprimé",
            opsNames ?: (if (opName.isNotBlank()) opName else "Retiré de l'observatoire")
        )
        change.code == "SYSTEM_REMOVED" -> Pair(
            if (change.systems.size > 1) "Extinction de fréquences (${change.systems.size})" else "Extinction de fréquence",
            if (opName.isNotBlank()) opName else if (change.systems.size > 1) "Fréquences retirées" else "Fréquence retirée"
        )
        change.code == "OPERATOR_REMOVED" -> Pair(
            if (change.ops != null && change.ops.size > 1) "Départ d'opérateurs" else "Départ d'un opérateur",
            if (opName.isNotBlank()) "$opName n'émet plus sur ce support" else if (change.ops != null && change.ops.size > 1) "Les opérateurs n'émettent plus sur ce support" else "L'opérateur n'émet plus sur ce support"
        )
        change.code == "MOVED" -> Pair(
            "Déplacement géographique",
            "Coordonnées recalculées (~${change.movedMeters?.toInt() ?: 0} mètres)"
        )
        else -> Pair("Modification diverse", change.code)
    }

    // Couleurs alignées avec celles de statut de la fiche site (FrequencyStatusPalette / SiteStatusCard)
    val titleColor = when {
        isActivation -> FrequencyStatusPalette.InService // Vert (En service)
        isDeactivation || change.code == "SITE_REMOVED" || change.code == "SYSTEM_REMOVED" || change.code == "OPERATOR_REMOVED" -> Color(0xFFE53935) // Rouge (Suppression / Extinction / HS)
        change.code == "SITE_ADDED" || change.code == "SYSTEM_ADDED" || change.code == "OPERATOR_ADDED" -> FrequencyStatusPalette.Approved // Orange (Projet approuvé / Ajout)
        else -> MaterialTheme.colorScheme.onSurface
    }

    val iconRes = when {
        isActivation -> R.drawable.ic_change_activation
        isDeactivation || change.code == "SYSTEM_REMOVED" -> R.drawable.ic_change_extinction
        change.code == "SITE_ADDED" || change.code == "SYSTEM_ADDED" -> R.drawable.ic_change_ajout
        change.code == "SITE_REMOVED" -> R.drawable.ic_change_suppression
        else -> null
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(sizing.spacing(12.dp))) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (iconRes != null) {
                    Box(
                        modifier = Modifier
                            .size(sizing.component(34.dp))
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(sizing.component(20.dp))
                        )
                    }
                    Spacer(modifier = Modifier.width(sizing.spacing(10.dp)))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                        fontWeight = FontWeight.Bold,
                        color = titleColor
                    )
                    Spacer(modifier = Modifier.height(sizing.spacing(2.dp)))
                    Text(
                        text = subtitle,
                        style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Fréquences concernées affichées sous forme de badges
            if (change.systems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
                Text(
                    text = when {
                        isActivation -> if (change.systems.size > 1) "Fréquences activées :" else "Fréquence activée :"
                        change.code == "SYSTEM_ADDED" -> if (change.systems.size > 1) "Fréquences ajoutées :" else "Fréquence ajoutée :"
                        change.code == "SYSTEM_REMOVED" -> if (change.systems.size > 1) "Fréquences retirées :" else "Fréquence retirée :"
                        else -> if (change.systems.size > 1) "Fréquences concernées :" else "Fréquence concernée :"
                    },
                    style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(sizing.spacing(4.dp)))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(sizing.spacing(6.dp)),
                    verticalArrangement = Arrangement.spacedBy(sizing.spacing(6.dp)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    change.systems.forEach { sys ->
                        FrequencyBadge(sys = sys, op = change.op)
                    }
                }
            }
        }
    }
}

@Composable
private fun FrequencyBadge(sys: String, op: String?) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val key = OperatorColors.keyFor(op ?: "") ?: op ?: ""
    val spec = OperatorColors.specForKey(key)
    val color = spec?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text = sys,
            style = sizing.textStyle(MaterialTheme.typography.labelSmall),
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = sizing.spacing(8.dp), vertical = sizing.spacing(3.dp))
        )
    }
}

private fun formatOpName(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val key = OperatorColors.keyFor(raw) ?: return raw
    return OperatorColors.specForKey(key)?.label ?: raw
}

