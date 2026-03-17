package com.andreas_kratzer.ghosttalk.core.actions

import android.graphics.Bitmap

interface CameraProvider {
    /**
     * Captures a single image from the camera and returns it as a Bitmap.
     * Should return null if the capture fails or permission is denied.
     */
    suspend fun captureImage(): Bitmap?
}
