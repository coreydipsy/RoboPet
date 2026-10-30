package com.example.robopet

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

enum class SoundEffectType {
    HAPPY_CHIRP,
    CURIOUS_SQUEAK,
    WAVE_GREETING,
    CONFUSED_BURP,
    PLAYFUL_TRILL,
    SLEEP_SIGH,
    WAKE_UP
}

class SoundEffects {
    private val scope = CoroutineScope(Dispatchers.Default)
    var isQuietMode: Boolean = false

    fun play(type: SoundEffectType) {
        if (isQuietMode) return

        scope.launch {
            try {
                when (type) {
                    SoundEffectType.HAPPY_CHIRP -> playFrequencySweep(600f, 1100f, 140)
                    SoundEffectType.CURIOUS_SQUEAK -> playFrequencySweep(450f, 850f, 180)
                    SoundEffectType.WAVE_GREETING -> {
                        playFrequencySweep(520f, 880f, 120)
                        playFrequencySweep(880f, 1200f, 140)
                    }
                    SoundEffectType.CONFUSED_BURP -> playFrequencySweep(600f, 380f, 180)
                    SoundEffectType.PLAYFUL_TRILL -> {
                        playFrequencySweep(700f, 1050f, 90)
                        playFrequencySweep(600f, 1250f, 120)
                    }
                    SoundEffectType.SLEEP_SIGH -> playFrequencySweep(550f, 280f, 350)
                    SoundEffectType.WAKE_UP -> playFrequencySweep(350f, 750f, 220)
                }
            } catch (_: Exception) {
                // Procedural audio failure is non-fatal
            }
        }
    }

    private fun playFrequencySweep(startFreq: Float, endFreq: Float, durationMs: Int) {
        val sampleRate = 22050
        val numSamples = (sampleRate * (durationMs / 1000f)).toInt()
        val buffer = ShortArray(numSamples)

        var currentPhase = 0.0
        val fadeSamples = (numSamples * 0.15f).toInt().coerceAtLeast(1)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val freq = startFreq + (endFreq - startFreq) * progress
            val phaseInc = 2.0 * PI * freq / sampleRate
            currentPhase += phaseInc

            // Apply envelope to prevent pops
            val envelope = when {
                i < fadeSamples -> i.toFloat() / fadeSamples
                i > numSamples - fadeSamples -> (numSamples - i).toFloat() / fadeSamples
                else -> 1f
            }

            val sample = (sin(currentPhase) * 16000 * envelope).toInt().toShort()
            buffer[i] = sample
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
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
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(buffer, 0, buffer.size)
        track.play()

        // Clean up track after completion
        Thread.sleep(durationMs.toLong() + 30)
        track.stop()
        track.release()
    }
}
