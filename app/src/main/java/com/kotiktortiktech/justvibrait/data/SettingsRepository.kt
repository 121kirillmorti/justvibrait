package com.kotiktortiktech.justvibrait.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for user preferences. The singleton is shared by the UI,
 * the local player and the foreground live-capture service so changes apply instantly.
 */
class SettingsRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        write(next)
        _state.value = next
    }

    fun reset() {
        val defaults = AppSettings()
        prefs.edit().clear().apply()
        _state.value = defaults
    }

    private fun read(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            intensity = prefs.getFloat(KEY_INTENSITY, defaults.intensity),
            sensitivity = prefs.getFloat(KEY_SENSITIVITY, defaults.sensitivity),
            syncDelayMs = prefs.getInt(KEY_DELAY, defaults.syncDelayMs),
            profile = prefs.getString(KEY_PROFILE, null)
                ?.let { runCatching { VibeProfile.valueOf(it) }.getOrNull() }
                ?: defaults.profile,
            themeMode = prefs.getString(KEY_THEME, null)
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: defaults.themeMode,
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC, defaults.dynamicColor),
            telemetryEnabled = prefs.getBoolean(KEY_TELEMETRY, defaults.telemetryEnabled),
            telemetryHost = prefs.getString(KEY_TELEMETRY_HOST, defaults.telemetryHost)
                ?: defaults.telemetryHost
        )
    }

    private fun write(settings: AppSettings) {
        prefs.edit()
            .putFloat(KEY_INTENSITY, settings.intensity)
            .putFloat(KEY_SENSITIVITY, settings.sensitivity)
            .putInt(KEY_DELAY, settings.syncDelayMs)
            .putString(KEY_PROFILE, settings.profile.name)
            .putString(KEY_THEME, settings.themeMode.name)
            .putBoolean(KEY_DYNAMIC, settings.dynamicColor)
            .putBoolean(KEY_TELEMETRY, settings.telemetryEnabled)
            .putString(KEY_TELEMETRY_HOST, settings.telemetryHost)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "justvibrait_settings"
        private const val KEY_INTENSITY = "intensity"
        private const val KEY_SENSITIVITY = "sensitivity"
        private const val KEY_DELAY = "sync_delay"
        private const val KEY_PROFILE = "profile"
        private const val KEY_THEME = "theme"
        private const val KEY_DYNAMIC = "dynamic_color"
        private const val KEY_TELEMETRY = "pc_telemetry"
        private const val KEY_TELEMETRY_HOST = "pc_telemetry_host"

        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
