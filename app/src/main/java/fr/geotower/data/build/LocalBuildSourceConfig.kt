package fr.geotower.data.build

import android.content.Context

/**
 * Configuration des sources de données pour la génération locale de la base de données.
 * Miroir des réglages disponibles côté administrateur web (docs/server/admin_ui.py).
 */
data class LocalBuildSourceConfig(
    val weeklyPreset: String = OfficialSources.WEEKLY_PRESET_DATAGOUV_ZIP,
    val weeklyCustomUrl: String = "",
    val monthlyPreset: String = OfficialSources.MONTHLY_PRESET_DATAGOUV_MONTHLY,
    val monthlyCustomUrl: String = "",
    val quarterlyPreset: String = OfficialSources.QUARTERLY_PRESET_ARCEP_LAST,
    val quarterlyCustomUrl: String = "",
) {
    fun effectiveWeeklyUrl(): String =
        if (weeklyPreset == OfficialSources.PRESET_CUSTOM && weeklyCustomUrl.isNotBlank()) {
            weeklyCustomUrl.trim()
        } else {
            OfficialSources.defaultUrlForWeekly(weeklyPreset)
        }

    fun effectiveMonthlyUrl(): String =
        if (monthlyPreset == OfficialSources.PRESET_CUSTOM && monthlyCustomUrl.isNotBlank()) {
            monthlyCustomUrl.trim()
        } else {
            OfficialSources.defaultUrlForMonthly(monthlyPreset)
        }

    fun effectiveQuarterlyUrl(): String =
        if (quarterlyPreset == OfficialSources.PRESET_CUSTOM && quarterlyCustomUrl.isNotBlank()) {
            quarterlyCustomUrl.trim()
        } else {
            OfficialSources.defaultUrlForQuarterly(quarterlyPreset)
        }

    val isWeeklyCustom: Boolean get() = weeklyPreset == OfficialSources.PRESET_CUSTOM
    val isMonthlyCustom: Boolean get() = monthlyPreset == OfficialSources.PRESET_CUSTOM
    val isQuarterlyCustom: Boolean get() = quarterlyPreset == OfficialSources.PRESET_CUSTOM
}

object LocalBuildSourceStore {
    const val PREFS_NAME = "app_preferences"
    const val PREF_WEEKLY_PRESET = "local_build_source_weekly_preset"
    const val PREF_WEEKLY_CUSTOM_URL = "local_build_source_weekly_custom_url"
    const val PREF_MONTHLY_PRESET = "local_build_source_monthly_preset"
    const val PREF_MONTHLY_CUSTOM_URL = "local_build_source_monthly_custom_url"
    const val PREF_QUARTERLY_PRESET = "local_build_source_quarterly_preset"
    const val PREF_QUARTERLY_CUSTOM_URL = "local_build_source_quarterly_custom_url"

    fun load(context: Context): LocalBuildSourceConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return LocalBuildSourceConfig(
            weeklyPreset = prefs.getString(PREF_WEEKLY_PRESET, OfficialSources.WEEKLY_PRESET_DATAGOUV_ZIP)
                ?: OfficialSources.WEEKLY_PRESET_DATAGOUV_ZIP,
            weeklyCustomUrl = prefs.getString(PREF_WEEKLY_CUSTOM_URL, "").orEmpty(),
            monthlyPreset = prefs.getString(PREF_MONTHLY_PRESET, OfficialSources.MONTHLY_PRESET_DATAGOUV_MONTHLY)
                ?: OfficialSources.MONTHLY_PRESET_DATAGOUV_MONTHLY,
            monthlyCustomUrl = prefs.getString(PREF_MONTHLY_CUSTOM_URL, "").orEmpty(),
            quarterlyPreset = prefs.getString(PREF_QUARTERLY_PRESET, OfficialSources.QUARTERLY_PRESET_ARCEP_LAST)
                ?: OfficialSources.QUARTERLY_PRESET_ARCEP_LAST,
            quarterlyCustomUrl = prefs.getString(PREF_QUARTERLY_CUSTOM_URL, "").orEmpty(),
        )
    }

    fun save(context: Context, config: LocalBuildSourceConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(PREF_WEEKLY_PRESET, config.weeklyPreset)
            .putString(PREF_WEEKLY_CUSTOM_URL, config.weeklyCustomUrl)
            .putString(PREF_MONTHLY_PRESET, config.monthlyPreset)
            .putString(PREF_MONTHLY_CUSTOM_URL, config.monthlyCustomUrl)
            .putString(PREF_QUARTERLY_PRESET, config.quarterlyPreset)
            .putString(PREF_QUARTERLY_CUSTOM_URL, config.quarterlyCustomUrl)
            .apply()
    }

    fun reset(context: Context): LocalBuildSourceConfig {
        val def = LocalBuildSourceConfig()
        save(context, def)
        return def
    }
}
