package fr.geotower.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.geotower.R
import fr.geotower.data.outages.OutageTechBreakdown
import fr.geotower.data.outages.OutageTechRow
import fr.geotower.data.outages.OutageTechnology
import fr.geotower.ui.theme.LocalGeoTowerUiStyle

/**
 * Petit tableau « générations hors service par opérateur », posé sous la répartition par opérateur
 * des cartes de pannes.
 *
 * Partagé par les DEUX sources pour qu'elles ne divergent pas, comme le reste du résumé :
 * [OutageDownloadCard] (copie du fichier serveur) et [OutageLocalGenerationControls] (préparation
 * sur l'appareil).
 *
 * Les comptes viennent des prefs, calculés à la récupération ([OutageTechBreakdown]) : la carte
 * n'ouvre jamais le fichier national pour les afficher. Une case « — » signale une génération que
 * l'opérateur ne renseigne pas du tout ; elle ne vaut PAS zéro panne.
 */
@Composable
fun OutageTechBreakdownTable(
    rows: List<OutageTechRow>,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return

    val sizing = LocalGeoTowerUiStyle.current.sizing
    val unpublishedMark = stringResource(R.string.outage_tech_breakdown_unpublished)
    val totals = OutageTechBreakdown.totalsOf(rows)

    fun cellText(count: Int): String =
        if (count == OutageTechBreakdown.UNPUBLISHED) unpublishedMark else count.toString()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(sizing.spacing(4.dp)),
    ) {
        Text(
            text = stringResource(R.string.outage_tech_breakdown_title),
            style = sizing.textStyle(MaterialTheme.typography.bodySmall),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TechBreakdownLine(
            label = "",
            cells = OutageTechnology.entries.map { it.label },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            bold = true,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        rows.forEach { row ->
            TechBreakdownLine(
                label = row.operateur,
                cells = row.counts.map(::cellText),
                color = MaterialTheme.colorScheme.onSurface,
                bold = false,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        TechBreakdownLine(
            label = stringResource(R.string.outage_tech_breakdown_total),
            cells = totals.map(::cellText),
            color = MaterialTheme.colorScheme.primary,
            bold = true,
        )

        // Note affichée seulement si une case est concernée : sinon elle expliquerait un symbole absent.
        if (rows.any { it.hasUnpublished }) {
            Text(
                text = stringResource(R.string.outage_tech_breakdown_unpublished_note, unpublishedMark),
                modifier = Modifier.padding(top = sizing.spacing(2.dp)),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Une ligne du tableau : libellé à gauche, une colonne par génération, toutes de même largeur. */
@Composable
private fun TechBreakdownLine(
    label: String,
    cells: List<String>,
    color: Color,
    bold: Boolean,
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            // Les comptes tiennent en 4 chiffres : la place restante va au nom de l'opérateur,
            // sinon « Bouygues Telecom » se fait rogner dès 360 dp de large.
            modifier = Modifier.weight(2.2f),
            style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        cells.forEach { cell ->
            TechBreakdownCell(text = cell, color = color, bold = bold)
        }
    }
}

@Composable
private fun RowScope.TechBreakdownCell(text: String, color: Color, bold: Boolean) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Text(
        text = text,
        modifier = Modifier.weight(1f),
        style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = color,
        textAlign = TextAlign.End,
        maxLines = 1,
    )
}
