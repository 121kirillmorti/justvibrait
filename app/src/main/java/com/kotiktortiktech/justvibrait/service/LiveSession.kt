package com.kotiktortiktech.justvibrait.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.kotiktortiktech.justvibrait.audio.AudioFeatures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class CaptureMode {
    PLAYBACK,
    MIC
}

enum class LiveError {
    PERMISSION,
    PROJECTION,
    AUDIO_INIT,
    CAPTURE,
    PROJECTION_STOPPED
}

data class LiveState(
    val isRunning: Boolean = false,
    val isPreparing: Boolean = false,
    val mode: CaptureMode = CaptureMode.PLAYBACK,
    val startedAtElapsedMs: Long = 0L,
    val error: LiveError? = null
)

/**
 * Process-wide state of the live capture session. The started foreground service
 * writes here; every screen can observe it without binding to the service.
 */
object LiveSession {

    internal const val ACTION_START_PLAYBACK = "com.kotiktortiktech.justvibrait.action.START_PLAYBACK"
    internal const val ACTION_START_MIC = "com.kotiktortiktech.justvibrait.action.START_MIC"
    internal const val ACTION_STOP = "com.kotiktortiktech.justvibrait.action.STOP"
    internal const val EXTRA_RESULT_CODE = "extra_result_code"
    internal const val EXTRA_DATA = "extra_data"

    private val _state = MutableStateFlow(LiveState())
    val state = _state.asStateFlow()

    private val _features = MutableStateFlow(AudioFeatures.SILENCE)
    val features = _features.asStateFlow()

    fun startPlayback(context: Context, resultCode: Int, data: Intent) {
        val intent = Intent(context, LiveVibeService::class.java)
            .setAction(ACTION_START_PLAYBACK)
            .putExtra(EXTRA_RESULT_CODE, resultCode)
            .putExtra(EXTRA_DATA, data)
        startServiceSafely(context, intent, LiveError.PROJECTION)
    }

    fun startMic(context: Context) {
        val intent = Intent(context, LiveVibeService::class.java)
            .setAction(ACTION_START_MIC)
        startServiceSafely(context, intent, LiveError.PERMISSION)
    }

    fun stop(context: Context) {
        runCatching {
            context.startService(
                Intent(context, LiveVibeService::class.java).setAction(ACTION_STOP)
            )
        }
    }

    private fun startServiceSafely(context: Context, intent: Intent, error: LiveError) {
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (t: Throwable) {
            _state.update {
                it.copy(isRunning = false, isPreparing = false, error = error)
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    internal fun setRunning(state: LiveState) {
        _state.value = state
    }

    internal fun setFeatures(features: AudioFeatures) {
        _features.value = features
    }

    internal fun resetFeatures() {
        _features.value = AudioFeatures.SILENCE
    }

    internal fun update(block: (LiveState) -> LiveState) {
        _state.update(block)
    }
}
