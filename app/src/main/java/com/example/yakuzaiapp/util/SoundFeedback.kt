package com.example.yakuzaiapp.util

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import com.example.yakuzaiapp.R

object SoundFeedback {
    @Volatile
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun playSuccess() {
        val context = applicationContext
        if (context != null && playRawSound(context, R.raw.sound_success)) {
            return
        }

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
        val context = applicationContext
        if (context != null && playRawSound(context, R.raw.sound_error_warning2)) {
            return
        }

        playSequence(
            streamType = AudioManager.STREAM_MUSIC,
            volume = 85,
            tones = listOf(
                ToneSpec(ToneGenerator.TONE_PROP_NACK, 450, 0)
            )
        )
    }

    private fun playRawSound(context: Context, resId: Int): Boolean {
        val player = runCatching {
            MediaPlayer.create(context, resId)
        }.getOrNull() ?: return false

        return runCatching {
            player.setOnCompletionListener { completedPlayer ->
                completedPlayer.release()
            }
            player.setOnErrorListener { erroredPlayer, _, _ ->
                erroredPlayer.release()
                true
            }
            player.start()
            true
        }.getOrElse {
            player.release()
            false
        }
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
