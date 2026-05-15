package fr.geotower.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.R
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings
import fr.geotower.utils.OperatorColors

fun oneUiActionButtonShape(
    useOneUi: Boolean,
    defaultShape: Shape = RoundedCornerShape(12.dp)
): Shape = if (useOneUi) RoundedCornerShape(22.dp) else defaultShape

// ============================================================
// 1. LOGIQUE MATHÉMATIQUE DES BOUTONS ONE UI
// ============================================================

@Composable
fun OneUiRadioButton(
    selected: Boolean,
    onClick: () -> Unit
) {
    OneUiRadioButton(
        selected = selected,
        selectedColor = MaterialTheme.colorScheme.primary,
        onClick = onClick
    )
}

@Composable
fun OneUiRadioButton(
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    val activeColor = selectedColor
    val color = if (selected) activeColor else MaterialTheme.colorScheme.outline

    Box(modifier = Modifier.size(24.dp).clip(CircleShape).border(2.dp, color, CircleShape).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }.padding(5.dp), contentAlignment = Alignment.Center) {
        if (selected) Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(color))
    }
}

// ============================================================
// 2. LOGIQUE MATHÉMATIQUE DU CURSEUR UNIVERSEL (CANVAS)
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSliderCard(
    title: String,
    currentValue: Int,
    steps: List<Int>,
    labels: List<String>,
    onValueChange: (Int) -> Unit,
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean,
    footerText: String? = null
) {
    var currentIndex by remember { mutableFloatStateOf(steps.indexOf(currentValue).coerceAtLeast(0).toFloat()) }
    val accentColor = MaterialTheme.colorScheme.primary
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent

    Surface(shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(labels[currentIndex.toInt()], style = MaterialTheme.typography.titleMedium, color = accentColor, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (useOneUi) {
                Slider(
                    value = currentIndex,
                    onValueChange = { currentIndex = it },
                    onValueChangeFinished = { onValueChange(steps[currentIndex.toInt()]) },
                    valueRange = 0f..(steps.size - 1).toFloat(),
                    steps = steps.size - 2,
                    thumb = {
                        Box(modifier = Modifier.size(24.dp).background(MaterialTheme.colorScheme.surface, CircleShape).border(3.dp, MaterialTheme.colorScheme.primary, CircleShape))
                    },
                    track = { _ ->
                        Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
                            val trackColor = Color.Gray.copy(alpha = 0.3f)
                            val dotColor = Color.Gray.copy(alpha = 0.6f)

                            drawLine(color = trackColor, start = Offset(0f, size.height / 2), end = Offset(size.width, size.height / 2), strokeWidth = 14.dp.toPx(), cap = StrokeCap.Round)

                            // Logique mathématique de calcul des espacements
                            val dotCount = steps.size
                            val stepWidth = size.width / (dotCount - 1)
                            for (i in 0 until dotCount) {
                                drawCircle(color = dotColor, radius = 4.dp.toPx(), center = Offset(i * stepWidth, size.height / 2))
                            }
                        }
                    }
                )
            } else {
                Slider(
                    value = currentIndex, onValueChange = { currentIndex = it }, onValueChangeFinished = { onValueChange(steps[currentIndex.toInt()]) },
                    valueRange = 0f..(steps.size - 1).toFloat(), steps = steps.size - 2
                )
            }

            if (footerText != null) {
                Text(footerText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ============================================================
// 3. AFFICHAGE DES FENÊTRES OPÉRATEUR ("CARTE") ET LANGUE
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperatorSheet(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit, sheetState: SheetState, useOneUi: Boolean, bubbleColor: Color) {
    var tempOp by remember { mutableStateOf(current) }
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        Column(
            modifier = Modifier
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(AppStrings.defaultOperator, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OperatorItem(AppStrings.none, null, Color.Gray, tempOp == "Aucun", useOneUi, bubbleColor) { tempOp = "Aucun" }
                OperatorGroupTitle(AppStrings.operatorRegionMetro)
                OperatorColors.metro.forEach { operator ->
                    OperatorItem(
                        name = operator.label,
                        logoRes = fr.geotower.utils.OperatorLogos.drawableRes(operator.key),
                        operatorColor = Color(operator.colorArgb),
                        isSelected = OperatorColors.keyFor(tempOp) == operator.key,
                        useOneUi = useOneUi,
                        bubbleColor = bubbleColor
                    ) { tempOp = operator.label }
                }

                OperatorGroupTitle(AppStrings.operatorRegionOverseas)
                OperatorColors.overseas.forEach { operator ->
                    OperatorItem(
                        name = operator.label,
                        logoRes = fr.geotower.utils.OperatorLogos.drawableRes(operator.key),
                        operatorColor = Color(operator.colorArgb),
                        isSelected = OperatorColors.keyFor(tempOp) == operator.key,
                        useOneUi = useOneUi,
                        bubbleColor = bubbleColor
                    ) { tempOp = operator.label }
                }

                Button(
                    onClick = { onSelect(tempOp); onDismiss() },
                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp)
                ) { Text(AppStrings.validate, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun OperatorGroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )
}

@Composable
fun OperatorItem(name: String, logoRes: Int?, operatorColor: Color, isSelected: Boolean, useOneUi: Boolean, bubbleColor: Color, onClick: () -> Unit) {
    val activeBg = operatorColor.copy(alpha = 0.1f)
    val bgColor = if (isSelected) activeBg else (if (useOneUi) bubbleColor else Color.Transparent)
    val border = if (useOneUi) { if (isSelected) BorderStroke(2.dp, operatorColor) else null } else { BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) operatorColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) }

    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().height(64.dp), color = bgColor, border = border, shape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (logoRes != null) Image(painterResource(logoRes), null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
            else Box(modifier = Modifier.size(40.dp).background(operatorColor.copy(alpha = 0.14f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text(text = name.take(1).uppercase(), color = operatorColor, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = name, style = MaterialTheme.typography.titleMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
            if (useOneUi) OneUiRadioButton(isSelected, selectedColor = operatorColor, onClick = onClick) else RadioButton(selected = isSelected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = operatorColor))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSheet(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit, sheetState: SheetState, useOneUi: Boolean, bubbleColor: Color) {
    var tempLang by remember { mutableStateOf(current) }
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())

    // ✅ MODIFICATION : On utilise la couleur primaire vibrante
    val activeColor = MaterialTheme.colorScheme.primary
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(AppStrings.appLanguageLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LanguageItem(AppStrings.systemLanguage, "📱", tempLang == AppStrings.LANGUAGE_SYSTEM, useOneUi, bubbleColor, activeColor) { tempLang = AppStrings.LANGUAGE_SYSTEM }
                LanguageItem(AppStrings.languageFrenchName, "🇫🇷", tempLang == AppStrings.LANGUAGE_FRENCH, useOneUi, bubbleColor, activeColor) { tempLang = AppStrings.LANGUAGE_FRENCH }
                LanguageItem(AppStrings.languageEnglishName, "🇬🇧", tempLang == AppStrings.LANGUAGE_ENGLISH, useOneUi, bubbleColor, activeColor) { tempLang = AppStrings.LANGUAGE_ENGLISH }
                LanguageItem(AppStrings.languagePortugueseName, "🇵🇹", tempLang == AppStrings.LANGUAGE_PORTUGUESE, useOneUi, bubbleColor, activeColor) { tempLang = AppStrings.LANGUAGE_PORTUGUESE }

                Button(
                    onClick = { onSelect(tempLang); onDismiss() },
                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    // ✅ MODIFICATION : On s'assure que le texte du bouton utilise "onPrimary" (généralement blanc) pour contraster avec la couleur vive
                    colors = ButtonDefaults.buttonColors(containerColor = activeColor, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) { Text(AppStrings.validate, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun LanguageItem(name: String, flag: String, isSelected: Boolean, useOneUi: Boolean, bubbleColor: Color, accentColor: Color, onClick: () -> Unit) {
    val activeBg = accentColor.copy(alpha = 0.1f)
    val bgColor = if (isSelected) activeBg else (if (useOneUi) bubbleColor else Color.Transparent)
    val border = if (useOneUi) { if (isSelected) BorderStroke(2.dp, accentColor) else null } else { BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) }

    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().height(64.dp), color = bgColor, border = border, shape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) { Text(flag, fontSize = 26.sp) }
            Spacer(modifier = Modifier.width(16.dp))
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
            if (useOneUi) OneUiRadioButton(isSelected, onClick) else RadioButton(selected = isSelected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = accentColor))
        }
    }
}
