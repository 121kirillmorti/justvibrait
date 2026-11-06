package com.kotiktortiktech.justvibrait.service

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kotiktortiktech.justvibrait.MainActivity
import com.kotiktortiktech.justvibrait.R
import com.kotiktortiktech.justvibrait.audio.AudioAnalyzer
import com.kotiktortiktech.justvibrait.audio.AudioFeatures
import com.kotiktortiktech.justvibrait.data.SettingsRepository
import com.kotiktortiktech.justvibrait.debug.VibeTelemetry
import com.kotiktortiktech.justvibrait.vibration.VibeEngine
import kotlin.math.max

/**
 * Foreground service that captures audio in the background and renders haptics.
 *
 * Playback capture follows the Android 14+ requirement strictly: the service calls
 * startForeground() with FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION *before* it asks
 * MediaProjectionManager for the projection, and registers a MediaProjection.Callback.
 */
class LiveVibeService : Service() {

    private companion object {
        const val CHANNEL_ID = "vibe_capture"
        const val NOTIFICATION_ID = 41
        const val WAKE_LOCK_TIMEOUT_MS = 60L * 60L * 1_000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var settings: SettingsRepository
    private lateinit var vibeEngine: VibeEngine
    private lateinit var telemetry: VibeTelemetry
    private var analyzer: AudioAnalyzer? = null
    private var audioRecord: AudioRecord? = null
    private var mediaProjection: MediaProjection? = null
    private var captureThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var running = false

    @Volatile
    private var shuttingDown = false

    private var mode = CaptureMode.MIC

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository.get(this)
        telemetry = VibeTelemetry.get(this)
        vibeEngine = VibeEngine(
            context = this,
            settingsProvider = { settings.state.value },
            telemetry = telemetry,
            sourceProvider = {
                if (mode == CaptureMode.PLAYBACK) "SYSTEM" else "MIC"
            }
        )
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent.action) {
            LiveSession.ACTION_STOP -> {
                stopSession()
            }

            LiveSession.ACTION_START_MIC -> {
                resetForNewSession()
                mode = CaptureMode.MIC
                if (!startForegroundSafely(mode)) {
                    fail(LiveError.PERMISSION)
                    return START_NOT_STICKY
                }
                LiveSession.setRunning(
                    LiveState(isRunning = true, isPreparing = true, mode = mode)
                )
                startMicCapture()
            }

            LiveSession.ACTION_START_PLAYBACK -> {
                resetForNewSession()
                mode = CaptureMode.PLAYBACK
                if (!startForegroundSafely(mode)) {
                    fail(LiveError.PROJECTION)
                    return START_NOT_STICKY
                }
                LiveSession.setRunning(
                    LiveState(isRunning = true, isPreparing = true, mode = mode)
                )

                val resultCode = intent.getIntExtra(
                    LiveSession.EXTRA_RESULT_CODE,
                    Activity.RESULT_CANCELED
                )
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(LiveSession.EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(LiveSession.EXTRA_DATA) as? Intent
                }
                if (resultCode != Activity.RESULT_OK || data == null) {
                    fail(LiveError.PROJECTION)
                } else {
                    startPlaybackCapture(resultCode, data)
                }
            }

            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        shuttingDown = true
        running = false
        releaseCaptureResources()
        LiveSession.resetFeatures()
        LiveSession.update { it.copy(isRunning = false, isPreparing = false) }
        vibeEngine.release()
        super.onDestroy()
    }

    private fun resetForNewSession() {
        running = false
        shuttingDown = false
        releaseCaptureResources()
        LiveSession.resetFeatures()
    }

