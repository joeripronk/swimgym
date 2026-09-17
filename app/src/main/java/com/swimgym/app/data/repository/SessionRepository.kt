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
        private val USER_NAME = stringPreferencesKey("user_name")
        private val USER_EMAIL = stringPreferencesKey("user_email")
        private val SELECTED_LEVEL = stringPreferencesKey("selected_level")
        private val SELECTED_CALENDAR = stringPreferencesKey("selected_calendar")
        private val SELECTED_CALENDAR_ID = stringPreferencesKey("selected_calendar_id")
        private val REMINDER_MINUTES = stringPreferencesKey("reminder_minutes")
        private val REMINDER_ENABLED = stringPreferencesKey("reminder_enabled")
        private val REMINDER_TIMES = stringPreferencesKey("reminder_times")
        private val HIDE_FULLY_BOOKED = stringPreferencesKey("hide_fully_booked")
        private val WORK_TIME_FILTER_ENABLED = stringPreferencesKey("work_time_filter_enabled")
        private val WORK_TIME_MONDAY = stringPreferencesKey("work_time_monday")
        private val WORK_TIME_MONDAY_ENABLED = stringPreferencesKey("work_time_monday_enabled")
        private val WORK_TIME_TUESDAY = stringPreferencesKey("work_time_tuesday")
        private val WORK_TIME_TUESDAY_ENABLED = stringPreferencesKey("work_time_tuesday_enabled")
        private val WORK_TIME_WEDNESDAY = stringPreferencesKey("work_time_wednesday")
        private val WORK_TIME_WEDNESDAY_ENABLED = stringPreferencesKey("work_time_wednesday_enabled")
        private val WORK_TIME_THURSDAY = stringPreferencesKey("work_time_thursday")
        private val WORK_TIME_THURSDAY_ENABLED = stringPreferencesKey("work_time_thursday_enabled")
        private val WORK_TIME_FRIDAY = stringPreferencesKey("work_time_friday")
        private val WORK_TIME_FRIDAY_ENABLED = stringPreferencesKey("work_time_friday_enabled")
        private val WORK_TIME_SATURDAY = stringPreferencesKey("work_time_saturday")
        private val WORK_TIME_SATURDAY_ENABLED = stringPreferencesKey("work_time_saturday_enabled")
        private val WORK_TIME_SUNDAY = stringPreferencesKey("work_time_sunday")
        private val WORK_TIME_SUNDAY_ENABLED = stringPreferencesKey("work_time_sunday_enabled")
        private val COOKIES = stringPreferencesKey("cookies")
        private val USER_AGENT = stringPreferencesKey("user_agent")
        private val ALARM_NOTIFICATION_ENABLED = stringPreferencesKey("alarm_notification_enabled")
    }

    val authToken: Flow<String?> = context.dataStore.data.map { it[AUTH_TOKEN] }

    val isLoggedIn: Flow<Boolean> = authToken.map { it != null }

    val userName: Flow<String?> = context.dataStore.data.map { it[USER_NAME] }

    val userEmail: Flow<String?> = context.dataStore.data.map { it[USER_EMAIL] }

    val selectedLevel: Flow<String?> = context.dataStore.data.map { it[SELECTED_LEVEL] }

    val selectedCalendar: Flow<String?> = context.dataStore.data.map { it[SELECTED_CALENDAR] }

    val selectedCalendarId: Flow<Long?> = context.dataStore.data.map { it[SELECTED_CALENDAR_ID]?.toLongOrNull() }

    private val _hideFullyBooked = context.dataStore.data.map { it[HIDE_FULLY_BOOKED]?.toBoolean() ?: false }
    val hideFullyBooked: Flow<Boolean> = _hideFullyBooked

    private val _workTimeFilterEnabled = context.dataStore.data.map { it[WORK_TIME_FILTER_ENABLED]?.toBoolean() ?: false }
    val workTimeFilterEnabled: Flow<Boolean> = _workTimeFilterEnabled

    private val _workTimeEnabledDays = context.dataStore.data.map { prefs ->
        listOf(
            prefs[WORK_TIME_MONDAY_ENABLED]?.toBoolean() ?: true,
            prefs[WORK_TIME_TUESDAY_ENABLED]?.toBoolean() ?: true,
            prefs[WORK_TIME_WEDNESDAY_ENABLED]?.toBoolean() ?: true,
            prefs[WORK_TIME_THURSDAY_ENABLED]?.toBoolean() ?: true,
            prefs[WORK_TIME_FRIDAY_ENABLED]?.toBoolean() ?: true,
            prefs[WORK_TIME_SATURDAY_ENABLED]?.toBoolean() ?: false,
            prefs[WORK_TIME_SUNDAY_ENABLED]?.toBoolean() ?: false
        )
    }
    val workTimeEnabledDays: Flow<List<Boolean>> = _workTimeEnabledDays

    private val _workTimeRanges = context.dataStore.data.map { prefs ->
        val keyList = listOf(
            WORK_TIME_MONDAY, WORK_TIME_TUESDAY, WORK_TIME_WEDNESDAY,
            WORK_TIME_THURSDAY, WORK_TIME_FRIDAY, WORK_TIME_SATURDAY, WORK_TIME_SUNDAY
        )
        keyList.map { key ->
            prefs[key]?.let { timeStr ->
                val parts = timeStr.split("|")
                if (parts.size == 2) Pair(parts[0], parts[1]) else Pair("08:00", "18:00")
            } ?: Pair("08:00", "18:00")
        }
    }
    val workTimeRanges: Flow<List<Pair<String, String>>> = _workTimeRanges

    suspend fun saveSession(token: String, name: String, email: String) {
        context.dataStore.edit { prefs ->
            prefs[AUTH_TOKEN] = token
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

    suspend fun saveReminderTimes(times: List<Int>) {
        context.dataStore.edit { prefs ->
            prefs[REMINDER_TIMES] = times.joinToString(",")
        }
    }

    suspend fun getReminderTimes(): List<Int> {
        return context.dataStore.data
            .map { it[REMINDER_TIMES] }
            .first()?.let { timesStr ->
                if (timesStr.isNullOrBlank()) emptyList()
                else timesStr.split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it > 0 }
            } ?: emptyList()
    }

    suspend fun saveHideFullyBooked(hide: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[HIDE_FULLY_BOOKED] = hide.toString()
        }
    }

    suspend fun getHideFullyBooked(): Boolean {
        return context.dataStore.data
            .map { it[HIDE_FULLY_BOOKED]?.toBoolean() ?: false }
            .first()
    }

    suspend fun saveWorkTimeFilterEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[WORK_TIME_FILTER_ENABLED] = enabled.toString()
        }
    }

    suspend fun getWorkTimeFilterEnabled(): Boolean {
        return context.dataStore.data
            .map { it[WORK_TIME_FILTER_ENABLED]?.toBoolean() ?: false }
            .first()
    }

    suspend fun saveWorkTimeForDay(day: Int, startTime: String, endTime: String) {
        val key = when (day) {
            0 -> WORK_TIME_MONDAY
            1 -> WORK_TIME_TUESDAY
            2 -> WORK_TIME_WEDNESDAY
            3 -> WORK_TIME_THURSDAY
            4 -> WORK_TIME_FRIDAY
            5 -> WORK_TIME_SATURDAY
            6 -> WORK_TIME_SUNDAY
            else -> WORK_TIME_MONDAY
        }
        context.dataStore.edit { prefs ->
            prefs[key] = "$startTime|$endTime"
        }
    }

    suspend fun getWorkTimeForDay(day: Int): Pair<String, String> {
        val key = when (day) {
            0 -> WORK_TIME_MONDAY
            1 -> WORK_TIME_TUESDAY
            2 -> WORK_TIME_WEDNESDAY
            3 -> WORK_TIME_THURSDAY
            4 -> WORK_TIME_FRIDAY
            5 -> WORK_TIME_SATURDAY
            6 -> WORK_TIME_SUNDAY
            else -> WORK_TIME_MONDAY
        }
        return context.dataStore.data
            .map { it[key] }
            .first()?.let { timeStr ->
                val parts = timeStr.split("|")
                if (parts.size == 2) Pair(parts[0], parts[1]) else Pair("08:00", "18:00")
            } ?: Pair("08:00", "18:00")
    }

    suspend fun saveWorkTimeEnabledForDay(day: Int, enabled: Boolean) {
        val key = when (day) {
            0 -> WORK_TIME_MONDAY_ENABLED
            1 -> WORK_TIME_TUESDAY_ENABLED
            2 -> WORK_TIME_WEDNESDAY_ENABLED
            3 -> WORK_TIME_THURSDAY_ENABLED
            4 -> WORK_TIME_FRIDAY_ENABLED
            5 -> WORK_TIME_SATURDAY_ENABLED
            6 -> WORK_TIME_SUNDAY_ENABLED
            else -> WORK_TIME_MONDAY_ENABLED
        }
        context.dataStore.edit { prefs ->
            prefs[key] = enabled.toString()
        }
    }

    suspend fun getWorkTimeEnabledForDay(day: Int): Boolean {
        val key = when (day) {
            0 -> WORK_TIME_MONDAY_ENABLED
            1 -> WORK_TIME_TUESDAY_ENABLED
            2 -> WORK_TIME_WEDNESDAY_ENABLED
            3 -> WORK_TIME_THURSDAY_ENABLED
            4 -> WORK_TIME_FRIDAY_ENABLED
            5 -> WORK_TIME_SATURDAY_ENABLED
            6 -> WORK_TIME_SUNDAY_ENABLED
            else -> WORK_TIME_MONDAY_ENABLED
        }
        return context.dataStore.data
            .map { it[key]?.toBoolean() ?: (day < 5) } // Monday-Friday enabled by default
            .first()
    }

    suspend fun saveAlarmNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[ALARM_NOTIFICATION_ENABLED] = enabled.toString()
        }
    }

    suspend fun getAlarmNotificationEnabled(): Boolean {
        return context.dataStore.data
            .map { it[ALARM_NOTIFICATION_ENABLED]?.toBoolean() ?: true }
            .first()
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