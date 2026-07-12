package fr.geotower.data.build

/** Parametres de la base radio produite (version comparable, estampilles metadata). */
class RadioBuildConfig(
    val version: String,
    val zipVersion: String? = null,
    val dateMajAnfr: String = "Inconnue",
)

/** Comptes retournes apres construction (diagnostic / tests). */
data class RadioBuildResult(val sites: Int, val details: Int)

/**
 * Construit `geotower_fr_radio.db` (sites ANFR **non-mobiles** : broadcast, reseaux prives,
 * ferroviaire, transport, radar, satellite, FH) a partir des memes fichiers SUP_* que
 * [GeoTowerDbBuilder]. Port fidele de `build_radio_db` (docs/server/fr_radio_db_builder.py),
 * adapte a l'appareil par une **strategie de staging SQLite** : SUP_STATION / SUP_ANTENNE /
 * SUP_EMETTEUR / SUP_BANDE (plusieurs millions de lignes) sont poses dans des tables `stg_r_*`
 * puis agreges par scans indexes. Seuls les accumulateurs bornes au nombre de **supports
 * non-mobiles** (~200 k) vivent en RAM. Le staging est purge puis `VACUUM` en fin de build afin
 * que le fichier installe ne contienne que les tables finales, compactes.
 *
 * L'enrichissement ARCOM (programmes radio FM/DAB) est **optionnel** cote serveur et n'est pas
 * porte ici : la ligne `Programmes:` du blob detail est simplement absente, sans impact schema.
 */
object RadioDbBuilder {

    private const val EMIT_EVERY = 100_000L

    private class Agg(val admId: Int?) {
        var serviceMask = 0
        var systemMask = 0
        var emitterCount = 0
        val systems = LinkedHashMap<String, Int>()
        var minKhz: Int? = null
        var maxKhz: Int? = null
        var freqRangeCount = 0
        val freqSamples = ArrayList<String>()

        fun addFreqRange(startKhz: Int, endKhz: Int, rawLabel: String) {
            val low = minOf(startKhz, endKhz)
            val high = maxOf(startKhz, endKhz)
            minKhz = minKhz?.let { minOf(it, low) } ?: low
            maxKhz = maxKhz?.let { maxOf(it, high) } ?: high
            freqRangeCount++
            if (freqSamples.size < 24 && !freqSamples.contains(rawLabel)) freqSamples.add(rawLabel)
        }
    }

    private class AntennaInfo {
        var count = 0
        val samples = ArrayList<String>()
        val typeIds = HashSet<Int>()
    }

