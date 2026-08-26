package fr.geotower.data.build

import fr.geotower.data.models.FrequencyDetailsCodec
import fr.geotower.data.models.RadioFilterMasks
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTowerDbBuilderTest {

    private fun row(vararg pairs: Pair<String, String?>): AnfrCsvRow = AnfrCsvRow.of(mapOf(*pairs))

    private fun sources(): AnfrSources = AnfrSources(
        weekly = listOf(
            row(
                "sta_nm_anfr" to "1", "coordonnees" to "48.85 2.35", "adm_lb_nom" to "Orange",
                "statut" to "En service", "generation" to "4G", "emr_lb_systeme" to "LTE 800",
                "emr_dt" to "2026-01-01", "date_maj" to "2026-06-01",
            ),
            row(
                "sta_nm_anfr" to "2", "coordonnees" to "43.60 1.44", "adm_lb_nom" to "SFR",
                "statut" to "En projet", "generation" to "5G", "emr_lb_systeme" to "5G NR 3500",
                "emr_dt" to "", "date_maj" to "2026-06-01",
            ),
        ),
        stations = listOf(
            row(
                "sta_nm_anfr" to "1", "adm_id" to "5", "dte_implantation" to "2020-01-01",
                "dte_modif" to "2026-05-01", "dte_en_service" to "2020-06-01",
            ),
            row("sta_nm_anfr" to "2", "adm_id" to "6"),
        ),
        bandes = listOf(
            row("emr_id" to "E1", "ban_nb_f_deb" to "791", "ban_nb_f_fin" to "801", "ban_fg_unite" to "M"),
            row("emr_id" to "E2", "ban_nb_f_deb" to "3400", "ban_nb_f_fin" to "3800", "ban_fg_unite" to "M"),
        ),
        emetteurs = listOf(
            row("sta_nm_anfr" to "1", "emr_id" to "E1", "aer_id" to "AE1", "emr_lb_systeme" to "LTE 800"),
            row("sta_nm_anfr" to "2", "emr_id" to "E2", "aer_id" to "AE2", "emr_lb_systeme" to "5G NR 3500"),
        ),
        antennes = listOf(
            row(
                "sta_nm_anfr" to "1", "aer_id" to "AE1", "sup_id" to "S1", "tae_id" to "16",
                "aer_nb_azimut" to "120", "aer_nb_alt_bas" to "28", "aer_nb_dimension" to "1,5",
            ),
            // Sans aer_nb_dimension : la station 2 couvre l'absence de tag [DIM: ...].
            row(
                "sta_nm_anfr" to "2", "aer_id" to "AE2", "sup_id" to "S2", "tae_id" to "32",
                "aer_nb_azimut" to "240", "aer_nb_alt_bas" to "30",
            ),
        ),
        supports = listOf(
            row(
                "sta_nm_anfr" to "1", "sup_id" to "S1", "nat_id" to "23", "tpo_id" to "1",
                "sup_nm_haut" to "30", "com_cd_insee" to "75056", "adr_lb_lieu" to "Rue X", "adr_nm_cp" to "75001",
            ),
            row(
                "sta_nm_anfr" to "2", "sup_id" to "S2", "nat_id" to "17", "tpo_id" to "2",
                "sup_nm_haut" to "25", "com_cd_insee" to "31555",
            ),
        ),
    )

    private fun references(): AnfrReferences = AnfrReferences(
        nature = mapOf("23" to "Pylone", "17" to "Chateau d'eau"),
        proprietaire = mapOf("1" to "Prop1", "2" to "Prop2"),
        exploitant = mapOf("5" to "Orange", "6" to "SFR"),
        typeAntenne = mapOf("16" to "Panneau", "32" to "Panneau 5G"),
        communes = mapOf("75056" to "PARIS", "31555" to "TOULOUSE"),
    )

    private fun arcep(): Map<Pair<String, String>, ArcepSiteMeta> =
        mapOf(("0000000001" to "ORANGE") to ArcepSiteMeta("NIDT1", 0))

    @Test
    fun buildsValidatorCompatibleDatabaseFromAnfrSources() {
        val file = File.createTempFile("geotower_build_test", ".db").apply { deleteOnExit() }

        val result = JdbcSqlDatabase(file.absolutePath).use { db ->
            GeoTowerDbBuilder.build(
                db, sources(), references(), arcep(),
                BuildConfig(version = "20260601_1200", zipVersion = "sup_2026_06.zip", quarterlyVersion = "2026-T2"),
            )
        }
        assertEquals(2, result.stations)
        assertEquals(2, result.supports)
        assertEquals(2, result.antennes)

        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { conn ->
            // Estampilles Room.
            assertEquals(7, conn.int("PRAGMA user_version"))
            assertEquals(
                "f92129b45cc37b357c5ecb8e0ba597f0",
                conn.one("SELECT identity_hash FROM room_master_table WHERE id = 42")!!["identity_hash"],
            )

            // Metadata (attendue par le validateur).
            val meta = conn.one("SELECT * FROM metadata")!!
            assertEquals(7, (meta["schema_version"] as Number).toInt())
            assertEquals("FR", meta["country_code"])
            assertEquals("20260601_1200", meta["version"])
            assertEquals("2026-06-01", meta["date_maj_anfr"])

            // Comptes.
            assertEquals(2L, conn.count("localisation"))
            assertEquals(2L, conn.count("support"))
            assertEquals(2L, conn.count("antenne"))

            // Station 1 : LTE 800 -> 4G / bande 800, azimut 120, ARCEP.
            val locA = conn.one("SELECT * FROM localisation WHERE id_anfr = '0000000001'")!!
            assertEquals(RadioFilterMasks.TECH_4G, (locA["tech_mask"] as Number).toInt())
            assertEquals(RadioFilterMasks.BAND_4G_800, (locA["band_mask"] as Number).toInt())
            assertEquals("120", locA["azimuts"])
            assertEquals("NIDT1", locA["arcep_nidt"])
            assertEquals(0, (locA["is_zb"] as Number).toInt())

            // Station 2 : 5G NR 3500 -> 5G / bande 3500, azimut 240, pas d'ARCEP.
            val locB = conn.one("SELECT * FROM localisation WHERE id_anfr = '0000000002'")!!
            assertEquals(RadioFilterMasks.TECH_5G, (locB["tech_mask"] as Number).toInt())
            assertEquals(RadioFilterMasks.BAND_5G_3500, (locB["band_mask"] as Number).toInt())
            assertEquals("240", locB["azimuts"])
            assertEquals(null, locB["arcep_nidt"])

            // Technique station 1 : actif, adresse composee, details_frequences decodables.
            val techA = conn.one("SELECT * FROM technique WHERE id_anfr = '0000000001'")!!
            assertEquals(1, (techA["has_active"] as Number).toInt())
            assertEquals("Rue X, 75001 PARIS", techA["adresse"])
            assertEquals(
                "LTE 800 : 791-801 MHz | En service | 2026-01-01 | Panneau : 120° (28m) [AER_ID: AE1] [DIM: 1,5m]",
                FrequencyDetailsCodec.decode(techA["details_frequences"] as String?),
            )

            // Technique station 2 : non actif (En projet), sans dimension declaree -> pas de tag [DIM].
            val techB = conn.one("SELECT * FROM technique WHERE id_anfr = '0000000002'")!!
            assertEquals(0, (techB["has_active"] as Number).toInt())
            val detailsB = FrequencyDetailsCodec.decode(techB["details_frequences"] as String?)!!
            assertTrue(detailsB.contains("Panneau 5G : 240° (30m) [AER_ID: AE2]"))
            assertFalse("aucun tag [DIM] sans aer_nb_dimension", detailsB.contains("[DIM"))

            // Referentiels.
            val operators = conn.column("SELECT libelle FROM ref_operateur")
            assertTrue(operators.contains("Orange"))
            assertTrue(operators.contains("SFR"))
            assertEquals("PARIS", conn.one("SELECT nom FROM ref_commune WHERE code_insee = '75056'")!!["nom"])

            // Stats courantes (obligatoires pour le validateur) : non vides et coherentes.
            assertTrue("radio_stat_current doit etre non vide", conn.count("radio_stat_current") >= 6L)

            // Orange (metropole) actif : support ALL 1/1, 4G 1/1.
            val orangeSupport = conn.one(
                "SELECT * FROM radio_stat_current WHERE operator_name = 'ORANGE' AND category = 'support' AND item_key = 'ALL'",
            )!!
            assertEquals(1, (orangeSupport["total_count"] as Number).toInt())
            assertEquals(1, (orangeSupport["active_count"] as Number).toInt())
            assertEquals("Supports", orangeSupport["label"])

            val orange4g = conn.one(
                "SELECT * FROM radio_stat_current WHERE operator_name = 'ORANGE' AND category = 'tech' AND item_key = '4G'",
            )!!
            assertEquals(1, (orange4g["total_count"] as Number).toInt())
            assertEquals(1, (orange4g["active_count"] as Number).toInt())

            // SFR "En projet" : declaree mais non active -> total 1, actif 0, avec libelle de bande.
            val sfrBand = conn.one(
                "SELECT * FROM radio_stat_current WHERE operator_name = 'SFR' AND category = 'band' AND item_key = '5G|3500'",
            )!!
            assertEquals(1, (sfrBand["total_count"] as Number).toInt())
            assertEquals(0, (sfrBand["active_count"] as Number).toInt())
            assertEquals("5G 3500 MHz", sfrBand["label"])
        }
    }

    @Test
    fun aggregatesMultipleEmittersBandsAndAzimutsPerStation() {
        val file = File.createTempFile("geotower_agg_test", ".db").apply { deleteOnExit() }

        val sources = AnfrSources(
            weekly = listOf(
                row(
                    "sta_nm_anfr" to "3", "coordonnees" to "48.0 2.0", "adm_lb_nom" to "Bouygues",
                    "statut" to "En service", "generation" to "4G", "emr_lb_systeme" to "LTE 800",
                    "emr_dt" to "2026-02-01", "date_maj" to "2026-06-01",
                ),
                row(
                    "sta_nm_anfr" to "3", "coordonnees" to "48.0 2.0", "adm_lb_nom" to "Bouygues",
                    "statut" to "En service", "generation" to "4G", "emr_lb_systeme" to "LTE 2600",
                    "emr_dt" to "2026-02-02", "date_maj" to "2026-06-01",
                ),
            ),
            stations = listOf(row("sta_nm_anfr" to "3", "adm_id" to "7")),
            bandes = listOf(
                row("emr_id" to "E3", "ban_nb_f_deb" to "791", "ban_nb_f_fin" to "801", "ban_fg_unite" to "M"),
                row("emr_id" to "E4", "ban_nb_f_deb" to "2500", "ban_nb_f_fin" to "2570", "ban_fg_unite" to "M"),
                row("emr_id" to "E4", "ban_nb_f_deb" to "2620", "ban_nb_f_fin" to "2690", "ban_fg_unite" to "M"),
            ),
            emetteurs = listOf(
                row("sta_nm_anfr" to "3", "emr_id" to "E3", "aer_id" to "AE3", "emr_lb_systeme" to "LTE 800"),
                row("sta_nm_anfr" to "3", "emr_id" to "E4", "aer_id" to "AE4", "emr_lb_systeme" to "LTE 2600"),
            ),
            antennes = listOf(
                row("sta_nm_anfr" to "3", "aer_id" to "AE3", "sup_id" to "S3", "tae_id" to "16", "aer_nb_azimut" to "90", "aer_nb_alt_bas" to "20"),
                row("sta_nm_anfr" to "3", "aer_id" to "AE4", "sup_id" to "S3", "tae_id" to "16", "aer_nb_azimut" to "270", "aer_nb_alt_bas" to "22"),
            ),
            supports = listOf(
                row("sta_nm_anfr" to "3", "sup_id" to "S3", "nat_id" to "23", "tpo_id" to "1", "sup_nm_haut" to "25", "com_cd_insee" to "75056"),
            ),
        )
        val references = AnfrReferences(
            nature = mapOf("23" to "Pylone"),
            typeAntenne = mapOf("16" to "Panneau"),
            communes = mapOf("75056" to "PARIS"),
        )

        JdbcSqlDatabase(file.absolutePath).use { db ->
            GeoTowerDbBuilder.build(db, sources, references, emptyMap(), BuildConfig(version = "20260601_1200"))
        }

        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { conn ->
            val loc = conn.one("SELECT * FROM localisation WHERE id_anfr = '0000000003'")!!
            assertEquals(RadioFilterMasks.TECH_4G, (loc["tech_mask"] as Number).toInt())
            // Deux emetteurs 4G -> bandes 800 ET 2600 cumulees.
            assertEquals(
                RadioFilterMasks.BAND_4G_800 or RadioFilterMasks.BAND_4G_2600,
                (loc["band_mask"] as Number).toInt(),
            )
            // Azimuts des deux antennes, tries par valeur.
            assertEquals("90,270", loc["azimuts"])

            // details_frequences : une ligne par emetteur, triees ; l'emetteur multi-bandes joint ses bandes.
            val tech = conn.one("SELECT * FROM technique WHERE id_anfr = '0000000003'")!!
            assertEquals(
                "LTE 2600 : 2500-2570 MHz, 2620-2690 MHz | En service | 2026-02-02 | Panneau : 270° (22m) [AER_ID: AE4]\n" +
                    "LTE 800 : 791-801 MHz | En service | 2026-02-01 | Panneau : 90° (20m) [AER_ID: AE3]",
                FrequencyDetailsCodec.decode(tech["details_frequences"] as String?),
            )
        }
    }

    @Test
    fun deduplicatesIdenticalDetailLines() {
        // Deux emetteurs STRICTEMENT identiques (meme emr_id / aer_id / systeme) : la ligne de detail
        // produite est la meme deux fois. Le regroupement par station (sortedSet dans applyDetails) doit
        // la dedupliquer -> une seule ligne, sans separateur.
        val file = File.createTempFile("geotower_dedup_test", ".db").apply { deleteOnExit() }
        val sources = AnfrSources(
            weekly = listOf(
                row(
                    "sta_nm_anfr" to "4", "coordonnees" to "48.0 2.0", "adm_lb_nom" to "Free",
                    "statut" to "En service", "generation" to "4G", "emr_lb_systeme" to "LTE 800",
                    "emr_dt" to "2026-03-01", "date_maj" to "2026-06-01",
                ),
            ),
            stations = listOf(row("sta_nm_anfr" to "4", "adm_id" to "8")),
            bandes = listOf(row("emr_id" to "E5", "ban_nb_f_deb" to "791", "ban_nb_f_fin" to "801", "ban_fg_unite" to "M")),
            emetteurs = listOf(
                row("sta_nm_anfr" to "4", "emr_id" to "E5", "aer_id" to "AE5", "emr_lb_systeme" to "LTE 800"),
                row("sta_nm_anfr" to "4", "emr_id" to "E5", "aer_id" to "AE5", "emr_lb_systeme" to "LTE 800"),
            ),
            antennes = listOf(
                row("sta_nm_anfr" to "4", "aer_id" to "AE5", "sup_id" to "S4", "tae_id" to "16", "aer_nb_azimut" to "90", "aer_nb_alt_bas" to "20"),
            ),
            supports = listOf(
                row("sta_nm_anfr" to "4", "sup_id" to "S4", "nat_id" to "23", "tpo_id" to "1", "sup_nm_haut" to "25", "com_cd_insee" to "75056"),
            ),
        )
        val references = AnfrReferences(
            nature = mapOf("23" to "Pylone"),
            typeAntenne = mapOf("16" to "Panneau"),
            communes = mapOf("75056" to "PARIS"),
        )

        JdbcSqlDatabase(file.absolutePath).use { db ->
            GeoTowerDbBuilder.build(db, sources, references, emptyMap(), BuildConfig(version = "20260601_1200"))
        }

        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { conn ->
            val tech = conn.one("SELECT * FROM technique WHERE id_anfr = '0000000004'")!!
            assertEquals(
                "LTE 800 : 791-801 MHz | En service | 2026-03-01 | Panneau : 90° (20m) [AER_ID: AE5]",
                FrequencyDetailsCodec.decode(tech["details_frequences"] as String?),
            )
        }
    }

    @Test
    fun keepsAzimutsOfBothStationsSharingAMutualizedAntenna() {
        // Site mutualise reel (support 497932, Crozon) : l'ANFR declare le MEME AER_ID sur la station
        // SFR et sur la station Bouygues. La table `antenne` ayant aer_id pour cle primaire, une seule
        // des deux y survit -> `localisation.azimuts` de l'autre ne doit PAS pour autant tomber a NULL.
        val file = File.createTempFile("geotower_mutualise_test", ".db").apply { deleteOnExit() }
        val sources = AnfrSources(
            weekly = listOf(
                row(
                    "sta_nm_anfr" to "0292700369", "coordonnees" to "48.245 -4.480", "adm_lb_nom" to "SFR",
                    "statut" to "En service", "generation" to "4G", "emr_lb_systeme" to "LTE 2100",
                    "emr_dt" to "2022-02-09", "date_maj" to "2026-06-01",
                ),
                row(
                    "sta_nm_anfr" to "0292750303", "coordonnees" to "48.245 -4.480", "adm_lb_nom" to "Bouygues",
                    "statut" to "En service", "generation" to "4G", "emr_lb_systeme" to "LTE 2100",
                    "emr_dt" to "2022-02-16", "date_maj" to "2026-06-01",
                ),
            ),
            stations = listOf(
                row("sta_nm_anfr" to "0292700369", "adm_id" to "6"),
                row("sta_nm_anfr" to "0292750303", "adm_id" to "7"),
            ),
            bandes = listOf(row("emr_id" to "E1", "ban_nb_f_deb" to "1920", "ban_nb_f_fin" to "2170", "ban_fg_unite" to "M")),
            emetteurs = listOf(
                row("sta_nm_anfr" to "0292700369", "emr_id" to "E1", "aer_id" to "1204115", "emr_lb_systeme" to "LTE 2100"),
                row("sta_nm_anfr" to "0292750303", "emr_id" to "E1", "aer_id" to "1204115", "emr_lb_systeme" to "LTE 2100"),
            ),
            // Trois panneaux partages : chaque ligne est declaree DEUX fois, une par station (ordre du
            // fichier ANFR : aer_id croissant, puis numero de station croissant -> Bouygues en dernier).
            antennes = listOf(
                row("sta_nm_anfr" to "0292700369", "aer_id" to "1204115", "sup_id" to "497932", "tae_id" to "16", "aer_nb_azimut" to "330", "aer_nb_alt_bas" to "30,8"),
                row("sta_nm_anfr" to "0292750303", "aer_id" to "1204115", "sup_id" to "497932", "tae_id" to "16", "aer_nb_azimut" to "330", "aer_nb_alt_bas" to "30,8"),
                row("sta_nm_anfr" to "0292700369", "aer_id" to "1204117", "sup_id" to "497932", "tae_id" to "16", "aer_nb_azimut" to "90", "aer_nb_alt_bas" to "30,8"),
                row("sta_nm_anfr" to "0292750303", "aer_id" to "1204117", "sup_id" to "497932", "tae_id" to "16", "aer_nb_azimut" to "90", "aer_nb_alt_bas" to "30,8"),
                row("sta_nm_anfr" to "0292700369", "aer_id" to "1204119", "sup_id" to "497932", "tae_id" to "16", "aer_nb_azimut" to "220", "aer_nb_alt_bas" to "30,8"),
                row("sta_nm_anfr" to "0292750303", "aer_id" to "1204119", "sup_id" to "497932", "tae_id" to "16", "aer_nb_azimut" to "220", "aer_nb_alt_bas" to "30,8"),
                // Panneau propre a Bouygues (non mutualise) : ne doit PAS remonter chez SFR.
                row("sta_nm_anfr" to "0292750303", "aer_id" to "9588616", "sup_id" to "497932", "tae_id" to "17", "aer_nb_azimut" to "235", "aer_nb_alt_bas" to "29,4"),
            ),
            supports = listOf(
                row("sta_nm_anfr" to "0292700369", "sup_id" to "497932", "nat_id" to "23", "tpo_id" to "1", "sup_nm_haut" to "31", "com_cd_insee" to "29042"),
                row("sta_nm_anfr" to "0292750303", "sup_id" to "497932", "nat_id" to "23", "tpo_id" to "1", "sup_nm_haut" to "31", "com_cd_insee" to "29042"),
            ),
        )
        val references = AnfrReferences(
            nature = mapOf("23" to "Pylone"),
            typeAntenne = mapOf("16" to "Panneau", "17" to "Parabole"),
            communes = mapOf("29042" to "CROZON"),
        )

        JdbcSqlDatabase(file.absolutePath).use { db ->
            GeoTowerDbBuilder.build(db, sources, references, emptyMap(), BuildConfig(version = "20260601_1200"))
        }

        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { conn ->
            // Les DEUX operateurs gardent les azimuts des panneaux mutualises.
            assertEquals("90,220,330", conn.one("SELECT * FROM localisation WHERE id_anfr = '0292700369'")!!["azimuts"])
            assertEquals("90,220,235,330", conn.one("SELECT * FROM localisation WHERE id_anfr = '0292750303'")!!["azimuts"])

            // La table `antenne` reste dedupliquee par aer_id (schema Room fige) : 4 lignes, pas 7.
            assertEquals(4L, conn.count("antenne"))
        }
    }

    @Test
    fun completesDetailsWithTheSystemsMissingFromTheMonthlyZip() {
        // Cas reel du support 758790 (Severac d'Aveyron), lignes RECOPIEES de observatoireod_20260806 :
        // l'observatoire du 06/08 annonce LTE 700, LTE 1800 et 5G NR 2100 en « Projet approuvé »
        // (emr_dt vide), que le ZIP du 30/06 ne porte pas encore. Sans completion, la fiche affichait
        // « 5G » dans le bandeau et trois emetteurs seulement dans le tableau.
        //
        // L'accent de « approuvé » n'est pas cosmetique : `classifyFrequencyStatus` ne reconnait le
        // statut qu'avec, et `clean_text` / `cleanText` ne touchent pas aux accents.
        val file = File.createTempFile("geotower_annonces_test", ".db").apply { deleteOnExit() }
        fun observatoire(systeme: String, generation: String, statut: String, date: String) = row(
            "sta_nm_anfr" to "0122290040", "coordonnees" to "44.309999999999995 , 3.102222222222222",
            "adm_lb_nom" to "ORANGE", "statut" to statut, "generation" to generation,
            "emr_lb_systeme" to systeme, "emr_dt" to date, "date_maj" to "2026-08-06",
        )
        fun emetteur(emrId: String, systeme: String) = row(
            "sta_nm_anfr" to "0122290040", "emr_id" to emrId, "aer_id" to "115577",
            "emr_lb_systeme" to systeme,
        )
        fun bande(emrId: String, debut: String, fin: String) = row(
            "emr_id" to emrId, "ban_nb_f_deb" to debut, "ban_nb_f_fin" to fin, "ban_fg_unite" to "M",
        )
        val sources = AnfrSources(
            weekly = listOf(
                observatoire("5G NR 2100", "5G", "Projet approuvé", ""),
                observatoire("GSM 900", "2G", "En service", "1997-12-15"),
                observatoire("LTE 1800", "4G", "Projet approuvé", ""),
                observatoire("LTE 700", "4G", "Projet approuvé", ""),
                observatoire("LTE 800", "4G", "En service", "2019-03-25"),
                observatoire("UMTS 900", "3G", "En service", "2018-12-26"),
            ),
            stations = listOf(row("sta_nm_anfr" to "0122290040", "adm_id" to "5")),
            // Le ZIP mensuel du 30/06 ne porte que les trois emetteurs deja en service
            // (identifiants et bandes releves sur l'antenne 115577).
            bandes = listOf(
                bande("118988", "933,9", "937,9"), bande("118988", "897,1", "897,3"),
                bande("118988", "942,1", "942,3"), bande("118988", "888,9", "892,9"),
                bande("10700395", "892,9", "897,1"), bande("10700395", "937,9", "942,1"),
                bande("10700397", "811", "821"), bande("10700397", "852", "862"),
            ),
            emetteurs = listOf(
                emetteur("118988", "GSM 900"),
                emetteur("10700395", "UMTS 900"),
                emetteur("10700397", "LTE 800"),
            ),
            antennes = listOf(
                row(
                    "sta_nm_anfr" to "0122290040", "aer_id" to "115577", "sup_id" to "758790", "tae_id" to "16",
                    "aer_nb_azimut" to "185", "aer_nb_alt_bas" to "28,7",
                ),
            ),
            supports = listOf(
                row(
                    "sta_nm_anfr" to "0122290040", "sup_id" to "758790", "nat_id" to "23", "tpo_id" to "1",
                    "sup_nm_haut" to "30", "com_cd_insee" to "12254",
                ),
            ),
        )
        val references = AnfrReferences(
            nature = mapOf("23" to "Pylone"),
            typeAntenne = mapOf("16" to "Panneau"),
            communes = mapOf("12254" to "SEVERAC D AVEYRON"),
        )

        JdbcSqlDatabase(file.absolutePath).use { db ->
            GeoTowerDbBuilder.build(db, sources, references, emptyMap(), BuildConfig(version = "20260806_2021"))
        }

        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { conn ->
            // Le bandeau annoncait deja les quatre generations : le tableau des emetteurs les porte
            // desormais toutes les six, les trois annoncees sans bandes ni azimut.
            val loc = conn.one("SELECT * FROM localisation WHERE id_anfr = '0122290040'")!!
            assertEquals(
                RadioFilterMasks.TECH_2G or RadioFilterMasks.TECH_3G or
                    RadioFilterMasks.TECH_4G or RadioFilterMasks.TECH_5G,
                (loc["tech_mask"] as Number).toInt(),
            )
            val panneau = "Panneau : 185° (28,7m) [AER_ID: 115577]"
            val tech = conn.one("SELECT * FROM technique WHERE id_anfr = '0122290040'")!!
            assertEquals(
                "5G NR 2100 :  | Projet approuvé |  | Azimut non specifie\n" +
                    "GSM 900 : 933,9-937,9 MHz, 897,1-897,3 MHz, 942,1-942,3 MHz, 888,9-892,9 MHz " +
                    "| En service | 1997-12-15 | $panneau\n" +
                    "LTE 1800 :  | Projet approuvé |  | Azimut non specifie\n" +
                    "LTE 700 :  | Projet approuvé |  | Azimut non specifie\n" +
                    "LTE 800 : 811-821 MHz, 852-862 MHz | En service | 2019-03-25 | $panneau\n" +
                    "UMTS 900 : 892,9-897,1 MHz, 937,9-942,1 MHz | En service | 2018-12-26 | $panneau",
                FrequencyDetailsCodec.decode(tech["details_frequences"] as String?),
            )
        }
    }

    @Test
    fun buildsDetailsForAStationTheMonthlyZipIgnoresEntirely() {
        // Site tout juste declare : l'observatoire le connait, aucun fichier SUP_* ne le porte encore.
        // Sa fiche restait vide de bout en bout.
        val file = File.createTempFile("geotower_annonces_seules_test", ".db").apply { deleteOnExit() }
        val sources = AnfrSources(
            weekly = listOf(
                row(
                    "sta_nm_anfr" to "20", "coordonnees" to "45.00 1.00", "adm_lb_nom" to "Free Mobile",
                    "statut" to "Projet approuve", "generation" to "5G", "emr_lb_systeme" to "5G NR 3500",
                    "emr_dt" to "", "date_maj" to "2026-08-06",
                ),
                row(
                    "sta_nm_anfr" to "20", "coordonnees" to "45.00 1.00", "adm_lb_nom" to "Free Mobile",
                    "statut" to "Projet approuve", "generation" to "4G", "emr_lb_systeme" to "LTE 700",
                    "emr_dt" to "", "date_maj" to "2026-08-06",
                ),
            ),
            stations = emptyList(),
            bandes = emptyList(),
            emetteurs = emptyList(),
            antennes = emptyList(),
            supports = emptyList(),
        )

        JdbcSqlDatabase(file.absolutePath).use { db ->
            GeoTowerDbBuilder.build(db, sources, AnfrReferences(), emptyMap(), BuildConfig(version = "20260806_2021"))
        }

        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { conn ->
            val tech = conn.one("SELECT * FROM technique WHERE id_anfr = '0000000020'")!!
            assertEquals(
                "5G NR 3500 :  | Projet approuve |  | Azimut non specifie\n" +
                    "LTE 700 :  | Projet approuve |  | Azimut non specifie",
                FrequencyDetailsCodec.decode(tech["details_frequences"] as String?),
            )
        }
    }

    @Test
    fun detailQueriesAggregateWithoutATemporarySort() {
        // La propriete PERF des requetes : elles doivent exploiter les cles id_anfr des tables de
        // staging sans « USE TEMP B-TREE ». Un tri de ~2,5 M de lignes sur le telephone est
        // precisement ce qui rend BUILDING_DETAILS lent sur les appareils modestes.
        // Le staging est supprime en fin de build : on le recree a vide, seul le PLAN compte ici.
        val file = File.createTempFile("geotower_plan_test", ".db").apply { deleteOnExit() }
        JdbcSqlDatabase(file.absolutePath).use { db ->
            GeoTowerDbBuilder.stagingStatements(db.stagingPrefix).forEach { db.execSql(it) }
            db.execSql("CREATE INDEX ix_stg_emetteur_id ON stg_emetteur(id_anfr, systeme, emr_id, aer_id)")
            listOf(
                GeoTowerDbBuilder.detailsSql(),
                GeoTowerDbBuilder.detailsStreamSql(),
                GeoTowerDbBuilder.announcedDetailsSql(),
            ).forEach { sql ->
                val plan = StringBuilder()
                db.query("EXPLAIN QUERY PLAN $sql") { row -> plan.append(row.getString("detail")).append('\n') }
                assertFalse("tri temporaire dans le plan de :\n$sql\n$plan", plan.contains("TEMP B-TREE"))
            }
        }
    }

    private fun Connection.one(sql: String): Map<String, Any?>? {
        createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                if (!rs.next()) return null
                val meta = rs.metaData
                return (1..meta.columnCount).associate { meta.getColumnLabel(it) to rs.getObject(it) }
            }
        }
    }

    private fun Connection.count(table: String): Long =
        createStatement().use { st -> st.executeQuery("SELECT COUNT(*) FROM $table").use { it.next(); it.getLong(1) } }

    private fun Connection.int(pragmaOrQuery: String): Int =
        createStatement().use { st -> st.executeQuery(pragmaOrQuery).use { it.next(); it.getInt(1) } }

    private fun Connection.column(sql: String): List<String> =
        createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }
}
