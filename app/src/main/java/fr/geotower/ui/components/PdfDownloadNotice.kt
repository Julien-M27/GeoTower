package fr.geotower.ui.components

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geotower.R
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppLogger

/** Rapport PDF qui vient d'être enregistré dans Téléchargements/GeoTower. */
data class PdfDownloadResult(
    val uri: Uri,
    val fileName: String
)

/**
 * Dernier rapport PDF téléchargé. [PdfDownloadResultDialog] est monté une seule fois dans
 * MainActivity : n'importe quel export peut donc proposer d'ouvrir le fichier sans que l'écran
 * appelant ait à porter l'état de la boîte de dialogue.
 */
object PdfDownloadNotice {
    val lastDownload = mutableStateOf<PdfDownloadResult?>(null)

    fun show(uri: Uri, fileName: String) {
        lastDownload.value = PdfDownloadResult(uri = uri, fileName = fileName)
    }

    fun dismiss() {
        lastDownload.value = null
    }
}

/** Propose d'ouvrir le PDF tout juste téléchargé, ou son dossier dans les Téléchargements. */
@Composable
fun PdfDownloadResultDialog() {
    val result = PdfDownloadNotice.lastDownload.value ?: return
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val context = LocalContext.current
    val txtNoViewer = stringResource(R.string.appstrings_pdf_open_error)

    AlertDialog(
        onDismissRequest = { PdfDownloadNotice.dismiss() },
        icon = {
            Icon(
                Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(sizing.component(28.dp))
            )
        },
        title = {
            Text(
                text = stringResource(R.string.appstrings_pdf_downloaded),
                style = sizing.textStyle(MaterialTheme.typography.titleLarge),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = stringResource(R.string.appstrings_pdf_downloaded_desc, result.fileName),
                style = sizing.textStyle(MaterialTheme.typography.bodyMedium)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    PdfDownloadNotice.dismiss()
                    if (!openDownloadedPdf(context, result.uri)) {
                        Toast.makeText(context, txtNoViewer, Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.appstrings_open),
                    style = sizing.textStyle(MaterialTheme.typography.labelLarge),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        // Deux actions seulement : au-delà, la rangée de boutons Material débordait en 120 % d'échelle.
        // Le dialogue se ferme par un appui à côté ou par Retour.
        dismissButton = {
            TextButton(
                onClick = {
                    PdfDownloadNotice.dismiss()
                    if (!openDownloadsFolder(context, result.uri)) {
                        Toast.makeText(context, txtNoViewer, Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(sizing.component(18.dp))
                )
                Text(
                    text = stringResource(R.string.appstrings_pdf_open_folder),
                    style = sizing.textStyle(MaterialTheme.typography.labelLarge),
                    modifier = Modifier.padding(start = sizing.spacing(6.dp))
                )
            }
        }
    )
}

/** Ouvre le PDF dans le lecteur du téléphone (Android affiche lui-même le choix s'il y en a plusieurs). */
private fun openDownloadedPdf(context: Context, uri: Uri): Boolean {
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        // Contexte localisé (LocaleProvider) : ce n'est pas une Activity, NEW_TASK est obligatoire.
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return context.startActivitySafely(view)
}

/** Ouvre les Téléchargements du système, où le dossier GeoTower contient le rapport. */
private fun openDownloadsFolder(context: Context, uri: Uri): Boolean {
    val downloads = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (context.startActivitySafely(downloads)) return true

    // Repli : certains constructeurs n'exposent pas l'écran Téléchargements, on ouvre le fichier.
    return openDownloadedPdf(context, uri)
}

private fun Context.startActivitySafely(intent: Intent): Boolean {
    return try {
        startActivity(intent)
        true
    } catch (e: Exception) {
        AppLogger.w("PdfDownloadNotice", "Unable to start ${intent.action}", e)
        false
    }
}
