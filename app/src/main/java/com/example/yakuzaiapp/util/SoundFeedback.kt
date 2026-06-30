package com.example.yakuzaiapp.util

import android.media.ToneGenerator
import android.media.AudioManager

object SoundFeedback {
    fun playSuccess() {
        playSequence(
            streamType = AudioManager.STREAM_ALARM,
            volume = 100,
            tones = listOf(
                ToneSpec(ToneGenerator.TONE_PROP_ACK, 180, 110),
                ToneSpec(ToneGenerator.TONE_PROP_ACK, 260, 0)
            )
        )
    }

    fun playError() {
        playSequence(
            streamType = AudioManager.STREAM_MUSIC,
            volume = 85,
            tones = listOf(
                ToneSpec(ToneGenerator.TONE_PROP_NACK, 450, 0)
            )
        )
    }

    private fun playSequence(
        streamType: Int,
        volume: Int,
        tones: List<ToneSpec>
    ) {
        Thread {
            val toneGenerator = ToneGenerator(streamType, volume)
            runCatching {
                tones.forEach { spec ->
                    toneGenerator.startTone(spec.toneType, spec.durationMs)
                    Thread.sleep((spec.durationMs + spec.pauseAfterMs).toLong())
                }
            }.also {
                toneGenerator.release()
            }
        }.start()
    }

    private data class ToneSpec(
        val toneType: Int,
        val durationMs: Int,
        val pauseAfterMs: Int
    )
}
