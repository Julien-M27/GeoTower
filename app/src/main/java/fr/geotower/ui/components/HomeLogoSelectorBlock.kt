package fr.geotower.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppIconManager
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.OperatorLogos
import androidx.compose.ui.res.stringResource
import fr.geotower.R
import fr.geotower.ui.theme.LocalGeoTowerUiStyle

data class HomeLogoOption(val id: String, val name: String, val resId: Int?)

@Composable
fun HomeLogoSelectorBlock(
    safeClick: SafeClick
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val sizing = LocalGeoTowerUiStyle.current.sizing

    // On lit le choix actuel (par défaut : "app")
    var currentSelection by remember { mutableStateOf(prefs.getString("home_logo_choice", "app") ?: "app") }

    // On récupère le logo actuel de l'application
    val appLogoRes by AppIconManager.currentIconRes

    // ---> 1. ON ÉCOUTE L'OPÉRATEUR PAR DÉFAUT EN TEMPS RÉEL <---
    val defaultOp by AppConfig.defaultOperator

    // On détermine son ID pour la liste
    val defaultId = OperatorColors.keyFor(defaultOp)?.lowercase().orEmpty()

    // ---> 2. L'ORDRE DE BASE DEMANDÉ (Orange > Bouygues > SFR > Free) <---
    val baseOperators = OperatorColors.all.mapNotNull { operator ->
        OperatorLogos.drawableRes(operator.key)?.let { logoRes ->
            HomeLogoOption(
                id = operator.key.lowercase(),
                name = operator.label,
                resId = logoRes
            )
        }
    }

    // ---> 3. ON PLACE L'OPÉRATEUR FAVORIS EN TÊTE S'IL EXISTE <---
    val sortedOperators = if (defaultId.isNotEmpty()) {
        val favOpt = baseOperators.firstOrNull { it.id == defaultId }
        if (favOpt != null) listOf(favOpt) + baseOperators.filter { it.id != defaultId } else baseOperators
    } else {
        baseOperators
    }

    // ---> 4. LISTE FINALE (Application toujours en premier) <---
    val options = listOf(
        HomeLogoOption("app", stringResource(R.string.appstrings_logo_app), appLogoRes.takeIf { it != 0 })
    ) + sortedOperators

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.appstrings_home_logo_setting_title),
            style = sizing.textStyle(MaterialTheme.typography.titleMedium),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = sizing.spacing(12.dp))
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(sizing.spacing(16.dp)),
            contentPadding = PaddingValues(bottom = sizing.spacing(8.dp))
        ) {
            items(options, key = { it.id }) { option ->
                val isSelected = currentSelection == option.id
                val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(sizing.component(80.dp))
                        .clickable {
                            safeClick("home_logo_${option.id}") {
                                currentSelection = option.id
                                // Sauvegarde immédiate du choix
                                prefs.edit().putString("home_logo_choice", option.id).apply()
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(sizing.component(72.dp))
                            .clip(RoundedCornerShape(sizing.component(16.dp)))
                            .border(BorderStroke(sizing.component(3.dp), borderColor), RoundedCornerShape(sizing.component(16.dp)))
                            .padding(sizing.spacing(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (option.resId != null && option.resId != 0) {

                            // ---> CORRECTION DU CRASH ICI <---
                            // On remplace Image() par AndroidView pour supporter les icônes Mipmap
                            androidx.compose.ui.viewinterop.AndroidView(
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(sizing.component(12.dp))),
                                factory = { ctx ->
                                    android.widget.ImageView(ctx).apply {
                                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                        setImageResource(option.resId)
                                    }
                                },
                                update = { view -> view.setImageResource(option.resId) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))
                    Text(
                        text = option.name,
                        fontSize = sizing.text(12.sp),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
