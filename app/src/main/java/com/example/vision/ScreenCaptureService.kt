package com.example.vision

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.HubApplication
import com.example.MainActivity
import com.example.R
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import java.io.ByteArrayOutputStream

class ScreenCaptureService : Service() {

    companion object {
        const val CHANNEL_ID = "channel_screen_share"
        const val NOTIFICATION_ID = 3001

        const val ACTION_START_CAPTURE = "com.example.vision.ACTION_START_CAPTURE"
        const val ACTION_STOP_CAPTURE = "com.example.vision.ACTION_STOP_CAPTURE"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        fun startCapture(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START_CAPTURE
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopCapture(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP_CAPTURE
            }
            context.startService(intent)
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var lastFrameTime = 0L
    private val frameIntervalMs = 1000L // 1 FPS default

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        backgroundThread = HandlerThread("ScreenCaptureThread").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_STOP_CAPTURE

        if (action == ACTION_STOP_CAPTURE) {
            stopCaptureAndService()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            HubLogger.log(LogLevel.ERROR, LogCategory.VISION, "ResultCode inválido ou dados ausentes para compartilhamento de tela.")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            setupMediaProjection(resultCode, resultData)

        } catch (e: Exception) {
            HubLogger.log(LogLevel.ERROR, LogCategory.VISION, "Exceção ao iniciar ScreenCaptureService: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        return START_NOT_STICKY
    }

    @SuppressLint("WrongConstant")
    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, data)

        if (projection == null) {
            HubLogger.log(LogLevel.ERROR, LogCategory.VISION, "MediaProjection retornou nulo.")
            stopSelf()
            return
        }

        mediaProjection = projection

        // Register callback BEFORE creating VirtualDisplay (Android 14+ strict requirement)
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                HubLogger.log(LogLevel.INFO, LogCategory.VISION, "MediaProjection interrompida pelo sistema ou usuário.")
                stopCaptureAndService()
            }
        }, backgroundHandler)

        val metrics = resources.displayMetrics
        val width = (metrics.widthPixels / 2).coerceAtLeast(360)
        val height = (metrics.heightPixels / 2).coerceAtLeast(640)
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val now = System.currentTimeMillis()
            var image: Image? = null
            try {
                image = reader.acquireLatestImage()
                if (image != null && (now - lastFrameTime >= frameIntervalMs)) {
                    lastFrameTime = now
                    processScreenImage(image, width, height)
                }
            } catch (e: Exception) {
                // Ignore frame errors
            } finally {
                image?.close() // ALWAYS close Image!
            }
        }, backgroundHandler)

        virtualDisplay = projection.createVirtualDisplay(
            "HubScreenShare",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, backgroundHandler
        )

        val app = application as? HubApplication
        app?.liveSessionManager?.setScreenShareActive(true)

        HubLogger.log(LogLevel.INFO, LogCategory.VISION, "Compartilhamento de tela ativado ($width x $height @ 1 FPS).")
    }

    private fun processScreenImage(image: Image, width: Int, height: Int) {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        val croppedBitmap = if (rowPadding == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, width, height)

        val outputStream = ByteArrayOutputStream()
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val jpegBytes = outputStream.toByteArray()

        if (bitmap != croppedBitmap) bitmap.recycle()
        croppedBitmap.recycle()

        val app = application as? HubApplication
        app?.liveSessionManager?.sendVideoFrameToWebSocket(jpegBytes)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenCaptureService::class.java).apply { action = ACTION_STOP_CAPTURE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Compartilhamento de Tela Ativo")
            .setContentText("A tela está sendo transmitida em tempo real para o modelo...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Parar Tela", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Compartilhamento de Tela",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação de transparência durante captura de tela"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopCaptureAndService() {
        val app = application as? HubApplication
        app?.liveSessionManager?.setScreenShareActive(false)

        try {
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
        } catch (e: Exception) {
            // Ignore
        } finally {
            virtualDisplay = null
            imageReader = null
            mediaProjection = null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
        HubLogger.log(LogLevel.INFO, LogCategory.VISION, "Compartilhamento de tela encerrado de forma limpa.")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCaptureAndService()
        backgroundThread?.quitSafely()
    }
}
