package com.swimgym.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

class SessionRepository(
    private val context: Context
) {
    companion object {
        private val AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val USER_ID = stringPreferencesKey("user_id")
        private val USER_NAME = stringPreferencesKey("user_name")
        private val USER_EMAIL = stringPreferencesKey("user_email")
        private val SELECTED_LEVEL = stringPreferencesKey("selected_level")
        private val SELECTED_CALENDAR = stringPreferencesKey("selected_calendar")
        private val SELECTED_CALENDAR_ID = stringPreferencesKey("selected_calendar_id")
        private val REMINDER_MINUTES = stringPreferencesKey("reminder_minutes")
        private val REMINDER_ENABLED = stringPreferencesKey("reminder_enabled")
        private val COOKIES = stringPreferencesKey("cookies")
        private val USER_AGENT = stringPreferencesKey("user_agent")
    }

    val authToken: Flow<String?> = context.dataStore.data.map { it[AUTH_TOKEN] }

    val isLoggedIn: Flow<Boolean> = authToken.map { it != null }

    val userName: Flow<String?> = context.dataStore.data.map { it[USER_NAME] }

    val userEmail: Flow<String?> = context.dataStore.data.map { it[USER_EMAIL] }

    val selectedLevel: Flow<String?> = context.dataStore.data.map { it[SELECTED_LEVEL] }

    val selectedCalendar: Flow<String?> = context.dataStore.data.map { it[SELECTED_CALENDAR] }

    val selectedCalendarId: Flow<Long?> = context.dataStore.data.map { it[SELECTED_CALENDAR_ID]?.toLongOrNull() }

    suspend fun saveSession(token: String, userId: Int, name: String, email: String) {
        context.dataStore.edit { prefs ->
            prefs[AUTH_TOKEN] = token
            prefs[USER_ID] = userId.toString()
            prefs[USER_NAME] = name
            prefs[USER_EMAIL] = email
        }
    }

    suspend fun saveSelectedLevel(level: String) {
        context.dataStore.edit { prefs ->
            prefs[SELECTED_LEVEL] = level
        }
    }

    suspend fun saveSelectedCalendar(calendar: String) {
        context.dataStore.edit { prefs ->
            prefs[SELECTED_CALENDAR] = calendar
        }
    }

    suspend fun saveSelectedCalendarId(calendarId: Long) {
        context.dataStore.edit { prefs ->
            prefs[SELECTED_CALENDAR_ID] = calendarId.toString()
        }
    }

    suspend fun saveReminderConfig(minutes: Int, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[REMINDER_MINUTES] = minutes.toString()
            prefs[REMINDER_ENABLED] = enabled.toString()
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun saveCookies(cookies: Map<String, String>) {
        val cookieString = cookies.map { "${it.key}=${it.value}" }.joinToString("; ")
        context.dataStore.edit { prefs ->
            prefs[COOKIES] = cookieString
        }
    }

    suspend fun loadCookies(): Map<String, String> {
        val cookieString = context.dataStore.data
            .map { it[COOKIES] }
            .first() ?: return emptyMap()

        return cookieString.split("; ")
            .filter { it.contains("=") }
            .map { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else "" to ""
            }
            .filter { it.first.isNotBlank() }
            .toMap()
    }

    suspend fun getReminderMinutes(): Int {
        return context.dataStore.data
            .map { it[REMINDER_MINUTES]?.toIntOrNull() ?: 30 }
            .first()
    }

    suspend fun getReminderEnabled(): Boolean {
        return context.dataStore.data
            .map { it[REMINDER_ENABLED]?.toBoolean() ?: true }
            .first()
    }

    suspend fun saveUserAgent(userAgent: String) {
        context.dataStore.edit { prefs ->
            prefs[USER_AGENT] = userAgent
        }
    }

    suspend fun loadUserAgent(): String? {
        return context.dataStore.data
            .map { it[USER_AGENT] }
            .first()
    }
}