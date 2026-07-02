package fr.geotower.data.db

import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.geotower.utils.AppLogger
import java.io.File

/**
 * Index de performance créés au runtime sur la base GeoTower.
 *
 * La base est un fichier SQLite **préconstruit côté serveur puis téléchargé** : on ne peut donc
 * PAS déclarer ces index via `@Index` sur les entités Room. Cela changerait le hash de schéma
 * attendu par Room et la base téléchargée (ancien schéma) serait rejetée comme « incompatible »
 * à chaque ouverture.
 *
 * On les crée donc en `CREATE INDEX IF NOT EXISTS` : invisibles pour la validation Room et pour
 * [GeoTowerDatabaseValidator] (qui ne contrôle que tables/colonnes/PK/NOT NULL), mais utilisés
 * automatiquement par le planificateur SQLite.
 *
 * Cibles = requêtes chaudes :
 *  - bounding-box carte (`latitude`/`longitude`) appelée à chaque pan/zoom ;
 *  - filtres opérateur / statut / « en service » ;
 *  - sous-requête détail antennes (`id_anfr` + `id_support`).
 */
object GeoTowerDatabaseIndexes {

    private const val TAG = "GeoTowerDbIndex"

    /** Idempotents : `IF NOT EXISTS` → no-op une fois les index présents. */
    val statements: List<String> = listOf(
        "CREATE INDEX IF NOT EXISTS idx_localisation_lat_lon ON localisation(latitude, longitude)",
        "CREATE INDEX IF NOT EXISTS idx_localisation_operateur ON localisation(operateur_id)",
        "CREATE INDEX IF NOT EXISTS idx_localisation_insee ON localisation(code_insee)",
        "CREATE INDEX IF NOT EXISTS idx_technique_statut ON technique(statut_id)",
        "CREATE INDEX IF NOT EXISTS idx_technique_has_active ON technique(has_active)",
        "CREATE INDEX IF NOT EXISTS idx_antenne_anfr_support ON antenne(id_anfr, id_support)"
    )

    /** Appelé depuis le callback Room `onOpen` (base ouverte en écriture par Room). */
    fun apply(db: SupportSQLiteDatabase) {
        statements.forEach { sql ->
            try {
                db.execSQL(sql)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Index ignoré: $sql", e)
            }
        }
    }

    /**
     * Crée les index directement sur le fichier fraîchement installé, sur le thread courant
     * (le téléchargement tourne déjà sur `Dispatchers.IO`). Une erreur ne fait jamais échouer
     * l'installation : le callback [apply] retentera de toute façon à la prochaine ouverture.
     */
    fun applyToFile(dbFile: File) {
        if (!dbFile.isFile || dbFile.length() <= 0L) return
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            statements.forEach { sql ->
                try {
                    db.execSQL(sql)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Index ignoré: $sql", e)
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Création des index impossible", e)
        } finally {
            db?.close()
        }
    }
}
