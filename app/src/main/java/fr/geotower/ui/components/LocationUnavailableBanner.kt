package fr.geotower.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.R
import fr.geotower.utils.LocationReadiness

/**
 * Bandeau d'alerte « localisation indisponible » — partagé par l'accueil et la boussole.
 *
 * Ne s'affiche que si la localisation n'est pas [LocationReadiness.Ready]. Le message et le libellé
 * du bouton s'adaptent à la cause :
 * - permission coupée → « Autoriser » (demande la permission) ;
 * - services (GPS) éteints → « Activer » (ouvre les réglages de localisation).
 */
@Composable
fun LocationUnavailableBanner(
    readiness: LocationReadiness,
    onFixClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = readiness != LocationReadiness.Ready,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier.fillMaxWidth().padding(top = 16.dp, start = 16.dp, end = 16.dp)
    ) {
        val descRes = if (readiness == LocationReadiness.ServicesOff) {
            R.string.appstrings_location_services_off_banner_desc
        } else {
            R.string.appstrings_location_disabled_banner_desc
        }
        val actionRes = if (readiness == LocationReadiness.ServicesOff) {
            R.string.appstrings_location_services_enable
        } else {
            R.string.common_authorize
        }
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.appstrings_location_disabled_title),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(descRes),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onFixClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = stringResource(actionRes),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * Alerte centrale « localisation indisponible » — icône + titre + description + bouton, centrée.
 *
 * Utilisée par l'écran « Antennes à proximité » et la boussole : elle remplace le contenu quand la
 * localisation est coupée. Le message et le bouton s'adaptent à la cause (permission vs GPS éteint).
 */
@Composable
fun LocationUnavailableMessage(
    readiness: LocationReadiness,
    onFixClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val descRes = if (readiness == LocationReadiness.ServicesOff) {
        R.string.appstrings_location_services_off_nearby_desc
    } else {
        R.string.appstrings_location_disabled_nearby_desc
    }
    val actionRes = if (readiness == LocationReadiness.ServicesOff) {
        R.string.appstrings_location_services_enable
    } else {
        R.string.common_authorize
    }
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.LocationOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.appstrings_location_disabled_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(descRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onFixClick) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(actionRes), fontWeight = FontWeight.Bold)
        }
    }
}
