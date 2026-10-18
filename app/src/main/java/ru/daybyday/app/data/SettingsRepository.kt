package ru.daybyday.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.localSettings: DataStore<Preferences> by preferencesDataStore(name = "settings")

// Shared by all repository instances, including background notification workers.
internal val storageMutex = Mutex()

data class AppSettings(
    val darkTheme: Boolean = true,
    val dailyEnabled: Boolean = false,
    val achievementEnabled: Boolean = false,
    val recordEnabled: Boolean = false,
    val hour: Int = 20,
    val minute: Int = 0,
    val privateNotifications: Boolean = true,
)

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.localSettings

    val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::fromPreferences)
        .distinctUntilChanged()

    suspend fun update(settings: AppSettings) = update { settings }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        storageMutex.withLock {
            dataStore.edit { preferences ->
                val next = transform(fromPreferences(preferences))
                require(next.hour in 0..23 && next.minute in 0..59) {
                    "Укажите корректное время уведомления"
                }
                preferences[Keys.darkTheme] = next.darkTheme
                preferences[Keys.dailyEnabled] = next.dailyEnabled
                preferences[Keys.achievementEnabled] = next.achievementEnabled
                preferences[Keys.recordEnabled] = next.recordEnabled
                preferences[Keys.hour] = next.hour
                preferences[Keys.minute] = next.minute
                preferences[Keys.privateNotifications] = next.privateNotifications
            }
        }
    }

    // Caller holds storageMutex when clearing history and preferences together.
    internal suspend fun clearLocked() {
        dataStore.edit { it.clear() }
    }

    private fun fromPreferences(preferences: Preferences) = AppSettings(
        darkTheme = preferences[Keys.darkTheme] ?: true,
        dailyEnabled = preferences[Keys.dailyEnabled] ?: false,
        achievementEnabled = preferences[Keys.achievementEnabled] ?: false,
        recordEnabled = preferences[Keys.recordEnabled] ?: false,
        hour = (preferences[Keys.hour] ?: 20).coerceIn(0, 23),
        minute = (preferences[Keys.minute] ?: 0).coerceIn(0, 59),
        privateNotifications = preferences[Keys.privateNotifications] ?: true,
    )

    private object Keys {
        val darkTheme = booleanPreferencesKey("dark_theme")
        val dailyEnabled = booleanPreferencesKey("daily_enabled")
        val achievementEnabled = booleanPreferencesKey("achievement_enabled")
        val recordEnabled = booleanPreferencesKey("record_enabled")
        val hour = intPreferencesKey("notification_hour")
        val minute = intPreferencesKey("notification_minute")
        val privateNotifications = booleanPreferencesKey("private_notifications")
    }
}
