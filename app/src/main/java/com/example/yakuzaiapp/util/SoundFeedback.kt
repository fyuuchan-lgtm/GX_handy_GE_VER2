package com.example.yakuzaiapp.util

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import com.example.yakuzaiapp.R

object SoundFeedback {
    private const val SUCCESS_MAX_DURATION_MS = 1200
    private const val SUCCESS_FADE_OUT_MS = 200
    private const val FADE_STEPS = 4

    @Volatile
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun playSuccess() {
        val context = applicationContext
        if (
            context != null &&
            playRawSound(
                context = context,
                resId = R.raw.sound_success,
                maxDurationMs = SUCCESS_MAX_DURATION_MS,
                fadeOutMs = SUCCESS_FADE_OUT_MS,
            )
        ) {
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

    private fun playRawSound(
        context: Context,
        resId: Int,
        maxDurationMs: Int? = null,
        fadeOutMs: Int = 0,
    ): Boolean {
        val player = runCatching {
            MediaPlayer.create(context, resId)
        }.getOrNull() ?: return false

        return runCatching {
            val handler = Handler(Looper.getMainLooper())
            val scheduledCallbacks = mutableListOf<Runnable>()
            var isReleased = false

            fun releasePlayer(stopFirst: Boolean) {
                if (isReleased) return
                isReleased = true
                scheduledCallbacks.forEach(handler::removeCallbacks)
                if (stopFirst) {
                    runCatching { player.stop() }
                }
                player.release()
            }

            player.setOnCompletionListener {
                releasePlayer(stopFirst = false)
            }
            player.setOnErrorListener { _, _, _ ->
                releasePlayer(stopFirst = false)
                true
            }
            player.start()
            if (maxDurationMs != null) {
                val fadeDuration = fadeOutMs.coerceAtLeast(0).coerceAtMost(maxDurationMs)
                val fadeStartMs = maxDurationMs - fadeDuration
                if (fadeDuration > 0) {
                    repeat(FADE_STEPS) { index ->
                        val step = index + 1
                        val delayMs = fadeStartMs + (fadeDuration * step / FADE_STEPS)
                        val callback = Runnable {
                            if (!isReleased) {
                                val volume = 1f - (step.toFloat() / FADE_STEPS)
                                player.setVolume(volume, volume)
                            }
                        }
                        scheduledCallbacks += callback
                        handler.postDelayed(callback, delayMs.toLong())
                    }
                }
                val stopCallback = Runnable {
                    releasePlayer(stopFirst = true)
                }
                scheduledCallbacks += stopCallback
                handler.postDelayed(stopCallback, maxDurationMs.toLong())
            }
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
