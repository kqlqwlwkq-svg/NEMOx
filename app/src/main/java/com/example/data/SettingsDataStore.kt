package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.nemoxDataStore: DataStore<Preferences> by preferencesDataStore(name = "nemox_preferences")

data class LocalAppPreferences(
    val languageCode: String = "system",
    val darkMode: Boolean = true,
    val lastBackgroundSyncMillis: Long = 0L
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("preferred_language")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val LAST_SYNC = longPreferencesKey("last_background_sync")
    }

    val preferencesFlow: Flow<LocalAppPreferences> = context.nemoxDataStore.data.map { prefs ->
        LocalAppPreferences(
            languageCode = prefs[Keys.LANGUAGE] ?: "system",
            darkMode = prefs[Keys.DARK_MODE] ?: true,
            lastBackgroundSyncMillis = prefs[Keys.LAST_SYNC] ?: 0L
        )
    }

    suspend fun setLanguage(code: String) {
        context.nemoxDataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = code
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.nemoxDataStore.edit { prefs ->
            prefs[Keys.DARK_MODE] = enabled
        }
    }

    suspend fun setLastBackgroundSync(millis: Long) {
        context.nemoxDataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC] = millis
        }
    }
}
