package com.wasifazhar.callrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import java.io.File

class RecordingService : Service() {
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var paused = false
    private var recordingStartedAt = 0L
    private var pausedDuration = 0L
    private var pauseStartedAt = 0L
    private var lastAmplitude = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture(intent)
            ACTION_PAUSE -> pauseCapture()
            ACTION_RESUME -> resumeCapture()
            ACTION_STOP -> stopCapture()
        }
        return START_NOT_STICKY
    }

    private fun startCapture(intent: Intent) {
        if (recorder != null) return
        val bitrate = intent.getIntExtra("bitrate", 128_000).coerceIn(32_000, 256_000)
        val stereo = intent.getBooleanExtra("stereo", false)
        startForegroundCompat(buildNotification("Preparing microphone", false))
        try {
            val directory = File(getExternalFilesDir(null), "Recordings").apply { mkdirs() }
            val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                .format(java.util.Date())
            output = File(directory, "Recording_$stamp.m4a")
            val media = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            media.setAudioSource(MediaRecorder.AudioSource.MIC)
            media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            media.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            media.setAudioSamplingRate(44_100)
            media.setAudioEncodingBitRate(bitrate)
            media.setAudioChannels(if (stereo) 2 else 1)
            media.setOutputFile(output!!.absolutePath)
            media.prepare()
            media.start()
            recorder = media
            paused = false
            recordingStartedAt = SystemClock.elapsedRealtime()
            pausedDuration = 0L
            broadcastState(true, false, null)
            updateNotification("Recording • tap to return to app", true)
            handler.post(levelTick)
        } catch (e: Exception) {
            try { recorder?.release() } catch (_: Exception) {}
            recorder = null
            try { output?.delete() } catch (_: Exception) {}
            output = null
            broadcastState(false, false, null)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            android.util.Log.e("VoiceRecorder", "Could not start microphone recording", e)
        }
    }

    private fun pauseCapture() {
        val active = recorder ?: return
        if (paused) return
        try {
            active.pause()
            paused = true
            pauseStartedAt = SystemClock.elapsedRealtime()
            broadcastState(true, true, null)
            updateNotification("Recording paused", true)
        } catch (e: Exception) {
            android.util.Log.e("VoiceRecorder", "Pause failed", e)
        }
    }

    private fun resumeCapture() {
        val active = recorder ?: return
        if (!paused) return
        try {
            active.resume()
            pausedDuration += SystemClock.elapsedRealtime() - pauseStartedAt
            paused = false
            broadcastState(true, false, null)
            updateNotification("Recording • tap to return to app", true)
        } catch (e: Exception) {
            android.util.Log.e("VoiceRecorder", "Resume failed", e)
        }
    }

    private fun stopCapture() {
        val active = recorder
        if (active == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        handler.removeCallbacks(levelTick)
        var savedPath: String? = null
        try {
            if (paused) {
                try { active.resume() } catch (_: Exception) {}
            }
            active.stop()
            savedPath = output?.takeIf { it.exists() && it.length() > 0L }?.absolutePath
        } catch (e: Exception) {
            output?.delete()
            android.util.Log.e("VoiceRecorder", "Recording could not be finalized", e)
        } finally {
            try { active.reset() } catch (_: Exception) {}
            try { active.release() } catch (_: Exception) {}
            recorder = null
            output = null
            paused = false
            broadcastState(false, false, savedPath)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun broadcastState(active: Boolean, isPaused: Boolean, path: String?) {
        val event = Intent(EVENT_STATE).setPackage(packageName)
            .putExtra("recording", active)
            .putExtra("paused", isPaused)
        if (path != null) event.putExtra("path", path)
        sendBroadcast(event)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Voice recording", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Shows when the microphone recorder is active"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String, active: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Voice Recorder")
            .setContentText(message)
            .setContentIntent(openApp)
            .setOngoing(active)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (active) {
            val action = if (paused) ACTION_RESUME else ACTION_PAUSE
            val label = if (paused) "Resume" else "Pause"
            val pauseIntent = PendingIntent.getService(
                this, 2, Intent(this, RecordingService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val stopIntent = PendingIntent.getService(
                this, 3, Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, label, pauseIntent)
            builder.addAction(android.R.drawable.ic_menu_save, "Save", stopIntent)
        }
        return builder.build()
    }

    private fun updateNotification(message: String, active: Boolean) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(message, active))
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val levelTick = object : Runnable {
        override fun run() {
            val value = try { recorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }
            lastAmplitude = value
            sendBroadcast(Intent(EVENT_LEVEL).setPackage(packageName).putExtra("amplitude", lastAmplitude))
            if (recorder != null && !paused) handler.postDelayed(this, 180)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(levelTick)
        recorder?.let {
            try { it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        recorder = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.wasifazhar.callrecorder.START"
        const val ACTION_PAUSE = "com.wasifazhar.callrecorder.PAUSE"
        const val ACTION_RESUME = "com.wasifazhar.callrecorder.RESUME"
        const val ACTION_STOP = "com.wasifazhar.callrecorder.STOP"
        const val EVENT_STATE = "com.wasifazhar.callrecorder.EVENT_STATE"
        const val EVENT_LEVEL = "com.wasifazhar.callrecorder.EVENT_LEVEL"
        private const val CHANNEL_ID = "voice_recording"
        private const val NOTIFICATION_ID = 701
    }
}
