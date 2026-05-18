package fr.geotower.utils

import android.content.Context
import android.os.Build
import fr.geotower.R
import java.text.DateFormatSymbols
import java.text.Normalizer
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object LocalizedDateLabels {
    fun monthName(context: Context, monthIndex: String): String {
        val month = monthIndex.toIntOrNull()?.takeIf { it in 1..12 } ?: return ""
        val locale = context.currentLocale()
        return DateFormatSymbols.getInstance(locale)
            .months
            .getOrNull(month - 1)
            .orEmpty()
            .localizedTitlecase(locale)
    }

    fun formatMonthlyVersion(context: Context, rawName: String): String {
        if (
            rawName.isBlank() ||
            rawName == "-" ||
            rawName == context.getString(R.string.appstrings_about_download_new_database)
        ) {
            return rawName
        }

        val match = Regex("^(\\d{4})(\\d{2})\\d{2}.*").find(rawName)
        if (match != null) {
            val year = match.groupValues[1]
            val monthName = monthName(context, match.groupValues[2])
            if (monthName.isNotEmpty()) return "$monthName $year"
        }
        return rawName
    }

    fun formatWeeklyVersionWithWeekNumber(context: Context, dateStr: String): String {
        if (dateStr.isBlank() || dateStr == "-" || !dateStr.contains("/")) return dateStr

        val locale = context.currentLocale()
        return try {
            val date = SimpleDateFormat("dd/MM/yyyy", locale).parse(dateStr)
            if (date == null) {
                dateStr
            } else {
                val calendar = Calendar.getInstance(locale).apply {
                    firstDayOfWeek = Calendar.MONDAY
                    minimalDaysInFirstWeek = 4
                    time = date
                }
                context.getString(
                    R.string.version_week_number_with_date,
                    calendar.get(Calendar.WEEK_OF_YEAR),
                    dateStr
                )
            }
        } catch (_: Exception) {
            dateStr
        }
    }

    fun formatPhotoExifMonth(context: Context, rawValue: String): String {
        val cleanValue = rawValue.trim().replace(Regex("\\s+"), " ")
        if (cleanValue.isEmpty()) return cleanValue

        val year = Regex("""\b(\d{4})\b""").find(cleanValue)?.groupValues?.get(1)
        val monthIndex = photoExifMonthIndex(cleanValue)

        return if (year != null && monthIndex != null) {
            "${monthName(context, monthIndex)} $year"
        } else {
            cleanValue.localizedTitlecase(context.currentLocale())
        }
    }

    fun formatPhotoExifDate(context: Context, rawValue: String): String {
        val cleanValue = rawValue.trim().replace(Regex("\\s+"), " ")
        if (cleanValue.isEmpty()) return cleanValue

        val parsedDate = photoExifDatePatterns.firstNotNullOfOrNull { pattern ->
            SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
            }.parseFull(cleanValue)
        }

        return if (parsedDate != null) {
            SimpleDateFormat("d MMMM yyyy", context.currentLocale()).format(parsedDate)
        } else {
            cleanValue.localizedTitlecase(context.currentLocale())
        }
    }

    private fun photoExifMonthIndex(rawValue: String): String? {
        Regex("""\b(\d{4})[-_/](\d{1,2})\b""").find(rawValue)
            ?.groupValues
            ?.getOrNull(2)
            ?.toPhotoExifMonthIndexOrNull()
            ?.let { return it }

        Regex("""\b(\d{1,2})[-_/](\d{4})\b""").find(rawValue)
            ?.groupValues
            ?.getOrNull(1)
            ?.toPhotoExifMonthIndexOrNull()
            ?.let { return it }

        Regex("""\b(\d{4})(\d{2})(?:\d{2})?\b""").find(rawValue)
            ?.groupValues
            ?.getOrNull(2)
            ?.toPhotoExifMonthIndexOrNull()
            ?.let { return it }

        val normalizedValue = rawValue.normalizedMonthSearchText()
        return photoExifMonthNamesByIndex.firstNotNullOfOrNull { (index, names) ->
            index.takeIf { names.any { monthName -> normalizedValue.contains(monthName) } }
        }
    }

    private fun Context.currentLocale(): Locale {
        val configuration = resources.configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            configuration.locale
        }
    }

    private fun String.toPhotoExifMonthIndexOrNull(): String? {
        val month = toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        return month.toString().padStart(2, '0')
    }

    private fun String.normalizedMonthSearchText(): String {
        return Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
    }

    private fun String.localizedTitlecase(locale: Locale): String {
        return replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(locale) else it.toString()
        }
    }

    private fun SimpleDateFormat.parseFull(value: String): java.util.Date? {
        val position = ParsePosition(0)
        val date = parse(value, position) ?: return null
        return date.takeIf { position.index == value.length }
    }

    private val photoExifDatePatterns = listOf(
        "yyyy-MM-dd",
        "yyyy/MM/dd",
        "yyyy_MM_dd",
        "yyyyMMdd",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'"
    )

    private val photoExifMonthNamesByIndex = mapOf(
        "01" to listOf("janvier", "january", "janeiro", "gennaio", "januar", "enero"),
        "02" to listOf("fevrier", "february", "fevereiro", "febbraio", "februar"),
        "03" to listOf("mars", "march", "marco", "marzo", "marz"),
        "04" to listOf("avril", "april", "abril", "aprile"),
        "05" to listOf("mai", "may", "maio", "maggio", "mayo"),
        "06" to listOf("juin", "june", "junho", "giugno", "juni", "junio"),
        "07" to listOf("juillet", "july", "julho", "luglio", "juli", "julio"),
        "08" to listOf("aout", "august", "agosto"),
        "09" to listOf("septembre", "september", "setembro", "settembre", "septiembre"),
        "10" to listOf("octobre", "october", "outubro", "ottobre", "oktober", "octubre"),
        "11" to listOf("novembre", "november", "novembro", "noviembre"),
        "12" to listOf("decembre", "december", "dezembro", "dicembre", "dezember", "diciembre")
    )
}
