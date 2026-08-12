package fr.geotower.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fr.geotower.R
import fr.geotower.data.api.SignalQuestPhotoReportReasons
import fr.geotower.data.community.PhotoReportHistoryStore
import fr.geotower.data.community.SignalQuestPhotoReporter
import fr.geotower.data.workers.PhotoReportCheckScheduler
import fr.geotower.data.workers.PhotoReportNotifier
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import kotlinx.coroutines.launch

/**
 * Feuille de signalement d'une photo SignalQuest.
 *
 * Le texte ne promet jamais un retrait : l'API range le signalement dans une file de modération,
 * la décision appartient à SignalQuest. [onReported] remonte le message à afficher une fois la
 * requête partie, la feuille se fermant d'elle-même.
 */
@Composable
fun PhotoReportDialog(
    photoId: String,
    siteId: String?,
    photoUrl: String?,
    operatorLabel: String?,
    onDismiss: () -> Unit,
    onReported: (String) -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dialogShape = RoundedCornerShape(sizing.component(18.dp))

    var selectedReason by remember { mutableStateOf(SignalQuestPhotoReportReasons.ordered.first()) }
    var description by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    val txtSent = stringResource(R.string.appstrings_photo_report_sent)
    val txtRateLimited = stringResource(R.string.appstrings_photo_report_rate_limited)
    val txtNotFound = stringResource(R.string.appstrings_photo_report_not_found)
    val txtUnavailable = stringResource(R.string.appstrings_photo_report_unavailable)
    val txtFailed = stringResource(R.string.appstrings_photo_report_failed)

    Dialog(onDismissRequest = { if (!sending) onDismiss() }) {
        Surface(
            shape = dialogShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = sizing.component(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = sizing.component(640.dp))
                    .padding(sizing.spacing(20.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.appstrings_photo_report_title),
                        style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss, enabled = !sending) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.appstrings_close),
                            modifier = Modifier.size(sizing.component(24.dp))
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.appstrings_photo_report_intro),
                        style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

                    SignalQuestPhotoReportReasons.ordered.forEach { reason ->
                        val isSelected = reason == selectedReason
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    enabled = !sending,
                                    onClick = { selectedReason = reason }
                                )
                                .padding(vertical = sizing.spacing(4.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                enabled = !sending,
                                onClick = { selectedReason = reason }
                            )
                            Spacer(modifier = Modifier.width(sizing.spacing(4.dp)))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(photoReportReasonLabel(reason)),
                                    style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(photoReportReasonDescription(reason)),
                                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

                    OutlinedTextField(
                        value = description,
                        onValueChange = { value ->
                            description = value.take(SignalQuestPhotoReportReasons.MAX_DESCRIPTION_LENGTH)
                        },
                        enabled = !sending,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                text = stringResource(R.string.appstrings_photo_report_description_label),
                                style = sizing.textStyle(MaterialTheme.typography.bodySmall)
                            )
                        },
                        supportingText = {
                            Text(
                                text = "${description.length} / ${SignalQuestPhotoReportReasons.MAX_DESCRIPTION_LENGTH}",
                                style = sizing.textStyle(MaterialTheme.typography.labelSmall)
                            )
                        },
                        minLines = 3,
                        maxLines = 6
                    )
                }

                Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (sending) {
                        CircularProgressIndicator(modifier = Modifier.size(sizing.component(20.dp)))
                        Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
                    }
                    TextButton(onClick = onDismiss, enabled = !sending) {
                        Text(
                            text = stringResource(R.string.appstrings_cancel),
                            style = sizing.textStyle(MaterialTheme.typography.labelLarge)
                        )
                    }
                    Spacer(modifier = Modifier.width(sizing.spacing(4.dp)))
                    TextButton(
                        enabled = !sending,
                        onClick = {
                            sending = true
                            scope.launch {
                                val result = SignalQuestPhotoReporter.report(
                                    photoId = photoId,
                                    reason = selectedReason,
                                    description = description
                                )
                                sending = false
                                if (result == SignalQuestPhotoReporter.Result.Sent) {
                                    // Le suivi n'est possible que si l'on sait où re-lister la photo.
                                    if (!siteId.isNullOrBlank()) {
                                        PhotoReportHistoryStore.add(
                                            context = context,
                                            photoId = photoId,
                                            siteId = siteId,
                                            reason = selectedReason,
                                            description = description.trim().takeIf { it.isNotEmpty() },
                                            photoUrl = photoUrl,
                                            operatorLabel = operatorLabel
                                        )
                                        PhotoReportCheckScheduler.reconcile(context)
                                    }
                                    PhotoReportNotifier.notifySent(context)
                                }
                                onReported(
                                    when (result) {
                                        SignalQuestPhotoReporter.Result.Sent -> txtSent
                                        SignalQuestPhotoReporter.Result.RateLimited -> txtRateLimited
                                        SignalQuestPhotoReporter.Result.NotFound -> txtNotFound
                                        SignalQuestPhotoReporter.Result.Unavailable -> txtUnavailable
                                        SignalQuestPhotoReporter.Result.Failed -> txtFailed
                                    }
                                )
                                onDismiss()
                            }
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.appstrings_photo_report_submit),
                            style = sizing.textStyle(MaterialTheme.typography.labelLarge),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun photoReportReasonLabel(reason: String): Int = when (reason) {
    SignalQuestPhotoReportReasons.WRONG_LOCATION -> R.string.appstrings_photo_report_reason_wrong_location
    SignalQuestPhotoReportReasons.QUALITY -> R.string.appstrings_photo_report_reason_quality
    SignalQuestPhotoReportReasons.INAPPROPRIATE -> R.string.appstrings_photo_report_reason_inappropriate
    SignalQuestPhotoReportReasons.COPYRIGHT -> R.string.appstrings_photo_report_reason_copyright
    SignalQuestPhotoReportReasons.SPAM -> R.string.appstrings_photo_report_reason_spam
    else -> R.string.appstrings_photo_report_reason_other
}

private fun photoReportReasonDescription(reason: String): Int = when (reason) {
    SignalQuestPhotoReportReasons.WRONG_LOCATION -> R.string.appstrings_photo_report_reason_wrong_location_desc
    SignalQuestPhotoReportReasons.QUALITY -> R.string.appstrings_photo_report_reason_quality_desc
    SignalQuestPhotoReportReasons.INAPPROPRIATE -> R.string.appstrings_photo_report_reason_inappropriate_desc
    SignalQuestPhotoReportReasons.COPYRIGHT -> R.string.appstrings_photo_report_reason_copyright_desc
    SignalQuestPhotoReportReasons.SPAM -> R.string.appstrings_photo_report_reason_spam_desc
    else -> R.string.appstrings_photo_report_reason_other_desc
}
