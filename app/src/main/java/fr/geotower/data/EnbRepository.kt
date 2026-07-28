package fr.geotower.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import fr.geotower.data.db.EnbDatabaseValidator
import fr.geotower.utils.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lecture de `geotower_fr_enb.db` (identifiants reseau eNB/gNB, source partenaire eNB-Analytics).
 *
 * Comme [RadioRepository], la base n'est pas geree par Room : elle est ouverte en lecture seule a
 * la requete. Elle est optionnelle - si elle n'est pas installee, tout renvoie un resultat vide et
 * l'appelant n'affiche simplement rien.
 *
 * Le rattachement se fait par `id_support` **et** operateur : un pylone mutualise porte les
 * stations de plusieurs operateurs, seul le MNC permet de choisir la bonne. Les MNC d'un operateur
 * sont lus dans `enb_source.mnc_list` et non deduits : Free apporte 208-15 **et** 208-16, s'en
 * tenir au MNC principal ferait disparaitre ses identifiants secondaires.
 */
class EnbRepository(private val context: Context) {

    /** Identifiants d'un site, par technologie. Une station peut en porter plusieurs. */
    data class SiteNetworkIds(
        val lte: List<Long> = emptyList(),
        val nr: List<Long> = emptyList()
    ) {
        val isEmpty: Boolean get() = lte.isEmpty() && nr.isEmpty()
    }

    fun isInstalled(): Boolean = databaseFile().isFile

    suspend fun getIdentifiersForSupport(
        idSupport: String?,
        operator: String?
    ): SiteNetworkIds = withContext(Dispatchers.IO) {
        val supportId = idSupport?.trim()?.toLongOrNull() ?: return@withContext SiteNetworkIds()
        val plmn = plmnForOperator(operator) ?: return@withContext SiteNetworkIds()
        if (!databaseFile().isFile) return@withContext SiteNetworkIds()

        try {
            openDatabase().use { db ->
                val mncs = readMncList(db, plmn)
                if (mncs.isEmpty()) return@use SiteNetworkIds()

                val placeholders = mncs.joinToString(",") { "?" }
                val args = (mncs.map { it.toString() } + supportId.toString()).toTypedArray()
                val lte = mutableListOf<Long>()
                val nr = mutableListOf<Long>()

                db.rawQuery(
                    "SELECT techno, enb FROM enb_cell " +
                        "WHERE mnc IN ($placeholders) AND id_support = ? " +
                        "ORDER BY techno, enb",
                    args
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        when (cursor.getInt(0)) {
                            TECHNO_LTE -> lte += cursor.getLong(1)
                            TECHNO_NR -> nr += cursor.getLong(1)
                        }
                    }
                }

                SiteNetworkIds(lte = lte, nr = nr)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "eNB identifiers lookup failed", e)
            SiteNetworkIds()
        }
    }

    private fun readMncList(db: SQLiteDatabase, plmn: String): List<Int> {
        db.rawQuery("SELECT mnc_list FROM enb_source WHERE plmn = ? LIMIT 1", arrayOf(plmn)).use { cursor ->
            if (!cursor.moveToFirst()) return emptyList()
            return cursor.getString(0).orEmpty()
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
        }
    }

    private fun openDatabase(): SQLiteDatabase =
        SQLiteDatabase.openDatabase(databaseFile().absolutePath, null, SQLiteDatabase.OPEN_READONLY)

    private fun databaseFile(): File = context.getDatabasePath(EnbDatabaseValidator.DB_NAME)

    companion object {
        const val TECHNO_LTE = 4
        const val TECHNO_NR = 5
        private const val TAG = "GeoTowerEnbDb"

        /**
         * Table PLMN (MCC 208 + MNC) des operateurs metropolitains, celle-la meme qui sert au lien
         * eNB-Analytics de la fiche site. null = operateur non couvert par le partenaire.
         */
        fun plmnForOperator(operator: String?): String? = when {
            operator == null -> null
            operator.contains("ORANGE", ignoreCase = true) -> "20801"
            operator.contains("FREE", ignoreCase = true) -> "20815"
            operator.contains("BOUYGUES", ignoreCase = true) -> "20820"
            operator.contains("SFR", ignoreCase = true) -> "20810"
            else -> null
        }
    }
}
