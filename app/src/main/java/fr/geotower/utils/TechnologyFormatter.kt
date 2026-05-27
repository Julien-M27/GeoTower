package fr.geotower.utils

fun formatTechnologies(tech: String?, txtUnknown: String): String {
    return tech
        ?.split(Regex("[/,\\-]"))
        ?.map { it.trim().uppercase() }
        ?.filter { it.isNotEmpty() }
        ?.sortedDescending()
        ?.joinToString(" - ")
        ?: txtUnknown
}
