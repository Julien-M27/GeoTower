package fr.geotower.data.db

import android.content.Context
import java.io.File

/** Nettoyage des fichiers transitoires sans toucher aux bases actuellement installees. */
object DatabaseStorageCleanup {

    private val transientSuffixes = listOf(".download", ".backup", ".localbuild")
    private val downloadSuffixes = listOf(".download", ".backup")
    private val sqliteSidecarSuffixes = listOf("-wal", "-shm", "-journal")

    /**
     * Supprime les sorties intermediaires d'une ou plusieurs bases, ainsi que leurs journaux
     * SQLite. Le fichier actif (`databaseName` sans suffixe) est volontairement conserve.
     */
    fun clearTransientArtifacts(context: Context, vararg databaseNames: String): Boolean {
        val databaseDir = databaseNames.firstOrNull()
            ?.let(context::getDatabasePath)
            ?.parentFile
            ?: return false
        return clearTransientArtifacts(databaseDir, databaseNames.asIterable())
    }

    /** Supprime uniquement les artefacts d'un telechargement, y compris apres un echec. */
    fun clearDownloadArtifacts(context: Context, databaseName: String): Boolean {
        val databaseDir = context.getDatabasePath(databaseName).parentFile ?: return false
        var clean = true
        downloadSuffixes.forEach { suffix ->
            clean = deleteSqliteFile(File(databaseDir, databaseName + suffix)) && clean
        }
        return clean
    }

    internal fun clearTransientArtifacts(databaseDir: File, databaseNames: Iterable<String>): Boolean {
        var clean = true
        databaseNames.forEach { databaseName ->
            transientSuffixes.forEach { suffix ->
                clean = deleteSqliteFile(File(databaseDir, databaseName + suffix)) && clean
            }
        }
        return clean
    }

    /** Supprime toutes les sources et bases de staging du dossier dedie au build local. */
    internal fun clearLocalBuildWorkspace(workDir: File): Boolean {
        if (!workDir.exists()) return true
        return workDir.deleteRecursively() || !workDir.exists()
    }

    /** Supprime les journaux SQLite d'une base avant un remplacement atomique. */
    fun clearSqliteSidecars(databaseFile: File): Boolean =
        sqliteSidecarSuffixes
            .map { suffix -> deleteFile(File(databaseFile.path + suffix)) }
            .all { it }

    private fun deleteSqliteFile(file: File): Boolean {
        var clean = deleteFile(file)
        sqliteSidecarSuffixes.forEach { suffix ->
            clean = deleteFile(File(file.path + suffix)) && clean
        }
        return clean
    }

    private fun deleteFile(file: File): Boolean = !file.exists() || file.delete()
}
