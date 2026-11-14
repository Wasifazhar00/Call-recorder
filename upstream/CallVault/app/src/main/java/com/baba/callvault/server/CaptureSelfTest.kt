/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.server

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Proves, without a phone call, whether THIS host process can capture both sides of an app call.
 *
 * Arms the VoIP policy, opens the far-side sink and a MIC record, then plays a short tone as
 * `USAGE_VOICE_COMMUNICATION` — exactly what a VoIP app's downlink looks like to the policy — and
 * reports each record's state and level. A host that can record app calls hears the tone on the
 * sink (well above −80 dB) and gets both records into RECORDING.
 *
 * Written to settle `docs/dev-notes/2026-10-17-shizuku-voip-research.md`: the same test, run in the
 * Shizuku-hosted process before and after the attribution fix, is the before/after measurement.
 */
object CaptureSelfTest {

    private const val SAMPLE_RATE = 48_000
    private const val TONE_HZ = 440.0
    private const val TONE_MS = 1_200
    private const val TONE_AMPLITUDE = 0.25
    private const val READ_MS = 1_000L

    @SuppressLint("MissingPermission")
    fun run(): String {
        val out = StringBuilder()
        out.append("pid=${Process.myPid()} uid=${Process.myUid()} ")
        out.append("attr=${AudioAttribution.describe()} ")
        val armedHere = !VoipAudioPolicy.isArmed
        val armed = VoipAudioPolicy.arm()
        out.append("arm=$armed ")
        if (!armed) return out.toString()

        var sink: AudioRecord? = null
        var mic: AudioRecord? = null
        var track: AudioTrack? = null
        try {
            sink = VoipAudioPolicy.createSink()
            out.append("sink=${stateOf(sink)} ")
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            mic = HostAudioRecord.open(
                "self-test mic", MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, minBuf * 4,
            )
            out.append("mic=${stateOf(mic)} ")

            sink?.let { runCatching { it.startRecording() }.onFailure { e -> out.append("sinkStartErr=${e.message} ") } }
            mic?.let { runCatching { it.startRecording() }.onFailure { e -> out.append("micStartErr=${e.message} ") } }
            out.append("sinkRec=${recState(sink)} micRec=${recState(mic)} ")

            // Created only now: routing is fixed when a track is created, so it must post-date the policy.
            track = toneTrack()
            track.play()

            val sinkDb = sink?.let { levelDb(it, stereo = true) }
            val micDb = mic?.let { levelDb(it, stereo = false) }
            out.append("sinkDb=${fmt(sinkDb)} micDb=${fmt(micDb)}")
        } catch (t: Throwable) {
            out.append("error=${t.javaClass.simpleName}: ${t.message}")
        } finally {
            runCatching { track?.stop() }; runCatching { track?.release() }
            runCatching { sink?.stop() }; runCatching { sink?.release() }
            runCatching { mic?.stop() }; runCatching { mic?.release() }
            if (armedHere) runCatching { VoipAudioPolicy.disarm() }
        }
        return out.toString()
    }

    private fun toneTrack(): AudioTrack {
        val samples = SAMPLE_RATE * TONE_MS / 1000
        val pcm = ShortArray(samples) { i ->
            (sin(2 * PI * TONE_HZ * i / SAMPLE_RATE) * TONE_AMPLITUDE * Short.MAX_VALUE).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        return track
    }

    /** RMS level over [READ_MS] of reads, in dBFS; null when nothing could be read. */
    private fun levelDb(rec: AudioRecord, stereo: Boolean): Double? {
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) return null
        val buf = ShortArray(if (stereo) 1920 else 960)
        var sumSq = 0.0
        var n = 0L
        val deadline = System.currentTimeMillis() + READ_MS
        while (System.currentTimeMillis() < deadline) {
            val read = rec.read(buf, 0, buf.size)
            if (read <= 0) return null
            for (i in 0 until read) { val s = buf[i].toDouble(); sumSq += s * s }
            n += read
        }
        if (n == 0L) return null
        val rms = sqrt(sumSq / n) / Short.MAX_VALUE
        return if (rms <= 0.0) -120.0 else 20 * log10(rms)
    }

    private fun stateOf(rec: AudioRecord?): String = when {
        rec == null -> "null"
        rec.state == AudioRecord.STATE_INITIALIZED -> "init"
        else -> "uninit"
    }

    private fun recState(rec: AudioRecord?): String = when (rec?.recordingState) {
        null -> "-"
        AudioRecord.RECORDSTATE_RECORDING -> "RECORDING"
        else -> "stopped"
    }

    private fun fmt(db: Double?): String = db?.let { "%.1f".format(it) } ?: "-"
}
