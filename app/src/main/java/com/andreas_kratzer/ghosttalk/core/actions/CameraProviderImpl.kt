package com.andreas_kratzer.ghosttalk.core.actions

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraProviderImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : CameraProvider {

    private val executor: Executor = ContextCompat.getMainExecutor(context)

    override suspend fun captureImage(): Bitmap? = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<Bitmap?>()

        // We must run CameraX setup on the main thread
        Handler(Looper.getMainLooper()).post {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    
                    // Focus on the back camera by default
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    // Bind to the process lifecycle
                    val lifecycleOwner = ProcessLifecycleOwner.get()
                    
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        imageCapture
                    )

                    imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val rotationDegrees = image.imageInfo.rotationDegrees
                            val bitmap = image.toBitmap().let {
                                if (rotationDegrees != 0) {
                                    it.rotate(rotationDegrees)
                                } else {
                                    it
                                }
                            }
                            image.close()
                            deferred.complete(bitmap)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            deferred.complete(null)
                        }
                    })
                } catch (e: Exception) {
                    deferred.complete(null)
                }
            }, executor)
        }

        deferred.await()
    }

    private fun Bitmap.rotate(degrees: Int): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
}
