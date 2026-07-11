package fr.geotower.data.build

import fr.geotower.data.models.RadioFilterMasks

/**
 * Construit `geotower_fr.db` a partir des sources ANFR ([AnfrSources]), en ecrivant via
 * [SqlDatabase]. Reproduit la logique de `run_build` (docs/server/build_fr_anfr_db.py) mais
 * avec une **strategie de staging SQLite** afin de borner la RAM : les grosses tables
 * (BANDE / EMETTEUR / ANTENNE / SUPPORT, plusieurs millions de lignes) sont ecrites dans des
 * tables temporaires `stg_*` puis agregees par des scans SQL ordonnes ; seul l'accumulateur
 * station (borne par le nombre de stations, pas d'antennes) reste en RAM, et les
 * `details_frequences` sont streames sur disque. Le pic RAM tient ainsi sous le heap Android.
 *
 * Les transformations unitaires (masques, codec, parsing, stats) sont reutilisees a
 * l'identique, garantissant la parite avec la generation serveur.
 */
object GeoTowerDbBuilder {

    private class StationAcc(
        val operateurId: Int,
        val operatorLabel: String,
        val latitude: Double,
        val longitude: Double,
        val statutId: Int,
        val statutLabel: String,
    ) {
        var admId: Int? = null
        var dateImp: String? = null
        var dateSer: String? = null
        var dateMod: String? = null
        var adresse: String? = null
        var codeInsee: String? = null
        val masks = StationMasks()
        var hasActive = 0
        var azimuts: String? = null
        var azimutsFh: String? = null
    }

    /** Insertion par lots reutilisable (borne la RAM a un lot). */
    private class BatchInserter(
        private val db: SqlDatabase,
        private val sql: String,
        private val batchSize: Int = 5000,
    ) {
        private val buffer = ArrayList<List<Any?>>(batchSize)
        var total = 0
            private set

        fun add(row: List<Any?>) {
            buffer.add(row)
            if (buffer.size >= batchSize) flush()
        }

        fun flush() {
            if (buffer.isNotEmpty()) {
                total += db.insertBatch(sql, buffer)
                buffer.clear()
            }
        }
    }

