package com.kotiktortiktech.justvibrait.data

enum class VibeProfile {
    SOFT,
    SHARP,
    DYNAMIC
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class AppSettings(
    val intensity: Float = 0.72f,
    val sensitivity: Float = 0.55f,
    val syncDelayMs: Int = 60,
    val profile: VibeProfile = VibeProfile.DYNAMIC,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val telemetryEnabled: Boolean = false,
    val telemetryHost: String = ""
) {
    /** Normalized amplitude threshold used by the beat detector and haptics. */
    val triggerThreshold: Float
        get() = 0.05f + (1f - sensitivity.coerceIn(0f, 1f)) * 0.35f
}
