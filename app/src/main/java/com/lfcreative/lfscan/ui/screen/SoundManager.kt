package com.lfcreative.lfscan.ui.screen

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.sin

object SoundManager {

    private val executor = Executors.newSingleThreadExecutor()

    /** Single beep — successful scan */
    fun playGoodScan() {
        executor.submit { playBeep(880, 120) }
    }

    /** Two descending beeps — duplicate scan */
    fun playDuplicateScan() {
        executor.submit {
            playBeep(660, 80)
            Thread.sleep(60)
            playBeep(440, 80)
        }
    }

    /** Eight rapid alternating beeps — unknown/not-found scan */
    fun playUnknownScan() {
        executor.submit {
            repeat(8) { i ->
                playBeep(if (i % 2 == 0) 880 else 300, 60)
                if (i < 7) Thread.sleep(40)
            }
        }
    }

    private fun playBeep(frequencyHz: Int, durationMs: Int, volume: Float = 0.8f) {
        val sampleRate = 44_100
        val numSamples = durationMs * sampleRate / 1000
        val fadeSamples = 5 * sampleRate / 1000   // 5 ms fade in/out

        val samples = ShortArray(numSamples) { i ->
            val sine = sin(2.0 * Math.PI * i * frequencyHz / sampleRate)
            val envelope = when {
                i < fadeSamples              -> i.toDouble() / fadeSamples
                i > numSamples - fadeSamples -> (numSamples - i).toDouble() / fadeSamples
                else                         -> 1.0
            }
            (sine * envelope * volume * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }

        val bufferBytes = numSamples * 2   // 2 bytes per PCM_16BIT sample

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            track.write(samples, 0, numSamples)
            track.play()
            Thread.sleep((durationMs + 20).toLong())
        } finally {
            track.stop()
            track.release()
        }
    }
}
