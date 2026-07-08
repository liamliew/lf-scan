package com.lfcreative.lfscan.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lfcreative.lfscan.data.model.TeamMember
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lfscan_session")

@Singleton
class SessionDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val teamMemberKey = stringPreferencesKey("team_member")
    private val scanModeKey = stringPreferencesKey("last_scan_mode")
    private val themeModeKey = stringPreferencesKey("theme_mode")

    val currentMember: Flow<TeamMember?> = context.dataStore.data.map { prefs ->
        prefs[teamMemberKey]?.let { Json.decodeFromString<TeamMember>(it) }
    }

    val scanMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[scanModeKey] ?: "2D"
    }

    // "system" | "light" | "dark" — device-wide, not tied to any employee, so it applies
    // immediately (including on the PIN screen) rather than only after a settings row loads.
    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[themeModeKey] ?: "system"
    }

    suspend fun saveSession(member: TeamMember) {
        context.dataStore.edit { prefs ->
            prefs[teamMemberKey] = Json.encodeToString(member)
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(teamMemberKey)
        }
    }

    suspend fun saveScanMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[scanModeKey] = mode
        }
    }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[themeModeKey] = mode
        }
    }
}
