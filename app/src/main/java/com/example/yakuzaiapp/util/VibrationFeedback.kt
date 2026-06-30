package com.example.yakuzaiapp.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.getSystemService

object VibrationFeedback {
    fun success(context: Context) {
        vibrate(context, 100)
    }

    fun error(context: Context) {
        vibrate(context, 500)
    }

    private fun vibrate(context: Context, durationMs: Long) {
        val vibrator = context.getSystemService<Vibrator>() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }
}