    private fun startMicCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            fail(LiveError.PERMISSION)
            return
        }

        val audioManager = getSystemService(android.media.AudioManager::class.java)
        val supportsUnprocessed = runCatching {
            audioManager?.getProperty(
                android.media.AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED
            ) == "true"
        }.getOrDefault(false)

        val sources = LinkedHashSet<Int>().apply {
            add(
                if (supportsUnprocessed) MediaRecorder.AudioSource.UNPROCESSED
                else MediaRecorder.AudioSource.MIC
            )
            add(MediaRecorder.AudioSource.MIC)
        }

        for (source in sources) {
            val rate = 44_100
            val minBuffer = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) continue

            val record = runCatching {
                AudioRecord.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setAudioSource(source)
                    .setBufferSizeInBytes(max(minBuffer * 2, 8_192))
                    .build()
            }.getOrNull()

            if (record != null && record.state == AudioRecord.STATE_INITIALIZED) {
                startRecord(record, rate, CaptureMode.MIC)
                return
            }
            record?.release()
        }
        fail(LiveError.AUDIO_INIT)
    }

    private fun startPlaybackCapture(resultCode: Int, data: Intent) {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val projection = runCatching {
            manager?.getMediaProjection(resultCode, data)
        }.getOrNull()
        if (projection == null) {
            fail(LiveError.PROJECTION)
            return
        }
        mediaProjection = projection

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                mainHandler.post {
                    if (running && !shuttingDown) fail(LiveError.PROJECTION_STOPPED)
                }
            }
        }, mainHandler)

        var record: AudioRecord? = null
        var chosenRate = 44_100
        for (rate in intArrayOf(44_100, 48_000)) {
            val minBuffer = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) continue

            val config = runCatching {
                AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()
            }.getOrNull() ?: continue

            val candidate = runCatching {
                AudioRecord.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(max(minBuffer * 2, 8_192))
                    .setAudioPlaybackCaptureConfig(config)
                    .build()
            }.getOrNull()

            if (candidate != null && candidate.state == AudioRecord.STATE_INITIALIZED) {
                record = candidate
                chosenRate = rate
                break
            }
            candidate?.release()
        }

        if (record == null) {
            fail(LiveError.AUDIO_INIT)
            return
        }
        startRecord(record, chosenRate, CaptureMode.PLAYBACK)
    }

    private fun startRecord(record: AudioRecord, sampleRate: Int, captureMode: CaptureMode) {
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            fail(LiveError.AUDIO_INIT)
            return
        }

        val localAnalyzer = AudioAnalyzer(
            settingsProvider = { settings.state.value },
            onFeatures = { vibeEngine.process(it) },
            onDisplay = { LiveSession.setFeatures(it) }
        )
        analyzer = localAnalyzer
        audioRecord = record

        try {
            record.startRecording()
        } catch (t: Throwable) {
            record.release()
            audioRecord = null
            analyzer = null
            fail(LiveError.AUDIO_INIT)
            return
        }

        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            record.release()
            audioRecord = null
            analyzer = null
            fail(LiveError.AUDIO_INIT)
            return
        }

        running = true
        acquireWakeLock()
        LiveSession.resetFeatures()
        LiveSession.setRunning(
            LiveState(
                isRunning = true,
                isPreparing = false,
                mode = captureMode,
                startedAtElapsedMs = SystemClock.elapsedRealtime()
            )
        )

        captureThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val buffer = ShortArray(2_048)
            try {
                while (running) {
                    val read = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    when {
                        read > 0 -> localAnalyzer.process(buffer, 1, sampleRate, read)
                        read < 0 -> error("AudioRecord.read returned $read")
                    }
                }
            } catch (t: Throwable) {
                if (running && !shuttingDown) {
                    mainHandler.post { fail(LiveError.CAPTURE) }
                }
            }
        }, "JustVibrait-Capture").also { it.start() }
    }

    private fun stopSession() {
        shuttingDown = true
        running = false
        releaseCaptureResources()
        LiveSession.resetFeatures()
        LiveSession.setRunning(LiveState(isRunning = false, mode = mode))
        stopForegroundSafely()
        stopSelf()
    }

    private fun fail(error: LiveError) {
        if (shuttingDown) return
        shuttingDown = true
        running = false
        releaseCaptureResources()
        LiveSession.resetFeatures()
        LiveSession.update {
            it.copy(isRunning = false, isPreparing = false, error = error)
        }
        stopForegroundSafely()
        stopSelf()
    }

    private fun releaseCaptureResources() {
        val record = audioRecord
        audioRecord = null
        if (record != null) {
            runCatching {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            }
            runCatching { record.release() }
        }
        captureThread = null
        analyzer = null

        val projection = mediaProjection
        mediaProjection = null
        if (projection != null) runCatching { projection.stop() }

        vibeEngine.stop()
        releaseWakeLock()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "JustVibrait::LiveCapture"
        ).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    private fun startForegroundSafely(captureMode: CaptureMode): Boolean {
        val notification = buildNotification(captureMode)
        val type = when (captureMode) {
            CaptureMode.PLAYBACK -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            CaptureMode.MIC -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            true
        } catch (t: Throwable) {
            // Always satisfy the startForeground() contract, then surface the failure.
            runCatching {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
            }
            false
        }
    }

    private fun stopForegroundSafely() {
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(captureMode: CaptureMode): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, LiveVibeService::class.java).setAction(LiveSession.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = when (captureMode) {
            CaptureMode.PLAYBACK -> getString(R.string.notification_playback)
            CaptureMode.MIC -> getString(R.string.notification_mic)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vibe)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(R.drawable.ic_stop, getString(R.string.notification_stop), stopIntent)
            .build()
    }
}
