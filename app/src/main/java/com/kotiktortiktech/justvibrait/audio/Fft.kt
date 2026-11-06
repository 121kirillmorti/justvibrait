package com.kotiktortiktech.justvibrait.audio

import kotlin.math.cos
import kotlin.math.sin

/**
 * Small in-place iterative radix-2 Cooley-Tukey FFT. No external dependency and
 * cheap enough to run on the audio analysis thread in real time.
 */
internal object Fft {

    fun transform(real: FloatArray, imag: FloatArray) {
        val n = real.size
        require(n == imag.size && n > 1 && n and (n - 1) == 0) { "FFT size must be a power of two" }

        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = real[i]
                real[i] = real[j]
                real[j] = tr
                val ti = imag[i]
                imag[i] = imag[j]
                imag[j] = ti
            }
        }

        var length = 2
        while (length <= n) {
            val angle = -2.0 * Math.PI / length
            val wLengthCos = cos(angle).toFloat()
            val wLengthSin = sin(angle).toFloat()
            val half = length shr 1

            var start = 0
            while (start < n) {
                var wCos = 1f
                var wSin = 0f
                for (k in 0 until half) {
                    val evenIndex = start + k
                    val oddIndex = evenIndex + half
                    val oddReal = real[oddIndex] * wCos - imag[oddIndex] * wSin
                    val oddImag = real[oddIndex] * wSin + imag[oddIndex] * wCos
                    val evenReal = real[evenIndex]
                    val evenImag = imag[evenIndex]

                    real[evenIndex] = evenReal + oddReal
                    imag[evenIndex] = evenImag + oddImag
                    real[oddIndex] = evenReal - oddReal
                    imag[oddIndex] = evenImag - oddImag

                    val nextCos = wCos * wLengthCos - wSin * wLengthSin
                    wSin = wCos * wLengthSin + wSin * wLengthCos
                    wCos = nextCos
                }
                start += length
            }
            length = length shl 1
        }
    }
}
