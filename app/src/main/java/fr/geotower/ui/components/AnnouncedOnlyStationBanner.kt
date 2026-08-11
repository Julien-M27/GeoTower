package fr.geotower.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geotower.R
import fr.geotower.ui.theme.LocalGeoTowerUiSizing

/**
 * Bandeau des stations que seul le releve hebdomadaire de l'ANFR connait (cf.
 * `fr.geotower.utils.isAnnouncedOnlyStation`).
 *
 * L'export mensuel de l'ANFR a jusqu'a cinq semaines de retard sur l'observatoire hebdomadaire :
 * entre les deux, une station toute neuve remonte avec ses seules technologies, sans une bande, un
 * azimut, une hauteur, une date ni un support. La fiche parait alors cassee ; ce bandeau dit d'ou
 * vient le vide et quand il se comblera, en tete de page pour etre lu avant les blocs a moitie vides.
 *
 * Volontairement hors des blocs personnalisables : ce n'est pas un contenu de fiche mais l'etat de la
 * source, et il ne s'affiche de toute facon que sur une poignee de stations a la fois.
 */
@Composable
fun AnnouncedOnlyStationBanner(
    blockShape: Shape,
    modifier: Modifier = Modifier
) {
    val sizing = LocalGeoTowerUiSizing.current
    val contentColor = MaterialTheme.colorScheme.onTertiaryContainer

    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = blockShape,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(sizing.spacing(16.dp))) {
            Icon(
                imageVector = Icons.Outlined.HourglassTop,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(sizing.component(24.dp))
            )
            Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.appstrings_station_weekly_only_title),
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    style = sizing.textStyle(MaterialTheme.typography.titleSmall)
                )
                Spacer(modifier = Modifier.height(sizing.spacing(4.dp)))
                Text(
                    text = stringResource(R.string.appstrings_station_weekly_only_desc),
                    color = contentColor,
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall)
                )
            }
        }
    }
}
