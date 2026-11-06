package com.kotiktortiktech.justvibrait.audio

/**
 * A single analysis frame. Values are normalized to 0..1 unless stated otherwise.
 */
data class AudioFeatures(
    val level: Float = 0f,
    val bass: Float = 0f,
    val flux: Float = 0f,
    val beat: Boolean = false,
    val bpm: Int = 0,
    val spectrum: FloatArray = FloatArray(SPECTRUM_BARS),
    val timestampMs: Long = 0L
) {
    companion object {
        const val SPECTRUM_BARS = 28
        val SILENCE = AudioFeatures()
    }
}
