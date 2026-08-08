package fr.geotower.data.build

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Jeu de sources ANFR **synthetique mais complet**, ecrit comme de vrais fichiers (CSV hebdomadaire
 * + ZIP mensuel) pour que les instantanes de [BuilderOutputSnapshotTest] traversent toute la chaine
 * reelle : lecture ZIP en flux, detection d'encodage/separateur, decoupage CSV avec guillemets,
 * puis les deux builders.
 *
 * Chaque ligne est la pour couvrir un chemin que les optimisations prevues risquent de casser
 * (cf. `docs/agent-ia-plan-optimisation-generation-locale-db-2026-08-05.md`) :
 *  - station 1 : cas nominal + ARCEP + adresse composee, champ **entre guillemets contenant un `;`**
 *    et accents (chemin lent du decoupage + detection UTF-8) + `AER_NB_DIMENSION` -> tag `[DIM:]` ;
 *  - station 2 : statut non actif (`En projet`) et pas de dimension declaree ;
 *  - station 3 : deux emetteurs dont un multi-bandes -> masques cumules, azimuts multiples, tri
 *    des lignes de detail ;
 *  - station 4 : deux lignes d'emetteur STRICTEMENT identiques -> deduplication des details ;
 *  - station 5 : emetteur FH **sans bande** -> masque FH par le chemin `f_deb IS NULL`,
 *    `azimuts_fh` et `antenne.is_fh` ;
 *  - station 6 : outre-mer (INSEE 97xxx) -> suffixe operateur des stats + ARCEP `is_zb` ;
 *  - station 7 : emetteur sans AER et **sans support** -> station sans adresse ni antenne ;
 *  - station 8 : systeme annonce par l'observatoire et ABSENT du ZIP mensuel -> ligne de detail
 *    completee sans bandes ni azimut, a cote de l'emetteur publie normalement ;
 *  - station 9 : connue du seul observatoire, aucun fichier SUP_* -> details entierement annonces ;
 *  - stations 0292700369 / 0292750303 : site MUTUALISE (meme `aer_id` sur deux stations) ;
 *  - station ABC123 : `sta_nm_anfr` **non numerique** -> chemin de repli de toute structure de cle
 *    compacte (le futur remplacement de l'accumulateur `stations` par des primitives) ;
 *  - stations 10 a 13 : sites NON mobiles (DAB, GSM-R, radar, FM sur un operateur mobile) —
 *    absents de l'observatoire, donc ignores par le build mobile et seuls retenus par le radio ;
 *  - station 999 : presente dans les fichiers SUP mais absente de l'observatoire -> doit etre
 *    filtree par le build mobile.
 */
object BuildSnapshotFixture {

    /** Estampilles figees : les instantanes doivent etre reproductibles a l'identique. */
    const val VERSION = "20260601_1200"
    const val ZIP_VERSION = "20260601-export-etalab-data.zip"
    const val QUARTERLY_VERSION = "2026-T2"
    const val DATE_MAJ_ANFR = "2026-06-01"

    /** CSV hebdomadaire (observatoire) : liste maitresse des stations MOBILES. */
    fun weeklyCsvFile(): File = tempFile(
        "snapshot_weekly", ".csv",
        table(
            "sta_nm_anfr;coordonnees;adm_lb_nom;statut;generation;emr_lb_systeme;emr_dt;date_maj",
            listOf("1", "48.85 2.35", "Orange", "En service", "4G", "LTE 800", "2026-01-01", DATE_MAJ_ANFR),
            listOf("2", "43.60 1.44", "SFR", "En projet", "5G", "5G NR 3500", "", DATE_MAJ_ANFR),
            listOf("3", "48.00 2.00", "Bouygues Telecom", "En service", "4G", "LTE 800", "2026-02-01", DATE_MAJ_ANFR),
            listOf("3", "48.00 2.00", "Bouygues Telecom", "En service", "4G", "LTE 2600", "2026-02-02", DATE_MAJ_ANFR),
            listOf("4", "47.00 1.00", "Free Mobile", "En service", "4G", "LTE 800", "2026-03-01", DATE_MAJ_ANFR),
            listOf("5", "45.00 3.00", "Orange", "En service", "", "FH 18 GHz", "2026-04-01", DATE_MAJ_ANFR),
            listOf("6", "16.24 -61.53", "Orange", "En service", "4G", "LTE 800", "2026-05-01", DATE_MAJ_ANFR),
            listOf("7", "44.00 5.00", "SFR", "Techniquement operationnel", "5G", "5G NR 700", "2026-05-02", DATE_MAJ_ANFR),
            listOf("8", "44.31 3.10", "Orange", "En service", "4G", "LTE 800", "2026-06-03", DATE_MAJ_ANFR),
            listOf("8", "44.31 3.10", "Orange", "Projet approuve", "5G", "5G NR 2100", "", DATE_MAJ_ANFR),
            listOf("9", "45.10 3.20", "Free Mobile", "Projet approuve", "5G", "5G NR 3500", "", DATE_MAJ_ANFR),
            listOf("0292700369", "48.245 -4.480", "SFR", "En service", "4G", "LTE 2100", "2022-02-09", DATE_MAJ_ANFR),
            listOf("0292750303", "48.245 -4.480", "Bouygues Telecom", "En service", "4G", "LTE 2100", "2022-02-16", DATE_MAJ_ANFR),
            listOf("ABC123", "46.00 4.00", "Orange", "En service", "4G", "LTE 1800", "2026-06-02", DATE_MAJ_ANFR),
        ),
    )

    /** ZIP mensuel : les cinq tables SUP_* + les quatre referentiels, comme l'export etalab. */
    fun monthlyZipFile(): File = zipOf(
        "SUP_STATION.txt" to table(
            "STA_NM_ANFR;ADM_ID;DTE_IMPLANTATION;DTE_MODIF;DTE_EN_SERVICE",
            listOf("1", "5", "2020-01-01", "2026-05-01", "2020-06-01"),
            listOf("2", "6", "", "", ""),
            listOf("3", "7", "2019-01-01", "", "2019-02-01"),
            listOf("4", "8", "", "", ""),
            listOf("5", "5", "2018-01-01", "", "2018-02-01"),
            listOf("6", "5", "2021-01-01", "", "2021-03-01"),
            listOf("7", "6", "", "", ""),
            listOf("8", "5", "2019-01-01", "", "2019-02-01"),
            listOf("0292700369", "6", "2015-01-01", "", "2015-02-01"),
            listOf("0292750303", "7", "2016-01-01", "", "2016-02-01"),
            listOf("ABC123", "5", "2017-01-01", "", "2017-02-01"),
            listOf("10", "20", "2010-01-01", "", "2010-02-01"),
            listOf("11", "21", "2011-01-01", "", "2011-02-01"),
            listOf("12", "22", "2012-01-01", "", "2012-02-01"),
            listOf("13", "5", "2013-01-01", "", "2013-02-01"),
            listOf("999", "9", "", "", ""),
        ),
        // E6 (FH) n'a VOLONTAIREMENT aucune bande : c'est le chemin `f_deb IS NULL` des masques.
        "SUP_BANDE.txt" to table(
            "EMR_ID;BAN_NB_F_DEB;BAN_NB_F_FIN;BAN_FG_UNITE",
            listOf("E1", "791", "801", "M"),
            listOf("E2", "3400", "3800", "M"),
            listOf("E3", "791", "801", "M"),
            listOf("E4", "2500", "2570", "M"),
            listOf("E4", "2620", "2690", "M"),
            listOf("E5", "791", "801", "M"),
            listOf("E7", "1920", "2170", "M"),
            listOf("E8", "791", "801", "M"),
            listOf("E9", "1805", "1880", "M"),
            listOf("E10", "703", "733", "M"),
            listOf("E11", "791", "801", "M"),
            listOf("E20", "174", "230", "M"),
            listOf("E21", "921", "925", "M"),
            listOf("E22", "2700", "2900", "M"),
            listOf("E23", "87.5", "108", "M"),
        ),
        "SUP_EMETTEUR.txt" to table(
            "STA_NM_ANFR;EMR_ID;AER_ID;EMR_LB_SYSTEME",
            listOf("1", "E1", "AE1", "LTE 800"),
            listOf("2", "E2", "AE2", "5G NR 3500"),
            listOf("3", "E3", "AE3", "LTE 800"),
            listOf("3", "E4", "AE4", "LTE 2600"),
            listOf("4", "E5", "AE5", "LTE 800"),
            listOf("4", "E5", "AE5", "LTE 800"),
            listOf("5", "E6", "AE6", "FH 18 GHz"),
            listOf("6", "E8", "AE8", "LTE 800"),
            listOf("7", "E10", "", "5G NR 700"),
            // Station 8 : le ZIP ne porte QUE la 4G ; la 5G NR 2100 n'existe que dans l'observatoire.
            listOf("8", "E11", "AE11", "LTE 800"),
            listOf("0292700369", "E7", "1204115", "LTE 2100"),
            listOf("0292750303", "E7", "1204115", "LTE 2100"),
            listOf("ABC123", "E9", "AE9", "LTE 1800"),
            listOf("10", "E20", "AE20", "RDF T-DAB"),
            listOf("11", "E21", "AE21", "GSM R"),
            listOf("12", "E22", "AE22", "RDR"),
            listOf("13", "E23", "AE23", "FM"),
            listOf("999", "E99", "AE99", "LTE 800"),
        ),
        "SUP_ANTENNE.txt" to table(
            "STA_NM_ANFR;AER_ID;SUP_ID;TAE_ID;AER_NB_AZIMUT;AER_NB_ALT_BAS;AER_NB_DIMENSION",
            listOf("1", "AE1", "S1", "16", "120", "28", "1,5"),
            listOf("2", "AE2", "S2", "32", "240", "30", ""),
            listOf("3", "AE3", "S3", "16", "90", "20", ""),
            listOf("3", "AE4", "S3", "16", "270", "22", ""),
            listOf("4", "AE5", "S4", "16", "90", "20", ""),
            listOf("5", "AE6", "S5", "17", "45", "35", ""),
            listOf("6", "AE8", "S6", "16", "180", "25", ""),
            listOf("8", "AE11", "S8", "16", "185", "28,7", ""),
            listOf("0292700369", "1204115", "497932", "16", "330", "30,8", ""),
            listOf("0292750303", "1204115", "497932", "16", "330", "30,8", ""),
            listOf("0292700369", "1204117", "497932", "16", "90", "30,8", ""),
            listOf("0292750303", "1204117", "497932", "16", "90", "30,8", ""),
            listOf("0292750303", "9588616", "497932", "17", "235", "29,4", ""),
            listOf("ABC123", "AE9", "S9", "16", "60", "18", ""),
            listOf("10", "AE20", "S20", "10", "45", "120", ""),
            listOf("11", "AE21", "S21", "10", "0", "50", ""),
            listOf("12", "AE22", "S22", "10", "180", "60", ""),
            listOf("13", "AE23", "S23", "10", "270", "80", ""),
            listOf("999", "AE99", "S99", "16", "10", "10", ""),
        ),
        // Les colonnes COR_* (degres/minutes/secondes) ne servent qu'au build RADIO ; le build
        // mobile prend ses coordonnees dans l'observatoire.
        "SUP_SUPPORT.txt" to table(
            "STA_NM_ANFR;SUP_ID;NAT_ID;TPO_ID;SUP_NM_HAUT;COM_CD_INSEE;ADR_LB_LIEU;ADR_LB_ADD1;" +
                "ADR_LB_ADD2;ADR_LB_ADD3;ADR_NM_CP;COR_NB_DG_LAT;COR_NB_MN_LAT;COR_NB_SC_LAT;COR_CD_NS_LAT;" +
                "COR_NB_DG_LON;COR_NB_MN_LON;COR_NB_SC_LON;COR_CD_EW_LON",
            support("1", "S1", "23", "1", "30", "75056", quoted("Rue des Lilas; angle Avenue A"), "Bâtiment C", "75001", "48", "51", "0", "N", "2", "21", "0", "E"),
            support("2", "S2", "17", "2", "25", "31555", "Château d'eau", "", "31000", "43", "36", "0", "N", "1", "26", "0", "E"),
            support("3", "S3", "23", "1", "25", "75056", "", "", "", "48", "0", "0", "N", "2", "0", "0", "E"),
            support("4", "S4", "23", "1", "20", "29042", "", "", "", "47", "0", "0", "N", "1", "0", "0", "W"),
            support("5", "S5", "23", "2", "40", "75056", "", "", "", "45", "0", "0", "N", "3", "0", "0", "E"),
            support("6", "S6", "23", "1", "15", "97105", "Morne Rouge", "", "97100", "16", "14", "0", "N", "61", "32", "0", "W"),
            support("8", "S8", "23", "1", "30", "75056", "Puech des Aires", "", "75001", "44", "18", "0", "N", "3", "6", "0", "E"),
            support("0292700369", "497932", "23", "1", "31", "29042", "", "", "", "48", "14", "42", "N", "4", "28", "48", "W"),
            support("0292750303", "497932", "23", "1", "31", "29042", "", "", "", "48", "14", "42", "N", "4", "28", "48", "W"),
            support("ABC123", "S9", "23", "1", "12", "31555", "", "", "", "46", "0", "0", "N", "4", "0", "0", "E"),
            support("10", "S20", "23", "1", "200", "75056", "Emetteur Nord", "", "75001", "48", "52", "0", "N", "2", "20", "0", "E"),
            support("11", "S21", "23", "1", "30", "31555", "", "", "", "43", "37", "0", "N", "1", "27", "0", "E"),
            support("12", "S22", "23", "1", "40", "29042", "", "", "", "48", "1", "0", "N", "4", "30", "0", "W"),
            support("13", "S23", "23", "2", "10", "75056", "", "", "", "48", "53", "0", "N", "2", "19", "0", "E"),
            support("999", "S99", "23", "1", "10", "75056", "", "", "", "48", "0", "0", "N", "2", "0", "0", "E"),
        ),
        "SUP_NATURE.txt" to table(
            "NAT_ID;NAT_LB_NOM",
            listOf("23", "Pylone"),
            listOf("17", "Chateau d'eau"),
        ),
        "SUP_PROPRIETAIRE.txt" to table(
            "TPO_ID;TPO_LB",
            listOf("1", "Operateur"),
            listOf("2", "Collectivite"),
        ),
        "SUP_EXPLOITANT.txt" to table(
            "ADM_ID;ADM_LB_NOM",
            listOf("5", "Orange"),
            listOf("6", "SFR"),
            listOf("7", "Bouygues Telecom"),
            listOf("8", "Free Mobile"),
            listOf("9", "Exploitant inconnu"),
            listOf("20", "TDF"),
            listOf("21", "SNCF RESEAU"),
            listOf("22", "MIN DEFENSE"),
        ),
        "SUP_TYPE_ANTENNE.txt" to table(
            "TAE_ID;TAE_LB",
            listOf("10", "Antenne broadcast"),
            listOf("16", "Panneau"),
            listOf("17", "Parabole"),
            listOf("32", "Panneau 5G"),
        ),
    )

    fun communes(): Map<String, String> = mapOf(
        "75056" to "PARIS",
        "31555" to "TOULOUSE",
        "29042" to "CROZON",
        "97105" to "BASSE-TERRE",
    )

    /** Referentiel departemental : superficie + population -> les ratios des stats sont calcules. */
    fun departments(): Map<String, DepartmentReferenceRow> = mapOf(
        "75" to DepartmentReferenceRow("75", "Paris", "11", 105.4, 2_145_906, DepartmentStatsBuilder.POPULATION_YEAR),
        "31" to DepartmentReferenceRow("31", "Haute-Garonne", "76", 6309.3, 1_400_039, DepartmentStatsBuilder.POPULATION_YEAR),
        "29" to DepartmentReferenceRow("29", "Finistere", "53", 6733.0, 915_090, DepartmentStatsBuilder.POPULATION_YEAR),
        "971" to DepartmentReferenceRow("971", "Guadeloupe", "01", 1628.4, 384_315, DepartmentStatsBuilder.POPULATION_YEAR),
    )

    /** Enrichissement ARCEP : un site avec NIDT, un site en zone blanche sans NIDT. */
    fun arcep(): Map<Pair<String, String>, ArcepSiteMeta> = mapOf(
        ("0000000001" to "ORANGE") to ArcepSiteMeta("NIDT1", 0),
        ("0000000006" to "ORANGE") to ArcepSiteMeta(null, 1),
    )

    /** Ligne SUP_SUPPORT : les colonnes ADD2/ADD3, toujours vides ici, sont posees automatiquement. */
    private fun support(
        sta: String,
        sup: String,
        nat: String,
        tpo: String,
        haut: String,
        insee: String,
        lieu: String,
        add1: String,
        cp: String,
        dgLat: String,
        mnLat: String,
        scLat: String,
        nsLat: String,
        dgLon: String,
        mnLon: String,
        scLon: String,
        ewLon: String,
    ): List<String> = listOf(
        sta, sup, nat, tpo, haut, insee, lieu, add1, "", "", cp,
        dgLat, mnLat, scLat, nsLat, dgLon, mnLon, scLon, ewLon,
    )

    /** Champ CSV entre guillemets (contient le separateur) : force le chemin lent du decoupage. */
    private fun quoted(value: String): String = "\"$value\""

    /**
     * Assemble un fichier CSV et **verifie** que chaque ligne a exactement le nombre de colonnes de
     * l'entete : une fixture desalignee produirait un instantane faux mais vert.
     */
    private fun table(header: String, vararg rows: List<String>): String {
        val columns = header.split(';').size
        val lines = rows.map { fields ->
            require(fields.size == columns) {
                "Ligne a ${fields.size} colonnes pour un entete a $columns : $fields"
            }
            fields.joinToString(";")
        }
        return (listOf(header) + lines).joinToString("\n")
    }

    private fun tempFile(prefix: String, suffix: String, content: String): File =
        File.createTempFile(prefix, suffix).apply {
            deleteOnExit()
            writeText(content, Charsets.UTF_8)
        }

    private fun zipOf(vararg entries: Pair<String, String>): File {
        val file = File.createTempFile("snapshot_monthly", ".zip").apply { deleteOnExit() }
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return file
    }
}