    fun build(
        db: SqlDatabase,
        sources: AnfrSources,
        references: AnfrReferences,
        arcep: Map<Pair<String, String>, ArcepSiteMeta>,
        config: BuildConfig,
        onProgress: (phase: BuildPhase, processed: Long) -> Unit = { _, _ -> },
    ): BuildResult {
        val operateurIds = IdRegistry()
        val systemeIds = IdRegistry()
        val statutIds = IdRegistry()
        operateurIds.getId("Inconnu")
        systemeIds.getId("Inconnu")
        statutIds.getId("Inconnu")

        val stations = LinkedHashMap<String, StationAcc>()
        val usedNat = HashSet<Int>()
        val usedTpo = HashSet<Int>()
        val usedAdm = HashSet<Int>()
        val usedTae = HashSet<Int>()
        val usedCommunes = HashSet<String>()
        var dateMajAnfr = "Inconnue"

        db.execSql("PRAGMA journal_mode = OFF")
        db.execSql("PRAGMA synchronous = OFF")
        db.execSql("PRAGMA temp_store = FILE")
        GeoTowerDbSchema.CREATE_TABLE_STATEMENTS.forEach { db.execSql(it) }
        db.execSql(GeoTowerDbSchema.CREATE_ROOM_MASTER_TABLE)
        STAGING_STATEMENTS.forEach { db.execSql(it) }
        onProgress(BuildPhase.READING_STATIONS, 0L)

        // 1/ CSV hebdomadaire : construit l'accumulateur station (RAM) + statuts par systeme (disque).
        val sysInserter = BatchInserter(db, "INSERT OR REPLACE INTO stg_sysstatus VALUES (?, ?, ?, ?)")
        var weeklyRows = 0L
        for (row in sources.weekly) {
            if (dateMajAnfr == "Inconnue") {
                val d = AnfrParsing.cleanText(row.get("date_maj"))
                if (d.isNotEmpty()) dateMajAnfr = d
            }
            val idAnfr = AnfrParsing.normalizeIdAnfr(row.get("sta_nm_anfr"))
            if (idAnfr.isEmpty()) continue
            val acc = stations.getOrPut(idAnfr) {
                val (lat, lon) = AnfrParsing.parseCoordinates(row.get("coordonnees"))
                val operateurId = operateurIds.getId(row.get("adm_lb_nom"))
                val statutId = statutIds.getId(row.get("statut"))
                StationAcc(operateurId, operateurIds.getLabel(operateurId), lat, lon, statutId, statutIds.getLabel(statutId))
            }
            if (AnfrParsing.isActiveStatus(row.get("statut"))) acc.hasActive = 1
            RadioMaskComputer.updateMasksFromGeneration(acc.masks, row.get("generation"))
            val sysName = AnfrParsing.cleanText(row.get("emr_lb_systeme"))
            if (sysName.isNotEmpty()) {
                val statutId = statutIds.getId(row.get("statut"))
                sysInserter.add(
                    listOf(
                        idAnfr, sysName.uppercase(), statutIds.getLabel(statutId),
                        AnfrParsing.cleanText(row.get("emr_dt")).ifEmpty { null },
                    ),
                )
            }
            if (++weeklyRows % EMIT_EVERY == 0L) onProgress(BuildPhase.READING_STATIONS, weeklyRows)
        }
        sysInserter.flush()

        onProgress(BuildPhase.READING_SUPPORTS, 0L)
        // 2/ SUP_STATION : dates + exploitant.
        for (row in sources.stations) {
            val idAnfr = AnfrParsing.normalizeIdAnfr(row.get("sta_nm_anfr"))
            val acc = stations[idAnfr] ?: continue
            val admId = AnfrParsing.intOrNone(row.get("adm_id"))
            acc.admId = admId
            acc.dateImp = AnfrParsing.cleanText(row.get("dte_implantation")).ifEmpty { null }
            acc.dateMod = AnfrParsing.cleanText(row.get("dte_modif")).ifEmpty { null }
            acc.dateSer = AnfrParsing.cleanText(row.get("dte_en_service")).ifEmpty { null }
            if (admId != null) usedAdm.add(admId)
        }

        // 3/ SUP_BANDE -> staging, puis frequences pre-formatees par emetteur.
        val bandeInserter = BatchInserter(db, "INSERT INTO stg_bande VALUES (?, ?, ?, ?, ?, ?)")
        var bandeRows = 0L
        for (row in sources.bandes) {
            val emrId = AnfrParsing.cleanText(row.get("emr_id"))
            val fDebRaw = AnfrParsing.cleanText(row.get("ban_nb_f_deb"))
            val fFinRaw = AnfrParsing.cleanText(row.get("ban_nb_f_fin"))
            val fDeb = AnfrParsing.floatOrNone(fDebRaw)
            val fFin = AnfrParsing.floatOrNone(fFinRaw)
            val unite = AnfrParsing.cleanText(row.get("ban_fg_unite")).ifEmpty { "M" }
            if (emrId.isNotEmpty() && fDeb != null && fFin != null) {
                bandeInserter.add(listOf(emrId, fDeb, fFin, unite, fDebRaw, fFinRaw))
            }
            if (++bandeRows % EMIT_EVERY == 0L) onProgress(BuildPhase.READING_SUPPORTS, bandeRows)
        }
        bandeInserter.flush()
        buildEmrFreqs(db)

        // 4/ SUP_EMETTEUR -> staging (filtre aux stations connues), enregistre les systemes.
        val emetteurInserter = BatchInserter(db, "INSERT INTO stg_emetteur VALUES (?, ?, ?, ?)")
        var emetteurRows = 0L
        for (row in sources.emetteurs) {
            val idAnfr = AnfrParsing.normalizeIdAnfr(row.get("sta_nm_anfr"))
            if (!stations.containsKey(idAnfr)) continue
            val emrId = AnfrParsing.cleanText(row.get("emr_id"))
            if (emrId.isEmpty()) continue
            val aerId = AnfrParsing.cleanText(row.get("aer_id")).ifEmpty { null }
            val systeme = AnfrParsing.cleanText(row.get("emr_lb_systeme")).ifEmpty { "Inconnu" }
            systemeIds.getId(systeme)
            emetteurInserter.add(listOf(idAnfr, emrId, aerId, systeme))
            if (++emetteurRows % EMIT_EVERY == 0L) onProgress(BuildPhase.READING_SUPPORTS, emetteurRows)
        }
        emetteurInserter.flush()
        db.execSql(
            "INSERT OR REPLACE INTO stg_fh_aer SELECT DISTINCT aer_id FROM stg_emetteur " +
                "WHERE aer_id IS NOT NULL AND aer_id != '' AND UPPER(systeme) LIKE '%FH%'",
        )

        onProgress(BuildPhase.COMPUTING_FREQUENCIES, 0L)
        // 5/ Masques technologie/bande : scan emetteur (x bande) -> accumulateur RAM.
        var maskRows = 0L
        db.query(
            "SELECT e.id_anfr AS id_anfr, e.systeme AS systeme, b.f_deb AS f_deb, b.f_fin AS f_fin, b.unite AS unite " +
                "FROM stg_emetteur e LEFT JOIN stg_bande b ON e.emr_id = b.emr_id",
        ) { row ->
            val acc = stations[row.getString("id_anfr")]
            if (acc != null) {
                val systeme = row.getString("systeme")
                val fDeb = row.getDouble("f_deb")
                if (fDeb == null) {
                    if (AnfrParsing.cleanText(systeme).uppercase().contains("FH")) {
                        acc.masks.techMask = acc.masks.techMask or RadioFilterMasks.TECH_FH
                        acc.masks.bandMask = acc.masks.bandMask or RadioFilterMasks.BAND_FH
                    }
                } else {
                    val unite = row.getString("unite") ?: "M"
                    RadioMaskComputer.updateMasksFromSystemAndBand(
                        acc.masks, systeme,
                        AnfrParsing.frequencyToMhz(fDeb, unite),
                        AnfrParsing.frequencyToMhz(row.getDouble("f_fin"), unite),
                    )
                }
            }
            if (++maskRows % EMIT_EVERY == 0L) onProgress(BuildPhase.COMPUTING_FREQUENCIES, maskRows)
        }

        // stg_bande n'est plus utile apres le calcul des masques (les frequences pre-formatees
        // sont deja dans stg_emr_freqs) : on la supprime tot pour liberer du disque (SUP_BANDE
        // ~200 Mo decompresse). Les pages liberees seront reutilisees par les inserts suivants.
        db.execSql("DROP TABLE IF EXISTS stg_bande")

        onProgress(BuildPhase.READING_SUPPORTS, 0L)
        // 6/ SUP_ANTENNE -> staging (physique pre-formatee), puis marquage FH.
        val antenneInserter = BatchInserter(db, "INSERT OR REPLACE INTO stg_antenne VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
        var antenneRows = 0L
        for (row in sources.antennes) {
            val idAnfr = AnfrParsing.normalizeIdAnfr(row.get("sta_nm_anfr"))
            if (!stations.containsKey(idAnfr)) continue
            val aerId = AnfrParsing.cleanText(row.get("aer_id"))
            if (aerId.isEmpty()) continue
            val supId = AnfrParsing.cleanText(row.get("sup_id")).ifEmpty { null }
            val taeId = AnfrParsing.intOrNone(row.get("tae_id"))
            val azimut = AnfrParsing.intOrNone(row.get("aer_nb_azimut"))
            val hauteurBas = AnfrParsing.floatOrNone(row.get("aer_nb_alt_bas"))
            val typeTexte = if (taeId != null) {
                references.typeAntenne[taeId.toString()] ?: "Type inconnu ($taeId)"
            } else {
                "Type inconnu"
            }
            val azimutText = AnfrParsing.cleanText(row.get("aer_nb_azimut")).ifEmpty { "N/A" }
            val hauteurText = AnfrParsing.cleanText(row.get("aer_nb_alt_bas")).ifEmpty { "N/A" }
            val physique = "$typeTexte : $azimutText° (${hauteurText}m) [AER_ID: $aerId]"
            if (taeId != null) usedTae.add(taeId)
            antenneInserter.add(listOf(aerId, idAnfr, supId, taeId, azimut, hauteurBas, 0, physique))
            if (++antenneRows % EMIT_EVERY == 0L) onProgress(BuildPhase.READING_SUPPORTS, antenneRows)
        }
        antenneInserter.flush()
        db.execSql("UPDATE stg_antenne SET is_fh = 1 WHERE aer_id IN (SELECT aer_id FROM stg_fh_aer)")

        onProgress(BuildPhase.COMPUTING_ANTENNAS, 0L)
        // 7/ Azimuts (mobile / FH) par station : scan ordonne -> accumulateur RAM.
        applyAzimuts(db, stations)

        onProgress(BuildPhase.READING_SUPPORTS, 0L)
        // 8/ SUP_SUPPORT -> staging + adresses/communes sur l'accumulateur station.
        val supportInserter = BatchInserter(db, "INSERT OR REPLACE INTO stg_support VALUES (?, ?, ?, ?, ?)")
        var supportRows = 0L
        for (row in sources.supports) {
            val idAnfr = AnfrParsing.normalizeIdAnfr(row.get("sta_nm_anfr"))
            val supId = AnfrParsing.cleanText(row.get("sup_id"))
            val acc = stations[idAnfr]
            if (acc == null || supId.isEmpty()) continue
            val natId = AnfrParsing.intOrNone(row.get("nat_id"))
            val tpoId = AnfrParsing.intOrNone(row.get("tpo_id"))
            val hauteur = AnfrParsing.floatOrNone(row.get("sup_nm_haut"))
            val codeInsee = AnfrParsing.cleanText(row.get("com_cd_insee")).ifEmpty { null }
            if (natId != null) usedNat.add(natId)
            if (tpoId != null) usedTpo.add(tpoId)
            if (codeInsee != null) usedCommunes.add(codeInsee)

            val lieu = AnfrParsing.cleanText(row.get("adr_lb_lieu"))
            val add1 = AnfrParsing.cleanText(row.get("adr_lb_add1"))
            val add2 = AnfrParsing.cleanText(row.get("adr_lb_add2"))
            val add3 = AnfrParsing.cleanText(row.get("adr_lb_add3"))
            val cp = AnfrParsing.cleanText(row.get("adr_nm_cp"))
            val ville = if (codeInsee != null) references.communes[codeInsee] ?: "" else ""
            val rue = listOf(lieu, add1, add2, add3).filter { it.isNotEmpty() }.joinToString(", ")
            var adresse = rue
            if (cp.isNotEmpty() || ville.isNotEmpty()) {
                adresse = if (adresse.isNotEmpty()) "$adresse, " else ""
                adresse += "$cp $ville".trim()
            }
            if (adresse.isNotEmpty()) acc.adresse = adresse
            if (codeInsee != null) acc.codeInsee = codeInsee

            supportInserter.add(listOf(idAnfr, supId, natId, tpoId, hauteur))
            if (++supportRows % EMIT_EVERY == 0L) onProgress(BuildPhase.READING_SUPPORTS, supportRows)
        }
        supportInserter.flush()

        // 9/ Fige l'accumulateur station et l'ARCEP sur disque, puis libere la RAM.
        val stationInserter = BatchInserter(
            db,
            "INSERT OR REPLACE INTO stg_station_final VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        )
        for ((idAnfr, acc) in stations) {
            stationInserter.add(
                listOf(
                    idAnfr, acc.operateurId, acc.operatorLabel, acc.latitude, acc.longitude, acc.statutId,
                    acc.statutLabel, acc.admId, acc.dateImp, acc.dateSer, acc.dateMod, acc.adresse, acc.codeInsee,
                    acc.masks.techMask, acc.masks.bandMask, acc.hasActive, acc.azimuts, acc.azimutsFh,
                ),
            )
        }
        stationInserter.flush()
        val stationCount = stations.size
        stations.clear()

        val arcepInserter = BatchInserter(db, "INSERT OR REPLACE INTO stg_arcep VALUES (?, ?, ?, ?)")
        for ((key, meta) in arcep) {
            arcepInserter.add(listOf(key.first, key.second, meta.nidt, meta.isZb))
        }
        arcepInserter.flush()

        onProgress(BuildPhase.BUILDING_DETAILS, 0L)
        // 10/ details_frequences par station : scan ordonne -> streaming sur disque.
        applyDetails(db)

        // Ces tables de staging ne servent plus a l'emission finale (SUP_EMETTEUR ~120 Mo) :
        // on libere avant d'ecrire les tables definitives.
        listOf("stg_emetteur", "stg_emr_freqs", "stg_sysstatus", "stg_fh_aer").forEach {
            db.execSql("DROP TABLE IF EXISTS $it")
        }

        onProgress(BuildPhase.INSERTING, 0L)
        // 11/ Emission des tables finales depuis le staging (pur SQL, sans RAM).
        db.execSql(
            "INSERT INTO localisation SELECT sf.id_anfr, sf.operateur_id, sf.latitude, sf.longitude, sf.azimuts, " +
                "sf.code_insee, sf.azimuts_fh, sf.tech_mask, sf.band_mask, ar.nidt, COALESCE(ar.is_zb, 0) " +
                "FROM stg_station_final sf " +
                "LEFT JOIN stg_arcep ar ON sf.id_anfr = ar.id_anfr AND ar.operator_upper = UPPER(TRIM(sf.operator_label))",
        )
        db.execSql(
            "INSERT INTO technique SELECT sf.id_anfr, sf.adm_id, sf.statut_id, sf.date_imp, sf.date_ser, sf.date_mod, " +
                "d.details, sf.adresse, sf.has_active " +
                "FROM stg_station_final sf LEFT JOIN stg_details d ON sf.id_anfr = d.id_anfr",
        )
        db.execSql("INSERT INTO support SELECT id_anfr, sup_id, nat_id, tpo_id, hauteur FROM stg_support")
        db.execSql("INSERT INTO antenne SELECT aer_id, id_anfr, sup_id, tae_id, azimut, hauteur_bas, is_fh FROM stg_antenne")

        // 12/ Referentiels + metadonnees.
        db.insertBatch("INSERT INTO ref_operateur VALUES (?, ?)", operateurIds.rows().map { listOf(it.first, it.second) })
        db.insertBatch(
            "INSERT INTO ref_nature VALUES (?, ?)",
            usedNat.sorted().map { listOf(it, references.nature[it.toString()] ?: "Code Nature $it") },
        )
        db.insertBatch(
            "INSERT INTO ref_proprietaire VALUES (?, ?)",
            usedTpo.sorted().map { listOf(it, references.proprietaire[it.toString()] ?: "Inconnu") },
        )
        db.insertBatch(
            "INSERT INTO ref_exploitant VALUES (?, ?)",
            usedAdm.sorted().map { listOf(it, references.exploitant[it.toString()] ?: "Code Exploitant $it") },
        )
        db.insertBatch(
            "INSERT INTO ref_type_antenne VALUES (?, ?)",
            usedTae.sorted().map { listOf(it, references.typeAntenne[it.toString()] ?: "Type inconnu ($it)") },
        )
        db.insertBatch("INSERT INTO ref_systeme VALUES (?, ?)", systemeIds.rows().map { listOf(it.first, it.second) })
        db.insertBatch("INSERT INTO ref_statut VALUES (?, ?)", statutIds.rows().map { listOf(it.first, it.second) })
        db.insertBatch(
            "INSERT INTO ref_commune VALUES (?, ?)",
            usedCommunes.filter { references.communes.containsKey(it) }.sorted()
                .map { listOf(it, references.communes[it]) },
        )

        db.insertBatch(
            "INSERT INTO metadata VALUES (?, ?, ?, ?, ?, ?, ?)",
            listOf(
                listOf(
                    config.version, GeoTowerDbSchema.SCHEMA_VERSION, GeoTowerDbSchema.COUNTRY_CODE,
                    GeoTowerDbSchema.COUNTRY_NAME, GeoTowerDbSchema.SOURCE, dateMajAnfr, config.zipVersion,
                ),
            ),
        )
        val sourceVersions = ArrayList<List<Any?>>()
        config.quarterlyVersion?.let { sourceVersions.add(listOf("quarterly_version", it)) }
        sourceVersions.add(listOf("provenance", "local_build"))
        db.insertBatch("INSERT OR REPLACE INTO source_versions (source_key, source_value) VALUES (?, ?)", sourceVersions)

        onProgress(BuildPhase.COMPUTING_STATS, 0L)
        // 13/ Stats courantes (radio_stat_current), exigees non vides par le validateur.
        AnfrStatsBuilder.populateCurrentStats(db)

        onProgress(BuildPhase.FINALIZING, 0L)
        // 14/ Nettoyage du staging (libere le disque avant finalisation).
        STAGING_TABLES.forEach { db.execSql("DROP TABLE IF EXISTS $it") }

        db.execSql(GeoTowerDbSchema.INSERT_ROOM_IDENTITY)
        // Room lit PRAGMA user_version a l'ouverture : a poser en tout dernier.
        db.execSql(GeoTowerDbSchema.SET_USER_VERSION)

        val supportCount = count(db, "support")
        val antenneCount = count(db, "antenne")
        return BuildResult(stationCount, supportCount, antenneCount)
    }

    /** Pre-formate, par emetteur, la chaine des bandes (`format_band_range` joint par ", "). */
    private fun buildEmrFreqs(db: SqlDatabase) {
        val inserter = BatchInserter(db, "INSERT OR REPLACE INTO stg_emr_freqs VALUES (?, ?)")
        var currentEmr: String? = null
        val parts = ArrayList<String>()
        fun flush() {
            currentEmr?.let { inserter.add(listOf(it, parts.joinToString(", "))) }
            parts.clear()
        }
        db.query("SELECT emr_id, f_deb_raw, f_fin_raw, unite FROM stg_bande ORDER BY emr_id, rowid") { row ->
            val emrId = row.getString("emr_id") ?: ""
            if (emrId != currentEmr) {
                flush()
                currentEmr = emrId
            }
            parts.add(
                AnfrParsing.formatBandRange(
                    row.getString("f_deb_raw") ?: "",
                    row.getString("f_fin_raw") ?: "",
                    row.getString("unite") ?: "M",
                ),
            )
        }
        flush()
        inserter.flush()
    }

    /** Azimuts mobiles / FH par station (tries par valeur) + masque FH si azimut FH present. */
    private fun applyAzimuts(db: SqlDatabase, stations: Map<String, StationAcc>) {
        var currentId: String? = null
        val mobile = LinkedHashSet<String>()
        val fh = LinkedHashSet<String>()
        fun flush() {
            val acc = currentId?.let { stations[it] }
            if (acc != null) {
                acc.azimuts = azimutsToString(mobile)
                acc.azimutsFh = azimutsToString(fh)
                if (fh.isNotEmpty()) {
                    acc.masks.techMask = acc.masks.techMask or RadioFilterMasks.TECH_FH
                    acc.masks.bandMask = acc.masks.bandMask or RadioFilterMasks.BAND_FH
                }
            }
            mobile.clear()
            fh.clear()
        }
        db.query("SELECT id_anfr, azimut, is_fh FROM stg_antenne WHERE azimut IS NOT NULL ORDER BY id_anfr") { row ->
            val id = row.getString("id_anfr") ?: ""
            if (id != currentId) {
                flush()
                currentId = id
            }
            val azimut = row.getIntOrNull("azimut")
            if (azimut != null) {
                if (row.getInt("is_fh") == 1) fh.add(azimut.toString()) else mobile.add(azimut.toString())
            }
        }
        flush()
    }

    /** Construit `details_frequences` par station (scan ordonne, emetteur x bandes x physique x statut). */
    private fun applyDetails(db: SqlDatabase) {
        val inserter = BatchInserter(db, "INSERT OR REPLACE INTO stg_details VALUES (?, ?)")
        var currentId: String? = null
        val lines = sortedSetOf<String>()
        fun flush() {
            val id = currentId
            if (id != null && lines.isNotEmpty()) {
                FrequencyDetailsEncoder.encode(lines.joinToString("\n"))?.let { inserter.add(listOf(id, it)) }
            }
            lines.clear()
        }
        db.query(
            "SELECT e.id_anfr AS id_anfr, e.systeme AS systeme, COALESCE(f.freqs_text, '') AS freqs, " +
                "s.statut AS sys_statut, s.emr_dt AS emr_dt, COALESCE(ap.physique, 'Azimut non specifie') AS phys, " +
                "sf.statut_label AS statut_label " +
                "FROM stg_emetteur e " +
                "LEFT JOIN stg_emr_freqs f ON e.emr_id = f.emr_id " +
                "LEFT JOIN stg_antenne ap ON e.aer_id = ap.aer_id " +
                "LEFT JOIN stg_sysstatus s ON e.id_anfr = s.id_anfr AND s.systeme_upper = UPPER(e.systeme) " +
                "JOIN stg_station_final sf ON e.id_anfr = sf.id_anfr " +
                "ORDER BY e.id_anfr",
        ) { row ->
            val id = row.getString("id_anfr") ?: ""
            if (id != currentId) {
                flush()
                currentId = id
            }
            val systeme = row.getString("systeme") ?: "Inconnu"
            val freqs = row.getString("freqs") ?: ""
            val statut = row.getString("sys_statut") ?: row.getString("statut_label") ?: "Inconnu"
            val date = row.getString("emr_dt") ?: ""
            val phys = row.getString("phys") ?: "Azimut non specifie"
            lines.add("$systeme : $freqs | $statut | $date | $phys")
        }
        flush()
        inserter.flush()
    }

    private fun azimutsToString(values: Set<String>): String? {
        if (values.isEmpty()) return null
        return values.sortedBy { it.toIntOrNull() ?: 999 }.joinToString(",")
    }

    private fun count(db: SqlDatabase, table: String): Int {
        var result = 0
        db.query("SELECT COUNT(*) AS n FROM $table") { result = it.getInt("n") }
        return result
    }

    // Frequence d'emission des compteurs de progression (1 notif par tranche de lignes).
    private const val EMIT_EVERY = 50_000L

    private val STAGING_TABLES = listOf(
        "stg_bande", "stg_emr_freqs", "stg_emetteur", "stg_fh_aer", "stg_antenne",
        "stg_support", "stg_sysstatus", "stg_station_final", "stg_arcep", "stg_details",
    )

    private val STAGING_STATEMENTS = listOf(
        "CREATE TABLE stg_bande (emr_id TEXT, f_deb REAL, f_fin REAL, unite TEXT, f_deb_raw TEXT, f_fin_raw TEXT)",
        "CREATE INDEX ix_stg_bande_emr ON stg_bande(emr_id)",
        "CREATE TABLE stg_emr_freqs (emr_id TEXT PRIMARY KEY, freqs_text TEXT)",
        "CREATE TABLE stg_emetteur (id_anfr TEXT, emr_id TEXT, aer_id TEXT, systeme TEXT)",
        "CREATE INDEX ix_stg_emetteur_emr ON stg_emetteur(emr_id)",
        "CREATE INDEX ix_stg_emetteur_aer ON stg_emetteur(aer_id)",
        "CREATE INDEX ix_stg_emetteur_id ON stg_emetteur(id_anfr)",
        "CREATE TABLE stg_fh_aer (aer_id TEXT PRIMARY KEY)",
        "CREATE TABLE stg_antenne (aer_id TEXT PRIMARY KEY, id_anfr TEXT, sup_id TEXT, tae_id INTEGER, azimut INTEGER, hauteur_bas REAL, is_fh INTEGER, physique TEXT)",
        "CREATE INDEX ix_stg_antenne_id ON stg_antenne(id_anfr)",
        "CREATE TABLE stg_support (id_anfr TEXT, sup_id TEXT, nat_id INTEGER, tpo_id INTEGER, hauteur REAL, PRIMARY KEY(id_anfr, sup_id))",
        "CREATE TABLE stg_sysstatus (id_anfr TEXT, systeme_upper TEXT, statut TEXT, emr_dt TEXT, PRIMARY KEY(id_anfr, systeme_upper))",
        "CREATE TABLE stg_station_final (id_anfr TEXT PRIMARY KEY, operateur_id INTEGER, operator_label TEXT, latitude REAL, longitude REAL, statut_id INTEGER, statut_label TEXT, adm_id INTEGER, date_imp TEXT, date_ser TEXT, date_mod TEXT, adresse TEXT, code_insee TEXT, tech_mask INTEGER, band_mask INTEGER, has_active INTEGER, azimuts TEXT, azimuts_fh TEXT)",
        "CREATE TABLE stg_arcep (id_anfr TEXT, operator_upper TEXT, nidt TEXT, is_zb INTEGER, PRIMARY KEY(id_anfr, operator_upper))",
        "CREATE TABLE stg_details (id_anfr TEXT PRIMARY KEY, details TEXT)",
    )
}
