package fr.geotower.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.utils.AppConfig
import fr.geotower.utils.DeviceProfile

internal const val GEO_TOWER_SIZE_SMALL_SCALE = 0.85f
internal const val GEO_TOWER_SIZE_NORMAL_SCALE = 0.925f
internal const val GEO_TOWER_SIZE_LARGE_SCALE = 1f

// --- Dimensionnement adaptatif de l'UI ---
// L'ancien palier "normal" (x0.925) devient la reference : 100% du reglage utilisateur, sur un
// ecran de reference. La taille finale = base(dp) x facteurEcran x (reglageUtilisateur / 100).

// L'echelle de reference : le rendu "normal" historique correspond desormais a 100%.
internal const val GEO_TOWER_UI_BASELINE_SCALE = GEO_TOWER_SIZE_NORMAL_SCALE

// Largeur d'ecran (dp) a laquelle le facteur d'ecran vaut exactement 1.0.
internal const val GEO_TOWER_UI_REFERENCE_WIDTH_DP = 400f
// Bornes du facteur d'ecran : evite une UI minuscule sur tres petit ecran et geante sur tablette.
internal const val GEO_TOWER_UI_SCREEN_FACTOR_MIN = 0.9f
internal const val GEO_TOWER_UI_SCREEN_FACTOR_MAX = 1.15f
// Au-dela de ce seuil (plus petite dimension, en dp) on est sur un grand ecran : Fold deplie ou
// tablette. Le facteur y est fige a 1.0 : un grand ecran doit montrer PLUS de contenu, pas du
// contenu PLUS GROS. En pratique la bande 460..600.dp est vide (les telephones plafonnent vers
// 450.dp de plus petite largeur, le palier tablette commence a 600.dp), la marche est donc
// invisible sur les appareils reels.
internal const val GEO_TOWER_UI_LARGE_SCREEN_MIN_DIMENSION_DP = 600
internal const val GEO_TOWER_UI_LARGE_SCREEN_FACTOR = 1f

// Reglage utilisateur, en pourcentage (100 = rendu de reference).
internal const val GEO_TOWER_UI_SCALE_PERCENT_MIN = 80
internal const val GEO_TOWER_UI_SCALE_PERCENT_MAX = 120
internal const val GEO_TOWER_UI_SCALE_PERCENT_DEFAULT = 100

/**
 * Facteur d'echelle derive de la taille d'ecran (borne). Vaut 1.0 a la largeur de reference.
 *
 * On raisonne sur la PLUS PETITE dimension de la fenetre, pas sur la largeur courante : sinon un
 * telephone bascule en paysage franchit d'un coup la largeur de reference et voit toute son UI
 * grossir a la rotation.
 *
 * Sur grand ecran le facteur est fige a 1.0. Le rapport largeur/400 saturait des 460.dp : un Fold
 * deplie (700 a 900.dp) etait donc colle au plafond de 1.15 et rendait toute l'application ~15%
 * plus grosse que sur un telephone, d'ou l'accueil, les reglages et « A propos » « trop zoomes ».
 */
fun geoTowerScreenFactor(screenWidthDp: Int, screenHeightDp: Int): Float {
    val smallestDimensionDp = minOf(screenWidthDp, screenHeightDp)
    if (smallestDimensionDp >= GEO_TOWER_UI_LARGE_SCREEN_MIN_DIMENSION_DP) {
        return GEO_TOWER_UI_LARGE_SCREEN_FACTOR
    }
    return (smallestDimensionDp / GEO_TOWER_UI_REFERENCE_WIDTH_DP)
        .coerceIn(GEO_TOWER_UI_SCREEN_FACTOR_MIN, GEO_TOWER_UI_SCREEN_FACTOR_MAX)
}

/** Echelle effective appliquee a toute l'UI = reference x facteur ecran x reglage utilisateur. */
fun geoTowerEffectiveScale(scalePercent: Int, screenWidthDp: Int, screenHeightDp: Int): Float =
    GEO_TOWER_UI_BASELINE_SCALE *
        geoTowerScreenFactor(screenWidthDp, screenHeightDp) *
        (scalePercent.coerceIn(GEO_TOWER_UI_SCALE_PERCENT_MIN, GEO_TOWER_UI_SCALE_PERCENT_MAX) / 100f)

@Immutable
data class GeoTowerUiSizing(
    val scalePercent: Int,
    val componentScale: Float,
    val spacingScale: Float,
    val textScale: Float
) {
    fun component(value: Dp): Dp = (value.value * componentScale).dp
    fun spacing(value: Dp): Dp = (value.value * spacingScale).dp
    fun text(value: TextUnit): TextUnit =
        if (value == TextUnit.Unspecified) value else (value.value * textScale).sp

    fun textStyle(value: TextStyle): TextStyle =
        if (value.fontSize == TextUnit.Unspecified) value else value.copy(fontSize = text(value.fontSize))
}

