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

    private fun versionKey(country: DatabaseCountry): String =
        if (country == DatabaseCountry.FR) MOBILE_VERSION_KEY else "${MOBILE_VERSION_KEY}_${country.code}"

    private fun sha256Key(country: DatabaseCountry): String =
        if (country == DatabaseCountry.FR) MOBILE_SHA256_KEY else "${MOBILE_SHA256_KEY}_${country.code}"

    private fun lengthKey(country: DatabaseCountry): String =
        if (country == DatabaseCountry.FR) MOBILE_LENGTH_KEY else "${MOBILE_LENGTH_KEY}_${country.code}"

    private fun modifiedAtKey(country: DatabaseCountry): String =
        if (country == DatabaseCountry.FR) MOBILE_MODIFIED_AT_KEY else "${MOBILE_MODIFIED_AT_KEY}_${country.code}"

    fun recordMobileServerDownload(
        context: Context,
        databaseFile: File,
        identity: DatabaseArtifactIdentity,
        country: DatabaseCountry = DatabaseCountryStore.getActive(context)
    ) {
        val snapshot = databaseFile.snapshotOrNull() ?: run {
            clearMobile(context, country)
            return
        }
        prefs(context).edit()
            .putString(versionKey(country), DatabaseVersionPolicy.normalizedVersion(identity.version))
            .putString(sha256Key(country), identity.sha256?.trim()?.lowercase())
            .putLong(lengthKey(country), snapshot.length)
            .putLong(modifiedAtKey(country), snapshot.modifiedAt)
            .apply()
    }

    fun matchesInstalledMobile(
        context: Context,
        databaseFile: File,
        proposed: DatabaseArtifactIdentity,
        country: DatabaseCountry = DatabaseCountryStore.getActive(context)
    ): Boolean {
        val prefs = prefs(context)
        val snapshot = databaseFile.snapshotOrNull() ?: return false
        if (
            prefs.getLong(lengthKey(country), -1L) != snapshot.length ||
            prefs.getLong(modifiedAtKey(country), -1L) != snapshot.modifiedAt
        ) {
            clearMobile(context, country)
            return false
        }

        val installed = DatabaseArtifactIdentity(
            version = prefs.getString(versionKey(country), null),
            sha256 = prefs.getString(sha256Key(country), null)
        )
        if (installed.version.isNullOrBlank() && installed.sha256.isNullOrBlank()) return false
        return DatabaseArtifactIdentityPolicy.matches(proposed, installed)
    }

    fun clearMobile(
        context: Context,
        country: DatabaseCountry = DatabaseCountryStore.getActive(context)
    ) {
        prefs(context).edit()
            .remove(versionKey(country))
            .remove(sha256Key(country))
            .remove(lengthKey(country))
            .remove(modifiedAtKey(country))
            .apply()
    }

    private fun File.snapshotOrNull(): FileSnapshot? =
        takeIf { it.isFile && it.length() > 0L }?.let { FileSnapshot(it.length(), it.lastModified()) }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)

    private data class FileSnapshot(val length: Long, val modifiedAt: Long)
}