    /** Insertion par lots (borne la RAM a un lot), reprise de [GeoTowerDbBuilder]. */
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
        config: RadioBuildConfig,
        onProgress: (percent: Int, processed: Long) -> Unit = { _, _ -> },
        allowedServiceMask: Int = -1,
    ): RadioBuildResult {
        prepareSchema(db)
        val sink = RadioStagingSink(db, references.typeAntenne)
        for (row in sources.stations) sink.station(row)
        for (row in sources.supports) sink.support(row)
        for (row in sources.antennes) sink.antenne(row)
        for (row in sources.emetteurs) sink.emetteur(row)
        for (row in sources.bandes) sink.bande(row)
        sink.finish()
        return buildFromStaging(db, references, config, onProgress, allowedServiceMask)
    }

    /** Pragmas + schema final + tables de staging. Appele par [build] et par le pipeline (sink partage). */
    fun prepareSchema(db: SqlDatabase) {
        db.execSql("PRAGMA journal_mode = OFF")
        db.execSql("PRAGMA synchronous = OFF")
        db.execSql("PRAGMA temp_store = FILE")
        RadioDbSchema.CREATE_TABLE_STATEMENTS.forEach { db.execSql(it) }
        RadioDbSchema.STAGING_STATEMENTS.forEach { db.execSql(it) }
    }

    /**
     * Sink de staging radio : replique les 5 boucles SUP -> stg_r_*. Utilise soit en standalone (via
     * [build]) soit pilote par [GeoTowerDbBuilder] pendant le parse mobile (le ZIP n'est parse qu'UNE
     * fois pour les deux bases). [finish] flush les lots puis materialise la vue support-unique + l'index.
     */
    class RadioStagingSink(
        private val db: SqlDatabase,
        private val typeLabels: Map<String, String>,
    ) : SupRowSink {
        private val stationInserter = BatchInserter(db, "INSERT OR REPLACE INTO stg_r_station VALUES (?, ?)")
        private val supportInserter =
            BatchInserter(db, "INSERT OR REPLACE INTO stg_r_support VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
        private val antenneInserter = BatchInserter(db, "INSERT OR REPLACE INTO stg_r_antenne VALUES (?, ?, ?, ?, ?)")
        private val emetteurInserter = BatchInserter(db, "INSERT INTO stg_r_emetteur VALUES (?, ?, ?, ?)")
        private val bandeInserter = BatchInserter(db, "INSERT INTO stg_r_bande VALUES (?, ?, ?, ?)")

        override fun station(row: AnfrCsvRow) {
            val sta = AnfrParsing.normalizeIdAnfr(row.get("STA_NM_ANFR"))
            if (sta.isEmpty()) return
            stationInserter.add(listOf(sta, AnfrParsing.intOrNone(row.get("ADM_ID"))))
        }

        override fun support(row: AnfrCsvRow) {
            val sta = AnfrParsing.normalizeIdAnfr(row.get("STA_NM_ANFR"))
            val sup = AnfrParsing.cleanText(row.get("SUP_ID"))
            if (sta.isEmpty() || sup.isEmpty()) return
            val latE6 = RadioParsing.dmsToE6(
                row.get("COR_NB_DG_LAT"), row.get("COR_NB_MN_LAT"), row.get("COR_NB_SC_LAT"), row.get("COR_CD_NS_LAT"),
            )
            val lonE6 = RadioParsing.dmsToE6(
                row.get("COR_NB_DG_LON"), row.get("COR_NB_MN_LON"), row.get("COR_NB_SC_LON"), row.get("COR_CD_EW_LON"),
            )
            if (latE6 == null || lonE6 == null) return
            val heightRaw = AnfrParsing.cleanText(row.get("SUP_NM_HAUT"))
            val heightDm = if (heightRaw.isNotEmpty()) {
                Math.round((AnfrParsing.floatOrNone(heightRaw) ?: 0.0) * 10.0).toInt()
            } else {
                null
            }
            val address = listOf(
                AnfrParsing.cleanText(row.get("ADR_LB_LIEU")),
                AnfrParsing.cleanText(row.get("ADR_LB_ADD1")),
                AnfrParsing.cleanText(row.get("ADR_LB_ADD2")),
                AnfrParsing.cleanText(row.get("ADR_LB_ADD3")),
                AnfrParsing.cleanText(row.get("ADR_NM_CP")),
            ).filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { null }
            supportInserter.add(
                listOf(
                    sta, sup, latE6, lonE6,
                    AnfrParsing.intOrNone(row.get("NAT_ID")), AnfrParsing.intOrNone(row.get("TPO_ID")),
                    heightDm, AnfrParsing.cleanText(row.get("COM_CD_INSEE")).ifEmpty { null }, address,
                ),
            )
        }

        override fun antenne(row: AnfrCsvRow) {
            val sta = AnfrParsing.normalizeIdAnfr(row.get("STA_NM_ANFR"))
            val aer = AnfrParsing.cleanText(row.get("AER_ID"))
            val sup = AnfrParsing.cleanText(row.get("SUP_ID"))
            if (sta.isEmpty() || aer.isEmpty() || sup.isEmpty()) return
            val taeId = AnfrParsing.intOrNone(row.get("TAE_ID"))
            val typeLabel = if (taeId != null) typeLabels[taeId.toString()] ?: "TAE_ID $taeId" else "Type inconnu"
            val az = AnfrParsing.cleanText(row.get("AER_NB_AZIMUT")).ifEmpty { "N/A" }
            val height = AnfrParsing.cleanText(row.get("AER_NB_ALT_BAS")).ifEmpty { "N/A" }
            antenneInserter.add(listOf(aer, sta, sup, taeId, "$typeLabel: $az deg (${height}m)"))
        }

        override fun emetteur(row: AnfrCsvRow) {
            val sta = AnfrParsing.normalizeIdAnfr(row.get("STA_NM_ANFR"))
            val emr = AnfrParsing.cleanText(row.get("EMR_ID"))
            if (sta.isEmpty() || emr.isEmpty()) return
            val aer = AnfrParsing.cleanText(row.get("AER_ID"))
            val system = AnfrParsing.cleanText(row.get("EMR_LB_SYSTEME")).ifEmpty { "Inconnu" }
            emetteurInserter.add(listOf(emr, sta, aer, system))
        }

        override fun bande(row: AnfrCsvRow) {
            val emr = AnfrParsing.cleanText(row.get("EMR_ID"))
            if (emr.isEmpty()) return
            bandeInserter.add(
                listOf(
                    emr, AnfrParsing.cleanText(row.get("BAN_NB_F_DEB")), AnfrParsing.cleanText(row.get("BAN_NB_F_FIN")),
                    AnfrParsing.cleanText(row.get("BAN_FG_UNITE")).ifEmpty { "M" },
                ),
            )
        }

        override fun finish() {
            stationInserter.flush()
            supportInserter.flush()
            antenneInserter.flush()
            emetteurInserter.flush()
            bandeInserter.flush()
            db.execSql(
                "INSERT INTO stg_r_single (sta, sup) SELECT sta, sup FROM stg_r_support GROUP BY sta HAVING COUNT(*) = 1",
            )
            db.execSql("CREATE INDEX ix_stg_r_bande_emr ON stg_r_bande(emr)")
        }
    }

    /**
     * Calcul + emission (classification -> tables finales), depuis un staging deja peuple.
     *
     * [allowedServiceMask] filtre les sites emis par categorie de service (bits [RadioServiceMasks]) :
     * un site n'est emis que si `serviceMask & allowedServiceMask != 0`. Defaut -1 = tous les services.
     * Permet de ne construire que « Radio/TV » (bit BROADCAST) ou que le « non-mobile technique »
     * (NON_BROADCAST) selon le choix de l'utilisateur.
     */
    fun buildFromStaging(
        db: SqlDatabase,
        references: AnfrReferences,
        config: RadioBuildConfig,
        onProgress: (percent: Int, processed: Long) -> Unit = { _, _ -> },
        allowedServiceMask: Int = -1,
    ): RadioBuildResult {
        val actorLabels = references.exploitant
        val natureLabels = references.nature
        val ownerLabels = references.proprietaire
        val typeLabels = references.typeAntenne

        // 6/ Classification des emetteurs : exclut le mobile public (2G/3G/4G/5G + FH des operateurs),
        // agrege service/system par support non-mobile, et enregistre les emetteurs retenus (bandes).
        onProgress(62, 0L)
        val aggregates = HashMap<String, Agg>()
        val selInserter = BatchInserter(db, "INSERT OR REPLACE INTO stg_r_sel VALUES (?, ?, ?)")
        var classified = 0L
        db.query(
            "SELECT e.sta AS e_sta, e.emr AS emr, e.system AS system, a.sta AS a_sta, a.sup AS a_sup, " +
                "sp.sta AS sp_exists, sg.sup AS single_sup, st.adm_id AS adm_id " +
                "FROM stg_r_emetteur e " +
                "LEFT JOIN stg_r_antenne a ON e.aer = a.aer " +
                "LEFT JOIN stg_r_support sp ON sp.sta = a.sta AND sp.sup = a.sup " +
                "LEFT JOIN stg_r_single sg ON sg.sta = e.sta " +
                "LEFT JOIN stg_r_station st ON st.sta = e.sta",
        ) { row ->
            val eSta = row.getString("e_sta") ?: ""
            val system = row.getString("system") ?: "Inconnu"
            val admId = row.getIntOrNull("adm_id")
            val actorLabel = admId?.let { actorLabels[it.toString()] } ?: ""
            val sysKey = RadioClassifier.systemKey(system)
            val excluded = (RadioClassifier.isMobileLikeSystem(system) || sysKey == "FH") &&
                RadioClassifier.isPublicMobileOperator(actorLabel)
            if (!excluded) {
                val aSup = row.getString("a_sup")
                var keySta: String? = null
                var keySup: String? = null
                if (aSup != null) {
                    if (row.getString("sp_exists") != null) {
                        keySta = row.getString("a_sta") ?: eSta
                        keySup = aSup
                    }
                } else {
                    val single = row.getString("single_sup")
                    if (single != null) {
                        keySta = eSta
                        keySup = single
                    }
                }
                if (keySta != null && keySup != null) {
                    val key = compositeKey(keySta, keySup)
                    val agg = aggregates.getOrPut(key) { Agg(admId) }
                    agg.serviceMask = agg.serviceMask or RadioClassifier.serviceFor(system, actorLabel)
                    agg.systemMask = agg.systemMask or RadioClassifier.systemBit(sysKey)
                    agg.emitterCount++
                    agg.systems[system] = (agg.systems[system] ?: 0) + 1
                    row.getString("emr")?.let { selInserter.add(listOf(it, keySta, keySup)) }
                }
            }
            if (++classified % EMIT_EVERY == 0L) onProgress(75, classified)
        }
        selInserter.flush()

        // 7/ Bandes des emetteurs retenus -> min/max/compte/echantillons de frequences par support.
        onProgress(78, 0L)
        var bandMatched = 0L
        db.query(
            "SELECT s.sta AS sta, s.sup AS sup, b.f_deb AS f_deb, b.f_fin AS f_fin, b.unite AS unite " +
                "FROM stg_r_bande b JOIN stg_r_sel s ON b.emr = s.emr",
        ) { row ->
            val agg = aggregates[compositeKey(row.getString("sta") ?: "", row.getString("sup") ?: "")]
            if (agg != null) {
                val unite = row.getString("unite") ?: "M"
                val startRaw = row.getString("f_deb") ?: ""
                val endRaw = row.getString("f_fin") ?: ""
                val start = RadioParsing.frequencyToKhz(startRaw, unite)
                val end = RadioParsing.frequencyToKhz(endRaw, unite)
                if (start != null && end != null) {
                    agg.addFreqRange(start, end, RadioParsing.formatFreqRange(startRaw, endRaw, unite))
                }
            }
            if (++bandMatched % EMIT_EVERY == 0L) onProgress(84, bandMatched)
        }

        // 8/ Antennes des supports non-mobiles -> compte + echantillons (<=12) + types utilises.
        onProgress(85, 0L)
        val antennaInfo = HashMap<String, AntennaInfo>()
        val usedTae = HashSet<Int>()
        db.query("SELECT sta, sup, tae_id, sample FROM stg_r_antenne") { row ->
            val key = compositeKey(row.getString("sta") ?: "", row.getString("sup") ?: "")
            if (aggregates.containsKey(key)) {
                val info = antennaInfo.getOrPut(key) { AntennaInfo() }
                info.count++
                row.getIntOrNull("tae_id")?.let { info.typeIds.add(it); usedTae.add(it) }
                if (info.samples.size < 12) info.samples.add(row.getString("sample") ?: "")
            }
        }

        // 9/ Emission des tables finales en streamant les supports (jointure RAM sur les agregats).
        onProgress(90, 0L)
        val siteInserter = BatchInserter(
            db, "INSERT INTO non_mobile_site VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        )
        val detailInserter = BatchInserter(db, "INSERT INTO non_mobile_detail VALUES (?, ?, ?)")
        val usedAdm = HashSet<Int>()
        val usedNat = HashSet<Int>()
        val usedTpo = HashSet<Int>()
        var emitted = 0L
        db.query(
            "SELECT sta, sup, lat_e6, lon_e6, nat_id, tpo_id, height_dm, code_insee, address FROM stg_r_support",
        ) { row ->
            val sta = row.getString("sta") ?: ""
            val sup = row.getString("sup") ?: ""
            val agg = aggregates[compositeKey(sta, sup)]
            // Filtre par categorie de service : un site avec au moins un service demande est emis.
            if (agg != null && (agg.serviceMask and allowedServiceMask) != 0) {
                val natId = row.getIntOrNull("nat_id")
                val tpoId = row.getIntOrNull("tpo_id")
                val heightDm = row.getIntOrNull("height_dm")
                val info = antennaInfo[compositeKey(sta, sup)]
                siteInserter.add(
                    listOf(
                        sta, sup, agg.admId, row.getIntOrNull("lat_e6"), row.getIntOrNull("lon_e6"),
                        natId, tpoId, heightDm, row.getString("code_insee"),
                        agg.serviceMask, agg.systemMask, agg.emitterCount, info?.count ?: 0,
                        agg.freqRangeCount, agg.minKhz, agg.maxKhz,
                    ),
                )
                val detail = detailText(agg, natId, tpoId, heightDm, row.getString("address"), info, actorLabels, natureLabels, ownerLabels)
                FrequencyDetailsEncoder.encode(detail)?.let { detailInserter.add(listOf(sta, sup, it)) }
                agg.admId?.let { usedAdm.add(it) }
                natId?.let { usedNat.add(it) }
                tpoId?.let { usedTpo.add(it) }
            }
            if (++emitted % EMIT_EVERY == 0L) onProgress(96, emitted)
        }
        siteInserter.flush()
        detailInserter.flush()

        // 10/ Referentiels (uniquement les ids utilises) + metadonnees.
        db.insertBatch(
            "INSERT INTO ref_actor VALUES (?, ?)",
            usedAdm.sorted().map { listOf(it, actorLabels[it.toString()] ?: "ADM_ID $it") },
        )
        db.insertBatch(
            "INSERT INTO ref_nature VALUES (?, ?)",
            usedNat.sorted().map { listOf(it, natureLabels[it.toString()] ?: "NAT_ID $it") },
        )
        db.insertBatch(
            "INSERT INTO ref_owner VALUES (?, ?)",
            usedTpo.sorted().map { listOf(it, ownerLabels[it.toString()] ?: "TPO_ID $it") },
        )
        db.insertBatch(
            "INSERT INTO ref_type_antenne VALUES (?, ?)",
            usedTae.sorted().map { listOf(it, typeLabels[it.toString()] ?: "TAE_ID $it") },
        )
        db.insertBatch(
            "INSERT INTO metadata VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            listOf(
                listOf(
                    config.version, RadioDbSchema.SCHEMA_VERSION, RadioDbSchema.COUNTRY_CODE,
                    RadioDbSchema.COUNTRY_NAME, RadioDbSchema.SOURCE, config.dateMajAnfr,
                    config.zipVersion, siteInserter.total,
                ),
            ),
        )

        // 11/ Purge du staging + index + compactage : le fichier installe ne garde que les tables finales.
        onProgress(98, 0L)
        RadioDbSchema.STAGING_TABLES.forEach { db.execSql("DROP TABLE IF EXISTS $it") }
        RadioDbSchema.CREATE_INDEX_STATEMENTS.forEach { db.execSql(it) }
        db.execSql("PRAGMA journal_mode = DELETE")
        db.execSql("VACUUM")
        onProgress(100, 0L)

        return RadioBuildResult(siteInserter.total, detailInserter.total)
    }

    /** Port de `detail_for` : lignes `Cle: valeur` (valeurs vides omises), lues par `RadioRepository`. */
    private fun detailText(
        agg: Agg,
        natId: Int?,
        tpoId: Int?,
        heightDm: Int?,
        address: String?,
        antenna: AntennaInfo?,
        actorLabels: Map<String, String>,
        natureLabels: Map<String, String>,
        ownerLabels: Map<String, String>,
    ): String {
        val actor = agg.admId?.let { actorLabels[it.toString()] } ?: "ADM_ID ${agg.admId}"
        val nature = natId?.let { natureLabels[it.toString()] } ?: "NAT_ID $natId"
        val owner = tpoId?.let { ownerLabels[it.toString()] } ?: "TPO_ID $tpoId"
        val systems = agg.systems.entries
            .sortedByDescending { it.value }
            .joinToString(", ") { "${it.key} x${it.value}" }
        var freqs = agg.freqSamples.joinToString(", ")
        if (agg.freqRangeCount > agg.freqSamples.size) {
            val extra = agg.freqRangeCount - agg.freqSamples.size
            freqs = if (freqs.isNotEmpty()) "$freqs, +$extra" else ""
        }
        val antennaText = antenna?.samples?.joinToString("; ") ?: ""
        val lines = listOf(
            "Acteur: $actor",
            "Familles: ${RadioClassifier.serviceNames(agg.serviceMask)}",
            "Systemes: $systems",
            "Support: $nature; proprietaire $owner; hauteur_dm=${heightDm ?: ""}",
            "Adresse: ${address ?: ""}",
            "Frequences: $freqs",
            "Programmes: ",
            "Antennes: $antennaText",
        )
        return lines.filter { it.isNotEmpty() && !it.endsWith(": ") }.joinToString("\n")
    }

    /** Cle composite (sta, sup) pour les accumulateurs RAM ; separateur absent des identifiants ANFR. */
    private fun compositeKey(sta: String, sup: String): String = "$sta\u0001$sup"
}
