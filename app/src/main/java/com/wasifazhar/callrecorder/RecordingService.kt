package com.wasifazhar.callrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.media.AudioManager
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.sqrt

class RecordingService : Service() {
    private var recorder: MediaRecorder? = null
    private var pcmRecorder: AudioRecord? = null
    private var output: File? = null
    private var paused = false
    private var stopping = false
    private var recordingThread: Thread? = null
    private var pcmBytes = 0L
    private var pcmSampleRate = 44100
    private var pcmChannels = 1
    private var lastAmplitude = 0

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate(); createNotificationChannel() }

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
        if (recorder != null || pcmRecorder != null) return
        val bitrate = intent.getIntExtra("bitrate", 128_000).coerceIn(32_000, 256_000)
        val stereo = intent.getBooleanExtra("stereo", false)
        val format = intent.getStringExtra("format") ?: "M4A"
        val inputId = intent.getIntExtra("inputDeviceId", -1)
        startForegroundCompat(buildNotification("Preparing microphone", false))
        try {
            val directory = File(getExternalFilesDir(null), "Recordings").apply { mkdirs() }
            val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
            output = File(directory, "Recording_$stamp.${if (format == "WAV") "wav" else "m4a"}")
            if (format == "WAV") startWav(stereo, inputId) else startM4a(bitrate, stereo, inputId)
            broadcastState(true, false, null)
            updateNotification("Recording • tap to return to app", true)
            handler.post(levelTick)
        } catch (e: Exception) {
            try { recorder?.release() } catch (_: Exception) {}
            try { pcmRecorder?.release() } catch (_: Exception) {}
            recorder = null; pcmRecorder = null
            output?.delete(); output = null
            broadcastState(false, false, null)
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            android.util.Log.e("VoiceRecorder", "Could not start microphone recording", e)
        }
    }

    private fun startM4a(bitrate: Int, stereo: Boolean, inputId: Int) {
        val media = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
        media.setAudioSource(MediaRecorder.AudioSource.MIC)
        media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        media.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        media.setAudioSamplingRate(44_100)
        media.setAudioEncodingBitRate(bitrate)
        media.setAudioChannels(if (stereo) 2 else 1)
        media.setOutputFile(output!!.absolutePath)
        if (Build.VERSION.SDK_INT >= 28) {
            val device = (getSystemService(AUDIO_SERVICE) as AudioManager).getDevices(AudioManager.GET_DEVICES_INPUTS)
                .firstOrNull { it.id == inputId }
            if (device != null) media.setPreferredDevice(device)
        }
        media.prepare(); media.start(); recorder = media
    }

    private fun startWav(stereo: Boolean, inputId: Int) {
        pcmSampleRate = 44100
        pcmChannels = if (stereo) 2 else 1
        val channelConfig = if (stereo) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        val min = AudioRecord.getMinBufferSize(pcmSampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT)
        check(min > 0) { "This device does not support the selected WAV input configuration" }
        val bufferSize = maxOf(min * 2, 8192)
        val audio = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(pcmSampleRate).setChannelMask(channelConfig)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(bufferSize).build()
        val device = (getSystemService(AUDIO_SERVICE) as AudioManager).getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.id == inputId }
        if (device != null) audio.setPreferredDevice(device)
        audio.startRecording()
        check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone did not start" }
        pcmRecorder = audio
        FileOutputStream(output!!).use { it.write(ByteArray(44)) }
        pcmBytes = 0L; stopping = false; paused = false
        recordingThread = thread(name = "WavRecorder") {
            val buffer = ShortArray(bufferSize / 2)
            FileOutputStream(output!!, true).use { stream ->
                while (!stopping) {
                    if (paused) { Thread.sleep(80); continue }
                    val count = audio.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (count > 0) {
                        val bytes = ByteArray(count * 2)
                        var sum = 0.0
                        for (i in 0 until count) {
                            val sample = buffer[i].toInt()
                            bytes[i * 2] = (sample and 0xff).toByte()
                            bytes[i * 2 + 1] = ((sample shr 8) and 0xff).toByte()
                            sum += sample.toDouble() * sample
                        }
                        stream.write(bytes); pcmBytes += bytes.size
                        lastAmplitude = sqrt(sum / count).toInt().coerceIn(0, 32767)
                        sendBroadcast(Intent(EVENT_LEVEL).setPackage(packageName).putExtra("amplitude", lastAmplitude))
                    }
                }
                stream.flush()
            }
        }
    }

    private fun writeWavHeader(file: File) {
        val byteRate = pcmSampleRate * pcmChannels * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()); header.putInt((36L + pcmBytes).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        header.put("WAVE".toByteArray()); header.put("fmt ".toByteArray()); header.putInt(16)
        header.putShort(1); header.putShort(pcmChannels.toShort()); header.putInt(pcmSampleRate)
        header.putInt(byteRate); header.putShort((pcmChannels * 2).toShort()); header.putShort(16)
        header.put("data".toByteArray()); header.putInt(pcmBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        RandomAccessFile(file, "rw").use { it.seek(0); it.write(header.array()) }
    }

    private fun pauseCapture() {
        if ((recorder == null && pcmRecorder == null) || paused) return
        try {
            if (Build.VERSION.SDK_INT >= 24) recorder?.pause()
            paused = true
            broadcastState(true, true, null); updateNotification("Recording paused", true)
        } catch (e: Exception) { android.util.Log.e("VoiceRecorder", "Pause failed", e) }
    }

    private fun resumeCapture() {
        if ((recorder == null && pcmRecorder == null) || !paused) return
        try {
            if (Build.VERSION.SDK_INT >= 24) recorder?.resume()
            paused = false
            broadcastState(true, false, null); updateNotification("Recording • tap to return to app", true)
        } catch (e: Exception) { android.util.Log.e("VoiceRecorder", "Resume failed", e) }
    }

    private fun stopCapture() {
        if (recorder == null && pcmRecorder == null) {
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return
        }
        handler.removeCallbacks(levelTick)
        stopping = true
        try { recordingThread?.join(2500) } catch (_: InterruptedException) {}
        recordingThread = null
        var savedPath: String? = null
        try {
            recorder?.let { active ->
                if (paused && Build.VERSION.SDK_INT >= 24) try { active.resume() } catch (_: Exception) {}
                active.stop(); active.reset(); active.release()
            }
            recorder = null
            pcmRecorder?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
            pcmRecorder = null
            val file = output
            if (file != null && file.extension.equals("wav", true) && pcmBytes > 0) writeWavHeader(file)
            savedPath = file?.takeIf { it.exists() && it.length() > 44L }?.absolutePath
        } catch (e: Exception) {
            output?.delete()
            android.util.Log.e("VoiceRecorder", "Recording could not be finalized", e)
        } finally {
            output = null; paused = false; stopping = false; pcmBytes = 0L
            broadcastState(false, false, savedPath)
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
    }

    private fun broadcastState(active: Boolean, isPaused: Boolean, path: String?) {
        val event = Intent(EVENT_STATE).setPackage(packageName).putExtra("recording", active).putExtra("paused", isPaused)
        if (path != null) event.putExtra("path", path)
        sendBroadcast(event)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Voice recording", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Shows when the microphone recorder is active"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String, active: Boolean): Notification {
        val openApp = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Voice Recorder")
            .setContentText(message).setContentIntent(openApp).setOngoing(active).setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (active) {
            val action = if (paused) ACTION_RESUME else ACTION_PAUSE
            val label = if (paused) "Resume" else "Pause"
            val pauseIntent = PendingIntent.getService(this, 2, Intent(this, RecordingService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val stopIntent = PendingIntent.getService(this, 3, Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
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
            val value = try { recorder?.maxAmplitude ?: lastAmplitude } catch (_: Exception) { 0 }
            sendBroadcast(Intent(EVENT_LEVEL).setPackage(packageName).putExtra("amplitude", value))
            if ((recorder != null || pcmRecorder != null) && !paused) handler.postDelayed(this, 180)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(levelTick)
        if (recorder != null || pcmRecorder != null) stopCapture()
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
