package com.example.authenticator

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Persists the app language and applies it to the activity configuration. */
internal object LanguageManager {
    const val SYSTEM = ""
    const val CHINESE = "zh"
    const val ENGLISH = "en"

    private const val PREFERENCES = "app_preferences"
    private const val LANGUAGE_KEY = "language"

    fun selectedLanguage(context: Context): String = context
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getString(LANGUAGE_KEY, SYSTEM)
        .orEmpty()

    fun setLanguage(context: Context, language: String) {
        require(language == SYSTEM || language == CHINESE || language == ENGLISH)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(LANGUAGE_KEY, language)
            .apply()
    }

    fun wrap(context: Context): Context {
        val language = selectedLanguage(context)
        if (language == SYSTEM) return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        return context.createConfigurationContext(configuration)
    }
}
