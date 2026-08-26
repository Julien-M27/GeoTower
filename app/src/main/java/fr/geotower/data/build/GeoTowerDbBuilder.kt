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

    /**
     * Accumulateur compact des stations connues par l'observatoire hebdomadaire.
     *
     * L'ancienne implementation gardait un objet mutable par station, avec un objet
     * [StationMasks], les champs Kotlin et les noeuds d'une [LinkedHashMap]. Sur un export national,
     * cette representation coute beaucoup plus que les seules donnees utiles. Les identifiants et
     * libelles restent necessairement des chaines, mais les valeurs numeriques vivent ici dans des
     * tableaux primitifs. Le resultat est ecrit dans `stg_station_final` une fois les cinq sources
     * consommees, comme auparavant.
     */
    private class StationAccumulator(initialCapacity: Int = 16_384) {
        // Les identifiants ANFR sont numeriques dans l'immense majorite des cas. Cette table ouverte
        // evite un noeud HashMap et le boxing d'un Int par station. Les rares identifiants textuels
        // restent couverts par le repli [textIndexes].
        private var numericKeys = LongArray(32_768) { EMPTY_KEY }
        private var numericValues = IntArray(32_768) { NO_INDEX }
        private var numericCount = 0
        private val textIndexes = HashMap<String, Int>(initialCapacity / 16)
        private var ids = arrayOfNulls<String>(initialCapacity)
        private var operatorIds = IntArray(initialCapacity)
        private var operatorLabels = arrayOfNulls<String>(initialCapacity)
        private var latitudes = DoubleArray(initialCapacity)
        private var longitudes = DoubleArray(initialCapacity)
        private var statusIds = IntArray(initialCapacity)
        private var statusLabels = arrayOfNulls<String>(initialCapacity)
        private var admIds = IntArray(initialCapacity) { NO_INT }
        private var dateImplantations = arrayOfNulls<String>(initialCapacity)
        private var dateServices = arrayOfNulls<String>(initialCapacity)
        private var dateModifications = arrayOfNulls<String>(initialCapacity)
        private var addresses = arrayOfNulls<String>(initialCapacity)
        private var inseeCodes = arrayOfNulls<String>(initialCapacity)
        private var masks = LongArray(initialCapacity)
        private var active = ByteArray(initialCapacity)
        private var azimuths = arrayOfNulls<String>(initialCapacity)
        private var fhAzimuths = arrayOfNulls<String>(initialCapacity)
        private var size = 0

        fun indexOf(idAnfr: String): Int {
            val numericKey = idAnfr.toLongOrNull()
            return if (numericKey != null) findNumeric(numericKey) else textIndexes[idAnfr] ?: NO_INDEX
        }

        /**
         * Recherche directe pour les lignes SUP brutes. Les identifiants numeriques sont indexes
         * par leur valeur, donc les zeros de remplissage n'ont pas besoin d'etre construits avant
         * la recherche. Cela evite `normalizeIdAnfr` sur plusieurs millions de lignes dont la
         * station n'est finalement pas conservee.
         */
        fun indexOfRaw(idAnfr: String?): Int {
            val raw = idAnfr?.trim().orEmpty()
            if (raw.isEmpty()) return NO_INDEX
            val numericKey = raw.toLongOrNull()
            return if (numericKey != null) findNumeric(numericKey) else textIndexes[raw] ?: NO_INDEX
        }

        fun add(
            idAnfr: String,
            operatorId: Int,
            operatorLabel: String,
            latitude: Double,
            longitude: Double,
            statusId: Int,
            statusLabel: String,
        ): Int {
            check(indexOf(idAnfr) == NO_INDEX) { "Station deja presente: $idAnfr" }
            ensureCapacity(size + 1)
            val index = size++
            val numericKey = idAnfr.toLongOrNull()
            if (numericKey != null) {
                ensureNumericCapacity(numericCount + 1)
                putNumeric(numericKey, index)
                numericCount++
            } else {
                textIndexes[idAnfr] = index
            }
            ids[index] = idAnfr
            operatorIds[index] = operatorId
            operatorLabels[index] = operatorLabel
            latitudes[index] = latitude
            longitudes[index] = longitude
            statusIds[index] = statusId
            statusLabels[index] = statusLabel
            return index
        }

        fun size(): Int = size

        fun setActive(index: Int) {
            active[index] = 1
        }

        fun updateGeneration(index: Int, generation: String?) {
            masks[index] = RadioMaskComputer.updateMasksFromGeneration(masks[index], generation)
        }

        fun updateSystemAndBand(index: Int, system: String?, fStartMhz: Double?, fEndMhz: Double?) {
            masks[index] = RadioMaskComputer.updateMasksFromSystemAndBand(masks[index], system, fStartMhz, fEndMhz)
        }

        fun addFhMask(index: Int) {
            masks[index] = masks[index] or packMasks(RadioFilterMasks.TECH_FH, RadioFilterMasks.BAND_FH)
        }

        fun setAdmId(index: Int, value: Int?) {
            admIds[index] = value ?: NO_INT
        }

        fun setDates(index: Int, implantation: String?, service: String?, modification: String?) {
            dateImplantations[index] = implantation
            dateServices[index] = service
            dateModifications[index] = modification
        }

        fun setAddress(index: Int, value: String?) {
            addresses[index] = value
        }

        fun setInseeCode(index: Int, value: String?) {
            inseeCodes[index] = value
        }

        fun setAzimuths(index: Int, mobile: String?, fh: String?) {
            azimuths[index] = mobile
            fhAzimuths[index] = fh
        }

        fun id(index: Int): String = ids[index] ?: error("Station sans identifiant")
        fun operatorId(index: Int): Int = operatorIds[index]
        fun operatorLabel(index: Int): String = operatorLabels[index].orEmpty()
        fun latitude(index: Int): Double = latitudes[index]
        fun longitude(index: Int): Double = longitudes[index]
        fun statusId(index: Int): Int = statusIds[index]
        fun statusLabel(index: Int): String = statusLabels[index].orEmpty()
        fun admId(index: Int): Int? = admIds[index].takeUnless { it == NO_INT }
        fun dateImplantation(index: Int): String? = dateImplantations[index]
        fun dateService(index: Int): String? = dateServices[index]
        fun dateModification(index: Int): String? = dateModifications[index]
        fun address(index: Int): String? = addresses[index]
        fun inseeCode(index: Int): String? = inseeCodes[index]
        fun techMask(index: Int): Int = masks[index].toInt()
        fun bandMask(index: Int): Int = (masks[index] ushr 32).toInt()
        fun hasActive(index: Int): Int = active[index].toInt()
        fun azimuths(index: Int): String? = azimuths[index]
        fun fhAzimuths(index: Int): String? = fhAzimuths[index]

        fun clear() {
            numericKeys.fill(EMPTY_KEY)
            numericValues.fill(NO_INDEX)
            numericCount = 0
            textIndexes.clear()
            ids.fill(null)
            operatorLabels.fill(null)
            statusLabels.fill(null)
            dateImplantations.fill(null)
            dateServices.fill(null)
            dateModifications.fill(null)
            addresses.fill(null)
            inseeCodes.fill(null)
            azimuths.fill(null)
            fhAzimuths.fill(null)
            size = 0

            // The accumulator is not reused after the final staging insert. Drop
            // its backing arrays so later statistics phases do not retain memory
            // that was needed only while merging station sources.
            numericKeys = LongArray(0)
            numericValues = IntArray(0)
            ids = emptyArray()
            operatorIds = IntArray(0)
            operatorLabels = emptyArray()
            latitudes = DoubleArray(0)
            longitudes = DoubleArray(0)
            statusIds = IntArray(0)
            statusLabels = emptyArray()
            admIds = IntArray(0)
            dateImplantations = emptyArray()
            dateServices = emptyArray()
            dateModifications = emptyArray()
            addresses = emptyArray()
            inseeCodes = emptyArray()
            masks = LongArray(0)
            active = ByteArray(0)
            azimuths = emptyArray()
            fhAzimuths = emptyArray()
        }

        private fun ensureCapacity(required: Int) {
            if (required <= ids.size) return
            val oldCapacity = ids.size
            val newCapacity = maxOf(required, oldCapacity * 2)
            ids = ids.copyOf(newCapacity)
            operatorIds = operatorIds.copyOf(newCapacity)
            operatorLabels = operatorLabels.copyOf(newCapacity)
            latitudes = latitudes.copyOf(newCapacity)
            longitudes = longitudes.copyOf(newCapacity)
            statusIds = statusIds.copyOf(newCapacity)
            statusLabels = statusLabels.copyOf(newCapacity)
            admIds = admIds.copyOf(newCapacity).also { it.fill(NO_INT, oldCapacity, newCapacity) }
            dateImplantations = dateImplantations.copyOf(newCapacity)
            dateServices = dateServices.copyOf(newCapacity)
            dateModifications = dateModifications.copyOf(newCapacity)
            addresses = addresses.copyOf(newCapacity)
            inseeCodes = inseeCodes.copyOf(newCapacity)
            masks = masks.copyOf(newCapacity)
            active = active.copyOf(newCapacity)
            azimuths = azimuths.copyOf(newCapacity)
            fhAzimuths = fhAzimuths.copyOf(newCapacity)
        }

        private fun findNumeric(key: Long): Int {
            var slot = hashSlot(key)
            while (true) {
                val stored = numericKeys[slot]
                if (stored == EMPTY_KEY) return NO_INDEX
                if (stored == key) return numericValues[slot]
                slot = (slot + 1) and (numericKeys.size - 1)
            }
        }

        private fun putNumeric(key: Long, value: Int) {
            var slot = hashSlot(key)
            while (numericKeys[slot] != EMPTY_KEY) {
                if (numericKeys[slot] == key) {
                    numericValues[slot] = value
                    return
                }
                slot = (slot + 1) and (numericKeys.size - 1)
            }
            numericKeys[slot] = key
            numericValues[slot] = value
        }

        private fun ensureNumericCapacity(required: Int) {
            if (required * 10 < numericKeys.size * 7) return
            val oldKeys = numericKeys
            val oldValues = numericValues
            numericKeys = LongArray(oldKeys.size * 2) { EMPTY_KEY }
            numericValues = IntArray(oldValues.size * 2) { NO_INDEX }
            for (slot in oldKeys.indices) {
                if (oldKeys[slot] != EMPTY_KEY) putNumeric(oldKeys[slot], oldValues[slot])
            }
        }

        private fun hashSlot(key: Long): Int {
            var value = key
            value = (value xor (value ushr 33)) * -49064778989728563L
            value = (value xor (value ushr 33)) * -4265267296055464877L
            value = value xor (value ushr 33)
            return value.toInt() and (numericKeys.size - 1)
        }

        private companion object {
            const val NO_INDEX = -1
            const val NO_INT = Int.MIN_VALUE
            const val EMPTY_KEY = Long.MIN_VALUE

            fun packMasks(techMask: Int, bandMask: Int): Long =
                (bandMask.toLong() shl 32) or (techMask.toLong() and 0xffffffffL)
        }
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
        supSink: SupRowSink = SupRowSink.None,
        /**
         * Appele des que le CSV hebdomadaire a ete lu en entier (etape 1) : il n'est plus jamais
         * relu ensuite. Sert a rendre les ~173 Mo de l'observatoire au systeme avant les etapes
         * lourdes, ou se situe le pic de stockage.
         */
        onWeeklyConsumed: () -> Unit = {},
        sourceCounts: AnfrSourceCounts = AnfrSourceCounts(),
        onProgressDetail: ((BuildProgressDetail) -> Unit)? = null,
    ): BuildResult {
        val operateurIds = IdRegistry()
        val systemeIds = IdRegistry()
        val statutIds = IdRegistry()
        operateurIds.getId("Inconnu")
        systemeIds.getId("Inconnu")
        statutIds.getId("Inconnu")

        val stations = StationAccumulator()
        val usedNat = HashSet<Int>()
        val usedTpo = HashSet<Int>()
        val usedAdm = HashSet<Int>()
        val usedTae = HashSet<Int>()
        val usedCommunes = HashSet<String>()
        var dateMajAnfr = "Inconnue"

        fun report(
            phase: BuildPhase,
            processed: Long = 0L,
            source: String? = null,
            total: Long = -1L,
        ) {
            onProgress(phase, processed)
            onProgressDetail?.invoke(BuildProgressDetail(phase, source, processed, total))
        }

        db.execSql("PRAGMA journal_mode = OFF")
        db.execSql("PRAGMA synchronous = OFF")
        db.execSql("PRAGMA temp_store = FILE")
        GeoTowerDbSchema.CREATE_TABLE_STATEMENTS.forEach { db.execSql(it) }
        db.execSql(GeoTowerDbSchema.CREATE_ROOM_MASTER_TABLE)
        stagingStatements(db.stagingPrefix).forEach { db.execSql(it) }
        report(BuildPhase.READING_STATIONS, source = SOURCE_OBSERVATOIRE, total = sourceCounts.weekly)

        // 1/ CSV hebdomadaire : construit l'accumulateur station (RAM) + statuts par systeme (disque).
        val sysInserter = BatchInserter(db, "INSERT OR REPLACE INTO stg_sysstatus VALUES (?, ?, ?, ?, ?)")
        var weeklyRows = 0L
        for (row in sources.weekly) {
            val currentRow = ++weeklyRows
            if (dateMajAnfr == "Inconnue") {
                val d = AnfrParsing.cleanText(row.get("date_maj"))
                if (d.isNotEmpty()) dateMajAnfr = d
            }
            val idAnfr = AnfrParsing.normalizeIdAnfr(row.get("sta_nm_anfr"))
            if (idAnfr.isEmpty()) continue
            var stationIndex = stations.indexOf(idAnfr)
            if (stationIndex == -1) {
                val (lat, lon) = AnfrParsing.parseCoordinates(row.get("coordonnees"))
                val operateurId = operateurIds.getId(row.get("adm_lb_nom"))
                val statutId = statutIds.getId(row.get("statut"))
                stationIndex = stations.add(
                    idAnfr,
                    operateurId,
                    operateurIds.getLabel(operateurId),
                    lat,
                    lon,
                    statutId,
                    statutIds.getLabel(statutId),
                )
            }
            if (AnfrParsing.isActiveStatus(row.get("statut"))) stations.setActive(stationIndex)
            stations.updateGeneration(stationIndex, row.get("generation"))
            val sysName = AnfrParsing.cleanText(row.get("emr_lb_systeme"))
            if (sysName.isNotEmpty()) {
                val statutId = statutIds.getId(row.get("statut"))
                sysInserter.add(
                    listOf(
                        idAnfr, sysName.uppercase(), sysName, statutIds.getLabel(statutId),
                        AnfrParsing.cleanText(row.get("emr_dt")).ifEmpty { null },
                    ),
                )
            }
            if (currentRow % EMIT_EVERY == 0L) {
                report(BuildPhase.READING_STATIONS, currentRow, SOURCE_OBSERVATOIRE, sourceCounts.weekly)
            }
        }
        sysInserter.flush()
        report(BuildPhase.READING_STATIONS, weeklyRows, SOURCE_OBSERVATOIRE, sourceCounts.weekly)
        onWeeklyConsumed()

        report(BuildPhase.READING_SUPPORTS, source = SOURCE_SUP_STATION, total = sourceCounts.stations)
        // 2/ SUP_STATION : dates + exploitant.
        var stationRows = 0L
        for (row in sources.stations) {
            val currentRow = ++stationRows
            supSink.station(row)
            val idAnfr = AnfrParsing.normalizeIdAnfr(row.get("sta_nm_anfr"))
            val stationIndex = stations.indexOf(idAnfr)
            if (stationIndex == -1) continue
            val admId = AnfrParsing.intOrNone(row.get("adm_id"))
            stations.setAdmId(stationIndex, admId)
            stations.setDates(
                stationIndex,
                AnfrParsing.cleanText(row.get("dte_implantation")).ifEmpty { null },
                AnfrParsing.cleanText(row.get("dte_en_service")).ifEmpty { null },
                AnfrParsing.cleanText(row.get("dte_modif")).ifEmpty { null },
            )
            if (admId != null) usedAdm.add(admId)
            if (currentRow % EMIT_EVERY == 0L) {
                report(BuildPhase.READING_SUPPORTS, currentRow, SOURCE_SUP_STATION, sourceCounts.stations)
            }
        }
        report(BuildPhase.READING_SUPPORTS, stationRows, SOURCE_SUP_STATION, sourceCounts.stations)

        // 3/ SUP_BANDE -> staging, puis frequences pre-formatees par emetteur.
        report(BuildPhase.READING_SUPPORTS, source = SOURCE_SUP_BANDE, total = sourceCounts.bandes)
        val bandeInserter = BatchInserter(db, "INSERT INTO stg_bande VALUES (?, ?, ?, ?, ?, ?)")
        var bandeRows = 0L
        for (row in sources.bandes) {
            val currentRow = ++bandeRows
            supSink.bande(row)
            val emrId = AnfrParsing.cleanText(row.get("emr_id"))
            val fDebRaw = AnfrParsing.cleanText(row.get("ban_nb_f_deb"))
            val fFinRaw = AnfrParsing.cleanText(row.get("ban_nb_f_fin"))
            val fDeb = AnfrParsing.floatOrNone(fDebRaw)
            val fFin = AnfrParsing.floatOrNone(fFinRaw)
            val unite = AnfrParsing.cleanText(row.get("ban_fg_unite")).ifEmpty { "M" }
            if (emrId.isNotEmpty() && fDeb != null && fFin != null) {
                bandeInserter.add(listOf(emrId, fDeb, fFin, unite, fDebRaw, fFinRaw))
            }
            if (currentRow % EMIT_EVERY == 0L) {
                report(BuildPhase.READING_SUPPORTS, currentRow, SOURCE_SUP_BANDE, sourceCounts.bandes)
            }
        }
        bandeInserter.flush()
        report(BuildPhase.READING_SUPPORTS, bandeRows, SOURCE_SUP_BANDE, sourceCounts.bandes)
        // Index cree APRES le chargement (perf) : requis par buildEmrFreqs (ORDER BY emr_id) et le scan masques.
        db.execSql("CREATE INDEX ${db.stagingPrefix}ix_stg_bande_emr ON stg_bande(emr_id)")
        buildEmrFreqs(db)

        // 4/ SUP_EMETTEUR -> staging (filtre aux stations connues), enregistre les systemes.
        report(BuildPhase.READING_SUPPORTS, source = SOURCE_SUP_EMETTEUR, total = sourceCounts.emetteurs)
        val emetteurInserter = BatchInserter(db, "INSERT INTO stg_emetteur VALUES (?, ?, ?, ?)")
        var emetteurRows = 0L
        for (row in sources.emetteurs) {
            val currentRow = ++emetteurRows
            supSink.emetteur(row)
            val idAnfr = AnfrParsing.normalizeIdAnfr(row.get("sta_nm_anfr"))
            if (stations.indexOf(idAnfr) == -1) continue
            val emrId = AnfrParsing.cleanText(row.get("emr_id"))
            if (emrId.isEmpty()) continue
            val aerId = AnfrParsing.cleanText(row.get("aer_id")).ifEmpty { null }
            val systeme = AnfrParsing.cleanText(row.get("emr_lb_systeme")).ifEmpty { "Inconnu" }
            systemeIds.getId(systeme)
            emetteurInserter.add(listOf(idAnfr, emrId, aerId, systeme))
            if (currentRow % EMIT_EVERY == 0L) {
                report(BuildPhase.READING_SUPPORTS, currentRow, SOURCE_SUP_EMETTEUR, sourceCounts.emetteurs)
            }
        }
        emetteurInserter.flush()
        report(BuildPhase.READING_SUPPORTS, emetteurRows, SOURCE_SUP_EMETTEUR, sourceCounts.emetteurs)
        // Index crees APRES le chargement (perf) : requis par le scan masques, applyDetails et applyAzimuts.
        db.execSql("CREATE INDEX ${db.stagingPrefix}ix_stg_emetteur_emr ON stg_emetteur(emr_id)")
        db.execSql("CREATE INDEX ${db.stagingPrefix}ix_stg_emetteur_aer ON stg_emetteur(aer_id)")
        // Index COUVRANT : applyDetails scanne `e` ordonne par id_anfr en lisant systeme/emr_id/aer_id
        // directement dans l'index (aucun acces rowid), et le GROUP BY id_anfr se fait EN FLUX sur cet
        // ordre (pas de tri temporaire). Le prefixe id_anfr couvre aussi les autres usages de l'index.
        db.execSql("CREATE INDEX ${db.stagingPrefix}ix_stg_emetteur_id ON stg_emetteur(id_anfr, systeme, emr_id, aer_id)")
        db.execSql(
            "INSERT OR REPLACE INTO stg_fh_aer SELECT DISTINCT aer_id FROM stg_emetteur " +
                "WHERE aer_id IS NOT NULL AND aer_id != '' AND UPPER(systeme) LIKE '%FH%'",
        )

        report(BuildPhase.COMPUTING_FREQUENCIES, source = SOURCE_FREQUENCIES)
        // 5/ Masques technologie/bande : scan emetteur (x bande) -> accumulateur RAM.
        var maskRows = 0L
        db.query(
            "SELECT e.id_anfr AS id_anfr, e.systeme AS systeme, b.f_deb AS f_deb, b.f_fin AS f_fin, b.unite AS unite " +
                "FROM stg_emetteur e LEFT JOIN stg_bande b ON e.emr_id = b.emr_id",
        ) { row ->
            val stationIndex = stations.indexOf(row.getString("id_anfr").orEmpty())
            if (stationIndex != -1) {
                val systeme = row.getString("systeme")
                val fDeb = row.getDouble("f_deb")
                if (fDeb == null) {
                    if (AnfrParsing.cleanText(systeme).uppercase().contains("FH")) {
                        stations.addFhMask(stationIndex)
                    }
                } else {
                    val unite = row.getString("unite") ?: "M"
                    stations.updateSystemAndBand(
                        stationIndex,
                        systeme,
                        AnfrParsing.frequencyToMhz(fDeb, unite),
                        AnfrParsing.frequencyToMhz(row.getDouble("f_fin"), unite),
                    )
                }
            }
            if (++maskRows % EMIT_EVERY == 0L) {
                report(BuildPhase.COMPUTING_FREQUENCIES, maskRows, SOURCE_FREQUENCIES)
            }
        }
        report(BuildPhase.COMPUTING_FREQUENCIES, maskRows, SOURCE_FREQUENCIES)

        // stg_bande n'est plus utile apres le calcul des masques (les frequences pre-formatees
        // sont deja dans stg_emr_freqs) : on la supprime tot pour liberer du disque (SUP_BANDE
        // ~200 Mo decompresse). Les pages liberees seront reutilisees par les inserts suivants.
        db.execSql("DROP TABLE IF EXISTS ${db.staging("stg_bande")}")

        report(BuildPhase.READING_SUPPORTS, source = SOURCE_SUP_ANTENNE, total = sourceCounts.antennes)
        // 6/ SUP_ANTENNE -> staging (physique pre-formatee), puis marquage FH.
        val antenneInserter = BatchInserter(db, "INSERT OR REPLACE INTO stg_antenne VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
        // Lien station <-> azimut : sur un site MUTUALISE, l'ANFR declare le MEME aer_id sur les deux
        // stations (SFR 02927 00xxx / Bouygues 02927 50xxx par ex.). `stg_antenne` ayant aer_id pour cle,
        // une seule station y survit et l'autre perdrait tous ses azimuts. Ce staging garde TOUTES les
        // paires, comme la boucle du script serveur (anfr_azimuts[id_anfr] alimente par ligne lue).
        val azimutStaInserter = BatchInserter(db, "INSERT INTO stg_antenne_sta VALUES (?, ?, ?, ?)")
        var antenneRows = 0L
        for (row in sources.antennes) {
            val currentRow = ++antenneRows
            supSink.antenne(row)
            val idAnfr = AnfrParsing.normalizeIdAnfr(row.get("sta_nm_anfr"))
            if (stations.indexOf(idAnfr) == -1) continue
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
            // Tag [DIM: ...] optionnel, meme convention que [AER_ID: ...] : les ecrans le retirent
            // de la chaine et l'affichent a cote du type de panneau.
            val dimensionText = AnfrParsing.antennaDimensionText(row.get("aer_nb_dimension"))
            val physique = "$typeTexte : $azimutText° (${hauteurText}m) [AER_ID: $aerId]" +
                if (dimensionText != null) " [DIM: $dimensionText]" else ""
            if (taeId != null) usedTae.add(taeId)
            antenneInserter.add(listOf(aerId, idAnfr, supId, taeId, azimut, hauteurBas, 0, physique))
            if (azimut != null) azimutStaInserter.add(listOf(idAnfr, aerId, azimut, 0))
            if (currentRow % EMIT_EVERY == 0L) {
                report(BuildPhase.READING_SUPPORTS, currentRow, SOURCE_SUP_ANTENNE, sourceCounts.antennes)
            }
        }
        antenneInserter.flush()
        azimutStaInserter.flush()
        report(BuildPhase.READING_SUPPORTS, antenneRows, SOURCE_SUP_ANTENNE, sourceCounts.antennes)
        db.execSql("UPDATE stg_antenne SET is_fh = 1 WHERE aer_id IN (SELECT aer_id FROM stg_fh_aer)")
        db.execSql("UPDATE stg_antenne_sta SET is_fh = 1 WHERE aer_id IN (SELECT aer_id FROM stg_fh_aer)")
        // Index COUVRANT cree APRES le chargement (perf) : applyAzimuts scanne (id_anfr, azimut, is_fh)
        // en flux, sans tri temporaire malgre le ORDER BY id_anfr.
        db.execSql("CREATE INDEX ${db.stagingPrefix}ix_stg_antenne_sta ON stg_antenne_sta(id_anfr, azimut, is_fh)")

        report(BuildPhase.COMPUTING_ANTENNAS, source = SOURCE_ANTENNA_STAGING)
        // 7/ Azimuts (mobile / FH) par station : scan ordonne -> accumulateur RAM.
        applyAzimuts(db, stations) { processed ->
            report(BuildPhase.COMPUTING_ANTENNAS, processed, SOURCE_ANTENNA_STAGING)
        }
        // Le lien station <-> azimut ne sert plus : on libere le disque avant la suite du build.
        db.execSql("DROP TABLE IF EXISTS ${db.staging("stg_antenne_sta")}")

        report(BuildPhase.READING_SUPPORTS, source = SOURCE_SUP_SUPPORT, total = sourceCounts.supports)
        // 8/ SUP_SUPPORT -> staging + adresses/communes sur l'accumulateur station.
        var supportRows = 0L
        db.insertInTransaction("INSERT OR REPLACE INTO stg_support VALUES (?, ?, ?, ?, ?)") { statement ->
            for (row in sources.supports) {
                val currentRow = ++supportRows
                if (supSink !== SupRowSink.None) supSink.support(row)
                val stationIndex = stations.indexOfRaw(row.get("sta_nm_anfr"))
                if (stationIndex == -1) continue
                val idAnfr = stations.id(stationIndex)
                val supId = AnfrParsing.cleanText(row.get("sup_id"))
                if (supId.isEmpty()) continue
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
                if (adresse.isNotEmpty()) stations.setAddress(stationIndex, adresse)
                if (codeInsee != null) stations.setInseeCode(stationIndex, codeInsee)

                statement.clearBindings()
                statement.bindString(1, idAnfr)
                statement.bindString(2, supId)
                if (natId == null) statement.bindNull(3) else statement.bindLong(3, natId.toLong())
                if (tpoId == null) statement.bindNull(4) else statement.bindLong(4, tpoId.toLong())
                if (hauteur == null) statement.bindNull(5) else statement.bindDouble(5, hauteur)
                statement.executeInsert()
                if (currentRow % EMIT_EVERY == 0L) {
                    report(BuildPhase.READING_SUPPORTS, currentRow, SOURCE_SUP_SUPPORT, sourceCounts.supports)
                }
            }
        }
        report(BuildPhase.READING_SUPPORTS, supportRows, SOURCE_SUP_SUPPORT, sourceCounts.supports)
        supSink.finish()

        // 9/ Fige l'accumulateur station et l'ARCEP sur disque, puis libere la RAM.
        val stationInserter = BatchInserter(
            db,
            "INSERT OR REPLACE INTO stg_station_final VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        )
        for (stationIndex in 0 until stations.size()) {
            stationInserter.add(
                listOf(
                    stations.id(stationIndex),
                    stations.operatorId(stationIndex),
                    stations.operatorLabel(stationIndex),
                    stations.latitude(stationIndex),
                    stations.longitude(stationIndex),
                    stations.statusId(stationIndex),
                    stations.statusLabel(stationIndex),
                    stations.admId(stationIndex),
                    stations.dateImplantation(stationIndex),
                    stations.dateService(stationIndex),
                    stations.dateModification(stationIndex),
                    stations.address(stationIndex),
                    stations.inseeCode(stationIndex),
                    stations.techMask(stationIndex),
                    stations.bandMask(stationIndex),
                    stations.hasActive(stationIndex),
                    stations.azimuths(stationIndex),
                    stations.fhAzimuths(stationIndex),
                ),
            )
        }
        stationInserter.flush()
        val stationCount = stations.size()
        stations.clear()

        val arcepInserter = BatchInserter(db, "INSERT OR REPLACE INTO stg_arcep VALUES (?, ?, ?, ?)")
        for ((key, meta) in arcep) {
            arcepInserter.add(listOf(key.first, key.second, meta.nidt, meta.isZb))
        }
        arcepInserter.flush()

        report(BuildPhase.BUILDING_DETAILS, source = SOURCE_DETAILS, total = stationCount.toLong())
        // 10/ details_frequences par station : group_concat en flux (une ligne par station).
        //     10a d'abord : les systemes annonces par le seul CSV hebdomadaire, que 10b fusionne.
        applyAnnouncedDetails(db)
        applyDetails(db) { processed ->
            report(BuildPhase.BUILDING_DETAILS, processed, SOURCE_DETAILS, stationCount.toLong())
        }

        // Ces tables de staging ne servent plus a l'emission finale (SUP_EMETTEUR ~120 Mo) :
        // on libere avant d'ecrire les tables definitives.
        listOf("stg_emetteur", "stg_emr_freqs", "stg_sysstatus", "stg_fh_aer", "stg_details_extra").forEach {
            db.execSql("DROP TABLE IF EXISTS ${db.staging(it)}")
        }

        report(BuildPhase.INSERTING, source = SOURCE_FINAL_TABLES)
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

        report(BuildPhase.COMPUTING_STATS, source = SOURCE_STATS)
        // 13/ Stats courantes (radio_stat_current), exigees non vides par le validateur.
        AnfrStatsBuilder.populateCurrentStats(db)
        // 13bis/ Stats par departement. Sans referentiel joignable, les compteurs sont ecrits
        // quand meme et seuls les ratios de densite/population restent vides.
        DepartmentStatsBuilder.populateDepartmentStats(db, references.departments)

        report(BuildPhase.FINALIZING, source = SOURCE_FINALIZING)
        // 14/ Nettoyage du staging (libere le disque avant finalisation).
        STAGING_TABLES.forEach { db.execSql("DROP TABLE IF EXISTS ${db.staging(it)}") }

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
    private fun applyAzimuts(
        db: SqlDatabase,
        stations: StationAccumulator,
        onProgress: (Long) -> Unit = {},
    ) {
        var currentId: String? = null
        val mobile = LinkedHashSet<String>()
        val fh = LinkedHashSet<String>()
        fun flush() {
            val stationIndex = currentId?.let { stations.indexOf(it) } ?: -1
            if (stationIndex != -1) {
                stations.setAzimuths(stationIndex, azimutsToString(mobile), azimutsToString(fh))
                if (fh.isNotEmpty()) {
                    stations.addFhMask(stationIndex)
                }
            }
            mobile.clear()
            fh.clear()
        }
        var processed = 0L
        db.query("SELECT id_anfr, azimut, is_fh FROM stg_antenne_sta WHERE azimut IS NOT NULL ORDER BY id_anfr") { row ->
            val id = row.getString("id_anfr") ?: ""
            if (id != currentId) {
                flush()
                currentId = id
            }
            val azimut = row.getIntOrNull("azimut")
            if (azimut != null) {
                if (row.getInt("is_fh") == 1) fh.add(azimut.toString()) else mobile.add(azimut.toString())
            }
            if (++processed % EMIT_EVERY == 0L) onProgress(processed)
        }
        flush()
        onProgress(processed)
    }

    /**
     * Construit `details_frequences` par station (emetteur x bandes x physique x statut).
     *
     * Le premier port utilisait `group_concat` avec `GROUP BY id_anfr`. Sur un stockage lent,
     * SQLite devait maintenir de gros agregats et faire de nombreux acces indexes aux tables
     * attachees ; la phase montait a 25 minutes alors que la meme base passait en 13 minutes sur
     * un autre appareil. La requete ci-dessous ne fait qu'un parcours ordonne par station : la
     * deduplication et le tri, deja necessaires pour reproduire le builder serveur, restent en
     * memoire pour UNE station seulement.
     */
    /**
     * Requete d'agregation de [applyDetails]. `internal` pour que le test puisse verifier son PLAN
     * d'execution : c'est lui, et non le resultat, qui porte la propriete critique (aucun tri
     * temporaire sur ~2,5 M de lignes).
     *
     * `stg_details_extra` a AU PLUS une ligne par station : la jointure ne multiplie donc pas les
     * lignes emetteur, et MAX() sur une valeur constante dans le groupe la restitue telle quelle.
     */
    internal fun detailsSql(): String {
        // Miroir SQL de la ligne "$systeme : $freqs | $statut | $date | $phys" ; aucun operande NULL
        // (systeme force a 'Inconnu' au parse, le reste en COALESCE) donc `||` ne produit jamais NULL.
        val line = "e.systeme || ' : ' || COALESCE(f.freqs_text, '') || ' | ' || " +
            "COALESCE(s.statut, sf.statut_label, 'Inconnu') || ' | ' || " +
            "COALESCE(s.emr_dt, '') || ' | ' || COALESCE(ap.physique, 'Azimut non specifie')"
        return "SELECT e.id_anfr AS id_anfr, group_concat($line, char(10)) AS details, " +
            "MAX(x.details) AS annonces " +
            "FROM stg_emetteur e " +
            "LEFT JOIN stg_emr_freqs f ON e.emr_id = f.emr_id " +
            "LEFT JOIN stg_antenne ap ON e.aer_id = ap.aer_id " +
            "LEFT JOIN stg_sysstatus s ON e.id_anfr = s.id_anfr AND s.systeme_upper = UPPER(e.systeme) " +
            "LEFT JOIN stg_details_extra x ON e.id_anfr = x.id_anfr " +
            "JOIN stg_station_final sf ON e.id_anfr = sf.id_anfr " +
            "GROUP BY e.id_anfr"
    }

    /**
     * Flux brut des lignes de details, ordonne par la cle de l'index couvrant des emetteurs.
     * L'ordre secondaire n'est pas necessaire : l'agregateur Kotlin trie les lignes de la station.
     */
    internal fun detailsStreamSql(): String =
        "SELECT e.id_anfr AS id_anfr, e.systeme AS systeme, " +
            "COALESCE(f.freqs_text, '') AS freqs, " +
            "COALESCE(s.statut, sf.statut_label, 'Inconnu') AS statut, " +
            "COALESCE(s.emr_dt, '') AS emr_dt, " +
            "COALESCE(ap.physique, 'Azimut non specifie') AS physique " +
            "FROM stg_emetteur e " +
            "LEFT JOIN stg_emr_freqs f ON e.emr_id = f.emr_id " +
            "LEFT JOIN stg_antenne ap ON e.aer_id = ap.aer_id " +
            "LEFT JOIN stg_sysstatus s ON e.id_anfr = s.id_anfr AND s.systeme_upper = UPPER(e.systeme) " +
            "JOIN stg_station_final sf ON e.id_anfr = sf.id_anfr " +
            "ORDER BY e.id_anfr"

    /** Requete d'agregation de [applyAnnouncedDetails], exposee pour la meme raison. */
    internal fun announcedDetailsSql(): String {
        val line = "s.systeme || ' :  | ' || COALESCE(s.statut, 'Inconnu') || ' | ' || " +
            "COALESCE(s.emr_dt, '') || ' | Azimut non specifie'"
        return "SELECT s.id_anfr AS id_anfr, group_concat($line, char(10)) AS details " +
            "FROM stg_sysstatus s " +
            "WHERE NOT EXISTS (SELECT 1 FROM stg_emetteur e " +
            "WHERE e.id_anfr = s.id_anfr AND UPPER(e.systeme) = s.systeme_upper) " +
            "GROUP BY s.id_anfr"
    }

    private fun applyDetails(db: SqlDatabase, onProgress: (processed: Long) -> Unit) {
        val inserter = BatchInserter(db, "INSERT OR REPLACE INTO stg_details VALUES (?, ?)")
        val encoder = FrequencyDetailsEncoder.Session()
        // Une seule valeur par station. La joindre au flux emetteur la repetait pour chaque ligne
        // et gonflait inutilement les CursorWindow Android, avec un cout tres variable selon le
        // stockage du telephone.
        val announcedByStation = HashMap<String, String>()
        val lines = sortedSetOf<String>()
        var currentId: String? = null
        var announced: String? = null
        var emitted = 0L

        fun flushStation() {
            val id = currentId ?: return
            normalizeDetailLines(lines, announced)?.let { text ->
                encoder.encode(text)?.let { inserter.add(listOf(id, it)) }
            }
            lines.clear()
            announced = null
            if (++emitted % EMIT_EVERY == 0L) onProgress(emitted)
        }

        try {
            db.query("SELECT id_anfr, details FROM stg_details_extra") { row ->
                val id = row.getString("id_anfr")
                val details = row.getString("details")
                if (id != null && details != null) announcedByStation[id] = details
            }
            db.query(detailsStreamSql()) { row ->
                val id = row.getString("id_anfr") ?: return@query
                if (id != currentId) {
                    flushStation()
                    currentId = id
                    announced = announcedByStation.remove(id)
                }
                val line = (row.getString("systeme") ?: "Inconnu") + " : " +
                    (row.getString("freqs") ?: "") + " | " +
                    (row.getString("statut") ?: "Inconnu") + " | " +
                    (row.getString("emr_dt") ?: "") + " | " +
                    (row.getString("physique") ?: "Azimut non specifie")
                lines.add(line)
            }
            flushStation()

            // Stations que le CSV hebdomadaire connait alors que le ZIP mensuel n'a AUCUN emetteur pour
            // elles (site tout juste declare) : elles restent dans la map car aucun emetteur ne les
            // a retirees pendant le scan principal.
            for ((id, details) in announcedByStation) {
                normalizeDetails(null, details)?.let { text ->
                    encoder.encode(text)?.let { inserter.add(listOf(id, it)) }
                }
            }
        } finally {
            encoder.close()
        }
        inserter.flush()
    }

    /**
     * Lignes des emetteurs du ZIP et lignes annoncees par le CSV, fusionnees comme le `set()` unique
     * du builder serveur : dedup + tri alphabetique sur l'ensemble des lignes de la station.
     */
    private fun normalizeDetails(
        details: String?,
        annonces: String?,
    ): String? {
        if (details.isNullOrEmpty() && annonces.isNullOrEmpty()) return null
        val lines = sortedSetOf<String>()
        details?.split('\n')?.forEach { lines.add(it) }
        return normalizeDetailLines(lines, annonces)
    }

    private fun normalizeDetailLines(
        lines: MutableSet<String>,
        annonces: String?,
    ): String? {
        annonces?.split('\n')?.forEach { lines.add(it) }
        if (lines.isEmpty()) return null
        return lines.joinToString("\n")
    }

    /**
     * Systemes connus du CSV hebdomadaire mais absents du ZIP mensuel -> une ligne de detail sans
     * bandes ni azimut, l'observatoire ne les portant pas.
     *
     * POURQUOI : le ZIP mensuel a jusqu'a cinq semaines de retard sur l'observatoire. Un systeme
     * declare entre les deux publications allume deja `tech_mask` (donc le bandeau « 5G - 4G » de la
     * fiche) sans avoir la moindre ligne dans le tableau des emetteurs. Port de la seconde boucle de
     * `build_frequency_details_for_station` (docs/server/build_fr_anfr_db.py).
     *
     * Le GROUP BY se fait EN FLUX sur l'index automatique de la cle primaire (id_anfr en tete), donc
     * sans tri temporaire — meme exigence que [applyDetails], verifiee par `GeoTowerDbBuilderTest`.
     */
    private fun applyAnnouncedDetails(db: SqlDatabase) {
        val inserter = BatchInserter(db, "INSERT OR REPLACE INTO stg_details_extra VALUES (?, ?)")
        db.query(announcedDetailsSql()) { row ->
            val id = row.getString("id_anfr")
            val details = row.getString("details")
            if (id != null && details != null) inserter.add(listOf(id, details))
        }
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

    private const val SOURCE_OBSERVATOIRE = "observatoire.csv"
    private const val SOURCE_SUP_STATION = "SUP_STATION.txt (ZIP ANFR)"
    private const val SOURCE_SUP_BANDE = "SUP_BANDE.txt (ZIP ANFR)"
    private const val SOURCE_SUP_EMETTEUR = "SUP_EMETTEUR.txt (ZIP ANFR)"
    private const val SOURCE_SUP_ANTENNE = "SUP_ANTENNE.txt (ZIP ANFR)"
    private const val SOURCE_SUP_SUPPORT = "SUP_SUPPORT.txt (ZIP ANFR)"
    private const val SOURCE_FREQUENCIES = "stg_emetteur x stg_bande"
    private const val SOURCE_ANTENNA_STAGING = "stg_antenne_sta"
    private const val SOURCE_DETAILS = "stg_emetteur -> details_frequences"
    private const val SOURCE_FINAL_TABLES = "stg_station_final -> tables finales"
    private const val SOURCE_STATS = "tables finales -> statistiques"
    private const val SOURCE_FINALIZING = "staging SQLite -> base finale"

    private val STAGING_TABLES = listOf(
        "stg_bande", "stg_emr_freqs", "stg_emetteur", "stg_fh_aer", "stg_antenne", "stg_antenne_sta",
        "stg_support", "stg_sysstatus", "stg_station_final", "stg_arcep", "stg_details", "stg_details_extra",
    )

    // NOTE perf : les index SECONDAIRES (ix_stg_*) ne sont PAS crees ici mais APRES le chargement en
    // masse de chaque table (cf. build()). Inserer des millions de lignes dans une table deja indexee
    // maintient le B-tree a chaque ligne (anti-pattern) ; construire l'index une fois, apres, est bien
    // plus rapide. Les cles PRIMARY KEY restent (necessaires aux INSERT OR REPLACE).
    //
    // `prefix` place ces tables dans la base de staging attachee quand il y en a une
    // (cf. [SqlDatabase.stagingPrefix]) ; vide, elles vivent dans le fichier final comme avant.
    internal fun stagingStatements(prefix: String) = listOf(
        "CREATE TABLE ${prefix}stg_bande (emr_id TEXT, f_deb REAL, f_fin REAL, unite TEXT, f_deb_raw TEXT, f_fin_raw TEXT)",
        "CREATE TABLE ${prefix}stg_emr_freqs (emr_id TEXT PRIMARY KEY, freqs_text TEXT)",
        "CREATE TABLE ${prefix}stg_emetteur (id_anfr TEXT, emr_id TEXT, aer_id TEXT, systeme TEXT)",
        "CREATE TABLE ${prefix}stg_fh_aer (aer_id TEXT PRIMARY KEY)",
        "CREATE TABLE ${prefix}stg_antenne (aer_id TEXT PRIMARY KEY, id_anfr TEXT, sup_id TEXT, tae_id INTEGER, azimut INTEGER, hauteur_bas REAL, is_fh INTEGER, physique TEXT)",
        // Sans cle primaire : un aer_id mutualise DOIT y apparaitre une fois par station (cf. build()).
        "CREATE TABLE ${prefix}stg_antenne_sta (id_anfr TEXT, aer_id TEXT, azimut INTEGER, is_fh INTEGER)",
        "CREATE TABLE ${prefix}stg_support (id_anfr TEXT, sup_id TEXT, nat_id INTEGER, tpo_id INTEGER, hauteur REAL, PRIMARY KEY(id_anfr, sup_id))",
        // `systeme` garde le libelle d'ORIGINE a cote de la cle majuscule : c'est lui qui est ecrit
        // dans les details quand le ZIP mensuel ne connait pas encore ce systeme (cf. applyAnnouncedDetails).
        "CREATE TABLE ${prefix}stg_sysstatus (id_anfr TEXT, systeme_upper TEXT, systeme TEXT, statut TEXT, emr_dt TEXT, PRIMARY KEY(id_anfr, systeme_upper))",
        "CREATE TABLE ${prefix}stg_details_extra (id_anfr TEXT PRIMARY KEY, details TEXT)",
        "CREATE TABLE ${prefix}stg_station_final (id_anfr TEXT PRIMARY KEY, operateur_id INTEGER, operator_label TEXT, latitude REAL, longitude REAL, statut_id INTEGER, statut_label TEXT, adm_id INTEGER, date_imp TEXT, date_ser TEXT, date_mod TEXT, adresse TEXT, code_insee TEXT, tech_mask INTEGER, band_mask INTEGER, has_active INTEGER, azimuts TEXT, azimuts_fh TEXT)",
        "CREATE TABLE ${prefix}stg_arcep (id_anfr TEXT, operator_upper TEXT, nidt TEXT, is_zb INTEGER, PRIMARY KEY(id_anfr, operator_upper))",
        "CREATE TABLE ${prefix}stg_details (id_anfr TEXT PRIMARY KEY, details TEXT)",
    )
}
