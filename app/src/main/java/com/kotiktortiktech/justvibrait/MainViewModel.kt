package com.kotiktortiktech.justvibrait

import android.app.Application
import android.net.Uri
import android.view.SurfaceView
import androidx.lifecycle.AndroidViewModel
import com.kotiktortiktech.justvibrait.data.AppSettings
import com.kotiktortiktech.justvibrait.data.AppLanguage
import com.kotiktortiktech.justvibrait.data.SettingsRepository
import com.kotiktortiktech.justvibrait.data.VibeProfile
import com.kotiktortiktech.justvibrait.debug.VibeTelemetry
import com.kotiktortiktech.justvibrait.media.LocalMediaManager
import com.kotiktortiktech.justvibrait.vibration.VibeEngine
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository.get(application)

    val settings: StateFlow<AppSettings> = settingsRepository.state

    private val localVibeEngine = VibeEngine(
        context = application,
        settingsProvider = { settingsRepository.state.value },
        telemetry = VibeTelemetry.get(application),
        sourceProvider = { "LOCAL" }
    )

    private val localMedia = LocalMediaManager(application, localVibeEngine)

    val localState = localMedia.state
    val localFeatures = localMedia.features

    fun openMedia(uri: Uri) = localMedia.open(uri)

    fun togglePlayback() = localMedia.togglePlayback()

    fun seekTo(positionMs: Long) = localMedia.seekTo(positionMs)

    fun attachVideoSurface(view: SurfaceView) = localMedia.attachVideoSurface(view)

    fun detachVideoSurface() = localMedia.detachVideoSurface()

    fun clearLocalError() = localMedia.clearError()

    fun setIntensity(value: Float) {
        settingsRepository.update { it.copy(intensity = value.coerceIn(0f, 1f)) }
    }

    fun setSensitivity(value: Float) {
        settingsRepository.update { it.copy(sensitivity = value.coerceIn(0f, 1f)) }
    }

    fun setSyncDelay(value: Int) {
        settingsRepository.update { it.copy(syncDelayMs = value.coerceIn(0, 250)) }
    }

    fun setProfile(profile: VibeProfile) {
        settingsRepository.update { it.copy(profile = profile) }
        localVibeEngine.preview(profile)
    }

    fun setThemeMode(mode: com.kotiktortiktech.justvibrait.data.ThemeMode) {
        settingsRepository.update { it.copy(themeMode = mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        settingsRepository.update { it.copy(dynamicColor = enabled) }
    }

    fun setLanguage(language: AppLanguage) {
        settingsRepository.update { it.copy(language = language) }
        LanguageHelper.apply(language)
    }

    fun setTelemetryEnabled(enabled: Boolean) {
        settingsRepository.update { it.copy(telemetryEnabled = enabled) }
    }

    fun setTelemetryHost(host: String) {
        settingsRepository.update { it.copy(telemetryHost = host.take(64)) }
    }

    fun resetSettings() {
        settingsRepository.reset()
    }

    override fun onCleared() {
        localMedia.release()
        localVibeEngine.release()
        super.onCleared()
    }
}
