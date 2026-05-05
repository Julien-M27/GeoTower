package fr.geotower.ui.components

import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.R
import fr.geotower.utils.AppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DatabaseDownloadCard(
    useOneUi: Boolean,
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    title: String? = null,
    onSafeClick: (() -> Unit) -> Unit = { it() }
) {
    val context = LocalContext.current
    val workManager = remember { androidx.work.WorkManager.getInstance(context) }
    val workInfos by workManager.getWorkInfosForUniqueWorkFlow("db_download").collectAsState(initial = emptyList())
    val currentWork = workInfos.firstOrNull()

    val isSyncing = currentWork?.state == androidx.work.WorkInfo.State.RUNNING || currentWork?.state == androidx.work.WorkInfo.State.ENQUEUED
    val downloadProgress = currentWork?.progress?.getInt("progress", 0)?.div(100f) ?: 0f

    val txtSearching = AppStrings.get("Recherche...", "Searching...", "Buscando...")
    val txtUnknown = AppStrings.get("Inconnue", "Unknown", "Desconocida")
    val txtNoDb = AppStrings.get("Aucune base installée", "No database installed", "Ninguna base instalada")
    val txtOldDb = AppStrings.get("Ancienne version (Non datée)", "Old version (Undated)", "Versión antigua (Sin fecha)")
    val txtLatestDb = AppStrings.get("Dernière base disponible :", "Latest database:", "Última base disp.:")
    val txtDownloadedDb = AppStrings.get("Base actuellement téléchargée :", "Currently downloaded:", "Base descargada atualmente:")

    val txtAnfrDatabaseFrom = AppStrings.anfrDatabaseFrom

    var dbSizeMb by remember { mutableDoubleStateOf(-1.0) }
    var localDbVersion by remember { mutableStateOf(txtSearching) }
    var remoteDbVersion by remember { mutableStateOf(txtSearching) }
    var localAnfrDate by remember { mutableStateOf("") }

    // ✅ NOUVEAUX ÉTATS POUR LA SUPPRESSION
    var showDeleteDialog by remember { mutableStateOf(false) }
    var dbRefreshTrigger by remember { mutableIntStateOf(0) } // Déclenche le re-scan local

    // ✅ ON AJOUTE dbRefreshTrigger AUX CLÉS DE L'EFFET
    LaunchedEffect(isSyncing, dbRefreshTrigger) {
        withContext(Dispatchers.IO) {
            dbSizeMb = try {
                fr.geotower.data.api.DatabaseDownloader.getDatabaseSize()
            } catch (e: Exception) {
                -1.0
            }

            fun formatVersion(raw: String?): String {
                if (raw != null && raw.length == 13) {
                    val year = raw.substring(0, 4)
                    val month = raw.substring(4, 6)
                    val day = raw.substring(6, 8)
                    val hour = raw.substring(9, 11)
                    val minute = raw.substring(11, 13)
                    return "$day/$month/$year - $hour:$minute"
                }
                return raw ?: txtUnknown
            }

            remoteDbVersion = try {
                val remote = fr.geotower.data.api.DatabaseDownloader.getLatestDatabaseVersion()
                if (remote != null) formatVersion(remote) else txtUnknown
            } catch (e: Exception) {
                txtUnknown
            }

            localDbVersion = try {
                val dbPath = context.getDatabasePath("geotower.db")

                if (dbPath.exists()) {
                    val db = SQLiteDatabase.openDatabase(dbPath.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

                    val cursor = try {
                        db.rawQuery("SELECT version, date_maj_anfr FROM metadata LIMIT 1", null)
                    } catch (e: Exception) {
                        db.rawQuery("SELECT version FROM metadata LIMIT 1", null)
                    }

                    var versionFormatee = txtUnknown
                    var anfrDateFormatee = ""

                    if (cursor.moveToFirst()) {
                        versionFormatee = formatVersion(cursor.getString(0))

                        if (cursor.columnCount > 1 && !cursor.isNull(1)) {
                            val raw = cursor.getString(1)
                            anfrDateFormatee = try {
                                if (raw.contains("T")) {
                                    val datePart = raw.substringBefore("T")
                                    val timePart = raw.substringAfter("T")

                                    val dParts = datePart.split("-")
                                    val tParts = timePart.split(":")

                                    if (dParts.size >= 3 && tParts.size >= 2) {
                                        "${dParts[2]}/${dParts[1]}/${dParts[0]} - ${tParts[0]}:${tParts[1]}"
                                    } else {
                                        raw
                                    }
                                } else {
                                    when (raw.length) {
                                        13 -> "${raw.substring(6, 8)}/${raw.substring(4, 6)}/${raw.substring(0, 4)} - ${raw.substring(9, 11)}:${raw.substring(11, 13)}"
                                        8 -> "${raw.substring(6, 8)}/${raw.substring(4, 6)}/${raw.substring(0, 4)}"
                                        else -> raw
                                    }
                                }
                            } catch (e: Exception) {
                                raw
                            }
                        }
                    }
                    cursor.close()
                    db.close()

                    localAnfrDate = anfrDateFormatee
                    versionFormatee
                } else {
                    localAnfrDate = "" // On vide la date si on n'a plus de base
                    txtNoDb
                }
            } catch (e: Exception) {
                txtOldDb
            }
        }
    }

    Surface(
        shape = shape,
        border = border,
        color = if (useOneUi) bubbleColor else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val iconColumnWidth = 24.dp
            val textStartPadding = 12.dp

            if (title != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.width(iconColumnWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_material_database),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(textStartPadding))
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Start
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Box(
                    modifier = Modifier.width(iconColumnWidth).padding(top = 2.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(textStartPadding))

                Column {
                    Text(text = txtLatestDb, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = remoteDbVersion, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier.width(iconColumnWidth).padding(top = 2.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(textStartPadding))

                Column {
                    Text(text = txtDownloadedDb, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = localDbVersion, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            val sizeText = when {
                dbSizeMb < 0.0 -> AppStrings.calcDbSize
                dbSizeMb == 0.0 -> AppStrings.unknownSize
                else -> AppStrings.dbSizeWarning(dbSizeMb)
            }

            Text(
                text = sizeText,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isSyncing) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    LinearWavyProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = AppStrings.downloadProgress((downloadProgress * 100).toInt()), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    Spacer(modifier = Modifier.height(16.dp))

                    // ✅ NOUVEAU : Le bouton pour annuler le téléchargement via WorkManager
                    OutlinedButton(
                        onClick = {
                            onSafeClick {
                                workManager.cancelUniqueWork("db_download")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(text = AppStrings.cancelDownload, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                val isUpToDate = localDbVersion != txtSearching &&
                                localDbVersion != txtNoDb &&
                                localDbVersion != txtUnknown &&
                                localDbVersion == remoteDbVersion

                Button(
                    onClick = {
                        onSafeClick {
                            val request = androidx.work.OneTimeWorkRequestBuilder<fr.geotower.data.workers.DatabaseDownloadWorker>().build()
                            workManager.enqueueUniqueWork("db_download", androidx.work.ExistingWorkPolicy.REPLACE, request)
                        }
                    },
                    enabled = !isUpToDate, // ✅ Grise le bouton si déjà à jour
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isUpToDate) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                        contentColor = if (isUpToDate) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isUpToDate) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isUpToDate) AppStrings.upToDate else AppStrings.downloadAntennas,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ✅ AFFICHAGE CONDITIONNEL DE LA DATE DE L'ANFR
            if (localAnfrDate.isNotEmpty() && localAnfrDate != "Inconnue") {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "$txtAnfrDatabaseFrom $localAnfrDate",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // ✅ BOUTON DE SUPPRESSION (Visible uniquement si une base est installée et qu'on ne télécharge pas)
            if (localDbVersion != txtNoDb && localDbVersion != txtUnknown && !isSyncing) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(text = AppStrings.deleteData, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // ✅ LE POP-UP D'AVERTISSEMENT
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = AppStrings.deleteDbWarningTitle, fontWeight = FontWeight.Bold) },
            text = { Text(AppStrings.deleteDbWarningDesc) },
            shape = shape,
            containerColor = MaterialTheme.colorScheme.surface,
            dismissButton = {
                // "Oui" en rouge (Texte pur)
                TextButton(onClick = {
                    showDeleteDialog = false

                    // 1. Fermer proprement la connexion à la BDD
                    fr.geotower.data.db.AppDatabase.closeDatabase()

                    // 2. Supprimer la base et ses fichiers temporaires
                    context.deleteDatabase("geotower.db")

                    // 3. Déclencher le rafraîchissement visuel instantanément
                    dbRefreshTrigger++

                }) {
                    Text(AppStrings.yes, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                // "Non" dans un rectangle aux coins ronds
                Button(
                    onClick = { showDeleteDialog = false },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(AppStrings.no, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
