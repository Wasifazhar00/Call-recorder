package com.wasifazhar.callrecorder

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var timer: TextView
    private lateinit var meter: TextView
    private lateinit var list: LinearLayout
    private lateinit var search: EditText
    private lateinit var quality: Spinner
    private lateinit var stereo: CheckBox
    private lateinit var recordButton: Button
    private lateinit var pauseButton: Button
    private var player: MediaPlayer? = null
    private var playingFile: File? = null
    private var recording = false
    private var paused = false
    private var startedAt = 0L
    private var elapsedBeforePause = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("voice_recorder", MODE_PRIVATE) }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                RecordingService.EVENT_STATE -> {
                    recording = intent.getBooleanExtra("recording", false)
                    paused = intent.getBooleanExtra("paused", false)
                    val path = intent.getStringExtra("path")
                    status.text = when {
                        recording && paused -> "Recording paused"
                        recording -> "Recording • saved locally when stopped"
                        path != null -> "Saved: ${File(path).name}"
                        else -> "Ready to record"
                    }
                    if (!recording && path != null) refreshLibrary()
                    updateControls()
                }
                RecordingService.EVENT_LEVEL -> {
                    val amplitude = intent.getIntExtra("amplitude", 0)
                    meter.text = if (amplitude <= 0) "Input level: —" else {
                        val bars = (amplitude / 3276).coerceIn(0, 10)
                        "Input level: " + "▮".repeat(bars) + "▯".repeat(10 - bars)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        val filter = IntentFilter().apply {
            addAction(RecordingService.EVENT_STATE)
            addAction(RecordingService.EVENT_LEVEL)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(stateReceiver, filter)
        refreshLibrary()
        handler.post(timerTick)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 16)
            setBackgroundColor(0xFFF7F7FB.toInt())
        }
        val title = TextView(this).apply {
            text = "Voice Recorder"
            textSize = 28f
            setTextColor(0xFF17233C.toInt())
            setPadding(0, 0, 0, 8)
        }
        val subtitle = TextView(this).apply {
            text = "Private, offline recordings stored on this device"
            textSize = 14f
            setTextColor(0xFF586174.toInt())
        }
        status = TextView(this).apply {
            text = "Ready to record"
            textSize = 15f
            setPadding(0, 20, 0, 4)
        }
        timer = TextView(this).apply {
            text = "00:00"
            textSize = 38f
            gravity = Gravity.CENTER
            setTextColor(0xFF17233C.toInt())
            setPadding(0, 12, 0, 4)
        }
        meter = TextView(this).apply {
            text = "Input level: —"
            gravity = Gravity.CENTER
            textSize = 13f
            setPadding(0, 4, 0, 12)
        }
        quality = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Economy • 64 kbps", "Standard • 128 kbps", "High • 192 kbps"))
            setSelection(prefs.getInt("quality", 1).coerceIn(0, 2))
        }
        stereo = CheckBox(this).apply {
            text = "Stereo (if supported by microphone)"
            isChecked = prefs.getBoolean("stereo", false)
        }
        val options = TextView(this).apply {
            text = "Recording quality"
            textSize = 13f
            setTextColor(0xFF586174.toInt())
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        recordButton = Button(this).apply {
            text = "● Record"
            setOnClickListener { requestAndStart() }
        }
        pauseButton = Button(this).apply {
            text = "Pause"
            setOnClickListener { sendService(if (paused) RecordingService.ACTION_RESUME else RecordingService.ACTION_PAUSE) }
        }
        val stop = Button(this).apply {
            text = "Stop & save"
            setOnClickListener { sendService(RecordingService.ACTION_STOP) }
        }
        controls.addView(recordButton, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(pauseButton, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(stop, LinearLayout.LayoutParams(0, -2, 1f))
        val libraryTitle = TextView(this).apply {
            text = "Your recordings"
            textSize = 20f
            setTextColor(0xFF17233C.toInt())
            setPadding(0, 20, 0, 8)
        }
        search = EditText(this).apply {
            hint = "Search recordings"
            singleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refreshLibrary()
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(title)
        root.addView(subtitle)
        root.addView(status)
        root.addView(timer)
        root.addView(meter)
        root.addView(options)
        root.addView(quality)
        root.addView(stereo)
        root.addView(controls)
        root.addView(libraryTitle)
        root.addView(search)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        updateControls()
    }

    private fun requestAndStart() {
        if (recording) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        }
        prefs.edit().putInt("quality", quality.selectedItemPosition).putBoolean("stereo", stereo.isChecked).apply()
        val bitrates = intArrayOf(64_000, 128_000, 192_000)
        val intent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
            putExtra("bitrate", bitrates[quality.selectedItemPosition.coerceIn(0, 2)])
            putExtra("stereo", stereo.isChecked)
        }
        ContextCompat.startForegroundService(this, intent)
        startedAt = System.currentTimeMillis()
        elapsedBeforePause = 0L
        status.text = "Starting microphone…"
    }

    private fun sendService(action: String) {
        startService(Intent(this, RecordingService::class.java).setAction(action))
    }

    private fun updateControls() {
        recordButton.isEnabled = !recording
        pauseButton.isEnabled = recording
        pauseButton.text = if (paused) "Resume" else "Pause"
        quality.isEnabled = !recording
        stereo.isEnabled = !recording
    }

    private val timerTick = object : Runnable {
        override fun run() {
            if (recording) {
                val elapsed = if (paused) elapsedBeforePause else elapsedBeforePause + (System.currentTimeMillis() - startedAt)
                val seconds = (elapsed / 1000).coerceAtLeast(0)
                timer.text = String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
            } else timer.text = "00:00"
            handler.postDelayed(this, 500)
        }
    }

    private fun recordingsDir(): File = File(getExternalFilesDir(null), "Recordings").apply { mkdirs() }

    private fun refreshLibrary() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        val query = if (::search.isInitialized) search.text.toString().trim().lowercase(Locale.ROOT) else ""
        val files = recordingsDir().listFiles()?.filter { it.isFile && it.extension.equals("m4a", true) }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
            .filter { it.name.lowercase(Locale.ROOT).contains(query) }
        if (files.isEmpty()) {
            list.addView(TextView(this).apply {
                text = if (query.isEmpty()) "No recordings yet. Tap Record to create your first memo." else "No matching recordings."
                setPadding(8, 24, 8, 24)
                textSize = 14f
            })
        }
        files.forEach { file ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 10, 12, 10)
                setBackgroundColor(0xFFFFFFFF.toInt())
            }
            val name = TextView(this).apply {
                text = file.nameWithoutExtension
                textSize = 16f
                setTextColor(0xFF17233C.toInt())
            }
            val meta = TextView(this).apply {
                text = "${formatBytes(file.length())}  •  ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))}"
                textSize = 12f
                setTextColor(0xFF687386.toInt())
            }
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fun action(label: String, click: () -> Unit) {
                actions.addView(Button(this@MainActivity).apply { text = label; setOnClickListener { click() } },
                    LinearLayout.LayoutParams(0, -2, 1f))
            }
            action(if (playingFile == file) "Stop" else "Play") { togglePlayback(file) }
            action("Rename") { rename(file) }
            action(if (isFavorite(file)) "★" else "☆") { toggleFavorite(file) }
            action("Share") { share(file) }
            action("Delete") { confirmDelete(file) }
            row.addView(name)
            row.addView(meta)
            row.addView(actions)
            val params = LinearLayout.LayoutParams(-1, -2)
            params.bottomMargin = 10
            list.addView(row, params)
        }
    }

    private fun togglePlayback(file: File) {
        if (playingFile == file) {
            stopPlayback()
            refreshLibrary()
            return
        }
        stopPlayback()
        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { stopPlayback(); refreshLibrary() }
                prepare()
                start()
            }
            playingFile = file
            status.text = "Playing: ${file.name}"
            refreshLibrary()
        } catch (e: Exception) {
            status.text = "Unable to play this recording: ${e.message}"
            stopPlayback()
        }
    }

    private fun stopPlayback() {
        try { player?.stop() } catch (_: Exception) {}
        player?.release()
        player = null
        playingFile = null
    }

    private fun rename(file: File) {
        val input = EditText(this).apply { setText(file.nameWithoutExtension); selectAll() }
        AlertDialog.Builder(this).setTitle("Rename recording").setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val clean = input.text.toString().trim().replace(Regex("[^a-zA-Z0-9 _-]"), "")
                if (clean.isNotBlank()) {
                    val target = File(file.parentFile, "$clean.m4a")
                    if (!target.exists() && file.renameTo(target)) refreshLibrary()
                    else status.text = "That name is empty or already in use."
                }
            }.show()
    }

    private fun confirmDelete(file: File) {
        AlertDialog.Builder(this).setTitle("Delete recording?")
            .setMessage("This permanently deletes ${file.name}.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                if (file.delete()) {
                    prefs.edit().remove("fav_${file.name}").apply()
                    refreshLibrary()
                } else status.text = "Could not delete the recording."
            }.show()
    }

    private fun share(file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "audio/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share recording"))
        } catch (e: Exception) {
            status.text = "Could not share file: ${e.message}"
        }
    }

    private fun isFavorite(file: File) = prefs.getBoolean("fav_${file.name}", false)
    private fun toggleFavorite(file: File) {
        prefs.edit().putBoolean("fav_${file.name}", !isFavorite(file)).apply()
        refreshLibrary()
    }
    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    override fun onDestroy() {
        handler.removeCallbacks(timerTick)
        stopPlayback()
        try { unregisterReceiver(stateReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
