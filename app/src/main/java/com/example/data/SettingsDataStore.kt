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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.nemoxDataStore: DataStore<Preferences> by preferencesDataStore(name = "nemox_preferences")

@Serializable
data class CachedArabicTranslation(
    val titleAr: String,
    val summaryAr: String
)

data class LocalAppPreferences(
    val languageCode: String = "ar",
    val darkMode: Boolean = true,
    val lastBackgroundSyncMillis: Long = 0L,
    val isGuestMode: Boolean = false,
    val preferredGenres: List<String> = emptyList(),
    val hasCompletedGenreOnboarding: Boolean = false,
    val recentSearches: List<String> = emptyList(),
    val followedNewsGames: List<String> = emptyList(),
    val preferredNewsCategories: List<String> = emptyList(),
    val newsAlertsEnabled: Boolean = true
)

class SettingsDataStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val LANGUAGE = stringPreferencesKey("preferred_language")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val LAST_SYNC = longPreferencesKey("last_background_sync")
        val IS_GUEST_MODE = booleanPreferencesKey("is_guest_mode")
        val PREFERRED_GENRES = stringPreferencesKey("preferred_genres_json")
        val GENRE_ONBOARDING_DONE = booleanPreferencesKey("genre_onboarding_done")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches_json")
        val FOLLOWED_NEWS_GAMES = stringPreferencesKey("followed_news_games_json")
        val PREFERRED_NEWS_CATEGORIES = stringPreferencesKey("preferred_news_categories_json")
        val NEWS_ALERTS_ENABLED = booleanPreferencesKey("news_alerts_enabled")
        val NEWS_TRANSLATIONS_CACHE = stringPreferencesKey("news_translations_cache_json")
        val GUEST_LIBRARY = stringPreferencesKey("guest_library_json")
        val GUEST_MISSIONS = stringPreferencesKey("guest_missions_json")
        val GUEST_WAITLIST = stringPreferencesKey("guest_waitlist_json")
        val GUEST_NOTIFICATIONS = stringPreferencesKey("guest_notifications_json")
    }

    val preferencesFlow: Flow<LocalAppPreferences> = context.nemoxDataStore.data.map { prefs ->
        val genres = runCatching {
            prefs[Keys.PREFERRED_GENRES]?.let { json.decodeFromString<List<String>>(it) } ?: emptyList()
        }.getOrDefault(emptyList())
        val searches = runCatching {
            prefs[Keys.RECENT_SEARCHES]?.let { json.decodeFromString<List<String>>(it) } ?: emptyList()
        }.getOrDefault(emptyList())
        val followedGames = runCatching {
            prefs[Keys.FOLLOWED_NEWS_GAMES]?.let { json.decodeFromString<List<String>>(it) } ?: emptyList()
        }.getOrDefault(emptyList())
        val newsCats = runCatching {
            prefs[Keys.PREFERRED_NEWS_CATEGORIES]?.let { json.decodeFromString<List<String>>(it) } ?: emptyList()
        }.getOrDefault(emptyList())

        LocalAppPreferences(
            languageCode = prefs[Keys.LANGUAGE] ?: "ar",
            darkMode = prefs[Keys.DARK_MODE] ?: true,
            lastBackgroundSyncMillis = prefs[Keys.LAST_SYNC] ?: 0L,
            isGuestMode = prefs[Keys.IS_GUEST_MODE] ?: false,
            preferredGenres = genres,
            hasCompletedGenreOnboarding = prefs[Keys.GENRE_ONBOARDING_DONE] ?: false,
            recentSearches = searches,
            followedNewsGames = followedGames,
            preferredNewsCategories = newsCats,
            newsAlertsEnabled = prefs[Keys.NEWS_ALERTS_ENABLED] ?: true
        )
    }

    val guestLibraryJsonFlow: Flow<String> = context.nemoxDataStore.data.map { it[Keys.GUEST_LIBRARY] ?: "[]" }
    val guestMissionsJsonFlow: Flow<String> = context.nemoxDataStore.data.map { it[Keys.GUEST_MISSIONS] ?: "[]" }
    val guestWaitlistJsonFlow: Flow<String> = context.nemoxDataStore.data.map { it[Keys.GUEST_WAITLIST] ?: "[]" }
    val guestNotificationsJsonFlow: Flow<String> = context.nemoxDataStore.data.map { it[Keys.GUEST_NOTIFICATIONS] ?: "[]" }

    suspend fun setLanguage(code: String) {
        context.nemoxDataStore.edit { prefs -> prefs[Keys.LANGUAGE] = code }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.nemoxDataStore.edit { prefs -> prefs[Keys.DARK_MODE] = enabled }
    }

    suspend fun setLastBackgroundSync(millis: Long) {
        context.nemoxDataStore.edit { prefs -> prefs[Keys.LAST_SYNC] = millis }
    }

    suspend fun setGuestMode(enabled: Boolean) {
        context.nemoxDataStore.edit { prefs -> prefs[Keys.IS_GUEST_MODE] = enabled }
    }

    suspend fun setPreferredGenres(genres: List<String>, onboardingDone: Boolean = true) {
        context.nemoxDataStore.edit { prefs ->
            prefs[Keys.PREFERRED_GENRES] = json.encodeToString(genres.distinct())
            prefs[Keys.GENRE_ONBOARDING_DONE] = onboardingDone
        }
    }

    suspend fun setNewsPreferences(
        followedGames: List<String>,
        preferredCategories: List<String>,
        newsAlertsEnabled: Boolean
    ) {
        context.nemoxDataStore.edit { prefs ->
            prefs[Keys.FOLLOWED_NEWS_GAMES] = json.encodeToString(followedGames.distinct())
            prefs[Keys.PREFERRED_NEWS_CATEGORIES] = json.encodeToString(preferredCategories.distinct())
            prefs[Keys.NEWS_ALERTS_ENABLED] = newsAlertsEnabled
        }
    }

    suspend fun loadNewsTranslationCache(): Map<String, CachedArabicTranslation> {
        val raw = context.nemoxDataStore.data.first()[Keys.NEWS_TRANSLATIONS_CACHE] ?: return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, CachedArabicTranslation>>(raw)
        }.getOrDefault(emptyMap())
    }

    suspend fun saveNewsTranslationCache(entries: Map<String, CachedArabicTranslation>) {
        // Cap cache at 160 most recent entries to keep DataStore lightweight
        val trimmed = entries.entries.toList().takeLast(160).associate { it.key to it.value }
        context.nemoxDataStore.edit { prefs ->
            prefs[Keys.NEWS_TRANSLATIONS_CACHE] = json.encodeToString(trimmed)
        }
    }

    suspend fun addRecentSearch(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        context.nemoxDataStore.edit { prefs ->
            val current = runCatching {
                prefs[Keys.RECENT_SEARCHES]?.let { json.decodeFromString<List<String>>(it) } ?: emptyList()
            }.getOrDefault(emptyList())
            val updated = (listOf(clean) + current.filterNot { it.equals(clean, ignoreCase = true) }).take(12)
            prefs[Keys.RECENT_SEARCHES] = json.encodeToString(updated)
        }
    }

    suspend fun removeRecentSearch(query: String) {
        context.nemoxDataStore.edit { prefs ->
            val current = runCatching {
                prefs[Keys.RECENT_SEARCHES]?.let { json.decodeFromString<List<String>>(it) } ?: emptyList()
            }.getOrDefault(emptyList())
            prefs[Keys.RECENT_SEARCHES] = json.encodeToString(current.filterNot { it.equals(query, ignoreCase = true) })
        }
    }

    suspend fun clearRecentSearches() {
        context.nemoxDataStore.edit { prefs -> prefs[Keys.RECENT_SEARCHES] = "[]" }
    }

    suspend fun saveGuestLibraryJson(raw: String) {
        context.nemoxDataStore.edit { it[Keys.GUEST_LIBRARY] = raw }
    }

    suspend fun saveGuestMissionsJson(raw: String) {
        context.nemoxDataStore.edit { it[Keys.GUEST_MISSIONS] = raw }
    }

    suspend fun saveGuestWaitlistJson(raw: String) {
        context.nemoxDataStore.edit { it[Keys.GUEST_WAITLIST] = raw }
    }

    suspend fun saveGuestNotificationsJson(raw: String) {
        context.nemoxDataStore.edit { it[Keys.GUEST_NOTIFICATIONS] = raw }
    }
}
