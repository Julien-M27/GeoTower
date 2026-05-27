package fr.geotower.utils

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

fun formatDateToFrench(dateStr: String?): String {
    if (dateStr.isNullOrBlank() || dateStr == "-") return "-"
    return try {
        val cleanDate = dateStr.take(10)
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val outputFormat = DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault())
        val date = inputFormat.parse(cleanDate)
        if (date != null) outputFormat.format(date) else dateStr
    } catch (e: Exception) {
        dateStr
    }
}
