package fr.geotower.utils

enum class FrequencyStatusType {
    InService,
    TechnicallyOperational,
    Approved,
    Unknown
}

fun classifyFrequencyStatus(status: String): FrequencyStatusType {
    return when {
        status.contains("En service", ignoreCase = true) -> FrequencyStatusType.InService
        status.contains("Techniquement", ignoreCase = true) -> FrequencyStatusType.TechnicallyOperational
        status.contains("Approuvé", ignoreCase = true) -> FrequencyStatusType.Approved
        status.contains("ApprouvÃ©", ignoreCase = true) -> FrequencyStatusType.Approved
        else -> FrequencyStatusType.Unknown
    }
}
