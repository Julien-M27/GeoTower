package fr.geotower.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.R
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppIconManager
import fr.geotower.utils.AppStrings

data class HomeLogoOption(val id: String, val name: String, val resId: Int?)

@Composable
fun HomeLogoSelectorBlock(
    safeClick: SafeClick
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

    // On lit le choix actuel (par défaut : "app")
    var currentSelection by remember { mutableStateOf(prefs.getString("home_logo_choice", "app") ?: "app") }

    // On récupère le logo actuel de l'application
    val appLogoRes by AppIconManager.currentIconRes

    // ---> 1. ON ÉCOUTE L'OPÉRATEUR PAR DÉFAUT EN TEMPS RÉEL <---
    val defaultOp by AppConfig.defaultOperator

    // On détermine son ID pour la liste
    val defaultId = when {
        defaultOp.contains("Orange", ignoreCase = true) -> "orange"
        defaultOp.contains("Bouygues", ignoreCase = true) -> "bouygues"
        defaultOp.contains("SFR", ignoreCase = true) -> "sfr"
        defaultOp.contains("Free", ignoreCase = true) -> "free"
        else -> ""
    }

    // ---> 2. L'ORDRE DE BASE DEMANDÉ (Orange > Bouygues > SFR > Free) <---
    val baseOperators = listOf(
        HomeLogoOption("orange", AppStrings.logoOrange, R.drawable.logo_orange),
        HomeLogoOption("bouygues", AppStrings.logoBouygues, R.drawable.logo_bouygues),
        HomeLogoOption("sfr", AppStrings.logoSfr, R.drawable.logo_sfr),
        HomeLogoOption("free", AppStrings.logoFree, R.drawable.logo_free)
    )

    // ---> 3. ON PLACE L'OPÉRATEUR FAVORIS EN TÊTE S'IL EXISTE <---
    val sortedOperators = if (defaultId.isNotEmpty()) {
        val favOpt = baseOperators.find { it.id == defaultId }!!
        listOf(favOpt) + baseOperators.filter { it.id != defaultId }
    } else {
        baseOperators
    }

    // ---> 4. LISTE FINALE (Application toujours en premier) <---
    val options = listOf(
        HomeLogoOption("app", AppStrings.logoApp, appLogoRes.takeIf { it != 0 })
    ) + sortedOperators

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = AppStrings.homeLogoSettingTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(options) { option ->
                val isSelected = currentSelection == option.id
                val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(80.dp)
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
                            .size(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(BorderStroke(3.dp, borderColor), RoundedCornerShape(16.dp))
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (option.resId != null && option.resId != 0) {

                            // ---> CORRECTION DU CRASH ICI <---
                            // On remplace Image() par AndroidView pour supporter les icônes Mipmap
                            androidx.compose.ui.viewinterop.AndroidView(
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = option.name,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
