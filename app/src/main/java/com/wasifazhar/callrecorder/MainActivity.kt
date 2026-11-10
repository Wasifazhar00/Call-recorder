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
import android.media.AudioManager
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.os.ParcelFileDescriptor
import android.media.AudioFormat
import android.widget.SeekBar
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
    private lateinit var format: Spinner
    private lateinit var inputDevice: Spinner
    private lateinit var waveform: WaveformView
    private lateinit var speed: Spinner
    private lateinit var seekBar: SeekBar
    private var inputIds: List<Int> = listOf(-1)
    private var seeking = false
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
                    val wasPaused = paused
                    val nowRecording = intent.getBooleanExtra("recording", false)
                    val nowPaused = intent.getBooleanExtra("paused", false)
                    if (nowRecording && nowPaused && !wasPaused) {
                        elapsedBeforePause += System.currentTimeMillis() - startedAt
                    } else if (nowRecording && !nowPaused && wasPaused) {
                        startedAt = System.currentTimeMillis()
                    }
                    recording = nowRecording
                    paused = nowPaused
                    if (!recording) elapsedBeforePause = 0L
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
                    if (::waveform.isInitialized) waveform.addLevel(amplitude)
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
        format = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("M4A • AAC", "WAV • PCM"))
            setSelection(prefs.getInt("format", 0).coerceIn(0, 1))
        }
        val devices = (getSystemService(AUDIO_SERVICE) as AudioManager).getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
        inputIds = listOf(-1) + devices.map { it.id }
        inputDevice = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Default microphone") + devices.map { it.productName.toString() })
        }
        waveform = WaveformView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 70) }
        speed = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×"))
            setSelection(2)
        }
        seekBar = SeekBar(this).apply {
            max = 1000
            isEnabled = false
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) player?.let { try { it.seekTo((it.duration * progress / 1000f).toInt()) } catch (_: Exception) {} }
                }
                override fun onStartTrackingTouch(bar: SeekBar?) { seeking = true }
                override fun onStopTrackingTouch(bar: SeekBar?) { seeking = false }
            })
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
            isSingleLine = true
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
        root.addView(TextView(this).apply { text = "Recording format" })
        root.addView(format)
        root.addView(TextView(this).apply { text = "Microphone input" })
        root.addView(inputDevice)
        root.addView(waveform)
        root.addView(controls)
        root.addView(TextView(this).apply { text = "Playback position / speed"; textSize = 12f })
        root.addView(seekBar)
        root.addView(speed)
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
        prefs.edit().putInt("format", format.selectedItemPosition).apply()
        waveform.clear()
        val intent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
            putExtra("bitrate", bitrates[quality.selectedItemPosition.coerceIn(0, 2)])
            putExtra("stereo", stereo.isChecked)
            putExtra("format", if (format.selectedItemPosition == 1) "WAV" else "M4A")
            putExtra("inputDeviceId", inputIds.getOrElse(inputDevice.selectedItemPosition) { -1 })
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
        format.isEnabled = !recording
        inputDevice.isEnabled = !recording
    }

    private val timerTick = object : Runnable {
        override fun run() {
            if (playingFile != null && player != null && !seeking) { try { seekBar.progress = (player!!.currentPosition * 1000L / player!!.duration.coerceAtLeast(1)).toInt() } catch (_: Exception) {} }
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
        val files = recordingsDir().listFiles()?.filter { it.isFile && (it.extension.equals("m4a", true) || it.extension.equals("wav", true)) }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
            .filter { file -> file.name.lowercase(Locale.ROOT).contains(query) || prefs.getString("tags_${file.name}", "").orEmpty().lowercase(Locale.ROOT).contains(query) || prefs.getString("folder_${file.name}", "").orEmpty().lowercase(Locale.ROOT).contains(query) || prefs.getString("notes_${file.name}", "").orEmpty().lowercase(Locale.ROOT).contains(query) }
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
                text = "${formatBytes(file.length())}  •  ${durationText(AudioFileTools.durationMs(file))}  •  ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))}"
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
            action("Mark") { addBookmark(file) }
            action("Marks") { showBookmarks(file) }
            action("Trim") { trimRecording(file) }
            action("Details") { editDetails(file) }
            if (file.extension.equals("wav", true)) action("Transcribe") { transcribeWav(file) }
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
                if (Build.VERSION.SDK_INT >= 23) playbackParams = playbackParams.setSpeed(speedValue())
            }
            playingFile = file
            seekBar.isEnabled = true
            seekBar.progress = 0
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
        if (::seekBar.isInitialized) { seekBar.progress = 0; seekBar.isEnabled = false }
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
                type = if (file.extension.equals("wav", true)) "audio/wav" else "audio/mp4"
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

    private fun speedValue(): Float = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).getOrElse(speed.selectedItemPosition) { 1f }
    private fun durationText(ms: Long): String {
        val sec = ms / 1000L
        return String.format(Locale.getDefault(), "%02d:%02d", sec / 60, sec % 60)
    }
    private fun addBookmark(file: File) {
        val at = if (playingFile == file) player?.currentPosition?.toLong() ?: 0L else 0L
        val key = "marks_${file.name}"
        val marks = prefs.getString(key, "").orEmpty().split(",").filter { it.isNotBlank() }.toMutableList()
        marks.add(at.toString())
        prefs.edit().putString(key, marks.joinToString(",")).apply()
        status.text = "Bookmark saved at ${durationText(at)}"
    }
    private fun showBookmarks(file: File) {
        val marks = prefs.getString("marks_${file.name}", "").orEmpty().split(",").filter { it.isNotBlank() }
        if (marks.isEmpty()) { status.text = "No bookmarks for this recording yet."; return }
        val labels = marks.mapIndexed { index, value -> "${index + 1}. ${durationText(value.toLongOrNull() ?: 0L)}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Bookmarks • ${file.nameWithoutExtension}")
            .setItems(labels) { _, which ->
                val position = marks[which].toLongOrNull()?.toInt() ?: 0
                if (playingFile != file) togglePlayback(file)
                handler.postDelayed({ try { player?.seekTo(position) } catch (_: Exception) {} }, 250)
            }.setNegativeButton("Close", null).show()
    }

    private fun editDetails(file: File) {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 8) }
        val folder = EditText(this).apply { hint = "Folder"; setText(prefs.getString("folder_${file.name}", "")) }
        val tags = EditText(this).apply { hint = "Tags, comma separated"; setText(prefs.getString("tags_${file.name}", "")) }
        val notes = EditText(this).apply { hint = "Transcript / notes"; minLines = 3; setText(prefs.getString("notes_${file.name}", "")) }
        panel.addView(folder); panel.addView(tags); panel.addView(notes)
        AlertDialog.Builder(this).setTitle("Recording details").setView(panel).setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString("folder_${file.name}", folder.text.toString())
                    .putString("tags_${file.name}", tags.text.toString()).putString("notes_${file.name}", notes.text.toString()).apply()
                refreshLibrary()
            }.show()
    }
    private fun trimRecording(file: File) {
        val duration = AudioFileTools.durationMs(file)
        if (duration < 1000L) { status.text = "Recording is too short to trim."; return }
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 8) }
        val start = EditText(this).apply { hint = "Start seconds"; inputType = 8194; setText("0") }
        val end = EditText(this).apply { hint = "End seconds"; inputType = 8194; setText((duration / 1000L).toString()) }
        panel.addView(start); panel.addView(end)
        AlertDialog.Builder(this).setTitle("Trim recording").setMessage("Creates a trimmed copy; original is kept.")
            .setView(panel).setNegativeButton("Cancel", null).setPositiveButton("Create copy") { _, _ ->
                try {
                    val a = (start.text.toString().toDouble() * 1000).toLong()
                    val b = (end.text.toString().toDouble() * 1000).toLong()
                    val target = File(file.parentFile, file.nameWithoutExtension + "_trimmed." + file.extension)
                    require(!target.exists()) { "Trimmed copy already exists." }
                    AudioFileTools.trim(file, target, a, b); status.text = "Saved ${target.name}"; refreshLibrary()
                } catch (e: Exception) { status.text = "Trim failed: ${e.message}" }
            }.show()
    }
    private fun transcribeWav(file: File) {
        if (Build.VERSION.SDK_INT < 33 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            status.text = "On-device file transcription is not available. Check for an offline speech model in system settings."
            return
        }
        try {
            val header = ByteArray(44)
            java.io.RandomAccessFile(file, "r").use { it.readFully(header) }
            val data = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val channels = data.getShort(22).toInt()
            val sampleRate = data.getInt(24)
            val pipe = ParcelFileDescriptor.createPipe()
            val readEnd = pipe[0]
            val writeEnd = pipe[1]
            Thread {
                try {
                    java.io.FileInputStream(file).use { input ->
                        input.skip(44)
                        ParcelFileDescriptor.AutoCloseOutputStream(writeEnd).use { output -> input.copyTo(output) }
                    }
                } catch (_: Exception) { try { writeEnd.close() } catch (_: Exception) {} }
            }.start()
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    try { readEnd.close() } catch (_: Exception) {}
                    status.text = "Transcription error: $error"; recognizer.destroy()
                }
                override fun onResults(results: Bundle?) {
                    try { readEnd.close() } catch (_: Exception) {}
                    val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    prefs.edit().putString("notes_${file.name}", transcript).apply()
                    status.text = if (transcript.isBlank()) "No speech detected." else "Transcript saved in Details."
                    recognizer.destroy(); refreshLibrary()
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readEnd)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, channels)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, sampleRate)
            }
            status.text = "Transcribing WAV on-device…"
            recognizer.startListening(request)
        } catch (e: Exception) { status.text = "Transcription could not start: ${e.message}" }
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
