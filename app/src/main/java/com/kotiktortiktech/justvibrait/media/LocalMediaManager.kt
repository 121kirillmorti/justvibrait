package com.kotiktortiktech.justvibrait.media

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.OpenableColumns
import android.view.SurfaceView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.kotiktortiktech.justvibrait.audio.AudioAnalyzer
import com.kotiktortiktech.justvibrait.audio.AudioFeatures
import com.kotiktortiktech.justvibrait.audio.AudioTapProcessor
import com.kotiktortiktech.justvibrait.data.SettingsRepository
import com.kotiktortiktech.justvibrait.vibration.VibeEngine
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LocalError {
    OPEN,
    PLAYBACK
}

data class LocalPlaybackState(
    val uri: Uri? = null,
    val title: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val hasVideo: Boolean = false,
    val error: LocalError? = null
)

/**
 * Owns ExoPlayer for local media, taps the decoded PCM stream and feeds it to the
 * analyzer. Video uses the same player timeline, so haptics stay synchronized with
 * both the audio track and the picture.
 */
@UnstableApi
class LocalMediaManager(
    context: Context,
    private val vibeEngine: VibeEngine
) {
    private val appContext = context.applicationContext
    private val settings = SettingsRepository.get(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(LocalPlaybackState())
    val state: StateFlow<LocalPlaybackState> = _state.asStateFlow()

    private val _features = MutableStateFlow(AudioFeatures.SILENCE)
    val features: StateFlow<AudioFeatures> = _features.asStateFlow()

    private val analysisThread = HandlerThread("JustVibrait-Analysis").apply { start() }
    private val analysisHandler = Handler(analysisThread.looper)
    private val pendingAnalysis = AtomicInteger(0)

    private val analyzer = AudioAnalyzer(
        settingsProvider = { settings.state.value },
        onFeatures = { vibeEngine.process(it) },
        onDisplay = { _features.value = it }
    )

    private val tapProcessor = AudioTapProcessor { samples, channelCount, sampleRate ->
        if (pendingAnalysis.get() < 4) {
            pendingAnalysis.incrementAndGet()
            analysisHandler.post {
                try {
                    analyzer.process(samples, channelCount, sampleRate)
                } finally {
                    pendingAnalysis.decrementAndGet()
                }
            }
        }
    }

    private val renderersFactory = object : DefaultRenderersFactory(appContext) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean
        ): AudioSink {
            return DefaultAudioSink.Builder(context)
                // Integer PCM keeps the custom processor in the pipeline on every device.
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(DefaultAudioSink.DefaultAudioProcessorChain(tapProcessor))
                .build()
        }
    }

    private val player: ExoPlayer = ExoPlayer.Builder(appContext, renderersFactory).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        setHandleAudioBecomingNoisy(true)
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val duration = safeDuration()
                _state.update {
                    it.copy(
                        isBuffering = playbackState == Player.STATE_BUFFERING,
                        isPlaying = isPlaying,
                        durationMs = duration
                    )
                }
                if (playbackState == Player.STATE_ENDED) {
                    vibeEngine.stop()
                    resetAnalyzer()
                    _features.value = AudioFeatures.SILENCE
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                if (!isPlaying) {
                    vibeEngine.stop()
                    resetAnalyzer()
                    _features.value = AudioFeatures.SILENCE
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                _state.update {
                    it.copy(hasVideo = videoSize.width > 0 && videoSize.height > 0)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                vibeEngine.stop()
                _state.update {
                    it.copy(
                        isPlaying = false,
                        isBuffering = false,
                        error = LocalError.PLAYBACK
                    )
                }
            }
        })
    }

    init {
        scope.launch {
            while (isActive) {
                if (player.mediaItemCount > 0) {
                    val duration = safeDuration()
                    val position = player.currentPosition.coerceAtLeast(0L)
                    _state.update {
                        it.copy(
                            durationMs = duration,
                            positionMs = position.coerceAtMost(
                                if (duration > 0L) duration else Long.MAX_VALUE
                            )
                        )
                    }
                }
                delay(250L)
            }
        }
    }

    fun open(uri: Uri) {
        vibeEngine.stop()
        resetAnalyzer()
        _features.value = AudioFeatures.SILENCE
        val title = queryDisplayName(uri)
        _state.value = LocalPlaybackState(
            uri = uri,
            title = title,
            isBuffering = true
        )
        runCatching {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = true
        }.onFailure {
            _state.update { state ->
                state.copy(isBuffering = false, error = LocalError.OPEN)
            }
        }
    }

    fun togglePlayback() {
        if (player.mediaItemCount == 0) return
        if (player.isPlaying) {
            player.pause()
            vibeEngine.stop()
            resetAnalyzer()
            _features.value = AudioFeatures.SILENCE
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0L)
            }
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        if (player.mediaItemCount == 0) return
        player.seekTo(positionMs.coerceAtLeast(0L))
        _state.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
        vibeEngine.stop()
        resetAnalyzer()
        _features.value = AudioFeatures.SILENCE
    }

    fun attachVideoSurface(view: SurfaceView) {
        player.setVideoSurfaceView(view)
    }

    fun detachVideoSurface() {
        player.clearVideoSurface()
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun release() {
        scope.cancel()
        vibeEngine.stop()
        runCatching { player.release() }
        analysisThread.quitSafely()
    }

    private fun resetAnalyzer() {
        analysisHandler.post { analyzer.reset() }
    }

    private fun safeDuration(): Long {
        val duration = player.duration
        return if (duration > 0L && duration != C.TIME_UNSET) duration else 0L
    }

    private fun queryDisplayName(uri: Uri): String {
        val fromProvider = runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return fromProvider
            ?: uri.lastPathSegment
            ?: uri.toString()
    }
}
