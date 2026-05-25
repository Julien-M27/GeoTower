package fr.geotower.data.api

private const val MISSING_SPEED_RANK = Float.NEGATIVE_INFINITY

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
