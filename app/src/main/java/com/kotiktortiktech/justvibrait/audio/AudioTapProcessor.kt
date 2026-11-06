package com.kotiktortiktech.justvibrait.audio

import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A pass-through Media3 AudioProcessor that copies decoded 16-bit PCM to the
 * analyzer without altering playback. It is inserted before silence skipping and
 * the sonic processor, so the analyzed samples are the actual audio timeline.
 */
@UnstableApi
class AudioTapProcessor(
    private val onPcm: (samples: ShortArray, channelCount: Int, sampleRate: Int) -> Unit
) : BaseAudioProcessor() {

    private var channels = 2
    private var rate = 44_100
    private var pcm16 = true

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        channels = inputAudioFormat.channelCount
        rate = inputAudioFormat.sampleRate
        pcm16 = inputAudioFormat.encoding == androidx.media3.common.C.ENCODING_PCM_16BIT
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size <= 0) {
            replaceOutputBuffer(0).flip()
            return
        }

        val bytes = ByteArray(size)
        inputBuffer.get(bytes)

        if (pcm16 && size % 2 == 0) {
            val shorts = ShortArray(size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asShortBuffer().get(shorts)
            onPcm(shorts, channels, rate)
        }

        val output = replaceOutputBuffer(size)
        output.put(bytes)
        output.flip()
    }
}
