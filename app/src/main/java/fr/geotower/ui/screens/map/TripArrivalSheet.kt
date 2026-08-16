package fr.geotower.ui.screens.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.R
import fr.geotower.data.upload.SupportSharedPhotoUploadOperator
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.formatSiteDistanceMeters

/**
 * Un support proposé à l'arrivée sur une étape, prêt pour un envoi : son identifiant d'envoi, la
 * position à transporter, et les opérateurs qui acceptent des photos.
 */
data class TripArrivalSupport(
    val supportId: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double,
    val address: String,
    val operators: List<SupportSharedPhotoUploadOperator>
)

/**
 * Ce qui s'ouvre en arrivant sur une étape d'une tournée.
 *
 * C'est la raison d'être du planificateur : on ne se déplace pas jusqu'à un pylône pour le cocher,
 * on y va pour le photographier. La feuille arrive donc d'elle-même, sans rien exiger — elle se
 * ferme d'un geste si l'on ne veut rien envoyer.
 *
 * Le choix du support est explicite dès qu'il y en a plusieurs : deux pylônes voisins ne se
 * distinguent pas depuis une carte, et envoyer les photos de l'un sous l'identité de l'autre
 * salirait la base de tout le monde.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TripArrivalSheet(
    stepNumber: Int,
    stepLabel: String,
    supports: List<TripArrivalSupport>,
    loading: Boolean,
    note: String,
    photosSentCount: Int,
    distanceUnit: Int,
    canUploadPhotos: Boolean,
    onNoteChange: (String) -> Unit,
    onSendPhotos: (TripArrivalSupport, List<SupportSharedPhotoUploadOperator>) -> Unit,
    onDismiss: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedSupportId by remember(supports) {
        mutableStateOf(supports.firstOrNull()?.supportId)
    }
    val selectedSupport = supports.firstOrNull { it.supportId == selectedSupportId }
        ?: supports.firstOrNull()

    // Un seul opérateur sur le pylône : le cocher d'office. Sur un site mutualisé au contraire, le
    // choix revient à l'utilisateur — il n'a pas forcément photographié l'antenne de tout le monde.
    var selectedOperatorKeys by remember(selectedSupport?.supportId) {
        mutableStateOf(
            selectedSupport?.operators
                ?.takeIf { it.size == 1 }
                ?.map { it.key }
                ?.toSet()
                .orEmpty()
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = sizing.spacing(20.dp))
                .padding(bottom = sizing.spacing(16.dp)),
            verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
        ) {
            Text(
                text = stringResource(R.string.trips_arrival_title, stepNumber),
                fontSize = sizing.text(18.sp),
                fontWeight = FontWeight.SemiBold
            )
            if (stepLabel.isNotBlank()) {
                Text(
                    text = stepLabel,
                    fontSize = sizing.text(14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (photosSentCount > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.trips_arrival_already_sent,
                        photosSentCount,
                        photosSentCount
                    ),
                    fontSize = sizing.text(13.sp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider()

            when {
                !canUploadPhotos -> Text(
                    text = stringResource(R.string.trips_arrival_upload_unavailable),
                    fontSize = sizing.text(13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(sizing.component(18.dp)))
                    Text(
                        text = stringResource(R.string.trips_arrival_searching),
                        fontSize = sizing.text(13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                supports.isEmpty() -> Text(
                    text = stringResource(R.string.trips_arrival_no_site),
                    fontSize = sizing.text(13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> {
                    if (supports.size > 1) {
                        Text(
                            text = stringResource(R.string.trips_arrival_choose_site),
                            fontSize = sizing.text(13.sp),
                            fontWeight = FontWeight.Medium
                        )
                        supports.forEach { support ->
                            SupportChoiceRow(
                                support = support,
                                distanceUnit = distanceUnit,
                                selected = support.supportId == selectedSupport?.supportId,
                                onSelect = { selectedSupportId = support.supportId }
                            )
                        }
                    }

                    val operators = selectedSupport?.operators.orEmpty()
                    if (operators.isEmpty()) {
                        Text(
                            text = stringResource(R.string.trips_arrival_no_operator),
                            fontSize = sizing.text(13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.trips_arrival_operators),
                            fontSize = sizing.text(13.sp),
                            fontWeight = FontWeight.Medium
                        )
                        operators.forEach { operator ->
                            OperatorCheckRow(
                                label = operator.label,
                                checked = operator.key in selectedOperatorKeys,
                                onToggle = {
                                    selectedOperatorKeys = if (operator.key in selectedOperatorKeys) {
                                        selectedOperatorKeys - operator.key
                                    } else {
                                        selectedOperatorKeys + operator.key
                                    }
                                }
                            )
                        }
                        Button(
                            onClick = {
                                val support = selectedSupport ?: return@Button
                                val targets = support.operators
                                    .filter { it.key in selectedOperatorKeys }
                                if (targets.isNotEmpty()) onSendPhotos(support, targets)
                            },
                            enabled = selectedOperatorKeys.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = null,
                                modifier = Modifier.size(sizing.component(18.dp))
                            )
                            Text(
                                text = stringResource(R.string.trips_arrival_send_photos),
                                fontSize = sizing.text(14.sp),
                                modifier = Modifier.padding(start = sizing.spacing(8.dp))
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // La note vit dans l'étape, pas dans un brouillon : elle est écrite à chaque frappe, de
            // sorte qu'un balayage vers le bas ne la perde pas.
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                label = { Text(stringResource(R.string.trips_arrival_note_label)) },
                placeholder = { Text(stringResource(R.string.trips_arrival_note_placeholder)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.appstrings_close), fontSize = sizing.text(14.sp))
            }
        }
    }

    // Un support qui disparaît (résolution terminée, liste rebâtie) ne doit pas laisser la sélection
    // sur un identifiant qui n'existe plus.
    LaunchedEffect(supports) {
        if (supports.none { it.supportId == selectedSupportId }) {
            selectedSupportId = supports.firstOrNull()?.supportId
        }
    }
}

@Composable
private fun SupportChoiceRow(
    support: TripArrivalSupport,
    distanceUnit: Int,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = sizing.spacing(4.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.padding(start = sizing.spacing(4.dp))) {
            Text(
                text = support.supportId,
                fontSize = sizing.text(14.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = listOfNotNull(
                formatSiteDistanceMeters(support.distanceMeters, distanceUnit),
                support.address.takeIf { it.isNotBlank() },
                support.operators.joinToString(", ") { it.label }.takeIf { it.isNotBlank() }
            ).joinToString(" - ")
            Text(
                text = subtitle,
                fontSize = sizing.text(12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OperatorCheckRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = sizing.spacing(2.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(
            text = label,
            fontSize = sizing.text(14.sp),
            modifier = Modifier.padding(start = sizing.spacing(4.dp))
        )
    }
}
