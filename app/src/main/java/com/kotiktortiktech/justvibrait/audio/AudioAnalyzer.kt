package com.kotiktortiktech.justvibrait.audio

import com.kotiktortiktech.justvibrait.data.AppSettings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import android.os.SystemClock

/**
 * Low-latency bass / transient analyzer.
 *
 * PCM frames are windowed with a Hann window, transformed with a 1024-point FFT,
 * and the 28..170 Hz peak is tracked. A beat is reported when the current bass
 * peak rises above an adaptive moving average and the user threshold.
 *
 * [onFeatures] is called for every hop (about 86 Hz at 44.1 kHz) and drives the
 * haptic engine. [onDisplay] is throttled to ~33 Hz so Jetpack Compose is not
 * recomposed more often than the screen refreshes.
 */
class AudioAnalyzer(
    private val settingsProvider: () -> AppSettings,
    private val onFeatures: (AudioFeatures) -> Unit,
    private val onDisplay: (AudioFeatures) -> Unit
) {
    private companion object {
        const val FFT_SIZE = 1024
        const val HOP_SIZE = 512
        const val DISPLAY_INTERVAL_MS = 30L
        const val BASS_MIN_HZ = 28f
        const val BASS_MAX_HZ = 170f
    }

    private val window = FloatArray(FFT_SIZE) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * i / (FFT_SIZE - 1))).toFloat()
    }
    private val pending = FloatArray(FFT_SIZE * 2)
    private var pendingCount = 0

    private var sampleRate = 44_100
    private val real = FloatArray(FFT_SIZE)
    private val imag = FloatArray(FFT_SIZE)
    private val spectrum = FloatArray(AudioFeatures.SPECTRUM_BARS)
    private var bandRanges: IntArray = IntArray(AudioFeatures.SPECTRUM_BARS * 2)

    private var smoothedLevel = 0f
    private var smoothedBass = 0f
    private var lastBass = 0f
    private var averageBass = 0.06f
    private var lastBeatAt = 0L
    private var bpm = 0
    private val beatIntervals = ArrayDeque<Long>()

    private var lastDisplayAt = 0L
    private var pendingDisplayedBeat = false

    @Synchronized
    fun process(samples: ShortArray, channelCount: Int, rate: Int, sampleCount: Int = samples.size) {
        if (channelCount <= 0) return
        if (rate != sampleRate) {
            sampleRate = rate
            buildBands()
            resetInternal()
        }

        val available = sampleCount.coerceIn(0, samples.size)
        var index = 0
        while (index + channelCount <= available) {
            var mono = 0f
            for (channel in 0 until channelCount) {
                mono += samples[index + channel].toFloat()
            }
            pending[pendingCount++] = mono / channelCount
            index += channelCount

            if (pendingCount == FFT_SIZE) {
                analyzeWindow()
                System.arraycopy(pending, HOP_SIZE, pending, 0, FFT_SIZE - HOP_SIZE)
                pendingCount = FFT_SIZE - HOP_SIZE
            }
        }
    }

    @Synchronized
    fun reset() {
        resetInternal()
    }

    private fun resetInternal() {
        pendingCount = 0
        smoothedLevel = 0f
        smoothedBass = 0f
        lastBass = 0f
        averageBass = 0.06f
        lastBeatAt = 0L
        bpm = 0
        beatIntervals.clear()
        spectrum.fill(0f)
        pendingDisplayedBeat = false
        lastDisplayAt = 0L
    }

    private fun buildBands() {
        val nyquist = sampleRate / 2f
        val minHz = 40f
        val maxHz = minOf(11_000f, nyquist - 1f)
        val ratio = maxHz / minHz
        for (i in 0 until AudioFeatures.SPECTRUM_BARS) {
            val f0 = minHz * ratio.toDouble().pow(i.toDouble() / AudioFeatures.SPECTRUM_BARS)
            val f1 = minHz * ratio.toDouble().pow((i + 1).toDouble() / AudioFeatures.SPECTRUM_BARS)
            val b0 = (f0 * FFT_SIZE / sampleRate).roundToInt().coerceIn(1, FFT_SIZE / 2 - 1)
            val b1 = (f1 * FFT_SIZE / sampleRate).roundToInt().coerceIn(b0 + 1, FFT_SIZE / 2)
            bandRanges[i * 2] = b0
            bandRanges[i * 2 + 1] = b1
        }
    }

    private fun analyzeWindow() {
        var sumSquares = 0.0
        for (i in 0 until FFT_SIZE) {
            val sample = pending[i]
            sumSquares += sample * sample
            real[i] = sample * window[i]
            imag[i] = 0f
        }
        Fft.transform(real, imag)

        val rms = (sqrt(sumSquares / FFT_SIZE) / 32_768.0).toFloat()
        val level = (rms * 2.8f).coerceIn(0f, 1f)

        // Compensate the Hann window coherent gain (0.5) and normalize to full scale.
        val amplitudeScale = 4f / FFT_SIZE / 32_768f
        val bassPeak = peakAmplitude(BASS_MIN_HZ, BASS_MAX_HZ, amplitudeScale)
        val bass = (bassPeak * 4.2f).coerceIn(0f, 1f)
        val flux = ((bass - lastBass) * 2.4f).coerceIn(0f, 1f)

        val now = SystemClock.elapsedRealtime()
        val settings = settingsProvider()
        val threshold = settings.triggerThreshold
        val adaptiveRatio = 1.25f + (1f - settings.sensitivity.coerceIn(0f, 1f)) * 0.75f
        val isBeat = bass >= threshold &&
            bass > averageBass * adaptiveRatio &&
            now - lastBeatAt >= 115L

        updateBpm(isBeat, now)

        averageBass = averageBass * 0.965f + bass * 0.035f
        smoothedBass = max(bass, smoothedBass * 0.80f)
        smoothedLevel = max(level, smoothedLevel * 0.82f)
        lastBass = bass

        updateSpectrum(amplitudeScale)

        val features = AudioFeatures(
            level = smoothedLevel,
            bass = smoothedBass,
            flux = flux,
            beat = isBeat,
            bpm = bpm,
            spectrum = spectrum.copyOf(),
            timestampMs = now
        )
        onFeatures(features)

        if (now - lastDisplayAt >= DISPLAY_INTERVAL_MS) {
            lastDisplayAt = now
            onDisplay(features.copy(beat = isBeat || pendingDisplayedBeat))
            pendingDisplayedBeat = false
        } else if (isBeat) {
            pendingDisplayedBeat = true
        }
    }

    private fun updateBpm(isBeat: Boolean, now: Long) {
        if (!isBeat) {
            if (now - lastBeatAt > 2_500L && lastBeatAt > 0L) {
                // Long silence: forget the tempo so a new track starts cleanly.
                beatIntervals.clear()
                bpm = 0
            }
            return
        }
        if (lastBeatAt > 0L) {
            val delta = now - lastBeatAt
            if (delta in 240L..1_500L) {
                beatIntervals.addLast(delta)
                while (beatIntervals.size > 8) beatIntervals.removeFirst()
                val sorted = beatIntervals.sorted()
                val median = sorted[sorted.size / 2]
                val candidate = (60_000.0 / median).roundToInt().coerceIn(40, 220)
                bpm = if (bpm == 0) candidate else (bpm * 0.65f + candidate * 0.35f).roundToInt()
            } else if (delta > 1_800L) {
                beatIntervals.clear()
            }
        }
        lastBeatAt = now
    }

    private fun updateSpectrum(amplitudeScale: Float) {
        for (i in 0 until AudioFeatures.SPECTRUM_BARS) {
            val from = bandRanges[i * 2]
            val to = bandRanges[i * 2 + 1]
            var sum = 0f
            for (bin in from until to) {
                val re = real[bin]
                val im = imag[bin]
                sum += sqrt(re * re + im * im) * amplitudeScale
            }
            val average = if (to > from) sum / (to - from) else 0f
            val value = sqrt((average * 6.5f).coerceIn(0f, 1f))
            spectrum[i] = max(value, spectrum[i] * 0.78f)
        }
    }

    private fun peakAmplitude(minHz: Float, maxHz: Float, scale: Float): Float {
        val from = (minHz * FFT_SIZE / sampleRate).roundToInt().coerceIn(1, FFT_SIZE / 2 - 1)
        val to = (maxHz * FFT_SIZE / sampleRate).roundToInt().coerceIn(from, FFT_SIZE / 2 - 1)
        var peak = 0f
        for (bin in from..to) {
            val re = real[bin]
            val im = imag[bin]
            val value = sqrt(re * re + im * im) * scale
            if (value > peak) peak = value
        }
        return peak
    }
}
