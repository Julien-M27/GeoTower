package fr.geotower.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import fr.geotower.data.EnbRepository
import fr.geotower.utils.PreferenceStores
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Nombre d'identifiants eNB et gNB distincts, regroupes par operateur. */
data class EnbOperatorCount(
    val operator: String,
    val enbCount: Int,
    val gnbCount: Int,
)

/**
 * Lit la repartition des identifiants de la base eNB installee.
 *
 * Le comptage est conserve avec la version de la base : la requete parcourt l'index de
 * `enb_cell`, et ne doit donc pas etre relancee a chaque retour sur la page des reglages.
 */
object EnbDatabaseOperatorCounts {

    private const val CACHE_VERSION_KEY = "db_enb_operator_counts_version"
    private const val CACHE_VALUE_KEY = "db_enb_operator_counts"

    fun read(context: Context, dbPath: File, versionRaw: String?): List<EnbOperatorCount>? {
        if (!dbPath.isFile) return null

        val prefs = context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
        if (versionRaw != null && prefs.getString(CACHE_VERSION_KEY, null) == versionRaw) {
            decode(prefs.getString(CACHE_VALUE_KEY, null))?.let { return it }
        }

        return try {
            SQLiteDatabase.openDatabase(dbPath.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val sourceRows = readSourceRows(db)
                val countsByMnc = readCountsByMnc(db)
                aggregateOperatorCounts(sourceRows, countsByMnc).also { counts ->
                    if (versionRaw != null) {
                        prefs.edit()
                            .putString(CACHE_VERSION_KEY, versionRaw)
                            .putString(CACHE_VALUE_KEY, encode(counts))
                            .apply()
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readSourceRows(db: SQLiteDatabase): List<EnbSourceOperatorRow> {
        val rows = ArrayList<EnbSourceOperatorRow>()
        db.rawQuery(
            "SELECT operator, mnc_list FROM enb_source ORDER BY plmn",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += EnbSourceOperatorRow(
                    operator = cursor.getString(0).orEmpty(),
                    mncs = parseMncList(cursor.getString(1)),
                )
            }
        }
        return rows
    }

    private fun readCountsByMnc(db: SQLiteDatabase): Map<Int, EnbMncTechnologyCounts> {
        val counts = HashMap<Int, EnbMncTechnologyCounts>()
        db.rawQuery(
            "SELECT mnc, techno, COUNT(DISTINCT enb) FROM enb_cell " +
                "WHERE techno IN (?, ?) GROUP BY mnc, techno",
            arrayOf(EnbRepository.TECHNO_LTE.toString(), EnbRepository.TECHNO_NR.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val mnc = cursor.getInt(0)
                val value = cursor.getInt(2)
                val previous = counts[mnc] ?: EnbMncTechnologyCounts()
                counts[mnc] = when (cursor.getInt(1)) {
                    EnbRepository.TECHNO_LTE -> previous.copy(enbCount = value)
                    EnbRepository.TECHNO_NR -> previous.copy(gnbCount = value)
                    else -> previous
                }
            }
        }
        return counts
    }

    private fun parseMncList(raw: String?): List<Int> =
        raw.orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }.distinct()

    private fun encode(counts: List<EnbOperatorCount>): String = JSONArray().apply {
        counts.forEach { count ->
            put(
                JSONObject()
                    .put("operator", count.operator)
                    .put("enb", count.enbCount)
                    .put("gnb", count.gnbCount),
            )
        }
    }.toString()

    private fun decode(raw: String?): List<EnbOperatorCount>? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val operator = item.optString("operator").trim()
                    if (operator.isNotEmpty()) {
                        add(
                            EnbOperatorCount(
                                operator = operator,
                                enbCount = item.optInt("enb", 0),
                                gnbCount = item.optInt("gnb", 0),
                            ),
                        )
                    }
                }
            }
        }.getOrNull()
    }

    internal fun aggregateOperatorCounts(
        sourceRows: List<EnbSourceOperatorRow>,
        countsByMnc: Map<Int, EnbMncTechnologyCounts>,
    ): List<EnbOperatorCount> {
        val mncsByOperator = LinkedHashMap<String, LinkedHashSet<Int>>()
        sourceRows.forEach { source ->
            val operator = source.operator.trim()
            if (operator.isNotEmpty()) {
                mncsByOperator.getOrPut(operator) { LinkedHashSet() }.addAll(source.mncs)
            }
        }
        return mncsByOperator.map { (operator, mncs) ->
            val totals = mncs.fold(EnbMncTechnologyCounts()) { total, mnc ->
                val current = countsByMnc[mnc] ?: EnbMncTechnologyCounts()
                EnbMncTechnologyCounts(
                    enbCount = total.enbCount + current.enbCount,
                    gnbCount = total.gnbCount + current.gnbCount,
                )
            }
            EnbOperatorCount(operator, totals.enbCount, totals.gnbCount)
        }.filter { it.enbCount > 0 || it.gnbCount > 0 }
    }

    internal data class EnbSourceOperatorRow(
        val operator: String,
        val mncs: List<Int>,
    )

    internal data class EnbMncTechnologyCounts(
        val enbCount: Int = 0,
        val gnbCount: Int = 0,
    )

}
