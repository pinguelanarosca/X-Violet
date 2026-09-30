package com.example.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class CameraVisionManager(private val context: Context) {

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    var isFrontCamera = false
    var isCameraActive = false
        private set

    var isOnDemandMode = false
    var configuredFps: Float = 1.0f // 1 FPS default
    var jpegQuality: Int = 70 // 70% default

    private var imageAnalysis: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private var lastFrameTimeMs = 0L
    private var lastFrameHash = 0

    var onFrameCaptured: ((ByteArray) -> Unit)? = null

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider? = null,
        onError: (String) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                provider.unbindAll()

                val cameraSelector = if (isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                val preview = surfaceProvider?.let { sp ->
                    Preview.Builder().build().also { p ->
                        p.setSurfaceProvider(sp)
                    }
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageProxy(imageProxy)
                }

                imageAnalysis = analysis

                if (preview != null) {
                    provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, analysis)
                } else {
                    provider.bindToLifecycle(lifecycleOwner, cameraSelector, analysis)
                }

                isCameraActive = true
                HubLogger.log(LogLevel.INFO, LogCategory.VISION, "Câmera CameraX iniciada (${if (isFrontCamera) "Frontal" else "Traseira"}).")

            } catch (e: Exception) {
                isCameraActive = false
                HubLogger.log(LogLevel.ERROR, LogCategory.VISION, "Erro ao iniciar CameraX: ${e.message}")
                onError("Não foi possível acessar a câmera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun toggleCamera(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider?) {
        isFrontCamera = !isFrontCamera
        if (isCameraActive) {
            startCamera(lifecycleOwner, surfaceProvider, onError = {})
        }
    }

    fun captureSingleFrameOnDemand() {
        lastFrameTimeMs = 0L
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        val intervalMs = (1000L / configuredFps).toLong()

        try {
            if (isOnDemandMode && lastFrameTimeMs != 0L) {
                return
            }

            if (!isOnDemandMode && (now - lastFrameTimeMs < intervalMs)) {
                return
            }

            lastFrameTimeMs = now

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val bitmap = imageProxy.toBitmap()

            // Apply rotation before JPEG encoding so the model receives upright image
            val matrix = Matrix().apply {
                postRotate(rotationDegrees.toFloat())
                if (isFrontCamera) {
                    postScale(-1f, 1f) // Mirror front camera
                }
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )

            // Downscale to max 640px
            val maxDim = 640
            val scale = (maxDim.toFloat() / rotatedBitmap.width.coerceAtLeast(rotatedBitmap.height)).coerceAtMost(1.0f)
            val scaledBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(
                    rotatedBitmap,
                    (rotatedBitmap.width * scale).toInt(),
                    (rotatedBitmap.height * scale).toInt(),
                    true
                )
            } else {
                rotatedBitmap
            }

            // Simple hash check to skip nearly identical frames
            val currentHash = scaledBitmap.hashCode()
            if (currentHash == lastFrameHash) {
                if (scaledBitmap != rotatedBitmap) scaledBitmap.recycle()
                if (rotatedBitmap != bitmap) rotatedBitmap.recycle()
                bitmap.recycle()
                return
            }
            lastFrameHash = currentHash

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, outputStream)
            val jpegBytes = outputStream.toByteArray()

            if (scaledBitmap != rotatedBitmap) scaledBitmap.recycle()
            if (rotatedBitmap != bitmap) rotatedBitmap.recycle()
            bitmap.recycle()

            onFrameCaptured?.invoke(jpegBytes)

        } catch (e: Exception) {
            HubLogger.log(LogLevel.WARN, LogCategory.VISION, "Erro ao processar quadro da câmera: ${e.message}")
        } finally {
            imageProxy.close() // ALWAYS close ImageProxy!
        }
    }

    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            isCameraActive = false
            HubLogger.log(LogLevel.INFO, LogCategory.VISION, "Câmera CameraX desativada.")
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun shutdown() {
        stopCamera()
        cameraExecutor.shutdown()
    }
}
