package com.kotiktortiktech.justvibrait.debug

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.os.Vibrator
import android.os.VibratorManager
import com.kotiktortiktech.justvibrait.audio.AudioFeatures
import com.kotiktortiktech.justvibrait.data.SettingsRepository
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class MotorCapability(
    val id: Int,
    val hasAmplitudeControl: Boolean,
    val isDefault: Boolean
)

/**
 * Debug-only UDP telemetry for the Windows motor monitor.
 *
 * Android never exposes the amplitude that a motor actually produced, so this class
 * reports two independent things:
 *  - the amplitude/duration that VibeEngine commanded, per enumerated vibrator;
 *  - an accelerometer-derived estimate of the vibration that actually reached the phone body.
 *
 * The packet is intentionally a flat, tab-separated line: it is trivial to inspect with
 * Wireshark and needs no JSON dependency on either side.
 */
class VibeTelemetry private constructor(context: Context) {

    companion object {
        const val PORT = 45_454

        @Volatile
        private var instance: VibeTelemetry? = null

        fun get(context: Context): VibeTelemetry {
            return instance ?: synchronized(this) {
                instance ?: VibeTelemetry(context.applicationContext).also { instance = it }
            }
        }

        private fun sanitize(value: String): String {
            return value
                .replace('\t', ' ')
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('|', '/')
        }

        private fun float(value: Float): String =
            String.format(Locale.US, "%.3f", value)
    }

    private val appContext = context.applicationContext
    private val settings = SettingsRepository.get(appContext)
    private val thread = HandlerThread("JustVibrait-Telemetry").apply { start() }
    private val handler = Handler(thread.looper)
    private val sensorManager = appContext.getSystemService(SensorManager::class.java)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val motors: List<MotorCapability> = enumerateMotors(appContext)
    val defaultMotorId: Int =
        motors.firstOrNull { it.isDefault }?.id ?: motors.firstOrNull()?.id ?: 0
    private val hasAccelerometer = accelerometer != null

    private var socket: DatagramSocket? = null
    private var sensorRegistered = false
    private var sequence = 0

    @Volatile
    private var source = "LOCAL"

    @Volatile
    private var profile = "DYNAMIC"

    @Volatile
    private var intensity = 0f

    @Volatile
    private var threshold = 0f

    @Volatile
    private var bass = 0f

    @Volatile
    private var level = 0f

    @Volatile
    private var bpm = 0

    @Volatile
    private var beat = false

    @Volatile
    private var activeMotorId = -1

    @Volatile
    private var pulseAmplitude = 0

    @Volatile
    private var pulseDurationMs = 0L

    @Volatile
    private var pulseAt = 0L

    @Volatile
    private var sensorLevel = 0f

    @Volatile
    private var sensorMs2 = 0f

    private var gravity = SensorManager.GRAVITY_EARTH
    private var heldActual = 0f

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = sqrt(x * x + y * y + z * z)
            gravity += (magnitude - gravity) * 0.08f
            val deviation = abs(magnitude - gravity)
            sensorMs2 = deviation
            sensorLevel = (deviation / 6f).coerceIn(0f, 1f)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val sender = object : Runnable {
        override fun run() {
            val current = settings.state.value
            try {
                if (current.telemetryEnabled) {
                    ensureSocket()
                    setSensorEnabled(true)
                    sendPacket(current.telemetryHost)
                    handler.postDelayed(this, 33L)
                } else {
                    setSensorEnabled(false)
                    closeSocket()
                    handler.postDelayed(this, 500L)
                }
            } catch (_: Throwable) {
                closeSocket()
                handler.postDelayed(this, 1_000L)
            }
        }
    }

    init {
        handler.post(sender)
    }

