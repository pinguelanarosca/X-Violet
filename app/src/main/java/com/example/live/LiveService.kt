package com.example.live

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.HubApplication
import com.example.MainActivity
import com.example.R
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel

class LiveService : Service() {

    companion object {
        const val CHANNEL_ID = "channel_live_service"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START_SERVICE = "com.example.live.ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.example.live.ACTION_STOP_SERVICE"
        const val ACTION_TOGGLE_MUTE = "com.example.live.ACTION_TOGGLE_MUTE"

        fun startService(context: Context) {
            val intent = Intent(context, LiveService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, LiveService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SERVICE

        when (action) {
            ACTION_STOP_SERVICE -> {
                HubLogger.log(LogLevel.INFO, LogCategory.LIVE, "Comando de encerramento do LiveService recebido.")
                stopForegroundAndService()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_MUTE -> {
                val app = application as? HubApplication
                app?.liveSessionManager?.toggleProactiveAudioMute()
                updateNotification()
                return START_NOT_STICKY
            }
        }

        // Check prerequisites before calling startForeground
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            HubLogger.log(LogLevel.ERROR, LogCategory.LIVE, "Permissão RECORD_AUDIO não concedida ao iniciar LiveService. Abortando serviço.")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            acquireWakeLock()
            updateMediaSessionState(isPlaying = true)

            HubLogger.log(LogLevel.INFO, LogCategory.LIVE, "LiveService iniciado em primeiro plano com tipo MICROPHONE.")

        } catch (e: Exception) {
            HubLogger.log(
                LogLevel.ERROR,
                LogCategory.LIVE,
                "Exceção ao chamar startForeground no LiveService: ${e.message}"
            )
            stopSelf()
            return START_NOT_STICKY
        }

        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HubMultimodal:LiveSessionWakeLock")
        }
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire(30 * 60 * 1000L) // 30 minutes timeout
                HubLogger.log(LogLevel.INFO, LogCategory.LIVE, "WakeLock parcial adquirido com timeout de 30 min.")
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                HubLogger.log(LogLevel.INFO, LogCategory.LIVE, "WakeLock parcial liberado.")
            }
        }
        wakeLock = null
    }

    private fun setupMediaSession() {
        try {
            mediaSession = MediaSession(this, "GeminiLiveSession").apply {
                setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)

                val metadata = MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "Gemini Live")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "Hub Multimodal AI")
                    .build()
                setMetadata(metadata)

                setCallback(object : MediaSession.Callback() {
                    override fun onStop() {
                        val app = application as? HubApplication
                        app?.liveSessionManager?.stopLiveSession()
                        stopForegroundAndService()
                    }

                    override fun onPause() {
                        val app = application as? HubApplication
                        app?.liveSessionManager?.toggleProactiveAudioMute()
                    }
                })

                isActive = true
            }
        } catch (e: Exception) {
            HubLogger.log(LogLevel.WARN, LogCategory.LIVE, "Erro ao configurar MediaSession: ${e.message}")
        }
    }

    private fun updateMediaSessionState(isPlaying: Boolean) {
        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_STOPPED
        val playbackState = PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_STOP or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY)
            .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
        mediaSession?.setPlaybackState(playbackState)
    }

    private fun buildNotification(): Notification {
        val app = application as? HubApplication
        val isMuted = app?.liveSessionManager?.isProactiveAudioMuted?.value == true

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LiveService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, LiveService::class.java).apply { action = ACTION_TOGGLE_MUTE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gemini Live Ativo")
            .setContentText("Conversa por voz em tempo real ativa em segundo plano...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                if (isMuted) android.R.drawable.ic_lock_silent_mode_off else android.R.drawable.ic_lock_silent_mode,
                if (isMuted) "Ativar Áudio" else "Silenciar Áudio",
                muteIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Encerrar Live",
                stopIntent
            )
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sessão Gemini Live",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação persistente para o Modo Live em segundo plano"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundAndService() {
        releaseWakeLock()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        mediaSession?.release()
        HubLogger.log(LogLevel.INFO, LogCategory.LIVE, "LiveService destruído.")
    }
}
