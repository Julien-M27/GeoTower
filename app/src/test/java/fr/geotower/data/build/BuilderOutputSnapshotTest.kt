package fr.geotower.data.build

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Instantanes (« goldens ») de la sortie des builders, sur le jeu de sources complet de
 * [BuildSnapshotFixture] lu via de vrais fichiers CSV/ZIP.
 *
 * C'est le FILET des optimisations prevues par
 * `docs/agent-ia-plan-optimisation-generation-locale-db-2026-08-05.md` : reduire la RAM, remplacer
 * les accumulateurs par des primitives ou du SQL, sortir le staging dans un fichier attache ou
 * paralleliser l'ingestion ne doit **rien** changer aux bases produites. Les autres tests verifient
 * des points precis ; ceux-ci verifient **tout le reste**, y compris ce a quoi personne n'a pense.
 *
 * Si un instantane casse : ce n'est PAS un test a mettre a jour a la legere. Soit l'optimisation a
 * un bug, soit le changement est fonctionnel et doit etre repercute sur le builder serveur.
 */
class BuilderOutputSnapshotTest {

    @Test
    fun mobileDatabaseMatchesGolden() {
        val file = tempDb("snapshot_mobile")
        withSources { sources, references ->
            JdbcSqlDatabase(file.absolutePath).use { db -> buildMobile(db, sources, references) }
        }
        CanonicalDump.assertMatchesGolden("mobile", CanonicalDump.of(file.absolutePath))
    }

    @Test
    fun radioStandaloneDatabaseMatchesGolden() {
        val file = tempDb("snapshot_radio_standalone")
        withSources { sources, references ->
            JdbcSqlDatabase(file.absolutePath).use { db ->
                RadioDbBuilder.build(
                    db, sources, references,
                    RadioBuildConfig(
                        version = BuildSnapshotFixture.VERSION,
                        zipVersion = BuildSnapshotFixture.ZIP_VERSION,
                        dateMajAnfr = BuildSnapshotFixture.DATE_MAJ_ANFR,
                    ),
                )
            }
        }
        CanonicalDump.assertMatchesGolden("radio", CanonicalDump.of(file.absolutePath))
    }

    /**
     * Chemin REEL de l'appareil quand les deux packs sont demandes : le ZIP n'est parse qu'une fois,
     * le build mobile « tee » chaque ligne SUP vers le staging radio, puis la base radio est emise
     * depuis ce staging. Compare au **meme** golden que le build radio autonome : les deux chemins
     * doivent produire exactement la meme base. Verifie en prime que brancher le sink ne change
     * **rien** a la base mobile.
     */
    @Test
    fun radioBuiltFromMobileTeeMatchesGoldenAndLeavesMobileUnchanged() {
        val mobileFile = tempDb("snapshot_mobile_tee")
        val radioFile = tempDb("snapshot_radio_tee")
        withSources { sources, references ->
            JdbcSqlDatabase(radioFile.absolutePath).use { radioDb ->
                RadioDbBuilder.prepareSchema(radioDb)
                val sink = RadioDbBuilder.RadioStagingSink(radioDb, references.typeAntenne)
                JdbcSqlDatabase(mobileFile.absolutePath).use { db ->
                    buildMobile(db, sources, references, sink)
                }
                RadioDbBuilder.buildFromStaging(
                    radioDb, references,
                    RadioBuildConfig(
                        version = BuildSnapshotFixture.VERSION,
                        zipVersion = BuildSnapshotFixture.ZIP_VERSION,
                        dateMajAnfr = BuildSnapshotFixture.DATE_MAJ_ANFR,
                    ),
                )
            }
        }

        CanonicalDump.assertMatchesGolden("radio", CanonicalDump.of(radioFile.absolutePath))

        // La base mobile doit etre rigoureusement la meme avec et sans sink radio branche.
        val reference = tempDb("snapshot_mobile_reference")
        withSources { sources, references ->
            JdbcSqlDatabase(reference.absolutePath).use { db -> buildMobile(db, sources, references) }
        }
        assertEquals(
            "le sink radio ne doit pas influencer la base mobile",
            CanonicalDump.of(reference.absolutePath),
            CanonicalDump.of(mobileFile.absolutePath),
        )
    }

    /**
     * Meme jeu de sources, mais staging dans une base **annexe attachee** au lieu du fichier final
     * (cf. [SqlDatabase.stagingPrefix]). Les deux bases produites doivent etre identiques aux memes
     * goldens : c'est ce qui prouve que deplacer le staging ne change rien a ce qui est installe,
     * y compris la disparition du VACUUM radio.
     */
    @Test
    fun attachedStagingProducesTheSameDatabases() {
        val mobileFile = tempDb("snapshot_mobile_attached")
        val radioFile = tempDb("snapshot_radio_attached")
        withSources { sources, references ->
            JdbcSqlDatabase(mobileFile.absolutePath, tempDb("staging_mobile").absolutePath).use { db ->
                buildMobile(db, sources, references)
            }
        }
        withSources { sources, references ->
            JdbcSqlDatabase(radioFile.absolutePath, tempDb("staging_radio").absolutePath).use { db ->
                RadioDbBuilder.build(
                    db, sources, references,
                    RadioBuildConfig(
                        version = BuildSnapshotFixture.VERSION,
                        zipVersion = BuildSnapshotFixture.ZIP_VERSION,
                        dateMajAnfr = BuildSnapshotFixture.DATE_MAJ_ANFR,
                    ),
                )
            }
        }

        CanonicalDump.assertMatchesGolden("mobile", CanonicalDump.of(mobileFile.absolutePath))
        CanonicalDump.assertMatchesGolden("radio", CanonicalDump.of(radioFile.absolutePath))
    }

    private fun buildMobile(
        db: SqlDatabase,
        sources: AnfrSources,
        references: AnfrReferences,
        sink: SupRowSink = SupRowSink.None,
    ) {
        GeoTowerDbBuilder.build(
            db, sources, references, BuildSnapshotFixture.arcep(),
            BuildConfig(
                version = BuildSnapshotFixture.VERSION,
                zipVersion = BuildSnapshotFixture.ZIP_VERSION,
                quarterlyVersion = BuildSnapshotFixture.QUARTERLY_VERSION,
            ),
            supSink = sink,
        )
    }

    /**
     * Ouvre le ZIP mensuel pour la duree du bloc : les sources sont des iterables **paresseux** qui
     * relisent le ZIP a la demande, elles ne survivent donc pas a sa fermeture.
     */
    private fun withSources(block: (AnfrSources, AnfrReferences) -> Unit) {
        val weekly = BuildSnapshotFixture.weeklyCsvFile()
        AnfrMonthlyZip(BuildSnapshotFixture.monthlyZipFile()).use { monthly ->
            block(
                anfrSourcesFrom(weekly, monthly),
                anfrReferencesFrom(monthly, BuildSnapshotFixture.communes(), BuildSnapshotFixture.departments()),
            )
        }
    }

    private fun tempDb(prefix: String): File =
        File.createTempFile(prefix, ".db").apply { deleteOnExit() }
}
