 package fr.geotower.utils

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import fr.geotower.R
import java.util.Locale

object AppLocale {
    const val LANGUAGE_SYSTEM = "Système"
    const val LANGUAGE_FRENCH = "Français"
    const val LANGUAGE_ENGLISH = "English"
    const val LANGUAGE_PORTUGUESE = "Português"
    const val LANGUAGE_ITALIAN = "Italiano"
    const val LANGUAGE_GERMAN = "Deutsch"
    const val LANGUAGE_SPANISH = "Español"

    fun languageTagForPreference(language: String): String? = when {
        language.equals(LANGUAGE_FRENCH, ignoreCase = true) || language.equals("fr", ignoreCase = true) -> "fr"
        language.equals(LANGUAGE_ENGLISH, ignoreCase = true) || language.equals("en", ignoreCase = true) -> "en"
        language.equals(LANGUAGE_PORTUGUESE, ignoreCase = true) || language.equals("pt", ignoreCase = true) -> "pt"
        language.equals(LANGUAGE_ITALIAN, ignoreCase = true) || language.equals("it", ignoreCase = true) -> "it"
        language.equals(LANGUAGE_GERMAN, ignoreCase = true) || language.equals("de", ignoreCase = true) -> "de"
        language.equals(LANGUAGE_SPANISH, ignoreCase = true) || language.equals("es", ignoreCase = true) -> "es"
        else -> null
    }

    @StringRes
    fun languageDisplayNameRes(language: String): Int = when {
        language.equals(LANGUAGE_FRENCH, ignoreCase = true) || language.equals("fr", ignoreCase = true) -> R.string.language_french_name
        language.equals(LANGUAGE_ENGLISH, ignoreCase = true) || language.equals("en", ignoreCase = true) -> R.string.language_english_name
        language.equals(LANGUAGE_PORTUGUESE, ignoreCase = true) || language.equals("pt", ignoreCase = true) -> R.string.language_portuguese_name
        language.equals(LANGUAGE_ITALIAN, ignoreCase = true) || language.equals("it", ignoreCase = true) -> R.string.language_italian_name
        language.equals(LANGUAGE_GERMAN, ignoreCase = true) || language.equals("de", ignoreCase = true) -> R.string.language_german_name
        language.equals(LANGUAGE_SPANISH, ignoreCase = true) || language.equals("es", ignoreCase = true) -> R.string.language_spanish_name
        else -> R.string.language_system
    }

    fun languageFlag(language: String): String = when {
        language.equals(LANGUAGE_FRENCH, ignoreCase = true) || language.equals("fr", ignoreCase = true) -> "\uD83C\uDDEB\uD83C\uDDF7"
        language.equals(LANGUAGE_ENGLISH, ignoreCase = true) || language.equals("en", ignoreCase = true) -> "\uD83C\uDDEC\uD83C\uDDE7"
        language.equals(LANGUAGE_PORTUGUESE, ignoreCase = true) || language.equals("pt", ignoreCase = true) -> "\uD83C\uDDF5\uD83C\uDDF9"
        language.equals(LANGUAGE_ITALIAN, ignoreCase = true) || language.equals("it", ignoreCase = true) -> "\uD83C\uDDEE\uD83C\uDDF9"
        language.equals(LANGUAGE_GERMAN, ignoreCase = true) || language.equals("de", ignoreCase = true) -> "\uD83C\uDDE9\uD83C\uDDEA"
        language.equals(LANGUAGE_SPANISH, ignoreCase = true) || language.equals("es", ignoreCase = true) -> "\uD83C\uDDEA\uD83C\uDDF8"
        language.equals(LANGUAGE_SYSTEM, ignoreCase = true) -> "\uD83D\uDCF1"
        else -> "\uD83C\uDF10"
    }

    fun localizedConfiguration(base: Configuration, language: String): Configuration {
        val languageTag = languageTagForPreference(language) ?: return Configuration(base)
        val locale = Locale.forLanguageTag(languageTag)
        return Configuration(base).apply {
            setLocales(LocaleList(locale))
            setLayoutDirection(locale)
        }
    }

    fun localizedContext(context: Context, language: String): Context {
        if (languageTagForPreference(language) == null) return context
        return context.createConfigurationContext(localizedConfiguration(context.resources.configuration, language))
    }

    fun getString(context: Context, language: String, @StringRes resId: Int, vararg formatArgs: Any): String {
        val localizedContext = localizedContext(context, language)
        return if (formatArgs.isEmpty()) {
            localizedContext.getString(resId)
        } else {
            localizedContext.getString(resId, *formatArgs)
        }
    }

    fun applyApplicationLocale(context: Context, language: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val languageTag = languageTagForPreference(language)
        val locales = if (languageTag == null) {
            LocaleList.getEmptyLocaleList()
        } else {
            LocaleList.forLanguageTags(languageTag)
        }
        context.getSystemService(LocaleManager::class.java).applicationLocales = locales
    }
}

@Composable
fun GeoTowerLocaleProvider(
    language: String = AppConfig.appLanguage.value,
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current
    val onBackPressedDispatcherOwner = LocalOnBackPressedDispatcherOwner.current
    val localizedConfiguration = remember(language, baseConfiguration) {
        AppLocale.localizedConfiguration(baseConfiguration, language)
    }
    val localizedContext = remember(baseContext, language, baseConfiguration) {
        AppLocale.localizedContext(baseContext, language)
    }

    if (activityResultRegistryOwner != null && onBackPressedDispatcherOwner != null) {
        CompositionLocalProvider(
            LocalActivityResultRegistryOwner provides activityResultRegistryOwner,
            LocalOnBackPressedDispatcherOwner provides onBackPressedDispatcherOwner,
            LocalContext provides localizedContext,
            LocalConfiguration provides localizedConfiguration,
            content = content
        )
    } else {
        CompositionLocalProvider(
            LocalContext provides localizedContext,
            LocalConfiguration provides localizedConfiguration,
            content = content
        )
    }
}
