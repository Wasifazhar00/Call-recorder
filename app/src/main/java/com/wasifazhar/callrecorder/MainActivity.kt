package com.wasifazhar.callrecorder

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var status: TextView
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    private val running = AtomicBoolean(false)
    private var output: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 48, 40, 32)
        }
        status = TextView(this).apply {
            textSize = 16f
            text = "Compatibility test only. This app may not receive cellular call audio on this device."
        }
        val start = Button(this).apply {
            text = "Start consented test"
            setOnClickListener { startCapture() }
        }
        val stop = Button(this).apply {
            text = "Stop test"
            setOnClickListener { stopCapture() }
        }
        layout.addView(status)
        layout.addView(start)
        layout.addView(stop)
        setContentView(layout)
    }

    private fun startCapture() {
        if (running.get()) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            status.text = "Grant microphone permission, then press Start again. Record only with consent."
            return
        }

        val sampleRate = 16000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            status.text = "This device does not support the requested PCM format ($minBuffer)."
            return
        }

        try {
            val audio = AudioRecord(
                MediaRecorder.AudioSource.VOICE_CALL,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2
            )
            if (audio.state != AudioRecord.STATE_INITIALIZED) {
                audio.release()
                status.text = "VOICE_CALL capture is unavailable or blocked by Android/vendor policy."
                return
            }
            output = File(getExternalFilesDir("recordings"), "call-test-${System.currentTimeMillis()}.pcm")
                .apply { parentFile?.mkdirs() }
            recorder = audio
            running.set(true)
            audio.startRecording()
            worker = thread(name = "CallAudioTest") {
                val buffer = ByteArray(minBuffer)
                try {
                    FileOutputStream(output!!).use { stream ->
                        while (running.get()) {
                            val count = audio.read(buffer, 0, buffer.size)
                            if (count > 0) stream.write(buffer, 0, count)
                        }
                    }
                } catch (_: Exception) {
                    // Status is updated after the worker exits.
                }
            }
            status.text = "Capture started. This does not prove both call sides are present. Stop when finished."
        } catch (e: SecurityException) {
            status.text = "Android denied VOICE_CALL capture: ${e.message ?: "permission/policy restriction"}"
        } catch (e: Exception) {
            status.text = "Could not start capture: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun stopCapture() {
        if (!running.getAndSet(false)) {
            status.text = "No test is running."
            return
        }
        try { recorder?.stop() } catch (_: Exception) { }
        try { worker?.join(1000) } catch (_: InterruptedException) { }
        recorder?.release()
        recorder = null
        worker = null
        status.text = "Test stopped. File: ${output?.absolutePath ?: "not saved"}. Playback may be silent or contain only one side."
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_AUDIO = 1001
    }
}
