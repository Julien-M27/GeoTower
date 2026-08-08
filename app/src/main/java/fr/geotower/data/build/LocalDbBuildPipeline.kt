package fr.geotower.data.build

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import fr.geotower.R
import fr.geotower.data.api.DatabaseDownloader
import fr.geotower.data.api.RadioDatabaseDownloader
import fr.geotower.data.db.GeoTowerDatabaseValidator
import fr.geotower.data.db.LocalDbProvenance
import fr.geotower.data.db.RadioDatabaseValidator
import fr.geotower.data.models.RadioServiceMasks
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

    /**
     * Ce que l'utilisateur choisit de generer. Le fichier SUP (source de TOUT) est toujours
     * telecharge ; l'observatoire mobile (~500 Mo) uniquement si [mobile]. [radioBroadcast] = Radio/TV
     * (diffusion FM/DAB/TV), [nonMobileTech] = le reste non-mobile (faisceaux, reseaux prives, ...).
     */
    data class Packs(
        val mobile: Boolean,
        val radioBroadcast: Boolean,
        val nonMobileTech: Boolean,
    ) {
        val anyRadio: Boolean get() = radioBroadcast || nonMobileTech

        /** Masque de service autorise pour la base radio (bits [RadioServiceMasks]). */
        val allowedServiceMask: Int
            get() = (if (radioBroadcast) RadioServiceMasks.BROADCAST else 0) or
                (if (nonMobileTech) RadioServiceMasks.NON_BROADCAST else 0)

        /** Libelle court des packs demandes, pour le rapport de mesures. */
        val label: String
            get() = listOfNotNull(
                "mobile".takeIf { mobile },
                "radio/TV".takeIf { radioBroadcast },
                "non-mobile".takeIf { nonMobileTech },
            ).joinToString("+").ifEmpty { "aucun" }
    }

    /**
     * Lance la generation et **mesure** ce qu'elle coute a l'appareil (duree par phase, pics de tas
     * Java / memoire native / stockage, taille des sources et des bases produites). Le rapport est
     * conserve par [LocalBuildReportStore] et affiche dans l'ecran Diagnostic : c'est la seule
     * facon de savoir sur quels appareils la generation peut etre ouverte, et ce que chaque
     * optimisation fait vraiment gagner.
     */
    suspend fun run(
        context: Context,
        packs: Packs,
        force: Boolean = false,
        onProgress: (phase: BuildPhase, percent: Int, detail: String?) -> Unit,
    ): Result {
        val metrics = LocalBuildMetrics()
        val device = BuildDeviceProfiles.read(context)
        val result = runMeasured(context, packs, force, metrics, onProgress)
        runCatching {
            LocalBuildReportStore.save(context, metrics.report(device, packs.label, result.success, result.reason))
        }.onFailure { AppLogger.w("GeoTowerDb", "Rapport de mesures non enregistre", it) }
        return result
    }

    private suspend fun runMeasured(
        context: Context,
        packs: Packs,
        force: Boolean,
        metrics: LocalBuildMetrics,
        onProgress: (phase: BuildPhase, percent: Int, detail: String?) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        // Chaque progression alimente aussi les mesures : `processed` (lignes de la sous-etape) sert
        // a cumuler le debit par phase, il reste a 0 pour les phases reseau.
        fun emit(phase: BuildPhase, percent: Int, detail: String?, processed: Long = 0L) {
            metrics.onProgress(phase, processed)
            onProgress(phase, percent, detail)
        }

        // Budgets mesures pour LES packs demandes (le stockage en depend fortement, pas le tas).
        // `force` = l'utilisateur a choisi de tenter malgre un appareil sous les seuils : l'echec
        // est sans danger (build dans un fichier temporaire, base active jamais touchee), il ne
        // coute que du temps et de la data.
        val eligibility = LocalBuildCapability.evaluate(context, packs.mobile, packs.anyRadio)
        if (!eligibility.eligible && !force) return@withContext Result(false, eligibility.reason)
        if (!packs.mobile && !packs.anyRadio) return@withContext Result(false, "Aucune donnée sélectionnée")

        // noBackupFilesDir (pas cacheDir) : le systeme ne le purge pas en cours de build.
        val workDir = File(context.noBackupFilesDir, "local_db_build").apply { mkdirs() }
        val dataZip = File(workDir, "sup_data.zip")
        val refZip = File(workDir, "sup_ref.zip")
        val observatoireCsv = File(workDir, "observatoire.csv")
        val arcepDir = File(workDir, "arcep").apply { mkdirs() }
        // Staging dans des fichiers ANNEXES : les bases produites ne contiennent alors jamais que
        // leurs tables finales (SQLite ne rend pas les pages d'un DROP sans VACUUM), et le staging
        // se rend au systeme en supprimant un fichier. Un par base : l'attachement est propre a une
        // connexion, et mobile et radio en ont chacune une.
        val stagingFile = File(workDir, "staging_mobile.db")
        val radioStagingFile = File(workDir, "staging_radio.db")
        val builtFile = context.getDatabasePath("${GeoTowerDatabaseValidator.DB_NAME}.localbuild")
        val builtRadioFile = context.getDatabasePath("${RadioDatabaseValidator.DB_NAME}.localbuild")
        var radioDb: AndroidSqlDatabase? = null
        // Enrichissement ARCEP trimestriel (arcep_nidt/is_zb, cf. localisation). Optionnel : telecharge
        // avec le pack mobile, best-effort — un echec laisse ces champs a null/0 sans casser le build.
        val arcepFiles = ArrayList<File>()
        var arcepQuarter: String? = null
        // Echantillonne memoire et stockage pendant toute la generation (arrete dans le `finally`).
        val recorder = LocalBuildMetricsRecorder(metrics, workDir, listOf(builtFile, builtRadioFile))
            .also { it.start() }

        try {
            emit(BuildPhase.RESOLVING, 0, null)
            val datasetJson = downloader.fetchText(OfficialSources.MONTHLY_SUP_DATASET_API_URL, MAX_JSON_BYTES)
            val monthly = OfficialSources.selectMonthlySupZipUrls(datasetJson)
                ?: return@withContext Result(false, "ZIP mensuel ANFR introuvable sur data.gouv")
            // Version « fichier » du mensuel = le VRAI nom ANFR (ex. 20260630-export-etalab-data.zip),
            // extrait de l'URL data.gouv. On NE stocke PAS dataZip.name ("sup_data.zip", le fichier temporaire
            // local) : c'est ce prefixe AAAAMMJJ que la section « Versions » de l'A-propos formate en « Juin 2026 »,
            // a l'identique d'une base telechargee. Fallback sur le nom local si l'URL n'a pas de segment de fichier.
            val monthlyFileVersion = monthly.dataUrl.substringAfterLast('/').substringBefore('?')
                .ifBlank { dataZip.name }

            emit(BuildPhase.DOWNLOADING, 5, null)
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
                            emit(BuildPhase.DOWNLOADING, pct, "$mb Mo (essai $attempt)")
                        }
                    }
                    zipError = verifyMonthlyZip(dataZip)
                    if (zipError == null) break
                } catch (e: Exception) {
                    zipError = "Telechargement du ZIP mensuel : ${e.message ?: e.javaClass.simpleName}"
                }
            }
            if (zipError != null) return@withContext Result(false, zipError)
            metrics.noteFile("sup_data.zip", dataZip.length())

            // Le ZIP de references est optionnel (le builder a des valeurs par defaut).
            monthly.refUrl?.let { runCatching { downloader.downloadToFile(it, refZip, MAX_REF_ZIP_BYTES) } }
            metrics.noteFile("sup_ref.zip", refZip.length())

            // Un seul telechargement des communes : le meme JSON sert aux noms (`ref_commune`) et a
            // l'agregation superficie/population des stats departementales.
            val communesJson = runCatching {
                downloader.fetchText(OfficialSources.COMMUNES_URL, MAX_JSON_BYTES)
            }.getOrDefault("")
            val communes = RawSourceDownloader.parseCommunesJson(communesJson)
            val departments = if (communesJson.isEmpty()) {
                emptyMap()
            } else {
                runCatching {
                    RawSourceDownloader.parseDepartmentReference(
                        departementsJson = downloader.fetchText(OfficialSources.DEPARTEMENTS_URL, MAX_JSON_BYTES),
                        communesJson = communesJson,
                        populationYear = DepartmentStatsBuilder.POPULATION_YEAR,
                    )
                }.onFailure {
                    AppLogger.w("GeoTowerDb", "Referentiel departements indisponible (non-fatal)", it)
                }.getOrDefault(emptyMap())
            }.let { metropoleAndDrom ->
                // Les COM ne sont dans aucune des deux listes globales : un appel chacune. On ne
                // tente ces douze requetes que si l'API a deja repondu, pour ne pas enchainer
                // autant de delais d'attente quand geo.api.gouv.fr est injoignable.
                if (metropoleAndDrom.isEmpty()) metropoleAndDrom else metropoleAndDrom + overseasDepartments(downloader, metropoleAndDrom.keys)
            }

            // Observatoire (~500 Mo) = source MOBILE uniquement -> telecharge SEULEMENT si le pack mobile
            // est demande. Pour un build « non-mobile seul », ces ~500 Mo sont economises.
            if (packs.mobile) {
                emit(BuildPhase.READING_STATIONS, 36, null)
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
                                emit(BuildPhase.READING_STATIONS, pct, "$mb Mo (essai $obsAttempt)")
                            }
                        }
                        obsError = if (observatoireCsv.length() > 1000L) null else "Observatoire vide"
                        if (obsError == null) break
                    } catch (e: Exception) {
                        obsError = "Telechargement de l'observatoire : ${e.message ?: e.javaClass.simpleName}"
                    }
                }
                if (obsError != null) return@withContext Result(false, obsError)
                metrics.noteFile("observatoire.csv", observatoireCsv.length())

                // ARCEP trimestriel : resout le listing "last/" (trimestre courant) et telecharge les
                // CSV de sites (Metropole + Outremer). Entierement best-effort : source d'enrichissement
                // OPTIONNELLE (arcep_nidt/is_zb), un echec ne doit jamais interrompre la generation.
                runCatching {
                    emit(BuildPhase.READING_STATIONS, 44, "ARCEP")
                    val listingHtml = downloader.fetchText(OfficialSources.ARCEP_SITES_LAST_URL, MAX_JSON_BYTES)
                    OfficialSources.resolveArcepSitesCsvUrls(listingHtml).forEachIndexed { index, url ->
                        arcepQuarter = arcepQuarter ?: OfficialSources.extractQuarter(url.substringAfterLast('/'))
                        val dest = File(arcepDir, "arcep_$index.csv")
                        runCatching { downloader.downloadToFile(url, dest, MAX_ARCEP_BYTES) }
                            .onSuccess { if (dest.length() > 100L) arcepFiles.add(dest) }
                            .onFailure { AppLogger.w("GeoTowerDb", "ARCEP CSV download failed (non-fatal): $url", it) }
                    }
                }.onFailure { AppLogger.w("GeoTowerDb", "ARCEP resolve failed (non-fatal)", it) }
                metrics.noteFile("arcep (${arcepFiles.size} CSV)", arcepFiles.sumOf { it.length() })
            }

            AnfrMonthlyZip(dataZip).use { data ->
                val refSource = if (refZip.exists()) AnfrMonthlyZip(refZip) else null
                try {
                    val references = if (refSource != null) {
                        anfrReferencesFrom(refSource, communes, departments)
                    } else {
                        AnfrReferences(communes = communes, departments = departments)
                    }
                    // Les fichiers SUP alimentent les deux bases ; l'observatoire (weekly) n'existe que pour le mobile.
                    val sources = AnfrSources(
                        weekly = if (packs.mobile) csvRows { observatoireCsv.inputStream() } else emptyList(),
                        stations = data.rows("SUP_STATION.txt"),
                        bandes = data.rows("SUP_BANDE.txt"),
                        emetteurs = data.rows("SUP_EMETTEUR.txt"),
                        antennes = data.rows("SUP_ANTENNE.txt"),
                        supports = data.rows("SUP_SUPPORT.txt"),
                    )
                    // Radio demandee si un pack radio est coche ET que les references (labels) sont presentes.
                    val wantRadio = packs.anyRadio && refZip.exists()
                    if (packs.anyRadio && !refZip.exists() && !packs.mobile) {
                        return@withContext Result(false, "References ANFR indisponibles pour la base radio")
                    }

                    if (packs.mobile) {
                        // === MOBILE (+ radio mutualise si demande) : le ZIP SUP est parse UNE fois,
                        // GeoTowerDbBuilder « tee » chaque ligne au sink radio (avant ses filtres mobiles). ===
                        if (builtFile.exists()) builtFile.delete()
                        builtFile.parentFile?.mkdirs()
                        if (wantRadio) {
                            if (builtRadioFile.exists()) builtRadioFile.delete()
                            builtRadioFile.parentFile?.mkdirs()
                            radioDb = AndroidSqlDatabase(SQLiteDatabase.openOrCreateDatabase(builtRadioFile, null))
                                .also { it.applyBuildPragmas(eligibility.totalRamBytes) }
                                .withStagingFile(radioStagingFile)
                                .also {
                                    it.applyStagingPragmas(eligibility.totalRamBytes)
                                    RadioDbBuilder.prepareSchema(it)
                                }
                        }
                        val radioSink: SupRowSink =
                            radioDb?.let { RadioDbBuilder.RadioStagingSink(it, references.typeAntenne) } ?: SupRowSink.None
                        var buildPercent = 45
                        // Metadonnees ARCEP (arcep_nidt/is_zb) fusionnees depuis les CSV telecharges. Les
                        // fichiers ne servent plus apres : ~19 Mo rendus avant la partie lourde du build.
                        val arcep = parseArcepFiles(arcepFiles)
                        arcepDir.deleteRecursively()
                        val db = AndroidSqlDatabase(SQLiteDatabase.openOrCreateDatabase(builtFile, null))
                        db.applyBuildPragmas(eligibility.totalRamBytes)
                        val staged = db.withStagingFile(stagingFile)
                        staged.applyStagingPragmas(eligibility.totalRamBytes)
                        try {
                            GeoTowerDbBuilder.build(
                                staged, sources, references, arcep,
                                BuildConfig(version = buildVersion(), zipVersion = monthlyFileVersion, quarterlyVersion = arcepQuarter),
                                onProgress = { phase, processed ->
                                    buildPercent = maxOf(buildPercent, percentFor(phase))
                                    emit(phase, buildPercent, detailFor(context, processed), processed)
                                },
                                supSink = radioSink,
                                // L'observatoire (~173 Mo) n'est lu QUE par la premiere phase : le rendre
                                // des sa derniere ligne retire autant du pic de stockage, qui tombe bien
                                // plus tard (calcul des masques).
                                onWeeklyConsumed = { observatoireCsv.delete() },
                            )
                        } finally {
                            db.close()
                            stagingFile.delete()
                        }

                        emit(BuildPhase.INSTALLING, 94, null)
                        metrics.noteFile("${GeoTowerDatabaseValidator.DB_NAME} (produite)", builtFile.length())
                        // Valide d'abord pour remonter la cause EXACTE (ex. « Table vide: support ») dans l'UI.
                        val validation = GeoTowerDatabaseValidator.validateDatabaseFile(builtFile)
                        if (!validation.isValid) {
                            return@withContext Result(false, "Validation : ${validation.reason ?: "base invalide"}")
                        }
                        if (!DatabaseDownloader.installBuiltDatabase(context, builtFile)) {
                            return@withContext Result(false, "Installation impossible (échange du fichier de base)")
                        }

                        // Base radio : staging DEJA peuple par le sink -> calcul/emission seulement, filtre par
                        // categorie(s) choisie(s). Best-effort (base radio optionnelle).
                        radioDb?.let { rdb ->
                            runCatching {
                                val radioVersion = buildVersion()
                                RadioDbBuilder.buildFromStaging(
                                    rdb, references,
                                    RadioBuildConfig(version = radioVersion, zipVersion = monthlyFileVersion),
                                    { percent, processed ->
                                        emit(BuildPhase.RADIO_BUILDING, percent, detailFor(context, processed), processed)
                                    },
                                    packs.allowedServiceMask,
                                )
                                rdb.close()
                                metrics.noteFile("${RadioDatabaseValidator.DB_NAME} (produite)", builtRadioFile.length())
                                val radioValidation = RadioDatabaseValidator.validateDatabaseFile(builtRadioFile)
                                if (radioValidation.isValid) {
                                    // Provenance : memorise la version installee pour la distinguer d'un telechargement.
                                    if (RadioDatabaseDownloader.installBuiltRadioDatabase(context, builtRadioFile)) {
                                        LocalDbProvenance.recordRadioLocalBuild(context, radioVersion)
                                    }
                                } else {
                                    AppLogger.w("GeoTowerDb", "Local radio DB validation failed: ${radioValidation.reason}")
                                }
                            }.onFailure { AppLogger.w("GeoTowerDb", "Local radio DB build failed (non-fatal)", it) }
                        }
                    } else {
                        // === RADIO SEUL (standalone) : pas de mobile, pas d'observatoire. RadioDbBuilder parse
                        // lui-meme les SUP dans son staging puis emet, filtre par categorie(s) choisie(s). ===
                        if (builtRadioFile.exists()) builtRadioFile.delete()
                        builtRadioFile.parentFile?.mkdirs()
                        val rdb = AndroidSqlDatabase(SQLiteDatabase.openOrCreateDatabase(builtRadioFile, null))
                            .also { it.applyBuildPragmas(eligibility.totalRamBytes) }
                            .withStagingFile(radioStagingFile)
                            .also { it.applyStagingPragmas(eligibility.totalRamBytes) }
                        radioDb = rdb
                        val radioVersion = buildVersion()
                        RadioDbBuilder.build(
                            rdb, sources, references,
                            RadioBuildConfig(version = radioVersion, zipVersion = monthlyFileVersion),
                            { percent, processed ->
                                emit(BuildPhase.RADIO_BUILDING, percent, detailFor(context, processed), processed)
                            },
                            packs.allowedServiceMask,
                        )
                        rdb.close()

                        emit(BuildPhase.INSTALLING, 94, null)
                        metrics.noteFile("${RadioDatabaseValidator.DB_NAME} (produite)", builtRadioFile.length())
                        val radioValidation = RadioDatabaseValidator.validateDatabaseFile(builtRadioFile)
                        if (!radioValidation.isValid) {
                            return@withContext Result(false, "Validation radio : ${radioValidation.reason ?: "base invalide"}")
                        }
                        if (!RadioDatabaseDownloader.installBuiltRadioDatabase(context, builtRadioFile)) {
                            return@withContext Result(false, "Installation radio impossible")
                        }
                        // Provenance : memorise la version installee pour la distinguer d'un telechargement.
                        LocalDbProvenance.recordRadioLocalBuild(context, radioVersion)
                    }
                } finally {
                    refSource?.close()
                }
            }

            emit(BuildPhase.DONE, 100, null)
            Result(true)
        } catch (e: Exception) {
            if (builtFile.exists()) builtFile.delete()
            Result(false, e.message ?: "Échec de la génération locale")
        } finally {
            // Avant tout nettoyage : le pic de stockage doit etre mesure sources encore presentes.
            recorder.stop()
            runCatching { radioDb?.close() }
            dataZip.delete()
            refZip.delete()
            observatoireCsv.delete()
            arcepDir.deleteRecursively()
            stagingFile.delete()
            radioStagingFile.delete()
            if (builtRadioFile.exists()) builtRadioFile.delete()
        }
    }

    /**
     * Fusionne les CSV ARCEP « sites » telecharges en `(id_anfr, operateur majuscules) -> (nidt, is_zb)`
     * (port de `load_arcep_site_metadata` cote [RawSourceDownloader.parseArcepSites]). Lecture en flux,
     * fichier apres fichier ; la map (dizaines de milliers d'entrees) reste en RAM le temps du build.
     * Vide si aucun fichier — l'enrichissement ARCEP est alors simplement absent (arcep_nidt=null, is_zb=0).
     */
    private fun parseArcepFiles(files: List<File>): Map<Pair<String, String>, ArcepSiteMeta> {
        if (files.isEmpty()) return emptyMap()
        val rows = Iterable {
            files.asSequence().flatMap { file -> csvRows { file.inputStream() }.asSequence() }.iterator()
        }
        return RawSourceDownloader.parseArcepSites(rows)
    }

    /**
     * Superficie, population et nom des collectivites d'outre-mer, une requete de nom et une de
     * communes par territoire. Chaque COM est independante : celle qui echoue est simplement
     * absente du referentiel (compteurs ANFR sans ratios), les autres restent completes.
     */
    private fun overseasDepartments(
        downloader: RawSourceDownloader,
        alreadyKnown: Set<String>,
    ): Map<String, DepartmentReferenceRow> {
        val result = LinkedHashMap<String, DepartmentReferenceRow>()
        for (code in OfficialSources.OVERSEAS_DEPARTMENT_CODES) {
            if (code in alreadyKnown) continue
            runCatching {
                RawSourceDownloader.parseSingleDepartmentReference(
                    departementJson = downloader.fetchText(OfficialSources.departementUrl(code), MAX_JSON_BYTES),
                    communesJson = downloader.fetchText(OfficialSources.departementCommunesUrl(code), MAX_JSON_BYTES),
                    code = code,
                    populationYear = DepartmentStatsBuilder.POPULATION_YEAR,
                )
            }.onFailure {
                AppLogger.w("GeoTowerDb", "Referentiel COM $code indisponible (non-fatal)", it)
            }.getOrNull()?.let { result[code] = it }
        }
        return result
    }

    /**
     * Reglages SQLite communs aux bases construites. Le cache est de la memoire **native** (pas le
     * heap Java, qui n'est donc pas menace) : sur un appareil eligible — au moins 6 Go par
     * construction, cf. [LocalBuildCapability] — lui donner bien plus que les 40 Mo d'origine
     * change beaucoup pour la creation des index et les jointures sur des tables de staging de
     * plusieurs centaines de Mo.
     */
    private fun AndroidSqlDatabase.applyBuildPragmas(totalRamBytes: Long) {
        execSql("PRAGMA cache_size = -${cacheSizeKib(totalRamBytes)}")
        execSql("PRAGMA mmap_size = 268435456")
    }

    /**
     * Memes reglages pour la base de staging attachee. Chaque base d'une connexion a son propre
     * pager, donc son propre cache : sans ces PRAGMA qualifies, c'est le fichier qui encaisse tout
     * le travail du build qui se retrouverait avec le cache par defaut de 2 Mo.
     */
    private fun AndroidSqlDatabase.applyStagingPragmas(totalRamBytes: Long) {
        val schema = AndroidSqlDatabase.STAGING_SCHEMA
        execSql("PRAGMA $schema.journal_mode = OFF")
        execSql("PRAGMA $schema.synchronous = OFF")
        execSql("PRAGMA $schema.cache_size = -${cacheSizeKib(totalRamBytes)}")
        // mmap PLUS LARGE que sur la base finale : c'est ce fichier (~765 Mo) que les deux phases
        // les plus longues parcourent en acces aleatoire (BUILDING_DETAILS et la classification
        // radio font chacune ~10 millions de recherches d'index). Au-dela de `mmap_size`, SQLite
        // repasse par des lectures classiques servies par son seul cache prive ; en le couvrant
        // entierement, les pages chaudes restent dans le cache du noyau. L'espace d'adressage est
        // gratuit sur un appareil 64 bits, et la valeur est un plafond, pas une reservation.
        execSql("PRAGMA $schema.mmap_size = ${STAGING_MMAP_BYTES}")
    }

    /** Taille du cache SQLite en Kio : 1/48e de la RAM, borne a [64 Mo, 160 Mo]. */
    private fun cacheSizeKib(totalRamBytes: Long): Long =
        (totalRamBytes / 48 / 1024).coerceIn(64L * 1024L, 160L * 1024L)

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

    /** Detail « live » (compteur de lignes) pour la carte, ou null si rien a afficher. */
    private fun detailFor(context: Context, processed: Long): String? =
        if (processed > 0L) context.getString(R.string.appstrings_local_build_lines, formatCount(processed)) else null

    private fun buildVersion(): String = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())

    private companion object {
        /** Plafond de projection memoire de la base de staging : au-dessus de son pic (~765 Mo). */
        const val STAGING_MMAP_BYTES = 1_073_741_824L

        const val MAX_ZIP_ATTEMPTS = 3
        // Garde-fous de taille (bornes larges ; a resserrer apres mesures reelles).
        const val MAX_ZIP_BYTES = 900L * 1024 * 1024
        const val MAX_REF_ZIP_BYTES = 64L * 1024 * 1024
        const val MAX_OBS_BYTES = 512L * 1024 * 1024
        // CSV ARCEP "sites" : Metropole ~20 Mo, Outremer ~1 Mo. Borne large pour tolerer la croissance.
        const val MAX_ARCEP_BYTES = 128L * 1024 * 1024
        const val MAX_JSON_BYTES = 64L * 1024 * 1024
    }
}
