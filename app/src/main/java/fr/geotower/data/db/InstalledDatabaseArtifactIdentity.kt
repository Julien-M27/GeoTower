package fr.geotower.data.db

import android.content.Context
import fr.geotower.utils.PreferenceStores
import java.io.File

/**
 * Memorise l'identite du manifeste qui a servi a installer la base mobile.
 *
 * `metadata.version` est une propriete interne du fichier SQLite : elle ne suffit pas lorsque le
 * principal et le miroir construisent chacun leur fichier. Nous gardons donc l'identite annoncee
 * par le manifeste, avec une empreinte legere du fichier installe afin de ne jamais reutiliser un
 * enregistrement devenu caduc apres une suppression ou un remplacement local.
 */
object InstalledDatabaseArtifactIdentity {
    private const val MOBILE_VERSION_KEY = "installed_mobile_db_manifest_version"
    private const val MOBILE_SHA256_KEY = "installed_mobile_db_manifest_sha256"
    private const val MOBILE_LENGTH_KEY = "installed_mobile_db_length"
    private const val MOBILE_MODIFIED_AT_KEY = "installed_mobile_db_modified_at"

    fun recordMobileServerDownload(
        context: Context,
        databaseFile: File,
        identity: DatabaseArtifactIdentity
    ) {
        val snapshot = databaseFile.snapshotOrNull() ?: run {
            clearMobile(context)
            return
        }
        prefs(context).edit()
            .putString(MOBILE_VERSION_KEY, DatabaseVersionPolicy.normalizedVersion(identity.version))
            .putString(MOBILE_SHA256_KEY, identity.sha256?.trim()?.lowercase())
            .putLong(MOBILE_LENGTH_KEY, snapshot.length)
            .putLong(MOBILE_MODIFIED_AT_KEY, snapshot.modifiedAt)
            .apply()
    }

    fun matchesInstalledMobile(
        context: Context,
        databaseFile: File,
        proposed: DatabaseArtifactIdentity
    ): Boolean {
        val prefs = prefs(context)
        val snapshot = databaseFile.snapshotOrNull() ?: return false
        if (
            prefs.getLong(MOBILE_LENGTH_KEY, -1L) != snapshot.length ||
            prefs.getLong(MOBILE_MODIFIED_AT_KEY, -1L) != snapshot.modifiedAt
        ) {
            clearMobile(context)
            return false
        }

        val installed = DatabaseArtifactIdentity(
            version = prefs.getString(MOBILE_VERSION_KEY, null),
            sha256 = prefs.getString(MOBILE_SHA256_KEY, null)
        )
        if (installed.version.isNullOrBlank() && installed.sha256.isNullOrBlank()) return false
        return DatabaseArtifactIdentityPolicy.matches(proposed, installed)
    }

    fun clearMobile(context: Context) {
        prefs(context).edit()
            .remove(MOBILE_VERSION_KEY)
            .remove(MOBILE_SHA256_KEY)
            .remove(MOBILE_LENGTH_KEY)
            .remove(MOBILE_MODIFIED_AT_KEY)
            .apply()
    }

    private fun File.snapshotOrNull(): FileSnapshot? =
        takeIf { it.isFile && it.length() > 0L }?.let { FileSnapshot(it.length(), it.lastModified()) }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)

    private data class FileSnapshot(val length: Long, val modifiedAt: Long)
}
