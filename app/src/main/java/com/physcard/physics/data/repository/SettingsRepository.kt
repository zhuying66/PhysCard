package com.physcard.physics.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.physcard.physics.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "physics_settings",
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val desiredRetention: Float = 0.90f,
    val dailyNewFormulas: Int = 3,
    val seedVersion: Int = 0,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val retention = floatPreferencesKey("desired_retention")
        val dailyNew = intPreferencesKey("daily_new_formulas")
        val seedVersion = intPreferencesKey("seed_version")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        AppSettings(
            themeMode = preferences[Keys.theme]?.let { raw ->
                runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
            } ?: ThemeMode.SYSTEM,
            desiredRetention = (preferences[Keys.retention] ?: 0.90f).coerceIn(0.80f, 0.97f),
            dailyNewFormulas = (preferences[Keys.dailyNew] ?: 3).coerceIn(1, 8),
            seedVersion = preferences[Keys.seedVersion] ?: 0,
        )
    }

    suspend fun updateTheme(themeMode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.theme] = themeMode.name }
    }

    suspend fun updateDesiredRetention(value: Float) {
        context.settingsDataStore.edit {
            it[Keys.retention] = value.coerceIn(0.80f, 0.97f)
        }
    }

    suspend fun updateDailyNewFormulas(value: Int) {
        context.settingsDataStore.edit {
            it[Keys.dailyNew] = value.coerceIn(1, 8)
        }
    }

    suspend fun updateSeedVersion(value: Int) {
        context.settingsDataStore.edit { it[Keys.seedVersion] = value }
    }
}
