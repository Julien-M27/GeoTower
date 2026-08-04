package fr.geotower.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geotower.R
import fr.geotower.data.outages.OutageLocalConfig
import fr.geotower.data.workers.OutageBackgroundScheduler
import fr.geotower.ui.components.CustomSliderCard
import fr.geotower.ui.components.OutageLocalGenerationControls
import fr.geotower.ui.theme.LocalGeoTowerUiStyle

/**
 * Réglages d'exécution de la récupération des pannes sur l'appareil, affichés **sous la carte
 * « Sites en panne »** (Réglages ▸ Base de données) dès que le cran de traitement local les
 * récupère ici.
 *
 * Il ne porte NI le choix de la source — c'est le niveau de traitement local qui en décide
 * ([fr.geotower.utils.AppConfig.outagesLocal]) — NI l'action : le bouton « Mettre à jour
 * maintenant », le résumé de la dernière récupération et la répartition par opérateur
 * ([OutageLocalGenerationControls]) sont portés par la carte, juste au-dessus. Les répéter ici
 * affichait deux fois le même bouton et le même décompte à quelques centimètres d'écart.
 *
 * Restent donc les seuls réglages : fréquence (durée de validité du cache, et période en
 * arrière-plan) et récupération en arrière-plan.
 */
@Composable
fun OutageLocalControls(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val useOneUi = uiStyle.useOneUi
    val shape = uiStyle.cardShape
    val border = uiStyle.cardBorder
    val bubbleColor = uiStyle.bubbleColor

    val config = remember { OutageLocalConfig(context) }
    var frequency by remember { mutableIntStateOf(config.frequencyHours) }
    var background by remember { mutableStateOf(config.backgroundEnabled) }

    val frequencySteps = remember {
        (OutageLocalConfig.MIN_FREQUENCY_HOURS..OutageLocalConfig.MAX_FREQUENCY_HOURS).toList()
    }
    val frequencyLabels = frequencySteps.map { context.getString(R.string.outage_source_frequency_unit, it) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp)),
    ) {
        // Titre des seuls RÉGLAGES : « Source des pannes » nommait un choix qui ne se fait plus
        // ici (c'est le cran), et la carte au-dessus explique déjà d'où viennent les données.
        Text(
            text = stringResource(R.string.outage_local_settings_title),
            style = sizing.textStyle(MaterialTheme.typography.titleMedium),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        CustomSliderCard(
            title = stringResource(R.string.outage_source_frequency_title),
            currentValue = frequency,
            steps = frequencySteps,
            labels = frequencyLabels,
            onValueChange = {
                frequency = it
                config.frequencyHours = it
                OutageBackgroundScheduler.reconcile(context)
            },
            shape = shape, border = border, bubbleColor = bubbleColor, useOneUi = useOneUi,
            footerText = stringResource(R.string.outage_source_frequency_footer),
        )

        PreferenceSwitchCard(
            title = stringResource(R.string.outage_source_background_title),
            desc = stringResource(R.string.outage_source_background_desc),
            checked = background,
            onCheckedChange = {
                background = it
                config.backgroundEnabled = it
                OutageBackgroundScheduler.reconcile(context)
            },
            shape = shape, border = border, bubbleColor = bubbleColor, useOneUi = useOneUi,
        )
    }
}