@Immutable
data class GeoTowerUiStyle(
    val useOneUi: Boolean,
    val isSamsungDevice: Boolean,
    val isDark: Boolean,
    val isOled: Boolean,
    val backgroundColor: Color,
    val bubbleColor: Color,
    val cardColor: Color,
    val secondaryCardColor: Color,
    val cardShape: Shape,
    val blockShape: Shape,
    val actionButtonShape: Shape,
    val smallItemShape: Shape,
    val sizing: GeoTowerUiSizing,
    val cardBorder: BorderStroke?,
    val subtleBorder: BorderStroke?
)

val LocalGeoTowerUiStyle = staticCompositionLocalOf<GeoTowerUiStyle> {
    error("LocalGeoTowerUiStyle is not provided")
}

/**
 * Echelle neutre (x1) : les valeurs passees a [GeoTowerUiSizing] ressortent inchangees.
 * Sert aux rendus hors ecran (images de partage, PDF), dont les dimensions ne doivent PAS
 * suivre le reglage de taille d'interface pour que l'export reste deterministe.
 *
 * Doit rester declare AVANT [LocalGeoTowerUiSizing] : les proprietes top-level d'un meme fichier
 * sont initialisees dans l'ordre de declaration, sinon le defaut du CompositionLocal serait null.
 */
val GeoTowerUnscaledSizing = GeoTowerUiSizing(
    scalePercent = GEO_TOWER_UI_SCALE_PERCENT_DEFAULT,
    componentScale = 1f,
    spacingScale = 1f,
    textScale = 1f
)

/**
 * Echelle seule, avec un defaut **neutre** (x1) au lieu d'une erreur.
 *
 * A utiliser dans les composables partages entre l'ecran et un rendu hors ecran (image de partage,
 * PDF) : ces rendus se font dans un `ComposeView` cree a la main, dont le `setContent` ne fournit
 * pas [LocalGeoTowerUiStyle]. Lire le style la-bas ferait planter l'export ; lire ce local-ci
 * renvoie l'echelle neutre, donc le composant scale dans l'app et garde sa taille a l'export.
 */
val LocalGeoTowerUiSizing = staticCompositionLocalOf { GeoTowerUnscaledSizing }

@Composable
fun rememberGeoTowerUiStyle(): GeoTowerUiStyle {
    val themeMode by AppConfig.themeMode
    val isOled by AppConfig.isOledMode
    val uiMode by AppConfig.uiMode
    val scalePercent by AppConfig.uiScalePercent
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val isDark = themeMode == 2 || (themeMode == 0 && isSystemInDarkTheme())
    val useOneUi = uiMode.usesOneUi()
    val backgroundColor = if (isDark && isOled) Color.Black else MaterialTheme.colorScheme.background
    val bubbleColor = if (useOneUi) {
        if (isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    } else {
        Color.Transparent
    }
    val cardColor = if (useOneUi) {
        bubbleColor
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val secondaryCardColor = if (useOneUi) {
        if (isDark) Color(0xFF2B2B2B) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val cardBorder = if (useOneUi) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    val subtleBorder = if (useOneUi) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
    val sizing = geoTowerUiSizing(scalePercent, screenWidthDp, screenHeightDp)

    return GeoTowerUiStyle(
        useOneUi = useOneUi,
        isSamsungDevice = DeviceProfile.isSamsungDevice,
        isDark = isDark,
        isOled = isOled,
        backgroundColor = backgroundColor,
        bubbleColor = bubbleColor,
        cardColor = cardColor,
        secondaryCardColor = secondaryCardColor,
        cardShape = if (useOneUi) RoundedCornerShape(28.dp) else RoundedCornerShape(12.dp),
        blockShape = if (useOneUi) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp),
        actionButtonShape = if (useOneUi) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp),
        smallItemShape = if (useOneUi) RoundedCornerShape(16.dp) else RoundedCornerShape(12.dp),
        sizing = sizing,
        cardBorder = cardBorder,
        subtleBorder = subtleBorder
    )
}

private fun geoTowerUiSizing(scalePercent: Int, screenWidthDp: Int, screenHeightDp: Int): GeoTowerUiSizing {
    val scale = geoTowerEffectiveScale(scalePercent, screenWidthDp, screenHeightDp)
    return GeoTowerUiSizing(
        scalePercent = scalePercent,
        componentScale = scale,
        spacingScale = scale,
        textScale = scale
    )
}

@Composable
fun GeoTowerUiStyleProvider(content: @Composable () -> Unit) {
    val style = rememberGeoTowerUiStyle()
    CompositionLocalProvider(
        LocalGeoTowerUiStyle provides style,
        LocalGeoTowerUiSizing provides style.sizing,
        content = content
    )
}

/**
 * Fournit [LocalGeoTowerUiStyle] dans une composition detachee (ComposeView hors ecran, capture
 * bitmap / PDF). Sans ca, tout composable partage avec l'app qui lit ce CompositionLocal plante
 * (« LocalGeoTowerUiStyle is not provided »). L'echelle y est neutre : l'image exportee garde
 * toujours la meme taille, quel que soit le slider.
 */
@Composable
fun GeoTowerExportUiStyleProvider(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalGeoTowerUiStyle provides rememberGeoTowerUiStyle().copy(sizing = GeoTowerUnscaledSizing),
        content = content
    )
}
