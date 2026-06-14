package fr.geotower.data.models

import com.google.gson.annotations.SerializedName
import fr.geotower.data.db.RadioStatRow
import fr.geotower.data.db.WeeklyRadioStatRow

data class LiveRadioStatsResponseDto(
    @SerializedName("country_code") val countryCode: String? = null,
    @SerializedName("operators") val operators: List<String> = emptyList(),
    @SerializedName("rows") val rows: List<LiveRadioStatRowDto> = emptyList()
)

data class LiveWeeklyRadioStatsResponseDto(
    @SerializedName("country_code") val countryCode: String? = null,
    @SerializedName("operators") val operators: List<String> = emptyList(),
    @SerializedName("rows") val rows: List<LiveWeeklyRadioStatRowDto> = emptyList()
)

data class LiveRadioStatRowDto(
    @SerializedName("category") val category: String? = null,
    @SerializedName("item_key") val itemKey: String? = null,
    @SerializedName("label") val label: String? = null,
    @SerializedName("total_count") val totalCount: Int? = null,
    @SerializedName("active_count") val activeCount: Int? = null
)

data class LiveWeeklyRadioStatRowDto(
    @SerializedName("week_key") val weekKey: String? = null,
    @SerializedName("week_start") val weekStart: String? = null,
    @SerializedName("source_date") val sourceDate: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("item_key") val itemKey: String? = null,
    @SerializedName("label") val label: String? = null,
    @SerializedName("total_count") val totalCount: Int? = null,
    @SerializedName("active_count") val activeCount: Int? = null
)

fun LiveRadioStatRowDto.toRadioStatRow(): RadioStatRow? {
    val safeCategory = category?.takeIf { it.isNotBlank() } ?: return null
    val safeItemKey = itemKey?.takeIf { it.isNotBlank() } ?: return null
    return RadioStatRow(
        category = safeCategory,
        itemKey = safeItemKey,
        label = label,
        totalCount = totalCount ?: 0,
        activeCount = activeCount ?: 0
    )
}

fun LiveWeeklyRadioStatRowDto.toWeeklyRadioStatRow(): WeeklyRadioStatRow? {
    val safeWeekKey = weekKey?.takeIf { it.isNotBlank() } ?: return null
    val safeCategory = category?.takeIf { it.isNotBlank() } ?: return null
    val safeItemKey = itemKey?.takeIf { it.isNotBlank() } ?: return null
    return WeeklyRadioStatRow(
        weekKey = safeWeekKey,
        weekStart = weekStart,
        sourceDate = sourceDate,
        category = safeCategory,
        itemKey = safeItemKey,
        label = label,
        totalCount = totalCount ?: 0,
        activeCount = activeCount ?: 0
    )
}
