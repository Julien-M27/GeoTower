package fr.geotower.data.build

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import fr.geotower.R
import fr.geotower.data.api.DatabaseDownloader
import fr.geotower.data.api.RadioDatabaseDownloader
import fr.geotower.data.db.GeoTowerDatabaseValidator
import fr.geotower.data.db.RadioDatabaseValidator
import fr.geotower.utils.AppLogger
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrateur de la generation locale. Verifie l'eligibilite, resout et telecharge les sources
 * ANFR **officielles** (deux ZIP mensuels data.gouv + observatoire ANFR d4c **streame** pour ne
 * pas saturer le disque), construit `geotower_fr.db` via [GeoTowerDbBuilder] (staging SQLite) et
 * l'installe par le meme chemin atomique que le telechargement.
 *
 * La progression est signalee via un unique callback non-suspend `onProgress(phase, percent,
 * detail)`, appele a chaque etape (y compris pendant le telechargement — avec les Mo — et pendant
 * la construction — le builder emet ses sous-phases). Le worker s'en sert pour la notification
 * live et pour la barre de la carte.
 */
class LocalDbBuildPipeline(
    private val downloader: RawSourceDownloader = RawSourceDownloader(),
) {
    data class Result(val success: Boolean, val reason: String? = null)

    suspend fun run(
        context: Context,
        onProgress: (phase: BuildPhase, percent: Int, detail: String?) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        val eligibility = LocalBuildCapability.evaluate(context)
        if (!eligibility.eligible) return@withContext Result(false, eligibility.reason)

        // noBackupFilesDir (pas cacheDir) : le systeme ne le purge pas en cours de build.
        val workDir = File(context.noBackupFilesDir, "local_db_build").apply { mkdirs() }
        val dataZip = File(workDir, "sup_data.zip")
        val refZip = File(workDir, "sup_ref.zip")
        val observatoireCsv = File(workDir, "observatoire.csv")
        val builtFile = context.getDatabasePath("${GeoTowerDatabaseValidator.DB_NAME}.localbuild")
        val builtRadioFile = context.getDatabasePath("${RadioDatabaseValidator.DB_NAME}.localbuild")
        var radioDb: AndroidSqlDatabase? = null

        try {
            onProgress(BuildPhase.RESOLVING, 0, null)
            val datasetJson = downloader.fetchText(OfficialSources.MONTHLY_SUP_DATASET_API_URL, MAX_JSON_BYTES)
            val monthly = OfficialSources.selectMonthlySupZipUrls(datasetJson)
                ?: return@withContext Result(false, "ZIP mensuel ANFR introuvable sur data.gouv")

            onProgress(BuildPhase.DOWNLOADING, 5, null)
            var lastPct = -1
            var zipError: String? = "ZIP mensuel non telecharge"
            var attempt = 0
            while (attempt < MAX_ZIP_ATTEMPTS) {
                attempt++
                try {
                    downloader.downloadToFile(monthly.dataUrl, dataZip, MAX_ZIP_BYTES) { copied, total ->
                        val mb = copied / (1024 * 1024)
                        val pct = if (total > 0) (5 + copied * 30 / total).toInt().coerceIn(5, 35) else 20
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress(BuildPhase.DOWNLOADING, pct, "$mb Mo (essai $attempt)")
                        }
                    }
                    zipError = verifyMonthlyZip(dataZip)
                    if (zipError == null) break
                } catch (e: Exception) {
                    zipError = "Telechargement du ZIP mensuel : ${e.message ?: e.javaClass.simpleName}"
                }
            }
            if (zipError != null) return@withContext Result(false, zipError)

            // Le ZIP de references est optionnel (le builder a des valeurs par defaut).
            monthly.refUrl?.let { runCatching { downloader.downloadToFile(it, refZip, MAX_REF_ZIP_BYTES) } }

            val communes = runCatching {
                RawSourceDownloader.parseCommunesJson(
                    downloader.fetchText(OfficialSources.COMMUNES_URL, MAX_JSON_BYTES),
                )
            }.getOrDefault(emptyMap())

            onProgress(BuildPhase.READING_STATIONS, 36, null)
            val exportHtml = downloader.fetchText(OfficialSources.OBSERVATOIRE_EXPORT_PAGE_URL, MAX_JSON_BYTES)
            val observatoireUrl = OfficialSources.resolveObservatoireCsvUrl(exportHtml)
                ?: return@withContext Result(false, "URL de l'observatoire ANFR introuvable (page d'export)")

            // Observatoire telecharge dans un FICHIER (retry + completude) plutot que streame :
            // un flux HTTP peut casser (PROTOCOL_ERROR) en plein build ; un fichier est retryable.
            var obsError: String? = "Observatoire non telecharge"
            var obsAttempt = 0
            var obsPct = -1
            while (obsAttempt < MAX_ZIP_ATTEMPTS) {
                obsAttempt++
                try {
                    downloader.downloadToFile(observatoireUrl, observatoireCsv, MAX_OBS_BYTES) { copied, total ->
                        val mb = copied / (1024 * 1024)
                        val pct = if (total > 0) (36 + copied * 8 / total).toInt().coerceIn(36, 44) else 40
                        if (pct != obsPct) {
                            obsPct = pct
                            onProgress(BuildPhase.READING_STATIONS, pct, "$mb Mo (essai $obsAttempt)")
                        }
                    }
                    obsError = if (observatoireCsv.length() > 1000L) null else "Observatoire vide"
                    if (obsError == null) break
                } catch (e: Exception) {
                    obsError = "Telechargement de l'observatoire : ${e.message ?: e.javaClass.simpleName}"
                }
            }
            if (obsError != null) return@withContext Result(false, obsError)

            if (builtFile.exists()) builtFile.delete()
            builtFile.parentFile?.mkdirs()

            // Mutualisation : la base radio est STAGEE pendant le parse mobile (le ZIP SUP n'est
            // parse qu'UNE fois pour les deux bases). On prepare la base radio + son sink AVANT le build
            // mobile ; GeoTowerDbBuilder « tee » chaque ligne SUP au sink (avant ses filtres).
            val radioEligible = refZip.exists()
            if (radioEligible) {
                if (builtRadioFile.exists()) builtRadioFile.delete()
                builtRadioFile.parentFile?.mkdirs()
                radioDb = AndroidSqlDatabase(SQLiteDatabase.openOrCreateDatabase(builtRadioFile, null)).also {
                    it.execSql("PRAGMA cache_size = -40000")
                    it.execSql("PRAGMA mmap_size = 268435456")
                    RadioDbBuilder.prepareSchema(it)
                }
            }
            var sharedReferences: AnfrReferences = AnfrReferences(communes = communes)

            AnfrMonthlyZip(dataZip).use { data ->
                val refSource = if (refZip.exists()) AnfrMonthlyZip(refZip) else null
                try {
                    val references = if (refSource != null) {
                        anfrReferencesFrom(refSource, communes)
                    } else {
                        AnfrReferences(communes = communes)
                    }
                    sharedReferences = references
                    val radioSink: SupRowSink =
                        radioDb?.let { RadioDbBuilder.RadioStagingSink(it, references.typeAntenne) } ?: SupRowSink.None
                    val sources = AnfrSources(
                        weekly = csvRows { observatoireCsv.inputStream() },
                        stations = data.rows("SUP_STATION.txt"),
                        bandes = data.rows("SUP_BANDE.txt"),
                        emetteurs = data.rows("SUP_EMETTEUR.txt"),
                        antennes = data.rows("SUP_ANTENNE.txt"),
                        supports = data.rows("SUP_SUPPORT.txt"),
                    )
                    var buildPercent = 45
                    val db = AndroidSqlDatabase(SQLiteDatabase.openOrCreateDatabase(builtFile, null))
                    db.execSql("PRAGMA cache_size = -40000")
                    db.execSql("PRAGMA mmap_size = 268435456")
                    try {
                        GeoTowerDbBuilder.build(
                            db, sources, references, emptyMap(),
                            BuildConfig(version = buildVersion(), zipVersion = dataZip.name),
                            onProgress = { phase, processed ->
                                buildPercent = maxOf(buildPercent, percentFor(phase))
                                val detail = if (processed > 0L) {
                                    context.getString(R.string.appstrings_local_build_lines, formatCount(processed))
                                } else {
                                    null
                                }
                                onProgress(phase, buildPercent, detail)
                            },
                            supSink = radioSink,
                        )
                    } finally {
                        db.close()
                    }
                } finally {
                    refSource?.close()
                }
            }

            onProgress(BuildPhase.INSTALLING, 94, null)
            // Valide d'abord pour remonter la cause EXACTE (ex. « Table vide: support ») dans l'UI.
            val validation = GeoTowerDatabaseValidator.validateDatabaseFile(builtFile)
            if (!validation.isValid) {
                return@withContext Result(false, "Validation : ${validation.reason ?: "base invalide"}")
            }
            val installed = DatabaseDownloader.installBuiltDatabase(context, builtFile)
            if (!installed) {
                return@withContext Result(false, "Installation impossible (échange du fichier de base)")
            }

            // Base radio : son staging est DEJA peuple (par le sink pendant le build mobile) -> il ne
            // reste que le calcul/emission, sans re-parser le ZIP. Best-effort (base radio optionnelle).
            radioDb?.let { rdb ->
                runCatching {
                    RadioDbBuilder.buildFromStaging(
                        rdb, sharedReferences,
                        RadioBuildConfig(version = buildVersion(), zipVersion = dataZip.name),
                    ) { percent, processed ->
                        val detail = if (processed > 0L) {
                            context.getString(R.string.appstrings_local_build_lines, formatCount(processed))
                        } else {
                            null
                        }
                        onProgress(BuildPhase.RADIO_BUILDING, percent, detail)
                    }
                    rdb.close()
                    val radioValidation = RadioDatabaseValidator.validateDatabaseFile(builtRadioFile)
                    if (radioValidation.isValid) {
                        RadioDatabaseDownloader.installBuiltRadioDatabase(context, builtRadioFile)
                    } else {
                        AppLogger.w("GeoTowerDb", "Local radio DB validation failed: ${radioValidation.reason}")
                    }
                }.onFailure { AppLogger.w("GeoTowerDb", "Local radio DB build failed (non-fatal)", it) }
            }

            onProgress(BuildPhase.DONE, 100, null)
            Result(true)
        } catch (e: Exception) {
            if (builtFile.exists()) builtFile.delete()
            Result(false, e.message ?: "Échec de la génération locale")
        } finally {
            runCatching { radioDb?.close() }
            dataZip.delete()
            refZip.delete()
            observatoireCsv.delete()
            if (builtRadioFile.exists()) builtRadioFile.delete()
        }
    }

    /** Verifie que `zip` est une archive ZIP valide contenant SUP_STATION. Retourne la cause si KO, sinon null. */
    private fun verifyMonthlyZip(zip: File): String? {
        if (!zip.isFile || zip.length() < 100L) return "ZIP mensuel vide ou absent (${zip.length()} o)"
        val magic = ByteArray(2)
        try {
            zip.inputStream().use { it.read(magic) }
        } catch (e: Exception) {
            return "ZIP mensuel illisible : ${e.message ?: e.javaClass.simpleName}"
        }
        if (magic[0] != 0x50.toByte() || magic[1] != 0x4B.toByte()) {
            return "ZIP mensuel invalide (pas une archive ZIP, ${zip.length()} o)"
        }
        return try {
            AnfrMonthlyZip(zip).use { monthly ->
                if (!monthly.rows("SUP_STATION.txt").iterator().hasNext()) {
                    "SUP_STATION introuvable ou vide dans le ZIP mensuel"
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            "Ouverture du ZIP mensuel impossible (${zip.length()} o) : ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun percentFor(phase: BuildPhase): Int = when (phase) {
        BuildPhase.READING_STATIONS -> 40
        BuildPhase.READING_SUPPORTS -> 52
        BuildPhase.COMPUTING_FREQUENCIES -> 62
        BuildPhase.COMPUTING_ANTENNAS -> 70
        BuildPhase.BUILDING_DETAILS -> 76
        BuildPhase.INSERTING -> 84
        BuildPhase.COMPUTING_STATS -> 88
        BuildPhase.FINALIZING -> 91
        else -> 45
    }

    private fun formatCount(count: Long): String = NumberFormat.getIntegerInstance().format(count)

    private fun buildVersion(): String = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())

    private companion object {
        const val MAX_ZIP_ATTEMPTS = 3
        // Garde-fous de taille (bornes larges ; a resserrer apres mesures reelles).
        const val MAX_ZIP_BYTES = 900L * 1024 * 1024
        const val MAX_REF_ZIP_BYTES = 64L * 1024 * 1024
        const val MAX_OBS_BYTES = 512L * 1024 * 1024
        const val MAX_JSON_BYTES = 64L * 1024 * 1024
    }
}
