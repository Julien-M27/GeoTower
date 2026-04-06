package fr.geotower.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SupportDetailsSection(
    mainInfo: LocalisationEntity,
    physique: PhysiqueEntity?,
    technique: TechniqueEntity?,
    distanceMeters: Float?, // ✅ Modifié : On demande la distance brute en mètres
    bearingStr: String,
    cardBgColor: Color,
    blockShape: Shape
) {
    val context = LocalContext.current
    val isMi = AppConfig.distanceUnit.intValue == 1

    val txtNotSpecified = AppStrings.notSpecified
    val txtIdNumber = AppStrings.idNumber
    val txtIdSupportCopy = AppStrings.idSupportCopy
    val txtIdCopied = AppStrings.idCopied
    val txtIdUnavailable = AppStrings.idUnavailable

    val txtAddressLabel = AppStrings.addressLabel
    val txtAddressCopy = AppStrings.addressCopy
    val txtAddressCopied = AppStrings.addressCopied

    val txtGpsLabel = AppStrings.gpsLabel
    val txtGpsCoordsCopy = AppStrings.gpsCoordsCopy
    val txtCoordsCopied = AppStrings.coordsCopied

    val txtSupportHeight = AppStrings.supportHeight
    val txtSupportNature = AppStrings.supportNature
    val txtDistanceLabel = AppStrings.distanceLabel
    val txtFromMyPosition = AppStrings.fromMyPosition
    val txtBearingLabel = AppStrings.bearingLabel

    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(blockShape)
                .background(cardBgColor)
                .padding(16.dp)
        ) {
            val fullAddress = technique?.adresse?.takeIf { it.isNotBlank() } ?: "Adresse non spécifiée"
            val gpsCoords = String.format(Locale.US, "%.5f°, %.5f°", mainInfo.latitude, mainInfo.longitude)
            val cleanGpsCoords = String.format(Locale.US, "%.5f, %.5f", mainInfo.latitude, mainInfo.longitude)

            val supportIdValue = physique?.idSupport?.takeIf { it.isNotBlank() } ?: txtNotSpecified

            InfoLine(
                label = txtIdNumber,
                value = supportIdValue,
                onCopy = {
                    if (supportIdValue != txtNotSpecified) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(txtIdSupportCopy, supportIdValue))
                        Toast.makeText(context, txtIdCopied, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, txtIdUnavailable, Toast.LENGTH_SHORT).show()
                    }
                }
            )
            Spacer(modifier = Modifier.height(4.dp))

            InfoLine(
                label = txtAddressLabel,
                value = fullAddress,
                onCopy = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(txtAddressCopy, fullAddress))
                    Toast.makeText(context, txtAddressCopied, Toast.LENGTH_SHORT).show()
                }
            )

            InfoLine(
                label = txtGpsLabel,
                value = gpsCoords,
                onCopy = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(txtGpsCoordsCopy, cleanGpsCoords))
                    Toast.makeText(context, txtCoordsCopied, Toast.LENGTH_SHORT).show()
                }
            )

            val nature = AppStrings.translateNature(physique?.natureSupport)

            // ✅ CALCUL HAUTEUR (ft/in ou m)
            val hauteurMeters = physique?.hauteur?.toString()?.toFloatOrNull()
            val hauteurStr = if (hauteurMeters != null) {
                if (isMi) {
                    val totalFeet = hauteurMeters * 3.28084f
                    val feet = totalFeet.toInt()
                    val inches = ((totalFeet - feet) * 12).roundToInt()
                    if (inches == 12) "${feet + 1} ft 0 in" else "$feet ft $inches in"
                } else {
                    "$hauteurMeters m"
                }
            } else "--"

            // ✅ CALCUL DISTANCE (mi/ft ou km/m)
            val finalDistanceStr = if (distanceMeters != null) {
                if (isMi) {
                    val distMiles = distanceMeters / 1609.34f
                    if (distMiles < 0.1f) "${(distanceMeters * 3.28084f).toInt()} ft"
                    else String.format(Locale.US, "%.2f mi", distMiles)
                } else {
                    if (distanceMeters >= 1000) String.format(Locale.US, "%.2f km", distanceMeters / 1000f)
                    else "${distanceMeters.toInt()} m"
                }
            } else "--"

            InfoLine(txtSupportHeight, hauteurStr)
            InfoLine(label = "$txtSupportNature : ", value = nature)
            InfoLine(txtDistanceLabel, "$finalDistanceStr $txtFromMyPosition")
            InfoLine(txtBearingLabel, bearingStr)
        }
    }
}

@Composable
fun InfoLine(label: String, value: String, onCopy: (() -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = buildAnnotatedString {
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append(label) }
            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(value) }
        }, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (onCopy != null) {
            IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = AppStrings.copy, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun SiteSupportDetailsBlock(
    info: LocalisationEntity,
    physique: PhysiqueEntity?,
    distanceMeters: Float?, // ✅ AJOUT : Distance brute
    bearingStr: String,     // ✅ AJOUT : Azimut
    cardBgColor: Color,
    blockShape: Shape
) {
    val txtSupportDetailsTitle = AppStrings.supportDetailsTitle
    val txtSupportNature = AppStrings.supportNature
    val txtOwner = AppStrings.owner
    val txtNotSpecified = AppStrings.notSpecified
    val txtDistanceLabel = AppStrings.distanceLabel
    val txtFromMyPosition = AppStrings.fromMyPosition
    val txtBearingLabel = AppStrings.bearingLabel

    // ✅ LECTURE DE L'UNITÉ
    val isMi = AppConfig.distanceUnit.intValue == 1

    // ✅ CALCUL DISTANCE (mi/ft ou km/m)
    val finalDistanceStr = if (distanceMeters != null) {
        if (isMi) {
            val distMiles = distanceMeters / 1609.34f
            if (distMiles < 0.1f) "${(distanceMeters * 3.28084f).toInt()} ft"
            else String.format(Locale.US, "%.2f mi", distMiles)
        } else {
            if (distanceMeters >= 1000) String.format(Locale.US, "%.2f km", distanceMeters / 1000f)
            else "${distanceMeters.toInt()} m"
        }
    } else "--"

    Card(
        shape = blockShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = txtSupportDetailsTitle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            val nature = AppStrings.translateNature(physique?.natureSupport)
            val proprietaire = AppStrings.translateOwner(physique?.proprietaire)

            InfoLine(label = "$txtSupportNature : ", value = nature)
            Spacer(modifier = Modifier.height(4.dp))
            InfoLine(label = "$txtOwner : ", value = proprietaire)

            // ✅ AFFICHAGE DES NOUVELLES INFOS DE DISTANCE
            if (distanceMeters != null) {
                Spacer(modifier = Modifier.height(4.dp))
                InfoLine(txtDistanceLabel, "$finalDistanceStr $txtFromMyPosition")
                if (bearingStr.isNotBlank()) {
                    InfoLine(txtBearingLabel, bearingStr)
                }
            }
        }
    }
}