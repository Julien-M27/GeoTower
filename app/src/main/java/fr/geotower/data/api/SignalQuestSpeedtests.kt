package fr.geotower.data.api

import fr.geotower.utils.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MISSING_SPEED_RANK = Float.NEGATIVE_INFINITY
private const val SPEEDTEST_PAGE_SIZE = 100
private const val TAG_SPEEDTEST_FETCH = "GeoTowerSpeedtest"

enum class SignalQuestSpeedtestSortMetric(val storageKey: String) {
    AVERAGE("average"),
    MAX("max"),
    DOWNLOAD("download");

    companion object {
        fun fromStorageKey(storageKey: String?): SignalQuestSpeedtestSortMetric {
            return values().firstOrNull { it.storageKey == storageKey } ?: AVERAGE
        }
    }
}

/**
 * Meilleur speedtest SignalQuest d'une station, au sens de [metric]. Parcourt toutes les pages de
 * l'API et ne garde que les mesures du PLMN de l'opérateur (une station mutualisée renvoie sinon
 * les relevés des voisins). Renvoie `null` si l'opérateur n'est pas couvert, si l'appel échoue ou
 * si aucune mesure n'est exploitable.
 *
 * L'appelant reste responsable des garde-fous (flags distants, préférences communautaires) :
 * cette fonction ne fait que l'appel réseau.
 */
suspend fun fetchBestSignalQuestSpeedtest(
    operator: String?,
    supportId: String?,
    anfrCode: String?,
    metric: SignalQuestSpeedtestSortMetric
): SqSpeedtestData? {
    val plmn = SignalQuestOperators.speedtestPlmnFor(operator)
    val fallbackOperator = SignalQuestOperators.operatorParamFor(operator).takeIf { plmn == null }
    if (plmn == null && fallbackOperator == null) return null

    val cleanSupportId = supportId?.trim()?.takeIf { it.isNotEmpty() }
    val cleanAnfrCode = anfrCode?.trim()?.takeIf { it.isNotEmpty() }
    val speedtests = mutableListOf<SqSpeedtestData>()
    var offset = 0
    var total: Int? = null

    try {
        while (true) {
            val response = withContext(Dispatchers.IO) {
                SignalQuestClient.api.getSiteSpeedtests(
                    siteId = cleanSupportId,
                    anfrCode = cleanAnfrCode,
                    nationalSiteCode = cleanAnfrCode,
                    sourceCode = cleanAnfrCode,
                    operator = fallbackOperator,
                    mcc = plmn?.mcc,
                    mnc = plmn?.mnc,
                    bestOnly = false,
                    limit = SPEEDTEST_PAGE_SIZE,
                    offset = offset
                )
            }

            if (!response.isSuccessful) {
                response.errorBody()?.close()
                AppLogger.w(TAG_SPEEDTEST_FETCH, "SignalQuest speedtest API failure code=${response.code()}")
                break
            }

            val body = response.body()
            val rawPage = body?.data.orEmpty()
            speedtests += rawPage.filterBySignalQuestPlmn(plmn)
            total = body?.meta?.total ?: total
            val fetchedCount = offset + rawPage.size
            val totalValue = total

            if (
                rawPage.isEmpty() ||
                rawPage.size < SPEEDTEST_PAGE_SIZE ||
                (totalValue != null && fetchedCount >= totalValue)
            ) {
                break
            }
            offset = fetchedCount
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.w(TAG_SPEEDTEST_FETCH, "SignalQuest speedtest request failed", e)
    }

    return speedtests.bestSignalQuestSpeedtestByMetric(metric)
}

val signalQuestSpeedtestRankingComparator: Comparator<SqSpeedtestData> = compareBy(
    { speedtest -> speedtest.averageSpeedRank() },
    { speedtest -> speedtest.publicDownloadRank() },
    { speedtest -> speedtest.timestamp.orEmpty() }
)

fun Iterable<SqSpeedtestData>.bestSignalQuestSpeedtest(): SqSpeedtestData? {
    return maxWithOrNull(signalQuestSpeedtestRankingComparator)
}

fun Iterable<SqSpeedtestData>.bestSignalQuestSpeedtestByMetric(
    metric: SignalQuestSpeedtestSortMetric
): SqSpeedtestData? {
    return sortedBySignalQuestMetric(metric = metric, descending = true).firstOrNull()
}

fun Iterable<SqSpeedtestData>.filterBySignalQuestPlmn(
    plmn: SignalQuestPlmnFilter?
): List<SqSpeedtestData> {
    if (plmn == null) return toList()
    return filter { speedtest -> speedtest.matchesSignalQuestPlmn(plmn) }
}

fun SqSpeedtestData.matchesSignalQuestPlmn(plmn: SignalQuestPlmnFilter?): Boolean {
    if (plmn == null) return true
    return mnc == plmn.mnc && (plmn.mcc == null || mcc == null || mcc == plmn.mcc)
}

fun Iterable<SqSpeedtestData>.sortedBySignalQuestRanking(): List<SqSpeedtestData> {
    return sortedWith(signalQuestSpeedtestRankingComparator.reversed())
}

fun Iterable<SqSpeedtestData>.sortedBySignalQuestMetric(
    metric: SignalQuestSpeedtestSortMetric,
    descending: Boolean
): List<SqSpeedtestData> {
    return sortedWith { first, second ->
        val metricComparison = compareNullableSpeed(
            first = first.sortValue(metric),
            second = second.sortValue(metric),
            descending = descending
        )
        if (metricComparison != 0) {
            metricComparison
        } else {
            signalQuestSpeedtestRankingComparator.compare(second, first)
        }
    }
}

private fun SqSpeedtestData.averageSpeedRank(): Float {
    return averageSpeed ?: MISSING_SPEED_RANK
}

private fun SqSpeedtestData.publicDownloadRank(): Float {
    return listOfNotNull(downloadSpeed, maxSpeed).maxOrNull() ?: MISSING_SPEED_RANK
}

private fun SqSpeedtestData.sortValue(metric: SignalQuestSpeedtestSortMetric): Float? {
    return when (metric) {
        SignalQuestSpeedtestSortMetric.AVERAGE -> averageSpeed
        SignalQuestSpeedtestSortMetric.MAX -> maxSpeed
        SignalQuestSpeedtestSortMetric.DOWNLOAD -> downloadSpeed
    }
}

private fun compareNullableSpeed(first: Float?, second: Float?, descending: Boolean): Int {
    return when {
        first == null && second == null -> 0
        first == null -> 1
        second == null -> -1
        first == second -> 0
        descending -> second.compareTo(first)
        else -> first.compareTo(second)
    }
}
