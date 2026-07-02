package fr.geotower.ui.screens.terms

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import fr.geotower.R
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.theme.LocalGeoTowerUiStyle

/**
 * Conditions d'utilisation et de confidentialité (RGPD).
 *
 * Le texte intégral est stocké dans des ressources `raw` localisées (res/raw, res/raw-fr, ...).
 * Comme [fr.geotower.utils.GeoTowerLocaleProvider] remplace LocalContext par un contexte
 * configuré pour la langue de l'application, `openRawResource` suit automatiquement la langue
 * choisie dans les réglages, exactement comme `stringResource`.
 *
 * Mise en page : un balisage léger est interprété par [parseTermsDocument] :
 *  - `# `   titre de section
 *  - `## `  sous-titre
 *  - `- `   puce
 *  - `1. `  étape numérotée
 *  - `> `   encadré d'information (fusionne les lignes consécutives)
 *  - `~ `   ligne discrète (date de mise à jour)
 *  - `**gras**` à l'intérieur du texte
 */
@Composable
fun TermsScreen(navController: NavController) {
    val context = LocalContext.current
    val uiStyle = LocalGeoTowerUiStyle.current
    val scrollState = rememberScrollState()
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "about")

    BackHandler(enabled = !safeBackNavigation.isLocked) {
        safeBackNavigation.navigateBack()
    }

    // Re-parsé lorsque le contexte change (donc lorsque la langue de l'app change).
    val blocks = remember(context) { parseTermsDocument(readRawText(context, R.raw.terms)) }

    Scaffold(
        containerColor = uiStyle.backgroundColor,
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.appstrings_terms_title),
                onBack = { safeBackNavigation.navigateBack() },
                backEnabled = !safeBackNavigation.isLocked
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(uiStyle.backgroundColor)
                .padding(innerPadding)
                .geoTowerFadingEdge(scrollState, fadeHeight = uiStyle.sizing.component(72.dp))
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(
                    horizontal = uiStyle.sizing.spacing(20.dp),
                    vertical = uiStyle.sizing.spacing(8.dp)
                )
        ) {
            blocks.forEach { block -> TermsBlockView(block) }
            Spacer(modifier = Modifier.height(uiStyle.sizing.spacing(48.dp)))
        }
    }
}

// ============================================================
// RENDU DES BLOCS
// ============================================================

@Composable
private fun TermsBlockView(block: TermsBlock) {
    when (block) {
        is TermsBlock.Heading1 -> Text(
            text = block.text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 6.dp)
        )

        is TermsBlock.Heading2 -> Text(
            text = block.text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 2.dp)
        )

        is TermsBlock.Paragraph -> Text(
            text = annotatedTerms(block.text),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
        )

        is TermsBlock.Bullet -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 3.dp, bottom = 3.dp)
        ) {
            Text(
                text = "•",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(18.dp)
            )
            Text(
                text = annotatedTerms(block.text),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }

        is TermsBlock.Step -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 3.dp, bottom = 3.dp)
        ) {
            Text(
                text = "${block.number}.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(22.dp)
            )
            Text(
                text = annotatedTerms(block.text),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }

        is TermsBlock.Callout -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    shape = LocalGeoTowerUiStyle.current.smallItemShape
                )
                .padding(14.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 0.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = annotatedTerms(block.text),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
        }

        is TermsBlock.Muted -> Text(
            text = block.text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp)
        )
    }
}

// ============================================================
// MODÈLE & PARSING
// ============================================================

private sealed interface TermsBlock {
    data class Heading1(val text: String) : TermsBlock
    data class Heading2(val text: String) : TermsBlock
    data class Paragraph(val text: String) : TermsBlock
    data class Bullet(val text: String) : TermsBlock
    data class Step(val number: String, val text: String) : TermsBlock
    data class Callout(val text: String) : TermsBlock
    data class Muted(val text: String) : TermsBlock
}

private val STEP_REGEX = Regex("""^(\d+)\.\s+(.*)$""")

private fun readRawText(context: Context, resId: Int): String =
    runCatching {
        context.resources.openRawResource(resId).bufferedReader().use { it.readText() }
    }.getOrDefault("")

private fun parseTermsDocument(raw: String): List<TermsBlock> {
    val blocks = mutableListOf<TermsBlock>()
    val calloutBuffer = StringBuilder()

    fun flushCallout() {
        if (calloutBuffer.isNotEmpty()) {
            blocks.add(TermsBlock.Callout(calloutBuffer.toString().trim()))
            calloutBuffer.clear()
        }
    }

    raw.lines().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.startsWith("> ")) {
            if (calloutBuffer.isNotEmpty()) calloutBuffer.append(' ')
            calloutBuffer.append(line.removePrefix("> ").trim())
            return@forEach
        }
        flushCallout()
        when {
            line.isBlank() -> Unit // espacement géré par le padding des blocs
            line.startsWith("## ") -> blocks.add(TermsBlock.Heading2(line.removePrefix("## ").trim()))
            line.startsWith("# ") -> blocks.add(TermsBlock.Heading1(line.removePrefix("# ").trim()))
            line.startsWith("~ ") -> blocks.add(TermsBlock.Muted(line.removePrefix("~ ").trim()))
            line.startsWith("- ") -> blocks.add(TermsBlock.Bullet(line.removePrefix("- ").trim()))
            else -> {
                val step = STEP_REGEX.matchEntire(line)
                if (step != null) {
                    blocks.add(TermsBlock.Step(step.groupValues[1], step.groupValues[2].trim()))
                } else {
                    blocks.add(TermsBlock.Paragraph(line))
                }
            }
        }
    }
    flushCallout()
    return blocks
}

/** Interprète les marqueurs `**gras**` au sein d'une ligne. */
private fun annotatedTerms(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        val start = text.indexOf("**", index)
        if (start < 0) {
            append(text.substring(index))
            break
        }
        append(text.substring(index, start))
        val end = text.indexOf("**", start + 2)
        if (end < 0) {
            append(text.substring(start))
            break
        }
        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        append(text.substring(start + 2, end))
        pop()
        index = end + 2
    }
}
