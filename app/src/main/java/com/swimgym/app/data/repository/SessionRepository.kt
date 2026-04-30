package com.swimgym.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

@Singleton
class SessionRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val USER_ID = stringPreferencesKey("user_id")
        private val USER_NAME = stringPreferencesKey("user_name")
        private val USER_EMAIL = stringPreferencesKey("user_email")
        private val SELECTED_LEVEL = stringPreferencesKey("selected_level")
    }

    val authToken: Flow<String?> = context.dataStore.data.map { it[AUTH_TOKEN] }

    val isLoggedIn: Flow<Boolean> = authToken.map { it != null }

    val userName: Flow<String?> = context.dataStore.data.map { it[USER_NAME] }

    val userEmail: Flow<String?> = context.dataStore.data.map { it[USER_EMAIL] }

    val selectedLevel: Flow<String?> = context.dataStore.data.map { it[SELECTED_LEVEL] }

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

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }
}