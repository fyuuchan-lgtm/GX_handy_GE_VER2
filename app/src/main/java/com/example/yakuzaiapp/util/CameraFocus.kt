package com.example.yakuzaiapp.util

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import java.util.concurrent.TimeUnit

internal fun focusCameraOnPreviewCenter(
    camera: Camera?,
    previewView: PreviewView,
    tag: String
) {
    val activeCamera = camera ?: return
    previewView.post {
        if (previewView.width <= 0 || previewView.height <= 0) {
            return@post
        }

        runCatching {
            val point = previewView.meteringPointFactory.createPoint(
                previewView.width / 2f,
                previewView.height / 2f
            )
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            )
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()
            activeCamera.cameraControl.startFocusAndMetering(action)
        }.onFailure { e ->
            Log.w(tag, "Failed to focus camera on preview center", e)
        }
    }
}