    fun recordFeatures(
        features: AudioFeatures,
        source: String,
        profile: String,
        intensity: Float,
        threshold: Float
    ) {
        if (!settings.state.value.telemetryEnabled) return
        this.source = source
        this.profile = profile
        this.intensity = intensity
        this.threshold = threshold
        bass = features.bass
        level = features.level
        bpm = features.bpm
        beat = features.beat
    }

    fun recordPulse(motorId: Int, amplitude: Int, durationMs: Long) {
        if (!settings.state.value.telemetryEnabled) return
        activeMotorId = motorId
        pulseAmplitude = amplitude
        pulseDurationMs = durationMs
        pulseAt = SystemClock.elapsedRealtime()
    }

    private fun ensureSocket() {
        if (socket == null) {
            socket = DatagramSocket().apply { broadcast = true }
        }
    }

    private fun closeSocket() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun setSensorEnabled(enabled: Boolean) {
        if (enabled == sensorRegistered) return
        if (!hasAccelerometer) return
        if (enabled) {
            sensorManager?.registerListener(sensorListener, accelerometer, 10_000, handler)
        } else {
            sensorManager?.unregisterListener(sensorListener)
        }
        sensorRegistered = enabled
    }

    private fun sendPacket(host: String) {
        val datagramSocket = socket ?: return
        val target = if (host.isBlank()) {
            InetAddress.getByName("255.255.255.255")
        } else {
            runCatching { InetAddress.getByName(host.trim()) }.getOrNull() ?: return
        }

        val now = SystemClock.elapsedRealtime()
        val holdMs = max(pulseDurationMs, 140L)
        val active = activeMotorId >= 0 &&
            pulseAmplitude > 0 &&
            now - pulseAt <= holdMs
        val amplitude = if (active) pulseAmplitude else 0

        heldActual = max(sensorLevel, heldActual * 0.90f)
        if (heldActual < 0.01f) heldActual = 0f

        val motorSpec = motors.joinToString(";") { motor ->
            val motorAmplitude = if (active && motor.id == activeMotorId) amplitude else 0
            val motorDuration = if (motorAmplitude > 0) pulseDurationMs else 0L
            "${motor.id}:${if (motor.hasAmplitudeControl) 1 else 0}:" +
                "$motorAmplitude:$motorDuration:${if (motor.isDefault) 1 else 0}"
        }

        val model = sanitize(
            listOf(Build.MANUFACTURER, Build.MODEL)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "Android device" }
        )
        val payload = listOf(
            "JV1",
            (sequence++).toString(),
            now.toString(),
            model,
            sanitize(Build.VERSION.RELEASE),
            Build.VERSION.SDK_INT.toString(),
            sanitize(source),
            sanitize(profile),
            float(intensity),
            float(threshold),
            float(bass),
            float(level),
            bpm.toString(),
            if (beat) "1" else "0",
            float(heldActual),
            float(sensorMs2),
            if (hasAccelerometer) "1" else "0",
            defaultMotorId.toString(),
            motorSpec
        ).joinToString("\t")

        val bytes = payload.toByteArray(Charsets.UTF_8)
        datagramSocket.send(DatagramPacket(bytes, bytes.size, target, PORT))
    }

    private fun enumerateMotors(context: Context): List<MotorCapability> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(VibratorManager::class.java)
                    ?: return emptyList()
                val defaultId = manager.defaultVibrator?.id ?: 0
                manager.vibratorIds.toList().mapNotNull { id ->
                    val vibrator = runCatching { manager.getVibrator(id) }.getOrNull()
                    if (vibrator == null || !vibrator.hasVibrator()) {
                        null
                    } else {
                        MotorCapability(
                            id = id,
                            hasAmplitudeControl = vibrator.hasAmplitudeControl(),
                            isDefault = id == defaultId
                        )
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (vibrator?.hasVibrator() == true) {
                    listOf(
                        MotorCapability(
                            id = 0,
                            hasAmplitudeControl = vibrator.hasAmplitudeControl(),
                            isDefault = true
                        )
                    )
                } else {
                    emptyList()
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
