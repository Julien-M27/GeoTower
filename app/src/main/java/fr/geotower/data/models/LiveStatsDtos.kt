package fr.geotower.data.models

import com.google.gson.annotations.SerializedName
import fr.geotower.data.db.DepartmentOperatorTechRow
import fr.geotower.data.db.DepartmentStatRow
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

data class LiveDepartmentStatsResponseDto(
    @SerializedName("country_code") val countryCode: String? = null,
    @SerializedName("rows") val rows: List<LiveDepartmentStatRowDto> = emptyList()
)

data class LiveDepartmentOperatorStatsResponseDto(
    @SerializedName("country_code") val countryCode: String? = null,
    @SerializedName("dept_code") val deptCode: String? = null,
    @SerializedName("rows") val rows: List<LiveDepartmentOperatorTechRowDto> = emptyList()
)

data class LiveDepartmentStatRowDto(
    @SerializedName("dept_code") val deptCode: String? = null,
    @SerializedName("dept_name") val deptName: String? = null,
    @SerializedName("area_km2") val areaKm2: Double? = null,
    @SerializedName("population") val population: Int? = null,
    @SerializedName("population_year") val populationYear: String? = null,
    @SerializedName("supports") val supports: Int? = null,
    @SerializedName("supports_active") val supportsActive: Int? = null,
    @SerializedName("stations") val stations: Int? = null,
    @SerializedName("stations_active") val stationsActive: Int? = null,
    @SerializedName("antennas") val antennas: Int? = null,
    @SerializedName("antennas_active") val antennasActive: Int? = null,
    @SerializedName("antennas_fh") val antennasFh: Int? = null,
    @SerializedName("stations_per_support") val stationsPerSupport: Double? = null,
    @SerializedName("antennas_per_station") val antennasPerStation: Double? = null,
    @SerializedName("supports_per_km2") val supportsPerKm2: Double? = null,
    @SerializedName("stations_per_km2") val stationsPerKm2: Double? = null,
    @SerializedName("antennas_per_km2") val antennasPerKm2: Double? = null,
    @SerializedName("supports_per_1k_hab") val supportsPer1kHab: Double? = null,
    @SerializedName("stations_per_1k_hab") val stationsPer1kHab: Double? = null,
    @SerializedName("antennas_per_1k_hab") val antennasPer1kHab: Double? = null,
    @SerializedName("hab_per_support") val habPerSupport: Double? = null,
    @SerializedName("hab_per_station") val habPerStation: Double? = null,
    @SerializedName("hab_per_antenna") val habPerAntenna: Double? = null
)

data class LiveDepartmentOperatorTechRowDto(
    @SerializedName("dept_code") val deptCode: String? = null,
    @SerializedName("operator_name") val operatorName: String? = null,
    @SerializedName("tech") val tech: String? = null,
    @SerializedName("supports") val supports: Int? = null,
    @SerializedName("supports_active") val supportsActive: Int? = null,
    @SerializedName("stations") val stations: Int? = null,
    @SerializedName("stations_active") val stationsActive: Int? = null,
    @SerializedName("antennas") val antennas: Int? = null,
    @SerializedName("antennas_active") val antennasActive: Int? = null
)

fun LiveDepartmentStatRowDto.toDepartmentStatRow(): DepartmentStatRow? {
    val safeCode = deptCode?.takeIf { it.isNotBlank() } ?: return null
    return DepartmentStatRow(
        deptCode = safeCode,
        deptName = deptName,
        areaKm2 = areaKm2,
        population = population,
        populationYear = populationYear,
        supports = supports ?: 0,
        supportsActive = supportsActive ?: 0,
        stations = stations ?: 0,
        stationsActive = stationsActive ?: 0,
        antennas = antennas ?: 0,
        antennasActive = antennasActive ?: 0,
        antennasFh = antennasFh ?: 0,
        stationsPerSupport = stationsPerSupport,
        antennasPerStation = antennasPerStation,
        supportsPerKm2 = supportsPerKm2,
        stationsPerKm2 = stationsPerKm2,
        antennasPerKm2 = antennasPerKm2,
        supportsPer1kHab = supportsPer1kHab,
        stationsPer1kHab = stationsPer1kHab,
        antennasPer1kHab = antennasPer1kHab,
        habPerSupport = habPerSupport,
        habPerStation = habPerStation,
        habPerAntenna = habPerAntenna
    )
}

fun LiveDepartmentOperatorTechRowDto.toDepartmentOperatorTechRow(): DepartmentOperatorTechRow? {
    val safeCode = deptCode?.takeIf { it.isNotBlank() } ?: return null
    val safeOperator = operatorName?.takeIf { it.isNotBlank() } ?: return null
    val safeTech = tech?.takeIf { it.isNotBlank() } ?: return null
    return DepartmentOperatorTechRow(
        deptCode = safeCode,
        operatorName = safeOperator,
        tech = safeTech,
        supports = supports ?: 0,
        supportsActive = supportsActive ?: 0,
        stations = stations ?: 0,
        stationsActive = stationsActive ?: 0,
        antennas = antennas ?: 0,
        antennasActive = antennasActive ?: 0
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
