package fr.geotower.data.build

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.fail

/**
 * Dump canonique d'une base construite — schema complet + toutes les lignes de toutes les tables,
 * dans un ordre stable — et comparaison a un fichier « golden » versionne dans
 * `src/test/resources/golden/`.
 *
 * FILET DE SECURITE des optimisations du builder (cf.
 * `docs/agent-ia-plan-optimisation-generation-locale-db-2026-08-05.md`) : reduire la RAM, changer
 * les structures d'accumulation, deplacer le staging ou paralleliser ne doit **rien** changer au
 * fichier produit. Toute optimisation doit donc laisser ces dumps STRICTEMENT identiques. Un dump
 * qui bouge est soit un bug, soit un changement fonctionnel a assumer explicitement (et a
 * repercuter cote builder serveur, cf. parite `docs/server/build_fr_anfr_db.py`).
 *
 * Le dump couvre volontairement plus que les assertions ciblees des autres tests :
 *  - `sqlite_master` en entier -> une table de staging oubliee, un index en trop ou une DDL qui
 *    derive (donc l'identity_hash Room) sautent aux yeux ;
 *  - `PRAGMA user_version` -> Room refuse la base s'il n'est pas pose en dernier ;
 *  - chaque ligne de chaque table, valeurs typees -> masques, azimuts, blobs, stats, referentiels.
 *
 * Mise a jour d'un golden : lancer le test, recopier le fichier ecrit dans `build/golden-actual/`
 * vers `src/test/resources/golden/`, et **relire le diff** avant de le commiter.
 */
object CanonicalDump {

    /** Repertoire (relatif au module) ou le dump reellement produit est ecrit a chaque execution. */
    private val ACTUAL_DIR = File("build/golden-actual")

    /** Dump canonique de la base SQLite situee a [databasePath]. */
    fun of(databasePath: String): String =
        DriverManager.getConnection("jdbc:sqlite:$databasePath").use { dump(it) }

    /**
     * Compare [actual] au golden `golden/<name>.txt` du classpath de test. Ecrit toujours le dump
     * produit dans `build/golden-actual/<name>.txt` pour rendre la mise a jour triviale, et signale
     * la **premiere ligne divergente avec son contexte** plutot qu'un `assertEquals` de plusieurs
     * centaines de lignes illisible.
     */
    fun assertMatchesGolden(name: String, actual: String) {
        val actualFile = File(ACTUAL_DIR, "$name.txt")
        actualFile.parentFile?.mkdirs()
        actualFile.writeText(actual, Charsets.UTF_8)

        val expected = javaClass.getResourceAsStream("/golden/$name.txt")
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        if (expected == null) {
            fail(
                "Golden absent : src/test/resources/golden/$name.txt\n" +
                    "Le dump produit a ete ecrit dans ${actualFile.absolutePath} ; " +
                    "relire son contenu puis le recopier a l'emplacement ci-dessus.",
            )
            return
        }

        val expectedLines = expected.lines()
        val actualLines = actual.lines()
        val firstDiff = (0 until maxOf(expectedLines.size, actualLines.size))
            .firstOrNull { expectedLines.getOrNull(it) != actualLines.getOrNull(it) }
            ?: return

        val from = maxOf(0, firstDiff - 3)
        val context = (from until minOf(firstDiff + 4, maxOf(expectedLines.size, actualLines.size)))
            .joinToString("\n") { index ->
                val marker = if (index == firstDiff) ">>" else "  "
                "$marker ${index + 1}\n$marker   attendu: ${expectedLines.getOrNull(index) ?: "<fin>"}" +
                    "\n$marker   obtenu : ${actualLines.getOrNull(index) ?: "<fin>"}"
            }
        fail(
            "La sortie du builder a change (golden $name).\n" +
                "Lignes attendues=${expectedLines.size}, obtenues=${actualLines.size}. " +
                "Premiere divergence ligne ${firstDiff + 1} :\n$context\n\n" +
                "Si le changement est VOULU : verifier la parite avec le builder serveur, puis recopier " +
                "${actualFile.absolutePath} vers src/test/resources/golden/$name.txt.",
        )
    }

    private fun dump(conn: Connection): String = buildString {
        append("user_version=").append(scalar(conn, "PRAGMA user_version")).append('\n')

        append("\n# schema\n")
        for (line in schemaObjects(conn)) append(line).append('\n')

        for (table in tables(conn)) {
            val rows = rowsOf(conn, table)
            append("\n# table ").append(table).append(" (").append(rows.size).append(" lignes)\n")
            for (row in rows.sorted()) append(row).append('\n')
        }
    }

    /** Toutes les entrees de `sqlite_master` (tables, index, vues, triggers), DDL comprise. */
    private fun schemaObjects(conn: Connection): List<String> =
        conn.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT type, name, sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY type, name",
            ).use { rs ->
                buildList {
                    while (rs.next()) {
                        add("${rs.getString(1)} ${rs.getString(2)} :: ${escape(rs.getString(3) ?: "")}")
                    }
                }
            }
        }

    private fun tables(conn: Connection): List<String> =
        conn.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
            ).use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }

    private fun rowsOf(conn: Connection, table: String): List<String> =
        conn.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM \"$table\"").use { rs ->
                val meta = rs.metaData
                val names = (1..meta.columnCount).map { meta.getColumnLabel(it) }
                buildList {
                    while (rs.next()) {
                        add(names.mapIndexed { index, name -> "$name=${value(rs.getObject(index + 1))}" }.joinToString("|"))
                    }
                }
            }
        }

    private fun scalar(conn: Connection, sql: String): String =
        conn.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs -> if (rs.next()) value(rs.getObject(1)) else "<vide>" }
        }

    /**
     * Rendu stable d'une valeur SQLite. La classe de stockage est conservee (un entier et le texte
     * "28" ne se dumpent pas pareil) car c'est elle qui compte pour la parite du fichier produit.
     */
    private fun value(raw: Any?): String = when (raw) {
        null -> "<null>"
        is ByteArray -> "x'" + raw.joinToString("") { "%02x".format(it) } + "'"
        is Double, is Float -> raw.toString()
        is Number -> raw.toString()
        else -> escape(raw.toString())
    }

    /** Une ligne de dump = une ligne de fichier : les separateurs et sauts de ligne sont echappes. */
    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("|", "\\|")
}
