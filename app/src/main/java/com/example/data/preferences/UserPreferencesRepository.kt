package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class AppLanguage(val code: String) {
    AR("ar"),
    EN("en")
}

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

data class UserPreferences(
    val language: AppLanguage = AppLanguage.AR,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cineboard_prefs")

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val langCode = prefs[Keys.LANGUAGE] ?: AppLanguage.AR.code
            val language = AppLanguage.entries.firstOrNull { it.code == langCode } ?: AppLanguage.AR

            val themeRaw = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
            val themeMode = ThemeMode.entries.firstOrNull { it.name == themeRaw } ?: ThemeMode.SYSTEM

            val dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true

            UserPreferences(
                language = language,
                themeMode = themeMode,
                dynamicColor = dynamicColor
            )
        }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = language.code
        }
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = themeMode.name
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DYNAMIC_COLOR] = enabled
        }
    }
}
