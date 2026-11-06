package com.kotiktortiktech.justvibrait.vibration

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.kotiktortiktech.justvibrait.audio.AudioFeatures
import com.kotiktortiktech.justvibrait.data.AppSettings
import com.kotiktortiktech.justvibrait.data.VibeProfile
import com.kotiktortiktech.justvibrait.debug.VibeTelemetry
import kotlin.math.roundToInt

/**
 * Real-time haptic renderer.
 *
 * The phone motor is driven with short one-shot pulses rather than a pre-rendered
 * waveform: this keeps the vibration glued to the analyzed audio and lets the
 * amplitude follow the bass envelope. The profiles are tuned for the linear
 * actuators used in Pixel phones, which reproduce 8–30 ms pulses with precise
 * amplitude control much better than long rotating-mass patterns.
 *
 * All vibrator calls happen on a dedicated HandlerThread, so the audio thread is
 * never blocked by IPC.
 */
class VibeEngine(
    context: Context,
    private val settingsProvider: () -> AppSettings,
    private val telemetry: VibeTelemetry? = null,
    private val sourceProvider: () -> String = { "LOCAL" }
) {
    private val appContext = context.applicationContext

    private val handlerThread = HandlerThread("JustVibrait-Haptics").apply { start() }
    private val handler = Handler(handlerThread.looper)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val hasVibrator = vibrator?.hasVibrator() == true
    private val hasAmplitudeControl = vibrator?.hasAmplitudeControl() == true

    private var lastPulseAt = 0L

    @Volatile
    private var generation = 0

    /** Called for every analysis hop (~86 Hz) with the latest bass envelope. */
    fun process(features: AudioFeatures) {
        val settings = settingsProvider()
        telemetry?.recordFeatures(
            features = features,
            source = sourceProvider(),
            profile = settings.profile.name,
            intensity = settings.intensity,
            threshold = settings.triggerThreshold
        )
        if (!hasVibrator) return
        if (settings.intensity <= 0.01f) return

        val profile = settings.profile
        val threshold = settings.triggerThreshold
        val now = SystemClock.elapsedRealtime()

        val minInterval = when (profile) {
            VibeProfile.SOFT -> 105L
            VibeProfile.SHARP -> 58L
            VibeProfile.DYNAMIC -> 34L
        }
        if (now - lastPulseAt < minInterval) return

        val bass = features.bass
        val shouldPulse = when (profile) {
            VibeProfile.SOFT -> features.beat && bass >= threshold * 1.15f
            VibeProfile.SHARP -> (features.beat || features.flux > threshold * 0.85f) &&
                bass >= threshold * 0.75f
            VibeProfile.DYNAMIC -> bass >= threshold || features.beat
        }
        if (!shouldPulse) return
        lastPulseAt = now

        val shaped = when (profile) {
            VibeProfile.SOFT -> 0.30f + bass * 0.45f
            VibeProfile.SHARP -> 0.78f + bass * 0.22f
            VibeProfile.DYNAMIC -> 0.22f + bass * 0.78f
        }
        val accent = if (features.beat) 1.12f else 1f
        val amplitude = (shaped * accent * settings.intensity * 255f)
            .roundToInt()
            .coerceIn(1, 255)
        val durationMs = when (profile) {
            VibeProfile.SOFT -> 30L + (bass * 14f).roundToInt()
            VibeProfile.SHARP -> 9L
            VibeProfile.DYNAMIC -> 16L + (bass * 12f).roundToInt()
        }

        val scheduledGeneration = generation
        val motorId = telemetry?.defaultMotorId ?: 0
        val reportedAmplitude = if (hasAmplitudeControl) amplitude else 255
        val action = Runnable {
            if (scheduledGeneration != generation) return@Runnable
            runCatching {
                val effect = if (hasAmplitudeControl) {
                    VibrationEffect.createOneShot(durationMs, amplitude)
                } else {
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vibrator?.vibrate(effect)
                telemetry?.recordPulse(motorId, reportedAmplitude, durationMs)
            }
        }

        val delay = settings.syncDelayMs.coerceIn(0, 250).toLong()
        if (delay > 0) handler.postDelayed(action, delay) else handler.post(action)
    }

    /** Small preview pulse used when the user switches profiles in Settings. */
    fun preview(profile: VibeProfile) {
        if (!hasVibrator) return
        val settings = settingsProvider()
        val amplitude = (settings.intensity * 210f).roundToInt().coerceIn(20, 255)
        val duration = when (profile) {
            VibeProfile.SOFT -> 45L
            VibeProfile.SHARP -> 12L
            VibeProfile.DYNAMIC -> 24L
        }
        val scheduledGeneration = generation
        val motorId = telemetry?.defaultMotorId ?: 0
        val reportedAmplitude = if (hasAmplitudeControl) amplitude else 255
        handler.post {
            if (scheduledGeneration != generation) return@post
            runCatching {
                val effect = if (hasAmplitudeControl) {
                    VibrationEffect.createOneShot(duration, amplitude)
                } else {
                    VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vibrator?.vibrate(effect)
                telemetry?.recordPulse(motorId, reportedAmplitude, duration)
            }
        }
    }

    fun stop() {
        generation++
        handler.removeCallbacksAndMessages(null)
        handler.post {
            runCatching { vibrator?.cancel() }
        }
    }

    fun release() {
        stop()
        handlerThread.quitSafely()
    }
}
